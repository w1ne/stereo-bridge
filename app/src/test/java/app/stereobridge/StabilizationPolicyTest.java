package app.stereobridge;
import static org.junit.Assert.*;
import android.hardware.camera2.CaptureRequest;
import org.junit.Test;
public class StabilizationPolicyTest {
 @Test public void prefersPreviewStabilizationThenOn() {
  assertEquals(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION,
      StabilizationPolicy.videoStabilization(new int[] {
          CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
          CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON,
          CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION}));
  assertEquals(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON,
      StabilizationPolicy.videoStabilization(new int[] {
          CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
          CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON}));
 }
 @Test public void reportsUnsupportedWithoutAdvertisedModes() {
  assertEquals(StabilizationPolicy.NONE, StabilizationPolicy.videoStabilization(null));
  assertEquals(StabilizationPolicy.NONE,
      StabilizationPolicy.videoStabilization(new int[] {CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF}));
  assertEquals(StabilizationPolicy.NONE, StabilizationPolicy.videoStabilization(new int[0]));
 }
 @Test public void opticalStabilizationRequiresAdvertisedOn() {
  assertTrue(StabilizationPolicy.opticalStabilization(
      new int[] {CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON}));
  assertFalse(StabilizationPolicy.opticalStabilization(
      new int[] {CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF}));
  assertFalse(StabilizationPolicy.opticalStabilization(null));
 }
}
