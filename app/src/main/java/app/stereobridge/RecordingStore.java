package app.stereobridge;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import java.io.Closeable;
import java.io.IOException;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** Direct, scoped MediaStore recording with a durable single-capture recovery journal. API 29+. */
final class RecordingStore {
  private static final OpenCaptureRegistry OPEN_CAPTURES = new OpenCaptureRegistry();
  private static final String PENDING_URI = "recording_store.pending_uri";
  private static final String FINALIZED = "recording_store.finalized";
  private static final String LAST_URI = "last_video_uri";
  private static final Uri COLLECTION =
      MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
  private final ContentResolver resolver;
  private final SharedPreferences preferences;
  private final String packageName;
  private final PendingRecordingJournal journal;
  private Capture active;

  static final class Capture implements Closeable {
    final Uri uri;
    final ParcelFileDescriptor descriptor;
    private volatile boolean closed;
    private final OpenCaptureRegistry.Lease lease;
    private Capture(Uri uri, ParcelFileDescriptor descriptor) {
      this.uri = uri; this.descriptor = descriptor;
      lease = OPEN_CAPTURES.open(uri.toString());
    }
    /** Close only after encoder completion/release is confirmed, including the abort path. */
    @Override public synchronized void close() throws IOException {
      if (!closed) {
        try { descriptor.close(); } finally {
          closed = true;
          lease.close();
        }
      }
    }
    boolean isClosed() { return closed; }
  }

  RecordingStore(Context context) {
    this(context, context.getSharedPreferences("bridge", Context.MODE_PRIVATE));
  }
  RecordingStore(Context context, SharedPreferences preferences) {
    resolver = context.getApplicationContext().getContentResolver();
    packageName = context.getPackageName();
    this.preferences = preferences;
    journal = new PendingRecordingJournal(new PreferenceJournal(), new ScopedMedia());
  }

