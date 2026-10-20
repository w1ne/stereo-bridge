package app.stereobridge;
import static org.junit.Assert.*;
import org.junit.Test;
public class CodecPolicyTest {
 @Test public void avcRequestNeverTriesHevc() {
  assertArrayEquals(new String[] {"video/avc"}, CodecPolicy.order("avc"));
  assertArrayEquals(new String[] {"video/avc"}, CodecPolicy.order(""));
 }
 @Test public void hevcRequestFallsBackToAvc() {
  assertArrayEquals(new String[] {"video/hevc", "video/avc"}, CodecPolicy.order("hevc"));
  assertArrayEquals(new String[] {"video/hevc", "video/avc"}, CodecPolicy.order(CodecPolicy.HEVC));
 }
}
