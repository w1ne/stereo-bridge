package app.stereobridge;

/** Pure zoom clamping and API 29 crop-region arithmetic. */
final class ZoomPolicy {
  static float clamp(float desired, float min, float max) {
    if (max < min) return min;
    return Math.max(min, Math.min(max, desired));
  }

  /** Centered crop region {left, top, right, bottom} in active-array pixels for API 29. */
  static float[] cropRegion(float zoom, float activeWidth, float activeHeight) {
    float safe = Math.max(1f, zoom);
    float width = activeWidth / safe, height = activeHeight / safe;
    float left = (activeWidth - width) / 2f, top = (activeHeight - height) / 2f;
    return new float[] {left, top, left + width, top + height};
  }
}
