package app.stereobridge;

public final class BridgeState {
  public enum Phase {
    STOPPED,
    SETUP_REQUIRED,
    CONNECTING,
    LIVE,
    RECONNECTING,
    STOPPING,
    ERROR
  }

  public static volatile Phase phase = Phase.STOPPED;
  public static volatile String message = "Connect two microphones and choose your camera.";
  public static volatile boolean cameraMode;
  public static volatile String leftStatus = "Disconnected", rightStatus = "Disconnected";
  public static volatile int leftLevel, rightLevel;
  public static volatile long droppedFrames, startedAt;

  public static void update(Phase value, String detail) {
    phase = value;
    message = detail;
  }

  private BridgeState() {}
}
