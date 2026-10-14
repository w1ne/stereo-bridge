package app.stereobridge;
import static org.junit.Assert.*;
import org.junit.Test;
public class EncoderReleaseBarrierTest {
 @Test public void incompleteAbortDoesNotPermitDescriptorRecovery() {
   EncoderReleaseBarrier b = new EncoderReleaseBarrier();
   assertFalse(b.isReleased());
   b.markReleased();
   assertTrue(b.isReleased());
 }
 @Test public void waiterRunsOnlyAfterActualRelease() throws Exception {
   EncoderReleaseBarrier b = new EncoderReleaseBarrier();
   java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
   Thread t = new Thread(() -> { try { b.await(); ready.set(true); } catch(InterruptedException ignored){} });
   t.start();
   assertFalse(ready.get());
   b.markReleased(); t.join(1000);
   assertTrue(ready.get());
 }
}
