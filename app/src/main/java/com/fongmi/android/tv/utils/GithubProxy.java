package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.net.URI;

public final class GithubProxy {

    public static final String DEFAULT = "https://gh-proxy.com/";
    public static final String DIRECT = "direct";
    public static final String MODE_FULL_URL = "full_url";
    public static final String MODE_STRIP_SCHEME = "strip_scheme";

    private static final String[] BUILT_IN = {
            DEFAULT,
            "https://ghfast.top/",
            "https://99z.top/",
            "https://proxy.v2gh.com/",
            "https://proxy.api.030101.xyz/"
    };

    private GithubProxy() {
    }

    public static String apply(String url) {
        return apply(url, Setting.getGithubProxy(), Setting.isGithubProxyEnabled());
    }

    static String apply(String url, String configured) {
        return apply(url, configured, true);
    }

    static String apply(String url, String configured, boolean enabled) {
        if (!enabled || !isGithubDownload(url) || isProxied(url)) return url;
        String proxy = first(configured);
        return isEmpty(proxy) ? url : normalize(proxy) + url;
    }

    public static Config config() {
        return config(Setting.getGithubProxy(), Setting.getGithubProxyMode(), Setting.isGithubProxyEnabled());
    }

    public static Config config(String configured, String mode, boolean enabled) {
        if (!enabled) return new Config(DIRECT, "", MODE_FULL_URL);
        return new Config("proxy", normalize(first(configured)), normalizeMode(mode));
    }

    public static String normalizeMode(String mode) {
        return MODE_STRIP_SCHEME.equals(mode) ? MODE_STRIP_SCHEME : MODE_FULL_URL;
    }

    public static String defaultSources() {
        return String.join("\n", BUILT_IN);
    }

    public static List<String> getSources() {
        return sources(Setting.getGithubProxy());
    }

    public static String getActive() {
        return first(Setting.getGithubProxy());
    }

    public static boolean isBuiltIn(String source) {
        if (isEmpty(source)) return false;
        String normalized = normalize(source).toLowerCase(Locale.ROOT);
        for (String item : BUILT_IN) {
            if (item.toLowerCase(Locale.ROOT).equals(normalized)) return true;
        }
        return false;
    }

    public static String setActive(String source) {
        if (isEmpty(source)) return Setting.getGithubProxy();
        String normalized = normalize(source);
        List<String> list = new ArrayList<>();
        list.add(normalized);
        for (String item : sources(Setting.getGithubProxy())) {
            if (!item.equals(normalized)) list.add(item);
        }
        return String.join("\n", list);
    }

    public static String addSource(String source) {
        if (isEmpty(source)) return Setting.getGithubProxy();
        String normalized = normalize(source.trim());
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            return Setting.getGithubProxy();
        }
        List<String> list = sources(Setting.getGithubProxy());
        if (list.contains(normalized)) return Setting.getGithubProxy();
        list.add(normalized);
        return String.join("\n", list);
    }

    public static String addSources(String configured, String value) {
        if (isEmpty(value)) return configured;
        List<String> list = sources(configured);
        for (String source : sources(value)) {
            if (!list.contains(source)) list.add(source);
        }
        return String.join("\n", list);
    }

    public static String removeSource(String source) {
        if (isEmpty(source)) return Setting.getGithubProxy();
        String normalized = normalize(source);
        List<String> list = sources(Setting.getGithubProxy());
        list.remove(normalized);
        return list.isEmpty() ? defaultSources() : String.join("\n", list);
    }

    public static String normalizeConfig(String value) {
        List<String> sources = sources(value);
        return sources.isEmpty() ? defaultSources() : String.join("\n", sources);
    }

    public static String probeUrl(String source) {
        String proxy = normalize(isEmpty(source) ? first(Setting.getGithubProxy()) : source);
        return proxy + "https://github.com/Silent1566/webhtv/releases/download/update-channel/update.json";
    }

    public static final class Config {

        public final String id;
        public final String baseUrl;
        public final String mode;

        private Config(String id, String baseUrl, String mode) {
            this.id = id;
            this.baseUrl = baseUrl;
            this.mode = mode;
        }

        public String rewrite(String url) {
            String target = requireHttpsUrl(url);
            if (DIRECT.equals(id) || baseUrl.isEmpty()) return target;
            String prefix = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            if (MODE_STRIP_SCHEME.equals(mode)) return prefix + target.substring("https://".length());
            return prefix + target;
        }
    }

    private static String first(String configured) {
        List<String> sources = sources(configured);
        return sources.isEmpty() ? DEFAULT : sources.get(0);
    }

    private static List<String> sources(String value) {
        List<String> list = new ArrayList<>();
        String text = isEmpty(value) ? defaultSources() : value;
        for (String item : text.split("[\\r\\n,;\\s]+")) {
            if (!item.startsWith("http://") && !item.startsWith("https://")) continue;
            String source = normalize(item);
            if (!list.contains(source)) list.add(source);
        }
        return list;
    }

    private static String normalize(String proxy) {
        return proxy.endsWith("/") ? proxy : proxy + "/";
    }

    private static boolean isGithubDownload(String url) {
        if (isEmpty(url)) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://raw.githubusercontent.com/")
                || lower.startsWith("https://gist.githubusercontent.com/")
                || lower.matches("https://github\\.com/[^/]+/[^/]+/releases/(latest/)?download/.+");
    }

    private static boolean isProxied(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        for (String source : BUILT_IN) if (lower.startsWith(source.toLowerCase(Locale.ROOT))) return true;
        return lower.matches("https?://[^/]+/https?://.*");
    }

    private static String requireHttpsUrl(String value) {
        try {
            String text = value == null ? "" : value.trim();
            URI uri = URI.create(text);
            if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("HTTPS required");
            if (uri.getHost() == null || uri.getHost().isEmpty()) throw new IllegalArgumentException("Host required");
            if (uri.getUserInfo() != null) throw new IllegalArgumentException("Credentials are not allowed");
            return text;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid HTTPS URL", e);
        }
    }

    private static boolean isEmpty(String text) {
        return text == null || text.length() == 0;
    }
}
