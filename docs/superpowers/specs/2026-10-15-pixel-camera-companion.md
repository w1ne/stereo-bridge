# Pixel Camera companion prototype

Andrii approved investigating a separately installed locally patched Pixel Camera copy. Keep the original Camera and released Stereo Bridge untouched. No root, accessories or OS update. Daily target: power on two paired transmitters and tap Record in the companion camera; no patcher/framework UI during recording.

First gate: clone the installed APK/split set under a separate package, change only explicit package/authority/label identity required for parallel installation, and prove the camera starts. Second gate: intercept its app-local AudioRecord reads with deterministic separate left/right tones and verify the saved video contains them. Third gate: replace fixture PCM with the existing independently decoded DJI audio engine, authenticated local IPC, and coherent timestamps. Preserve the Camera video pipeline; verify rather than assume quality equivalence. Do not publish Google APKs or decompiled source.

Capture must not silently use the phone mic if the bridge fails. Final user flow needs clear readiness, connection failure and Stop/save behavior. A working experimental capture is not a Play-ready universal microphone provider. Do not release or advertise unsupported behavior.

## Everyday use requirement

Pair and grant permissions once in a guided setup. Remember the two microphone identities and left/right assignment. On later launches, reconnect automatically and show one primary Record button. Keep gains and routing at safe saved defaults; expose advanced controls separately.

Before recording, show both microphones with distinct live meters and a short Ready status. If a microphone is missing, show its friendly name and one actionable instruction. Never silently substitute the phone microphone. During recording, make Stop unmistakable, keep recording state visible, and report a disconnect immediately. After Stop, confirm that the video was saved and offer playback.

Target user instruction: **Turn on both mics, open Stereo Camera, tap Record.** Wi-Fi and debugging must not be daily prerequisites. Reboot, reconnection, permission denial, interruption, and repeated recording checks are release gates. This is a target, not a claim that the current prototype meets it.


## Revised architecture checkpoint

The clone approach stopped at its startup compatibility gate. A working Pixel prototype now uses the original Camera capture intent, parallel direct Bluetooth capture, and automatic local audio replacement afterward. No additional camera app is needed. This preserves the compressed video track, while Camera auxiliary metadata tracks are currently omitted. The helper is required to start the session; opening Camera alone does not start DJI capture. See experiments/camera-handoff/README.md for verified behavior and release limits. Original one-button intent remains a usability goal; Camera's capture intent exposes its own Record, Stop, and Done controls.
