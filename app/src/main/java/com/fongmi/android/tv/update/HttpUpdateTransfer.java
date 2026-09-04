package com.fongmi.android.tv.update;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.utils.Path;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Future;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class HttpUpdateTransfer implements UpdateTransfer {

    private final String url;
    private final File file;
    private final long expectedSize;
    private volatile boolean canceled;
    private volatile Call call;
    private Future<?> future;

    public HttpUpdateTransfer(String url, File file, long expectedSize) {
        this.url = UpdateUrl.requireHttpsUrl(url);
        this.file = file;
        this.expectedSize = Math.max(0, expectedSize);
    }

    @Override
    public void start(Callback callback) {
        canceled = false;
        future = Task.submit(() -> download(callback));
    }

    @Override
    public void cancel() {
        canceled = true;
        if (call != null) call.cancel();
        if (future != null) future.cancel(true);
        Path.clear(file);
    }

    private void download(Callback callback) {
        Request request = new Request.Builder().url(url).header("Accept-Encoding", "identity").build();
        call = UpdateHttp.client().newCall(request);
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) throw new IOException("Download failed: HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Download failed: empty response");
            if (expectedSize > 0 && body.contentLength() > 0 && body.contentLength() != expectedSize) throw new IOException("Download size mismatch");
            stream(body.byteStream(), body.contentLength(), callback);
            if (canceled) return;
            App.post(() -> {
                if (!canceled) callback.success(file);
            });
        } catch (Exception e) {
            Path.clear(file);
            if (canceled || isCanceled(e)) return;
            App.post(() -> callback.error(message(e)));
        } finally {
            call = null;
            future = null;
        }
    }

    private void stream(InputStream source, long length, Callback callback) throws IOException {
        try (BufferedInputStream input = new BufferedInputStream(source); FileOutputStream output = new FileOutputStream(Path.create(file))) {
            byte[] buffer = new byte[16384];
            long bytes = 0;
            long start = System.currentTimeMillis();
            long lastTime = start;
            long lastBytes = 0;
            int lastProgress = -1;
            postProgress(callback, length > 0 ? 0 : -1, 0, length, 0, 0);
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (canceled || Thread.currentThread().isInterrupted()) throw new IOException("Canceled");
                output.write(buffer, 0, count);
                bytes += count;
                if (expectedSize > 0 && bytes > expectedSize) throw new IOException("Download exceeds expected size");
                long now = System.currentTimeMillis();
                int progress = length > 0 ? (int) (bytes * 100 / length) : -1;
                if (progress == lastProgress && now - lastTime < 1000) continue;
                long speed = (bytes - lastBytes) * 1000 / Math.max(1, now - lastTime);
                postProgress(callback, progress, bytes, length, speed, now - start);
                lastProgress = progress;
                lastTime = now;
                lastBytes = bytes;
            }
            if (length > 0 && bytes != length) throw new IOException("Download incomplete");
            if (expectedSize > 0 && bytes != expectedSize) throw new IOException("Download size mismatch");
        }
    }

    private void postProgress(Callback callback, int progress, long bytes, long total, long speed, long elapsed) {
        App.post(() -> {
            if (!canceled) callback.progress(progress, bytes, total, speed, elapsed);
        });
    }

    private boolean isCanceled(Exception e) {
        String message = e.getMessage();
        return "Canceled".equals(message) || "Socket closed".equals(message);
    }

    private String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
