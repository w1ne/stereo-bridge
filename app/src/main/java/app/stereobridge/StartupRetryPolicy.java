package app.stereobridge;
/** A failed startup may be retried only after both camera and codec ownership drains. */
final class StartupRetryPolicy {
 static final int MAX_ATTEMPTS = 12;
 static boolean canRetry(boolean preparing,boolean cameraClosed,boolean encoderReleased,int attempts) {
  return preparing && cameraClosed && encoderReleased && attempts < MAX_ATTEMPTS;
 }
}
