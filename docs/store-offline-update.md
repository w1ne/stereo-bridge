# Google Play release record — 14 October 2026

## Submitted update

**Stereo Bridge 0.2.0-offline-rc4, version code 7**, was submitted under Artomatica. Publishing overview shows **Changes in review**. Google's automatic quick checks were still running at submission. Managed publishing is off: approved changes will publish automatically at 100% rollout in the existing 178 targeted countries/regions. This is submission confirmation, not approval or a claim that the offline update is already live.

Submitted together:

- Signed code 7 AAB; codes 5 and 6 excluded from the production draft.
- Accurate offline short/full description.
- Four current Pixel recorder screenshots, with the recorder screen first.
- New feature graphic matching the offline architecture.
- Camera/connectedDevice foreground-service declarations explaining local Camera2 capture and received paired RFCOMM audio, with the [current muted demonstration](https://shylenko.com/stereo-bridge/assets/review-demo-offline.mp4).
- Reviewer hardware/setup instructions for code 7: two original DJI Mic Mini transmitters, ordinary pairing, Camera/Nearby devices, Record/Stop. No login, Wi-Fi, receiver or debugging.

The legacy microphone-service declaration/demo remains associated with the existing code 3 bridge while it is still active. Code 7 does not request RECORD_AUDIO or FOREGROUND_SERVICE_MICROPHONE. Data-safety declarations remain no off-device collection/sharing, no advertising or analytics. The current [public privacy policy](https://shylenko.com/stereo-bridge/privacy.html) and [version 0.1 archive](https://shylenko.com/stereo-bridge/privacy-0.1.html) are live. AI-assisted icon/feature artwork is labeled; screenshots are actual device captures. The owner approved developer imagery. Public demonstration video has no audio and masks the phone home screen.

## Verification

- 109 local unit tests passed; release lint zero errors/nine warnings; signed APK/AAB built and signature/16KiB ZIP checks passed.
- Native libraries match the alignment-verified code 6 binaries; bundled privacy matches source.
- Exact application source `1f9a6b3` passed [API29 hosted capture checks](https://github.com/w1ne/stereo-bridge/actions/runs/38601216505): repeat/background/sleep/reboot, separate synthetic stereo tones, monotonic timestamps and no silent 100 ms windows.
- Pixel 6 / Android 15 real code 7 take saved after background recording; 22.39-second 1080p H.264/stereo AAC MP4 fully decoded. Earlier code 6 real take 42.34 seconds also decoded.
- Multiple independent source reviews addressed codec profile/level, native preview transform, permissions and session/descriptor lifetime. Final focused reviews found no serious issues.

[Version-scoped verification and limits](offline-verification.md) retain the separate earlier 375-second sequence-loss stop and 15-minute successful take. Final-code endurance and physical microphone reboot remain unverified. No universal phone compatibility or Google approval is claimed.

## Public source and downloads

[Organized repository](https://github.com/w1ne/stereo-bridge) · [Signed APK/AAB and checksums](https://github.com/w1ne/stereo-bridge/releases/tag/v0.2.0-offline-rc4) · [Usage](usage.md) · [Compatibility](compatibility.md)

Published asset digests match the local signed artifacts. Raw microphone recordings and full diagnostic logs remain private. The Pixel retains the final signed release; test app removed, original Wi-Fi/size/rotation restored, wireless debugging stays off.
