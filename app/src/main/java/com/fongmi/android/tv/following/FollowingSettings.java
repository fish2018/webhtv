package com.fongmi.android.tv.following;

import com.github.catvod.utils.Prefers;

public final class FollowingSettings {

    public static final String ENABLED = "following_enabled";
    public static final String NOTIFICATIONS = "following_notifications";
    public static final String SOURCE_PROBE = "following_source_probe";
    public static final String SERVER_IMPORT = "following_server_import";
    public static final String SOURCE_PROBE_WHEN_UPDATE = "when_update";
    public static final String SOURCE_PROBE_MANUAL = "manual";
    public static final String SOURCE_PROBE_NEVER = "never";

    private FollowingSettings() {
    }

    public static boolean isEnabled() {
        return Prefers.getBoolean(ENABLED, false);
    }

    public static void setEnabled(boolean enabled) {
        Prefers.put(ENABLED, enabled);
    }

    public static boolean isNotificationsEnabled() {
        return Prefers.getBoolean(NOTIFICATIONS, false);
    }

    public static void setNotificationsEnabled(boolean enabled) {
        Prefers.put(NOTIFICATIONS, enabled);
    }

    public static String sourceProbeMode() {
        String value = Prefers.getString(SOURCE_PROBE, SOURCE_PROBE_WHEN_UPDATE);
        return value == null || value.isBlank() ? SOURCE_PROBE_WHEN_UPDATE : value;
    }

    public static void setSourceProbeMode(String mode) {
        if (SOURCE_PROBE_MANUAL.equals(mode) || SOURCE_PROBE_NEVER.equals(mode)) Prefers.put(SOURCE_PROBE, mode);
        else Prefers.put(SOURCE_PROBE, SOURCE_PROBE_WHEN_UPDATE);
    }

    public static boolean isServerImportEnabled() {
        return Prefers.getBoolean(SERVER_IMPORT, false);
    }

    public static void setServerImportEnabled(boolean enabled) {
        Prefers.put(SERVER_IMPORT, enabled);
    }

    public static boolean shouldProbe(boolean manual, boolean metadataChanged, FollowingSource source) {
        if (!isEnabled() || SOURCE_PROBE_NEVER.equals(sourceProbeMode())) return false;
        if (manual) return true;
        if (SOURCE_PROBE_MANUAL.equals(sourceProbeMode())) return source == null;
        return metadataChanged || source == null || source.lastProbeAt <= 0;
    }
}
