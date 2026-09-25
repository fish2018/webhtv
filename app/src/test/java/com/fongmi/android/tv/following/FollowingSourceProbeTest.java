package com.fongmi.android.tv.following;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.Vod;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FollowingSourceProbeTest {

    @Test
    public void preferredFlagIsUsedWhenItHasPlayableEpisodes() {
        Vod vod = vod(
                Flag.create("preferred", "1$one#2$two"),
                Flag.create("fallback", "1$one#2$two#3$three"));

        assertEquals("preferred", FollowingSourceProbe.chooseFlag(vod, "preferred").getFlag());
    }

    @Test
    public void emptyPreferredFlagFallsBackToLargestPlayableFlag() {
        Vod vod = vod(
                Flag.create("preferred", ""),
                Flag.create("small", "1$one"),
                Flag.create("large", "1$one#2$two#3$three"));

        assertEquals("large", FollowingSourceProbe.chooseFlag(vod, "preferred").getFlag());
    }

    @Test
    public void noPlayableEpisodesReturnsNull() {
        assertNull(FollowingSourceProbe.chooseFlag(vod(Flag.create("empty", "")), ""));
    }

    @Test
    public void fallbackFlagUsesTrackedSeasonCoverage() {
        Vod vod = vod(
                seasonFlag("other", 2, 5),
                seasonFlag("tracked", 1, 2));

        assertEquals("tracked", FollowingSourceProbe.chooseFlag(vod, "", 1).getFlag());
    }

    @Test
    public void episodeNumberPrefersTmdbMapping() {
        Episode episode = Episode.create("第10集", "url");
        episode.setTmdbEpisode(new com.fongmi.android.tv.bean.TmdbEpisode(3, "", "", "", "", 0, 0));

        assertEquals(3, FollowingSourceProbe.episodeNumber(episode));
    }

    @Test
    public void seasonMappedEpisodesMustMatchTrackedSeason() {
        Episode seasonTwo = Episode.create("第1集", "url");
        seasonTwo.setTmdbEpisode(new com.fongmi.android.tv.bean.TmdbEpisode(1, "", "", "", "", 0, 0, 1, 2));

        assertTrue(FollowingSourceProbe.matchesSeason(seasonTwo, 2));
        assertFalse(FollowingSourceProbe.matchesSeason(seasonTwo, 1));
        assertTrue(FollowingSourceProbe.matchesSeason(Episode.create("第1集", "url"), 1));
    }

    private static Vod vod(Flag... flags) {
        Vod vod = new Vod();
        vod.setFlags(List.of(flags));
        return vod;
    }

    private static Flag seasonFlag(String name, int season, int count) {
        Flag flag = new Flag(name);
        for (int i = 1; i <= count; i++) {
            Episode episode = Episode.create("第" + i + "集", name + ":" + i);
            episode.setTmdbEpisode(new TmdbEpisode(i, "", "", "", "", 0, 0, 0, season));
            flag.getEpisodes().add(episode);
        }
        return flag;
    }
}
