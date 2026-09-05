package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EpisodeCardPolicyTest {

    @Test
    public void shouldShowCard_whenTmdbEpisodeMatches() {
        assertTrue(EpisodeCardPolicy.shouldShowCard(true, true, false));
    }

    @Test
    public void shouldShowCard_whenTmdbEpisodeMissingButFallbackStillExists() {
        assertTrue(EpisodeCardPolicy.shouldShowCard(true, false, true));
    }

    /**
     * 卡片模式必须整列统一。曾经这里退化成文本按钮，导致刮削不全的长剧（如航海王，源站集数
     * 远超 TMDB 单季集数）在同一网格里卡片与矮按钮混排；无图的集应由 holder 降级绑定，
     * 而不是换成另一种尺寸的布局。
     */
    @Test
    public void shouldStayCard_whenNeitherTmdbEpisodeNorFallbackStillExists() {
        assertTrue(EpisodeCardPolicy.shouldShowCard(true, false, false));
    }

    @Test
    public void shouldUseText_whenTmdbCardModeIsDisabled() {
        assertFalse(EpisodeCardPolicy.shouldShowCard(false, true, true));
        assertFalse(EpisodeCardPolicy.shouldShowCard(false, false, false));
    }
}
