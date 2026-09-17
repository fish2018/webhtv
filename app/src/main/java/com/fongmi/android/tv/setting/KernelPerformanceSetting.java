package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

public final class KernelPerformanceSetting {

    private static final String KEY_MIGRATED = "perf_kernel_shared_migrated";
    private static final String KEY_PAUSE_PRELOAD_MIGRATED = "perf_pause_preload_policy_v2_migrated";

    private KernelPerformanceSetting() {
    }

    public static int getBuffer(int kernel) {
        ensureMigrated();
        return clamp(Prefers.getInt(key(kernel, "buffer"), 1), 1, 15);
    }

    public static void putBuffer(int kernel, int value) {
        ensureMigrated();
        Prefers.put(key(kernel, "buffer"), clamp(value, 1, 15));
    }

    public static int getBufferBytesOption(int kernel) {
        ensureMigrated();
        return clamp(Prefers.getInt(key(kernel, "buffer_bytes"), 0), 0, 3);
    }

    public static void putBufferBytesOption(int kernel, int value) {
        ensureMigrated();
        Prefers.put(key(kernel, "buffer_bytes"), clamp(value, 0, 3));
    }

    public static int getBackBufferOption(int kernel) {
        ensureMigrated();
        return clamp(Prefers.getInt(key(kernel, "back_buffer"), 0), 0, 3);
    }

    public static void putBackBufferOption(int kernel, int value) {
        ensureMigrated();
        Prefers.put(key(kernel, "back_buffer"), clamp(value, 0, 3));
    }

    public static int getPlayCacheOption(int kernel) {
        ensureMigrated();
        return clamp(Prefers.getInt(key(kernel, "play_cache"), 0), 0, 4);
    }

    public static void putPlayCacheOption(int kernel, int value) {
        ensureMigrated();
        Prefers.put(key(kernel, "play_cache"), clamp(value, 0, 4));
    }

    public static boolean isPreload(int kernel) {
        ensureMigrated();
        return Prefers.getBoolean(key(kernel, "preload"));
    }

    public static void putPreload(int kernel, boolean value) {
        ensureMigrated();
        Prefers.put(key(kernel, "preload"), value);
    }

    public static int getPreloadThreads(int kernel) {
        ensureMigrated();
        return clamp(Prefers.getInt(key(kernel, "preload_threads"), PreloadSetting.DEFAULT_THREADS), PreloadSetting.MIN_THREADS, PreloadSetting.MAX_THREADS);
    }

    public static void putPreloadThreads(int kernel, int value) {
        ensureMigrated();
        Prefers.put(key(kernel, "preload_threads"), clamp(value, PreloadSetting.MIN_THREADS, PreloadSetting.MAX_THREADS));
    }

    public static int getPreloadSizeMb(int kernel) {
        ensureMigrated();
        return closestPreloadSize(Prefers.getInt(key(kernel, "preload_size"), PreloadSetting.MIN_SIZE_MB));
    }

    public static void putPreloadSizeMb(int kernel, int value) {
        ensureMigrated();
        Prefers.put(key(kernel, "preload_size"), closestPreloadSize(value));
    }

    public static int getPreloadTimeSeconds(int kernel) {
        ensureMigrated();
        int seconds = clamp(Prefers.getInt(key(kernel, "preload_time"), PreloadSetting.DEFAULT_TIME_SECONDS), PreloadSetting.MIN_TIME_SECONDS, PreloadSetting.MAX_TIME_SECONDS);
        int steps = Math.round((float) (seconds - PreloadSetting.MIN_TIME_SECONDS) / PreloadSetting.STEP_TIME_SECONDS);
        return clamp(PreloadSetting.MIN_TIME_SECONDS + steps * PreloadSetting.STEP_TIME_SECONDS, PreloadSetting.MIN_TIME_SECONDS, PreloadSetting.MAX_TIME_SECONDS);
    }

    public static void putPreloadTimeSeconds(int kernel, int value) {
        ensureMigrated();
        Prefers.put(key(kernel, "preload_time"), clamp(value, PreloadSetting.MIN_TIME_SECONDS, PreloadSetting.MAX_TIME_SECONDS));
    }

    public static int getPreloadAheadSeconds(int kernel) {
        ensureMigrated();
        return Prefers.getInt(key(kernel, "preload_ahead"), PreloadSetting.DEFAULT_AHEAD_SECONDS);
    }

