package app.stereobridge;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Retry transient backpressure without blocking session cancellation indefinitely. */
final class PcmFrameWriter {
  interface Sink { int write(byte[] data, int offset, int length); }
  interface Pause { void await() throws InterruptedException; }

  static int write(byte[] frame, Sink sink, BooleanSupplier running,
      LongSupplier clockMillis, Pause pause) throws IOException, InterruptedException {
    long started = clockMillis.getAsLong();
    int offset = 0;
    while (running.getAsBoolean() && offset < frame.length) {
      int n = sink.write(frame, offset, frame.length - offset);
      if (n < 0 || n > frame.length - offset)
        throw new IOException("Audio injection write failed: " + n);
      offset += n;
      if (n == 0) {
        // A camera that is not consuming input must not retain stale audio forever.
        if (clockMillis.getAsLong() - started >= 200) break;
        pause.await();
      }
    }
    return frame.length - offset;
  }
}
