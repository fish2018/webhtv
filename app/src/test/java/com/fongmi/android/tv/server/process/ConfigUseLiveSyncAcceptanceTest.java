package com.fongmi.android.tv.server.process;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.setting.ConfigSyncPolicy;

import org.junit.Test;

public class ConfigUseLiveSyncAcceptanceTest {

    @Test
    public void synchronizedPairingContinuesWhenUrlsMatch() {
        assertTrue(ConfigSyncPolicy.shouldSyncLive("https://example.com/config", "https://example.com/config"));
    }

    @Test
    public void independentLiveConfigIsPreservedWhenUrlsDiffer() {
        assertFalse(ConfigSyncPolicy.shouldSyncLive("https://vod.example.com/config", "https://live.example.com/config"));
    }

    @Test
    public void emptyInitialPairingContinuesWhenBothUrlsAreEmpty() {
        assertTrue(ConfigSyncPolicy.shouldSyncLive("", ""));
    }

    @Test
    public void nullVodUrlIsNotTreatedAsSameIndependentLiveSource() {
        assertFalse(ConfigSyncPolicy.shouldSyncLive(null, "https://live.example.com/config"));
    }
}
