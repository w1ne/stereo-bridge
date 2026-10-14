package app.stereobridge;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class Mp4AtomProbeTest {
  private static byte[] atoms(String... types) {
    ByteBuffer b = ByteBuffer.allocate(types.length * 8);
    for (String type : types) { b.putInt(8); for (char c : type.toCharArray()) b.put((byte)c); }
    return b.array();
  }
  private static Mp4AtomProbe.Result probe(byte[] bytes) {
    return Mp4AtomProbe.inspect(bytes.length, (position, buffer, count) -> {
      int length = Math.min(count, bytes.length - (int) position);
      System.arraycopy(bytes, (int) position, buffer, 0, length); return length;
    });
  }
  @Test public void emptyCrashFileIsInvalid() { assertEquals(Mp4AtomProbe.Result.MISSING_METADATA, probe(new byte[0])); }
  @Test public void completeAtomsWithoutMoovAreInvalid() {
    assertEquals(Mp4AtomProbe.Result.MISSING_METADATA, probe(atoms("ftyp", "free", "mdat")));
  }
  @Test public void validMoovIsPreserved() {
    assertEquals(Mp4AtomProbe.Result.HAS_METADATA, probe(atoms("ftyp", "moov", "mdat")));
  }
  @Test public void truncatedKnownMp4IsInvalid() {
    byte[] bytes = atoms("ftyp", "mdat"); ByteBuffer.wrap(bytes).putInt(8, 1024);
    assertEquals(Mp4AtomProbe.Result.TRUNCATED, probe(bytes));
  }
  @Test public void shortTrailingHeaderIsInvalidForKnownMp4() {
    byte[] bytes = java.util.Arrays.copyOf(atoms("ftyp"), 12);
    assertEquals(Mp4AtomProbe.Result.TRUNCATED, probe(bytes));
  }
  @Test public void unknownFormatIsPreserved() {
    assertEquals(Mp4AtomProbe.Result.UNKNOWN, probe(atoms("nope")));
  }
  @Test public void readFailuresArePreserved() {
    assertEquals(Mp4AtomProbe.Result.UNKNOWN, Mp4AtomProbe.inspect(100,
        (position, buffer, count) -> { throw new IOException("FD unavailable"); }));
  }
  @Test public void shortReadsAreAmbiguous() {
    assertEquals(Mp4AtomProbe.Result.UNKNOWN, Mp4AtomProbe.inspect(100,
        (position, buffer, count) -> 0));
  }
  @Test public void zeroSizedMdatConsumesRestAndConfirmsMissingMoov() {
    byte[] bytes = atoms("ftyp", "mdat"); ByteBuffer.wrap(bytes).putInt(8, 0);
    assertEquals(Mp4AtomProbe.Result.MISSING_METADATA, probe(bytes));
  }
  @Test public void boundedProbeDoesNotScanUnboundedHeaders() {
    String[] types = new String[5000]; java.util.Arrays.fill(types, "free"); types[0] = "ftyp";
    assertEquals(Mp4AtomProbe.Result.UNKNOWN, probe(atoms(types)));
  }
  @Test public void unknownSizeIsPreserved() {
    assertEquals(Mp4AtomProbe.Result.UNKNOWN, Mp4AtomProbe.inspect(-1,
        (position, buffer, count) -> { throw new AssertionError(); }));
  }
}
