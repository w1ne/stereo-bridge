package app.stereobridge;

import java.util.*;

/** Pure negotiation ordering and AE range selection. */
final class ModeMatrixPolicy {
  /**
   * Feasible modes in fallback order; the remembered mode first when feasible.
   *
   * <p>The caller passes {@code feasible} in {@link VideoMode#FALLBACK} order. This method
   * preserves that input order, only promoting a feasible remembered mode to the front.
   */
  static List<VideoMode> order(List<VideoMode> feasible, String rememberedKey) {
    List<VideoMode> ordered = new ArrayList<>();
    VideoMode remembered = VideoMode.fromKey(rememberedKey);
    if (remembered != null && feasible.contains(remembered)) ordered.add(remembered);
    for (VideoMode mode : feasible) if (!ordered.contains(mode)) ordered.add(mode);
    return ordered;
  }

  /** Advertised AE ranges as {lower, upper} pairs; true when {@link #aeRange} selects one. */
  static boolean fpsRangeCovers(int[][] ranges, int rate) {
    return aeRange(ranges, rate) != null;
  }

  /**
   * The advertised range capped at the rate, preferring [rate, rate]; null when none.
   *
   * <p>Among ranges whose upper bound equals the rate, the highest lower bound (the most
   * constrained, closest to a fixed rate) wins; ties keep the first encountered. Malformed
   * entries are skipped: null, length other than 2, or upper below lower. This selection is
   * the single source of truth for {@link #fpsRangeCovers}.
   */
  static int[] aeRange(int[][] ranges, int rate) {
    if (ranges == null) return null;
    int[] best = null;
    for (int[] range : ranges) {
      if (range == null || range.length != 2) continue;
      int lower = range[0], upper = range[1];
      if (upper < lower || upper != rate) continue;
      if (best == null || (best[0] != rate && (lower == rate || lower > best[0]))) best = range;
    }
    return best;
  }
}
