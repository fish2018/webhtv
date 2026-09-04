package com.fongmi.android.tv.setting;

import android.os.SystemClock;

import androidx.media3.common.C;

import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackAutoContextStore;
import com.fongmi.android.tv.player.PlaybackSystemConditionMonitor;
import com.fongmi.android.tv.player.PlaybackTrace;
import com.fongmi.android.tv.player.exo.ExoNetworkProtectionPolicy;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Prefers;

public final class ExoPerformanceSetting {

    public static final int CODEC_QUEUE_AUTO = 0;
    public static final int CODEC_QUEUE_ASYNC = 1;
    public static final int CODEC_QUEUE_SYNC = 2;
    public static final int FRAME_RATE_OFF = 0;
    public static final int FRAME_RATE_SEAMLESS = 1;
    public static final int FRAME_RATE_MOVIE_ALWAYS = 2;
    public static final int FRAME_RATE_RESOLUTION_AND_RATE = 3;

    private static final String KEY_CODEC_QUEUE_MODE = "perf_exo_codec_queue_mode";
    private static final String KEY_FRAME_RATE_MODE = "perf_exo_frame_rate_mode";
    private static final String KEY_START_BUFFER_MS = "perf_exo_start_buffer_ms";
    private static final String KEY_REBUFFER_MS = "perf_exo_rebuffer_ms";
    private static final String KEY_PRIORITIZE_TIME = "perf_exo_prioritize_time";
    private static final String KEY_AUTO_REBUFFER_MS = "perf_exo_auto_rebuffer_ms";
    private static final String KEY_AUTO_CLEAN_STREAK = "perf_exo_auto_clean_streak";
    private static final String KEY_NETWORK_PROTECTION_MODE = "perf_exo_network_protection_mode";
    private static final ExoRebufferLearningCoordinator REBUFFER_LEARNING =
            new ExoRebufferLearningCoordinator(
                    new ExoRebufferLearningStore(
                            new ExoRebufferLearningPreferences()));

    private ExoPerformanceSetting() {
    }

    public static int getCodecQueueMode() {
        if (!Prefers.getPrefers().contains(KEY_CODEC_QUEUE_MODE)) return PlaybackPerformanceSetting.isCodecAsyncQueueingEnabled() ? CODEC_QUEUE_ASYNC : CODEC_QUEUE_SYNC;
        return clamp(Prefers.getInt(KEY_CODEC_QUEUE_MODE, CODEC_QUEUE_AUTO), CODEC_QUEUE_AUTO, CODEC_QUEUE_SYNC);
    }

    public static void putCodecQueueMode(int value) {
        int mode = clamp(value, CODEC_QUEUE_AUTO, CODEC_QUEUE_SYNC);
        Prefers.put(KEY_CODEC_QUEUE_MODE, mode);
        PlaybackPerformanceSetting.setOverride(
                PlaybackPerformanceCatalog.CODEC_ASYNC,
                mode != CODEC_QUEUE_AUTO);
    }

    public static String getCodecQueueText() {
        return switch (getCodecQueueMode()) {
            case CODEC_QUEUE_ASYNC -> "异步";
            case CODEC_QUEUE_SYNC -> "同步";
            default -> "自动";
        };
    }

    public static int getFrameRateMode() {
        return clamp(Prefers.getInt(KEY_FRAME_RATE_MODE, FRAME_RATE_SEAMLESS), FRAME_RATE_OFF, FRAME_RATE_RESOLUTION_AND_RATE);
    }

