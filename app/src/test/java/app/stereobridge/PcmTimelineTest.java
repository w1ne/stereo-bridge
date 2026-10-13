package app.stereobridge;

import static org.junit.Assert.*;
import org.junit.Test;

public class PcmTimelineTest {
  @Test public void fiveMillisecondFramesHaveExactTimestamps() {
    PcmTimeline timeline = new PcmTimeline();
    assertEquals(0,timeline.ptsUs());
    timeline.advance(240);
    assertEquals(5000,timeline.ptsUs());
    timeline.advance(240);
    assertEquals(10000,timeline.ptsUs());
  }
  @Test public void partialBuffersDoNotAccumulateRoundingError() {
    PcmTimeline timeline = new PcmTimeline();
    for (int i=0;i<48000;i++) timeline.advance(1);
    assertEquals(1000000,timeline.ptsUs());
  }
  @Test public void supportsLongSessionsBeyondIntegerSampleCount() {
    PcmTimeline timeline = new PcmTimeline();
    for (int i=0;i<100;i++) timeline.advance(Integer.MAX_VALUE);
    assertEquals(4473924264583L,timeline.ptsUs());
  }
  @Test public void rejectsNegativeAdvanceWithoutChangingPosition() {
    PcmTimeline timeline = new PcmTimeline();
    timeline.advance(1);
    assertThrows(IllegalArgumentException.class, () -> timeline.advance(-1));
    timeline.advance(0);
    assertEquals(20,timeline.ptsUs());
  }
}
