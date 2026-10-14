# Build and verify

Use **JDK17**, **Android SDK36**, **NDK28.2.13676358** and **CMake3.22.1**. The Gradle wrapper pins version9.6.0 and its checksum.

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug :app:bundleRelease
```

Application ID: `app.stereobridge`; debug adds `.debug`. Release builds are installable only when signed. Set `STEREO_BRIDGE_SIGNING_PROPERTIES` to an external properties file with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Keep credentials outside the repository.

## Synthetic camera checks

Debug builds support test tones; release builds disable synthetic input. On an emulator:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
python3 scripts/offline-emulator-smoke.py --serial emulator-5554 --output /tmp/stereo-smoke
python3 scripts/verify-stereo-mp4.py /tmp/stereo-smoke/videos
```

The harness covers repeat capture, background/sleep and reboot with Wi-Fi off. The verifier checks separate tones, timestamps and silence. Synthetic results do not establish physical microphone reliability.

## Physical checks

Record the exact phone, Android/app versions and mic model. Exercise first-use/denied permissions, Record/Stop/repeat, disconnect/reconnect, sleep/background and reboot. Decode saved MP4s and listen to prompted speech on each mic. Document failures and limits in [verification](offline-verification.md).

Never commit owner recordings, full Bluetooth logs, local SDK settings or signing material.
