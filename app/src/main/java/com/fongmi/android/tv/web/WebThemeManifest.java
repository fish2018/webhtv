package com.fongmi.android.tv.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class WebThemeManifest {

    public static final int SCHEMA_VERSION = 2;
    public static final int HOST_API_VERSION = 3;
    public static final int MAX_MANIFEST_BYTES = 128 * 1024;

    private static final int MAX_ID_LENGTH = 96;
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_VERSION_LENGTH = 32;
    private static final int MAX_ENTRY_LENGTH = 2048;
    private static final int MAX_PERMISSIONS = 32;
    private static final int MAX_PERMISSION_LENGTH = 64;
    private static final String ASSET_ROOT = "file:///android_asset/webhome/";
    private static final Set<String> RESERVED_FIELDS = Set.of("player", "tokens");

    private final String manifestUrl;
    private final String id;
    private final String name;
    private final String version;
    private final int minHostApi;
    private final EnumMap<WebThemePage, Page> pages;
    private final Set<String> reservedFields;

    private WebThemeManifest(String manifestUrl, String id, String name, String version, int minHostApi,
            EnumMap<WebThemePage, Page> pages, Set<String> reservedFields) {
        this.manifestUrl = manifestUrl;
        this.id = id;
        this.name = name;
        this.version = version;
        this.minHostApi = minHostApi;
        this.pages = pages;
        this.reservedFields = reservedFields;
    }

    public static WebThemeManifest parse(String manifestUrl, String json, String target) {
        String raw = json == null ? "" : json;
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_MANIFEST_BYTES) {
            throw new IllegalArgumentException("Theme manifest is too large");
        }
        String source = trim(raw);
        if (!WebHomeTarget.isSafeThemeUrl(manifestUrl) || !WebHomeTarget.isManifestUrl(manifestUrl)) {
            throw new IllegalArgumentException("Unsafe theme manifest URL");
        }
        try {
            JsonElement element = JsonParser.parseString(source);
            if (!element.isJsonObject()) throw new IllegalArgumentException("Theme manifest must be an object");
            JsonObject root = element.getAsJsonObject();
            if (integer(root, "schemaVersion") != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported theme schema");
            }
            String id = required(root, "id", MAX_ID_LENGTH);
            if (!id.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("Invalid theme id");
            String name = optional(root, "name", MAX_NAME_LENGTH, id);
            String version = required(root, "version", MAX_VERSION_LENGTH);
            int minHostApi = integer(root, "minHostApi");
            if (minHostApi < 1 || minHostApi > HOST_API_VERSION) {
                throw new IllegalArgumentException("Unsupported host API");
            }
            requireTarget(root, trim(target));
            Set<String> reservedFields = reservedFields(root);

            JsonObject pageObjects = object(root, "pages");
            JsonObject permissionObjects = object(root, "permissions");
            EnumMap<WebThemePage, Page> pages = new EnumMap<>(WebThemePage.class);
            for (WebThemePage page : WebThemePage.values()) {
                Page parsed = parsePage(manifestUrl, page, pageObjects, permissionObjects);
                if (parsed != null) pages.put(page, parsed);
            }
            return new WebThemeManifest(manifestUrl, id, name, version, minHostApi, pages, reservedFields);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid theme manifest", e);
        }
    }

    private static Page parsePage(String manifestUrl, WebThemePage page, JsonObject pages, JsonObject permissions) {
        if (!pages.has(page.getKey()) || !pages.get(page.getKey()).isJsonObject()) return null;
        try {
            JsonObject object = pages.getAsJsonObject(page.getKey());
            String entry = required(object, "entry", MAX_ENTRY_LENGTH);
            String contract = required(object, "contract", MAX_PERMISSION_LENGTH);
            if (!page.getContract().equals(contract)) return null;
            String fallback = optional(object, "fallback", 16, "native");
            if (!"native".equals(fallback)) return null;
            String entryUrl = resolveEntry(manifestUrl, entry);
            if (entryUrl == null) return null;
            Set<String> pagePermissions = WebThemeCapabilityRegistry.filterPermissions(page,
                    permissions(permissions, page.getKey()));
            String contractPermission = contract.substring(0, contract.lastIndexOf('@'));
            if (!pagePermissions.contains(contractPermission)) return null;
            return new Page(entryUrl, contract, pagePermissions);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String resolveEntry(String manifestUrl, String entry) {
        try {
            URI manifest = URI.create(manifestUrl);
            URI resolved = manifest.resolve(entry).normalize();
            if ("file".equalsIgnoreCase(manifest.getScheme())) {
                String value = WebHomeTarget.canonicalThemeAsset(resolved.toString());
                return value.startsWith(ASSET_ROOT) ? value : null;
            }
            if (!"https".equalsIgnoreCase(resolved.getScheme()) || !sameOrigin(manifest, resolved)) return null;
            String value = resolved.toString();
            return WebHomeTarget.isSafeThemeUrl(value) ? value : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean sameOrigin(URI first, URI second) {
        return first.getHost() != null && second.getHost() != null
                && first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && port(first) == port(second);
    }

    private static int port(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : 443;
    }

    private static Set<String> permissions(JsonObject root, String page) {
        if (!root.has(page) || !root.get(page).isJsonArray()) return Collections.emptySet();
        JsonArray array = root.getAsJsonArray(page);
        if (array.size() > MAX_PERMISSIONS) throw new IllegalArgumentException("Too many theme permissions");
        Set<String> result = new HashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) continue;
            String value = trim(element.getAsString());
            if (!value.isEmpty() && value.length() <= MAX_PERMISSION_LENGTH
                    && value.matches("[A-Za-z0-9._-]+")) result.add(value);
        }
        return Collections.unmodifiableSet(result);
    }

    private static Set<String> reservedFields(JsonObject root) {
        Set<String> result = new HashSet<>();
        for (String field : RESERVED_FIELDS) {
            if (!root.has(field)) continue;
            if (!root.get(field).isJsonObject()) {
                throw new IllegalArgumentException("Invalid reserved theme field: " + field);
            }
            result.add(field);
        }
        return Collections.unmodifiableSet(result);
    }

    private static void requireTarget(JsonObject root, String target) {
        if (!root.has("targets")) return;
        if (!root.get("targets").isJsonArray()) throw new IllegalArgumentException("Invalid theme targets");
        JsonArray targets = root.getAsJsonArray("targets");
        if (targets.size() > 8 || target.isEmpty()) throw new IllegalArgumentException("Unsupported theme target");
        for (JsonElement element : targets) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                    && target.equalsIgnoreCase(trim(element.getAsString()))) return;
        }
        throw new IllegalArgumentException("Unsupported theme target");
    }

    private static JsonObject object(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : new JsonObject();
    }

    private static int integer(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.get(key).getAsJsonPrimitive().isNumber()) return 0;
        try {
            return object.get(key).getAsJsonPrimitive().getAsBigDecimal().intValueExact();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static String required(JsonObject object, String key, int maxLength) {
        String value = optional(object, key, maxLength, "");
        if (value.isEmpty()) throw new IllegalArgumentException("Missing theme field: " + key);
        return value;
    }

    private static String optional(JsonObject object, String key, int maxLength, String fallback) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.get(key).getAsJsonPrimitive().isString()) return fallback;
        String value = trim(object.get(key).getAsString());
        if (value.length() > maxLength) throw new IllegalArgumentException("Theme field is too long: " + key);
        return value.isEmpty() ? fallback : value;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public String getManifestUrl() {
        return manifestUrl;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public int getMinHostApi() {
        return minHostApi;
    }

    public Page getPage(WebThemePage page) {
        return pages.get(page);
    }

    public Set<String> getReservedFields() {
        return reservedFields;
    }

    public static final class Page {
        private final String entryUrl;
        private final String contract;
        private final Set<String> permissions;

        private Page(String entryUrl, String contract, Set<String> permissions) {
            this.entryUrl = entryUrl;
            this.contract = contract;
            this.permissions = permissions;
        }

        public String getEntryUrl() {
            return entryUrl;
        }

        public String getContract() {
            return contract;
        }

        public Set<String> getPermissions() {
            return permissions;
        }
    }
}
