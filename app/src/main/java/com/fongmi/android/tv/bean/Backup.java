package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.github.catvod.utils.Prefers;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Backup {

    private static final Set<String> APP_PREFS = Set.of("doh", "ua", "wall", "wall_type", "reset", "site_mode", "sync_mode", "incognito", "drive_check", "drive_check_cache", "shell_proxy", "shell_proxy_url", "shell_proxy_hosts", "update", "adblock", "zhuyin", "theme_color", "wall_color", "crash", "render", "size", "scale", "buffer", "background", "speed", "caption", "tunnel", "audio_prefer", "video_prefer", "prefer_aac", "subtitle_text_size", "subtitle_position", "boot_live", "across", "change", "invert", "scale_live");

    @SerializedName("site")
    private List<Site> site;
    @SerializedName("live")
    private List<Live> live;
    @SerializedName("keep")
    private List<Keep> keep;
    @SerializedName("config")
    private List<Config> config;
    @SerializedName("history")
    private List<History> history;
    @SerializedName("prefers")
    private Map<String, ?> prefers;

    public static Backup create() {
        Backup backup = new Backup();
        backup.setPrefers(Prefers.getPrefers().getAll());
        backup.setSite(AppDatabase.get().getSiteDao().findAll());
        backup.setLive(AppDatabase.get().getLiveDao().findAll());
        backup.setKeep(AppDatabase.get().getKeepDao().findAll());
        backup.setConfig(AppDatabase.get().getConfigDao().findAll());
        backup.setHistory(AppDatabase.get().getHistoryDao().findAll());
        return backup;
    }

    public static Backup create(SyncOptions options) {
        Backup backup = new Backup();
        if (options.isConfig()) {
            backup.setSite(AppDatabase.get().getSiteDao().findAll());
            backup.setLive(AppDatabase.get().getLiveDao().findAll());
            backup.setConfig(AppDatabase.get().getConfigDao().findAll());
        }
        if (options.isKeep()) backup.setKeep(AppDatabase.get().getKeepDao().findAll());
        if (options.isHistory()) backup.setHistory(AppDatabase.get().getHistoryDao().findAll());
        backup.setPrefers(filter(Prefers.getPrefers().getAll(), options));
        return backup;
    }

    public static Backup objectFrom(String json) {
        try {
            Gson gson = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LAZILY_PARSED_NUMBER).create();
            Backup backup = gson.fromJson(json, Backup.class);
            return backup == null ? new Backup() : backup;
        } catch (Exception e) {
            return new Backup();
        }
    }

    public void restore() {
        AppDatabase.get().clearAllTables();
        AppDatabase.get().getSiteDao().insertOrUpdate(getSite());
        AppDatabase.get().getLiveDao().insertOrUpdate(getLive());
        AppDatabase.get().getKeepDao().insertOrUpdate(getKeep());
        AppDatabase.get().getConfigDao().insertOrUpdate(getConfig());
        AppDatabase.get().getHistoryDao().insertOrUpdate(getHistory());
        for (Map.Entry<String, ?> entry : getPrefers().entrySet()) Prefers.put(entry.getKey(), entry.getValue());
    }

    public void restore(SyncOptions options, boolean force) {
        Map<Integer, Integer> cids = new HashMap<>();
        if (options.isConfig()) {
            if (force) {
                AppDatabase.get().getSiteDao().delete();
                AppDatabase.get().getLiveDao().delete();
                AppDatabase.get().getConfigDao().delete();
            }
            AppDatabase.get().getSiteDao().insertOrUpdate(getSite());
            AppDatabase.get().getLiveDao().insertOrUpdate(getLive());
            cids.putAll(restoreConfig());
        }
        if (options.isKeep()) {
            if (force) AppDatabase.get().getKeepDao().deleteAll();
            for (Keep item : getKeep()) if (cids.containsKey(item.getCid())) item.setCid(cids.get(item.getCid()));
            AppDatabase.get().getKeepDao().insertOrUpdate(getKeep());
        }
        if (options.isHistory()) {
            if (force) AppDatabase.get().getHistoryDao().delete();
            for (History item : getHistory()) if (cids.containsKey(item.getCid())) item.setCid(cids.get(item.getCid()));
            AppDatabase.get().getHistoryDao().insertOrUpdate(getHistory());
        }
        for (Map.Entry<String, ?> entry : filter(getPrefers(), options).entrySet()) Prefers.put(entry.getKey(), entry.getValue());
        if (options.isConfig()) reloadConfig();
        if (options.isKeep()) RefreshEvent.keep();
        if (options.isHistory()) RefreshEvent.history();
        RefreshEvent.home();
    }

    private void reloadConfig() {
        VodConfig.get().init().load(new Callback());
        LiveConfig.get().init().load();
        WallConfig.get().init().load();
        ConfigEvent.common();
    }

    private Map<Integer, Integer> restoreConfig() {
        Map<Integer, Integer> cids = new HashMap<>();
        for (Config item : getConfig()) {
            int source = item.getId();
            Config current = AppDatabase.get().getConfigDao().find(item.getUrl(), item.getType());
            item.setId(current == null ? 0 : current.getId());
            long id = AppDatabase.get().getConfigDao().insert(item);
            if (id == -1) AppDatabase.get().getConfigDao().update(item);
            else item.setId(Math.toIntExact(id));
            if (source > 0) cids.put(source, item.getId());
        }
        return cids;
    }

    private static Map<String, ?> filter(Map<String, ?> source, SyncOptions options) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            if (entry.getValue() != null && include(entry.getKey(), options)) result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static boolean include(String key, SyncOptions options) {
        if (key.startsWith("cache_")) return options.isWebHome() || options.isSpider();
        if (key.startsWith("config_")) return options.isConfig();
        if ("keyword".equals(key) || "hot".equals(key)) return options.isSearch();
        if (isAppPref(key)) return options.isSettings();
        return options.isSpider();
    }

    private static boolean isAppPref(String key) {
        return APP_PREFS.contains(key) || key.startsWith("danmaku_");
    }

    public List<Site> getSite() {
        return site == null ? Collections.emptyList() : site;
    }

    public void setSite(List<Site> site) {
        this.site = site;
    }

    public List<Live> getLive() {
        return live == null ? Collections.emptyList() : live;
    }

    public void setLive(List<Live> live) {
        this.live = live;
    }

    public List<Keep> getKeep() {
        return keep == null ? Collections.emptyList() : keep;
    }

    public void setKeep(List<Keep> keep) {
        this.keep = keep;
    }

    public List<Config> getConfig() {
        return config == null ? Collections.emptyList() : config;
    }

    public void setConfig(List<Config> config) {
        this.config = config;
    }

    public List<History> getHistory() {
        return history == null ? Collections.emptyList() : history;
    }

    public void setHistory(List<History> history) {
        this.history = history;
    }

    public Map<String, ?> getPrefers() {
        return prefers == null ? new HashMap<>() : prefers;
    }

    public void setPrefers(Map<String, ?> prefers) {
        this.prefers = prefers;
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }
}
