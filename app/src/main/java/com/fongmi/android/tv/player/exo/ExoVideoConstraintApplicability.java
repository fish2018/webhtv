package com.fongmi.android.tv.player.exo;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;

import java.util.ArrayList;
import java.util.List;

/** Runtime limits only help when the current content offers selectable alternative formats. */
final class ExoVideoConstraintApplicability {
    private ExoVideoConstraintApplicability() {}

    static boolean canAdjust(Tracks tracks, TrackSelectionParameters parameters) {
        if (tracks == null || parameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO)) return false;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            boolean manual = parameters.overrides.containsKey(group.getMediaTrackGroup());
            if (!group.isSelected() || !group.isAdaptiveSupported() || manual) continue;
            List<Format> supported = new ArrayList<>(group.length);
            for (int i = 0; i < group.length; i++) {
                if (group.isTrackSupported(i)) supported.add(group.getTrackFormat(i));
            }
            if (canAdjust(group.isSelected(), group.isAdaptiveSupported(), manual, false, supported)) return true;
        }
        return false;
    }

    static boolean canAdjust(boolean selected, boolean adaptive, boolean manual,
                             boolean disabled, List<Format> supportedFormats) {
        if (!selected || !adaptive || manual || disabled) return false;
        int width = 0, height = 0, bitrate = 0;
        float frameRate = 0;
        for (Format candidate : supportedFormats) {
            if (candidate.width > 0 && candidate.height > 0) {
                if (width > 0 && (width != candidate.width || height != candidate.height)) return true;
                width = candidate.width;
                height = candidate.height;
            }
            if (candidate.frameRate > 0 && Float.isFinite(candidate.frameRate)) {
                if (frameRate > 0 && frameRate != candidate.frameRate) return true;
                frameRate = candidate.frameRate;
            }
            int candidateBitrate = ExoPlaybackDiagnostics.trackConstraintBitrate(candidate);
            if (candidateBitrate > 0) {
                if (bitrate > 0 && bitrate != candidateBitrate) return true;
                bitrate = candidateBitrate;
            }
        }
        return false;
    }
}
