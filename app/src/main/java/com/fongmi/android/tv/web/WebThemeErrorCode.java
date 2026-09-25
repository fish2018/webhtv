package com.fongmi.android.tv.web;

/** Stable WebTheme error codes with aliases for the existing bridge wire format. */
public enum WebThemeErrorCode {
    PERMISSION_DENIED("PERMISSION_DENIED"),
    INVALID_ARGUMENT("INVALID_ARGUMENT"),
    SOURCE_CHANGED("SOURCE_CHANGED"),
    STALE_REFERENCE("INVALID_ARGUMENT"),
    PAGE_UNAVAILABLE("UNAVAILABLE"),
    NATIVE_FALLBACK("UNAVAILABLE"),
    RATE_LIMITED("BUSY"),
    RESPONSE_TOO_LARGE("RESPONSE_TOO_LARGE"),
    INVALID_REQUEST("INVALID_REQUEST"),
    REQUEST_FAILED("REQUEST_FAILED");

    private final String legacyCode;

    WebThemeErrorCode(String legacyCode) {
        this.legacyCode = legacyCode;
    }

    public String getCode() {
        return name();
    }

    public String getLegacyCode() {
        return legacyCode;
    }

    public static WebThemeErrorCode from(Throwable error) {
        String message = error == null ? "" : value(error.getMessage());
        WebThemeErrorCode explicit = explicit(message);
        if (explicit != null) return explicit;
        if (isStaleReference(message)) return STALE_REFERENCE;
        if (error instanceof SecurityException) return PERMISSION_DENIED;
        if (error instanceof IllegalArgumentException) return INVALID_ARGUMENT;
        if (error instanceof IllegalStateException) return PAGE_UNAVAILABLE;
        return REQUEST_FAILED;
    }

    private static boolean isStaleReference(String message) {
        return "Invalid playRef".equals(message)
                || (message.startsWith("Unknown ") && message.endsWith(" reference"));
    }

    private static WebThemeErrorCode explicit(String message) {
        for (WebThemeErrorCode candidate : values()) {
            if (candidate.getCode().equals(message)) return candidate;
        }
        if ("BUSY".equals(message)) return RATE_LIMITED;
        if ("UNAVAILABLE".equals(message)) return PAGE_UNAVAILABLE;
        return null;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
