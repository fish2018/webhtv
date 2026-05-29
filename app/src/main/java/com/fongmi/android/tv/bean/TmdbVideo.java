package com.fongmi.android.tv.bean;

import android.text.TextUtils;

public class TmdbVideo {

    private final String name;
    private final String subtitle;
    private final String thumbnailUrl;
    private final String videoUrl;

    public TmdbVideo(String name, String subtitle, String thumbnailUrl, String videoUrl) {
        this.name = name;
        this.subtitle = subtitle;
        this.thumbnailUrl = thumbnailUrl;
        this.videoUrl = videoUrl;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public String getSubtitle() {
        return TextUtils.isEmpty(subtitle) ? "" : subtitle;
    }

    public String getThumbnailUrl() {
        return TextUtils.isEmpty(thumbnailUrl) ? "" : thumbnailUrl;
    }

    public String getVideoUrl() {
        return TextUtils.isEmpty(videoUrl) ? "" : videoUrl;
    }
}