    public static void putPreloadAheadSeconds(int kernel, int value) {
        ensureMigrated();
        Prefers.put(key(kernel, "preload_ahead"), value);
    }

    public static int getPausePreloadPolicy(int kernel) {
        ensureMigrated();
        ensurePausePreloadMigrated();
        return PreloadSetting.normalizePausePreloadPolicy(
                Prefers.getInt(key(kernel, "preload_pause"), PreloadSetting.DEFAULT_PAUSE_PRELOAD));
    }

    public static void putPausePreloadPolicy(int kernel, int value) {
        ensureMigrated();
        ensurePausePreloadMigrated();
        Prefers.put(key(kernel, "preload_pause"), PreloadSetting.normalizePausePreloadPolicy(value));
    }

    public static boolean isAudioPassThrough(int kernel) {
        ensureMigrated();
        return Prefers.getBoolean(key(kernel, "audio_pass_through"), true);
    }

    public static void putAudioPassThrough(int kernel, boolean value) {
        ensureMigrated();
        Prefers.put(key(kernel, "audio_pass_through"), value);
    }

    public static boolean isPreferAac(int kernel) {
        ensureMigrated();
        return Prefers.getBoolean(key(kernel, "prefer_aac"));
    }

    public static void putPreferAac(int kernel, boolean value) {
        ensureMigrated();
        Prefers.put(key(kernel, "prefer_aac"), value);
    }

    public static boolean isAudioPrefer(int kernel) {
        ensureMigrated();
        return Prefers.getBoolean(key(kernel, "audio_prefer"));
    }

    public static void putAudioPrefer(int kernel, boolean value) {
        ensureMigrated();
        Prefers.put(key(kernel, "audio_prefer"), value);
    }

    public static boolean isVideoPrefer(int kernel) {
        ensureMigrated();
        return Prefers.getBoolean(key(kernel, "video_prefer"));
    }

    public static void putVideoPrefer(int kernel, boolean value) {
        ensureMigrated();
        Prefers.put(key(kernel, "video_prefer"), value);
    }

    static void applyOriginal(int kernel) {
        putBuffer(kernel, 1);
        putBufferBytesOption(kernel, 0);
        putBackBufferOption(kernel, 0);
        putPlayCacheOption(kernel, 0);
        putPreload(kernel, false);
        putPreloadThreads(kernel, 1);
        putPreloadSizeMb(kernel, PreloadSetting.MIN_SIZE_MB);
        putPreloadTimeSeconds(kernel, PreloadSetting.MAX_TIME_SECONDS);
        putAudioPassThrough(kernel, true);
        putPreferAac(kernel, false);
        putAudioPrefer(kernel, false);
        putVideoPrefer(kernel, false);
    }

    public static void applyPreset(int kernel, int profile) {
        if (profile == PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT
                || profile == PlaybackPerformanceSetting.PROFILE_COMPATIBLE) {
            putBuffer(kernel, kernel == PlayerSetting.EXO ? exoBufferForPreset(profile)
                    : kernel == PlayerSetting.MPV ? mpvBufferForPreset(profile) : 5);
            putBufferBytesOption(kernel, kernel == PlayerSetting.EXO ? exoBufferBytesOptionForPreset(profile)
                    : kernel == PlayerSetting.MPV ? mpvBufferBytesOptionForPreset(profile) : 1);
            putBackBufferOption(kernel, kernel == PlayerSetting.MPV
                    ? mpvBackBufferOptionForPreset(profile) : 0);
            putPlayCacheOption(kernel, 0);
            putPreload(kernel, false);
            putPreloadThreads(kernel, preloadThreadsForPreset(profile));
            putPreloadSizeMb(kernel, PreloadSetting.MIN_SIZE_MB);
            putPreloadTimeSeconds(kernel, preloadTimeForPreset(profile));
            putPreloadAheadSeconds(kernel, PreloadSetting.DEFAULT_AHEAD_SECONDS);
            putPausePreloadPolicy(kernel, PreloadSetting.DEFAULT_PAUSE_PRELOAD);
            putAudioPassThrough(kernel, audioPassthroughForPreset(kernel));
            putPreferAac(kernel, true);
            putAudioPrefer(kernel, false);
            putVideoPrefer(kernel, false);
        } else {
            putBuffer(kernel, kernel == PlayerSetting.EXO ? exoBufferForPreset(profile)
                    : kernel == PlayerSetting.MPV ? mpvBufferForPreset(profile) : 10);
            putBufferBytesOption(kernel, kernel == PlayerSetting.EXO ? exoBufferBytesOptionForPreset(profile)
                    : kernel == PlayerSetting.MPV ? mpvBufferBytesOptionForPreset(profile) : 3);
            putBackBufferOption(kernel, kernel == PlayerSetting.EXO ? exoBackBufferOptionForPreset(profile)
                    : kernel == PlayerSetting.MPV ? mpvBackBufferOptionForPreset(profile) : 2);
            putPlayCacheOption(kernel, 2);
            putPreload(kernel, true);
            putPreloadThreads(kernel, preloadThreadsForPreset(profile));
            putPreloadSizeMb(kernel, 512);
            putPreloadTimeSeconds(kernel, preloadTimeForPreset(profile));
            putPreloadAheadSeconds(kernel, PreloadSetting.DEFAULT_AHEAD_SECONDS);
            putPausePreloadPolicy(kernel, PreloadSetting.DEFAULT_PAUSE_PRELOAD);
            putAudioPassThrough(kernel, audioPassthroughForPreset(kernel));
            putPreferAac(kernel, false);
            putAudioPrefer(kernel, false);
            putVideoPrefer(kernel, false);
        }
    }

