# Compatibility and permissions

## Supported configuration

| Component | Current scope |
| --- | --- |
| Android | Android 10/API29 or later; target API36 |
| CPU | ARM32, ARM64, x86_64 native codec builds |
| Camera | Camera2; negotiated modes 4K30 (default), 4K60, 1080p60, 1080p30; session-level video stabilization and OIS when advertised; tap-focus, zoom and torch when advertised; 10-bit HLG HDR only where camera and encoder both support it (30 fps modes only); OIS is used when video stabilization is unavailable; 10-bit HDR requires Android 13+ |
| Microphones | Two original DJI Mic Mini transmitters paired through classic Bluetooth |
| Physical checks | Pixel 8 Pro (connected 2026-10-18), Pixel 6 / Android 15; version-scoped results in [verification](offline-verification.md) |

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

A previous real-microphone candidate completed a 15-minute take, but another stopped on a large sequence discontinuity and retained a playable partial video. The cause is unclassified. Code 7 passed a short real-microphone background capture; final-code endurance and a physical microphone reboot check remain unverified. Independent microphone clock drift and Bluetooth scheduling need more hardware coverage.

Camera mode availability is negotiated per device. Unsupported modes are shown disabled with a reason, HDR shows a disabled-state hint when unavailable, and controls whose capability keys are absent are hidden. On devices without a canonical 16:9 mode, a bounded legacy fallback records at the largest shared camera/encoder size up to 1080p (30 or 24 fps) and shows its derived label in the mode chip. Pixel Camera's closed computational video features (Video Boost and similar) are out of scope.

Version 0.2 records inside Stereo Bridge. It does not expose a virtual audio device to other camera apps. Historical version 0.1 used a different debugging-dependent stock-camera bridge.
