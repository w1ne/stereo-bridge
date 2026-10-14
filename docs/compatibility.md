# Compatibility and permissions

## Supported configuration

| Component | Current scope |
| --- | --- |
| Android | Android 10/API29 or later; target API36 |
| CPU | ARM32, ARM64, x86_64 native codec builds |
| Camera | Camera2 and compatible H.264 encoder; negotiated mode up to 1080p |
| Microphones | Two original DJI Mic Mini transmitters paired through classic Bluetooth |
| Physical checks | Pixel 6, Android 15; version-scoped results in [verification](offline-verification.md) |

Rear camera, autofocus and a built-in microphone are optional. The app queries camera/encoder capabilities and tries smaller startup modes or another camera when needed. This does not prove every phone works.

Other DJI generations and other microphone brands are unsupported. EQ and additional mic drivers are future work, not advertised features.

## Permissions

| Access | Why |
| --- | --- |
| Camera | Capture video after Record |
| Nearby devices (Android12+) / Bluetooth (Android10/11) | Connect to already paired external transmitters |
| Notifications, optional | Recording status and Stop outside the app |
| Camera + connectedDevice foreground service | Continue user-started camera and external-device capture |
| Wake lock | Keep an active capture running with screen asleep |

No phone Microphone, Internet, location, Bluetooth discovery, broad storage, root or debugging access. MediaStore saves app-owned videos without browsing other files. Google Play evaluates the foreground-service declaration for the actual Camera2/RFCOMM transport.

## Known limits

A previous real-microphone candidate completed a 15-minute take, but another stopped on a large sequence discontinuity and retained a playable partial video. The cause is unclassified. The current code 6 endurance/reboot microphone check remains pending charged transmitters. Independent microphone clock drift and Bluetooth scheduling need more hardware coverage.

Version 0.2 records inside Stereo Bridge. It does not expose a virtual audio device to other camera apps. Historical version 0.1 used a different debugging-dependent stock-camera bridge.
