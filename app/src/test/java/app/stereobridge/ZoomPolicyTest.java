package app.stereobridge;
import static org.junit.Assert.*;
import org.junit.Test;
public class ZoomPolicyTest {
 @Test public void clampsToRange() {
  assertEquals(1f, ZoomPolicy.clamp(0.5f, 1f, 8f), 0f);
  assertEquals(8f, ZoomPolicy.clamp(12f, 1f, 8f), 0f);
  assertEquals(2.5f, ZoomPolicy.clamp(2.5f, 1f, 8f), 0f);
  assertEquals(1f, ZoomPolicy.clamp(2f, 1f, 0.5f), 0f);
 }
 @Test public void cropRegionIsCenteredAndInverseScaled() {
  float[] full = ZoomPolicy.cropRegion(1f, 4000f, 3000f);
  assertArrayEquals(new float[] {0f, 0f, 4000f, 3000f}, full, 0.001f);
  float[] half = ZoomPolicy.cropRegion(2f, 4000f, 3000f);
  assertArrayEquals(new float[] {1000f, 750f, 3000f, 2250f}, half, 0.001f);
 }
 @Test public void subUnitZoomBehavesAsOne() {
  assertArrayEquals(ZoomPolicy.cropRegion(1f, 4000f, 3000f), ZoomPolicy.cropRegion(0.2f, 4000f, 3000f), 0.001f);
 }
}
