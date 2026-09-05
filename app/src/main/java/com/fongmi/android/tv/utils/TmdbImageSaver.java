package com.fongmi.android.tv.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.github.catvod.net.OkHttp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.Response;
import okhttp3.ResponseBody;

public class TmdbImageSaver {

    private static final String ALBUM = "webhtv";
    private static final String TAG = "tmdb_image_save";

    public static void save(FragmentActivity activity, String url, Callback callback) {
        String source = normalize(url);
        if (TextUtils.isEmpty(source)) {
            if (callback != null) callback.error(ResUtil.getString(R.string.detail_image_save_failed));
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(source, callback);
        } else {
            PermissionUtil.requestFile(activity, granted -> {
                if (!granted) {
                    if (callback != null) callback.error(ResUtil.getString(R.string.detail_image_permission_denied));
                    return;
                }
                saveLegacy(source, callback);
            });
        }
    }

    private static void saveScoped(String url, Callback callback) {
        Task.execute(() -> {
            String displayName = fileName(url);
            String mimeType = mimeType(displayName);
            Uri uri = null;
            try {
                ContentResolver resolver = App.get().getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
                values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + ALBUM);
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
                uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException(ResUtil.getString(R.string.detail_image_save_failed));
                try (OutputStream output = resolver.openOutputStream(uri)) {
                    if (output == null) throw new IllegalStateException(ResUtil.getString(R.string.detail_image_save_failed));
                    download(url, output);
                }
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
                success(callback, Environment.DIRECTORY_PICTURES + File.separator + ALBUM + File.separator + displayName);
            } catch (Exception e) {
                if (uri != null) App.get().getContentResolver().delete(uri, null, null);
                error(callback, e);
            }
        });
    }

    private static void saveLegacy(String url, Callback callback) {
        Task.execute(() -> {
            String displayName = fileName(url);
            String mimeType = mimeType(displayName);
            try {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM);
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException(ResUtil.getString(R.string.detail_image_save_failed));
                File file = uniqueFile(dir, displayName);
                try (OutputStream output = new FileOutputStream(file)) {
                    download(url, output);
                }
                MediaScannerConnection.scanFile(App.get(), new String[]{file.getAbsolutePath()}, new String[]{mimeType}, null);
                success(callback, file.getAbsolutePath());
            } catch (Exception e) {
                error(callback, e);
            }
        });
    }

    private static void download(String url, OutputStream output) throws Exception {
        try (Response response = OkHttp.newCall(url, TAG).execute()) {
            if (!response.isSuccessful()) throw new IllegalStateException(response.message());
            ResponseBody body = response.body();
            if (body == null) throw new IllegalStateException(ResUtil.getString(R.string.detail_image_save_failed));
            try (InputStream input = body.byteStream()) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                output.flush();
            }
        }
    }

    private static File uniqueFile(File dir, String displayName) {
        File file = new File(dir, displayName);
        if (!file.exists()) return file;
        String name = displayName;
        String ext = "";
        int dot = displayName.lastIndexOf('.');
        if (dot >= 0) {
            name = displayName.substring(0, dot);
            ext = displayName.substring(dot);
        }
        int index = 1;
        do {
            file = new File(dir, name + "_" + index++ + ext);
        } while (file.exists());
        return file;
    }

    private static String fileName(String url) {
        String ext = extension(url);
        return "webhtv_tmdb_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date()) + ext;
    }

    private static String extension(String url) {
        String clean = url.split("\\?")[0].split("#")[0].toLowerCase(Locale.US);
        int slash = clean.lastIndexOf('/');
        int dot = clean.lastIndexOf('.');
        if (dot > slash) {
            String ext = clean.substring(dot);
            if (".jpg".equals(ext) || ".jpeg".equals(ext) || ".png".equals(ext) || ".webp".equals(ext)) return ext;
        }
        return ".jpg";
    }

    private static String mimeType(String displayName) {
        String lower = displayName.toLowerCase(Locale.US);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private static String normalize(String url) {
        if (TextUtils.isEmpty(url)) return "";
        url = UrlUtil.convert(url.trim());
        if (url.startsWith("//")) url = "https:" + url;
        int header = url.indexOf("@Headers=");
        int cookie = url.indexOf("@Cookie=");
        int referer = url.indexOf("@Referer=");
        int agent = url.indexOf("@User-Agent=");
        int cut = firstPositive(header, cookie, referer, agent);
        if (cut > 0) url = url.substring(0, cut);
        return url.startsWith("http://") || url.startsWith("https://") ? url : "";
    }

    private static int firstPositive(int... values) {
        int result = -1;
        for (int value : values) {
            if (value <= 0) continue;
            result = result < 0 ? value : Math.min(result, value);
        }
        return result;
    }

    private static void success(Callback callback, String name) {
        if (callback != null) App.post(() -> callback.success(name));
    }

    private static void error(Callback callback, Exception e) {
        if (callback == null) return;
        String message = TextUtils.isEmpty(e.getMessage()) ? ResUtil.getString(R.string.detail_image_save_failed) : e.getMessage();
        App.post(() -> callback.error(message));
    }

    public interface Callback {

        void success(String name);

        void error(String message);
    }
}
