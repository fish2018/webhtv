package com.fongmi.android.tv.player.exo;

import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.C;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.PlayerHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrackUtil {

    public static int count(Tracks tracks, int type) {
        return tracks.getGroups().stream().filter(trackGroup -> trackGroup.getType() == type).mapToInt(trackGroup -> trackGroup.length).sum();
    }

    public static Format selectedFormat(Tracks tracks, int type) {
        if (tracks == null || tracks.isEmpty()) return null;
        Format first = null;
        Format supported = null;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != type) continue;
            for (int i = 0; i < group.length; i++) {
                Format format = group.getTrackFormat(i);
                if (first == null) first = format;
                if (supported == null && group.isTrackSupported(i)) supported = format;
                if (group.isTrackSelected(i)) return format;
            }
        }
        return supported != null ? supported : first;
    }

    public static Format explicitlySelectedFormat(Tracks tracks, int type) {
        if (tracks == null || tracks.isEmpty()) return null;
        List<Format> selected = new ArrayList<>();
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != type) continue;
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSelected(i)) continue;
                selected.add(group.getTrackFormat(i));
                if (selected.size() > 1) return null;
            }
        }
        return onlySelectedFormat(selected);
    }

    static Format onlySelectedFormat(List<Format> selected) {
        return selected != null && selected.size() == 1 ? selected.get(0) : null;
    }

    public static void reset(Player player) {
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverrides().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build());
    }

    public static void enable(Player player, int type) {
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(type, false).build());
    }

    private static TrackInfo find(Player player, Track track) {
        Tracks currentTracks = player.getCurrentTracks();
        if (track.getPlayerId() != null) {
            for (Tracks.Group group : currentTracks.getGroups()) {
                if (group.getType() != track.getType()) continue;
                for (int i = 0; i < group.length; i++) {
                    if (track.getPlayerId().equals(group.getTrackFormat(i).id)) return new TrackInfo(group, i);
                }
            }
        }
        if (track.getFormat() == null) return null;
        for (Tracks.Group trackGroup : currentTracks.getGroups()) {
            if (trackGroup.getType() != track.getType()) continue;
            for (int i = 0; i < trackGroup.length; i++) {
                Format format = trackGroup.getTrackFormat(i);
                if (track.getFormat().equals(PlayerHelper.describeFormat(format))) {
                    return new TrackInfo(trackGroup, i);
                }
            }
        }
        return null;
    }

    /** Resolves a current runtime id first, falling back to a persisted format preference. */
    public static TrackSelectionOverride findOverride(Player player, Track track) {
        TrackInfo info = find(player, track);
        return info == null ? null : new TrackSelectionOverride(info.trackGroup.getMediaTrackGroup(), info.trackIndex);
    }

    public static void setTrackSelection(Player player, List<Track> tracks) {
        Map<Integer, TrackGroup> mediaGroupMapByType = new HashMap<>();
        Map<Integer, Integer> selectedIndexMapByType = new HashMap<>();
        for (Track track : tracks) {
            if (track.isDisabled()) {
                mediaGroupMapByType.put(track.getType(), null);
                continue;
            }
            TrackInfo info = find(player, track);
            if (info == null) continue;
            int type = info.trackGroup.getType();
            mediaGroupMapByType.put(type, info.trackGroup.getMediaTrackGroup());
            if (track.isSelected()) selectedIndexMapByType.put(type, info.trackIndex);
        }
        TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon();
        if (builder instanceof DefaultTrackSelector.Parameters.Builder exoBuilder) {
            exoBuilder.setExceedRendererCapabilitiesIfNecessary(true);
            exoBuilder.setExceedVideoConstraintsIfNecessary(true);
            exoBuilder.setExceedAudioConstraintsIfNecessary(true);
        }
        mediaGroupMapByType.forEach((type, mediaGroup) -> {
            builder.setTrackTypeDisabled(type, mediaGroup == null);
            if (mediaGroup == null) return;
            Integer selectedIndex = selectedIndexMapByType.get(type);
            List<Integer> indices = selectedIndex != null ? List.of(selectedIndex) : List.of();
            builder.setOverrideForType(new TrackSelectionOverride(mediaGroup, indices));
        });
        player.setTrackSelectionParameters(builder.build());
    }

    private record TrackInfo(Tracks.Group trackGroup, int trackIndex) {
    }
}
