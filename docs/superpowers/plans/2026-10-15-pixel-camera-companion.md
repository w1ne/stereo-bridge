# Pixel Camera Companion Implementation Plan

**Goal:** Test separately installed Pixel Camera using Stereo Bridge audio, retaining its video pipeline.

**Architecture:** Local APK transformation with app-local audio-read adapter; independent Bluetooth audio provider added only after fixture recording succeeds. Original Google-signed APK is never replaced. All proprietary artifacts stay in private temporary storage.

**Tech Stack:** apktool/smali, Java17/D8, Android15, existing DJI LC3 driver.

- [ ] Preserve source APKs/splits and hashes; inspect manifest/package/authority identity.
- [ ] Decode/rebuild a separate package. Verify archive structure and signature, then install it alongside original Camera.
- [ ] Launch and inspect failures; stop before deeper work if package/native/split compatibility is not recoverable.
- [ ] Add test-first stereo fixture generation and a narrow AudioRecord read adapter. Check both direct ByteBuffer and array reads, byte order and frame continuity.
- [ ] Patch only verified audio-read call sites, install and record in companion Camera. Inspect actual saved video/audio with ffprobe and decode. A fixture-only result must be labeled as such.
- [ ] If fixture succeeds, add authenticated audio-provider IPC and map capture timing; connect the two real DJI sources.
- [ ] Verify separate real-mic speech, repeated sessions, disconnect behavior and boot persistence. Prompt before owner speech.
- [ ] Make setup and daily recording simple; document exact working behavior and limitations. Keep all modified Google binaries out of Git and public release assets.


## Revised route: original Camera handoff

The clone startup gate failed; no Camera injection was established. The owner reiterated that no additional camera should be installed. Switched to the unchanged Camera capture intent plus parallel audio and local postprocessing. Original Camera video packets are copied, and DJI sound is added after recording.

- [x] Invoke unchanged Pixel Camera with a narrow private output URI and validate a returned 4K take.
- [x] Capture both direct DJI sources while Camera records; verify a real spoken take.
- [x] Implement local envelope alignment with ambiguous/silent input rejection and agreeing independent windows.
- [x] Copy compressed Camera video and encode stereo AAC; verify exact video hashes, channel identity, priming, and duration.
- [x] Reduce real 54-second take finishing to approximately nine seconds on Pixel.
- [x] Add private recovery journal, Activity-independent session state, cancellation cleanup, retention controls, connection generation/deadline guards, and capture limits.
- [x] Verify cold-process recovery and rotation while finishing; run unit tests, Android lint, and independent code review.
- [ ] Receive owner playback/lip-sync feedback.
- [ ] Verify full phone reboot, disconnected microphones, storage failures, and background finishing behavior.
- [ ] Extend and verify camera/audio-codec/device compatibility.
- [ ] Integrate the validated flow into the published app with appropriate release documentation and Play review.

Prototype source: experiments/camera-handoff. No Google binary or private recording is included. The failed camera clone was uninstalled.
