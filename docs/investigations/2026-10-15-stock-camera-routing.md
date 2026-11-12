# Stock Camera routing investigation — 15 October 2026

## Required behavior

Two original DJI Mic Mini transmitters should behave as one normal stereo microphone in the stock Pixel Camera. Video must be recorded by stock Camera. Separate video/audio capture and later replacement are not accepted as the solution. The earlier requirements also exclude a receiver, root, Wi-Fi and repeated debugging setup.

## Physical investigation

Pixel 6, Android 15. A temporary diagnostic APK (`dev.stereoroute`, target SDK 35) ran as its own application UID, 10341. No root, shell UID impersonation, hidden-API policy override or system role change was used. The released Stereo Bridge APK and source were unchanged. No microphone or video recording was required for these capability checks.

| Route | Observation | Meaning |
| --- | --- | --- |
| Normal audio settings | `MODIFY_AUDIO_SETTINGS` granted | This permission does not confer audio injection. |
| Injector AudioPolicy registration | Returns 0 | Registration alone is not proof of a usable virtual microphone. |
| `createAudioTrackSource` | Returns null with missing routing-permission warning | Framework refuses the injector source. |
| Native source creation after bypassing only the app-local Java precheck | Track remains uninitialized (state 0); AudioFlinger returns -1 | The system server independently enforces access. The local precheck is not the only obstacle. |
| Native log | `MODIFY_AUDIO_ROUTING denied for uid 10341`; `getOutputForAttr: permission denied: audio attributes tags not allowed` | Real permission denial from the phone's audio server. |
| One-time `pm grant` | `Permission android.permission.MODIFY_AUDIO_ROUTING is managed by role` | A normal persistent runtime grant cannot replace the earlier helper. |
| VirtualDeviceManager creation | Remote permission-enforcement failure at `createVirtualDevice_enforcePermission` | The virtual-device API is not freely available to this ordinary app. |
| Audio endpoints | One DJI HFP input is exposed, alongside built-in/system inputs | The two independent app-decoded SPP streams have not become a stereo system microphone. |
| Installed effects | Built-in EQ, AEC, NS and other platform engines | Effect controls do not establish a custom PCM input source. No custom DSP-engine loading was tested. |

Android 15's role definitions grant `MODIFY_AUDIO_ROUTING` to the static, hidden, system-only `SYSTEM_AUDIO_INTELLIGENCE` and `SYSTEM_AMBIENT_AUDIO_INTELLIGENCE` roles. The companion-computer role does not provide it (and the Android 15 role definition is itself system-only). The app-streaming request permission is itself privileged. Availability of a role is not eligibility to obtain it.

Bluetooth profile APIs control the platform's connected headset service. They do not provide a documented way for this app to present its mixed PCM as another headset connected to the same phone. No full Bluetooth-controller emulation implementation was attempted; this is an API/source finding, not a physical emulation test.

## Conclusion and limits

The exact requirement remains unmet. These tests establish concrete barriers to the investigated ordinary-app routes; they do not prove every possible undocumented implementation or future OS feature impossible. The earlier shell-helper bridge remains the demonstrated live stock-Camera route, with its debugging/startup limitations. A production solution must not be described as equivalent until ordinary permissions, reboot persistence and actual stock-Camera stereo recordings are verified.

No replacement capture architecture or new Play release was made during this investigation. The diagnostic process was stopped and Stereo Bridge reopened. Reproducible diagnostic source, APK, app report and audio-server excerpt are retained locally under `/private/tmp/stereo-routing-audit/` (temporary storage, not public assets).

## Primary references

