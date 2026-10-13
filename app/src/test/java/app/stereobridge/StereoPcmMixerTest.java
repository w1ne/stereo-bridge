package app.stereobridge;

import static org.junit.Assert.*;
import org.junit.Test;

public class StereoPcmMixerTest {
  @Test public void interleavesLittleEndianPhysicalInputs() {
    assertArrayEquals(new byte[] {0x34,0x12,-2,-1,0,-128,-1,127},
        StereoPcmMixer.mix(new short[] {0x1234,Short.MIN_VALUE},new short[] {-2,Short.MAX_VALUE},1,1,false));
  }
  @Test public void gainsApplyBeforeSwapAndSaturate() {
    assertArrayEquals(new byte[] {50,0,-1,127,-50,-1,0,-128},
        StereoPcmMixer.mix(new short[] {20000,-20000},new short[] {100,-100},2,.5f,true));
  }
  @Test public void muteAndFractionalGainTruncateTowardZero() {
    assertArrayEquals(new byte[] {0,0,1,0,0,0,-1,-1},
        StereoPcmMixer.mix(new short[] {123,-123},new short[] {3,-3},0,.5f,false));
  }
  @Test public void rejectsInvalidFramesAndGains() {
    assertThrows(IllegalArgumentException.class, () -> StereoPcmMixer.mix(null,new short[1],1,1,false));
    assertThrows(IllegalArgumentException.class, () -> StereoPcmMixer.mix(new short[0],new short[0],1,1,false));
    assertThrows(IllegalArgumentException.class, () -> StereoPcmMixer.mix(new short[1],new short[2],1,1,false));
    for (float gain : new float[] {-1,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> StereoPcmMixer.mix(new short[1],new short[1],gain,1,false));
      assertThrows(IllegalArgumentException.class, () -> StereoPcmMixer.mix(new short[1],new short[1],1,gain,false));
    }
  }
  @Test public void peakHandlesNegativeFullScaleAndClamps() {
    assertEquals(1f,StereoPcmMixer.peak(new short[] {Short.MIN_VALUE},1),0);
    assertEquals(.5f,StereoPcmMixer.peak(new short[] {16384},1),0);
    assertEquals(1f,StereoPcmMixer.peak(new short[] {20000},2),0);
    assertEquals(0f,StereoPcmMixer.peak(new short[] {20000},0),0);
    assertThrows(IllegalArgumentException.class, () -> StereoPcmMixer.peak(new short[0],1));
    assertThrows(IllegalArgumentException.class, () -> StereoPcmMixer.peak(new short[1],Float.NaN));
  }
}
