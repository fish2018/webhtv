package com.fongmi.android.tv.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** TMDB API 与图片线路的地址归一化和内置线路定义。 */
public final class TmdbProxy {

    public static final String OFFICIAL_API = "https://api.tmdb.org";
    public static final String OFFICIAL_IMAGE = "https://images.tmdb.org";
    public static final String AUTO = "auto";
    /** 实测 API 与 /t/p 图片路径均可用的公共镜像。 */
    public static final String ITV666 = "http://tmdb.itv666.cc";
    /** wsrv.nl 将原始图片地址放在 url 查询参数中。 */
    public static final String WSRV_IMAGE = "https://wsrv.nl/?url=https://image.tmdb.org";

    private static final String REMOVED_WORKER_POOL = "worker-pool";
    private static final String REMOVED_NASTOOL = "https://tmdb.nastool.org";
    private static final Pattern POOL_SEPARATOR = Pattern.compile("[,，;；\\s]+");

    private static final List<Option> API_OPTIONS = List.of(
            new Option(AUTO, "自动（低延迟优先，失败切换）"),
            new Option(OFFICIAL_API, "官方 API（直连）"),
            new Option(ITV666, "itv666 API 代理"));
    private static final List<Option> IMAGE_OPTIONS = List.of(
            new Option(AUTO, "自动（低延迟优先，失败切换）"),
            new Option(OFFICIAL_IMAGE, "官方图片（直连）"),
            new Option(ITV666, "itv666 图片代理"),
            new Option(WSRV_IMAGE, "wsrv.nl 图片代理"));

    private TmdbProxy() {
    }

    public static List<Option> apiOptions() {
        return API_OPTIONS;
    }

    public static List<Option> imageOptions() {
        return IMAGE_OPTIONS;
    }

    public static List<String> apiValues() {
        return values(API_OPTIONS);
    }

    public static List<String> imageValues() {
        return values(IMAGE_OPTIONS);
    }

