package app.stereobridge;

import static org.junit.Assert.*;
import java.io.IOException;
import org.junit.Test;

public class PcmFrameWriterTest {
  @Test public void fullBufferRetainsFrameAndPartialOffset() throws Exception {
    long[] now = {0};
    int[] calls = {0};
    int[] expectedOffsets = {0, 400, 400, 400};
    int[] results = {400, 0, 0, 560};
    int discarded = PcmFrameWriter.write(new byte[960], (b, off, len) -> {
      assertEquals(expectedOffsets[calls[0]], off);
      return results[calls[0]++];
    }, () -> true, () -> now[0], () -> now[0] += 2);
    assertEquals(0, discarded);
    assertEquals(4, calls[0]);
  }

  @Test public void absentConsumerHasFiniteBudget() throws Exception {
    long[] now = {0};
    assertEquals(960, PcmFrameWriter.write(new byte[960], (b, o, n) -> 0,
        () -> true, () -> now[0], () -> now[0] += 2));
    assertEquals(200, now[0]);
  }

  @Test public void stopCancelsBufferWait() throws Exception {
    boolean[] running = {true};
    int[] calls = {0};
    assertEquals(960, PcmFrameWriter.write(new byte[960], (b, o, n) -> {
      calls[0]++; return 0;
    }, () -> running[0], () -> 0, () -> running[0] = false));
    assertEquals(1, calls[0]);
  }

  @Test(expected = IOException.class) public void negativeWriteFails() throws Exception {
    PcmFrameWriter.write(new byte[960], (b, o, n) -> -6,
        () -> true, () -> 0, () -> {});
  }
}
