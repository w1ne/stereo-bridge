package app.stereobridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class VideoCapabilityPolicyTest {
 @Test public void limitsRatesToBothCameraAndCodec() { assertEquals(24,VideoCapabilityPolicy.rate(30,1,24)); assertEquals(0,VideoCapabilityPolicy.rate(15,24,60)); }
 @Test public void scalesAndClampsBitrate() { assertEquals(7776000,VideoCapabilityPolicy.bitrate(1920,1080,30,100000,8000000)); assertEquals(1000000,VideoCapabilityPolicy.bitrate(640,480,15,1000000,4000000)); }
 @Test public void variableRangeCanBeBoundedByConfiguredOutput() { assertEquals(30,VideoCapabilityPolicy.rate(30,15,60)); assertEquals(0,VideoCapabilityPolicy.rate(24,30,60)); }
 @Test public void actualRateUsesBothOutputDurationsWithoutArtificialThirtyCap() {
  assertEquals(60,VideoCapabilityPolicy.captureRate(60,16000000,16000000));
  assertEquals(30,VideoCapabilityPolicy.captureRate(60,33000000,16000000));
  assertEquals(30,VideoCapabilityPolicy.captureRate(60,16000000,33000000));
  assertEquals(60,VideoCapabilityPolicy.captureRate(60,0,0));
 }
 @Test public void advertisedThirtyRangePreferredEvenWhenSixtyRangeDurationCapsToThirty() {
  assertTrue(VideoCapabilityPolicy.preferRange(30,30,15,60,30,15));
  assertFalse(VideoCapabilityPolicy.preferRange(60,30,15,30,30,15));
 }
}