  /** Journal is committed before returning a writable descriptor and before any muxer is started. */
  synchronized Capture begin() throws IOException {
    synchronized (OPEN_CAPTURES) {
      if (journal.pending() != null || (active != null && !active.isClosed()))
        throw new IOException("An earlier video still needs recovery or publication");
      ContentValues values = new ContentValues();
      String time = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
      values.put(MediaStore.Video.Media.DISPLAY_NAME,
          "StereoBridge-" + time + "-" + UUID.randomUUID().toString().substring(0, 8) + ".mp4");
      values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
      values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Stereo Bridge");
      values.put(MediaStore.Video.Media.IS_PENDING, 1);
      Uri uri;
      try { uri = resolver.insert(COLLECTION, values); }
      catch (RuntimeException e) { throw io("Could not create video", e); }
      if (uri == null) throw new IOException("MediaStore did not create video");
      try {
        journal.begin(uri.toString());
        ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "rw");
        if (descriptor == null) throw new IOException("MediaStore did not open video");
        active = new Capture(uri, descriptor);
        return active;
      } catch (IOException | RuntimeException failure) {
        IOException error = io("Could not start video", failure);
        // The URI was just inserted by this instance. Compensate even if the first journal commit failed.
        try {
          new ScopedMedia().delete(uri.toString());
          new PreferenceJournal().save(null);
        } catch (IOException cleanup) {
          error.addSuppressed(cleanup);
          // Retain a retryable cleanup record if storage recovers after the first failed journal commit.
          try { new PreferenceJournal().save(new PendingRecordingJournal.Entry(uri.toString(), false)); }
          catch (IOException persist) { error.addSuppressed(persist); }
        }
        throw error;
      }
    }
  }

  /** Call after encoder.finish() succeeds and capture.close(). Failed publication retains the journal. */
  synchronized Uri finishAndPublish(Capture capture) throws IOException {
    synchronized (OPEN_CAPTURES) {
      if (capture != active || !capture.isClosed())
        throw new IOException("Finish encoder and close the capture descriptor before publishing");
      try { return Uri.parse(journal.finish(capture.uri.toString())); }
      finally { active = null; }
    }
  }

  /** Run on service startup or explicit Save retry, never while a capture is writing. */
  synchronized Uri recover() throws IOException {
    synchronized (OPEN_CAPTURES) {
      if (active != null && !active.isClosed()) throw new IOException("Capture is still writing");
      PendingRecordingJournal.Entry pending = journal.pending();
      if (pending != null && OPEN_CAPTURES.isOpen(pending.uri))
        throw new IOException("An encoder in this process is still using the pending video");
      active = null;
      String recovered = journal.recover();
      return recovered == null ? null : Uri.parse(recovered);
    }
  }

  synchronized boolean hasPending() { return journal.pending() != null; }
  String lastSavedUri() { return preferences.getString(LAST_URI, ""); }

  /** Use only when capture could not produce a muxer-finalized file; recover() preserves playable partials. */
  synchronized void discardUnfinished(Capture capture) throws IOException {
    synchronized (OPEN_CAPTURES) {
      if (capture != active || !capture.isClosed())
        throw new IOException("Close the capture before discarding");
      try { journal.discardUnfinished(capture.uri.toString()); }
      finally { active = null; }
    }
  }

  private final class PreferenceJournal implements PendingRecordingJournal.Persistence {
    @Override public PendingRecordingJournal.Entry load() {
      String uri = preferences.getString(PENDING_URI, "");
      return uri.isEmpty() ? null : new PendingRecordingJournal.Entry(uri,
          preferences.getBoolean(FINALIZED, false));
    }
    @Override public void save(PendingRecordingJournal.Entry entry) throws IOException {
      SharedPreferences.Editor edit = preferences.edit();
      if (entry == null) edit.remove(PENDING_URI).remove(FINALIZED);
      else edit.putString(PENDING_URI, entry.uri).putBoolean(FINALIZED, entry.finalized);
      if (!edit.commit()) throw new IOException("Could not persist video recovery journal");
    }
    @Override public void published(String uri) throws IOException {
      if (!preferences.edit().putString(LAST_URI, uri).remove(PENDING_URI).remove(FINALIZED).commit()) {
        // SharedPreferences can change its in-memory map despite a failed disk commit.
        // Restore the retry record so this process cannot overwrite an unresolved capture.
        save(new PendingRecordingJournal.Entry(uri, true));
        throw new IOException("Video published, but saving its recovery state failed; retry Save");
      }
    }
  }

  private final class ScopedMedia implements PendingRecordingJournal.Media {
    @Override public PendingRecordingJournal.Inspection inspect(String value) {
      try {
        PendingRecordingJournal.Inspection ownership = ownership(value);
        if (ownership != PendingRecordingJournal.Inspection.VALID) return ownership;
        try (ParcelFileDescriptor descriptor = resolver.openFileDescriptor(Uri.parse(value), "r")) {
          if (descriptor == null) return PendingRecordingJournal.Inspection.UNAVAILABLE;
          if (descriptor.getStatSize() == 0) return PendingRecordingJournal.Inspection.INVALID;
          MediaExtractor extractor = new MediaExtractor();
          try {
            extractor.setDataSource(descriptor.getFileDescriptor());
            boolean audio = false, video = false;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
              MediaFormat format = extractor.getTrackFormat(i);
              String mime = format.getString(MediaFormat.KEY_MIME);
              if (mime == null || (!mime.startsWith("audio/") && !mime.startsWith("video/"))) continue;
              extractor.selectTrack(i);
              extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
              boolean sample = extractor.getSampleTime() >= 0;
              extractor.unselectTrack(i);
              if (mime.startsWith("audio/")) audio |= sample;
              if (mime.startsWith("video/")) video |= sample;
            }
            return audio && video ? PendingRecordingJournal.Inspection.VALID
                : PendingRecordingJournal.Inspection.INVALID;
          } catch (SecurityException e) {
            return PendingRecordingJournal.Inspection.UNAVAILABLE;
          } catch (IOException | RuntimeException e) {
            return inspectFailedParse(value, descriptor);
          } finally { extractor.release(); }
        }
      } catch (IOException | RuntimeException e) {
        return PendingRecordingJournal.Inspection.UNAVAILABLE;
      }
    }
    private PendingRecordingJournal.Inspection inspectFailedParse(
        String value, ParcelFileDescriptor descriptor) {
      // Missing moov after a crash is positively distinguishable from a provider/read failure.
      PendingRecordingJournal.Entry pending = journal.pending();
      if (pending != null && !pending.finalized && pending.uri.equals(value)) {
        Mp4AtomProbe.Result result = probeAtoms(descriptor);
        if (result == Mp4AtomProbe.Result.MISSING_METADATA
            || result == Mp4AtomProbe.Result.TRUNCATED)
          return PendingRecordingJournal.Inspection.INVALID;
      }
      return PendingRecordingJournal.Inspection.UNAVAILABLE;
    }
    private Mp4AtomProbe.Result probeAtoms(ParcelFileDescriptor source) {
      try (FileInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
          ParcelFileDescriptor.dup(source.getFileDescriptor()))) {
        FileChannel channel = input.getChannel();
        return Mp4AtomProbe.inspect(channel.size(), (position, buffer, count) -> {
          ByteBuffer target = ByteBuffer.wrap(buffer, 0, count);
          int read = 0;
          while (target.hasRemaining()) {
            int current = channel.read(target, position + read);
            if (current <= 0) break;
            read += current;
          }
          return read;
        });
      } catch (IOException | RuntimeException e) { return Mp4AtomProbe.Result.UNKNOWN; }
    }
    @Override public void publish(String value) throws IOException {
      try {
        if (ownership(value) != PendingRecordingJournal.Inspection.VALID)
          throw new IOException("Pending video is missing, inaccessible, or not owned by this app");
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.IS_PENDING, 0);
        if (resolver.update(Uri.parse(value), values, null, null) != 1)
          throw new IOException("Could not publish video; retry Save");
      } catch (RuntimeException e) { throw io("Could not publish video; retry Save", e); }
    }
    @Override public void delete(String value) throws IOException {
      try {
        PendingRecordingJournal.Inspection owner = ownership(value);
        if (owner == PendingRecordingJournal.Inspection.MISSING) return;
        if (owner != PendingRecordingJournal.Inspection.VALID)
          throw new IOException("Cannot delete an inaccessible video or one not owned by this app");
        // Protect any already published output even if a stale journal says unfinished.
        Uri uri = Uri.parse(value);
        try (Cursor cursor = resolver.query(uri, new String[] { MediaStore.Video.Media.IS_PENDING },
            null, null, null)) {
          if (cursor == null) throw new IOException("Could not inspect pending video");
          if (!cursor.moveToFirst()) return;
          if (cursor.getInt(0) != 1) throw new IOException("Published video must not be deleted");
        }
        if (resolver.delete(uri, null, null) != 1) throw new IOException("Could not remove unfinished video");
      } catch (RuntimeException e) { throw io("Could not remove unfinished video", e); }
    }
    private PendingRecordingJournal.Inspection ownership(String value) throws IOException {
      Uri uri = Uri.parse(value);
      // The journal never grants authority to operate on arbitrary provider rows.
      try {
        long id = ContentUris.parseId(uri);
        if (id < 0 || !ContentUris.withAppendedId(COLLECTION, id).equals(uri))
          return PendingRecordingJournal.Inspection.FOREIGN;
      } catch (RuntimeException e) { return PendingRecordingJournal.Inspection.FOREIGN; }
      try (Cursor cursor = resolver.query(uri,
          new String[] { MediaStore.MediaColumns.OWNER_PACKAGE_NAME }, null, null, null)) {
        if (cursor == null) return PendingRecordingJournal.Inspection.UNAVAILABLE;
        if (!cursor.moveToFirst()) return PendingRecordingJournal.Inspection.MISSING;
        String owner = cursor.getString(0);
        if (owner == null) return PendingRecordingJournal.Inspection.UNAVAILABLE;
        return packageName.equals(owner) ? PendingRecordingJournal.Inspection.VALID
            : PendingRecordingJournal.Inspection.FOREIGN;
      }
    }
  }
  private static IOException io(String message, Throwable cause) {
    return new IOException(message, cause);
  }
}
