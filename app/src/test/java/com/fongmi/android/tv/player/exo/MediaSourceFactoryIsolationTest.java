package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.okhttp.OkHttpDataSource;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;

public class MediaSourceFactoryIsolationTest {

    @Test
    public void itemFactories_keepHeadersAndUserAgentSnapshotsWithoutReplacingClient() throws Exception {
        Call.Factory client = request -> { throw new AssertionError("No network expected"); };
        Map<String, String> firstHeaders = new LinkedHashMap<>();
        firstHeaders.put("Cookie", "sid=first");
        firstHeaders.put("Referer", "https://example.test/first");
        firstHeaders.put("Authorization", "Bearer first");
        firstHeaders.put("user-agent", " First UA ");
        OkHttpDataSource.Factory first = MediaSourceFactory.createHttpDataSourceFactory(client, firstHeaders);
        OkHttpDataSource.Factory second = MediaSourceFactory.createHttpDataSourceFactory(client,
                Map.of("Cookie", "sid=second", "Referer", "https://example.test/second",
                        "Authorization", "Bearer second", "User-Agent", "Second UA"));
        firstHeaders.put("Cookie", "sid=mutated");

        assertEquals("sid=first", headers(first).get("Cookie"));
        assertEquals("https://example.test/first", headers(first).get("Referer"));
        assertEquals("Bearer first", headers(first).get("Authorization"));
        assertEquals("First UA", field(first, "userAgent"));
        assertFalse(headers(first).containsKey("user-agent"));
        assertEquals("sid=second", headers(second).get("Cookie"));
        assertEquals("Second UA", field(second, "userAgent"));
        // Do not substitute a NO_COOKIES client, drop the proxy or alter redirect handling.
        assertSame(client, field(first, "callFactory"));
        assertSame(client, field(second, "callFactory"));
        assertEquals(" First UA ", firstHeaders.get("user-agent"));
    }

    @Test
    public void absentOrBlankUserAgent_preservesUnderlyingClientDefault() throws Exception {
        Call.Factory client = request -> { throw new AssertionError("No network expected"); };
        OkHttpDataSource.Factory factory = MediaSourceFactory.createHttpDataSourceFactory(
                client, Map.of("USER-AGENT", " "));

        assertNull(field(factory, "userAgent"));
        assertEquals(Map.of(), headers(factory));
    }

    @Test
    public void preloadAndForeground_shareIdentityAndKeepCredentialScopesDistinct() {
        AtomicReference<String> accessedKey = new AtomicReference<>();
        AtomicBoolean released = new AtomicBoolean();
        String url = "https://example.test/segment.ts";
        Map<String, String> headers = new LinkedHashMap<>(Map.of("Authorization", "Bearer first"));
        String expected = MediaSourceFactory.cacheKey(headers, url);
        Cache delegate = recordingCache(accessedKey, released, Set.of(expected, "unrelated"));
        Cache first = MediaSourceFactory.scopedCache(delegate, headers);
        Cache second = MediaSourceFactory.scopedCache(delegate, Map.of("Authorization", "Bearer second"));
        headers.put("Authorization", "Bearer mutated");

        first.isCached(url, 0, 1);
        assertEquals(expected, accessedKey.get());
        assertEquals(Set.of(url), first.getKeys());
        second.isCached(url, 0, 1);
        assertNotEquals(expected, accessedKey.get());
        first.release();
        assertFalse(released.get());
    }

    @Test
    public void preloadWithoutHeaders_keepsLegacyCacheKeysAndEnumeration() {
        AtomicReference<String> accessedKey = new AtomicReference<>();
        String url = "https://example.test/video.mp4";
        Cache delegate = recordingCache(accessedKey, new AtomicBoolean(), Set.of(url));
        Cache scoped = MediaSourceFactory.scopedCache(delegate, Map.of());

        scoped.isCached(url, 0, 1);
        assertEquals(url, accessedKey.get());
        assertEquals(Set.of(url), scoped.getKeys());
        assertEquals(MediaSourceFactory.cacheKey(Map.of(), url), accessedKey.get());
    }

    private static Cache recordingCache(
            AtomicReference<String> key, AtomicBoolean released, Set<String> keys) {
        return (Cache) Proxy.newProxyInstance(Cache.class.getClassLoader(), new Class<?>[] {Cache.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isCached":
                            key.set((String) args[0]);
                            return keys.contains(key.get());
                        case "getKeys":
                            return keys;
                        case "release":
                            released.set(true);
                            return null;
                        default:
                            throw new AssertionError("Unexpected cache operation: " + method.getName());
                    }
                });
    }

    private static Map<String, String> headers(OkHttpDataSource.Factory factory) throws Exception {
        return ((HttpDataSource.RequestProperties) field(factory, "defaultRequestProperties")).getSnapshot();
    }

    private static Object field(OkHttpDataSource.Factory factory, String name) throws Exception {
        Field field = OkHttpDataSource.Factory.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(factory);
    }
}
