package com.fongmi.android.tv.api.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/** Keeps site-health aggregation keyed by the stable site key rather than its display label. */
public class AdBlockStatsStoreSourceTest {

    @Test
    public void aggregateUsesStableSiteKeyAndRetainsDomainFallback() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/fongmi/android/tv/api/config/AdBlockStatsStore.java"));

        assertTrue(source.contains("stats.incrementBlocks(identity.siteKey(), pipeline, ruleId, 1)"));
        assertTrue(source.contains("stats.incrementBlocks(identity.siteKey(), pipeline, entry.getKey(), count)"));
        assertTrue(source.contains("stats.incrementBlocks(identity.siteKey(), pipeline, \"hls.legacy-fallback\", safeFallbackCount)"));
        assertTrue(source.contains("fallbackKey = TextUtils.isEmpty(siteKeyOrDomain) ? siteDomain : siteKeyOrDomain"));
        assertFalse(source.contains("stats.incrementBlocks(identity.siteName()"));
    }
}
