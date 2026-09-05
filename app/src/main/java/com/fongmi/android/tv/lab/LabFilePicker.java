package com.fongmi.android.tv.lab;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import com.fongmi.android.tv.App;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class LabFilePicker {

    private static final Map<Integer, Pending> PENDING = new HashMap<>();
    private static int nextCode = 3000;

    private static class Pending {
        final Context context;
        final boolean directory;
        final Consumer<String> callback;

        Pending(Context context, boolean directory, Consumer<String> callback) {
            this.context = context;
            this.directory = directory;
            this.callback = callback;
        }
    }

    private LabFilePicker() {
    }

    public static void pick(Activity activity, boolean directory, Consumer<String> callback) {
        pick(activity, directory, "*/*", callback);
    }

    public static void pick(Activity activity, boolean directory, String mime, Consumer<String> callback) {
        try {
            File root = Environment.getExternalStorageDirectory();
            if (root.canRead() && root.listFiles() != null) {
                new LabFileBrowserDialog(activity, directory, callback).show();
                return;
            }
        } catch (Exception ignored) {
        }
        int code = nextCode++;
        PENDING.put(code, new Pending(activity, directory, callback));
        try {
            Intent intent;
            if (directory) {
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            } else {
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(mime == null || mime.isEmpty() ? "*/*" : mime);
            }
            activity.startActivityForResult(intent, code);
        } catch (Exception e) {
            PENDING.remove(code);
            if (callback != null) callback.accept("");
        }
    }

    public static void onActivityResult(int requestCode, int resultCode, Intent data) {
        Pending pending = PENDING.remove(requestCode);
        if (pending == null) return;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            if (pending.callback != null) pending.callback.accept("");
            return;
        }
        Uri uri = data.getData();
        String path = pending.directory ? resolveDirectory(uri) : resolve(pending.context, uri);
        final String result = path;
        App.post(() -> {
            if (pending.callback != null) pending.callback.accept(result);
        });
    }

    private static String resolveDirectory(Uri uri) {
        try {
            String id = DocumentsContract.getTreeDocumentId(uri);
            if (id != null) {
                if (id.startsWith("primary:")) {
                    String path = "/storage/emulated/0/" + id.substring("primary:".length());
                    if (new File(path).exists()) return path;
                } else if (id.startsWith("home:")) {
                    String path = "/storage/emulated/0/" + id.substring("home:".length());
                    if (new File(path).exists()) return path;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String resolve(Context context, Uri uri) {
        try {
            String id = DocumentsContract.getDocumentId(uri);
            if (id != null) {
                if (id.startsWith("primary:")) {
                    String path = "/storage/emulated/0/" + id.substring("primary:".length());
                    if (new File(path).exists()) return path;
                } else if (id.startsWith("home:")) {
                    String path = "/storage/emulated/0/" + id.substring("home:".length());
                    if (new File(path).exists()) return path;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            String name = displayName(context, uri);
            File dir = new File("/storage/emulated/0/VodPlus/lab_files");
            dir.mkdirs();
            File target = new File(dir, name == null || name.isEmpty() ? "file_" + System.currentTimeMillis() : name);
            try (InputStream in = context.getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[16384];
                int len;
                while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
            }
            return target.getAbsolutePath();
        } catch (Exception e) {
            return uri.toString();
        }
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null ? "" : last;
    }
}
