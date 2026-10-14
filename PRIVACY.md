# Privacy — offline recorder candidate

This policy describes Stereo Bridge 0.2.0-offline-rc4 (version code 7). The previously published bridge uses the policy distributed with that release.

Stereo Bridge records camera video and audio from the two selected original DJI Mic Mini Bluetooth transmitters only after you tap Record. Capture continues when the screen sleeps or another app is open. Tap Stop in the app or recording notification to finish; cancel startup to cancel. A capture connection failure stops capture. Recording does not start automatically after reboot. If notifications are disabled, return to the app to Stop; enable them in Notification settings for a visible Stop action.

Audio is decoded and mixed on your phone. Videos are written to an app-owned pending item in Movies / Stereo Bridge and published when finalized. A finalized take whose publication fails is kept for a save retry. Completed videos remain if you uninstall the app. Unfinished pending captures and private recovery metadata may be removed during recovery; clearing app storage or uninstalling deletes private metadata. Delete completed videos using your gallery or file manager.

The app has no Internet permission, analytics, advertising, cloud upload or account requirement. It does not use wireless debugging, a local ADB identity, root or Shizuku.

The app saves selected microphone addresses and names, gain/mute/channel settings, the URI of the last saved video and pending-video recovery metadata. Application backup is disabled. Clear application storage or uninstall to delete these settings. Bluetooth pairings are managed separately by Android Settings.

Camera permission enables video. Nearby devices permission on Android 12 and later permits connections to paired Bluetooth transmitters; Android 10 and 11 use ordinary Bluetooth access. The app does not request phone Microphone, location or broad storage permission. The foreground service uses camera and connected-device access for active camera capture and Bluetooth RFCOMM streams. Notifications are optional and provide recording status and Stop. A wake lock keeps an active capture running when the screen sleeps.

Publisher: Artomatica. Contact: andrii@shylenko.com. Effective date: 14 October 2026.
