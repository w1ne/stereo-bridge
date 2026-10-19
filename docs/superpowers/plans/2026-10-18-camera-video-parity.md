# Camera Video Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring Stereo Bridge camera video capture to Pixel Camera parity surface — 4K30/4K60/1080p60/1080p30 modes, session-level stabilization, tap-focus/lock, zoom, torch, flip, and probe-gated 10-bit HLG HDR — without touching the verified audio/mux/recovery machinery.

**Architecture:** Pure, unit-tested policy classes (mode matrix, bitrate presets, stabilization/zoom/focus/HDR/codec enablement) feed the existing Camera2 + MediaCodec + MediaMuxer pipeline in `OfflineRecorderService` / `OfflineVideoEncoder`. UI gains a thin `CameraControls` overlay and a mode sheet; persistence reuses generic `BridgeSettings` keys. No new permissions, no new dependencies.

**Tech Stack:** Java (repo style: one class per concern, JUnit 4), Android Camera2, MediaCodec/MediaMuxer, Gradle `app` module (CI: `./gradlew testDebugUnitTest lintRelease assembleDebug bundleRelease`).

**Spec:** `docs/superpowers/specs/2026-10-18-camera-video-parity-design.md`

---

## File structure

New main sources under `app/src/main/java/app/stereobridge/`:

| File | Responsibility |
| --- | --- |
| `VideoMode.java` | Value type: label, width, height, rate, key; display and fallback orders |
| `ModeMatrixPolicy.java` | Pure: remembered-first ordering, AE range selection, FPS coverage |
| `StabilizationPolicy.java` | Pure: video stabilization mode choice, OIS support |
| `ZoomPolicy.java` | Pure: zoom clamp, API 29 crop-region math |
| `FocusPointMapper.java` | Pure: view tap to normalized sensor point (rotation, front mirror) |
| `HdrPolicy.java` | Pure: HLG offer rules and the 30 fps rule |
| `CodecPolicy.java` | Pure: codec ladder order and labels |
| `CameraCapabilities.java` | Data object for UI enablement plus one-line log description |
| `CameraControls.java` | Preview overlay: gestures, focus ring, chips |
| `ModeChooserDialog.java` | Mode / HDR / codec chooser dialog |

New tests under `app/src/test/java/app/stereobridge/`: `VideoModeTest`, `ModeMatrixPolicyTest`, `VideoCapabilityPolicyTest`, `StabilizationPolicyTest`, `ZoomPolicyTest`, `FocusPointMapperTest`, `HdrPolicyTest`, `CodecPolicyTest`, `CameraCapabilitiesTest`.

Modified: `OfflineRecorderService.java`, `OfflineVideoEncoder.java`, `VideoCapabilityPolicy.java`, `CameraGeometry.java`, `CameraGeometryTest.java`, `MainActivity.java`, `scripts/verify-stereo-mp4.py`, `docs/compatibility.md`, `docs/offline-verification.md`, `docs/camera-capability-probe.md` (new), `README.md`.

Persisted settings keys (via existing generic `BridgeSettings`): `video_mode` (mode key), `video_hdr` (bool), `video_codec` (`avc` or `hevc`), `facing` (`back` or `front`).

---

### Task 0: Workspace and design commit

**Files:**
- Commit: `docs/superpowers/specs/2026-10-18-camera-video-parity-design.md` (already written in the main clone)
- Commit: `docs/superpowers/plans/2026-10-18-camera-video-parity.md` (this file)

- [ ] **Step 1: Create the isolated worktree**

Preferred: use the `superpowers:using-git-worktrees` skill. Fallback:

```bash
cd /home/andrii/projects/stereo-bridge
git worktree add ../stereo-bridge-wt-camera -b camera-video-parity main
cp docs/superpowers/specs/2026-10-18-camera-video-parity-design.md ../stereo-bridge-wt-camera/docs/superpowers/specs/
```

- [ ] **Step 2: Commit spec and plan first**

```bash
cd ../stereo-bridge-wt-camera
git add docs/superpowers/specs/2026-10-18-camera-video-parity-design.md docs/superpowers/plans/2026-10-18-camera-video-parity.md
git commit -m "Document camera video parity design and implementation plan"
```

Expected: one commit; `git status` shows a clean worktree.

---

### Task 1: VideoMode and ModeMatrixPolicy

Execution note (reconciled during implementation, commits 305bf28 + bfb42bc): a range covers a rate only when its upper bound equals the rate; `aeRange` prefers `[rate, rate]` then the highest lower bound. The committed tests in the branch are authoritative over the reference test code below where they differ, and `fpsRangeCovers` delegates to `aeRange` as a single source of truth.

**Files:**
- Create: `app/src/main/java/app/stereobridge/VideoMode.java`
- Create: `app/src/main/java/app/stereobridge/ModeMatrixPolicy.java`
- Test: `app/src/test/java/app/stereobridge/VideoModeTest.java`
- Test: `app/src/test/java/app/stereobridge/ModeMatrixPolicyTest.java`

- [ ] **Step 1: Write the failing tests**

`VideoModeTest.java`:

```java
package app.stereobridge;
import static org.junit.Assert.*;
import org.junit.Test;
public class VideoModeTest {
 @Test public void exposesDisplayAndFallbackOrders() {
  assertEquals(4, VideoMode.ALL.size());
  assertEquals(VideoMode.UHD30, VideoMode.ALL.get(0));
  assertEquals(VideoMode.UHD60, VideoMode.ALL.get(1));
  assertEquals(VideoMode.FHD60, VideoMode.ALL.get(2));
  assertEquals(VideoMode.FHD30, VideoMode.ALL.get(3));
  assertEquals(VideoMode.UHD30, VideoMode.FALLBACK.get(0));
  assertEquals(VideoMode.FHD60, VideoMode.FALLBACK.get(1));
  assertEquals(VideoMode.FHD30, VideoMode.FALLBACK.get(2));
  assertEquals(VideoMode.UHD60, VideoMode.FALLBACK.get(3));
 }
 @Test public void roundTripsKeysAndLabels() {
  assertEquals("3840x2160@30", VideoMode.UHD30.key());
  assertEquals("4K30", VideoMode.UHD30.label());
  assertEquals(VideoMode.UHD60, VideoMode.fromKey("3840x2160@60"));
  assertNull(VideoMode.fromKey("9999x9999@24"));
  assertNull(VideoMode.fromKey(""));
 }
 @Test public void equalsIgnoresInstance() {
  assertEquals(VideoMode.fromKey(VideoMode.FHD30.key()), VideoMode.FHD30);
  assertNotEquals(VideoMode.FHD30, VideoMode.FHD60);
 }
}
```

`ModeMatrixPolicyTest.java`:

```java
package app.stereobridge;
import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;
public class ModeMatrixPolicyTest {
 @Test public void rememberedFeasibleModeComesFirst() {
  assertEquals(Arrays.asList(VideoMode.UHD30, VideoMode.FHD30),
      ModeMatrixPolicy.order(Arrays.asList(VideoMode.FHD30, VideoMode.UHD30), VideoMode.UHD30.key()));
 }
 @Test public void rememberedInfeasibleModeIsIgnored() {
  assertEquals(Arrays.asList(VideoMode.FHD30, VideoMode.UHD60),
      ModeMatrixPolicy.order(Arrays.asList(VideoMode.FHD30, VideoMode.UHD60), VideoMode.UHD30.key()));
  assertEquals(Arrays.asList(VideoMode.FHD30, VideoMode.UHD60),
      ModeMatrixPolicy.order(Arrays.asList(VideoMode.FHD30, VideoMode.UHD60), ""));
 }
 @Test public void fpsRangeCoversAnyAdvertisedRange() {
  int[][] ranges = {{15, 30}, {60, 60}};
  assertTrue(ModeMatrixPolicy.fpsRangeCovers(ranges, 30));
  assertTrue(ModeMatrixPolicy.fpsRangeCovers(ranges, 60));
  assertFalse(ModeMatrixPolicy.fpsRangeCovers(ranges, 24));
  assertFalse(ModeMatrixPolicy.fpsRangeCovers(null, 30));
  assertFalse(ModeMatrixPolicy.fpsRangeCovers(new int[][] {{30}}, 30));
 }
 @Test public void aeRangePrefersExactRateThenLowestUpperBound() {
  int[][] ranges = {{15, 30}, {30, 60}, {60, 60}, {24, 30}};
  assertArrayEquals(new int[] {60, 60}, ModeMatrixPolicy.aeRange(ranges, 60));
  assertArrayEquals(new int[] {15, 30}, ModeMatrixPolicy.aeRange(ranges, 30));
  assertNull(ModeMatrixPolicy.aeRange(ranges, 24));
  assertNull(ModeMatrixPolicy.aeRange(null, 30));
 }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "app.stereobridge.VideoModeTest" --tests "app.stereobridge.ModeMatrixPolicyTest"`
Expected: FAIL (classes do not exist).

- [ ] **Step 3: Write the implementations**

`VideoMode.java`:

```java
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
```

`ModeMatrixPolicy.java`:

```java
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

  /** Advertised AE ranges as {lower, upper} pairs; true when any covers the rate. */
  static boolean fpsRangeCovers(int[][] ranges, int rate) {
    if (ranges == null) return false;
    for (int[] range : ranges)
      if (range != null && range.length == 2 && rate >= range[0] && rate <= range[1]) return true;
    return false;
  }

  /** The advertised range containing the rate with the lowest upper bound; null when none. */
  static int[] aeRange(int[][] ranges, int rate) {
    if (ranges == null) return null;
    int[] best = null;
    for (int[] range : ranges) {
      if (range == null || range.length != 2) continue;
      if (rate < range[0] || rate > range[1]) continue;
      if (best == null || range[1] < best[1] || (range[1] == best[1] && range[0] > best[0])) best = range;
    }
    return best;
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "app.stereobridge.VideoModeTest" --tests "app.stereobridge.ModeMatrixPolicyTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/stereobridge/VideoMode.java app/src/main/java/app/stereobridge/ModeMatrixPolicy.java app/src/test/java/app/stereobridge/VideoModeTest.java app/src/test/java/app/stereobridge/ModeMatrixPolicyTest.java
git commit -m "Add video mode values and negotiation ordering"
```

