package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleQuery;
import com.fongmi.android.tv.subtitle.model.SubtitleQuerySource;
import com.fongmi.android.tv.subtitle.model.SubtitleStrictness;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SubtitleQueryPlanner {

    public List<SubtitleQuery> build(SubtitleContext context) {
        List<SubtitleQuery> queries = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();
        if (context == null || SubtitleStrings.isEmpty(context.getCanonicalTitle())) return queries;

        if ("tv".equalsIgnoreCase(context.getMediaType())) {
            addEpisodeQueries(context, queries, dedupe);
        } else {
            addMovieQueries(context, queries, dedupe);
        }
        return queries;
    }

    public List<SubtitleQuery> buildManual(SubtitleContext context, String keyword) {
        String text = keyword == null ? "" : keyword.trim();
        if (SubtitleStrings.isEmpty(text)) return build(context);
        List<SubtitleQuery> queries = new ArrayList<>();
        int year = context == null ? 0 : context.getYear();
        int season = context == null ? -1 : context.getSeasonNumber();
        int episode = context == null ? -1 : context.getEpisodeNumber();
        String language = context == null || SubtitleStrings.isEmpty(context.getPreferredLanguage()) ? "zh" : context.getPreferredLanguage();
        queries.add(new SubtitleQuery(text, text, language, SubtitleQuerySource.SOURCE_TITLE, SubtitleStrictness.FALLBACK, year, season, episode));
        return queries;
    }

    private void addMovieQueries(SubtitleContext context, List<SubtitleQuery> queries, Set<String> dedupe) {
        add(queries, dedupe, new SubtitleQuery(key(context.getCanonicalTitle(), context.getYear()), withYear(context.getCanonicalTitle(), context.getYear()), "zh", SubtitleQuerySource.TMDB_TITLE, context.hasTmdbIdentity() ? SubtitleStrictness.STRICT : SubtitleStrictness.NORMAL, context.getYear(), -1, -1));
        if (!SubtitleStrings.isEmpty(context.getOriginalTitle()) && !context.getOriginalTitle().equals(context.getCanonicalTitle())) {
            add(queries, dedupe, new SubtitleQuery(key(context.getOriginalTitle(), context.getYear()), withYear(context.getOriginalTitle(), context.getYear()), "zh", SubtitleQuerySource.TMDB_ORIGINAL_TITLE, SubtitleStrictness.NORMAL, context.getYear(), -1, -1));
        }
        add(queries, dedupe, new SubtitleQuery(key(context.getCanonicalTitle(), 0), context.getCanonicalTitle(), "zh", SubtitleQuerySource.SOURCE_TITLE, SubtitleStrictness.FALLBACK, context.getYear(), -1, -1));
        for (String alias : context.getAliases()) add(queries, dedupe, new SubtitleQuery(key(alias, 0), alias, "zh", SubtitleQuerySource.SOURCE_TITLE, SubtitleStrictness.FALLBACK, context.getYear(), -1, -1));
    }

    private void addEpisodeQueries(SubtitleContext context, List<SubtitleQuery> queries, Set<String> dedupe) {
        if (context.getSeasonNumber() > 0 && context.getEpisodeNumber() > 0) {
            String sxe = String.format("%s S%02dE%02d", context.getCanonicalTitle(), context.getSeasonNumber(), context.getEpisodeNumber());
            add(queries, dedupe, new SubtitleQuery(key(sxe, 0), sxe, "zh", SubtitleQuerySource.EPISODE_CODE, context.hasTmdbIdentity() ? SubtitleStrictness.STRICT : SubtitleStrictness.NORMAL, context.getYear(), context.getSeasonNumber(), context.getEpisodeNumber()));
        }
        if (context.getEpisodeNumber() > 0) {
            String numbered = context.getCanonicalTitle() + " 第" + context.getEpisodeNumber() + "集";
            add(queries, dedupe, new SubtitleQuery(key(numbered, 0), numbered, "zh", SubtitleQuerySource.EPISODE_CODE, SubtitleStrictness.NORMAL, context.getYear(), context.getSeasonNumber(), context.getEpisodeNumber()));
        }
        if (!SubtitleStrings.isEmpty(context.getEpisodeTitle())) {
            String title = context.getCanonicalTitle() + " " + context.getEpisodeTitle();
            add(queries, dedupe, new SubtitleQuery(key(title, 0), title, "zh", SubtitleQuerySource.EPISODE_TITLE, SubtitleStrictness.NORMAL, context.getYear(), context.getSeasonNumber(), context.getEpisodeNumber()));
        }
        add(queries, dedupe, new SubtitleQuery(key(context.getCanonicalTitle(), 0), context.getCanonicalTitle(), "zh", SubtitleQuerySource.SOURCE_TITLE, SubtitleStrictness.FALLBACK, context.getYear(), context.getSeasonNumber(), context.getEpisodeNumber()));
        if (!SubtitleStrings.isEmpty(context.getOriginalTitle()) && !context.getOriginalTitle().equals(context.getCanonicalTitle())) {
            add(queries, dedupe, new SubtitleQuery(key(context.getOriginalTitle(), 0), context.getOriginalTitle(), "zh", SubtitleQuerySource.TMDB_ORIGINAL_TITLE, SubtitleStrictness.FALLBACK, context.getYear(), context.getSeasonNumber(), context.getEpisodeNumber()));
        }
    }

    private void add(List<SubtitleQuery> queries, Set<String> dedupe, SubtitleQuery query) {
        if (query == null || SubtitleStrings.isEmpty(query.getText())) return;
        String value = query.getText().trim();
        if (!dedupe.add(value)) return;
        queries.add(query);
    }

    private String key(String title, int year) {
        return title + "|" + year;
    }

    private String withYear(String title, int year) {
        return year > 0 ? title + " " + year : title;
    }
}
