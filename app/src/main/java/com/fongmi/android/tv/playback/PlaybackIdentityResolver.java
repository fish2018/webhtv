package com.fongmi.android.tv.playback;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.fongmi.android.tv.utils.Task;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Resolves address clues to a server-owned interfaceKey without uploading URLs. */
public final class PlaybackIdentityResolver {

    private static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private PlaybackIdentityResolver() {
    }

    public static void resolveSaved(Config config) {
        if (config == null || config.getId() <= 0) return;
        Task.execute(() -> {
            for (RemoteSyncConfig remote : PlaybackRemoteSyncStore.list()) {
                Result result = resolve(remote, config.getId());
                if ("conflict".equals(result.action) || "confirm_required".equals(result.action)) {
                    SpiderDebug.log("playback-identity-resolve", "config=%s action=%s matchedBy=%s", config.getId(), result.action, result.matchedBy);
                }
            }
        });
    }

    public static Result resolve(RemoteSyncConfig remote, int cid) {
        if (remote == null || !remote.isUsable()) return Result.unavailable("远端同步源未完成配置");
        Config config = Config.find(cid);
        if (config == null || TextUtils.isEmpty(config.getUrl())) return Result.unavailable("当前接口配置为空");
        String interfaceKey = config.ensureInterfaceKey();
        try {
            IdentityRequest request = request(config, cid);
            String endpoint = identityEndpoint(remote.url);
            Request.Builder builder = new Request.Builder().url(endpoint).post(RequestBody.create(request.body.toString(), JSON));
            builder.header("Accept", "application/json");
            builder.header("X-WebHTV-Identity-Version", PlaybackConfigIdentity.IDENTITY_VERSION);
            builder.header("X-WebHTV-Address-Match-Version", PlaybackConfigIdentity.ADDRESS_MATCH_VERSION);
            builder.header("X-WebHTV-Request-Id", UUID.randomUUID().toString());
            if (!TextUtils.isEmpty(remote.token)) builder.header("X-WebHTV-Token", remote.token);
            try (Response response = OkHttp.client(TIMEOUT_MS).newCall(builder.build()).execute()) {
                String text = response.body() == null ? "" : response.body().string();
                if (response.code() == 404 || response.code() == 405 || response.code() == 501) {
                    config.identityResolutionState("unsupported").save();
                    remote.identityState = "unsupported";
                    return Result.unsupported("服务端不支持自动身份匹配");
                }
                if (response.code() == 409) return parse(text, "conflict", response.code(), interfaceKey);
                if (!response.isSuccessful()) {
                    if (response.code() == 503) return Result.unavailable("服务端暂时不可用");
                    return Result.failure("身份解析 HTTP " + response.code());
                }
                Result result = parse(text, "invalid", response.code(), interfaceKey);
                if (result.success) apply(config, result);
                remote.identityState = result.action;
                remote.identityEpoch = result.identityEpoch;
                remote.identitySupported = result.capabilities;
                remote.identityCanonicalKey = result.canonicalInterfaceKey;
                remote.identityMatchedBy = result.matchedBy;
                remote.identityMessage = result.message;
                return result;
            }
        } catch (Throwable error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            SpiderDebug.log("playback-identity-resolve", error);
            return Result.unavailable(message);
        }
    }

    private static IdentityRequest request(Config config, int cid) {
        PlaybackConfigIdentity.IdentitySnapshot snapshot = PlaybackConfigIdentity.snapshot(config);
        JsonObject body = new JsonObject();
        body.addProperty("schema", PlaybackConfigIdentity.IDENTITY_SCHEMA);
        body.addProperty("operation", "resolve");
        body.addProperty("configType", PlaybackConfigIdentity.configType(config.getType()));
        body.addProperty("interfaceKey", snapshot.interfaceKey);
        body.add("strictAddressKeys", array(snapshot.strictAddressKeys));
        body.add("endpointMatchKeys", array(snapshot.endpointMatchKeys));
        body.add("hostMatchKeys", array(snapshot.hostMatchKeys));
        body.add("legacyConfigKeys", array(snapshot.legacyConfigKeys));
        body.addProperty("sourceDataState", hasLocalData(cid) ? "has_data" : "empty");
        body.addProperty("client", "android");
        body.addProperty("appVersion", "5.6.0");
        return new IdentityRequest(body);
    }

