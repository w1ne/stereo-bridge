package app.stereobridge;

import static org.junit.Assert.*;

import java.lang.reflect.*;
import org.junit.Test;

public class AudioProcessorTest {
  private byte[] process(byte[] pcm, float l, float r, boolean swap) throws Exception {
    Class<?> type;
    try {
      type = Class.forName("app.stereobridge.AudioProcessor");
    } catch (ClassNotFoundException e) {
      fail("Independent audio processor is missing");
      return null;
    }
    return (byte[])
        type.getMethod("process", byte[].class, float.class, float.class, boolean.class)
            .invoke(null, pcm, l, r, swap);
  }

  private int sample(byte[] data, int k) {
    return (short) ((data[k] & 255) | (data[k + 1] << 8));
  }

  @Test
  public void convertsSignedPcm24To16() throws Exception {
    byte[] p = {0, 0, 64, 0, 0, (byte) 192};
    byte[] out = process(p, 1, 1, false);
    assertEquals(16384, sample(out, 0));
    assertEquals(-16384, sample(out, 2));
  }

  @Test
  public void gainsClipRatherThanWrap() throws Exception {
    byte[] p = {0, 0, 64, 0, 0, (byte) 192};
    byte[] out = process(p, 4, 4, false);
    assertEquals(32767, sample(out, 0));
    assertEquals(-32768, sample(out, 2));
  }

  @Test
  public void muteOnlySelectedChannel() throws Exception {
    byte[] p = {0, 0, 64, 0, 0, (byte) 192};
    byte[] out = process(p, 0, 1, false);
    assertEquals(0, sample(out, 0));
    assertEquals(-16384, sample(out, 2));
  }

  @Test
  public void swapsPhysicalChannelAssignment() throws Exception {
    byte[] p = {0, 0, 64, 0, 0, (byte) 192};
    byte[] out = process(p, 1, 1, true);
    assertEquals(-16384, sample(out, 0));
    assertEquals(16384, sample(out, 2));
  }

  @Test
  public void rejectsPartialStereoFrame() throws Exception {
    try {
      process(new byte[5], 1, 1, false);
      fail("Accepted partial frame");
    } catch (InvocationTargetException e) {
      assertTrue(e.getCause() instanceof IllegalArgumentException);
    }
  }
}
