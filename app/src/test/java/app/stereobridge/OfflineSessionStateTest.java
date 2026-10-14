package app.stereobridge;

import static org.junit.Assert.*;

import org.junit.Test;

public class OfflineSessionStateTest {
  @Test
  public void rebootCreatesReadyIdleState() {
    assertFalse(new OfflineSessionState().busy());
  }

  @Test
  public void oneSessionCannotBeStartedTwice() {
    OfflineSessionState s = new OfflineSessionState();
    assertTrue(s.begin());
    assertFalse(s.begin());
    assertTrue(s.busy());
    s.saving();
    assertFalse(s.begin());
    s.saved("content://take");
    assertFalse(s.busy());
    assertEquals("content://take", s.savedUri());
    assertTrue(s.begin());
  }

  @Test
  public void cancelAndErrorNeverClaimRecording() {
    OfflineSessionState s = new OfflineSessionState();
    s.begin();
    s.fail("mic missing");
    assertFalse(s.busy());
    assertEquals("mic missing", s.message());
    assertTrue(s.begin());
    s.idle();
    assertFalse(s.busy());
  }

  @Test
  public void cannotMarkIdleSessionRecording() {
    OfflineSessionState s = new OfflineSessionState();
    assertFalse(s.recording());
    s.begin();
    assertTrue(s.recording());
    assertEquals(OfflineSessionState.Phase.RECORDING, s.phase());
  }

  @Test
  public void restoringLastVideoDoesNotResumeCapture() {
    OfflineSessionState s = new OfflineSessionState();
    s.restoreLast("content://previous");
    assertEquals("content://previous", s.savedUri());
    assertFalse(s.busy());
    assertEquals(OfflineSessionState.Phase.IDLE, s.phase());
    assertTrue(s.begin());
  }

  @Test
  public void restoringSettingsCannotReplaceNewlySavedTake() {
    OfflineSessionState s = new OfflineSessionState();
    s.saved("content://new");
    s.restoreLast("content://old");
    assertEquals("content://new", s.savedUri());
  }
  @Test
  public void recoveryRemembersVideoWithoutChangingActiveStartup() {
    OfflineSessionState s = new OfflineSessionState();
    s.restoreLast("content://old");
    s.begin();
    s.rememberSaved("content://recovered");
    assertEquals("content://recovered", s.savedUri());
    assertEquals(OfflineSessionState.Phase.CONNECTING, s.phase());
    assertTrue(s.busy());
  }
}
