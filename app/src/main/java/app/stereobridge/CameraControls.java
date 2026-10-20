package app.stereobridge;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Preview overlay: tap focus, pinch zoom, double-tap flip, and status chips. */
final class CameraControls {
  interface Listener {
    void onTap(float x, float y);
    void onZoom(float ratio);
    void onFlip();
    void onTorch();
    void onMode();
    void onLock();
  }

  final TextView modeChip, zoomChip, torchChip, flipChip, lockChip;
  private final FocusRing ring;
  private final GestureDetector gestures;
  private final ScaleGestureDetector scale;
  private final LinearLayout actions;
  private final FrameLayout.LayoutParams modeParams, actionsParams, lockParams, zoomParams;
  private final int pad, bottomGap;
  private int safeLeft = -1, safeTop = -1, safeRight = -1, safeBottom = -1;
  private float currentZoom = 1f;
  private boolean scaling;
  private final Listener listener;
  private float lastZoom = -1, lastAlpha = -1;
  private String lastZoomText;
  private boolean lastZoomEnabled = true;
  private int lastModeVisibility = -1, lastZoomVisibility = -1, lastTorchVisibility = -1,
      lastLockVisibility = -1, lastFlipVisibility = -1;

  CameraControls(Activity activity, FrameLayout frame, Ui ui, Listener listener) {
    this.listener = listener;
    pad = ui.dp(16);
    bottomGap = ui.dp(12);
    modeChip = chip(activity, ui, 13);
    zoomChip = chip(activity, ui, 13);
    torchChip = chip(activity, ui, 13);
    flipChip = chip(activity, ui, 13);
    lockChip = chip(activity, ui, 13);
    flipChip.setText("Flip");
    modeChip.setContentDescription("Video mode");
    flipChip.setContentDescription("Switch camera");
    torchChip.setContentDescription("Torch");
    zoomChip.setContentDescription("Reset zoom");
    lockChip.setContentDescription("Lock exposure and focus");
    ring = new FocusRing(activity, ui.border, ui.accent, ui.dp(24), ui.dp(2));
    frame.addView(ring, new FrameLayout.LayoutParams(-1, -1));
    modeParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
    modeParams.setMargins(pad, pad, pad, pad);
    frame.addView(modeChip, modeParams);
    actions = new LinearLayout(activity);
    actions.setOrientation(LinearLayout.HORIZONTAL);
    actions.setClickable(true);
    actions.setFocusable(false);
    actions.addView(flipChip, new LinearLayout.LayoutParams(-2, -2));
    LinearLayout.LayoutParams torchParams = new LinearLayout.LayoutParams(-2, -2);
    torchParams.leftMargin = ui.dp(8);
    actions.addView(torchChip, torchParams);
    actionsParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
    actionsParams.setMargins(pad, pad, pad, pad);
    frame.addView(actions, actionsParams);
    boolean landscape =
        activity.getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_LANDSCAPE;
    int clearance = ui.dp(landscape ? 170 : 280);
    lockParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
    lockParams.setMargins(pad, pad, pad, clearance);
    frame.addView(lockChip, lockParams);
    zoomParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
    zoomParams.setMargins(pad, pad, pad, clearance);
    frame.addView(zoomChip, zoomParams);
    modeChip.setOnClickListener(v -> listener.onMode());
    flipChip.setOnClickListener(v -> listener.onFlip());
    torchChip.setOnClickListener(v -> listener.onTorch());
    lockChip.setOnClickListener(v -> listener.onLock());
    zoomChip.setOnClickListener(v -> {
      currentZoom = 1f;
      listener.onZoom(1f);
    });
    gestures =
        new GestureDetector(
            activity,
            new GestureDetector.SimpleOnGestureListener() {
              @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                ring.show(e.getX(), e.getY(), false);
                listener.onTap(e.getX(), e.getY());
                return true;
              }

              @Override public boolean onDoubleTap(MotionEvent e) {
                listener.onFlip();
                return true;
              }
            });
    scale =
        new ScaleGestureDetector(
            activity,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
              @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                return true;
              }

