package com.fongmi.android.tv.player.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.Player;

import org.junit.Test;

public class SystemSimplePlayerStateTest {

    @Test
    public void loadingIsSuppressedForIdleAndEndedStates() {
        assertFalse(SystemPlayerState.loadingFor(Player.STATE_IDLE, true));
        assertFalse(SystemPlayerState.loadingFor(Player.STATE_ENDED, true));
    }

    @Test
    public void loadingIsPreservedForActiveStates() {
        assertTrue(SystemPlayerState.loadingFor(Player.STATE_BUFFERING, true));
        assertTrue(SystemPlayerState.loadingFor(Player.STATE_READY, true));
        assertFalse(SystemPlayerState.loadingFor(Player.STATE_BUFFERING, false));
    }
}
