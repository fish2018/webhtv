package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class BackupTmdbSeasonProgressTest {

    @Test
    public void sanitizeDropsNullAndInvalidSeasonSnapshots() {
        TmdbSeasonProgress valid = TmdbSeasonProgress.of(7, "TV", 88, 2, 5,
                1_000, 2_000, "site@@@vod");
        valid.sourceFlag = null;
        TmdbSeasonProgress invalid = TmdbSeasonProgress.of(7, "movie", 88, -1, 0,
                0, 0, "");

        List<TmdbSeasonProgress> result = Backup.sanitizeTmdbSeasonProgress(
                Arrays.asList(null, invalid, valid));

        assertEquals(1, result.size());
        assertEquals("tv", result.get(0).mediaType);
        assertEquals("", result.get(0).sourceFlag);
    }

    @Test
    public void legacyBackupRebuildKeepsNewestHistoryPerSeason() {
        History newer = history("site-a@@@vod-a", 300);
        History older = history("site-b@@@vod-b", 100);

        List<TmdbSeasonProgress> result = Backup.latestTmdbSeasonProgress(
                List.of(newer, older));

        assertEquals(1, result.size());
        assertEquals("site-a@@@vod-a", result.get(0).sourceHistoryKey);
        assertEquals(300, result.get(0).updatedAt);
    }

    @Test
    public void legacyFallbackUsesHistoryCidAlreadyRemappedByRestore() {
        History remapped = history("site-a@@@vod-a", 300);
        remapped.setCid(2);

        List<TmdbSeasonProgress> result = Backup.latestTmdbSeasonProgress(List.of(remapped));

        assertEquals(2, result.get(0).cid);
    }

    private static History history(String key, long time) {
        History history = new History();
        history.setKey(key);
        history.setCid(7);
        history.setMediaType("tv");
        history.setTmdbId(88);
        history.setTmdbEpisodePosition(1, 1);
        history.setCreateTime(time);
        return history;
    }
}
