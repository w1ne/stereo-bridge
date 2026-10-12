package app.stereobridge;

import java.io.*;

/** Finalizes a private stereo PCM16 recording when the user stops. */
final class WavFile implements AutoCloseable {
  private final RandomAccessFile file;
  private long bytes;

  WavFile(File path) throws IOException {
    file = new RandomAccessFile(path, "rw");
    file.setLength(0);
    file.write(new byte[44]);
  }

  void write(byte[] data) throws IOException {
    file.write(data);
    bytes += data.length;
  }

  public void close() throws IOException {
    file.seek(0);
    file.writeBytes("RIFF");
    le((int) bytes + 36, 4);
    file.writeBytes("WAVEfmt ");
    le(16, 4);
    le(1, 2);
    le(2, 2);
    le(48000, 4);
    le(192000, 4);
    le(4, 2);
    le(16, 2);
    file.writeBytes("data");
    le((int) bytes, 4);
    file.close();
  }

  private void le(int v, int n) throws IOException {
    for (int i = 0; i < n; i++) file.write(v >>> (8 * i));
  }
}
