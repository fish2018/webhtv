package com.fongmi.android.tv.bean;

import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class HlsRuleState {

    private HlsRuleState() {}

    public static String key(String origin, String sourceId, String ruleId) {
        return safe(origin) + ":" + hash(sourceId) + ":" + safe(ruleId);
    }

    public static boolean resolveEnabled(HlsAdRule rule, String stateKey, Map<String, Boolean> overrides) {
        if (rule == null) return false;
        Boolean override = overrides == null ? null : overrides.get(stateKey);
        return override == null ? rule.isEnabledByDefault() : override;
    }

    private static String safe(String value) { return value == null ? "" : value.replace(":", "_"); }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(safe(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(16);
            for (int i = 0; i < 8; i++) output.append(String.format("%02x", digest[i]));
            return output.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
