package app.stereobridge;
import static org.junit.Assert.*;
import org.junit.Test;
public class VideoModeTest {
 @Test public void exposesDisplayAndFallbackOrders() {
  assertEquals(4, VideoMode.ALL.size());
  assertEquals(VideoMode.UHD30, VideoMode.ALL.get(0));
  assertEquals(VideoMode.UHD60, VideoMode.ALL.get(1));
  assertEquals(VideoMode.FHD60, VideoMode.ALL.get(2));
  assertEquals(VideoMode.FHD30, VideoMode.ALL.get(3));
  assertEquals(VideoMode.UHD30, VideoMode.FALLBACK.get(0));
  assertEquals(VideoMode.FHD60, VideoMode.FALLBACK.get(1));
  assertEquals(VideoMode.FHD30, VideoMode.FALLBACK.get(2));
  assertEquals(VideoMode.UHD60, VideoMode.FALLBACK.get(3));
 }
 @Test public void roundTripsKeysAndLabels() {
  assertEquals("3840x2160@30", VideoMode.UHD30.key());
  assertEquals("4K30", VideoMode.UHD30.label());
  assertEquals(VideoMode.UHD60, VideoMode.fromKey("3840x2160@60"));
  assertNull(VideoMode.fromKey("9999x9999@24"));
  assertNull(VideoMode.fromKey(""));
 }
 @Test public void equalsIgnoresInstance() {
  assertEquals(VideoMode.fromKey(VideoMode.FHD30.key()), VideoMode.FHD30);
  assertNotEquals(VideoMode.FHD30, VideoMode.FHD60);
 }
}
