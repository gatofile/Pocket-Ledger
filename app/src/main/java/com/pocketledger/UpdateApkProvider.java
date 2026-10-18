package com.pocketledger;

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

/** Grants Android's installer read access to the single downloaded update, never ledger data. */
public final class UpdateApkProvider extends ContentProvider {
    static File apkFile(Context context) { return new File(context.getFilesDir(), "updates/pocket-ledger-update.apk"); }
    @Override public boolean onCreate() { return true; }
    private void checkUri(Uri uri) {
        if (!"/update.apk".equals(uri.getPath())) throw new SecurityException("Unknown update path");
    }
    @Override public String getType(Uri uri) { checkUri(uri); return "application/vnd.android.package-archive"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        checkUri(uri);
        if (!"r".equals(mode)) throw new FileNotFoundException("Read access only");
        return ParcelFileDescriptor.open(apkFile(getContext()), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        checkUri(uri);
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] values = new Object[columns.length];
        for (int index = 0; index < columns.length; index++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[index])) values[index] = "Pocket-Ledger-update.apk";
            else if (OpenableColumns.SIZE.equals(columns[index])) values[index] = apkFile(getContext()).length();
        }
        cursor.addRow(values);
        return cursor;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
