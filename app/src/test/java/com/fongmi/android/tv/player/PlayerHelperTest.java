package com.fongmi.android.tv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerHelperTest {

    @Test
    public void resolveUa_prefersPlaybackSettingWithoutCallingFallback() {
        AtomicBoolean fallbackCalled = new AtomicBoolean(false);

        String ua = PlayerHelper.resolveUa("okhttp", () -> {
            fallbackCalled.set(true);
            return "fallback";
        });

        assertEquals("okhttp", ua);
        assertFalse(fallbackCalled.get());
    }

    @Test
    public void resolveUa_usesFallbackWhenPlaybackSettingEmpty() {
        assertEquals("fallback", PlayerHelper.resolveUa("", () -> "fallback"));
        assertEquals("fallback", PlayerHelper.resolveUa(null, () -> "fallback"));
    }
}
