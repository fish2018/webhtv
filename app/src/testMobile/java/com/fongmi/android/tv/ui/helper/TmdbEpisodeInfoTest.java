package com.fongmi.android.tv.ui.helper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbEpisodeInfoTest {

    @Test
    public void from_ignoresMovies() {
        TmdbEpisodeInfo info = TmdbEpisodeInfo.from("movie", json("{\"number_of_episodes\":12}"), -1);

        assertTrue(info.isEmpty());
    }

    @Test
    public void from_readsCompletedSingleSeasonSeries() {
        TmdbEpisodeInfo info = TmdbEpisodeInfo.from("tv", json("{"
                + "\"status\":\"Ended\","
                + "\"number_of_seasons\":1,"
                + "\"number_of_episodes\":36,"
                + "\"last_episode_to_air\":{\"season_number\":1,\"episode_number\":36},"
                + "\"seasons\":[{\"season_number\":0,\"episode_count\":4},{\"season_number\":1,\"episode_count\":36}]"
                + "}"), -1);

        assertEquals(TmdbEpisodeInfo.State.COMPLETE, info.getState());
        assertEquals(1, info.getSeasonCount());
        assertEquals(36, info.getTotalEpisodes());
        assertEquals(1, info.getLastSeason());
        assertEquals(36, info.getLastEpisode());
        assertFalse(info.isSeasonScoped());
    }

    @Test
    public void from_readsOngoingSingleSeasonProgress() {
        TmdbEpisodeInfo info = TmdbEpisodeInfo.from("tv", json("{"
                + "\"status\":\"Returning Series\","
                + "\"in_production\":true,"
                + "\"number_of_seasons\":1,"
                + "\"number_of_episodes\":36,"
                + "\"last_episode_to_air\":{\"season_number\":1,\"episode_number\":18},"
                + "\"next_episode_to_air\":{\"season_number\":1,\"episode_number\":19},"
                + "\"seasons\":[{\"season_number\":1,\"episode_count\":36}]"
                + "}"), -1);

        assertEquals(TmdbEpisodeInfo.State.ONGOING, info.getState());
        assertEquals(18, info.getLastEpisode());
        assertEquals(36, info.getTotalEpisodes());
    }

    @Test
    public void from_keepsMultiSeasonProgressAsSeasonAndEpisode() {
        TmdbEpisodeInfo info = TmdbEpisodeInfo.from("tv", json("{"
                + "\"status\":\"Returning Series\","
                + "\"number_of_seasons\":3,"
                + "\"number_of_episodes\":24,"
                + "\"last_episode_to_air\":{\"season_number\":3,\"episode_number\":4},"
                + "\"next_episode_to_air\":{\"season_number\":3,\"episode_number\":5},"
                + "\"seasons\":["
                + "{\"season_number\":0,\"episode_count\":20},"
                + "{\"season_number\":1,\"episode_count\":10},"
                + "{\"season_number\":2,\"episode_count\":10},"
                + "{\"season_number\":3,\"episode_count\":4}"
                + "]}"
        ), -1);

        assertEquals(TmdbEpisodeInfo.State.ONGOING, info.getState());
        assertEquals(3, info.getLastSeason());
        assertEquals(4, info.getLastEpisode());
        assertEquals(24, info.getTotalEpisodes());
    }

    @Test
    public void from_scopesExplicitSeasonToItsEpisodeCount() {
        TmdbEpisodeInfo info = TmdbEpisodeInfo.from("tv", json("{"
                + "\"status\":\"Returning Series\","
                + "\"number_of_seasons\":2,"
                + "\"number_of_episodes\":46,"
                + "\"last_episode_to_air\":{\"season_number\":2,\"episode_number\":18},"
                + "\"next_episode_to_air\":{\"season_number\":2,\"episode_number\":19},"
                + "\"seasons\":[{\"season_number\":1,\"episode_count\":10},{\"season_number\":2,\"episode_count\":36}]"
                + "}"), 2);

        assertTrue(info.isSeasonScoped());
        assertEquals(2, info.getScopedSeason());
        assertEquals(36, info.getScopedTotalEpisodes());
        assertEquals(18, info.getScopedAiredEpisodes());
        assertEquals(TmdbEpisodeInfo.State.ONGOING, info.getState());
    }

    @Test
    public void from_marksPastExplicitSeasonComplete() {
        TmdbEpisodeInfo info = TmdbEpisodeInfo.from("tv", json("{"
                + "\"status\":\"Returning Series\","
                + "\"number_of_seasons\":2,"
                + "\"number_of_episodes\":20,"
                + "\"last_episode_to_air\":{\"season_number\":2,\"episode_number\":4},"
                + "\"seasons\":[{\"season_number\":1,\"episode_count\":10},{\"season_number\":2,\"episode_count\":10}]"
                + "}"), 1);

        assertEquals(TmdbEpisodeInfo.State.COMPLETE, info.getState());
        assertEquals(10, info.getScopedAiredEpisodes());
    }

    @Test
    public void from_marksUnairedSeriesPlanned() {
        TmdbEpisodeInfo info = TmdbEpisodeInfo.from("tv", json("{"
                + "\"status\":\"Planned\","
                + "\"in_production\":true,"
                + "\"number_of_seasons\":1,"
                + "\"number_of_episodes\":12,"
                + "\"last_episode_to_air\":null,"
                + "\"seasons\":[{\"season_number\":1,\"episode_count\":12}]"
                + "}"), -1);

        assertEquals(TmdbEpisodeInfo.State.PLANNED, info.getState());
        assertEquals(12, info.getTotalEpisodes());
        assertEquals(0, info.getLastEpisode());
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }
}
