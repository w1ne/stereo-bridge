# Google Play listing DRAFT — offline candidate code 6

Publisher: Artomatica
Package: app.stereobridge
Language: English (United States)
Category: Video Players & Editors
Pricing: Free
Support: andrii@shylenko.com
Website: https://shylenko.com/stereo-bridge/ (offline update not deployed)
Privacy: https://shylenko.com/stereo-bridge/privacy.html (offline update not deployed)

## Short description
Record stereo video with two original DJI Mic Mini transmitters. No receiver.

## Full description
Stereo Bridge records video inside the app with two original DJI Mic Mini transmitters as separate left and right audio channels. Audio decoding and mixing run on your phone. No receiver, account, Wi-Fi or wireless debugging is needed.

Pair both transmitters in Android Bluetooth settings, open Stereo Bridge and tap Record. Tap Stop to save an MP4 to Movies / Stereo Bridge. Capture continues when your screen sleeps or another app is open. Optional notifications show recording status and provide Stop. If notifications are off, return to the app to Stop.

Advanced controls include independent microphone meters, gain, mute and left/right channel swap. Camera and Nearby devices access on Android 12 or later enable capture. Android 10 and 11 use ordinary Bluetooth access. Phone Microphone, location and broad storage permissions are not requested.

The candidate supports Android 10 or later, a camera and classic Bluetooth, with ARM 32-bit, ARM 64-bit and x86_64 builds. Physical microphone checks use Pixel 6 / Android 15 and two original DJI Mic Mini transmitters. Broader devices and sustained capture remain subject to validation. DJI Mic Mini 2, DJI Mic and DJI Mic 2 are unsupported.

No advertising, analytics or cloud upload. Completed videos stay on your phone and remain after uninstalling. Source and third-party licenses are public on GitHub. Stereo Bridge is independent and is not affiliated with or endorsed by DJI.

## App access for review DRAFT
No login required. Use two original DJI Mic Mini transmitters paired in Android Bluetooth settings. Open app → Record → allow Camera and Nearby devices (Android 12+) → speak into each microphone → Stop → Open last video. Enable optional notifications to demonstrate background Stop. Background/sleep recording must be demonstrated. Current physical verification uses Pixel 6 / Android 15; no universal microphone compatibility claim.

## Data safety DRAFT
No user data collected off-device or shared. Audio/video are processed and stored locally. Pending MediaStore videos and recovery metadata support finalization/retry. Microphone selections/settings and recovery metadata are deleted by clearing storage or uninstalling; completed videos can be deleted in a gallery/file manager. Verify final dependencies and declarations before submission.

## Foreground service declaration DRAFT
Declare camera and connectedDevice types. Explicit Record starts camera capture and paired external Bluetooth RFCOMM audio streams. Visible status and optional notification Stop support ongoing user-initiated recording; denied notifications still permit capture and in-app Stop. Stop/cancel/failure terminates capture. A wake lock maintains capture during screen sleep. No automatic recording after reboot. Submit the real foreground/background recording demonstration and exact external-device use for Google review; acceptance remains pending.
