# Privacy — offline recorder candidate

This policy describes Stereo Bridge 0.2.0's offline recorder. The previously published 0.1.0 bridge uses the policy distributed with that release.

Stereo Bridge captures video and audio from the two Bluetooth microphones you select only after you tap Record. Capture stops when you tap Stop, use the notification Stop action, cancel startup, or a capture connection fails. It does not start recording after a reboot.

Audio is decoded and mixed on your phone. Completed MP4 videos are saved to Movies / Stereo Bridge and remain there if you uninstall the app. Temporary takes are stored privately while saving. A failed save can leave a private take for recovery; clearing app storage or uninstalling deletes private files. Delete completed videos using your gallery or file manager.

The app has no Internet permission, analytics, advertising, cloud upload or account requirement. It does not use wireless debugging, a local ADB identity, root or Shizuku.

The app saves your selected microphone addresses and names, gain/mute/channel settings and the URI of the last saved video. Application backup is disabled. Clear application storage or uninstall to delete these settings. Bluetooth pairings are managed separately by Android Settings.

Camera, Microphone and Nearby devices permissions enable capture. Notifications show recording progress and provide Stop. A wake lock keeps an active capture running when the screen sleeps. No location permission is requested.

Publisher: Artomatica. Contact: [andrii@shylenko.com](mailto:andrii@shylenko.com). Effective date: 13 October 2026.
