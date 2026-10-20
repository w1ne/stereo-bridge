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
 @Test public void compatSizeAcceptsUpTo1080pEitherOrientation() {
  assertTrue(ModeMatrixPolicy.compatSize(1920, 1080));
  assertTrue(ModeMatrixPolicy.compatSize(1080, 1920));
  assertTrue(ModeMatrixPolicy.compatSize(1280, 720));
  assertFalse(ModeMatrixPolicy.compatSize(3840, 2160));
  assertFalse(ModeMatrixPolicy.compatSize(2048, 1080));
  assertFalse(ModeMatrixPolicy.compatSize(0, 0));
 }
 @Test public void compatRatePrefers30Then24() {
  assertEquals(30, ModeMatrixPolicy.compatRate(new int[][] {{15, 30}}));
  assertEquals(30, ModeMatrixPolicy.compatRate(new int[][] {{24, 30}}));
  assertEquals(24, ModeMatrixPolicy.compatRate(new int[][] {{24, 24}, {60, 60}}));
  assertEquals(0, ModeMatrixPolicy.compatRate(new int[][] {{60, 60}}));
  assertEquals(0, ModeMatrixPolicy.compatRate(null));
 }
 @Test public void compatAeRangeFindsContainingRange() {
  assertArrayEquals(new int[] {15, 30}, ModeMatrixPolicy.compatAeRange(new int[][] {{15, 30}}, 30));
  assertArrayEquals(new int[] {24, 30}, ModeMatrixPolicy.compatAeRange(new int[][] {{24, 30}, {15, 30}}, 30));
  assertNull(ModeMatrixPolicy.compatAeRange(new int[][] {{60, 60}}, 24));
  assertNull(ModeMatrixPolicy.compatAeRange(null, 30));
 }
}
