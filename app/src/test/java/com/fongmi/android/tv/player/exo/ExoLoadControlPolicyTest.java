package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExoLoadControlPolicyTest {

    @Test
    public void fixedProfilesHonorConfiguredPriorityWithoutAutoOverride() {
        assertFalse(ExoLoadControlPolicy.prioritizeTime(false));
        assertTrue(ExoLoadControlPolicy.prioritizeTime(true));
    }

    @Test
    public void automaticProfileSeparatesStreamingAndLocalBoundaries() {
        ExoLoadControlPolicy.AutomaticConfiguration configuration =
                ExoLoadControlPolicy.automatic(1_500);

        assertEquals(30_000, configuration.streaming().minBufferMs());
        assertEquals(60_000, configuration.streaming().maxBufferMs());
        assertEquals(1_500, configuration.streamingStartBufferMs());
        assertEquals(15_000, configuration.streamingRebufferMs());
        assertFalse(configuration.streamingPrioritizeTime());
        assertEquals(1_000, configuration.local().minBufferMs());
        assertEquals(15_000, configuration.local().maxBufferMs());
        assertEquals(500, configuration.localStartBufferMs());
        assertEquals(1_000, configuration.localRebufferMs());
        assertTrue(configuration.localPrioritizeTime());
    }

    @Test
    public void automaticStreamingStartIsClampedToItsMinimumBuffer() {
        assertEquals(0, ExoLoadControlPolicy.automatic(-1).streamingStartBufferMs());
        assertEquals(30_000, ExoLoadControlPolicy.automatic(60_000).streamingStartBufferMs());
    }

    @Test
    public void fixedProfilesUseAuditedBufferDurations() {
        assertDurations(PlaybackPerformanceSetting.PROFILE_RECOMMENDED, 30_000, 60_000);
        assertDurations(PlaybackPerformanceSetting.PROFILE_AUTO, 30_000, 60_000);
        assertDurations(PlaybackPerformanceSetting.PROFILE_COMPATIBLE, 15_000, 30_000);
        assertDurations(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT, 15_000, 30_000);
    }

    @Test
    public void customLevelScalesFromFifteenToThirtySeconds() {
        assertDurations(PlaybackPerformanceSetting.PROFILE_CUSTOM, 1, 15_000, 30_000);
        assertDurations(PlaybackPerformanceSetting.PROFILE_CUSTOM, 8, 22_500, 45_000);
        assertDurations(PlaybackPerformanceSetting.PROFILE_CUSTOM, 15, 30_000, 60_000);
        assertDurations(PlaybackPerformanceSetting.PROFILE_CUSTOM, 16, 30_000, 60_000);
    }

    private static void assertDurations(int profile, int minMs, int maxMs) {
        assertDurations(profile, 1, minMs, maxMs);
    }

    private static void assertDurations(int profile, int level, int minMs, int maxMs) {
        ExoLoadControlPolicy.BufferDurations durations = ExoLoadControlPolicy.resolve(profile, level);
        assertEquals(minMs, durations.minBufferMs());
        assertEquals(maxMs, durations.maxBufferMs());
    }
}
