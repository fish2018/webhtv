package com.fongmi.android.tv.history;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.TmdbItem;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HistorySourceResolverTest {

    @Test
    public void tmdbIdentityOutranksTitleOnlyCandidate() {
        History history = history("示例剧", "2026", "第9集");
        history.setTmdbId(100);
        history.setMediaType("tv");
        TmdbItem tmdb = new TmdbItem(100, "tv", "示例剧", "", "", "", "");

        int score = HistorySourceResolver.scoreCandidate(history, "International Alias", "2030", tmdb);

        assertTrue(score >= HistorySourceResolver.TMDB_MATCH_SCORE);
    }

    @Test
    public void conflictingTmdbIdentityRejectsCandidate() {
        History history = history("示例剧", "2026", "第9集");
        history.setTmdbId(100);
        history.setMediaType("tv");
        TmdbItem tmdb = new TmdbItem(101, "tv", "示例剧", "", "", "", "");

        assertEquals(HistorySourceResolver.REJECTED, HistorySourceResolver.scoreCandidate(history, "示例剧", "2026", tmdb));
    }

    @Test
    public void automaticCandidateFromAnotherKnownSeasonIsRejected() {
        History history = history("Lego Ninjago Dragons Rising", "2026", "Episode 5");
        history.setTmdbId(100);
        history.setMediaType("tv");
        history.setTmdbEpisodePosition(1, 5);
        TmdbItem tmdb = new TmdbItem(100, "tv", "Lego Ninjago Dragons Rising", "", "", "", "");

        int score = HistorySourceResolver.scoreAutomaticCandidate(
                history,
                "site-b@@@vod-b",
                "Lego Ninjago Dragons Rising Season 3",
                "2026",
                tmdb);

        assertEquals(HistorySourceResolver.REJECTED, score);
    }

    @Test
    public void sameTmdbIdentityAllowsUnknownSeasonUntilDetailFlagsAreChecked() {
        History history = history("Lego Ninjago Dragons Rising", "2026", "Episode 5");
        history.setTmdbId(100);
        history.setMediaType("tv");
        history.setTmdbEpisodePosition(2, 5);
        TmdbItem tmdb = new TmdbItem(100, "tv", "Lego Ninjago Dragons Rising", "", "", "", "");

        int score = HistorySourceResolver.scoreAutomaticCandidate(
                history,
                "site-b@@@vod-b",
                "Lego Ninjago Dragons Rising",
                "2026",
                tmdb);

        assertTrue(score >= HistorySourceResolver.TMDB_MATCH_SCORE);
    }

    @Test
    public void automaticKnownSeasonRejectsUnknownFlagFromAnotherSource() {
        History history = history("Lego Ninjago Dragons Rising", "2026", "Episode 1");
        history.setMediaType("tv");
        history.setTmdbEpisodePosition(2, 1);
        Flag unknown = Flag.create("Default line", "Episode 1$url");

        assertNull(HistorySourceResolver.findAutomaticEpisode(
                List.of(unknown), history, "site-b@@@vod-b"));
        assertEquals("url", HistorySourceResolver.findAutomaticEpisode(
                List.of(unknown), history, history.getKey()).episode().getUrl());
    }

    @Test
    public void automaticKnownSeasonAcceptsFlagWithMatchingSeasonEvidence() {
        History history = history("Lego Ninjago Dragons Rising", "2026", "Episode 1");
        history.setMediaType("tv");
        history.setTmdbEpisodePosition(2, 1);
        Flag matching = Flag.create("Season 2 line", "Episode 1$url-s2e1");

        HistorySourceResolver.EpisodeMatch match = HistorySourceResolver.findAutomaticEpisode(
                List.of(matching), history, "site-b@@@vod-b");

        assertEquals("url-s2e1", match.episode().getUrl());
    }

    @Test
    public void automaticMultiSeasonFlagMatchesEpisodeInsideSavedSeasonOnly() {
        History history = history("Lego Ninjago Dragons Rising", "2026", "E05");
        history.setMediaType("tv");
        history.setTmdbEpisodePosition(2, 5);
        Flag multi = Flag.create("All seasons", "S01E05$url-s1e5#S02E05$url-s2e5");

        HistorySourceResolver.EpisodeMatch match = HistorySourceResolver.findAutomaticEpisode(
                List.of(multi), history, "site-b@@@vod-b", 2);

        assertEquals("url-s2e5", match.episode().getUrl());
    }

    @Test
    public void selectedMultiSeasonFlagMatchesEpisodeInsideSavedSeasonOnly() {
        History history = history("Lego Ninjago Dragons Rising", "2026", "E05");
        history.setMediaType("tv");
        history.setTmdbEpisodePosition(2, 5);
        Flag multi = Flag.create("All seasons", "S01E05$url-s1e5#S02E05$url-s2e5");

        HistorySourceResolver.EpisodeMatch match = HistorySourceResolver.findEpisode(
                List.of(multi), history);

        assertEquals("url-s2e5", match.episode().getUrl());
    }

    @Test
    public void candidateTitleWithMatchingSeasonAllowsUnknownDetailFlag() {
        History history = history("Lego Ninjago Dragons Rising", "2026", "Episode 1");
        history.setMediaType("tv");
        history.setTmdbEpisodePosition(2, 1);
        Flag unknown = Flag.create("Default line", "Episode 1$url");

        HistorySourceResolver.EpisodeMatch match = HistorySourceResolver.findAutomaticEpisode(
                List.of(unknown), history, "site-b@@@vod-b", 2);

        assertEquals("url", match.episode().getUrl());
    }

    @Test
    public void unknownSeasonDoesNotCrossSourceAutomatically() {
        History history = history("Lego Ninjago Dragons Rising", "2026", "Episode 5");
        history.setTmdbId(100);
        history.setMediaType("tv");

        assertFalse(HistorySourceResolver.canAutoReuseSeason(history, -1, "site-b@@@vod-b"));
        assertTrue(HistorySourceResolver.canAutoReuseSeason(history, -1, history.getKey()));
    }

    @Test
    public void yearConflictRejectsAutomaticCandidate() {
        History history = history("同名作品", "2020", "第1集");

        assertEquals(HistorySourceResolver.REJECTED, HistorySourceResolver.scoreCandidate(history, "同名作品", "2024", null));
    }

    @Test
    public void fuzzyTitleMatchRequiresManualSelection() {
        History history = history("庆余年", "2024", "第1集");

        int score = HistorySourceResolver.scoreCandidate(history, "庆余年2", "", null);

        assertTrue(score > HistorySourceResolver.REJECTED);
        assertFalse(HistorySourceResolver.isAutomaticScore(score));
    }

    @Test
    public void exactTitleMatchCanResolveAutomatically() {
        History history = history("庆余年", "2024", "第1集");

        int score = HistorySourceResolver.scoreCandidate(history, "庆余年", "2024", null);

        assertTrue(HistorySourceResolver.isAutomaticScore(score));
    }

    @Test
    public void targetEpisodeMatchesAcrossDifferentLabelsAndUrls() {
        History history = history("示例剧", "2026", "第9集");
        history.setVodFlag("旧线路");
        history.setEpisodeUrl("old-url");
        Flag target = Flag.create("新线路", "[277MB] 9. 新标题$new-url#第10集$url-10");

        HistorySourceResolver.EpisodeMatch match = HistorySourceResolver.findEpisode(List.of(target), history);

        assertEquals("新线路", match.flag().getFlag());
        assertEquals("new-url", match.episode().getUrl());
    }

    @Test
    public void targetEpisodeUsesPersistedTmdbNumberBeforeScrapedTitleDigits() {
        History history = history("凡人修仙传", "2020", "20. 燕家堡之战3：血灵大法");
        history.setTmdbSeasonNumber(1);
        history.setTmdbEpisodeNumber(20);
        Flag target = Flag.create("新线路", "第19集$url-19#第20集$url-20#第21集$url-21");

        HistorySourceResolver.EpisodeMatch match = HistorySourceResolver.findEpisode(List.of(target), history);

        assertEquals("url-20", match.episode().getUrl());
    }

    @Test
    public void targetEpisodeSkipsFlagFromAnotherExplicitSeason() {
        History history = history("Lego Ninjago Dragons Rising", "2026", "Episode 1");
        history.setMediaType("tv");
        history.setTmdbEpisodePosition(2, 1);
        history.setVodFlag("Season 3 line");
        Flag wrong = Flag.create("Season 3 line", "Episode 1$url-s3e1");
        Flag expected = Flag.create("Season 2 line", "Episode 1$url-s2e1");

        HistorySourceResolver.EpisodeMatch match = HistorySourceResolver.findEpisode(List.of(wrong, expected), history);

        assertEquals("Season 2 line", match.flag().getFlag());
        assertEquals("url-s2e1", match.episode().getUrl());
    }

    @Test
    public void missingTargetEpisodeIsRejected() {
        History history = history("示例剧", "2026", "第9集");
        Flag target = Flag.create("线路", "第1集$url-1#第2集$url-2");

        assertNull(HistorySourceResolver.findEpisode(List.of(target), history));
    }

    @Test
    public void movieWithoutEpisodeNumberUsesFirstPlayableEpisode() {
        History history = history("示例电影", "2026", "示例电影");
        Flag target = Flag.create("线路", "正片$movie-url");

        HistorySourceResolver.EpisodeMatch match = HistorySourceResolver.findEpisode(List.of(target), history);

        assertEquals("movie-url", match.episode().getUrl());
    }

    private static History history(String title, String year, String episode) {
        History history = new History();
        history.setKey("site@@@vod@@@1");
        history.setVodName(title);
        history.setYear(year);
        history.setVodRemarks(episode);
        return history;
    }

}
