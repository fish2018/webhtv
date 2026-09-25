package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WebThemeErrorCodeTest {

    @Test
    public void mapsExistingFailuresToStableCanonicalCodes() {
        assertEquals(WebThemeErrorCode.PERMISSION_DENIED,
                WebThemeErrorCode.from(new SecurityException("denied")));
        assertEquals(WebThemeErrorCode.INVALID_ARGUMENT,
                WebThemeErrorCode.from(new IllegalArgumentException("bad payload")));
        assertEquals(WebThemeErrorCode.STALE_REFERENCE,
                WebThemeErrorCode.from(new SecurityException("Unknown VOD reference")));
        assertEquals(WebThemeErrorCode.STALE_REFERENCE,
                WebThemeErrorCode.from(new IllegalArgumentException("Unknown image reference")));
        assertEquals(WebThemeErrorCode.STALE_REFERENCE,
                WebThemeErrorCode.from(new SecurityException("Invalid playRef")));
        assertEquals(WebThemeErrorCode.SOURCE_CHANGED,
                WebThemeErrorCode.from(new IllegalStateException("SOURCE_CHANGED")));
        assertEquals(WebThemeErrorCode.RESPONSE_TOO_LARGE,
                WebThemeErrorCode.from(new IllegalStateException("RESPONSE_TOO_LARGE")));
        assertEquals(WebThemeErrorCode.RATE_LIMITED,
                WebThemeErrorCode.from(new IllegalStateException("RATE_LIMITED")));
        assertEquals(WebThemeErrorCode.PAGE_UNAVAILABLE,
                WebThemeErrorCode.from(new IllegalStateException("not ready")));
        assertEquals(WebThemeErrorCode.REQUEST_FAILED,
                WebThemeErrorCode.from(new RuntimeException("failed")));
    }

    @Test
    public void keepsLegacyWireMessagesWhileExposingCanonicalCodes() {
        assertEquals("RATE_LIMITED", WebThemeErrorCode.RATE_LIMITED.getCode());
        assertEquals("BUSY", WebThemeErrorCode.RATE_LIMITED.getLegacyCode());
        assertEquals("PAGE_UNAVAILABLE", WebThemeErrorCode.PAGE_UNAVAILABLE.getCode());
        assertEquals("UNAVAILABLE", WebThemeErrorCode.PAGE_UNAVAILABLE.getLegacyCode());
        assertEquals("STALE_REFERENCE", WebThemeErrorCode.STALE_REFERENCE.getCode());
        assertEquals("INVALID_ARGUMENT", WebThemeErrorCode.STALE_REFERENCE.getLegacyCode());
        assertEquals("NATIVE_FALLBACK", WebThemeErrorCode.NATIVE_FALLBACK.getCode());
        assertEquals("UNAVAILABLE", WebThemeErrorCode.NATIVE_FALLBACK.getLegacyCode());
    }
}