---

### Task 2: Preset bitrates in VideoCapabilityPolicy

**Files:**
- Modify: `app/src/main/java/app/stereobridge/VideoCapabilityPolicy.java:17-20`
- Test: `app/src/test/java/app/stereobridge/VideoCapabilityPolicyTest.java`

- [ ] **Step 1: Write the failing test**

```java
package app.stereobridge;
import static org.junit.Assert.*;
import org.junit.Test;
public class VideoCapabilityPolicyTest {
 @Test public void usesPixelCalibratedPresets() {
  assertEquals(45_000_000, VideoCapabilityPolicy.bitrate(3840, 2160, 30, 0, 100_000_000));
  assertEquals(80_000_000, VideoCapabilityPolicy.bitrate(3840, 2160, 60, 0, 100_000_000));
  assertEquals(30_000_000, VideoCapabilityPolicy.bitrate(1920, 1080, 60, 0, 100_000_000));
  assertEquals(20_000_000, VideoCapabilityPolicy.bitrate(1920, 1080, 30, 0, 100_000_000));
 }
 @Test public void keepsUnmodeledSizesOnTheLegacyFormula() {
  assertEquals(3_456_000, VideoCapabilityPolicy.bitrate(1280, 720, 30, 0, 8_000_000));
  assertEquals(288_000, VideoCapabilityPolicy.bitrate(320, 240, 30, 0, 8_000_000));
 }
 @Test public void clampsToEncoderRange() {
  assertEquals(20_000_000, VideoCapabilityPolicy.bitrate(3840, 2160, 30, 0, 20_000_000));
  assertEquals(50_000_000, VideoCapabilityPolicy.bitrate(1920, 1080, 30, 50_000_000, 100_000_000));
 }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.stereobridge.VideoCapabilityPolicyTest"`
Expected: FAIL (45 Mbps assertion fails with the old formula).

- [ ] **Step 3: Replace the bitrate implementation**

Replace the existing `bitrate` method in `VideoCapabilityPolicy.java`:

```java
  static int bitrate(int width, int height, int rate, int lower, int upper) {
    long desired;
    if (width >= 3840) desired = rate >= 50 ? 80_000_000L : 45_000_000L;
    else if (width >= 1920) desired = rate >= 50 ? 30_000_000L : 20_000_000L;
    else desired = Math.max(256000L, Math.min(8000000L, (long) width * height * rate / 8));
    return (int) Math.max(lower, Math.min(upper, desired));
  }
```

Add the calibration note as a comment above it: `// Presets calibrated against a real Pixel Camera 4K30 HDR file (~43 Mbps).`

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "app.stereobridge.VideoCapabilityPolicyTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/stereobridge/VideoCapabilityPolicy.java app/src/test/java/app/stereobridge/VideoCapabilityPolicyTest.java
git commit -m "Use Pixel-calibrated video bitrate presets"
```

---

### Task 3: StabilizationPolicy and ZoomPolicy

**Files:**
- Create: `app/src/main/java/app/stereobridge/StabilizationPolicy.java`
- Create: `app/src/main/java/app/stereobridge/ZoomPolicy.java`
- Test: `app/src/test/java/app/stereobridge/StabilizationPolicyTest.java`
- Test: `app/src/test/java/app/stereobridge/ZoomPolicyTest.java`

- [ ] **Step 1: Write the failing tests**

`StabilizationPolicyTest.java`:

```java
package app.stereobridge;
import static org.junit.Assert.*;
import android.hardware.camera2.CaptureRequest;
import org.junit.Test;
public class StabilizationPolicyTest {
 @Test public void prefersPreviewStabilizationThenOn() {
  assertEquals(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION,
      StabilizationPolicy.videoStabilization(new int[] {
          CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
          CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON,
          CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION}));
  assertEquals(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON,
      StabilizationPolicy.videoStabilization(new int[] {
          CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
          CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON}));
 }
 @Test public void reportsUnsupportedWithoutAdvertisedModes() {
  assertEquals(StabilizationPolicy.NONE, StabilizationPolicy.videoStabilization(null));
  assertEquals(StabilizationPolicy.NONE,
      StabilizationPolicy.videoStabilization(new int[] {CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF}));
  assertEquals(StabilizationPolicy.NONE, StabilizationPolicy.videoStabilization(new int[0]));
 }
 @Test public void opticalStabilizationRequiresAdvertisedOn() {
  assertTrue(StabilizationPolicy.opticalStabilization(
      new int[] {CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON}));
  assertFalse(StabilizationPolicy.opticalStabilization(
      new int[] {CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF}));
  assertFalse(StabilizationPolicy.opticalStabilization(null));
 }
}
```

`ZoomPolicyTest.java`:

```java
package app.stereobridge;
import static org.junit.Assert.*;
import org.junit.Test;
public class ZoomPolicyTest {
 @Test public void clampsToRange() {
  assertEquals(1f, ZoomPolicy.clamp(0.5f, 1f, 8f), 0f);
  assertEquals(8f, ZoomPolicy.clamp(12f, 1f, 8f), 0f);
  assertEquals(2.5f, ZoomPolicy.clamp(2.5f, 1f, 8f), 0f);
  assertEquals(1f, ZoomPolicy.clamp(2f, 1f, 0.5f), 0f);
 }
 @Test public void cropRegionIsCenteredAndInverseScaled() {
  float[] full = ZoomPolicy.cropRegion(1f, 4000f, 3000f);
  assertArrayEquals(new float[] {0f, 0f, 4000f, 3000f}, full, 0.001f);
  float[] half = ZoomPolicy.cropRegion(2f, 4000f, 3000f);
  assertArrayEquals(new float[] {1000f, 750f, 3000f, 2250f}, half, 0.001f);
 }
 @Test public void subUnitZoomBehavesAsOne() {
  assertArrayEquals(ZoomPolicy.cropRegion(1f, 4000f, 3000f), ZoomPolicy.cropRegion(0.2f, 4000f, 3000f), 0.001f);
 }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "app.stereobridge.StabilizationPolicyTest" --tests "app.stereobridge.ZoomPolicyTest"`
Expected: FAIL (classes do not exist).

- [ ] **Step 3: Write the implementations**

`StabilizationPolicy.java`:

```java
package app.stereobridge;

import android.hardware.camera2.CaptureRequest;

/** Pure stabilization request choice from advertised mode lists. */
final class StabilizationPolicy {
  static final int NONE = -1;

  /** Session-level video stabilization: PREVIEW_STABILIZATION when offered, else ON, else NONE. */
  static int videoStabilization(int[] availableModes) {
    if (availableModes == null) return NONE;
    boolean preview = false, on = false;
    for (int mode : availableModes) {
      if (mode == CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION) preview = true;
      else if (mode == CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON) on = true;
    }
    if (preview) return CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION;
    if (on) return CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON;
    return NONE;
  }

  static boolean opticalStabilization(int[] availableModes) {
    if (availableModes == null) return false;
    for (int mode : availableModes)
      if (mode == CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON) return true;
    return false;
  }
}
```

`ZoomPolicy.java`:

```java
package app.stereobridge;

/** Pure zoom clamping and API 29 crop-region arithmetic. */
final class ZoomPolicy {
  static float clamp(float desired, float min, float max) {
    if (max < min) return min;
    return Math.max(min, Math.min(max, desired));
  }

