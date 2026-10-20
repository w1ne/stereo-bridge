package app.stereobridge;

/** Inverts the preview transform (CameraGeometry.previewCorners) and converts to active-array space. */
final class FocusPointMapper {
  /** Tap (view pixels) to normalized coordinates [0,1] in oriented preview space.
   *  Front mirroring is applied in view space, matching the on-screen mirror. */
  static float[] sensorPoint(float viewX, float viewY, int viewWidth, int viewHeight,
      int bufferWidth, int bufferHeight, int sensorOrientation, int displayDegrees, boolean front) {
    if (viewWidth <= 0 || viewHeight <= 0 || bufferWidth <= 0 || bufferHeight <= 0)
      return new float[] {0.5f, 0.5f};
    if (front) viewX = viewWidth - viewX;
    float sw = sensorOrientation % 180 == 0 ? bufferWidth : bufferHeight;
    float sh = sensorOrientation % 180 == 0 ? bufferHeight : bufferWidth;
    float rw = displayDegrees % 180 == 0 ? sw : sh;
    float rh = displayDegrees % 180 == 0 ? sh : sw;
    float scale = Math.max(viewWidth / rw, viewHeight / rh);
    float dx = (viewX - viewWidth / 2f) / scale;
    float dy = (viewY - viewHeight / 2f) / scale;
    double angle = Math.toRadians(displayDegrees);
    float x = (float) (dx * Math.cos(angle) - dy * Math.sin(angle));
    float y = (float) (dx * Math.sin(angle) + dy * Math.cos(angle));
    return new float[] {clamp(x / sw + 0.5f), clamp(y / sh + 0.5f)};
  }

  /** Oriented preview space to normalized active-array coordinates [0,1] for the given sensor orientation. */
  static float[] activePoint(float u, float v, int sensorOrientation) {
    switch (Math.floorMod(sensorOrientation, 360)) {
      case 90: return new float[] {clamp(v), clamp(1f - u)};
      case 180: return new float[] {clamp(1f - u), clamp(1f - v)};
      case 270: return new float[] {clamp(1f - v), clamp(u)};
      default: return new float[] {clamp(u), clamp(v)};
    }
  }

  private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
}
