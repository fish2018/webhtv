package com.fongmi.android.tv.setting;

public class PreloadSetting {

    public static final int MIN_THREADS = 1;
    public static final int MAX_THREADS = 4;
    public static final int DEFAULT_THREADS = 1;
    public static final int MIN_SIZE_MB = 128;
    public static final int MAX_SIZE_MB = 4096;
    public static final int MIN_TIME_SECONDS = 20;
    public static final int MAX_TIME_SECONDS = 120;
    public static final int DEFAULT_TIME_SECONDS = 20;
    public static final int STEP_TIME_SECONDS = 10;
    public static final int WHOLE_MEDIA_AHEAD_SECONDS = 0;
    public static final int DEFAULT_AHEAD_SECONDS = 300;
    /** Legacy persisted value from the three-option pause preload setting. */
    public static final int PAUSE_PRELOAD_LEGACY_OFF = 0;
    public static final int PAUSE_PRELOAD_WIFI = 1;
    public static final int PAUSE_PRELOAD_ALWAYS = 2;
    public static final int DEFAULT_PAUSE_PRELOAD = PAUSE_PRELOAD_ALWAYS;
    private static final int[] SIZE_OPTIONS_MB = {128, 256, 512, 1024, 2048, 4096};
    private static final int[] AHEAD_OPTIONS_SECONDS = {60, 180, 300, 600, 1800, 3600, WHOLE_MEDIA_AHEAD_SECONDS};
    private static final int[] PAUSE_PRELOAD_OPTIONS = {PAUSE_PRELOAD_ALWAYS, PAUSE_PRELOAD_WIFI};

    public static boolean isPreload() {
        return isPreload(PlayerSetting.getPlayer());
    }

    public static boolean isPreload(int kernel) {
        return KernelPerformanceSetting.isPreload(PlayerSetting.sanitizePlayer(kernel));
    }

    public static void putPreload(boolean preload) {
        KernelPerformanceSetting.putPreload(PlayerSetting.getPlayer(), preload);
    }

    public static int getPreloadThreads() {
        return getPreloadThreads(PlayerSetting.getPlayer());
    }

    public static int getPreloadThreads(int kernel) {
        return KernelPerformanceSetting.getPreloadThreads(PlayerSetting.sanitizePlayer(kernel));
    }

    public static void putPreloadThreads(int threads) {
        KernelPerformanceSetting.putPreloadThreads(PlayerSetting.getPlayer(), threads);
    }

    public static int getPreloadSizeMb() {
        return getPreloadSizeMb(PlayerSetting.getPlayer());
    }

    public static int getPreloadSizeMb(int kernel) {
        return closestSize(KernelPerformanceSetting.getPreloadSizeMb(PlayerSetting.sanitizePlayer(kernel)));
    }

    public static void putPreloadSizeMb(int size) {
        KernelPerformanceSetting.putPreloadSizeMb(PlayerSetting.getPlayer(), closestSize(size));
    }

    public static int getPreloadSizeOptionCount() {
        return SIZE_OPTIONS_MB.length;
    }

    public static int getPreloadSizeMbAt(int index) {
        return SIZE_OPTIONS_MB[clamp(index, 0, SIZE_OPTIONS_MB.length - 1)];
    }

    public static int getPreloadSizeIndex() {
        int value = getPreloadSizeMb();
        for (int i = 0; i < SIZE_OPTIONS_MB.length; i++) if (SIZE_OPTIONS_MB[i] == value) return i;
        return 0;
    }

    public static int getNextPreloadSizeMb() {
        return SIZE_OPTIONS_MB[(getPreloadSizeIndex() + 1) % SIZE_OPTIONS_MB.length];
    }

    public static long getPreloadSizeBytes() {
        return getPreloadSizeBytes(PlayerSetting.getPlayer());
    }

    public static long getPreloadSizeBytes(int kernel) {
        return getPreloadSizeMb(kernel) * 1024L * 1024L;
    }

    public static int getPreloadTimeSeconds() {
        return getPreloadTimeSeconds(PlayerSetting.getPlayer());
    }

    public static int getPreloadTimeSeconds(int kernel) {
        int seconds = clamp(KernelPerformanceSetting.getPreloadTimeSeconds(PlayerSetting.sanitizePlayer(kernel)), MIN_TIME_SECONDS, MAX_TIME_SECONDS);
        int steps = Math.round((float) (seconds - MIN_TIME_SECONDS) / STEP_TIME_SECONDS);
        return clamp(MIN_TIME_SECONDS + steps * STEP_TIME_SECONDS, MIN_TIME_SECONDS, MAX_TIME_SECONDS);
    }

