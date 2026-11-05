package com.keplerslicer.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Read-only provider for Export "Share" and generated Import files. Only regular files directly inside
 * cache/share, cache/shapes or cache/url-import are served, through per-intent URI grants (not exported).
 */
public final class ShareProvider extends ContentProvider {
    static Uri uriFor(Context context, File file) {
        return new Uri.Builder().scheme("content").authority(context.getPackageName() + ".share").appendPath(file.getParentFile().getName()).appendPath(file.getName()).build();
    }
    static File directory(Context context) throws IOException {
        File dir = new File(context.getCacheDir(), "share").getCanonicalFile();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException(L.t("Не удалось подготовить файл для отправки."));
        return dir;
    }
    private File resolve(Uri uri) throws FileNotFoundException {
        try {
            java.util.List<String> parts = uri.getPathSegments(); if (parts.size() != 2 || !java.util.Arrays.asList("share", "shapes", "url-import").contains(parts.get(0))) throw new FileNotFoundException();
            File dir = new File(getContext().getCacheDir(), parts.get(0)).getCanonicalFile(), file = new File(dir, parts.get(1)).getCanonicalFile();
            if (!dir.equals(file.getParentFile()) || !file.isFile()) throw new FileNotFoundException();
            return file;
        } catch (IOException e) { throw new FileNotFoundException(e.getMessage()); }
    }
    @Override public boolean onCreate() { return true; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only");
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        File file; try { file = resolve(uri); } catch (FileNotFoundException e) { return null; }
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns); Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) row[i] = OpenableColumns.DISPLAY_NAME.equals(columns[i]) ? file.getName() : OpenableColumns.SIZE.equals(columns[i]) ? file.length() : null;
        cursor.addRow(row); return cursor;
    }
    @Override public String getType(Uri uri) { String name = uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment().toLowerCase(java.util.Locale.ROOT); return name.endsWith(".obj") ? "model/obj" : name.endsWith(".stl") ? "model/stl" : "application/octet-stream"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
