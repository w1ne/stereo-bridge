package app.stereobridge;

import android.content.*;
import android.media.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** Packaged helper, executed only by the owner's authorized local ADB shell. */
public final class RoutingMain {
  static Object call(Object o, String name, Object... args) throws Exception {
    for (Method m : o.getClass().getMethods())
      if (m.getName().equals(name) && m.getParameterCount() == args.length)
        try {
          return m.invoke(o, args);
        } catch (IllegalArgumentException ignored) {
        }
    throw new NoSuchMethodException(name);
  }

  public static void main(String[] args) {
    int exitCode = 0;
    try {
      run(args);
    } catch (Throwable failure) {
      failure.printStackTrace();
      exitCode = 1;
    } finally {
      // ActivityThread starts Binder threads that can outlive this session's main thread.
      System.exit(exitCode);
    }
  }

  private static void run(String[] args) throws Exception {
    if (args.length != 3) throw new IllegalArgumentException("uid port session required");
    Looper.prepareMainLooper();
    Object t = Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
    Context base =
        ((Context)
                Class.forName("android.app.ActivityThread").getMethod("getSystemContext").invoke(t))
            .createPackageContext("com.android.shell", 0);
    AttributionSource attribution =
        new AttributionSource.Builder(2000).setPackageName("com.android.shell").build();
    Context context =
        new ContextWrapper(base) {
          public String getOpPackageName() {
            return "com.android.shell";
          }

          public AttributionSource getAttributionSource() {
            return attribution;
          }
        };
    AudioManager manager = context.getSystemService(AudioManager.class);
    Object policy = null;
    AudioTrack track = null;
    boolean registered = false;
    AtomicBoolean running = new AtomicBoolean(true);
    Thread writer = null;
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("127.0.0.1", Integer.parseInt(args[1])), 10000);
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      out.writeUTF(args[2]);
      out.flush();
      Class<?> ruleType = Class.forName("android.media.audiopolicy.AudioMixingRule");
      Object rb = Class.forName(ruleType.getName() + "$Builder").getConstructor().newInstance();
      call(rb, "setTargetMixRole", ruleType.getField("MIX_ROLE_INJECTOR").getInt(null));
      call(rb, "addMixRule", 4, Integer.valueOf(args[0]));
      Object rule = call(rb, "build");
      Object mb =
          Class.forName("android.media.audiopolicy.AudioMix$Builder")
              .getConstructor(ruleType)
              .newInstance(rule);
      call(
          mb,
          "setFormat",
          new AudioFormat.Builder()
              .setSampleRate(48000)
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
              .build());
      call(mb, "setRouteFlags", 2);
      Object mix = call(mb, "build");
      Object pb =
          Class.forName("android.media.audiopolicy.AudioPolicy$Builder")
              .getConstructor(Context.class)
              .newInstance(context);
      call(pb, "addMix", mix);
      policy = call(pb, "build");
      int result = (Integer) call(manager, "registerAudioPolicy", policy);
      if (result != 0) throw new IOException("Routing registration failed: " + result);
      registered = true;
      AudioTrack.Builder builder =
          new AudioTrack.Builder()
              .setBufferSizeInBytes(19200)
              .setTransferMode(AudioTrack.MODE_STREAM);
      call(builder, "setContext", context);
      track = (AudioTrack) call(policy, "createAudioTrackSource", mix, builder);
      if (track == null || track.getState() != AudioTrack.STATE_INITIALIZED)
        throw new IOException("Virtual input unavailable");
      track.play();
      out.writeUTF("READY");
      out.flush();
      // SPP delivers five-frame bursts; allow 200 ms of scheduling headroom.
      ArrayBlockingQueue<byte[]> frames = new ArrayBlockingQueue<>(40);
      AudioTrack source = track;
      writer =
          new Thread(
              () -> {
                try {
                  while (running.get()) {
                    byte[] frame = frames.take();
                    int offset = 0;
                    while (running.get() && offset < frame.length) {
                      int n =
                          source.write(
                              frame, offset, frame.length - offset, AudioTrack.WRITE_NON_BLOCKING);
                      if (n < 0) throw new IOException("Audio injection failed: " + n);
                      // No recorder may be consuming the mix yet. Never retain unwritable PCM.
                      if (n == 0) break;
                      offset += n;
                    }
                  }
                } catch (InterruptedException expected) {
                  Thread.currentThread().interrupt();
                } catch (Exception failure) {
                  failure.printStackTrace();
                  try {
                    socket.close();
                  } catch (IOException ignored) {
                  }
                }
              },
              "stereo-injection");
      writer.setDaemon(true);
      writer.start();
      DataInputStream input = new DataInputStream(socket.getInputStream());
      while (true) {
        byte[] frame = new byte[960];
        input.readFully(frame);
        // Keep reading even while AudioTrack has no consumer, including the session's EOF.
        if (!frames.offer(frame)) {
          frames.poll();
          frames.offer(frame);
        }
      }
    } catch (EOFException expected) {
    } finally {
      running.set(false);
      if (writer != null) {
        writer.interrupt();
        try {
          writer.join(1000);
        } catch (InterruptedException expected) {
          Thread.currentThread().interrupt();
        }
      }
      if (track != null) {
        try {
          track.stop();
        } catch (Exception ignored) {
        }
        try {
          track.release();
        } finally {
          if (registered) call(manager, "unregisterAudioPolicy", policy);
        }
      } else if (registered) {
        call(manager, "unregisterAudioPolicy", policy);
      }
    }
  }
}
