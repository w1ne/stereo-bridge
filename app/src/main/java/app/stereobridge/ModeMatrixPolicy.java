package app.stereobridge;

import java.util.*;

/** Pure negotiation ordering and AE range selection. */
final class ModeMatrixPolicy {
  /** Feasible modes in fallback order; the remembered mode first when feasible. */
  static List<VideoMode> order(List<VideoMode> feasible, String rememberedKey) {
    List<VideoMode> ordered = new ArrayList<>();
    VideoMode remembered = VideoMode.fromKey(rememberedKey);
    if (remembered != null && feasible.contains(remembered)) ordered.add(remembered);
    for (VideoMode mode : feasible) if (!ordered.contains(mode)) ordered.add(mode);
    return ordered;
  }

  /** Advertised AE ranges as {lower, upper} pairs; true when any is capped at the rate. */
  static boolean fpsRangeCovers(int[][] ranges, int rate) {
    if (ranges == null) return false;
    for (int[] range : ranges)
      if (range != null && range.length == 2 && range[1] == rate) return true;
    return false;
  }

  /** The advertised range capped at the rate, preferring [rate, rate]; null when none. */
  static int[] aeRange(int[][] ranges, int rate) {
    if (ranges == null) return null;
    int[] best = null;
    for (int[] range : ranges) {
      if (range == null || range.length != 2 || range[1] != rate) continue;
      if (best == null || (range[0] == rate && best[0] != rate)) best = range;
    }
    return best;
  }
}
