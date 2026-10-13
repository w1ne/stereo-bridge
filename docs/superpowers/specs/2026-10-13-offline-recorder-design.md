# Offline recorder design

## Decision and authorization
Andrii explicitly requested the most robust architecture, including replacing the previous architecture, and authorized building and testing without the Pixel. The new app records video itself through public Android Camera2, MediaCodec and MediaMuxer APIs. Stock Camera injection is removed from this version. No root, ADB, Wireless debugging, Wi-Fi, Internet permission or downloaded executable is required.

## User flow
First use: grant camera/nearby-device/microphone permissions and pair the two transmitters in Android Bluetooth settings. Saved microphone identities and controls persist across app restarts and phone reboots. Daily usage: open Stereo Bridge and tap Record; it connects both microphones and starts video only when both deliver audio. Tap Stop to finalize and save a stereo MP4 in Movies/Stereo Bridge. Do not auto-record after reboot. Advanced contains gains, mute, swap and microphone selection. The recorder shows a camera preview, two live meters, duration, connection state and save result.

## Components
OfflineRecorderService owns a single session, camera, two independent DJI transports, stereo mixing and OfflineVideoEncoder. The activity owns only UI and preview surface attachment. Foreground camera/microphone service keeps an intentional active recording alive when the UI backgrounds. Dropping the preview must reconfigure capture to encoder-only. Codec timestamps share an elapsed-realtime session epoch; PCM uses an explicit 48 kHz sample timeline. Encoded samples are serialized into one MP4; codec output is buffered only until both track formats are available, with finite bounds. Stop is asynchronous, drains both encoders, then publishes through MediaStore IS_PENDING. Failures close all resources and report whether any playable partial take was saved. Process death never claims a successful video.

## Reliability and scope
Finite connection/start/stop deadlines, no silent fallback to phone microphone, no silent conversion to mono, cancellation while connecting, fresh frames from both mics before recording, bounded input/mux queues, no silent frame loss on encoder backpressure. Save takes atomically and leave interrupted temporary takes private; document recovery limits honestly. Persistent settings survive reboot; no privileged state needs restoration. First target is original Mic Mini pair, Pixel 8 Pro. Start with 1080p/30 H.264 plus 48 kHz stereo AAC; use camera/encoder capability checks. Other mic generations remain unsupported. Long-duration physical clock drift remains a required Pixel check, not an emulator claim.

## Validation
Unit tests: stereo assignment, gains/clipping, timeline and sample timestamps, cancellation/state transitions and mux coordination. Emulator: real camera preview, H.264/AAC encoding and MP4 save using debug-only synthetic left/right input; repeated recordings, background/foreground, permission denial and reboot-ready idle behavior. Release build contains no synthetic-source entry point. Verify merged manifest lacks INTERNET, debugging or boot-autorecord dependencies. Build signed APK/AAB; keep previous Play release unchanged until actual Pixel audio/video tests pass.

## Pixel handoff
Connect Pixel 8 Pro directly to this Mac with a USB-C data cable, leaving both transmitters disconnected from USB. One user step: unlock the Pixel and accept the USB-debugging prompt if shown. That debugging is only for development installation/tests, not application recording. Test with Wi-Fi and Wireless debugging off, then reboot and record again. Prompt the user before each speech take.
