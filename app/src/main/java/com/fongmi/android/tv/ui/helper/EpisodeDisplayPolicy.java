package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;

import java.util.List;

public final class EpisodeDisplayPolicy {

    private EpisodeDisplayPolicy() {
    }

    /** 是否存在通过严格集号校验的有效 TMDB 分集，决定要不要用增强卡片。 */
    public static boolean hasTmdbEpisodeData(List<Episode> items) {
        if (items == null || items.isEmpty()) return false;
        for (Episode item : items) {
            if (item != null && TmdbEpisodeMatcher.shouldApply(item, item.getTmdbEpisode())) return true;
        }
        return false;
    }

    public static boolean shouldUseTmdbEpisodeCards(boolean tmdbSourceEnabled, List<Episode> items) {
        return tmdbSourceEnabled && hasTmdbEpisodeData(items);
    }

    /**
     * 站源集数已经可用时不再隐藏列表：先渲染纯文本选集，TMDB 元数据到达后原地换成卡片。
     * 隐藏整个列表等 TMDB 会让用户对着「正在加载剧集信息...」干等最长 15 秒，而列表内容
     * 其实早就拿到了。只有在还没有任何集数可渲染时才有必要占位。
     */
    public static boolean shouldWaitForTmdbEpisodes(boolean tmdbSourceEnabled, boolean tmdbEpisodeEnrichmentPending, boolean tmdbAdapterReady, boolean tmdbEpisodeMetadataLoaded, List<Episode> items) {
        return tmdbSourceEnabled && tmdbEpisodeEnrichmentPending && tmdbAdapterReady && !tmdbEpisodeMetadataLoaded && (items == null || items.isEmpty());
    }

    /**
     * 表头（倒序 / 列表 / 原文件名）在「已有卡片数据」或「富集仍在进行」时显示。
     * 参数不再叫 waitingForTmdbEpisodes：选集现在先上屏，隐藏与富集已经解耦，调用方传的是
     * 富集是否进行中，让表头在纯文本阶段就位、卡片到达时不必二次插入造成列表跳动。
     */
    public static boolean shouldShowTmdbEpisodeChrome(boolean tmdbSourceEnabled, boolean enrichmentPending, List<Episode> items) {
        return tmdbSourceEnabled && (hasTmdbEpisodeData(items) || enrichmentPending);
    }

    public static boolean shouldShowEpisodeGroup(int groupCount, boolean tmdbDetailLayout) {
        return groupCount > 1;
    }
}
