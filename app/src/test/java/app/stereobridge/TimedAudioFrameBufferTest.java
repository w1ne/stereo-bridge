package app.stereobridge;

import static org.junit.Assert.*;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class TimedAudioFrameBufferTest {
  @Test public void preservesTimestampAndOwnsOfferedSamples() throws Exception {
    TimedAudioFrameBuffer buffer = new TimedAudioFrameBuffer();
    short[] samples = {1,2};
    buffer.offer(samples,123456789L);
    samples[0] = 99;
    TimedAudioFrameBuffer.Frame frame = buffer.poll(0,TimeUnit.NANOSECONDS);
    assertEquals(123456789L,frame.elapsedNanos);
    assertArrayEquals(new short[] {1,2},frame.samples);
    assertNull(buffer.poll(0,TimeUnit.NANOSECONDS));
  }
  @Test public void startupOverflowKeepsNewestFortyWithoutLiveLoss() throws Exception {
    TimedAudioFrameBuffer buffer = new TimedAudioFrameBuffer();
    for (int i=0;i<41;i++) buffer.offer(new short[] {(short)i},i);
    assertEquals(0,buffer.droppedFrames());
    assertEquals(1,buffer.poll(0,TimeUnit.NANOSECONDS).elapsedNanos);
  }
  @Test public void beginLiveDiscardsStartupAndCountsOverflow() throws Exception {
    TimedAudioFrameBuffer buffer = new TimedAudioFrameBuffer();
    buffer.offer(new short[] {1},1);
    buffer.beginLive();
    assertNull(buffer.poll(0,TimeUnit.NANOSECONDS));
    for (int i=0;i<42;i++) buffer.offer(new short[] {(short)i},i);
    assertEquals(2,buffer.droppedFrames());
    assertEquals(2,buffer.poll(0,TimeUnit.NANOSECONDS).elapsedNanos);
    buffer.beginLive();
    assertEquals(0,buffer.droppedFrames());
    assertNull(buffer.poll(0,TimeUnit.NANOSECONDS));
  }
  @Test public void clearResetsQueueAndReturnsToStartupMode() throws Exception {
    TimedAudioFrameBuffer buffer = new TimedAudioFrameBuffer();
    buffer.beginLive();
    for (int i=0;i<41;i++) buffer.offer(new short[] {1},i);
    buffer.clear();
    assertEquals(0,buffer.droppedFrames());
    assertNull(buffer.poll(0,TimeUnit.NANOSECONDS));
    for (int i=0;i<41;i++) buffer.offer(new short[] {1},i);
    assertEquals(0,buffer.droppedFrames());
  }
  @Test public void waitingPollReceivesConcurrentOffer() throws Exception {
    TimedAudioFrameBuffer buffer = new TimedAudioFrameBuffer();
    Thread producer = new Thread(() -> buffer.offer(new short[] {7},777));
    producer.start();
    TimedAudioFrameBuffer.Frame frame = buffer.poll(1,TimeUnit.SECONDS);
    producer.join();
    assertNotNull(frame);
    assertEquals(777,frame.elapsedNanos);
  }
  @Test public void validatesFramesAndTimeouts() throws Exception {
    TimedAudioFrameBuffer buffer = new TimedAudioFrameBuffer();
    assertThrows(IllegalArgumentException.class, () -> buffer.offer(null,0));
    assertThrows(IllegalArgumentException.class, () -> buffer.offer(new short[0],0));
    assertThrows(IllegalArgumentException.class, () -> buffer.poll(-1,TimeUnit.SECONDS));
    assertThrows(NullPointerException.class, () -> buffer.poll(0,null));
    assertNull(buffer.poll(1,TimeUnit.MILLISECONDS));
  }
}
