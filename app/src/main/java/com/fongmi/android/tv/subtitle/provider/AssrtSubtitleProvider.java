package com.fongmi.android.tv.subtitle.provider;

import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.Setting;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

public final class AssrtSubtitleProvider implements SubtitleProvider {

    private static final String TAG = "SubtitleMatch";
    private static final String NAME = "assrt";
    private static final String API_BASE = "https://api.assrt.net/v1";
    private static final String REFERER = "https://assrt.net/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final SubtitleTitleParser parser;
    private final SubtitleAssetStore assetStore;

    public AssrtSubtitleProvider() {
        this.parser = new SubtitleTitleParser();
        this.assetStore = new SubtitleAssetStore();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean isEnabled() {
        boolean enabled = !TextUtils.isEmpty(Setting.getSubtitleAssrtToken());
        if (!enabled) Log.w(TAG, "assrt disabled reason=empty_token");
        return enabled;
    }

    @Override
    public List<SubtitleCandidate> search(SubtitleQuery query, SubtitleContext context) throws Exception {
        List<SubtitleCandidate> items = new ArrayList<>();
        if (!isEnabled() || query == null || TextUtils.isEmpty(query.getText())) return items;
        Log.i(TAG, "assrt search request q=" + query.getText());
        String url = API_BASE + "/sub/search?token=" + encode(Setting.getSubtitleAssrtToken()) + "&q=" + encode(query.getText()) + "&is_file=1&cnt=15";
        try (Response response = OkHttp.client().newCall(new Request.Builder().url(url).get().build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Log.w(TAG, "assrt search http_failed code=" + response.code() + " q=" + query.getText());
                return items;
            }
            String body = response.body().string();
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            int status = safeInt(root, "status");
            if (status != 0) {
                Log.w(TAG, "assrt search api_failed status=" + status + " q=" + query.getText());
                return items;
            }
            JsonArray subs = safeArray(safeObject(root, "sub"), "subs");
            Log.i(TAG, "assrt search response q=" + query.getText() + " count=" + subs.size());
            for (JsonElement element : subs) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                String id = firstString(item, "id", "fileid");
                if (TextUtils.isEmpty(id)) continue;
                String name = firstString(item, "name", "sub_name", "m_version", "m_title");
                String videoName = firstString(item, "videoname", "m_videoname", "video_chinese_name", "m_video_chinese_name");
                String format = detectFormat(firstString(item, "subtype", "m_subtype"), name);
                String language = firstString(safeObject(item, "lang"), "desc");
                if (TextUtils.isEmpty(language)) language = firstString(item, "m_lang");
                int season = parser.seasonNumber(name + " " + videoName);
                int episode = parser.episodeNumber(name + " " + videoName);
                int year = parser.firstYear(videoName);
                JsonObject payload = new JsonObject();
                payload.addProperty("id", id);
                payload.addProperty("format", format);
                items.add(new SubtitleCandidate(NAME, id, name, language, format, videoName, 0, year, season, episode, SubtitleMatchType.METADATA_FUZZY, query.getKey(), true, App.gson().toJson(payload)));
            }
        }
        Log.i(TAG, "assrt search candidates q=" + query.getText() + " count=" + items.size());
        return items;
    }

    @Override
    public SubtitleAsset resolve(SubtitleCandidate candidate, SubtitleContext context) throws Exception {
        if (candidate == null) return null;
        JsonObject payload = JsonParser.parseString(candidate.getProviderPayload()).getAsJsonObject();
        String id = safeString(payload, "id");
        if (TextUtils.isEmpty(id)) id = candidate.getCandidateId();
        Log.i(TAG, "assrt detail request id=" + id);
        String url = API_BASE + "/sub/detail?token=" + encode(Setting.getSubtitleAssrtToken()) + "&id=" + encode(id);
        try (Response response = OkHttp.client().newCall(new Request.Builder().url(url).get().build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Log.w(TAG, "assrt detail http_failed code=" + response.code() + " id=" + id);
                return null;
            }
            String body = response.body().string();
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            int status = safeInt(root, "status");
            if (status != 0) {
                Log.w(TAG, "assrt detail api_failed status=" + status + " id=" + id);
                return null;
            }
            JsonArray subs = safeArray(safeObject(root, "sub"), "subs");
            JsonObject first = firstObject(subs);
            if (first == null) {
                Log.w(TAG, "assrt detail empty_subs id=" + id);
                return null;
            }
            String downloadUrl = safeString(first, "url");
            if (TextUtils.isEmpty(downloadUrl)) {
                Log.w(TAG, "assrt detail empty_url id=" + id);
                return null;
            }
            String filename = safeString(first, "filename");
            File archive = download(downloadUrl, candidate, filename);
            File target = isZip(archive, filename) ? assetStore.unzipAndPick(archive, NAME, id) : archive;
            Log.i(TAG, "assrt detail resolved id=" + id + " file=" + target.getName());
            return assetStore.toAsset(target, candidate.getDisplayName(), candidate.getLanguage(), false);
        }
    }

    private File download(String url, SubtitleCandidate candidate, String filename) throws Exception {
        String suffix = suffix(filename, candidate.getFormat());
        File target = assetStore.file(NAME, candidate.getCandidateId(), suffix);
        try (Response response = executeRedirects(url)) {
            if (!response.isSuccessful() || response.body() == null) {
                Log.w(TAG, "assrt download failed code=" + response.code() + " id=" + candidate.getCandidateId());
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

    private Response executeRedirects(String url) throws Exception {
        String current = url;
        for (int i = 0; i < 5; i++) {
            Request request = new Request.Builder().url(current).header("User-Agent", USER_AGENT).header("Referer", REFERER).header("Connection", "close").get().build();
            Response response = OkHttp.noRedirect().newCall(request).execute();
            if (!isRedirect(response.code())) return response;
            String location = response.header("Location");
            HttpUrl resolved = response.request().url().resolve(location == null ? "" : location);
            response.close();
            if (resolved == null) throw new IllegalStateException("redirect_failed");
            current = resolved.toString();
        }
        throw new IllegalStateException("redirect_overflow");
    }

    private boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private boolean isZip(File file, String filename) {
        if (!TextUtils.isEmpty(filename) && filename.toLowerCase(Locale.ROOT).endsWith(".zip")) return true;
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] header = new byte[4];
            if (input.read(header) == 4) return header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04;
        } catch (Exception ignored) {
        }
        return false;
    }

    private String suffix(String filename, String format) {
        if (!TextUtils.isEmpty(filename) && filename.contains(".")) return filename.substring(filename.lastIndexOf('.'));
        if (!TextUtils.isEmpty(format)) return "." + format.toLowerCase(Locale.ROOT);
        return ".sub";
    }

    private String detectFormat(String subtype, String name) {
        String text = (subtype + " " + name).toLowerCase(Locale.ROOT);
        if (text.contains(".ass") || text.contains(" ass")) return "ass";
        if (text.contains(".ssa") || text.contains(" ssa")) return "ssa";
        if (text.contains(".vtt") || text.contains(" vtt")) return "vtt";
        return "srt";
    }

    private JsonObject firstObject(JsonArray array) {
        for (JsonElement element : array) if (element.isJsonObject()) return element.getAsJsonObject();
        return null;
    }

    private JsonObject safeObject(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : new JsonObject();
    }

    private JsonArray safeArray(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private String safeString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        return object.get(key).getAsString();
    }

    private String firstString(JsonObject object, String... keys) {
        if (object == null || keys == null) return "";
        for (String key : keys) {
            String value = safeString(object, key);
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private int safeInt(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return 0;
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
