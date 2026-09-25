package com.fongmi.android.tv.following;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FollowingMetadataClientTest {

    @Test
    public void parsesOfficialStatusAndNextAirEpisode() {
        JsonObject detail = JsonParser.parseString("""
                {"status":"Returning Series","number_of_episodes":24,
                 "last_episode_to_air":{"season_number":2,"episode_number":8},
                 "next_episode_to_air":{"season_number":2,"episode_number":9,"air_date":"2026-09-20"},
                 "seasons":[{"season_number":2,"episode_count":12}]}
                """).getAsJsonObject();
        Following item = new Following();
        item.trackedSeason = 2;

        FollowingMetadataSnapshot snapshot = FollowingMetadataClient.parseDetail(detail, item, 1234);

        assertEquals(FollowingMetadataSnapshot.RETURNING, snapshot.status);
        assertEquals(8, snapshot.latestReleasedEpisode);
        assertEquals(12, snapshot.seasonTotalEpisodes);
        assertEquals(9, snapshot.nextAirEpisode);
        assertTrue(snapshot.nextAirAt > 0);
        assertEquals(1234, snapshot.fetchedAt);
    }

    @Test
    public void seasonDetailUsesLatestAiredEpisodeNumberNotReleasedCount() {
        JsonObject season = JsonParser.parseString("""
                {
                  "episode_count": 10,
                  "episodes": [
                    {"episode_number": 1, "air_date": "2026-09-18"},
                    {"episode_number": 2, "air_date": "2027-01-01"},
                    {"episode_number": 10, "air_date": "2026-09-19"}
                  ]
                }
                """).getAsJsonObject();
        FollowingMetadataSnapshot snapshot = new FollowingMetadataSnapshot();
        snapshot.latestReleasedSeason = 1;
        snapshot.latestReleasedEpisode = 1;

        FollowingMetadataClient.applySeason(snapshot, season, 1, FollowingMetadataClient.airTime("2026-09-20"));

        assertEquals(10, snapshot.latestReleasedEpisode);
        assertEquals(2, snapshot.seasonReleasedEpisodes);
    }
}
