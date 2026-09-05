package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.Player;

import org.junit.Test;

public class PlaybackStateReconciliationTest {

    @Test
    public void replaysReadyWhenControllerAttachedAfterCurrentItemBecameReady() {
        assertTrue(PlaybackStateReconciliation.shouldReplayReady(
                "site@@vod@@2", "site@@vod@@2", "site@@vod@@2",
                "site@@vod@@2", "site@@vod@@2", Player.STATE_READY, Player.STATE_READY));
    }

    @Test
    public void doesNotReplayReadyFromPreviousItem() {
        assertFalse(PlaybackStateReconciliation.shouldReplayReady(
                "site@@vod@@2", "site@@vod@@1", "site@@vod@@1",
                "site@@vod@@1", "site@@vod@@1", Player.STATE_READY, Player.STATE_READY));
    }

    @Test
    public void doesNotReplayNonReadyOrUnidentifiedPlayback() {
        assertFalse(PlaybackStateReconciliation.shouldReplayReady(
                "site@@vod@@2", "site@@vod@@2", "site@@vod@@2",
                "site@@vod@@2", "site@@vod@@2", Player.STATE_READY, Player.STATE_BUFFERING));
        assertFalse(PlaybackStateReconciliation.shouldReplayReady(
                "site@@vod@@2", "site@@vod@@2", "site@@vod@@2",
                "site@@vod@@2", "site@@vod@@2", Player.STATE_BUFFERING, Player.STATE_READY));
        assertFalse(PlaybackStateReconciliation.shouldReplayReady(
                null, "site@@vod@@2", "site@@vod@@2",
                "site@@vod@@2", "site@@vod@@2", Player.STATE_READY, Player.STATE_READY));
        assertFalse(PlaybackStateReconciliation.shouldReplayReady(
                "site@@vod@@2", null, "site@@vod@@2",
                "site@@vod@@2", "site@@vod@@2", Player.STATE_READY, Player.STATE_READY));
        assertFalse(PlaybackStateReconciliation.shouldReplayReady(
                "site@@vod@@2", "site@@vod@@2", "site@@vod@@2",
                "site@@vod@@2", null, Player.STATE_READY, Player.STATE_READY));
    }
}
