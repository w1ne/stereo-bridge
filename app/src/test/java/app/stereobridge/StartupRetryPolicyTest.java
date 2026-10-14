package app.stereobridge;
import static org.junit.Assert.*;
import org.junit.Test;
public class StartupRetryPolicyTest {
 @Test public void retryOnlyBeforeRecordingAndAfterBothProducersRelease() {
  assertTrue(StartupRetryPolicy.canRetry(true,true,true,1));
  assertFalse(StartupRetryPolicy.canRetry(false,true,true,1));
  assertFalse(StartupRetryPolicy.canRetry(true,false,true,1));
  assertFalse(StartupRetryPolicy.canRetry(true,true,false,1));
  assertFalse(StartupRetryPolicy.canRetry(true,true,true,12));
 }
}
