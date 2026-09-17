package com.fongmi.android.tv.ui.helper;

public final class EpisodeCardPolicy {

    private EpisodeCardPolicy() {
    }

    /**
     * 卡片模式只由整体开关决定，不看单集是否刮削到 TMDB、也不看有没有兜底图。
     * 容器（EpisodeListDialog 的固定行高、GridLayoutManager 的等分列宽）按整体模式计算尺寸，
     * 逐集在卡片与文本之间切换会让未匹配的集退化成矮按钮，与卡片行混排。
     * 缺数据的集由各 holder 走降级绑定：无图时隐藏图层、文字面板居中，外框尺寸保持一致。
     */
    public static boolean shouldShowCard(boolean useTmdbCard, boolean hasTmdbEpisode, boolean hasFallbackStill) {
        return useTmdbCard;
    }
}
