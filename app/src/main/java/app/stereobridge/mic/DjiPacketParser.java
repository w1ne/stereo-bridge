package app.stereobridge.mic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Independent parser for the observed original Mic Mini V1 audio layout. Unknown checksums and
 * command groups are not decoded. Single reader only.
 */
public final class DjiPacketParser {
  private static final int GROUP_BYTES = 629;
  private byte[] buffer = new byte[2048];
  private int size;
  private long discardedBytes;

  public static final class Frame {
    public final int sequence;
    public final byte[] payload;
    public final byte[] clockPrefix;
    public final int batteryEvent;

    /** Controller clock in 312.5-microsecond ticks, modulo 2^32. */
    public final long bluetoothClockTicks;

    private Frame(int sequence, byte[] payload, byte[] clockPrefix, int batteryEvent) {
      this.sequence = sequence;
      this.payload = payload;
      this.clockPrefix = clockPrefix;
      this.bluetoothClockTicks =
          (clockPrefix[0] & 255L)
              | ((clockPrefix[1] & 255L) << 8)
              | ((clockPrefix[2] & 255L) << 16)
              | ((clockPrefix[3] & 255L) << 24);
      this.batteryEvent = batteryEvent;
    }
  }

  public List<Frame> feed(byte[] data, int offset, int length) {
    if (offset < 0 || length < 0 || offset > data.length - length)
      throw new IndexOutOfBoundsException();
    if (length > Integer.MAX_VALUE - size) throw new IllegalArgumentException("Input too large");
    int needed = size + length;
    if (needed > buffer.length) buffer = Arrays.copyOf(buffer, needed);
    System.arraycopy(data, offset, buffer, size, length);
    size = needed;
    List<Frame> frames = new ArrayList<>();
    while (true) {
      int marker = -1;
      for (int i = 0; i + 2 < size; i++) {
        if ((buffer[i] & 255) == 0xaa && buffer[i + 1] == 0x14 && buffer[i + 2] == 0) {
          marker = i;
          break;
        }
      }
      if (marker < 0) {
        discard(Math.max(0, size - 2), true);
        break;
      }
      if (marker > 0) discard(marker, true);
      if (size < GROUP_BYTES) break;
      boolean valid = true;
      for (int i = 0; i < 5; i++) {
        int p = 3 + i * 125;
        if (buffer[p + 6] != 0x55
            || buffer[p + 7] != 0x77
            || buffer[p + 8] != 5
            || buffer[p + 10] != 0x10) valid = false;
      }
      if (!valid) {
        discard(1, true);
        continue;
      }
      for (int i = 0; i < 5; i++) {
        int p = 3 + i * 125;
        frames.add(
            new Frame(
                buffer[p + 12] & 255,
                Arrays.copyOfRange(buffer, p + 13, p + 124),
                Arrays.copyOfRange(buffer, p, p + 4),
                buffer[p + 11] & 255));
      }
      discard(GROUP_BYTES, false);
    }
    return frames;
  }

  private void discard(int count, boolean lost) {
    if (lost) discardedBytes += count;
    System.arraycopy(buffer, count, buffer, 0, size - count);
    size -= count;
  }

  public long getDiscardedBytes() {
    return discardedBytes;
  }

  public int getBufferedBytes() {
    return size;
  }
}
