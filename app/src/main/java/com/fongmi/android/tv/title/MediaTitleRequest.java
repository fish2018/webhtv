package com.fongmi.android.tv.title;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MediaTitleRequest {

    private static final int MAX_CONTEXT_TITLES = 16;

    private String siteKey;
    private String vodId;
    private String rawTitle;
    private String rawRemarks;
    private String searchKeyword;
    private String vodYear;
    private String episodeName;
    private String flag;
    private String source;
    private String folderName;
    private List<String> contextTitles;
    private int tmdbId;
    private int tmdbSeasonNumber;
    private List<MediaTitleLearningExample> learningExamples;
    private boolean allowAi;

    private MediaTitleRequest(Builder builder) {
        this.siteKey = clean(builder.siteKey);
        this.vodId = clean(builder.vodId);
        this.rawTitle = clean(builder.rawTitle);
        this.rawRemarks = clean(builder.rawRemarks);
        this.searchKeyword = clean(builder.searchKeyword);
        this.vodYear = clean(builder.vodYear);
        this.episodeName = clean(builder.episodeName);
        this.flag = clean(builder.flag);
        this.source = clean(builder.source);
        this.folderName = cleanFolderName(builder.folderName);
        this.contextTitles = cleanContextTitles(builder.contextTitles);
        this.tmdbId = builder.tmdbId;
        this.tmdbSeasonNumber = builder.tmdbSeasonNumber;
        this.learningExamples = new ArrayList<>(builder.learningExamples);
        this.allowAi = builder.allowAi;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSiteKey() {
        return siteKey;
    }

    public String getVodId() {
        return vodId;
    }

    public String getRawTitle() {
        return rawTitle;
    }

    public String getRawRemarks() {
        return rawRemarks;
    }

    public String getSearchKeyword() {
        return searchKeyword;
    }

    public String getVodYear() {
        return vodYear;
    }

    public String getEpisodeName() {
        return episodeName;
    }

    public String getFlag() {
        return flag;
    }

    public String getSource() {
        return source;
    }

    /**
     * Returns only the final directory component.  Title recognition must not
     * send a complete local path or URL to an AI provider.
     */
    public String getFolderName() {
        return folderName;
    }

    /**
     * Returns bounded, de-duplicated sibling/context titles in caller order.
     */
    public List<String> getContextTitles() {
        return Collections.unmodifiableList(contextTitles);
    }

    public int getTmdbId() {
        return tmdbId;
    }

    public int getTmdbSeasonNumber() {
        return tmdbSeasonNumber;
    }

    public List<MediaTitleLearningExample> getLearningExamples() {
        return Collections.unmodifiableList(learningExamples);
    }

    public boolean isAllowAi() {
        return allowAi;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {

        private String siteKey;
        private String vodId;
        private String rawTitle;
        private String rawRemarks;
        private String searchKeyword;
        private String vodYear;
        private String episodeName;
        private String flag;
        private String source;
        private String folderName;
        private List<String> contextTitles = new ArrayList<>();
        private int tmdbId;
        private int tmdbSeasonNumber;
        private final List<MediaTitleLearningExample> learningExamples = new ArrayList<>();
        private boolean allowAi;

        public Builder siteKey(String siteKey) {
            this.siteKey = siteKey;
            return this;
        }

        public Builder vodId(String vodId) {
            this.vodId = vodId;
            return this;
        }

        public Builder rawTitle(String rawTitle) {
            this.rawTitle = rawTitle;
            return this;
        }

        public Builder rawRemarks(String rawRemarks) {
            this.rawRemarks = rawRemarks;
            return this;
        }

        public Builder searchKeyword(String searchKeyword) {
            this.searchKeyword = searchKeyword;
            return this;
        }

        public Builder vodYear(String vodYear) {
            this.vodYear = vodYear;
            return this;
        }

        public Builder episodeName(String episodeName) {
            this.episodeName = episodeName;
            return this;
        }

        public Builder flag(String flag) {
            this.flag = flag;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder folderName(String folderName) {
            this.folderName = folderName;
            return this;
        }

        public Builder contextTitles(List<String> contextTitles) {
            this.contextTitles.clear();
            if (contextTitles != null) this.contextTitles.addAll(contextTitles);
            return this;
        }

        public Builder tmdbId(int tmdbId) {
            this.tmdbId = tmdbId;
            return this;
        }

        public Builder tmdbSeasonNumber(int tmdbSeasonNumber) {
            this.tmdbSeasonNumber = tmdbSeasonNumber;
            return this;
        }

        public Builder learningExamples(List<MediaTitleLearningExample> values) {
            learningExamples.clear();
            if (values != null) for (MediaTitleLearningExample value : values) if (value != null) learningExamples.add(value);
            return this;
        }

        public Builder allowAi(boolean allowAi) {
            this.allowAi = allowAi;
            return this;
        }

        public MediaTitleRequest build() {
            return new MediaTitleRequest(this);
        }
    }

    private static String cleanFolderName(String value) {
        String text = clean(value).replace('\\', '/');
        while (text.length() > 1 && text.endsWith("/")) text = text.substring(0, text.length() - 1);
        int slash = text.lastIndexOf('/');
        if (slash >= 0) text = text.substring(slash + 1);
        return text.trim();
    }

    private static List<String> cleanContextTitles(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values == null) return result;
        for (String value : values) {
            String text = clean(value).replaceAll("(?i)https?://\\S+", " ").trim();
            String pathText = text.replace('\\', '/');
            int slash = pathText.lastIndexOf('/');
            if (slash >= 0) text = pathText.substring(slash + 1).trim();
            if (text.isEmpty() || text.length() > 160) continue;
            boolean exists = false;
            for (String item : result) if (item.equalsIgnoreCase(text)) {
                exists = true;
                break;
            }
            if (!exists) result.add(text);
            if (result.size() >= MAX_CONTEXT_TITLES) break;
        }
        return result;
    }
}
