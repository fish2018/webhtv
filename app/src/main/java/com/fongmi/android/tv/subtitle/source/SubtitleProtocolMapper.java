package com.fongmi.android.tv.subtitle.source;

import androidx.media3.common.C;

import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.subtitle.model.SubtitleAsset;
import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchType;
import com.fongmi.android.tv.subtitle.model.SubtitleQuery;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SubtitleProtocolMapper {

    private SubtitleProtocolMapper() {
    }

    public static void requireSuccess(String response) {
        envelope(response);
    }

    public static String createSearchRequest(SubtitleSourceConfig source, SubtitleQuery query, SubtitleContext context) {
        JsonObject request = baseRequest("search", source, context);
        JsonObject value = new JsonObject();
        value.addProperty("text", query == null ? "" : query.getText());
        value.addProperty("language", query == null ? "" : query.getLanguage());
        value.addProperty("source", query == null || query.getSource() == null ? "" : query.getSource().name());
        value.addProperty("strictness", query == null || query.getStrictness() == null ? "" : query.getStrictness().name());
        value.addProperty("year", query == null ? 0 : query.getYear());
        value.addProperty("season", query == null ? -1 : query.getSeasonNumber());
        value.addProperty("episode", query == null ? -1 : query.getEpisodeNumber());
        request.add("query", value);
        return request.toString();
    }

    public static String createResolveRequest(SubtitleSourceConfig source, SubtitleCandidate candidate, SubtitleContext context) {
        JsonObject request = baseRequest("resolve", source, context);
        JsonObject value = new JsonObject();
        if (candidate != null) {
            value.addProperty("id", candidate.getCandidateId());
            value.addProperty("name", candidate.getDisplayName());
            value.addProperty("language", candidate.getLanguage());
            value.addProperty("format", candidate.getFormat());
            value.add("payload", parsePayload(candidate.getProviderPayload()));
        }
        request.add("candidate", value);
        return request.toString();
    }

    public static List<SubtitleCandidate> parseCandidates(SubtitleSourceConfig source, SubtitleQuery query, String response) {
        JsonElement data = envelope(response);
        JsonArray array;
        if (data.isJsonArray()) array = data.getAsJsonArray();
        else if (data.isJsonObject() && data.getAsJsonObject().has("items") && data.getAsJsonObject().get("items").isJsonArray()) array = data.getAsJsonObject().getAsJsonArray("items");
        else array = new JsonArray();
        List<SubtitleCandidate> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            String id = string(item, "id");
            if (id.isEmpty()) continue;
            String payload = item.has("payload") ? item.get("payload").toString() : "";
            result.add(new SubtitleCandidate(
                    source.getKey(), id, string(item, "name"), string(item, "language"),
                    string(item, "format"), string(item, "releaseInfo"), integer(item, "score"),
                    integer(item, "year"), integer(item, "season", -1), integer(item, "episode", -1),
                    matchType(string(item, "matchType")), query == null ? "" : query.getText(),
                    bool(item, "requiresResolve"), payload));
        }
        return result;
    }

    public static SubtitleAsset parseAsset(SubtitleSourceConfig source, SubtitleCandidate candidate, String response) {
        JsonElement data = envelope(response);
        if (!data.isJsonObject()) throw new IllegalStateException("Subtitle resolve data must be an object");
        JsonObject item = data.getAsJsonObject();
        String uri = string(item, "url");
        if (uri.isEmpty()) throw new IllegalStateException("Subtitle resolve response has no URL");
        String name = first(string(item, "fileName"), candidate == null ? "" : candidate.getDisplayName());
        String language = first(string(item, "language"), candidate == null ? "" : candidate.getLanguage());
        String format = first(string(item, "format"), candidate == null ? "" : candidate.getFormat());
        long expiresAt = longValue(item, "expiresAt");
        String mime = PlayerHelper.getSubtitleMimeType(name.isEmpty() ? "subtitle." + format : name);
        return new SubtitleAsset(uri, "", name, language, mime, C.SELECTION_FLAG_DEFAULT, false, expiresAt);
    }

    private static JsonObject baseRequest(String action, SubtitleSourceConfig source, SubtitleContext context) {
        JsonObject request = new JsonObject();
        request.addProperty("action", action);
        request.addProperty("requestId", UUID.randomUUID().toString());
        JsonObject sourceValue = new JsonObject();
        sourceValue.addProperty("key", source == null ? "" : source.getKey());
        sourceValue.add("params", source == null ? new JsonObject() : source.getParams());
        request.add("source", sourceValue);
        request.add("context", context(context));
        return request;
    }

    private static JsonObject context(SubtitleContext context) {
        JsonObject value = new JsonObject();
        if (context == null) return value;
        value.addProperty("playbackKey", context.getPlaybackKey());
        value.addProperty("siteKey", context.getSiteKey());
        value.addProperty("vodId", context.getVodId());
        value.addProperty("mediaType", context.getMediaType());
        value.addProperty("mediaPath", context.getMediaPath());
        JsonArray aliases = new JsonArray();
        for (String alias : context.getAliases()) aliases.add(alias == null ? "" : alias);
        value.add("aliases", aliases);
        value.addProperty("canonicalTitle", context.getCanonicalTitle());
        value.addProperty("originalTitle", context.getOriginalTitle());
        value.addProperty("year", context.getYear());
        value.addProperty("season", context.getSeasonNumber());
        value.addProperty("episode", context.getEpisodeNumber());
        value.addProperty("episodeTitle", context.getEpisodeTitle());
        value.addProperty("preferredLanguage", context.getPreferredLanguage());
        value.addProperty("originalLanguage", context.getOriginalLanguage());
        value.addProperty("originCountry", context.getOriginCountry());
        value.addProperty("networkStream", context.isNetworkStream());
        return value;
    }

    private static JsonElement envelope(String response) {
        if (response == null || response.isBlank()) throw new IllegalStateException("Empty subtitle protocol response");
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(response);
        } catch (Exception error) {
            throw new IllegalStateException("Invalid subtitle protocol response", error);
        }
        if (!parsed.isJsonObject()) throw new IllegalStateException("Subtitle protocol response must be an object");
        JsonObject object = parsed.getAsJsonObject();
        int code = integer(object, "code");
        if (code != 0) throw new IllegalStateException(first(string(object, "message"), "subtitle_protocol_error_" + code));
        return object.has("data") && !object.get("data").isJsonNull() ? object.get("data") : new JsonObject();
    }

    private static JsonElement parsePayload(String payload) {
        if (payload == null || payload.isBlank()) return new JsonObject();
        try {
            return JsonParser.parseString(payload);
        } catch (Exception ignored) {
            return new com.google.gson.JsonPrimitive(payload);
        }
    }

    private static SubtitleMatchType matchType(String value) {
        if (value == null || value.isBlank()) return SubtitleMatchType.METADATA_FUZZY;
        try {
            return SubtitleMatchType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return SubtitleMatchType.METADATA_FUZZY;
        }
    }

    private static String string(JsonObject object, String key) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static int integer(JsonObject object, String key) {
        return integer(object, key, 0);
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject object, String key) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static boolean bool(JsonObject object, String key) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() && object.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String first(String first, String second) {
        return first == null || first.isEmpty() ? second == null ? "" : second : first;
    }
}
