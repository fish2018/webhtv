package com.fongmi.android.tv.ui.host;

import com.fongmi.android.tv.bean.TmdbItem;
import com.google.gson.JsonObject;

/**
 * 接口：提供 TMDB 影片级详情数据的宿主
 * <p>
 * 用于让详情页（如 TmdbDetailActivity）向对话框（如 EpisodeDetailDialog）
 * 暴露已加载的 matchedTmdbItem / matchedTmdbDetail，避免在电影场景下
 * 重新加载或无法展示影片信息。
 */
public interface TmdbDetailHost {

    /**
     * 获取当前匹配的 TMDB 影片项（标题/海报/评分等轻量数据）
     *
     * @return TmdbItem 或 null（若未刮削或加载中）
     */
    TmdbItem getMatchedTmdbItem();

    /**
     * 获取当前匹配的 TMDB 影片详情 JSON（完整 detail 对象，含 cast/photos）
     *
     * @return JsonObject 或 null（若未刮削或加载中）
     */
    JsonObject getMatchedTmdbDetail();
}
