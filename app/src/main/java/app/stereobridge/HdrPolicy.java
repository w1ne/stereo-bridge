package app.stereobridge;

/** Pure 10-bit HLG offer rules matching Pixel Camera's own constraints. */
final class HdrPolicy {
  static boolean offer(boolean cameraOffersHlg, boolean encoderAcceptsHlg) {
    return cameraOffersHlg && encoderAcceptsHlg;
  }
  static boolean allowedAtRate(int rate) { return rate == 30; }
}
