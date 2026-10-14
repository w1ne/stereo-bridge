# Third-party components — offline recorder

- Google liblc3 — Apache-2.0. Vendored source and full license in `third_party/liblc3`. Revision recorded in its `UPSTREAM.md`.

The offline recorder has no LibADB, SPAKE2, Conscrypt or Bouncy Castle runtime dependency. Historical source retained in this repository is not linked into the offline application and retains its own license.

No DJI Mimo application, SDK or proprietary decoder is included. Microphone framing was independently implemented from observations of owned hardware. DJI is not affiliated with this project.

The applicable Apache license and notice are bundled in APK assets/licenses. Technical use of a codec does not establish patent licensing rights.