              @Override public boolean onScale(ScaleGestureDetector detector) {
                currentZoom *= detector.getScaleFactor();
                listener.onZoom(currentZoom);
                return true;
              }
            });
    frame.setOnTouchListener(
        (v, event) -> {
          if (event.getActionMasked() == MotionEvent.ACTION_DOWN) scaling = false;
          scale.onTouchEvent(event);
          if (scale.isInProgress()) {
            scaling = true;
            return true;
          }
          if (!scaling) gestures.onTouchEvent(event);
          return true;
        });
  }

  void refresh(CameraCapabilities caps, String modeLabel, float zoom, boolean torch, boolean idle, boolean focusLocked) {
    String modeText = modeLabel == null ? "" : modeLabel;
    setTextIfChanged(modeChip, modeText);
    int modeVisibility = modeLabel == null || modeLabel.isEmpty() ? View.GONE : View.VISIBLE;
    if (modeVisibility != lastModeVisibility) {
      modeChip.setVisibility(modeVisibility);
      lastModeVisibility = modeVisibility;
    }
    float alpha = idle ? 1f : 0.5f;
    if (alpha != lastAlpha) {
      modeChip.setAlpha(alpha);
      flipChip.setAlpha(alpha);
      lastAlpha = alpha;
    }
    currentZoom = zoom;
    if (zoom != lastZoom) {
      lastZoom = zoom;
      lastZoomText = String.format(Locale.ROOT, "%.1fx", zoom);
    }
    int zoomVisibility = zoom <= 1.001f ? View.GONE : View.VISIBLE;
    if (zoomVisibility != lastZoomVisibility) {
      zoomChip.setVisibility(zoomVisibility);
      lastZoomVisibility = zoomVisibility;
    }
    if (zoomVisibility == View.VISIBLE) setTextIfChanged(zoomChip, lastZoomText);
    boolean zoomEnabled = caps != null && caps.zoom;
    if (zoomEnabled != lastZoomEnabled) {
      zoomChip.setEnabled(zoomEnabled);
      lastZoomEnabled = zoomEnabled;
    }
    int torchVisibility = caps != null && caps.flash ? View.VISIBLE : View.GONE;
    if (torchVisibility != lastTorchVisibility) {
      torchChip.setVisibility(torchVisibility);
      lastTorchVisibility = torchVisibility;
    }
    setTextIfChanged(torchChip, torch ? "Torch on" : "Torch");
    if (lastFlipVisibility != View.VISIBLE) {
      flipChip.setVisibility(View.VISIBLE);
      lastFlipVisibility = View.VISIBLE;
    }
    int lockVisibility = caps != null && caps.maxAfRegions > 0 ? View.VISIBLE : View.GONE;
    if (lockVisibility != lastLockVisibility) {
      lockChip.setVisibility(lockVisibility);
      lastLockVisibility = lockVisibility;
    }
    setTextIfChanged(lockChip, focusLocked ? "Locked" : "Lock AE/AWB");
    if (ring.locked != focusLocked) {
      ring.locked = focusLocked;
      ring.invalidate();
    }
  }

  private static void setTextIfChanged(TextView view, String text) {
    if (!view.getText().toString().equals(text)) view.setText(text);
  }

  void showFocusRing(float x, float y, boolean locked) {
    ring.show(x, y, locked);
  }

  void clearFocusRing() {
    ring.clear();
  }

  private static TextView chip(Activity activity, Ui ui, int size) {
    TextView chip = new TextView(activity);
    chip.setTextSize(size);
    chip.setTextColor(ui.text);
    chip.setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6));
    chip.setBackground(ui.shape(ui.dark ? 0xCC171F2B : 0xE6FFFFFF, 14, true));
    chip.setClickable(true);
    chip.setMinHeight(ui.dp(40));
    return chip;
  }

  void setSafeInsets(int left, int top, int right, int bottom) {
    if (left == safeLeft && top == safeTop && right == safeRight && bottom == safeBottom) return;
    safeLeft = left;
    safeTop = top;
    safeRight = right;
    safeBottom = bottom;
    modeParams.leftMargin = pad + left;
    modeParams.topMargin = pad + top;
    actionsParams.rightMargin = pad + right;
    actionsParams.topMargin = pad + top;
    lockParams.leftMargin = pad + left;
    lockParams.bottomMargin = bottom + bottomGap;
    zoomParams.rightMargin = pad + right;
    zoomParams.bottomMargin = bottom + bottomGap;
    modeChip.setLayoutParams(modeParams);
    actions.setLayoutParams(actionsParams);
    lockChip.setLayoutParams(lockParams);
    zoomChip.setLayoutParams(zoomParams);
  }

  private static final class FocusRing extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int border, accent;
    private final float radius;
    private float x = -1, y = -1;
    boolean locked;

    FocusRing(Activity activity, int border, int accent, int radius, int stroke) {
      super(activity);
      this.border = border;
      this.accent = accent;
      this.radius = radius;
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(stroke);
    }

    protected void onDraw(Canvas canvas) {
      if (x < 0) return;
      paint.setColor(locked ? accent : border);
      canvas.drawCircle(x, y, radius, paint);
    }

    void show(float x, float y, boolean locked) {
      this.x = x;
      this.y = y;
      this.locked = locked;
      invalidate();
    }

    void clear() {
      x = -1;
      invalidate();
    }
  }
}
