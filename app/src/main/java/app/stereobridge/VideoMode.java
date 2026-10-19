package app.stereobridge;

import java.util.*;

/** Canonical video capture modes with display order and negotiation fallback order. */
public final class VideoMode {
  public final int width, height, rate;
  private final String label;

  private VideoMode(int width, int height, int rate, String label) {
    this.width = width;
    this.height = height;
    this.rate = rate;
    this.label = label;
  }

  public static final VideoMode UHD30 = new VideoMode(3840, 2160, 30, "4K30");
  public static final VideoMode UHD60 = new VideoMode(3840, 2160, 60, "4K60");
  public static final VideoMode FHD60 = new VideoMode(1920, 1080, 60, "1080p60");
  public static final VideoMode FHD30 = new VideoMode(1920, 1080, 30, "1080p30");

  /** Display order for the mode chooser. */
  public static final List<VideoMode> ALL =
      Collections.unmodifiableList(Arrays.asList(UHD30, UHD60, FHD60, FHD30));
  /** Fallback preference: default, then storage-friendly rates, then the remaining mode. */
  public static final List<VideoMode> FALLBACK =
      Collections.unmodifiableList(Arrays.asList(UHD30, FHD60, FHD30, UHD60));

  public String label() { return label; }

  public String key() { return width + "x" + height + "@" + rate; }

  public static VideoMode fromKey(String key) {
    for (VideoMode mode : ALL) if (mode.key().equals(key)) return mode;
    return null;
  }

  @Override public boolean equals(Object other) {
    if (!(other instanceof VideoMode)) return false;
    VideoMode mode = (VideoMode) other;
    return width == mode.width && height == mode.height && rate == mode.rate;
  }

  @Override public int hashCode() { return Objects.hash(width, height, rate); }

  @Override public String toString() { return key(); }
}
