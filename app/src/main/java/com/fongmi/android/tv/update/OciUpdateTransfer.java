package com.fongmi.android.tv.update;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.utils.Path;

import java.io.File;
import java.util.concurrent.Future;

import okhttp3.Call;

public final class OciUpdateTransfer implements UpdateTransfer, OciRegistryClient.Observer {

    private final UpdateTarget target;
    private final File file;
    private volatile boolean canceled;
    private volatile Call call;
    private Future<?> future;
    private Callback callback;

    public OciUpdateTransfer(UpdateTarget target, File file) {
        if (target == null || target.kind != UpdateTarget.Kind.OCI) throw new IllegalArgumentException("OCI target required");
        this.target = target;
        this.file = file;
    }

    @Override
    public void start(Callback callback) {
        this.callback = callback;
        this.canceled = false;
        this.future = Task.submit(this::pull);
    }

    @Override
    public void cancel() {
        canceled = true;
        if (call != null) call.cancel();
        if (future != null) future.cancel(true);
        Path.clear(file);
    }

    private void pull() {
        try {
            new OciRegistryClient(UpdateHttp.ociClient()).pull(target.endpoint, target.artifact, file, this);
            if (canceled) return;
            App.post(() -> {
                if (!canceled) callback.success(file);
            });
        } catch (Exception e) {
            Path.clear(file);
            if (canceled || "Canceled".equals(e.getMessage())) return;
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            App.post(() -> callback.error(message));
        } finally {
            call = null;
            future = null;
        }
    }

    @Override
    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public void onCall(Call call) {
        this.call = call;
        if (canceled) call.cancel();
    }

    @Override
    public void onProgress(int progress, long bytes, long total, long speed, long elapsed) {
        App.post(() -> {
            if (!canceled) callback.progress(progress, bytes, total, speed, elapsed);
        });
    }
}
