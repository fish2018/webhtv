package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.api.TmdbSourceCredentialIngress;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Process-memory TMDB credential scope owned by the currently selected subscription. */
public final class SubscriptionTmdbCredentialStore {

    private static final Object LOCK = new Object();

    private static boolean hasScope;
    private static int configId;
    private static String configUrl = "";
    private static long scopeEpoch;
    private static String apiKey = "";
    private static String fingerprint = "";
    private static long receivedAt;
    private static String sourceKey = "";
    private static String sourceRevision = "";

    private SubscriptionTmdbCredentialStore() {
    }

    public static Scope beginSubscription(int nextConfigId, String nextConfigUrl, String reason) {
        String normalizedUrl = normalizeUrl(nextConfigUrl);
        synchronized (LOCK) {
            if (!hasScope || configId != nextConfigId || !configUrl.equals(normalizedUrl)) {
                scopeEpoch++;
                clearCredential();
                configId = nextConfigId;
                configUrl = normalizedUrl;
                hasScope = true;
            }
            return scope();
        }
    }

    public static Scope currentScope() {
        synchronized (LOCK) {
            return scope();
        }
    }

    public static boolean accept(String candidateKey, int expectedConfigId, String expectedConfigUrl, long expectedEpoch,
                                 String candidateSourceKey, String candidateSourceRevision) {
        String normalizedKey = TmdbSourceCredentialIngress.normalizeApiKey(candidateKey);
        String normalizedUrl = normalizeUrl(expectedConfigUrl);
        if (normalizedKey.isEmpty()) return false;
        synchronized (LOCK) {
            if (!hasScope || configId != expectedConfigId || !configUrl.equals(normalizedUrl) || scopeEpoch != expectedEpoch) return false;
            if (!apiKey.isEmpty() && !apiKey.equals(normalizedKey)) scopeEpoch++;
            apiKey = normalizedKey;
            fingerprint = fingerprint(normalizedKey);
            receivedAt = System.currentTimeMillis();
            sourceKey = trim(candidateSourceKey);
            sourceRevision = trim(candidateSourceRevision);
            return true;
        }
    }

    public static Snapshot snapshot(int expectedConfigId, String expectedConfigUrl, long expectedEpoch) {
        String normalizedUrl = normalizeUrl(expectedConfigUrl);
        synchronized (LOCK) {
            if (!hasScope || configId != expectedConfigId || !configUrl.equals(normalizedUrl) || scopeEpoch != expectedEpoch) {
                return Snapshot.empty(expectedEpoch);
            }
            if (apiKey.isEmpty()) return Snapshot.empty(expectedEpoch);
            return new Snapshot(apiKey, subscriptionKey(configId, configUrl), scopeEpoch, receivedAt, sourceKey, sourceRevision, fingerprint);
        }
    }

    public static Snapshot snapshot(Scope scope) {
        if (scope == null) return Snapshot.empty(currentScope().getEpoch());
        return snapshot(scope.configId, scope.configUrl, scope.epoch);
    }

    public static boolean isCurrent(Scope scope) {
        if (scope == null || !scope.available) return false;
        return isCurrent(scope.configId, scope.configUrl, scope.epoch);
    }

    public static boolean isCurrent(int expectedConfigId, String expectedConfigUrl, long expectedEpoch) {
        String normalizedUrl = normalizeUrl(expectedConfigUrl);
        synchronized (LOCK) {
            return hasScope && configId == expectedConfigId && configUrl.equals(normalizedUrl) && scopeEpoch == expectedEpoch;
        }
    }

    public static boolean isCurrent(String expectedSubscriptionKey, long expectedEpoch) {
        if (expectedSubscriptionKey == null || expectedSubscriptionKey.isEmpty()) return false;
        synchronized (LOCK) {
            return hasScope && scopeEpoch == expectedEpoch
                    && expectedSubscriptionKey.equals(subscriptionKey(configId, configUrl));
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            scopeEpoch++;
            clearCredential();
        }
    }

    public static boolean clearIfCurrent(String expectedSubscriptionKey, long expectedEpoch) {
        if (expectedSubscriptionKey == null || expectedSubscriptionKey.isEmpty()) return false;
        synchronized (LOCK) {
            if (!hasScope || scopeEpoch != expectedEpoch || !expectedSubscriptionKey.equals(subscriptionKey(configId, configUrl))) return false;
            scopeEpoch++;
            clearCredential();
            return true;
        }
    }

    public static void discardCredential() {
        synchronized (LOCK) {
            if (apiKey.isEmpty()) return;
            scopeEpoch++;
            clearCredential();
        }
    }

    private static Scope scope() {
        return new Scope(configId, configUrl, scopeEpoch, hasScope);
    }

    private static void clearCredential() {
        apiKey = "";
        fingerprint = "";
        receivedAt = 0L;
        sourceKey = "";
        sourceRevision = "";
    }

    private static String normalizeUrl(String value) {
        String normalized = trim(value);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String subscriptionKey(int configId, String configUrl) {
        return configId + "\u0000" + configUrl;
    }

    private static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) hex.append(String.format("%02x", item & 0xff));
            return hex.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static final class Scope {

        private final int configId;
        private final String configUrl;
        private final long epoch;
        private final boolean available;

        private Scope(int configId, String configUrl, long epoch, boolean available) {
            this.configId = configId;
            this.configUrl = configUrl;
            this.epoch = epoch;
            this.available = available;
        }

        public int getConfigId() {
            return configId;
        }

        public String getConfigUrl() {
            return configUrl;
        }

        public long getEpoch() {
            return epoch;
        }

        public boolean isAvailable() {
            return available;
        }
    }

    public static final class Snapshot {

        private static final Snapshot EMPTY = new Snapshot("", "", 0L, 0L, "", "", "");

        private final String apiKey;
        private final String subscriptionKey;
        private final long scopeEpoch;
        private final long receivedAt;
        private final String sourceKey;
        private final String sourceRevision;
        private final String fingerprint;

        private Snapshot(String apiKey, String subscriptionKey, long scopeEpoch, long receivedAt,
                         String sourceKey, String sourceRevision, String fingerprint) {
            this.apiKey = apiKey;
            this.subscriptionKey = subscriptionKey;
            this.scopeEpoch = scopeEpoch;
            this.receivedAt = receivedAt;
            this.sourceKey = sourceKey;
            this.sourceRevision = sourceRevision;
            this.fingerprint = fingerprint;
        }

        private static Snapshot empty(long scopeEpoch) {
            return scopeEpoch == EMPTY.scopeEpoch ? EMPTY : new Snapshot("", "", scopeEpoch, 0L, "", "", "");
        }

        public String getApiKey() {
            return apiKey;
        }

        public String getSubscriptionKey() {
            return subscriptionKey;
        }

        public long getScopeEpoch() {
            return scopeEpoch;
        }

        public long getReceivedAt() {
            return receivedAt;
        }

        public String getSourceKey() {
            return sourceKey;
        }

        public String getSourceRevision() {
            return sourceRevision;
        }

        public String getFingerprint() {
            return fingerprint;
        }

        public boolean isEmpty() {
            return apiKey.isEmpty();
        }

        public boolean isPresent() {
            return !isEmpty();
        }

        @Override
        public String toString() {
            return "Snapshot{hasKey=" + !isEmpty() + ",scopeEpoch=" + scopeEpoch + ",sourceKey='" + sourceKey + "'}";
        }
    }
}
