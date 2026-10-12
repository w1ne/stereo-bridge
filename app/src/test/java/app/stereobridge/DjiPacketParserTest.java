package app.stereobridge;

import static org.junit.Assert.*;

import app.stereobridge.mic.DjiPacketParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class DjiPacketParserTest {
  private byte[] group(int firstSequence) {
    byte[] b = new byte[629];
    b[0] = (byte) 0xaa;
    b[1] = 0x14;
    for (int i = 0; i < 5; i++) {
      int p = 3 + i * 125;
      long clock = 0x12345678L + 16L * i;
      for (int n = 0; n < 4; n++) b[p + n] = (byte) (clock >>> (8 * n));
      b[p + 6] = 0x55;
      b[p + 7] = 0x77;
      b[p + 8] = 5;
      b[p + 10] = 0x10;
      b[p + 12] = (byte) (firstSequence + i);
      for (int n = 0; n < 111; n++) b[p + 13 + n] = (byte) (n + i);
    }
    b[628] = (byte) 0xa5;
    return b;
  }

  @Test
  public void fragmentedTransportPreservesFivePayloadsAndClock() {
    byte[] data = group(254);
    DjiPacketParser p = new DjiPacketParser();
    List<DjiPacketParser.Frame> frames = new ArrayList<>();
    for (int n = 0; n < data.length; n += 17)
      frames.addAll(p.feed(data, n, Math.min(17, data.length - n)));
    assertEquals(5, frames.size());
    assertEquals(254, frames.get(0).sequence);
    assertEquals(0, frames.get(2).sequence);
    assertEquals(0x12345678L, frames.get(0).bluetoothClockTicks);
    assertEquals(111, frames.get(4).payload.length);
    assertEquals(114, frames.get(4).payload[110] & 255);
    assertEquals(0, p.getBufferedBytes());
    assertEquals(0, p.getDiscardedBytes());
  }

  @Test
  public void invalidRecordResynchronizesToNextValidGroup() {
    byte[] invalid = group(10), valid = group(20), data = new byte[1258];
    invalid[3 + 6] = 0;
    System.arraycopy(invalid, 0, data, 0, 629);
    System.arraycopy(valid, 0, data, 629, 629);
    DjiPacketParser p = new DjiPacketParser();
    List<DjiPacketParser.Frame> frames = p.feed(data, 0, data.length);
    assertEquals(5, frames.size());
    assertEquals(20, frames.get(0).sequence);
    assertEquals(629, p.getDiscardedBytes());
  }

  @Test
  public void partialMarkerSurvivesBetweenChunks() {
    DjiPacketParser p = new DjiPacketParser();
    byte[] data = group(3);
    assertTrue(p.feed(new byte[] {1, 2, (byte) 0xaa}, 0, 3).isEmpty());
    assertEquals(5, p.feed(data, 1, data.length - 1).size());
    assertEquals(2, p.getDiscardedBytes());
  }

  @Test
  public void unrelatedDataDoesNotGrowRetainedBuffer() {
    DjiPacketParser p = new DjiPacketParser();
    byte[] noise = new byte[4096];
    for (int i = 0; i < 20; i++) assertTrue(p.feed(noise, 0, noise.length).isEmpty());
    assertTrue(p.getBufferedBytes() <= 2);
  }
}