  /** Centered crop region {left, top, right, bottom} in active-array pixels for API 29. */
  static float[] cropRegion(float zoom, float activeWidth, float activeHeight) {
    float safe = Math.max(1f, zoom);
    float width = activeWidth / safe, height = activeHeight / safe;
    float left = (activeWidth - width) / 2f, top = (activeHeight - height) / 2f;
    return new float[] {left, top, left + width, top + height};
  }
}
```

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "app.stereobridge.StabilizationPolicyTest" --tests "app.stereobridge.ZoomPolicyTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/stereobridge/StabilizationPolicy.java app/src/main/java/app/stereobridge/ZoomPolicy.java app/src/test/java/app/stereobridge/StabilizationPolicyTest.java app/src/test/java/app/stereobridge/ZoomPolicyTest.java
git commit -m "Add stabilization and zoom policies"
```

---

### Task 4: FocusPointMapper

Execution note (reconciled during review, commit dd54299): the mapper inverts the full preview transform (center-crop scale + view-space front mirror) and exposes `activePoint(u, v, sensorOrientation)` for active-array conversion. It returns oriented preview space from `sensorPoint(viewX, viewY, viewWidth, viewHeight, bufferWidth, bufferHeight, sensorOrientation, displayDegrees, front)` and normalized active-array coordinates from `activePoint`. The reference code below is superseded by the committed implementation.

**Files:**
- Create: `app/src/main/java/app/stereobridge/FocusPointMapper.java`
- Test: `app/src/test/java/app/stereobridge/FocusPointMapperTest.java`

- [ ] **Step 1: Write the failing test**

```java
package app.stereobridge;
import static org.junit.Assert.*;
import org.junit.Test;
public class FocusPointMapperTest {
 @Test public void centerStaysCenteredAtEveryRotation() {
  for (int display : new int[] {0, 90, 180, 270}) {
   float[] p = FocusPointMapper.sensorPoint(500f, 400f, 1000, 800, display, false);
   assertEquals(0.5f, p[0], 0.001f);
   assertEquals(0.5f, p[1], 0.001f);
  }
 }
 @Test public void rotatesViewIntoUnrotatedSensorSpace() {
  float[] p = FocusPointMapper.sensorPoint(100f, 400f, 1000, 800, 90, false);
  assertEquals(0.5f, p[0], 0.001f);
  assertEquals(0.1f, p[1], 0.001f);
  float[] q = FocusPointMapper.sensorPoint(100f, 400f, 1000, 800, 270, false);
  assertEquals(0.5f, q[0], 0.001f);
  assertEquals(0.9f, q[1], 0.001f);
 }
 @Test public void mirrorsHorizontallyForFrontCamera() {
  float[] back = FocusPointMapper.sensorPoint(100f, 400f, 1000, 800, 0, false);
  float[] front = FocusPointMapper.sensorPoint(100f, 400f, 1000, 800, 0, true);
  assertEquals(0.1f, back[0], 0.001f);
  assertEquals(0.9f, front[0], 0.001f);
  assertEquals(back[1], front[1], 0.001f);
 }
 @Test public void clampsOutsideTouchesAndGuardsEmptyView() {
  float[] p = FocusPointMapper.sensorPoint(-50f, 900f, 1000, 800, 0, false);
  assertEquals(0f, p[0], 0.001f);
  assertEquals(1f, p[1], 0.001f);
  float[] q = FocusPointMapper.sensorPoint(0f, 0f, 0, 0, 0, false);
  assertEquals(0.5f, q[0], 0.001f);
  assertEquals(0.5f, q[1], 0.001f);
 }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.stereobridge.FocusPointMapperTest"`
Expected: FAIL (class does not exist).

- [ ] **Step 3: Write the implementation**

`FocusPointMapper.java`:

```java
package app.stereobridge;

/** Maps a preview touch point to normalized sensor coordinates [0,1]. */
final class FocusPointMapper {
  static float[] sensorPoint(
      float viewX, float viewY, int viewWidth, int viewHeight, int displayDegrees, boolean front) {
    float nx = viewWidth <= 0 ? 0.5f : viewX / viewWidth;
    float ny = viewHeight <= 0 ? 0.5f : viewY / viewHeight;
    float vx = nx - 0.5f, vy = ny - 0.5f;
    double angle = Math.toRadians(displayDegrees);
    float bx = (float) (vx * Math.cos(angle) - vy * Math.sin(angle)) + 0.5f;
    float by = (float) (vx * Math.sin(angle) + vy * Math.cos(angle)) + 0.5f;
    if (front) bx = 1f - bx;
    return new float[] {Math.max(0f, Math.min(1f, bx)), Math.max(0f, Math.min(1f, by))};
  }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "app.stereobridge.FocusPointMapperTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/stereobridge/FocusPointMapper.java app/src/test/java/app/stereobridge/FocusPointMapperTest.java
git commit -m "Add tap-to-focus point mapping"
```

---

### Task 5: HdrPolicy, CodecPolicy, CameraCapabilities

Execution note (reconciled during review, commits 9a322a7 + cc76f4d): `CodecPolicy.order` accepts both the persisted settings token (`"hevc"`) and the MIME constant (`"video/hevc"`), since the encoder layer passes MIMEs and the service passes settings values.

**Files:**
- Create: `app/src/main/java/app/stereobridge/HdrPolicy.java`, `app/src/main/java/app/stereobridge/CodecPolicy.java`, `app/src/main/java/app/stereobridge/CameraCapabilities.java`
- Test: `app/src/test/java/app/stereobridge/HdrPolicyTest.java`, `app/src/test/java/app/stereobridge/CodecPolicyTest.java`, `app/src/test/java/app/stereobridge/CameraCapabilitiesTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// HdrPolicyTest.java
package app.stereobridge;
import static org.junit.Assert.*;
import org.junit.Test;
public class HdrPolicyTest {
 @Test public void offersHdrOnlyWhenCameraAndEncoderAgree() {
  assertTrue(HdrPolicy.offer(true, true));
  assertFalse(HdrPolicy.offer(true, false));
  assertFalse(HdrPolicy.offer(false, true));
  assertFalse(HdrPolicy.offer(false, false));
 }
 @Test public void hdrIsLimitedToThirtyFps() {
  assertTrue(HdrPolicy.allowedAtRate(30));
  assertFalse(HdrPolicy.allowedAtRate(24));
  assertFalse(HdrPolicy.allowedAtRate(60));
 }
}

// CodecPolicyTest.java
package app.stereobridge;
import static org.junit.Assert.*;
import org.junit.Test;
public class CodecPolicyTest {
 @Test public void avcRequestNeverTriesHevc() {
  assertArrayEquals(new String[] {"video/avc"}, CodecPolicy.order("avc"));
  assertArrayEquals(new String[] {"video/avc"}, CodecPolicy.order(""));
 }
 @Test public void hevcRequestFallsBackToAvc() {
  assertArrayEquals(new String[] {"video/hevc", "video/avc"}, CodecPolicy.order("hevc"));
 }
 @Test public void labelsAreHumanReadable() {
  assertEquals("AVC", CodecPolicy.label("video/avc"));
  assertEquals("HEVC", CodecPolicy.label("video/hevc"));
 }
}

// CameraCapabilitiesTest.java
package app.stereobridge;
import static org.junit.Assert.*;
import org.junit.Test;
public class CameraCapabilitiesTest {
 @Test public void noneIsFullyDisabled() {
  CameraCapabilities none = CameraCapabilities.NONE;
  assertEquals(StabilizationPolicy.NONE, none.stabilizationMode);
  assertFalse(none.opticalStabilization || none.flash || none.zoom || none.hlgCamera || none.hlgEncoder);
  assertEquals(1f, none.zoomMin, 0f);
  assertEquals(1f, none.zoomMax, 0f);
  assertEquals(0, none.maxAfRegions);
 }
 @Test public void describesEveryField() {
  CameraCapabilities caps = new CameraCapabilities(2, true, true, true, 1f, 8f, 3, true, true);
  String text = caps.describe();
  assertTrue(text.contains("stabilization=2"));
  assertTrue(text.contains("optical=true"));
  assertTrue(text.contains("zoom=1.0-8.0"));
  assertTrue(text.contains("flash=true"));
  assertTrue(text.contains("afRegions=3"));
  assertTrue(text.contains("hdrCamera=true"));
  assertTrue(text.contains("hdrEncoder=true"));
 }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "app.stereobridge.HdrPolicyTest" --tests "app.stereobridge.CodecPolicyTest" --tests "app.stereobridge.CameraCapabilitiesTest"`
Expected: FAIL (classes do not exist).

- [ ] **Step 3: Write the implementations**

```java
// HdrPolicy.java
package app.stereobridge;

/** Pure 10-bit HLG offer rules matching Pixel Camera's own constraints. */
final class HdrPolicy {
  static boolean offer(boolean cameraOffersHlg, boolean encoderAcceptsHlg) {
    return cameraOffersHlg && encoderAcceptsHlg;
  }
  static boolean allowedAtRate(int rate) { return rate == 30; }
}

// CodecPolicy.java
package app.stereobridge;

/** Pure codec preference order; encoder selection validates what is actually supported. */
final class CodecPolicy {
  static final String AVC = "video/avc", HEVC = "video/hevc";
  static String[] order(String requested) {
    return HEVC.equals(requested) ? new String[] {HEVC, AVC} : new String[] {AVC};
  }
  static String label(String mime) { return HEVC.equals(mime) ? "HEVC" : "AVC"; }
}

// CameraCapabilities.java
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
```

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "app.stereobridge.HdrPolicyTest" --tests "app.stereobridge.CodecPolicyTest" --tests "app.stereobridge.CameraCapabilitiesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/stereobridge/HdrPolicy.java app/src/main/java/app/stereobridge/CodecPolicy.java app/src/main/java/app/stereobridge/CameraCapabilities.java app/src/test/java/app/stereobridge/HdrPolicyTest.java app/src/test/java/app/stereobridge/CodecPolicyTest.java app/src/test/java/app/stereobridge/CameraCapabilitiesTest.java
git commit -m "Add HDR, codec and camera capability rules"
```

---

### Task 6: Encoder upgrades (HEVC, High profile, HDR)

**Files:**
- Modify: `app/src/main/java/app/stereobridge/OfflineVideoEncoder.java`

Framework-bound (no unit tests); verification is compile plus the emulator smoke in Task 10.

- [ ] **Step 1: Extend `videoFormat` with mime/profile selection**

Replace the existing `static MediaFormat videoFormat(MediaCodecInfo info, int width, int height, int requestedFps)` (lines 64–93) and `supportsVideo` (lines 95–99) with:

```java
  static MediaFormat videoFormat(MediaCodecInfo info, String mime, int width, int height, int requestedFps, boolean hdr) {
    try {
      MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mime);
      boolean surface = false;
      for (int color : caps.colorFormats)
        if (color == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) surface = true;
      if (!surface) return null;
      MediaCodecInfo.VideoCapabilities v = caps.getVideoCapabilities();
      if (!v.isSizeSupported(width, height)) return null;
      android.util.Range<Double> rates = v.getSupportedFrameRatesFor(width, height);
      int rate = VideoCapabilityPolicy.rate(requestedFps, rates.getLower(), rates.getUpper());
      if (rate != requestedFps) return null;
      MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
      format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
      format.setInteger(MediaFormat.KEY_FRAME_RATE, rate);
      format.setInteger(MediaFormat.KEY_BIT_RATE, VideoCapabilityPolicy.bitrate(width, height, rate, v.getBitrateRange().getLower(), v.getBitrateRange().getUpper()));
      format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
      int profile = 0, level = 0;
      for (MediaCodecInfo.CodecProfileLevel candidate : caps.profileLevels) {
        if (CodecPolicy.HEVC.equals(mime)) {
          boolean family = hdr
              ? candidate.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                  || candidate.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
              : candidate.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain;
          if (family && candidate.level > level) { profile = candidate.profile; level = candidate.level; }
        } else {
          boolean family = candidate.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
              || candidate.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;
          if (family && candidate.level > level) { profile = candidate.profile; level = candidate.level; }
        }
      }
      if (profile == 0) return null;
      format.setInteger(MediaFormat.KEY_PROFILE, profile);
      // Legacy OMX requires a level whenever a profile is set (Android 10 ACodec regression).
      format.setInteger(MediaFormat.KEY_LEVEL, level);
      return caps.isFormatSupported(format) ? format : null;
    } catch (IllegalArgumentException e) { return null; }
  }

  static boolean supportsVideo(int width, int height, int rate) {
    return supportsVideo(width, height, rate, CodecPolicy.AVC);
  }

  static boolean supportsVideo(int width, int height, int rate, String mime) {
    for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos())
      if (info.isEncoder() && videoFormat(info, mime, width, height, rate, false) != null) return true;
    return false;
  }

  static boolean supportsHlgEncode(int width, int height, int rate) {
    if (android.os.Build.VERSION.SDK_INT < 33 || rate != 30) return false;
    for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos())
      if (info.isEncoder() && videoFormat(info, CodecPolicy.HEVC, width, height, rate, true) != null) return true;
    return false;
  }
