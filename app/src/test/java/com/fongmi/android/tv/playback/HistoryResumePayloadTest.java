package com.fongmi.android.tv.playback;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.TmdbSeasonProgress;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class HistoryResumePayloadTest {

    @Test
    public void seasonalPayloadRestoresClickedSnapshotInsteadOfCurrentRouteSeason() {
        History current = history("route", 3, 8, 300, 900, "duplicate#0");
        History clicked = history("route", 1, 5, 100, 600, "duplicate#1");
        TmdbSeasonProgress snapshot = TmdbSeasonProgressStore.fromHistory(clicked);

        History restored = HistoryResumePayload.restore(
                current, snapshot, HistoryResumePayload.encode(clicked));

        assertEquals(1, restored.getTmdbSeasonNumber());
        assertEquals(5, restored.getTmdbEpisodeNumber());
        assertEquals(100, restored.getPosition());
        assertEquals(600, restored.getDuration());
        assertEquals("duplicate#1", restored.getSourceBindingKey());
        assertEquals(3, current.getTmdbSeasonNumber());
    }

    @Test
    public void seasonalPayloadRejectsSnapshotOwnedByAnotherRoute() {
        History current = history("route", 3, 8, 300, 900, "duplicate#0");
        History clicked = history("route", 1, 5, 100, 600, "duplicate#1");
        TmdbSeasonProgress snapshot = TmdbSeasonProgressStore.fromHistory(clicked);
        snapshot.sourceHistoryKey = "other-route";

        assertNull(HistoryResumePayload.restore(
                current, snapshot, HistoryResumePayload.encode(clicked)));
    }

    @Test
    public void seasonalPayloadFallsBackToCurrentRouteWhenSnapshotIsMissing() {
        History current = history("route", 3, 8, 300, 900, "duplicate#0");

        assertSame(current, HistoryResumePayload.restore(
                current, null, HistoryResumePayload.encode(current)));
    }

    @Test
    public void seasonalPayloadDoesNotFallbackToAnotherSeasonWhenSnapshotIsMissing() {
        History current = history("route", 3, 8, 300, 900, "duplicate#0");
        History clicked = history("route", 1, 5, 100, 600, "duplicate#1");

        assertNull(HistoryResumePayload.restore(
                current, null, HistoryResumePayload.encode(clicked)));
    }

    @Test
    public void legacyHistoryKeyStillRestoresCurrentRow() {
        History current = history("route", 3, 8, 300, 900, "duplicate#0");

        assertSame(current, HistoryResumePayload.restore(current, null, current.getKey()));
    }

    private static History history(String key, int season, int episode,
                                   long position, long duration, String sourceBindingKey) {
        History history = new History();
        history.setCid(7);
        history.setKey(key);
        history.setTmdbId(88);
        history.setMediaType("tv");
        history.setTmdbEpisodePosition(season, episode);
        history.setPosition(position);
        history.setDuration(duration);
        history.setVodFlag("duplicate");
        history.setVodRemarks("E" + episode);
        history.setEpisodeUrl("url-" + season + "-" + episode);
        history.setSourceBindingKey(sourceBindingKey);
        history.setCreateTime(1_000L + season);
        return history;
    }
}
