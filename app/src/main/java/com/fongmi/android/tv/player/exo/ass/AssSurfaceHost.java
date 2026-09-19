package com.fongmi.android.tv.player.exo.ass;

import android.graphics.PixelFormat;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;

/** Main-thread display arbiter. Only the duplicate ASS SubtitleView is hidden. */
final class AssSurfaceHost implements SurfaceHolder.Callback, View.OnLayoutChangeListener {
    private final ExoAssSession session;
    private final PlayerView view;
    private final SubtitleView compatible;
    private SurfaceView video;
    private SurfaceView overlay;
    private boolean hidingCompatible;
    private int compatibleVisibility;

    AssSurfaceHost(ExoAssSession session, PlayerView view) {
        this.session = session;
        this.view = view;
        this.compatible = view.getSubtitleView();
    }

    PlayerView view() { return view; }

    void update(boolean eligible, boolean active) {
        View current = view.getVideoSurfaceView();
        if (!eligible || !(current instanceof SurfaceView) || !(current.getParent() instanceof FrameLayout)) {
            removeSurface();
            return;
        }
        if (video != current) {
            removeSurface();
            video = (SurfaceView) current;
            FrameLayout parent = (FrameLayout) video.getParent();
            overlay = new SurfaceView(view.getContext());
            overlay.setZOrderMediaOverlay(true);
            overlay.getHolder().setFormat(PixelFormat.TRANSLUCENT);
            overlay.getHolder().addCallback(this);
            overlay.setFocusable(false);
            overlay.setClickable(false);
            overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            overlay.setAlpha(0f);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(video.getLayoutParams());
            if (video.getLayoutParams() instanceof FrameLayout.LayoutParams original) params.gravity = original.gravity;
            parent.addView(overlay, parent.indexOfChild(video) + 1, params);
            video.addOnLayoutChangeListener(this);
        }
        if (active && overlay != null && overlay.getHolder().getSurface().isValid()) {
            if (!hidingCompatible && compatible != null) {
                compatibleVisibility = compatible.getVisibility();
                hidingCompatible = true;
                compatible.setVisibility(View.INVISIBLE);
            }
            overlay.setAlpha(1f);
        } else {
            showCompatible();
        }
    }

    private void showCompatible() {
        if (overlay != null) overlay.setAlpha(0f);
        if (hidingCompatible && compatible != null) compatible.setVisibility(compatibleVisibility);
        hidingCompatible = false;
    }

    private void removeSurface() {
        showCompatible();
        if (video != null) video.removeOnLayoutChangeListener(this);
        video = null;
        if (overlay == null) return;
        session.setSurface(this, null, 0, 0, true);
        overlay.getHolder().removeCallback(this);
        if (overlay.getParent() instanceof ViewGroup parent) parent.removeView(overlay);
        overlay = null;
    }

    void release() { removeSurface(); }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        if (overlay != null && holder == overlay.getHolder())
            session.setSurface(this, holder.getSurface(), overlay.getWidth(), overlay.getHeight(), true);
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        if (overlay != null && holder == overlay.getHolder())
            session.setSurface(this, holder.getSurface(), width, height, false);
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        if (overlay == null || holder != overlay.getHolder()) return;
        showCompatible();
        session.setSurface(this, null, 0, 0, true);
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom,
                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
        if (overlay != null && overlay.getHolder().getSurface().isValid()) {
            // The overlay follows View layout. The video's fixed buffer dimensions are unrelated.
            session.setSurface(this, overlay.getHolder().getSurface(), overlay.getWidth(), overlay.getHeight(), false);
        }
    }
}
