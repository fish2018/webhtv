package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.exoplayer.DefaultRenderersFactory;

import com.fongmi.android.tv.player.engine.PlayerEngine;

import org.junit.Test;

public class ExoFfmpegFallbackTuneTest {

    @Test
    public void hardDecodeStillReachesTheFfmpegVideoRenderer() {
        // Hard decode maps to EXTENSION_RENDERER_MODE_OFF, which getFfmpegVideoRenderMode
        // converts to ON so the renderer stays available as a codec fallback.
        int hardMode = ExoUtil.getRenderMode(PlayerEngine.HARD);
        assertTrue(hardMode == DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
        assertTrue(ExoUtil.isFfmpegVideoReachable(hardMode));
    }

    @Test
    public void softDecodeReachesTheFfmpegVideoRenderer() {
        int softMode = ExoUtil.getRenderMode(PlayerEngine.SOFT);
        assertTrue(ExoUtil.isFfmpegVideoReachable(softMode));
    }

    @Test
    public void tuningAppliesToTheHardDecodeFallbackNotJustSoftDecode() {
        int hardMode = ExoUtil.getRenderMode(PlayerEngine.HARD);
        int softMode = ExoUtil.getRenderMode(PlayerEngine.SOFT);

        assertTrue(ExoUtil.shouldTuneFfmpegVideo(true, ExoUtil.isFfmpegVideoReachable(hardMode)));
        assertTrue(ExoUtil.shouldTuneFfmpegVideo(true, ExoUtil.isFfmpegVideoReachable(softMode)));
    }

    @Test
    public void tuningStaysOffWhenTheUserDisabledIt() {
        int hardMode = ExoUtil.getRenderMode(PlayerEngine.HARD);
        assertFalse(ExoUtil.shouldTuneFfmpegVideo(false, ExoUtil.isFfmpegVideoReachable(hardMode)));
    }

    @Test
    public void tuningStaysOffWhenTheRendererCannotDecode() {
        assertFalse(ExoUtil.shouldTuneFfmpegVideo(true, false));
    }

    @Test
    public void decodeBuffersScaleWithThreadCount() {
        // Frame-threaded decode needs roughly threads + 1 frames in flight.
        assertTrue(ExoUtil.ffmpegDecodeBuffers(8) > ExoUtil.ffmpegDecodeBuffers(2));
        assertEquals(10, ExoUtil.ffmpegDecodeBuffers(8));
        assertEquals(6, ExoUtil.ffmpegDecodeBuffers(4));
    }

    @Test
    public void decodeBuffersNeverDropBelowTheOldFixedFloor() {
        // The previous behavior was a fixed 4; single-core must not regress below it.
        assertEquals(4, ExoUtil.ffmpegDecodeBuffers(1));
        assertEquals(4, ExoUtil.ffmpegDecodeBuffers(0));
        assertEquals(4, ExoUtil.ffmpegDecodeBuffers(-3));
    }

    @Test
    public void decodeBuffersAreCappedSoMemoryStaysBounded() {
        assertEquals(12, ExoUtil.ffmpegDecodeBuffers(64));
        assertEquals(12, ExoUtil.ffmpegDecodeBuffers(Integer.MAX_VALUE));
    }

    @Test
    public void loadSheddingKeepsEveryFrame() {
        // AVDISCARD_NONREF (8) would drop frames the renderer never sees, breaking motion
        // continuity while still reporting zero dropped frames.
        assertEquals(0, ExoUtil.ffmpegSkipFrame());
    }

    @Test
    public void hardDecodeFallbackRemainsFallbackOnly() {
        // Tuning must not turn the fallback renderer into a track thief: it still only
        // claims codecs MediaCodec cannot handle.
        int hardMode = ExoUtil.getRenderMode(PlayerEngine.HARD);
        assertTrue(ExoUtil.isFfmpegVideoFallbackOnly(hardMode, false));
        assertFalse(ExoUtil.isFfmpegVideoFallbackOnly(hardMode, true));
    }
}
