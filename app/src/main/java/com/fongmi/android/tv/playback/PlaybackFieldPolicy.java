package com.fongmi.android.tv.playback;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class PlaybackFieldPolicy {

    private static final Set<String> PROTOCOL = set("schema", "event", "eventId", "timestamp", "sessionId", "dedupeKey", "scope", "deletedAt");
    private static final Set<String> OBJECT = set("cid", "configKey", "configName", "historyKey", "siteKey", "siteName", "vodId", "vodName", "vodPic", "flag", "sourceBindingKey", "episodeName", "mediaType", "tmdbId", "seasonNumber", "tmdbEpisodeNumber");
    private static final Set<String> PROGRESS = set("state", "positionMs", "durationMs", "progress", "speed", "speedOverride", "completed");
    private static final Set<String> STANDARD = set("appVersion", "client");
    private static final Set<String> FULL = set("episodeUrl", "episodeIndex", "clientKey");
    private static final Set<String> OPTIONAL = set("episodeUrl", "episodeIndex", "appVersion", "client", "clientKey");
    private static final Set<String> CUSTOM_SAFE = customSafe();

    private final Set<String> fields;
    private final boolean hashHistoryKey;

    private PlaybackFieldPolicy(Set<String> fields, boolean hashHistoryKey) {
        this.fields = fields;
        this.hashHistoryKey = hashHistoryKey;
    }

    public static PlaybackFieldPolicy apiSafe() {
        Set<String> fields = base();
        fields.remove("event");
        fields.remove("eventId");
        return new PlaybackFieldPolicy(fields, false);
    }

    public static PlaybackFieldPolicy webhook(WebhookConfig config) {
        String preset = config == null ? WebhookConfig.PRESET_BASIC : WebhookConfig.normalizePreset(config.fieldPreset);
        if (WebhookConfig.PRESET_ANONYMOUS.equals(preset)) return anonymous();
        if (WebhookConfig.PRESET_CUSTOM.equals(preset)) return custom(config);
        Set<String> fields = base();
        if (WebhookConfig.PRESET_STANDARD.equals(preset)) fields.addAll(STANDARD);
        if (WebhookConfig.PRESET_FULL.equals(preset)) {
            fields.addAll(STANDARD);
            fields.addAll(FULL);
        }
        return new PlaybackFieldPolicy(fields, false);
    }

    public boolean includes(String field) {
        return fields.contains(field);
    }

    public boolean hashHistoryKey() {
        return hashHistoryKey;
    }

    private static PlaybackFieldPolicy anonymous() {
        Set<String> fields = new LinkedHashSet<>();
        fields.addAll(PROTOCOL);
        fields.add("historyKey");
        fields.addAll(PROGRESS);
        return new PlaybackFieldPolicy(fields, true);
    }

    private static Set<String> base() {
        Set<String> fields = new LinkedHashSet<>();
        fields.addAll(PROTOCOL);
        fields.addAll(OBJECT);
        fields.addAll(PROGRESS);
        return fields;
    }

    private static PlaybackFieldPolicy custom(WebhookConfig config) {
        Set<String> fields = new LinkedHashSet<>(PROTOCOL);
        if (config == null || config.fields == null || config.fields.isEmpty()) {
            fields.addAll(OBJECT);
            fields.addAll(PROGRESS);
            return new PlaybackFieldPolicy(fields, false);
        }
        for (String field : config.fields) if (CUSTOM_SAFE.contains(field)) fields.add(field);
        return new PlaybackFieldPolicy(fields, false);
    }

    private static Set<String> customSafe() {
        Set<String> fields = new LinkedHashSet<>();
        fields.addAll(OBJECT);
        fields.addAll(PROGRESS);
        fields.addAll(OPTIONAL);
        return fields;
    }

    private static Set<String> set(String... fields) {
        return new LinkedHashSet<>(Arrays.asList(fields));
    }
}
