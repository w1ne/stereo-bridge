package app.stereobridge;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

/** Keeps the newest 200 ms of one microphone without counting setup as live loss. */
final class AudioFrameBuffer {
  private final ArrayDeque<short[]> frames = new ArrayDeque<>();
  private boolean live;
  private long dropped;

  synchronized void offer(short[] frame) {
    if (frames.size() == 40) {
      frames.removeFirst();
      if (live) dropped++;
    }
    frames.addLast(frame);
    notifyAll();
  }

  synchronized short[] poll(long timeout, TimeUnit unit) throws InterruptedException {
    long remaining = unit.toNanos(timeout);
    long deadline = System.nanoTime() + remaining;
    while (frames.isEmpty() && remaining > 0) {
      TimeUnit.NANOSECONDS.timedWait(this, remaining);
      remaining = deadline - System.nanoTime();
    }
    return frames.pollFirst();
  }

  synchronized void beginLive() {
    frames.clear();
    dropped = 0;
    live = true;
  }

  synchronized void clear() {
    frames.clear();
    dropped = 0;
    live = false;
  }

  synchronized long droppedFrames() {
    return dropped;
  }
}
