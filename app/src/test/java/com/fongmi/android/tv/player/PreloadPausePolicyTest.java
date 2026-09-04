package com.fongmi.android.tv.player;

import com.fongmi.android.tv.setting.PreloadSetting;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PreloadPausePolicyTest {

    @Test
    public void activePlaybackAlwaysAllowsPreload() {
        assertTrue(PreloadPausePolicy.evaluate(
                true,
                PreloadSetting.PAUSE_PRELOAD_WIFI,
                PlaybackAutoContext.NetworkSnapshot.unknown()).allowed());
    }

    @Test
    public void pausePolicyHasOnlyCellularAndWifiLikeModes() {
        assertTrue(PreloadPausePolicy.evaluate(
                false,
                PreloadSetting.PAUSE_PRELOAD_WIFI,
                wifi()).allowed());
        assertFalse(PreloadPausePolicy.evaluate(
                false,
                PreloadSetting.PAUSE_PRELOAD_WIFI,
                snapshot(true, true, PlaybackAutoContext.NetworkTransport.CELLULAR)).allowed());
        assertTrue(PreloadPausePolicy.evaluate(
                false,
                PreloadSetting.PAUSE_PRELOAD_WIFI,
                snapshot(true, false, PlaybackAutoContext.NetworkTransport.WIFI)).allowed());
        assertTrue(PreloadPausePolicy.evaluate(
                false,
                PreloadSetting.PAUSE_PRELOAD_WIFI,
                PlaybackAutoContext.NetworkSnapshot.unknown()).allowed());
    }

    @Test
    public void alwaysPolicyAllowsPausedPreloadWithoutNetworkEvidence() {
        assertTrue(PreloadPausePolicy.evaluate(
                false,
                PreloadSetting.PAUSE_PRELOAD_ALWAYS,
                PlaybackAutoContext.NetworkSnapshot.unknown()).allowed());
    }

    private static PlaybackAutoContext.NetworkSnapshot wifi() {
        return snapshot(true, true, PlaybackAutoContext.NetworkTransport.WIFI);
    }

    private static PlaybackAutoContext.NetworkSnapshot snapshot(
            Boolean available,
            Boolean validated,
            PlaybackAutoContext.NetworkTransport transport) {
        return new PlaybackAutoContext.NetworkSnapshot(
                available,
                validated,
                null,
                null,
                transport,
                PlaybackAutoContext.DataSaverState.UNKNOWN);
    }
}
