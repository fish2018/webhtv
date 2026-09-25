package com.fongmi.android.tv.playback;

import android.text.TextUtils;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Stable playback identity and protocol-v1 address matching helpers. */
public final class PlaybackConfigIdentity {

    public static final String IDENTITY_SCHEMA = "webhtv.playback.identity.v1";
    public static final String IDENTITY_VERSION = "2";
    public static final String ADDRESS_MATCH_VERSION = "1";

    private PlaybackConfigIdentity() {
    }

    public static String currentKey() {
        return keyForCid(VodConfig.getCid());
    }

    public static String currentName() {
        return safe(VodConfig.getDesc());
    }

    public static String keyForCid(int cid) {
        Config config = Config.find(cid);
        if (config == null) return keyForUrl(VodConfig.getUrl());
        String key = normalizeKey(config.getInterfaceKey());
        if (!TextUtils.isEmpty(key)) return key;
        key = config.ensureInterfaceKey();
        config.save();
        return normalizeKey(key);
    }

    public static String nameForCid(int cid) {
        Config config = Config.find(cid);
        if (config == null || TextUtils.isEmpty(config.getDesc())) return currentName();
        return config.getDesc();
    }

    public static int cidForKey(String configKey) {
        configKey = normalizeKey(configKey);
        if (TextUtils.isEmpty(configKey)) return 0;
        for (Config config : Config.getAll(0)) {
            if (config == null) continue;
            if (TextUtils.equals(configKey, normalizeKey(config.getInterfaceKey()))) return config.getId();
            for (String url : config.getUrls()) if (TextUtils.equals(configKey, keyForUrl(url))) return config.getId();
            if (config.getLegacyConfigKeys().contains(configKey)) return config.getId();
        }
        return 0;
    }

    /** The old protocol is intentionally byte-for-byte URL hashing. */
    public static String keyForUrl(String url) {
        url = safe(url).trim();
        return TextUtils.isEmpty(url) ? "" : sha256(url);
    }

    public static String configType(int type) {
        if (type == 1) return "live";
        if (type == 2) return "wall";
        return "vod";
    }