```

Note: the previous code preferred `AVCProfileBaseline` only; the new AVC path now picks the highest advertised level across High/Baseline, which upgrades the SDR default to High profile automatically. HEVC HLG color metadata travels via the camera surface dataspace set by the dynamic-range profile (Task 9); the physical run in Task 13 verifies the written file shows `arib-std-b67`.

- [ ] **Step 2: Extend `configuredVideo` and the constructor**

Replace `private static MediaCodec configuredVideo(int width, int height, int rate)` with:

```java
  private static MediaCodec configuredVideo(int width, int height, int rate, String mime, boolean hdr) throws IOException {
    Throwable last = null;
    for (String candidateMime : hdr ? new String[] {CodecPolicy.HEVC} : CodecPolicy.order(mime)) {
      boolean candidateHdr = hdr;
      for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
        if (!info.isEncoder()) continue;
        MediaFormat format = videoFormat(info, candidateMime, width, height, rate, candidateHdr);
        if (format == null) continue;
        MediaCodec codec = null;
        try {
          codec = MediaCodec.createByCodecName(info.getName());
          codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
          return codec;
        } catch (Exception e) { last = e; if (codec != null) codec.release(); }
      }
    }
    throw new IOException("No compatible surface encoder for " + mime + (hdr ? " HDR" : ""), last);
  }
```

Update the constructor chain so the file descriptor constructor gains `String mime, boolean hdr`:

```java
  public OfflineVideoEncoder(FileDescriptor output, int width, int height, int rotation, int rate) throws IOException {
    this(null, java.util.Objects.requireNonNull(output), width, height, rotation, rate, CodecPolicy.AVC, false);
  }

  public OfflineVideoEncoder(FileDescriptor output, int width, int height, int rotation, int rate, String mime, boolean hdr) throws IOException {
    this(null, java.util.Objects.requireNonNull(output), width, height, rotation, rate, mime, hdr);
  }
```

and the private constructor calls `configuredVideo(width, height, rate, mime, hdr)`.

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/stereobridge/OfflineVideoEncoder.java
git commit -m "Support HEVC High profile and HLG encoding in the video encoder"
```

---

### Task 7: Mode negotiation in OfflineRecorderService

Execution note (reconciled during review, commits 8f8b763 + 6eafb2b): retry keys are scoped per camera and codec (`id:WxH@rate:mime`), codecs are retried per mode, negotiation state resets and the new fields are `volatile`, `rejectedModes` is a `ConcurrentHashMap.newKeySet()`, and the now-unused `captureRate`/`preferRange` helpers were removed. The snippets below show the pre-review form; the committed version is authoritative.

**Files:**
- Modify: `app/src/main/java/app/stereobridge/OfflineRecorderService.java` (`chooseCamera`, fields)
- Modify: `app/src/main/java/app/stereobridge/CameraGeometry.java` (remove `within1080`)
- Modify: `app/src/test/java/app/stereobridge/CameraGeometryTest.java` (remove `within1080` assertions)

- [ ] **Step 1: Add fields and helpers**

Near the existing `selectedMode` field (line ~148), add:

```java
  private VideoMode mode;
  private List<VideoMode> feasibleModes = Collections.emptyList();
  private String codecMime = CodecPolicy.AVC;
  private boolean hdrActive;
```

Add these methods next to `chooseCamera`:

```java
  private String firstSupportedCodec(VideoMode candidate) {
    for (String mime : CodecPolicy.order(settings.get("video_codec", "avc")))
      if (OfflineVideoEncoder.supportsVideo(candidate.width, candidate.height, candidate.rate, mime))
        return mime;
    return null;
  }

  private static int facingRank(CameraManager manager, String id, boolean wantFront) {
    try {
      Integer facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
      boolean front = Objects.equals(facing, CameraCharacteristics.LENS_FACING_FRONT);
      boolean back = Objects.equals(facing, CameraCharacteristics.LENS_FACING_BACK);
      if (wantFront) return front ? 0 : back ? 1 : 2;
      return back ? 0 : front ? 1 : 2;
    } catch (CameraAccessException e) { return 3; }
  }
```

- [ ] **Step 2: Replace `chooseCamera`**

Replace the whole method (currently lines 707–768) with:

```java
  private void chooseCamera(CameraManager manager) throws CameraAccessException, IOException {
    if (cameraId != null && (camera != null || opening)) return;
    boolean wantFront = "front".equals(settings.get("facing", "back"));
    List<String> ids = new ArrayList<>(Arrays.asList(manager.getCameraIdList()));
    ids.sort((a, b) -> Integer.compare(facingRank(manager, a, wantFront), facingRank(manager, b, wantFront)));
    selectedMode = null;
    for (String id : ids) {
      CameraCharacteristics c;
      try { c = manager.getCameraCharacteristics(id); } catch (CameraAccessException e) { continue; }
      StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      if (map == null) continue;
      Size[] recordSizes = map.getOutputSizes(MediaCodec.class);
      Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
      if (recordSizes == null || previewSizes == null) continue;
      List<Size> recordList = Arrays.asList(recordSizes), previewList = Arrays.asList(previewSizes);
      Range<Integer>[] advertised = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
      int[][] ranges = null;
      if (advertised != null) {
        ranges = new int[advertised.length][2];
        for (int n = 0; n < advertised.length; n++) {
          ranges[n][0] = advertised[n].getLower();
          ranges[n][1] = advertised[n].getUpper();
        }
      }
      List<VideoMode> feasible = new ArrayList<>();
      for (VideoMode candidate : VideoMode.FALLBACK) {
        Size candidateSize = new Size(candidate.width, candidate.height);
        if (!recordList.contains(candidateSize) || !previewList.contains(candidateSize)) continue;
        if (!ModeMatrixPolicy.fpsRangeCovers(ranges, candidate.rate)) continue;
        if (firstSupportedCodec(candidate) == null) continue;
        feasible.add(candidate);
      }
      if (feasible.isEmpty()) continue;
      List<VideoMode> ordered = ModeMatrixPolicy.order(feasible, settings.get("video_mode", ""));
      VideoMode chosen = null;
      String chosenMime = null;
      for (VideoMode candidate : ordered) {
        String mime = firstSupportedCodec(candidate);
        if (preparingCamera && rejectedModes.contains(candidate.key() + ":" + mime)) continue;
        chosen = candidate;
        chosenMime = mime;
        break;
      }
      if (chosen == null) continue;
      mode = chosen;
      codecMime = chosenMime;
      feasibleModes = ordered;
      selectedMode = chosen.key() + ":" + chosenMime;
      size = new Size(chosen.width, chosen.height);
      int[] ae = ModeMatrixPolicy.aeRange(ranges, chosen.rate);
      fps = new Range<>(ae[0], ae[1]);
      frameRate = chosen.rate;
      if (previewTexture != null) previewTexture.setDefaultBufferSize(chosen.width, chosen.height);
      Integer sensor = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
      sensorOrientation = sensor == null ? 90 : sensor;
      frontFacing = Objects.equals(c.get(CameraCharacteristics.LENS_FACING), CameraCharacteristics.LENS_FACING_FRONT);
      rotation = CameraGeometry.rotation(sensorOrientation, displayDegrees, frontFacing);
      realtimeSensor = Objects.equals(c.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE), CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME);
      return;
    }
    throw new IOException("No camera and encoder share a supported video mode.");
  }
```

- [ ] **Step 3: Pass negotiated codec and HDR flag to the encoder**

In `record()` (line 334), replace:

```java
          current = new OfflineVideoEncoder(take.descriptor.getFileDescriptor(), size.getWidth(), size.getHeight(), rotation, frameRate);
```

with:

```java
          current = new OfflineVideoEncoder(take.descriptor.getFileDescriptor(), size.getWidth(), size.getHeight(), rotation, frameRate, codecMime, hdrActive);
```

- [ ] **Step 4: Delete `within1080`**

Remove from `CameraGeometry.java`:

```java
 public static boolean within1080(int width,int height) { return width>0 && height>0 && Math.max(width,height)<=1920 && Math.min(width,height)<=1080; }
```

and remove the test method `acceptsPortraitAndNonWideSizesBelowCap` from `CameraGeometryTest.java`.

- [ ] **Step 5: Run the unit suite and build**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS/build successful; existing tests green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/stereobridge/OfflineRecorderService.java app/src/main/java/app/stereobridge/CameraGeometry.java app/src/test/java/app/stereobridge/CameraGeometryTest.java
git commit -m "Negotiate video modes from the capability matrix"
```

---

### Task 8: Session-level stabilization and live camera controls

**Files:**
- Modify: `app/src/main/java/app/stereobridge/OfflineRecorderService.java`

- [ ] **Step 1: Add capability and control fields**

Next to the Task 7 fields add:

```java
  private volatile CameraCapabilities capabilities = CameraCapabilities.NONE;
  private int stabMode = StabilizationPolicy.NONE;
  private volatile boolean oisOffered, zoomSupported, torchOn, focusLock;
  private volatile float zoomRatio = 1f;
  private float[] focusPoint;
  private android.graphics.Rect activeArray;
  private List<Surface> sessionSurfaces = Collections.emptyList();
  private boolean sessionRecording;
  private CameraCaptureSession.CaptureCallback sessionCallback;
  private Runnable focusRevert;
