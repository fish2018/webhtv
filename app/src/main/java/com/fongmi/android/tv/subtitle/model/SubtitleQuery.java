package com.fongmi.android.tv.subtitle.model;

public final class SubtitleQuery {

    private final String key;
    private final String text;
    private final String language;
    private final SubtitleQuerySource source;
    private final SubtitleStrictness strictness;
    private final int year;
    private final int seasonNumber;
    private final int episodeNumber;

    public SubtitleQuery(String key, String text, String language, SubtitleQuerySource source, SubtitleStrictness strictness, int year, int seasonNumber, int episodeNumber) {
        this.key = key == null ? "" : key;
        this.text = text == null ? "" : text;
        this.language = language == null ? "" : language;
        this.source = source == null ? SubtitleQuerySource.SOURCE_TITLE : source;
        this.strictness = strictness == null ? SubtitleStrictness.NORMAL : strictness;
        this.year = year;
        this.seasonNumber = seasonNumber;
        this.episodeNumber = episodeNumber;
    }

    public String getKey() {
        return key;
    }

    public String getText() {
        return text;
    }

    public String getLanguage() {
        return language;
    }

    public SubtitleQuerySource getSource() {
        return source;
    }

    public SubtitleStrictness getStrictness() {
        return strictness;
    }

    public int getYear() {
        return year;
    }

    public int getSeasonNumber() {
        return seasonNumber;
    }

    public int getEpisodeNumber() {
        return episodeNumber;
    }
}
