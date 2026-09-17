package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.fongmi.android.tv.App;

public final class ActivityLaunch {

    private static final String TAG = ActivityLaunch.class.getSimpleName();

    private ActivityLaunch() {
    }

    public static void postOnAnimation(Activity activity, Runnable launch) {
        if (activity == null || launch == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            App.post(() -> postOnAnimation(activity, launch));
            return;
        }
        if (!isAlive(activity)) return;
        View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (decor == null || !decor.isAttachedToWindow()) return;
        // Leave the click/key dispatch that requested playback before Android cancels pending input.
        decor.postOnAnimation(() -> {
            if (!isAlive(activity)) return;
            try {
                launch.run();
            } catch (NullPointerException e) {
                if (!isCancelPendingInputCrash(e)) throw e;
                // Activity.startActivity has already dispatched the launch before this framework cleanup.
                Log.w(TAG, "Ignored ViewGroup input-cancellation crash after Activity launch", e);
            }
        });
    }

    static boolean isAlive(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    static boolean isCancelPendingInputCrash(Throwable error) {
        if (error == null || error.getStackTrace().length == 0) return false;
        StackTraceElement top = error.getStackTrace()[0];
        return "android.view.ViewGroup".equals(top.getClassName())
                && "dispatchCancelPendingInputEvents".equals(top.getMethodName());
    }
}
