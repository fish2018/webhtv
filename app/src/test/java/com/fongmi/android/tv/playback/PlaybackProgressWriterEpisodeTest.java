package com.fongmi.android.tv.playback;

import com.fongmi.android.tv.bean.History;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackProgressWriterEpisodeTest {

    @Test
    public void sameEpisodeUsesPersistedTmdbPositionWhenIncomingSourceHasOnlyTitle() {
        History local = history("源站第20集", "", 2, 20);
        PlaybackProgressInput input = input("20. 新标题", "");

        assertTrue(PlaybackProgressWriter.isSameEpisode(local, input));
    }

    @Test
    public void emptyUrlsDoNotMakeDifferentEpisodeTitlesLookIdentical() {
        History local = history("源站第20集", "", 2, 20);
        PlaybackProgressInput input = input("源站第21集", "");

        assertFalse(PlaybackProgressWriter.isSameEpisode(local, input));
    }

    private static History history(String remarks, String url, int season, int episode) {
        History history = new History();
        history.setVodRemarks(remarks);
        history.setEpisodeUrl(url);
        history.setTmdbSeasonNumber(season);
        history.setTmdbEpisodeNumber(episode);
        return history;
    }

    private static PlaybackProgressInput input(String episodeName, String episodeUrl) {
        PlaybackProgressInput input = new PlaybackProgressInput();
        input.episodeName = episodeName;
        input.episodeUrl = episodeUrl;
        return input;
    }
}
