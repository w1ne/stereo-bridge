# Release candidate verification — 2026-10-12

Version: 0.1.0-rc1. Tested device: Pixel 8 Pro, Android 17; two original DJI Mic Mini transmitters, no receiver.

- Signed release APK and AAB built successfully.
- Five DSP unit tests passed; release lint reported no errors.
- APK signature and 16 KB ZIP alignment checks passed.
- Primary Start automatically selected two paired microphones and opened Pixel Camera after routing became Live.
- Stock Camera saved a 51.19-second 3840 × 2160 video with 48 kHz, two-channel AAC. Both decoded channels contained distinct nonzero audio. This format and signal check does not establish speech intelligibility.
- After the helper cleanup fix, Stop terminated the shell routing helper. A second Start created a new session. Force-stopping the app also terminated its routing helper.
- The independently decoded prototype had user-confirmed recognizable speech. Final release camera speech playback still needs confirmation.

## Remaining distribution work

Provide a publisher contact and public privacy-policy URL, configure the intended distribution account, and submit the signed artifact. Store review and availability have not been verified. Other devices, Android releases, camera applications and microphone models are not validated.

## User flow

Start → guided first-time permissions/pairing → Camera opens → Camera record button. Stop in Stereo Bridge or its notification when finished. Advanced contains microphone assignment, gains, mute, channel swap and an audio-only preview.

## Candidate 0.1.0-rc2

- Wrapper Gradle 9.6.0 / JDK 17 built signed APK and AAB, all 31 unit tests, and release lint successfully. APK v2 signature and 16 KB ZIP alignment verified.
- Independent spec and quality reviews passed after fixes to ADB timeout/EOF handling and routing-write stall detection.
- Actual Pixel screenshots inspected in both light/dark modes. Light status-bar contrast was corrected subsequently; fresh screenshot still pending.
- Primary Start launched stock Pixel Camera and independent shell routing helper. Force-stop of the app removed its helper. Concurrent phone use prevented the remaining interactive check; no new camera recording claim is made for this exact build.
- Pending on this exact candidate: normal Stop/restart and cancellation exercise, physical speech playback in stock Camera, disconnect notification while Camera is foreground, and a longer drift/drop check.

Automated tests cover buffer overflow/live accounting, parser fragmentation/resynchronization, display-state labels, WAV format, real loopback ADB open/EOF behavior and route-write stall boundaries. They do not replace device audio verification.

## Public distribution

Public repository and signed prerelease are published at github.com/w1ne/stereo-bridge. Fresh hosted Linux CI passed unit tests, debug assembly and release bundle build. GitHub Pages deployment succeeded; HTTPS website and privacy policy are live at shylenko.com/stereo-bridge/.

Stereo Bridge has been created in Artomatica's Google Play Console after the publisher approved the policy and export declarations. Privacy, reviewer access instructions, advertising, advertising ID, target audience, data safety, government, financial and health declarations are saved. The listing copy, icon and feature graphic are saved as a draft. Google processed the signed rc2 bundle as version 2, minimum API 31, target API 36, arm64-v8a; the production release draft and notes are saved. Availability is configured for 178 countries/regions.

IARC terms were approved and the content rating is completed. The listing now includes two fresh phone screenshots and is saved as ready to send for review. Google also requires a demonstration video for the microphone foreground-service declaration. Store review submission has not occurred.

## Candidate 0.1.0-rc3

- Corrected scrolling content clipping beneath the status bar; inspected the fix on the Pixel.
- Both original transmitters connected after being restarted. Stock Camera saved a 43.09-second video with 48 kHz stereo AAC and distinct channel signals. The user reported audible interruptions during playback; this take is not release acceptance.
- Routing diagnostics showed the nonblocking AudioTrack writer discarding roughly 13% of PCM bytes on transient backpressure, with repeated underruns.
- Added a bounded, cancellable write retry that retains partially written frames. All 35 unit tests pass, including four regressions for partial writes, backpressure, absent consumers and cancellation/error handling.
- The updated build recorded a 40.45-second stock-camera video with 48 kHz stereo AAC. After playback, the user confirmed that it works well. The PCM discard counter remained unchanged during the speech recording. This is a short check, not a long-duration reliability or synchronization result.
- The final signed build and bundle, all 35 unit tests and release lint completed successfully. APK v2 signing and 16 KB ZIP alignment passed. A subsequent Start → Camera → app → Stop demonstration was captured; Stop removed the routing helper. Startup queue drops and underruns were observed, so zero-drop/zero-underrun operation is not claimed.
- Review demonstration: https://shylenko.com/stereo-bridge/assets/review-demo.mp4. It contains app/Camera UI without microphone audio or personal camera imagery. Longer recordings and clock drift remain unvalidated.
