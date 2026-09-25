package com.fongmi.android.tv.bean;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbConfigImageHostTest {

    @Test
    public void imageHostRoundTrip_keepsCustomHttpsHost() {
        TmdbConfig config = TmdbConfig.objectFrom("{\"imageBase\":\"https://cdn.example.com\",\"apiKey\":\"test-key\"}");

        assertEquals("https://cdn.example.com", config.getImageHost());
        assertEquals("https://cdn.example.com/t/p/w342", config.getImageBase());
        assertEquals("https://cdn.example.com/t/p/w780", config.getBackdropBase());
        assertEquals("https://cdn.example.com", TmdbConfig.objectFrom(config.toJson()).getImageHost());
    }

    @Test
    public void imageHostRoundTrip_addsSchemeForBareDomain() {
        TmdbConfig config = TmdbConfig.objectFrom("{\"imageBase\":\"cdn.example.com\",\"apiKey\":\"test-key\"}");

        assertEquals("https://cdn.example.com", config.getImageHost());
        assertEquals("https://cdn.example.com/t/p/w342", config.getImageBase());
        assertEquals("https://cdn.example.com", TmdbConfig.objectFrom(config.toJson()).getImageHost());
    }

    @Test
    public void imageHostRoundTrip_keepsPathPrefixHosts() {
        TmdbConfig config = TmdbConfig.objectFrom("{\"imageBase\":\"https://proxy.example.com/tmdb\",\"apiKey\":\"test-key\"}");

        assertEquals("https://proxy.example.com/tmdb", config.getImageHost());
        assertEquals("https://proxy.example.com/tmdb/t/p/w342", config.getImageBase());
        assertEquals("https://proxy.example.com/tmdb", TmdbConfig.objectFrom(config.toJson()).getImageHost());
    }

    @Test
    public void imageHost_defaultWhenEmpty() {
        TmdbConfig config = TmdbConfig.objectFrom("{\"apiKey\":\"test-key\"}");

        assertEquals("https://images.tmdb.org", config.getImageHost());
        assertEquals("https://images.tmdb.org/t/p/w342", config.getImageBase());
    }

    @Test
    public void imageHost_stripsKnownTmdbSizesIncludingW185() {
        TmdbConfig config = TmdbConfig.objectFrom("{\"imageBase\":\"https://cdn.example.com/t/p/w185\",\"apiKey\":\"test-key\"}");

        assertEquals("https://cdn.example.com", config.getImageHost());
        assertEquals("https://cdn.example.com/t/p/w342", config.getImageBase());
    }

    @Test
    public void apiHostRoundTrip_keepsCustomBareDomain() {
        TmdbConfig config = TmdbConfig.objectFrom("{\"apiBase\":\"api.example.com\",\"apiKey\":\"test-key\"}");

        assertEquals("https://api.example.com", config.getApiHost());
        assertEquals("https://api.example.com/3", config.getApiBase());
        assertEquals("https://api.example.com", TmdbConfig.objectFrom(config.toJson()).getApiHost());
    }

    @Test
    public void toJson_preservesCustomImageHostForDialogReload() {
        String saved = TmdbConfig.objectFrom("{\"imageBase\":\"https://img.mirror.test\",\"apiKey\":\"k\",\"language\":\"zh-CN\"}").toJson();
        TmdbConfig reloaded = TmdbConfig.objectFrom(saved);

        assertEquals("https://img.mirror.test", reloaded.getImageHost());
        assertTrue(saved.contains("img.mirror.test"));
    }
    @Test
    public void proxyBaseOverridesApiRequestBaseWithoutChangingConfiguredBase() {
        TmdbConfig config = TmdbConfig.objectFrom("{\"apiBase\":\"https://api.tmdb.org/3\",\"proxyBase\":\"https://proxy.example.com/tmdb\",\"apiKey\":\"test-key\"}");

        assertEquals("https://proxy.example.com/tmdb/3", config.getApiBase());
        assertEquals("https://api.tmdb.org/3", config.getConfiguredApiBase());
        assertEquals("https://proxy.example.com/tmdb", config.getProxyBase());
        assertEquals("https://proxy.example.com/tmdb", config.getImageHost());
        assertEquals("https://images.tmdb.org", config.getConfiguredImageHost());
        assertTrue(config.isProxyEnabled());
    }

    @Test
    public void explicitImageDomainWinsOverProxyRoute() {
        TmdbConfig config = TmdbConfig.objectFrom("{\"apiBase\":\"https://api.tmdb.org/3\",\"proxyBase\":\"https://proxy.example.com\",\"imageBase\":\"https://cdn.example.com/t/p/w342\",\"apiKey\":\"test-key\"}");

        assertEquals("https://proxy.example.com/3", config.getApiBase());
        assertEquals("https://cdn.example.com/t/p/w342", config.getImageBase());
        assertEquals("https://cdn.example.com", config.getImageHost());
    }

    @Test
    public void customImageAndAutoRoutesArePersistedAndResolved() {
        TmdbConfig custom = TmdbConfig.objectFrom("{\"apiBase\":\"https://custom-api.example/3\",\"imageBase\":\"https://custom-image.example/t/p/w342\",\"apiKey\":\"k\"}");
        assertEquals("https://custom-api.example/3", custom.getApiBase());
        assertEquals("https://custom-image.example/t/p/w342", custom.getImageBase());

        TmdbConfig auto = TmdbConfig.objectFrom("{\"apiBase\":\"https://api.tmdb.org/3\",\"apiAuto\":true,\"imageBase\":\"https://images.tmdb.org/t/p/w342\",\"imageAuto\":true,\"apiKey\":\"k\"}");
        assertTrue(auto.isApiAuto());
        assertTrue(auto.isImageAuto());
        assertEquals("https://api.tmdb.org/3", auto.getApiBase());
        assertTrue(auto.getImageBase().contains("/t/p/w342"));
    }

    @Test
    public void legacyOfficialRoutesArePresentedAsDefaultAutoCandidates() {
        TmdbConfig config = TmdbConfig.objectFrom("{\"apiBase\":\"https://api.tmdb.org/3\",\"imageBase\":\"https://images.tmdb.org/t/p/w342\",\"apiKey\":\"k\"}");

        assertTrue(config.isApiRouteDefault());
        assertTrue(config.isImageRouteDefault());
        assertTrue(config.isApiAuto());
        assertTrue(config.isImageAuto());
    }

    @Test
    public void explicitOfficialDirectRouteStaysDirectAfterNewSave() {
        TmdbConfig config = TmdbConfig.objectFrom("{\"apiBase\":\"https://api.tmdb.org/3\",\"apiAuto\":false,\"apiRouteConfigured\":true,\"apiRouteMode\":\"direct\",\"imageBase\":\"https://images.tmdb.org/t/p/w342\",\"imageAuto\":false,\"imageRouteConfigured\":true,\"imageRouteMode\":\"direct\",\"apiKey\":\"k\"}");

        assertFalse(config.isApiAuto());
        assertFalse(config.isImageAuto());
    }

}
