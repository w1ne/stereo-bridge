package app.stereobridge.handoff;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class SyncEstimatorTest {
  @Test public void findsKnownPrerollDespiteGainAndNoise() {
    Random random = new Random(9);
    float[] microphone = new float[1600];
    for (int i = 0; i < microphone.length; i++) microphone[i] = random.nextFloat();
    float[] camera = new float[700];
    for (int i = 0; i < camera.length; i++) camera[i] = microphone[137 + i] * 4 + 2 + random.nextFloat() * .02f;
    SyncEstimator.Result result = SyncEstimator.align(camera, microphone);
    assertTrue(result.confident);
    assertEquals(137, result.offsetBins);
  }
  @Test public void refusesSilenceAndUnrelatedRecordings() {
    assertFalse(SyncEstimator.align(new float[700], new float[1600]).confident);
    Random random = new Random(21);
    float[] camera = new float[700], microphone = new float[1600];
    for (int i = 0; i < camera.length; i++) camera[i] = random.nextFloat();
    for (int i = 0; i < microphone.length; i++) microphone[i] = random.nextFloat();
    assertFalse(SyncEstimator.align(camera, microphone).confident);
  }
  @Test public void refusesAmbiguousRepeatedSoundAndShortTakes() {
    float[] camera = new float[700], microphone = new float[1600];
    for (int i = 0; i < camera.length; i++) camera[i] = i % 20;
    for (int i = 0; i < microphone.length; i++) microphone[i] = i % 20;
    assertFalse(SyncEstimator.align(camera, microphone).confident);
    assertFalse(SyncEstimator.align(new float[20], microphone).confident);
  }
  @Test public void findsSpeechEnvelopeWithSlowAutomaticGainChanges() {
    Random random = new Random(2);
    float[] microphone = new float[2400], camera = new float[1600];
    for (int i = 0; i < microphone.length; i++) microphone[i] = (float)Math.exp(random.nextDouble()*3);
    for (int i = 0; i < camera.length; i++) camera[i] = microphone[231+i]*(float)Math.exp(6.0*i/camera.length-3);
    SyncEstimator.Result result = SyncEstimator.align(camera, microphone);
    assertTrue(result.confident);
    assertEquals(231, result.offsetBins);
  }
  @Test public void findsOffsetsBetweenCoarseSearchSteps() {
    Random random = new Random(28);
    float[] microphone = new float[2400];
    for (int i=0;i<microphone.length;i++) microphone[i]=random.nextFloat()*100;
    for(int offset=137;offset<147;offset++) {
      float[] camera=Arrays.copyOfRange(microphone,offset,offset+1600);
      SyncEstimator.Result result=SyncEstimator.align(camera,microphone);
      assertTrue(result.confident);
      assertEquals(offset,result.offsetBins);
    }
  }
}