```

In `onCreate()` after `cameraHandler = ...` add:

```java
    focusRevert = () -> {
      if (!focusLock) {
        focusPoint = null;
        applyControls();
      }
    };
```

- [ ] **Step 2: Read capabilities during negotiation**

In `chooseCamera`, just before `return;` (after `realtimeSensor = ...`), add:

```java
      activeArray = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
      Integer[] stabilizationModes = c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
      Integer[] opticalModes = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
      android.util.Range<Float> zoomRange =
          Build.VERSION.SDK_INT >= 30 ? c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) : null;
      Float maxDigitalZoom = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
      boolean zoomAvailable = zoomRange != null
          || (activeArray != null && maxDigitalZoom != null && maxDigitalZoom > 1.05f);
      float zoomMin = zoomRange != null ? zoomRange.getLower() : 1f;
      float zoomMax = zoomRange != null
          ? zoomRange.getUpper()
          : (maxDigitalZoom == null ? 1f : Math.min(maxDigitalZoom, 8f));
      Integer afRegions = c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
      capabilities = new CameraCapabilities(
          StabilizationPolicy.videoStabilization(toInts(stabilizationModes)),
          StabilizationPolicy.opticalStabilization(toInts(opticalModes)),
          Boolean.TRUE.equals(c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)),
          zoomAvailable,
          zoomMin,
          zoomMax,
          afRegions == null ? 0 : afRegions,
          hlgCamera(c),
          OfflineVideoEncoder.supportsHlgEncode(chosen.width, chosen.height, chosen.rate));
      stabMode = capabilities.stabilizationMode;
      oisOffered = capabilities.opticalStabilization;
      zoomSupported = capabilities.zoom;
      android.util.Log.i("StereoRecorder", "Camera capabilities: " + capabilities.describe() + " mode=" + chosen.label());
```

Add the helpers:

```java
  private static int[] toInts(Integer[] values) {
    if (values == null) return null;
    int[] out = new int[values.length];
    for (int n = 0; n < values.length; n++) out[n] = values[n];
    return out;
  }

  private static boolean hlgCamera(CameraCharacteristics c) {
    if (Build.VERSION.SDK_INT < 33) return false;
    int[] abilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
    boolean tenBit = false;
    if (abilities != null)
      for (int ability : abilities)
        if (ability == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT) tenBit = true;
    if (!tenBit) return false;
    android.hardware.camera2.params.DynamicRangeProfiles profiles =
        c.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES);
    return profiles != null && profiles.getSupportedProfiles().contains(DynamicRangeProfiles.HLG10);
  }
```

(Import `android.hardware.camera2.CameraMetadata`, `android.hardware.camera2.params.DynamicRangeProfiles`, `android.hardware.camera2.params.MeteringRectangle` and `android.hardware.camera2.params.OutputConfiguration`; `CameraCaptureSession`, `CaptureRequest` and `CameraDevice` are already covered by the existing `android.hardware.camera2.*` wildcard import.)

- [ ] **Step 3: Base request builder and repeat-request updates**

Add:

```java
  private CaptureRequest.Builder baseRequest(int template) throws CameraAccessException {
    CaptureRequest.Builder request = camera.createCaptureRequest(template);
    request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
    if (fps != null) request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps);
    if (stabMode != StabilizationPolicy.NONE)
      request.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, stabMode);
    // Explicit OIS only when EIS is unavailable: an EIS request already engages the HAL's fused
    // OIS+EIS on Pixel-class devices, and AOSP warns against explicitly combining both.
    if (oisOffered && stabMode == StabilizationPolicy.NONE)
      request.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
    request.set(CaptureRequest.CONTROL_AF_MODE,
        focusLock ? CaptureRequest.CONTROL_AF_MODE_AUTO : CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
    if (focusPoint != null && activeArray != null && capabilities.maxAfRegions > 0) {
      android.util.Size regionSize =
          new android.util.Size(Math.max(1, activeArray.width() / 10), Math.max(1, activeArray.height() / 10));
      int left = Math.round(focusPoint[0] * activeArray.width()) - regionSize.getWidth() / 2;
      int top = Math.round(focusPoint[1] * activeArray.height()) - regionSize.getHeight() / 2;
      left = Math.max(activeArray.left, Math.min(activeArray.right - regionSize.getWidth(), left));
      top = Math.max(activeArray.top, Math.min(activeArray.bottom - regionSize.getHeight(), top));
      MeteringRectangle rect = new MeteringRectangle(
          left, top, regionSize.getWidth(), regionSize.getHeight(), MeteringRectangle.METERING_WEIGHT_MAX);
      request.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[] {rect});
      request.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[] {rect});
    }
    if (focusLock) {
      request.set(CaptureRequest.CONTROL_AE_LOCK, true);
      request.set(CaptureRequest.CONTROL_AWB_LOCK, true);
    }
    if (torchOn) request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
    if (zoomRatio > 1f && zoomSupported) {
      if (Build.VERSION.SDK_INT >= 30) request.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio);
      else if (activeArray != null) {
        float[] region = ZoomPolicy.cropRegion(zoomRatio, activeArray.width(), activeArray.height());
        request.set(CaptureRequest.SCALER_CROP_REGION, new android.graphics.Rect(
            Math.round(region[0]), Math.round(region[1]), Math.round(region[2]), Math.round(region[3])));
      }
    }
    return request;
  }

  private void applyControls() {
    CameraCaptureSession session = cameraSession;
    if (session == null || camera == null || sessionCallback == null) return;
    try {
      CaptureRequest.Builder request =
          baseRequest(sessionRecording ? CameraDevice.TEMPLATE_RECORD : CameraDevice.TEMPLATE_PREVIEW);
      for (Surface surface : sessionSurfaces) request.addTarget(surface);
      session.setRepeatingRequest(request.build(), sessionCallback, cameraHandler);
    } catch (Exception ignored) {
      // Live control updates are best effort; the base session remains valid.
    }
  }
```

- [ ] **Step 4: Rewire `configure()` to the base builder**

In `configure()`, replace the request construction block with:

```java
      CaptureRequest.Builder request =
          baseRequest(target == null ? CameraDevice.TEMPLATE_PREVIEW : CameraDevice.TEMPLATE_RECORD);
      for (Surface s : surfaces) request.addTarget(s);
      sessionSurfaces = new ArrayList<>(surfaces);
      sessionRecording = target != null;
      sessionCallback =
          new CameraCaptureSession.CaptureCallback() {
            boolean first = true;

            @Override
            public void onCaptureStarted(
                CameraCaptureSession s, CaptureRequest req, long timestamp, long frame) {
              if (first && target != null) {
                first = false;
                target.videoStarted(
                    realtimeSensor ? timestamp : SystemClock.elapsedRealtimeNanos());
                if (cameraReady != null) cameraReady.countDown();
              }
            }
          };
```

and where the session previously received its inline CaptureCallback, use `sessionCallback`:

```java
                session.setRepeatingRequest(request.build(), sessionCallback, cameraHandler);
```

In `closeCamera()` add `torchOn = false;` beside `cameraSession = null;` handling.

- [ ] **Step 5: Binder live-control methods**

Add to `LocalBinder`:

```java
    public CameraCapabilities capabilities() { return capabilities; }
    public String modeLabel() {
      VideoMode current = mode;
      return current == null ? "" : current.label() + (hdrActive ? " · HDR" : "");
    }
    public float zoom() { return zoomRatio; }
    public boolean torch() { return torchOn; }
    public boolean focusLocked() { return focusLock; }
    public boolean modeFeasible(String key) {
      VideoMode candidate = VideoMode.fromKey(key);
      return candidate != null && feasibleModes.contains(candidate);
    }
    public void setZoom(float ratio) {
      cameraHandler.post(() -> {
        if (!zoomSupported) return;
        zoomRatio = ZoomPolicy.clamp(ratio, capabilities.zoomMin, Math.min(capabilities.zoomMax, 8f));
        applyControls();
      });
    }
    public void setFocusPoint(float nx, float ny) {
      cameraHandler.post(() -> {
        if (capabilities.maxAfRegions <= 0) return;
        focusPoint = new float[] {nx, ny};
        cameraHandler.removeCallbacks(focusRevert);
        if (!focusLock) cameraHandler.postDelayed(focusRevert, 4000);
        applyControls();
      });
    }
    public void setFocusLock(boolean locked) {
      cameraHandler.post(() -> {
        focusLock = locked;
        cameraHandler.removeCallbacks(focusRevert);
        if (locked && focusPoint == null) focusPoint = new float[] {0.5f, 0.5f};
        if (!locked) focusPoint = null;
        applyControls();
      });
    }
    public void setTorch(boolean on) {
      cameraHandler.post(() -> {
        if (!capabilities.flash) return;
        torchOn = on;
        applyControls();
      });
    }
    public void flip() {
      cameraHandler.post(() -> {
        if (worker != null) return;
        settings.set("facing", frontFacing ? "back" : "front");
        cameraId = null;
        zoomRatio = 1f;
        focusPoint = null;
        focusLock = false;
        torchOn = false;
        try {
          drainCameraForRetry();
        } catch (Exception e) {
          cameraFailure(message(e));
          return;
        }
        ensureCamera();
      });
    }
    public void requestMode(String modeKey, boolean hdr, String codec) {
      cameraHandler.post(() -> {
        if (worker != null) return;
        settings.set("video_mode", modeKey);
        settings.flag("video_hdr", hdr);
        settings.set("video_codec", codec);
        cameraId = null;
        zoomRatio = 1f;
        focusPoint = null;
        focusLock = false;
        try {
          drainCameraForRetry();
        } catch (Exception e) {
          cameraFailure(message(e));
          return;
        }
        ensureCamera();
      });
    }
