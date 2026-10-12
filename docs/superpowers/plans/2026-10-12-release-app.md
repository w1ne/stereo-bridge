# Stereo Bridge Implementation Plan

**Goal:** Build an independent, open-source Android audio bridge with a standalone setup flow and release artifacts.
**Architecture:** Native Android application with a foreground audio service, independent transmitter driver, DSP, and packaged local shell routing helper. User-authorized wireless debugging provides routing privileges.
**Tech Stack:** Java 17, Gradle/Android Gradle Plugin, Android Bluetooth/AudioPolicy APIs, open-source ADB pairing client and audio codec.

## Task 1 — Independent transmitter investigation
- [ ] Capture/inspect transmitter protocol with owned devices and existing laboratory byte captures.
- [ ] Determine stream handshake, packet framing, and codec against real audio.
- [ ] Implement independently in focused transport/parser/decoder classes with malformed/partial packet checks.
- [ ] Verify two streams against physical speech/taps; do not substitute noise or partial decoding.

## Task 2 — Application and build
- [ ] Establish isolated new repository and feature branch; ignore local credentials, outputs, and audio artifacts.
- [ ] Add standard Gradle wrapper, release/debug variants, resources, manifest, and automated CI build.
- [ ] Add persistent device/camera/channel settings and DSP tests for conversion, gain, mute, swap, clipping.
- [ ] Build a readable dashboard, setup screen, microphone/camera selectors, and foreground notification with Stop.

## Task 3 — Local startup and routing
- [ ] Integrate open-source ADB TLS/pairing with app-private credentials and explicit pairing flow.
- [ ] Package the routing helper in the APK; resolve camera UID dynamically; authenticate per-session PCM IPC.
- [ ] Register recorder injector mix and validate successful routing before declaring Live.
- [ ] Cancel and release sessions on stop, disconnect, and process death; use bounded audio queues and metrics.

## Task 4 — Release verification and documentation
- [ ] Build and inspect a non-debuggable release APK/AAB; signing keys stay private.
- [ ] Install on Pixel and test standalone setup, physical channel identity, gain/mute/swap, stop/restart, disconnections.
- [ ] Document actual compatibility and remaining restrictions; include license and privacy documents.
- [ ] Review source for secrets, binaries, proprietary dependencies, placeholders, and unsupported success claims.
- [ ] Deliver repository and verified artifacts. Publish only the concrete reviewed source/artifacts when authorized.
