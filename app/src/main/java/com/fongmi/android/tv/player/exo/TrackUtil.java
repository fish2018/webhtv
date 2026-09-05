package com.fongmi.android.tv.player.exo;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
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

    /**
     * 自适应选轨会把同一组里的多条轨道同时标记为已选中，轨道列表因此会高亮出多项，用户看不出
     * 当前到底在播哪一条。这里用播放器正在解码的格式把范围收敛到一条；匹配不上时返回 null，
     * 让调用方退回原始的选中标记，而不是让列表一项都不高亮。
     */
    public static Format uniqueActiveFormat(Tracks tracks, int type, Format active) {
        if (tracks == null || tracks.isEmpty() || active == null) return null;
        List<Format> selected = selectedFormats(tracks, type);
        if (selected.size() < 2) return null;
        for (Format format : selected) if (sameRendition(format, active)) return format;
        return null;
    }

    static List<Format> selectedFormats(Tracks tracks, int type) {
        List<Format> selected = new ArrayList<>();
        if (tracks == null) return selected;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != type) continue;
            for (int i = 0; i < group.length; i++) {
                if (group.isTrackSelected(i)) selected.add(group.getTrackFormat(i));
            }
        }
        return selected;
    }

    static boolean sameRendition(Format candidate, Format active) {
        if (candidate == null || active == null) return false;
        if (candidate.equals(active)) return true;
        if (candidate.id != null && candidate.id.equals(active.id)) return true;
        if (candidate.width != active.width || candidate.height != active.height) return false;
        int candidateBitrate = getBitrate(candidate);
        int activeBitrate = getBitrate(active);
        boolean bitrateComparable = candidateBitrate > 0 && activeBitrate > 0;
        if (bitrateComparable && candidateBitrate != activeBitrate) return false;
        // 同一分辨率可能同时提供 H.264 和 HEVC 两条轨道，编码能比就必须一致。
        Boolean codecMatch = codecMatch(candidate, active);
        if (codecMatch != null) return codecMatch;
        // 编码无从比较时只能靠码率区分；两边码率都未知就判定不匹配，
        // 让调用方退回播放器的选中标记，而不是高亮到错的一条。
        return bitrateComparable;
    }

    /** 返回编码是否一致，两边信息不足以比较时返回 null。 */
    private static Boolean codecMatch(Format candidate, Format active) {
        if (candidate.sampleMimeType != null && active.sampleMimeType != null) {
            return candidate.sampleMimeType.equals(active.sampleMimeType);
        }
        if (candidate.codecs != null && active.codecs != null) {
            return candidate.codecs.equals(active.codecs);
        }
        return null;
    }

    static Format onlySelectedFormat(List<Format> selected) {
        return selected != null && selected.size() == 1 ? selected.get(0) : null;
    }

    public static void reset(Player player) {
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverrides().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build());
    }

public static void reset(Player player, int type) {
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type, false).build());
    }

    public static boolean preferAAC(Player player) {
        TrackInfo info = findAAC(player);
        if (info == null) return false;
        if (info.trackGroup.isTrackSelected(info.trackIndex)) return false;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setOverrideForType(new TrackSelectionOverride(info.trackGroup.getMediaTrackGroup(), List.of(info.trackIndex))).build());
        return true;
    }

    public static boolean hasTrack(Player player, List<Track> tracks, int type) {
        for (Track track : tracks) {
            if (track.getType() == type && find(player, track) != null) return true;
        }
        return false;
    }

    public static void enable(Player player, int type) {
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(type, false).build());
    }

    private static TrackInfo find(Player player, Track track) {
        if (track.getFormat() == null) return null;
        Tracks currentTracks = player.getCurrentTracks();
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

    private static TrackInfo findAAC(Player player) {
        TrackInfo best = null;
        for (Tracks.Group trackGroup : player.getCurrentTracks().getGroups()) {
            if (trackGroup.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < trackGroup.length; i++) {
                Format format = trackGroup.getTrackFormat(i);
                if (!trackGroup.isTrackSupported(i) || !isAAC(format)) continue;
                TrackInfo info = new TrackInfo(trackGroup, i);
                if (best == null || getBitrate(format) > getBitrate(best.trackGroup.getTrackFormat(best.trackIndex))) best = info;
            }
        }
        return best;
    }

    private static boolean isAAC(Format format) {
        String codecs = format.codecs == null ? "" : format.codecs.toLowerCase();
        return MimeTypes.AUDIO_AAC.equals(format.sampleMimeType) || codecs.contains("mp4a") || codecs.contains("aac");
    }

    private static int getBitrate(Format format) {
        return Math.max(format.averageBitrate, format.peakBitrate);
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
