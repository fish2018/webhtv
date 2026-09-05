package com.fongmi.android.tv.subtitle.provider;

import android.util.Log;

import com.fongmi.android.tv.subtitle.SubtitleTitleParser;
import com.fongmi.android.tv.subtitle.cache.SubtitleAssetStore;
import com.fongmi.android.tv.subtitle.model.SubtitleAsset;
import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchType;
import com.fongmi.android.tv.subtitle.model.SubtitleQuery;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Request;
import okhttp3.Response;

public final class XunleiSubtitleProvider implements SubtitleProvider {

    static final String NAME = "xunlei";

    private static final String TAG = "SubtitleMatch";
    private static final String API = "https://api-shoulei-ssl.xunlei.com/oracle/subtitle?name=";
    private static final String REFERER = "https://sl-m-ssl.xunlei.com/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final SubtitleTitleParser parser;
    private final SubtitleAssetStore assetStore;

    public XunleiSubtitleProvider() {
        this(new SubtitleTitleParser(), new SubtitleAssetStore());
    }

    XunleiSubtitleProvider(SubtitleTitleParser parser, SubtitleAssetStore assetStore) {
        this.parser = parser;
        this.assetStore = assetStore;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public List<SubtitleCandidate> search(SubtitleQuery query, SubtitleContext context) throws Exception {
        if (query == null || isEmpty(query.getText())) return new ArrayList<>();
        String url = API + encode(query.getText());
        Log.i(TAG, "xunlei search request q=" + query.getText());
        try (Response response = OkHttp.client().newCall(request(url).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Log.w(TAG, "xunlei search http_failed code=" + response.code() + " q=" + query.getText());
                return new ArrayList<>();
            }
            String body = response.body().string();
            List<SubtitleCandidate> candidates = parseCandidates(body, query, parser, computeContentId(context));
            Log.i(TAG, "xunlei search candidates q=" + query.getText() + " count=" + candidates.size());
            return candidates;
        }
    }

    @Override
    public SubtitleAsset resolve(SubtitleCandidate candidate, SubtitleContext context) throws Exception {
        if (candidate == null || isEmpty(candidate.getProviderPayload())) return null;
        JsonObject payload = JsonParser.parseString(candidate.getProviderPayload()).getAsJsonObject();
        String url = safeString(payload, "url");
        if (isEmpty(url)) return null;
        String filename = safeString(payload, "name");
        File file = download(url, candidate, filename);
        return assetStore.toAsset(file, candidate.getDisplayName(), candidate.getLanguage(), false);
    }

    static List<SubtitleCandidate> parseCandidates(String body, SubtitleQuery query, SubtitleTitleParser parser) {
        return parseCandidates(body, query, parser, "");
    }

    static List<SubtitleCandidate> parseCandidates(String body, SubtitleQuery query, SubtitleTitleParser parser, String contentId) {
        List<SubtitleCandidate> items = new ArrayList<>();
        if (isEmpty(body) || query == null) return items;
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (safeInt(root, "code") != 0) return items;
        String result = safeString(root, "result");
        if (!isEmpty(result) && !"ok".equalsIgnoreCase(result)) return items;
        JsonArray data = safeArray(root, "data");
        for (JsonElement element : data) {
            if (!element.isJsonObject()) continue;
            try {
                SubtitleCandidate candidate = parseCandidate(element.getAsJsonObject(), query, parser, contentId);
                if (candidate != null) items.add(candidate);
            } catch (Exception ignored) {
            }
        }
        return items;
    }

    private static SubtitleCandidate parseCandidate(JsonObject item, SubtitleQuery query, SubtitleTitleParser parser, String contentId) {
        String url = safeString(item, "url");
        String id = firstString(item, "cid", "gcid");
        if (isEmpty(id)) id = url;
        if (isEmpty(id) || isEmpty(url)) return null;
        String name = safeString(item, "name");
        String format = detectFormat(safeString(item, "ext"), name, url);
        if (isEmpty(name)) name = id + "." + format;
        String language = normalizeLanguage(firstLanguage(item));
        String extraName = safeString(item, "extra_name");
        long duration = safeLong(item, "duration");
        int year = parser == null ? 0 : parser.firstYear(name + " " + extraName);
        int season = parser == null ? -1 : parser.seasonNumber(name);
        int episode = parser == null ? -1 : parser.episodeNumber(name);
        int score = Math.max(safeInt(item, "score"), safeInt(item, "fingerprintf_score"));
        SubtitleMatchType matchType = SubtitleMatchType.METADATA_FUZZY;
        if (!isEmpty(contentId) && contentId.equalsIgnoreCase(safeString(item, "cid"))) {
            score += 120;
            matchType = SubtitleMatchType.METADATA_STRICT;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("url", url);
        payload.addProperty("name", name);
        payload.addProperty("format", format);
        payload.addProperty("duration", duration);
        payload.addProperty("gcid", safeString(item, "gcid"));
        payload.addProperty("cid", safeString(item, "cid"));
        return new SubtitleCandidate(NAME, id, name, language, format, releaseInfo(extraName, duration), score, year, season, episode, matchType, query.getKey(), true, payload.toString());
    }

    private static String computeContentId(SubtitleContext context) {
        String mediaPath = context == null ? "" : context.getMediaPath();
        if (isEmpty(mediaPath)) return "";
        try {
            File file = new File(mediaPath);
            return file.isFile() ? computeContentId(file) : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    static String computeContentId(File file) throws Exception {
        if (file == null || !file.isFile()) return "";
        long fileSize = file.length();
        byte[] buffer;
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            if (fileSize < 0xf000) {
                buffer = new byte[(int) fileSize];
                input.readFully(buffer);
            } else {
                buffer = new byte[0xf000];
                input.readFully(buffer, 0, 0x5000);
                input.seek(fileSize / 3);
                input.readFully(buffer, 0x5000, 0x5000);
                input.seek(fileSize - 0x5000);
                input.readFully(buffer, 0xa000, 0x5000);
            }
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(buffer);
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) builder.append(String.format("%02X", b & 0xff));
        return builder.toString();
    }

    private File download(String url, SubtitleCandidate candidate, String filename) throws Exception {
        File target = assetStore.file(NAME, candidate.getCandidateId(), suffix(filename, candidate.getFormat()));
        try (Response response = OkHttp.client().newCall(request(url).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Log.w(TAG, "xunlei download failed code=" + response.code() + " id=" + candidate.getCandidateId());
                throw new IllegalStateException("download_failed");
            }
            try (InputStream input = response.body().byteStream(); FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            }
        }
        return target;
    }

    private static Request.Builder request(String url) {
        return new Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Referer", REFERER).header("Connection", "close").get();
    }

    private static String firstLanguage(JsonObject item) {
        JsonArray languages = safeArray(item, "languages");
        for (JsonElement element : languages) {
            if (element == null || element.isJsonNull()) continue;
            try {
                String value = element.getAsString();
                if (!isEmpty(value)) return value.trim();
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private static String normalizeLanguage(String value) {
        if (isEmpty(value)) return "zh";
        String lower = value.toLowerCase(Locale.ROOT).trim();
        if ("默认".equals(value) || "default".equals(lower)) return "zh";
        if (containsAny(lower, "中文", "中字", "简", "繁", "chs", "cht", "zh", "english", "eng", "en", "日", "ja", "jp", "韩", "韓", "ko", "kr")) return value;
        return "zh";
    }

    private static String releaseInfo(String extraName, long duration) {
        StringBuilder builder = new StringBuilder();
        if (!isEmpty(extraName)) builder.append(extraName);
        if (duration > 0) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(duration / 60000).append("m");
        }
        return builder.toString();
    }

    private static String suffix(String filename, String format) {
        if (!isEmpty(filename) && filename.contains(".")) {
            String suffix = filename.substring(filename.lastIndexOf('.'));
            if (suffix.length() <= 8) return suffix;
        }
        if (!isEmpty(format)) return "." + format.toLowerCase(Locale.ROOT);
        return ".sub";
    }

    private static String detectFormat(String ext, String name, String url) {
        String value = firstNonEmpty(ext, extension(name), extension(url)).toLowerCase(Locale.ROOT).replace(".", "");
        if ("ass".equals(value) || "ssa".equals(value) || "srt".equals(value) || "vtt".equals(value)) return value;
        String text = (name + " " + url).toLowerCase(Locale.ROOT);
        if (text.contains(".ass")) return "ass";
        if (text.contains(".ssa")) return "ssa";
        if (text.contains(".vtt")) return "vtt";
        return "srt";
    }

    private static String extension(String value) {
        if (isEmpty(value)) return "";
        int index = value.lastIndexOf('.');
        if (index < 0 || index == value.length() - 1) return "";
        String suffix = value.substring(index + 1);
        return suffix.length() <= 8 ? suffix : "";
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (!isEmpty(needle) && value.contains(needle)) return true;
        return false;
    }

    private static JsonArray safeArray(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static String firstString(JsonObject object, String... keys) {
        if (object == null || keys == null) return "";
        for (String key : keys) {
            String value = safeString(object, key);
            if (!isEmpty(value)) return value;
        }
        return "";
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) if (!isEmpty(value)) return value;
        return "";
    }

    private static String safeString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static int safeInt(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return 0;
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static long safeLong(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return 0L;
        try {
            return object.get(key).getAsLong();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static boolean isEmpty(CharSequence value) {
        return value == null || value.length() == 0;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
