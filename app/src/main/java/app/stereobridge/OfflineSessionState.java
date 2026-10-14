package app.stereobridge;

/** Process-local capture state; reboot/process death always starts idle. */
final class OfflineSessionState {
  enum Phase {
    IDLE,
    CONNECTING,
    RECORDING,
    SAVING,
    ERROR
  }

  private volatile Phase phase = Phase.IDLE;
  private volatile String message = "Ready to record", savedUri = "";

  synchronized boolean begin() {
    if (busy()) return false;
    phase = Phase.CONNECTING;
    message = "Connecting both microphones…";
    return true;
  }

  synchronized boolean recording() {
    if (phase != Phase.CONNECTING) return false;
    phase = Phase.RECORDING;
    message = "Recording stereo video";
    return true;
  }

  synchronized void saving() {
    phase = Phase.SAVING;
    message = "Saving your video…";
  }

  synchronized void saved(String uri) {
    savedUri = uri;
    phase = Phase.IDLE;
    message = "Video saved to Movies / Stereo Bridge";
  }

  synchronized void fail(String error) {
    phase = Phase.ERROR;
    message = error;
  }

  synchronized void idle() {
    phase = Phase.IDLE;
    message = "Ready to record";
  }

  boolean busy() {
    return phase == Phase.CONNECTING || phase == Phase.RECORDING || phase == Phase.SAVING;
  }

  Phase phase() {
    return phase;
  }

  String message() {
    return message;
  }

  String savedUri() {
    return savedUri;
  }

  synchronized void rememberSaved(String uri) {
    savedUri = uri;
  }

  synchronized void restoreLast(String uri) {
    if (savedUri.isEmpty()) savedUri = uri;
  }
}
