package app.stereobridge;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-driven setup. No microphone capture starts here. */
public final class SetupActivity extends Activity {
  private EditText pairPort, code, connectPort;
  private TextView status;
  private Button done;
  private BridgeSettings settings;
  private Ui theme;
  private final Handler ui = new Handler(Looper.getMainLooper());
  private final AtomicBoolean working = new AtomicBoolean();
  private volatile boolean destroyed;
  private boolean discovering;

  public void onCreate(Bundle state) {
    Ui.theme(this);
    super.onCreate(state);
    theme = new Ui(this);
    settings = new BridgeSettings(this);
    ScrollView scroll = new ScrollView(this);
    LinearLayout root = column();
    scroll.addView(root);
    root.addView(theme.caption("STEREO BRIDGE / SETUP"));
    theme.space(root, 12);
    root.addView(theme.text("Connect to\nyour camera", 32, true));
    theme.space(root, 8);
    root.addView(
        theme.caption("Android needs a one-time approval to route stereo audio into another app."));
    theme.space(root, 20);
    step(
        root,
        "1",
        "Enable Wireless debugging",
        "Open Android setup below. In Developer options, turn on Wireless debugging while connected"
            + " to Wi-Fi. Keep it enabled for camera sessions.");
    step(
        root,
        "2",
        "Get a pairing code",
        "Tap Pair device with pairing code. Pull down notifications and tap Enter pairing code on"
            + " Stereo Bridge. Enter Android’s six-digit code.");
    step(
        root,
        "3",
        "Return to Stereo Bridge",
        "Once connected, tap Done. Next time, just tap Start.");
    LinearLayout connection = theme.card(root);
    status = theme.text("Looking for this phone…", 16, true);
    connection.addView(status);
    done =
        theme.button(
            connection,
            "Done",
            true,
            () -> {
              setResult(RESULT_OK);
              finish();
            });
    done.setEnabled(false);
    theme.button(root, "Open Android setup", false, this::openAndroidSetup);
    theme.space(root, 8);
    root.addView(
        theme.caption(
            "No Developer options? Go to About phone and tap Build number seven times, then return"
                + " here."));
    LinearLayout manual = column();
    manual.setVisibility(android.view.View.GONE);
    theme.button(
        root,
        "Manual setup",
        false,
        () ->
            manual.setVisibility(
                manual.getVisibility() == android.view.View.GONE
                    ? android.view.View.VISIBLE
                    : android.view.View.GONE));
    root.addView(manual);
    theme.space(manual, 12);
    manual.addView(
        theme.caption(
            "Use split screen if notifications or automatic discovery are unavailable. Pairing and"
                + " connection use different ports."));
    pairPort = field(manual, "Pairing port", "");
    code = field(manual, "Six-digit pairing code", "");
    theme.button(
        manual,
        "Pair this phone",
        false,
        () -> {
          try {
            int port = port(pairPort);
            String value = code.getText().toString().trim();
            if (!value.matches("[0-9]{6}"))
              throw new Exception("Enter Android’s six-digit pairing code.");
            work(
                () -> {
                  if (!LocalAdb.get(this).pair("127.0.0.1", port, value))
                    throw new Exception("Generate a fresh pairing code and try again.");
                  settings.flag("adb_paired", true);
                  post(() -> code.setText(""));
                  return "Paired. Enter the main Wireless debugging connection port below.";
                },
                false);
          } catch (Exception e) {
            status.setText(e.getMessage());
          }
        });
    connectPort = field(manual, "Connection port", settings.get("adb_port", ""));
    theme.button(
        manual,
        "Connect",
        false,
        () -> {
          try {
            int port = port(connectPort);
            connect(port);
          } catch (Exception e) {
            status.setText(e.getMessage());
          }
        });
    theme.screen(scroll, root);
  }

  private LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private void step(LinearLayout root, String number, String title, String body) {
    LinearLayout card = theme.card(root);
    TextView label = theme.text(number + "  " + title, 17, true);
    card.addView(label);
    theme.space(card, 6);
    card.addView(theme.caption(body));
  }

