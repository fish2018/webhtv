package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.api.config.SubscriptionTmdbCredentialStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbConfigEffectiveTest {

    private static final String SOURCE_KEY = "0123456789abcdef0123456789abcdef";

    @Before
    @After
    public void clearStore() {
        SubscriptionTmdbCredentialStore.clear();
    }

    @Test
    public void userCredentialAlwaysWinsAndTransientKeyIsNeverPersisted() {
        TmdbConfig configured = TmdbConfig.objectFrom("{\"apiBase\":\"https://mirror.example.com/3\",\"apiKey\":\"user-key-0123456789\",\"language\":\"en-US\"}");
        SubscriptionTmdbCredentialStore.Scope scope = SubscriptionTmdbCredentialStore.beginSubscription(1, "https://source.example/config", "test");
        assertTrue(SubscriptionTmdbCredentialStore.accept(SOURCE_KEY, 1, "https://source.example/config", scope.getEpoch(), "site", "vod"));

        TmdbConfig effective = TmdbConfig.effective(configured, SubscriptionTmdbCredentialStore.snapshot(scope));

        assertEquals("user-key-0123456789", effective.getApiKey());
        assertEquals("https://mirror.example.com/3", effective.getApiBase());
        assertEquals(TmdbConfig.ORIGIN_USER, effective.getCredentialOrigin());
        assertFalse(effective.isTransientSubscriptionCredential());
        assertFalse(effective.toJson().contains(SOURCE_KEY));
    }

    @Test
    public void transientCredentialUsesOfficialBaseAndKeepsDisplayConfiguration() {
        TmdbConfig configured = TmdbConfig.objectFrom("{\"apiBase\":\"https://mirror.example.com/3\",\"language\":\"en-US\",\"imageBase\":\"https://images.example.com/t/p/w342\"}");
        SubscriptionTmdbCredentialStore.Scope scope = SubscriptionTmdbCredentialStore.beginSubscription(2, "https://source.example/config", "test");
        assertTrue(SubscriptionTmdbCredentialStore.accept(SOURCE_KEY, 2, "https://source.example/config", scope.getEpoch(), "site", "vod"));

        TmdbConfig effective = TmdbConfig.effective(configured, SubscriptionTmdbCredentialStore.snapshot(scope));

        assertEquals(SOURCE_KEY, effective.getApiKey());
        assertEquals("https://api.tmdb.org/3", effective.getApiBase());
        assertEquals("en-US", effective.getLanguage());
        assertEquals("https://images.example.com/t/p/w342", effective.getImageBase());
        assertTrue(effective.isTransientSubscriptionCredential());
        assertEquals(scope.getEpoch(), effective.getCredentialScopeEpoch());
        assertFalse(effective.getCredentialSubscriptionKey().isEmpty());
        assertFalse(effective.toJson().contains(SOURCE_KEY));
    }

    @Test
    public void staleOrMissingSnapshotProducesNoCredential() {
        TmdbConfig configured = TmdbConfig.objectFrom("{\"language\":\"zh-CN\"}");
        SubscriptionTmdbCredentialStore.Scope scope = SubscriptionTmdbCredentialStore.beginSubscription(3, "https://source.example/config", "test");
        assertTrue(SubscriptionTmdbCredentialStore.accept(SOURCE_KEY, 3, "https://source.example/config", scope.getEpoch(), "site", "vod"));
        SubscriptionTmdbCredentialStore.clear();

        TmdbConfig effective = TmdbConfig.effective(configured, SubscriptionTmdbCredentialStore.snapshot(scope));

        assertFalse(effective.isReady());
        assertEquals(TmdbConfig.ORIGIN_USER, effective.getCredentialOrigin());
        assertFalse(effective.toJson().contains(SOURCE_KEY));
    }

    @Test
    public void officialApiBaseWhitelistRejectsCredentialCaptureSurfaces() {
        assertTrue(TmdbConfig.isOfficialApiBase("https://api.tmdb.org/3"));
        assertTrue(TmdbConfig.isOfficialApiBase("https://api.themoviedb.org/3/"));
        assertFalse(TmdbConfig.isOfficialApiBase("http://api.tmdb.org/3"));
        assertFalse(TmdbConfig.isOfficialApiBase("https://api.tmdb.org:8443/3"));
        assertFalse(TmdbConfig.isOfficialApiBase("https://mirror.example.com/3"));
        assertFalse(TmdbConfig.isOfficialApiBase("https://api.tmdb.org/3/proxy"));
        assertFalse(TmdbConfig.isOfficialApiBase("https://user@api.tmdb.org/3"));
    }
    @Test
    public void transientCredentialDisablesConfiguredProxy() {
        TmdbConfig configured = TmdbConfig.objectFrom("{\"proxyBase\":\"https://mirror.example.com\",\"language\":\"en-US\"}");
        SubscriptionTmdbCredentialStore.Scope scope = SubscriptionTmdbCredentialStore.beginSubscription(4, "https://source.example/config", "test");
        assertTrue(SubscriptionTmdbCredentialStore.accept(SOURCE_KEY, 4, "https://source.example/config", scope.getEpoch(), "site", "vod"));

        TmdbConfig effective = TmdbConfig.effective(configured, SubscriptionTmdbCredentialStore.snapshot(scope));

        assertEquals("https://api.tmdb.org/3", effective.getApiBase());
        assertFalse(effective.isProxyEnabled());
    }

}
