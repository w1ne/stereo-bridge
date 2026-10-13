package app.stereobridge;

/** Stereo signed 16-bit PCM. Gains belong to physical inputs, before output swapping. */
public final class StereoPcmMixer {
  private StereoPcmMixer() {}

  public static byte[] mix(
      short[] left, short[] right, float leftGain, float rightGain, boolean swap) {
    requireSamples(left);
    requireSamples(right);
    if (left.length != right.length || left.length > Integer.MAX_VALUE / 4)
      throw new IllegalArgumentException("Mismatched or oversized PCM frames");
    requireGain(leftGain);
    requireGain(rightGain);
    byte[] stereo = new byte[left.length * 4];
    for (int i = 0; i < left.length; i++) {
      int l = scale(left[i], leftGain);
      int r = scale(right[i], rightGain);
      put(stereo, i * 4, swap ? r : l);
      put(stereo, i * 4 + 2, swap ? l : r);
    }
    return stereo;
  }

  /** Normalized input peak after gain, capped at full scale for display. */
  public static float peak(short[] samples, float gain) {
    requireSamples(samples);
    requireGain(gain);
    int peak = 0;
    for (short sample : samples) peak = Math.max(peak, Math.abs((int) sample));
    return (float) Math.min(1.0, peak * (double) gain / 32768.0);
  }

  private static int scale(short sample, float gain) {
    return (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample * (double) gain));
  }

  private static void put(byte[] output, int offset, int sample) {
    output[offset] = (byte) sample;
    output[offset + 1] = (byte) (sample >> 8);
  }

  private static void requireSamples(short[] samples) {
    if (samples == null || samples.length == 0)
      throw new IllegalArgumentException("PCM frame must contain samples");
  }

  private static void requireGain(float gain) {
    if (!Float.isFinite(gain) || gain < 0) throw new IllegalArgumentException("Invalid gain");
  }
}
