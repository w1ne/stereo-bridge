package app.stereobridge;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

public class WavFileTest {
  @Test
  public void closeFinalizesStereoPcmHeaderAndPreservesSamples() throws Exception {
    File path = File.createTempFile("stereo", ".wav");
    try {
      byte[] samples = {1, 2, 3, 4, 5, 6, 7, 8};
      try (WavFile wav = new WavFile(path)) {
        wav.write(samples);
      }
      byte[] data = Files.readAllBytes(path.toPath());
      ByteBuffer header = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
      assertEquals(52, data.length);
      assertEquals("RIFF", new String(data, 0, 4, StandardCharsets.US_ASCII));
      assertEquals(44, header.getInt(4));
      assertEquals("WAVE", new String(data, 8, 4, StandardCharsets.US_ASCII));
      assertEquals(1, header.getShort(20));
      assertEquals(2, header.getShort(22));
      assertEquals(48000, header.getInt(24));
      assertEquals(192000, header.getInt(28));
      assertEquals(4, header.getShort(32));
      assertEquals(16, header.getShort(34));
      assertEquals(8, header.getInt(40));
      assertArrayEquals(samples, java.util.Arrays.copyOfRange(data, 44, data.length));
    } finally {
      path.delete();
    }
  }

  @Test
  public void newTakeTruncatesPreviousTake() throws Exception {
    File path = File.createTempFile("stereo", ".wav");
    try {
      Files.write(path.toPath(), new byte[4096]);
      try (WavFile wav = new WavFile(path)) {
        wav.write(new byte[4]);
      }
      assertEquals(48, path.length());
    } finally {
      path.delete();
    }
  }
}
