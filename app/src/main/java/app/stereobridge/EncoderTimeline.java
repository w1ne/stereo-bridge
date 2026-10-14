package app.stereobridge;

/** Timestamp mapping independent of the camera sensor timestamp source. */
final class EncoderTimeline {
  private volatile long captureUs = -1;
  private long firstPts = Long.MIN_VALUE;
  private long lastPts = Long.MIN_VALUE;

  synchronized void videoStarted(long elapsedOffsetUs) {
    if (elapsedOffsetUs < 0) throw new IllegalArgumentException("Negative capture offset");
    if (captureUs < 0) captureUs = elapsedOffsetUs;
  }

  boolean ready() {
    return captureUs >= 0;
  }

  long videoPts(long codecPts) {
    if (!ready()) throw new IllegalStateException("Camera capture has not started");
    if (codecPts < lastPts) throw new IllegalArgumentException("Video timestamp moved backwards");
    if (firstPts == Long.MIN_VALUE) firstPts = codecPts;
    lastPts = codecPts;
    return codecPts - firstPts + captureUs;
  }

  static long pcmPts(long startUs, int byteOffset) {
    if (byteOffset < 0 || byteOffset % 4 != 0)
      throw new IllegalArgumentException("Stereo frame alignment");
    return startUs + byteOffset / 4L * 1_000_000L / 48000;
  }
}
