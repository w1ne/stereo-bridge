package app.stereobridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class OpenCaptureRegistryTest {
  @Test public void logicalConsumersShareAnOpenLease() {
    OpenCaptureRegistry r = new OpenCaptureRegistry();
    OpenCaptureRegistry.Lease producer = r.open("uri");
    assertTrue(r.isOpen("uri")); producer.close(); assertFalse(r.isOpen("uri"));
  }
  @Test public void sameUriRemainsOpenUntilEveryLeaseCloses() {
    OpenCaptureRegistry r = new OpenCaptureRegistry();
    OpenCaptureRegistry.Lease a = r.open("uri"), b = r.open("uri");
    a.close(); a.close(); assertTrue(r.isOpen("uri"));
    b.close(); assertFalse(r.isOpen("uri"));
  }
  @Test public void unknownUriDoesNotBlockAnotherCapture() {
    OpenCaptureRegistry r = new OpenCaptureRegistry();
    OpenCaptureRegistry.Lease a = r.open("one");
    assertFalse(r.isOpen("two")); assertFalse(r.isOpen(null));
    a.close(); assertFalse(r.isOpen("one"));
  }
  @Test public void newProcessRegistryDoesNotRetainOldLeases() {
    OpenCaptureRegistry old = new OpenCaptureRegistry(); old.open("uri");
    assertFalse(new OpenCaptureRegistry().isOpen("uri"));
  }
  @Test public void closingOldLeaseCannotReleaseNewLease() {
    OpenCaptureRegistry r = new OpenCaptureRegistry();
    OpenCaptureRegistry.Lease old = r.open("uri"); old.close();
    OpenCaptureRegistry.Lease fresh = r.open("uri"); old.close();
    assertTrue(r.isOpen("uri")); fresh.close(); assertFalse(r.isOpen("uri"));
  }
}
