package com.fongmi.android.tv.bean;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.HlsRuleConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.web.HomeWebController;
import com.fongmi.android.tv.web.ext.WebHomeExtensionRegistry;
import com.github.catvod.utils.Prefers;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.ToNumberPolicy;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Backup {

    public static final String PREF_WEB_HOME_EXTENSION = "web_home_extension";
    public static final String PREF_WEB_HOME_EXTENSION_SOURCES = "web_home_extension_user_sources";

    private static final Set<String> APP_PREFS = Set.of("doh", "ua", "wall", "wall_type", "reset", "site_mode", "site_block_keys", "site_names", "search_column", "sync_mode", "sync_paths", "incognito", "drive_check", "drive_check_cache", "compact_episode_title", "web_home_fullscreen", "web_home_theme_enabled", "web_home_theme_url", "audio_config", "short_drama_config", "tmdb_enabled", "tmdb_config", "tmdb_model", "ai_config", "ai_title_extraction", "ai_ad_detection", "user_ad_rules", "user_group_rules", "disabled_group_rule_ids", "disabled_default_rule_ids", "builtin_hls_rule_overrides", "subtitle_ai_max_concurrency", "subtitle_ai_chunk_count", "detail_open_mode", "detail_interaction_mode", "detail_theme_mode", "tmdb_detail_theme", "tmdb_detail_backdrop_slide", "personal_recommendation", "episode_history", "global_history_mode", "history_aggregation_by_tmdb", "ai_recommendation", "auto_skip_intro_outro", "viewing_record_sync_enabled", "viewing_record_sync_local_write", "playback_remote_sync_config", "playback_webhook_config", "playback_webhook_privacy_accepted", "shell_proxy", "shell_proxy_rules", "shell_proxy_url", "shell_proxy_hosts", "github_proxy", "github_proxy_enabled", "github_proxy_mode", "update", "adblock", "zhuyin", "theme_color", "wall_color", "crash", "render", "ffmpeg_mode", "pad_live_mode", "size", "scale", "custom_aspect_width", "custom_aspect_height", "buffer", "buffer_bytes", "back_buffer", "play_cache", "preload", "preload_threads", "preload_size", "preload_time", "player_auto_change", "player_failure_fallback", "background", "speed", "play_speed", "caption", "tunnel", "exo_4k_compat", "playback_performance_profile", "playback_performance_initialized", "perf_codec_async_queueing", "perf_dynamic_scheduling", "perf_video_duration_progress", "perf_late_drop_input", "perf_track_limit", "perf_adaptive_downgrade", "perf_load_only_selected_tracks", "perf_surface_fixed_size", "perf_decoder_fallback", "perf_soft_video_tune", "perf_high_buffer", "perf_bandwidth_meter", "perf_exo_network_protection_mode", "player_button_order", "player_button_hidden", "audio_prefer", "video_prefer", "prefer_aac", "subtitle_text_size", "subtitle_position", "display_time", "display_traffic", "display_size", "display_progress", "display_mini", "display_title", "player_osd_title", "player_osd_resolution", "player_osd_time", "player_osd_progress", "player_osd_traffic", "player_osd_mini", "player_osd_diagnostics", "boot_live", "across", "change", "invert", "scale_live", "live_epg_url", "live_epg_history");

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
    @SerializedName("tmdbSeasonProgress")
    private List<TmdbSeasonProgress> tmdbSeasonProgress;
    @SerializedName("track")
    private List<Track> track;
    @SerializedName("device")
    private List<Device> device;
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
        backup.setTmdbSeasonProgress(AppDatabase.get().getTmdbSeasonProgressDao().findAll());
        backup.setTrack(AppDatabase.get().getTrackDao().findAll());
        backup.setDevice(AppDatabase.get().getDeviceDao().findAll());
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
        if (options.isHistory()) {
            backup.setHistory(AppDatabase.get().getHistoryDao().findAll());
            backup.setTmdbSeasonProgress(AppDatabase.get().getTmdbSeasonProgressDao().findAll());
        }
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
        restore(true);
    }

    public void restore(boolean preserveMissingWebHomePrefs) {
        AppDatabase.get().clearAllTables();
        AppDatabase.get().getSiteDao().insertOrUpdate(getSite());
        AppDatabase.get().getLiveDao().insertOrUpdate(getLive());
        AppDatabase.get().getKeepDao().insertOrUpdate(getKeep());
        AppDatabase.get().getConfigDao().insertOrUpdate(getConfig());
        AppDatabase.get().getHistoryDao().insertOrUpdate(getHistory());
        restoreTmdbSeasonProgress(Collections.emptyMap());
        AppDatabase.get().getTrackDao().insertOrUpdate(getTrack());
        AppDatabase.get().getDeviceDao().insertOrUpdate(getDevice());
        restorePrefers(getPrefers(), true, preserveMissingWebHomePrefs);
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
            if (force) AppDatabase.get().getTmdbSeasonProgressDao().deleteAll();
            for (History item : getHistory()) if (cids.containsKey(item.getCid())) item.setCid(cids.get(item.getCid()));
            AppDatabase.get().getHistoryDao().insertOrUpdate(getHistory());
            restoreTmdbSeasonProgress(cids);
        }
        restorePrefers(filter(getPrefers(), options), false, false);
        if (options.isConfig() || options.isSpider() || options.isWebHome() || options.isLoginState()) reloadConfig();
        if (options.isWebHome()) refreshWebHomeExtensions();
        if (options.isKeep()) RefreshEvent.keep();
        if (options.isHistory()) RefreshEvent.history();
        RefreshEvent.home();
    }

    private void reloadConfig() {
        VodConfig.get().clear("sync-restore").init().load(new Callback());
        LiveConfig.get().clear().init().load();
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

    private void restoreTmdbSeasonProgress(Map<Integer, Integer> cids) {
        List<TmdbSeasonProgress> items = sanitizeTmdbSeasonProgress(getTmdbSeasonProgress());
        if (!items.isEmpty()) {
            for (TmdbSeasonProgress item : items) {
                if (cids.containsKey(item.cid)) item.cid = cids.get(item.cid);
            }
            AppDatabase.get().getTmdbSeasonProgressDao().insertOrUpdate(items);
            return;
        }
        AppDatabase.get().getTmdbSeasonProgressDao().insertOrUpdate(
                latestTmdbSeasonProgress(getHistory()));
    }

    static List<TmdbSeasonProgress> latestTmdbSeasonProgress(List<History> histories) {
        Map<String, TmdbSeasonProgress> latest = new LinkedHashMap<>();
        if (histories == null) return List.of();
        for (History history : histories) {
            TmdbSeasonProgress progress = com.fongmi.android.tv.playback.TmdbSeasonProgressStore.fromHistory(history);
            if (progress == null) continue;
            TmdbSeasonProgress previous = latest.get(progress.identityKey());
            if (previous == null || progress.updatedAt > previous.updatedAt) {
                latest.put(progress.identityKey(), progress);
            }
        }
        return new ArrayList<>(latest.values());
    }

    private static void normalize(TmdbSeasonProgress item) {
        if (item == null) return;
        item.mediaType = TmdbSeasonProgress.normalizeMediaType(item.mediaType);
        if (item.sourceFlag == null) item.sourceFlag = "";
        if (item.sourceEpisodeName == null) item.sourceEpisodeName = "";
        if (item.sourceEpisodeUrl == null) item.sourceEpisodeUrl = "";
        if (item.sourceHistoryKey == null) item.sourceHistoryKey = "";
        if (item.sourceBindingKey == null) item.sourceBindingKey = "";
    }

    static List<TmdbSeasonProgress> sanitizeTmdbSeasonProgress(List<TmdbSeasonProgress> source) {
        List<TmdbSeasonProgress> result = new ArrayList<>();
        if (source == null) return result;
        for (TmdbSeasonProgress item : source) {
            if (item == null) continue;
            normalize(item);
            if (!"tv".equals(item.mediaType) || item.tmdbId <= 0 || item.seasonNumber < 0
                    || item.episodeNumber <= 0 || item.sourceHistoryKey.isEmpty()) continue;
            result.add(item);
        }
        return result;
    }

    private static Map<String, ?> filter(Map<String, ?> source, SyncOptions options) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            if (entry.getValue() != null && include(entry.getKey(), options)) result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    static boolean include(String key, SyncOptions options) {
        if (key.startsWith("playback_experiment_")) return false;
        if (key.startsWith("playback_performance_profile_merge_")) {
            return false;
        }
        if ("playback_performance_profile_auto_light_v1".equals(key)) {
            return false;
        }
        if ("perf_exo_single_rate_rescue_enabled_v1".equals(key)) {
            return false;
        }
        if (key.startsWith("remote_trust_")) return false;
        if (isWebHomeExtensionPref(key)) return options.isWebHome();
        if (key.startsWith("cache_")) return false;
        if (key.startsWith("config_")) return options.isConfig();
        if ("site_names".equals(key)) return options.isConfig() || options.isSettings();
        if ("keyword".equals(key) || "hot".equals(key) || key.startsWith("hot_")) return options.isSearch();
        if ("git_cloud_accounts".equals(key)) return options.isSpider() || options.isSettings() || options.isLoginState();
        if (key.startsWith("login_state_")) return options.isLoginState();
        if (isAppPref(key)) return options.isSettings();
        return options.isSpider();
    }

    static boolean isWebHomeExtensionPref(String key) {
        return PREF_WEB_HOME_EXTENSION.equals(key) || key.startsWith("web_home_extension_") || key.startsWith("web_home_ext_enabled_");
    }

    private static boolean isAppPref(String key) {
        return APP_PREFS.contains(key)
                // 三个 update_github_proxy* 是已废弃的旧键，但必须继续随「设置」备份走：
                // 从旧版备份恢复时它们要落盘，Setting.migrateLegacyGithubProxy() 才会触发
                // 并把用户当年的代理选择迁到 github_proxy。剔掉它们等于把那个选择丢弃而非迁移。
                || Set.of("update_source", "update_github_proxy", "update_github_proxy_url", "update_github_proxy_mode", "update_oci_mirror", "update_oci_mirror_url").contains(key)
                || key.startsWith("danmaku_") || key.startsWith("playback_performance_") || key.startsWith("perf_exo_") || key.startsWith("perf_mpv_") || key.startsWith("perf_ijk_") || key.startsWith("perf_kernel_");
    }

    private static void restorePrefers(Map<String, ?> values, boolean clear, boolean preserveMissingWebHomePrefs) {
        Map<String, Object> preserved = new HashMap<>();
        if (clear && preserveMissingWebHomePrefs) {
            for (Map.Entry<String, ?> entry : Prefers.getPrefers().getAll().entrySet()) {
                if (isWebHomeExtensionPref(entry.getKey()) && !values.containsKey(entry.getKey())) preserved.put(entry.getKey(), entry.getValue());
            }
        }
        SharedPreferences.Editor editor = Prefers.getPrefers().edit();
        if (clear) editor.clear();
        if (containsPlaybackPerformanceProfile(values)) {
            editor.remove("playback_performance_profile_auto_light_v1");
        }
        putPrefers(editor, preserved);
        putPrefers(editor, values);
        editor.commit();
        HlsRuleConfig.invalidate();
    }

    private static boolean containsPlaybackPerformanceProfile(
            Map<String, ?> values) {
        return values.containsKey("playback_performance_profile")
                || values.containsKey("perf_exo_profile")
                || values.containsKey("perf_mpv_profile")
                || values.containsKey("perf_ijk_profile");
    }

    private static void putPrefers(SharedPreferences.Editor editor, Map<String, ?> values) {
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
            else if (value instanceof Number) {
                Number number = (Number) value;
                if (number.toString().contains(".")) editor.putFloat(entry.getKey(), number.floatValue());
                else editor.putInt(entry.getKey(), number.intValue());
            }
        }
    }

    public static void refreshWebHomeExtensions() {
        WebHomeExtensionRegistry.get().clear();
        HomeWebController.requestExtensionReload();
    }

    public int getWebHomeExtensionPreferenceCount() {
        int count = 0;
        for (String key : getPrefers().keySet()) if (isWebHomeExtensionPref(key)) count++;
        return count;
    }

    public int getWebHomeExtensionSourceCount() {
        Object value = getPrefers().get(PREF_WEB_HOME_EXTENSION_SOURCES);
        if (!(value instanceof String) || ((String) value).isEmpty()) return 0;
        try {
            JsonElement element = new Gson().fromJson((String) value, JsonElement.class);
            return element != null && element.isJsonArray() ? element.getAsJsonArray().size() : 0;
        } catch (Throwable e) {
            return 0;
        }
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

    public List<TmdbSeasonProgress> getTmdbSeasonProgress() {
        return tmdbSeasonProgress == null ? Collections.emptyList() : tmdbSeasonProgress;
    }

    public void setTmdbSeasonProgress(List<TmdbSeasonProgress> progress) {
        this.tmdbSeasonProgress = progress;
    }

    public List<Track> getTrack() {
        return track == null ? Collections.emptyList() : track;
    }

    public void setTrack(List<Track> track) {
        this.track = track;
    }

    public List<Device> getDevice() {
        return device == null ? Collections.emptyList() : device;
    }

    public void setDevice(List<Device> device) {
        this.device = device;
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
