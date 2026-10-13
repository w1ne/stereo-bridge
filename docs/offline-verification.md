# Offline recorder candidate — 13 October 2026

## Architecture and daily use

Camera2 and MediaCodec run inside Stereo Bridge. Two independent DJI Mic Mini Bluetooth streams are decoded, mixed as left/right PCM and encoded to stereo AAC. Camera frames are encoded to H.264. MediaMuxer publishes MP4 videos to Movies / Stereo Bridge.

**Daily use: open Stereo Bridge and tap Record. Tap Stop to save.**

First use only: pair both original DJI Mic Mini transmitters in Android Settings and allow camera, microphone and nearby devices access. The two transmitters need power and Bluetooth enabled. There is no receiver or cable during recording. Saved device assignments and controls are restored after reboot; recording itself never starts automatically.

## Local checks

- Debug APK, signed release APK and signed release AAB build with JDK 17 / SDK 36.
- All 55 unit tests pass (zero failures/errors). Tests cover parser/buffer behavior, stereo mixing, sample timestamps, bounded mux buffering, video timestamp mapping and session state restoration.
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

The official API 31 arm64 emulator image installed, but the emulator could not boot: its userdata/disk requirements exceeded the Mac’s free space. No emulator recording or reboot test is claimed. The temporary emulator packages were removed to restore disk space; the harness is ready for a host with sufficient space. Synthetic testing cannot establish Bluetooth reconnect reliability or physical microphone intelligibility.

## Pixel validation still required

The exact offline candidate has not been installed or tested on the Pixel. Test two-mic startup, speech/channel assignment, Stop/save/repeat, screen sleep, microphone disconnect, at least a 15-minute take and reconnection after reboot with Wi-Fi and wireless debugging disabled. Driver timestamps are host estimates; long-term clock drift and Bluetooth scheduling remain hardware-dependent.

For installation/testing: **plug the Pixel 8 Pro into the Mac with a USB-C data cable. Keep both DJI transmitters wireless. One user step: unlock the Pixel and accept “Allow USB debugging” if Android shows it.** This development connection is not a recording requirement.

## Distribution

The previous 0.1.0-rc3 release and its Google Play submission remain separate. Do not replace that submission until physical offline-candidate validation passes. The listing, screenshots, reviewer demonstration and foreground-service declaration must be updated for the built-in camera architecture before submitting 0.2.0.