```

- [ ] **Step 6: Build and run unit tests**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS/build successful.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/stereobridge/OfflineRecorderService.java
git commit -m "Apply stabilization and live camera controls at session level"
```

---

### Task 9: 10-bit HLG session path with single SDR fallback

Execution note (reconciled during review, commits 2a669b7 + 9273191): on a recording-session HLG rejection, `hdrSessionFailed` is set, the rejection key is rewritten to the effective HEVC codec, and the bounded startup retry recreates an SDR encoder (AVC) in the same mode. Preview-only failures never disable HDR, and there is no internal SDR fallback branch.

**Files:**
- Modify: `app/src/main/java/app/stereobridge/OfflineRecorderService.java`

- [ ] **Step 1: Track HDR state during negotiation**

Add field `private boolean hdrSessionFailed;` and in `record()` where `rejectedModes.clear()` is called (line ~317) add:

```java
      hdrSessionFailed = false;
```

In `chooseCamera`, after the capabilities block, add:

```java
      hdrActive = !hdrSessionFailed
          && settings.flag("video_hdr")
          && HdrPolicy.offer(capabilities.hlgCamera, capabilities.hlgEncoder)
          && HdrPolicy.allowedAtRate(chosen.rate);
```

- [ ] **Step 2: Configure both outputs with the same dynamic range profile**

In `configure()`, extract the state callback into a variable and branch on `hdrActive`. Replace the `camera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {...}, cameraHandler);` call with:

```java
      CameraCaptureSession.StateCallback stateCallback =
          new CameraCaptureSession.StateCallback() {
            public void onConfigured(CameraCaptureSession session) {
              if (version != configuration) {
                session.close();
                return;
              }
              cameraSession = session;
              try {
                session.setRepeatingRequest(request.build(), sessionCallback, cameraHandler);
              } catch (Exception e) {
                cameraFailure(message(e));
              }
            }

            public void onConfigureFailed(CameraCaptureSession session) {
              session.close();
              if (version != configuration) return;
              if (hdrActive) {
                hdrActive = false;
                hdrSessionFailed = true;
                android.util.Log.w("StereoRecorder", "HLG session rejected; using SDR for this session");
                configure();
                return;
              }
              cameraFailure("Camera capture configuration failed.");
            }

            public void onClosed(CameraCaptureSession session) {
              releaseSession.run();
            }
          };
      if (hdrActive && Build.VERSION.SDK_INT >= 33) {
        List<OutputConfiguration> outputs = new ArrayList<>();
        for (Surface surface : surfaces) {
          OutputConfiguration output = new OutputConfiguration(surface);
          output.setDynamicRangeProfile(DynamicRangeProfiles.HLG10);
          outputs.add(output);
        }
        camera.createCaptureSessionByOutputConfigurations(outputs, stateCallback, cameraHandler);
      } else {
        camera.createCaptureSession(surfaces, stateCallback, cameraHandler);
      }
```

Key behavior (from the AOSP `camera2-hdrvideo` sample): both preview and record outputs must carry the same profile or session configuration fails; the encoder was already created as HEVC Main10 by Task 7 + Task 6.

- [ ] **Step 3: Build and run unit tests**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS/build successful.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/stereobridge/OfflineRecorderService.java
git commit -m "Enable 10-bit HLG sessions with a single SDR fallback"
```

---

### Task 10: CameraControls overlay and mode chooser

**Files:**
- Create: `app/src/main/java/app/stereobridge/CameraControls.java`
- Create: `app/src/main/java/app/stereobridge/ModeChooserDialog.java`
- Modify: `app/src/main/java/app/stereobridge/MainActivity.java`

- [ ] **Step 1: Write `CameraControls.java`**

```java
package app.stereobridge;

import android.app.Activity;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Preview overlay: tap focus, pinch zoom, double-tap flip, and status chips. */
final class CameraControls {
  interface Listener {
    void onTap(float x, float y);
    void onZoom(float ratio);
    void onFlip();
    void onTorch();
    void onMode();
    void onLock();
  }

  final TextView modeChip, zoomChip, torchChip, flipChip, lockChip;
  private final FocusRing ring;
  private final GestureDetector gestures;
  private final ScaleGestureDetector scale;
  private float pinchStart = 1f, currentZoom = 1f;
  private final Listener listener;

  CameraControls(Activity activity, FrameLayout frame, Ui ui, Listener listener) {
    this.listener = listener;
    modeChip = chip(activity, ui, 13);
    zoomChip = chip(activity, ui, 13);
    torchChip = chip(activity, ui, 13);
    flipChip = chip(activity, ui, 13);
    lockChip = chip(activity, ui, 13);
    ring = new FocusRing(activity, ui.border, ui.accent);
    frame.addView(ring, new FrameLayout.LayoutParams(-1, -1));
    addChip(frame, modeChip, Gravity.TOP | Gravity.START);
    addChip(frame, flipChip, Gravity.TOP | Gravity.END);
    addChip(frame, torchChip, Gravity.BOTTOM | Gravity.START);
    addChip(frame, zoomChip, Gravity.BOTTOM | Gravity.END);
    addChip(frame, lockChip, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    modeChip.setOnClickListener(v -> listener.onMode());
    flipChip.setOnClickListener(v -> listener.onFlip());
    torchChip.setOnClickListener(v -> listener.onTorch());
    lockChip.setOnClickListener(v -> listener.onLock());
    zoomChip.setOnClickListener(v -> {
      currentZoom = 1f;
      listener.onZoom(1f);
    });
    gestures =
        new GestureDetector(
            activity,
            new GestureDetector.SimpleOnGestureListener() {
              @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                ring.show(e.getX(), e.getY(), false);
                listener.onTap(e.getX(), e.getY());
                return true;
              }

              @Override public boolean onDoubleTap(MotionEvent e) {
                listener.onFlip();
                return true;
              }
            });
    scale =
        new ScaleGestureDetector(
            activity,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
              @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                pinchStart = currentZoom;
                return true;
              }

              @Override public boolean onScale(ScaleGestureDetector detector) {
                currentZoom = pinchStart * detector.getScaleFactor();
                listener.onZoom(currentZoom);
                return true;
              }
            });
    frame.setOnTouchListener(
        (v, event) -> {
          scale.onTouchEvent(event);
          if (!scale.isInProgress()) gestures.onTouchEvent(event);
          return true;
        });
  }

  void refresh(CameraCapabilities caps, String modeLabel, float zoom, boolean torch, boolean idle, boolean focusLocked) {
    modeChip.setText(modeLabel == null ? "" : modeLabel);
    modeChip.setVisibility(modeLabel == null || modeLabel.isEmpty() ? View.GONE : View.VISIBLE);
    modeChip.setAlpha(idle ? 1f : 0.5f);
    currentZoom = zoom;
    if (zoom <= 1.001f) zoomChip.setVisibility(View.GONE);
    else {
      zoomChip.setText(String.format(Locale.ROOT, "%.1fx", zoom));
      zoomChip.setVisibility(View.VISIBLE);
    }
    zoomChip.setEnabled(caps != null && caps.zoom);
    torchChip.setVisibility(caps != null && caps.flash ? View.VISIBLE : View.GONE);
    torchChip.setText(torch ? "Torch on" : "Torch");
    flipChip.setVisibility(View.VISIBLE);
    flipChip.setAlpha(idle ? 1f : 0.5f);
    lockChip.setVisibility(caps != null && caps.maxAfRegions > 0 ? View.VISIBLE : View.GONE);
    lockChip.setText(focusLocked ? "Locked" : "Lock AE/AWB");
    ring.locked = focusLocked;
    ring.invalidate();
  }

  void showFocusRing(float x, float y, boolean locked) {
    ring.show(x, y, locked);
  }

  void clearFocusRing() {
    ring.clear();
  }

  private static TextView chip(Activity activity, Ui ui, int size) {
    TextView chip = new TextView(activity);
    chip.setTextSize(size);
    chip.setTextColor(ui.text);
    chip.setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6));
    chip.setBackground(ui.shape(ui.dark ? 0xCC171F2B : 0xE6FFFFFF, 14, true));
    chip.setClickable(true);
    return chip;
  }

  private void addChip(FrameLayout frame, TextView chip, int gravity) {
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, gravity);
    params.setMargins(16, 16, 16, 16);
    frame.addView(chip, params);
  }

  private static final class FocusRing extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int border, accent;
    private float x = -1, y = -1;
    boolean locked;

    FocusRing(Activity activity, int border, int accent) {
      super(activity);
      this.border = border;
      this.accent = accent;
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(4f);
    }

    protected void onDraw(Canvas canvas) {
      if (x < 0) return;
      paint.setColor(locked ? accent : border);
      canvas.drawCircle(x, y, 40f, paint);
    }

    void show(float x, float y, boolean locked) {
      this.x = x;
      this.y = y;
      this.locked = locked;
      invalidate();
    }

    void clear() {
      x = -1;
      invalidate();
    }
  }
}
```

- [ ] **Step 2: Write `ModeChooserDialog.java`**

```java
package app.stereobridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Mode, HDR and codec chooser with GCam-derived enablement rules. */
final class ModeChooserDialog {
  interface Listener {
    void onApply(String modeKey, boolean hdr, String codec);
  }

  static void show(
      Activity activity,
      Ui ui,
      CameraCapabilities caps,
      String currentKey,
      boolean currentHdr,
      String currentCodec,
      Predicate<String> modeFeasible,
      Supplier<Boolean> hevcAvailable,
      Listener listener) {
    LinearLayout root = new LinearLayout(activity);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(4));
    root.addView(ui.caption("Video mode"));
    RadioGroup modes = new RadioGroup(activity);
    int[] selectedIndex = {0};
    final boolean[] hdrChosen = {currentHdr};
    final int[] selectedRate = {30};
    CheckBox hdr = new CheckBox(activity);
    ui.controls(hdr);
    hdr.setText("10-bit HDR (30 fps only)");
    TextView hdrHint = ui.caption("10-bit HDR can't be used unless video FPS is 30.");
    hdrHint.setVisibility(hdrChosen[0] ? android.view.View.GONE : android.view.View.VISIBLE);

