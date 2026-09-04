package com.fongmi.android.tv.setting;

import android.content.SharedPreferences;

import com.github.catvod.utils.Prefers;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PlaybackPerformanceSetting {

    public static final int PROFILE_RECOMMENDED = 0;
    public static final int PROFILE_COMPATIBLE = 1;
    public static final int PROFILE_CUSTOM = 2;
    public static final int PROFILE_LIGHTWEIGHT = 3;
    public static final int PROFILE_AUTO = 4;

    public static final int DV7_HANDLING_P81 = 0;
    public static final int DV7_HANDLING_HDR10 = 1;

    public static final String KEY_PROFILE = "playback_performance_profile";
    private static final String KEY_PROFILE_MIGRATED = "playback_performance_profile_per_kernel";
    private static final String KEY_PROFILE_EXO = "perf_exo_profile";
    private static final String KEY_PROFILE_MPV = "perf_mpv_profile";
    private static final String KEY_PROFILE_IJK = "perf_ijk_profile";
    private static final String KEY_INITIALIZED = "playback_performance_initialized";
    private static final String KEY_BUFFER_WATERMARKS_MIGRATED = "playback_performance_buffer_watermarks_v2";
    private static final String KEY_EXO_SIZE_PRIORITY_MIGRATED = "playback_performance_exo_size_priority_v1";
    private static final String KEY_PRELOAD_DEFAULTS_MIGRATED = "playback_performance_preload_defaults_v1";
    private static final String KEY_EXO_LOAD_CONTROL_MIGRATED = "playback_performance_exo_load_control_v2";
    private static final String KEY_EXO_BACK_BUFFER_MIGRATED = "playback_performance_exo_back_buffer_v1";
    private static final String KEY_EXO_REBUFFER_MIGRATED = "playback_performance_exo_rebuffer_v3";
    private static final String KEY_MPV_REBUFFER_MIGRATED = "playback_performance_mpv_rebuffer_v1";
    private static final String KEY_MPV_AUTO_BASELINE_MIGRATED = "playback_performance_mpv_auto_baseline_v1";
    private static final String KEY_PROFILE_MERGE_SCHEMA =
            "playback_performance_profile_merge_schema";
    private static final String KEY_PROFILE_MERGE_ROLLED_BACK =
            "playback_performance_profile_merge_rolled_back";
    private static final String KEY_PROFILE_MERGE_MIGRATED_MASK =
            "playback_performance_profile_merge_migrated_mask";
    private static final String KEY_PROFILE_AUTO_LIGHT_MIGRATED =
            "playback_performance_profile_auto_light_v1";
    private static final String KEY_AUDIO_PASSTHROUGH_DEFAULT_MIGRATED =
            "playback_performance_audio_passthrough_default_v1";
    private static final String KEY_CODEC_ASYNC_QUEUEING = "perf_codec_async_queueing";
    private static final String KEY_DYNAMIC_SCHEDULING = "perf_dynamic_scheduling";
    private static final String KEY_VIDEO_DURATION_PROGRESS = "perf_video_duration_progress";
    private static final String KEY_LATE_DROP_INPUT = "perf_late_drop_input";
    private static final String KEY_TRACK_LIMIT = "perf_track_limit";
    private static final String KEY_ADAPTIVE_DOWNGRADE = "perf_adaptive_downgrade";
    private static final String KEY_LOAD_ONLY_SELECTED_TRACKS = "perf_load_only_selected_tracks";
    private static final String KEY_SURFACE_FIXED_SIZE = "perf_surface_fixed_size";
    private static final String KEY_DECODER_FALLBACK = "perf_decoder_fallback";
    private static final String KEY_DV7_HDR10_FALLBACK_LEGACY =
            "perf_dv7_hdr10_fallback";
    private static final String KEY_MPV_DV7_HANDLING = "perf_mpv_dv7_handling";
    private static final String KEY_DV7_HANDLING = "perf_dv7_handling";
    private static final String KEY_SOFT_VIDEO_TUNE = "perf_soft_video_tune";
    private static final String KEY_HIGH_BUFFER = "perf_high_buffer";
    private static final String KEY_BANDWIDTH_METER = "perf_bandwidth_meter";
    private static final String KEY_AUTO_OVERRIDES_EXO = "perf_exo_auto_overrides_v1";
    private static final String KEY_AUTO_OVERRIDES_MPV = "perf_mpv_auto_overrides_v1";
    private static final String KEY_AUTO_OVERRIDES_IJK = "perf_ijk_auto_overrides_v1";

    public static void ensureInitialized() {
        PlaybackExperimentSetting.ensureInitialized();
        if (!Prefers.getPrefers().contains(KEY_INITIALIZED)) {
            applyAutoValues();
            Prefers.put(KEY_INITIALIZED, true);
        }
        migrateProfiles();
        migrateBufferWatermarks();
        migrateExoSizePriority();
        migratePreloadDefaults();
        migrateExoLoadControl();
        migrateExoBackBuffer();
        migrateExoRebuffer();
        migrateMpvRebuffer();
        migrateMpvAutoBaseline();
        // The former recommended-profile rollback must not run after the
        // profile list has been consolidated, otherwise an interrupted old
        // rollback could restore a removed profile behind the new UI.
        migrateAutoLightProfiles();
        migrateAudioPassthroughDefault();
    }

    public static int getProfile() {
        return getProfile(PlayerSetting.getPlayer());
    }

    public static int getProfile(int kernel) {
        ensureInitialized();
        return PlaybackProfileMergePolicy.effectiveProfile(
                rawProfile(kernel),
                profileMergeResolution().mergeEnabled());
    }

    public static void applyAuto() {
        int kernel = PlayerSetting.getPlayer();
        clearOverrides(kernel);
        applyAutoProfile(kernel);
        putCurrentProfile(PROFILE_AUTO);
    }

    public static void applyRecommended() {
        applyAuto();
    }

    private static void applyRecommendedProfile(int kernel) {
        KernelPerformanceSetting.applyPreset(kernel, PROFILE_RECOMMENDED);
        if (kernel == PlayerSetting.EXO) {
            putRecommendedFlags();
            ExoPerformanceSetting.applyRecommended();
            Prefers.put("render", PlayerSetting.RENDER_SURFACE);
            Prefers.put("tunnel", false);
            Prefers.put("exo_4k_compat", true);
        } else if (kernel == PlayerSetting.MPV) {
            MpvPerformanceSetting.applyRecommended();
            put(KEY_MPV_DV7_HANDLING, DV7_HANDLING_P81);
        } else {
            IjkPerformanceSetting.applyRecommended();
        }
    }

    private static void applyAutoProfile(int kernel) {
        KernelPerformanceSetting.applyPreset(kernel, PROFILE_AUTO);
        if (kernel == PlayerSetting.EXO) {
            putRecommendedFlags();
            ExoPerformanceSetting.applyAuto();
            Prefers.put("render", PlayerSetting.RENDER_SURFACE);
            Prefers.put("tunnel", false);
            Prefers.put("exo_4k_compat", true);
        } else if (kernel == PlayerSetting.MPV) {
            MpvPerformanceSetting.applyAuto();
            put(KEY_MPV_DV7_HANDLING, DV7_HANDLING_P81);
        } else {
            IjkPerformanceSetting.applyRecommended();
        }
    }

    private static void applyAutoValues() {
        for (int kernel : new int[]{PlayerSetting.EXO, PlayerSetting.MPV, PlayerSetting.IJK}) {
            applyAutoProfile(kernel);
            Prefers.put(profileKey(kernel), PROFILE_AUTO);
        }
        Prefers.put(KEY_PROFILE, PROFILE_AUTO);
    }

    public static void applyCompatible() {
        applyLightweight();
    }

    public static void applyLightweight() {
        int kernel = PlayerSetting.getPlayer();
        clearOverrides(kernel);
        applyLightweightProfile(kernel);
        putCurrentProfile(PROFILE_LIGHTWEIGHT);
    }

    private static void applyLightweightProfile(int kernel) {
        KernelPerformanceSetting.applyPreset(kernel, PROFILE_LIGHTWEIGHT);
        if (kernel == PlayerSetting.EXO) {
            putRecommendedFlags();
            ExoPerformanceSetting.applyLightweight();
            Prefers.put("render", PlayerSetting.RENDER_SURFACE);
            Prefers.put("tunnel", false);
            Prefers.put("exo_4k_compat", true);
        } else if (kernel == PlayerSetting.MPV) {
            MpvPerformanceSetting.applyLightweight();
            put(KEY_MPV_DV7_HANDLING, DV7_HANDLING_P81);
        } else {
            IjkPerformanceSetting.applyLightweight();
        }
    }

    public static void markCustom() {
        ensureInitialized();
        clearOverrides(PlayerSetting.getPlayer());
        putCurrentProfile(PROFILE_CUSTOM);
    }

    public static void markOverride(String optionId) {
        setOverride(optionId, true);
    }

    public static synchronized void setOverride(
            String optionId,
            boolean overridden) {
        ensureInitialized();
        String id = optionId == null ? "" : optionId.trim();
        if (id.isEmpty()) {
            markCustom();
            return;
        }
        int kernel = PlayerSetting.getPlayer();
        if (!isAuto(kernel)) {
            markCustom();
            return;
        }
        Set<String> overrides = getOverrides(kernel);
        boolean changed = overridden ? overrides.add(id) : overrides.remove(id);
        if (!changed) return;
        SharedPreferences.Editor editor = Prefers.getPrefers().edit();
        if (overrides.isEmpty()) editor.remove(overrideKey(kernel));
        else editor.putStringSet(overrideKey(kernel), overrides);
        editor.apply();
    }

    public static String getProfileName() {
        int profile = getProfile();
        return switch (profile) {
            case PROFILE_AUTO -> {
                int count = getOverrideCount(PlayerSetting.getPlayer());
                yield count == 0 ? "自动" : "自动（已覆盖" + count + "项）";
            }
            case PROFILE_COMPATIBLE,
                 PROFILE_LIGHTWEIGHT -> "轻量";
            case PROFILE_CUSTOM -> "自定义";
            default -> "均衡";
        };
    }

    public static boolean isRecommended() {
        return getProfile() == PROFILE_RECOMMENDED;
    }

    public static boolean isRecommendedMerged() {
        return true;
    }

    public static boolean canChangeRecommendedMerge() {
        return false;
    }

    public static synchronized boolean rollbackRecommendedMerge() {
        return false;
    }

    public static synchronized boolean enableRecommendedMerge() {
        return true;
    }

    public static boolean isAuto() {
        return isAuto(PlayerSetting.getPlayer());
    }

    public static boolean isAuto(int kernel) {
        return getProfile(kernel) == PROFILE_AUTO;
    }

    public static boolean isAuto(String optionId) {
        return isAuto(PlayerSetting.getPlayer(), optionId);
    }

    public static boolean isAuto(int kernel, String optionId) {
        return isAuto(kernel) && !isOverridden(kernel, optionId);
    }

    public static boolean hasAutomaticOptions(int kernel, String... optionIds) {
        if (!isAuto(kernel) || optionIds == null) return false;
        for (String optionId : optionIds) {
            if (!isOverridden(kernel, optionId)) return true;
        }
        return false;
    }

    public static boolean isOverridden(int kernel, String optionId) {
        String id = optionId == null ? "" : optionId.trim();
        return !id.isEmpty() && getOverrides(kernel).contains(id);
    }

    public static int getOverrideCount(int kernel) {
        return getOverrides(kernel).size();
    }

    public static synchronized void clearOverrides(int kernel) {
        Prefers.remove(overrideKey(kernel));
    }

    public static boolean isCompatible() {
        return isLightweight();
    }

    public static boolean isLightweight() {
        return getProfile() == PROFILE_LIGHTWEIGHT;
    }

    public static boolean isHighBufferEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_HIGH_BUFFER, true);
    }

    public static void putHighBufferEnabled(boolean value) {
        putCustom(KEY_HIGH_BUFFER, value, PlaybackPerformanceCatalog.BUFFER_TIME);
    }

    public static boolean isBandwidthMeterEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_BANDWIDTH_METER, true);
    }

    public static void putBandwidthMeterEnabled(boolean value) {
        putCustom(KEY_BANDWIDTH_METER, value, PlaybackPerformanceCatalog.BANDWIDTH_METER);
    }

    public static boolean isDynamicSchedulingEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_DYNAMIC_SCHEDULING, true);
    }

    public static void putDynamicSchedulingEnabled(boolean value) {
        putCustom(KEY_DYNAMIC_SCHEDULING, value, PlaybackPerformanceCatalog.DYNAMIC_SCHEDULING);
    }

    public static boolean isCodecAsyncQueueingEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_CODEC_ASYNC_QUEUEING, true);
    }

    public static void putCodecAsyncQueueingEnabled(boolean value) {
        putCustom(KEY_CODEC_ASYNC_QUEUEING, value, PlaybackPerformanceCatalog.CODEC_ASYNC);
    }

    public static boolean isVideoDurationProgressEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_VIDEO_DURATION_PROGRESS, true);
    }

    public static void putVideoDurationProgressEnabled(boolean value) {
        putCustom(KEY_VIDEO_DURATION_PROGRESS, value, PlaybackPerformanceCatalog.DURATION_PROGRESS);
    }

    public static boolean isLateDropInputEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_LATE_DROP_INPUT, true);
    }

    public static void putLateDropInputEnabled(boolean value) {
        putCustom(KEY_LATE_DROP_INPUT, value, PlaybackPerformanceCatalog.LATE_DROP);
    }

    public static boolean isTrackLimitEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_TRACK_LIMIT, true);
    }

    public static void putTrackLimitEnabled(boolean value) {
        putCustom(KEY_TRACK_LIMIT, value, PlaybackPerformanceCatalog.TRACK_LIMIT);
    }

    public static boolean isAdaptiveDowngradeEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_ADAPTIVE_DOWNGRADE, true);
    }

    public static void putAdaptiveDowngradeEnabled(boolean value) {
        putCustom(KEY_ADAPTIVE_DOWNGRADE, value, PlaybackPerformanceCatalog.ADAPTIVE_DOWNGRADE);
    }

    public static boolean isLoadOnlySelectedTracksEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_LOAD_ONLY_SELECTED_TRACKS, true);
    }

    public static void putLoadOnlySelectedTracksEnabled(boolean value) {
        putCustom(KEY_LOAD_ONLY_SELECTED_TRACKS, value, PlaybackPerformanceCatalog.LOAD_SELECTED_TRACKS);
    }

    public static boolean isSurfaceFixedSizeEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_SURFACE_FIXED_SIZE, true);
    }

    public static void putSurfaceFixedSizeEnabled(boolean value) {
        putCustom(KEY_SURFACE_FIXED_SIZE, value, PlaybackPerformanceCatalog.SURFACE_FIXED_SIZE);
    }

    public static boolean isDecoderFallbackEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_DECODER_FALLBACK, true);
    }

    public static void putDecoderFallbackEnabled(boolean value) {
        putCustom(KEY_DECODER_FALLBACK, value, PlaybackPerformanceCatalog.DECODER_FALLBACK);
    }

    public static boolean isDv7Hdr10FallbackEnabled() {
        ensureInitialized();
        if (PlayerSetting.getPlayer() == PlayerSetting.MPV) {
            return getMpvDv7HandlingMode() == DV7_HANDLING_HDR10;
        }
        return getDv7HandlingMode() == DV7_HANDLING_HDR10;
    }

    public static void putDv7Hdr10FallbackEnabled(boolean value) {
        if (PlayerSetting.getPlayer() == PlayerSetting.MPV) {
            putMpvDv7HandlingMode(value ? DV7_HANDLING_HDR10 : DV7_HANDLING_P81);
        } else {
            putDv7HandlingMode(value ? DV7_HANDLING_HDR10 : DV7_HANDLING_P81);
        }
    }

    public static int getDv7HandlingMode() {
        ensureInitialized();
        return clampDv7Handling(Prefers.getInt(KEY_DV7_HANDLING, DV7_HANDLING_P81));
    }

    public static boolean isDv7P81Enabled() {
        return getDv7HandlingMode() == DV7_HANDLING_P81;
    }

    /** HDR10 is used only when the user explicitly selects the HDR10 handling mode. */
    public static boolean isDv7FallbackAllowed() {
        ensureInitialized();
        return getDv7HandlingMode() == DV7_HANDLING_HDR10;
    }

    public static String getDv7HandlingText() {
        return getDv7HandlingMode() == DV7_HANDLING_P81
                ? "升级P8.1" : "降级HDR10";
    }

    public static void putDv7HandlingMode(int mode) {
        putCustom(KEY_DV7_HANDLING, clampDv7Handling(mode), PlaybackPerformanceCatalog.DV7_HDR10_FALLBACK);
    }

    public static int getMpvDv7HandlingMode() {
        ensureInitialized();
        if (Prefers.getPrefers().contains(KEY_MPV_DV7_HANDLING))
            return clampDv7Handling(Prefers.getInt(KEY_MPV_DV7_HANDLING, DV7_HANDLING_P81));
        if (Prefers.getPrefers().contains(KEY_DV7_HDR10_FALLBACK_LEGACY)) {
            return Prefers.getBoolean(KEY_DV7_HDR10_FALLBACK_LEGACY, true)
                    ? DV7_HANDLING_HDR10 : DV7_HANDLING_P81;
        }
        return DV7_HANDLING_P81;
    }

    public static void putMpvDv7HandlingMode(int mode) {
        putCustom(KEY_MPV_DV7_HANDLING, clampDv7Handling(mode),
                PlaybackPerformanceCatalog.DV7_HDR10_FALLBACK);
    }

    public static String getMpvDv7HandlingText() {
        return getMpvDv7HandlingMode() == DV7_HANDLING_P81
                ? "升级P8.1" : "降级HDR10";
    }

    public static String getMpvDv7HandlingOption() {
        return getMpvDv7HandlingMode() == DV7_HANDLING_P81
                ? "p81" : "hdr10";
    }

    public static boolean isSoftVideoTuneEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_SOFT_VIDEO_TUNE, true);
    }

    public static void putSoftVideoTuneEnabled(boolean value) {
        putCustom(KEY_SOFT_VIDEO_TUNE, value, PlaybackPerformanceCatalog.SOFT_VIDEO_TUNE);
    }

    public static String getSummary() {
        ensureInitialized();
        return getProfileName();
    }

    public static String getForwardBufferText() {
        ensureInitialized();
        int kernel = PlayerSetting.getPlayer();
        return forwardBufferText(
                kernel,
                displayProfile(kernel, PlaybackPerformanceCatalog.BUFFER_TIME),
                PlayerSetting.getBuffer());
    }

    public static String getMemoryBufferText() {
        ensureInitialized();
        int kernel = PlayerSetting.getPlayer();
        if (kernel == PlayerSetting.IJK) {
            return ijkMemoryBufferText(
                    displayProfile(kernel, PlaybackPerformanceCatalog.IJK_BUFFER),
                    IjkPerformanceSetting.getBufferMb());
        }
        return memoryBufferText(
                kernel,
                displayProfile(kernel, PlaybackPerformanceCatalog.BUFFER_BYTES),
                PlayerSetting.getBufferBytesOption());
    }

    public static String getPlayedDataRetentionText() {
        ensureInitialized();
        int kernel = PlayerSetting.getPlayer();
        return playedDataRetentionText(
                kernel,
                displayProfile(kernel, PlaybackPerformanceCatalog.BACK_BUFFER),
                PlayerSetting.getBackBufferOption());
    }

    public static String getPlaybackDiskCacheText() {
        ensureInitialized();
        return playbackDiskCacheText(PlayerSetting.getPlayCacheOption());
    }

    public static String getExoStartBufferText() {
        ensureInitialized();
        return isAuto(PlayerSetting.EXO, PlaybackPerformanceCatalog.EXO_START_BUFFER)
                ? "自动 · 0.5～8秒"
                : secondsText(ExoPerformanceSetting.getStartBufferMs());
    }

    public static String getExoRebufferText() {
        ensureInitialized();
        return isAuto(PlayerSetting.EXO, PlaybackPerformanceCatalog.EXO_REBUFFER)
                ? "自动 · 1～15秒"
                : secondsText(ExoPerformanceSetting.getRebufferMs());
    }

    public static String getExoPrioritizeTimeText() {
        ensureInitialized();
        return isAuto(PlayerSetting.EXO, PlaybackPerformanceCatalog.EXO_PRIORITIZE_TIME)
                ? "自动 · 按资源"
                : onOff(ExoPerformanceSetting.isPrioritizeTime());
    }

    public static String getDetail() {
        ensureInitialized();
        return "配置：" + getProfileName()
                + "\n渲染：" + (PlayerSetting.getRender() == PlayerSetting.RENDER_SURFACE ? "SurfaceView" : "TextureView")
                + "\n轨道限制：" + onOff(isTrackLimitEnabled()) + "，自适应降级：" + onOff(isAdaptiveDowngradeEnabled())
                + "\n前向缓冲目标：" + getForwardBufferText() + "，内存缓冲上限：" + getMemoryBufferText() + "，已播放数据保留：" + getPlayedDataRetentionText()
                + bufferWatermarksText()
                + playbackDiskCacheDetailText()
                + preloadDetailText()
                + "\nMediaCodec异步：" + onOff(isCodecAsyncQueueingEnabled()) + "，动态调度：" + onOff(isDynamicSchedulingEnabled())
                + "\n解码耗时推进：" + onOff(isVideoDurationProgressEnabled()) + "，输入丢帧阈值：" + onOff(isLateDropInputEnabled())
                + "\nDV7处理：" + getDv7HandlingText()
                + "\n只加载选中轨道：" + onOff(isLoadOnlySelectedTracksEnabled()) + "，Surface固定尺寸：" + onOff(isSurfaceFixedSizeEnabled())
                + "\n动态网络保护：" + ExoPerformanceSetting.getNetworkProtectionText()
                + "\n音频直通：" + onOff(PlayerSetting.isAudioPassThrough()) + "，AAC优先：" + onOff(PlayerSetting.isPreferAAC())
                + "\n视频软解优先：" + onOff(PlayerSetting.isVideoPrefer()) + "，音频软解优先：" + onOff(PlayerSetting.isAudioPrefer())
                + "\n软解降负载：" + onOff(isSoftVideoTuneEnabled());
    }

    private static void putRecommendedFlags() {
        put(KEY_CODEC_ASYNC_QUEUEING, true);
        put(KEY_DYNAMIC_SCHEDULING, true);
        put(KEY_VIDEO_DURATION_PROGRESS, true);
        put(KEY_LATE_DROP_INPUT, true);
        put(KEY_TRACK_LIMIT, true);
        put(KEY_ADAPTIVE_DOWNGRADE, true);
        put(KEY_LOAD_ONLY_SELECTED_TRACKS, true);
        put(KEY_SURFACE_FIXED_SIZE, true);
        put(KEY_DECODER_FALLBACK, true);
        put(KEY_DV7_HANDLING, DV7_HANDLING_P81);
        put(KEY_SOFT_VIDEO_TUNE, true);
        put(KEY_HIGH_BUFFER, true);
        put(KEY_BANDWIDTH_METER, true);
    }

    private static int clampProfile(int profile) {
        return profile == PROFILE_COMPATIBLE || profile == PROFILE_CUSTOM || profile == PROFILE_LIGHTWEIGHT || profile == PROFILE_AUTO ? profile : PROFILE_RECOMMENDED;
    }

    private static int clampDv7Handling(int mode) {
        return mode == DV7_HANDLING_HDR10 ? DV7_HANDLING_HDR10 : DV7_HANDLING_P81;
    }

    private static void put(String key, boolean value) {
        Prefers.put(key, value);
    }

    private static void put(String key, int value) {
        Prefers.put(key, value);
    }

    private static void putCustom(String key, boolean value, String optionId) {
        ensureInitialized();
        Prefers.put(key, value);
        markOverride(optionId);
    }

    private static void putCustom(String key, int value, String optionId) {
        ensureInitialized();
        Prefers.put(key, value);
        markOverride(optionId);
    }

    private static int displayProfile(int kernel, String optionId) {
        int profile = getProfile(kernel);
        return profile == PROFILE_AUTO && isOverridden(kernel, optionId)
                ? PROFILE_CUSTOM : profile;
    }

    private static Set<String> getOverrides(int kernel) {
        try {
            Set<String> stored = Prefers.getPrefers().getStringSet(
                    overrideKey(kernel), Collections.emptySet());
            return stored == null ? new HashSet<>() : new HashSet<>(stored);
        } catch (ClassCastException ignored) {
            return new HashSet<>();
        }
    }

    private static String overrideKey(int kernel) {
        return switch (PlayerSetting.sanitizePlayer(kernel)) {
            case PlayerSetting.MPV -> KEY_AUTO_OVERRIDES_MPV;
            case PlayerSetting.IJK -> KEY_AUTO_OVERRIDES_IJK;
            default -> KEY_AUTO_OVERRIDES_EXO;
        };
    }

    private static void migrateProfiles() {
        if (Prefers.getBoolean(KEY_PROFILE_MIGRATED)) return;
        int oldProfile = clampProfile(Prefers.getInt(KEY_PROFILE, PROFILE_RECOMMENDED));
        Prefers.put(KEY_PROFILE_EXO, oldProfile);
        Prefers.put(KEY_PROFILE_MPV, oldProfile);
        Prefers.put(KEY_PROFILE_IJK, oldProfile);
        applyKernelSpecificPreset(PlayerSetting.EXO, oldProfile);
        applyKernelSpecificPreset(PlayerSetting.MPV, oldProfile);
        applyKernelSpecificPreset(PlayerSetting.IJK, oldProfile);
        Prefers.put(KEY_PROFILE_MIGRATED, true);
    }

    private static void migrateBufferWatermarks() {
        if (Prefers.getBoolean(KEY_BUFFER_WATERMARKS_MIGRATED)) return;
        int exoProfile = clampProfile(Prefers.getInt(profileKey(PlayerSetting.EXO), PROFILE_RECOMMENDED));
        int mpvProfile = clampProfile(Prefers.getInt(profileKey(PlayerSetting.MPV), PROFILE_RECOMMENDED));
        if (exoProfile != PROFILE_CUSTOM) ExoPerformanceSetting.applyRebufferPreset(exoProfile);
        if (mpvProfile != PROFILE_CUSTOM) MpvPerformanceSetting.applyRebufferPreset(mpvProfile);
        Prefers.put(KEY_BUFFER_WATERMARKS_MIGRATED, true);
    }

    private static void migrateExoSizePriority() {
        if (Prefers.getBoolean(KEY_EXO_SIZE_PRIORITY_MIGRATED)) return;
        int exoProfile = clampProfile(Prefers.getInt(profileKey(PlayerSetting.EXO), PROFILE_RECOMMENDED));
        if (shouldMigrateExoSizePriority(exoProfile)) ExoPerformanceSetting.applyPrioritizeTimePreset(exoProfile);
        Prefers.put(KEY_EXO_SIZE_PRIORITY_MIGRATED, true);
    }

    static boolean shouldMigrateExoSizePriority(int profile) {
        return clampProfile(profile) != PROFILE_CUSTOM;
    }

    private static void migratePreloadDefaults() {
        if (Prefers.getBoolean(KEY_PRELOAD_DEFAULTS_MIGRATED)) return;
        for (int kernel : new int[]{PlayerSetting.EXO, PlayerSetting.MPV, PlayerSetting.IJK}) {
            int profile = clampProfile(Prefers.getInt(profileKey(kernel), PROFILE_RECOMMENDED));
            if (shouldMigratePreloadDefaults(profile)) KernelPerformanceSetting.applyPreloadPreset(kernel, profile);
        }
        Prefers.put(KEY_PRELOAD_DEFAULTS_MIGRATED, true);
    }

    static boolean shouldMigratePreloadDefaults(int profile) {
        return clampProfile(profile) != PROFILE_CUSTOM;
    }

    private static void migrateExoLoadControl() {
        if (Prefers.getBoolean(KEY_EXO_LOAD_CONTROL_MIGRATED)) return;
        int profile = clampProfile(Prefers.getInt(profileKey(PlayerSetting.EXO), PROFILE_RECOMMENDED));
        if (shouldMigrateExoLoadControl(profile)) {
            KernelPerformanceSetting.applyExoLoadControlPreset(profile);
            ExoPerformanceSetting.applyPrioritizeTimePreset(profile);
        }
        Prefers.put(KEY_EXO_LOAD_CONTROL_MIGRATED, true);
    }

    static boolean shouldMigrateExoLoadControl(int profile) {
        return clampProfile(profile) != PROFILE_CUSTOM;
    }

    private static void migrateExoBackBuffer() {
        if (Prefers.getBoolean(KEY_EXO_BACK_BUFFER_MIGRATED)) return;
        int profile = clampProfile(Prefers.getInt(profileKey(PlayerSetting.EXO), PROFILE_RECOMMENDED));
        if (shouldMigrateExoBackBuffer(profile)) KernelPerformanceSetting.applyExoBackBufferPreset(profile);
        Prefers.put(KEY_EXO_BACK_BUFFER_MIGRATED, true);
    }

    static boolean shouldMigrateExoBackBuffer(int profile) {
        return clampProfile(profile) != PROFILE_CUSTOM;
    }

    private static void migrateExoRebuffer() {
        if (Prefers.getBoolean(KEY_EXO_REBUFFER_MIGRATED)) return;
        int profile = clampProfile(Prefers.getInt(profileKey(PlayerSetting.EXO), PROFILE_RECOMMENDED));
        if (shouldMigrateExoRebuffer(profile)) ExoPerformanceSetting.applyRebufferPreset(profile);
        Prefers.put(KEY_EXO_REBUFFER_MIGRATED, true);
    }

    static boolean shouldMigrateExoRebuffer(int profile) {
        return clampProfile(profile) != PROFILE_CUSTOM;
    }

    private static void migrateMpvRebuffer() {
        if (Prefers.getBoolean(KEY_MPV_REBUFFER_MIGRATED)) return;
        int profile = clampProfile(Prefers.getInt(profileKey(PlayerSetting.MPV), PROFILE_RECOMMENDED));
        if (shouldMigrateMpvRebuffer(profile)) MpvPerformanceSetting.applyRebufferPreset(profile);
        Prefers.put(KEY_MPV_REBUFFER_MIGRATED, true);
    }

    static boolean shouldMigrateMpvRebuffer(int profile) {
        return clampProfile(profile) != PROFILE_CUSTOM;
    }

    private static void migrateMpvAutoBaseline() {
        if (Prefers.getBoolean(KEY_MPV_AUTO_BASELINE_MIGRATED)) return;
        int profile = clampProfile(Prefers.getInt(profileKey(PlayerSetting.MPV), PROFILE_RECOMMENDED));
        if (shouldMigrateMpvAutoBaseline(profile)) {
            KernelPerformanceSetting.applyMpvAutoBaselinePreset();
        }
        Prefers.put(KEY_MPV_AUTO_BASELINE_MIGRATED, true);
    }

    static boolean shouldMigrateMpvAutoBaseline(int profile) {
        return clampProfile(profile) == PROFILE_AUTO;
    }

    private static synchronized void migrateAudioPassthroughDefault() {
        if (Prefers.getBoolean(KEY_AUDIO_PASSTHROUGH_DEFAULT_MIGRATED)) return;
        boolean legacyExplicitOff = Prefers.getPrefers().contains("audio_pass_through")
                && !Prefers.getBoolean("audio_pass_through", true);
        for (int kernel : new int[]{PlayerSetting.EXO, PlayerSetting.MPV}) {
            int profile = rawProfile(kernel);
            boolean overridden = isOverridden(
                    kernel, PlaybackPerformanceCatalog.AUDIO_PASSTHROUGH);
            if (shouldMigrateAudioPassthroughDefault(
                    profile, overridden, legacyExplicitOff)) {
                KernelPerformanceSetting.putAudioPassThrough(kernel, true);
            }
        }
        Prefers.put(KEY_AUDIO_PASSTHROUGH_DEFAULT_MIGRATED, true);
    }

    static boolean shouldMigrateAudioPassthroughDefault(
            int profile,
            boolean overridden,
            boolean legacyExplicitOff) {
        int normalized = clampProfile(profile);
        if (legacyExplicitOff || normalized == PROFILE_CUSTOM) return false;
        return normalized != PROFILE_AUTO || !overridden;
    }

    private static synchronized void migrateRecommendedProfileMerge() {
        PlaybackProfileMergePolicy.Resolution resolution =
                profileMergeResolution();
        PlaybackProfileMergePolicy.State state = resolution.state();
        int[] kernels = {
                PlayerSetting.EXO, PlayerSetting.MPV, PlayerSetting.IJK};
        boolean[] migrate = new boolean[kernels.length];
        boolean profileChanged = false;
        if (resolution.mergeEnabled()) {
            for (int index = 0; index < kernels.length; index++) {
                int rawProfile = rawProfile(kernels[index]);
                migrate[index] = PlaybackProfileMergePolicy.shouldMigrate(
                        rawProfile, true);
                if (!migrate[index]) continue;
                state = state.withMigrated(mergeSlot(kernels[index]));
                profileChanged = true;
            }
        }
        int globalProfile = rawGlobalProfile();
        boolean migrateGlobal = resolution.mergeEnabled()
                && PlaybackProfileMergePolicy.shouldMigrate(
                globalProfile, true);
        if ((resolution.writeBack() || profileChanged)
                && !writeProfileMergeState(state)) {
            return;
        }
        if (!resolution.mergeEnabled()) {
            if (resolution.sourceValid()
                    && state.rolledBack()
                    && state.migratedMask() != 0) {
                completeRecommendedProfileRollback(state);
            }
            return;
        }
        for (int index = 0; index < kernels.length; index++) {
            if (!migrate[index]) continue;
            try {
                applyAutoProfile(kernels[index]);
                Prefers.put(profileKey(kernels[index]), PROFILE_AUTO);
            } catch (Throwable ignored) {
            }
        }
        if (migrateGlobal) {
            try {
                Prefers.put(KEY_PROFILE, PROFILE_AUTO);
            } catch (Throwable ignored) {
            }
        }
        if (PlaybackProfileAbSetting.isEnrolled()) {
            PlaybackProfileAbSetting.putEnrolled(false);
        }
    }

    private static synchronized void migrateAutoLightProfiles() {
        if (Prefers.getBoolean(KEY_PROFILE_AUTO_LIGHT_MIGRATED)) return;
        try {
            for (int kernel : new int[]{
                    PlayerSetting.EXO, PlayerSetting.MPV, PlayerSetting.IJK}) {
                int rawProfile = rawProfile(kernel);
                int targetProfile = PlaybackProfileMergePolicy.effectiveProfile(
                        rawProfile, true);
                switch (PlaybackProfileMergePolicy.consolidationAction(
                        rawProfile)) {
                    case APPLY_AUTO -> applyAutoProfile(kernel);
                    case APPLY_LIGHTWEIGHT -> applyLightweightProfile(kernel);
                    case KEEP -> {
                    }
                }
                Prefers.put(profileKey(kernel), targetProfile);
            }
            Prefers.put(KEY_PROFILE, rawProfile(PlayerSetting.getPlayer()));
            Prefers.put(KEY_PROFILE_AUTO_LIGHT_MIGRATED, true);
        } catch (Throwable ignored) {
            // Partial writes are safe: without the completion marker the
            // idempotent migration is retried on the next initialization.
        }
    }

    private static void completeRecommendedProfileRollback(
            PlaybackProfileMergePolicy.State state) {
        PlaybackProfileMergePolicy.State pending = state;
        for (int kernel : new int[]{
                PlayerSetting.EXO, PlayerSetting.MPV, PlayerSetting.IJK}) {
            PlaybackProfileMergePolicy.Slot slot = mergeSlot(kernel);
            if (!pending.wasMigrated(slot)) continue;
            int rawProfile = rawProfile(kernel);
            if (PlaybackProfileMergePolicy.shouldRestore(
                    pending, slot, rawProfile)) {
                try {
                    applyRecommendedProfile(kernel);
                    Prefers.put(profileKey(kernel), PROFILE_RECOMMENDED);
                    rawProfile = PROFILE_RECOMMENDED;
                } catch (Throwable ignored) {
                    continue;
                }
            }
            if (kernel == PlayerSetting.getPlayer()) {
                try {
                    Prefers.put(KEY_PROFILE, rawProfile);
                } catch (Throwable ignored) {
                    continue;
                }
            }
            PlaybackProfileMergePolicy.State completed =
                    pending.withoutMigrated(slot);
            if (!writeProfileMergeState(completed)) return;
            pending = completed;
        }
        try {
            Prefers.put(KEY_PROFILE, rawProfile(PlayerSetting.getPlayer()));
        } catch (Throwable ignored) {
        }
    }

    private static PlaybackProfileMergePolicy.Resolution
    profileMergeResolution() {
        Map<String, ?> values;
        try {
            values = Prefers.getPrefers().getAll();
        } catch (Throwable ignored) {
            return PlaybackProfileMergePolicy.resolve(
                    new PlaybackProfileMergePolicy.RawState(
                            "unavailable", null, null));
        }
        return PlaybackProfileMergePolicy.resolve(
                new PlaybackProfileMergePolicy.RawState(
                        values.get(KEY_PROFILE_MERGE_SCHEMA),
                        values.get(KEY_PROFILE_MERGE_ROLLED_BACK),
                        values.get(KEY_PROFILE_MERGE_MIGRATED_MASK)));
    }

    private static boolean writeProfileMergeState(
            PlaybackProfileMergePolicy.State state) {
        PlaybackProfileMergePolicy.State safe = state == null
                ? PlaybackProfileMergePolicy.State.legacyRollback() : state;
        try {
            SharedPreferences.Editor editor = Prefers.getPrefers().edit();
            editor.putInt(KEY_PROFILE_MERGE_SCHEMA,
                    PlaybackProfileMergePolicy.CURRENT_SCHEMA_VERSION);
            editor.putBoolean(KEY_PROFILE_MERGE_ROLLED_BACK,
                    safe.rolledBack());
            editor.putInt(KEY_PROFILE_MERGE_MIGRATED_MASK,
                    safe.migratedMask());
            editor.apply();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int rawProfile(int kernel) {
        return rawProfileValue(
                profileKey(PlayerSetting.sanitizePlayer(kernel)),
                rawGlobalProfile());
    }

    private static int rawGlobalProfile() {
        return rawProfileValue(KEY_PROFILE, PROFILE_AUTO);
    }

    private static int rawProfileValue(String key, int fallback) {
        try {
            Object value = Prefers.getPrefers().getAll().get(key);
            return value instanceof Number
                    ? clampProfile(((Number) value).intValue())
                    : clampProfile(fallback);
        } catch (Throwable ignored) {
            return clampProfile(fallback);
        }
    }

    private static PlaybackProfileMergePolicy.Slot mergeSlot(int kernel) {
        return switch (PlayerSetting.sanitizePlayer(kernel)) {
            case PlayerSetting.MPV -> PlaybackProfileMergePolicy.Slot.MPV;
            case PlayerSetting.IJK -> PlaybackProfileMergePolicy.Slot.IJK;
            default -> PlaybackProfileMergePolicy.Slot.EXO;
        };
    }

    private static void applyKernelSpecificPreset(int kernel, int profile) {
        if (kernel == PlayerSetting.EXO) {
            if (profile == PROFILE_COMPATIBLE) ExoPerformanceSetting.applyCompatible();
            else if (profile == PROFILE_LIGHTWEIGHT) ExoPerformanceSetting.applyLightweight();
            else if (profile == PROFILE_AUTO) ExoPerformanceSetting.applyAuto();
            else ExoPerformanceSetting.applyRecommended();
        } else if (kernel == PlayerSetting.MPV) {
            if (profile == PROFILE_COMPATIBLE) MpvPerformanceSetting.applyCompatible();
            else if (profile == PROFILE_LIGHTWEIGHT) MpvPerformanceSetting.applyLightweight();
            else if (profile == PROFILE_AUTO) MpvPerformanceSetting.applyAuto();
            else MpvPerformanceSetting.applyRecommended();
        } else {
            if (profile == PROFILE_COMPATIBLE) IjkPerformanceSetting.applyCompatible();
            else if (profile == PROFILE_LIGHTWEIGHT) IjkPerformanceSetting.applyLightweight();
            else IjkPerformanceSetting.applyRecommended();
        }
    }

    private static void putCurrentProfile(int profile) {
        int value = PlaybackProfileMergePolicy.effectiveProfile(
                profile, profileMergeResolution().mergeEnabled());
        Prefers.put(profileKey(PlayerSetting.getPlayer()), value);
        Prefers.put(KEY_PROFILE, value);
    }

    private static String profileKey(int kernel) {
        return switch (kernel) {
            case PlayerSetting.IJK -> KEY_PROFILE_IJK;
            case PlayerSetting.MPV -> KEY_PROFILE_MPV;
            default -> KEY_PROFILE_EXO;
        };
    }

    private static String onOff(boolean value) {
        return value ? "开" : "关";
    }

    static String forwardBufferText(int kernel, int profile, int level) {
        int normalized = Math.clamp(level, 1, 10);
        if (kernel == PlayerSetting.EXO) {
            if (profile == PROFILE_AUTO) return "自动 · 网络30～60秒";
            if (profile == PROFILE_LIGHTWEIGHT || profile == PROFILE_COMPATIBLE) return "15～30秒";
            if (profile == PROFILE_RECOMMENDED) return "30～60秒";
            int minBufferMs = 15_000 + (normalized - 1) * 15_000 / 9;
            return secondsRangeText(minBufferMs, minBufferMs * 2);
        }
        if (kernel == PlayerSetting.MPV) {
            int targetSeconds = Math.min(60, Math.max(15, normalized * 3));
            return (profile == PROFILE_AUTO ? "自动 · " : "") + "目标" + targetSeconds + "秒";
        }
        return "由读包内存和水位控制";
    }

    static String memoryBufferText(int kernel, int profile, int option) {
        if (profile == PROFILE_AUTO) {
            if (kernel == PlayerSetting.EXO) return "自动 · 16～192MB";
            if (kernel == PlayerSetting.MPV) return "自动 · 24～192MB";
        }
        return switch (Math.clamp(option, 0, 3)) {
            case 1 -> "64MB";
            case 2 -> "128MB";
            case 3 -> "256MB";
            default -> kernel == PlayerSetting.MPV
                    ? "默认64MB" : "设备自适应 · 最高256MB";
        };
    }

    static String ijkMemoryBufferText(int profile, int bufferMb) {
        return profile == PROFILE_AUTO
                ? "自动 · 读包4～15MB"
                : "读包" + Math.max(0, bufferMb) + "MB";
    }

    static String playedDataRetentionText(int kernel, int profile, int option) {
        if (kernel == PlayerSetting.IJK) return "无独立保留";
        if (kernel == PlayerSetting.MPV) {
            if (profile == PROFILE_AUTO) return "自动 · 0～64MB";
            return switch (Math.clamp(option, 0, 3)) {
                case 1 -> "少量 · 至少16MB";
                case 2 -> "中等 · 至少32MB";
                case 3 -> "与前向内存相同";
                default -> "关闭";
            };
        }
        return switch (Math.clamp(option, 0, 3)) {
            case 1 -> "15秒";
            case 2 -> "30秒";
            case 3 -> "60秒";
            default -> "关闭";
        };
    }

    static String playbackDiskCacheText(int option) {
        return switch (Math.clamp(option, 0, 4)) {
            case 1 -> "256MB";
            case 2 -> "512MB";
            case 3 -> "1GB";
            case 4 -> "2GB";
            default -> "128MB";
        };
    }

    private static String bufferWatermarksText() {
        return switch (PlayerSetting.getPlayer()) {
            case PlayerSetting.EXO -> "\n起播阈值：" + getExoStartBufferText() + "，重缓冲恢复：" + getExoRebufferText();
            case PlayerSetting.MPV -> "\n参数优先级：" + MpvPerformanceSetting.getOptionPriorityText() + "，重缓冲恢复：" + secondsText(MpvPerformanceSetting.getRebufferMs());
            default -> "";
        };
    }

    private static String playbackDiskCacheDetailText() {
        return PlayerSetting.getPlayer() == PlayerSetting.EXO
                ? "" : "\nHLS 磁盘缓存上限：" + getPlaybackDiskCacheText();
    }

    private static String preloadDetailText() {
        if (!isAuto()) {
            return "\n磁盘预载：" + onOff(PreloadSetting.isPreload()) + "，并发：" + PreloadSetting.getPreloadThreads() + "，磁盘配额：" + PreloadSetting.getPreloadSizeMb() + "MB，单次时长：" + PreloadSetting.getPreloadTimeSeconds() + "秒，向前目标：" + preloadAheadText();
        }
        return "\n磁盘预载：自动，并发：0～2，磁盘配额：" + PreloadSetting.getPreloadSizeMb() + "MB，单次时长：10～30秒，向前目标：" + preloadAheadText();
    }

    private static String preloadAheadText() {
        int seconds = PreloadSetting.getPreloadAheadSeconds();
        return seconds == PreloadSetting.WHOLE_MEDIA_AHEAD_SECONDS
                ? "整部" : seconds / 60 + "分钟";
    }

    private static String secondsText(int milliseconds) {
        return milliseconds % 1000 == 0 ? milliseconds / 1000 + "秒" : String.format(java.util.Locale.US, "%.1f秒", milliseconds / 1000f);
    }

    private static String secondsRangeText(int minimumMs, int maximumMs) {
        if (minimumMs % 1000 == 0 && maximumMs % 1000 == 0) {
            return minimumMs / 1000 + "～" + maximumMs / 1000 + "秒";
        }
        return secondsText(minimumMs) + "～" + secondsText(maximumMs);
    }
}
