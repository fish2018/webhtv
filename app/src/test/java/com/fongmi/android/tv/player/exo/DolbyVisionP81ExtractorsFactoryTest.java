package com.fongmi.android.tv.player.exo;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DolbyVisionP81ExtractorsFactoryTest {

    @Test
    public void rewritesOnlyProfile7Codec() {
        assertEquals("dvhe.08.06", DolbyVisionP81ExtractorsFactory
                .rewriteProfile81("dvhe.07.06"));
        assertEquals("dvh1.08.09", DolbyVisionP81ExtractorsFactory
                .rewriteProfile81("dvh1.07.09"));
        assertEquals("dvhe.05.06", DolbyVisionP81ExtractorsFactory
                .rewriteProfile81("dvhe.05.06"));
    }

    @Test
    public void recognizesOnlyDolbyVisionProfile7() {
        assertTrue(DolbyVisionP81ExtractorsFactory.isProfile7(format("dvhe.07.06")));
        assertTrue(DolbyVisionP81ExtractorsFactory.isProfile7(format("dvh1.07.06")));
        assertFalse(DolbyVisionP81ExtractorsFactory.isProfile7(format("dvhe.08.06")));
        assertFalse(DolbyVisionP81ExtractorsFactory.isProfile7(
                new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265)
                        .setCodecs("dvhe.07.06").build()));
    }

    @Test
    public void choosesNativeBeforeConversionOrFallback() {
        assertEquals(DolbyVisionP81ExtractorsFactory.PlaybackPath.NATIVE,
                DolbyVisionP81ExtractorsFactory.resolvePlaybackPath(true, true, true));
    }

    @Test
    public void choosesP81BeforeHdr10Fallback() {
        assertEquals(DolbyVisionP81ExtractorsFactory.PlaybackPath.P81,
                DolbyVisionP81ExtractorsFactory.resolvePlaybackPath(false, true, true));
    }

    @Test
    public void choosesHdr10WhenDv7AndP81AreUnavailable() {
        assertEquals(DolbyVisionP81ExtractorsFactory.PlaybackPath.HDR10,
                DolbyVisionP81ExtractorsFactory.resolvePlaybackPath(false, false, true));
        assertEquals(DolbyVisionP81ExtractorsFactory.PlaybackPath.UNSUPPORTED,
                DolbyVisionP81ExtractorsFactory.resolvePlaybackPath(false, false, false));
    }

    @Test
    public void transformsAccessUnitsOnlyForP81() {
        assertTrue(DolbyVisionP81ExtractorsFactory.requiresAccessUnitTransformation(
                DolbyVisionP81ExtractorsFactory.PlaybackPath.P81));
        assertFalse(DolbyVisionP81ExtractorsFactory.requiresAccessUnitTransformation(
                DolbyVisionP81ExtractorsFactory.PlaybackPath.HDR10));
    }

    @Test
    public void rewritesProfile81CodecAndCsdTogether() {
        Format output = DolbyVisionP81ExtractorsFactory.asProfile81(
                formatWithInitializationData("dvhe.07.06", List.of(new byte[]{1})));

        assertEquals("dvhe.08.06", output.codecs);
        assertEquals(1, output.initializationData.size());
        assertArrayEquals(new byte[]{1}, output.initializationData.get(0));
    }

    @Test
    public void preservesNonDolbyVisionCsdAtIndexTwo() {
        byte[] otherCsd = {9, 8, 7};
        List<byte[]> rewritten = DolbyVisionP81ExtractorsFactory.rewriteDolbyVisionCsd(
                Arrays.asList(new byte[]{1}, new byte[]{2}, otherCsd),
                new byte[]{1, 0, 16, 52, 16});

        assertEquals(3, rewritten.size());
        assertArrayEquals(otherCsd, rewritten.get(2));
    }

    @Test
    public void replacesExistingDolbyVisionCsdWithoutSynthesizingMissingEntries() {
        byte[] oldCsd = {1, 0, 14, 52, 0};
        byte[] newCsd = {1, 0, 16, 52, 16};
        List<byte[]> replaced = DolbyVisionP81ExtractorsFactory.rewriteDolbyVisionCsd(
                List.of(new byte[]{1}, new byte[]{2}, oldCsd), newCsd);
        List<byte[]> unchanged = DolbyVisionP81ExtractorsFactory.rewriteDolbyVisionCsd(
                null, newCsd);

        assertEquals(3, replaced.size());
        assertArrayEquals(newCsd, replaced.get(2));
        assertTrue(unchanged.isEmpty());
    }

    @Test
    public void hdr10FallbackUsesHevcAndRemovesDolbyVisionCsd() {
        Format source = formatWithInitializationData("dvhe.07.06", List.of(
                new byte[]{0, 0, 0, 1, 1},
                new byte[]{0, 0, 0, 1, 2},
                new byte[]{1, 0, 14, 52, 0}));

        Format output = DolbyVisionP81ExtractorsFactory.asHdr10Fallback(source);

        assertEquals(MimeTypes.VIDEO_H265, output.sampleMimeType);
        assertEquals(null, output.codecs);
        assertEquals(2, output.initializationData.size());
        assertArrayEquals(source.initializationData.get(0), output.initializationData.get(0));
        assertArrayEquals(source.initializationData.get(1), output.initializationData.get(1));
    }

    @Test
    public void runtimeFallbackRequestSurvivesAttemptResetButNotFullReset() {
        ExoDolbyVisionPlaybackState state = new ExoDolbyVisionPlaybackState();
        state.requestHdr10Fallback();

        state.resetAttempt();
        assertTrue(state.isHdr10FallbackRequested());

        state.reset();
        assertFalse(state.isHdr10FallbackRequested());
    }

    @Test
    public void p81ConversionEvidenceSurvivesAttemptResetButNotFullReset() {
        ExoDolbyVisionPlaybackState state = new ExoDolbyVisionPlaybackState();
        state.activate(format("dvhe.07.06"),
                DolbyVisionP81ExtractorsFactory.asHdr10Fallback(
                        format("dvhe.07.06")));
        assertFalse(state.isP81ConversionAttempted());

        state.activateP81(format("dvhe.07.06"), format("dvhe.08.06"));

        state.resetAttempt();
        assertTrue(state.isP81ConversionAttempted());

        state.reset();
        assertFalse(state.isP81ConversionAttempted());
    }

    @Test
    public void doesNotModifyNonProfile7Format() {
        byte[] csd = {1, 2, 3};
        Format source = formatWithInitializationData("dvhe.08.06", List.of(csd));
        Format output = DolbyVisionP81ExtractorsFactory.asProfile81(source);

        assertEquals(source, output);
        assertArrayEquals(csd, output.initializationData.get(0));
    }

    @Test
    public void stripsEnhancementLayerNalusAndKeepsBaseAndRpu() {
        byte[] sample = {
                0, 0, 0, 1, 0x26, 0x01, 0x11,
                0, 0, 1, 0x7E, 0x01, 0x22,
                0, 0, 0, 1, 0x7C, 0x01, 0x33
        };

        int length = DolbyVisionP81ExtractorsFactory
                .stripEnhancementLayerNalus(sample, sample.length);

        assertEquals(14, length);
        assertEquals(0x26, sample[4]);
        assertEquals(0x7C, sample[11]);
    }

    @Test
    public void stripsLateHdr10PlusMetadataFromProfile81() {
        byte[] sample = {
                0, 0, 0, 1, 0x26, 0x01, 0x11,
                0, 0, 1, 0x7C, 0x01, 0x22,
                0, 0, 0, 1, 0x4E, 0x01, (byte) 0xB5, 0x00, 0x3C, 0x00, 0x01, 0x04,
                0x01,
                0, 0, 0, 1, 0x4E, 0x01, (byte) 0x99
        };

        int length = DolbyVisionP81ExtractorsFactory
                .stripProfile81Nalus(sample, sample.length);

        assertEquals(7 + 6 + 7, length);
        assertEquals(0x7C, sample[7 + 3]);
        assertEquals(0x4E, sample[7 + 6 + 4]);
        assertEquals((byte) 0x99, sample[length - 1]);
    }

    @Test
    public void stripsDolbyVisionRpuAndHdr10PlusForHdr10Fallback() {
        byte[] sample = {
                0, 0, 0, 1, 0x26, 0x01, 0x11,
                0, 0, 1, 0x7C, 0x01, 0x22,
                0, 0, 0, 1, 0x4E, 0x01, (byte) 0xB5, 0x00, 0x3C, 0x00, 0x01, 0x04,
                0x01,
                0, 0, 0, 1, 0x4E, 0x01, (byte) 0x99
        };

        int length = DolbyVisionP81ExtractorsFactory
                .stripDolbyVisionNalus(sample, sample.length);

        assertEquals(7 + 7, length);
        assertEquals(0x26, sample[4]);
        assertEquals(0x4E, sample[7 + 4]);
        assertEquals((byte) 0x99, sample[7 + 7 - 1]);
    }

    @Test
    public void keepsOnlyLastProfile81RpuPerAccessUnit() {
        byte[] sample = {
                0, 0, 0, 1, 0x26, 0x01, 0x11,
                0, 0, 1, 0x7C, 0x01, 0x22,
                0, 0, 0, 1, 0x7C, 0x01, 0x33
        };

        int length = DolbyVisionP81ExtractorsFactory
                .stripProfile81Nalus(sample, sample.length);

        assertEquals(7 + 7, length);
        assertEquals(0x26, sample[4]);
        assertEquals(0x7C, sample[7 + 4]);
        assertEquals(0x33, sample[length - 1]);
    }

    @Test
    public void detectsDecodedPictureAccessUnits() {
        byte[] picture = {0, 0, 0, 1, 0x26, 0x01, 0x11};
        byte[] metadataOnly = {0, 0, 0, 1, 0x7C, 0x01, 0x22};

        assertTrue(DolbyVisionP81ExtractorsFactory.containsVclNal(
                picture, picture.length));
        assertFalse(DolbyVisionP81ExtractorsFactory.containsVclNal(
                metadataOnly, metadataOnly.length));
    }

    private static Format format(String codecs) {
        return new Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                .setCodecs(codecs)
                .build();
    }

    private static Format formatWithInitializationData(
            String codecs, List<byte[]> initializationData) {
        return new Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                .setCodecs(codecs)
                .setInitializationData(new ArrayList<>(initializationData))
                .build();
    }
}
