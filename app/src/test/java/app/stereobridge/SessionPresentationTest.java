package app.stereobridge;

import static org.junit.Assert.*;

import org.junit.Test;

public class SessionPresentationTest {
  @Test
  public void stoppedOffersStart() {
    SessionPresentation p = SessionPresentation.of(BridgeState.Phase.STOPPED, true);
    assertEquals("Ready when you are", p.title);
    assertEquals("Start", p.action);
    assertTrue(p.enabled);
  }

  @Test
  public void connectingOffersCancellation() {
    SessionPresentation p = SessionPresentation.of(BridgeState.Phase.CONNECTING, true);
    assertEquals("Connecting your microphones", p.title);
    assertEquals("Cancel", p.action);
  }

  @Test
  public void cameraLiveDoesNotClaimVideoRecording() {
    SessionPresentation p = SessionPresentation.of(BridgeState.Phase.LIVE, true);
    assertEquals("Your camera is ready", p.title);
    assertEquals("Stop", p.action);
  }

  @Test
  public void audioTestIsClearlyRecording() {
    assertEquals(
        "Recording audio preview", SessionPresentation.of(BridgeState.Phase.LIVE, false).title);
  }

  @Test
  public void stoppingDisablesPrimary() {
    assertFalse(SessionPresentation.of(BridgeState.Phase.STOPPING, true).enabled);
  }

  @Test
  public void errorOffersExplicitRetry() {
    assertEquals("Try again", SessionPresentation.of(BridgeState.Phase.ERROR, true).action);
  }
}
