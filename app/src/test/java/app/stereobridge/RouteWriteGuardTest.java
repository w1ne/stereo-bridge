package app.stereobridge;

import static org.junit.Assert.*;

import java.lang.reflect.*;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class RouteWriteGuardTest {
  private Object guard() throws Exception {
    try {
      Class<?> type = Class.forName("app.stereobridge.BridgeService$RouteWriteGuard");
      Constructor<?> constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ClassNotFoundException e) {
      fail("Route writes need a finite stall guard");
      return null;
    }
  }

  private void begin(Object guard, long now) throws Exception {
    guard.getClass().getDeclaredMethod("begin", long.class).invoke(guard, now);
  }

  private void finish(Object guard) throws Exception {
    guard.getClass().getDeclaredMethod("finish").invoke(guard);
  }

  private boolean stalled(Object guard, long now) throws Exception {
    return (boolean) guard.getClass().getDeclaredMethod("stalled", long.class).invoke(guard, now);
  }

  @Test
  public void idleMicrophonePollingNeverCountsAsRouteStall() throws Exception {
    assertFalse(stalled(guard(), TimeUnit.DAYS.toNanos(100)));
  }

  @Test
  public void writeMustStayPendingForThreeSecondsToStall() throws Exception {
    Object guard = guard();
    begin(guard, 100);
    assertFalse(stalled(guard, 100 + TimeUnit.SECONDS.toNanos(3) - 1));
    assertTrue(stalled(guard, 100 + TimeUnit.SECONDS.toNanos(3)));
    finish(guard);
    assertFalse(stalled(guard, TimeUnit.DAYS.toNanos(100)));
  }

  @Test
  public void eachWriteGetsItsOwnBudgetAfterIdleGap() throws Exception {
    Object guard = guard();
    begin(guard, 100);
    finish(guard);
    long next = TimeUnit.DAYS.toNanos(100);
    begin(guard, next);
    assertFalse(stalled(guard, next + TimeUnit.SECONDS.toNanos(2)));
  }

  @Test
  public void monotonicCounterRolloverPreservesElapsedBudget() throws Exception {
    Object guard = guard();
    long start = Long.MAX_VALUE - 10;
    begin(guard, start);
    assertTrue(stalled(guard, start + TimeUnit.SECONDS.toNanos(3)));
  }
}
