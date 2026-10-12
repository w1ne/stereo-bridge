package app.stereobridge;

/** Pure PCM DSP. Gains refer to physical inputs; swapping changes output assignment. */
public final class AudioProcessor {
  private AudioProcessor() {}

  public static byte[] process(byte[] pcm24, float leftGain, float rightGain, boolean swap) {
    if (pcm24.length % 6 != 0) throw new IllegalArgumentException("Incomplete stereo PCM frame");
    if (!Float.isFinite(leftGain) || !Float.isFinite(rightGain) || leftGain < 0 || rightGain < 0)
      throw new IllegalArgumentException("Invalid gain");
    byte[] out = new byte[pcm24.length / 3 * 2];
    for (int i = 0, j = 0; i < pcm24.length; i += 6, j += 4) {
      int l = scale(sample24(pcm24, i), leftGain), r = scale(sample24(pcm24, i + 3), rightGain);
      put(out, j, swap ? r : l);
      put(out, j + 2, swap ? l : r);
    }
    return out;
  }

  public static int sample24(byte[] data, int offset) {
    return (data[offset] & 255) | ((data[offset + 1] & 255) << 8) | (data[offset + 2] << 16);
  }

  public static int scale(int sample24, float gain) {
    return (int) Math.max(-32768, Math.min(32767, (sample24 / 256.0) * gain));
  }

  private static void put(byte[] out, int i, int sample) {
    out[i] = (byte) sample;
    out[i + 1] = (byte) (sample >> 8);
  }
}
