package com.fongmi.android.tv.playback;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlaybackRemoteSyncStore {

    private static final String KEY_CONFIG = "playback_remote_sync_config";
    private static final Type LIST_TYPE = new TypeToken<List<RemoteSyncConfig>>() {
    }.getType();

    private PlaybackRemoteSyncStore() {
    }

    public static synchronized List<RemoteSyncConfig> list() {
        try {
            List<RemoteSyncConfig> configs = App.gson().fromJson(Prefers.getString(KEY_CONFIG), LIST_TYPE);
            return configs == null ? new ArrayList<>() : normalize(configs);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static synchronized void save(List<RemoteSyncConfig> configs) {
        Prefers.put(KEY_CONFIG, App.gson().toJson(normalize(configs)));
    }

    public static synchronized void upsert(RemoteSyncConfig config) {
        List<RemoteSyncConfig> configs = list();
        boolean updated = false;
        for (int i = 0; i < configs.size(); i++) {
            if (!configs.get(i).id.equals(config.id)) continue;
            configs.set(i, normalize(config));
            updated = true;
            break;
        }
        if (!updated) configs.add(normalize(config));
        save(configs);
    }

    public static synchronized void remove(String id) {
        List<RemoteSyncConfig> configs = list();
        configs.removeIf(config -> config.id.equals(id));
        save(configs);
    }

    public static synchronized RemoteSyncConfig find(String id) {
        for (RemoteSyncConfig config : list()) if (config.id.equals(id)) return config;
        return null;
    }

    public static synchronized void markIdentity(String id, PlaybackIdentityResolver.Result result) {
        if (result == null) return;
        List<RemoteSyncConfig> configs = list();
        for (RemoteSyncConfig config : configs) {
            if (!config.id.equals(id)) continue;
            config.identityState = result.action;
            config.identityEpoch = result.identityEpoch;
            config.identitySupported = result.capabilities;
            config.identityCanonicalKey = result.canonicalInterfaceKey;
            config.identityMatchedBy = result.matchedBy;
            config.identityMessage = result.message;
            break;
        }
        save(configs);
    }

    public static synchronized void markResult(String id, PlaybackRemoteSyncResult result) {
        List<RemoteSyncConfig> configs = list();
        long now = System.currentTimeMillis();
        for (RemoteSyncConfig config : configs) {
            if (!config.id.equals(id)) continue;
            config.lastSyncAt = now;
            config.lastFetched = result == null ? 0 : result.fetched;
            config.lastApplied = result == null ? 0 : result.applied;
            config.lastDeleted = result == null ? 0 : result.deleted;
            config.lastSkipped = result == null ? 0 : result.skipped;
            config.lastFailed = result == null ? 0 : result.failed;
            config.lastError = result == null ? "" : result.message;
            if (result != null) {
                config.identityState = result.identityState == null ? config.identityState : result.identityState;
                config.identityEpoch = result.identityEpoch == null ? config.identityEpoch : result.identityEpoch;
                config.identitySupported = result.identitySupported;
                config.identityCanonicalKey = result.identityCanonicalKey == null ? "" : result.identityCanonicalKey;
                config.identityMatchedBy = result.identityMatchedBy == null ? "" : result.identityMatchedBy;
                config.identityMessage = result.identityMessage == null ? "" : result.identityMessage;
            }
            if (result != null && result.success) {
                config.lastSuccessAt = now;
                config.lastError = "";
                if (result.cursors != null) {
                    for (java.util.Map.Entry<String, String> entry : result.cursors.entrySet()) config.cursor(entry.getKey(), entry.getValue());
                } else {
                    config.cursor(result.configKey, result.nextSince);
                }
            }
            break;
        }
        save(configs);
    }

    public static synchronized int totalCount() {
        return list().size();
    }

    public static synchronized int activeCount() {
        int count = 0;
        for (RemoteSyncConfig config : list()) if (config.isUsable()) count++;
        return count;
    }

    private static List<RemoteSyncConfig> normalize(List<RemoteSyncConfig> configs) {
        List<RemoteSyncConfig> result = new ArrayList<>();
        if (configs == null) return result;
        for (RemoteSyncConfig config : configs) result.add(normalize(config));
        return result;
    }

    private static RemoteSyncConfig normalize(RemoteSyncConfig config) {
        if (config == null) config = new RemoteSyncConfig();
        if (config.id == null || config.id.isEmpty()) config.id = UUID.randomUUID().toString();
        if (config.name == null) config.name = "";
        if (config.url == null) config.url = "";
        if (config.token == null) config.token = "";
        if (config.siteKeys == null) config.siteKeys = new ArrayList<>();
        if (config.cursors == null) config.cursors = new java.util.HashMap<>();
        if (config.intervalMinutes < 0) config.intervalMinutes = 0;
        if (config.maxItems <= 0) config.maxItems = 100;
        if (config.maxItems > 1000) config.maxItems = 1000;
        if (config.lastError == null) config.lastError = "";
        if (config.identityState == null) config.identityState = "unknown";
        if (config.identityEpoch == null) config.identityEpoch = "";
        if (config.identityCanonicalKey == null) config.identityCanonicalKey = "";
        if (config.identityMatchedBy == null) config.identityMatchedBy = "";
        if (config.identityMessage == null) config.identityMessage = "";
        return config;
    }
}
