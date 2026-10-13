package app.stereobridge;

import org.junit.Test;
import static org.junit.Assert.*;

public class EncoderTimelineTest {
    @Test public void mapsArbitrarySensorClockToElapsedCapture() {
        EncoderTimeline t = new EncoderTimeline();
        t.videoStarted(250000);
        assertEquals(250000, t.videoPts(998877665544L));
        assertEquals(283333, t.videoPts(998877698877L));
    }
    @Test public void firstCaptureCallbackWins() {
        EncoderTimeline t = new EncoderTimeline();
        t.videoStarted(10); t.videoStarted(50);
        assertEquals(10, t.videoPts(500));
    }
    @Test(expected = IllegalStateException.class) public void missingCaptureIsRejected() {
        new EncoderTimeline().videoPts(0);
    }
    @Test(expected = IllegalArgumentException.class) public void backwardsVideoIsRejected() {
        EncoderTimeline t = new EncoderTimeline(); t.videoStarted(0);
        t.videoPts(100); t.videoPts(99);
    }
    @Test public void splitPcmRetainsSampleTimeline() {
        assertEquals(110000, EncoderTimeline.pcmPts(100000, 1920));
        assertEquals(100020, EncoderTimeline.pcmPts(100000, 4));
    }
}
