package com.fongmi.android.tv.remote;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.setting.ConfigSyncPolicy;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.remote.RemoteModels.RemoteCommandResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class RemoteConfigOps {

    private RemoteConfigOps() {
    }

    public static RemoteCommandResult list() {
        return RemoteCommandResult.success("", data());
    }

    public static RemoteCommandResult upsert(JsonObject payload) {
        int type = number(payload, "type", 0);
        String url = string(payload, "url");
        String name = string(payload, "name");
        String interfaceKey = string(payload, "interfaceKey");
        if (TextUtils.isEmpty(url)) return RemoteCommandResult.failure("Missing config url");
        Config config = TextUtils.isEmpty(interfaceKey)
                ? Config.find(url, type)
                : AppDatabase.get().getConfigDao().findByInterfaceKey(interfaceKey, type);
        if (config == null) config = findByAddressAlias(payload, type);
        if (config == null) config = Config.create(type);
        config.interfaceKey(interfaceKey).mergeUrls(urls(payload)).url(url).name(name).save();
        com.fongmi.android.tv.playback.PlaybackIdentityResolver.resolveSaved(config);
        return RemoteCommandResult.success("Config saved", data());
    }

    public static RemoteCommandResult use(JsonObject payload) {
        int type = number(payload, "type", 0);
        String url = string(payload, "url");
        Config config = findConfig(payload, type);
        if (config == null || config.isEmpty()) return RemoteCommandResult.failure("Config not found");
        String previousVodUrl = type == 0 ? VodConfig.getUrl() : null;
        Config liveConfig = type == 0 ? matchingLiveConfig(config, previousVodUrl) : null;
        App.post(() -> {
            if (type == 1) LiveConfig.load(config, new Callback());
            else if (type == 2) WallConfig.load(config, new Callback());
            else {
                VodConfig.load(config, new Callback());
                if (liveConfig != null) LiveConfig.load(liveConfig, new Callback());
            }
        });
        return RemoteCommandResult.success("Config switched", data());
    }

    private static Config matchingLiveConfig(Config config, String previousVodUrl) {
        if (!ConfigSyncPolicy.shouldSyncLive(previousVodUrl, LiveConfig.getUrl())) return null;
        return AppDatabase.get().getConfigDao().find(config.getUrl(), 1);
    }

    public static RemoteCommandResult delete(JsonObject payload) {
        int type = number(payload, "type", 0);
        Config config = findConfig(payload, type);
        if (config == null || config.isEmpty()) return RemoteCommandResult.failure("Config not found");
        config.delete();
        return RemoteCommandResult.success("Config deleted", data());
    }

    private static Config findByAddressAlias(JsonObject payload, int type) {
        for (Config config : Config.getAll(type)) {
            for (String alias : strings(payload, "addressMatchAliases")) if (config.getAddressMatchAliases().contains(alias)) return config;
        }
        return null;
    }

    private static List<String> strings(JsonObject payload, String key) {
        List<String> result = new ArrayList<>();
        JsonElement element = payload == null ? null : payload.get(key);
        if (element == null || !element.isJsonArray()) return result;
        for (JsonElement item : element.getAsJsonArray()) if (item != null && item.isJsonPrimitive()) result.add(item.getAsString());
        return result;
    }

    private static Config findConfig(JsonObject payload, int type) {
        String interfaceKey = string(payload, "interfaceKey");
        if (!TextUtils.isEmpty(interfaceKey)) {
            Config config = AppDatabase.get().getConfigDao().findByInterfaceKey(interfaceKey, type);
            if (config != null) return config;
        }
        String url = string(payload, "url");
        return TextUtils.isEmpty(url) ? null : AppDatabase.get().getConfigDao().find(url, type);
    }

    public static RemoteCommandResult sites(JsonObject payload) {
        Config config = payloadVodConfig(payload);
        if (config == null || config.isEmpty()) return RemoteCommandResult.failure("Missing vod config url");
        try {
            return RemoteCommandResult.success("", RemoteConfigSiteParser.parse(config, homeKey(config, payload), homeName(config, payload)));
        } catch (Throwable e) {
            return RemoteCommandResult.failure(e.getMessage());
        }
    }

    public static RemoteCommandResult home(JsonObject payload) {
        Config config = vodConfig(payload);
        String key = string(payload, "key");
        if (config == null || config.isEmpty()) return RemoteCommandResult.failure("Missing vod config url");
        if (TextUtils.isEmpty(key)) return RemoteCommandResult.failure("Missing site key");
        String error = loadVod(config);
        if (!TextUtils.isEmpty(error)) return RemoteCommandResult.failure(error);
        Site site = VodConfig.get().getSite(key);
        if (site == null || TextUtils.isEmpty(site.getKey())) return RemoteCommandResult.failure("Site not found");
        VodConfig.get().setHome(site);
        return RemoteCommandResult.success("Home site updated", sitesData(config));
    }

    private static JsonObject data() {
        JsonObject object = new JsonObject();
        JsonArray items = new JsonArray();
        List<String> keys = new ArrayList<>();
        for (int type = 0; type <= 2; type++) {
            for (Config config : Config.getAll(type)) addItem(items, keys, config, false);
            Config current = current(type);
            if (!current.isEmpty()) addItem(items, keys, current, true);
        }
        object.add("items", items);
        return object;
    }

    private static void addItem(JsonArray items, List<String> keys, Config config, boolean forceActive) {
        String key = config.getType() + "|" + config.ensureInterfaceKey();
        if (keys.contains(key)) return;
        keys.add(key);
        items.add(item(config, forceActive));
    }

    private static JsonObject item(Config config, boolean forceActive) {
        JsonObject item = new JsonObject();
        item.addProperty("type", config.getType());
        item.addProperty("typeName", typeName(config.getType()));
        item.addProperty("name", config.getName());
        item.addProperty("url", config.getUrl());
        item.addProperty("interfaceKey", config.ensureInterfaceKey());
        item.add("urls", App.gson().toJsonTree(config.getUrls()));
        item.addProperty("desc", config.getDesc());
        item.addProperty("time", config.getTime());
        item.addProperty("active", forceActive || isCurrent(config));
        if (config.getType() == 0 && isCurrent(config) && VodConfig.get().getHome() != null) {
            item.addProperty("homeKey", VodConfig.get().getHome().getKey());
            item.addProperty("homeName", VodConfig.get().getHome().getName());
        }
        return item;
    }

    private static boolean isCurrent(Config config) {
        return TextUtils.equals(current(config.getType()).getUrl(), config.getUrl());
    }

    private static Config current(int type) {
        if (type == 1) return LiveConfig.get().getConfig();
        if (type == 2) return WallConfig.get().getConfig();
        return VodConfig.get().getConfig();
    }

    private static JsonObject sitesData(Config config) {
        JsonObject data = new JsonObject();
        JsonArray sites = new JsonArray();
        for (Site site : VodConfig.get().getSites()) {
            if (site == null || TextUtils.isEmpty(site.getKey())) continue;
            JsonObject item = new JsonObject();
            item.addProperty("key", site.getKey());
            item.addProperty("name", site.getName());
            item.addProperty("displayName", site.getDisplayName());
            item.addProperty("homePage", site.hasHomePage());
            item.addProperty("selected", TextUtils.equals(site.getKey(), VodConfig.get().getHome().getKey()));
            sites.add(item);
        }
        data.add("sites", sites);
        data.addProperty("url", config.getUrl());
        data.addProperty("name", config.getName());
        return data;
    }

    private static Config payloadVodConfig(JsonObject payload) {
        String url = string(payload, "url");
        String name = string(payload, "name");
        if (TextUtils.isEmpty(url)) return null;
        return new Config().type(0).url(url).name(name);
    }

    private static Config vodConfig(JsonObject payload) {
        String url = string(payload, "url");
        String name = string(payload, "name");
        if (TextUtils.isEmpty(url)) return null;
        return Config.find(url, 0).name(name).save();
    }

    private static String homeKey(Config config, JsonObject payload) {
        if (isCurrent(config)) return VodConfig.get().getHome().getKey();
        return string(payload, "homeKey");
    }

    private static String homeName(Config config, JsonObject payload) {
        if (isCurrent(config)) return VodConfig.get().getHome().getName();
        return string(payload, "homeName");
    }

    private static String loadVod(Config config) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>("");
        VodConfig.load(config, new Callback() {
            @Override
            public void success() {
                latch.countDown();
            }

            @Override
            public void error(String msg) {
                error.set(TextUtils.isEmpty(msg) ? "Load config failed" : msg);
                latch.countDown();
            }
        });
        try {
            if (!latch.await(6, TimeUnit.SECONDS)) return "Load config timed out";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Load config interrupted";
        }
        return error.get();
    }

    private static String typeName(int type) {
        if (type == 1) return "直播";
        if (type == 2) return "壁纸";
        return "点播";
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        return object.get(key).getAsString().trim();
    }

    private static List<String> urls(JsonObject object) {
        List<String> result = new ArrayList<>();
        if (object == null || !object.has("urls") || !object.get("urls").isJsonArray()) return result;
        for (JsonElement item : object.getAsJsonArray("urls")) {
            if (item != null && item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) result.add(item.getAsString());
        }
        return result;
    }

    private static int number(JsonObject object, String key, int fallback) {
        try {
            if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
            return object.get(key).getAsInt();
        } catch (Throwable e) {
            return fallback;
        }
    }
}
