# Camera video parity design

## Decision and authorization

Andrii requested camera quality and features on par with the original Pixel Camera, podcast-first, video only. The selected approach is a pure Camera2 uplift (approach A) on the existing verified pipeline — no CameraX migration. Pixel Camera is used as a black-box oracle: its installed APK, advertised HAL capabilities and recorded output files were inspected for personal interoperability study on 2026-10-18; Stereo Bridge re-implements behavior through public Android APIs only and copies no proprietary code or assets. This effort is camera-only. The podcast pass (audio-only export, long-take endurance hardening, Bluetooth discontinuity investigation) is deliberately deferred to a separate spec.

Honest parity statement: observable video capture — modes, stabilization, autofocus, zoom, codec, bitrate, HDR video where the hardware allows — targets Pixel Camera parity. Google's closed computational video pipeline (Video Boost and similar) is not reproducible and is recorded as a known gap, not faked.

## Evidence baseline

Inspected 2026-10-18 on Pixel 8 Pro (Android 15-era, Pixel Camera 10.4).

- HAL exposes to third-party apps: `android.control.availableVideoStabilizationModes = [OFF, ON, PREVIEW_STABILIZATION]`; `DYNAMIC_RANGE_TEN_BIT` capability; recommended ten-bit profile = HLG10.
- Pixel Camera's own rules (resource strings): "10-bit HDR" only at 24/30 fps, never 60 fps or 8K; codec setting defaults to AVC ("Best compatibility"), with HEVC and AV1 options; stabilization is a settings toggle.
- Real Pixel Camera output, 125 s 4K30 HDR file: HEVC Main10, bt2020, HLG (`arib-std-b67`), ~43 Mbps, 30 fps.
- AOSP `camera2-hdrvideo` sample (Apache-2.0) recipe: query `REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES`; call `setDynamicRangeProfile()` on both preview and record outputs — they must match or session configuration fails; HLG10 encodes as HEVC Main10 with level 5.1; changing range requires a full session close/open. The sample explicitly leaves the MediaCodec plus muxer path out of scope, which is the path Stereo Bridge already owns.

Pixel 6 capability parity remains unprobed until that device is connected; the design gates every feature on advertised capabilities so an unsupported device degrades cleanly.

## Mode matrix and negotiation

Modes (16:9 only): 4K30 (default), 4K60, 1080p60, 1080p30. A mode is feasible on a camera only when all three agree: the camera advertises the size for both `MediaCodec` and `SurfaceTexture` outputs, the encoder ladder can encode the exact width, height and rate, and an AE target FPS range covers the rate.

Negotiation builds the ordered feasible-mode list for the selected camera (rear preferred, unchanged), picks the remembered mode when feasible, else walks 4K30 → 1080p60 → 1080p30 → the best remaining feasible mode ordered by highest resolution, then highest rate. This replaces `CameraGeometry.within1080` and the size-walking loop in `OfflineRecorderService.chooseCamera` with a pure, unit-testable `ModeMatrixPolicy`. The existing bounded startup retry and rejected-mode tracking keep protecting session failures. The mode chip always shows the actual negotiated mode.

Selected mode, HDR and codec choices persist through `BridgeSettings`.

## Capture pipeline

All capture controls live in one base request builder shared by preview-only and record sessions; control updates rebuild from that base so nothing is silently dropped.

- Stabilization, session level (ported behavior from the GrapheneOS Camera / CameraX fix): request `CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION` when advertised, else `..._MODE_ON` when advertised, else leave unset. Set once per session configuration; never toggled mid-session. This gives WYSIWYG preview framing and avoids the known Pixel EIS inconsistency.
- OIS: `LENS_OPTICAL_STABILIZATION_MODE_ON` when advertised.
- Focus: `CONTROL_AF_MODE_CONTINUOUS_VIDEO` at base. Tap sets an AF/AE metering point with `AF_TRIGGER_START`, view-to-sensor coordinate mapping, auto-revert to continuous after lock confirmation or about four seconds. The lock control holds AE/AWB (and fixed AF regions) until released.
- Zoom: `CONTROL_ZOOM_RATIO` clamped to the advertised range; API 29 falls back to `SCALER_CROP_REGION` arithmetic. Pinch plus a zoom chip; zoom is allowed while recording.
- Flip: front/back selection reuses the existing camera choosing path; the preview mirrors as today and recordings remain unmirrored. Double-tap and a chip trigger it.
- Torch: `FLASH_MODE_TORCH` in the repeating request when flash is advertised; released at session end.
- Mode and camera flips are locked while recording (UI-enforced); tap-focus, zoom and torch stay live. Mode changes therefore never rebuild a session mid-take, and the microphone pipeline never renegotiates.

