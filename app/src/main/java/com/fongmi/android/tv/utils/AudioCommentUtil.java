package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class AudioCommentUtil {

    private static final String TAG = "audio-comment";
    private static final Map<String, String> NETEASE_HEADERS = new HashMap<>();

    static {
        NETEASE_HEADERS.put("User-Agent", "Mozilla/5.0");
        NETEASE_HEADERS.put("Referer", "https://music.163.com/");
    }

    private AudioCommentUtil() {
    }

    public static Page load(String title, String subtitle, long durationMs, boolean hot, int limit) {
        try {
            LyricUtil.Track track = LyricUtil.findNeteaseTrack(title, subtitle, durationMs);
            if (track == null || TextUtils.isEmpty(track.getId())) return Page.message(hot, "未匹配到评论");
            String url = "https://music.163.com/api/v1/resource/comments/R_SO_4_" + track.getId() + "?limit=" + Math.max(1, limit) + "&offset=0";
            JsonObject object = Json.safeObject(Json.parse(OkHttp.string(url, NETEASE_HEADERS)));
            JsonArray source = commentsArray(object, hot);
            List<Comment> comments = new ArrayList<>();
            for (JsonElement element : source) {
                Comment comment = Comment.from(Json.safeObject(element));
                if (!TextUtils.isEmpty(comment.content)) comments.add(comment);
            }
            int total = safeInt(object, "total");
            SpiderDebug.log(TAG, "评论加载 source=netease title=%s subtitle=%s track=%s hot=%s total=%d count=%d", title, subtitle, track.getId(), hot, total, comments.size());
            return new Page(track, hot, total, comments, comments.isEmpty() ? (hot ? "暂无热门评论" : "暂无最新评论") : "");
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "评论加载异常 title=%s subtitle=%s error=%s", title, subtitle, e.getMessage());
            return Page.message(hot, "评论加载失败");
        }
    }

    private static JsonArray commentsArray(JsonObject object, boolean hot) {
        JsonArray array = array(object, hot ? "hotComments" : "comments");
        if (hot && array.isEmpty()) array = array(object, "topComments");
        return array;
    }

    private static JsonArray array(JsonObject object, String key) {
        try {
            return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
        } catch (Throwable e) {
            return new JsonArray();
        }
    }

    private static JsonObject object(JsonObject parent, String key) {
        try {
            return parent.has(key) ? Json.safeObject(parent.get(key)) : new JsonObject();
        } catch (Throwable e) {
            return new JsonObject();
        }
    }

    private static int safeInt(JsonObject object, String key) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : 0;
        } catch (Throwable e) {
            return 0;
        }
    }

    private static long safeLong(JsonObject object, String key) {
        try {
            return object.has(key) ? object.get(key).getAsLong() : 0L;
        } catch (Throwable e) {
            return 0L;
        }
    }

    private static String secure(String url) {
        return TextUtils.isEmpty(url) ? "" : url.replace("http://", "https://");
    }

    private static String time(long value) {
        if (value <= 0) return "";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(value));
    }

    public static final class Page {

        private final LyricUtil.Track track;
        private final List<Comment> comments;
        private final String message;
        private final boolean hot;
        private final int total;

        private Page(LyricUtil.Track track, boolean hot, int total, List<Comment> comments, String message) {
            this.track = track;
            this.hot = hot;
            this.total = total;
            this.comments = comments == null ? new ArrayList<>() : comments;
            this.message = Objects.toString(message, "");
        }

        private static Page message(boolean hot, String message) {
            return new Page(null, hot, 0, new ArrayList<>(), message);
        }

        public List<Comment> getComments() {
            return comments;
        }

        public String getMessage() {
            return TextUtils.isEmpty(message) ? (hot ? "暂无热门评论" : "暂无最新评论") : message;
        }

        public String getMeta() {
            if (track == null) return "网易云";
            String count = total > 0 ? " · " + total + " 条评论" : "";
            return "网易云 · " + track.getLabel() + count;
        }
    }

    public static final class Comment {

        private final String user;
        private final String avatar;
        private final String content;
        private final String time;
        private final String location;
        private final String reply;
        private final int liked;

        private Comment(String user, String avatar, String content, String time, String location, String reply, int liked) {
            this.user = Objects.toString(user, "");
            this.avatar = secure(avatar);
            this.content = Objects.toString(content, "");
            this.time = Objects.toString(time, "");
            this.location = Objects.toString(location, "");
            this.reply = Objects.toString(reply, "");
            this.liked = liked;
        }

        private static Comment from(JsonObject item) {
            JsonObject user = object(item, "user");
            JsonObject ip = object(item, "ipLocation");
            return new Comment(
                    Json.safeString(user, "nickname"),
                    Json.safeString(user, "avatarUrl"),
                    Json.safeString(item, "content"),
                    time(safeLong(item, "time")),
                    Json.safeString(ip, "location"),
                    reply(item),
                    safeInt(item, "likedCount")
            );
        }

        private static String reply(JsonObject item) {
            JsonArray replies = array(item, "beReplied");
            if (replies.isEmpty()) return "";
            JsonObject reply = Json.safeObject(replies.get(0));
            String content = Json.safeString(reply, "content");
            if (TextUtils.isEmpty(content)) return "";
            String user = Json.safeString(object(reply, "user"), "nickname");
            return TextUtils.isEmpty(user) ? content : "回复 @" + user + ": " + content;
        }

        public String getUser() {
            return TextUtils.isEmpty(user) ? "网易云用户" : user;
        }

        public String getAvatar() {
            return avatar;
        }

        public String getContent() {
            return content;
        }

        public String getReply() {
            return reply;
        }

        public String getMeta() {
            List<String> parts = new ArrayList<>();
            if (!TextUtils.isEmpty(time)) parts.add(time);
            if (!TextUtils.isEmpty(location)) parts.add(location);
            if (liked > 0) parts.add(liked + " 赞");
            return TextUtils.join(" · ", parts);
        }
    }
}
