# Google Play listing — Stereo Bridge

Publisher: Artomatica
Package: app.stereobridge
Language: English (United States)
Category: Video Players & Editors
Pricing: Free
Support: andrii@shylenko.com
Website: https://w1ne.github.io/stereo-bridge/
Privacy: https://w1ne.github.io/stereo-bridge/privacy.html

## Short description
Two original DJI Mic Mini transmitters. Stereo audio in your camera. No receiver.

## Full description
Stereo Bridge connects two original DJI Mic Mini transmitters directly to your Android phone and routes them into your camera as separate left and right audio channels. No DJI receiver is needed. Audio decoding and mixing run on your phone; video stays in your camera app.

Tap Start to connect both microphones and open Camera. Use Camera’s record button to shoot video. Stop the bridge in Stereo Bridge or its persistent notification when finished.

ESSENTIAL CONTROLS
• Independent left and right microphone levels
• Gain and mute for each microphone
• Swap left and right channels
• Audio-only preview with on-phone playback
• Light and dark appearance following your phone

SETUP REQUIREMENTS
Pair both transmitters in Android Bluetooth settings. Camera routing requires Developer options and Wireless debugging, a Wi-Fi connection for setup, and a one-time local pairing code. Keep Wireless debugging enabled while bridging audio. The app guides you through setup. Microphone, nearby-device and notification permissions support capture and visible connection alerts.

COMPATIBILITY
The release candidate is tested with two original DJI Mic Mini transmitters on Pixel 8 Pro running Android 17 with stock Pixel Camera. Other phones, Android versions and camera apps are not yet validated. DJI Mic Mini 2, DJI Mic and DJI Mic 2 are not supported by this driver. Please check this compatibility before installing. A successful routing connection does not mean Camera has started video recording.

LOCAL AND OPEN SOURCE
No accounts, advertising, analytics or cloud audio upload. Only your latest user-started audio preview is saved privately in Stereo Bridge. Camera recordings are saved by your camera app. Source code and third-party licenses are public on GitHub.

Stereo Bridge is an independent project and is not affiliated with or endorsed by DJI. DJI and product names belong to their respective owners.

## App access for review
No app account or login is required. Reviewers need a Pixel 8 Pro / Android 17 and two original DJI Mic Mini transmitters. Pair both in Bluetooth; open Stereo Bridge; tap Start; grant permissions; follow Wireless debugging setup and enter the six-digit local pairing code through the app notification; return and tap Done. Camera opens after both inputs are live. Camera’s record button starts video. Stop from Stereo Bridge or its notification. Audio preview under Advanced can validate captured speech without camera routing. No publisher-managed credentials or remote service are required.

## Data safety draft
No user data collected off-device or shared by Stereo Bridge. Audio is processed on-device. App has no analytics/ads/remote backend. Camera app’s own recording behavior is outside Stereo Bridge. Local ADB identity, microphone choices and audio preview can be deleted by clearing storage or uninstalling. Verify declaration against final packaged dependencies before submission.

## Foreground service declaration draft
Foreground microphone service runs only after explicit Start or Record audio preview. The ongoing notification shows activity and Stop. It is required to continue capturing both Bluetooth microphone streams while the selected camera is foreground. Stop, disconnect or failure terminates capture and releases routing resources. A local wake lock supports continuous capture. No automatic restart after errors or reboot.
