# Stereo Bridge release design

## Objective
An open-source, standalone Android audio bridge that feeds two original DJI Mic Mini transmitters to left/right stereo channels in stock Pixel Camera. Video stays in the camera app. No receiver, root, Shizuku, or computer required during recording.

## Setup and interface
Users enable Android Wireless debugging, pair this app using the system pairing code, select two bonded Mic Mini transmitters, and choose a camera app. Dashboard shows connection status, independent left/right RMS and clipping indicators, gain/mute controls, channel swap, and Start/Stop/Open Camera actions. Settings are persistent. Setup explains reactivation after reboot. No unprompted microphone startup.

## Components
Native Android app UI, persistent configuration, foreground bridge service, independent Bluetooth transport/protocol driver, audio DSP, and shell-identity routing helper packaged inside the APK. A built-in open-source ADB client starts the packaged helper through user-authorized local wireless debugging. No executable code downloaded at runtime. Camera UID is resolved through PackageManager; transmitter addresses come from user selections.

The independent transmitter driver requires verified framing, stream-start handshake, and an open-source decoder. The old DJI Mimo service is a laboratory reference, not a dependency of the release architecture. Do not ship DJI binaries, copied implementation, or the private Mimo integration in this repository.

## Reliability and privacy
Typed state transitions distinguish setup needed, connecting, live, reconnecting, stopping, and error. Single session ownership; bounded queues; cancellation and timeouts; Binder/process and Bluetooth disconnect handling; drop metrics; stop releases audio policies. Any PCM IPC uses per-session authentication and loops only over the local device. No stored camera audio by default; user-started audio tests retain only the latest take for replay. No analytics or audio upload. Private pairing credentials and app signing keys excluded from source control and backups.

## Release scope
First verified configuration: Pixel 8 Pro, Android 17, original DJI Mic Mini pair. Other devices must pass capability checks and remain unverified until actual tests. Build signed release APK and AAB plus build instructions, license notices, privacy policy, compatibility notes, and issue templates. Prepare an open-source repository; no automatic public upload of recordings, keys, or research assets. Google Play approval is not guaranteed and must be confirmed through submission review, especially for privileged routing and device protocol usage.

## Acceptance
No Mimo/Shizuku package dependency or phone-specific addresses in release code. Two physical microphones identified by independent tap/speech takes in an unmodified stock-camera recording. On-phone setup starts the packaged helper from a non-debuggable build. Controls persist across process recreation; stop/reconnect/reboot flows tested. APK/AAB build reproducibly; source and packaged third-party notices are complete.
