package app.stereobridge;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Offline camera screen: the primary action records and then saves a stereo video. */
public final class MainActivity extends Activity {
  private Ui ui;
  private BridgeSettings settings;
  private TextureView preview;
  private int transformedWidth=-1,transformedHeight=-1,bufferWidth=-1,bufferHeight=-1,bufferRotation=-1;
  private boolean transformedFront;
  private int transformedDisplay = -1;
  private SurfaceTexture texture;
  private OfflineRecorderService.LocalBinder recorder, previewOwner;
  private boolean bound, resumed, pendingRecord, permissionRequestInFlight;
  private CameraControls controls;
  private FrameLayout previewFrame;
  private LinearLayout topPanel, bottomPanel;
  private TextView status, leftLabel, rightLabel, timer;
  private Button record, last;
  private Meter leftMeter, rightMeter;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ServiceConnection connection =
      new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder binder) {
          recorder = (OfflineRecorderService.LocalBinder) binder;
          attach();
          if (pendingRecord && resumed) permissions(true);
        }

        public void onServiceDisconnected(ComponentName n) {
          recorder = null;
        }
      };

  @Override
  public void onCreate(Bundle saved) {
    Ui.theme(this);
    super.onCreate(saved);
    ui = new Ui(this);
    settings = new BridgeSettings(this);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    build();
  }

  private void build() {
    getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));
    View decor = getWindow().getDecorView();
    if (Build.VERSION.SDK_INT >= 30) {
      getWindow().setDecorFitsSystemWindows(false);
      getWindow().setStatusBarColor(Color.TRANSPARENT);
      getWindow().setNavigationBarColor(Color.TRANSPARENT);
      WindowInsetsController controller = decor.getWindowInsetsController();
      if (controller != null) {
        int mask =
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        controller.setSystemBarsAppearance(ui.dark ? 0 : mask, mask);
      }
    } else {
      int mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
      decor.setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              | (ui.dark ? 0 : mask));
    }
    FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(Color.BLACK);
    preview = new TextureView(this);
    preview.setOpaque(true);
    previewFrame = new FrameLayout(this);
    previewFrame.setBackgroundColor(Color.BLACK);
    previewFrame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
    root.addView(previewFrame, new FrameLayout.LayoutParams(-1, -1));
    preview.setSurfaceTextureListener(
        new TextureView.SurfaceTextureListener() {
          public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            MainActivity.this.texture = texture;
            attach();
            transform(width, height);
          }

          public void onSurfaceTextureSizeChanged(SurfaceTexture t, int w, int h) {
            transform(w, h);
          }

          public boolean onSurfaceTextureDestroyed(SurfaceTexture t) {
            if (previewOwner != null) previewOwner.detachPreview(t, true);
            else t.release();
            previewOwner = null;
            texture = null;
            return false;
          }

          public void onSurfaceTextureUpdated(SurfaceTexture t) {}
        });
    controls =
        new CameraControls(
            this,
            previewFrame,
            ui,
            new CameraControls.Listener() {
              public void onTap(float x, float y) {
                if (recorder == null) return;
                int[] info = recorder.previewInfo();
                boolean front = info.length > 3 && info[3] == 1;
                int sensor = info.length > 4 ? info[4] : 90;
                float[] oriented = FocusPointMapper.sensorPoint(
                    x, y, preview.getWidth(), preview.getHeight(), info[0], info[1], sensor,
                    getWindowManager().getDefaultDisplay().getRotation() * 90, front);
                float[] point = FocusPointMapper.activePoint(oriented[0], oriented[1], sensor);
                recorder.setFocusPoint(point[0], point[1]);
                controls.showFocusRing(x, y, recorder.focusLocked());
              }

              public void onZoom(float ratio) {
                if (recorder != null) recorder.setZoom(ratio);
              }

              public void onFlip() {
                if (recorder == null) return;
                if (OfflineRecorderService.state.busy()) {
                  Toast.makeText(MainActivity.this, "Stop before switching camera", Toast.LENGTH_SHORT).show();
                  return;
                }
                controls.clearFocusRing();
                recorder.flip();
              }

              public void onTorch() {
                if (recorder != null) recorder.setTorch(!recorder.torch());
              }

              public void onLock() {
                if (recorder != null) recorder.setFocusLock(!recorder.focusLocked());
              }

              public void onMode() {
                if (recorder == null) return;
                if (OfflineRecorderService.state.busy()) {
                  Toast.makeText(MainActivity.this, "Stop before changing video mode", Toast.LENGTH_SHORT).show();
                  return;
                }
                controls.clearFocusRing();
                ModeChooserDialog.show(
                    MainActivity.this,
                    ui,
                    recorder.capabilities(),
                    settings.get("video_mode", ""),
                    settings.flag("video_hdr"),
                    settings.get("video_codec", "avc"),
                    recorder::modeFeasible,
                    recorder::hdrAvailable,
                    recorder::hevcAvailable,
                    (modeKey, hdr, codec) -> recorder.requestMode(modeKey, hdr, codec));
              }
            });
    topPanel = new LinearLayout(this);
    topPanel.setOrientation(LinearLayout.VERTICAL);
    topPanel.setBackground(ui.shape(ui.dark ? 0xCC171F2B : 0xE6FFFFFF, 18, false));
    LinearLayout titleRow = new LinearLayout(this);
    titleRow.setOrientation(LinearLayout.HORIZONTAL);
    titleRow.setGravity(Gravity.CENTER_VERTICAL);
    TextView title = ui.text("STEREO BRIDGE", 11, true);
    title.setLetterSpacing(0.12f);
    title.setTextColor(ui.secondary);
    titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    timer = ui.caption("");
    timer.setGravity(Gravity.END);
    timer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    titleRow.addView(timer);
    topPanel.addView(titleRow);
    status = ui.text("Ready to record", 17, true);
    LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
    statusParams.topMargin = ui.dp(2);
    topPanel.addView(status, statusParams);
    topPanel.setPadding(ui.dp(18), ui.dp(10), ui.dp(18), ui.dp(10));
    FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
    topParams.setMargins(ui.dp(12), 0, ui.dp(12), 0);
    root.addView(topPanel, topParams);

    bottomPanel = new LinearLayout(this);
    bottomPanel.setOrientation(LinearLayout.VERTICAL);
    bottomPanel.setBackground(ui.shape(ui.dark ? 0xE6171F2B : 0xE6FFFFFF, 22, false));
    bottomPanel.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
    LinearLayout actions = new LinearLayout(this);
    actions.setOrientation(LinearLayout.HORIZONTAL);
    last =
        ui.button(
            actions,
            "Open last video",
            false,
            () -> {
              String uri = OfflineRecorderService.state.savedUri();
              if (!uri.isEmpty())
                try {
                  startActivity(
                      new Intent(Intent.ACTION_VIEW)
                          .setDataAndType(android.net.Uri.parse(uri), "video/mp4")
                          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                  Toast.makeText(
                          this, "Find your video in Movies / Stereo Bridge", Toast.LENGTH_LONG)
                      .show();
                }
            });
    compact(last);
    compact(ui.button(actions, "More", false, this::showMore));
    bottomPanel.addView(actions);

    LinearLayout mics = new LinearLayout(this);
    mics.setOrientation(LinearLayout.VERTICAL);
    mics.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));
    mics.setBackground(ui.shape(ui.dark ? 0xCC0C1118 : 0xCCF3F5F8, 16, false));
    leftLabel = ui.caption("");
    leftMeter = new Meter(ui.accent);
    mics.addView(micRow(leftLabel, leftMeter));
    ui.space(mics, 8);
    rightLabel = ui.caption("");
    rightMeter = new Meter(ui.blue);
    mics.addView(micRow(rightLabel, rightMeter));
    LinearLayout.LayoutParams micsParams = new LinearLayout.LayoutParams(-1, -2);
    micsParams.topMargin = ui.dp(6);
    bottomPanel.addView(mics, micsParams);

    record = ui.button(bottomPanel, "Record", true, this::recordOrStop);
    TextView hint =
        ui.caption(
            "Tap Record to connect both mics and start video. Tap Stop to save."
                + " Capture continues with the screen asleep or another app open.");
    hint.setGravity(Gravity.CENTER);
    hint.setTextSize(13);
    bottomPanel.addView(hint);

    FrameLayout.LayoutParams bottomParams =
        new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
    bottomParams.setMargins(ui.dp(12), 0, ui.dp(12), 0);
    root.addView(bottomPanel, bottomParams);

    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          int[] bars = systemBars(insets);
          topPanel.setPadding(
              ui.dp(18), bars[1] + ui.dp(10), ui.dp(18), ui.dp(10));
          bottomPanel.setPadding(
              ui.dp(16), ui.dp(12), ui.dp(16), bars[3] + ui.dp(12));
          updateControlClearance();
          return insets;
        });
    View.OnLayoutChangeListener clearance =
        (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
            updateControlClearance();
    topPanel.addOnLayoutChangeListener(clearance);
    bottomPanel.addOnLayoutChangeListener(clearance);

    setContentView(root);
    root.requestApplyInsets();
    refresh();
  }

  private void compact(Button button) {
    button.setTextSize(14);
    button.setMinHeight(ui.dp(40));
    button.setMinimumHeight(ui.dp(40));
    button.setPadding(ui.dp(10), 0, ui.dp(10), 0);
    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) button.getLayoutParams();
    params.width = 0;
    params.weight = 1;
    params.topMargin = 0;
    params.bottomMargin = 0;
    params.setMarginStart(ui.dp(4));
    params.setMarginEnd(ui.dp(4));
  }

  private View micRow(TextView label, Meter meter) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    LinearLayout.LayoutParams meterParams = new LinearLayout.LayoutParams(0, ui.dp(10), 1);
    meterParams.leftMargin = ui.dp(12);
    row.addView(meter, meterParams);
    return row;
  }

  private void updateControlClearance() {
    if (controls == null || topPanel.getHeight() == 0 || bottomPanel.getHeight() == 0) return;
    controls.setVerticalInsets(topPanel.getHeight(), bottomPanel.getHeight() + ui.dp(8));
  }

  private static int[] systemBars(WindowInsets insets) {
    if (Build.VERSION.SDK_INT >= 30) {
      Insets bars =
          insets.getInsets(
              WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
      return new int[] {bars.left, bars.top, bars.right, bars.bottom};
    }
    DisplayCutout cutout = insets.getDisplayCutout();
    return new int[] {
      Math.max(insets.getSystemWindowInsetLeft(), cutout == null ? 0 : cutout.getSafeInsetLeft()),
      Math.max(insets.getSystemWindowInsetTop(), cutout == null ? 0 : cutout.getSafeInsetTop()),
      Math.max(insets.getSystemWindowInsetRight(), cutout == null ? 0 : cutout.getSafeInsetRight()),
      Math.max(insets.getSystemWindowInsetBottom(), cutout == null ? 0 : cutout.getSafeInsetBottom())
    };
  }

  private void showMore() {
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(8));
    content.addView(ui.text("Advanced mic controls", 16, true));
    microphone(content, true);
    microphone(content, false);
    CheckBox swap = new CheckBox(this);
    swap.setText("Swap left and right");
    ui.controls(swap);
    swap.setChecked(settings.flag("swap"));
    swap.setOnCheckedChangeListener((b, v) -> settings.flag("swap", v));
    content.addView(swap);
    ui.button(
        content,
        "Pair microphones",
        false,
        () -> startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
    content.addView(
        ui.caption(
            "First use: pair two original DJI Mic Mini transmitters and allow camera"
                + " and nearby devices access (Android 12 or later). Your choices are saved after reboot. Wi-Fi and"
                + " debugging are not needed. Other microphone models are unsupported."));
    boolean notificationsOff =
        !getSystemService(NotificationManager.class).areNotificationsEnabled();
    if (notificationsOff) {
      content.addView(
          ui.caption(
              "Notifications are off. Capture can continue in the background; return here to"
                  + " Stop. Enable notifications for a visible Stop action."));
      ui.button(
          content,
          "Notification settings",
          false,
          () ->
              startActivity(
                  new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                      .putExtra(
                          android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName())));
    }
    boolean denied =
        checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            || !bluetoothGranted();
    if (denied)
      ui.button(content, "Open app permission settings", false, this::openAppSettings);
    ui.button(content, "Privacy policy", false, this::showPrivacy);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(content);
    new AlertDialog.Builder(this)
        .setTitle("More")
        .setView(scroll)
        .setPositiveButton("Close", null)
        .show();
  }

  private void transform(int width, int height) {
    if (width <= 0 || height <= 0) return;
    int displayDegrees = getWindowManager().getDefaultDisplay().getRotation() * 90;
    if (recorder != null) recorder.setDisplayRotation(displayDegrees);
    int[] info = recorder == null ? new int[] {1920, 1080, 90} : recorder.previewInfo();
    boolean front = info.length > 3 && info[3] == 1;
    int sensor = info.length > 4 ? info[4] : 90;
    if(width==transformedWidth&&height==transformedHeight&&info[0]==bufferWidth&&info[1]==bufferHeight&&sensor==bufferRotation&&displayDegrees==transformedDisplay&&front==transformedFront)return;
    transformedFront=front;transformedDisplay=displayDegrees;
    transformedWidth=width;transformedHeight=height;bufferWidth=info[0];bufferHeight=info[1];bufferRotation=sensor;
    float[] source = {0,0,width,0,width,height,0,height};
    float[] target = CameraGeometry.previewCorners(width,height,info[0],info[1],sensor,displayDegrees);
    Matrix matrix = new Matrix();
    matrix.setPolyToPoly(source, 0, target, 0, 4);
    preview.setTransform(matrix);
  }

  private void microphone(LinearLayout parent, boolean left) {
    ui.button(
        parent,
        left ? "Choose left microphone" : "Choose right microphone",
        false,
        () -> select(left));
    TextView gain = ui.caption("Gain " + Math.round(settings.gain(left) * 100) + "%");
    parent.addView(gain);
    SeekBar slider = new SeekBar(this);
    slider.setMax(200);
    slider.setProgress(Math.round(settings.gain(left) * 100));
    slider.setMinimumHeight(ui.dp(48));
    slider.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar s, int p, boolean user) {
            if (user) settings.gain(left, p / 100f);
            gain.setText("Gain " + p + "%");
          }

          public void onStartTrackingTouch(SeekBar s) {}

          public void onStopTrackingTouch(SeekBar s) {}
        });
    parent.addView(slider);
    CheckBox mute = new CheckBox(this);
    mute.setText(left ? "Mute left" : "Mute right");
    ui.controls(mute);
    mute.setChecked(settings.flag(left ? "mute_left" : "mute_right"));
    mute.setOnCheckedChangeListener((b, v) -> settings.flag(left ? "mute_left" : "mute_right", v));
    parent.addView(mute);
  }

  private boolean bluetoothGranted() {
    return Build.VERSION.SDK_INT < 31
        || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
  }

  private void openAppSettings() {
    startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .setData(android.net.Uri.parse("package:" + getPackageName())));
  }

  private void showPrivacy() {
    try (java.io.InputStream input = getAssets().open("privacy.md")) {
      java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
      byte[] chunk = new byte[4096];
      for (int count; (count = input.read(chunk)) != -1;) bytes.write(chunk, 0, count);
      String policy = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8)
          .replaceAll("(?m)^#+\\s+", "");
      TextView text = ui.text(policy, 16, false);
      text.setTextIsSelectable(true);
      text.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12));
      ScrollView scroll = new ScrollView(this);
      scroll.addView(text);
      new AlertDialog.Builder(this).setTitle("Privacy policy").setView(scroll)
          .setPositiveButton("Close", null).show();
    } catch (java.io.IOException e) {
      Toast.makeText(this, "Privacy policy could not be opened", Toast.LENGTH_LONG).show();
    }
  }

  private void select(boolean left) {
    if (OfflineRecorderService.state.busy()) {
      Toast.makeText(this, "Stop before changing microphones", Toast.LENGTH_SHORT).show();
      return;
    }
    if (!bluetoothGranted()) {
      if (settings.flag("denied_" + Manifest.permission.BLUETOOTH_CONNECT)
          && !shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)) {
        openAppSettings();
        return;
      }
      settings.flag("asked_" + Manifest.permission.BLUETOOTH_CONNECT, true);
      requestPermissions(new String[] {Manifest.permission.BLUETOOTH_CONNECT}, 2);
      return;
    }
    List<BluetoothDevice> devices = devices();
    if (devices.isEmpty()) {
      pairingHelp();
      return;
    }
    String[] labels = devices.stream().map(this::deviceName).toArray(String[]::new);
    new AlertDialog.Builder(this)
        .setTitle(left ? "Left microphone" : "Right microphone")
        .setItems(
            labels,
            (dialog, i) -> {
              BluetoothDevice d = devices.get(i);
              settings.microphone(left, d.getAddress(), deviceName(d));
              refresh();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private String deviceName(BluetoothDevice device) {
    try { return device.getName(); }
    catch (SecurityException denied) { return "Bluetooth access needed"; }
  }

  private List<BluetoothDevice> devices() {
    if (!bluetoothGranted()) return Collections.emptyList();
    List<BluetoothDevice> found = new ArrayList<>();
    BluetoothManager manager = getSystemService(BluetoothManager.class);
    BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
    try {
    if (adapter != null)
      for (BluetoothDevice d : adapter.getBondedDevices())
        if (d.getName() != null
            && d.getName().startsWith("DJI Mic Mini")
            && !d.getName().startsWith("DJI Mic Mini 2")) found.add(d);
    } catch (SecurityException denied) {
      OfflineRecorderService.state.fail("Allow nearby devices access in app settings.");
      return Collections.emptyList();
    }
    found.sort(Comparator.comparing(BluetoothDevice::getAddress));
    return found;
  }

  private void pairingHelp() {
    new AlertDialog.Builder(this)
        .setTitle("Pair your two microphones")
        .setMessage(
            "Pair both original DJI Mic Mini transmitters in Android Bluetooth settings. Then"
                + " return and tap Record. If more than two are paired, choose left and right in"
                + " Advanced.")
        .setPositiveButton(
            "Open Bluetooth",
            (d, w) ->
                startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
        .setNegativeButton("Close", null)
        .show();
  }

  private void recordOrStop() {
    if (!OfflineRecorderService.state.busy() && OfflineRecorderService.needsRecovery) {
      startService(new Intent(this, OfflineRecorderService.class).setAction(OfflineRecorderService.RECOVER));
      return;
    }
    if (OfflineRecorderService.state.busy()) {
      if (OfflineRecorderService.state.phase() != OfflineSessionState.Phase.SAVING)
        startService(
            new Intent(this, OfflineRecorderService.class).setAction(OfflineRecorderService.STOP));
      return;
    }
    pendingRecord = true;
    permissions(true);
  }

  private void permissions(boolean recording) {
    if (permissionRequestInFlight) return;
    List<String> missing = new ArrayList<>();
    List<String> required = new ArrayList<>();
    required.add(Manifest.permission.CAMERA);
    if (Build.VERSION.SDK_INT >= 31) required.add(Manifest.permission.BLUETOOTH_CONNECT);
    for (String p : required) {
      if (checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED && settings.flag("denied_" + p))
        settings.flag("denied_" + p, false);
    }
    for (String p : required)
      if ((!p.equals(Manifest.permission.BLUETOOTH_CONNECT) || Build.VERSION.SDK_INT >= 31)
          && checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED
          && (recording || p.equals(Manifest.permission.CAMERA))) {
        if (!PermissionPromptPolicy.shouldRequest(recording, settings.flag("denied_" + p), shouldShowRequestPermissionRationale(p))) {
          pendingRecord = false;
          OfflineRecorderService.state.fail("Allow camera and nearby devices access in app settings.");
          refresh();
          return;
        }
        missing.add(p);
      }
    if (recording
        && Build.VERSION.SDK_INT >= 33
        && !settings.flag("notification_asked")
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
      settings.flag("notification_asked", true);
      missing.add(Manifest.permission.POST_NOTIFICATIONS);
    }
    if (!missing.isEmpty()) {
      for (String p : missing) settings.flag("asked_" + p, true);
      permissionRequestInFlight = true;
      requestPermissions(missing.toArray(new String[0]), 1);
      return;
    }
    if (!recording && bluetoothGranted()
        && OfflineRecorderService.state.message().startsWith("Allow camera and nearby"))
      OfflineRecorderService.state.idle();
    attach();
    if (recording) startRecording();
  }

  private void startRecording() {
    if (!resumed) { pendingRecord = true; return; }
    boolean synthetic = BuildConfig.DEBUG && getIntent().getBooleanExtra("synthetic", false);
    if (!synthetic
        && !bluetoothGranted()) {
      pendingRecord = false;
      return;
    }
    if (!synthetic && (settings.address(true).isEmpty() || settings.address(false).isEmpty())) {
      List<BluetoothDevice> found = devices();
      if (found.size() == 2
          && settings.address(true).isEmpty()
          && settings.address(false).isEmpty()) {
        for (int n = 0; n < 2; n++)
          settings.microphone(n == 0, found.get(n).getAddress(), deviceName(found.get(n)));
      } else {
        pairingHelp();
        return;
      }
    }
    if (recorder == null) {
      pendingRecord = true;
      return;
    }
    pendingRecord = false;
    try {
      startForegroundService(
          new Intent(this, OfflineRecorderService.class)
              .setAction(OfflineRecorderService.START)
              .putExtra("synthetic", synthetic));
    } catch (RuntimeException e) {
      OfflineRecorderService.state.fail("Could not start capture. Keep the app open and tap Record again.");
      refresh();
    }
  }

  private void attach() {
    if (recorder != null
        && texture != null
        && resumed
        && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
      previewOwner = recorder;
      recorder.attachPreview(texture);
    }
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request == 1 || request == 2) {
      permissionRequestInFlight = false;
      boolean requiredGranted = true;
      for (int n = 0; n < permissions.length; n++) {
        boolean granted = n < results.length && results[n] == PackageManager.PERMISSION_GRANTED;
        if (!permissions[n].equals(Manifest.permission.POST_NOTIFICATIONS)) {
          settings.flag("denied_" + permissions[n], !granted);
          if (!granted) requiredGranted = false;
        }
      }
      if (requiredGranted && request == 1 && resumed) permissions(pendingRecord);
      else if (!requiredGranted) {
        pendingRecord = false;
        OfflineRecorderService.state.fail(
            "Allow camera and nearby devices access to record. Use app settings if access was blocked.");
      }
    }
  }

  private static void textIfChanged(TextView view,String text){if(!view.getText().toString().equals(text))view.setText(text);}

  private void refresh() {
    transform(preview.getWidth(), preview.getHeight());
    OfflineSessionState s = OfflineRecorderService.state;
    textIfChanged(status,s.message());
    int statusColor=s.phase()==OfflineSessionState.Phase.ERROR?ui.danger:ui.text;
    if(status.getCurrentTextColor()!=statusColor)status.setTextColor(statusColor);
    textIfChanged(leftLabel,
        "L · "
            + (settings.address(true).isEmpty() ? "Auto-select on Record" : settings.name(true)));
    textIfChanged(rightLabel,
        "R · "
            + (settings.address(false).isEmpty() ? "Auto-select on Record" : settings.name(false)));
    if(leftMeter.level!=OfflineRecorderService.leftLevel){leftMeter.level=OfflineRecorderService.leftLevel;leftMeter.invalidate();}
    if(rightMeter.level!=OfflineRecorderService.rightLevel){rightMeter.level=OfflineRecorderService.rightLevel;rightMeter.invalidate();}
    String action =
        s.phase() == OfflineSessionState.Phase.SAVING
            ? "Saving…"
            : s.phase() == OfflineSessionState.Phase.CONNECTING
                ? "Cancel"
                : s.phase() == OfflineSessionState.Phase.RECORDING ? "Stop" : "Record";
    if (!s.busy() && OfflineRecorderService.needsRecovery) action = "Retry save";
    textIfChanged(record,action);
    record.setEnabled(s.phase() != OfflineSessionState.Phase.SAVING);
    long seconds =
        Math.max(0, (SystemClock.elapsedRealtime() - OfflineRecorderService.startedAt) / 1000);
    textIfChanged(timer,
        s.phase() == OfflineSessionState.Phase.RECORDING
            ? String.format(Locale.ROOT, "%02d:%02d · Stereo recording", seconds / 60, seconds % 60)
            : "");
    boolean idle = !s.busy();
    if (recorder != null)
      controls.refresh(recorder.capabilities(), recorder.modeLabel(), recorder.zoom(), recorder.torch(), idle, recorder.focusLocked());
    else controls.refresh(null, "", 1f, false, idle, false);
    last.setEnabled(!s.busy() && !s.savedUri().isEmpty());
  }

  private final Runnable tick =
      new Runnable() {
        public void run() {
          refresh();
          handler.postDelayed(this, 200);
        }
      };

  @Override
  protected void onStart() {
    super.onStart();
    bound =
        bindService(
            new Intent(this, OfflineRecorderService.class), connection, Context.BIND_AUTO_CREATE);
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    handler.post(tick);
    permissions(pendingRecord);
  }

  @Override
  protected void onPause() {
    resumed = false;
    handler.removeCallbacks(tick);
    if (recorder != null) recorder.detachPreview(texture, false);
    super.onPause();
  }

  @Override
  protected void onStop() {
    if (bound) {
      unbindService(connection);
      bound = false;
      recorder = null;
    }
    super.onStop();
  }

  private final class Meter extends View {
    int level;
    final int color;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    Meter(int color) {
      super(MainActivity.this);
      this.color = color;
      setContentDescription(
          color == ui.accent ? "Left microphone level" : "Right microphone level");
    }

    protected void onDraw(Canvas c) {
      paint.setColor(ui.border);
      c.drawRoundRect(0, 0, getWidth(), getHeight(), 8, 8, paint);
      paint.setColor(color);
      c.drawRoundRect(0, 0, getWidth() * Math.min(1, level / 32768f), getHeight(), 8, 8, paint);
    }
  }
}
