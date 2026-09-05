package com.fongmi.android.tv.playback;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.TmdbSeasonProgress;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbSeasonProgressStoreTest {

    @Test
    public void progressIdentityIncludesConfigAndSeason() {
        TmdbSeasonProgress progress = TmdbSeasonProgress.of(
                7, "tv", 88, 2, 5, 1200, 2400, "site@@@vod@@@flag");

        assertEquals("7:tv:88:season:2", progress.identityKey());
        assertEquals(5, progress.episodeNumber);
        assertEquals("site@@@vod@@@flag", progress.sourceHistoryKey);
    }

    @Test
    public void onlyKnownTvSeasonHistoryIsEligible() {
        History known = history("tv", 88, 2, 5);
        History seasonZero = history("tv", 88, 0, 1);
        History unknown = history("tv", 88, -1, 5);
        History movie = history("movie", 88, 2, 5);
        History noTmdb = history("tv", 0, 2, 5);

        assertTrue(TmdbSeasonProgressStore.isEligible(known));
        assertTrue(TmdbSeasonProgressStore.isEligible(seasonZero));
        assertFalse(TmdbSeasonProgressStore.isEligible(unknown));
        assertFalse(TmdbSeasonProgressStore.isEligible(movie));
        assertFalse(TmdbSeasonProgressStore.isEligible(noTmdb));
    }

    @Test
    public void historyWritesAndDeletesKeepSeasonProgressInSync() throws Exception {
        String history = read("app/src/main/java/com/fongmi/android/tv/bean/History.java");
        String writer = read("app/src/main/java/com/fongmi/android/tv/playback/PlaybackProgressWriter.java");

        assertTrue(history.contains("TmdbSeasonProgressStore.write(this)"));
        assertTrue(writer.contains("TmdbSeasonProgressStore.write(history)"));
        assertTrue(writer.contains("TmdbSeasonProgressStore.reconcile("));
        assertTrue(writer.contains("deleteSeason(cid, input, filter)"));
        assertTrue(writer.contains("deleteBySource(cid, history.getKey())"));
        assertFalse(writer.contains("restoreAnotherSeason(history)"));
    }

    @Test
    public void remoteSeasonDeleteDoesNotOverwriteNewerLocalProgress() {
        History newerRoute = history("tv", 88, 2, 5);
        newerRoute.setCreateTime(700);
        TmdbSeasonProgress newerSnapshot = TmdbSeasonProgress.of(
                7, "tv", 88, 2, 5, 1_000, 2_000, "site@@@vod");
        newerSnapshot.updatedAt = 800;

        assertTrue(PlaybackProgressWriter.hasNewerSeasonState(
                List.of(newerRoute), List.of(newerSnapshot), 2, 600));
        assertFalse(PlaybackProgressWriter.hasNewerSeasonState(
                List.of(newerRoute), List.of(newerSnapshot), 2, 900));
    }

    @Test
    public void olderSnapshotCannotReplaceNewerSeasonProgress() {
        TmdbSeasonProgress newer = TmdbSeasonProgress.of(7, "tv", 88, 1, 5, 100, 200, "route");
        newer.updatedAt = 300;
        TmdbSeasonProgress older = TmdbSeasonProgress.of(7, "tv", 88, 1, 4, 50, 200, "route");
        older.updatedAt = 200;

        assertFalse(TmdbSeasonProgressStore.shouldWrite(newer, older));
        assertTrue(TmdbSeasonProgressStore.shouldWrite(older, newer));
    }

    @Test
    public void stableFlagKeySurvivesSeasonSnapshotProjection() {
        History source = history("tv", 88, 2, 5);
        source.setKey("site@@@vod@@@7");
        source.setCid(7);
        source.setVodFlag("duplicate");
        source.setSourceBindingKey("duplicate#1");

        TmdbSeasonProgress progress = TmdbSeasonProgressStore.fromHistory(source);
        History projected = source.copy();
        projected.setSourceBindingKey("");
        TmdbSeasonProgressStore.apply(projected, progress);

        assertEquals("duplicate#1", progress.sourceBindingKey);
        assertEquals("duplicate#1", projected.getSourceBindingKey());
    }

    @Test
    public void siteFilteredSeasonDeleteKeepsOtherSiteSnapshot() {
        History siteA = history("tv", 88, 1, 1);
        siteA.setKey("site-a@@@vod-a");
        History siteB = history("tv", 88, 1, 1);
        siteB.setKey("site-b@@@vod-b");
        TmdbSeasonProgress fromA = TmdbSeasonProgress.of(7, "tv", 88, 1, 1, 0, 0, siteA.getKey());
        TmdbSeasonProgress fromB = TmdbSeasonProgress.of(7, "tv", 88, 1, 1, 0, 0, siteB.getKey());
        RemoteSyncConfig filter = new RemoteSyncConfig();
        filter.siteKeys = List.of("site-a");

        List<TmdbSeasonProgress> filtered = PlaybackProgressWriter.snapshotsForRoutes(
                List.of(fromA, fromB), List.of(siteA, siteB), filter);

        assertEquals(List.of(fromA), filtered);
    }

    private static History history(String mediaType, int tmdbId, int season, int episode) {
        History history = new History();
        history.setMediaType(mediaType);
        history.setTmdbId(tmdbId);
        history.setTmdbEpisodePosition(season, episode);
        return history;
    }

    private static String read(String relative) throws Exception {
        Path path = Path.of(relative);
        if (!Files.exists(path) && relative.startsWith("app/")) path = Path.of(relative.substring(4));
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
