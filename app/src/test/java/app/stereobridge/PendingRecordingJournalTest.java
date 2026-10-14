package app.stereobridge;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class PendingRecordingJournalTest {
  private static final String URI = "content://media/external/video/media/42";
  static class Memory implements PendingRecordingJournal.Persistence {
    PendingRecordingJournal.Entry entry;
    String saved;
    boolean fail;
    public PendingRecordingJournal.Entry load() { return entry; }
    public void save(PendingRecordingJournal.Entry value) throws IOException {
      if (fail) throw new IOException("disk unavailable");
      entry = value;
    }
    public void published(String uri) throws IOException {
      if (fail) throw new IOException("disk unavailable");
      saved = uri; entry = null;
    }
  }
  static class Media implements PendingRecordingJournal.Media {
    PendingRecordingJournal.Inspection inspection = PendingRecordingJournal.Inspection.VALID;
    int publishes, deletes;
    boolean failPublish, failDelete;
    public PendingRecordingJournal.Inspection inspect(String uri) { return inspection; }
    public void publish(String uri) throws IOException {
      publishes++;
      if (failPublish) throw new IOException("provider unavailable");
    }
    public void delete(String uri) throws IOException {
      if (failDelete) throw new IOException("provider unavailable");
      deletes++;
    }
  }
  @Test public void publicationFailureRetainsFinalizedJournalForRestartRetry() throws Exception {
    Memory p = new Memory(); Media m = new Media();
    PendingRecordingJournal j = new PendingRecordingJournal(p, m);
    j.begin(URI); m.failPublish = true;
    try { j.finish(URI); fail(); } catch (IOException expected) { }
    assertTrue(p.entry.finalized);
    m.failPublish = false;
    assertEquals(URI, new PendingRecordingJournal(p, m).recover());
    assertEquals(URI, p.saved); assertNull(p.entry); assertEquals(0, m.deletes);
  }
  @Test public void unfinishedPlayableCaptureRecovers() throws Exception {
    Memory p = new Memory(); Media m = new Media();
    PendingRecordingJournal j = new PendingRecordingJournal(p, m);
    j.begin(URI);
    assertEquals(URI, j.recover()); assertEquals(1, m.publishes);
  }
  @Test public void unfinishedInvalidCaptureIsDeleted() throws Exception {
    Memory p = new Memory(); Media m = new Media();
    PendingRecordingJournal j = new PendingRecordingJournal(p, m);
    j.begin(URI); m.inspection = PendingRecordingJournal.Inspection.INVALID;
    assertNull(j.recover()); assertNull(p.entry); assertEquals(1, m.deletes);
  }
  @Test public void inaccessibleCaptureIsRetainedForRetry() throws Exception {
    Memory p = new Memory(); Media m = new Media();
    PendingRecordingJournal j = new PendingRecordingJournal(p, m);
    j.begin(URI); m.inspection = PendingRecordingJournal.Inspection.UNAVAILABLE;
    assertNull(j.recover()); assertNotNull(p.entry); assertEquals(0, m.deletes);
  }
  @Test public void foreignOrMissingUriClearsOnlyJournal() throws Exception {
    for (PendingRecordingJournal.Inspection result : new PendingRecordingJournal.Inspection[] {
        PendingRecordingJournal.Inspection.FOREIGN, PendingRecordingJournal.Inspection.MISSING }) {
      Memory p = new Memory(); Media m = new Media();
      PendingRecordingJournal j = new PendingRecordingJournal(p, m);
      j.begin(URI); m.inspection = result;
      assertNull(j.recover()); assertNull(p.entry); assertEquals(0, m.deletes);
    }
  }
  @Test public void finalizedInvalidFileIsRetained() throws Exception {
    Memory p = new Memory(); Media m = new Media();
    p.entry = new PendingRecordingJournal.Entry(URI, true);
    m.inspection = PendingRecordingJournal.Inspection.INVALID;
    assertNull(new PendingRecordingJournal(p, m).recover());
    assertNotNull(p.entry); assertEquals(0, m.deletes); assertEquals(0, m.publishes);
  }
  @Test public void failedFinalizedCommitPreventsPublication() throws Exception {
    Memory p = new Memory(); Media m = new Media();
    PendingRecordingJournal j = new PendingRecordingJournal(p, m);
    j.begin(URI); p.fail = true;
    try { j.finish(URI); fail(); } catch (IOException expected) { }
    assertEquals(0, m.publishes); assertNotNull(p.entry);
  }
  @Test public void failedSavedCommitRetainsFinalizedJournalAndRetriesIdempotently() throws Exception {
    Memory p = new Memory(); Media m = new Media();
    p.entry = new PendingRecordingJournal.Entry(URI, true); p.fail = true;
    PendingRecordingJournal j = new PendingRecordingJournal(p, m);
    try { j.recover(); fail(); } catch (IOException expected) { }
    assertTrue(p.entry.finalized); p.fail = false;
    assertEquals(URI, j.recover()); assertEquals(2, m.publishes);
  }
  @Test public void pendingCaptureCannotBeOverwrittenOrFinishedByDifferentUri() throws Exception {
    Memory p = new Memory(); Media m = new Media();
    PendingRecordingJournal j = new PendingRecordingJournal(p, m); j.begin(URI);
    try { j.begin("other"); fail(); } catch (IOException expected) { }
    try { j.finish("other"); fail(); } catch (IOException expected) { }
    assertEquals(URI, p.entry.uri); assertEquals(0, m.publishes);
  }
  @Test public void failedDeleteRetainsJournal() throws Exception {
    Memory p = new Memory(); Media m = new Media();
    PendingRecordingJournal j = new PendingRecordingJournal(p, m); j.begin(URI);
    m.inspection = PendingRecordingJournal.Inspection.INVALID; m.failDelete = true;
    try { j.recover(); fail(); } catch (IOException expected) { }
    assertNotNull(p.entry);
  }
}
