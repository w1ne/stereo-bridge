package app.stereobridge;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Bounded source queue retaining estimated first-sample host capture times. */
final class TimedAudioFrameBuffer {
  static final class Frame {
    final short[] samples;
    final long elapsedNanos;

    private Frame(short[] samples, long elapsedNanos) {
      this.samples = samples;
      this.elapsedNanos = elapsedNanos;
    }
  }

  private static final int CAPACITY = 40;
  private final ArrayDeque<Frame> frames = new ArrayDeque<>();
  private boolean live;
  private long dropped;

  synchronized void offer(short[] samples, long elapsedNanos) {
    if (samples == null || samples.length == 0)
      throw new IllegalArgumentException("PCM frame must contain samples");
    Frame frame = new Frame(samples.clone(), elapsedNanos);
    if (frames.size() == CAPACITY) {
      frames.removeFirst();
      if (live && dropped < Long.MAX_VALUE) dropped++;
    }
    frames.addLast(frame);
    notifyAll();
  }

  synchronized Frame poll(long timeout, TimeUnit unit) throws InterruptedException {
    Objects.requireNonNull(unit);
    if (timeout < 0) throw new IllegalArgumentException("Negative timeout");
    long budget = unit.toNanos(timeout);
    long started = System.nanoTime();
    long remaining = budget;
    while (frames.isEmpty() && remaining > 0) {
      TimeUnit.NANOSECONDS.timedWait(this, remaining);
      // Subtract elapsed time rather than adding a possibly overflowing deadline.
      long elapsed = System.nanoTime() - started;
      remaining = elapsed >= budget ? 0 : budget - elapsed;
    }
    return frames.pollFirst();
  }

  synchronized void beginLive() {
    frames.clear();
    dropped = 0;
    live = true;
  }

  synchronized long droppedFrames() {
    return dropped;
  }

  synchronized void clear() {
    frames.clear();
    dropped = 0;
    live = false;
  }
}
