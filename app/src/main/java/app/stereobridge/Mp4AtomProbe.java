package app.stereobridge;

import java.io.IOException;

/** Bounded top-level MP4 header inspection. Never reads media payload or treats IO as corruption. */
final class Mp4AtomProbe {
  enum Result { HAS_METADATA, MISSING_METADATA, TRUNCATED, UNKNOWN }
  interface Reader { int read(long offset, byte[] buffer, int count) throws IOException; }
  static Result inspect(long length, Reader reader) {
    if (length < 0) return Result.UNKNOWN;
    if (length == 0) return Result.MISSING_METADATA;
    byte[] header = new byte[16];
    long offset = 0;
    boolean mp4 = false;
    try {
      for (int atoms = 0; offset < length && atoms < 4096; atoms++) {
        if (length - offset < 8) return mp4 ? Result.TRUNCATED : Result.UNKNOWN;
        if (reader.read(offset, header, 8) != 8) return Result.UNKNOWN;
        long size = u32(header, 0);
        boolean ftyp = type(header, "ftyp");
        boolean moov = type(header, "moov");
        int headerSize = 8;
        if (size == 1) {
          headerSize = 16;
          if (length - offset < 16) return mp4 ? Result.TRUNCATED : Result.UNKNOWN;
          if (reader.read(offset, header, 16) != 16) return Result.UNKNOWN;
          long high = u32(header, 8);
          if (high > 0x7fffffffL) return Result.UNKNOWN;
          size = (high << 32) | u32(header, 12);
        } else if (size == 0) size = length - offset;
        if (size < headerSize || size > length - offset)
          return mp4 ? Result.TRUNCATED : Result.UNKNOWN;
        if (ftyp) mp4 = true;
        if (moov) return Result.HAS_METADATA;
        offset += size;
      }
      return offset == length && mp4 ? Result.MISSING_METADATA : Result.UNKNOWN;
    } catch (IOException | RuntimeException e) { return Result.UNKNOWN; }
  }
  private static long u32(byte[] b, int offset) {
    return ((b[offset] & 255L) << 24) | ((b[offset + 1] & 255L) << 16)
        | ((b[offset + 2] & 255L) << 8) | (b[offset + 3] & 255L);
  }
  private static boolean type(byte[] b, String type) {
    for (int i = 0; i < 4; i++) if (b[4 + i] != (byte) type.charAt(i)) return false;
    return true;
  }
  private Mp4AtomProbe() { }
}
