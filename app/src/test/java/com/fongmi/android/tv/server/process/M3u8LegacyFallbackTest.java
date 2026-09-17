package com.fongmi.android.tv.server.process;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/** Keeps the local HLS proxy from invoking unconfigured legacy ad heuristics. */
public class M3u8LegacyFallbackTest {

    @Test
    public void proxyUsesLegacyFallbackOnlyForConfiguredRules() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/fongmi/android/tv/server/process/M3u8.java"));

        assertFalse(source.contains("hlsRules(), true"));
        assertFalse(source.contains("rules, true"));
        assertTrue(source.contains("legacyFallback = !rules.isEmpty() && HlsRuleConfig.isLegacyFallbackEnabled()"));
    }
}
