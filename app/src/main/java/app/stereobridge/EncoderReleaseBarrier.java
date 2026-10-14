package app.stereobridge;
import java.util.concurrent.CountDownLatch;
/** Descriptor ownership ends only after all encoder/muxer release calls return. */
final class EncoderReleaseBarrier {
 private final CountDownLatch completion = new CountDownLatch(1);
 boolean isReleased() { return completion.getCount() == 0; }
 void markReleased() { completion.countDown(); }
 void await() throws InterruptedException { completion.await(); }
}
