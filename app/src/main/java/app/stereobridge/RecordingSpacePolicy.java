package app.stereobridge;
/** Leave room for buffered packets, MP4 metadata and publication bookkeeping. */
final class RecordingSpacePolicy {
 static final long RESERVE_BYTES = 64L*1024*1024;
 static boolean low(long available) { return available >= 0 && available < RESERVE_BYTES; }
}
