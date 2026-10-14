package app.stereobridge;
import static org.junit.Assert.*;
import java.nio.ByteBuffer;
import org.junit.Test;
public class PcmBatchQueueTest {
 @Test public void coalescesSmallFramesIntoAacBatch() {
  PcmBatchQueue q=new PcmBatchQueue(8192,512);
  for(int i=0;i<5;i++) { byte[] b=new byte[960]; java.util.Arrays.fill(b,(byte)i); q.offer(b,i*5000L); }
  assertTrue(q.ready(false));
  ByteBuffer b=ByteBuffer.allocate(4096); PcmBatchQueue.Batch batch=q.take(b,false);
  assertEquals(4096,batch.bytes); assertEquals(0,batch.ptsUs); assertEquals(704,q.bytes());
  assertEquals(4,b.get(4095)); assertFalse(q.ready(false));
  b.clear(); batch=q.take(b,true); assertEquals(704,batch.bytes); assertEquals(21333,batch.ptsUs);
  assertEquals(25000,batch.endUs); assertEquals(0,q.bytes());
 }
 @Test public void handlesSmallCodecCapacityAndFinalPartialFrame() {
  PcmBatchQueue q=new PcmBatchQueue(8192,512); q.offer(new byte[4096],1000);
  PcmBatchQueue.Batch b=q.take(ByteBuffer.allocate(1001),false);
  assertEquals(1000,b.bytes); assertEquals(1000,b.ptsUs);
  b=q.take(ByteBuffer.allocate(4096),true); assertEquals(3096,b.bytes); assertEquals(6208,b.ptsUs); assertEquals(22333,b.endUs);
 }
 @Test public void rejectsGapAndBoundsQueue() {
  PcmBatchQueue q=new PcmBatchQueue(4096,2); q.offer(new byte[960],0);
  assertThrows(IllegalArgumentException.class,()->q.offer(new byte[960],6000));
  q.offer(new byte[960],5000);
  assertThrows(IllegalStateException.class,()->q.offer(new byte[960],10000));
 }
 @Test public void waitsForFullBatchUntilStop() {
  PcmBatchQueue q=new PcmBatchQueue(8192,512); q.offer(new byte[960],0);
  assertFalse(q.ready(false)); assertTrue(q.ready(true));
  assertNull(q.take(ByteBuffer.allocate(4096),false));
  assertEquals(960,q.take(ByteBuffer.allocate(4096),true).bytes);
 }
 @Test public void derivesTimestampFromSampleCountWithoutRoundingAccumulation() {
  PcmBatchQueue q=new PcmBatchQueue(8192,2048);
  byte[] sample=new byte[] {1,2,3,4};
  for(int i=0;i<1024;i++) q.offer(sample,77+i*1_000_000L/48000);
  sample[0]=99;
  ByteBuffer output=ByteBuffer.allocate(4096);
  PcmBatchQueue.Batch batch=q.take(output,false);
  assertEquals(77,batch.ptsUs); assertEquals(21410,batch.endUs); assertEquals(1,output.get(0));
 }
 @Test public void byteLimitRemainsExplicit() {
  PcmBatchQueue q=new PcmBatchQueue(4096,512);
  q.offer(new byte[4096],0);
  assertThrows(IllegalStateException.class,()->q.offer(new byte[4],21333));
 }
}
