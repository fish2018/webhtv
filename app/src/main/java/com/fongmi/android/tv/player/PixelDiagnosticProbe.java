package com.fongmi.android.tv.player;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import androidx.media3.ui.PlayerView;
import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.diagnostics.DiagnosticCapture;
import com.github.catvod.crawler.diagnostics.DiagnosticEvent;
import static com.github.catvod.crawler.diagnostics.DiagnosticEvent.Status.*;

/** Three 64x36 statistics only. PixelCopy never certifies physical presentation. */
final class PixelDiagnosticProbe {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastCapture;
    private int[] previous;

    void maybeSample(PlayerView view, String trace) {
        DiagnosticCapture.Session capture = DiagnosticCapture.current();
        if (!DebugLogStore.categoryEnabled(com.github.catvod.crawler.diagnostics.DiagnosticCategories.Category.VIDEO)
                || capture == null || !capture.trace().equals(trace) || capture.id().equals(lastCapture)) return;
        lastCapture = capture.id(); previous = null;
        sample(view, capture);
    }

    private void sample(PlayerView view, DiagnosticCapture.Session capture) {
        if (!DebugLogStore.categoryEnabled(com.github.catvod.crawler.diagnostics.DiagnosticCategories.Category.VIDEO)
                || DiagnosticCapture.current() != capture || !view.isAttachedToWindow()) return;
        int index = DiagnosticCapture.claimPixel(capture);
        if (index < 0) return;
        View target = view.getVideoSurfaceView();
        Activity activity = activity(view.getContext());
        boolean secure = activity == null || (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0;
        if (view.getPlayer() != null) for (androidx.media3.common.Tracks.Group group : view.getPlayer().getCurrentTracks().getGroups())
            for (int i = 0; i < group.length; i++) if (group.isTrackSelected(i) && group.getTrackFormat(i).drmInitData != null) secure = true;
        boolean protectedTarget = secure;
        if (secure || Build.VERSION.SDK_INT < 24 || target == null || target.getWidth() <= 0 || target.getHeight() <= 0) {
            emit(capture, index, target, e -> e.unknown("result", protectedTarget ? PERMISSION_DENIED : Build.VERSION.SDK_INT < 24 ? NOT_SUPPORTED : UNAVAILABLE));
            return;
        }
        Bitmap bitmap = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888);
        long start = android.os.SystemClock.elapsedRealtime();
        PixelCopy.OnPixelCopyFinishedListener callback = result -> {
            try {
                if (DiagnosticCapture.current() != capture) return;
                emit(capture, index, target, e -> {
                    e.observed("errorCode", result).observed("durationMs", android.os.SystemClock.elapsedRealtime() - start);
                    if (result != PixelCopy.SUCCESS) { e.unknown("luminance", UNAVAILABLE); return; }
                    int[] pixels = new int[64 * 36]; bitmap.getPixels(pixels, 0, 64, 0, 0, 64, 36);
                    double sum = 0; int black = 0, changed = 0;
                    for (int i = 0; i < pixels.length; i++) {
                        int pixel = pixels[i];
                        double luma = (0.2126 * ((pixel >> 16) & 255) + 0.7152 * ((pixel >> 8) & 255) + 0.0722 * (pixel & 255)) / 255;
                        sum += luma; if (luma < 0.02) black++;
                        if (previous != null && pixel != previous[i]) changed++;
                    }
                    e.observed("pixelCount", pixels.length).observed("luminance", sum / pixels.length)
                            .observed("blackRatio", (double) black / pixels.length);
                    if (previous == null) e.unknown("changeRatio", NOT_COLLECTED); else e.observed("changeRatio", (double) changed / pixels.length);
                    previous = pixels;
                });
            } finally {
                bitmap.recycle();
                if (DiagnosticCapture.current() == capture && index < 2) handler.postDelayed(() -> sample(view, capture), 500);
                else previous = null;
            }
        };
        try {
            if (target instanceof SurfaceView surface) PixelCopy.request(surface, bitmap, callback, handler);
            else if (Build.VERSION.SDK_INT >= 26) {
                int[] xy = new int[2]; target.getLocationInWindow(xy);
                PixelCopy.request(activity.getWindow(), new Rect(xy[0], xy[1], xy[0] + target.getWidth(), xy[1] + target.getHeight()), bitmap, callback, handler);
            } else { bitmap.recycle(); emit(capture, index, target, e -> e.unknown("result", NOT_SUPPORTED)); }
        } catch (RuntimeException error) {
            bitmap.recycle(); emit(capture, index, target, e -> e.unknown("result", READ_ERROR).observed("javaClass", error.getClass().getSimpleName()));
        }
    }

    private void emit(DiagnosticCapture.Session capture, int index, View target, java.util.function.Consumer<DiagnosticEvent> facts) {
        DiagnosticEvent event = new DiagnosticEvent("video.pixel-probe", capture.trace(), capture.instance(), capture.generation(), capture.attempt())
                .source("ui", "pixelcopy", "foreground", "PixelCopy", "capture-owner", null, null, index, android.os.SystemClock.elapsedRealtimeNanos())
                .observed("captureId", capture.id()).observed("probeIndex", index)
                .observed("surfaceType", target instanceof SurfaceView ? "video-SurfaceView" : "window-region; may include UI")
                .unknown("physicalDisplay", NOT_OBSERVABLE).observed("metricScope", "queued buffer statistics; black/static content can be valid");
        facts.accept(event); DebugLogStore.event(event);
    }

    private static Activity activity(Context context) {
        for (int i = 0; i < 16 && context instanceof ContextWrapper wrapper; i++) {
            if (context instanceof Activity activity) return activity;
            Context base = wrapper.getBaseContext(); if (base == context) break; context = base;
        }
        return context instanceof Activity activity ? activity : null;
    }
}
