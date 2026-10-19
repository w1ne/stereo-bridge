package app.stereobridge;
import static org.junit.Assert.*;
import org.junit.Test;
public class FocusPointMapperTest {
 @Test public void centerStaysCenteredAtEveryRotation() {
  for (int display : new int[] {0, 90, 180, 270}) {
   float[] p = FocusPointMapper.sensorPoint(500f, 400f, 1000, 800, display, false);
   assertEquals(0.5f, p[0], 0.001f);
   assertEquals(0.5f, p[1], 0.001f);
  }
 }
 @Test public void rotatesViewIntoUnrotatedSensorSpace() {
  float[] p = FocusPointMapper.sensorPoint(100f, 400f, 1000, 800, 90, false);
  assertEquals(0.5f, p[0], 0.001f);
  assertEquals(0.1f, p[1], 0.001f);
  float[] q = FocusPointMapper.sensorPoint(100f, 400f, 1000, 800, 270, false);
  assertEquals(0.5f, q[0], 0.001f);
  assertEquals(0.9f, q[1], 0.001f);
 }
 @Test public void mirrorsHorizontallyForFrontCamera() {
  float[] back = FocusPointMapper.sensorPoint(100f, 400f, 1000, 800, 0, false);
  float[] front = FocusPointMapper.sensorPoint(100f, 400f, 1000, 800, 0, true);
  assertEquals(0.1f, back[0], 0.001f);
  assertEquals(0.9f, front[0], 0.001f);
  assertEquals(back[1], front[1], 0.001f);
 }
 @Test public void clampsOutsideTouchesAndGuardsEmptyView() {
  float[] p = FocusPointMapper.sensorPoint(-50f, 900f, 1000, 800, 0, false);
  assertEquals(0f, p[0], 0.001f);
  assertEquals(1f, p[1], 0.001f);
  float[] q = FocusPointMapper.sensorPoint(0f, 0f, 0, 0, 0, false);
  assertEquals(0.5f, q[0], 0.001f);
  assertEquals(0.5f, q[1], 0.001f);
 }
}
