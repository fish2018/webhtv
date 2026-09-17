package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EpisodeDisplayPolicyTest {

    @Test
    public void nativeMode_usesNativeEpisodeDisplayEvenIfEpisodeHasTmdbObject() {
        assertFalse(EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(false, Collections.singletonList(tmdbEpisode())));
        assertFalse(EpisodeDisplayPolicy.shouldShowTmdbEpisodeChrome(false, false, Collections.singletonList(tmdbEpisode())));
        assertFalse(EpisodeDisplayPolicy.shouldShowTmdbEpisodeChrome(false, true, Collections.singletonList(tmdbEpisode())));
    }

    @Test
    public void tmdbModeWithoutMatchedEpisodeData_usesNativeEpisodeDisplayAfterLoadCompletes() {
        assertFalse(EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(true, Collections.singletonList(nativeEpisode())));
        assertFalse(EpisodeDisplayPolicy.shouldWaitForTmdbEpisodes(true, false, true, true, Collections.singletonList(nativeEpisode())));
        assertFalse(EpisodeDisplayPolicy.shouldShowTmdbEpisodeChrome(true, false, Collections.singletonList(nativeEpisode())));
    }

    @Test
    public void tmdbModeWhileLoading_showsScrapedEpisodesImmediatelyWithLoadingChrome() {
        // 站源集数已可渲染时不再等 TMDB：先出纯文本列表，卡片随后原地替换。
        assertFalse(EpisodeDisplayPolicy.shouldWaitForTmdbEpisodes(true, true, true, false, Collections.singletonList(nativeEpisode())));
        assertTrue(EpisodeDisplayPolicy.shouldShowTmdbEpisodeChrome(true, true, Collections.singletonList(nativeEpisode())));
    }

    @Test
    public void tmdbModeWhileLoadingWithoutAnyEpisode_stillShowsPlaceholder() {
        // 一集都还没有时占位符仍是唯一能显示的东西。
        assertTrue(EpisodeDisplayPolicy.shouldWaitForTmdbEpisodes(true, true, true, false, Collections.emptyList()));
        assertTrue(EpisodeDisplayPolicy.shouldWaitForTmdbEpisodes(true, true, true, false, null));
    }

    @Test
    public void rejectedTmdbEpisode_stopsMetadataLoadingWaitBecauseEpisodesAreRenderable() {
        // 集号对不上不代表没有可渲染的集数；否则整季误匹配会把 loading 卡住直到超时
        assertFalse(EpisodeDisplayPolicy.shouldWaitForTmdbEpisodes(
                true, true, true, false, Collections.singletonList(wrongNumberTmdbEpisode())));
    }

    @Test
    public void tmdbModeWithMatchedEpisodeData_usesTmdbCardsAndChrome() {
        assertTrue(EpisodeDisplayPolicy.hasTmdbEpisodeData(Arrays.asList(nativeEpisode(), tmdbEpisode())));
        assertTrue(EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(true, Arrays.asList(nativeEpisode(), tmdbEpisode())));
        assertTrue(EpisodeDisplayPolicy.shouldShowTmdbEpisodeChrome(true, false, Arrays.asList(nativeEpisode(), tmdbEpisode())));
    }

    @Test
    public void originalEnhancedMode_keepsMatchedEpisodeAcrossDifferentlyNamedSourceLines() {
        Episode episode = sameNumberDifferentTitleEpisode();

        assertTrue(EpisodeDisplayPolicy.hasTmdbEpisodeData(Collections.singletonList(episode)));
        assertTrue(EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(true, Collections.singletonList(episode)));
        assertTrue(EpisodeDisplayPolicy.shouldShowTmdbEpisodeChrome(true, false, Collections.singletonList(episode)));
    }

    @Test
    public void rejectedTmdbEpisode_doesNotEnableCardsOrChrome() {
        Episode episode = wrongNumberTmdbEpisode();

        assertFalse(EpisodeDisplayPolicy.hasTmdbEpisodeData(Collections.singletonList(episode)));
        assertFalse(EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(true, Collections.singletonList(episode)));
        assertFalse(EpisodeDisplayPolicy.shouldShowTmdbEpisodeChrome(true, false, Collections.singletonList(episode)));
    }

    @Test
    public void anyValidMatch_keepsWholeSeasonInCardMode() {
        assertTrue(EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(
                true, Arrays.asList(wrongNumberTmdbEpisode(), tmdbEpisode())));
    }

    @Test
    public void explicitSafeMapping_countsAsValidMatch() {
        Episode episode = Episode.create("第25集 源站标题", "http://example.test/25");
        episode.setMappedTmdbEpisode(new TmdbEpisode(1, "Mapped title", "", "", "", 0, 0, 0, 2));

        assertTrue(EpisodeDisplayPolicy.hasTmdbEpisodeData(Collections.singletonList(episode)));
        assertTrue(EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(true, Collections.singletonList(episode)));
    }

    @Test
    public void episodeGroup_showsInTmdbDetailLayout() {
        assertTrue(EpisodeDisplayPolicy.shouldShowEpisodeGroup(2, false));
        assertTrue(EpisodeDisplayPolicy.shouldShowEpisodeGroup(2, true));
        assertFalse(EpisodeDisplayPolicy.shouldShowEpisodeGroup(1, false));
    }

    private static Episode nativeEpisode() {
        return Episode.create("第1集", "http://example.test/1");
    }

    private static Episode tmdbEpisode() {
        Episode episode = Episode.create("第2集", "http://example.test/2");
        episode.setTmdbEpisode(new TmdbEpisode(2, "Title", "", "", "", 0, 0));
        return episode;
    }

    private static Episode sameNumberDifferentTitleEpisode() {
        Episode episode = Episode.create("第2集 Source Title", "http://example.test/2");
        episode.setTmdbEpisode(new TmdbEpisode(2, "Different Title", "", "", "", 0, 0));
        return episode;
    }

    private static Episode wrongNumberTmdbEpisode() {
        Episode episode = Episode.create("第2集 Source Title", "http://example.test/2");
        episode.setTmdbEpisode(new TmdbEpisode(9, "Wrong episode", "", "", "", 0, 0));
        return episode;
    }
}
