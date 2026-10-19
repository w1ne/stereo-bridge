package app.stereobridge;

import android.hardware.camera2.CaptureRequest;

/** Pure stabilization request choice from advertised mode lists. */
final class StabilizationPolicy {
  static final int NONE = -1;

  /** Session-level video stabilization: PREVIEW_STABILIZATION when offered, else ON, else NONE. */
  static int videoStabilization(int[] availableModes) {
    if (availableModes == null) return NONE;
    boolean preview = false, on = false;
    for (int mode : availableModes) {
      if (mode == CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION) preview = true;
      else if (mode == CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON) on = true;
    }
    if (preview) return CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION;
    if (on) return CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON;
    return NONE;
  }

  static boolean opticalStabilization(int[] availableModes) {
    if (availableModes == null) return false;
    for (int mode : availableModes)
      if (mode == CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON) return true;
    return false;
  }
}