    public static void putPreloadTimeSeconds(int seconds) {
        KernelPerformanceSetting.putPreloadTimeSeconds(PlayerSetting.getPlayer(), clamp(seconds, MIN_TIME_SECONDS, MAX_TIME_SECONDS));
    }

    public static long getPreloadDurationMs() {
        return getPreloadDurationMs(PlayerSetting.getPlayer());
    }

    public static long getPreloadDurationMs(int kernel) {
        return getPreloadTimeSeconds(kernel) * 1000L;
    }

    public static int getPreloadAheadSeconds() {
        return getPreloadAheadSeconds(PlayerSetting.getPlayer());
    }

    public static int getPreloadAheadSeconds(int kernel) {
        return closestAhead(KernelPerformanceSetting.getPreloadAheadSeconds(PlayerSetting.sanitizePlayer(kernel)));
    }

    public static void putPreloadAheadSeconds(int seconds) {
        KernelPerformanceSetting.putPreloadAheadSeconds(PlayerSetting.getPlayer(), closestAhead(seconds));
    }

    public static int getPreloadAheadOptionCount() {
        return AHEAD_OPTIONS_SECONDS.length;
    }

    public static int getPreloadAheadSecondsAt(int index) {
        return AHEAD_OPTIONS_SECONDS[clamp(index, 0, AHEAD_OPTIONS_SECONDS.length - 1)];
    }

    public static int getPreloadAheadIndex() {
        int value = getPreloadAheadSeconds();
        for (int i = 0; i < AHEAD_OPTIONS_SECONDS.length; i++) if (AHEAD_OPTIONS_SECONDS[i] == value) return i;
        return 0;
    }

    public static int getNextPreloadAheadSeconds() {
        return AHEAD_OPTIONS_SECONDS[(getPreloadAheadIndex() + 1) % AHEAD_OPTIONS_SECONDS.length];
    }

    public static long getPreloadAheadDurationMs(int kernel) {
        int seconds = getPreloadAheadSeconds(kernel);
        return seconds == WHOLE_MEDIA_AHEAD_SECONDS ? Long.MAX_VALUE : seconds * 1000L;
    }

    public static int getPausePreloadPolicy() {
        return getPausePreloadPolicy(PlayerSetting.getPlayer());
    }

    public static int getPausePreloadPolicy(int kernel) {
        return normalizePausePreloadPolicy(KernelPerformanceSetting.getPausePreloadPolicy(PlayerSetting.sanitizePlayer(kernel)));
    }

    public static void putPausePreloadPolicy(int policy) {
        KernelPerformanceSetting.putPausePreloadPolicy(PlayerSetting.getPlayer(), normalizePausePreloadPolicy(policy));
    }

    public static int getPausePreloadOptionCount() {
        return PAUSE_PRELOAD_OPTIONS.length;
    }

    public static int getPausePreloadPolicyAt(int index) {
        return PAUSE_PRELOAD_OPTIONS[clamp(index, 0, PAUSE_PRELOAD_OPTIONS.length - 1)];
    }

    public static int getPausePreloadPolicyIndex() {
        int policy = getPausePreloadPolicy();
        for (int i = 0; i < PAUSE_PRELOAD_OPTIONS.length; i++) {
            if (PAUSE_PRELOAD_OPTIONS[i] == policy) return i;
        }
        return 0;
    }

    public static int getNextPausePreloadPolicy() {
        return PAUSE_PRELOAD_OPTIONS[(getPausePreloadPolicyIndex() + 1) % PAUSE_PRELOAD_OPTIONS.length];
    }

    static int normalizePausePreloadPolicy(int policy) {
        return policy == PAUSE_PRELOAD_ALWAYS ? PAUSE_PRELOAD_ALWAYS : PAUSE_PRELOAD_WIFI;
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private static int closestSize(int value) {
        int closest = SIZE_OPTIONS_MB[0];
        int distance = Math.abs(value - closest);
        for (int option : SIZE_OPTIONS_MB) {
            int current = Math.abs(value - option);
            if (current >= distance) continue;
            closest = option;
            distance = current;
        }
        return closest;
    }

    private static int closestAhead(int value) {
        if (value == WHOLE_MEDIA_AHEAD_SECONDS) return WHOLE_MEDIA_AHEAD_SECONDS;
        int closest = AHEAD_OPTIONS_SECONDS[0];
        int distance = Math.abs(value - closest);
        for (int option : AHEAD_OPTIONS_SECONDS) {
            if (option == WHOLE_MEDIA_AHEAD_SECONDS) continue;
            int current = Math.abs(value - option);
            if (current >= distance) continue;
            closest = option;
            distance = current;
        }
        return closest;
    }
}
