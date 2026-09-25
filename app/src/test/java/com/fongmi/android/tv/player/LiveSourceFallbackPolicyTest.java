package com.fongmi.android.tv.player;

import static com.fongmi.android.tv.player.LiveSourceFallbackPolicy.Action.NEXT_LINE;
import static com.fongmi.android.tv.player.LiveSourceFallbackPolicy.Action.NEXT_SOURCE;
import static com.fongmi.android.tv.player.LiveSourceFallbackPolicy.Action.NONE;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LiveSourceFallbackPolicyTest {

    @Test
    public void regularLineFallbackUsesNextLineBeforeChangingSource() {
        assertEquals(NEXT_LINE, LiveSourceFallbackPolicy.decide(true, true, true, false, false, true));
    }

    @Test
    public void disabledSourceFallbackDoesNothingAfterLineFallbackIsExhausted() {
        assertEquals(NONE, LiveSourceFallbackPolicy.decide(false, false, true, true, false, true));
    }

    @Test
    public void enabledSourceFallbackUsesNextSourceWhenAvailable() {
        assertEquals(NEXT_SOURCE, LiveSourceFallbackPolicy.decide(false, true, true, true, false, true));
    }

    @Test
    public void enabledSourceFallbackUsesAnotherChannelLineWhenNoNextSourceExists() {
        assertEquals(NEXT_LINE, LiveSourceFallbackPolicy.decide(false, true, true, true, false, false));
    }

    @Test
    public void enabledSourceFallbackStopsWhenNeitherSourceNorLineIsAvailable() {
        assertEquals(NONE, LiveSourceFallbackPolicy.decide(false, true, true, true, true, false));
    }
}
