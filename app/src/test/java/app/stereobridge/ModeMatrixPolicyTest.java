package app.stereobridge;
import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;
public class ModeMatrixPolicyTest {
 @Test public void rememberedFeasibleModeComesFirst() {
  assertEquals(Arrays.asList(VideoMode.UHD30, VideoMode.FHD30),
      ModeMatrixPolicy.order(Arrays.asList(VideoMode.FHD30, VideoMode.UHD30), VideoMode.UHD30.key()));
 }
 @Test public void rememberedModeNotAlreadyFirstIsPromoted() {
  assertEquals(Arrays.asList(VideoMode.FHD30, VideoMode.UHD30),
      ModeMatrixPolicy.order(Arrays.asList(VideoMode.UHD30, VideoMode.FHD30), VideoMode.FHD30.key()));
 }
 @Test public void nullRememberedKeepsInputOrder() {
  assertEquals(Arrays.asList(VideoMode.UHD30, VideoMode.FHD30),
      ModeMatrixPolicy.order(Arrays.asList(VideoMode.UHD30, VideoMode.FHD30), null));
 }
 @Test public void rememberedInfeasibleModeIsIgnored() {
  assertEquals(Arrays.asList(VideoMode.FHD30, VideoMode.UHD60),
      ModeMatrixPolicy.order(Arrays.asList(VideoMode.FHD30, VideoMode.UHD60), VideoMode.UHD30.key()));
  assertEquals(Arrays.asList(VideoMode.FHD30, VideoMode.UHD60),
      ModeMatrixPolicy.order(Arrays.asList(VideoMode.FHD30, VideoMode.UHD60), ""));
 }
 @Test public void fpsRangeCoversAnyAdvertisedRange() {
  int[][] ranges = {{15, 30}, {60, 60}};
  assertTrue(ModeMatrixPolicy.fpsRangeCovers(ranges, 30));
  assertTrue(ModeMatrixPolicy.fpsRangeCovers(ranges, 60));
  assertFalse(ModeMatrixPolicy.fpsRangeCovers(ranges, 24));
  assertFalse(ModeMatrixPolicy.fpsRangeCovers(null, 30));
  assertFalse(ModeMatrixPolicy.fpsRangeCovers(new int[][] {{30}}, 30));
 }
 @Test public void aeRangePrefersExactRateThenMostConstrained() {
  int[][] ranges = {{15, 30}, {30, 60}, {60, 60}, {24, 30}};
  assertArrayEquals(new int[] {60, 60}, ModeMatrixPolicy.aeRange(ranges, 60));
  assertArrayEquals(new int[] {24, 30}, ModeMatrixPolicy.aeRange(ranges, 30));
  assertNull(ModeMatrixPolicy.aeRange(ranges, 24));
  assertNull(ModeMatrixPolicy.aeRange(null, 30));
 }
 @Test public void aeRangeSkipsMalformedEntries() {
  int[][] ranges = {null, {30}, {30, 30}};
  assertArrayEquals(new int[] {30, 30}, ModeMatrixPolicy.aeRange(ranges, 30));
  assertTrue(ModeMatrixPolicy.fpsRangeCovers(ranges, 30));
  assertNull(ModeMatrixPolicy.aeRange(new int[][] {{30}}, 30));
  assertNull(ModeMatrixPolicy.aeRange(new int[][] {{60, 30}}, 30));
  assertNull(ModeMatrixPolicy.aeRange(null, 30));
  assertFalse(ModeMatrixPolicy.fpsRangeCovers(new int[][] {{30}}, 30));
  assertFalse(ModeMatrixPolicy.fpsRangeCovers(null, 30));
 }
}
