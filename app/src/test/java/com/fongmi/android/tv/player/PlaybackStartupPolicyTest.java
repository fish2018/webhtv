package com.fongmi.android.tv.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlaybackStartupPolicyTest {

    @Test
    public void exoVideoWaitsForRenderedFirstFrameEvent() {
        assertEquals(PlaybackStartupPolicy.Completion.NONE, PlaybackStartupPolicy.resolve(true, false, true, true));
    }

    @Test
    public void mpvVideoRequiresFrameEvidenceInAdditionToReady() {
        assertEquals(PlaybackStartupPolicy.Completion.NONE, PlaybackStartupPolicy.resolve(true, false, true, true));
        assertEquals(PlaybackStartupPolicy.Completion.FIRST_FRAME, PlaybackStartupPolicy.resolve(true, true, true, true));
    }

    @Test
    public void readyAudioWithoutVideoIsPlayable() {
        assertEquals(PlaybackStartupPolicy.Completion.AUDIO_PLAYABLE, PlaybackStartupPolicy.resolve(true, false, false, true));
        assertEquals(PlaybackStartupPolicy.Completion.AUDIO_PLAYABLE, PlaybackStartupPolicy.resolve(true, true, false, true));
    }

    @Test
    public void tracksDoNotCompleteStartupBeforeReady() {
        assertEquals(PlaybackStartupPolicy.Completion.NONE, PlaybackStartupPolicy.resolve(false, true, true, true));
        assertEquals(PlaybackStartupPolicy.Completion.NONE, PlaybackStartupPolicy.resolve(false, false, false, true));
    }
}
