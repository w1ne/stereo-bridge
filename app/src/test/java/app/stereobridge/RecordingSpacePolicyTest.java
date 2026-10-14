package app.stereobridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class RecordingSpacePolicyTest {
 @Test public void reservesRoomForMuxerFinalization() { assertTrue(RecordingSpacePolicy.low(0)); assertTrue(RecordingSpacePolicy.low(RecordingSpacePolicy.RESERVE_BYTES-1)); assertFalse(RecordingSpacePolicy.low(RecordingSpacePolicy.RESERVE_BYTES)); }
 @Test public void unknownStatDoesNotBlockCompatibleProvider() { assertFalse(RecordingSpacePolicy.low(-1)); }
}
