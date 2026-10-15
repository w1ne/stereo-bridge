# Record with the original Camera

Local prototype: original Pixel Camera records video; Stereo Bridge captures two original DJI Mic Mini transmitters alongside it and adds their sound after Camera returns. No camera clone, root, receiver, Wi-Fi, debugging bridge, or network API is used by the helper.

## Usage

Turn on both paired mics. Open **Stereo Bridge · Camera test**, tap **Connect mics and open Camera**, record normally, then tap **Stop** and **Done** in Camera. Wait for **Stereo video saved**, then tap **Play video**.

The prototype requests nearby-device access once. It requests no camera, microphone, network, location, or broad media-reading permissions. Camera's capture intent has its own Record, Stop, and Done controls.

## Verified on Pixel 6 / Android 15

- Unchanged Pixel Camera recorded a 54.053-second 3840 × 2160 HEVC take while both DJI sources streamed. Capture metadata reported no transport error.
- Automatic alignment and finishing ran on the Pixel using that take. Multiple windows must agree for each mic; otherwise original video and PCM files are retained and no synchronized result is published.
- Saved output contains HEVC video plus AAC 48 kHz stereo. Compressed video SHA-256 matches the original exactly. Decoded stereo channels correlate 0.9944 / 0.9938 with their selected source microphone PCM.
- A separate on-device diagnostic verifies distinct 440 Hz left / 997 Hz right tones with negligible cross-channel energy.
- The tested AOSP AAC codec's two priming packets are removed. Diagnostic tone phase matches input after priming, and audio/video duration differs by less than one 48 kHz sample in the short fixture. A short decoder startup transient remains.
- Processing the real 54-second take takes about nine seconds on this Pixel after bulk PCM reads and coarse/fine alignment.
- Five alignment unit tests pass: known offset, gain variation, coarse-search boundaries, silence/unrelated audio rejection, and ambiguous/short recording rejection.

## Recovery, review, and release blockers

This is not the published app or a generic live microphone provider. Camera sound is replaced **after** recording. Pixel Camera is currently selected explicitly. The prototype supports 48 kHz Camera audio, a specific tested AOSP AAC encoder, two original Mic Mini devices, and takes up to two minutes. Quiet or ambiguous recordings can fail automatic synchronization. Other phones, cameras, codecs, long recordings, a full phone reboot, disconnect notification, background finishing, disk-full behavior, and owner playback need further validation. Cold-process recovery of an interrupted finishing job and activity rotation while finishing have passed on the Pixel. Camera auxiliary metadata tracks are not copied. New successful sessions delete their private original/PCM after publication; cancellation deletes the private capture. Interrupted takes remain private and offer Finish (when capture ended cleanly), Save original without DJI audio, or Discard. Older diagnostic fixtures remain private on the test device.

Build: `./gradlew :cameraHandoff:assembleDebug`

Checks: `./gradlew :cameraHandoff:testDebugUnitTest :cameraHandoff:lintDebug`

No Google APKs, decompiled code, private recordings, or device addresses belong here. The published `:app` is separate.


## Reliability checks

An app-owned session controller holds connection and finishing state across Activity recreation. A private journal restores interrupted finishing after process death. The exported launcher accepts no diagnostic processing intents. Test-only recovery seeding lives in the separately signed instrumentation APK, which is not part of the application.

Verified: restore a clean interrupted-finishing journal after a cold process start; finish while rotating portrait/landscape; update the recreated UI; delete exactly the three private test inputs; retain the saved result URI across another cold start. Independent review found no further serious issues in this controller. Generation guards prevent earlier connection callbacks/timeouts from touching later attempts. READY expires after 15 seconds outside Camera; direct microphone capture stops at a three-minute safety limit. Build, five unit tests, and Android lint pass (lint warnings remain).
