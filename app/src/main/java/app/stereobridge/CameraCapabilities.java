package app.stereobridge;

/** Camera features surfaced to the UI after negotiation; NONE disables everything. */
public final class CameraCapabilities {
  public static final CameraCapabilities NONE =
      new CameraCapabilities(StabilizationPolicy.NONE, false, false, false, 1f, 1f, 0, false, false);
  public final int stabilizationMode;
  public final boolean opticalStabilization, flash, zoom, hlgCamera, hlgEncoder;
  public final float zoomMin, zoomMax;
  public final int maxAfRegions;

  public CameraCapabilities(int stabilizationMode, boolean opticalStabilization, boolean flash,
      boolean zoom, float zoomMin, float zoomMax, int maxAfRegions, boolean hlgCamera, boolean hlgEncoder) {
    this.stabilizationMode = stabilizationMode;
    this.opticalStabilization = opticalStabilization;
    this.flash = flash;
    this.zoom = zoom;
    this.zoomMin = zoomMin;
    this.zoomMax = zoomMax;
    this.maxAfRegions = maxAfRegions;
    this.hlgCamera = hlgCamera;
    this.hlgEncoder = hlgEncoder;
  }

  public String describe() {
    return "camera stabilization=" + stabilizationMode + " optical=" + opticalStabilization
        + " zoom=" + (zoom ? zoomMin + "-" + zoomMax : "off") + " flash=" + flash
        + " afRegions=" + maxAfRegions + " hdrCamera=" + hlgCamera + " hdrEncoder=" + hlgEncoder;
  }
}
