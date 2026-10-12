package app.stereobridge.mic;

/** Apache-licensed Google LC3 decoder; no DJI binaries or service. */
public final class NativeLc3 implements AutoCloseable {
  static {
    System.loadLibrary("stereobridge_codec");
  }

  private long handle;

  public NativeLc3() {
    handle = create();
    if (handle == 0) throw new IllegalStateException("LC3 decoder initialization failed");
  }

  public synchronized short[] decode(byte[] frame) {
    if (handle == 0) throw new IllegalStateException("Decoder closed");
    if (frame.length != 111) throw new IllegalArgumentException("Expected111 encoded bytes");
    return decodeFrame(handle, frame);
  }

  public synchronized short[] conceal() {
    if (handle == 0) throw new IllegalStateException("Decoder closed");
    return concealFrame(handle);
  }

  public synchronized void close() {
    if (handle != 0) {
      destroy(handle);
      handle = 0;
    }
  }

  private static native long create();

  private static native short[] decodeFrame(long decoder, byte[] encoded);

  private static native short[] concealFrame(long decoder);

  private static native void destroy(long decoder);
}
