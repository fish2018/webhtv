package com.fongmi.android.tv.ui.activity;

import com.fongmi.android.tv.bean.History;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HistoryResumeCoordinatorTest {

    @Test
    public void resumeVersionChangesWhenSeasonSnapshotAdvances() {
        History requested = history(2, 5, 100, "e5", "line#1");
        History advanced = history(2, 6, 200, "e6", "line#1");

        assertFalse(HistoryResumeCoordinator.isSameResumeVersion(requested, advanced));
    }

    @Test
    public void resumeVersionKeepsAnUnchangedSnapshot() {
        History requested = history(2, 5, 100, "e5", "line#1");
        History restored = history(2, 5, 100, "e5", "line#1");

        assertTrue(HistoryResumeCoordinator.isSameResumeVersion(requested, restored));
    }

    private static History history(int season, int episode, long updatedAt,
                                   String episodeUrl, String sourceBindingKey) {
        History history = new History();
        history.setCid(7);
        history.setKey("site@@@vod@@@7");
        history.setMediaType("tv");
        history.setTmdbId(88);
        history.setTmdbEpisodePosition(season, episode);
        history.setCreateTime(updatedAt);
        history.setEpisodeUrl(episodeUrl);
        history.setSourceBindingKey(sourceBindingKey);
        history.setPosition(12);
        history.setDuration(34);
        return history;
    }
}
