# Offline recorder verification — 14 October 2026

## Daily use

**With both paired microphones powered on and Bluetooth enabled, open Stereo Bridge and tap Record. Tap Stop to save to Movies / Stereo Bridge.**

First use requires pairing both original DJI Mic Mini transmitters in Android Settings, then granting Camera and Nearby devices on Android 12+. Android 10/11 use the normal legacy Bluetooth permission. Notifications are optional. No Wi-Fi, receiver, root, Shizuku or debugging setup is required to record. Settings survive reboot; recording never starts automatically.

## Current candidate: 0.2.0-offline-rc4 (code 7)

- Android 10/API 29 minimum, target API 36. Native libraries: armeabi-v7a, arm64-v8a and x86_64.
- Camera and paired-device Bluetooth access; optional notifications. No RECORD_AUDIO, Internet, location, device discovery or broad storage permission. Foreground service uses camera and connectedDevice types for Camera2 plus Bluetooth RFCOMM.
- Camera/codec capability negotiation, bounded startup fallback, display-aware preview and frozen capture orientation.
- Direct pending MediaStore recording avoids copying an entire video at Save. Journal, ownership checks and bounded MP4 inspection recover retained captures safely. Finalized videos are retained if publication fails; Retry save is available.
- Permission-denial recovery, optional-notification capture, low-storage reserve, disconnect errors and repeated-session cleanup are covered by focused checks.
- Independent spec and quality reviews found issues in descriptor lifetime, camera fallback, permission prompts and UI recovery; these were addressed. Final review and candidate-specific hardware checks are recorded separately from earlier releases.

## Camera video parity candidate (unreleased)

Unit and emulator checks pass on the camera parity branch (144 unit tests). Physical results are recorded incrementally in the implementation plan (docs/superpowers/plans/2026-10-18-camera-video-parity.md, Task 13) as each check completes. Until physical takes are attached, no parity claim is made. Known verification gaps: API 29 emulator smoke was blocked locally by missing system image and low host resources; the hosted workflow remains the emulator gate.

## Local package checks (code 6)

106 unit tests passed with zero failures/errors. Release lint reported zero errors and nine warnings. Debug APK, signed release APK and signed AAB built successfully. APK signature, 16 KiB ZIP alignment and native LOAD alignment passed for all three ABIs. Bundled privacy text exactly matches PRIVACY.md. Final focused independent source review found no critical or important issues.

## Physical Pixel 6 / Android 15

Wi-Fi and wireless debugging were disabled during recording checks. The development USB connection is only used for installation and observation.

Earlier code 4 offline candidate:

- Synthetic camera recordings: 11.36 seconds, 91.74 seconds through Home/screen sleep/PIN lock, and 11.23 seconds after reboot and ordinary owner unlock. All passed distinct 440 Hz left / 880 Hz right, monotonic timestamps and no silent 100 ms audio windows.
- Real microphones: 151.34-second stereo take; Bluetooth loss saved a playable 8.93-second partial take; reconnect saved a playable 12.30-second take. Full decode passed.
- Owner confirmed clear speech in the 110.56-second prompted take: “works great!”
- A 931.31-second real-microphone take passed full decode. Audio duration was 931.2213 seconds and video duration 931.3078 seconds.
- A separate endurance attempt stopped after 375.49 seconds on a large sequence discontinuity and saved a playable partial take. Its cause is unclassified. Protective stop-and-save remains; this is not claimed fixed by the passing take.

Code 5 candidate:

- Two short real-microphone recordings saved directly to MediaStore and repeated successfully after removal of phone microphone permission.
- Debug camera pipeline saved with notification permission denied.
- Process termination during capture followed by restart cleaned the known unusable owned pending row; the next capture saved.
- Camera denial displayed Settings recovery, and restoring permission allowed the next capture to save.
- Latest final real-microphone validation is pending: both transmitters showed 10% battery and the right transmitter disconnected before capture. Charge both before retrying. Earlier code 4 endurance results do not establish final code 5 endurance.

Raw owner video/audio and diagnostic logs remain private and are not committed to the public repository.

## Code 6 physical camera check

Two consecutive Pixel debug takes (11.27 and 16.37 seconds) passed full decode, distinct 440/880Hz channel tones, monotonic timestamps and no silent 100ms windows. After charging/powering both transmitters, a real code 6 release take saved and passed full decode: 42.34 seconds, 1080p H.264 and two-channel AAC. An earlier attempt stopped before capture with right-microphone disconnected. The signed release remains installed, the temporary debug app was removed and original Wi-Fi enabled state was restored.

## Android 10 encoder regression and code 6

The fresh API29 check on code 5 failed during AVC configuration. Android10 ACodec requires an AVC level whenever an explicit profile is set; code 5 supplied only profile. Code 6 supplies both advertised profile and level. [Fresh API29 hosted validation passed](https://github.com/w1ne/stereo-bridge/actions/runs/38598915925) on source `66498ab`: build/unit/lint, three camera takes including repeat/background/sleep/reboot, and stereo tone/timestamp/silence verification. The profile/level correction is verified on that emulator.

## Hosted emulator coverage

API 31 x86_64 validation passed on earlier source `2fafcd6`: [Actions run 38435020995](https://github.com/w1ne/stereo-bridge/actions/runs/38435020995). Three synthetic takes covered repeat/background/sleep/reboot with Wi-Fi disabled and passed channel/timestamp/silence checks.

The current API29 workflow passed on source `66498ab`; see the code 6 result above. Synthetic testing cannot prove Bluetooth microphone intelligibility, physical clock drift or device-wide compatibility. The local Mac emulator could not boot within available disk space; temporary packages were removed.

## Distribution status

Signed code 7 was submitted with the updated listing/screenshots, reviewer instructions and camera/connectedDevice declarations on 14 October 2026. Google Play shows Changes in review, with automatic quick checks still running. Managed publishing is off, so approved changes publish automatically. The old 0.1 build remains live until the update is approved. See [release record](store-offline-update.md); approval is not claimed.

## Final preview correction (code 7)

Final source review confirmed TextureView already applies sensor orientation and default front mirroring. The app preview now compensates display rotation only and uses sensor-oriented dimensions for uniform crop. Three pure geometry regression tests were added. 109 local unit tests passed, including the three geometry regressions; signed APK/AAB and release lint passed. The Pixel release recorded 22.39 seconds with both real mics, continued across Home, returned to Recording and saved on Stop. Full decode passed: 1080p H.264 and 48kHz two-channel AAC. Current screenshots demonstrate the corrected upright preview. The change affects preview, not encoded capture orientation.

[Final code 7 API29 hosted check passed](https://github.com/w1ne/stereo-bridge/actions/runs/38601216505) on exact application source `1f9a6b3`. Public signed APK/AAB/checksum digests were verified against local files. The public muted screen demonstration and developer screenshot were explicitly approved; raw microphone media remains private.
