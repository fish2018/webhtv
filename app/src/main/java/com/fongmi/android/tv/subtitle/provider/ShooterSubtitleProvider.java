package com.fongmi.android.tv.subtitle.provider;

import android.util.Log;

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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;

public final class ShooterSubtitleProvider implements SubtitleProvider {

    static final String NAME = "shooter";

    private static final String TAG = "SubtitleMatch";
    private static final String API = "https://www.shooter.cn/api/subapi.php";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final SubtitleAssetStore assetStore;

    public ShooterSubtitleProvider() {
        this(new SubtitleAssetStore());
    }

    ShooterSubtitleProvider(SubtitleAssetStore assetStore) {
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
    public boolean isQueryIndependent() {
        return true;
    }

    @Override
    public List<SubtitleCandidate> search(SubtitleQuery query, SubtitleContext context) throws Exception {
        List<SubtitleCandidate> items = new ArrayList<>();
        String mediaPath = context == null ? "" : context.getMediaPath();
        if (isEmpty(mediaPath)) return items;
        File file = new File(mediaPath);
        if (!file.isFile()) return items;
        String hash = computeFileHash(file);
        if (isEmpty(hash)) return items;

        String language = normalizeLanguage(query == null ? "" : query.getLanguage());
        Log.i(TAG, "shooter search request file=" + file.getName() + " hash=" + hash + " lang=" + language);
        FormBody body = new FormBody.Builder()
                .add("filehash", hash)
                .add("pathinfo", mediaPath)
                .add("format", "json")
                .add("lang", "eng".equals(language) ? "eng" : "chn")
                .build();
        Request request = new Request.Builder().url(API).header("User-Agent", USER_AGENT).post(body).build();
        try (Response response = OkHttp.client().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Log.w(TAG, "shooter search http_failed code=" + response.code() + " file=" + file.getName());
                return items;
            }
            String responseBody = response.body().string();
            items.addAll(parseCandidates(responseBody, query, context));
        }
        Log.i(TAG, "shooter search candidates file=" + file.getName() + " count=" + items.size());
        return items;
    }

    @Override
    public SubtitleAsset resolve(SubtitleCandidate candidate, SubtitleContext context) throws Exception {
        if (candidate == null || isEmpty(candidate.getProviderPayload())) return null;
        JsonObject payload = JsonParser.parseString(candidate.getProviderPayload()).getAsJsonObject();
        String url = safeString(payload, "url");
        if (isEmpty(url)) return null;
        File file = download(url, candidate);
        return assetStore.toAsset(file, candidate.getDisplayName(), candidate.getLanguage(), false);
    }

    static List<SubtitleCandidate> parseCandidates(String body, SubtitleQuery query, SubtitleContext context) {
        List<SubtitleCandidate> items = new ArrayList<>();
        if (isEmpty(body) || context == null) return items;
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonArray()) return items;
        String fileName = new File(context.getMediaPath()).getName();
        String language = normalizeLanguage(query == null ? context.getPreferredLanguage() : query.getLanguage());
        int year = query == null ? context.getYear() : query.getYear();
        int season = query == null ? context.getSeasonNumber() : query.getSeasonNumber();
        int episode = query == null ? context.getEpisodeNumber() : query.getEpisodeNumber();
        String queryKey = query == null ? context.getPlaybackKey() : query.getKey();
        for (JsonElement group : root.getAsJsonArray()) {
            JsonArray files = safeArray(group.isJsonObject() ? group.getAsJsonObject() : null, "Files");
            for (JsonElement element : files) {
                if (!element.isJsonObject()) continue;
                JsonObject file = element.getAsJsonObject();
                String url = safeString(file, "Link");
                if (isEmpty(url)) continue;
                String format = detectFormat(safeString(file, "Ext"), url);
                JsonObject payload = new JsonObject();
                payload.addProperty("url", url);
                payload.addProperty("format", format);
                String name = (isEmpty(fileName) ? context.getCanonicalTitle() : fileName) + " | 射手 | " + format;
                items.add(new SubtitleCandidate(NAME, url, name, language, format, "hash", 120, year, season, episode, SubtitleMatchType.METADATA_STRICT, queryKey, true, payload.toString()));
            }
        }
        return items;
    }

    static String computeFileHash(File file) throws Exception {
        if (file == null || !file.isFile() || file.length() < 12 * 1024) return "";
        long[] offsets = new long[]{4 * 1024L, file.length() / 3 * 2, file.length() / 3, file.length() - 8 * 1024L};
        StringBuilder builder = new StringBuilder();
        byte[] buffer = new byte[4 * 1024];
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            for (long offset : offsets) {
                input.seek(offset);
                input.readFully(buffer);
                if (builder.length() > 0) builder.append(';');
                builder.append(md5(buffer));
            }
        }
        return builder.toString();
    }

    private File download(String url, SubtitleCandidate candidate) throws Exception {
        File target = assetStore.file(NAME, candidate.getCandidateId(), suffix(candidate.getFormat(), url));
        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build();
        try (Response response = OkHttp.client().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Log.w(TAG, "shooter download failed code=" + response.code() + " id=" + candidate.getCandidateId());
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

    private static String md5(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] hash = digest.digest(bytes);
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) builder.append(String.format("%02x", b & 0xff));
        return builder.toString();
    }

    private static String normalizeLanguage(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("en") || "eng".equals(lower)) return "eng";
        return "zh";
    }

    private static String detectFormat(String ext, String url) {
        String value = isEmpty(ext) ? extension(url) : ext;
        value = value.toLowerCase(Locale.ROOT).replace(".", "");
        if ("ass".equals(value) || "ssa".equals(value) || "srt".equals(value) || "vtt".equals(value)) return value;
        return "srt";
    }

    private static String suffix(String format, String url) {
        String ext = detectFormat(format, url);
        return isEmpty(ext) ? ".sub" : "." + ext;
    }

    private static String extension(String value) {
        if (isEmpty(value)) return "";
        int index = value.lastIndexOf('.');
        if (index < 0 || index == value.length() - 1) return "";
        String suffix = value.substring(index + 1);
        return suffix.length() <= 8 ? suffix : "";
    }

    private static JsonArray safeArray(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static String safeString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean isEmpty(CharSequence value) {
        return value == null || value.length() == 0;
    }
}
