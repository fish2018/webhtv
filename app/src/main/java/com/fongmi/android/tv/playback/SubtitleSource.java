package com.fongmi.android.tv.playback;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Sub;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

/**
 * 一次外挂字幕选择的来源标识。
 *
 * <p>保存来源而不是 mpv 的 sid：外挂字幕的轨道是运行时 {@code sub-add} 出来的，
 * 退出播放器后那条轨道就不存在了，下次起播时同一个 sid 会落到别的轨道上。
 * 来源（路径或 URL）是跨会话稳定的，而且三个内核都能直接消费——恢复时走
 * {@code PlaySpec.setSub()}，和用户手动加载完全同一条链路。
 *
 * <p>字段命名与 {@code docs/unified-media-identity-cross-site-resume.md} 里设计的
 * {@code SubtitleSnapshot} 对齐，将来落地跨站旁路表时可以平移。
 */
public final class SubtitleSource {

    public static final String MODE_EXTERNAL = "external";
    public static final String MODE_DISABLED = "disabled";

    private static final Gson GSON = new Gson();

    @SerializedName("mode")
    private String mode;
    @SerializedName("url")
    private String url;
    @SerializedName("name")
    private String name;
    @SerializedName("lang")
    private String lang;
    @SerializedName("format")
    private String format;
    @SerializedName("episodeUrl")
    private String episodeUrl;
    @SerializedName("time")
    private long time;

    public SubtitleSource() {
    }

    /**
     * 从一次字幕选择构造来源记录。
     *
     * @param episodeUrl 当前集的播放地址，恢复时用它挡住「上一集的字幕挂到下一集」
     */
    public static SubtitleSource of(Sub sub, String episodeUrl) {
        if (sub == null || TextUtils.isEmpty(sub.getUrl())) return null;
        SubtitleSource source = new SubtitleSource();
        source.mode = MODE_EXTERNAL;
        source.url = sub.getUrl();
        source.name = sub.getName();
        source.lang = sub.getLang();
        source.format = sub.getFormat();
        source.episodeUrl = episodeUrl == null ? "" : episodeUrl;
        source.time = System.currentTimeMillis();
        return source;
    }

    /** 解析失败返回 null——一条脏数据不能让整条历史不可用。 */
    public static SubtitleSource decode(String json) {
        if (TextUtils.isEmpty(json)) return null;
        try {
            SubtitleSource source = GSON.fromJson(json, SubtitleSource.class);
            return source != null && source.isUsable() ? source : null;
        } catch (JsonSyntaxException | IllegalStateException e) {
            return null;
        }
    }

    public static String encode(SubtitleSource source) {
        return source == null || !source.isUsable() ? "" : GSON.toJson(source);
    }

    public Sub toSub() {
        return isUsable() ? Sub.create(getName(), getUrl(), getLang(), getFormat()) : null;
    }

    public boolean isUsable() {
        return isExternal() && !TextUtils.isEmpty(url);
    }

    public boolean isExternal() {
        return !MODE_DISABLED.equals(mode);
    }

    /**
     * 远端字幕每次起播都能重新取，本地文件却可能已被删除或随缓存清理消失。
     * 恢复时据此决定是否要先确认文件还在。
     */
    public boolean isRemote() {
        return getUrl().contains("://");
    }

    public String getMode() {
        return mode == null ? MODE_EXTERNAL : mode;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public String getLang() {
        return lang == null ? "" : lang;
    }

    public String getFormat() {
        return format == null ? "" : format;
    }

    public String getEpisodeUrl() {
        return episodeUrl == null ? "" : episodeUrl;
    }

    public long getTime() {
        return time;
    }
}