    java.util.List<RadioButton> buttons = new java.util.ArrayList<>();
    String feasibleDefault = null;
    for (int index = 0; index < VideoMode.ALL.size(); index++) {
      VideoMode mode = VideoMode.ALL.get(index);
      RadioButton button = new RadioButton(activity);
      ui.controls(button);
      boolean feasible = modeFeasible.test(mode.key());
      button.setText(mode.label() + (feasible ? "" : "  (not supported on this camera)"));
      button.setEnabled(feasible);
      if (feasible && feasibleDefault == null) feasibleDefault = mode.key();
      if (mode.key().equals(currentKey) && feasible) {
        button.setChecked(true);
        selectedIndex[0] = index;
        selectedRate[0] = mode.rate;
      }
      final int indexFinal = index;
      button.setOnClickListener(v -> {
        selectedIndex[0] = indexFinal;
        selectedRate[0] = mode.rate;
        hdrChosen[0] = hdr.isChecked();
      });
      modes.addView(button);
      buttons.add(button);
    }
    if (feasibleDefault != null && !buttons.get(selectedIndex[0]).isChecked()) {
      for (int index = 0; index < VideoMode.ALL.size(); index++)
        if (VideoMode.ALL.get(index).key().equals(feasibleDefault)) {
          buttons.get(index).setChecked(true);
          selectedIndex[0] = index;
          selectedRate[0] = VideoMode.ALL.get(index).rate;
        }
    }
    boolean hdrOffered = HdrPolicy.offer(caps.hlgCamera, caps.hlgEncoder);
    hdr.setEnabled(hdrOffered && HdrPolicy.allowedAtRate(selectedRate[0]));
    hdr.setChecked(hdrChosen[0] && hdr.isEnabled());
    for (RadioButton button : buttons)
      button.addOnCheckedChangeListener((b, checked) -> {
        if (!checked) return;
        boolean allowed = hdrOffered && HdrPolicy.allowedAtRate(selectedRate[0]);
        hdr.setEnabled(allowed);
        if (!allowed) hdr.setChecked(false);
        hdrHint.setVisibility(allowed ? android.view.View.GONE : android.view.View.VISIBLE);
      });
    root.addView(modes);
    root.addView(hdr);
    root.addView(hdrHint);
    ui.space(root, 8);
    root.addView(ui.caption("Video codec"));
    RadioGroup codecGroup = new RadioGroup(activity);
    RadioButton avc = new RadioButton(activity);
    ui.controls(avc);
    avc.setText("AVC · best compatibility");
    RadioButton hevc = new RadioButton(activity);
    ui.controls(hevc);
    hevc.setText("HEVC · saves storage");
    hevc.setEnabled(hevcAvailable.get());
    (CodecPolicy.HEVC.equals(currentCodec) && hevc.isEnabled() ? hevc : avc).setChecked(true);
    codecGroup.addView(avc);
    codecGroup.addView(hevc);
    root.addView(codecGroup);
    new AlertDialog.Builder(activity)
        .setTitle("Video mode")
        .setView(root)
        .setPositiveButton(
            "Apply",
            (dialog, which) ->
                listener.onApply(
                    VideoMode.ALL.get(selectedIndex[0]).key(),
                    hdr.isChecked(),
                    hevc.isChecked() ? CodecPolicy.HEVC : CodecPolicy.AVC))
        .setNegativeButton("Cancel", null)
        .show();
  }
}
```

- [ ] **Step 3: Expose HEVC availability from the service**

In `LocalBinder` (Task 8 step 5 remainder) add:

```java
    public boolean hevcAvailable() {
      for (VideoMode candidate : feasibleModes)
        if (OfflineVideoEncoder.supportsVideo(candidate.width, candidate.height, candidate.rate, CodecPolicy.HEVC))
          return true;
      return false;
    }
```

- [ ] **Step 4: Wire controls into `MainActivity`**

Add fields:

```java
  private CameraControls controls;
  private FrameLayout previewFrame;
```

Replace the local `FrameLayout previewFrame = new FrameLayout(this);` declaration in `build()` with assignment to the field. After the existing SurfaceTextureListener block, add:

```java
    controls =
        new CameraControls(
            this,
            previewFrame,
            ui,
            new CameraControls.Listener() {
              public void onTap(float x, float y) {
                if (recorder == null) return;
                int[] info = recorder.previewInfo();
                boolean front = info.length > 3 && info[3] == 1;
                int sensor = info.length > 4 ? info[4] : 90;
                float[] oriented = FocusPointMapper.sensorPoint(
                    x, y, preview.getWidth(), preview.getHeight(), info[0], info[1], sensor,
                    getWindowManager().getDefaultDisplay().getRotation() * 90, front);
                float[] point = FocusPointMapper.activePoint(oriented[0], oriented[1], sensor);
                recorder.setFocusPoint(point[0], point[1]);
                controls.showFocusRing(x, y, recorder.focusLocked());
              }

              public void onZoom(float ratio) {
                if (recorder != null) recorder.setZoom(ratio);
              }

              public void onFlip() {
                if (recorder == null) return;
                if (OfflineRecorderService.state.busy()) {
                  Toast.makeText(MainActivity.this, "Stop before switching camera", Toast.LENGTH_SHORT).show();
                  return;
                }
                controls.clearFocusRing();
                recorder.flip();
              }

              public void onTorch() {
                if (recorder != null) recorder.setTorch(!recorder.torch());
              }

              public void onLock() {
                if (recorder != null) recorder.setFocusLock(!recorder.focusLocked());
              }

              public void onMode() {
                if (recorder == null) return;
                if (OfflineRecorderService.state.busy()) {
                  Toast.makeText(MainActivity.this, "Stop before changing video mode", Toast.LENGTH_SHORT).show();
                  return;
                }
                ModeChooserDialog.show(
                    MainActivity.this,
                    ui,
                    recorder.capabilities(),
                    settings.get("video_mode", ""),
                    settings.flag("video_hdr"),
                    settings.get("video_codec", "avc"),
                    recorder::modeFeasible,
                    recorder::hevcAvailable,
                    (modeKey, hdr, codec) -> recorder.requestMode(modeKey, hdr, codec));
              }
            });
```

In `refresh()`, after the timer update, add:

```java
    boolean idle = !s.busy();
    if (recorder != null)
      controls.refresh(recorder.capabilities(), recorder.modeLabel(), recorder.zoom(), recorder.torch(), idle, recorder.focusLocked());
    else controls.refresh(null, "", 1f, false, idle, false);
```

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Emulator smoke with the recorder UI**

Run: `python3 scripts/offline-emulator-smoke.py` (boots the API 29 emulator, installs the debug app, records synthetic takes, then runs `scripts/verify-stereo-mp4.py` on `/tmp/stereo-offline-smoke/videos`).
Expected: all checks pass; matrix falls back to a feasible mode on the virtual camera; synthetic tones still verified.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/stereobridge/CameraControls.java app/src/main/java/app/stereobridge/ModeChooserDialog.java app/src/main/java/app/stereobridge/OfflineRecorderService.java app/src/main/java/app/stereobridge/MainActivity.java
git commit -m "Add camera controls overlay and video mode chooser"
```

---

### Task 11: Extend the stereo verifier for codec and HDR fields

**Files:**
- Modify: `scripts/verify-stereo-mp4.py`

- [ ] **Step 1: Relax the codec assertion and add reported fields**

Replace `assert video['codec_name'] == 'h264'` with:

```python
    assert video['codec_name'] in ('h264', 'hevc'), f'{path}: unexpected codec {video["codec_name"]}'
```

In the per-file report dictionary (where `report = {...}` is built), add:

```python
        'codec': video['codec_name'],
        'profile': video.get('profile'),
        'pix_fmt': video.get('pix_fmt'),
        'color_transfer': video.get('color_transfer'),
        'hdr': video.get('color_transfer') == 'arib-std-b67',
        'fps': video.get('avg_frame_rate'),
```

- [ ] **Step 2: Run the verifier against existing takes**

Run: `python3 scripts/verify-stereo-mp4.py /tmp/stereo-offline-smoke/videos` (re-run the emulator smoke from Task 10 first if the directory is empty).
Expected: PASS; JSON report now includes codec, profile, pix_fmt, color_transfer, hdr, fps for every take.

- [ ] **Step 3: Commit**

```bash
git add scripts/verify-stereo-mp4.py
git commit -m "Report codec, profile and HDR fields in the stereo verifier"
```

---

### Task 12: Documentation updates

**Files:**
- Modify: `docs/compatibility.md`
- Modify: `README.md`
- Modify: `docs/offline-verification.md`
- Create: `docs/camera-capability-probe.md`

- [ ] **Step 1: Update the compatibility matrix**

In `docs/compatibility.md`, change the Camera row to:

```markdown
| Camera | Camera2; negotiated modes 4K30 (default), 4K60, 1080p60, 1080p30; session-level video stabilization and OIS when advertised; tap-focus, zoom and torch when advertised; 10-bit HLG HDR only where camera and encoder both support it (30 fps modes only) |
```

and in the Known limits section add one sentence:

```markdown
Camera mode availability is negotiated per device; unsupported modes and controls are hidden rather than shown disabled. Pixel Camera's closed computational video features (Video Boost and similar) are out of scope.
```

- [ ] **Step 2: Create the capability probe record**

Create `docs/camera-capability-probe.md`:

