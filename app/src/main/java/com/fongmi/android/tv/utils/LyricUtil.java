package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Util;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class LyricUtil {

    private static final String TAG = "audio-lyric";
    private static final String CACHE_PREFIX = "audio_lyric_";
    private static final long DURATION_TOLERANCE_MS = 10000L;

    private static final Map<String, String> LRCLIB_HEADERS = new HashMap<>();
    private static final Map<String, String> NETEASE_HEADERS = new HashMap<>();

    static {
        LRCLIB_HEADERS.put("User-Agent", "webhtv/1.0 (https://github.com/)");
        NETEASE_HEADERS.put("User-Agent", "Mozilla/5.0");
        NETEASE_HEADERS.put("Referer", "https://music.163.com/");
    }

    public static String find(String title, String subtitle, long durationMs) {
        try {
            List<Query> queries = buildQueries(title, subtitle);
            if (queries.isEmpty()) {
                SpiderDebug.log(TAG, "歌词匹配跳过：标题为空 title=%s subtitle=%s", title, subtitle);
                return "";
            }
            String cacheKey = cacheKey(title, subtitle, durationMs);
            String cached = Prefers.getString(cacheKey);
            if (TextUtils.isEmpty(cached) && durationMs > 0) cached = Prefers.getString(cacheKey(title, subtitle, 0));
            if (!TextUtils.isEmpty(cached)) {
                SpiderDebug.log(TAG, "歌词匹配命中缓存 title=%s subtitle=%s", title, subtitle);
                return cached;
            }
            SpiderDebug.log(TAG, "开始自动匹配歌词 title=%s subtitle=%s duration=%sms queryCount=%d", title, subtitle, durationMs, queries.size());
            String lyric = findFromLrclib(queries, durationMs);
            if (TextUtils.isEmpty(lyric)) lyric = findFromNetease(queries, durationMs);
            if (!TextUtils.isEmpty(lyric)) {
                Prefers.put(cacheKey, lyric);
                SpiderDebug.log(TAG, "歌词匹配成功并写入缓存 title=%s subtitle=%s length=%d", title, subtitle, lyric.length());
            } else {
                SpiderDebug.log(TAG, "歌词匹配失败 title=%s subtitle=%s", title, subtitle);
            }
            return lyric;
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "歌词匹配异常 title=%s subtitle=%s error=%s", title, subtitle, e.getMessage());
            return "";
        }
    }

    public static String findArtwork(String title, String subtitle, long durationMs) {
        try {
            List<Query> queries = buildQueries(title, subtitle);
            if (queries.isEmpty()) return "";
            String cacheKey = CACHE_PREFIX + "art_" + Util.md5(normalize(title) + "|" + normalize(subtitle) + "|" + (durationMs > 0 ? durationMs / 10000 : 0));
            String cached = Prefers.getString(cacheKey);
            if (!TextUtils.isEmpty(cached)) {
                SpiderDebug.log(TAG, "封面匹配命中缓存 title=%s subtitle=%s", title, subtitle);
                return cached;
            }
            SpiderDebug.log(TAG, "开始自动匹配封面 title=%s subtitle=%s duration=%sms queryCount=%d", title, subtitle, durationMs, queries.size());
            for (Query query : queries) {
                if (TextUtils.isEmpty(query.artist) && durationMs <= 0) continue;
                String artwork = findArtworkFromNetease(query, durationMs);
                if (TextUtils.isEmpty(artwork)) continue;
                Prefers.put(cacheKey, artwork);
                SpiderDebug.log(TAG, "封面匹配成功 title=%s subtitle=%s artwork=%s", title, subtitle, artwork);
                return artwork;
            }
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "封面匹配异常 title=%s subtitle=%s error=%s", title, subtitle, e.getMessage());
        }
        return "";
    }

    public static Track findNeteaseTrack(String title, String subtitle, long durationMs) {
        try {
            List<Query> queries = buildQueries(title, subtitle);
            for (Query query : queries) {
                String keyword = TextUtils.isEmpty(query.artist) ? query.song : query.artist + " " + query.song;
                String url = "https://music.163.com/api/search/get/web?s=" + encode(keyword) + "&type=1&limit=10";
                JsonObject object = Json.safeObject(Json.parse(OkHttp.string(url, NETEASE_HEADERS)));
                JsonArray songs = object.has("result") && object.get("result").isJsonObject() && object.getAsJsonObject("result").has("songs") ? object.getAsJsonObject("result").getAsJsonArray("songs") : new JsonArray();
                SpiderDebug.log(TAG, "网易云歌曲查询 query=%s/%s result=%d", query.song, query.artist, songs.size());
                List<Candidate> matches = new ArrayList<>();
                for (JsonElement element : songs) {
                    Candidate candidate = Candidate.netease(Json.safeObject(element));
                    if (candidate.matches(query, durationMs)) matches.add(candidate);
                }
                matches.sort((a, b) -> Long.compare(a.durationDiff(durationMs), b.durationDiff(durationMs)));
                if (!matches.isEmpty()) return matches.get(0).track();
            }
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "网易云歌曲匹配异常 title=%s subtitle=%s error=%s", title, subtitle, e.getMessage());
        }
        return null;
    }

    public static List<Option> findOptions(String title, String subtitle, long durationMs) {
        List<Option> options = new ArrayList<>();
        try {
            List<Query> queries = buildQueries(title, subtitle);
            for (Query query : queries) {
                addLrclibOptions(options, query, durationMs);
                addNeteaseOptions(options, query, durationMs);
                if (options.size() >= 12) break;
            }
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "手动歌词候选异常 title=%s subtitle=%s error=%s", title, subtitle, e.getMessage());
        }
        return options;
    }

    public static void save(String title, String subtitle, long durationMs, String lyric) {
        if (!TextUtils.isEmpty(lyric)) Prefers.put(cacheKey(title, subtitle, durationMs), lyric);
    }

    public static String loadProvided(String lrc) {
        if (TextUtils.isEmpty(lrc)) return "";
        if (!lrc.startsWith("http://") && !lrc.startsWith("https://")) return lrc;
        String text = OkHttp.string(lrc);
        SpiderDebug.log(TAG, "加载源站歌词 url=%s length=%d", lrc, text.length());
        return text;
    }

    private static String findFromLrclib(List<Query> queries, long durationMs) {
        for (Query query : queries) {
            String url = "https://lrclib.net/api/search?track_name=" + encode(query.song) + (TextUtils.isEmpty(query.artist) ? "" : "&artist_name=" + encode(query.artist));
            String text = OkHttp.string(url, LRCLIB_HEADERS);
            JsonArray array = safeArray(text);
            SpiderDebug.log(TAG, "LRCLIB 查询 query=%s/%s result=%d", query.song, query.artist, array.size());
            List<Candidate> matches = new ArrayList<>();
            for (JsonElement element : array) {
                JsonObject item = Json.safeObject(element);
                Candidate candidate = Candidate.lrclib(item);
                if (!candidate.matches(query, durationMs)) continue;
                if (!TextUtils.isEmpty(candidate.lyric)) matches.add(candidate);
            }
            matches.sort((a, b) -> Long.compare(a.durationDiff(durationMs), b.durationDiff(durationMs)));
            if (!matches.isEmpty()) {
                Candidate candidate = matches.get(0);
                SpiderDebug.log(TAG, "LRCLIB 命中 song=%s artist=%s duration=%sms", candidate.name, candidate.artist, candidate.durationMs);
                return candidate.lyric;
            }
        }
        return "";
    }

    private static void addLrclibOptions(List<Option> options, Query query, long durationMs) {
        String url = "https://lrclib.net/api/search?track_name=" + encode(query.song) + (TextUtils.isEmpty(query.artist) ? "" : "&artist_name=" + encode(query.artist));
        JsonArray array = safeArray(OkHttp.string(url, LRCLIB_HEADERS));
        List<Candidate> matches = new ArrayList<>();
        for (JsonElement element : array) {
            Candidate candidate = Candidate.lrclib(Json.safeObject(element));
            if (candidate.matches(query, durationMs) && !TextUtils.isEmpty(candidate.lyric)) matches.add(candidate);
        }
        matches.sort((a, b) -> Long.compare(a.durationDiff(durationMs), b.durationDiff(durationMs)));
        for (Candidate candidate : matches) addOption(options, candidate.option("LRCLIB", candidate.lyric));
    }

    private static String findFromNetease(List<Query> queries, long durationMs) {
        for (Query query : queries) {
            String keyword = TextUtils.isEmpty(query.artist) ? query.song : query.artist + " " + query.song;
            String url = "https://music.163.com/api/search/get/web?s=" + encode(keyword) + "&type=1&limit=10";
            JsonObject object = Json.safeObject(Json.parse(OkHttp.string(url, NETEASE_HEADERS)));
            JsonArray songs = object.has("result") && object.get("result").isJsonObject() && object.getAsJsonObject("result").has("songs") ? object.getAsJsonObject("result").getAsJsonArray("songs") : new JsonArray();
            SpiderDebug.log(TAG, "网易云查询 query=%s/%s result=%d", query.song, query.artist, songs.size());
            List<Candidate> matches = new ArrayList<>();
            for (JsonElement element : songs) {
                JsonObject item = Json.safeObject(element);
                Candidate candidate = Candidate.netease(item);
                if (!candidate.matches(query, durationMs)) continue;
                matches.add(candidate);
            }
            matches.sort((a, b) -> Long.compare(a.durationDiff(durationMs), b.durationDiff(durationMs)));
            for (Candidate candidate : matches) {
                String lyric = getNeteaseLyric(candidate.id);
                if (!TextUtils.isEmpty(lyric)) {
                    SpiderDebug.log(TAG, "网易云命中 song=%s artist=%s id=%s duration=%sms", candidate.name, candidate.artist, candidate.id, candidate.durationMs);
                    return lyric;
                }
            }
        }
        return "";
    }

    private static void addNeteaseOptions(List<Option> options, Query query, long durationMs) {
        String keyword = TextUtils.isEmpty(query.artist) ? query.song : query.artist + " " + query.song;
        String url = "https://music.163.com/api/search/get/web?s=" + encode(keyword) + "&type=1&limit=10";
        JsonObject object = Json.safeObject(Json.parse(OkHttp.string(url, NETEASE_HEADERS)));
        JsonArray songs = object.has("result") && object.get("result").isJsonObject() && object.getAsJsonObject("result").has("songs") ? object.getAsJsonObject("result").getAsJsonArray("songs") : new JsonArray();
        List<Candidate> matches = new ArrayList<>();
        for (JsonElement element : songs) {
            Candidate candidate = Candidate.netease(Json.safeObject(element));
            if (candidate.matches(query, durationMs)) matches.add(candidate);
        }
        matches.sort((a, b) -> Long.compare(a.durationDiff(durationMs), b.durationDiff(durationMs)));
        for (Candidate candidate : matches) {
            String lyric = getNeteaseLyric(candidate.id);
            if (!TextUtils.isEmpty(lyric)) addOption(options, candidate.option("网易云", lyric));
            if (options.size() >= 12) return;
        }
    }

    private static void addOption(List<Option> options, Option option) {
        if (TextUtils.isEmpty(option.lyric)) return;
        for (Option item : options) if (item.same(option)) return;
        options.add(option);
    }

    private static String findArtworkFromNetease(Query query, long durationMs) {
        String keyword = TextUtils.isEmpty(query.artist) ? query.song : query.artist + " " + query.song;
        String url = "https://music.163.com/api/search/get/web?s=" + encode(keyword) + "&type=1&limit=10";
        JsonObject object = Json.safeObject(Json.parse(OkHttp.string(url, NETEASE_HEADERS)));
        JsonArray songs = object.has("result") && object.get("result").isJsonObject() && object.getAsJsonObject("result").has("songs") ? object.getAsJsonObject("result").getAsJsonArray("songs") : new JsonArray();
        SpiderDebug.log(TAG, "网易云封面查询 query=%s/%s result=%d", query.song, query.artist, songs.size());
        for (JsonElement element : songs) {
            JsonObject item = Json.safeObject(element);
            Candidate candidate = Candidate.netease(item);
            if (!candidate.matches(query, durationMs)) continue;
            String artwork = getNeteaseArtwork(candidate.id);
            if (!TextUtils.isEmpty(artwork)) return artwork;
        }
        return "";
    }

    private static String getNeteaseLyric(String id) {
        if (TextUtils.isEmpty(id)) return "";
        String url = "https://music.163.com/api/song/lyric?id=" + encode(id) + "&lv=1&kv=1&tv=-1";
        JsonObject object = Json.safeObject(Json.parse(OkHttp.string(url, NETEASE_HEADERS)));
        if (!object.has("lrc") || !object.get("lrc").isJsonObject()) return "";
        return Json.safeString(object.getAsJsonObject("lrc"), "lyric");
    }

    private static String getNeteaseArtwork(String id) {
        if (TextUtils.isEmpty(id)) return "";
        String url = "https://music.163.com/api/song/detail/?ids=[" + encode(id) + "]";
        JsonObject object = Json.safeObject(Json.parse(OkHttp.string(url, NETEASE_HEADERS)));
        if (!object.has("songs") || !object.get("songs").isJsonArray() || object.getAsJsonArray("songs").isEmpty()) return "";
        JsonObject song = Json.safeObject(object.getAsJsonArray("songs").get(0));
        if (!song.has("album") || !song.get("album").isJsonObject()) return "";
        JsonObject album = song.getAsJsonObject("album");
        String artwork = Json.safeString(album, "picUrl");
        if (TextUtils.isEmpty(artwork)) artwork = Json.safeString(album, "blurPicUrl");
        return TextUtils.isEmpty(artwork) ? "" : artwork.replace("http://", "https://");
    }

    private static List<Query> buildQueries(String title, String subtitle) {
        List<Query> queries = new ArrayList<>();
        String base = cleanSong(TextUtils.isEmpty(subtitle) ? title : subtitle);
        String context = cleanSong(title);
        if (TextUtils.isEmpty(base)) return queries;
        addSplitQueries(queries, base);
        if (!TextUtils.isEmpty(context) && !normalize(context).equals(normalize(base))) add(queries, new Query(base, context));
        add(queries, new Query(base, ""));
        return queries;
    }

    private static void addSplitQueries(List<Query> queries, String text) {
        String[] separators = {" - ", "-", " -", "- ", " / ", "/", "_", "·", "、"};
        for (String separator : separators) {
            if (!text.contains(separator)) continue;
            String[] parts = text.split(java.util.regex.Pattern.quote(separator), 2);
            if (parts.length != 2) continue;
            String left = cleanSong(parts[0]);
            String right = cleanSong(parts[1]);
            if (TextUtils.isEmpty(left) || TextUtils.isEmpty(right)) continue;
            add(queries, new Query(right, left));
            add(queries, new Query(left, right));
            return;
        }
    }

    private static void add(List<Query> queries, Query query) {
        if (TextUtils.isEmpty(query.song)) return;
        for (Query item : queries) if (item.same(query)) return;
        queries.add(query);
    }

    private static JsonArray safeArray(String text) {
        try {
            JsonElement element = Json.parse(text);
            return element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
        } catch (Throwable e) {
            return new JsonArray();
        }
    }

    private static String cacheKey(String title, String subtitle, long durationMs) {
        long durationBucket = durationMs > 0 ? durationMs / 10000 : 0;
        return CACHE_PREFIX + Util.md5(normalize(title) + "|" + normalize(subtitle) + "|" + durationBucket);
    }

    private static String encode(String text) {
        try {
            return URLEncoder.encode(Objects.toString(text, ""), StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return "";
        }
    }

    private static String cleanSong(String text) {
        if (TextUtils.isEmpty(text)) return "";
        return text.replaceAll("https?://\\S+", " ")
                .replaceAll("(?i)\\.(mp3|m4a|flac|ape|wav|aac)$", "")
                .replaceAll("(?i)^(\\d{1,4}[\\.、\\-\\s]+)", "")
                .replaceAll("[\\[【(（].*?[\\]】)）]", " ")
                .replaceAll("(?i)\\b(hi[- ]?res|lossless|flac|mp3|m4a|wav|ape|aac|hq|sq|mv|live|伴奏|纯音乐)\\b", " ")
                .replace('\u00A0', ' ')
                .replace('\u3000', ' ')
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String normalize(String text) {
        return cleanSong(text).toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]+", "");
    }

    private static final class Query {

        private final String song;
        private final String artist;
        private final String songNorm;
        private final String artistNorm;

        private Query(String song, String artist) {
            this.song = cleanSong(song);
            this.artist = cleanSong(artist);
            this.songNorm = normalize(this.song);
            this.artistNorm = normalize(this.artist);
        }

        private boolean same(Query query) {
            return songNorm.equals(query.songNorm) && artistNorm.equals(query.artistNorm);
        }
    }

    private static final class Candidate {

        private final String id;
        private final String name;
        private final String artist;
        private final String lyric;
        private final long durationMs;

        private Candidate(String id, String name, String artist, String lyric, long durationMs) {
            this.id = id;
            this.name = Objects.toString(name, "");
            this.artist = Objects.toString(artist, "");
            this.lyric = Objects.toString(lyric, "");
            this.durationMs = durationMs;
        }

        private static Candidate lrclib(JsonObject item) {
            String name = Json.safeString(item, "trackName");
            if (TextUtils.isEmpty(name)) name = Json.safeString(item, "name");
            String lyric = Json.safeString(item, "syncedLyrics");
            long duration = secondsToMs(item.has("duration") ? item.get("duration").getAsDouble() : 0);
            return new Candidate(Json.safeString(item, "id"), name, Json.safeString(item, "artistName"), lyric, duration);
        }

        private static Candidate netease(JsonObject item) {
            List<String> artists = new ArrayList<>();
            if (item.has("artists") && item.get("artists").isJsonArray()) {
                for (JsonElement element : item.getAsJsonArray("artists")) {
                    String name = Json.safeString(Json.safeObject(element), "name");
                    if (!TextUtils.isEmpty(name)) artists.add(name);
                }
            }
            return new Candidate(Json.safeString(item, "id"), Json.safeString(item, "name"), String.join("/", artists), "", item.has("duration") ? item.get("duration").getAsLong() : 0);
        }

        private boolean matches(Query query, long targetDurationMs) {
            if (TextUtils.isEmpty(query.songNorm) || !normalize(name).equals(query.songNorm)) return false;
            if (!TextUtils.isEmpty(query.artistNorm) && !matchArtist(query.artistNorm)) return false;
            if (targetDurationMs > 0 && durationMs > 0 && Math.abs(targetDurationMs - durationMs) > DURATION_TOLERANCE_MS) return false;
            return true;
        }

        private long durationDiff(long targetDurationMs) {
            if (targetDurationMs <= 0 || durationMs <= 0) return Long.MAX_VALUE;
            return Math.abs(targetDurationMs - durationMs);
        }

        private Option option(String source, String lyric) {
            return new Option(source, name, artist, durationMs, lyric);
        }

        private Track track() {
            return new Track(id, name, artist, durationMs);
        }

        private boolean matchArtist(String expected) {
            for (String item : artist.split("[/&、,，]")) {
                String actual = normalize(item);
                if (actual.equals(expected) || expected.contains(actual) || actual.contains(expected)) return true;
            }
            return false;
        }

        private static long secondsToMs(double value) {
            return value <= 0 ? 0 : Math.round(value * 1000);
        }
    }

    public static final class Track {

        private final String id;
        private final String name;
        private final String artist;
        private final long durationMs;

        private Track(String id, String name, String artist, long durationMs) {
            this.id = Objects.toString(id, "");
            this.name = Objects.toString(name, "");
            this.artist = Objects.toString(artist, "");
            this.durationMs = durationMs;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getArtist() {
            return artist;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public String getLabel() {
            String duration = durationMs > 0 ? String.format(Locale.getDefault(), "%d:%02d", durationMs / 60000, durationMs / 1000 % 60) : "--:--";
            String by = TextUtils.isEmpty(artist) ? name : name + " - " + artist;
            return duration + "  " + by;
        }
    }

    public static final class Option {

        private final String source;
        private final String name;
        private final String artist;
        private final long durationMs;
        private final String lyric;

        private Option(String source, String name, String artist, long durationMs, String lyric) {
            this.source = Objects.toString(source, "");
            this.name = Objects.toString(name, "");
            this.artist = Objects.toString(artist, "");
            this.durationMs = durationMs;
            this.lyric = Objects.toString(lyric, "");
        }

        public String getLyric() {
            return lyric;
        }

        public String getLabel() {
            String duration = durationMs > 0 ? String.format(Locale.getDefault(), "%d:%02d", durationMs / 60000, durationMs / 1000 % 60) : "--:--";
            String by = TextUtils.isEmpty(artist) ? name : name + " - " + artist;
            return source + "  " + duration + "\n" + by;
        }

        private boolean same(Option option) {
            return normalize(name).equals(normalize(option.name)) && normalize(artist).equals(normalize(option.artist)) && durationMs == option.durationMs;
        }
    }
}
