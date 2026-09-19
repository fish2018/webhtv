package com.github.catvod.crawler.diagnostics;

import java.util.Locale;

/** Stable persisted bits. A missing preference enables every category. */
public final class DiagnosticCategories {
    public enum Category {
        PLAYBACK(1, "播放流程"), NETWORK(2, "网络请求"), VIDEO(4, "视频画面"),
        AUDIO(8, "音频输出"), SUBTITLE(16, "字幕脚本"), SYSTEM(32, "内核设备");
        public final int bit;
        public final String title;
        Category(int bit, String title) { this.bit = bit; this.title = title; }
    }
    public static final int ALL = 63;
    private DiagnosticCategories() {}

    public static Category event(String name) {
        if (name.startsWith("diag.")) return Category.SYSTEM; // 诊断控制事件归内核设备，受开关控制。
        if (name.startsWith("audio.") || name.equals("mpv.audio.path")) return Category.AUDIO;
        if (name.startsWith("video.") || name.startsWith("surface.") || name.contains("display") || name.equals("mpv.video.path")) return Category.VIDEO;
        if (name.startsWith("input.") || name.equals("resolve.result")) return Category.NETWORK;
        if (name.startsWith("env.") || name.startsWith("process.") || name.startsWith("mpv.") || name.equals("play.resources")) return Category.SYSTEM;
        return Category.PLAYBACK;
    }

    public static Category nativePrefix(String prefix) {
        String value = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        if (value.equals("ad") || value.startsWith("ao") || value.startsWith("af") || value.startsWith("ffmpeg/audio")) return Category.AUDIO;
        if (value.equals("vd") || value.startsWith("vo") || value.startsWith("vf") || value.startsWith("ffmpeg/video")) return Category.VIDEO;
        if (value.startsWith("sub") || value.startsWith("osd") || value.startsWith("lua")) return Category.SUBTITLE;
        if (value.startsWith("demux") || value.contains("http") || value.startsWith("stream")) return Category.NETWORK;
        return Category.SYSTEM;
    }

    public static Category text(String tag) {
        String value = tag == null ? "" : tag.toLowerCase(Locale.ROOT);
        if (value.equals("quickjs") || value.equals("python-spider")) return Category.NETWORK;
        if (value.contains("subtitle") || value.contains("libass") || value.contains("script") || value.equals("ass")) return Category.SUBTITLE;
        if (value.contains("audio") || value.contains("audiotrack")) return Category.AUDIO;
        if (value.contains("surface") || value.contains("video") || value.contains("render") || value.contains("codec")) return Category.VIDEO;
        if (value.contains("http") || value.contains("proxy") || value.contains("network") || value.contains("okhttp") || value.contains("spider") || value.contains("resolve") || value.contains("cache") || value.contains("request")) return Category.NETWORK;
        if (value.contains("playback") || value.contains("player") || value.contains("trace") || value.contains("track")) return Category.PLAYBACK;
        return Category.SYSTEM;
    }

    public static boolean accepts(int mask, Category category) { return category != null && (mask & category.bit) != 0; }
    public static String summary(int mask) {
        StringBuilder result = new StringBuilder();
        for (Category category : Category.values()) {
            if (result.length() > 0) result.append(',');
            result.append(category.name().toLowerCase(Locale.ROOT)).append('=').append(accepts(mask, category) ? "on" : "off");
        }
        return result.toString();
    }
}
