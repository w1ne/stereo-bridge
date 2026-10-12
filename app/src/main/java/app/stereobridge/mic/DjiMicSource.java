package app.stereobridge.mic;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Independent, receive-only SPP transport for one user-selected bonded Mic Mini. Callbacks run on
 * the source's reader thread. Each source owns an LC3 decoder. Timestamps are monotonic host time
 * for the first sample, advancing by 5 ms.
 */
public final class DjiMicSource implements MicSource {
  private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
  private static final long FRAME_NS = 5_000_000L;
  private final BluetoothAdapter adapter;
  private final String mac;
  private volatile boolean running;
  private volatile long deadline;
  private BluetoothSocket socket;
  private Thread reader;
  private ScheduledExecutorService watchdog;
  private boolean started;

  public DjiMicSource(BluetoothAdapter adapter, String mac) {
    this.adapter = Objects.requireNonNull(adapter);
    if (!BluetoothAdapter.checkBluetoothAddress(mac))
      throw new IllegalArgumentException("Invalid Bluetooth MAC");
    this.mac = mac;
  }

  @Override
  public synchronized void start(Listener listener) {
    Objects.requireNonNull(listener);
    if (started) throw new IllegalStateException("Source already started or closed");
    started = true;
    running = true;
    deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    watchdog =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "mic-timeout-" + mac);
              t.setDaemon(true);
              return t;
            });
    watchdog.scheduleAtFixedRate(
        () -> {
          if (running && System.nanoTime() >= deadline) closeSocket();
        },
        250,
        250,
        TimeUnit.MILLISECONDS);
    reader = new Thread(() -> read(listener), "mic-spp-" + mac);
    reader.start();
  }

  private void read(Listener listener) {
    NativeLc3 decoder = null;
    try {
      BluetoothDevice device = adapter.getRemoteDevice(mac);
      if (device.getBondState() != BluetoothDevice.BOND_BONDED)
        throw new IOException("Selected microphone is not bonded: " + mac);
      BluetoothSocket next = device.createRfcommSocketToServiceRecord(SPP);
      synchronized (this) {
        if (!running) {
          next.close();
          return;
        }
        socket = next;
      }
      listener.onStatus("Connecting " + mac);
      next.connect();
      deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      decoder = new NativeLc3();
      listener.onStatus("Streaming " + mac);
      InputStream input = next.getInputStream();
      DjiPacketParser parser = new DjiPacketParser();
      byte[] bytes = new byte[4096];
      int previous = -1;
      long timestamp = 0;
      while (running) {
        int count = input.read(bytes);
        if (count < 0) throw new EOFException("Microphone disconnected: " + mac);
        if (count == 0) continue;
        for (DjiPacketParser.Frame frame : parser.feed(bytes, 0, count)) {
          if (!running) break;
          deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
          if (timestamp == 0) timestamp = System.nanoTime() - 20_000_000L;
          if (previous >= 0) {
            int delta = (frame.sequence - previous) & 255;
            if (delta == 0) continue;
            int missing = delta - 1;
            if (missing <= 16) {
              for (int i = 0; i < missing && running; i++) {
                listener.onAudio(decoder.conceal(), timestamp);
                timestamp += FRAME_NS;
              }
            } else {
              decoder.close();
              decoder = new NativeLc3();
              timestamp = Math.max(timestamp, System.nanoTime() - 20_000_000L);
              listener.onStatus("Sequence discontinuity " + mac);
            }
          }
          if (!running) break;
          listener.onAudio(decoder.decode(frame.payload), timestamp);
          timestamp += FRAME_NS;
          previous = frame.sequence;
        }
      }
    } catch (SecurityException error) {
      if (running) listener.onError(error);
    } catch (Exception error) {
      if (running) listener.onError(error);
    } finally {
      running = false;
      closeSocket();
      if (decoder != null) decoder.close();
      synchronized (this) {
        if (watchdog != null) watchdog.shutdownNow();
      }
    }
  }

  private void closeSocket() {
    BluetoothSocket current;
    synchronized (this) {
      current = socket;
      socket = null;
    }
    if (current != null)
      try {
        current.close();
      } catch (IOException ignored) {
      }
  }

  @Override
  public void close() {
    Thread current;
    synchronized (this) {
      started = true;
      running = false;
      if (watchdog != null) watchdog.shutdownNow();
      current = reader;
    }
    closeSocket(); // Android closes the blocked connect/read as well.
    if (current != null && current != Thread.currentThread()) {
      current.interrupt();
      try {
        current.join(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
