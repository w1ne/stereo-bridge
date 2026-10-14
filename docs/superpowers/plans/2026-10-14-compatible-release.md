# Compatible, minimum-permission offline release

User authorization: broad compatibility, minimum ordinary privileges, independent re-review and Google Play readiness. Existing offline one-button architecture remains; Google decides approval. Do not publish claims of universal mic support or completed endurance validation.

## Design

- Runtime access: CAMERA and paired-device Bluetooth only. Optional notifications provide recording Stop. Use camera|connectedDevice foreground service for Camera2 plus RFCOMM external devices; document this exact transport honestly in Play. No Android AudioRecord input or phone-microphone permission. No Internet, discovery/location, storage, debug/root privileges.
- Minimum Android 10/API29 enables scoped MediaStore without broad file permissions. Legacy BLUETOOTH permission maxSdk30; CONNECT runtime request on API31+. Include portable 32-bit ARM, arm64 and x86_64 native builds where compilation succeeds. Explicit optional implied features avoid accidental Play exclusion. Keep camera.any and classic Bluetooth required.
- Camera/encoder negotiation considers usable cameras, orientation/lens facing/display rotation, encoder format capabilities and smaller fallback profiles. Avoid relying on portrait lock for tablets; orientation at capture start remains fixed for the take, preview can resize/rotate safely.
- Direct app-owned pending MediaStore FD recording removes full-video duplication on Save. Persist a pending URI and finalized state; recover publication after a failed update/restart. Delete only known own unusable pending captures. Retain accessible finalized output on publication failure with retry; do not silently discard playable video.
- Local in-app privacy policy and correct website/store drafts; precise explanation of continued capture on sleep, denied-permission Settings recovery and optional notification limitations. Broader compatibility is supported by capability checks, with hardware scope reported honestly.
- Sequence-loss behavior changes only after diagnostic evidence and targeted tests. Keep protective partial-save behavior for ambiguous jumps; do not blindly enlarge concealment.

## Tasks and ownership

1. UI/permission/build task: MainActivity, AndroidManifest, app/build.gradle, generated/local privacy assets, PRIVACY.md, store/site drafts. Tests for denied/granted permissions and manifest/ABIs; avoid service and encoder edits. Preview orientation integration coordinated with camera task.
2. Camera/codec task: OfflineRecorderService camera selection and rotation, OfflineVideoEncoder codec negotiation, new focused pure helpers/tests. Also adjust service FGS type and checks to match task1. Exclusive ownership of these two files until released; preserve existing diagnostic log.
3. Storage task: new RecordingStore helper + pure journal/recovery tests first. Root integrates FD constructor/service lifecycle after camera ownership releases. Validate direct publication, restart recovery and scoped ownership.
4. Root: collect long-test diagnostic sequence events, design/test any evidenced recovery fix, integrate tasks, build signed next candidate and verify fresh Pixel release with ordinary permissions and offline startup/save/repeat/background/reboot. Owner PIN unlock only when necessary.
5. Independent spec then quality re-review, address important findings, record limitations and artifacts, synchronize public repo through connected GitHub app if CLI token remains invalid. Keep Play submission separate until required validation/declarations/assets are complete.

## Acceptance evidence

Fresh unit/lint/signed builds; release manifest/ABI/16KiB checks; physical Pixel capture and full MP4 decode/timestamps; denied permissions and no RECORD_AUDIO; rotation/profiles/unit capability tests; direct pending-store finalization/recovery; repeat/disconnect/retry/endurance; clear owner speech confirmation already obtained on earlier candidate. Broader OS/ABI execution documented separately from physical mic hardware validation.