- [Android 15 routing permission](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/core/res/AndroidManifest.xml)
- [Android 15 AudioPolicy client checks](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/media/java/android/media/audiopolicy/AudioPolicy.java)
- [Native audio-policy permission enforcement](https://android.googlesource.com/platform/frameworks/av/+/refs/heads/android15-release/services/audiopolicy/service/AudioPolicyInterfaceImpl.cpp)
- [Android 15 role definitions](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android15-release/PermissionController/res/xml/roles.xml)
- [Bluetooth profile APIs](https://developer.android.com/develop/connectivity/bluetooth/profiles)
- [AudioEffect API](https://developer.android.com/reference/android/media/audiofx/AudioEffect)


## Follow-up: communication streams, existing apps and USB loopback

Continued on the same Pixel on 15 October 2026.

### Root-free communication-stream claim

Inspected MicUp commit `aacf0b087dfd77eb73c69633d305f1a94386d0d6` without installing or running its code. Its README advertises a virtual microphone, but its current `SoftwareLoopback.kt` writes PCM to an ordinary AudioTrack and controls communication mode/output selection. That implementation does not register a new microphone device. The tier called MEDIA_PROJECTION in `VirtualMicService.kt` is a label/description, not evidence of successful injection into a stock Camera AudioRecord.

Built an independent small API probe to test the underlying communication-output hypothesis:

- Ordinary app UID 10341, microphone permission for this temporary test only.
- AudioTrack with USAGE_VOICE_COMMUNICATION, stereo PCM16/48 kHz, MODE_IN_COMMUNICATION, earpiece selected, low track gain 0.04.
- Generated 440 Hz left / 997 Hz right, amplitude 6000, with quiet periods before and after.
- Simultaneous AudioRecord with CAMCORDER source, stereo PCM16/48 kHz, six seconds total.
- Both stream objects initialized. All six input endpoint IDs/types were unchanged before/after starting the communication stream.
- During the generated tones, captured RMS was 1.84 left / 1.72 right in PCM16 units. Estimated 440 Hz amplitude was 0.03/0.04 and 997 Hz 0.03/0.04. No usable independently routed test tones reached the CAMCORDER recording.
- Communication mode was restored to MODE_NORMAL afterward. This was a direct CAMCORDER AudioRecord test, **not a new video test in the Google Camera application**. It does not establish every variation of communication routing impossible.

Android playback capture copies another app's permitted **output** into the capturing app. It does not make the capturing app's PCM replace an unrelated Camera app's microphone. Android 15 AudioMix.Builder also explicitly rejects RENDER / LOOPBACK_RENDER flags for recorder/injector mixes; the MediaProjection exception cannot simply be attached to an injector mix.

### ALSA / device emulation

On this Pixel, `/dev/snd/*` nodes are owned by system:audio with mode 0660; normal app access has not been established. `/proc/asound/cards` and `/proc/asound/pcm` are denied even to the shell. USB gadget configuration is protected. No kernel module was loaded, system audio configuration changed, role assigned, root operation attempted or boot setting changed.

LesserAudioSwitchAndroid11's published mechanism is a Magisk module, so it does not provide the required ordinary-app alternative.

### Supported hardware candidate, not yet tested

A full-duplex USB audio interface with **hardware USB playback-to-capture loopback** could receive our decoded stereo mix from AudioTrack and return it to Android as standard USB microphone audio. This is different from a DJI receiver: the phone would still receive and decode both Bluetooth transmitters. It nevertheless requires an extra USB accessory, so it is **not a solution to a strict no-additional-hardware requirement**.

Manufacturers document the relevant building blocks:

- IK Multimedia iRig Stream: Android USB audio support, stereo interface and connected-device loopback.
- Zoom AMS-22: Android support and a hardware LOOPBACK switch. Its compatibility sheet tests several Pixel models, but not this exact Pixel 6 / Android 15 / stock-Camera bridge configuration.
- Roland GO:MIXER PRO-X: manufacturer states built-in Android camera/video app compatibility.

The complete two-DJI → app mix → USB loopback → Pixel Camera chain has **not been physically verified**. Stereo preservation, latency, concurrent Bluetooth operation, Camera source selection and reboot behavior must be measured before recommending purchase or claiming support. No hardware was purchased and no USB-dependent app mode was published.

### Additional primary references

- [MicUp source inspected](https://github.com/papergray/MicUp/blob/aacf0b087dfd77eb73c69633d305f1a94386d0d6/app/src/main/java/com/micplugin/service/SoftwareLoopback.kt)
- [MicUp tier implementation](https://github.com/papergray/MicUp/blob/aacf0b087dfd77eb73c69633d305f1a94386d0d6/app/src/main/java/com/micplugin/service/VirtualMicService.kt)
- [Android playback-capture documentation](https://developer.android.com/media/platform/av-capture)
- [Android 15 AudioMix constraints](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/media/java/android/media/audiopolicy/AudioMix.java)
- [Android 15 debugging manager: Wi-Fi network check on enable](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/core/java/com/android/server/adb/AdbDebuggingManager.java)
- [LesserAudioSwitchAndroid11 requirements](https://github.com/michioxd/LesserAudioSwitchAndroid11)
- [Android USB digital audio](https://source.android.com/docs/core/audio/usb)
- [iRig Stream specifications and loopback](https://ikmultimedia.com/products/irigstream)
- [AMS-22 manual](https://zoomcorp.com/manuals/ams-22-en/)
- [AMS Android compatibility sheet](https://zoomcorp.com/documents/2471/AMS-series_Android_Devices_EN.pdf)
- [Roland stock-camera compatibility statement](https://support.roland.com/hc/en-us/articles/4402359188507-GO-MIXER-PRO-X-Which-Video-and-Audio-Recording-Apps-Are-Supported)

## Follow-up: phone-only helper bootstrap without an external Wi-Fi network

A new primary report from the Shizuku fork's maintainer confirms a local-only-hotspot / debugging-setting race on some newer Android builds:
https://github.com/thedjchi/Shizuku/issues/165

This is a different hypothesis from unrestricted virtual-microphone APIs: retain the previously working authorized shell routing helper, but bootstrap it locally after reboot. It still requires initial debugging authorization and potentially a one-time USB grant of WRITE_SECURE_SETTINGS. It is **not** an ordinary-permissions-only app and reliability/Play acceptance are unverified.

### Pixel 6 / Android 15 experiment

- Turned off station Wi-Fi temporarily; the phone was not connected to an external Wi-Fi network during this test.
- Temporary diagnostic APK successfully started a local-only hotspot through WifiManager.
- A first shell loop wrote adb_wifi_enabled=1 100 times; it returned to 0.
- A shell app_process fast-loop attempt failed before any writes because its ContentResolver attribution was incorrectly android rather than shell. Exit 137 was the crash handler killing that diagnostic process, not evidence that Android blocked a successful bootstrap.
- Granted WRITE_SECURE_SETTINGS temporarily to the diagnostic APK and ran the loop in its ordinary app process.
- 3,304 writes over 12 seconds made the setting remain 1, but no usable TLS debugging listener was established. The setting value alone is **not success**. No local authenticated connection, helper startup or new Camera capture was demonstrated by this experiment.
- Restored adb_wifi_enabled=0 and original station Wi-Fi enabled state. Stopped the hotspot process and revoked diagnostic WRITE_SECURE_SETTINGS and NEARBY_WIFI_DEVICES. The earlier diagnostic RECORD_AUDIO permission was already revoked. No reboot or OS installation was performed.

### Source difference and next physical test

In Android 15's AdbService, enabling the TLS-server property is deferred to AdbDebuggingManager on secure builds. That manager first requires current Wi-Fi AP information. Android 16's AdbService instead enables the TLS-server property before the manager's Wi-Fi rejection; this supplies a concrete explanation for the reported newer-version race. Android 17's AdbService also enables it earlier, but its debugging lifecycle and network monitoring have changed substantially. The same trick must not be assumed to work unchanged.

The connected Pixel's official System Update screen offers **Android 17**. Download/install has not been pressed. User approval was requested before changing the OS. If approved, test authenticated local startup first, then repeatability and reboot recovery, and finally actual stock-Camera stereo recording. Do not claim a fix based solely on the toggle staying on or a listening port.

Primary source comparisons:

- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/core/java/com/android/server/adb/AdbService.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/adb/AdbService.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/adb/AdbService.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/adb/AdbDebuggingManager.java

## Follow-up: audio-effects hypothesis

Required target remains **unmodified stock Camera apps**, with Android 15, no extra hardware, no root and no OS installation/update. The owner declined the offered Android update; do not proceed with it.

### Source and device inspection

The Pixel's `/vendor/etc/audio_effects.xml` lists the platform/vendor implementations: bundle EQ/volume/bass, reverb, visualizer, downmix, dynamics processing, loudness enhancement, offloaded echo/noise processing, haptics and spatializer. Its preprocessing defaults list echo cancellation and noise suppression for voice_communication. No custom Stereo Bridge effect engine is installed.

AOSP documents that device effects must have a vendor-library implementation listed in audio_effects.xml. The ordinary AudioEffect API selects and controls an existing effect implementation by UUID; it does not accept an application PCM-processing callback or a new effect-library path. Loading a DSP library inside our own application does not install that library into the Camera's audio processing chain.

### Physical API test

Temporary diagnostic APK, UID 10341, target SDK 35. No capture, playback, stock-Camera session modification, privileged permission grant or vendor-file write was required.

- Created an ordinary output AudioTrack without playing it: initialized, state 1.
- Positive control: standard Equalizer on that own track's audio session worked, hasControl=true, five bands.
- Queried custom fixture UUID d97f4d21-84b0-4c98-8db2-57ea6ef77324: not registered.
- Tried constructing AudioEffect with that custom implementation UUID: Java error -3; audio-server log `createEffect() error -2 from getEffectDescriptor`.
- Tried the hidden SourceDefaultEffect constructor for CAMCORDER: filtered/unavailable to this target, NoSuchMethodException. This test did **not** reach the native default-effect permission check.
- Application check for MODIFY_DEFAULT_AUDIO_EFFECTS returned -1 (denied). AOSP's SourceDefaultEffect and native addSourceDefaultEffect implementation also require that protected permission.

This verifies that ordinary EQ control works on the device, but it does not demonstrate custom code or PCM injection into Camera. The unregistered-UUID failure is expected; it is not a proof that no possible preinstalled vendor effect could ever implement an injection feature. No such feature has been identified in the inspected Pixel effect implementations or configuration.

The diagnostic was stopped afterward. Built-in effects and the unused output track were released. No app release or system change was made.

Primary references:

- https://source.android.com/docs/core/audio/audio-effects
- https://source.android.com/docs/core/audio/implement-pre-processing
- https://developer.android.com/reference/android/media/audiofx/AudioEffect
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/media/java/android/media/audiofx/SourceDefaultEffect.java
- https://android.googlesource.com/platform/frameworks/av/+/refs/heads/android15-release/services/audiopolicy/service/AudioPolicyInterfaceImpl.cpp

## Further source survey: plug-in hosting and app-local hooks

- **AAP (Audio Plugins For Android)**: real inter-process audio plug-ins, but a host app must implement the AAP hosting API. It is not a system microphone registration mechanism for arbitrary stock Camera apps. https://github.com/atsushieno/aap-core
- **aosp-virtual-mic**: a concrete stereo PCM shared-memory → microphone design, but its README requires building/booting an AOSP image with a custom Audio HAL and SELinux policy. Excluded by the owner's no-OS-change constraint. https://github.com/jsuppe/aosp-virtual-mic
- **LSPatch / app-level audio hooks**: rootless code insertion into the target APK is a different potential route. It modifies the app, not the system microphone provider. It is not equivalent to an unchanged stock Camera installation. https://github.com/LSPosed/LSPatch
- **VCAM source audit** at aa23a684767cfffe01b4bc964fd855024f781275: its Java microphone hooks replace AudioRecord.read buffers. The inspected MediaRecorder.setAudioSource hook only logs the source; it does not inject audio. NativeAudioHook.java declares an optional native library; the inspected repository did not contain its implementation. Do not treat its README's broad compatibility claims as verified. Source was read only; no third-party APK was installed. https://github.com/Yaahua/vcam

### Read-only Pixel Camera compatibility inspection

Pulled only the installed base APK to private temporary storage. Pixel Camera version 9.8.102.748116395.16 (code 68281476), original package com.google.android.GoogleCamera. No installed APK was modified or replaced; no Google binary or decompiled source is being published.

A narrow DEX-reference scan followed by single-class inspection verified AudioRecord.read(ByteBuffer, size), byte-array read fallback, and AudioRecord.getTimestamp handling in an audio wrapper inside the actual installed app. A second audio worker also has a Java AudioRecord.read path. These are plausible app-local interception points. The APK also contains MediaRecorder paths; **the presence of a hookable path does not prove which recording modes use it or that a patched camera will work**.

Potential experiment: a separately installed, locally modified Camera copy obtains stereo PCM and timestamps from Stereo Bridge while retaining the camera's video pipeline. This needs compatibility, signature/package, split-module, native-library and synchronization work. It is unverified, does not make an audio input available to every other unmodified app, and has not established a generally distributable Play release architecture.

The original Google-signed app cannot be updated with an APK signed by us. A separate copy would require package adaptation, and may lose features or privileges; success and video-quality equivalence must not be assumed. Asked the owner whether such a modified copy is acceptable before attempting modification/installation. Until answered, this remains a read-only feasibility investigation.


### Approved companion prototype: startup gate

Owner approved a separate locally patched Camera copy. Created package `app.stereobridge.camera.test`, label `Stereo Camera · Test`, with remapped authorities and independent signing. Original Camera remains installed unchanged. Factory Camera 8.3 was selected as a self-contained initial baseline; installed 9.8 uses split modules. Both contain PairIP code, so the older version is not an integrity-free baseline.

Baseline built and installed, but reproducibly crashes before the UI. Native logs show `libpairipcore.so` loading immediately before SIGSEGV; backtrace reaches libc memcpy and code in the APK. This establishes the failing startup boundary, not the exact internal cause. Changing only the application class from its wrapper to CameraApp still produces the same crash. CameraApp's superclass has a static initializer invoking the PairIP VM; the wrapper was therefore not the sole initialization path. Do not claim that removing a signature check fixes startup. No video or live microphone capture has been proven in this clone.

Independent pure-Java stereo fixture checks pass for left/right frequency separation, phase continuity, write bounds, and whole-frame validation. Fixture injection into Camera has not yet been implemented. Updated companion spec with one-time setup, automatic reconnection, a single primary Record control, missing-mic handling and explicit save confirmation. These remain release requirements rather than verified prototype features.


### Startup isolation follow-ups and original Camera handoff

Skipping the superclass VM initialization avoids the native crash but exposes a null string dependency during CameraContentProvider startup. The VM initializes string fields used by ordinary Camera code; it is not just a detachable startup signature check. Pointing its Java bytecode lookup at the original read-only factory APK still yields the same native startup crash. Stopped this sequence after three failed startup approaches; no viable clone is claimed.

A separate, original-code handoff probe successfully invokes the unchanged Google Camera through ACTION_VIDEO_CAPTURE with a temporary grant to an app-private output file. Pending MediaStore output failed because MediaProvider restricts pending items to their owner even with the attempted grant. A narrow private ContentProvider fixed that without broad media access. Camera's Record, Stop, and Done return a valid video to the helper.

On-device diagnostic audio finishing replaces Camera audio with separate stereo tones while copying its encoded video. ffprobe confirms HEVC 3840x2160 plus AAC 48000 Hz stereo. Encoded video hashes match exactly before/after. Decoded tone amplitude is ~7981 left at440Hz and ~7985 right at997Hz; opposite-channel tone amplitude ~0.02. This is fixture evidence only. Auxiliary Camera metadata tracks are omitted. AAC priming and a longer audio tail are visible, so synchronization is not proven. Real-mic direct capture has connected both sources; concurrent speech validation remains pending. No Google binaries were published.


### Real take: automatic on-device finishing

Owner powered both mics and spoke after an explicit recording prompt. Both direct sources continued while unchanged Pixel Camera recorded 54.053 seconds of 4K HEVC. Final capture metadata reported no transport error. The helper then processed the retained take entirely on the Pixel: decoded Camera reference audio, independently aligned each DJI source with multiple agreeing windows, encoded stereo AAC, copied the HEVC video packets, and published a result. Alignment selected raw left offset15.010s and right15.450s, with Camera audio starting5ms into video; trims subtract that5ms. Whole-envelope scores0.6699 /0.7197 pass only together with alternative-peak and window-consistency checks. These scores alone are not subjective lip-sync validation.

Decoded output channels correlate0.9944 /0.9938 with selected source PCM. Compressed video hash matches the original exactly. Audio54.052896s vs video54.052856s differs40 microseconds in this take. Auxiliary Camera metadata tracks are omitted.

The AOSP c2.android.aac.encoder was observed to add2048 priming samples. A diagnostic two-tone phase measurement identified the delay, and removal of the two priming packets produced matching steady-state phase. EOS duration explicitly sets the last audio-sample duration using the documented MediaMuxer mechanism. This is restricted to the tested codec, not claimed across OEM encoders. First-packet decoder startup transient remains. Official API reference: https://developer.android.com/reference/android/media/MediaMuxer

Bulk PCM reads reduced total real-take processing from roughly46 seconds to roughly9 seconds (decode2.829s, envelopes3.205s cumulative, alignment3.666s cumulative, publication8.636s cumulative). Five unit tests pass. The failed camera clone was uninstalled; original Camera and published Stereo Bridge remain unchanged. Owner playback feedback and independent code review are pending. See experiments/camera-handoff/README.md for remaining release blockers.


### Reliability checkpoint

Moved connection/render state out of Activity into HandoffSession, persisted private take paths/clean-capture status, and removed all exported diagnostic processing extras. Recovery seeding is confined to a separately signed androidTest APK, removed from the phone after use. A cold process start recovered a clean unfinished take; finishing completed while the Activity rotated portrait/landscape; the recreated screen displayed Saved/Play; exactly the copied test input files were removed and the journal retained only the published URI. Another cold process start restored that URI. Rotation settings were restored to their initial values (automatic rotation enabled, saved rotation1). The test used private copies because this Android build rejected app-created hard links.

Connection failures/cancellation clean up pre-roll, interrupted sessions offer explicit retention controls, READY has a15-second deadline, and microphone capture has a three-minute safety limit. Delayed connection work is scoped to a generation. Independent code review found no further serious issues in the revised controller; wider compatibility/background rendering/full reboot remain unverified. Android build, five unit tests, and lint pass with zero lint errors. The test app follows phone light/dark mode. No public release was changed.
