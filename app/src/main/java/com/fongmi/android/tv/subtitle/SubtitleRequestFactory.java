package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.subtitle.model.SubtitleRequest;
import com.fongmi.android.tv.subtitle.model.SubtitleTrigger;

public class SubtitleRequestFactory {

    public SubtitleRequest create(String playbackKey, Site site, Vod vod, Episode episode, Result result, TmdbItem tmdbItem, TmdbEpisode tmdbEpisode, SubtitleTrigger trigger) {
        if (result == null) return null;
        String vodName = firstNonEmpty(vod == null ? "" : vod.getName(), tmdbItem == null ? "" : tmdbItem.getTitle());
        String episodeName = episode == null ? "" : firstNonEmpty(episode.getDisplayName(), episode.getDesc(), episode.getName());
        int seasonNumber = tmdbEpisode == null ? -1 : tmdbEpisode.getSeasonNumber();
        int episodeNumber = tmdbEpisode != null && tmdbEpisode.getNumber() > 0 ? tmdbEpisode.getNumber() : episode == null ? -1 : episode.getNumber();

        return SubtitleRequest.builder()
                .playbackKey(playbackKey)
                .siteKey(site == null ? "" : site.getKey())
                .siteName(site == null ? "" : site.getName())
                .vodId(vod == null ? "" : vod.getId())
                .vodName(vodName)
                .vodRemarks(vod == null ? "" : vod.getRemarks())
                .vodYear(vod == null ? "" : vod.getYear())
                .episodeName(episodeName)
                .playUrl(result.getRealUrl())
                .playHeaders(result.getHeader())
                .preferredLanguage(Setting.getSubtitlePreferredLanguage())
                .trigger(trigger)
                .allowTmdbLookup(Setting.isTmdbReady())
                .tmdbItem(tmdbItem)
                .tmdbEpisode(tmdbEpisode)
                .seasonNumber(seasonNumber)
                .episodeNumber(episodeNumber)
                .build();
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) if (!SubtitleStrings.isEmpty(value)) return value;
        return "";
    }
}
