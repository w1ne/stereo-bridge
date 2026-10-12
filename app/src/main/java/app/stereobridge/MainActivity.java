package app.stereobridge;

import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public final class MainActivity extends Activity {
  private BridgeSettings settings;
  private TextView status, time, detail, leftName, rightName, leftState, rightState, diagnostics;
  private Ui ui;
  private Button repair, advancedButton;
  private boolean expanded;
  private Button record, bridge, stop, play, camera;
  private Meter leftMeter, rightMeter;
  private final Handler handler = new Handler();
  private MediaPlayer player;
  private boolean pendingCamera, launchWhenLive;
  private LinearLayout advanced;

  protected void onSaveInstanceState(Bundle state) {
    state.putBoolean("advanced", expanded);
    state.putBoolean("pending_camera", pendingCamera);
    state.putBoolean("launch_when_live", launchWhenLive);
    super.onSaveInstanceState(state);
  }

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private TextView text(String s, int size) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextColor(ui.text);
    t.setTextSize(size);
    return t;
  }

  private Button button(LinearLayout root, String label, Runnable action) {
    return ui.button(root, label, false, action);
  }

  public void onCreate(Bundle state) {
    Ui.theme(this);
    super.onCreate(state);
    ui = new Ui(this);
    settings = new BridgeSettings(this);
    if (state != null) {
      expanded = state.getBoolean("advanced");
      pendingCamera = state.getBoolean("pending_camera");
      launchWhenLive = state.getBoolean("launch_when_live");
    }
    build();
  }

  private void build() {
    ScrollView scroll = new ScrollView(this);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(root);
    TextView brand = ui.caption("STEREO BRIDGE");
    brand.setLetterSpacing(0.14f);
    root.addView(brand);
    ui.space(root, 8);
    root.addView(ui.text("Two mics.\nOne camera.", 34, true));
    ui.space(root, 8);
    root.addView(ui.caption("Receiver-free stereo audio for your camera."));
    ui.space(root, 24);
    LinearLayout session = ui.card(root);
    status = ui.text("", 20, true);
    session.addView(status);
    ui.space(session, 6);
    detail = ui.caption("");
    session.addView(detail);
    ui.space(session, 10);
    time = ui.text("", 15, true);
    time.setTextColor(ui.accent);
    session.addView(time);
    repair =
        ui.button(
            session,
            "Camera setup",
            false,
            () -> startActivityForResult(new Intent(this, SetupActivity.class), 3));
    LinearLayout l = ui.card(root);
    l.addView(ui.text("L  /  LEFT", 12, true));
    leftName = ui.text("", 17, true);
    l.addView(leftName);
    leftState = ui.caption("");
    l.addView(leftState);
    ui.space(l, 12);
    leftMeter = new Meter(this, ui.accent, ui.border);
    leftMeter.setContentDescription("Left microphone signal level");
    l.addView(leftMeter, new LinearLayout.LayoutParams(-1, dp(12)));
    LinearLayout r = ui.card(root);
    r.addView(ui.text("R  /  RIGHT", 12, true));
    rightName = ui.text("", 17, true);
    r.addView(rightName);
    rightState = ui.caption("");
    r.addView(rightState);
    ui.space(r, 12);
    rightMeter = new Meter(this, ui.blue, ui.border);
    rightMeter.setContentDescription("Right microphone signal level");
    r.addView(rightMeter, new LinearLayout.LayoutParams(-1, dp(12)));
    bridge =
        ui.button(
            root,
            "Start",
            true,
            () -> {
              if (busy()) {
                launchWhenLive = false;
                startService(new Intent(this, BridgeService.class).setAction(BridgeService.STOP));
              } else start(true);
            });
    ui.space(root, 8);
    TextView hint =
        ui.caption(
            "Start connects both mics and opens Camera.\n"
                + "Use Camera’s record button to shoot video.");
    hint.setGravity(Gravity.CENTER);
    root.addView(hint);
    ui.space(root, 12);
    advancedButton =
        button(
            root,
            expanded ? "Hide controls" : "Advanced controls",
            () -> {
              expanded = !expanded;
              advanced.setVisibility(expanded ? View.VISIBLE : View.GONE);
              advancedButton.setText(expanded ? "Hide controls" : "Advanced controls");
            });
    advanced = new LinearLayout(this);
    advanced.setOrientation(LinearLayout.VERTICAL);
    advanced.setVisibility(expanded ? View.VISIBLE : View.GONE);
    root.addView(advanced);
    mic(advanced, true);
    mic(advanced, false);
    CheckBox swap = new CheckBox(this);
    swap.setText("Swap left and right output");
    ui.controls(swap);
    swap.setChecked(settings.flag("swap"));
    swap.setOnCheckedChangeListener((b, v) -> settings.flag("swap", v));
    advanced.addView(swap);
    record = button(advanced, "Record audio preview", () -> start(false));
    stop =
        button(
            advanced,
            "Stop and save preview",
            () ->
                startService(new Intent(this, BridgeService.class).setAction(BridgeService.STOP)));
    camera = button(advanced, "Open camera", this::openCamera);
    play = button(advanced, "Play last preview", this::play);
    button(advanced, "Camera setup", () -> startActivity(new Intent(this, SetupActivity.class)));
    button(advanced, "Choose camera app", this::chooseCamera);
    diagnostics = ui.caption("");
    ui.space(advanced, 12);
    advanced.addView(diagnostics);
    ui.screen(scroll, root);
    refresh();
  }

  private boolean busy() {
    return BridgeState.phase == BridgeState.Phase.LIVE
        || BridgeState.phase == BridgeState.Phase.CONNECTING
        || BridgeState.phase == BridgeState.Phase.STOPPING;
  }

  private Meter mic(LinearLayout root, boolean left) {
    TextView heading = text(left ? "LEFT MICROPHONE" : "RIGHT MICROPHONE", 13);
    heading.setPadding(0, dp(24), 0, dp(4));
    root.addView(heading);
    button(root, settings.name(left), () -> selectMic(left));
    Meter meter = new Meter(this, left ? ui.accent : ui.blue, ui.border);

    TextView gain = text("Gain " + Math.round(settings.gain(left) * 100) + "%", 14);
    root.addView(gain);
    SeekBar slider = new SeekBar(this);
    slider.setMinimumHeight(dp(48));
    slider.setMax(200);
    slider.setProgress(Math.round(settings.gain(left) * 100));
    slider.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar s, int p, boolean user) {
            if (user) settings.gain(left, p / 100f);
            gain.setText("Gain " + p + "%");
          }

          public void onStartTrackingTouch(SeekBar s) {}

          public void onStopTrackingTouch(SeekBar s) {}
        });
    slider.setProgressTintList(android.content.res.ColorStateList.valueOf(ui.accent));
    slider.setThumbTintList(android.content.res.ColorStateList.valueOf(ui.accent));
    root.addView(slider);
    CheckBox mute = new CheckBox(this);
    mute.setText("Mute " + (left ? "left" : "right"));
    ui.controls(mute);
    mute.setChecked(settings.flag(left ? "mute_left" : "mute_right"));
    mute.setOnCheckedChangeListener((b, v) -> settings.flag(left ? "mute_left" : "mute_right", v));
    root.addView(mute);
    return meter;
  }

  private void selectMic(boolean left) {
    if (BridgeState.phase != BridgeState.Phase.STOPPED
        && BridgeState.phase != BridgeState.Phase.ERROR) {
      Toast.makeText(this, "Stop before changing microphones", Toast.LENGTH_SHORT).show();
      return;
    }
    if (checkSelfPermission("android.permission.BLUETOOTH_CONNECT")
        != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {"android.permission.BLUETOOTH_CONNECT"}, 2);
      return;
    }
    BluetoothAdapter adapter = getSystemService(BluetoothManager.class).getAdapter();
    List<BluetoothDevice> devices = new ArrayList<>();
    if (adapter != null)
      for (BluetoothDevice d : adapter.getBondedDevices())
        if (d.getName() != null && d.getName().startsWith("DJI Mic Mini")) devices.add(d);
    if (devices.isEmpty()) {
      new AlertDialog.Builder(this)
          .setMessage("Pair your DJI Mic Mini transmitters in Android Bluetooth settings first.")
          .setPositiveButton(
              "Open Bluetooth",
              (d, w) ->
                  startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)))
          .setNegativeButton("Close", null)
          .show();
      return;
    }
    String[] labels =
        devices.stream().map(d -> d.getName() + "\n" + d.getAddress()).toArray(String[]::new);
    new AlertDialog.Builder(this)
        .setTitle(left ? "Choose left microphone" : "Choose right microphone")
        .setItems(
            labels,
            (dialog, n) -> {
              BluetoothDevice d = devices.get(n);
              settings.microphone(left, d.getAddress(), d.getName());
              build();
            })
        .show();
  }

  private void start(boolean routed) {
    pendingCamera = routed;
    List<String> permissions = new ArrayList<>();
    for (String p :
        new String[] {
          "android.permission.BLUETOOTH_CONNECT",
          "android.permission.RECORD_AUDIO",
          "android.permission.POST_NOTIFICATIONS"
        })
      if ((Build.VERSION.SDK_INT >= 33 || !p.endsWith("POST_NOTIFICATIONS"))
          && checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED
          && (!p.endsWith("POST_NOTIFICATIONS") || !settings.flag("notification_asked")))
        permissions.add(p);
    if (!permissions.isEmpty()) {
      settings.flag("notification_asked", true);
      requestPermissions(permissions.toArray(new String[0]), 1);
      return;
    }
    if (routed
        && Build.VERSION.SDK_INT >= 33
        && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
            != PackageManager.PERMISSION_GRANTED
        && !settings.flag("notification_warning_seen")) {
      settings.flag("notification_warning_seen", true);
      new AlertDialog.Builder(this)
          .setTitle("Keep connection alerts visible")
          .setMessage(
              "Allow notifications so you can see microphone failures while Camera is open. Without"
                  + " them, check Stereo Bridge to confirm the connection is still live.")
          .setPositiveButton(
              "Enable alerts",
              (d, w) ->
                  startActivity(
                      new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                          .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName())))
          .setNegativeButton("Continue", (d, w) -> start(true))
          .show();
      return;
    }
    if (settings.address(true).isEmpty() || settings.address(false).isEmpty()) {
      BluetoothAdapter adapter = getSystemService(BluetoothManager.class).getAdapter();
      List<BluetoothDevice> found = new ArrayList<>();
      if (adapter != null)
        for (BluetoothDevice device : adapter.getBondedDevices())
          if (device.getName() != null && device.getName().startsWith("DJI Mic Mini"))
            found.add(device);
      found.sort(Comparator.comparing(BluetoothDevice::getAddress));
      if (found.size() == 2
          && settings.address(true).isEmpty()
          && settings.address(false).isEmpty()) {
        for (int i = 0; i < 2; i++)
          settings.microphone(i == 0, found.get(i).getAddress(), found.get(i).getName());
      } else {
        new AlertDialog.Builder(this)
            .setMessage(
                "Pair both DJI Mic Mini microphones in Android Bluetooth settings. If you have more"
                    + " than two, choose left and right in Advanced.")
            .setPositiveButton(
                "Open Bluetooth",
                (d, w) ->
                    startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)))
            .setNegativeButton("Close", null)
            .show();
        return;
      }
    }
    if (routed && (!settings.flag("adb_paired") || settings.get("adb_port", "").isEmpty())) {
      startActivityForResult(new Intent(this, SetupActivity.class), 3);
      return;
    }
    stopPlayback();
    launchWhenLive = routed;
    startForegroundService(new Intent(this, BridgeService.class).putExtra("camera", routed));
  }

  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 3 && result == RESULT_OK) start(true);
  }

  public void onRequestPermissionsResult(int r, String[] p, int[] g) {
    super.onRequestPermissionsResult(r, p, g);
    if (r == 1 && g.length > 0) {
      for (int i = 0; i < g.length; i++)
        if (g[i] != PackageManager.PERMISSION_GRANTED && !p[i].endsWith("POST_NOTIFICATIONS")) {
          Toast.makeText(
                  this, "Allow microphone and Bluetooth access to record", Toast.LENGTH_SHORT)
              .show();
          return;
        }
      start(pendingCamera);
    }
  }

  private void openCamera() {
    Intent video = new Intent("android.media.action.VIDEO_CAMERA").setPackage(settings.camera());
    if (video.resolveActivity(getPackageManager()) != null) {
      startActivity(video);
      return;
    }
    Intent launch = getPackageManager().getLaunchIntentForPackage(settings.camera());
    if (launch == null) {
      Toast.makeText(this, "Choose an installed camera app in Advanced", Toast.LENGTH_SHORT).show();
      return;
    }
    startActivity(launch);
  }

  private void chooseCamera() {
    if (busy()) {
      Toast.makeText(this, "Stop before changing the camera app", Toast.LENGTH_SHORT).show();
      return;
    }
    List<ResolveInfo> list =
        getPackageManager()
            .queryIntentActivities(new Intent("android.media.action.VIDEO_CAMERA"), 0);
    if (list.isEmpty()) {
      Toast.makeText(this, "No video camera found", Toast.LENGTH_SHORT).show();
      return;
    }
    String[] names =
        list.stream().map(r -> r.loadLabel(getPackageManager()).toString()).toArray(String[]::new);
    new AlertDialog.Builder(this)
        .setTitle("Camera app")
        .setItems(names, (d, n) -> settings.set("camera", list.get(n).activityInfo.packageName))
        .show();
  }

  private void play() {
    try {
      stopPlayback();
      player = new MediaPlayer();
      player.setAudioAttributes(
          new AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build());
      player.setDataSource(new File(getFilesDir(), "last-take.wav").getAbsolutePath());
      for (AudioDeviceInfo d :
          getSystemService(AudioManager.class).getDevices(AudioManager.GET_DEVICES_OUTPUTS))
        if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) player.setPreferredDevice(d);
      player.prepare();
      player.setOnCompletionListener(
          m -> {
            stopPlayback();
            refresh();
          });
      player.start();
      refresh();
    } catch (Exception e) {
      stopPlayback();
      new AlertDialog.Builder(this)
          .setMessage("Playback failed: " + e.getMessage())
          .setPositiveButton("Close", null)
          .show();
    }
  }

  private void stopPlayback() {
    if (player != null) {
      player.release();
      player = null;
    }
  }

  private void refresh() {
    boolean busy =
        BridgeState.phase == BridgeState.Phase.LIVE
            || BridgeState.phase == BridgeState.Phase.CONNECTING
            || BridgeState.phase == BridgeState.Phase.STOPPING;
    SessionPresentation presentation =
        SessionPresentation.of(BridgeState.phase, BridgeState.cameraMode);
    status.setText(presentation.title);
    status.setTextColor(BridgeState.phase == BridgeState.Phase.ERROR ? ui.danger : ui.text);
    detail.setText(
        BridgeState.phase == BridgeState.Phase.STOPPED
            ? "Your phone handles the audio. Camera handles the video."
            : BridgeState.phase == BridgeState.Phase.LIVE
                ? (BridgeState.cameraMode
                    ? "Both microphones are live. Record in your camera app."
                    : "Speak into each microphone, then stop and replay.")
                : BridgeState.message);
    long seconds = Math.max(0, (SystemClock.elapsedRealtime() - BridgeState.startedAt) / 1000);
    time.setText(
        BridgeState.phase == BridgeState.Phase.LIVE
            ? String.format(
                Locale.ROOT,
                "%02d:%02d · %s",
                seconds / 60,
                seconds % 60,
                BridgeState.cameraMode ? "Stereo connected" : "Audio preview")
            : player != null ? "Playing through phone speaker" : "");
    time.setVisibility(time.getText().length() == 0 ? View.GONE : View.VISIBLE);
    repair.setVisibility(BridgeState.phase == BridgeState.Phase.ERROR ? View.VISIBLE : View.GONE);
    leftName.setText(
        settings.address(true).isEmpty() ? "Auto-select on Start" : settings.name(true));
    rightName.setText(
        settings.address(false).isEmpty() ? "Auto-select on Start" : settings.name(false));
    leftState.setText(micState(true));
    rightState.setText(micState(false));
    diagnostics.setText(
        "Live input buffer drops: " + BridgeState.droppedFrames + "\nCamera: " + settings.camera());
    record.setEnabled(!busy);
    bridge.setEnabled(presentation.enabled);
    bridge.setText(presentation.action);
    stop.setEnabled(busy && !BridgeState.cameraMode);
    camera.setEnabled(BridgeState.phase == BridgeState.Phase.LIVE && BridgeState.cameraMode);
    play.setEnabled(!busy && new File(getFilesDir(), "last-take.wav").exists());
    leftMeter.level = BridgeState.leftLevel;
    rightMeter.level = BridgeState.rightLevel;
    leftMeter.invalidate();
    rightMeter.invalidate();
    if (launchWhenLive && BridgeState.phase == BridgeState.Phase.LIVE) {
      launchWhenLive = false;
      openCamera();
    }
    if (BridgeState.phase == BridgeState.Phase.ERROR) launchWhenLive = false;
  }

  private String micState(boolean left) {
    if (!busy())
      return settings.address(left).isEmpty()
          ? "Pair both mics in Bluetooth settings"
          : "Selected · connects on Start";
    if (settings.flag(left ? "mute_left" : "mute_right")) return "Muted · no audio sent";
    int level = left ? BridgeState.leftLevel : BridgeState.rightLevel;
    if (level >= 32700) return "Clipping · lower the gain in Advanced";
    return left ? BridgeState.leftStatus : BridgeState.rightStatus;
  }

  private final Runnable tick =
      new Runnable() {
        public void run() {
          refresh();
          handler.postDelayed(this, 200);
        }
      };

  protected void onResume() {
    super.onResume();
    handler.post(tick);
    LocalDiscovery.refresh(this);
  }

  protected void onPause() {
    handler.removeCallbacks(tick);
    super.onPause();
  }

  protected void onDestroy() {
    stopPlayback();
    super.onDestroy();
  }

  private static final class Meter extends View {
    int level, color, background;
    Paint paint = new Paint();

    Meter(Context c, int color, int background) {
      super(c);
      this.color = color;
      this.background = background;
    }

    protected void onDraw(Canvas c) {
      paint.setColor(background);
      c.drawRoundRect(0, 0, getWidth(), getHeight(), 8, 8, paint);
      float width =
          (float) Math.max(0, (20 * Math.log10(Math.max(1, level) / 32768.0) + 60) / 60)
              * getWidth();
      paint.setColor(level >= 32700 ? Color.RED : color);
      c.drawRoundRect(0, 0, Math.min(getWidth(), width), getHeight(), 8, 8, paint);
    }
  }
}
