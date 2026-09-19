package com.fongmi.android.tv.player.exo.ass;

import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;

/** Video eligibility and the color contract for the independent SDR subtitle surface. */
final class AssVideoPolicy {
    static boolean supports(Format video) {
        ColorInfo color = video.colorInfo;
        return video.width > 0 && video.height > 0 && video.rotationDegrees == 0
                && video.drmInitData == null && video.cryptoType == C.CRYPTO_TYPE_NONE
                && (color == null || color.colorTransfer == Format.NO_VALUE
                || color.colorTransfer == C.COLOR_TRANSFER_SDR
                || color.colorTransfer == C.COLOR_TRANSFER_ST2084
                || color.colorTransfer == C.COLOR_TRANSFER_HLG);
    }

    static boolean usesSdrRgb(Format video) {
        ColorInfo color = video.colorInfo;
        return MimeTypes.VIDEO_DOLBY_VISION.equals(video.sampleMimeType)
                || color != null && (ColorInfo.isTransferHdr(color)
                || color.colorSpace == C.COLOR_SPACE_BT2020);
    }

    static int colorSpace(Format video) {
        if (usesSdrRgb(video)) return AssNative.COLOR_SPACE_SDR_RGB;
        ColorInfo color = video.colorInfo;
        return color == null || color.colorSpace == Format.NO_VALUE
                ? (video.height <= 576 ? C.COLOR_SPACE_BT601 : C.COLOR_SPACE_BT709)
                : color.colorSpace;
    }

    static int colorRange(Format video) {
        if (usesSdrRgb(video)) return C.COLOR_RANGE_FULL;
        ColorInfo color = video.colorInfo;
        return color == null || color.colorRange == Format.NO_VALUE
                ? C.COLOR_RANGE_LIMITED : color.colorRange;
    }

    private AssVideoPolicy() { }
}
