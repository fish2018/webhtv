package com.fongmi.android.tv.theme;

import android.content.Context;
import android.content.res.AssetManager;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Loads the APK-bundled theme catalog without network or executable theme content. */
public final class ThemeCatalog {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_INDEX_BYTES = 64 * 1024;
    public static final int MAX_ENTRIES = 32;
    public static final int MAX_ASSET_PATH_LENGTH = 256;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,95}");
    private static final Gson GSON = new Gson();

    private ThemeCatalog() {
    }

    public static Result load(Context context) {
        if (context == null) return Result.failure("theme catalog context is unavailable");
        try {
            AssetReader reader = path -> readAsset(context.getAssets(), path);
            String index = reader.read("themes/index.json");
            String expectedIndexDigest = reader.read("themes/index.sha256").trim().toLowerCase(Locale.ROOT);
            if (!matchesDigest(index, expectedIndexDigest)) {
                return Result.failure("theme catalog index digest mismatch");
            }
            return Result.success(parseIndex(index, reader, expectedIndexDigest));
        } catch (IOException | RuntimeException e) {
            return Result.failure(message(e, "theme catalog is invalid"));
        }
    }

    /** Parses an index using a caller-provided asset reader; kept pure for deterministic tests. */
    static Catalog parseIndex(String json, AssetReader reader, String indexDigest) throws IOException {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_INDEX_BYTES) {
            throw new IllegalArgumentException("theme catalog index is too large");
        }
        IndexData data = GSON.fromJson(json, IndexData.class);
        if (data == null || data.schemaVersion != SCHEMA_VERSION || data.version < 1
                || data.version > 1_000_000 || data.entries == null || data.entries.isEmpty()
                || data.entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("theme catalog schema or version is unsupported");
        }
        String normalizedIndexDigest = requireDigest(indexDigest, "index sha256");
        if (!matchesDigest(json, normalizedIndexDigest)) {
            throw new IllegalArgumentException("theme catalog index digest mismatch");
        }
        Set<String> ids = new HashSet<>();
        List<Entry> entries = new ArrayList<>(data.entries.size());
        for (EntryData item : data.entries) {
            validateEntry(item, ids);
            String profileJson = reader.read(item.profile);
            ThemeProfile profile = parseProfile(profileJson, item.sha256);
            if (!item.id.equals(profile.id)) throw new IllegalArgumentException("theme catalog profile id mismatch");
            String preview = reader.read(item.preview);
            if (!matchesDigest(preview, item.previewSha256)) {
                throw new IllegalArgumentException("theme preview digest mismatch: " + item.id);
            }
            entries.add(new Entry(item.id, item.version, item.name, profile,
                    ThemeProfileCodec.encode(profile), item.profile, item.preview, item.previewSha256, preview));
        }
        return new Catalog(SCHEMA_VERSION, data.version, normalizedIndexDigest, entries);
    }

    static ThemeProfile parseProfile(String json, String expectedDigest) {
        String canonicalDigest = requireDigest(expectedDigest, "profile sha256");
        ThemeProfile profile = ThemeProfileCodec.parse(json);
        String canonical = ThemeProfileCodec.encode(profile);
        if (!canonical.equals(json)) throw new IllegalArgumentException("theme profile is not canonical JSON");
        if (!matchesDigest(canonical, canonicalDigest)) throw new IllegalArgumentException("theme profile digest mismatch");
        return profile;
    }

    static Catalog fromCache(String json) {
        if (json == null || json.isBlank() || json.getBytes(StandardCharsets.UTF_8).length > MAX_INDEX_BYTES * 4) {
            return null;
        }
        try {
            CacheData data = GSON.fromJson(json, CacheData.class);
            if (data == null || data.schemaVersion != SCHEMA_VERSION || data.version < 1
                    || data.version > 1_000_000 || data.entries == null || data.entries.isEmpty()
                    || data.entries.size() > MAX_ENTRIES) return null;
            String indexDigest = requireDigest(data.indexSha256, "cached index sha256");
            String cacheDigest = requireDigest(data.cacheSha256, "cached snapshot sha256");
            data.cacheSha256 = "";
            if (!matchesDigest(GSON.toJson(data), cacheDigest)) return null;
            Set<String> ids = new HashSet<>();
            List<Entry> entries = new ArrayList<>(data.entries.size());
            for (EntryData item : data.entries) {
                validateEntry(item, ids);
                ThemeProfile profile = parseProfile(item.profileJson, item.sha256);
                if (!item.id.equals(profile.id)) return null;
                if (!matchesDigest(item.previewContent, item.previewSha256)) return null;
                entries.add(new Entry(item.id, item.version, item.name, profile,
                    item.profileJson, item.profile, item.preview, item.previewSha256, item.previewContent));
            }
            return new Catalog(SCHEMA_VERSION, data.version, indexDigest, entries);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String toCacheJson(Catalog catalog) {
        CacheData data = new CacheData();
        data.schemaVersion = catalog.schemaVersion();
        data.version = catalog.version();
        data.indexSha256 = catalog.indexSha256();
        data.cacheSha256 = "";
        data.entries = new ArrayList<>();
        for (Entry entry : catalog.entries()) {
            EntryData item = new EntryData();
            item.id = entry.id();
            item.version = entry.version();
            item.name = entry.name();
            item.profile = entry.profilePath();
            item.profileJson = entry.profileJson();
            item.sha256 = sha256(entry.profileJson());
            item.preview = entry.preview();
            item.previewSha256 = entry.previewSha256();
            item.previewContent = entry.previewContent();
            data.entries.add(item);
        }
        data.cacheSha256 = sha256(GSON.toJson(data));
        return GSON.toJson(data);
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte part : digest) result.append(String.format(Locale.ROOT, "%02x", part & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static boolean matchesDigest(String value, String expected) {
        if (expected == null || !SHA256.matcher(expected).matches()) return false;
        byte[] actualBytes = sha256(value).getBytes(StandardCharsets.US_ASCII);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actualBytes, expectedBytes);
    }

    private static void validateEntry(EntryData item, Set<String> ids) {
        if (item == null || item.id == null || !ID.matcher(item.id).matches() || !ids.add(item.id)) {
            throw new IllegalArgumentException("theme catalog entry id is invalid");
        }
        if (item.version < 1 || item.version > 1_000_000 || item.name == null
                || item.name.isBlank() || item.name.length() > 80) {
            throw new IllegalArgumentException("theme catalog entry metadata is invalid: " + item.id);
        }
        requireAssetPath(item.profile, "profile");
        requireAssetPath(item.preview, "preview");
        requireDigest(item.sha256, "profile sha256");
        requireDigest(item.previewSha256, "preview sha256");
    }

    private static String requireDigest(String value, String label) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(normalized).matches()) throw new IllegalArgumentException(label + " is invalid");
        return normalized;
    }

    private static void requireAssetPath(String value, String label) {
        if (value == null || value.length() == 0 || value.length() > MAX_ASSET_PATH_LENGTH
                || !value.startsWith("themes/") || value.contains("\\") || value.contains("//")
                || value.contains("../") || value.endsWith("/..") || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(label + " asset path is invalid");
        }
    }

    private static String readAsset(AssetManager assets, String path) throws IOException {
        try (InputStream input = assets.open(path, AssetManager.ACCESS_STREAMING)) {
            return ThemeTransfer.read(input);
        }
    }

    private static String message(Exception error, String fallback) {
        return error.getMessage() == null || error.getMessage().isBlank() ? fallback : error.getMessage();
    }

    @FunctionalInterface
    interface AssetReader {
        String read(String path) throws IOException;
    }

    public record Entry(String id, int version, String name, ThemeProfile profile, String profileJson,
            String profilePath, String preview, String previewSha256, String previewContent) {
    }

    public record Catalog(int schemaVersion, int version, String indexSha256, List<Entry> entries) {
        public Catalog {
            entries = entries == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(entries));
        }
    }

    public record Result(Catalog catalog, String error) {
        static Result success(Catalog catalog) {
            return new Result(catalog, "");
        }

        static Result failure(String error) {
            return new Result(null, error == null ? "theme catalog is unavailable" : error);
        }

        public boolean success() {
            return catalog != null && (error == null || error.isEmpty());
        }
    }

    private static final class IndexData {
        int schemaVersion;
        int version;
        List<EntryData> entries;
    }

    private static final class CacheData {
        int schemaVersion;
        int version;
        String indexSha256;
        List<EntryData> entries;
        String cacheSha256;
    }

    private static final class EntryData {
        String id;
        int version;
        String name;
        String profile;
        String profileJson;
        String sha256;
        String preview;
        String previewSha256;
        String previewContent;
    }
}
