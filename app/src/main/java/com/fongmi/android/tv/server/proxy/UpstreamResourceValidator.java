package com.fongmi.android.tv.server.proxy;

import java.util.Objects;

/**
 * Immutable identity chosen from upstream response validators for safe range reuse.
 */
public final class UpstreamResourceValidator {

    private final Type type;
    private final String value;

    private UpstreamResourceValidator(Type type, String value) {
        this.type = Objects.requireNonNull(type, "type");
        this.value = value;
    }

    public static UpstreamResourceValidator select(String etag, String lastModified) {
        String normalizedEtag = normalize(etag);
        if (isStrongEtag(normalizedEtag)) {
            return new UpstreamResourceValidator(Type.STRONG_ETAG, normalizedEtag);
        }
        String normalizedLastModified = normalize(lastModified);
        if (!normalizedLastModified.isEmpty()) {
            return new UpstreamResourceValidator(Type.LAST_MODIFIED, normalizedLastModified);
        }
        return new UpstreamResourceValidator(Type.NONE, null);
    }

    public Type type() {
        return type;
    }

    public String value() {
        return value;
    }

    public String ifRangeValue() {
        return value;
    }

    public boolean isUsable() {
        return type != Type.NONE;
    }

    public boolean isStrong() {
        return type == Type.STRONG_ETAG;
    }

    public boolean matches(String etag, String lastModified) {
        if (type == Type.NONE) return true;
        String candidate = type == Type.STRONG_ETAG ? normalize(etag) : normalize(lastModified);
        return value.equals(candidate);
    }

    private static boolean isStrongEtag(String value) {
        if (value.length() < 2 || value.regionMatches(true, 0, "W/", 0, 2)) return false;
        if (value.charAt(0) != 34 || value.charAt(value.length() - 1) != 34) return false;
        for (int i = 1; i < value.length() - 1; i++) {
            char character = value.charAt(i);
            if (character == 34 || character < 0x20 || character == 0x7f) return false;
        }
        return true;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        if (normalized.indexOf(13) >= 0 || normalized.indexOf(10) >= 0) return "";
        return normalized;
    }

    public enum Type {
        STRONG_ETAG,
        LAST_MODIFIED,
        NONE
    }
}