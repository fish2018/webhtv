package com.fongmi.android.tv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Tracks;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.google.gson.Gson;

import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerManagerTest {

    @Test
    public void speedToggle_restoresOriginalShowSpeedAfterFastToggle() {
        PlayerManager.SpeedToggleState state = new PlayerManager.SpeedToggleState();

        float fastSpeed = state.next(1.5f, 1.5f, 3.0f, 1.25f);
        float restoredSpeed = state.next(fastSpeed, fastSpeed, 3.0f, 1.25f);

        assertEquals(3.0f, fastSpeed, 0.001f);
        assertEquals(1.5f, restoredSpeed, 0.001f);
    }

    @Test
    public void speedToggle_withoutSessionBaselineFallsBackToPersonalDefault() {
        PlayerManager.SpeedToggleState state = new PlayerManager.SpeedToggleState();

        float restoredSpeed = state.next(3.0f, 3.0f, 3.0f, 1.25f);

        assertEquals(1.25f, restoredSpeed, 0.001f);
    }

    @Test
    public void exoSpeedRestore_defersSpeedUntilMatchingPrepareIsReady() {
        PlayerManager.ExoSpeedRestoreState state = new PlayerManager.ExoSpeedRestoreState();

        assertEquals(1.0f, state.beginPrepare(7, 1.5f), 0.001f);
        assertEquals(1.5f, state.effectiveSpeed(1.0f), 0.001f);
        assertTrue(state.deferSpeed(1.25f));
        assertEquals(1.25f, state.effectiveSpeed(1.0f), 0.001f);
        assertTrue(Float.isNaN(state.takeReadySpeed(6)));
        assertEquals(1.25f, state.takeReadySpeed(7), 0.001f);
        assertEquals(1.0f, state.effectiveSpeed(1.0f), 0.001f);
    }

    @Test
    public void exoSpeedRestore_carriesPendingTargetIntoReplacementPrepare() {
        PlayerManager.ExoSpeedRestoreState state = new PlayerManager.ExoSpeedRestoreState();

        state.beginPrepare(7, 1.5f);
        float desiredSpeed = state.effectiveSpeed(1.0f);
        state.beginPrepare(8, desiredSpeed);

        assertTrue(Float.isNaN(state.takeReadySpeed(7)));
        assertEquals(1.5f, state.takeReadySpeed(8), 0.001f);
    }

    @Test
    public void exoSpeedRestore_cancelledPrepareKeepsTargetButRejectsLateReady() {
        PlayerManager.ExoSpeedRestoreState state = new PlayerManager.ExoSpeedRestoreState();

        state.beginPrepare(7, 1.5f);
        state.cancelPrepare(7);

        assertTrue(Float.isNaN(state.takeReadySpeed(7)));
        assertEquals(1.5f, state.effectiveSpeed(1.0f), 0.001f);
        assertTrue(state.deferSpeed(1.25f));
        assertEquals(1.0f, state.beginPrepare(8, state.effectiveSpeed(1.0f)), 0.001f);
        assertTrue(Float.isNaN(state.takeReadySpeed(7)));
        assertEquals(1.25f, state.takeReadySpeed(8), 0.001f);
    }

    @Test
    public void exoSpeedRestore_staleCancellationDoesNotCancelReplacementPrepare() {
        PlayerManager.ExoSpeedRestoreState state = new PlayerManager.ExoSpeedRestoreState();

        state.beginPrepare(7, 1.5f);
        state.beginPrepare(8, state.effectiveSpeed(1.0f));
        state.cancelPrepare(7);

        assertEquals(1.5f, state.takeReadySpeed(8), 0.001f);
    }

    @Test
    public void nextFallbackAction_obeysConfiguredMode() {
        assertEquals(PlayerManager.FALLBACK_DECODE, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_FULL, PlayerEngine.HARD));
        assertEquals(PlayerManager.FALLBACK_PLAYER, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_FULL, PlayerEngine.SOFT));
        assertEquals(PlayerManager.FALLBACK_DECODE, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_DECODE_ONLY, PlayerEngine.HARD));
        assertEquals(PlayerManager.FALLBACK_NONE, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_DECODE_ONLY, PlayerEngine.SOFT));
        assertEquals(PlayerManager.FALLBACK_PLAYER, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_PLAYER_ONLY, PlayerEngine.HARD));
        assertEquals(PlayerManager.FALLBACK_PLAYER, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_PLAYER_ONLY, PlayerEngine.SOFT));
        assertEquals(PlayerManager.FALLBACK_NONE, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_DISABLED, PlayerEngine.HARD));
        assertEquals(PlayerManager.FALLBACK_NONE, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_DISABLED, PlayerEngine.SOFT));
    }

    @Test
    public void fallbackDecode_obeysConfiguredMode() {
        assertEquals(PlayerEngine.HARD, PlayerManager.fallbackDecode(PlayerSetting.FALLBACK_FULL, PlayerSetting.EXO, PlayerSetting.IJK, PlayerEngine.SOFT));
        assertEquals(PlayerEngine.SOFT, PlayerManager.fallbackDecode(PlayerSetting.FALLBACK_PLAYER_ONLY, PlayerSetting.EXO, PlayerSetting.IJK, PlayerEngine.SOFT));
        assertEquals(PlayerEngine.SOFT, PlayerManager.fallbackDecode(PlayerSetting.FALLBACK_FULL, PlayerSetting.EXO, PlayerSetting.EXO, PlayerEngine.SOFT));
        assertEquals(PlayerEngine.HARD, PlayerManager.fallbackDecode(PlayerSetting.FALLBACK_PLAYER_ONLY, PlayerSetting.EXO, PlayerSetting.IJK, 99));
    }

    @Test
    public void shouldRetryMpvCopy_retriesAutomaticGpuHardFailures() {
        assertTrue(PlayerManager.shouldRetryMpvCopy(
                true, true, false, false, false,
                PlaybackException.ERROR_CODE_TIMEOUT, null));
        assertTrue(PlayerManager.shouldRetryMpvCopy(
                true, true, false, false, false,
                PlaybackException.ERROR_CODE_DECODING_FAILED, "MPV_DECODE_FAILED: codec init"));
    }

    @Test
    public void shouldRetryMpvCopy_preservesManualModesAndRejectsUnrelatedFailures() {
        assertFalse(PlayerManager.shouldRetryMpvCopy(
                false, true, false, false, false,
                PlaybackException.ERROR_CODE_DECODING_FAILED, null));
        assertFalse(PlayerManager.shouldRetryMpvCopy(
                true, true, true, false, false,
                PlaybackException.ERROR_CODE_DECODING_FAILED, null));
        assertFalse(PlayerManager.shouldRetryMpvCopy(
                true, true, false, true, false,
                PlaybackException.ERROR_CODE_DECODING_FAILED, null));
        assertFalse(PlayerManager.shouldRetryMpvCopy(
                true, true, false, false, true,
                PlaybackException.ERROR_CODE_DECODING_FAILED, null));
        assertFalse(PlayerManager.shouldRetryMpvCopy(
                true, true, false, false, false,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, "Socket timeout"));
    }

    @Test
    public void shouldStopOnManualSwitchFailure_blocksFallbackWhileManualSwitchIsPending() {
        assertEquals(true, PlayerManager.shouldStopOnManualSwitchFailure(true, PlayerEngine.ErrorAction.FATAL));
    }

    @Test
    public void shouldStopOnManualSwitchFailure_allowsRecoveredManualErrors() {
        assertEquals(false, PlayerManager.shouldStopOnManualSwitchFailure(true, PlayerEngine.ErrorAction.RECOVERED));
    }

    @Test
    public void shouldStopOnManualSwitchFailure_allowsAutomaticFallbacks() {
        assertEquals(false, PlayerManager.shouldStopOnManualSwitchFailure(false, PlayerEngine.ErrorAction.FATAL));
    }

    @Test
    public void snapshotCurrentResult_usesResolvedSpecStateWhilePreservingMetadata() {
        Result source = new Gson().fromJson(
                "{\"url\":\"source.m3u8\",\"playUrl\":\"https://proxy.example/?url=\",\"parse\":1,\"jx\":1,\"artwork\":\"https://img.example/art.jpg\"}",
                Result.class);
        source.setHeader(new HashMap<>(Map.of("Old", "1")));
        PlaySpec spec = PlaySpec.fromParse(source, "VE:site@@vod|线路一|1", null);
        spec.setUrl("https://cdn.example/current.m3u8");
        spec.setHeaders(new HashMap<>(Map.of("Referer", "https://example.com/")));
        spec.setFormat(MimeTypes.APPLICATION_M3U8);

        Result snapshot = PlayerManager.snapshotCurrentResult(spec);

        assertEquals("https://cdn.example/current.m3u8", snapshot.getRealUrl());
        assertEquals("", snapshot.getPlayUrl());
        assertEquals(0, snapshot.getParse().intValue());
        assertFalse(snapshot.needParse());
        assertEquals("https://example.com/", snapshot.getHeader().get("Referer"));
        assertFalse(snapshot.getHeader().containsKey("Old"));
        assertEquals(MimeTypes.APPLICATION_M3U8, snapshot.getFormat());
        assertEquals("https://img.example/art.jpg", snapshot.getArtwork());
        assertEquals("https://proxy.example/?url=source.m3u8", source.getRealUrl());
        assertTrue(source.needParse());
        assertEquals("1", source.getHeader().get("Old"));
    }

    @Test
    public void isCurrentDirectSwitchRefresh_acceptsOnlyMatchingPendingRequest() {
        PlaySpec requested = PlaySpec.from("key", "https://example.com/video.m3u8", null, null);
        PlaySpec replacement = PlaySpec.from("other", "https://example.com/other.m3u8", null, null);

        assertTrue(PlayerManager.isCurrentDirectSwitchRefresh(true, 7, 7, PlayerSetting.EXO, PlayerSetting.EXO, requested, requested));
        assertFalse(PlayerManager.isCurrentDirectSwitchRefresh(false, 7, 7, PlayerSetting.EXO, PlayerSetting.EXO, requested, requested));
        assertFalse(PlayerManager.isCurrentDirectSwitchRefresh(true, 7, 8, PlayerSetting.EXO, PlayerSetting.EXO, requested, requested));
        assertFalse(PlayerManager.isCurrentDirectSwitchRefresh(true, 7, 7, PlayerSetting.EXO, PlayerSetting.IJK, requested, requested));
        assertFalse(PlayerManager.isCurrentDirectSwitchRefresh(true, 7, 7, PlayerSetting.EXO, PlayerSetting.EXO, requested, replacement));
    }

    @Test
    public void findSubtitleSub_matchesSelectedExternalSubtitleByLabelAndMime() {
        Sub english = Sub.create("English", "/tmp/english.srt", "en", MimeTypes.APPLICATION_SUBRIP);
        Sub chinese = Sub.create("Chinese", "/tmp/chinese.srt", "zh-Hans", MimeTypes.APPLICATION_SUBRIP);
        Format selected = new Format.Builder()
                .setLabel("English")
                .setLanguage("eng")
                .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
                .build();

        Sub matched = PlayerManager.findSubtitleSub(List.of(chinese, english), selected);

        assertSame(english, matched);
    }

    @Test
    public void findSubtitleSub_ignoresInternalSubtitleWithoutMatchingSub() {
        Sub english = Sub.create("English", "/tmp/english.srt", "en", MimeTypes.APPLICATION_SUBRIP);
        Format selected = new Format.Builder()
                .setLabel("Embedded English")
                .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
                .build();

        assertNull(PlayerManager.findSubtitleSub(List.of(english), selected));
    }

    @Test
    public void findSelectedSubtitleSub_usesFirstExternalSubtitleBeforeTracksLoad() {
        Sub manual = Sub.create("Manual", "/tmp/manual.srt", "zh-Hans", MimeTypes.APPLICATION_SUBRIP);

        assertSame(manual, PlayerManager.findSelectedSubtitleSub(List.of(manual), Tracks.EMPTY));
    }

    @Test
    public void httpStatus_findsNestedResponseCode() {
        IOException error = new IOException("source", new IOException("Response code: 403"));

        assertEquals(403, PlayerManager.httpStatus(error));
    }

    @Test
    public void httpStatus_returnsZeroWithoutResponseCode() {
        assertEquals(0, PlayerManager.httpStatus(new IOException("Socket closed")));
    }

    @Test
    public void nextUntriedFfmpegMode_walksOrderThenExhausts() {
        int[] order = PlayerSetting.FFMPEG_AUTO_ORDER;
        boolean[] tried = new boolean[order.length];

        int first = PlayerManager.nextUntriedFfmpegMode(order, tried);
        assertEquals(order[0], first);
        tried[0] = true;

        int second = PlayerManager.nextUntriedFfmpegMode(order, tried);
        assertEquals(order[1], second);
        tried[1] = true;

        int third = PlayerManager.nextUntriedFfmpegMode(order, tried);
        assertEquals(order[2], third);
        tried[2] = true;

        assertEquals(PlayerSetting.NONE, PlayerManager.nextUntriedFfmpegMode(order, tried));
    }
}
