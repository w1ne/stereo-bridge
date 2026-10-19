package app.stereobridge;

import android.app.Activity;
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
  private float currentZoom = 1f;
  private final Listener listener;

  CameraControls(Activity activity, FrameLayout frame, Ui ui, Listener listener) {
    this.listener = listener;
    modeChip = chip(activity, ui, 13);
    zoomChip = chip(activity, ui, 13);
    torchChip = chip(activity, ui, 13);
    flipChip = chip(activity, ui, 13);
    lockChip = chip(activity, ui, 13);
    ring = new FocusRing(activity, ui.border, ui.accent);
    frame.addView(ring, new FrameLayout.LayoutParams(-1, -1));
    addChip(frame, modeChip, Gravity.TOP | Gravity.START);
    addChip(frame, flipChip, Gravity.TOP | Gravity.END);
    addChip(frame, torchChip, Gravity.BOTTOM | Gravity.START);
    addChip(frame, zoomChip, Gravity.BOTTOM | Gravity.END);
    addChip(frame, lockChip, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
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
          scale.onTouchEvent(event);
          if (!scale.isInProgress()) gestures.onTouchEvent(event);
          return true;
        });
  }

  void refresh(CameraCapabilities caps, String modeLabel, float zoom, boolean torch, boolean idle, boolean focusLocked) {
    modeChip.setText(modeLabel == null ? "" : modeLabel);
    modeChip.setVisibility(modeLabel == null || modeLabel.isEmpty() ? View.GONE : View.VISIBLE);
    modeChip.setAlpha(idle ? 1f : 0.5f);
    currentZoom = zoom;
    if (zoom <= 1.001f) zoomChip.setVisibility(View.GONE);
    else {
      zoomChip.setText(String.format(Locale.ROOT, "%.1fx", zoom));
      zoomChip.setVisibility(View.VISIBLE);
    }
    zoomChip.setEnabled(caps != null && caps.zoom);
    torchChip.setVisibility(caps != null && caps.flash ? View.VISIBLE : View.GONE);
    torchChip.setText(torch ? "Torch on" : "Torch");
    flipChip.setVisibility(View.VISIBLE);
    flipChip.setAlpha(idle ? 1f : 0.5f);
    lockChip.setVisibility(caps != null && caps.maxAfRegions > 0 ? View.VISIBLE : View.GONE);
    lockChip.setText(focusLocked ? "Locked" : "Lock AE/AWB");
    ring.locked = focusLocked;
    ring.invalidate();
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
    return chip;
  }

  private void addChip(FrameLayout frame, TextView chip, int gravity) {
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, gravity);
    params.setMargins(16, 16, 16, 16);
    frame.addView(chip, params);
  }

  private static final class FocusRing extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int border, accent;
    private float x = -1, y = -1;
    boolean locked;

    FocusRing(Activity activity, int border, int accent) {
      super(activity);
      this.border = border;
      this.accent = accent;
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(4f);
    }

    protected void onDraw(Canvas canvas) {
      if (x < 0) return;
      paint.setColor(locked ? accent : border);
      canvas.drawCircle(x, y, 40f, paint);
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
