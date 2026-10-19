package app.stereobridge;
import static org.junit.Assert.*;
import org.junit.Test;
public class CameraCapabilitiesTest {
 @Test public void noneIsFullyDisabled() {
  CameraCapabilities none = CameraCapabilities.NONE;
  assertEquals(StabilizationPolicy.NONE, none.stabilizationMode);
  assertFalse(none.opticalStabilization || none.flash || none.zoom || none.hlgCamera || none.hlgEncoder);
  assertEquals(1f, none.zoomMin, 0f);
  assertEquals(1f, none.zoomMax, 0f);
  assertEquals(0, none.maxAfRegions);
 }
 @Test public void describesEveryField() {
  CameraCapabilities caps = new CameraCapabilities(2, true, true, true, 1f, 8f, 3, true, true);
  String text = caps.describe();
  assertTrue(text.contains("stabilization=2"));
  assertTrue(text.contains("optical=true"));
  assertTrue(text.contains("zoom=1.0-8.0"));
  assertTrue(text.contains("flash=true"));
  assertTrue(text.contains("afRegions=3"));
  assertTrue(text.contains("hdrCamera=true"));
  assertTrue(text.contains("hdrEncoder=true"));
 }
}