    public static String[] labels(List<Option> options) {
        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) labels[i] = options.get(i).label;
        return labels;
    }

    /** 将单个地址归一化；兼容旧配置中的候选串时只取第一个有效地址，失效 Worker/NAStool 线路直接丢弃。 */
    public static String normalizeConfig(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || "direct".equalsIgnoreCase(text) || isRemovedRoute(text)) return "";
        if (AUTO.equalsIgnoreCase(text)) return AUTO;

        for (String item : POOL_SEPARATOR.split(text)) {
            if (isRemovedRoute(item)) continue;
            String host = normalizeHost(item);
            if (host != null) return host;
        }
        return "";
    }

    public static String normalizeImageConfig(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || "direct".equalsIgnoreCase(text) || isRemovedRoute(text)) return "";
        if (AUTO.equalsIgnoreCase(text)) return AUTO;
        if (isImageWrapper(text)) return imageWrapperHost(text);
        return normalizeConfig(text);
    }

    public static String valueForImageInput(String value) {
        String text = value == null ? "" : value.trim();
        for (Option option : IMAGE_OPTIONS) {
            if (option.label.equals(text)) return option.value;
        }
        return normalizeImageConfig(text);
    }

    public static String displayImage(String value) {
        String normalized = normalizeImageConfig(value);
        for (Option option : IMAGE_OPTIONS) {
            if (option.value.equals(normalized)) return option.label;
        }
        return normalized;
    }

    public static List<String> autoApiCandidates() {
        return List.of(OFFICIAL_API, ITV666);
    }

    public static List<String> autoImageCandidates() {
        return List.of(OFFICIAL_IMAGE, ITV666, WSRV_IMAGE);
    }
    public static String apiBaseFor(String route) {
        String value = normalizeConfig(route);
        if ((value == null || value.isEmpty()) || AUTO.equals(value)) return OFFICIAL_API + "/3";
        return value.endsWith("/3") ? value : value + "/3";
    }

    public static String routeForImageUrl(String url) {
        if (url == null) return "";
        if (url.startsWith(WSRV_IMAGE)) return WSRV_IMAGE;
        if (url.startsWith(ITV666)) return ITV666;
        if (url.startsWith(OFFICIAL_IMAGE) || url.startsWith("https://image.tmdb.org")) return OFFICIAL_IMAGE;
        return "";
    }

    public static String nextAutoImageUrl(String url) {
        if (!RouteSelector.isAuto(RouteSelector.Kind.IMAGE)) return "";
        String current = routeForImageUrl(url);
        if (current.isEmpty()) return "";
        String target = url;
        if (isImageWrapper(url)) {
            int query = url.indexOf("?url=");
            target = query < 0 ? url : url.substring(query + 5);
        }
        int marker = target.indexOf("/t/p/");
        if (marker < 0) return "";
        String suffix = target.substring(marker + 5);
        int slash = suffix.indexOf('/');
        if (slash <= 0 || slash >= suffix.length() - 1) return "";
        String size = suffix.substring(0, slash);
        String path = suffix.substring(slash + 1);
        for (String candidate : RouteSelector.order(RouteSelector.Kind.IMAGE, autoImageCandidates())) {
            if (candidate.equals(current)) continue;
            return imageUrl(imageBaseFor(candidate, size), "/" + path);
        }
        return "";
    }

    public static String imageBaseFor(String route, String size) {
        String value = normalizeImageConfig(route);
        if ((value == null || value.isEmpty()) || AUTO.equals(value)) value = OFFICIAL_IMAGE;
        if (isImageWrapper(value)) return imageWrapperBase(value, size);
        return trimTrailingSlash(value) + "/t/p/" + size;
    }

    public static String imageUrl(String base, String path) {
        if (base == null || base.isEmpty() || path == null || path.isEmpty()) return "";
        String suffix = path.startsWith("/") ? path : "/" + path;
        if (!isImageWrapper(base)) return trimTrailingSlash(base) + suffix;
        int query = base.indexOf("?url=");
        if (query < 0) return trimTrailingSlash(base) + suffix;
        String prefix = base.substring(0, query + 5);
        String target = base.substring(query + 5);
        return prefix + target + suffix;
    }


    public static boolean isAuto(String value) {
        return AUTO.equalsIgnoreCase(value == null ? "" : value.trim());
    }

    public static boolean isImageWrapper(String value) {
        if (value == null) return false;
        try {
            URI uri = new URI(value.trim());
            return "wsrv.nl".equalsIgnoreCase(uri.getHost()) && uri.getRawQuery() != null
                    && uri.getRawQuery().toLowerCase(Locale.ROOT).startsWith("url=");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String imageWrapperHost(String value) {
        if (!isImageWrapper(value)) return normalizeConfig(value);
        int query = value.indexOf("?url=");
        if (query < 0) return "";
        String target = value.substring(query + 5);
        int amp = target.indexOf('&');
        if (amp >= 0) target = target.substring(0, amp);
        String normalizedTarget = normalizeHost(target);
        return normalizedTarget == null ? "" : value.substring(0, query + 5) + normalizedTarget;
    }

    public static String imageWrapperBase(String value, String size) {
        String base = imageWrapperHost(value);
        if (base.isEmpty()) return base;
        String target = base.substring(base.indexOf("?url=") + 5);
        target = stripImagePathSize(target);
        if (!target.endsWith("/t/p")) target += "/t/p";
        return base.substring(0, base.indexOf("?url=") + 5) + target + "/" + size;
    }

    public static String valueForInput(String value, List<Option> options) {
        String text = value == null ? "" : value.trim();
        for (Option option : options) {
            if (option.label.equals(text)) return option.value;
        }
        return normalizeConfig(text);
    }

    public static String displayFor(String value, List<Option> options) {
        String normalized = normalizeConfig(value);
        for (Option option : options) {
            if (option.value.equals(normalized)) return option.label;
        }
        return normalized;
    }

    /** 兼容旧版 proxyBase 配置：旧配置仍可解析，但新 UI 不再生成该字段。 */
    public static String resolve(String value) {
        List<String> pool = parsePool(value);
        return pool.isEmpty() ? "" : pool.get(0);
    }

    public static boolean contains(String configured, String resolved) {
        if (resolved == null || resolved.isEmpty()) return parsePool(configured).isEmpty();
        return parsePool(configured).contains(resolved);
    }

    public static String imageHostFor(String resolvedApiHost) {
        return normalizeHost(resolvedApiHost);
    }

    public static boolean isOfficialApiHost(String value) {
        String normalized = normalizeHost(value);
        return OFFICIAL_API.equalsIgnoreCase(normalized)
                || "https://api.themoviedb.org".equalsIgnoreCase(normalized);
    }

    public static boolean isOfficialImageHost(String value) {
        String normalized = normalizeHost(value);
        return OFFICIAL_IMAGE.equalsIgnoreCase(normalized)
                || "https://image.tmdb.org".equalsIgnoreCase(normalized)
                || "https://images.tmdb.org".equalsIgnoreCase(normalized)
                || "https://media.themoviedb.org".equalsIgnoreCase(normalized);
    }

    public static boolean isRemovedRoute(String value) {
        if (value == null) return false;
        String text = value.trim();
        return REMOVED_WORKER_POOL.equalsIgnoreCase(text)
                || REMOVED_NASTOOL.equalsIgnoreCase(trimTrailingSlash(text));
    }

    private static List<String> parsePool(String value) {
        String normalized = normalizeConfig(value);
        if (normalized.isEmpty()) return List.of();
        List<String> hosts = new ArrayList<>();
        for (String item : normalized.split(",")) {
            String host = normalizeHost(item);
            if (host != null) hosts.add(host);
        }
        return hosts;
    }

    private static List<String> values(List<Option> options) {
        List<String> values = new ArrayList<>();
        for (Option option : options) values.add(option.value);
        return Collections.unmodifiableList(values);
    }

    private static String normalizeHost(String value) {
        if (value == null) return null;
        String text = value.trim();
        if (text.isEmpty() || isRemovedRoute(text)) return null;
        if (!text.contains("://")) text = "https://" + text;
        try {
            URI uri = new URI(text);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if ((scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)))
                    || host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                return null;
            }
            String authority = uri.getRawAuthority();
            String path = uri.getPath() == null ? "" : trimTrailingSlash(uri.getPath());
            if (path.matches(".*/(?:w\\d+|h\\d+|original)$")) path = path.substring(0, path.lastIndexOf('/'));
            if (path.endsWith("/3")) path = path.substring(0, path.length() - 2);
            if (path.endsWith("/t/p")) path = path.substring(0, path.length() - 4);
            return scheme.toLowerCase(Locale.ROOT) + "://" + authority + path;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String stripImagePathSize(String value) {
        String result = trimTrailingSlash(value);
        int marker = result.indexOf("/t/p/");
        if (marker < 0) return result;
        String suffix = result.substring(marker + 5);
        int slash = suffix.indexOf('/');
        String first = slash < 0 ? suffix : suffix.substring(0, slash);
        if (first.matches("(?:w\\d+|h\\d+|original)")) {
            return result.substring(0, marker + 4) + (slash < 0 ? "" : suffix.substring(slash));
        }
        return result;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) return "";
        String result = value;
        while (result.endsWith("/") && !result.isEmpty()) result = result.substring(0, result.length() - 1);
        return result;
    }

    public static final class RouteSelector {
        public enum Kind { API, IMAGE }

        private static final long FAILURE_COOLDOWN_MILLIS = 30_000L;
        private static final long HEALTH_TTL_MILLIS = 10 * 60_000L;
        private static final java.util.Map<String, Health> HEALTH = new java.util.HashMap<>();

        public static synchronized List<String> order(Kind kind, List<String> candidates) {
            long now = System.currentTimeMillis();
            List<String> result = new ArrayList<>(candidates == null ? List.of() : candidates);
            java.util.Map<String, Integer> original = new java.util.HashMap<>();
            for (int i = 0; i < result.size(); i++) original.put(result.get(i), i);
            result.sort(java.util.Comparator
                    .comparing((String route) -> isCooling(kind, route, now))
                    .thenComparingLong(route -> latency(kind, route, now))
                    .thenComparingInt(original::get));
            return result;
        }

        public static synchronized String preferred(Kind kind, List<String> candidates) {
            List<String> ordered = order(kind, candidates);
            return ordered.isEmpty() ? "" : ordered.get(0);
        }

        public static synchronized void success(Kind kind, String route, long latencyMillis) {
            if (route == null || route.isEmpty()) return;
            Health health = HEALTH.computeIfAbsent(key(kind, route), ignored -> new Health());
            health.latencyMillis = Math.max(0L, latencyMillis);
            health.checkedAt = System.currentTimeMillis();
            health.failureUntil = 0L;
        }

        public static synchronized void failure(Kind kind, String route) {
            if (route == null || route.isEmpty()) return;
            Health health = HEALTH.computeIfAbsent(key(kind, route), ignored -> new Health());
            health.failureUntil = System.currentTimeMillis() + FAILURE_COOLDOWN_MILLIS;
            health.checkedAt = System.currentTimeMillis();
        }

        public static synchronized void setAuto(Kind kind, boolean enabled) {
            if (enabled) HEALTH.put("AUTO|" + kind.name(), new Health());
            else HEALTH.remove("AUTO|" + kind.name());
        }

        public static synchronized boolean isAuto(Kind kind) {
            return HEALTH.containsKey("AUTO|" + kind.name());
        }

        public static synchronized void clearForTest() {
            HEALTH.clear();
        }

        private static boolean isCooling(Kind kind, String route, long now) {
            Health health = HEALTH.get(key(kind, route));
            return health != null && health.failureUntil > now;
        }

        private static long latency(Kind kind, String route, long now) {
            Health health = HEALTH.get(key(kind, route));
            if (health == null || health.checkedAt <= 0 || now - health.checkedAt > HEALTH_TTL_MILLIS) return Long.MAX_VALUE;
            return health.latencyMillis;
        }

        private static String key(Kind kind, String route) {
            return kind.name() + "|" + route;
        }

        private static final class Health {
            long latencyMillis = Long.MAX_VALUE;
            long checkedAt;
            long failureUntil;
        }
    }

    public static final class Option {
        public final String value;
        public final String label;

        private Option(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }
}
