package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;

public final class TmdbEpisodeMatcher {

    private TmdbEpisodeMatcher() {
    }

    /**
     * 严格按源集号匹配；只有已经经过安全跨季映射的 Episode 才允许源集号与 TMDB 本季集号不同。
     */
    public static boolean shouldApply(Episode episode, TmdbEpisode tmdbEpisode) {
        if (episode == null || tmdbEpisode == null || episode.getNumber() <= 0 || tmdbEpisode.getNumber() <= 0) return false;
        if (episode.getNumber() == tmdbEpisode.getNumber()) return true;
        return episode.isTmdbEpisodeMapped()
                && episode.getTmdbEpisode() == tmdbEpisode
                && tmdbEpisode.getSeasonNumber() >= 0;
    }

    /**
     * 保留旧调用的严格语义；mappedNumber 不能绕过源集号校验。
     */
    public static boolean shouldApply(Episode episode, TmdbEpisode tmdbEpisode, int mappedNumber) {
        if (episode == null || tmdbEpisode == null || episode.getNumber() <= 0 || mappedNumber <= 0) return false;
        if (episode.getNumber() == tmdbEpisode.getNumber()) return tmdbEpisode.getNumber() == mappedNumber;
        return episode.isTmdbEpisodeMapped()
                && episode.getTmdbEpisode() == tmdbEpisode
                && tmdbEpisode.getNumber() == mappedNumber
                && tmdbEpisode.getSeasonNumber() >= 0;
    }

    /**
     * 验证由源集号主键推导出的 TMDB 本季集号。调用方必须先通过
     * EpisodeSeasonPolicy.mapFlatEpisodeNumber 取得明确映射，不能按列表位置猜测。
     */
    public static boolean shouldApplyMapped(Episode episode, TmdbEpisode tmdbEpisode, int mappedSeason, int mappedNumber) {
        return episode != null
                && tmdbEpisode != null
                && episode.getNumber() > 0
                && mappedSeason >= 0
                && mappedNumber > 0
                && tmdbEpisode.getSeasonNumber() == mappedSeason
                && tmdbEpisode.getNumber() == mappedNumber
                && tmdbEpisode.getSeasonNumber() >= 0;
    }

    public static boolean shouldApply(Episode episode, int number, String tmdbTitle) {
        return true;
    }
}
