package app.stereobridge;
import static org.junit.Assert.*;
import org.junit.Test;
public class FocusPointMapperTest {
 @Test public void centerStaysCenteredAtEveryRotationForBothCameras() {
  for (int display : new int[] {0, 90, 180, 270}) {
   for (boolean front : new boolean[] {false, true}) {
    float[] p = FocusPointMapper.sensorPoint(500f, 400f, 1000, 800, 1920, 1080, 90, display, front);
    assertEquals(0.5f, p[0], 0.001f);
    assertEquals(0.5f, p[1], 0.001f);
   }
  }
 }
 @Test public void invertsCenterCropScaleAtZeroRotation() {
  float[] p = FocusPointMapper.sensorPoint(100f, 400f, 1000, 800, 1920, 1080, 90, 0, false);
  assertEquals(0.1f, p[0], 0.001f);
  assertEquals(0.5f, p[1], 0.001f);
  float[] q = FocusPointMapper.sensorPoint(500f, 100f, 1000, 800, 1920, 1080, 90, 0, false);
  assertEquals(0.5f, q[0], 0.001f);
  assertEquals(0.3313f, q[1], 0.001f);
 }
 @Test public void invertsCenterCropScaleAtNinetyDegrees() {
  float[] p = FocusPointMapper.sensorPoint(500f, 400f, 1000, 800, 1920, 1080, 90, 90, false);
  assertEquals(0.5f, p[0], 0.001f);
  assertEquals(0.5f, p[1], 0.001f);
  float[] q = FocusPointMapper.sensorPoint(500f, 100f, 1000, 800, 1920, 1080, 90, 90, false);
  assertEquals(0.875f, q[0], 0.001f);
  assertEquals(0.5f, q[1], 0.001f);
 }
 @Test public void mirrorsFrontCameraInViewSpaceBeforeRotation() {
  float[] p = FocusPointMapper.sensorPoint(100f, 400f, 1000, 800, 1920, 1080, 90, 0, true);
  assertEquals(0.9f, p[0], 0.001f);
  assertEquals(0.5f, p[1], 0.001f);
 }
 @Test public void clampsOutsideTouches() {
  float[] p = FocusPointMapper.sensorPoint(-50f, 900f, 1000, 800, 1920, 1080, 90, 0, false);
  assertEquals(0f, p[0], 0.001f);
  float[] q = FocusPointMapper.sensorPoint(2000f, -300f, 1000, 800, 1920, 1080, 90, 0, false);
  assertTrue(q[0] >= 0f && q[0] <= 1f);
  assertTrue(q[1] >= 0f && q[1] <= 1f);
 }
 @Test public void guardsEmptyViewAndBuffer() {
  assertArrayEquals(new float[] {0.5f, 0.5f}, FocusPointMapper.sensorPoint(0f, 0f, 0, 0, 1920, 1080, 90, 0, false), 0.001f);
  assertArrayEquals(new float[] {0.5f, 0.5f}, FocusPointMapper.sensorPoint(0f, 0f, 1000, 800, 0, 0, 90, 0, false), 0.001f);
  assertArrayEquals(new float[] {0.5f, 0.5f}, FocusPointMapper.sensorPoint(0f, 0f, -1, -1, -1, -1, 90, 0, false), 0.001f);
 }
 @Test public void mapsOrientedPreviewToActiveArray() {
  assertArrayEquals(new float[] {0.1f, 0.2f}, FocusPointMapper.activePoint(0.1f, 0.2f, 0), 0.001f);
  assertArrayEquals(new float[] {0.5f, 0.5f}, FocusPointMapper.activePoint(0.5f, 0.5f, 90), 0.001f);
  assertArrayEquals(new float[] {0.5f, 0.9f}, FocusPointMapper.activePoint(0.1f, 0.5f, 90), 0.001f);
  assertArrayEquals(new float[] {0.8f, 0.1f}, FocusPointMapper.activePoint(0.1f, 0.2f, 270), 0.001f);
  assertArrayEquals(new float[] {0.9f, 0.8f}, FocusPointMapper.activePoint(0.1f, 0.2f, 180), 0.001f);
 }
}
