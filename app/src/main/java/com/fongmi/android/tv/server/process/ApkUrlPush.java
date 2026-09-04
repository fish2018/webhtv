package com.fongmi.android.tv.server.process;

import android.app.Activity;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.dialog.ApkPushProgressDialog;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.Proxy;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class ApkUrlPush {

    private static final ApkUrlPush INSTANCE = new ApkUrlPush();
    private static final OkHttpClient CLIENT = createClient();

    static OkHttpClient createClient() {
        return new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.MINUTES)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .dns(ApkUrlPolicy.publicDns())
            .proxy(Proxy.NO_PROXY)
            .protocols(List.of(Protocol.HTTP_1_1))
            .build();
    }

    private final AtomicBoolean active = new AtomicBoolean();
    private volatile boolean canceled;
    private volatile Call call;
    private volatile int lastProgress = -1;
    private volatile long lastBytes;
    private volatile long lastTotal;
    private volatile long lastSpeed;
    private volatile long lastElapsed;
    private ApkPushProgressDialog dialog;

    public static ApkUrlPush get() {
        return INSTANCE;
    }

    StartResult start(String value, String sender) {
        HttpUrl url = ApkUrlPolicy.parse(value);
        if (!active.compareAndSet(false, true)) return StartResult.BUSY;
        canceled = false;
        resetProgress();
        App.post(this::showDialog);
        try {
            Task.submit(() -> downloadAndInstall(url, sender));
            return StartResult.ACCEPTED;
        } catch (RuntimeException e) {
            active.set(false);
            throw e;
        }
    }

    public void resume(FragmentActivity activity) {
        if (!active.get()) {
            ApkPushProgressDialog.dismiss(activity);
            return;
        }
        showDialog(activity);
    }

    private void cancel() {
        if (!active.get() || canceled) return;
        canceled = true;
        Call current = call;
        if (current != null) current.cancel();
        dismissDialog();
        Notify.show(R.string.apk_push_url_canceled);
    }

    private void downloadAndInstall(HttpUrl url, String sender) {
        File target = Path.cache("pushed-url-" + System.currentTimeMillis() + ".apk");
        boolean ready = false;
        Exception failure = null;
        try {
            throwIfCanceled();
            long size = download(url, target);
            throwIfCanceled();
            if (App.get().getPackageManager().getPackageArchiveInfo(target.getAbsolutePath(), 0) == null) throw new IOException("Downloaded file is not a valid APK");
            SpiderDebug.log("apk-push-url", "downloaded size=%d sender=%s", size, sender == null ? "" : sender);
            ready = true;
            File installFile = target;
            App.post(() -> complete(installFile));
        } catch (Exception e) {
            failure = e;
            if (!canceled) SpiderDebug.log("apk-push-url", e);
        } finally {
            call = null;
            if (!ready) {
                Path.clear(target);
                String message = failure == null ? "Unknown error" : error(failure);
                App.post(() -> {
                    try {
                        dismissDialog();
                        if (!canceled) Notify.show(App.get().getString(R.string.apk_push_url_download_failed, message));
                    } finally {
                        active.set(false);
                    }
                });
            }
        }
    }

    private long download(HttpUrl initial, File target) throws IOException {
        HttpUrl current = initial;
        for (int redirects = 0; ; redirects++) {
            throwIfCanceled();
            Call currentCall = CLIENT.newCall(new Request.Builder().url(current).get().build());
            call = currentCall;
            try (Response response = currentCall.execute()) {
                throwIfCanceled();
                SpiderDebug.log("apk-push-url", "response host=%s protocol=%s code=%d length=%d", current.host(), response.protocol(), response.code(), response.body() == null ? -1 : response.body().contentLength());
                if (ApkUrlPolicy.isRedirect(response.code())) {
                    if (redirects >= ApkUrlPolicy.MAX_REDIRECTS) throw new IOException("Too many redirects");
                    try {
                        current = ApkUrlPolicy.redirect(current, response.header("Location"));
                    } catch (IllegalArgumentException e) {
                        throw new IOException(e.getMessage(), e);
                    }
                    continue;
                }
                if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
                ResponseBody body = response.body();
                if (body == null) throw new IOException("Empty download response");
                long available = FileUtil.getAvailableStorageSpace(Path.cache());
                try (InputStream input = new BufferedInputStream(body.byteStream()); OutputStream output = new BufferedOutputStream(new FileOutputStream(Path.create(target)))) {
                    return copy(input, output, body.contentLength(), available, ApkUrlPolicy.MAX_BYTES, ApkUrlPolicy.STORAGE_RESERVE_BYTES, () -> canceled, this::reportProgress);
                }
            } finally {
                if (call == currentCall) call = null;
            }
        }
    }

    static long copy(InputStream input, OutputStream output, long expectedLength, long availableBytes, long maxBytes, long reserveBytes) throws IOException {
        return copy(input, output, expectedLength, availableBytes, maxBytes, reserveBytes, () -> false, null);
    }

    static long copy(InputStream input, OutputStream output, long expectedLength, long availableBytes, long maxBytes, long reserveBytes, BooleanSupplier canceled, ProgressCallback callback) throws IOException {
        requireBudget(expectedLength, availableBytes, maxBytes, reserveBytes);
        byte[] buffer = new byte[64 * 1024];
        long startTime = System.nanoTime();
        long lastNotifyTime = startTime;
        long lastNotifyBytes = 0;
        int lastProgress = expectedLength > 0 ? 0 : -1;
        long written = 0;
        notifyProgress(callback, lastProgress, written, expectedLength, 0, 0);
        int count;
        while (true) {
            if (canceled.getAsBoolean()) throw new InterruptedIOException("APK download canceled");
            count = input.read(buffer);
            if (count == -1) break;
            long next;
            try {
                next = Math.addExact(written, count);
            } catch (ArithmeticException e) {
                throw new IOException("APK is too large", e);
            }
            requireBudget(next, availableBytes, maxBytes, reserveBytes);
            output.write(buffer, 0, count);
            written = next;
            long now = System.nanoTime();
            long elapsed = TimeUnit.NANOSECONDS.toMillis(now - startTime);
            int progress = expectedLength > 0 ? (int) (written * 100.0 / expectedLength) : -1;
            long sinceNotify = TimeUnit.NANOSECONDS.toMillis(now - lastNotifyTime);
            if (progress != lastProgress || sinceNotify >= 1000) {
                long speed = (written - lastNotifyBytes) * 1000 / Math.max(1, sinceNotify);
                notifyProgress(callback, progress, written, expectedLength, speed, elapsed);
                lastProgress = progress;
                lastNotifyBytes = written;
                lastNotifyTime = now;
            }
        }
        if (canceled.getAsBoolean()) throw new InterruptedIOException("APK download canceled");
        output.flush();
        if (written <= 0) throw new IOException("Downloaded APK is empty");
        if (expectedLength >= 0 && written != expectedLength) throw new IOException("APK download is incomplete");
        if (lastNotifyBytes != written) {
            long now = System.nanoTime();
            long elapsed = TimeUnit.NANOSECONDS.toMillis(now - startTime);
            long sinceNotify = TimeUnit.NANOSECONDS.toMillis(now - lastNotifyTime);
            long speed = (written - lastNotifyBytes) * 1000 / Math.max(1, sinceNotify);
            notifyProgress(callback, expectedLength > 0 ? 100 : -1, written, expectedLength, speed, elapsed);
        }
        return written;
    }

    private static void notifyProgress(ProgressCallback callback, int progress, long bytes, long total, long speed, long elapsed) {
        if (callback != null) callback.onProgress(progress, bytes, total, speed, elapsed);
    }

    private static void requireBudget(long bytes, long availableBytes, long maxBytes, long reserveBytes) throws IOException {
        if (bytes > maxBytes) throw new IOException("APK exceeds the 512 MiB limit");
        if (availableBytes > 0 && (availableBytes <= reserveBytes || bytes > availableBytes - reserveBytes)) throw new IOException("Insufficient storage");
    }

    private void complete(File file) {
        try {
            if (canceled) {
                Path.clear(file);
                return;
            }
            Notify.show(R.string.apk_push_url_install_ready);
            FileUtil.openFile(file);
            Task.schedule(() -> Path.clear(file), 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            Path.clear(file);
            Notify.show(App.get().getString(R.string.apk_push_url_download_failed, error(e)));
        } finally {
            active.set(false);
            dismissDialog();
        }
    }

    private void reportProgress(int progress, long bytes, long total, long speed, long elapsed) {
        lastProgress = progress;
        lastBytes = bytes;
        lastTotal = total;
        lastSpeed = speed;
        lastElapsed = elapsed;
        App.post(() -> {
            if (!active.get() || canceled) return;
            if (dialog == null) showDialog();
            if (dialog != null) dialog.setProgress(progress, bytes, total, speed, elapsed);
        });
    }

    private void showDialog() {
        Activity activity = App.activity();
        if (activity instanceof FragmentActivity) showDialog((FragmentActivity) activity);
    }

    private void showDialog(FragmentActivity activity) {
        if (!active.get() || canceled) return;
        dialog = ApkPushProgressDialog.open(activity, this::cancel);
        if (dialog != null) dialog.setProgress(lastProgress, lastBytes, lastTotal, lastSpeed, lastElapsed);
    }

    private void dismissDialog() {
        ApkPushProgressDialog current = dialog;
        dialog = null;
        if (current != null) current.dismissAllowingStateLoss();
        Activity activity = App.activity();
        if (activity instanceof FragmentActivity) ApkPushProgressDialog.dismiss((FragmentActivity) activity);
    }

    private void resetProgress() {
        lastProgress = -1;
        lastBytes = 0;
        lastTotal = 0;
        lastSpeed = 0;
        lastElapsed = 0;
    }

    private void throwIfCanceled() throws InterruptedIOException {
        if (canceled) throw new InterruptedIOException("APK download canceled");
    }

    private String error(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        return message == null || message.isBlank() ? "Unknown error" : message;
    }

    enum StartResult {
        ACCEPTED,
        BUSY
    }

    interface ProgressCallback {

        void onProgress(int progress, long bytes, long total, long speed, long elapsed);
    }
}
