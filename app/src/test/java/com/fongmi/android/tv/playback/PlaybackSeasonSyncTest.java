package com.fongmi.android.tv.playback;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.PlaybackDeleteTombstone;
import com.fongmi.android.tv.bean.TmdbSeasonProgress;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlaybackSeasonSyncTest {

    @Test
    public void progressPayloadParsesTmdbSeasonIdentity() {
        PlaybackProgressInput input = PlaybackProgressInput.fromJson("""
                {"siteKey":"site","vodId":"vod","vodName":"Series","episodeName":"E5",
                 "positionMs":1000,"durationMs":2000,"mediaType":"TV","tmdbId":88,
                 "seasonNumber":2,"episodeNumber":5}
                """);

        assertEquals("tv", input.mediaType);
        assertEquals(88, input.tmdbId);
        assertEquals(2, input.seasonNumber);
        assertEquals(5, input.episodeNumber);
    }

    @Test
    public void playbackRecordPublishesTmdbSeasonIdentity() {
        History history = new History();
        history.setKey("site@@@vod");
        history.setCid(7);
        history.setVodName("Series");
        history.setVodRemarks("E5");
        history.setPosition(1_000);
        history.setDuration(2_000);
        history.setMediaType("tv");
        history.setTmdbId(88);
        history.setTmdbEpisodePosition(2, 5);

        PlaybackRecord record = new PlaybackRecord();
        PlaybackRecord.applyTmdbIdentity(record, history);

        assertEquals("tv", record.mediaType);
        assertEquals(88, record.tmdbId);
        assertEquals(2, record.seasonNumber);
        assertEquals(5, record.tmdbEpisodeNumber);
    }

    @Test
    public void stableDuplicateFlagKeySurvivesRemoteProgressRoundTrip() {
        PlaybackRecord record = new PlaybackRecord();
        record.sourceBindingKey = "duplicate#1";
        PlaybackFieldPolicy policy = PlaybackFieldPolicy.apiSafe();

        JsonObject json = record.toJson(policy);
        PlaybackProgressInput input = PlaybackProgressInput.fromJson(json);
        History restored = new History();
        PlaybackProgressWriter.applySourceBindingKey(restored, input);

        assertTrue(policy.includes("sourceBindingKey"));
        assertEquals("duplicate#1", json.get("sourceBindingKey").getAsString());
        assertEquals("duplicate#1", input.sourceBindingKey);
        assertEquals("duplicate#1", restored.getSourceBindingKey());
    }

    @Test
    public void legacyRemoteProgressWithoutStableFlagKeyKeepsCompatibilityFallback() {
        PlaybackProgressInput input = PlaybackProgressInput.fromJson("{}");

        assertEquals("", input.sourceBindingKey);
    }

    @Test
    public void seasonTombstoneBlocksOnlyTheDeletedSeason() {
        PlaybackProgressDeleteInput input = new PlaybackProgressDeleteInput();
        input.scope = "season";
        input.configKey = "config";
        input.mediaType = "tv";
        input.tmdbId = 88;
        input.seasonNumber = 2;
        input.deletedAt = 500;
        PlaybackDeleteTombstone tombstone = PlaybackDeleteTombstoneStore.create(input, 7);

        assertEquals(500, PlaybackDeleteTombstoneStore.latest(
                List.of(tombstone), "config", 7, "site@@@vod", "site", "vod", "tv", 88, 2));
        assertEquals(0, PlaybackDeleteTombstoneStore.latest(
                List.of(tombstone), "config", 7, "site@@@vod", "site", "vod", "tv", 88, 1));
        assertTrue(tombstone.id.length() > 10);
    }

    @Test
    public void siteScopedSeasonTombstoneDoesNotBlockAnotherSite() {
        PlaybackProgressDeleteInput input = new PlaybackProgressDeleteInput();
        input.scope = "season";
        input.configKey = "config";
        input.siteKey = "site-a";
        input.mediaType = "tv";
        input.tmdbId = 88;
        input.seasonNumber = 2;
        input.deletedAt = 500;
        PlaybackDeleteTombstone tombstone = PlaybackDeleteTombstoneStore.create(input, 7);

        assertEquals(500, PlaybackDeleteTombstoneStore.latest(
                List.of(tombstone), "config", 7, "", "site-a", "vod-a", "tv", 88, 2));
        assertEquals(0, PlaybackDeleteTombstoneStore.latest(
                List.of(tombstone), "config", 7, "", "site-b", "vod-b", "tv", 88, 2));
    }

    @Test
    public void olderCrossSeasonRemoteUpdateWritesSnapshotWithoutMovingCurrentRoute() {
        PlaybackProgressInput input = seasonInput(1, 150);
        History currentRoute = new History();
        currentRoute.setMediaType("tv");
        currentRoute.setTmdbId(88);
        currentRoute.setTmdbEpisodePosition(2, 3);
        currentRoute.setCreateTime(200);
        TmdbSeasonProgress olderTarget = TmdbSeasonProgress.of(7, "tv", 88, 1, 1, 0, 0, "site@@@vod");
        olderTarget.updatedAt = 100;

        assertEquals(PlaybackProgressWriter.RemoteSeasonUpsertMode.SNAPSHOT_ONLY,
                PlaybackProgressWriter.planRemoteSeasonUpsert(input, currentRoute, olderTarget));
    }

    @Test
    public void remoteUpdateCannotRegressNewerTargetSeasonSnapshot() {
        PlaybackProgressInput input = seasonInput(1, 150);
        TmdbSeasonProgress newerTarget = TmdbSeasonProgress.of(7, "tv", 88, 1, 2, 0, 0, "site@@@vod");
        newerTarget.updatedAt = 200;

        assertEquals(PlaybackProgressWriter.RemoteSeasonUpsertMode.SKIP,
                PlaybackProgressWriter.planRemoteSeasonUpsert(input, null, newerTarget));
    }

    private static PlaybackProgressInput seasonInput(int season, long updatedAt) {
        PlaybackProgressInput input = new PlaybackProgressInput();
        input.mediaType = "tv";
        input.tmdbId = 88;
        input.seasonNumber = season;
        input.episodeNumber = 1;
        input.updatedAt = updatedAt;
        return input;
    }
}
