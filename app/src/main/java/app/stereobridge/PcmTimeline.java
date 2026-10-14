package app.stereobridge;

/** Session-relative 48 kHz timestamp for the first sample of the next stereo PCM buffer. */
public final class PcmTimeline {
  public static final int SAMPLE_RATE = 48_000;
  private long samples;

  public long ptsUs() {
    // Divide first so samples * 1,000,000 cannot wrap during a long session.
    return Math.addExact(
        Math.multiplyExact(samples / SAMPLE_RATE, 1_000_000L),
        (samples % SAMPLE_RATE) * 1_000_000L / SAMPLE_RATE);
  }

  /** Advance by samples per channel, never interleaved sample count or byte count. */
  public void advance(int monoSamples) {
    if (monoSamples < 0) throw new IllegalArgumentException("Negative sample count");
    long next = Math.addExact(samples, monoSamples);
    // Refuse timestamp overflow without mutating the timeline.
    Math.addExact(
        Math.multiplyExact(next / SAMPLE_RATE, 1_000_000L),
        (next % SAMPLE_RATE) * 1_000_000L / SAMPLE_RATE);
    samples = next;
  }
}
