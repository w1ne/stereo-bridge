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

Public repository and signed prerelease are published at github.com/w1ne/stereo-bridge. Fresh hosted Linux CI passed unit tests, debug assembly and release bundle build. GitHub Pages deployment succeeded; HTTPS website and privacy policy are live at shylenko.com/stereo-bridge/. The Google Play new-app form is prepared and package availability confirmed; creation is pending the publisher’s required policy/export-law declarations. Store submission has not occurred.
