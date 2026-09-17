package com.fongmi.android.tv.player;

import org.junit.Test;

import static com.fongmi.android.tv.player.PlaybackServiceReleasePolicy.Action.DETACH;
import static com.fongmi.android.tv.player.PlaybackServiceReleasePolicy.Action.RESET_SESSION;
import static com.fongmi.android.tv.player.PlaybackServiceReleasePolicy.Action.SHUTDOWN;
import static com.fongmi.android.tv.player.PlaybackServiceReleasePolicy.Action.SUSPEND_AND_RESET;
import static org.junit.Assert.assertEquals;

public class PlaybackServiceReleasePolicyTest {

    @Test
    public void keepsTheExistingReleaseMatrix() {
        assertEquals(RESET_SESSION, PlaybackServiceReleasePolicy.decide(true, true, false));
        assertEquals(SUSPEND_AND_RESET, PlaybackServiceReleasePolicy.decide(true, false, true));
        assertEquals(RESET_SESSION, PlaybackServiceReleasePolicy.decide(false, false, true));
        assertEquals(SHUTDOWN, PlaybackServiceReleasePolicy.decide(true, false, false));
        assertEquals(DETACH, PlaybackServiceReleasePolicy.decide(false, false, false));
    }
}
