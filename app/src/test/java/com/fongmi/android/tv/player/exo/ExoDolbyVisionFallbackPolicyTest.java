package com.fongmi.android.tv.player.exo;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExoDolbyVisionFallbackPolicyTest {

    @Test
    public void dv7FallbackFollowsTheUserSwitch() {
        Format dv7 = dolbyVision("dvhe.07.06");

        assertTrue(ExoUtil.shouldUseDolbyVisionHdr10Fallback(dv7, true));
        assertFalse(ExoUtil.shouldUseDolbyVisionHdr10Fallback(dv7, false));
    }

    @Test
    public void dv5FallbackRemainsAvailable() {
        Format dv5 = dolbyVision("dvhe.05.06");

        assertTrue(ExoUtil.shouldUseDolbyVisionHdr10Fallback(dv5, true));
        assertTrue(ExoUtil.shouldUseDolbyVisionHdr10Fallback(dv5, false));
    }

    private static Format dolbyVision(String codecs) {
        return new Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                .setCodecs(codecs)
                .build();
    }
}
