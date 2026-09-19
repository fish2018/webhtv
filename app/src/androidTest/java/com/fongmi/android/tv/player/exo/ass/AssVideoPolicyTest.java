package com.fongmi.android.tv.player.exo.ass;

import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DrmInitData;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;

import junit.framework.TestCase;

public class AssVideoPolicyTest extends TestCase {
    private Format video(String mime, ColorInfo color) {
        return new Format.Builder().setSampleMimeType(mime).setWidth(3840).setHeight(2160)
                .setColorInfo(color).build();
    }

    private ColorInfo color(int space, int transfer, int range) {
        return new ColorInfo.Builder().setColorSpace(space).setColorTransfer(transfer)
                .setColorRange(range).build();
    }

    public void testHdrAndWideGamutKeepSubtitleRgb() {
        for (int transfer : new int[]{C.COLOR_TRANSFER_ST2084, C.COLOR_TRANSFER_HLG,
                C.COLOR_TRANSFER_SDR, Format.NO_VALUE}) {
            Format format = video(MimeTypes.VIDEO_H265,
                    color(C.COLOR_SPACE_BT2020, transfer, C.COLOR_RANGE_LIMITED));
            assertTrue(AssVideoPolicy.supports(format));
            assertEquals(AssNative.COLOR_SPACE_SDR_RGB, AssVideoPolicy.colorSpace(format));
            assertEquals(C.COLOR_RANGE_FULL, AssVideoPolicy.colorRange(format));
        }
        Format pq709 = video(MimeTypes.VIDEO_H265,
                color(C.COLOR_SPACE_BT709, C.COLOR_TRANSFER_ST2084, C.COLOR_RANGE_LIMITED));
        assertTrue(AssVideoPolicy.supports(pq709));
        assertEquals(AssNative.COLOR_SPACE_SDR_RGB, AssVideoPolicy.colorSpace(pq709));
    }

    public void testDolbyVisionWithoutColorMetadataIsStillSdrSubtitleRgb() {
        Format format = video(MimeTypes.VIDEO_DOLBY_VISION, null);
        assertTrue(AssVideoPolicy.supports(format));
        assertEquals(AssNative.COLOR_SPACE_SDR_RGB, AssVideoPolicy.colorSpace(format));
        assertEquals(C.COLOR_RANGE_FULL, AssVideoPolicy.colorRange(format));
    }

    public void testSdrVideoRetainsDeclaredAndFallbackMatrices() {
        Format sdr = video(MimeTypes.VIDEO_H264,
                color(C.COLOR_SPACE_BT709, C.COLOR_TRANSFER_SDR, C.COLOR_RANGE_FULL));
        assertTrue(AssVideoPolicy.supports(sdr));
        assertEquals(C.COLOR_SPACE_BT709, AssVideoPolicy.colorSpace(sdr));
        assertEquals(C.COLOR_RANGE_FULL, AssVideoPolicy.colorRange(sdr));
        Format unspecified = video(MimeTypes.VIDEO_H264, null);
        assertEquals(C.COLOR_SPACE_BT709, AssVideoPolicy.colorSpace(unspecified));
        assertEquals(C.COLOR_RANGE_LIMITED, AssVideoPolicy.colorRange(unspecified));
        assertEquals(C.COLOR_SPACE_BT601,
                AssVideoPolicy.colorSpace(unspecified.buildUpon().setHeight(576).build()));
        Format declared601 = video(MimeTypes.VIDEO_H264,
                color(C.COLOR_SPACE_BT601, C.COLOR_TRANSFER_SDR, C.COLOR_RANGE_LIMITED));
        assertEquals(C.COLOR_SPACE_BT601, AssVideoPolicy.colorSpace(declared601));
    }

    public void testSecureRotatedAndUnknownTransferStillUseCompatiblePath() {
        Format hdr = video(MimeTypes.VIDEO_H265,
                color(C.COLOR_SPACE_BT2020, C.COLOR_TRANSFER_ST2084, C.COLOR_RANGE_LIMITED));
        assertFalse(AssVideoPolicy.supports(hdr.buildUpon().setWidth(0).build()));
        assertFalse(AssVideoPolicy.supports(hdr.buildUpon().setRotationDegrees(90).build()));
        assertFalse(AssVideoPolicy.supports(hdr.buildUpon().setCryptoType(C.CRYPTO_TYPE_FRAMEWORK).build()));
        assertFalse(AssVideoPolicy.supports(hdr.buildUpon()
                .setDrmInitData(new DrmInitData(new DrmInitData.SchemeData[0])).build()));
        assertFalse(AssVideoPolicy.supports(hdr.buildUpon()
                .setColorInfo(color(C.COLOR_SPACE_BT2020, C.COLOR_TRANSFER_LINEAR, C.COLOR_RANGE_FULL)).build()));
    }
}
