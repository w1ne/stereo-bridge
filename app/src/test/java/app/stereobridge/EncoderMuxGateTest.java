package app.stereobridge;

import org.junit.Test;
import static org.junit.Assert.*;

public class EncoderMuxGateTest {
    @Test public void waitsForBothTrackFormatsInEitherOrder() {
        for (boolean first : new boolean[]{true, false}) {
            EncoderMuxGate gate = new EncoderMuxGate(10, 2);
            assertFalse(gate.ready()); gate.format(first); assertFalse(gate.ready());
            gate.format(!first); assertTrue(gate.ready());
        }
    }
    @Test(expected = IllegalStateException.class) public void duplicateTrackFormatRejected() {
        EncoderMuxGate gate = new EncoderMuxGate(10, 2); gate.format(true); gate.format(true);
    }
    @Test public void bytesAndSampleCountBoundedWithoutDroppingData() {
        EncoderMuxGate gate = new EncoderMuxGate(10, 2);
        gate.reserve(6);
        try { gate.reserve(5); fail(); } catch (IllegalStateException expected) {}
        gate.reserve(4);
        try { gate.reserve(1); fail(); } catch (IllegalStateException expected) {}
        gate.release(6); gate.reserve(6);
    }
    @Test(expected = IllegalStateException.class) public void tinySamplesAlsoBounded() {
        EncoderMuxGate gate = new EncoderMuxGate(10000, 2);
        gate.reserve(1); gate.reserve(1); gate.reserve(1);
    }
}
