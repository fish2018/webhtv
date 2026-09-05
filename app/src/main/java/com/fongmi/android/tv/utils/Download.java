package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.concurrent.Future;

import okhttp3.Request;
import okhttp3.Response;

public class Download {

    private final File file;
    private final String url;
    private Callback callback;
    private Future<?> future;
    private Map<String, String> headers;
    private String tag;
    private boolean resume;
    private volatile boolean canceled;
    private volatile boolean paused;

    public static Download create(String url, File file) {
        return new Download(GithubProxy.apply(url), file);
    }

    public static Download create(String url, Map<String, String> headers, File file) {
        return new Download(GithubProxy.apply(url), file).headers(headers);
    }

    public Download(String url, File file) {
        this.tag = url;
        this.url = url;
        this.file = file;
    }

    public Download tag(String tag) {
        this.tag = tag;
        return this;
    }

    public Download headers(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    /**
     * 开启斷點續傳，續傳時會帶上 Range 請求頭並以追加方式寫入。
     */
    public Download resume(boolean resume) {
        this.resume = resume;
        return this;
    }

    public File get() {
        doInBackground();
        return file;
    }

    public void start(Callback callback) {
        this.callback = callback;
        this.canceled = false;
        this.paused = false;
        future = Task.submit(this::doInBackground);
    }

    public Download cancel() {
        canceled = true;
        if (future != null) future.cancel(true);
        OkHttp.cancel(tag);
        Path.clear(file);
        future = null;
        return this;
    }

    /**
     * 暫停下載並保留已下載的檔案內容，後續可搭配 resume(true) 續傳。
     */
    public Download pause() {
        paused = true;
        if (future != null) future.cancel(true);
        OkHttp.cancel(tag);
        future = null;
        return this;
    }

    public boolean isPaused() {
        return paused;
    }

    private void doInBackground() {
        long start = resume && file.exists() ? file.length() : 0;
        SpiderDebug.log("download", "doInBackground url=%s fileLen=%s resume=%s", url, start, resume);
        try (Response res = newCall(start).execute()) {
            if (!res.isSuccessful()) throw new IOException("Download failed: HTTP " + res.code());
            if (res.body() == null) throw new IOException("Download failed: empty response");
            boolean append = start > 0 && res.code() == 206;
            long length = getLength(res);
            if (append && length > 0) length += start;
            SpiderDebug.log("download", "response code=%s append=%s length=%s offset=%s", res.code(), append, length, append ? start : 0);
            boolean completed = download(res.body().byteStream(), length, append ? start : 0);
            if (paused) return;
            if (!completed || canceled) {
                Path.clear(file);
                return;
            }
            if (callback != null) App.post(() -> {
                if (!canceled) callback.success(file);
            });
        } catch (Exception e) {
            if (paused) return;
            Path.clear(file);
            if (canceled || isCanceled(e)) return;
            if (callback != null) App.post(() -> callback.error(e.getMessage()));
            else throw new RuntimeException(e.getMessage(), e);
        }
    }

    private okhttp3.Call newCall(long start) {
        Request.Builder builder = new Request.Builder().url(url).tag(tag);
        if (headers != null) for (Map.Entry<String, String> entry : headers.entrySet()) builder.addHeader(entry.getKey(), entry.getValue());
        if (start > 0) builder.addHeader(HttpHeaders.RANGE, "bytes=" + start + "-");
        return OkHttp.client().newCall(builder.build());
    }

    private boolean download(InputStream is, long length, long offset) throws IOException {
        // 續傳（offset>0 且檔案已存在）時絕對不能清空已下載內容：Path.create 會刪除已存在的檔案，
        // 導致 seek(offset) 寫入後檔案前 offset 位元組變成 0 空洞 → 檔案損壞無法播放。
        // 因此續傳直接以 "rw" 模式開啟（不截斷）；僅首次（offset==0）或檔案不存在時清空重建。
        if (offset == 0 || !file.exists()) {
            Path.clear(file);
            Path.create(file);
        }
        SpiderDebug.log("download", "download() file=%s offset=%s fileLenBefore=%s", file.getName(), offset, file.length());
        try (BufferedInputStream input = new BufferedInputStream(is); RandomAccessFile os = new RandomAccessFile(file, "rw")) {
            if (offset > 0) os.seek(offset);
            else os.setLength(0);
            byte[] buffer = new byte[16384];
            int readBytes;
            int lastProgress = -1;
            long totalBytes = offset;
            long startTime = System.currentTimeMillis();
            long lastNotifyTime = startTime;
            long lastNotifyBytes = offset;
            long begin = offset;
            if (callback != null) App.post(() -> callback.progress(length > 0 ? (int) (begin * 100.0 / length) : -1, begin, length, 0, 0));
            while ((readBytes = input.read(buffer)) != -1) {
                if (canceled || paused || Thread.currentThread().isInterrupted()) return false;
                totalBytes += readBytes;
                os.write(buffer, 0, readBytes);
                if (callback == null) continue;
                long now = System.currentTimeMillis();
                int progress = length > 0 ? (int) (totalBytes * 100.0 / length) : -1;
                boolean shouldNotify = progress != lastProgress || now - lastNotifyTime >= 1000;
                if (!shouldNotify) continue;
                long deltaTime = Math.max(1, now - lastNotifyTime);
                long speed = (totalBytes - lastNotifyBytes) * 1000 / deltaTime;
                long elapsed = now - startTime;
                lastProgress = progress;
                lastNotifyTime = now;
                lastNotifyBytes = totalBytes;
                long bytes = totalBytes;
                long total = length;
                App.post(() -> callback.progress(progress, bytes, total, speed, elapsed));
            }
            if (canceled || paused) return false;
            if (length > 0 && totalBytes != length) throw new IOException("Download incomplete");
            return true;
        }
    }

    private boolean isCanceled(Exception e) {
        String message = e.getMessage();
        return "Canceled".equals(message) || "Socket closed".equals(message);
    }

    private long getLength(Response res) {
        try {
            String header = res.header(HttpHeaders.CONTENT_LENGTH);
            return header != null ? Long.parseLong(header) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public interface Callback {

        void progress(int progress);

        default void progress(int progress, long bytes, long total, long speed, long elapsed) {
            progress(progress);
        }

        void error(String msg);

        void success(File file);
    }
}
