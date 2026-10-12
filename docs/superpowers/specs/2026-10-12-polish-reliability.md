# Stereo Bridge polish and reliability

Approved by user: redesign and harden the essential flow, following the phone theme; test on Pixel; publish public repository and prepare/submit Google Play application.

## Interface
Follow system light/dark appearance using native Android views, an adaptive palette and shared styling. Dashboard has brand header, a distinct session status card, two microphone cards with names, signal meters and mute/clipping state, one large Start/Stop button, and collapsed Advanced controls. Camera recording remains in Camera. Start automatically selects exactly two paired Mic Mini devices. Existing controls and settings persist. Respect system insets and font scaling; use 48dp minimum touch targets. No raw diagnostics on the main screen. Setup is a short, numbered guide with its own connection status and collapsed manual fallback. Add an original vector launcher icon.

## Reliability
Stop and failure cleanup must execute off the Android main thread. Stop cancels in-progress Bluetooth/ADB/socket operations; session finalization finishes before a new session begins. Notify users of failures even with Camera in the foreground. No auto restart or background microphone activation after failure. Clear startup backlog before declaring Live, and count overflow during Live separately from connection buffering. Validate Bluetooth state, distinct bonded mics, installed camera and required permissions before starting. Bound waits and preserve user-controlled audio previews on normal Stop. Guard setup callbacks after destruction and duplicate setup work. Make microphone status and session mode visible to UI through explicit state.

## Verification
Pure Java regression tests cover PCM buffering, startup/live accounting, status presentation, file headers and interrupted take recovery if implemented. Run unit tests, release lint, APK/AAB builds, signature and alignment verification. On Pixel: inspect both themes; Start/Stop/repeated sessions; stop during connection; app process death; audio preview; stock-camera stereo clip. Do not equate technical stereo format with speech intelligibility. Keep physical listening confirmation separate.

## Distribution
Publish only reviewed source, licenses, documentation, public UI screenshots and signed artifacts. Exclude recordings, lab material, pairing and signing credentials. Public repo destination defaults to authenticated w1ne/stereo-bridge. Store support email/account supplied by user; do not invent contact details or claim approval. Prepare truthful compatibility, setup, privacy and data-safety text plus screenshots. Attempt console submission with available account; report concrete remaining console requirements.
