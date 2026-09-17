package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackgroundPlaybackPolicyTest {

    @Test
    public void legacyPictureInPictureModeMigratesToEnabled() {
        assertEquals(BackgroundPlaybackPolicy.ON, BackgroundPlaybackPolicy.normalize(2));
        assertTrue(BackgroundPlaybackPolicy.isEnabled(2));
    }

    @Test
    public void disabledModeNeverUsesPictureInPicture() {
        assertFalse(BackgroundPlaybackPolicy.shouldUsePictureInPicture(BackgroundPlaybackPolicy.OFF, false));
        assertFalse(BackgroundPlaybackPolicy.shouldUsePictureInPicture(BackgroundPlaybackPolicy.OFF, true));
    }

    @Test
    public void enabledVideoModeUsesPictureInPicture() {
        assertTrue(BackgroundPlaybackPolicy.shouldUsePictureInPicture(BackgroundPlaybackPolicy.ON, false));
    }

    @Test
    public void enabledAudioModeUsesRegularBackgroundPlayback() {
        assertFalse(BackgroundPlaybackPolicy.shouldUsePictureInPicture(BackgroundPlaybackPolicy.ON, true));
    }
}