    public static IdentitySnapshot snapshotForCid(int cid) {
        Config config = Config.find(cid);
        if (config == null) return new IdentitySnapshot("", 0, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        return snapshot(config);
    }

    public static IdentitySnapshot snapshot(Config config) {
        if (config == null) return new IdentitySnapshot("", 0, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        List<String> urls = config.getUrls();
        String primaryLegacyKey = keyForUrl(config.getUrl());
        List<String> legacy = unique(config.getLegacyConfigKeys());
        if (!TextUtils.isEmpty(primaryLegacyKey) && !legacy.contains(primaryLegacyKey)) legacy.add(primaryLegacyKey);
        List<String> strict = new ArrayList<>();
        List<String> endpoint = new ArrayList<>();
        List<String> host = new ArrayList<>();
        for (String url : urls) {
            String legacyKey = keyForUrl(url);
            if (!TextUtils.isEmpty(legacyKey) && !legacy.contains(legacyKey)) legacy.add(legacyKey);
            CanonicalAddress address = canonicalize(url);
            if (address == null) continue;
            add(strict, sha256(material(configType(config.getType()), address, "strict")));
            add(endpoint, sha256(material(configType(config.getType()), address, "endpoint")));
            add(host, sha256(material(configType(config.getType()), address, "host")));
        }
        return new IdentitySnapshot(normalizeKey(config.ensureInterfaceKey()), config.getType(), strict, endpoint, host, legacy);
    }

    public static List<String> strictAddressKeysForCid(int cid) {
        return snapshotForCid(cid).strictAddressKeys;
    }

    public static List<String> endpointMatchKeysForCid(int cid) {
        return snapshotForCid(cid).endpointMatchKeys;
    }

    public static List<String> hostMatchKeysForCid(int cid) {
        return snapshotForCid(cid).hostMatchKeys;
    }

    public static List<String> legacyKeysForCid(int cid) {
        return snapshotForCid(cid).legacyConfigKeys;
    }

    public static List<String> strictAddressKeys(int type, List<String> urls) {
        return keysFor(type, urls, "strict");
    }

    public static List<String> endpointMatchKeys(int type, List<String> urls) {
        return keysFor(type, urls, "endpoint");
    }

    public static List<String> hostMatchKeys(int type, List<String> urls) {
        return keysFor(type, urls, "host");
    }

    public static String normalizeKey(String value) {
        return safe(value).trim().toLowerCase(Locale.ROOT);
    }

    public static CanonicalAddress canonicalize(String value) {
        String source = safe(value).trim();
        if (TextUtils.isEmpty(source)) return null;
        try {
            URI uri = URI.create(source);
            String scheme = safe(uri.getScheme()).toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) return null;
            if (!TextUtils.isEmpty(uri.getUserInfo()) || TextUtils.isEmpty(uri.getHost())) return null;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            int portNumber = uri.getPort();
            if (("http".equals(scheme) && portNumber == 80) || ("https".equals(scheme) && portNumber == 443)) portNumber = -1;
            String port = portNumber < 0 ? "" : String.valueOf(portNumber);
            String path = normalizePath(uri.getRawPath());
            String query = normalizePercentEncoding(uri.getRawQuery());
            return new CanonicalAddress(scheme, host, port, path, query);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String normalizePath(String value) {
        String input = TextUtils.isEmpty(value) ? "/" : value;
        if (!input.startsWith("/")) input = "/" + input;
        boolean trailing = input.length() > 1 && input.endsWith("/");
        ArrayDeque<String> stack = new ArrayDeque<>();
        for (String part : input.split("/", -1)) {
            if (TextUtils.isEmpty(part) || ".".equals(part)) continue;
            if ("..".equals(part)) {
                if (!stack.isEmpty()) stack.removeLast();
            } else stack.addLast(part);
        }
        StringBuilder output = new StringBuilder("/");
        for (String item : stack) {
            if (output.length() > 1) output.append('/');
            output.append(item);
        }
        if (trailing && output.length() > 1) output.append('/');
        return normalizePercentEncoding(output.toString());
    }

    public static String normalizePercentEncoding(String value) {
        String input = safe(value);
        StringBuilder output = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == '%' && i + 2 < input.length()) {
                int high = Character.digit(input.charAt(i + 1), 16);
                int low = Character.digit(input.charAt(i + 2), 16);
                if (high >= 0 && low >= 0) {
                    int decoded = high * 16 + low;
                    if (isUnreserved(decoded)) output.append((char) decoded);
                    else output.append('%').append(Character.toUpperCase(input.charAt(i + 1))).append(Character.toUpperCase(input.charAt(i + 2)));
                    i += 2;
                    continue;
                }
            }
            output.append(current);
        }
        return output.toString();
    }

    private static List<String> keysFor(int type, List<String> urls, String kind) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (urls != null) for (String url : urls) {
            CanonicalAddress address = canonicalize(url);
            if (address != null) result.add(sha256(material(configType(type), address, kind)));
        }
        return new ArrayList<>(result);
    }

    private static String material(String type, CanonicalAddress address, String kind) {
        if ("strict".equals(kind)) return String.join("\n", "webhtv.address.strict.v1", type, address.scheme, address.host, address.port, address.path, address.query);
        if ("endpoint".equals(kind)) return String.join("\n", "webhtv.address.endpoint.v1", type, address.host, address.port, address.path, address.query);
        return String.join("\n", "webhtv.address.host.v1", type, address.host, address.port);
    }

    private static void add(List<String> values, String value) {
        if (!TextUtils.isEmpty(value) && !values.contains(value)) values.add(value);
    }

    private static List<String> unique(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static boolean isUnreserved(int value) {
        return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z' || value >= '0' && value <= '9' || value == '-' || value == '_' || value == '.' || value == '~';
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(safe(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) builder.append(String.format(Locale.ROOT, "%02x", b));
            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class CanonicalAddress {
        public final String scheme;
        public final String host;
        public final String port;
        public final String path;
        public final String query;

        private CanonicalAddress(String scheme, String host, String port, String path, String query) {
            this.scheme = scheme;
            this.host = host;
            this.port = port;
            this.path = path;
            this.query = query;
        }
    }

    public static final class IdentitySnapshot {
        public final String interfaceKey;
        public final int configType;
        public final List<String> strictAddressKeys;
        public final List<String> endpointMatchKeys;
        public final List<String> hostMatchKeys;
        public final List<String> legacyConfigKeys;

        private IdentitySnapshot(String interfaceKey, int configType, List<String> strictAddressKeys, List<String> endpointMatchKeys, List<String> hostMatchKeys, List<String> legacyConfigKeys) {
            this.interfaceKey = interfaceKey;
            this.configType = configType;
            this.strictAddressKeys = Collections.unmodifiableList(strictAddressKeys);
            this.endpointMatchKeys = Collections.unmodifiableList(endpointMatchKeys);
            this.hostMatchKeys = Collections.unmodifiableList(hostMatchKeys);
            this.legacyConfigKeys = Collections.unmodifiableList(legacyConfigKeys);
        }
    }
}