    static void applyPreloadPreset(int kernel, int profile) {
        if (profile == PlaybackPerformanceSetting.PROFILE_RECOMMENDED || profile == PlaybackPerformanceSetting.PROFILE_AUTO) {
            putPreload(kernel, true);
            putPreloadSizeMb(kernel, 512);
        } else {
            putPreload(kernel, false);
            putPreloadSizeMb(kernel, PreloadSetting.MIN_SIZE_MB);
        }
        putPreloadThreads(kernel, preloadThreadsForPreset(profile));
        putPreloadTimeSeconds(kernel, preloadTimeForPreset(profile));
        putPreloadAheadSeconds(kernel, PreloadSetting.DEFAULT_AHEAD_SECONDS);
        putPausePreloadPolicy(kernel, PreloadSetting.DEFAULT_PAUSE_PRELOAD);
    }

    static int preloadThreadsForPreset(int profile) {
        return PreloadSetting.DEFAULT_THREADS;
    }

    static int preloadTimeForPreset(int profile) {
        return PreloadSetting.DEFAULT_TIME_SECONDS;
    }

    static void applyExoLoadControlPreset(int profile) {
        putBuffer(PlayerSetting.EXO, exoBufferForPreset(profile));
        putBufferBytesOption(PlayerSetting.EXO, exoBufferBytesOptionForPreset(profile));
    }

    static void applyExoBackBufferPreset(int profile) {
        putBackBufferOption(PlayerSetting.EXO, exoBackBufferOptionForPreset(profile));
    }

    static void applyMpvAutoBaselinePreset() {
        putBuffer(PlayerSetting.MPV, mpvBufferForPreset(PlaybackPerformanceSetting.PROFILE_AUTO));
        putBufferBytesOption(PlayerSetting.MPV,
                mpvBufferBytesOptionForPreset(PlaybackPerformanceSetting.PROFILE_AUTO));
        putBackBufferOption(PlayerSetting.MPV,
                mpvBackBufferOptionForPreset(PlaybackPerformanceSetting.PROFILE_AUTO));
    }

    static int exoBufferForPreset(int profile) {
        return switch (profile) {
            case PlaybackPerformanceSetting.PROFILE_COMPATIBLE,
                 PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT -> 1;
            default -> 10;
        };
    }

    static int exoBufferBytesOptionForPreset(int profile) {
        return switch (profile) {
            case PlaybackPerformanceSetting.PROFILE_AUTO -> 0;
            case PlaybackPerformanceSetting.PROFILE_RECOMMENDED -> 2;
            default -> 1;
        };
    }

    static int exoBackBufferOptionForPreset(int profile) {
        return 0;
    }

    static int mpvBufferForPreset(int profile) {
        return profile == PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT
                || profile == PlaybackPerformanceSetting.PROFILE_COMPATIBLE ? 5 : 10;
    }

    static int mpvBufferBytesOptionForPreset(int profile) {
        return switch (profile) {
            case PlaybackPerformanceSetting.PROFILE_AUTO -> 0;
            case PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT,
                 PlaybackPerformanceSetting.PROFILE_COMPATIBLE -> 1;
            default -> 3;
        };
    }

    static int mpvBackBufferOptionForPreset(int profile) {
        return switch (profile) {
            case PlaybackPerformanceSetting.PROFILE_AUTO,
                 PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT,
                 PlaybackPerformanceSetting.PROFILE_COMPATIBLE -> 0;
            default -> 2;
        };
    }

