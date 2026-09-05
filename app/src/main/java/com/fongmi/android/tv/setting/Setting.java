package com.fongmi.android.tv.setting;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.LocaleList;
import android.provider.Settings;
import android.util.DisplayMetrics;

import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.bean.AudioConfig;
import com.fongmi.android.tv.bean.DanmakuMatchCache;
import com.fongmi.android.tv.bean.ShortDramaConfig;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbMatchCache;
import com.fongmi.android.tv.bean.TmdbSeasonMatchCache;
import com.fongmi.android.tv.bean.Update;
import com.fongmi.android.tv.utils.AppCache;
import com.fongmi.android.tv.update.OciMirror;
import com.fongmi.android.tv.update.UpdateSource;
import com.fongmi.android.tv.utils.GithubProxy;
import com.fongmi.android.tv.utils.WebViewUtil;
import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Trans;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class Setting {

    public static final String REALTIME_SUBTITLE_MODEL_ZH = "zh";
    public static final String REALTIME_SUBTITLE_MODEL_YUE = "yue";
    public static final String REALTIME_SUBTITLE_MODEL_EN = "en";
    public static final String REALTIME_SUBTITLE_MODEL_DE = "de";
    public static final String REALTIME_SUBTITLE_MODEL_FR = "fr";
    public static final String REALTIME_SUBTITLE_MODEL_ES = "es";
    public static final String REALTIME_SUBTITLE_MODEL_JA = "ja";
    public static final String REALTIME_SUBTITLE_MODEL_ZH_EN = "zh-en";

    public static final int TMDB_MODEL_NATIVE = 0;
    public static final int DETAIL_OPEN_FUSION = 0;
    public static final int DETAIL_OPEN_ENHANCED = 1;
    public static final int DETAIL_OPEN_DIRECT = 2;
    public static final int DETAIL_OPEN_CINEMA = 3;
    public static final int DETAIL_OPEN_PLAYER = 4;
    public static final int DETAIL_OPEN_ORIGINAL_ENHANCED = 5;
    public static final int DETAIL_STYLE_PROFILE = 0;
    public static final int DETAIL_STYLE_CINEMA = 1;
    public static final int DETAIL_STYLE_NATIVE = 2;
    public static final int TMDB_MATCH_STRICT = 0;
    public static final int TMDB_MATCH_SMART = 1;
    public static final int TMDB_MATCH_STRICT_DIALOG = 2;
    public static final int TMDB_MATCH_SMART_DIALOG = 3;
    public static final int GLOBAL_HISTORY_OFF = 0;
    public static final int GLOBAL_HISTORY_AUTO = 1;
    public static final int GLOBAL_HISTORY_SEARCH = 2;
    public static final int DETAIL_INTERACTION_SYSTEM = 0;
    public static final int INTRO_SKIP_OFF = 0;
    public static final int INTRO_SKIP_AUTO = 1;
    public static final int INTRO_SKIP_CONFIRM = 2;
    public static final int INTRO_SKIP_KIND_RECAP = 1;
    public static final int INTRO_SKIP_KIND_INTRO = 1 << 1;
    public static final int INTRO_SKIP_KIND_OUTRO = 1 << 2;
    public static final int INTRO_SKIP_KIND_PREVIEW = 1 << 3;
    public static final int INTRO_SKIP_KIND_ALL = INTRO_SKIP_KIND_RECAP | INTRO_SKIP_KIND_INTRO | INTRO_SKIP_KIND_OUTRO | INTRO_SKIP_KIND_PREVIEW;
    public static final int INTRO_SKIP_KIND_DEFAULT = INTRO_SKIP_KIND_RECAP | INTRO_SKIP_KIND_INTRO | INTRO_SKIP_KIND_OUTRO;
    public static final int DETAIL_INTERACTION_ORIGINAL = 1;
    public static final int DETAIL_THEME_CURRENT = DETAIL_STYLE_NATIVE;
    private static final Type STRING_LIST = new TypeToken<List<String>>() {}.getType();

    public static final int LANGUAGE_FOLLOW_SYSTEM = 0;
    public static final int LANGUAGE_SIMPLIFIED = 1;
    public static final int LANGUAGE_TRADITIONAL = 2;
    private static final int[] LANGUAGE_OPTIONS = {LANGUAGE_FOLLOW_SYSTEM, LANGUAGE_SIMPLIFIED, LANGUAGE_TRADITIONAL};

    public static final int CSP_WARMUP_DISABLED = 0;
    public static final int CSP_WARMUP_DEFAULT = 1;
    public static final int CSP_WARMUP_CUSTOM = 2;

    public static final int UI_SCALE_FOLLOW_SYSTEM = 0;
    public static final int UI_SCALE_STANDARD = 1;
    public static final int UI_SCALE_COMPACT = 2;
    public static final int UI_SCALE_SMALLER = 3;
    public static final int UI_SCALE_MILD_COMPACT = 4;
    public static final int UI_SCALE_MORE_COMPACT = 5;
    private static final int[] UI_SCALE_OPTIONS = {UI_SCALE_FOLLOW_SYSTEM, UI_SCALE_STANDARD, UI_SCALE_MILD_COMPACT, UI_SCALE_COMPACT, UI_SCALE_MORE_COMPACT, UI_SCALE_SMALLER};

    public static final int WALL_CINEMA = 5;
    public static final int WALL_CINEMA_WARM = 6;
    public static final int WALL_CINEMA_MOSS = 7;
    public static final int WALL_CINEMA_BLUE = 8;
    public static final int WALL_CINEMA_CLAY = 9;
    public static final int WALL_AURORA_GLASS = 10;
    public static final int WALL_SUNSET_PRISM = 11;
    public static final int WALL_MINT_GLACIER = 12;
    public static final int WALL_LIQUID_CHROME = 13;
    public static final int WALL_NEON_BERRY = 14;
    public static final int WALL_CHAMPAGNE_MIST = 15;
    public static final int WALL_GLASS_GRADIENT = 16;
    public static final int WALL_DEEP_SPACE_GLASS = 17;
    public static final int WALL_POLAR_LIGHT_GLASS = 18;
    public static final int WALL_NEON_CYBER = 19;
    public static final int WALL_WARM_MOON_GLASS = 20;
    public static final int WALL_CRYSTAL_SKY = 21;
    public static final int WALL_DREAM_PURPLE = 22;
    public static final int WALL_SKY_MINT = 23;
    public static final int WALL_FOREST_MIST = 24;
    public static final int WALL_DAYLIGHT_MINIMAL = 25;
    public static final int WALL_DEEP_SEA = 26;
    public static final int WALL_VIOLET_SMOKE = 27;
    public static final int WALL_ROSE_VEIL = 28;
    public static final int WALL_EMERALD_AURORA = 29;
    public static final int WALL_BLUE_SILK = 30;
    public static final int WALL_PEACH_DAWN = 31;
    public static final int WALL_GRAPHITE_SMOKE = 32;
    public static final int WALL_PASTEL_PRISM = 33;
    public static final int WALL_MIDNIGHT_MOON = 34;
    public static final int WALL_CYAN_CRYSTAL = 35;
    public static final int WALL_LAVENDER_CRYSTAL = 36;
    public static final int WALL_GREEN = 1;

    private static final int[] DEFAULT_WALLS = {
            WALL_DREAM_PURPLE, WALL_LAVENDER_CRYSTAL, WALL_PASTEL_PRISM, WALL_ROSE_VEIL, WALL_VIOLET_SMOKE,
            WALL_NEON_BERRY, WALL_MIDNIGHT_MOON, WALL_NEON_CYBER, WALL_DEEP_SPACE_GLASS, WALL_GRAPHITE_SMOKE,
            WALL_DAYLIGHT_MINIMAL, WALL_SKY_MINT, WALL_POLAR_LIGHT_GLASS, WALL_GLASS_GRADIENT, WALL_CRYSTAL_SKY,
            WALL_BLUE_SILK, WALL_CYAN_CRYSTAL, WALL_MINT_GLACIER, WALL_AURORA_GLASS, WALL_DEEP_SEA,
            WALL_LIQUID_CHROME, WALL_FOREST_MIST, WALL_EMERALD_AURORA, WALL_WARM_MOON_GLASS, WALL_PEACH_DAWN,
            WALL_CHAMPAGNE_MIST, WALL_SUNSET_PRISM
    };

    public static String getDoh() {
        return Prefers.getString("doh");
    }

    public static void putDoh(String doh) {
        Prefers.put("doh", doh);
    }

    public static String getKeyword() {
        return Prefers.getString("keyword");
    }

    public static void putKeyword(String keyword) {
        Prefers.put("keyword", keyword);
    }

    public static String getHot() {
        return Prefers.getString("hot");
    }

    public static void putHot(String hot) {
        Prefers.put("hot", hot);
    }

    public static String getHotTv() {
        return Prefers.getString("hot_tv");
    }

    public static void putHotTv(String hot) {
        Prefers.put("hot_tv", hot);
    }

    public static String getHotMovie() {
        return Prefers.getString("hot_movie");
    }

    public static void putHotMovie(String hot) {
        Prefers.put("hot_movie", hot);
    }

    public static String getHotVariety() {
        return Prefers.getString("hot_variety");
    }

    public static void putHotVariety(String hot) {
        Prefers.put("hot_variety", hot);
    }

    public static String getUa() {
        return Prefers.getString("ua");
    }

    public static void putUa(String ua) {
        Prefers.put("ua", ua);
    }

    public static int getWall() {
        int wall = Prefers.getInt("wall", WALL_DREAM_PURPLE);
        return wall == WALL_GREEN || isLegacyColorWall(wall) ? WALL_DREAM_PURPLE : wall;
    }

    public static void putWall(int wall) {
        Prefers.put("wall", wall);
    }

    public static int getWallType() {
        return Prefers.getInt("wall_type", 0);
    }

    public static void putWallType(int type) {
        Prefers.put("wall_type", type);
    }

    public static int nextDefaultWall() {
        int wall = getWall();
        for (int i = 0; i < DEFAULT_WALLS.length; i++) {
            if (DEFAULT_WALLS[i] == wall) return DEFAULT_WALLS[(i + 1) % DEFAULT_WALLS.length];
        }
        return WALL_DREAM_PURPLE;
    }

    public static int[] getDefaultWalls() {
        return DEFAULT_WALLS.clone();
    }

    public static int getDefaultWallIndex(int wall) {
        for (int i = 0; i < DEFAULT_WALLS.length; i++) {
            if (DEFAULT_WALLS[i] == wall) return i;
        }
        return -1;
    }

    public static boolean isBuiltInWall(int wall) {
        return isBuiltInDesignWall(wall);
    }

    public static boolean isBuiltInColorWall(int wall) {
        return false;
    }

    private static boolean isLegacyColorWall(int wall) {
        return wall == WALL_CINEMA || wall == WALL_CINEMA_WARM || wall == WALL_CINEMA_MOSS || wall == WALL_CINEMA_BLUE || wall == WALL_CINEMA_CLAY;
    }

    public static boolean isBuiltInDesignWall(int wall) {
        return getDefaultWallIndex(wall) != -1;
    }

    public static int getBuiltInWallColor(int wall) {
        if (wall == WALL_AURORA_GLASS) return 0xFF2B8ECB;
        if (wall == WALL_SUNSET_PRISM) return 0xFFB65B88;
        if (wall == WALL_MINT_GLACIER) return 0xFF55BCA8;
        if (wall == WALL_LIQUID_CHROME) return 0xFF53657F;
        if (wall == WALL_NEON_BERRY) return 0xFF7B42CF;
        if (wall == WALL_CHAMPAGNE_MIST) return 0xFFB47692;
        if (wall == WALL_GLASS_GRADIENT) return 0xFF5E91B3;
        if (wall == WALL_DEEP_SPACE_GLASS) return 0xFF2E2B74;
        if (wall == WALL_POLAR_LIGHT_GLASS) return 0xFF6FA6B8;
        if (wall == WALL_NEON_CYBER) return 0xFF4B2BD8;
        if (wall == WALL_WARM_MOON_GLASS) return 0xFF9E7568;
        if (wall == WALL_CRYSTAL_SKY) return 0xFF7890C5;
        if (wall == WALL_DREAM_PURPLE) return 0xFF7560CA;
        if (wall == WALL_SKY_MINT) return 0xFF6DA6B1;
        if (wall == WALL_FOREST_MIST) return 0xFF4E8750;
        if (wall == WALL_DAYLIGHT_MINIMAL) return 0xFF7B8D9C;
        if (wall == WALL_DEEP_SEA) return 0xFF2F7290;
        if (wall == WALL_VIOLET_SMOKE) return 0xFF7C4BE2;
        if (wall == WALL_ROSE_VEIL) return 0xFFB27FAE;
        if (wall == WALL_EMERALD_AURORA) return 0xFF27B07D;
        if (wall == WALL_BLUE_SILK) return 0xFF5E9BB3;
        if (wall == WALL_PEACH_DAWN) return 0xFFC27863;
        if (wall == WALL_GRAPHITE_SMOKE) return 0xFF4B5360;
        if (wall == WALL_PASTEL_PRISM) return 0xFF8A84C8;
        if (wall == WALL_MIDNIGHT_MOON) return 0xFF4935B4;
        if (wall == WALL_CYAN_CRYSTAL) return 0xFF168BA6;
        if (wall == WALL_LAVENDER_CRYSTAL) return 0xFF8875D0;
        return 0xFF2B8ECB;
    }

    public static String getBuiltInWallName(int wall) {
        if (wall == WALL_AURORA_GLASS) return "蓝紫流光";
        if (wall == WALL_SUNSET_PRISM) return "珊瑚暮色";
        if (wall == WALL_MINT_GLACIER) return "薄荷星云";
        if (wall == WALL_LIQUID_CHROME) return "银色潮汐";
        if (wall == WALL_NEON_BERRY) return "莓果极光";
        if (wall == WALL_CHAMPAGNE_MIST) return "香槟晨雾";
        if (wall == WALL_GLASS_GRADIENT) return "玻璃渐变风";
        if (wall == WALL_DEEP_SPACE_GLASS) return "深空玻璃风";
        if (wall == WALL_POLAR_LIGHT_GLASS) return "极光轻玻璃风";
        if (wall == WALL_NEON_CYBER) return "暗夜霓虹";
        if (wall == WALL_WARM_MOON_GLASS) return "暖月玻璃风";
        if (wall == WALL_CRYSTAL_SKY) return "冰晶幻彩风";
        if (wall == WALL_DREAM_PURPLE) return "梦幻紫霞";
        if (wall == WALL_SKY_MINT) return "雾青薄荷";
        if (wall == WALL_FOREST_MIST) return "森林雾绿";
        if (wall == WALL_DAYLIGHT_MINIMAL) return "雾蓝极简";
        if (wall == WALL_DEEP_SEA) return "深海月影";
        if (wall == WALL_VIOLET_SMOKE) return "紫雾星旋";
        if (wall == WALL_ROSE_VEIL) return "玫瑰薄雾";
        if (wall == WALL_EMERALD_AURORA) return "翡翠极光";
        if (wall == WALL_BLUE_SILK) return "蓝绸流影";
        if (wall == WALL_PEACH_DAWN) return "暖桃晨光";
        if (wall == WALL_GRAPHITE_SMOKE) return "石墨烟岚";
        if (wall == WALL_PASTEL_PRISM) return "彩虹幻璃";
        if (wall == WALL_MIDNIGHT_MOON) return "午夜月影";
        if (wall == WALL_CYAN_CRYSTAL) return "水晶青蓝";
        if (wall == WALL_LAVENDER_CRYSTAL) return "薰衣水晶";
        return "梦幻紫霞";
    }

    public static String getWallDesc(String desc) {
        return getWallType() == 0 && isBuiltInWall(getWall()) ? getBuiltInWallName(getWall()) : desc;
    }

    public static int getReset() {
        return Prefers.getInt("reset", 0);
    }

    public static void putReset(int reset) {
        Prefers.put("reset", reset);
    }

    public static int getSiteMode() {
        return Prefers.getInt("site_mode");
    }

    public static void putSiteMode(int mode) {
        Prefers.put("site_mode", mode);
    }

    public static int getSyncMode() {
        return Prefers.getInt("sync_mode");
    }

    public static void putSyncMode(int mode) {
        Prefers.put("sync_mode", mode);
    }

    public static String getSyncPaths() {
        return Prefers.getString("sync_paths", "TV\nTVBox\nTVData");
    }

    public static void putSyncPaths(String paths) {
        Prefers.put("sync_paths", paths);
    }

    public static String getLoginStatePaths() {
        return Prefers.getString("login_state_paths");
    }

    public static void putLoginStatePaths(String paths) {
        Prefers.put("login_state_paths", paths);
    }

    public static String getLoginStatePendingPaths() {
        return Prefers.getString("login_state_pending_paths");
    }

    public static void putLoginStatePendingPaths(String paths) {
        Prefers.put("login_state_pending_paths", paths);
    }

    public static String getLoginStateSnapshot() {
        return Prefers.getString("login_state_snapshot");
    }

    public static void putLoginStateSnapshot(String snapshot) {
        Prefers.put("login_state_snapshot", snapshot);
    }

    public static String getLoginStateFindings() {
        return Prefers.getString("login_state_findings");
    }

    public static void putLoginStateFindings(String findings) {
        Prefers.put("login_state_findings", findings);
    }

    public static boolean isIncognito() {
        return Prefers.getBoolean("incognito");
    }

    public static void putIncognito(boolean incognito) {
        Prefers.put("incognito", incognito);
    }

    public static int getLanguage() {
        int language = Prefers.getInt("language", LANGUAGE_FOLLOW_SYSTEM);
        return isLanguage(language) ? language : LANGUAGE_FOLLOW_SYSTEM;
    }

    public static void putLanguage(int language) {
        int value = isLanguage(language) ? language : LANGUAGE_FOLLOW_SYSTEM;
        Prefers.put("language", value);
        applyLanguage(value);
        App.get().invalidateResources();
    }

    public static void applyLanguage() {
        applyLanguage(getLanguage());
    }

    public static int getLanguageIndex() {
        int language = getLanguage();
        for (int i = 0; i < LANGUAGE_OPTIONS.length; i++) if (LANGUAGE_OPTIONS[i] == language) return i;
        return LANGUAGE_FOLLOW_SYSTEM;
    }

    public static void putLanguageIndex(int index) {
        putLanguage(index >= 0 && index < LANGUAGE_OPTIONS.length ? LANGUAGE_OPTIONS[index] : LANGUAGE_FOLLOW_SYSTEM);
    }

    private static boolean isLanguage(int language) {
        for (int option : LANGUAGE_OPTIONS) if (option == language) return true;
        return false;
    }

    private static void applyLanguage(int language) {
        if (language == LANGUAGE_SIMPLIFIED) Trans.setTraditional(false);
        else if (language == LANGUAGE_TRADITIONAL) Trans.setTraditional(true);
        else Trans.setTraditional(null);
    }

    public static Context wrapDisplay(Context context) {
        return wrapUiScale(wrapLanguage(context));
    }

    public static Context wrapLanguage(Context context) {
        int language = getLanguage();
        applyLanguage(language);
        if (language == LANGUAGE_FOLLOW_SYSTEM) return context;
        Locale locale = language == LANGUAGE_TRADITIONAL ? Locale.TRADITIONAL_CHINESE : Locale.SIMPLIFIED_CHINESE;
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLocales(new LocaleList(locale));
        return context.createConfigurationContext(config);
    }

    public static int getUiScale() {
        int scale = Prefers.getInt("ui_scale", UI_SCALE_FOLLOW_SYSTEM);
        return isUiScale(scale) ? scale : UI_SCALE_FOLLOW_SYSTEM;
    }

    public static void putUiScale(int scale) {
        Prefers.put("ui_scale", isUiScale(scale) ? scale : UI_SCALE_FOLLOW_SYSTEM);
    }

    public static int getUiScaleIndex() {
        int scale = getUiScale();
        for (int i = 0; i < UI_SCALE_OPTIONS.length; i++) if (UI_SCALE_OPTIONS[i] == scale) return i;
        return UI_SCALE_FOLLOW_SYSTEM;
    }

    public static void putUiScaleIndex(int index) {
        putUiScale(index >= 0 && index < UI_SCALE_OPTIONS.length ? UI_SCALE_OPTIONS[index] : UI_SCALE_FOLLOW_SYSTEM);
    }

    private static boolean isUiScale(int scale) {
        for (int option : UI_SCALE_OPTIONS) if (option == scale) return true;
        return false;
    }

    public static Context wrapUiScale(Context context) {
        int scale = getUiScale();
        if (scale == UI_SCALE_FOLLOW_SYSTEM) return context;
        float factor = getUiScaleFactor(scale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int stableDensity = DisplayMetrics.DENSITY_DEVICE_STABLE > 0 ? DisplayMetrics.DENSITY_DEVICE_STABLE : metrics.densityDpi;
        int densityDpi = Math.max(DisplayMetrics.DENSITY_LOW, Math.round(stableDensity * factor));
        config.densityDpi = densityDpi;
        config.fontScale = 1.0f;
        config.screenWidthDp = pxToDp(metrics.widthPixels, densityDpi);
        config.screenHeightDp = pxToDp(metrics.heightPixels, densityDpi);
        config.smallestScreenWidthDp = Math.min(config.screenWidthDp, config.screenHeightDp);
        return context.createConfigurationContext(config);
    }

    private static float getUiScaleFactor(int scale) {
        return switch (scale) {
            case UI_SCALE_STANDARD -> 0.8f;
            case UI_SCALE_MILD_COMPACT -> 0.75f;
            case UI_SCALE_COMPACT -> 0.7f;
            case UI_SCALE_MORE_COMPACT -> 0.65f;
            case UI_SCALE_SMALLER -> 0.6f;
            default -> 1.0f;
        };
    }

    private static int pxToDp(int px, int densityDpi) {
        return Math.max(1, Math.round(px * (float) DisplayMetrics.DENSITY_DEFAULT / densityDpi));
    }

    public static boolean isDriveCheck() {
        return Prefers.getBoolean("drive_check", true);
    }

    public static void putDriveCheck(boolean driveCheck) {
        Prefers.put("drive_check", driveCheck);
    }

    public static int getSiteColumn() {
        return clampSiteColumn(Prefers.getInt("site_column", 1));
    }

    public static void putSiteColumn(int column) {
        Prefers.put("site_column", clampSiteColumn(column));
    }

    private static int clampSiteColumn(int column) {
        return column == 2 ? 2 : 1;
    }

    public static boolean isCompactEpisodeTitle() {
        return Prefers.getBoolean("compact_episode_title");
    }

    public static void putCompactEpisodeTitle(boolean compact) {
        Prefers.put("compact_episode_title", compact);
    }

    public static boolean isSiteHealthSort() {
        return Prefers.getBoolean("site_health_sort", true);
    }

    public static void putSiteHealthSort(boolean sort) {
        Prefers.put("site_health_sort", sort);
    }

    public static boolean isSiteHealthDialogSort() {
        return Prefers.getBoolean("site_health_dialog_sort");
    }

    public static void putSiteHealthDialogSort(boolean sort) {
        Prefers.put("site_health_dialog_sort", sort);
    }

    public static boolean isWebHomeExtension() {
        return Prefers.getBoolean("web_home_extension", true);
    }

    public static void putWebHomeExtension(boolean extension) {
        Prefers.put("web_home_extension", extension);
    }

    public static boolean isWebHomeThemeEnabled() {
        return Prefers.getBoolean("web_home_theme_enabled");
    }

    public static void putWebHomeThemeEnabled(boolean enabled) {
        Prefers.put("web_home_theme_enabled", enabled);
    }

    public static String getWebHomeThemeUrl() {
        String legacy = "file:///android_asset/webhome/eclipse.html";
        String current = Prefers.getString("web_home_theme_url", "file:///android_asset/webhome/theme.json");
        if (!legacy.equals(current)) return current;
        current = "file:///android_asset/webhome/theme.json";
        Prefers.put("web_home_theme_url", current);
        return current;
    }

    public static void putWebHomeThemeUrl(String url) {
        Prefers.put("web_home_theme_url", url);
    }

    public static String getWebHomeThemeTrustedUrl() {
        return Prefers.getString("web_home_theme_trusted_url");
    }

    public static void putWebHomeThemeTrustedUrl(String url) {
        Prefers.put("web_home_theme_trusted_url", url);
    }

    public static boolean isWebHomeFullscreen() {
        return Prefers.getBoolean("web_home_fullscreen", true);
    }

    public static void putWebHomeFullscreen(boolean fullscreen) {
        Prefers.put("web_home_fullscreen", fullscreen);
    }

    public static boolean isPlaybackArtworkWall() {
        return Prefers.getBoolean("playback_artwork_wall", true);
    }

    public static void putPlaybackArtworkWall(boolean artworkWall) {
        Prefers.put("playback_artwork_wall", artworkWall);
    }

    public static boolean isCspWarmup() {
        return getCspWarmupMode() != CSP_WARMUP_DISABLED;
    }

    public static void putCspWarmup(boolean warmup) {
        if (warmup) {
            Prefers.put("csp_warmup", true);
            if (getCspWarmupSelectedMode() == CSP_WARMUP_DISABLED) Prefers.put("csp_warmup_mode", CSP_WARMUP_DEFAULT);
        } else {
            Prefers.put("csp_warmup", false);
        }
    }

    public static int getCspWarmupMode() {
        if (!Prefers.getBoolean("csp_warmup")) return CSP_WARMUP_DISABLED;
        return getCspWarmupSelectedMode();
    }

    public static int getCspWarmupSelectedMode() {
        int mode = Prefers.getInt("csp_warmup_mode", CSP_WARMUP_DEFAULT);
        return mode == CSP_WARMUP_CUSTOM ? CSP_WARMUP_CUSTOM : CSP_WARMUP_DEFAULT;
    }

    public static void putCspWarmupMode(int mode) {
        if (mode == CSP_WARMUP_DISABLED) {
            Prefers.put("csp_warmup", false);
        } else {
            Prefers.put("csp_warmup", true);
            Prefers.put("csp_warmup_mode", mode == CSP_WARMUP_CUSTOM ? CSP_WARMUP_CUSTOM : CSP_WARMUP_DEFAULT);
        }
    }

    public static List<String> getCspWarmupSites() {
        try {
            List<String> keys = App.gson().fromJson(Prefers.getString("csp_warmup_sites", "[]"), STRING_LIST);
            if (keys == null) return Collections.emptyList();
            List<String> result = new ArrayList<>();
            for (String key : keys) if (key != null && !key.trim().isEmpty() && !result.contains(key.trim())) result.add(key.trim());
            return result;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static void putCspWarmupSites(List<String> keys) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (keys != null) for (String key : keys) if (key != null && !key.trim().isEmpty()) result.add(key.trim());
        Prefers.put("csp_warmup_sites", App.gson().toJson(result));
    }

    public static boolean isDebugLog() {
        return DebugLogStore.isEnabled();
    }

    public static void putDebugLog(boolean debugLog) {
        DebugLogStore.setEnabled(debugLog);
        if (debugLog) logDebugEnvironment("enable");
    }

    public static void logDebugEnvironment(String reason) {
        boolean hardwareAccelerated = (App.get().getApplicationInfo().flags & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) != 0;
        SpiderDebug.log("env", "reason=%s app=%s(%s) mode=%s abi=%s debug=%s hardware=%s android=%s sdk=%s incremental=%s manufacturer=%s brand=%s model=%s device=%s product=%s supportedAbis=%s",
                reason,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                BuildConfig.FLAVOR_mode,
                BuildConfig.FLAVOR_abi,
                BuildConfig.DEBUG,
                hardwareAccelerated,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT,
                Build.VERSION.INCREMENTAL,
                Build.MANUFACTURER,
                Build.BRAND,
                Build.MODEL,
                Build.DEVICE,
                Build.PRODUCT,
                String.join(",", Build.SUPPORTED_ABIS));
        WebViewUtil.logProvider("debug-env");
    }

    public static boolean isShellProxy() {
        return Prefers.getBoolean("shell_proxy");
    }

    public static void putShellProxy(boolean shellProxy) {
        Prefers.put("shell_proxy", shellProxy);
        ProxySetting.apply();
    }

    public static String getShellProxyRules() {
        return Prefers.getString("shell_proxy_rules");
    }

    public static void putShellProxyRules(String rules) {
        Prefers.put("shell_proxy_rules", rules);
        ProxySetting.apply();
    }

    public static void putShellProxyConfig(String url, String rules) {
        Prefers.put("shell_proxy_url", url);
        Prefers.put("shell_proxy_rules", rules);
        Prefers.put("shell_proxy_hosts", "*");
        ProxySetting.apply();
    }

    public static String getShellProxyUrl() {
        return Prefers.getString("shell_proxy_url");
    }

    public static void putShellProxyUrl(String url) {
        Prefers.put("shell_proxy_url", url);
        ProxySetting.apply();
    }

    public static String getShellProxyHosts() {
        return Prefers.getString("shell_proxy_hosts", "*");
    }

    public static void putShellProxyHosts(String hosts) {
        Prefers.put("shell_proxy_hosts", hosts);
        ProxySetting.apply();
    }

    public static boolean getUpdate() {
        return Prefers.getBoolean("update", true);
    }

    public static void putUpdate(boolean update) {
        Prefers.put("update", update);
    }

    public static String getUpdateSource() {
        return UpdateSource.normalize(Prefers.getString("update_source", UpdateSource.OCI));
    }

    public static void putUpdateSource(String source) {
        Prefers.put("update_source", UpdateSource.normalize(source));
    }

    public static String getUpdateOciMirror() {
        return OciMirror.find(Prefers.getString("update_oci_mirror", OciMirror.DEFAULT)).id;
    }

    public static void putUpdateOciMirror(String mirror) {
        Prefers.put("update_oci_mirror", OciMirror.find(mirror).id);
    }

    public static String getUpdateOciMirrorUrl() {
        return Prefers.getString("update_oci_mirror_url");
    }

    public static void putUpdateOciMirrorUrl(String url) {
        Prefers.put("update_oci_mirror_url", url == null ? "" : url.trim());
    }

    public static String getGithubProxy() {
        migrateLegacyGithubProxy();
        return Prefers.getString("github_proxy", com.fongmi.android.tv.utils.GithubProxy.defaultSources());
    }

    public static void putGithubProxy(String value) {
        Prefers.put("github_proxy", com.fongmi.android.tv.utils.GithubProxy.normalizeConfig(value));
    }

    public static String getGithubProxyMode() {
        migrateLegacyGithubProxy();
        return GithubProxy.normalizeMode(Prefers.getString("github_proxy_mode", GithubProxy.MODE_FULL_URL));
    }

    public static void putGithubProxyMode(String mode) {
        Prefers.put("github_proxy_mode", GithubProxy.normalizeMode(mode));
    }

    public static boolean isGithubProxyEnabled() {
        return Prefers.getBoolean("github_proxy_enabled", true);
    }

    public static void putGithubProxyEnabled(boolean enabled) {
        Prefers.put("github_proxy_enabled", enabled);
    }

    /**
     * 把旧的单选 GitHub 代理设置迁移成新的多源列表。
     *
     * <p>{@code synchronized}：两个 getter 都会调它，而它先读后删旧键。不加锁时
     * 「关于」对话框与探测线程可能同时进来，两边都看到旧键存在，后写的 {@code putGithubProxy}
     * 会盖掉前一次已经扩好的列表。窗口只有升级后第一次读，但那正是唯一一次机会。
     */
    private static synchronized void migrateLegacyGithubProxy() {
        if (!Prefers.getPrefers().contains("update_github_proxy")) return;

        String proxy = Prefers.getString("update_github_proxy");
        if (GithubProxy.DIRECT.equals(proxy)) {
            putGithubProxyEnabled(false);
        } else {
            String url = "custom".equals(proxy)
                    ? Prefers.getString("update_github_proxy_url")
                    : legacyGithubProxyUrl(proxy);
            if (!url.isEmpty()) {
                // 旧选择成为列表首位（即生效源），其余内置源保留在后面备用。
                // 先前这里用 putGithubProxy(url) 覆盖，会把刚合并出来的整份列表抹成一条。
                String merged = GithubProxy.addSources(Prefers.getString("github_proxy"), legacyGithubProxySources(url));
                putGithubProxy(GithubProxy.addSources(url, merged));
                putGithubProxyEnabled(true);
                putGithubProxyMode(Prefers.getString("update_github_proxy_mode", GithubProxy.MODE_FULL_URL));
            }
        }

        Prefers.remove("update_github_proxy");
        Prefers.remove("update_github_proxy_url");
        Prefers.remove("update_github_proxy_mode");
    }

    private static String legacyGithubProxyUrl(String proxy) {
        return switch (proxy) {
            case "github_chenc" -> "https://github.chenc.dev";
            case "gh_acmsz" -> "https://gh.acmsz.top";
            case "ghfast" -> "https://ghfast.top";
            case "gh_monlor" -> "https://gh.monlor.com";
            default -> "";
        };
    }

    /**
     * 旧版本内置的四个代理加上用户自填的那个，作为迁移时要并入的候选源。
     *
     * <p>{@code selectedUrl} 放在最前：并入后它就是生效源，与用户升级前的选择一致。
     * 自填地址无论当时选没选中都保留 —— 它是用户手输的，丢掉就再也找不回来。
     */
    private static String legacyGithubProxySources(String selectedUrl) {
        List<String> list = new ArrayList<>();
        if (selectedUrl != null && !selectedUrl.isEmpty()) list.add(selectedUrl);
        list.add("https://github.chenc.dev");
        list.add("https://gh.acmsz.top");
        list.add("https://ghfast.top");
        list.add("https://gh.monlor.com");
        String custom = Prefers.getString("update_github_proxy_url");
        if (!custom.isEmpty()) list.add(custom);
        return String.join("\n", list);
    }

    public static boolean isAdblock() {
        return Prefers.getBoolean("adblock", true);
    }

    public static void putAdblock(boolean adblock) {
        Prefers.put("adblock", adblock);
    }

    public static boolean isZhuyin() {
        return Prefers.getBoolean("zhuyin");
    }

    public static void putZhuyin(boolean zhuyin) {
        Prefers.put("zhuyin", zhuyin);
    }

    public static int getThemeColor() {
        return Prefers.getInt("theme_color", -1);
    }

    public static void putThemeColor(int color) {
        Prefers.put("theme_color", color);
    }

    public static int getWallColor() {
        return Prefers.getInt("wall_color", 0);
    }

    public static void putWallColor(int color) {
        Prefers.put("wall_color", color);
    }

    public static int getDynamicColor() {
        int color = getThemeColor();
        if (color == -1) return 0;
        return color != 0 ? color : getWallColor();
    }

    public static boolean hasFileAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager() || hasLegacyFileAccess();
        return hasLegacyFileAccess();
    }

    private static boolean hasLegacyFileAccess() {
        boolean read = ContextCompat.checkSelfPermission(App.get(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean write = ContextCompat.checkSelfPermission(App.get(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean legacy = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Environment.isExternalStorageLegacy();
        return hasLegacyFileAccess(Build.VERSION.SDK_INT, App.get().getApplicationInfo().targetSdkVersion, read, write, legacy);
    }

    static boolean hasLegacyFileAccess(int sdkInt, int targetSdk, boolean read, boolean write, boolean legacyStorage) {
        if (sdkInt >= Build.VERSION_CODES.R) return read && targetSdk < Build.VERSION_CODES.R;
        if (sdkInt >= Build.VERSION_CODES.Q) return read && (write || legacyStorage);
        return read && write;
    }

    public static boolean hasFileManager() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        return new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + App.get().getPackageName())).resolveActivity(App.get().getPackageManager()) != null || new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).resolveActivity(App.get().getPackageManager()) != null;
    }

    public static String getAudioConfig() {
        return Prefers.getString("audio_config");
    }

    public static void putAudioConfig(String value) {
        Prefers.put("audio_config", value);
    }

    public static boolean isAudioSiteEnabled(String key, String name) {
        return AudioConfig.objectFrom(getAudioConfig()).isSiteEnabled(key, name);
    }

    public static String getShortDramaConfig() {
        return Prefers.getString("short_drama_config");
    }

    public static void putShortDramaConfig(String value) {
        Prefers.put("short_drama_config", value);
    }

    public static boolean isShortDramaSiteEnabled(String key, String name) {
        return ShortDramaConfig.objectFrom(getShortDramaConfig()).isSiteEnabled(key, name);
    }

    public static String getTmdbConfig() {
        return Prefers.getString("tmdb_config");
    }

    public static void putTmdbConfig(String value) {
        Prefers.put("tmdb_config", value);
    }

    public static boolean isTmdbReady() {
        return com.fongmi.android.tv.bean.TmdbConfig.objectFrom(getTmdbConfig()).isReady();
    }

    public static boolean isTmdbSiteEnabled(String key, String name) {
        return com.fongmi.android.tv.bean.TmdbConfig.objectFrom(getTmdbConfig()).isSiteEnabled(key, name);
    }

    public static String getAiConfig() {
        return Prefers.getString("ai_config");
    }

    public static void putAiConfig(String value) {
        Prefers.put("ai_config", value);
    }

    public static boolean isAiConfigReady() {
        return AiConfig.objectFrom(getAiConfig()).isReady();
    }

    public static boolean isAiTitleExtraction() {
        return Prefers.getBoolean("ai_title_extraction", false);
    }

    public static void putAiTitleExtraction(boolean enabled) {
        Prefers.put("ai_title_extraction", enabled);
    }

    public static boolean isAiAdDetection() {
        return Prefers.getBoolean("ai_ad_detection", false);
    }

    public static void putAiAdDetection(boolean enabled) {
        Prefers.put("ai_ad_detection", enabled);
    }

    public static synchronized TmdbMatchCache getTmdbMatchCache() {
        return TmdbMatchCache.objectFrom(AppCache.get(AppCache.KEY_TMDB_MATCH));
    }

    public static synchronized void putTmdbMatchCache(TmdbMatchCache cache) {
        AppCache.put(AppCache.KEY_TMDB_MATCH, App.gson().toJson(cache));
    }

    public static synchronized TmdbSeasonMatchCache getTmdbSeasonMatchCache() {
        return TmdbSeasonMatchCache.objectFrom(AppCache.get(AppCache.KEY_TMDB_SEASON_MATCH));
    }

    public static synchronized void putTmdbSeasonMatchCache(TmdbSeasonMatchCache cache) {
        AppCache.put(AppCache.KEY_TMDB_SEASON_MATCH, App.gson().toJson(cache));
    }
    public static DanmakuMatchCache getDanmakuMatchCache() {
        return DanmakuMatchCache.objectFrom(Prefers.getString("danmaku_match_cache"));
    }

    public static void putDanmakuMatchCache(DanmakuMatchCache cache) {
        Prefers.put("danmaku_match_cache", App.gson().toJson(cache));
    }

    public static boolean isTmdbEnabled() {
        if (!Prefers.getPrefers().contains("tmdb_enabled")) {
            if (Prefers.getPrefers().contains("detail_open_mode")) return isTmdbMode(clampDetailOpenMode(Prefers.getInt("detail_open_mode", DETAIL_OPEN_ENHANCED)));
            if (Prefers.getPrefers().contains("search_detail_page")) return Prefers.getBoolean("search_detail_page", true);
        }
        return Prefers.getBoolean("tmdb_enabled", false);
    }

    public static void putTmdbEnabled(boolean enabled) {
        Prefers.put("tmdb_enabled", enabled);
    }

    public static int getTmdbModel() {
        return clampTmdbModel(Prefers.getInt("tmdb_model", TMDB_MODEL_NATIVE));
    }

    public static void putTmdbModel(int model) {
        Prefers.put("tmdb_model", clampTmdbModel(model));
    }

    private static int clampTmdbModel(int model) {
        return model == TMDB_MODEL_NATIVE ? TMDB_MODEL_NATIVE : TMDB_MODEL_NATIVE;
    }

    public static int getDetailInteractionMode() {
        if (Prefers.getPrefers().contains("detail_open_mode")) {
            return isTmdbMode(getDetailOpenMode()) ? DETAIL_INTERACTION_SYSTEM : DETAIL_INTERACTION_ORIGINAL;
        }
        if (!Prefers.getPrefers().contains("detail_interaction_mode")) {
            return isTmdbEnabled() ? DETAIL_INTERACTION_SYSTEM : DETAIL_INTERACTION_ORIGINAL;
        }
        int mode = clampDetailInteractionMode(Prefers.getInt("detail_interaction_mode", DETAIL_INTERACTION_ORIGINAL));
        return mode == DETAIL_INTERACTION_SYSTEM && !isTmdbEnabled() ? DETAIL_INTERACTION_ORIGINAL : mode;
    }

    public static void putDetailInteractionMode(int mode) {
        int value = clampDetailInteractionMode(mode);
        putDetailOpenMode(value == DETAIL_INTERACTION_ORIGINAL ? DETAIL_OPEN_DIRECT : DETAIL_OPEN_ORIGINAL_ENHANCED);
    }

    private static int clampDetailInteractionMode(int mode) {
        return mode == DETAIL_INTERACTION_SYSTEM ? DETAIL_INTERACTION_SYSTEM : DETAIL_INTERACTION_ORIGINAL;
    }

    public static int getDetailThemeMode() {
        if (Prefers.getPrefers().contains("detail_theme_mode")) {
            int theme = Prefers.getInt("detail_theme_mode", DETAIL_THEME_CURRENT);
            if (theme == DETAIL_STYLE_PROFILE && isCurrentThemePreference()) return DETAIL_STYLE_NATIVE;
            return clampDetailThemeMode(theme);
        }
        if (Prefers.getPrefers().contains("tmdb_detail_style")) return clampDetailThemeMode(Prefers.getInt("tmdb_detail_style", DETAIL_STYLE_PROFILE));
        return getDetailOpenMode() == DETAIL_OPEN_ORIGINAL_ENHANCED ? DETAIL_STYLE_NATIVE : DETAIL_STYLE_PROFILE;
    }

    public static void putDetailThemeMode(int mode) {
        int value = clampDetailThemeMode(mode);
        Prefers.put("detail_theme_mode", value);
        Prefers.put("tmdb_detail_style", value);
    }

    private static int clampDetailThemeMode(int mode) {
        if (mode == DETAIL_STYLE_PROFILE || mode == DETAIL_STYLE_CINEMA || mode == DETAIL_STYLE_NATIVE) return mode;
        return DETAIL_STYLE_NATIVE;
    }

    private static boolean isCurrentThemePreference() {
        if (Prefers.getPrefers().contains("detail_open_mode")) return false;
        if (Prefers.getPrefers().contains("tmdb_detail_style")) return false;
        return Prefers.getPrefers().contains("detail_interaction_mode") && Prefers.getInt("detail_interaction_mode", DETAIL_INTERACTION_ORIGINAL) == DETAIL_INTERACTION_SYSTEM && isTmdbEnabled();
    }

    public static int getTmdbMatchMode() {
        if (Prefers.getPrefers().contains("tmdb_match_mode")) return clampTmdbMatchMode(Prefers.getInt("tmdb_match_mode", TMDB_MATCH_SMART));
        if (Prefers.getPrefers().contains("tmdb_match_dialog")) return Prefers.getBoolean("tmdb_match_dialog", true) ? TMDB_MATCH_STRICT_DIALOG : TMDB_MATCH_STRICT;
        return TMDB_MATCH_SMART;
    }

    public static void putTmdbMatchMode(int mode) {
        Prefers.put("tmdb_match_mode", clampTmdbMatchMode(mode));
    }

    public static boolean isTmdbSmartMatch() {
        int mode = getTmdbMatchMode();
        return mode == TMDB_MATCH_SMART || mode == TMDB_MATCH_SMART_DIALOG;
    }

    public static boolean isTmdbMatchDialog() {
        int mode = getTmdbMatchMode();
        return mode == TMDB_MATCH_STRICT_DIALOG || mode == TMDB_MATCH_SMART_DIALOG;
    }

    public static boolean isPersonalRecommendation() {
        if (Prefers.getPrefers().contains("personal_recommendation")) return Prefers.getBoolean("personal_recommendation", false);
        return Prefers.getBoolean("ai_recommendation", false);
    }

    public static void putPersonalRecommendation(boolean enabled) {
        Prefers.put("personal_recommendation", enabled);
        Prefers.put("ai_recommendation", enabled);
    }

    @Deprecated
    public static boolean isAiRecommendation() {
        return isPersonalRecommendation();
    }

    @Deprecated
    public static void putAiRecommendation(boolean enabled) {
        putPersonalRecommendation(enabled);
    }

    private static int clampTmdbMatchMode(int mode) {
        if (mode == TMDB_MATCH_STRICT || mode == TMDB_MATCH_SMART || mode == TMDB_MATCH_STRICT_DIALOG || mode == TMDB_MATCH_SMART_DIALOG) return mode;
        return TMDB_MATCH_SMART;
    }

    public static boolean isTmdbDetailBackdropSlide() {
        return Prefers.getBoolean("tmdb_detail_backdrop_slide", true);
    }

    public static void putTmdbDetailBackdropSlide(boolean enabled) {
        Prefers.put("tmdb_detail_backdrop_slide", enabled);
    }

    public static boolean isTmdbDetailPage() {
        return isTmdbMode(getDetailOpenMode()) && getTmdbModel() == TMDB_MODEL_NATIVE && TmdbConfig.objectFrom(getTmdbConfig()).isReady();
    }

    public static int getDetailOpenMode() {
        int mode;
        if (Prefers.getPrefers().contains("detail_open_mode")) {
            int stored = Prefers.getInt("detail_open_mode", DETAIL_OPEN_ENHANCED);
            if (stored == DETAIL_OPEN_CINEMA) {
                if (!Prefers.getPrefers().contains("detail_theme_mode") && !Prefers.getPrefers().contains("tmdb_detail_style")) putDetailThemeMode(DETAIL_STYLE_CINEMA);
                mode = DETAIL_OPEN_ENHANCED;
                Prefers.put("detail_open_mode", mode);
            } else {
                mode = clampDetailOpenMode(stored);
            }
        } else if (Prefers.getPrefers().contains("detail_interaction_mode")) {
            mode = getDetailInteractionMode() == DETAIL_INTERACTION_SYSTEM ? DETAIL_OPEN_ORIGINAL_ENHANCED : DETAIL_OPEN_DIRECT;
            Prefers.put("detail_open_mode", mode);
            migrateCurrentDetailTheme(mode);
        } else if (Prefers.getPrefers().contains("search_detail_page")) {
            mode = Prefers.getBoolean("search_detail_page") ? DETAIL_OPEN_ENHANCED : DETAIL_OPEN_DIRECT;
        } else {
            mode = isTmdbEnabled() ? DETAIL_OPEN_ORIGINAL_ENHANCED : DETAIL_OPEN_DIRECT;
            migrateCurrentDetailTheme(mode);
        }
        return isTmdbMode(mode) && !isTmdbReady() ? DETAIL_OPEN_DIRECT : mode;
    }

    public static void putDetailOpenMode(int mode) {
        if (mode == DETAIL_OPEN_CINEMA) {
            putDetailThemeMode(DETAIL_STYLE_CINEMA);
            mode = DETAIL_OPEN_ENHANCED;
        } else if (mode == DETAIL_OPEN_FUSION) {
            putDetailThemeMode(DETAIL_STYLE_PROFILE);
        } else if (mode == DETAIL_OPEN_ORIGINAL_ENHANCED) {
            putDetailThemeMode(DETAIL_STYLE_NATIVE);
        }
        int value = clampDetailOpenMode(mode);
        Prefers.put("detail_open_mode", value);
        Prefers.put("detail_interaction_mode", isTmdbMode(value) ? DETAIL_INTERACTION_SYSTEM : DETAIL_INTERACTION_ORIGINAL);
        putTmdbEnabled(isTmdbMode(value));
    }

    public static boolean isTmdbMode(int mode) {
        return mode == DETAIL_OPEN_FUSION || mode == DETAIL_OPEN_ENHANCED || mode == DETAIL_OPEN_PLAYER || mode == DETAIL_OPEN_ORIGINAL_ENHANCED;
    }

    public static boolean isStandaloneTmdbDetailMode(int mode) {
        return mode == DETAIL_OPEN_FUSION || mode == DETAIL_OPEN_ENHANCED || mode == DETAIL_OPEN_PLAYER;
    }

    public static boolean isFusionDetailPage() {
        return getDetailOpenMode() == DETAIL_OPEN_FUSION;
    }

    public static boolean isPlayerDetailPage() {
        return getDetailOpenMode() == DETAIL_OPEN_PLAYER;
    }

    public static boolean isDirectDetailPage() {
        return getDetailOpenMode() == DETAIL_OPEN_DIRECT;
    }

    public static boolean isSearchDetailPage() {
        return getDetailOpenMode() == DETAIL_OPEN_ENHANCED;
    }

    public static boolean isOriginalEnhancedDetailPage() {
        return getDetailOpenMode() == DETAIL_OPEN_ORIGINAL_ENHANCED;
    }

    public static boolean isCinemaDetailPage() {
        return isTmdbDetailPage() && isTmdbCinemaStyle();
    }

    public static void putSearchDetailPage(boolean enabled) {
        putDetailOpenMode(enabled ? DETAIL_OPEN_ENHANCED : DETAIL_OPEN_DIRECT);
    }

    public static int nextDetailOpenMode() {
        int[] modes = {DETAIL_OPEN_ORIGINAL_ENHANCED, DETAIL_OPEN_FUSION, DETAIL_OPEN_ENHANCED, DETAIL_OPEN_PLAYER, DETAIL_OPEN_DIRECT};
        int mode = getDetailOpenMode();
        for (int i = 0; i < modes.length; i++) if (modes[i] == mode) return modes[(i + 1) % modes.length];
        return DETAIL_OPEN_ORIGINAL_ENHANCED;
    }

    private static int clampDetailOpenMode(int mode) {
        if (mode == DETAIL_OPEN_CINEMA) return DETAIL_OPEN_ENHANCED;
        if (mode == DETAIL_OPEN_FUSION || mode == DETAIL_OPEN_ENHANCED || mode == DETAIL_OPEN_DIRECT || mode == DETAIL_OPEN_PLAYER || mode == DETAIL_OPEN_ORIGINAL_ENHANCED) return mode;
        return DETAIL_OPEN_ORIGINAL_ENHANCED;
    }

    private static void migrateCurrentDetailTheme(int mode) {
        if (mode != DETAIL_OPEN_ORIGINAL_ENHANCED) return;
        if (Prefers.getPrefers().contains("tmdb_detail_style")) return;
        if (!Prefers.getPrefers().contains("detail_theme_mode") || Prefers.getInt("detail_theme_mode", DETAIL_STYLE_PROFILE) == DETAIL_STYLE_PROFILE) putDetailThemeMode(DETAIL_STYLE_NATIVE);
    }

    public static int getTmdbDetailStyle() {
        return getDetailThemeMode();
    }

    public static void putTmdbDetailStyle(int style) {
        putDetailThemeMode(style);
    }

    public static boolean isTmdbCinemaStyle() {
        return getTmdbDetailStyle() == DETAIL_STYLE_CINEMA;
    }

    public static boolean isTmdbNativeStyle() {
        return getTmdbDetailStyle() == DETAIL_STYLE_NATIVE;
    }

    public static int getTmdbDetailTheme() {
        return clampTmdbDetailTheme(Prefers.getInt("tmdb_detail_theme", 2));
    }

    public static void putTmdbDetailTheme(int theme) {
        Prefers.put("tmdb_detail_theme", clampTmdbDetailTheme(theme));
    }

    public static boolean getTmdbEpisodeGridMode() {
        return Prefers.getBoolean("tmdb_episode_grid_mode", !"mobile".equals(BuildConfig.FLAVOR_mode));
    }

    public static void putTmdbEpisodeGridMode(boolean gridMode) {
        Prefers.put("tmdb_episode_grid_mode", gridMode);
    }

    public static boolean isTmdbEpisodeFileSize() {
        return Prefers.getBoolean("tmdb_episode_file_size", false);
    }

    public static void putTmdbEpisodeFileSize(boolean enabled) {
        Prefers.put("tmdb_episode_file_size", enabled);
    }

    public static boolean getTmdbEpisodeShowScrapedName() {
        return Prefers.getBoolean("tmdb_episode_show_scraped_name", true);
    }

    public static void putTmdbEpisodeShowScrapedName(boolean showScraped) {
        Prefers.put("tmdb_episode_show_scraped_name", showScraped);
    }

    public static int nextTmdbDetailTheme(int theme) {
        return clampTmdbDetailTheme(theme) == 2 ? 1 : 2;
    }

    public static boolean resolveTmdbDetailLightTheme(int theme, boolean systemNight) {
        int value = clampTmdbDetailTheme(theme);
        return value != 1;
    }

    static int clampTmdbDetailTheme(int theme) {
        return theme == 1 ? 1 : 2;
    }

    public static boolean isHomeHistory() {
        return Prefers.getBoolean("home_history", true);
    }

    public static void putHomeHistory(boolean homeHistory) {
        Prefers.put("home_history", homeHistory);
    }

    public static boolean isEpisodeHistory() {
        return Prefers.getBoolean("episode_history", true);
    }

    public static void putEpisodeHistory(boolean episodeHistory) {
        Prefers.put("episode_history", episodeHistory);
    }

    public static int getGlobalHistoryMode() {
        return clampGlobalHistoryMode(Prefers.getInt("global_history_mode", GLOBAL_HISTORY_OFF));
    }

    public static void putGlobalHistoryMode(int mode) {
        Prefers.put("global_history_mode", clampGlobalHistoryMode(mode));
    }

    public static boolean isGlobalHistoryEnabled() {
        return getGlobalHistoryMode() != GLOBAL_HISTORY_OFF;
    }

    public static boolean isGlobalHistoryAuto() {
        return getGlobalHistoryMode() == GLOBAL_HISTORY_AUTO;
    }

    public static boolean isGlobalHistorySearch() {
        return getGlobalHistoryMode() == GLOBAL_HISTORY_SEARCH;
    }

    private static int clampGlobalHistoryMode(int mode) {
        return mode == GLOBAL_HISTORY_AUTO || mode == GLOBAL_HISTORY_SEARCH ? mode : GLOBAL_HISTORY_OFF;
    }

    public static boolean isHistoryAggregationByTmdb() {
        return isTmdbReady() && Prefers.getBoolean("history_aggregation_by_tmdb", true);
    }

    public static void putHistoryAggregationByTmdb(boolean value) {
        Prefers.put("history_aggregation_by_tmdb", value);
    }

    public static boolean isHistoryAggregationEffective() {
        return isHistoryAggregationByTmdb();
    }

    private static final String KEY_TMDB_SEASON_OFFSET = "tmdb_season_offset_map";

    /** 用户自定义的"TMDB 剧名 -> 季号偏移"映射（源季号 + 偏移 = TMDB season_number）。 */
    public static Map<String, Integer> getTmdbSeasonOffsetMap() {
        String json = Prefers.getString(KEY_TMDB_SEASON_OFFSET, null);
        if (json == null || json.isEmpty()) return new HashMap<>();
        try {
            Map<String, Integer> map = App.gson().fromJson(json, new TypeToken<Map<String, Integer>>() {}.getType());
            return map != null ? map : new HashMap<>();
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public static void putTmdbSeasonOffsetMap(Map<String, Integer> map) {
        Prefers.put(KEY_TMDB_SEASON_OFFSET, App.gson().toJson(map));
    }

    public static int getTmdbSeasonOffset(String tmdbTitle) {
        if (tmdbTitle == null) return 0;
        Integer offset = getTmdbSeasonOffsetMap().get(tmdbTitle.trim());
        return offset != null ? offset : 0;
    }

    public static void putTmdbSeasonOffset(String tmdbTitle, int offset) {
        if (tmdbTitle == null || tmdbTitle.trim().isEmpty()) return;
        Map<String, Integer> map = getTmdbSeasonOffsetMap();
        if (offset == 0) map.remove(tmdbTitle.trim());
        else map.put(tmdbTitle.trim(), offset);
        putTmdbSeasonOffsetMap(map);
    }

    public static void removeTmdbSeasonOffset(String tmdbTitle) {
        if (tmdbTitle == null) return;
        Map<String, Integer> map = getTmdbSeasonOffsetMap();
        map.remove(tmdbTitle.trim());
        putTmdbSeasonOffsetMap(map);
    }

    public static boolean isHomeVodAutoLoad() {
        return Prefers.getBoolean("home_vod_auto_load", true);
    }

    public static void putHomeVodAutoLoad(boolean autoLoad) {
        Prefers.put("home_vod_auto_load", autoLoad);
    }

    public static boolean isHomeSiteLock() {
        return Prefers.getBoolean("home_site_lock", false);
    }

    public static void putHomeSiteLock(boolean homeSiteLock) {
        Prefers.put("home_site_lock", homeSiteLock);
    }

    public static boolean isAutoBackup() {
        return Prefers.getBoolean("auto_backup", false);
    }

    public static void putAutoBackup(boolean autoBackup) {
        Prefers.put("auto_backup", autoBackup);
    }

    public static int getFullscreenMenuKey() {
        return Prefers.getInt("fullscreen_menu_key", 0);
    }

    public static void putFullscreenMenuKey(int menuKey) {
        Prefers.put("fullscreen_menu_key", menuKey);
    }

    public static int getHomeMenuKey() {
        int menuKey = Prefers.getInt("home_menu_key", 0);
        return menuKey < 0 || menuKey > 9 ? 0 : menuKey;
    }

    public static void putHomeMenuKey(int menuKey) {
        Prefers.put("home_menu_key", menuKey);
    }

    public static boolean isPlayBackToDetail() {
        return Prefers.getBoolean("play_back_to_detail");
    }

    public static void putPlayBackToDetail(boolean backToDetail) {
        Prefers.put("play_back_to_detail", backToDetail);
    }

    public static boolean isSubtitleAutoMatchEnabled() {
        return Prefers.getBoolean("subtitle_auto_match", false);
    }

    public static void putSubtitleAutoMatchEnabled(boolean enabled) {
        Prefers.put("subtitle_auto_match", enabled);
    }

    public static String getSubtitlePreferredLanguage() {
        return Prefers.getString("subtitle_preferred_language", "zh");
    }

    public static void putSubtitlePreferredLanguage(String language) {
        Prefers.put("subtitle_preferred_language", language == null || language.isEmpty() ? "zh" : language);
    }

    public static String getRealtimeSubtitleModel() {
        String model = Prefers.getString("subtitle_realtime_model", REALTIME_SUBTITLE_MODEL_ZH);
        return isRealtimeSubtitleModel(model) ? model : REALTIME_SUBTITLE_MODEL_ZH;
    }

    public static void putRealtimeSubtitleModel(String model) {
        Prefers.put("subtitle_realtime_model", isRealtimeSubtitleModel(model) ? model : REALTIME_SUBTITLE_MODEL_ZH);
    }

    private static boolean isRealtimeSubtitleModel(String model) {
        return switch (model) {
            case REALTIME_SUBTITLE_MODEL_ZH,
                 REALTIME_SUBTITLE_MODEL_YUE,
                 REALTIME_SUBTITLE_MODEL_EN,
                 REALTIME_SUBTITLE_MODEL_DE,
                 REALTIME_SUBTITLE_MODEL_FR,
                 REALTIME_SUBTITLE_MODEL_ES,
                 REALTIME_SUBTITLE_MODEL_JA,
                 REALTIME_SUBTITLE_MODEL_ZH_EN -> true;
            case null, default -> false;
        };
    }
    public static String getSubtitleAssrtToken() {
        return Prefers.getString("subtitle_assrt_token");
    }

    public static void putSubtitleAssrtToken(String token) {
        Prefers.put("subtitle_assrt_token", token);
    }

    public static int getSubtitleAiMaxConcurrency() {
        return clampSubtitleAiMaxConcurrency(Prefers.getInt("subtitle_ai_max_concurrency", 2));
    }

    public static void putSubtitleAiMaxConcurrency(int value) {
        Prefers.put("subtitle_ai_max_concurrency", clampSubtitleAiMaxConcurrency(value));
    }

    public static int getSubtitleAiChunkCount() {
        return clampSubtitleAiChunkCount(Prefers.getInt("subtitle_ai_chunk_count", 2));
    }

    public static void putSubtitleAiChunkCount(int value) {
        Prefers.put("subtitle_ai_chunk_count", clampSubtitleAiChunkCount(value));
    }

    private static int clampSubtitleAiMaxConcurrency(int value) {
        return Math.max(1, Math.min(value, 8));
    }

    private static int clampSubtitleAiChunkCount(int value) {
        return Math.max(1, Math.min(value, 32));
    }

    public static int getIntroSkipMode() {
        return Prefers.getInt("intro_skip_mode", INTRO_SKIP_OFF);
    }

    public static void putIntroSkipMode(int mode) {
        Prefers.put("intro_skip_mode", mode);
    }

    public static boolean isAutoSkipIntroOutro() {
        return getIntroSkipMode() == INTRO_SKIP_AUTO;
    }

    public static boolean isIntroSkipEnabled() {
        return getIntroSkipMode() != INTRO_SKIP_OFF;
    }

    public static void putAutoSkipIntroOutro(boolean enabled) {
        putIntroSkipMode(enabled ? INTRO_SKIP_AUTO : INTRO_SKIP_OFF);
    }

    /**
     * 允许跳过的片段类型位掩码。默认回顾 + 片头 + 片尾，预告不默认开——预告在片尾之后，
     * 跳掉它等于直接进下一集，属于更激进的行为，让用户自己选。
     */
    public static int getIntroSkipKinds() {
        return Prefers.getInt("intro_skip_kinds", INTRO_SKIP_KIND_DEFAULT);
    }

    public static void putIntroSkipKinds(int kinds) {
        Prefers.put("intro_skip_kinds", kinds & INTRO_SKIP_KIND_ALL);
    }

    public static boolean isIntroSkipKindEnabled(int kind) {
        return (getIntroSkipKinds() & kind) != 0;
    }

    public static int getSearchUi() {
        return Prefers.getInt("search_ui", 1) == 0 ? 0 : 1;
    }

    public static void putSearchUi(int ui) {
        Prefers.put("search_ui", ui == 0 ? 0 : 1);
    }

    public static int getSearchThread() {
        return clampSearchThread(Prefers.getInt("search_thread", 20));
    }

    public static void putSearchThread(int thread) {
        Prefers.put("search_thread", clampSearchThread(thread));
    }

    private static int clampSearchThread(int thread) {
        return Math.max(1, Math.min(thread, 100));
    }

    public static int getSearchColumn() {
        return clampSearchColumn(Prefers.getInt("search_column", 0));
    }

    public static void putSearchColumn(int column) {
        Prefers.put("search_column", clampSearchColumn(column));
    }

    private static int clampSearchColumn(int column) {
        return column < 0 || column > 2 ? 0 : column;
    }

    public static int getSearchResultSort() {
        return Prefers.getInt("search_result_sort", 0);
    }

    public static void putSearchResultSort(int sort) {
        Prefers.put("search_result_sort", sort == 0 ? 0 : 1);
    }

    public static int getSearchSimilarity() {
        return Prefers.getInt("search_similarity", 30);
    }

    public static void putSearchSimilarity(int percent) {
        Prefers.put("search_similarity", Math.max(0, Math.min(100, percent)));
    }
}
