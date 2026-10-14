# Stereo Bridge

**Two mics. One recording.** An open-source Android video recorder for two original DJI Mic Mini transmitters, without a receiver.

## Daily use

**Open Stereo Bridge and tap Record.** Tap **Stop** to save the stereo video to **Movies / Stereo Bridge**.

First use only: pair both transmitters in Android Bluetooth settings and allow Camera and Nearby devices (Android 12+); recording notifications are optional. The app automatically selects exactly two paired original Mic Mini transmitters. If more are paired, choose left and right in Advanced controls. Turn on Bluetooth and both microphones before recording.

Microphone assignments, gain, mute and channel swap are saved. Recording needs no Wi-Fi, Internet, computer, Developer options, wireless debugging, root or Shizuku. After a phone reboot, open the app and tap Record again. It never starts recording automatically.

## Offline candidate status

This branch builds **0.2.0-offline-rc2**, a new architecture: Camera2 captures video inside Stereo Bridge; independent Bluetooth microphone capture feeds two-channel AAC; Android MediaMuxer saves an H.264/AAC MP4. The two selected microphones occupy left and right channels. Camera and encoder capabilities choose an available mode up to 1080p, preferring 30 fps. Startup can retry smaller modes or another camera. This candidate uses its own camera screen.

The previous **0.1.0-rc3** stock-camera bridge remains available in [published releases](https://github.com/w1ne/stereo-bridge/releases). That older version requires wireless debugging and is a separate architecture. Pixel 6 / Android 15 has saved offline real-microphone stereo video and the owner confirmed clear speech. The earlier candidate completed one 15-minute take; another stopped and saved a partial take after a large audio sequence gap. The new permission/storage candidate is undergoing fresh tests and has not replaced the Google Play submission. See [offline verification](docs/offline-verification.md).

Only original DJI Mic Mini transmitters are supported. Other phones, DJI generations and manufacturers need separate validation. Audio startup is aligned using driver timestamps; long-term independent microphone clock drift still requires hardware testing.

## Compatibility and permissions

Android 10+ (API29), with ARM32, ARM64 and x86-64 native builds. Camera and classic Bluetooth hardware are required; autofocus, a rear camera and a built-in microphone are optional. Only original DJI Mic Mini transmitters are supported; broader phone support is capability-based and must be validated on actual devices.

The app uses Camera and paired Bluetooth access. It does not request phone Microphone, Bluetooth discovery/location, broad storage, Internet, root or debugging access. Notifications are optional. Videos are written directly to pending MediaStore items; failed publication can be retried, and unfinished captures are checked after restart. Recording always starts only when you tap Record.

## Build

Use JDK 17, Android SDK 36, NDK 28.2.13676358 and CMake 3.22.1. Gradle 9.6.0 is pinned with a distribution checksum.

```sh
./gradlew testDebugUnitTest lintRelease assembleDebug bundleRelease
```

Application ID: `app.stereobridge`; debug variant adds `.debug`. Release signing is optional for local builds. Set `STEREO_BRIDGE_SIGNING_PROPERTIES` to an external properties file containing `storeFile`, `storePassword`, `keyAlias` and `keyPassword` to sign. Keep credentials outside this repository.

The release app has no Internet permission or ADB dependency. A debug-only synthetic audio source supports camera/encoder checks without physical microphones; it is disabled in release builds. Private recordings, lab logs, keys and local SDK settings are ignored.

## Project

[Privacy](PRIVACY.md) · [Third-party notices](THIRD_PARTY_NOTICES.md) · [Driver notes](docs/independent-driver-research.md) · [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) · [Report an issue](https://github.com/w1ne/stereo-bridge/issues)

Apache-2.0 application source; Google liblc3 retains its license. Independent project, not affiliated with or endorsed by DJI. Publisher: Artomatica. Support: andrii@shylenko.com.
