package com.fongmi.android.tv.player.iso;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;

public interface RemoteIsoSource extends Closeable {

    long length() throws IOException;

    int readAt(long offset, byte[] buffer, int bufferOffset, int length) throws IOException;

    /** Publish only bytes already written to the target; never overwrite a published prefix. */
    default int readAt(long offset, byte[] buffer, int bufferOffset, int length,
                       ReadRequest request) throws IOException {
        request.checkCancelled();
        int count = readAt(offset, buffer, bufferOffset, length);
        if (count > 0) request.publish(count);
        return count;
    }

    String validator();

    @Override
    void close();

    final class ReadRequest {
        public interface Progress {
            void available(int count) throws IOException;
        }

        private final Progress progress;
        private volatile boolean cancelled;
        private Runnable cancellation;

        public ReadRequest(Progress progress) {
            this.progress = progress;
        }

        public void publish(int count) throws IOException {
            checkCancelled();
            progress.available(count);
        }

        public void checkCancelled() throws InterruptedIOException {
            if (cancelled) throw new InterruptedIOException("ISO range superseded");
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public void attach(Runnable action) {
            synchronized (this) {
                if (!cancelled) {
                    cancellation = action;
                    return;
                }
            }
            action.run();
        }

        public synchronized void detach() {
            cancellation = null;
        }

        public void cancel() {
            Runnable action;
            synchronized (this) {
                cancelled = true;
                action = cancellation;
                cancellation = null;
            }
            if (action != null) action.run();
        }
    }
}
