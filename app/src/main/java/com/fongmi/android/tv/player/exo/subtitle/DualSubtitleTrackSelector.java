package com.fongmi.android.tv.player.exo.subtitle;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.RendererConfiguration;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.trackselection.FixedTrackSelection;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelectorResult;
import androidx.media3.exoplayer.upstream.BandwidthMeter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Adds one explicit subtitle selection without changing the original renderer mapping or policy. */
public final class DualSubtitleTrackSelector extends TrackSelector {
    private final TrackSelector delegate;
    private int secondaryRendererIndex = C.INDEX_UNSET;
    private volatile Request request = new Request(null, 0);
    private volatile SelectionInfo active = SelectionInfo.EMPTY;

    public DualSubtitleTrackSelector(TrackSelector delegate) {
        this.delegate = delegate;
    }

    void setSecondaryRendererIndex(int index) {
        secondaryRendererIndex = index;
    }

    /** A request is also an identity token, so queued output cannot survive a track/source change. */
    static final class Request {
        @Nullable final TrackSelectionOverride override;
        final long mediaGeneration;

        Request(@Nullable TrackSelectionOverride override, long mediaGeneration) {
            this.override = override;
            this.mediaGeneration = mediaGeneration;
        }

        boolean accepts(@Nullable Format format) {
            return override != null && format != null
                    && override.mediaTrackGroup.getFormat(override.trackIndices.get(0)).equals(format);
        }
    }

    Request request() {
        return request;
    }

    public synchronized boolean setSecondaryOverride(@Nullable TrackSelectionOverride override) {
        if (override != null) {
            if (override.getType() != C.TRACK_TYPE_TEXT || override.trackIndices.size() != 1) {
                throw new IllegalArgumentException("A secondary subtitle must select exactly one text track");
            }
            if (active.mediaGeneration == request.mediaGeneration
                    && active.primaryGroups.contains(override.mediaTrackGroup)) {
                return false;
            }
        }
        if (Objects.equals(request.override, override)) return true;
        request = new Request(override, request.mediaGeneration);
        invalidate();
        return true;
    }

    public synchronized void reset() {
        request = new Request(null, request.mediaGeneration + 1);
        active = SelectionInfo.EMPTY;
        invalidate();
    }

    public boolean isPrimarySelected(@Nullable Format format) {
        SelectionInfo selection = active;
        return format != null && selection.mediaGeneration == request.mediaGeneration
                && selection.primaryFormats.contains(format);
    }

    public boolean isSecondarySelected(@Nullable Format format) {
        SelectionInfo selection = active;
        Request current = request;
        return format != null && selection.mediaGeneration == current.mediaGeneration
                && format.equals(selection.secondaryFormat) && current.accepts(format);
    }

    @Override
    public void init(InvalidationListener listener, BandwidthMeter bandwidthMeter) {
        super.init(listener, bandwidthMeter);
        delegate.init(listener, bandwidthMeter);
    }

    @Override
    public void release() {
        delegate.release();
        active = SelectionInfo.EMPTY;
        super.release();
    }

    @Override
    public TrackSelectionParameters getParameters() {
        return delegate.getParameters();
    }

    @Override
    public void setParameters(TrackSelectionParameters parameters) {
        delegate.setParameters(parameters);
    }

    @Override
    public boolean isSetParametersSupported() {
        return delegate.isSetParametersSupported();
    }

    @Override
    public void setAudioAttributes(AudioAttributes audioAttributes) {
        delegate.setAudioAttributes(audioAttributes);
    }

    @Nullable
    @Override
    public RendererCapabilities.Listener getRendererCapabilitiesListener() {
        return delegate.getRendererCapabilitiesListener();
    }

