package com.fongmi.android.tv.setting;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.gson.ExtAdapter;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class CustomCspSetting {

    private static final String DIR = "TV/CustomCsp";
    private static final String REGISTRY = "registry.json";
    private static final String PREFIX = "__custom_csp_";
    private static final int MAX_INSERT_INDEX = 9;
    private static final String KIND_WEB_HOME = "webHome";
    private static final String KIND_CSP = "csp";
    private static final String KIND_LIVE = "live";
    private static final String KIND_OTHER = "other";
    private static final String API_BUILTIN = "csp_Builtin";
    private static final long MAX_SUMMARY_REGISTRY_SIZE = 8L * 1024 * 1024;
    private static final Set<String> ROOT_SKIP_FIELDS = Set.of("enabled", "insertIndex", "homeKey", "items", "sites", "lives", "proxy");
    private static final List<String> ROOT_OTHER_FIELD_LIST = List.of("spider", "parses", "doh", "hosts", "headers", "rules", "ads", "flags", "wallpaper", "logo", "notice", "home", "parse", "urls", "msg");
    private static final Set<String> ROOT_OTHER_FIELDS = new HashSet<>(ROOT_OTHER_FIELD_LIST);
    private static final Set<String> ROOT_OTHER_STRING_FIELDS = Set.of("spider", "wallpaper", "logo", "notice", "home", "parse", "msg");
    private static final Pattern PYTHON_LIVE_METHOD = Pattern.compile("(?m)^\\s*def\\s+liveContent\\s*\\(");
    private static final Pattern JS_LIVE_METHOD = Pattern.compile("(?m)(?:^|[,{;]\\s*)(?:async\\s+)?liveContent\\s*(?:[:=]\\s*(?:async\\s*)?(?:function\\s*)?|\\()", Pattern.CASE_INSENSITIVE);

    public static Registry load() {
        String text = Path.read(registryFile());
        return objectFrom(text);
    }

    public static Registry objectFrom(String text) {
        try {
            return parse(text);
        } catch (Exception e) {
            return new Registry();
        }
    }

    public static Registry parse(String text) throws Exception {
        if (TextUtils.isEmpty(text)) return new Registry();
        JsonElement element = parseFlexible(text);
        if (element.isJsonNull()) return new Registry();
        if (element.isJsonArray()) return new Registry().items(itemsFrom(element.getAsJsonArray())).normalize();
        if (!element.isJsonObject()) throw new IllegalArgumentException("Invalid custom CSP JSON");
        JsonObject object = element.getAsJsonObject();
        if (object.has("items")) {
            Registry registry = App.gson().fromJson(object, Registry.class);
            if (registry != null && object.has("home") && !object.has("homeKey")) registry.setHomeKey(object.get("home").getAsString());
            if (registry != null && object.get("items").isJsonArray()) registry.setItems(itemsFrom(object.getAsJsonArray("items")));
            return registry == null ? new Registry() : registry.normalize();
        }
        Registry registry = new Registry();
        if (object.has("enabled")) registry.setEnabled(object.get("enabled").getAsBoolean());
        if (object.has("insertIndex")) registry.setInsertIndex(object.get("insertIndex").getAsInt());
        if (object.has("homeKey")) registry.setHomeKey(object.get("homeKey").getAsString());
        else if (object.has("home")) registry.setHomeKey(object.get("home").getAsString());
        registry.items(itemsFrom(object));
        return registry.normalize();
    }

    public static JsonElement parseFlexible(String text) {
        String value = stripTrailingCommas(text);
        try {
            return JsonParser.parseString(value);
        } catch (Exception e) {
            if (!looksLikeRootFields(value)) throw e;
            return JsonParser.parseString("{" + stripTrailingCommas(value) + "}");
        }
    }

    private static boolean looksLikeRootFields(String value) {
        if (TextUtils.isEmpty(value) || value.startsWith("{") || value.startsWith("[")) return false;
        return value.startsWith("\"") && value.contains("\":");
    }

    private static String stripTrailingCommas(String text) {
        String value = text == null ? "" : text.trim();
        if (TextUtils.isEmpty(value)) return "";
        StringBuilder builder = new StringBuilder(value.length());
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (inString) {
                builder.append(c);
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') {
                inString = true;
                builder.append(c);
                continue;
            }
            if (c == ',') {
                int next = nextNonWhitespace(value, i + 1);
                if (next >= 0 && (value.charAt(next) == ']' || value.charAt(next) == '}')) continue;
            }
            builder.append(c);
        }
        value = builder.toString().trim();
        while (value.endsWith(",") || value.endsWith(";") || value.endsWith("；")) value = value.substring(0, value.length() - 1).trim();
        return value;
    }

    private static int nextNonWhitespace(String text, int start) {
        for (int i = start; i < text.length(); i++) if (!Character.isWhitespace(text.charAt(i))) return i;
        return -1;
    }

    private static List<Item> itemsFrom(JsonObject object) {
        List<Item> items = new ArrayList<>();
        if (object.has("sites") && object.get("sites").isJsonArray()) items.addAll(itemsFrom(object.getAsJsonArray("sites"), false));
        if (object.has("lives") && object.get("lives").isJsonArray()) items.addAll(itemsFrom(object.getAsJsonArray("lives"), true));
        items.addAll(othersFrom(object, !items.isEmpty()));
        if (items.isEmpty()) items.add(itemFrom(object, isLiveObject(object)));
        return items;
    }

    private static List<Item> othersFrom(JsonObject object, boolean rootConfig) {
        if (!rootConfig && !hasRootOtherField(object)) return Collections.emptyList();
        if (!rootConfig && (object.has("key") || object.has("site") || object.has("live"))) return Collections.emptyList();
        List<Item> items = new ArrayList<>();
        for (String key : object.keySet()) {
            if (ROOT_SKIP_FIELDS.contains(key)) continue;
            items.add(otherItem(key, object.get(key)));
        }
        return items;
    }

    private static Item otherItem(String key, JsonElement value) {
        Item item = new Item();
        item.setKind(KIND_OTHER);
        item.setName(key);
        item.setOther(key, value);
        return item.normalize();
    }

    private static boolean hasRootOtherField(JsonObject object) {
        for (String key : ROOT_OTHER_FIELDS) if (object.has(key)) return true;
        return false;
    }

    private static List<Item> itemsFrom(JsonArray array) {
        return itemsFrom(array, null);
    }

    private static List<Item> itemsFrom(JsonArray array, Boolean live) {
        List<Item> items = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            items.add(itemFrom(object, live == null ? isLiveObject(object) : live));
        }
        return items;
    }

    private static Item itemFrom(JsonObject object, boolean live) {
        Item item = App.gson().fromJson(object, Item.class);
        if (live) {
            item.setKind(KIND_LIVE);
            if (!object.has("live")) item.setLive(object.deepCopy());
        } else if (isOtherObject(object) || (!object.has("site") && !object.has("key") && hasRootOtherField(object))) {
            item.setKind(KIND_OTHER);
            if (!object.has("other") || !object.get("other").isJsonObject()) item.setOther(otherObjectFromItem(object));
        } else if (!isOtherObject(object) && !object.has("site")) {
            item.setSite(object.deepCopy());
        }
        return item.normalize();
    }

    private static JsonObject otherObjectFromItem(JsonObject object) {
        JsonObject other = new JsonObject();
        Set<String> meta = Set.of("id", "name", "kind", "enabled", "site", "live", "other", "webHome");
        for (String key : object.keySet()) {
            if (meta.contains(key) || ROOT_SKIP_FIELDS.contains(key)) continue;
            other.add(key, object.get(key).deepCopy());
        }
        return other;
    }

    private static boolean isOtherObject(JsonObject object) {
        if (object.has("kind") && object.get("kind").isJsonPrimitive()) return KIND_OTHER.equals(object.get("kind").getAsString());
        return object.has("other") && object.get("other").isJsonObject();
    }

    private static boolean isLiveObject(JsonObject object) {
        if (object.has("kind") && object.get("kind").isJsonPrimitive()) return KIND_LIVE.equals(object.get("kind").getAsString());
        if (object.has("live") && object.get("live").isJsonObject()) return true;
        if (object.has("site") || object.has("key")) return false;
        if (object.has("url") || object.has("groups") || object.has("epg")) return true;
        String api = Json.safeString(object, "api");
        return hasLocalLiveMethod(api) || hasLiveScriptConvention(api);
    }

    private static boolean hasLocalLiveMethod(String api) {
        if (TextUtils.isEmpty(api)) return false;
        try {
            String value = api.trim();
            value = value.replace("clan://", "file://tvbox/");
            String path;
            if (value.startsWith("file://")) path = value.substring("file://".length());
            else {
                Uri uri = Uri.parse(value);
                if (!isLocalHost(uri.getHost()) || uri.getPath() == null || !uri.getPath().startsWith("/file/")) return false;
                path = uri.getPath().substring("/file/".length());
            }
            File file = Path.local(path);
            if (!file.isFile()) return false;
            String script = Path.read(file);
            boolean live = hasLiveMethod(api, script);
            if (live) SpiderDebug.log("custom-csp", "recognized live spider by method api=%s", api);
            return live;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isLocalHost(String host) {
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host);
    }

    public static boolean isRemoteScript(String api) {
        if (TextUtils.isEmpty(api)) return false;
        try {
            Uri uri = Uri.parse(api.trim());
            String scheme = uri.getScheme();
            String path = uri.getPath();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || TextUtils.isEmpty(path)) return false;
            String lower = path.toLowerCase(Locale.ROOT);
            return lower.endsWith(".py") || lower.endsWith(".js");
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasLiveMethod(String api, String script) {
        if (TextUtils.isEmpty(api) || TextUtils.isEmpty(script)) return false;
        String path;
        try {
            path = Uri.parse(api.trim()).getPath();
        } catch (Exception e) {
            path = api;
        }
        String lower = TextUtils.isEmpty(path) ? api.toLowerCase(Locale.ROOT) : path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".py")) return PYTHON_LIVE_METHOD.matcher(script).find();
        if (lower.endsWith(".js")) return JS_LIVE_METHOD.matcher(script).find();
        return false;
    }

    private static boolean hasLiveScriptConvention(String api) {
        if (TextUtils.isEmpty(api)) return false;
        String value = api.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        boolean script = value.endsWith(".py") || value.endsWith(".js");
        boolean liveDirectory = value.contains("/py_live/") || value.contains("/js_live/") || value.contains("/live_spider/");
        if (script && liveDirectory) SpiderDebug.log("custom-csp", "recognized live spider by path convention api=%s", api);
        return script && liveDirectory;
    }

    public static void save(Registry registry) {
        ensureFileAccess();
        Set<String> before = itemIds(load());
        File file = registryFile();
        String text = App.gson().toJson(registry.normalize());
        Path.write(file, text.getBytes(StandardCharsets.UTF_8));
        ensureWritten(file, text);
        cleanupRemovedFiles(before, itemIds(registry));
    }

    public static void writePage(String id, String code) {
        ensureFileAccess();
        File file = file(id, "index.html");
        String text = code == null ? "" : code;
        Path.write(file, text.getBytes(StandardCharsets.UTF_8));
        ensureWritten(file, text);
    }

    public static void copyPage(File source, String id) {
        ensureFileAccess();
        File file = file(id, "index.html");
        Path.copy(source, file);
        if (!source.exists() || !file.exists() || !Arrays.equals(Path.readToByte(source), Path.readToByte(file))) throw new IllegalStateException(App.get().getString(R.string.setting_custom_csp_save_failed, file.getAbsolutePath()));
    }

    public static String copyFile(File source, String id) {
        ensureFileAccess();
        if (source == null || !source.isFile()) throw new IllegalStateException(App.get().getString(R.string.setting_custom_csp_save_failed, ""));
        File file = file(id, source.getName());
        try {
            if (!source.getCanonicalPath().equals(file.getCanonicalPath())) Path.copy(source, file);
        } catch (Exception e) {
            Path.copy(source, file);
        }
        if (!file.exists() || !Arrays.equals(Path.readToByte(source), Path.readToByte(file))) throw new IllegalStateException(App.get().getString(R.string.setting_custom_csp_save_failed, file.getAbsolutePath()));
        return localUrl(id, source.getName());
    }

    public static void deleteFiles(String id) {
        if (TextUtils.isEmpty(id)) return;
        try {
            File root = dir().getCanonicalFile();
            File target = new File(root, id).getCanonicalFile();
            if (!isInside(root, target)) return;
            Path.clear(target);
        } catch (Throwable e) {
            SpiderDebug.log("custom-csp", "delete files failed id=%s error=%s", id, e.toString());
        }
    }

    private static boolean isInside(File root, File target) throws Exception {
        String rootPath = root.getCanonicalPath();
        String targetPath = target.getCanonicalPath();
        return targetPath.equals(rootPath) || targetPath.startsWith(rootPath + File.separator);
    }

    private static Set<String> itemIds(Registry registry) {
        Set<String> ids = new HashSet<>();
        if (registry == null) return ids;
        for (Item item : registry.getItems()) if (item != null && !TextUtils.isEmpty(item.getId())) ids.add(item.getId());
        return ids;
    }

    private static void cleanupRemovedFiles(Set<String> before, Set<String> after) {
        for (String id : before) if (!after.contains(id)) deleteFiles(id);
    }

    private static void ensureFileAccess() {
        if (!Setting.hasFileAccess()) throw new IllegalStateException(App.get().getString(R.string.setting_custom_csp_permission_required));
    }

    private static void ensureWritten(File file, String text) {
        if (!file.exists() || !TextUtils.equals(Path.read(file), text)) throw new IllegalStateException(App.get().getString(R.string.setting_custom_csp_save_failed, file.getAbsolutePath()));
    }

    public static File dir() {
        File dir = Path.root(DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File file(String id, String name) {
        return new File(new File(dir(), id), name);
    }

    public static String localUrl(String id, String name) {
        return "file://" + DIR + "/" + id + "/" + name;
    }

    public static Result inject(List<Site> sites) {
        Registry registry = load();
        if (!registry.isEnabled()) return Result.empty();
        Server.get().start();
        List<Site> items = registry.sites();
        if (items.isEmpty()) return Result.empty();
        for (Site site : items) sites.remove(site);
        int index = Math.max(0, Math.min(registry.getInsertIndex(), sites.size()));
        sites.addAll(index, items);
        Site home = items.stream().filter(site -> site.getKey().equals(registry.getHomeKey())).findFirst().orElse(new Site());
        return new Result(home);
    }

    public static void inject(List<Live> lives, String spider) {
        Registry registry = load();
        if (!registry.isEnabled()) return;
        List<Live> items = registry.lives(spider);
        if (items.isEmpty()) return;
        for (Live live : items) lives.remove(live);
        int index = Math.max(0, Math.min(registry.getInsertIndex(), lives.size()));
        lives.addAll(index, items);
    }

    public static void inject(JsonObject object) {
        Registry registry = load();
        if (!registry.isEnabled() || object == null) return;
        for (Item item : registry.getItems()) {
            if (!item.isEnabled() || !item.isOther() || !item.isValid()) continue;
            mergeRoot(object, item.getOther());
        }
    }

    private static void mergeRoot(JsonObject target, JsonObject source) {
        for (String key : source.keySet()) {
            if (ROOT_SKIP_FIELDS.contains(key) || "sites".equals(key) || "lives".equals(key)) continue;
            JsonElement value = source.get(key);
            if (value == null || value.isJsonNull()) continue;
            if (value.isJsonArray()) mergeArray(target, key, value.getAsJsonArray());
            else if (value.isJsonObject()) mergeObject(target, key, value.getAsJsonObject());
            else if (!target.has(key) || target.get(key).isJsonNull() || isEmptyPrimitive(target.get(key))) target.add(key, value.deepCopy());
        }
    }

    private static void mergeArray(JsonObject target, String key, JsonArray source) {
        if (source.size() == 0) return;
        JsonArray array = target.has(key) && target.get(key).isJsonArray() ? target.getAsJsonArray(key) : new JsonArray();
        Set<String> exists = new HashSet<>();
        for (JsonElement element : array) exists.add(App.gson().toJson(element));
        for (JsonElement element : source) {
            String id = App.gson().toJson(element);
            if (exists.add(id)) array.add(element.deepCopy());
        }
        target.add(key, array);
    }

    private static void mergeObject(JsonObject target, String key, JsonObject source) {
        if (source.size() == 0) return;
        if (!target.has(key) || !target.get(key).isJsonObject()) {
            target.add(key, source.deepCopy());
            return;
        }
        JsonObject object = target.getAsJsonObject(key);
        for (String child : source.keySet()) {
            JsonElement value = source.get(child);
            if (!object.has(child) || object.get(child).isJsonNull() || isEmptyPrimitive(object.get(child))) object.add(child, value.deepCopy());
        }
    }

    private static boolean isEmptyPrimitive(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() && TextUtils.isEmpty(element.getAsString());
    }

    public static boolean hasLives() {
        Registry registry = load();
        return registry.isEnabled() && registry.getItems().stream().anyMatch(item -> item.isEnabled() && item.isLive() && item.isValid());
    }

    public static int countEnabled() {
        return count().active();
    }

    public static int countItems() {
        return load().getItems().size();
    }

    public static Count count() {
        return count(load());
    }

    public static Count count(Registry registry) {
        if (registry == null) registry = new Registry();
        int enabled = 0;
        int active = 0;
        for (Item item : registry.getItems()) {
            if (!item.isEnabled()) continue;
            if (!item.isOther()) enabled++;
            if (registry.isEnabled() && item.isInjectable()) active++;
        }
        return new Count(active, enabled);
    }

    public static Status status() {
        try {
            File file = registryFile();
            if (file.isFile() && file.length() > MAX_SUMMARY_REGISTRY_SIZE) {
                SpiderDebug.log("custom-csp", "summary skipped registry too large size=%d", file.length());
                return Status.unavailable();
            }
            Registry registry = load();
            return new Status(true, registry.isEnabled(), count(registry));
        } catch (Throwable e) {
            SpiderDebug.log("custom-csp", "summary failed error=%s", e.toString());
            return Status.unavailable();
        }
    }

    public static Item createDefaultItem() {
        Item item = new Item();
        item.setId("local_" + System.currentTimeMillis() + "_" + Long.toHexString(System.nanoTime()));
        item.setKey(PREFIX + item.getId());
        item.setWebHome(true);
        item.setType(3);
        item.setApi(API_BUILTIN);
        return item;
    }

    public static Item createDefaultLiveItem() {
        Item item = new Item();
        item.setId("live_" + System.currentTimeMillis() + "_" + Long.toHexString(System.nanoTime()));
        item.setKind(KIND_LIVE);
        item.setType(0);
        item.setPlayerType(2);
        item.setUa("okhttp");
        return item;
    }

    public static Item createDefaultOtherItem() {
        return otherItem(ROOT_OTHER_FIELD_LIST.get(1), defaultOtherValue(ROOT_OTHER_FIELD_LIST.get(1)));
    }

    public static List<String> otherFields() {
        return new ArrayList<>(ROOT_OTHER_FIELD_LIST);
    }

    public static JsonElement defaultOtherValue(String key) {
        return ROOT_OTHER_STRING_FIELDS.contains(key) ? new JsonPrimitive("") : new JsonArray();
    }

    public static boolean isOtherStringField(String key) {
        return ROOT_OTHER_STRING_FIELDS.contains(key);
    }

    public record Count(int active, int enabled) {
    }

    public record Status(boolean available, boolean enabled, Count count) {

        public static Status unavailable() {
            return new Status(false, false, new Count(0, 0));
        }
    }

    private static File registryFile() {
        return new File(dir(), REGISTRY);
    }

    public record Result(Site home) {

        public static Result empty() {
            return new Result(new Site());
        }
    }

    public static class Registry {

        @SerializedName("enabled")
        private Boolean enabled;
        @SerializedName("insertIndex")
        private Integer insertIndex;
        @SerializedName("homeKey")
        private String homeKey;
        @SerializedName("items")
        private List<Item> items;

        public Registry normalize() {
            if (items == null) items = new ArrayList<>();
            items.removeIf(Objects::isNull);
            List<Item> expanded = new ArrayList<>();
            for (Item item : items) {
                item.normalize();
                expanded.addAll(item.splitOther());
            }
            items = expanded;
            Set<String> ids = new HashSet<>();
            Set<String> keys = new HashSet<>();
            List<Item> unique = new ArrayList<>();
            for (Item item : items) {
                String oldKey = item.peekKey();
                item.normalize();
                item.ensureUniqueKey(keys);
                if (!TextUtils.isEmpty(oldKey) && oldKey.equals(homeKey) && !oldKey.equals(item.getKey())) homeKey = item.getKey();
                if (!ids.add(item.getId())) continue;
                unique.add(item);
            }
            items = unique;
            if (homeKey == null) homeKey = "";
            return this;
        }

        public boolean isEnabled() {
            return enabled == null || enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getInsertIndex() {
            return insertIndex == null ? 0 : Math.max(0, Math.min(MAX_INSERT_INDEX, insertIndex));
        }

        public void setInsertIndex(int insertIndex) {
            this.insertIndex = Math.max(0, Math.min(MAX_INSERT_INDEX, insertIndex));
        }

        public String getHomeKey() {
            return TextUtils.isEmpty(homeKey) ? "" : homeKey;
        }

        public void setHomeKey(String homeKey) {
            this.homeKey = homeKey;
        }

        public List<Item> getItems() {
            return items == null ? Collections.emptyList() : items;
        }

        public void setItems(List<Item> items) {
            this.items = items;
        }

        public Registry items(List<Item> items) {
            setItems(items);
            return this;
        }

        public Item addDefault() {
            Item item = createDefaultItem();
            if (items == null) items = new ArrayList<>();
            items.add(item);
            return item;
        }

        public List<Site> sites() {
            return getItems().stream().filter(Item::isEnabled).filter(item -> !item.isLive() && !item.isOther()).filter(Item::isValid).map(Item::site).filter(site -> !site.isEmpty()).toList();
        }

        public List<Live> lives(String spider) {
            return getItems().stream().filter(Item::isEnabled).filter(Item::isLive).filter(Item::isValid).map(item -> item.live(spider)).filter(live -> !live.isEmpty()).toList();
        }
    }

    public static class Item {

        @SerializedName("id")
        private String id;
        @SerializedName("key")
        private String key;
        @SerializedName("name")
        private String name;
        @SerializedName("enabled")
        private Boolean enabled;
        @SerializedName("kind")
        private String kind;
        @SerializedName("webHome")
        private Boolean webHome;
        @SerializedName("type")
        private Integer type;
        @SerializedName("playerType")
        private Integer playerType;
        @SerializedName("api")
        private String api;
        @SerializedName("ext")
        @JsonAdapter(ExtAdapter.class)
        private String ext;
        @SerializedName("jar")
        private String jar;
        @SerializedName("homePage")
        private String homePage;
        @SerializedName("extensions")
        private JsonElement extensions;
        @SerializedName("click")
        private String click;
        @SerializedName("playUrl")
        private String playUrl;
        @SerializedName("url")
        private String url;
        @SerializedName("logo")
        private String logo;
        @SerializedName("epg")
        private String epg;
        @SerializedName("ua")
        private String ua;
        @SerializedName("origin")
        private String origin;
        @SerializedName("referer")
        private String referer;
        @SerializedName("timeZone")
        private String timeZone;
        @SerializedName("timeout")
        private Integer timeout;
        @SerializedName("hide")
        private Integer hide;
        @SerializedName("searchable")
        private Integer searchable;
        @SerializedName("changeable")
        private Integer changeable;
        @SerializedName("quickSearch")
        private Integer quickSearch;
        @SerializedName("site")
        private JsonObject site;
        @SerializedName("live")
        private JsonObject live;
        @SerializedName("other")
        private JsonObject other;
        private transient String extensionsText;
        private transient boolean extensionsInvalid;
        private transient Boolean extensionsExpanded;

        public Item normalize() {
            normalizeKind();
            if (isOther()) name = TextUtils.isEmpty(getOtherKey()) ? "other" : getOtherKey();
            if (TextUtils.isEmpty(id)) id = isOther() ? "other_" + Util.md5(App.gson().toJson(getOther())).substring(0, 8) : Util.md5(getKey() + getName() + getApi() + getHomePage() + getUrl());
            if (isOther()) return this;
            if (!isLive() && TextUtils.isEmpty(key)) {
                String siteKey = getSiteString("key");
                key = TextUtils.isEmpty(siteKey) ? PREFIX + id : siteKey;
            }
            if (!isLive() && shouldUseNameKey(key)) key = keyFromName(getName(), id);
            return this;
        }

        private void normalizeKind() {
            if (!TextUtils.isEmpty(kind)) return;
            if (other != null) kind = KIND_OTHER;
            else
            if (live != null) kind = KIND_LIVE;
            else kind = inferWebHome() ? KIND_WEB_HOME : KIND_CSP;
        }

        public boolean isEnabled() {
            return enabled == null || enabled;
        }

        public boolean isWebHome() {
            if (isLive() || isOther()) return false;
            return KIND_WEB_HOME.equals(kind) || (webHome != null && webHome) || isWebHomeByFields();
        }

        private boolean isWebHomeByFields() {
            return webHome == null ? inferWebHome() : webHome;
        }

        private boolean inferWebHome() {
            String apiValue = !TextUtils.isEmpty(api) ? api.trim() : getSiteString("api");
            String homeValue = !TextUtils.isEmpty(homePage) ? homePage.trim() : getSiteString("homePage");
            if (TextUtils.isEmpty(homeValue)) homeValue = getSiteHomeAlias();
            return !homeValue.isEmpty() && isBuiltinApi(apiValue);
        }

        public boolean isLive() {
            normalizeKind();
            return KIND_LIVE.equals(kind);
        }

        public boolean isOther() {
            normalizeKind();
            return KIND_OTHER.equals(kind);
        }

        public String getKind() {
            normalizeKind();
            return isOther() ? KIND_OTHER : isLive() ? KIND_LIVE : isWebHome() ? KIND_WEB_HOME : KIND_CSP;
        }

        public boolean isValid() {
            if (isOther()) return other != null && other.size() > 0;
            if (isLive()) return !getName().isEmpty() && (!getUrl().isEmpty() || !getApi().isEmpty() || hasLiveGroups());
            return isWebHome() ? !getHomePage().isEmpty() : !getApi().isEmpty();
        }

        public boolean isInjectable() {
            return !isOther() && isValid();
        }

        public String getDefaultName() {
            return isOther() ? "其他" : isLive() ? "直播" : isWebHome() ? "WebHome" : "通用 CSP";
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setWebHome(boolean webHome) {
            this.webHome = webHome;
            setKind(webHome ? KIND_WEB_HOME : KIND_CSP);
        }

        public void setKind(String kind) {
            boolean wasLive = isLive();
            this.kind = KIND_OTHER.equals(kind) ? KIND_OTHER : KIND_LIVE.equals(kind) ? KIND_LIVE : KIND_WEB_HOME.equals(kind) ? KIND_WEB_HOME : KIND_CSP;
            this.webHome = KIND_WEB_HOME.equals(this.kind);
            if (KIND_OTHER.equals(this.kind)) {
                key = null;
                site = null;
                live = null;
                other = other == null ? new JsonObject() : other;
                return;
            }
            other = null;
            if (KIND_LIVE.equals(this.kind) && live == null) live = new JsonObject();
            if (KIND_LIVE.equals(this.kind)) {
                key = null;
                if (!wasLive) {
                    if (type == null) type = 0;
                    if (playerType == null) playerType = 2;
                    if (TextUtils.isEmpty(ua)) ua = "okhttp";
                }
                homePage = null;
                playUrl = null;
                hide = null;
                searchable = null;
                changeable = null;
                quickSearch = null;
                site = null;
            } else {
                live = null;
                if (wasLive) type = null;
                url = null;
                logo = null;
                epg = null;
                ua = null;
                playerType = null;
                origin = null;
                referer = null;
                timeZone = null;
                timeout = null;
                if (isWebHome()) {
                    if (type == null) type = 3;
                    if (TextUtils.isEmpty(api) || isBuiltinApi(api)) api = API_BUILTIN;
                }
            }
        }

        public String getId() {
            normalize();
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setKey(String key) {
            this.key = key;
            putSite("key", key);
        }

        public void setName(String name) {
            this.name = name;
            if (isOther()) return;
            if (!isLive() && shouldUseNameKey(getKey())) setKey(keyFromName(getName(), getId()));
            if (isLive()) putLive("name", name);
            else putSite("name", name);
        }

        public void setType(Integer type) {
            this.type = type;
            if (isLive()) putLive("type", type);
            else putSite("type", type);
        }

        public Integer getType() {
            return type == null ? isLive() ? getLiveInt("type", 0) : getSiteInt("type", 3) : type;
        }

        public void setPlayerType(Integer playerType) {
            this.playerType = playerType;
            if (playerType == null) removeLive("playerType");
            else putLive("playerType", playerType);
        }

        public Integer getPlayerType() {
            return playerType == null ? getLiveInt("playerType", null) : playerType;
        }

        public Integer getHide() {
            return hide == null ? getSiteInt("hide", 0) : hide;
        }

        public Integer getSearchable() {
            return searchable == null ? getSiteInt("searchable", isWebHome() ? 0 : 1) : searchable;
        }

        public Integer getChangeable() {
            return changeable == null ? getSiteInt("changeable", 1) : changeable;
        }

        public Integer getQuickSearch() {
            return quickSearch == null ? getSiteInt("quickSearch", isWebHome() ? 0 : 1) : quickSearch;
        }

        public void setApi(String api) {
            this.api = api;
            if (isLive()) putLive("api", api);
            else putSite("api", api);
        }

        public void setExt(String ext) {
            this.ext = ext;
            if (isLive()) putLive("ext", ext);
            else putSite("ext", ext);
        }

        public void setJar(String jar) {
            this.jar = jar;
            if (isLive()) putLive("jar", jar);
            else putSite("jar", jar);
        }

        public void setHomePage(String homePage) {
            this.homePage = homePage;
            putSite("homePage", homePage);
        }

        public void setExtensionsText(String text) {
            extensionsText = text == null ? "" : text.trim();
            extensionsInvalid = false;
            if (TextUtils.isEmpty(extensionsText)) {
                extensions = null;
                removeSite("extensions");
                return;
            }
            try {
                extensions = parseExtensions(extensionsText);
                putSite("extensions", extensions);
            } catch (Exception e) {
                extensionsInvalid = true;
            }
        }

        public void setExtensionsExpanded(boolean expanded) {
            extensionsExpanded = expanded;
            if (!expanded) setExtensionsText("");
        }

        public void setClick(String click) {
            this.click = click;
            if (isLive()) putLive("click", click);
            else putSite("click", click);
        }

        public void setPlayUrl(String playUrl) {
            this.playUrl = playUrl;
            putSite("playUrl", playUrl);
        }

        public void setUrl(String url) {
            this.url = url;
            putLive("url", url);
        }

        public void setLogo(String logo) {
            this.logo = logo;
            putLive("logo", logo);
        }

        public void setEpg(String epg) {
            this.epg = epg;
            putLive("epg", epg);
        }

        public void setUa(String ua) {
            this.ua = ua;
            putLive("ua", ua);
        }

        public void setOrigin(String origin) {
            this.origin = origin;
            putLive("origin", origin);
        }

        public void setReferer(String referer) {
            this.referer = referer;
            putLive("referer", referer);
        }

        public void setTimeZone(String timeZone) {
            this.timeZone = timeZone;
            putLive("timeZone", timeZone);
        }

        public void setTimeout(Integer timeout) {
            this.timeout = timeout;
            if (timeout == null) removeLive("timeout");
            else putLive("timeout", timeout);
        }

        public void setHide(Integer hide) {
            this.hide = hide;
            putSite("hide", hide);
        }

        public void setSearchable(Integer searchable) {
            this.searchable = searchable;
            putSite("searchable", searchable);
        }

        public void setChangeable(Integer changeable) {
            this.changeable = changeable;
            putSite("changeable", changeable);
        }

        public void setQuickSearch(Integer quickSearch) {
            this.quickSearch = quickSearch;
            putSite("quickSearch", quickSearch);
        }

        public void setSite(JsonObject site) {
            this.site = site;
        }

        public void setLive(JsonObject live) {
            this.live = live;
        }

        public void setOther(JsonObject other) {
            this.other = other;
        }

        public void setOther(String key, JsonElement value) {
            String field = TextUtils.isEmpty(key) ? ROOT_OTHER_FIELD_LIST.get(1) : key.trim();
            JsonObject object = new JsonObject();
            object.add(field, value == null || value.isJsonNull() ? defaultOtherValue(field) : value.deepCopy());
            this.other = object;
            this.name = field;
        }

        public void setOtherKey(String key) {
            String oldKey = getOtherKey();
            JsonElement value = getOtherValue();
            if (value == null || value.isJsonNull()) value = defaultOtherValue(key);
            setOther(key, value);
            if (!TextUtils.equals(oldKey, key)) id = null;
        }

        public void setOtherValue(JsonElement value) {
            setOther(getOtherKey(), value);
            id = null;
        }

        public String getKey() {
            return !TextUtils.isEmpty(key) ? key.trim() : getSiteString("key");
        }

        public String getName() {
            if (isOther()) return TextUtils.isEmpty(getOtherKey()) ? "other" : getOtherKey();
            String value = !TextUtils.isEmpty(name) ? name.trim() : isLive() ? getLiveString("name") : getSiteString("name");
            return TextUtils.isEmpty(value) ? getKey() : value;
        }

        public JsonObject getOther() {
            return other == null ? new JsonObject() : other;
        }

        public String getOtherKey() {
            if (other == null || other.size() == 0) return "";
            for (String key : other.keySet()) return key;
            return "";
        }

        public JsonElement getOtherValue() {
            String key = getOtherKey();
            return TextUtils.isEmpty(key) || other == null || !other.has(key) ? JsonNull.INSTANCE : other.get(key);
        }

        public String getOtherText() {
            JsonElement value = getOtherValue();
            return value == null || value.isJsonNull() ? "" : App.gson().toJson(value);
        }

        public String getOtherKeys() {
            if (other == null || other.size() == 0) return "";
            return String.join(", ", other.keySet());
        }

        public String getApi() {
            return !TextUtils.isEmpty(api) ? api.trim() : isLive() ? getLiveString("api") : getSiteString("api");
        }

        public String getExt() {
            return !TextUtils.isEmpty(ext) ? ext.trim() : isLive() ? getLiveString("ext") : getSiteString("ext");
        }

        public String getJar() {
            return !TextUtils.isEmpty(jar) ? jar.trim() : isLive() ? getLiveString("jar") : getSiteString("jar");
        }

        public String getHomePage() {
            String value = !TextUtils.isEmpty(homePage) ? homePage.trim() : getSiteString("homePage");
            return TextUtils.isEmpty(value) ? getSiteHomeAlias() : value;
        }

        public String getExtensionsText() {
            if (extensionsText != null) return extensionsText;
            JsonElement element = getExtensions();
            if (element == null || element.isJsonNull()) return "";
            if (element.isJsonPrimitive()) return element.getAsString().trim();
            return App.gson().toJson(element);
        }

        public boolean hasInvalidExtensions() {
            return extensionsInvalid;
        }

        public boolean isExtensionsExpanded() {
            return extensionsExpanded == null ? !TextUtils.isEmpty(getExtensionsText()) : extensionsExpanded;
        }

        public String getClick() {
            return !TextUtils.isEmpty(click) ? click.trim() : isLive() ? getLiveString("click") : getSiteString("click");
        }

        public String getPlayUrl() {
            return !TextUtils.isEmpty(playUrl) ? playUrl.trim() : getSiteString("playUrl");
        }

        public String getUrl() {
            return !TextUtils.isEmpty(url) ? url.trim() : getLiveString("url");
        }

        public String getLogo() {
            return !TextUtils.isEmpty(logo) ? logo.trim() : getLiveString("logo");
        }

        public String getEpg() {
            return !TextUtils.isEmpty(epg) ? epg.trim() : getLiveString("epg");
        }

        public String getUa() {
            return !TextUtils.isEmpty(ua) ? ua.trim() : getLiveString("ua");
        }

        public String getOrigin() {
            return !TextUtils.isEmpty(origin) ? origin.trim() : getLiveString("origin");
        }

        public String getReferer() {
            return !TextUtils.isEmpty(referer) ? referer.trim() : getLiveString("referer");
        }

        public String getTimeZone() {
            return !TextUtils.isEmpty(timeZone) ? timeZone.trim() : getLiveString("timeZone");
        }

        public Integer getTimeout() {
            return timeout == null ? getLiveInt("timeout", null) : timeout;
        }

        public Site site() {
            if (isLive() || isOther()) return new Site();
            normalize();
            if (site != null) return siteFromJson();
            Site site = new Site();
            boolean webHomeOnly = isWebHome();
            site.setKey(getKey());
            site.setName(getName());
            site.setType(getType());
            site.setApi(webHomeOnly ? API_BUILTIN : UrlUtil.convert(getApi()));
            site.setExt(webHomeOnly ? "" : UrlUtil.convert(getExt()));
            site.setJar(webHomeOnly ? "" : getJar());
            site.setHomePage(UrlUtil.convert(getHomePage()));
            site.setClick(webHomeOnly ? "" : getClick());
            site.setPlayUrl(webHomeOnly ? "" : getPlayUrl());
            site.setHide(getHide());
            site.setSearchable(getSearchable());
            site.setChangeable(getChangeable());
            site.setQuickSearch(getQuickSearch());
            if (webHomeOnly && getExtensions() != null) site.setExtensions(getExtensions().deepCopy());
            site.setStyle(Style.rect());
            return site;
        }

        public Live live(String spider) {
            normalize();
            return Live.objectFrom(liveObject(), spider);
        }

        private JsonObject liveObject() {
            JsonObject object = live == null ? new JsonObject() : live.deepCopy();
            if (!TextUtils.isEmpty(name)) object.addProperty("name", name.trim());
            else if (!object.has("name")) object.addProperty("name", getName());
            if (type != null) object.addProperty("type", type);
            if (playerType != null) object.addProperty("playerType", playerType);
            if (!TextUtils.isEmpty(url)) object.addProperty("url", url.trim());
            if (!TextUtils.isEmpty(api)) object.addProperty("api", api.trim());
            if (!TextUtils.isEmpty(ext)) object.addProperty("ext", ext.trim());
            if (!TextUtils.isEmpty(jar)) object.addProperty("jar", jar.trim());
            if (!TextUtils.isEmpty(click)) object.addProperty("click", click.trim());
            if (!TextUtils.isEmpty(logo)) object.addProperty("logo", logo.trim());
            if (!TextUtils.isEmpty(epg)) object.addProperty("epg", epg.trim());
            if (!TextUtils.isEmpty(ua)) object.addProperty("ua", ua.trim());
            if (!TextUtils.isEmpty(origin)) object.addProperty("origin", origin.trim());
            if (!TextUtils.isEmpty(referer)) object.addProperty("referer", referer.trim());
            if (!TextUtils.isEmpty(timeZone)) object.addProperty("timeZone", timeZone.trim());
            if (timeout != null) object.addProperty("timeout", timeout);
            return object;
        }

        private Site siteFromJson() {
            JsonObject object = site.deepCopy();
            sanitizeSiteObject(object);
            if (!TextUtils.isEmpty(key)) object.addProperty("key", key.trim());
            else if (!object.has("key")) object.addProperty("key", getKey());
            if (!TextUtils.isEmpty(name)) object.addProperty("name", name.trim());
            if (!TextUtils.isEmpty(api)) object.addProperty("api", api.trim());
            if (!TextUtils.isEmpty(ext)) object.addProperty("ext", ext.trim());
            if (!TextUtils.isEmpty(jar)) object.addProperty("jar", jar.trim());
            if (!TextUtils.isEmpty(homePage)) object.addProperty("homePage", homePage.trim());
            if (!TextUtils.isEmpty(click)) object.addProperty("click", click.trim());
            if (!TextUtils.isEmpty(playUrl)) object.addProperty("playUrl", playUrl.trim());
            if (type != null) object.addProperty("type", type);
            if (hide != null) object.addProperty("hide", hide);
            if (searchable != null) object.addProperty("searchable", searchable);
            if (changeable != null) object.addProperty("changeable", changeable);
            if (quickSearch != null) object.addProperty("quickSearch", quickSearch);
            if (isWebHome()) {
                object.addProperty("type", getType());
                object.addProperty("api", API_BUILTIN);
                object.remove("ext");
                object.remove("jar");
                object.remove("click");
                object.remove("playUrl");
                JsonElement value = getExtensions();
                if (value == null || value.isJsonNull()) object.remove("extensions");
                else object.add("extensions", value.deepCopy());
            }
            Site result = Site.objectFrom(object, getJar());
            boolean webHomeOnly = isWebHome() || isBuiltinApi(result.getApi()) && !result.getHomePage().isEmpty();
            if (webHomeOnly && searchable == null && !object.has("searchable")) result.setSearchable(0);
            if (webHomeOnly && quickSearch == null && !object.has("quickSearch")) result.setQuickSearch(0);
            return result;
        }

        private void sanitizeSiteObject(JsonObject object) {
            object.remove("kind");
            object.remove("enabled");
            object.remove("live");
            if (object.has("webHome") && object.get("webHome").isJsonPrimitive() && object.getAsJsonPrimitive("webHome").isBoolean()) object.remove("webHome");
        }

        private String getSiteString(String key) {
            return getString(site, key);
        }

        private String getSiteHomeAlias() {
            if (site == null || !site.has("webHome") || !site.get("webHome").isJsonPrimitive()) return "";
            if (site.getAsJsonPrimitive("webHome").isBoolean()) return "";
            return site.get("webHome").getAsString().trim();
        }

        private String getLiveString(String key) {
            return getString(live, key);
        }

        private JsonElement getExtensions() {
            if (extensions != null) return extensions;
            if (site == null || !site.has("extensions")) return null;
            return site.get("extensions");
        }

        private JsonElement parseExtensions(String text) {
            String value = text == null ? "" : text.trim();
            JsonArray array = new JsonArray();
            if (!value.startsWith("[") && !value.startsWith("{") && !value.startsWith("\"")) {
                array.add(value);
                return array;
            }
            JsonElement element = JsonParser.parseString(value);
            if (element.isJsonArray()) return element;
            if (element.isJsonObject() && element.getAsJsonObject().has("extensions")) return element.getAsJsonObject().get("extensions");
            array.add(element);
            return array;
        }

        private boolean isBuiltinApi(String value) {
            return TextUtils.isEmpty(value) || API_BUILTIN.equals(value.trim());
        }

        private String getString(JsonObject object, String key) {
            if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return "";
            return object.get(key).getAsString().trim();
        }

        private int getSiteInt(String key, int fallback) {
            try {
                if (site == null || !site.has(key) || !site.get(key).isJsonPrimitive()) return fallback;
                return site.get(key).getAsInt();
            } catch (Exception e) {
                return fallback;
            }
        }

        private Integer getLiveInt(String key, Integer fallback) {
            try {
                if (live == null || !live.has(key) || !live.get(key).isJsonPrimitive()) return fallback;
                return live.get(key).getAsInt();
            } catch (Exception e) {
                return fallback;
            }
        }

        private boolean hasLiveGroups() {
            return live != null && live.has("groups") && live.get("groups").isJsonArray() && !live.getAsJsonArray("groups").isEmpty();
        }

        private String peekKey() {
            if (!TextUtils.isEmpty(key)) return key.trim();
            if (site == null || !site.has("key") || !site.get("key").isJsonPrimitive()) return "";
            return site.get("key").getAsString().trim();
        }

        private List<Item> splitOther() {
            if (!isOther() || other == null || other.size() <= 1) return Collections.singletonList(this);
            List<Item> items = new ArrayList<>();
            String baseId = TextUtils.isEmpty(id) ? "other_" + Util.md5(App.gson().toJson(other)).substring(0, 8) : id;
            for (String key : other.keySet()) {
                Item item = new Item();
                item.id = baseId + "_" + Util.md5(key).substring(0, 6);
                item.enabled = enabled;
                item.kind = KIND_OTHER;
                item.setOther(key, other.get(key));
                items.add(item.normalize());
            }
            return items;
        }

        private void ensureUniqueKey(Set<String> keys) {
            if (isLive() || isOther()) return;
            String base = getKey();
            String key = base;
            int index = 2;
            while (keys.contains(key)) key = base + "_" + index++;
            setKey(key);
            keys.add(key);
        }

        private boolean shouldUseNameKey(String key) {
            return TextUtils.isEmpty(key) || key.startsWith(PREFIX);
        }

        private String keyFromName(String name, String fallback) {
            String slug = slug(name);
            if (TextUtils.isEmpty(slug)) slug = TextUtils.isEmpty(fallback) ? Util.md5(String.valueOf(System.nanoTime())).substring(0, 8) : fallback;
            return PREFIX + slug;
        }

        private String slug(String text) {
            String value = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            StringBuilder builder = new StringBuilder();
            boolean underscore = false;
            for (int i = 0; i < value.length(); ) {
                int codePoint = value.codePointAt(i);
                if (Character.isLetterOrDigit(codePoint) || codePoint == '-' || codePoint == '.') {
                    builder.appendCodePoint(codePoint);
                    underscore = false;
                } else if (!underscore && builder.length() > 0) {
                    builder.append('_');
                    underscore = true;
                }
                i += Character.charCount(codePoint);
            }
            while (builder.length() > 0 && builder.charAt(builder.length() - 1) == '_') builder.deleteCharAt(builder.length() - 1);
            return builder.toString();
        }

        private void putSite(String key, String value) {
            if (site == null) return;
            if (TextUtils.isEmpty(value) && site.has(key) && !site.get(key).isJsonPrimitive()) return;
            site.addProperty(key, value);
        }

        private void putSite(String key, Integer value) {
            if (site != null && value != null) site.addProperty(key, value);
        }

        private void putSite(String key, JsonElement value) {
            if (site != null && value != null) site.add(key, value.deepCopy());
        }

        private void removeSite(String key) {
            if (site != null) site.remove(key);
        }

        private void putLive(String key, String value) {
            if (live == null) return;
            if (TextUtils.isEmpty(value)) {
                removeLive(key);
                return;
            }
            live.addProperty(key, value);
        }

        private void putLive(String key, Integer value) {
            if (live != null && value != null) live.addProperty(key, value);
        }

        private void removeLive(String key) {
            if (live != null) live.remove(key);
        }
    }
}
