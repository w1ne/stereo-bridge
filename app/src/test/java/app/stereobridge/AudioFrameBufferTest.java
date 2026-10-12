package app.stereobridge;

import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class AudioFrameBufferTest {
  @Test
  public void burstKeepsNewestFortyFrames() throws Exception {
    AudioFrameBuffer buffer = new AudioFrameBuffer();
    for (short i = 0; i < 60; i++) buffer.offer(new short[] {i});
    for (short i = 20; i < 60; i++) assertEquals(i, buffer.poll(0, TimeUnit.MILLISECONDS)[0]);
    assertNull(buffer.poll(0, TimeUnit.MILLISECONDS));
    assertEquals(0, buffer.droppedFrames());
  }

  @Test
  public void beginLiveDiscardsStartupAndCountsOnlyLiveLoss() throws Exception {
    AudioFrameBuffer buffer = new AudioFrameBuffer();
    for (int i = 0; i < 100; i++) buffer.offer(new short[] {1});
    buffer.beginLive();
    assertNull(buffer.poll(0, TimeUnit.MILLISECONDS));
    for (int i = 0; i < 47; i++) buffer.offer(new short[] {2});
    assertEquals(7, buffer.droppedFrames());
    buffer.beginLive();
    assertEquals(0, buffer.droppedFrames());
    assertNull(buffer.poll(0, TimeUnit.MILLISECONDS));
  }

  @Test
  public void clearResetsForNextStartup() throws Exception {
    AudioFrameBuffer buffer = new AudioFrameBuffer();
    buffer.beginLive();
    for (int i = 0; i < 45; i++) buffer.offer(new short[] {1});
    buffer.clear();
    assertNull(buffer.poll(0, TimeUnit.MILLISECONDS));
    for (int i = 0; i < 45; i++) buffer.offer(new short[] {1});
    assertEquals(0, buffer.droppedFrames());
  }

  @Test
  public void waitingConsumerReceivesFreshFrame() throws Exception {
    AudioFrameBuffer buffer = new AudioFrameBuffer();
    Thread producer = new Thread(() -> buffer.offer(new short[] {42}));
    producer.start();
    assertEquals(42, buffer.poll(1, TimeUnit.SECONDS)[0]);
    producer.join();
  }
}
