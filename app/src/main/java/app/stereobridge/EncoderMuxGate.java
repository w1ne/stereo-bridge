package app.stereobridge;

/** Pure mux format handshake and strict output buffering budget. Worker confined. */
final class EncoderMuxGate {
  private final int byteLimit, sampleLimit;
  private int bytes, samples;
  private boolean video, audio;

  EncoderMuxGate(int byteLimit, int sampleLimit) {
    if (byteLimit <= 0 || sampleLimit <= 0)
      throw new IllegalArgumentException("Invalid mux limits");
    this.byteLimit = byteLimit;
    this.sampleLimit = sampleLimit;
  }

  void format(boolean isVideo) {
    if (isVideo ? video : audio) throw new IllegalStateException("Codec format changed twice");
    if (isVideo) video = true;
    else audio = true;
  }

  boolean ready() {
    return video && audio;
  }

  void reserve(int size) {
    if (size <= 0) throw new IllegalArgumentException("Empty sample");
    if (samples >= sampleLimit || size > byteLimit - bytes)
      throw new IllegalStateException("Mux buffer full");
    bytes += size;
    samples++;
  }

  void release(int size) {
    if (samples <= 0 || size <= 0 || size > bytes)
      throw new IllegalStateException("Invalid mux accounting");
    bytes -= size;
    samples--;
  }
}
