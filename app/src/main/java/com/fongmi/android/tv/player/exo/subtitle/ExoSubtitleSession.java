package com.fongmi.android.tv.player.exo.subtitle;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.text.TextRenderer;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;

import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.exo.TrackUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** One player's secondary subtitle state; the Activity owns only the currently attached View. */
public final class ExoSubtitleSession {
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private DualSubtitleTrackSelector selector;
    @Nullable private PlayerView host;
    @Nullable private SubtitleView view;
    private List<Cue> cues = Collections.emptyList();
    private volatile boolean released;

    public TrackSelector wrapTrackSelector(TrackSelector delegate) {
        if (selector != null) throw new IllegalStateException("Subtitle session already has a selector");
        return selector = new DualSubtitleTrackSelector(delegate);
    }

    public RenderersFactory wrapRenderersFactory(RenderersFactory factory) {
        return (eventHandler, video, audio, primaryText, metadata) -> {
            Renderer[] original = factory.createRenderers(eventHandler, video, audio, primaryText, metadata);
            if (selector == null) throw new IllegalStateException("Create the subtitle selector first");
            selector.setSecondaryRendererIndex(original.length);
            // Keep a real TextRenderer so Media3 still handles offset, final-stream and scheduling.
            // The existing observer supplies stream identity, protecting against queued old Cues.
            TextRenderer secondary = new TextRenderer(cueGroup -> { }, null);
            secondary.setObserver(new SecondaryObserver(selector));
            Renderer[] result = Arrays.copyOf(original, original.length + 1);
            result[original.length] = secondary;
            return result;
        };
    }

    public boolean isPrimarySelected(Format format) {
        return selector != null && selector.isPrimarySelected(format);
    }

    public boolean isSecondarySelected(Format format) {
        return selector != null && selector.isSecondarySelected(format);
    }

    public boolean selectSecondary(Player player, Track track) {
        if (released || selector == null || track == null) return false;
        TrackSelectionOverride override = track.isDisabled() ? null : TrackUtil.findOverride(player, track);
        if (!track.isDisabled() && override == null) return false;
        DualSubtitleTrackSelector.Request previous = selector.request();
        if (!selector.setSecondaryOverride(override)) return false;
        if (previous != selector.request()) clearCues();
        return true;
    }

    public boolean canSelectPrimary(Player player, Track track) {
        if (selector == null || track.getType() != C.TRACK_TYPE_TEXT || track.isDisabled() || !track.isSelected()) {
            return true;
        }
        TrackSelectionOverride override = TrackUtil.findOverride(player, track);
        TrackSelectionOverride secondary = selector.request().override;
        return override == null || secondary == null || !secondary.mediaTrackGroup.equals(override.mediaTrackGroup);
    }

    /** Called before replacing media, stopping or resetting track preferences. */
    public void reset() {
        if (selector != null) selector.reset();
        clearCues();
    }

    public void attach(PlayerView host) {
        if (released) return;
        if (this.host != host) {
            detach();
            this.host = host;
        }
        updateView();
    }

    public void detach() {
        if (view != null && view.getParent() instanceof ViewGroup parent) parent.removeView(view);
        view = null;
        host = null;
    }

    public void release() {
        released = true;
        handler.removeCallbacksAndMessages(null);
        cues = Collections.emptyList();
        detach();
    }

    private void clearCues() {
        cues = Collections.emptyList();
        updateView();
    }

    private void updateView() {
        if (view == null && !cues.isEmpty() && host != null) {
            SubtitleView primary = host.getSubtitleView();
            if (primary == null || !(primary.getParent() instanceof ViewGroup parent)) return;
            view = new SubtitleView(host.getContext());
            view.setStyle(ExoUtil.getCaptionStyle());
            view.setApplyEmbeddedStyles(false);
            view.setApplyEmbeddedFontSizes(false);
            view.setFocusable(false);
            view.setClickable(false);
            view.setSaveEnabled(false);
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            ViewGroup.LayoutParams params = primary.getLayoutParams() instanceof FrameLayout.LayoutParams original
                    ? new FrameLayout.LayoutParams(original) : new ViewGroup.LayoutParams(primary.getLayoutParams());
            parent.addView(view, parent.indexOfChild(primary) + 1, params);
        }
        if (view != null) view.setCues(cues);
    }

    private void publish(DualSubtitleTrackSelector source, DualSubtitleTrackSelector.Request request, CueGroup group) {
        handler.post(() -> {
            if (released || selector != source || source.request() != request) return;
            cues = SecondarySubtitleCues.atTop(group.cues);
            updateView();
        });
    }

    private final class SecondaryObserver implements TextRenderer.Observer {
        private final DualSubtitleTrackSelector source;
        @Nullable private TextRenderer.Stream reading;
        @Nullable private TextRenderer.Stream previous;
        private long readingMediaGeneration = -1;
        private long previousMediaGeneration = -1;

        SecondaryObserver(DualSubtitleTrackSelector source) {
            this.source = source;
        }

        @Override
        public void onStream(TextRenderer.Stream stream, long generation) {
            // Read-ahead can replace the input while the previous stream is still on screen.
            // Keep both identities, but never relabel an old source with a new request's epoch.
            previous = reading;
            previousMediaGeneration = readingMediaGeneration;
            reading = stream;
            readingMediaGeneration = source.request().mediaGeneration;
        }
        @Override public void onReset(TextRenderer.Stream stream, long generation, long positionUs) { }
        @Override public void onSample(TextRenderer.Stream stream, long generation, Format format, ByteBuffer data, long timeUs) { }
        @Override public void onClock(TextRenderer.Stream reading, @Nullable TextRenderer.Stream displaying,
                                      long generation, long positionUs, long textOffsetUs, boolean started, boolean ended) { }

        @Override
        public void onCues(@Nullable TextRenderer.Stream stream, long generation, CueGroup group) {
            if (released) return;
            DualSubtitleTrackSelector.Request current = source.request();
            if (current.override == null || stream == null || !current.accepts(stream.format)) return;
            long mediaGeneration = stream == reading ? readingMediaGeneration
                    : stream == previous ? previousMediaGeneration : -1;
            if (mediaGeneration != current.mediaGeneration) return;
            publish(source, current, group);
        }

        @Override
        public void onDisabled(long generation) {
            reading = previous = null;
            readingMediaGeneration = previousMediaGeneration = -1;
            if (!released) publish(source, source.request(), CueGroup.EMPTY_TIME_ZERO);
        }
    }
}
