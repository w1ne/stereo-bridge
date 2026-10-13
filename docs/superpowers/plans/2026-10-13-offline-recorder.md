# Offline Recorder Implementation Plan

> For agentic workers: use superpowers:subagent-driven-development for isolated components and explicit review checkpoints.

**Goal:** One-button offline stereo video recording that remains ready after reboot.
**Architecture:** Camera2 foreground recording service plus direct stereo PCM → AAC and video Surface → H.264, serialized into MediaMuxer MP4. No privileged routing or network dependency.
**Tech Stack:** Android Java 17, Camera2, MediaCodec, MediaMuxer, MediaStore, independent SPP/LC3 driver, JUnit, Android emulator.

- [ ] Add tested stereo PCM/timestamp/session primitives; preserve driver and gain controls.
- [ ] Build OfflineVideoEncoder with bounded format/sample coordination, partial input handling and finite EOS drain; test media components on emulator.
- [ ] Add OfflineRecorderService with camera capability selection, preview ownership, fresh dual-mic gating, cancellable sessions and MediaStore saves.
- [ ] Replace launcher with preview, two meters and one Record/Stop control; remove debugging setup and dependencies from compiled app.
- [ ] Install official emulator, add debug-only synthetic microphone harness and exercise MP4 encoding, repeat, lifecycle, reboot and permission flows.
- [ ] Review spec compliance and code quality, fix findings, build signed offline candidate and document evidence/limitations.
- [ ] Provide exact Pixel cable/user step and final one-step usage. Do not replace Play release before physical validation.