```markdown
# Camera capability probe

`OfflineRecorderService` logs one line per camera negotiation:

```
Camera capabilities: camera stabilization=<mode> optical=<bool> zoom=<min-max|off> flash=<bool> afRegions=<n> hdrCamera=<bool> hdrEncoder=<bool> mode=<label>
```

Capture it with:

```bash
adb logcat -c
# open Stereo Bridge once
adb logcat -d | grep "Camera capabilities" | tail -1
```

## Recorded devices

| Device | Date | Probe line |
| --- | --- | --- |
| Pixel 8 Pro | pending first physical run (Task 13) | |
| Pixel 6 | pending device availability | |
```

- [ ] **Step 3: Update the README feature bullets**

In `README.md`, after the "Separate voices" bullet add:

```markdown
- **Camera parity where the hardware allows:** 4K30/4K60/1080p60/1080p30, video stabilization, tap-to-focus, zoom, torch, camera flip, and 10-bit HDR on supporting devices.
```

- [ ] **Step 4: Add the verification section stub**

In `docs/offline-verification.md`, add a section after the current candidate section:

```markdown
## Camera video parity candidate (unreleased)

Unit and emulator checks pass on the camera parity branch. Physical results are recorded in Task 13 of the implementation plan (`docs/superpowers/plans/2026-10-18-camera-video-parity.md`) as each check completes. Until physical takes are attached, no parity claim is made.
```

- [ ] **Step 5: Commit**

```bash
git add docs/compatibility.md README.md docs/offline-verification.md docs/camera-capability-probe.md
git commit -m "Document camera mode matrix and capability probe"
```

---

### Task 13: Physical validation on the connected Pixel (owner-assisted)

**Requires:** the Pixel connected over USB (Pixel 8 Pro was connected on 2026-10-18), both DJI Mic Mini transmitters charged and paired, Wi-Fi/wireless debugging off. Install the feature build: `./gradlew :app:installDebug` (or assemble and `adb install` the signed release after release signing is configured as usual).

- [ ] **Step 1: Capture the capability probe line**

```bash
adb logcat -c
adb shell am start -n app.stereobridge/.MainActivity
sleep 5
adb logcat -d | grep "Camera capabilities" | tail -1
```

Record the line in `docs/camera-capability-probe.md` with date. Expected on Pixel 8 Pro: `stabilization=2`, `hdrCamera=true`; `hdrEncoder` true only if the device HEVC encoder accepts Main10 at the negotiated mode.

- [ ] **Step 2: One take per offered mode**

Owner records short takes via the UI: 4K30 (default), 4K60, 1080p60, 1080p30, plus 4K30 with HDR on (if the mode sheet offers it). Pull and inspect:

```bash
mkdir -p /tmp/camera-validation && cd /tmp/camera-validation
adb pull "/sdcard/Movies/Stereo Bridge" .
for f in */*.mp4; do
  ffprobe -v error -select_streams v -show_entries stream=codec_name,profile,width,height,r_frame_rate,color_transfer,pix_fmt -of default=nw=1 "$f"
done
```

Expected: each file shows the requested resolution/rate; HDR take shows `codec_name=hevc`, `profile=Main 10`, `color_transfer=arib-std-b67`, `pix_fmt=yuv420p10le` (the recorded Pixel Camera sample measured exactly this shape at ~43 Mbps; captured bitrate should land in the same order of magnitude). Owner confirms audio and picture by playback. If HDR session configuration fails, the app falls back to SDR once — record that honestly in `docs/offline-verification.md` with the logcat line.

- [ ] **Step 3: Stabilization A/B**

Owner records two 10-second handheld walking clips at 4K30: stabilization on (default) and off via the mode sheet (temporarily request 1080p30 or disable via probe if needed — simplest: compare against a Pixel Camera clip of the same walk). Expected: owner confirms the stabilized clip is visibly smoother and preview matches the recorded framing (WYSIWYG).

- [ ] **Step 4: Zoom, tap-focus and flip sanity**

Owner records: one pinch-zoomed clip, one tap-to-focus clip (ring appears, refocus visible), one flip to front and back. Expected: zoom chip shows the ratio; flip returns to rear and stays remembered after app restart. Direction calibration: at portrait with the rear camera, tap the left-center and top-center of the preview; the refocus/ring should land on the tapped side. If the focus visibly lands mirrored or rotated instead, flip the corresponding `FocusPointMapper.activePoint` branch for that sensor orientation, commit that one-line change, and re-run this check.

- [ ] **Step 5: Endurance take**

Owner records one continuous 4K30 take of at least 15 minutes with both mics live (this also feeds the deferred podcast-pass evidence). Expected: take saves and plays; watch the logcat for the documented Bluetooth discontinuity (`Sequence discontinuity`); if it stops the take, keep the partial and record it as the known unclassified issue — do not mark it fixed. Check storage math: ~45 Mbps implies about 20 GB per hour.

- [ ] **Step 6: Oracle comparison against Pixel Camera**

Record the same scene on Pixel Camera 4K30 HDR and on Stereo Bridge 4K30 HDR (or nearest equivalent). Probe both and write the delta table (codec, profile, resolution, fps, color transfer, approximate bitrate, stabilization option) into `docs/offline-verification.md`, stating honestly where parity holds and where GCam's closed pipeline still leads.

- [ ] **Step 7: Update documents and commit**

Fill `docs/camera-capability-probe.md` and the verification section with the recorded results, then:

```bash
git add docs/camera-capability-probe.md docs/offline-verification.md
git commit -m "Record camera parity physical validation results"
```

---

### Task 14: Full CI, PR, merge, receipts

- [ ] **Step 1: Run the full local gate**

```bash
./gradlew testDebugUnitTest lintRelease assembleDebug bundleRelease
python3 scripts/offline-emulator-smoke.py
```

Expected: tests pass (106 original + new policy tests), lint has no errors, both build outputs succeed, emulator smoke passes.

- [ ] **Step 2: Push and open the PR**

```bash
git push -u origin camera-video-parity
gh pr create --title "Camera video parity: modes, stabilization, controls, HDR" --body "Implements docs/superpowers/specs/2026-10-18-camera-video-parity-design.md. Unit and emulator checks green; physical validation per plan Task 13."
```

- [ ] **Step 3: Wait for CI and record the receipt**

```bash
gh pr checks --watch
gh pr view --json url,mergeStateStatus
```

Record the PR URL and CI result in `docs/offline-verification.md`.

- [ ] **Step 4: Present everything and wait for explicit approval, then merge**

Present the PR URL, diff summary, CI results and physical-validation status. Standing instruction (2026-10-19): show everything before merging and never auto-merge or push to main without explicit user approval. Only after approval:

```bash
gh pr merge --squash --delete-branch
git log --oneline -1
```

Record the merge commit hash alongside the PR URL and CI result in `docs/offline-verification.md`, commit that receipt, and push.

- [ ] **Step 5: Deferred follow-up**

Open the next spec for the podcast pass when ready: audio-only L/R WAV export, endurance hardening beyond 15 minutes, and the Bluetooth discontinuity investigation. Do not bundle it into this branch.

---

## Self-review notes

- Spec coverage: mode matrix (Tasks 1, 7), stabilization session-level (Tasks 3, 8), AF tap/lock (Tasks 4, 8), zoom (Tasks 3, 8), flip/torch (Task 8), encoder ladder + presets (Tasks 2, 6), HLG probe-gated with 30 fps rule and single SDR fallback (Tasks 5, 9), UI sheet with GCam wording (Task 10), capability gating (Tasks 5, 8, 10), verification (Tasks 10–14), docs (Task 12).
- Audio pipeline, journal, muxer, storage policy, permissions: intentionally untouched.
- Type consistency check: `VideoMode`, `ModeMatrixPolicy`, `StabilizationPolicy.NONE`, `CameraCapabilities`, `CodecPolicy.AVC/HEVC`, `HdrPolicy.offer/allowedAtRate`, `FocusPointMapper.sensorPoint`, `ZoomPolicy.clamp/cropRegion` are used with identical signatures across tasks.


---

### Task 15: Camera-first full-screen UI with overlaid mic data

**Files:**
- Modify: `app/src/main/java/app/stereobridge/MainActivity.java`
- Modify: `app/src/main/java/app/stereobridge/CameraControls.java`

Owner request (2026-10-19): the fixed 120/250 dp preview is too small; the camera view must be full screen and mic data must appear on the camera screen.

- [ ] **Step 1: Rebuild the screen as an overlay stack**

Root FrameLayout filling the display: `previewFrame` (match_parent, black) with the TextureView; `CameraControls` chips/ring inside it; then overlay containers added after it — a top status bar (status text, timer, title) and a bottom panel (mic rows with labels + meters, Record/Stop, one-line hint, and a visible "Open last video" control). Secondary actions (Advanced controls, pairing, notification settings, permission settings, Privacy policy) move behind a small "More" control that opens a dialog rebuilt on demand from current settings; remove those views from the main layout and from the per-tick refresh (visibility decided when the dialog is built). Keep the exact texts "Record", "Stop", "Recording stereo video", "Video saved to Movies / Stereo Bridge" and "Open last video" visible for the emulator smoke.

- [ ] **Step 2: Respect insets and keep the preview edge-to-edge**

Do not pad the root; apply the display-cutout/system-bar top inset to the top bar and the bottom inset (plus 12 dp) to the bottom panel, and give chips top/bottom margins that clear them. The preview keeps `CameraGeometry.previewCorners` center-crop filling the screen.

- [ ] **Step 3: Move camera chips clear of the bottom panel**

In `CameraControls`, lay out: mode chip top-start; flip and torch in one top-end row; lock chip bottom-start above the bottom panel; zoom chip bottom-end above it. Use `ui.dp` values and keep the existing tap/pinch/lock behavior unchanged.

- [ ] **Step 4: Verify**

`ANDROID_HOME=/home/andrii/Android/Sdk ./gradlew testDebugUnitTest lintRelease assembleDebug` green (143 tests, 0 lint errors); hosted CI emulator smoke must still find the preserved texts. Install `app-debug.apk` on the connected Pixel and capture a screenshot confirming the edge-to-edge preview and overlaid mic meters. Commit with message: `Make the camera screen full screen with overlay controls`.
