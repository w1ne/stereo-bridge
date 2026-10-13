package app.stereobridge;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/** Bounded contiguous PCM queue, coalescing small source packets into AAC input buffers. */
final class PcmBatchQueue {
  static final int AAC_BYTES = 1024 * 4;
  static final class Batch {
    final int bytes;
    final long ptsUs, endUs;
    Batch(int bytes, long ptsUs, long endUs) {
      this.bytes = bytes; this.ptsUs = ptsUs; this.endUs = endUs;
    }
  }
  private final ArrayDeque<byte[]> packets = new ArrayDeque<>();
  private final int maxBytes, maxPackets;
  private int bytes, offset;
  private long originUs, offeredBytes, consumedBytes;
  private boolean started;

  PcmBatchQueue(int maxBytes, int maxPackets) {
    this.maxBytes = maxBytes; this.maxPackets = maxPackets;
  }

  synchronized void offer(byte[] pcm, long ptsUs) {
    if (pcm == null || pcm.length == 0 || pcm.length % 4 != 0 || ptsUs < 0)
      throw new IllegalArgumentException("Expected stereo PCM16");
    if (started && Math.abs(ptsUs - timestamp(offeredBytes)) > 1)
      throw new IllegalArgumentException("Discontinuous PCM timestamps");
    if (packets.size() >= maxPackets || pcm.length > maxBytes - bytes)
      throw new IllegalStateException("Audio encoder queue full");
    if (!started) { originUs = ptsUs; started = true; }
    packets.add(pcm.clone()); bytes += pcm.length; offeredBytes += pcm.length;
  }

  synchronized int bytes() { return bytes; }
  synchronized boolean ready(boolean stopping) { return bytes >= AAC_BYTES || stopping; }

  synchronized Batch take(ByteBuffer output, boolean stopping) {
    if (bytes == 0 || !ready(stopping)) return null;
    int n = Math.min(output.remaining() / 4 * 4, bytes);
    if (n == 0) throw new IllegalArgumentException("AAC input buffer too small");
    long pts = timestamp(consumedBytes);
    int remaining = n;
    while (remaining > 0) {
      byte[] packet = packets.peek();
      int copy = Math.min(remaining, packet.length - offset);
      output.put(packet, offset, copy);
      offset += copy; remaining -= copy;
      if (offset == packet.length) { packets.remove(); offset = 0; }
    }
    bytes -= n; consumedBytes += n;
    return new Batch(n, pts, timestamp(consumedBytes));
  }

  private long timestamp(long byteOffset) {
    long samples = byteOffset / 4;
    return originUs + samples / 48000 * 1_000_000L + samples % 48000 * 1_000_000L / 48000;
  }
  synchronized void clear() { packets.clear(); bytes = 0; offset = 0; }
}
