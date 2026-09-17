package com.fongmi.android.tv.service;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackExitPolicyTest {

    @Test
    public void activeMedia_canContinueInBackground() {
        assertTrue(PlaybackExitPolicy.canContinueInBackground(true, true, true));
    }

    @Test
    public void runningServiceWithoutMedia_doesNotOfferBackgroundPlayback() {
        assertFalse(PlaybackExitPolicy.canContinueInBackground(true, true, false));
    }

    @Test
    public void unavailablePlayer_doesNotOfferBackgroundPlayback() {
        assertFalse(PlaybackExitPolicy.canContinueInBackground(true, false, true));
    }

    @Test
    public void stoppedService_doesNotOfferBackgroundPlayback() {
        assertFalse(PlaybackExitPolicy.canContinueInBackground(false, true, true));
    }
}
