package com.fongmi.android.tv.player.lut;

import android.text.TextUtils;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Tracks;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.engine.MpvPlayerEngine;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.Locale;

public class LutEligibility {

    public static String getUnavailableReason(PlayerEngine engine, PlaySpec spec) {
        return getUnavailableReason(engine, spec, false);
    }

    public static String getUnavailableReason(PlayerEngine engine, PlaySpec spec,
                                              boolean allowMpvSurfaceDirect) {
        if (!allowMpvSurfaceDirect
                && engine instanceof MpvPlayerEngine mpv && mpv.isSurfaceDirect()) {
            return ResUtil.getString(R.string.lut_unavailable_output_mode);
        }
        if (engine == null || !engine.supportsLut()) return ResUtil.getString(R.string.lut_unavailable_player);
        if (spec != null && spec.getDrm() != null) return ResUtil.getString(R.string.lut_unavailable_drm);
        if (!engine.supportsNativeLut() && PlayerSetting.isTunnel()) return ResUtil.getString(R.string.lut_unavailable_tunnel);
        if (!engine.supportsNativeLut() && engine.getDecode() == PlayerEngine.SOFT) return ResUtil.getString(R.string.lut_unavailable_soft_decode);
        if (!engine.supportsNativeLut() && PlayerSetting.isVideoPrefer(PlayerSetting.getActivePlayer())) return ResUtil.getString(R.string.lut_unavailable_video_prefer);
        if (isHdr(engine.getVideoFormat()) || isHdr(engine.getCurrentTracks())) return ResUtil.getString(R.string.lut_unavailable_hdr);
        if (isKnownAudioOnly(engine.getCurrentTracks())) return ResUtil.getString(R.string.lut_unavailable_no_video);
        return null;
    }

    public static boolean isAvailable(PlayerEngine engine, PlaySpec spec) {
        return TextUtils.isEmpty(getUnavailableReason(engine, spec));
    }

    private static boolean isHdr(Format format) {
        if (format == null) return false;
        if (MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType)) return true;
        String codecs = format.codecs == null ? "" : format.codecs.toLowerCase(Locale.ROOT);
        if (codecs.contains("dvhe") || codecs.contains("dvh1")) return true;
        if (isLikelyTenBitCodec(codecs)) return true;
        if (format.colorInfo == null) return false;
        if (format.colorInfo.hdrStaticInfo != null && format.colorInfo.hdrStaticInfo.length > 0) return true;
        if (format.colorInfo.colorSpace == C.COLOR_SPACE_BT2020) return true;
        if (format.colorInfo.lumaBitdepth > 8 || format.colorInfo.chromaBitdepth > 8) return true;
        return androidx.media3.common.ColorInfo.isTransferHdr(format.colorInfo);
    }

    private static boolean isLikelyTenBitCodec(String codecs) {
        if (TextUtils.isEmpty(codecs)) return false;
        if (codecs.contains("hvc1.2") || codecs.contains("hev1.2")) return true;
        if (codecs.contains("vp09.02")) return true;
        if (codecs.contains("av01.") && (codecs.contains(".10") || codecs.contains(".12"))) return true;
        if (codecs.contains("avc1.6e")) return true;
        return false;
    }

    private static boolean isHdr(Tracks tracks) {
        if (tracks == null || tracks.isEmpty()) return false;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < group.length; i++) {
                if (isHdr(group.getTrackFormat(i))) return true;
            }
        }
        return false;
    }

    private static boolean isKnownAudioOnly(Tracks tracks) {
        if (tracks == null || tracks.isEmpty()) return false;
        for (Tracks.Group group : tracks.getGroups()) if (group.getType() == C.TRACK_TYPE_VIDEO) return false;
        return true;
    }
}