    public static void putFrameRateMode(int value) {
        Prefers.put(KEY_FRAME_RATE_MODE, clamp(value, FRAME_RATE_OFF, FRAME_RATE_RESOLUTION_AND_RATE));
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.EXO_FRAME_RATE);
    }

    public static int getFrameRateStrategy() {
        return switch (getFrameRateMode()) {
            case FRAME_RATE_OFF -> C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF;
            default -> C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS;
        };
    }

    public static String getFrameRateText() {
        return switch (getFrameRateMode()) {
            case FRAME_RATE_OFF -> "关闭";
            case FRAME_RATE_MOVIE_ALWAYS -> "电影强制";
            case FRAME_RATE_RESOLUTION_AND_RATE -> "分辨率+刷新率";
            default -> "仅无缝";
        };
    }

    public static int getStartBufferMs() {
        return normalizeStart(Prefers.getInt(KEY_START_BUFFER_MS, startBufferForPreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED)));
    }

    public static void putStartBufferMs(int value) {
        Prefers.put(KEY_START_BUFFER_MS, normalizeStart(value));
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.EXO_START_BUFFER);
    }

    public static int nextStartBufferMs() {
        return switch (getStartBufferMs()) {
            case 500 -> 1_000;
            case 1_000 -> 1_500;
            case 1_500 -> 2_000;
            case 2_000 -> 3_000;
            default -> 500;
        };
    }

    public static int getRebufferMs() {
        if (PlaybackPerformanceSetting.isAuto(
                PlayerSetting.EXO,
                PlaybackPerformanceCatalog.EXO_REBUFFER)) {
            return getAutoSessionRebufferMs();
        }
        return normalizeRebuffer(Prefers.getInt(KEY_REBUFFER_MS, rebufferForPreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED)));
    }

    public static void putRebufferMs(int value) {
        Prefers.put(KEY_REBUFFER_MS, normalizeRebuffer(value));
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.EXO_REBUFFER);
    }

    public static int nextRebufferMs() {
        return switch (getRebufferMs()) {
            case 1_000 -> 2_000;
            case 2_000 -> 3_000;
            case 3_000 -> 5_000;
            case 5_000 -> 8_000;
            case 8_000 -> 10_000;
            case 10_000 -> 15_000;
            default -> 1_000;
        };
    }

    public static boolean isPrioritizeTime() {
        return Prefers.getBoolean(KEY_PRIORITIZE_TIME, prioritizeTimeForPreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED));
    }

    public static void putPrioritizeTime(boolean value) {
        Prefers.put(KEY_PRIORITIZE_TIME, value);
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.EXO_PRIORITIZE_TIME);
    }

    public static int getNetworkProtectionMode() {
        int defaultMode = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.EXO,
                PlaybackPerformanceCatalog.EXO_NETWORK_PROTECTION)
                ? ExoNetworkProtectionPolicy.MODE_AUTO : ExoNetworkProtectionPolicy.MODE_OFF;
        return ExoNetworkProtectionPolicy.resolve(Prefers.getInt(KEY_NETWORK_PROTECTION_MODE, defaultMode)).mode();
    }

    public static void putNetworkProtectionMode(int value) {
        int mode = ExoNetworkProtectionPolicy.resolve(value).mode();
        Prefers.put(KEY_NETWORK_PROTECTION_MODE, mode);
        PlaybackPerformanceSetting.setOverride(
                PlaybackPerformanceCatalog.EXO_NETWORK_PROTECTION,
                mode != ExoNetworkProtectionPolicy.MODE_AUTO);
    }

    public static int nextNetworkProtectionMode() {
        return getNetworkProtectionMode() == ExoNetworkProtectionPolicy.MODE_OFF ? ExoNetworkProtectionPolicy.MODE_AUTO : ExoNetworkProtectionPolicy.MODE_OFF;
    }

    public static boolean isNetworkProtectionEnabled() {
        return ExoNetworkProtectionPolicy.resolve(getNetworkProtectionMode()).enabled();
    }

    public static float getNetworkProtectionMinimumSpeed() {
        return ExoNetworkProtectionPolicy.resolve(getNetworkProtectionMode()).minimumSpeed();
    }

    public static String getNetworkProtectionText() {
        return isNetworkProtectionEnabled() ? "开启" : "关闭";
    }

    public static void applyRecommended() {
        Prefers.put(KEY_CODEC_QUEUE_MODE, CODEC_QUEUE_AUTO);
        Prefers.put(KEY_FRAME_RATE_MODE, FRAME_RATE_SEAMLESS);
        Prefers.put(KEY_NETWORK_PROTECTION_MODE, ExoNetworkProtectionPolicy.MODE_OFF);
        applyStartBufferPreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED);
        applyRebufferPreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED);
        applyPrioritizeTimePreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED);
    }

    public static void applyAuto() {
        Prefers.put(KEY_CODEC_QUEUE_MODE, CODEC_QUEUE_AUTO);
        Prefers.put(KEY_FRAME_RATE_MODE, FRAME_RATE_SEAMLESS);
        Prefers.put(KEY_NETWORK_PROTECTION_MODE, ExoNetworkProtectionPolicy.MODE_AUTO);
        applyStartBufferPreset(PlaybackPerformanceSetting.PROFILE_AUTO);
        applyRebufferPreset(PlaybackPerformanceSetting.PROFILE_AUTO);
        applyPrioritizeTimePreset(PlaybackPerformanceSetting.PROFILE_AUTO);
        resetAutoAdaptiveValues();
    }

    public static void recordAutoSession(
            String traceId,
            int rebufferCount,
            long rebufferTotalMs,
            long positionMs,
            long mediaBitrate,
            long bandwidthEstimate) {
        if (!PlaybackPerformanceSetting.isAuto(
                PlayerSetting.EXO,
                PlaybackPerformanceCatalog.EXO_REBUFFER)) {
            REBUFFER_LEARNING.discard(traceId);
            return;
        }
        ExoRebufferLearningCoordinator.FinishResult result =
                REBUFFER_LEARNING.finish(
                        traceId,
                        currentNetworkDigest(),
                        rebufferCount,
                        rebufferTotalMs,
                        positionMs,
                        mediaBitrate,
                        bandwidthEstimate,
                        System.currentTimeMillis());
        logLearning(
                result.action(),
                result.key(),
                result.rebufferMs(),
                result.sampleCount());
    }

    public static void discardAutoSession(String traceId) {
        REBUFFER_LEARNING.discard(traceId);
    }

    public static int updateAutoSession(
            String traceId,
            int rebufferCount,
            long rebufferTotalMs,
            long positionMs,
            long mediaBitrate,
            long bandwidthEstimate) {
        if (!PlaybackPerformanceSetting.isAuto(
                PlayerSetting.EXO,
                PlaybackPerformanceCatalog.EXO_REBUFFER)) {
            return getRebufferMs();
        }
        return REBUFFER_LEARNING.update(
                traceId,
                rebufferCount,
                rebufferTotalMs,
                positionMs,
                mediaBitrate,
                bandwidthEstimate).rebufferMs();
    }

    public static void beginAutoSession() {
        PlaybackAutoContext context = PlaybackAutoContextStore.process().snapshot();
        if (!PlaybackPerformanceSetting.isAuto(
                PlayerSetting.EXO,
                PlaybackPerformanceCatalog.EXO_REBUFFER)) {
            REBUFFER_LEARNING.discard(context.session().traceId());
            return;
        }
        clearLegacyAutoLearning();
        long nowElapsedMs = SystemClock.elapsedRealtime();
        ExoRebufferLearningKey.Key key = ExoRebufferLearningKey.resolve(
                context,
                currentNetworkDigest(),
                nowElapsedMs);
        ExoRebufferLearningCoordinator.BeginResult result =
                REBUFFER_LEARNING.begin(
                        context.session().traceId(),
                        key,
                        System.currentTimeMillis());
        logLearning(
                result.action(),
                result.key(),
                result.rebufferMs(),
                result.sampleCount());
    }

    public static void refreshAutoSession(String traceId) {
        if (!PlaybackPerformanceSetting.isAuto(
                PlayerSetting.EXO,
                PlaybackPerformanceCatalog.EXO_REBUFFER)
                || !REBUFFER_LEARNING.hasSession(traceId)) {
            return;
        }
        PlaybackAutoContext context = PlaybackAutoContextStore.process().snapshot();
        if (!context.session().active()
                || !context.session().traceId().equals(
                PlaybackTrace.normalize(traceId))) {
            return;
        }
        String networkDigest = REBUFFER_LEARNING.needsBinding(traceId)
                ? currentNetworkDigest()
                : "";
        ExoRebufferLearningCoordinator.BindResult result =
                REBUFFER_LEARNING.bind(
                        traceId,
                        ExoRebufferLearningKey.resolve(
                                context,
                                networkDigest,
                                SystemClock.elapsedRealtime()),
                        System.currentTimeMillis());
        if (result.action()
                == ExoRebufferLearningCoordinator.Action.LATE_BOUND
                || result.action()
                == ExoRebufferLearningCoordinator.Action.LATE_HIT) {
            logLearning(
                    result.action(),
                    result.key(),
                    result.rebufferMs(),
                    result.sampleCount());
        }
    }

    public static int getAutoSessionRebufferMs() {
        return REBUFFER_LEARNING.currentRebufferMs();
    }

    public static int getAutoSessionStartBufferMs() {
        return AutoRebufferPolicy.startBufferMs(getAutoSessionRebufferMs());
    }

    public static int getEffectiveStartBufferMs() {
        if (!PlaybackPerformanceSetting.isAuto(
                PlayerSetting.EXO,
                PlaybackPerformanceCatalog.EXO_START_BUFFER)) {
            return getStartBufferMs();
        }
        return AutoRebufferPolicy.startBufferMs(getEffectiveRebufferMs());
    }

    public static int getEffectiveRebufferMs() {
        return PlaybackPerformanceSetting.isAuto(
                PlayerSetting.EXO,
                PlaybackPerformanceCatalog.EXO_REBUFFER)
                ? getAutoSessionRebufferMs()
                : normalizeRebuffer(Prefers.getInt(
                KEY_REBUFFER_MS,
                rebufferForPreset(PlaybackPerformanceSetting.PROFILE_AUTO)));
    }

    public static int getAutoDefaultStartBufferMs() {
        return AutoRebufferPolicy.DEFAULT_START_BUFFER_MS;
    }

    private static void resetAutoAdaptiveValues() {
        clearLegacyAutoLearning();
        REBUFFER_LEARNING.clear();
    }

    private static String currentNetworkDigest() {
        return PlaybackSystemConditionMonitor.process()
                .currentNetworkIdentityDigest();
    }

    private static void clearLegacyAutoLearning() {
        Prefers.remove(KEY_AUTO_REBUFFER_MS);
        Prefers.remove(KEY_AUTO_CLEAN_STREAK);
    }

    private static void logLearning(
            ExoRebufferLearningCoordinator.Action action,
            ExoRebufferLearningKey.Key key,
            int rebufferMs,
            int sampleCount) {
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log(
                "exo-buffer",
                "learning action=%s network=%s path=%s protocol=%s stream=%s rebufferMs=%d samples=%d",
                action == null ? "unknown" : action.label(),
                key == null ? "unknown" : "known",
                key == null ? "unknown" : key.pathKind().label(),
                key == null ? "unknown" : key.protocol().label(),
                key == null ? "unknown" : key.streamKind().label(),
                Math.max(0, rebufferMs),
                Math.max(0, sampleCount));
    }

    public static void applyCompatible() {
        applyLightweight();
    }

    public static void applyLightweight() {
        Prefers.put(KEY_CODEC_QUEUE_MODE, CODEC_QUEUE_AUTO);
        Prefers.put(KEY_FRAME_RATE_MODE, FRAME_RATE_OFF);
        Prefers.put(KEY_NETWORK_PROTECTION_MODE, ExoNetworkProtectionPolicy.MODE_OFF);
        applyStartBufferPreset(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT);
        applyRebufferPreset(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT);
        applyPrioritizeTimePreset(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT);
    }

    static void applyStartBufferPreset(int profile) {
        Prefers.put(KEY_START_BUFFER_MS, startBufferForPreset(profile));
    }

    static int startBufferForPreset(int profile) {
        return switch (profile) {
            case PlaybackPerformanceSetting.PROFILE_COMPATIBLE,
                 PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT -> 1_500;
            default -> 1_500;
        };
    }

    static void applyRebufferPreset(int profile) {
        Prefers.put(KEY_REBUFFER_MS, rebufferForPreset(profile));
    }

    static int rebufferForPreset(int profile) {
        return switch (profile) {
            case PlaybackPerformanceSetting.PROFILE_COMPATIBLE,
                 PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT -> 3_000;
            default -> 3_000;
        };
    }

    static void applyPrioritizeTimePreset(int profile) {
        Prefers.put(KEY_PRIORITIZE_TIME, prioritizeTimeForPreset(profile));
    }

    static boolean prioritizeTimeForPreset(int profile) {
        return false;
    }

    private static int normalizeStart(int value) {
        if (value <= 500) return 500;
        if (value <= 1_000) return 1_000;
        if (value <= 1_500) return 1_500;
        if (value <= 2_000) return 2_000;
        return 3_000;
    }

    private static int normalizeRebuffer(int value) {
        if (value <= 1_000) return 1_000;
        if (value <= 2_000) return 2_000;
        if (value <= 3_000) return 3_000;
        if (value <= 5_000) return 5_000;
        if (value <= 8_000) return 8_000;
        if (value <= 10_000) return 10_000;
        return 15_000;
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}
