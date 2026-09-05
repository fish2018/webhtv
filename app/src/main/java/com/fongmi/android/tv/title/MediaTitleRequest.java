package com.fongmi.android.tv.title;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MediaTitleRequest {

    private String siteKey;
    private String vodId;
    private String rawTitle;
    private String rawRemarks;
    private String searchKeyword;
    private String vodYear;
    private String episodeName;
    private String flag;
    private String source;
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
}