## Encoder and quality ladder

- Codec ladder per requested codec: an AVC request tries AVC High then AVC Baseline; an HEVC request tries HEVC Main then falls back to AVC High and AVC Baseline (the existing fallback retained). Default is AVC High, matching Pixel Camera's "best compatibility" default; HEVC is selectable and remembered when feasible.
- Bitrate presets, clamped to the encoder's advertised range with the existing 256 kbps floor: 4K30 ≈ 45 Mbps (calibrated to the measured Pixel Camera file), 4K60 ≈ 80, 1080p60 ≈ 30, 1080p30 ≈ 20 Mbps. I-frame interval stays one second.
- 10-bit HDR (HLG10) is probe-gated: offered only when the camera advertises a supported HLG10 dynamic range profile and an encoder advertises HEVC Main10. When active, both preview and record outputs carry the same dynamic range profile and the encoder is configured HEVC Main10 with the released level. HDR is available only on 30 fps modes, matching Pixel Camera's own rule, with a disabled-state hint using the same reasoning. HDR session failure falls back to SDR once in the same mode and stays SDR for the session.
- Audio path is untouched: LC3 decode to stereo PCM to AAC, muxer, journal and publication machinery unchanged. Each recording gets a fresh encoder; no live reconfiguration.

## UI surface

The one-button core (Record/Stop, meters, gain/mute/swap) is unchanged. Additions: tap-to-focus with lock control, pinch zoom with a chip, double-tap and chip camera flip, torch icon, and a mode chip opening a bottom sheet with the four modes, the 10-bit HDR toggle and the codec choice. Controls whose capability keys are absent are hidden, never dead. Persistence covers mode, HDR, codec and facing; zoom resets per session.

Gesture mapping, zoom clamping, focus-point mapping and HDR/codec enablement rules live in pure classes with unit tests; the view layer stays thin.

## Failure handling

Negotiation and session failures walk the bounded fallback chain with no loops. HAL-ignored keys are prevented by capability gating in the UI. HDR failure degrades once to SDR. Torch and zoom updates are dropped safely if a session lacks them. Front camera uses the same pipeline with mirrored preview only. The foreground service, wake lock and lifecycle behavior are unchanged.

## Validation

- Unit tests for every new pure class: `ModeMatrixPolicyTest`, bitrate presets, stabilization policy, zoom clamp plus API 29 crop math, focus-point mapping, HDR enablement (30 fps rule), codec ladder — with the existing 106 tests staying green.
- CI emulators (existing API 29/31 workflows): baseline paths must not regress; matrix negotiation exercised, 4K not claimed on emulators.
- Physical evidence on Pixel 8 Pro (connected) and Pixel 6 when available:
  - capability probe dump recorded in documentation;
  - one take per offered mode, validated with an extended `verify-stereo-mp4.py` reporting codec, profile, resolution, fps and HDR flags alongside the existing stereo-distinctness, monotonic-timestamp and silent-window checks;
  - stabilization on/off shake comparison, owner-observed;
  - zoom and tap-focus sanity clips; HDR file plays on device;
  - black-box oracle round against Pixel Camera on the same scene, delta table recorded honestly;
  - at least one 15-minute 4K30 take for thermal and storage behavior; the unclassified Bluetooth discontinuity is watched but not claimed fixed;
  - recording space check: approximately 20 GB/h at 4K30 HDR against `RecordingSpacePolicy`.
- Manifest unchanged: no new permissions. Documentation updates: compatibility matrix, verification log, README.

## Deferred

Podcast pass in a later spec: audio-only L/R WAV export, long-take endurance hardening beyond 15 minutes, and the Bluetooth discontinuity investigation.