    @Override
    public TrackSelectorResult selectTracks(RendererCapabilities[] capabilities, TrackGroupArray groups,
                                           MediaPeriodId periodId, Timeline timeline) throws ExoPlaybackException {
        int secondaryIndex = secondaryRendererIndex;
        if (secondaryIndex < 0 || secondaryIndex != capabilities.length - 1
                || capabilities[secondaryIndex].getTrackType() != C.TRACK_TYPE_TEXT) {
            throw new IllegalStateException("The secondary TextRenderer must be appended to the original renderers");
        }
        Request requested = request;
        // The delegate sees exactly the renderer list it saw before dual subtitles were enabled.
        // In particular, no extra audio/video capability queries or new automatic policies are added.
        TrackSelectorResult primary = delegate.selectTracks(
                Arrays.copyOf(capabilities, secondaryIndex), groups, periodId, timeline);
        RendererConfiguration[] configurations = Arrays.copyOf(primary.rendererConfigurations, capabilities.length);
        ExoTrackSelection[] selections = Arrays.copyOf(primary.selections, capabilities.length);
        List<Format> primaryFormats = selectedTextFormats(primary.selections);
        List<TrackGroup> primaryGroups = selectedTextGroups(primary.selections);
        ExoTrackSelection secondary = selectSecondary(requested, groups, capabilities[secondaryIndex], primaryGroups);
        if (secondary != null) {
            configurations[secondaryIndex] = RendererConfiguration.DEFAULT;
            selections[secondaryIndex] = secondary;
        }
        return new TrackSelectorResult(configurations, selections, withSecondary(primary.tracks, secondary),
                new SelectionInfo(primary.info, primaryFormats, primaryGroups,
                        secondary == null ? null : secondary.getSelectedFormat(), requested.mediaGeneration));
    }

    @Nullable
    private static ExoTrackSelection selectSecondary(Request request, TrackGroupArray groups,
                                                     RendererCapabilities capabilities, List<TrackGroup> primaryGroups)
            throws ExoPlaybackException {
        TrackSelectionOverride override = request.override;
        if (override == null || groups.indexOf(override.mediaTrackGroup) == C.INDEX_UNSET) return null;
        int index = override.trackIndices.get(0);
        Format format = override.mediaTrackGroup.getFormat(index);
        // HLS/Progressive streams may have only one queue per group. Two variants of one
        // group are adaptive alternatives, not two independently consumable subtitles.
        if (primaryGroups.contains(override.mediaTrackGroup)
                || RendererCapabilities.getFormatSupport(capabilities.supportsFormat(format)) != C.FORMAT_HANDLED) {
            return null;
        }
        return new FixedTrackSelection(override.mediaTrackGroup, index);
    }

    private static List<Format> selectedTextFormats(ExoTrackSelection[] selections) {
        List<Format> formats = new ArrayList<>();
        for (ExoTrackSelection selection : selections) {
            if (selection == null || selection.getTrackGroup().type != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < selection.length(); i++) formats.add(selection.getFormat(i));
        }
        return Collections.unmodifiableList(formats);
    }

    private static List<TrackGroup> selectedTextGroups(ExoTrackSelection[] selections) {
        List<TrackGroup> groups = new ArrayList<>();
        for (ExoTrackSelection selection : selections) {
            if (selection != null && selection.getTrackGroup().type == C.TRACK_TYPE_TEXT) groups.add(selection.getTrackGroup());
        }
        return Collections.unmodifiableList(groups);
    }

    private static Tracks withSecondary(Tracks primary, @Nullable ExoTrackSelection secondary) {
        if (secondary == null) return primary;
        List<Tracks.Group> groups = new ArrayList<>(primary.getGroups().size());
        TrackGroup selectedGroup = secondary.getTrackGroup();
        for (Tracks.Group group : primary.getGroups()) {
            if (!group.getMediaTrackGroup().equals(selectedGroup)) {
                groups.add(group);
                continue;
            }
            int[] support = new int[group.length];
            boolean[] selected = new boolean[group.length];
            for (int i = 0; i < group.length; i++) {
                support[i] = group.getTrackSupport(i);
                selected[i] = group.isTrackSelected(i) || i == secondary.getIndexInTrackGroup(0);
            }
            groups.add(new Tracks.Group(selectedGroup, group.isAdaptiveSupported(), support, selected));
        }
        return new Tracks(groups);
    }

    @Override
    public void onSelectionActivated(@Nullable Object info) {
        if (!(info instanceof SelectionInfo selection)) {
            delegate.onSelectionActivated(info);
            active = SelectionInfo.EMPTY;
            return;
        }
        delegate.onSelectionActivated(selection.delegateInfo);
        // Selection may happen while buffering a future period. Only activation publishes roles.
        if (selection.mediaGeneration == request.mediaGeneration) active = selection;
    }

    private record SelectionInfo(@Nullable Object delegateInfo, List<Format> primaryFormats, List<TrackGroup> primaryGroups,
                                 @Nullable Format secondaryFormat, long mediaGeneration) {
        static final SelectionInfo EMPTY = new SelectionInfo(null, Collections.emptyList(), Collections.emptyList(), null, -1);
    }
}