    private static boolean hasLocalData(int cid) {
        try {
            List<History> histories = AppDatabaseHolder.history(cid);
            return histories != null && !histories.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static JsonArray array(List<String> values) {
        JsonArray array = new JsonArray();
        if (values != null) for (String value : values) if (!TextUtils.isEmpty(value)) array.add(value);
        return array;
    }

    private static void apply(Config config, Result result) {
        if (result.action.equals("adopt") || result.action.equals("migration_pending")) {
            if (!TextUtils.isEmpty(result.canonicalInterfaceKey)) config.setInterfaceKey(result.canonicalInterfaceKey);
        }
        if (!TextUtils.isEmpty(result.canonicalInterfaceKey)) config.setIdentityResolutionState(result.action);
        if (!TextUtils.isEmpty(result.identityEpoch)) config.addAddressMatchAliases(result.matchedKeys);
        config.save();
    }

    private static Result parse(String text, String fallbackAction, int status, String sourceKey) {
        try {
            JsonElement element = JsonParser.parseString(text);
            if (!element.isJsonObject()) return Result.failure("身份解析响应无效");
            JsonObject object = element.getAsJsonObject();
            String action = string(object, "action", fallbackAction);
            String canonical = string(object, "canonicalInterfaceKey", "");
            String matchedBy = string(object, "matchedBy", "none");
            String epoch = string(object, "identityEpoch", "");
            List<String> matched = strings(object.get("matchedKeys"));
            boolean migration = bool(object, "migrationRequired");
            boolean reset = bool(object, "resetSince");
            boolean capabilities = object.has("capabilities") && object.get("capabilities").isJsonObject()
                    && bool(object.getAsJsonObject("capabilities"), "identityResolve");
            boolean success = status < 400 && (action.equals("create") || action.equals("keep") || action.equals("adopt")
                    || action.equals("migration_pending"));
            return new Result(success, action, canonical, matchedBy, matched, epoch, migration, reset, capabilities, string(object, "error", ""), sourceKey);
        } catch (Throwable error) {
            return Result.failure("身份解析响应解析失败");
        }
    }

    private static String identityEndpoint(String source) {
        URI uri = URI.create(source.trim());
        String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
        String identityPath;
        if (path.endsWith("/api/playback/sync")) identityPath = path.substring(0, path.length() - "/sync".length()) + "/identity/resolve";
        else if (path.endsWith("/playback/sync")) identityPath = path.substring(0, path.length() - "/sync".length()) + "/identity/resolve";
        else identityPath = (path.isEmpty() ? "" : path) + "/api/playback/identity/resolve";
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), identityPath, null, null).toString();
        } catch (Exception error) {
            return source;
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        try {
            JsonElement value = object.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsString();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject object, String key) {
        try {
            JsonElement value = object.get(key);
            return value != null && !value.isJsonNull() && (value.getAsBoolean() || "1".equals(value.getAsString()));
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> strings(JsonElement element) {
        if (element == null || !element.isJsonArray()) return Collections.emptyList();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (JsonElement item : element.getAsJsonArray()) if (item != null && item.isJsonPrimitive()) result.add(item.getAsString());
        return new ArrayList<>(result);
    }

    private static final class IdentityRequest {
        private final JsonObject body;

        private IdentityRequest(JsonObject body) {
            this.body = body;
        }
    }

    public static final class Result {
        public final boolean success;
        public final String action;
        public final String canonicalInterfaceKey;
        public final String matchedBy;
        public final List<String> matchedKeys;
        public final String identityEpoch;
        public final boolean migrationRequired;
        public final boolean resetSince;
        public final boolean capabilities;
        public final String message;
        public final String sourceInterfaceKey;

        private Result(boolean success, String action, String canonicalInterfaceKey, String matchedBy, List<String> matchedKeys, String identityEpoch, boolean migrationRequired, boolean resetSince, boolean capabilities, String message, String sourceInterfaceKey) {
            this.success = success;
            this.action = action;
            this.canonicalInterfaceKey = canonicalInterfaceKey == null ? "" : canonicalInterfaceKey;
            this.matchedBy = matchedBy == null ? "none" : matchedBy;
            this.matchedKeys = matchedKeys == null ? Collections.emptyList() : matchedKeys;
            this.identityEpoch = identityEpoch == null ? "" : identityEpoch;
            this.migrationRequired = migrationRequired;
            this.resetSince = resetSince;
            this.capabilities = capabilities;
            this.message = message == null ? "" : message;
            this.sourceInterfaceKey = sourceInterfaceKey == null ? "" : sourceInterfaceKey;
        }

        public static Result unsupported(String message) {
            return new Result(false, "unsupported", "", "none", Collections.emptyList(), "", false, false, false, message, "");
        }

        public static Result unavailable(String message) {
            return new Result(false, "unavailable", "", "none", Collections.emptyList(), "", false, false, false, message, "");
        }

        public static Result failure(String message) {
            return new Result(false, "invalid", "", "none", Collections.emptyList(), "", false, false, false, message, "");
        }
    }

    private static final class AppDatabaseHolder {
        static List<History> history(int cid) {
            return com.fongmi.android.tv.db.AppDatabase.get().getHistoryDao().findAll(cid);
        }
    }
}
