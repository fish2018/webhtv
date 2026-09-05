package com.fongmi.android.tv.player;

import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;

import com.fongmi.android.tv.player.engine.PlayerEngine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PlaybackMediaFactsMapperTest {

    @Test
    public void mapsDeclaredTrackFieldsWithoutInventingMissingAverageOrPeak() {
        Format format = new Format.Builder()
                .setSampleMimeType("video/avc")
                .setCodecs("avc1.640028")
                .setWidth(3840)
                .setHeight(2160)
                .setFrameRate(23.976f)
                .setPeakBitrate(42_000_000)
                .build();

        PlaybackAutoContext.TrackFacts facts = PlaybackMediaFactsMapper.mapTrack(format, 100);

        assertEquals("video/avc", facts.mimeType().value());
        assertEquals("avc1.640028", facts.codecs().value());
        assertEquals(Integer.valueOf(100), facts.profile().value());
        assertEquals(Integer.valueOf(40), facts.level().value());
        assertEquals(Integer.valueOf(3840), facts.width().value());
        assertEquals(Integer.valueOf(2160), facts.height().value());
        assertEquals(23.976f, facts.frameRate().value(), 0.001f);
        assertFalse(facts.averageBitrateBitsPerSecond().hasValue());
        assertEquals(Long.valueOf(42_000_000), facts.peakBitrateBitsPerSecond().value());
    }

    @Test
    public void parsesHevcAv1AndDolbyVisionProfileLevelConservatively() {
        PlaybackAutoContext.TrackFacts hevc = PlaybackMediaFactsMapper.mapTrack(new Format.Builder()
                .setSampleMimeType("video/hevc").setCodecs("hvc1.2.4.L153.B0").build(), 10);
        PlaybackAutoContext.TrackFacts av1 = PlaybackMediaFactsMapper.mapTrack(new Format.Builder()
                .setSampleMimeType("video/av01").setCodecs("av01.0.08M.10").build(), 11);
        PlaybackAutoContext.TrackFacts dolby = PlaybackMediaFactsMapper.mapTrack(new Format.Builder()
                .setSampleMimeType("video/dolby-vision").setCodecs("dvhe.05.06").build(), 12);

        assertEquals(Integer.valueOf(2), hevc.profile().value());
        assertEquals(Integer.valueOf(153), hevc.level().value());
        assertEquals(Integer.valueOf(0), av1.profile().value());
        assertEquals(Integer.valueOf(8), av1.level().value());
        assertEquals(Integer.valueOf(5), dolby.profile().value());
        assertEquals(Integer.valueOf(6), dolby.level().value());
        assertEquals(PlaybackAutoContext.HdrType.DOLBY_VISION, dolby.hdrType().value());
        assertEquals(PlaybackAutoContext.ValueSource.CODEC_STRING, dolby.hdrType().source());
    }

    @Test
    public void mapsHdrColorAndKeepsUnknownWhenEvidenceIsMissing() {
        ColorInfo pq = new ColorInfo.Builder()
                .setColorSpace(C.COLOR_SPACE_BT2020)
                .setColorRange(C.COLOR_RANGE_LIMITED)
                .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                .build();
        PlaybackAutoContext.TrackFacts hdr = PlaybackMediaFactsMapper.mapTrack(
                new Format.Builder().setSampleMimeType("video/hevc").setColorInfo(pq).build(), 20);
        PlaybackAutoContext.TrackFacts unknown = PlaybackMediaFactsMapper.mapTrack(
                new Format.Builder().setSampleMimeType("video/hevc").build(), 21);

        assertEquals(PlaybackAutoContext.HdrType.HDR10, hdr.hdrType().value());
        assertTrue(hdr.color().value().hasEvidence());
        assertEquals(C.COLOR_TRANSFER_ST2084, hdr.color().value().colorTransfer());
        assertFalse(unknown.hdrType().hasValue());
        assertFalse(unknown.color().hasValue());
    }

    @Test
    public void decoderFactsRequireTheObservedDecoderFormatToMatchTheSelectedTrack() {
        Format selected = video("video/avc", "avc1.640028", 1920, 1080);
        Format stale = video("video/hevc", "hvc1.2.4.L153", 3840, 2160);
        PlayerEngine.PlaybackFactsSnapshot snapshot = new PlayerEngine.PlaybackFactsSnapshot(
                selected, null, stale, null, "c2.vendor.avc.decoder", "",
                PlayerEngine.DecoderKind.HARDWARE, null, "", "", false);

        PlaybackMediaFactsMapper.MappedEngineFacts mapped = PlaybackMediaFactsMapper.map(snapshot, 3, true, 30);

        assertTrue(mapped.rawDecoderEvidence());
        assertFalse(mapped.decoderSynchronizedWithTracks());
        assertFalse(mapped.decoder().hasEvidence());
        assertEquals(3, mapped.decoder().trackSequence());
    }

    @Test
    public void mapsActualNativeDecoderAndOutputWithoutUsingRequestedFallbacks() {
        Format selected = video("video/hevc", "hvc1.2.4.L153", 3840, 2160);
        PlayerEngine.PlaybackFactsSnapshot snapshot = new PlayerEngine.PlaybackFactsSnapshot(
                selected, null, selected, null, "OMX.vendor.hevc.decoder.secure", "",
                PlayerEngine.DecoderKind.HARDWARE, null,
                "mediacodec-copy", "gpu-next", null);

        PlaybackMediaFactsMapper.MappedEngineFacts mapped = PlaybackMediaFactsMapper.map(snapshot, 4, true, 40);

        assertEquals(PlaybackAutoContext.DecodeMode.HARDWARE, mapped.decoder().videoDecodeMode().value());
        assertTrue(mapped.decoder().secureVideoDecoder().value());
        assertEquals(PlaybackAutoContext.Confidence.MEDIUM, mapped.decoder().secureVideoDecoder().confidence());
        assertEquals("mediacodec-copy", mapped.output().hwdecCurrent().value());
        assertEquals("gpu-next", mapped.output().currentVideoOutput().value());
        assertEquals(PlaybackAutoContext.RenderPath.MPV_GPU, mapped.output().renderPath().value());
    }

    @Test
    public void marksConfiguredTunnelingAsRequestedEvidence() {
        PlayerEngine.PlaybackFactsSnapshot snapshot = new PlayerEngine.PlaybackFactsSnapshot(
                null, null, null, null, "", "",
                PlayerEngine.DecoderKind.UNKNOWN, null, "", "", true);

        PlaybackMediaFactsMapper.MappedEngineFacts mapped = PlaybackMediaFactsMapper.map(snapshot, 5, true, 45);

        assertEquals(PlaybackAutoContext.RenderPath.EXO_TUNNELING, mapped.output().renderPath().value());
        assertEquals(PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST, mapped.output().renderPath().source());
        assertEquals(PlaybackAutoContext.Confidence.MEDIUM, mapped.output().renderPath().confidence());
        assertEquals(PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST, mapped.output().tunneling().source());
        assertEquals(PlaybackAutoContext.Confidence.MEDIUM, mapped.output().tunneling().confidence());
    }

    @Test
    public void missingDecoderEvidenceDoesNotForceAReplacement() {
        Format selected = video("video/avc", "avc1.640028", 1920, 1080);
        PlayerEngine.PlaybackFactsSnapshot snapshot = new PlayerEngine.PlaybackFactsSnapshot(
                selected, null, selected, null, "", "",
                PlayerEngine.DecoderKind.UNKNOWN, null, "", "", null);

        PlaybackMediaFactsMapper.MappedEngineFacts mapped = PlaybackMediaFactsMapper.map(snapshot, 5, true, 50);

        assertFalse(mapped.rawDecoderEvidence());
        assertNull(mapped.decoder());
    }

    @Test
    public void rejectsPathLikeRuntimeLabelsButKeepsMimeTypes() {
        assertEquals("video/avc", PlaybackMediaFactsMapper.safeLabel("video/avc"));
        assertEquals("", PlaybackMediaFactsMapper.safeLabel("https://secret.example/video"));
        assertEquals("", PlaybackMediaFactsMapper.safeLabel("/data/user/0/private"));
    }

    private static Format video(String mime, String codecs, int width, int height) {
        return new Format.Builder()
                .setSampleMimeType(mime)
                .setCodecs(codecs)
                .setWidth(width)
                .setHeight(height)
                .build();
    }

    // --- decodeModeFact: single source of truth for soft/hard decode across all kernels ---

    @Test
    public void engineReportedKindWinsOverTheName() {
        // IJK reports via FFP_PROPV_DECODER_*, MPV via hwdec. That is authoritative and must
        // not be second-guessed by name parsing.
        assertEquals(PlaybackAutoContext.DecodeMode.HARDWARE,
                decodeMode(PlayerEngine.DecoderKind.HARDWARE, "ffmpegLavc63.3.100-hevc"));
        assertEquals(PlaybackAutoContext.DecodeMode.SOFTWARE,
                decodeMode(PlayerEngine.DecoderKind.SOFTWARE, "c2.mtk.hevc.decoder"));
    }

    @Test
    public void exoFfmpegRendererIsClassifiedSoftwareByName() {
        // The real case that motivated this: Exo passes DecoderKind.UNKNOWN plus the analytics
        // decoder name, and the panel must not keep claiming hardware.
        assertEquals(PlaybackAutoContext.DecodeMode.SOFTWARE,
                decodeMode(PlayerEngine.DecoderKind.UNKNOWN, "ffmpegLavc63.3.100-hevc"));
    }

    @Test
    public void platformSoftwareDecodersAreClassifiedSoftwareByName() {
        for (String name : new String[]{
                "OMX.google.h264.decoder", "c2.android.hevc.decoder",
                "OMX.ffmpeg.hevc.decoder", "libgav1-av1", "libvpx-vp9", "dav1d", "avcodec-hevc"}) {
            assertEquals(name, PlaybackAutoContext.DecodeMode.SOFTWARE,
                    decodeMode(PlayerEngine.DecoderKind.UNKNOWN, name));
        }
    }

    @Test
    public void vendorHardwareNamesAreNotClassifiedSoftware() {
        // Guards the substring tokens against a false software claim.
        for (String name : new String[]{
                "c2.mtk.hevc.decoder", "OMX.amlogic.hevc.decoder", "c2.qti.avc.decoder",
                "OMX.SEC.hevc.dec", "c2.rk.hevc.decoder", "OMX.qcom.video.decoder.vp9"}) {
            PlaybackAutoContext.DecodeMode mode =
                    decodeMode(PlayerEngine.DecoderKind.UNKNOWN, name);
            assertFalse(name, mode == PlaybackAutoContext.DecodeMode.SOFTWARE);
        }
    }

    @Test
    public void unresolvableNameStaysUnknownRatherThanGuessing() {
        // Unknown must not be reported as hardware: the label logic treats only a positive
        // SOFTWARE result as a mismatch, so an unknown name simply produces no claim.
        PlaybackAutoContext.Fact<PlaybackAutoContext.DecodeMode> fact =
                PlaybackMediaFactsMapper.decodeModeFact(
                        PlayerEngine.DecoderKind.UNKNOWN, "", 100);
        assertFalse(fact.hasValue());
        assertEquals(PlaybackAutoContext.DecodeMode.UNKNOWN, fact.value());
    }

    @Test
    public void nameClassificationIsCaseInsensitive() {
        assertEquals(PlaybackAutoContext.DecodeMode.SOFTWARE,
                decodeMode(PlayerEngine.DecoderKind.UNKNOWN, "FFMPEGLavc63-HEVC"));
        assertEquals(PlaybackAutoContext.DecodeMode.SOFTWARE,
                decodeMode(PlayerEngine.DecoderKind.UNKNOWN, "OMX.GOOGLE.h264.decoder"));
    }

    private static PlaybackAutoContext.DecodeMode decodeMode(
            PlayerEngine.DecoderKind kind, String decoderName) {
        return PlaybackMediaFactsMapper.decodeModeFact(kind, decoderName, 100).value();
    }
}
