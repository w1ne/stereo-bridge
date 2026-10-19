package app.stereobridge;

/** Maps a preview touch point to normalized sensor coordinates [0,1]. */
final class FocusPointMapper {
  static float[] sensorPoint(
      float viewX, float viewY, int viewWidth, int viewHeight, int displayDegrees, boolean front) {
    float nx = viewWidth <= 0 ? 0.5f : viewX / viewWidth;
    float ny = viewHeight <= 0 ? 0.5f : viewY / viewHeight;
    float vx = nx - 0.5f, vy = ny - 0.5f;
    double angle = Math.toRadians(displayDegrees);
    float bx = (float) (vx * Math.cos(angle) - vy * Math.sin(angle)) + 0.5f;
    float by = (float) (vx * Math.sin(angle) + vy * Math.cos(angle)) + 0.5f;
    if (front) bx = 1f - bx;
    return new float[] {Math.max(0f, Math.min(1f, bx)), Math.max(0f, Math.min(1f, by))};
  }
}
