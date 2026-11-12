package app.stereobridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class VideoCapabilityPolicyTest {
 @Test public void limitsRatesToBothCameraAndCodec() { assertEquals(24,VideoCapabilityPolicy.rate(30,1,24)); assertEquals(0,VideoCapabilityPolicy.rate(15,24,60)); }
 @Test public void cameraSizeDurationsBoundNominalModes() {
  assertFalse(VideoCapabilityPolicy.supportsCaptureRate(60, 33_333_333L, 16_666_667L));
  assertFalse(VideoCapabilityPolicy.supportsCaptureRate(60, 16_666_667L, 33_333_333L));
  assertTrue(VideoCapabilityPolicy.supportsCaptureRate(60, 16_666_667L, 16_666_667L));
  assertTrue(VideoCapabilityPolicy.supportsCaptureRate(30, 33_333_334L, 16_666_667L));
  assertFalse(VideoCapabilityPolicy.supportsCaptureRate(30, 41_666_667L, 0));
 }
 @Test public void durationBoundsHandleLegacyUnknownMetadata() {
  assertTrue(VideoCapabilityPolicy.supportsCaptureRate(30, 0, 0));
  assertTrue(VideoCapabilityPolicy.supportsCaptureRate(30, 0, 33_333_334L));
  assertFalse(VideoCapabilityPolicy.supportsCaptureRate(60, 0, 33_333_334L));
  assertFalse(VideoCapabilityPolicy.supportsCaptureRate(0, 0, 0));
  assertFalse(VideoCapabilityPolicy.supportsCaptureRate(30, -1, 0));
 }
 @Test public void scalesAndClampsBitrate() { assertEquals(8000000,VideoCapabilityPolicy.bitrate(1920,1080,30,100000,8000000)); assertEquals(1000000,VideoCapabilityPolicy.bitrate(640,480,15,1000000,4000000)); }
 @Test public void usesPixelCalibratedPresets() {
  assertEquals(45_000_000, VideoCapabilityPolicy.bitrate(3840, 2160, 30, 0, 100_000_000));
  assertEquals(80_000_000, VideoCapabilityPolicy.bitrate(3840, 2160, 60, 0, 100_000_000));
  assertEquals(30_000_000, VideoCapabilityPolicy.bitrate(1920, 1080, 60, 0, 100_000_000));
  assertEquals(20_000_000, VideoCapabilityPolicy.bitrate(1920, 1080, 30, 0, 100_000_000));
 }
 @Test public void keepsUnmodeledSizesOnTheLegacyFormula() {
  assertEquals(3_456_000, VideoCapabilityPolicy.bitrate(1280, 720, 30, 0, 8_000_000));
  assertEquals(288_000, VideoCapabilityPolicy.bitrate(320, 240, 30, 0, 8_000_000));
 }
 @Test public void clampsToEncoderRange() {
  assertEquals(20_000_000, VideoCapabilityPolicy.bitrate(3840, 2160, 30, 0, 20_000_000));
  assertEquals(50_000_000, VideoCapabilityPolicy.bitrate(1920, 1080, 30, 50_000_000, 100_000_000));
 }
 @Test public void variableRangeCanBeBoundedByConfiguredOutput() { assertEquals(30,VideoCapabilityPolicy.rate(30,15,60)); assertEquals(0,VideoCapabilityPolicy.rate(24,30,60)); }
}
