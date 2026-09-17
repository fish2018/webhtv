package com.fongmi.android.tv.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

final class WebThemeDataIsolation {

    static final String DEFAULT_PROFILE = "Default";
    static final String REMOTE_PROFILE_PREFIX = "webtheme_remote_";
    private static final int PROFILE_HASH_BYTES = 16;
    private static final DataProfile TRUSTED = new DataProfile(DEFAULT_PROFILE, false);

    private WebThemeDataIsolation() {
    }

    static DataProfile trustedProfile() {
        return TRUSTED;
    }

    static DataProfile profileFor(WebHomeTarget target) {
        if (target == null || !target.isRemoteGlobal()) return TRUSTED;
        String origin = target.getOriginRule();
        if (origin.isEmpty()) throw new IllegalArgumentException("Remote theme origin is unavailable");
        return new DataProfile(REMOTE_PROFILE_PREFIX + digest(origin), true);
    }

    static boolean requiresReplacement(DataProfile current, DataProfile desired) {
        return !Objects.equals(current, desired);
    }

    private static String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(PROFILE_HASH_BYTES * 2);
            for (int i = 0; i < PROFILE_HASH_BYTES; i++) {
                result.append(Character.forDigit((hash[i] >>> 4) & 0x0f, 16));
                result.append(Character.forDigit(hash[i] & 0x0f, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 unavailable", e);
        }
    }

    record DataProfile(String name, boolean isolated) {

        DataProfile {
            if (name == null || name.isEmpty()) throw new IllegalArgumentException("Profile name is required");
        }
    }
}
