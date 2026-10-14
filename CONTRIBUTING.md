# Contributing

Build with JDK 17, SDK 36, NDK 28.2.13676358 and CMake 3.22.1. Run `./gradlew testDebugUnitTest lintRelease assembleDebug` before proposing changes.

See [architecture](docs/architecture.md) for component boundaries. Keep microphone drivers separate from camera/encoder lifecycle and the UI. Receive-only transports, bounded buffers and explicit user Record/Stop are core requirements. Add regression tests for lifecycle or PCM behavior changes. Test device-specific audio paths on real hardware and state the exact Android/camera/microphone versions.

Never include recordings, Bluetooth logs, signing keys or pairing codes in issues or patches. Share only app diagnostics with unrelated device data removed. See THIRD_PARTY_NOTICES.md for license obligations when modifying bundled components.
