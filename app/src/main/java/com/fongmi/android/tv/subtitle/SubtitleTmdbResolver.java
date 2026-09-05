package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.subtitle.model.ResolvedMediaIdentity;
import com.fongmi.android.tv.subtitle.model.SubtitleRequest;

public final class SubtitleTmdbResolver {

    private final SubtitleTitleParser parser;

    public SubtitleTmdbResolver() {
        this(new SubtitleTitleParser());
    }

    SubtitleTmdbResolver(SubtitleTitleParser parser) {
        this.parser = parser;
    }

    public ResolvedMediaIdentity resolve(SubtitleRequest request) {
        TmdbItem tmdbItem = request.getTmdbItem();
        TmdbEpisode tmdbEpisode = request.getTmdbEpisode();
        if (tmdbItem == null && request.isAllowTmdbLookup() && !SubtitleStrings.isEmpty(request.getSiteKey()) && !SubtitleStrings.isEmpty(request.getVodId())) {
            tmdbItem = Setting.getTmdbMatchCache().find(request.getSiteKey(), request.getVodId(), request.getVodName());
        }

        ResolvedMediaIdentity.Builder builder = ResolvedMediaIdentity.builder();
        if (tmdbItem != null) {
            builder.tmdbId(tmdbItem.getTmdbId())
                    .mediaType(tmdbItem.getMediaType())
                    .canonicalTitle(tmdbItem.getTitle())
                    .year(parser.firstYear(tmdbItem.getSubtitle()))
                    .fromCache(request.getTmdbItem() == null);
        }
        if (tmdbEpisode != null) {
            builder.tmdbEpisodeId(tmdbEpisode.getTmdbId())
                    .episodeTitle(tmdbEpisode.getTitle())
                    .seasonNumber(tmdbEpisode.getSeasonNumber())
                    .episodeNumber(tmdbEpisode.getNumber())
                    .fromBoundEpisode(true);
        }

        if (tmdbItem == null) {
            String canonical = parser.cleanTitle(request.getVodName());
            int year = parser.firstYear(request.getVodYear());
            if (year <= 0) year = parser.firstYear(request.getVodName());
            if (year <= 0) year = parser.firstYear(request.getVodRemarks());
            int seasonNumber = request.getSeasonNumber() > 0 ? request.getSeasonNumber() : parser.seasonNumber(request.getVodName());
            if (seasonNumber <= 0) seasonNumber = parser.seasonNumber(request.getEpisodeName());
            int episodeNumber = request.getEpisodeNumber() > 0 ? request.getEpisodeNumber() : parser.episodeNumber(request.getEpisodeName());
            String mediaType = seasonNumber > 0 || episodeNumber > 0 ? "tv" : "movie";
            builder.mediaType(mediaType)
                    .canonicalTitle(canonical)
                    .year(year)
                    .seasonNumber(seasonNumber)
                    .episodeNumber(episodeNumber)
                    .episodeTitle(parser.cleanTitle(request.getEpisodeName()));
        }

        return builder.build();
    }
}
