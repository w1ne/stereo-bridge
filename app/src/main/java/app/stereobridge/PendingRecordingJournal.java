package app.stereobridge;

import java.io.IOException;

/** Pure lifecycle policy. Persistence must commit synchronously; media operations are idempotent. */
final class PendingRecordingJournal {
  enum Inspection { VALID, INVALID, UNAVAILABLE, MISSING, FOREIGN }
  static final class Entry {
    final String uri;
    final boolean finalized;
    Entry(String uri, boolean finalized) { this.uri = uri; this.finalized = finalized; }
  }
  interface Persistence {
    Entry load();
    void save(Entry entry) throws IOException;
    /** Atomically save the last output URI and remove the pending journal. */
    void published(String uri) throws IOException;
  }
  interface Media {
    Inspection inspect(String uri) throws IOException;
    void publish(String uri) throws IOException;
    /** Must verify app ownership before deleting. */
    void delete(String uri) throws IOException;
  }
  private final Persistence persistence;
  private final Media media;
  PendingRecordingJournal(Persistence persistence, Media media) {
    this.persistence = persistence; this.media = media;
  }
  Entry pending() { return persistence.load(); }
  void begin(String uri) throws IOException {
    if (pending() != null) throw new IOException("An earlier video still needs recovery or publication");
    persistence.save(new Entry(uri, false));
  }
  String finish(String uri) throws IOException {
    Entry entry = require(uri);
    if (!entry.finalized) persistence.save(new Entry(uri, true));
    media.publish(uri);
    persistence.published(uri);
    return uri;
  }
  String recover() throws IOException {
    Entry entry = pending();
    if (entry == null) return null;
    switch (media.inspect(entry.uri)) {
      case VALID: return finish(entry.uri);
      case INVALID:
        if (!entry.finalized) {
          media.delete(entry.uri);
          persistence.save(null);
        }
        return null;
      case FOREIGN:
      case MISSING: persistence.save(null); return null;
      case UNAVAILABLE: return null;
      default: throw new AssertionError();
    }
  }
  /** Only for a capture known not to have finalized; successful deletion precedes journal removal. */
  void discardUnfinished(String uri) throws IOException {
    Entry entry = require(uri);
    if (entry.finalized) throw new IOException("Finalized video must be retained for publication");
    media.delete(uri);
    persistence.save(null);
  }
  private Entry require(String uri) throws IOException {
    Entry entry = pending();
    if (entry == null || !entry.uri.equals(uri)) throw new IOException("Capture does not match pending journal");
    return entry;
  }
}