    static boolean audioPassthroughForPreset(int kernel) {
        return PlayerSetting.sanitizePlayer(kernel) != PlayerSetting.IJK;
    }

    private static synchronized void ensureMigrated() {
        if (Prefers.getBoolean(KEY_MIGRATED)) return;
        int buffer = clamp(Prefers.getInt("buffer"), 1, 15);
        int bufferBytes = clamp(Prefers.getInt("buffer_bytes"), 0, 3);
        int backBuffer = clamp(Prefers.getInt("back_buffer"), 0, 3);
        int playCache = clamp(Prefers.getInt("play_cache"), 0, 4);
        boolean preload = Prefers.getBoolean("preload");
        int preloadThreads = clamp(Prefers.getInt("preload_threads", PreloadSetting.DEFAULT_THREADS), PreloadSetting.MIN_THREADS, PreloadSetting.MAX_THREADS);
        int preloadSize = closestPreloadSize(Prefers.getInt("preload_size", PreloadSetting.MIN_SIZE_MB));
        int preloadTime = clamp(Prefers.getInt("preload_time", PreloadSetting.DEFAULT_TIME_SECONDS), PreloadSetting.MIN_TIME_SECONDS, PreloadSetting.MAX_TIME_SECONDS);
        boolean audioPass = Prefers.getBoolean("audio_pass_through", true);
        boolean preferAac = Prefers.getBoolean("prefer_aac");
        boolean audioPrefer = Prefers.getBoolean("audio_prefer");
        boolean videoPrefer = Prefers.getBoolean("video_prefer");
        for (int kernel : new int[]{PlayerSetting.EXO, PlayerSetting.MPV, PlayerSetting.IJK}) {
            Prefers.put(key(kernel, "buffer"), buffer);
            Prefers.put(key(kernel, "buffer_bytes"), bufferBytes);
            Prefers.put(key(kernel, "back_buffer"), backBuffer);
            Prefers.put(key(kernel, "play_cache"), playCache);
            Prefers.put(key(kernel, "preload"), preload);
            Prefers.put(key(kernel, "preload_threads"), preloadThreads);
            Prefers.put(key(kernel, "preload_size"), preloadSize);
            Prefers.put(key(kernel, "preload_time"), preloadTime);
            Prefers.put(key(kernel, "audio_pass_through"), audioPass);
            Prefers.put(key(kernel, "prefer_aac"), preferAac);
            Prefers.put(key(kernel, "audio_prefer"), audioPrefer);
            Prefers.put(key(kernel, "video_prefer"), videoPrefer);
        }
        Prefers.put(KEY_MIGRATED, true);
    }

    private static synchronized void ensurePausePreloadMigrated() {
        if (Prefers.getBoolean(KEY_PAUSE_PRELOAD_MIGRATED)) return;
        for (int kernel : new int[]{PlayerSetting.EXO, PlayerSetting.MPV, PlayerSetting.IJK}) {
            String preferenceKey = key(kernel, "preload_pause");
            int legacy = Prefers.getPrefers().contains(preferenceKey)
                    ? Prefers.getInt(preferenceKey, PreloadSetting.DEFAULT_PAUSE_PRELOAD)
                    : PreloadSetting.DEFAULT_PAUSE_PRELOAD;
            // The previous default was value 1. Existing default installs move to
            // the new "always" default; the removed "off" value falls back to WiFi.
            int migrated = legacy == PreloadSetting.PAUSE_PRELOAD_LEGACY_OFF
                    ? PreloadSetting.PAUSE_PRELOAD_WIFI
                    : PreloadSetting.PAUSE_PRELOAD_ALWAYS;
            Prefers.put(preferenceKey, migrated);
        }
        Prefers.put(KEY_PAUSE_PRELOAD_MIGRATED, true);
    }

    private static String key(int kernel, String suffix) {
        String prefix = kernel == PlayerSetting.MPV ? "perf_mpv_" : kernel == PlayerSetting.IJK ? "perf_ijk_" : "perf_exo_";
        return prefix + suffix;
    }

    private static int closestPreloadSize(int value) {
        int[] options = {128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768};
        int closest = options[0];
        int distance = Math.abs(value - closest);
        for (int option : options) {
            int current = Math.abs(value - option);
            if (current >= distance) continue;
            closest = option;
            distance = current;
        }
        return closest;
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}
