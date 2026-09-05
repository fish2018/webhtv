package com.fongmi.android.tv.player.exo;

final class PreCacheThreadExceptionHandler implements Thread.UncaughtExceptionHandler {

    interface Listener {

        void onRuntimeFailure(Thread thread, RuntimeException error);
    }

    private final Thread.UncaughtExceptionHandler fallback;
    private final Listener listener;

    PreCacheThreadExceptionHandler(Listener listener, Thread.UncaughtExceptionHandler fallback) {
        this.listener = listener;
        this.fallback = fallback;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable error) {
        if (error instanceof RuntimeException runtimeException) {
            try {
                listener.onRuntimeFailure(thread, runtimeException);
            } catch (Throwable recoveryFailure) {
                if (recoveryFailure != error) recoveryFailure.addSuppressed(error);
                delegate(thread, recoveryFailure);
            }
        } else {
            delegate(thread, error);
        }
    }

    private void delegate(Thread thread, Throwable error) {
        if (fallback != null) fallback.uncaughtException(thread, error);
        else if (thread.getThreadGroup() != null) thread.getThreadGroup().uncaughtException(thread, error);
    }
}