  private EditText field(LinearLayout root, String hint, String value) {
    EditText e = new EditText(this);
    e.setHint(hint);
    e.setTextColor(theme.text);
    e.setHintTextColor(theme.secondary);
    e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    e.setText(value);
    e.setMinHeight(theme.dp(52));
    root.addView(e);
    return e;
  }

  private int port(EditText e) throws Exception {
    try {
      int p = Integer.parseInt(e.getText().toString().trim());
      if (p > 0 && p <= 65535) return p;
    } catch (NumberFormatException ignored) {
    }
    throw new Exception("Enter a port between 1 and 65535.");
  }

  private void post(Runnable r) {
    ui.post(
        () -> {
          if (!destroyed) r.run();
        });
  }

  interface Task {
    String run() throws Exception;
  }

  private void work(Task task, boolean connection) {
    if (destroyed || !working.compareAndSet(false, true)) return;
    done.setEnabled(false);
    status.setText("Connecting securely to this phone…");
    new Thread(
            () -> {
              try {
                String result = task.run();
                post(
                    () -> {
                      status.setText(result);
                      if (connection) done.setEnabled(true);
                    });
              } catch (Exception e) {
                post(() -> status.setText("Could not connect. " + e.getMessage()));
              } finally {
                working.set(false);
              }
            },
            "local-adb-setup")
        .start();
  }

  private void connect(int port) {
    work(
        () -> {
          if (!LocalAdb.get(this).connect("127.0.0.1", port))
            throw new Exception("Pair this phone first, then try again.");
          settings.set("adb_port", Integer.toString(port));
          settings.flag("adb_paired", true);
          return "Connected to this phone. You’re ready.";
        },
        true);
  }

  private void openAndroidSetup() {
    settings.set("pair_port", "");
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
            != android.content.pm.PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 9);
    else
      PairingReceiver.notify(
          this, "Enter the code from Wireless debugging → Pair device with pairing code.", true);
    LocalDiscovery.discover(
        this,
        "_adb-tls-pairing._tcp",
        port -> {
          if (destroyed) return;
          settings.set("pair_port", Integer.toString(port));
          pairPort.setText(Integer.toString(port));
          PairingReceiver.notify(this, "Enter Android’s six-digit pairing code.", true);
        });
    try {
      startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
    } catch (Exception e) {
      status.setText(
          "Open About phone and tap Build number seven times to enable Developer options.");
    }
  }

  public void onRequestPermissionsResult(int r, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(r, permissions, grants);
    if (r == 9
        && grants.length > 0
        && grants[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
      PairingReceiver.notify(this, "Enter Android’s six-digit pairing code.", true);
    else if (r == 9)
      status.setText(
          "Use Manual setup with split screen, or enable notifications to enter the pairing code.");
  }

  private void discover() {
    if (discovering) return;
    discovering = true;
    LocalDiscovery.discover(
        this,
        "_adb-tls-connect._tcp",
        port -> {
          if (destroyed) return;
          settings.set("adb_port", Integer.toString(port));
          connectPort.setText(Integer.toString(port));
          if (settings.flag("adb_paired") && !done.isEnabled()) connect(port);
        });
    ui.postDelayed(
        () -> {
          if (!destroyed) discovering = false;
        },
        25000);
  }

  protected void onResume() {
    super.onResume();
    if (settings != null) {
      discover();
      // The connection advertisement may have been resolved before pairing completed.
      // Reuse its local port when returning from Android, without another discovery listener.
      if (settings.flag("adb_paired") && !done.isEnabled() && !working.get()) {
        try {
          int cached = Integer.parseInt(settings.get("adb_port", "0"));
          if (cached > 0 && cached <= 65535) connect(cached);
        } catch (NumberFormatException ignored) {
        }
      }
    }
  }

  protected void onDestroy() {
    destroyed = true;
    ui.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
