# Privacy

Stereo Bridge processes audio from the two Bluetooth microphones you select, only after you press Start or Record audio preview. It stops when you press Stop, use the notification Stop action, or the microphone connection fails.

Audio is decoded on your phone. When you choose an audio preview, the latest take is stored in the app's private storage for playback. Camera bridge sessions do not retain microphone audio in Stereo Bridge. A new completed audio preview replaces the previous take. Uninstalling the app deletes these private recordings. Camera video is saved by your camera app under that app's own storage and privacy settings.

Stereo Bridge has no analytics, advertising, cloud audio upload, or account requirement. Internet permission is used for an authenticated connection to the same phone's local wireless debugging service and a loopback audio connection. Discovered debugging services on other devices are not connected.

The app saves your chosen microphone addresses, camera package, gain/mute settings, and a local ADB identity. The identity is encrypted with a key stored in Android Keystore. Application backup is disabled. Pairing codes are not stored.

Microphone and Bluetooth access are needed to capture audio. Notifications show an active recording and provide Stop. Camera bridging requires the owner's explicit Android wireless debugging pairing; no root access or Shizuku is used.

Publisher: Artomatica. Contact: [andrii@shylenko.com](mailto:andrii@shylenko.com). Effective date: 12 October 2026.

To delete saved settings, the local pairing identity and audio previews, clear Stereo Bridge storage in Android Settings or uninstall the app. Removing the app’s paired entry in Wireless debugging also revokes its camera-routing authorization.
