package com.fongmi.android.tv.ui.audio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AudioPlaybackReconciliationTest {

    @Test
    public void serviceBrowseTrackReplacesStaleMiniPlayerIndexAndRemainsOwned() {
        AudioPlaybackReconciliation.Match match = AudioPlaybackReconciliation.reconcile(
                "VE:site@@vod|线路一|1", "site@@vod", "线路一", 2, 3);

        assertTrue(match.owned());
        assertEquals(1, match.index());
    }

    @Test
    public void originalPlaybackKeyKeepsCurrentPlaylistIndex() {
        AudioPlaybackReconciliation.Match match = AudioPlaybackReconciliation.reconcile(
                "site@@vod", "site@@vod", "线路一", 2, 3);

        assertTrue(match.owned());
        assertEquals(2, match.index());
    }

    @Test
    public void unrelatedOrMalformedBrowseTrackIsNotAdopted() {
        assertFalse(AudioPlaybackReconciliation.reconcile(
                "VE:other@@vod|线路一|1", "site@@vod", "线路一", 2, 3).owned());
        assertFalse(AudioPlaybackReconciliation.reconcile(
                "VE:site@@vod|线路二|1", "site@@vod", "线路一", 2, 3).owned());
        assertFalse(AudioPlaybackReconciliation.reconcile(
                "VE:site@@vod|线路一|x", "site@@vod", "线路一", 2, 3).owned());
        assertFalse(AudioPlaybackReconciliation.reconcile(
                "VE:site@@vod|线路一|3", "site@@vod", "线路一", 2, 3).owned());
    }
}
