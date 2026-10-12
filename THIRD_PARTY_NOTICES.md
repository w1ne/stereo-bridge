# Third-party components

- Google liblc3 — Apache-2.0. Vendored source and full license in `third_party/liblc3`. Revision recorded in `UPSTREAM.md`.
- LibADB Android — Apache-2.0 OR GPL-3.0-or-later; this project selects Apache-2.0. https://github.com/MuntashirAkon/libadb-android
- Conscrypt — Apache-2.0. https://github.com/google/conscrypt
- Bouncy Castle — Bouncy Castle license. https://www.bouncycastle.org/about/license/
- LibADB's SPAKE2 Android dependency — LGPL-3.0. https://github.com/MuntashirAkon/spake2-java
- AndroidX annotations — Apache-2.0. https://android.googlesource.com/platform/frameworks/support/

No DJI Mimo application, SDK, or proprietary decoder is included. The microphone framing was independently implemented from observations of owned hardware. DJI is not affiliated with this project.

Full dependency license texts are bundled in the APK assets/licenses directory. SPAKE2 source is provided in third_party/spake2, including its nested native source. You may modify that library, build its Android AAR and set STEREO_BRIDGE_SPAKE2_AAR to its absolute path when rebuilding this app to relink it. The application imposes no extra restriction on reverse engineering for debugging library modifications. The technical use of a codec does not establish patent licensing rights.
