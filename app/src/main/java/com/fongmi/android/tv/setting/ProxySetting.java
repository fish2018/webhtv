package com.fongmi.android.tv.setting;

import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.bean.Proxy;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

public class ProxySetting {

    private static final String NAME = "app";

    public static void apply() {
        OkHttp.selector().remove(NAME);
        if (!Setting.isShellProxy()) return;
        List<Proxy> rules = getRules();
        if (rules.isEmpty()) return;
        OkHttp.selector().addAll(rules);
        SpiderDebug.log("proxy", "app proxy enabled rules=%s", rules.size());
    }

    public static List<Proxy> getRules() {
        String rules = Setting.getShellProxyRules().trim();
        if (!TextUtils.isEmpty(rules)) return parse(rules, Setting.getShellProxyUrl().trim());
        return legacy();
    }

    private static List<Proxy> legacy() {
        String url = Setting.getShellProxyUrl().trim();
        if (TextUtils.isEmpty(url) || !isValid(url)) return List.of();
        return Proxy.arrayFrom(legacy(url));
    }

    private static List<Proxy> parse(String rules, String defaultUrl) {
        try {
            if (Json.isArray(rules)) return Proxy.arrayFrom(normalize(Json.parse(rules).getAsJsonArray()));
            if (Json.isObj(rules)) return parseObject(Json.parse(rules).getAsJsonObject());
            return Proxy.arrayFrom(parseLines(rules, defaultUrl));
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<Proxy> parseObject(JsonObject object) {
        if (object.has("proxy")) return Proxy.arrayFrom(normalize(object.getAsJsonArray("proxy")));
        JsonArray array = new JsonArray();
        array.add(object);
        return Proxy.arrayFrom(normalize(array));
    }

    private static JsonArray normalize(JsonArray input) {
        JsonArray output = new JsonArray();
        for (int i = 0; i < input.size(); i++) {
            if (!input.get(i).isJsonObject()) continue;
            JsonObject object = input.get(i).getAsJsonObject().deepCopy();
            object.addProperty("name", NAME);
            output.add(object);
        }
        return output;
    }

    private static JsonArray parseLines(String rules, String defaultUrl) {
        JsonArray array = new JsonArray();
        int index = 0;
        for (String line : rules.split("\\r?\\n")) {
            JsonObject object = parseLine(line.trim(), ++index, defaultUrl);
            if (object != null) array.add(object);
        }
        return array;
    }

    private static JsonObject parseLine(String line, int index, String defaultUrl) {
        if (TextUtils.isEmpty(line) || line.startsWith("#")) return null;
        String[] parts = line.split("\\s+", 2);
        String hosts = parts.length > 1 ? parts[0].trim() : line.trim();
        String urls = parts.length > 1 ? parts[1].trim() : defaultUrl;
        if (parts.length == 1 && looksLikeProxyUrl(hosts)) {
            urls = hosts;
            hosts = "*";
        }
        if (TextUtils.isEmpty(hosts) || TextUtils.isEmpty(urls)) return null;
        JsonObject object = new JsonObject();
        object.addProperty("name", NAME);
        object.add("hosts", array(hosts));
        object.add("urls", array(urls));
        return object;
    }

    private static JsonArray legacy(String url) {
        JsonObject object = new JsonObject();
        object.addProperty("name", NAME);
        object.add("hosts", hosts());
        object.add("urls", urls(url));
        JsonArray array = new JsonArray();
        array.add(object);
        return array;
    }

    private static JsonArray hosts() {
        return array(Setting.getShellProxyHosts());
    }

    private static JsonArray urls(String url) {
        return array(url);
    }

    private static JsonArray array(String text) {
        JsonArray array = new JsonArray();
        for (String item : text.split(",")) {
            String value = item.trim();
            if (!TextUtils.isEmpty(value)) array.add(value);
        }
        if (array.isEmpty()) array.add("*");
        return array;
    }

    public static boolean isValid(String url) {
        if (TextUtils.isEmpty(url)) return false;
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        return scheme != null && (scheme.startsWith("http") || scheme.startsWith("socks")) && uri.getHost() != null && uri.getPort() > 0;
    }

    private static boolean looksLikeProxyUrl(String text) {
        Uri uri = Uri.parse(text);
        String scheme = uri.getScheme();
        return scheme != null && (scheme.startsWith("http") || scheme.startsWith("socks"));
    }

    public static boolean isValidRules(String rules, String defaultUrl) {
        String text = rules == null ? "" : rules.trim();
        String url = defaultUrl == null ? "" : defaultUrl.trim();
        if (TextUtils.isEmpty(text)) return TextUtils.isEmpty(url) || isValid(url);
        if (!TextUtils.isEmpty(url) && !isValid(url)) return false;
        List<Proxy> items = parse(text, url);
        if (items.isEmpty()) return false;
        for (Proxy proxy : items) {
            proxy.init();
            if (proxy.getHosts().isEmpty() || proxy.getProxies().isEmpty()) return false;
        }
        return true;
    }

    public static boolean isValidRules(String rules) {
        return isValidRules(rules, Setting.getShellProxyUrl());
    }

    public static int count() {
        return getRules().size();
    }

}
