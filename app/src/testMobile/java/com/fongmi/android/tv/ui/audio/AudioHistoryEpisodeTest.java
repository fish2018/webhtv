package com.fongmi.android.tv.ui.audio;

import com.fongmi.android.tv.bean.History;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AudioHistoryEpisodeTest {

    @Test
    public void persistedTmdbPositionMatchesIncomingSourceEpisodeNumber() {
        History local = history("源站第20集", "", 2, 20);

        assertTrue(AudioHistory.isSameEpisode(local, "20. 新标题", ""));
    }

    @Test
    public void emptyUrlsDoNotPreserveProgressAcrossDifferentEpisodes() {
        History local = history("源站第20集", "", 2, 20);

        assertFalse(AudioHistory.isSameEpisode(local, "源站第21集", ""));
    }

    private static History history(String remarks, String url, int season, int episode) {
        History history = new History();
        history.setVodRemarks(remarks);
        history.setEpisodeUrl(url);
        history.setTmdbSeasonNumber(season);
        history.setTmdbEpisodeNumber(episode);
        return history;
    }
}
