package com.fongmi.android.tv.player.mpv;

import android.os.Build;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.setting.MpvPerformanceSetting;
import com.github.catvod.utils.Prefers;

import java.util.Locale;

/** Selects the low-power Vulkan import path while remembering proven driver failures. */
public final class MpvVulkanBackendPolicy {

    public static final String OPTION = "android-vulkan-aimagereader-backend";
    public static final String AUTO = "auto";
    public static final String DIRECT = "direct";
    public static final String LEGACY = "legacy";
    public static final String STABLE = "stable";
    private static final String KEY_STABLE_ENVIRONMENT = "mpv_vulkan_stable_environment_v1";

    private MpvVulkanBackendPolicy() {
    }

    public static String configuredBackend() {
        return resolveConfigured(MpvPerformanceSetting.getVulkanBackendOption(),
                MpvConfigStore.getOptionValue(OPTION),
                MpvPerformanceSetting.isPerformancePriority());
    }

    public static String appOverride() {
        return normalize(MpvPerformanceSetting.getVulkanBackendOption());
    }

    public static boolean isAutomaticConfig() {
        String value = configuredBackend();
        return value.isEmpty() || AUTO.equals(value);
    }

    public static String automaticOverride() {
        return isStableRemembered() ? STABLE : "";
    }

    public static void rememberDirectFailure() {
        Prefers.put(KEY_STABLE_ENVIRONMENT, currentEnvironment());
    }

    public static boolean isStableRemembered() {
        return currentEnvironment().equals(Prefers.getString(KEY_STABLE_ENVIRONMENT));
    }

    static String resolve(String configured, boolean stableRemembered) {
        String value = normalize(configured);
        if (!value.isEmpty() && !AUTO.equals(value)) return value;
        return stableRemembered ? STABLE : AUTO;
    }

    static String resolveConfigured(String appSetting, String configSetting,
                                    boolean performancePriority) {
        String app = normalize(appSetting);
        String config = normalize(configSetting);
        if (performancePriority) return app.isEmpty() ? config : app;
        return config.isEmpty() ? app : config;
    }

    private static String currentEnvironment() {
        return safe(Build.FINGERPRINT) + '|' + safe(Build.HARDWARE) + '|'
                + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? safe(Build.SOC_MODEL) : "")
                + '|' + Build.VERSION.SDK_INT + '|' + BuildConfig.VERSION_CODE;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case AUTO, DIRECT, LEGACY, STABLE, "compute", "fragment" -> normalized;
            default -> "";
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
