package app.stereobridge.mic;

/** One independently timed mono48k input; implementations own their transport and decoding. */
public interface MicSource extends AutoCloseable {
  interface Listener {
    void onAudio(short[] samples, long timestampNanos);

    void onStatus(String message);

    void onError(Exception error);
  }

  void start(Listener listener);

  @Override
  void close();
}
