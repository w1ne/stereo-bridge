# Offline recorder candidate — updated 14 October 2026

## Architecture and daily use

Camera2 and MediaCodec run inside Stereo Bridge. Two independent DJI Mic Mini Bluetooth streams are decoded, mixed as left/right PCM and encoded to stereo AAC. Camera frames are encoded to H.264. MediaMuxer publishes MP4 videos to Movies / Stereo Bridge.

**Daily use: open Stereo Bridge and tap Record. Tap Stop to save.**

First use only: pair both original DJI Mic Mini transmitters in Android Settings and allow camera, microphone and nearby devices access. The two transmitters need power and Bluetooth enabled. There is no receiver or cable during recording. Saved device assignments and controls are restored after reboot; recording itself never starts automatically.

## Local checks

- Debug APK, signed release APK and signed release AAB build with JDK 17 / SDK 36.
- All 61 unit tests pass (zero failures/errors). Tests cover parser/buffer behavior, stereo mixing, sample timestamps, AAC input batching, bounded mux buffering, video timestamp mapping and session state restoration.
- Release lint has zero errors; warnings include English-only UI, pinned toolchain versions and arm64-only support.
- Release manifest has no Internet permission, debugging setup, boot receiver or exported recording service.
- LibADB, SPAKE2, Conscrypt and Bouncy Castle are absent from runtime dependencies.
- APK v2 signature and 16 KB ZIP alignment checks pass.
- Independent source reviews led to fixes for cancellation, MediaStore cleanup, preview destruction, pending Camera2 session ownership and waiting for camera closure before encoder finalization.

## Emulator harness

`scripts/offline-emulator-smoke.py` is restricted to emulator serials. Install the debug APK and run:

```sh
python3 scripts/offline-emulator-smoke.py --serial emulator-5554 --output /private/tmp/stereo-offline-smoke
```

It disables Wi-Fi, records debug-only 440 Hz left / 880 Hz right tones through the real camera/encoder path, repeats with background and screen sleep, reboots, records again and pulls the saved videos. Release builds disable synthetic input.

The Mac could not boot the API 31 arm64 emulator because its disk requirements exceeded available space; those temporary packages were removed. Hosted API 31 x86_64 emulator validation **passed** on source commit `2fafcd6` in [GitHub Actions run 38435020995](https://github.com/w1ne/stereo-bridge/actions/runs/38435020995):

- Three takes saved successfully, including repeat recording across background/screen sleep and another take after reboot with Wi-Fi disabled.
- The last saved video URI restored after reboot without setup.
- All three MP4s contain 1280×720 H.264 video and 48 kHz two-channel AAC audio.
- Distinct 440 Hz left / 880 Hz right tones, monotonic track timestamps and no silent 100 ms audio windows passed for each take.
- Evidence artifact contains screenshots, logcat, the videos and `media-verification.json`.

Earlier hosted runs exposed AAC input batching and camera session/device shutdown bugs; both were fixed before this passing run. Harness fixes wait for shared storage after reboot and tolerate UI automation exit errors when a valid fresh snapshot exists. The signed candidate uses the same application source as the passing run (application last changed at `ad5c87f`; subsequent commits change only the harness).

Synthetic emulator testing cannot establish physical Bluetooth reconnect reliability, microphone intelligibility or long-term microphone clock drift.

## Physical Pixel checks — in progress

A USB-connected **Pixel 6 / Android 15** identified as `oriole` (the expected Pixel 8 Pro was not connected). The signed release APK installed successfully. Camera preview and normal first-use Camera, Microphone, Nearby devices and notification permission dialogs worked. With only one original DJI transmitter paired, Record correctly opened the two-microphone pairing guide.

A separate debug package exercised the same camera/encoder implementation with synthetic tones. With Wi-Fi disabled and wireless debugging off, it saved:

- 11.36-second 1920×1080 H.264 / 48 kHz stereo AAC take.
- 91.74-second repeated take spanning Home, screen sleep and PIN lock. Stop was sent through the debug app's existing Stop action while locked; the saved file remained playable. Video continued, with a largest frame timestamp gap of 0.278 seconds across this take.

Both files passed distinct left/right tones, monotonic track timestamps and no silent 100 ms audio windows. These are test tones, **not microphone captures**. The phone was then rebooted; post-reboot interaction is awaiting the owner's PIN unlock.

Still required: post-reboot saved-settings/recording check, physical two-mic startup and speech/channel assignment, microphone disconnect, a longer real-microphone take, and reconnection with Wi-Fi/wireless debugging disabled. Driver timestamps are host estimates; long-term clock drift and Bluetooth scheduling remain hardware-dependent.

For installation/testing: **plug the Pixel 8 Pro into the Mac with a USB-C data cable. Keep both DJI transmitters wireless. One user step: unlock the Pixel and accept “Allow USB debugging” if Android shows it.** This development connection is not a recording requirement.

## Distribution

The previous 0.1.0-rc3 release and its Google Play submission remain separate. Do not replace that submission until physical offline-candidate validation passes. The listing, screenshots, reviewer demonstration and foreground-service declaration must be updated for the built-in camera architecture before submitting 0.2.0.
