package app.stereobridge;

import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import app.stereobridge.mic.*;
import io.github.muntashirakon.adb.AdbStream;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BridgeService extends Service {
  public static final String STOP = "app.stereobridge.STOP";
  private final AtomicBoolean active = new AtomicBoolean();
  private final AudioFrameBuffer left = new AudioFrameBuffer(), right = new AudioFrameBuffer();
  private final Object cleanupLock = new Object();
  private volatile Thread cancellation;
  private PowerManager.WakeLock wakeLock;
  private final List<MicSource> sources = new ArrayList<>();
  private volatile Socket socket;
  private volatile ServerSocket server;
  private volatile AdbStream shell;
  private volatile Thread worker;
  private volatile String helperDiagnostic = "";
  private volatile long generation;
  private volatile String failure;
  private boolean cameraMode;
  private BridgeSettings settings;

  public IBinder onBind(Intent i) {
    return null;
  }

  public synchronized int onStartCommand(Intent i, int flags, int id) {
    if (i == null || STOP.equals(i.getAction())) {
      stopSession();
      return START_NOT_STICKY;
    }
    if (worker != null) return START_NOT_STICKY;
    if (!active.compareAndSet(false, true)) return START_NOT_STICKY;
    generation++;
    failure = null;
    settings = new BridgeSettings(this);
    cameraMode = i.getBooleanExtra("camera", false);
    BridgeState.cameraMode = cameraMode;
    BridgeState.leftStatus = BridgeState.rightStatus = "Connecting";
    left.clear();
    right.clear();
    BridgeState.startedAt = SystemClock.elapsedRealtime();
    BridgeState.droppedFrames = 0;
    BridgeState.update(BridgeState.Phase.CONNECTING, "Connecting both microphones…");
    try {
      if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
          != android.content.pm.PackageManager.PERMISSION_GRANTED)
        throw new SecurityException("Allow microphone permission before starting.");
      NotificationManager manager = getSystemService(NotificationManager.class);
      manager.cancel(2);
      manager.createNotificationChannel(
          new NotificationChannel(
              "capture", "Microphone recording", NotificationManager.IMPORTANCE_LOW));
      startForeground(1, captureNotification("Connecting both microphones"));
      wakeLock =
          getSystemService(PowerManager.class)
              .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StereoBridge:capture");
      wakeLock.acquire();
      worker = new Thread(this::runSession, "stereo-mixer");
      worker.start();
    } catch (RuntimeException e) {
      active.set(false);
      releaseWakeLock();
      BridgeState.leftStatus = BridgeState.rightStatus = "Disconnected";
      BridgeState.update(
          BridgeState.Phase.ERROR,
          "Could not start capture: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
      stopForeground(STOP_FOREGROUND_REMOVE);
      showErrorNotification();
      stopSelf();
    }
    return START_NOT_STICKY;
  }

  private void runSession() {
    final long expected = generation;
    File temporary = new File(getFilesDir(), "take-recording.wav");
    boolean saved = false;
    boolean recordingCreated = false;
    ScheduledExecutorService routeWatchdog = null;
    RouteWriteGuard routeWrite = new RouteWriteGuard();
    try {
      if (settings.address(true).isEmpty()
          || settings.address(false).isEmpty()
          || settings.address(true).equals(settings.address(false)))
        throw new IOException("Choose two different microphones.");
      if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
              != android.content.pm.PackageManager.PERMISSION_GRANTED
          || checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
              != android.content.pm.PackageManager.PERMISSION_GRANTED)
        throw new IOException("Allow microphone and nearby devices permissions before starting.");
      BluetoothManager bluetooth = getSystemService(BluetoothManager.class);
      BluetoothAdapter adapter = bluetooth == null ? null : bluetooth.getAdapter();
      if (adapter == null || !adapter.isEnabled())
        throw new IOException("Turn on Bluetooth before starting.");
      for (boolean side : new boolean[] {true, false}) {
        String address = settings.address(side);
        if (!BluetoothAdapter.checkBluetoothAddress(address)
            || adapter.getRemoteDevice(address).getBondState() != BluetoothDevice.BOND_BONDED)
          throw new IOException(
              "Pair both selected microphones in Bluetooth settings before starting.");
      }
      if (cameraMode) {
        try {
          getPackageManager().getApplicationInfo(settings.camera(), 0);
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
          throw new IOException(
              "Selected camera is unavailable. Choose an installed camera in setup.");
        }
      }
      for (int channel = 0; channel < 2; channel++) {
        checkActive();
        final int side = channel;
        MicSource source = new DjiMicSource(adapter, settings.address(side == 0));
        synchronized (sources) {
          checkActive();
          sources.add(source);
        }
        source.start(
            new MicSource.Listener() {
              public void onAudio(short[] pcm, long timestamp) {
                synchronized (BridgeService.this) {
                  if (!active.get() || expected != generation) return;
                  (side == 0 ? left : right).offer(pcm);
                  if (side == 0) BridgeState.leftStatus = "Streaming";
                  else BridgeState.rightStatus = "Streaming";
                }
              }

              public void onStatus(String message) {
                synchronized (BridgeService.this) {
                  if (!active.get() || expected != generation) return;
                  String status =
                      message.startsWith("Streaming")
                          ? "Streaming"
                          : message.startsWith("Sequence") ? "Recovering audio" : "Connecting";
                  if (side == 0) BridgeState.leftStatus = status;
                  else BridgeState.rightStatus = status;
                }
              }

              public void onError(Exception e) {
                fail(
                    (side == 0 ? "Left" : "Right") + " microphone disconnected: " + e.getMessage(),
                    expected);
              }
            });
      }
      OutputStream output = null;
      if (cameraMode) {
        checkActive();
        output = openRoute(expected);
        routeWatchdog =
            Executors.newSingleThreadScheduledExecutor(
                task -> {
                  Thread thread = new Thread(task, "stereo-route-watchdog");
                  thread.setDaemon(true);
                  return thread;
                });
        routeWatchdog.scheduleAtFixedRate(
            () -> {
              if (routeWrite.stalled(System.nanoTime()))
                fail("Camera audio routing stalled. Stereo capture stopped.", expected);
            },
            250,
            250,
            TimeUnit.MILLISECONDS);
      }
      checkActive();
      // Observe audio from both sources, then discard all setup backlog.
      short[] initialLeft = left.poll(20, TimeUnit.SECONDS);
      checkActive();
      short[] initialRight = right.poll(20, TimeUnit.SECONDS);
      checkActive();
      if (initialLeft == null || initialRight == null)
        throw new IOException("Both microphones must send audio. Check power and pairing.");
      synchronized (this) {
        checkActive();
        left.beginLive();
        right.beginLive();
      }
      short[] freshLeft = left.poll(1, TimeUnit.SECONDS);
      short[] freshRight = right.poll(1, TimeUnit.SECONDS);
      checkActive();
      if (freshLeft == null || freshRight == null)
        throw new IOException("Microphone audio stalled before capture started.");
      try (WavFile wav = cameraMode ? null : new WavFile(temporary)) {
        recordingCreated = wav != null;
        boolean firstFrame = true;
        while (active.get()) {
          short[] l = firstFrame ? freshLeft : left.poll(250, TimeUnit.MILLISECONDS),
              r = firstFrame ? freshRight : right.poll(250, TimeUnit.MILLISECONDS);
          checkActive();
          if (l == null || r == null)
            throw new IOException("Microphone audio stalled. Recording stopped.");
          if (l.length != 240 || r.length != 240)
            throw new IOException("Unexpected audio frame size");
          byte[] pcm = new byte[960];
          float lg = settings.flag("mute_left") ? 0 : settings.gain(true),
              rg = settings.flag("mute_right") ? 0 : settings.gain(false);
          boolean swap = settings.flag("swap");
          int peakL = 0, peakR = 0;
          for (int n = 0; n < 240; n++) {
            int lv = clamp(l[n] * lg), rv = clamp(r[n] * rg);
            peakL = Math.max(peakL, Math.abs(lv));
            peakR = Math.max(peakR, Math.abs(rv));
            int a = swap ? rv : lv, b = swap ? lv : rv;
            int off = n * 4;
            pcm[off] = (byte) a;
            pcm[off + 1] = (byte) (a >> 8);
            pcm[off + 2] = (byte) b;
            pcm[off + 3] = (byte) (b >> 8);
          }
          BridgeState.droppedFrames = left.droppedFrames() + right.droppedFrames();
          BridgeState.leftLevel = peakL;
          BridgeState.rightLevel = peakR;
          if (wav != null) wav.write(pcm);
          if (output != null) {
            routeWrite.begin(System.nanoTime());
            try {
              output.write(pcm);
            } finally {
              routeWrite.finish();
            }
          }
          if (firstFrame) {
            synchronized (this) {
              checkActive();
              BridgeState.startedAt = SystemClock.elapsedRealtime();
              getSystemService(NotificationManager.class)
                  .notify(
                      1,
                      captureNotification(
                          cameraMode
                              ? "Stereo connected · record in Camera"
                              : "Recording audio preview"));
              BridgeState.update(
                  BridgeState.Phase.LIVE,
                  cameraMode
                      ? "Stereo audio routed · start recording in your camera"
                      : "Recording audio preview · speak into either microphone");
            }
            firstFrame = false;
          }
        }
      }
      saved = !cameraMode;
    } catch (Exception e) {
      if (active.get()) fail(e.getMessage() == null ? e.toString() : e.getMessage(), expected);
      else saved = recordingCreated && temporary.length() > 44;
    } finally {
      active.set(false);
      routeWrite.finish();
      if (routeWatchdog != null) routeWatchdog.shutdownNow();
      Thread closing = cancellation;
      if (closing != null && closing != Thread.currentThread()) {
        boolean interrupted = Thread.interrupted();
        for (; ; ) {
          try {
            closing.join();
            break;
          } catch (InterruptedException e) {
            interrupted = true;
          }
        }
        if (interrupted) Thread.currentThread().interrupt();
      }
      closeResources();
      if (saved && temporary.length() > 44) {
        File last = new File(getFilesDir(), "last-take.wav");
        try {
          java.nio.file.Files.move(
              temporary.toPath(), last.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
          BridgeState.update(
              BridgeState.Phase.ERROR, "Could not save recording: " + e.getMessage());
        }
      }
      synchronized (this) {
        BridgeState.leftLevel = BridgeState.rightLevel = 0;
        BridgeState.leftStatus = BridgeState.rightStatus = "Disconnected";
        releaseWakeLock();
        if (failure != null) BridgeState.update(BridgeState.Phase.ERROR, failure);
        else if (BridgeState.phase != BridgeState.Phase.ERROR)
          BridgeState.update(
              BridgeState.Phase.STOPPED, saved ? "Take saved. Play it below." : "Stopped.");
        if (BridgeState.phase == BridgeState.Phase.ERROR) {
          stopForeground(STOP_FOREGROUND_REMOVE);
          showErrorNotification();
        } else stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        worker = null;
        cancellation = null;
      }
    }
  }

  private OutputStream openRoute(long expected) throws Exception {
    checkActive();
    int uid = getPackageManager().getApplicationInfo(settings.camera(), 0).uid;
    ServerSocket listening = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
    synchronized (cleanupLock) {
      server = listening;
    }
    checkActive();
    listening.setSoTimeout(20000);
    String token = UUID.randomUUID().toString();
    LocalAdb adb = LocalAdb.get(this);
    int port = Integer.parseInt(settings.get("adb_port", "0"));
    if (port < 1 || !adb.connect("127.0.0.1", port))
      throw new IOException("Open Camera setup and reconnect wireless debugging.");
    checkActive();
    String apk = getApplicationInfo().sourceDir;
    String command =
        "CLASSPATH="
            + quote(apk)
            + " app_process /system/bin app.stereobridge.RoutingMain "
            + uid
            + " "
            + listening.getLocalPort()
            + " "
            + quote(token);
    final AdbStream launched = adb.shell(command);
    synchronized (cleanupLock) {
      shell = launched;
    }
    checkActive();
    helperDiagnostic = "";
    new Thread(
            () -> {
              try {
                byte[] b = new byte[512];
                InputStream in = launched.openInputStream();
                int n;
                while ((n = in.read(b)) >= 0) {
                  String line = new String(b, 0, n, java.nio.charset.StandardCharsets.UTF_8);
                  synchronized (BridgeService.this) {
                    if (!active.get() || expected != generation) return;
                    helperDiagnostic = (helperDiagnostic + line);
                    if (helperDiagnostic.length() > 2000)
                      helperDiagnostic =
                          helperDiagnostic.substring(helperDiagnostic.length() - 2000);
                  }
                }
                if (active.get())
                  fail("Camera routing helper stopped. " + helperDiagnostic, expected);
              } catch (Exception e) {
                if (active.get())
                  fail("Camera routing connection closed. " + helperDiagnostic, expected);
              }
            },
            "routing-helper-output")
        .start();
    Socket connected = listening.accept();
    synchronized (cleanupLock) {
      socket = connected;
    }
    checkActive();
    connected.setSoTimeout(10000);
    DataInputStream input = new DataInputStream(connected.getInputStream());
    if (!token.equals(input.readUTF()))
      throw new IOException("Routing session authentication failed");
    if (!"READY".equals(input.readUTF()))
      throw new IOException("Camera routing did not initialize");
    connected.setSoTimeout(0);
    listening.close();
    synchronized (cleanupLock) {
      server = null;
    }
    return connected.getOutputStream();
  }

  static String quote(String value) {
    return "'" + value.replace("'", "'\\''") + "'";
  }

  private static int clamp(float v) {
    return Math.max(-32768, Math.min(32767, Math.round(v)));
  }

  private synchronized void fail(String message, long expected) {
    if (expected != generation || !active.get()) return;
    failure = message;
    BridgeState.update(BridgeState.Phase.STOPPING, "Stopping: " + message);
    cancelSession();
  }

  private void closeResources() {
    synchronized (cleanupLock) {
      // Unblock route reads/writes before waiting for microphone readers.
      try {
        if (socket != null) socket.close();
      } catch (Exception ignored) {
      }
      try {
        if (server != null) server.close();
      } catch (Exception ignored) {
      }
      try {
        if (shell != null) shell.close();
      } catch (Exception ignored) {
      }
      socket = null;
      server = null;
      shell = null;
      synchronized (sources) {
        for (MicSource source : sources) source.close();
        sources.clear();
      }
      left.clear();
      right.clear();
    }
  }

  private void checkActive() throws InterruptedException {
    if (!active.get()) throw new InterruptedException("Stopped");
  }

  private synchronized void cancelSession() {
    active.set(false);
    Thread current = worker;
    if (current != null) current.interrupt();
    if (cancellation == null && current != null) {
      cancellation = new Thread(this::closeResources, "stereo-cancel");
      cancellation.start();
    }
  }

  private synchronized void stopSession() {
    if (worker != null) {
      if (active.get())
        BridgeState.update(
            BridgeState.Phase.STOPPING, cameraMode ? "Stopping audio routing…" : "Saving take…");
      cancelSession();
    } else stopSelf();
  }

  private Notification captureNotification(String message) {
    PendingIntent open =
        PendingIntent.getActivity(
            this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
    PendingIntent stop =
        PendingIntent.getService(
            this,
            1,
            new Intent(this, BridgeService.class).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(this, "capture")
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Stereo Bridge")
        .setContentText(message)
        .setContentIntent(open)
        .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
        .setOngoing(true)
        .build();
  }

  /** Times only an actual socket write, so source polling never looks like routing failure. */
  static final class RouteWriteGuard {
    private boolean pending;
    private long started;

    synchronized void begin(long now) {
      started = now;
      pending = true;
    }

    synchronized void finish() {
      pending = false;
    }

    synchronized boolean stalled(long now) {
      return pending && now - started >= TimeUnit.SECONDS.toNanos(3);
    }
  }

  private void showErrorNotification() {
    NotificationManager manager = getSystemService(NotificationManager.class);
    manager.createNotificationChannel(
        new NotificationChannel(
            "failures", "Connection alerts", NotificationManager.IMPORTANCE_HIGH));
    manager.notify(
        2,
        new Notification.Builder(this, "failures")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Stereo Bridge stopped")
            .setContentText(BridgeState.message)
            .setStyle(new Notification.BigTextStyle().bigText(BridgeState.message))
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .setOngoing(false)
            .build());
  }

  private synchronized void releaseWakeLock() {
    if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    wakeLock = null;
  }

  public synchronized void onDestroy() {
    if (active.get()) BridgeState.update(BridgeState.Phase.STOPPING, "Stopping capture…");
    cancelSession();
    releaseWakeLock();
    super.onDestroy();
  }
}
