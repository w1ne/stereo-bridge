# Stereo Bridge

**Two mics. One recording.** An open-source Android video recorder for two original DJI Mic Mini transmitters, without a receiver.

## Daily use

**Open Stereo Bridge and tap Record.** Tap **Stop** to save the stereo video to **Movies / Stereo Bridge**.

First use only: pair both transmitters in Android Bluetooth settings and allow Camera, Microphone and Nearby devices. The app automatically selects exactly two paired original Mic Mini transmitters. If more are paired, choose left and right in Advanced controls. Turn on Bluetooth and both microphones before recording.

Microphone assignments, gain, mute and channel swap are saved. Recording needs no Wi-Fi, Internet, computer, Developer options, wireless debugging, root or Shizuku. After a phone reboot, open the app and tap Record again. It never starts recording automatically.

## Offline candidate status

This branch builds **0.2.0-offline-rc1**, a new architecture: Camera2 captures video inside Stereo Bridge; independent Bluetooth microphone capture feeds two-channel AAC; Android MediaMuxer saves an H.264/AAC MP4. The two selected microphones occupy left and right channels. Videos are capped at the largest available 16:9 camera mode up to 1080p, with a 30 fps target. This candidate uses its own camera screen.

The previous **0.1.0-rc3** stock-camera bridge remains available in [published releases](https://github.com/w1ne/stereo-bridge/releases). That older version requires wireless debugging and is a separate architecture. The offline candidate has not yet been physically validated on the Pixel and has not replaced the Google Play submission. See [offline verification](docs/offline-verification.md).

Only original DJI Mic Mini transmitters are supported. Other phones, DJI generations and manufacturers need separate validation. Audio startup is aligned using driver timestamps; long-term independent microphone clock drift still requires hardware testing.

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
