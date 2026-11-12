package app.stereobridge.handoff;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Narrow, temporary URI grant to a single private capture file. */
public final class CaptureProvider extends ContentProvider {
  @Override public boolean onCreate() { return true; }
  static File file(Context context, Uri uri) throws FileNotFoundException {
    String name = uri.getLastPathSegment();
    if (!"app.stereobridge.handoff.test.capture".equals(uri.getAuthority())
        || name == null || !name.matches("take-[0-9]+\\.mp4")
        || !uri.getPath().equals("/" + name)) throw new FileNotFoundException("Invalid capture URI");
    return new File(context.getFilesDir(), name);
  }
  @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    return ParcelFileDescriptor.open(file(getContext(), uri), ParcelFileDescriptor.parseMode(mode));
  }
  @Override public String getType(Uri uri) { return "video/mp4"; }
  @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
    try {
      File file = file(getContext(), uri);
      String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
      MatrixCursor cursor = new MatrixCursor(columns);
      MatrixCursor.RowBuilder row = cursor.newRow();
      for (String column : columns) row.add(OpenableColumns.DISPLAY_NAME.equals(column) ? file.getName()
          : OpenableColumns.SIZE.equals(column) ? file.length() : null);
      return cursor;
    } catch (IOException error) { throw new IllegalArgumentException(error); }
  }
  @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
  @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new UnsupportedOperationException(); }
  @Override public int delete(Uri uri, String where, String[] args) { throw new UnsupportedOperationException(); }
}
