# Independent original DJI Mic Mini driver

Research date: 12 October 2026. The implementation was derived from observations of owned transmitters and public APIs. No DJI implementation, SDK or binary is included.

## Verified findings

Two original Mic Mini transmitters can independently stream encoded audio through the standard Bluetooth Serial Port Profile UUID `00001101-0000-1000-8000-00805f9b34fb`. Both provided receive-only audio after Bluetooth reconnection without outgoing application commands. A user-controlled recording with separate speech into each microphone was independently decoded and the owner confirmed recognizable speech. Earlier quiet takes were inconclusive because no speech occurred during their recording windows.

Google’s Apache-2.0 [liblc3](https://github.com/google/liblc3) decoded each 111-byte payload at non-HR 48 kHz, 5 ms, producing 240 PCM16 samples. The bundled version is pinned in the third-party notices. Separate source instances own separate decoder state. Missing modulo-256 sequence values insert packet loss concealment for short gaps; large discontinuities reset the decoder.

## Observed framing

A group consists of a three-byte `AA 14 00` marker, five 125-byte records and one trailer byte, totaling 629 bytes. Relative record offsets used by the parser:

| Offset | Size | Meaning |
|---|---:|---|
| 0 | 4 | Little-endian Bluetooth controller clock |
| 6 | 2 | `55 77` fixed bytes |
| 8 | 1 | `05` fixed byte |
| 10 | 1 | `10` audio command |
| 11 | 1 | Battery/event metadata; not used as battery percentage |
| 12 | 1 | Sequence modulo 256 |
| 13 | 111 | Encoded audio payload |

The parser accepts arbitrary input chunks and resynchronizes at known fixed bytes. Unknown checksum and control layouts are not validated or decoded. Controller clock jumps were observed without corresponding sequence gaps, so the clock is not used to synthesize missing audio. Current stereo mixing pairs frames from the two receive queues; precise clock synchronization and drift correction are not validated.

## Limits

Validated hardware is an original Mic Mini pair on Pixel 8 Pro / Android 17. Other Mic generations and other manufacturers are unsupported. Receive-only streaming across full transmitter power cycles still needs broader testing. Bluetooth scheduling can lose frames; diagnostics report app input-buffer overflow, not every possible RF or camera-side loss.

Raw owner audio, HCI logs and proprietary reference applications are excluded from the repository. Public evidence is summarized in [release verification](release-verification.md).
