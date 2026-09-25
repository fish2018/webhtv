package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.subtitle.model.ResolvedMediaIdentity;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleRequest;
import com.fongmi.android.tv.title.MediaTitleLearningExample;
import com.fongmi.android.tv.title.MediaTitleRequest;
import com.fongmi.android.tv.title.MediaTitleResolution;
import com.fongmi.android.tv.title.MediaTitleResolver;

import java.net.URI;
import java.util.Locale;

public final class SubtitleContextBuilder {

    private final SubtitleTmdbResolver resolver;
    private final SubtitleTitleParser parser;

    public SubtitleContextBuilder() {
        this(new SubtitleTmdbResolver(), new SubtitleTitleParser());
    }

    SubtitleContextBuilder(SubtitleTmdbResolver resolver, SubtitleTitleParser parser) {
        this.resolver = resolver;
        this.parser = parser;
    }

    public SubtitleContext build(SubtitleRequest request) {
        return build(request, false);
    }

    public SubtitleContext buildWithAiFallback(SubtitleRequest request) {
        return build(request, true);
    }

    private SubtitleContext build(SubtitleRequest request, boolean forceAi) {
        ResolvedMediaIdentity identity = resolver.resolve(request);
        MediaTitleRequest titleRequest = MediaTitleRequest.builder()
                .siteKey(request.getSiteKey())
                .vodId(request.getVodId())
                .rawTitle(request.getVodName())
                .rawRemarks(request.getVodRemarks())
                .vodYear(request.getVodYear())
                .episodeName(request.getEpisodeName())
                .source(MediaTitleLearningExample.SOURCE_SUBTITLE_AUTO)
                .allowAi(true)
                .build();
        MediaTitleResolver titleResolver = new MediaTitleResolver();
        MediaTitleResolution titleResolution = forceAi ? titleResolver.resolveWithAiFallback(titleRequest) : titleResolver.resolve(titleRequest);
        String canonicalTitle = forceAi && !identity.hasTmdbIdentity() && !SubtitleStrings.isEmpty(titleResolution.getCanonicalTitle()) ? titleResolution.getCanonicalTitle() : !SubtitleStrings.isEmpty(identity.getCanonicalTitle()) ? identity.getCanonicalTitle() : !SubtitleStrings.isEmpty(titleResolution.getCanonicalTitle()) ? titleResolution.getCanonicalTitle() : parser.cleanTitle(request.getVodName());
        String originalTitle = identity.getOriginalTitle();
        int year = identity.getYear() > 0 ? identity.getYear() : parser.firstYear(request.getVodYear());
        int seasonNumber = identity.getSeasonNumber() > 0 ? identity.getSeasonNumber() : request.getSeasonNumber();
        int episodeNumber = identity.getEpisodeNumber() > 0 ? identity.getEpisodeNumber() : request.getEpisodeNumber();
        String episodeTitle = !SubtitleStrings.isEmpty(identity.getEpisodeTitle()) ? identity.getEpisodeTitle() : parser.cleanTitle(request.getEpisodeName());
        String originalLanguage = request.getTmdbItem() == null ? "" : request.getTmdbItem().getOriginalLanguage();
        String originCountry = request.getTmdbItem() == null ? "" : request.getTmdbItem().getOriginCountry();
        String mediaPath = localMediaPath(request.getPlayUrl());

        SubtitleContext.Builder builder = SubtitleContext.builder()
                .playbackKey(request.getPlaybackKey())
                .siteKey(request.getSiteKey())
                .vodId(request.getVodId())
                .mediaType(identity.getMediaType())
                .mediaPath(mediaPath)
                .canonicalTitle(canonicalTitle)
                .originalTitle(originalTitle)
                .year(year)
                .seasonNumber(seasonNumber)
                .episodeNumber(episodeNumber)
                .episodeTitle(episodeTitle)
                .preferredLanguage(request.getPreferredLanguage())
                .originalLanguage(originalLanguage)
                .originCountry(originCountry)
                .identity(identity)
                .networkStream(SubtitleStrings.isEmpty(mediaPath));

        for (String alias : parser.aliases(request.getVodName(), request.getVodRemarks(), request.getEpisodeName())) builder.addAlias(alias);
        for (String alias : titleResolution.queryTitles()) builder.addAlias(alias);
        return builder.build();
    }

    private String localMediaPath(String value) {
        if (SubtitleStrings.isEmpty(value)) return "";
        String text = value.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("file://")) {
            try {
                String path = URI.create(text).getPath();
                return path == null ? "" : path;
            } catch (Exception ignored) {
                return text.substring("file://".length());
            }
        }
        if (lower.contains("://")) return "";
        if (text.startsWith("/") || text.matches("^[A-Za-z]:[\\\\/].*")) return text;
        return "";
    }
}
