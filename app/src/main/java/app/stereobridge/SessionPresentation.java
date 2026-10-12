package app.stereobridge;

/** Plain-language session labels. Live audio routing does not imply camera recording. */
final class SessionPresentation {
  final String title, action;
  final boolean enabled;

  private SessionPresentation(String title, String action, boolean enabled) {
    this.title = title;
    this.action = action;
    this.enabled = enabled;
  }

  static SessionPresentation of(BridgeState.Phase phase, boolean camera) {
    switch (phase) {
      case CONNECTING:
        return new SessionPresentation("Connecting your microphones", "Cancel", true);
      case LIVE:
        return new SessionPresentation(
            camera ? "Your camera is ready" : "Recording audio preview", "Stop", true);
      case STOPPING:
        return new SessionPresentation("Finishing your session", "Stopping…", false);
      case ERROR:
        return new SessionPresentation("Connection interrupted", "Try again", true);
      case SETUP_REQUIRED:
        return new SessionPresentation("One-time setup", "Set up", true);
      case RECONNECTING:
        return new SessionPresentation("Reconnecting", "Cancel", true);
      default:
        return new SessionPresentation("Ready when you are", "Start", true);
    }
  }
}
