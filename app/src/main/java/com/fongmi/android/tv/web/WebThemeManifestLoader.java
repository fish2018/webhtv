package com.fongmi.android.tv.web;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.CookieJar;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class WebThemeManifestLoader {

    static final int MAX_CACHE_ENTRIES = 8;
    static final int MAX_VERSIONS_PER_ENTRY = 2;
    static final long CACHE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(15);

    private static final int MAX_ETAG_LENGTH = 256;
    private static final int REVISION_LENGTH = 64;
    private static final Object[] CACHE_LOCKS = createCacheLocks(16);
    private static final Map<String, CachedManifest> CACHE = new LinkedHashMap<>(8, 0.75f, true);
    private static final PersistentCache NO_PERSISTENT_CACHE = new PersistentCache() {
        @Override
        public StoredCache read(String cacheKey) {
            return null;
        }

        @Override
        public void write(String cacheKey, StoredCache stored) {
        }

        @Override
        public void remove(String cacheKey) {
        }
    };
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(CookieJar.NO_COOKIES)
            .proxy(Proxy.NO_PROXY)
            .authenticator(okhttp3.Authenticator.NONE)
            .proxyAuthenticator(okhttp3.Authenticator.NONE)
            .dns(WebThemeManifestLoader::lookupPublic)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build();

    enum CacheState {
        REFRESHED,
        CACHE_HIT,
        LAST_KNOWN_GOOD,
        ROLLED_BACK
    }

    enum RollbackAction {
        NONE,
        ROLLBACK,
        RETRY
    }

    record LoadResult(WebThemeManifest manifest, CacheState state, IOException refreshFailure,
            boolean activationPending, boolean rollbackAvailable, String revision) {
        LoadResult {
            revision = normalizeRevision(revision);
        }

        boolean usedLastKnownGood() {
            return state == CacheState.LAST_KNOWN_GOOD;
        }

        boolean usedRollback() {
            return state == CacheState.ROLLED_BACK;
        }
    }

    record StoredManifest(String json, String etag, long validatedAt) {
        StoredManifest {
            json = json == null ? "" : json;
            etag = normalizeEtag(etag);
        }

        boolean isFresh(long now) {
            return validatedAt > 0 && now >= validatedAt && now - validatedAt < CACHE_TTL_MILLIS;
        }

        StoredManifest revalidated(String responseEtag, long now) {
            String nextEtag = normalizeEtag(responseEtag);
            return new StoredManifest(json, nextEtag.isEmpty() ? etag : nextEtag, now);
        }

        StoredManifest touched(long now) {
            return new StoredManifest(json, etag, now);
        }
    }

    record StoredCache(StoredManifest current, StoredManifest previous,
            boolean activationPending, String blockedRevision) {
        StoredCache {
            if (current == null) {
                previous = null;
                activationPending = false;
                blockedRevision = "";
            } else {
                if (previous != null && revision(current.json()).equals(revision(previous.json()))) {
                    previous = null;
                }
                blockedRevision = normalizeRevision(blockedRevision);
                if (previous == null || !blockedRevision.equals(revision(previous.json()))) {
                    blockedRevision = "";
                }
            }
        }

        boolean blocked() {
            return previous != null && !blockedRevision.isEmpty();
        }

        boolean rollbackAvailable() {
            return previous != null && !blocked();
        }

        String validator() {
            if (current == null) return "";
            return blocked() ? previous.etag() : current.etag();
        }
    }

    record FetchResult(String json, String etag, boolean notModified) {
        FetchResult {
            json = notModified ? null : json;
            etag = normalizeEtag(etag);
        }

        static FetchResult modified(String json, String etag) {
            return new FetchResult(json, etag, false);
        }

        static FetchResult notModified(String etag) {
            return new FetchResult(null, etag, true);
        }
    }

    private record CachedManifest(WebThemeManifest manifest, WebThemeManifest previousManifest,
            StoredCache stored) {
        String revision() {
            return WebThemeManifestLoader.revision(stored.current().json());
        }
    }

    @FunctionalInterface
    interface ManifestSource {
        String read() throws IOException;
    }

    @FunctionalInterface
    interface ConditionalSource {
        FetchResult read(String etag) throws IOException;
    }

    interface PersistentCache {
        StoredCache read(String cacheKey) throws IOException;

        void write(String cacheKey, StoredCache stored) throws IOException;

        void remove(String cacheKey);
    }

    private WebThemeManifestLoader() {
    }

    static WebThemeManifest load(Context context, String url, String target, boolean force) throws IOException {
        return loadResult(context, url, target, force).manifest();
    }

    static LoadResult loadResult(Context context, String url, String target, boolean force) throws IOException {
        String canonicalAsset = WebHomeTarget.canonicalThemeAsset(url);
        if (WebHomeTarget.ECLIPSE_URL.equals(canonicalAsset)) {
            return load(url, target, force,
                    () -> read(context.getAssets().open("webhome/theme.json"),
                            WebThemeManifest.MAX_MANIFEST_BYTES));
        }
        PersistentCache persistent = context != null && canonicalAsset.isEmpty()
                ? WebThemeManifestDiskCache.create(context)
                : NO_PERSISTENT_CACHE;
        return load(url, target, force, etag -> fetch(url, etag), persistent,
                System.currentTimeMillis());
    }

    static LoadResult load(String url, String target, boolean force, ManifestSource source) throws IOException {
        return load(url, target, force, source, NO_PERSISTENT_CACHE);
    }

    static LoadResult load(String url, String target, boolean force, ManifestSource source,
            PersistentCache persistentCache) throws IOException {
        return load(url, target, force,
                etag -> FetchResult.modified(source.read(), ""), persistentCache,
                System.currentTimeMillis());
    }

    static LoadResult load(String url, String target, boolean force, ConditionalSource source,
            PersistentCache persistentCache, long now) throws IOException {
        PersistentCache persistent = persistentCache == null ? NO_PERSISTENT_CACHE : persistentCache;
        String cacheKey = cacheKey(url, target);
        synchronized (cacheLock(cacheKey)) {
            return loadLocked(url, target, force, source, persistent, cacheKey, now);
        }
    }

    private static LoadResult loadLocked(String url, String target, boolean force,
            ConditionalSource source, PersistentCache persistent, String cacheKey, long now) throws IOException {
        CachedManifest cached = getOrReadCached(persistent, cacheKey, url, target);
        if (!force && cached != null && cached.stored().current().isFresh(now)) {
            CacheState state = cached.stored().blocked() ? CacheState.ROLLED_BACK : CacheState.CACHE_HIT;
            return result(cached, state, null);
        }
        try {
            String etag = cached == null ? "" : cached.stored().validator();
            FetchResult fetched = source.read(etag);
            if (fetched == null) throw new IOException("Theme manifest request returned no result");
            if (fetched.notModified()) {
                if (cached == null || etag.isEmpty()) {
                    throw new IOException("Theme manifest was not modified without a cached validator");
                }
                CachedManifest revalidated = revalidate(cached, fetched.etag(), etag, now);
                putCached(cacheKey, revalidated);
                persistBestEffort(persistent, cacheKey, revalidated.stored());
                CacheState state = revalidated.stored().blocked()
                        ? CacheState.ROLLED_BACK : CacheState.CACHE_HIT;
                return result(revalidated, state, null);
            }
            CachedManifest refreshed = activateFetched(url, target, cached, fetched, now);
            putCached(cacheKey, refreshed);
            persistBestEffort(persistent, cacheKey, refreshed.stored());
            CacheState state = refreshed.stored().blocked()
                    ? CacheState.ROLLED_BACK : CacheState.REFRESHED;
            return result(refreshed, state, null);
        } catch (IOException failure) {
            CachedManifest fallback = getCached(cacheKey);
            if (fallback == null) fallback = cached;
            if (fallback != null && fallback.stored().activationPending()
                    && fallback.previousManifest() != null) {
                CachedManifest rolledBack = rollback(fallback, now);
                putCached(cacheKey, rolledBack);
                persistBestEffort(persistent, cacheKey, rolledBack.stored());
                return result(rolledBack, CacheState.ROLLED_BACK, failure);
            }
            if (fallback != null) {
                CacheState state = fallback.stored().blocked()
                        ? CacheState.ROLLED_BACK : CacheState.LAST_KNOWN_GOOD;
                return result(fallback, state, failure);
            }
            throw failure;
        }
    }

    static boolean accept(Context context, String url, String target, String expectedRevision) {
        return accept(url, target, expectedRevision, persistentCache(context, url));
    }

    static boolean accept(String url, String target, String expectedRevision,
            PersistentCache persistentCache) {
        PersistentCache persistent = persistentCache == null ? NO_PERSISTENT_CACHE : persistentCache;
        String cacheKey = cacheKey(url, target);
        synchronized (cacheLock(cacheKey)) {
            CachedManifest cached = getOrReadCached(persistent, cacheKey, url, target);
            String expected = normalizeRevision(expectedRevision);
            if (cached == null || expected.isEmpty() || !cached.revision().equals(expected)
                    || !cached.stored().activationPending()) return false;
            StoredCache stored = new StoredCache(cached.stored().current(), cached.stored().previous(),
                    false, cached.stored().blockedRevision());
            CachedManifest accepted = new CachedManifest(
                    cached.manifest(), cached.previousManifest(), stored);
            putCached(cacheKey, accepted);
            persistBestEffort(persistent, cacheKey, stored);
            return true;
        }
    }

    static LoadResult rollbackPending(Context context, String url, String target,
            String expectedRevision) throws IOException {
        return rollbackPending(url, target, expectedRevision,
                persistentCache(context, url), System.currentTimeMillis());
    }

    static LoadResult rollbackPending(String url, String target, String expectedRevision,
            PersistentCache persistentCache, long now) throws IOException {
        PersistentCache persistent = persistentCache == null ? NO_PERSISTENT_CACHE : persistentCache;
        String cacheKey = cacheKey(url, target);
        synchronized (cacheLock(cacheKey)) {
            CachedManifest cached = getOrReadCached(persistent, cacheKey, url, target);
            String expected = normalizeRevision(expectedRevision);
            if (cached == null || expected.isEmpty() || !cached.revision().equals(expected)
                    || !cached.stored().activationPending() || cached.previousManifest() == null) {
                throw new IOException("Theme manifest rollback is no longer available");
            }
            CachedManifest rolledBack = rollback(cached, now);
            putCached(cacheKey, rolledBack);
            persistBestEffort(persistent, cacheKey, rolledBack.stored());
            return result(rolledBack, CacheState.ROLLED_BACK, null);
        }
    }

    static RollbackAction rollbackAction(Context context, String url, String target) {
        return rollbackAction(url, target, persistentCache(context, url));
    }

    static RollbackAction rollbackAction(String url, String target, PersistentCache persistentCache) {
        PersistentCache persistent = persistentCache == null ? NO_PERSISTENT_CACHE : persistentCache;
        String cacheKey = cacheKey(url, target);
        synchronized (cacheLock(cacheKey)) {
            CachedManifest cached = getOrReadCached(persistent, cacheKey, url, target);
            if (cached == null || cached.previousManifest() == null) return RollbackAction.NONE;
            return cached.stored().blocked() ? RollbackAction.RETRY : RollbackAction.ROLLBACK;
        }
    }

    static LoadResult applyRollbackAction(Context context, String url, String target,
            RollbackAction action) throws IOException {
        return applyRollbackAction(url, target, action,
                persistentCache(context, url), System.currentTimeMillis());
    }

    static LoadResult applyRollbackAction(String url, String target, RollbackAction action,
            PersistentCache persistentCache, long now) throws IOException {
        PersistentCache persistent = persistentCache == null ? NO_PERSISTENT_CACHE : persistentCache;
        String cacheKey = cacheKey(url, target);
        synchronized (cacheLock(cacheKey)) {
            CachedManifest cached = getOrReadCached(persistent, cacheKey, url, target);
            RollbackAction available = cached == null || cached.previousManifest() == null
                    ? RollbackAction.NONE
                    : cached.stored().blocked() ? RollbackAction.RETRY : RollbackAction.ROLLBACK;
            if (action == null || action == RollbackAction.NONE || action != available) {
                throw new IOException("Theme manifest recovery action is no longer available");
            }
            CachedManifest swapped = action == RollbackAction.ROLLBACK
                    ? rollback(cached, now)
                    : retry(cached, now);
            putCached(cacheKey, swapped);
            persistBestEffort(persistent, cacheKey, swapped.stored());
            CacheState state = action == RollbackAction.ROLLBACK
                    ? CacheState.ROLLED_BACK : CacheState.REFRESHED;
            return result(swapped, state, null);
        }
    }

    static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private static CachedManifest activateFetched(String url, String target,
            CachedManifest cached, FetchResult fetched, long now) throws IOException {
        StoredManifest stored = new StoredManifest(fetched.json(), fetched.etag(), now);
        WebThemeManifest manifest = parse(url, target, stored.json());
        String fetchedRevision = revision(stored.json());
        if (cached == null) {
            return new CachedManifest(manifest, null,
                    new StoredCache(stored, null, true, ""));
        }
        StoredCache existing = cached.stored();
        if (existing.blocked()) {
            if (fetchedRevision.equals(existing.blockedRevision())) {
                StoredManifest active = existing.current().touched(now);
                StoredCache blocked = new StoredCache(active, stored, false, fetchedRevision);
                return new CachedManifest(cached.manifest(), manifest, blocked);
            }
            if (fetchedRevision.equals(cached.revision())) {
                StoredCache reverted = new StoredCache(stored, null, false, "");
                return new CachedManifest(manifest, null, reverted);
            }
        } else if (fetchedRevision.equals(cached.revision())) {
            StoredCache same = new StoredCache(stored, existing.previous(),
                    existing.activationPending(), "");
            return new CachedManifest(manifest, cached.previousManifest(), same);
        }
        if (existing.activationPending() && existing.previous() != null
                && fetchedRevision.equals(revision(existing.previous().json()))) {
            StoredCache reverted = new StoredCache(stored, null, false, "");
            return new CachedManifest(manifest, null, reverted);
        }
        StoredManifest previous;
        WebThemeManifest previousManifest;
        if (existing.activationPending()) {
            previous = existing.previous();
            previousManifest = cached.previousManifest();
        } else {
            previous = existing.current();
            previousManifest = cached.manifest();
        }
        StoredCache activated = new StoredCache(stored, previous, true, "");
        return new CachedManifest(manifest, previousManifest, activated);
    }

    private static CachedManifest revalidate(CachedManifest cached, String responseEtag,
            String sentEtag, long now) {
        StoredCache existing = cached.stored();
        if (existing.blocked() && sentEtag.equals(existing.previous().etag())) {
            StoredManifest active = existing.current().touched(now);
            StoredManifest blocked = existing.previous().revalidated(responseEtag, now);
            StoredCache stored = new StoredCache(active, blocked, false, existing.blockedRevision());
            return new CachedManifest(cached.manifest(), cached.previousManifest(), stored);
        }
        StoredManifest current = existing.current().revalidated(responseEtag, now);
        StoredCache stored = new StoredCache(current, existing.previous(),
                existing.activationPending(), existing.blockedRevision());
        return new CachedManifest(cached.manifest(), cached.previousManifest(), stored);
    }

    private static CachedManifest rollback(CachedManifest cached, long now) {
        StoredManifest active = cached.stored().previous().touched(now);
        StoredManifest rejected = cached.stored().current();
        String blockedRevision = revision(rejected.json());
        StoredCache stored = new StoredCache(active, rejected, false, blockedRevision);
        return new CachedManifest(cached.previousManifest(), cached.manifest(), stored);
    }

    private static CachedManifest retry(CachedManifest cached, long now) {
        StoredManifest active = cached.stored().previous().touched(now);
        StoredManifest stable = cached.stored().current();
        StoredCache stored = new StoredCache(active, stable, true, "");
        return new CachedManifest(cached.previousManifest(), cached.manifest(), stored);
    }

    private static LoadResult result(CachedManifest cached, CacheState state, IOException failure) {
        return new LoadResult(cached.manifest(), state, failure,
                cached.stored().activationPending(), cached.stored().rollbackAvailable(),
                cached.revision());
    }

    private static PersistentCache persistentCache(Context context, String url) {
        if (context == null || !WebHomeTarget.canonicalThemeAsset(url).isEmpty()) {
            return NO_PERSISTENT_CACHE;
        }
        return WebThemeManifestDiskCache.create(context);
    }

    private static String cacheKey(String url, String target) {
        return url + "\n" + target;
    }

    private static Object cacheLock(String cacheKey) {
        return CACHE_LOCKS[(cacheKey.hashCode() & Integer.MAX_VALUE) % CACHE_LOCKS.length];
    }

    private static Object[] createCacheLocks(int count) {
        Object[] locks = new Object[count];
        for (int index = 0; index < locks.length; index++) locks[index] = new Object();
        return locks;
    }

    private static CachedManifest getCached(String cacheKey) {
        synchronized (CACHE) {
            return CACHE.get(cacheKey);
        }
    }

    private static CachedManifest getOrReadCached(PersistentCache persistent, String cacheKey,
            String url, String target) {
        CachedManifest cached = getCached(cacheKey);
        if (cached != null) return cached;
        cached = readPersistent(persistent, cacheKey, url, target);
        if (cached != null) putCached(cacheKey, cached);
        return cached;
    }

    private static void putCached(String cacheKey, CachedManifest cached) {
        synchronized (CACHE) {
            CACHE.put(cacheKey, cached);
            while (CACHE.size() > MAX_CACHE_ENTRIES) {
                String eldest = CACHE.keySet().iterator().next();
                CACHE.remove(eldest);
            }
        }
    }

    private static void persistBestEffort(PersistentCache persistent, String cacheKey,
            StoredCache stored) {
        try {
            persistent.write(cacheKey, stored);
        } catch (IOException ignored) {
        }
    }

    private static CachedManifest readPersistent(PersistentCache persistent, String cacheKey,
            String url, String target) {
        try {
            StoredCache stored = persistent.read(cacheKey);
            if (stored == null || stored.current() == null || stored.current().json().isEmpty()) {
                persistent.remove(cacheKey);
                return null;
            }
            WebThemeManifest current;
            try {
                current = parse(url, target, stored.current().json());
            } catch (IOException currentFailure) {
                if (stored.previous() == null || stored.previous().json().isEmpty()
                        || stored.blocked()) throw currentFailure;
                WebThemeManifest recovered = parse(url, target, stored.previous().json());
                StoredCache recovery = new StoredCache(stored.previous(), null, false, "");
                persistBestEffort(persistent, cacheKey, recovery);
                return new CachedManifest(recovered, null, recovery);
            }
            WebThemeManifest previous = null;
            StoredCache validated = stored;
            if (stored.previous() != null) {
                try {
                    previous = parse(url, target, stored.previous().json());
                } catch (IOException ignored) {
                    validated = new StoredCache(stored.current(), null,
                            stored.activationPending(), "");
                    persistBestEffort(persistent, cacheKey, validated);
                }
            }
            return new CachedManifest(current, previous, validated);
        } catch (IOException ignored) {
            persistent.remove(cacheKey);
            return null;
        }
    }

    private static WebThemeManifest parse(String url, String target, String json) throws IOException {
        try {
            return WebThemeManifest.parse(url, json, target);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid theme manifest", e);
        }
    }

    private static FetchResult fetch(String url, String etag) throws IOException {
        if (!WebHomeTarget.isSafeThemeUrl(url) || !WebHomeTarget.isManifestUrl(url)) {
            throw new IOException("Unsafe theme manifest URL");
        }
        return execute(CLIENT, buildRequest(url, etag));
    }

    static Request buildRequest(String url, String etag) {
        Request.Builder request = new Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json");
        String normalizedEtag = normalizeEtag(etag);
        if (!normalizedEtag.isEmpty()) request.header("If-None-Match", normalizedEtag);
        return request.build();
    }

    static FetchResult execute(OkHttpClient client, Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            if (!response.request().url().equals(request.url())) {
                throw new IOException("Theme manifest request was redirected");
            }
            String responseEtag = normalizeEtag(response.header("ETag"));
            if (response.code() == 304) return FetchResult.notModified(responseEtag);
            if (!response.isSuccessful()) {
                throw new IOException("Theme manifest request failed: " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null || body.contentLength() > WebThemeManifest.MAX_MANIFEST_BYTES) {
                throw new IOException("Theme manifest is too large");
            }
            return FetchResult.modified(
                    read(body.byteStream(), WebThemeManifest.MAX_MANIFEST_BYTES), responseEtag);
        }
    }

    static String read(InputStream input, int maxBytes) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > maxBytes) throw new IOException("Theme manifest is too large");
                output.write(buffer, 0, count);
            }
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(output.toByteArray()))
                        .toString();
            } catch (CharacterCodingException e) {
                throw new IOException("Theme manifest is not valid UTF-8", e);
            }
        }
    }

    static String revision(String json) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((json == null ? "" : json).getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(REVISION_LENGTH);
            for (byte part : digest) value.append(String.format(Locale.US, "%02x", part & 0xff));
            return value.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String normalizeRevision(String revision) {
        if (revision == null) return "";
        String value = revision.trim().toLowerCase(Locale.US);
        return value.matches("[0-9a-f]{" + REVISION_LENGTH + "}") ? value : "";
    }

    private static String normalizeEtag(String etag) {
        if (etag == null) return "";
        String value = etag.trim();
        if (value.isEmpty() || value.length() > MAX_ETAG_LENGTH) return "";
        int opaqueStart;
        if (value.startsWith("W/\"") && value.endsWith("\"")) opaqueStart = 3;
        else if (value.startsWith("\"") && value.endsWith("\"")) opaqueStart = 1;
        else return "";
        if (opaqueStart >= value.length() - 1) return "";
        for (int index = opaqueStart; index < value.length() - 1; index++) {
            char part = value.charAt(index);
            if (part == '"' || part < 0x20 || part > 0x7e) return "";
        }
        return value;
    }

    private static List<InetAddress> lookupPublic(String hostname) throws UnknownHostException {
        List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
        if (addresses.isEmpty()) throw new UnknownHostException(hostname);
        for (InetAddress address : addresses) {
            if (WebHomeTarget.isBlockedAddress(address)) throw new UnknownHostException("Blocked theme host");
        }
        return addresses;
    }
}
