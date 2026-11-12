package app.stereobridge;
import static org.junit.Assert.*;
import org.junit.Test;
public class HdrPolicyTest {
 @Test public void offersHdrOnlyWhenCameraAndEncoderAgree() {
  assertTrue(HdrPolicy.offer(true, true));
  assertFalse(HdrPolicy.offer(true, false));
  assertFalse(HdrPolicy.offer(false, true));
  assertFalse(HdrPolicy.offer(false, false));
 }
 @Test public void hdrIsLimitedToThirtyFps() {
  assertTrue(HdrPolicy.allowedAtRate(30));
  assertFalse(HdrPolicy.allowedAtRate(24));
  assertFalse(HdrPolicy.allowedAtRate(60));
 }
}
