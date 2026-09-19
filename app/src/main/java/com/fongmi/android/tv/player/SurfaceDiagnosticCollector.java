package com.fongmi.android.tv.player;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;

import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;

import com.github.catvod.crawler.DebugLogStore;

import static com.github.catvod.crawler.diagnostics.DiagnosticEvent.Status.*;

/** Adds SurfaceHolder observers without replacing the player-owned callbacks/listeners. */
public final class SurfaceDiagnosticCollector implements SurfaceHolder.Callback {
    private final PlaybackDiagnosticCollector log = new PlaybackDiagnosticCollector("ui", "surface-v1");
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PlayerView playerView;
    private View surfaceView;
    private String trace = "none", resizeOperation = "none";
    private final PixelDiagnosticProbe pixelProbe = new PixelDiagnosticProbe();
    private final Runnable tick = new Runnable() {
        @Override public void run() { snapshot("periodic"); if (playerView != null) handler.postDelayed(this, 5000); }
    };

    public void bind(PlayerView view, String trace) {
        if (!this.trace.equals(trace)) { this.trace = trace; log.begin(trace, "foreground-window"); }
        View target = view.getVideoSurfaceView();
        if (playerView == view && target == surfaceView) return;
        unbind(); playerView = view; surfaceView = target;
        if (target instanceof SurfaceView sv) {
            sv.getHolder().addCallback(this);
            state(sv.getHolder(), "surface.bind", "existing-holder");
        }
        snapshot("bind"); handler.postDelayed(tick, 5000);
    }

    public void unbind() {
        if (surfaceView instanceof SurfaceView sv) {
            state(sv.getHolder(), "surface.bind", "detach-observer"); sv.getHolder().removeCallback(this);
        }
        handler.removeCallbacks(tick); playerView = null; surfaceView = null;
    }

    public void resize(String mode, int width, int height) {
        if (!PlaybackDiagnosticCollector.enabled()) return;
        resizeOperation = PlaybackDiagnosticCollector.id("surface-resize");
        log.emit("surface.resize", "player-view-request", e -> e.observed("operationId", resizeOperation)
                .requested("resizeMode", mode).requested("requestedWidth", width < 0 ? null : width)
                .requested("requestedHeight", height < 0 ? null : height)
                .unknown("holderWidth", PENDING_CALLBACK).unknown("holderHeight", PENDING_CALLBACK));
    }

    public void display(com.fongmi.android.tv.player.exo.ExoOutputModeManager.Result result) {
        log.emit("display.change", "display-mode-manager", e -> {
            e.observed("reason", result.reason()).observed("result", result.applied() ? "request-applied" : "observed");
            if (result.requestedMode() != null) e.requested("modeId", result.requestedMode().id())
                    .requested("refreshHz", result.requestedMode().refreshRateMilliHz() / 1000f);
            if (result.currentMode() != null) e.observed("modeId", result.currentMode().id())
                    .observed("refreshHz", result.currentMode().refreshRateMilliHz() / 1000f);
        });
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { state(holder, "surface.created", "holder-callback"); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        log.emit("surface.changed", "SurfaceHolder.Callback", e -> e.observed("surfaceId", PlaybackDiagnosticCollector.objectId(holder.getSurface(), "surface"))
                .observed("operationId", resizeOperation).observed("holderWidth", width).observed("holderHeight", height)
                .observed("format", format).observed("valid", holder.getSurface().isValid()));
        snapshot("surface-changed");
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { state(holder, "surface.destroyed", "holder-callback"); }
    private void state(SurfaceHolder holder, String event, String reason) {
        log.emit(event, "SurfaceHolder", e -> {
            Rect rect = holder.getSurfaceFrame();
            e.observed("surfaceId", PlaybackDiagnosticCollector.objectId(holder.getSurface(), "surface"))
                    .observed("valid", holder.getSurface().isValid()).observed("holderWidth", rect.width())
                    .observed("holderHeight", rect.height()).observed("reason", reason).observed("surfaceType", "SurfaceView");
        });
    }

    public void snapshot(String reason) {
        if (!PlaybackDiagnosticCollector.enabled() || playerView == null) return;
        try {
            log.baseline();
            pixelProbe.maybeSample(playerView, trace);
            View target = surfaceView;
            if (target != null) log.emit("surface.visibility", "view-hierarchy", e -> {
                float alpha = target.getAlpha(); ViewParent parent = target.getParent(); int depth = 0;
                while (parent instanceof View view && depth++ < 32) { alpha *= view.getAlpha(); parent = view.getParent(); }
                e.observed("reason", reason).observed("width", target.getWidth()).observed("height", target.getHeight())
                        .observed("surfaceType", target.getClass().getSimpleName()).observed("attached", target.isAttachedToWindow())
                        .observed("shown", target.isShown()).observed("visibility", target.getVisibility())
                        .observed("windowVisibility", target.getWindowVisibility()).observed("alpha", target.getAlpha())
                        .observed("ancestorAlpha", alpha).observed("rotation", target.getRotation());
                if (target instanceof SurfaceView sv) e.observed("surfaceId", PlaybackDiagnosticCollector.objectId(sv.getHolder().getSurface(), "surface"));
                else e.unknown("surfaceId", NOT_OBSERVABLE);
            });
            Player player = playerView.getPlayer();
            View shutter = playerView.findViewById(androidx.media3.ui.R.id.exo_shutter);
            ImageView artwork = playerView.findViewById(androidx.media3.ui.R.id.exo_artwork);
            boolean shutterShown = shutter != null && shutter.isShown() && shutter.getAlpha() > 0;
            boolean artworkShown = artwork != null && artwork.isShown() && artwork.getDrawable() != null;
            log.emit("video.presentation.source", "PlayerView", e -> {
                e.observed("shutterVisible", shutterShown).observed("artworkVisible", artworkShown)
                        .observed("presentation", artworkShown ? "artwork" : shutterShown ? "shutter" : "video-surface-exposed")
                        .unknown("physicalDisplay", NOT_OBSERVABLE).observed("reason", reason);
                if (player != null) e.observed("videoSelected", player.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_VIDEO));
                else e.unknown("videoSelected", UNAVAILABLE);
            });
            android.view.Display display = playerView.getDisplay();
            if (display != null) log.emit("env.display", "View.getDisplay", e -> e.observed("displayId", display.getDisplayId())
                    .observed("refreshHz", display.getRefreshRate()).observed("rotation", display.getRotation()));
        } catch (RuntimeException ignored) { DebugLogStore.collectorFailure(); }
    }
}
