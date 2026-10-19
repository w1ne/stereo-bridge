package app.stereobridge;

/** Pure codec preference order; encoder selection validates what is actually supported. */
final class CodecPolicy {
  static final String AVC = "video/avc", HEVC = "video/hevc";
  static String[] order(String requested) {
    boolean hevc = "hevc".equals(requested) || HEVC.equals(requested);
    return hevc ? new String[] {HEVC, AVC} : new String[] {AVC};
  }
  static String label(String mime) { return HEVC.equals(mime) ? "HEVC" : "AVC"; }
}
