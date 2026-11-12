package app.stereobridge.handoff;

import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import app.stereobridge.mic.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Probe only: captures direct Bluetooth audio while the original Camera owns video. */
public final class MicCaptureService extends Service {
  static volatile String status = "Idle", error;
  static volatile boolean ready, finished;
  static volatile long epochNanos;
  static volatile File leftFile, rightFile;
  private final List<MicSource> sources = new ArrayList<>();
  private final List<DataOutputStream> outputs = new ArrayList<>();
  private final long[] epochs = new long[2];
  private final long[] frames = new long[2];
  private volatile boolean active;
  private final Handler timer=new Handler(Looper.getMainLooper());

  @Override public IBinder onBind(Intent intent) { return null; }
  @Override public int onStartCommand(Intent intent, int flags, int id) {
    if (intent != null && "stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
    if (active) return START_NOT_STICKY;
    if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
      error="Allow nearby devices access first";status=error;ready=false;finished=true;stopSelf();return START_NOT_STICKY;
    }
    NotificationManager notifications = getSystemService(NotificationManager.class);
    notifications.createNotificationChannel(new NotificationChannel("mic-capture", "Camera microphone capture", NotificationManager.IMPORTANCE_LOW));
    PendingIntent stop = PendingIntent.getService(this, 0, new Intent(this, MicCaptureService.class).setAction("stop"), PendingIntent.FLAG_IMMUTABLE);
    Notification notification = new Notification.Builder(this, "mic-capture")
        .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Stereo Camera test")
        .setContentText("Capturing both DJI mics alongside Camera")
        .setOngoing(true).addAction(new Notification.Action.Builder(null, "Stop microphones", stop).build()).build();
    startForeground(19, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
    active = true; ready = false; finished = false; error = null; epochNanos = 0;
    epochs[0] = epochs[1] = frames[0] = frames[1] = 0;
    try {
      BluetoothAdapter adapter = getSystemService(BluetoothManager.class).getAdapter();
      if (adapter == null || !adapter.isEnabled()) throw new IOException("Turn on Bluetooth");
      List<BluetoothDevice> mics = new ArrayList<>();
      for (BluetoothDevice device : adapter.getBondedDevices()) {
        String name = device.getName();
        if (name != null && name.startsWith("DJI Mic Mini-")) mics.add(device);
      }
      mics.sort(Comparator.comparing(BluetoothDevice::getAddress));
      if (mics.size() != 2) throw new IOException("Pair exactly two original DJI Mic Mini transmitters first");
      long take = System.currentTimeMillis();
      leftFile = new File(getFilesDir(), "left-" + take + ".pcm");
      rightFile = new File(getFilesDir(), "right-" + take + ".pcm");
      outputs.add(new DataOutputStream(new BufferedOutputStream(new FileOutputStream(leftFile))));
      outputs.add(new DataOutputStream(new BufferedOutputStream(new FileOutputStream(rightFile))));
      if(!getSharedPreferences("handoff-session",MODE_PRIVATE).edit().putString("staging_left",leftFile.getName()).putString("staging_right",rightFile.getName()).commit())throw new IOException("Could not save capture recovery information");
      timer.postDelayed(()->{if(active)fail(new IOException("Microphone capture reached the three-minute safety limit. Original video retained."));},180000);
      status = "Connecting both mics…";
      for (int channel = 0; channel < 2; channel++) {
        final int index = channel;
        MicSource source = new DjiMicSource(adapter, mics.get(channel).getAddress());
        sources.add(source);
        source.start(new MicSource.Listener() {
          public void onStatus(String message) { android.util.Log.i("HandoffMics", message); }
          public void onError(Exception failure) { fail(failure); }
          public void onAudio(short[] pcm, long timestamp) {
            synchronized (MicCaptureService.this) {
              if (!active) return;
              try {
                if (epochs[index] == 0) epochs[index] = timestamp;
                byte[] bytes=new byte[pcm.length*2];
                for(int i=0;i<pcm.length;i++) { bytes[2*i]=(byte)pcm[i];bytes[2*i+1]=(byte)(pcm[i]>>8); }
                outputs.get(index).write(bytes);
                frames[index] += pcm.length;
                if (epochs[0] != 0 && epochs[1] != 0) {
                  epochNanos = Math.min(epochs[0], epochs[1]);
                  ready = true;
                  status = "Both mics streaming · " + frames[0] + " / " + frames[1] + " samples";
                }
              } catch (IOException failure) { fail(failure); }
            }
          }
        });
      }
    } catch (Exception failure) { fail(failure); }
    return START_NOT_STICKY;
  }

  private void fail(Exception failure) {
    error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    status = "Microphones stopped: " + error;
    active = false; ready = false;
    new Handler(Looper.getMainLooper()).post(this::stopSelf);
  }

  @Override public void onDestroy() {
    timer.removeCallbacksAndMessages(null);
    active = false; ready = false;
    for (MicSource source : sources) source.close();
    synchronized (this) {
      for (DataOutputStream output : outputs) try { output.close(); } catch (IOException failure) { error = failure.getMessage(); }
      try (FileWriter metadata = new FileWriter(new File(getFilesDir(), "audio-timing.txt"))) {
        metadata.write("left=" + leftFile + " epoch=" + epochs[0] + " samples=" + frames[0] + "\nright=" + rightFile + " epoch=" + epochs[1] + " samples=" + frames[1] + "\nerror=" + error + "\n");
      } catch (IOException failure) { error = failure.getMessage(); }
    }
    finished = true;
    stopForeground(STOP_FOREGROUND_REMOVE);
    super.onDestroy();
  }
}
