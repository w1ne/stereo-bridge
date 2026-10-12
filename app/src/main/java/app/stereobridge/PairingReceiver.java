package app.stereobridge;

import android.app.*;
import android.content.*;
import android.os.*;

public final class PairingReceiver extends BroadcastReceiver {
  static final String CODE = "pairing_code";
  static final int NOTIFICATION = 42;
  static final String CHANNEL = "camera_setup";

  static void notify(Context c, String message, boolean input) {
    NotificationManager manager = c.getSystemService(NotificationManager.class);
    manager.createNotificationChannel(
        new NotificationChannel(CHANNEL, "Camera setup", NotificationManager.IMPORTANCE_HIGH));
    Notification.Builder builder =
        new Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Stereo Bridge camera setup")
            .setContentText(message)
            .setStyle(new Notification.BigTextStyle().bigText(message))
            .setContentIntent(
                PendingIntent.getActivity(
                    c,
                    42,
                    new Intent(c, SetupActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    if (input) {
      PendingIntent reply =
          PendingIntent.getBroadcast(
              c,
              42,
              new Intent(c, PairingReceiver.class),
              PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
      builder.addAction(
          new Notification.Action.Builder(null, "Enter pairing code", reply)
              .addRemoteInput(
                  new RemoteInput.Builder(CODE).setLabel("Six-digit pairing code").build())
              .build());
    }
    try {
      manager.notify(NOTIFICATION, builder.build());
    } catch (SecurityException ignored) {
    }
  }

  public void onReceive(Context context, Intent intent) {
    Bundle results = RemoteInput.getResultsFromIntent(intent);
    CharSequence value = results == null ? null : results.getCharSequence(CODE);
    if (value == null) return;
    String code = value.toString().trim();
    if (!code.matches("[0-9]{6}")) {
      notify(context, "Enter the six-digit code shown by Android.", true);
      return;
    }
    PendingResult pending = goAsync();
    Context c = context.getApplicationContext();
    new Thread(
            () -> {
              try {
                BridgeSettings settings = new BridgeSettings(c);
                int port = LocalDiscovery.find(c, "_adb-tls-pairing._tcp", 3000);
                if (port < 1 || !LocalAdb.get(c).pair("127.0.0.1", port, code))
                  throw new Exception("Open a fresh pairing code and try again.");
                settings.flag("adb_paired", true);
                notify(c, "Paired. Return to Stereo Bridge to finish connecting.", false);
                LocalDiscovery.refresh(c);
              } catch (Exception e) {
                notify(c, "Pairing failed. Open Stereo Bridge for manual setup.", false);
              } finally {
                pending.finish();
              }
            },
            "local-adb-pair")
        .start();
  }
}
