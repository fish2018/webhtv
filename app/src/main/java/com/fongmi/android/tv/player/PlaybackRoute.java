package com.fongmi.android.tv.player;

import com.fongmi.android.tv.setting.PreloadSetting;
import java.net.URI;

public enum PlaybackRoute {

    DIRECT_REMOTE_HTTP,
    APP_LOCAL_SERVICE,
    EXTERNAL_LOOPBACK_PROXY,
    OTHER;

    public static PlaybackRoute classify(String url) {
        return resolve(url).route();
    }

    public static Resolution resolve(String url) {
        if (url == null || url.isBlank()) return new Resolution(OTHER, Owner.UNKNOWN, Evidence.EMPTY_URL, Confidence.UNKNOWN, "none", false, Location.UNKNOWN);
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (isLocalScheme(scheme)) return new Resolution(OTHER, Owner.UNKNOWN, Evidence.LOCAL_SCHEME, Confidence.CONFIRMED, safeScheme(scheme), false, Location.LOCAL);
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return new Resolution(OTHER, Owner.UNKNOWN, Evidence.NON_HTTP_SCHEME, Confidence.UNKNOWN, safeScheme(scheme), false, Location.UNKNOWN);
            String host = uri.getHost();
            if (host == null || host.isBlank()) return new Resolution(OTHER, Owner.UNKNOWN, Evidence.INVALID_HTTP_URL, Confidence.UNKNOWN, safeScheme(scheme), false, Location.UNKNOWN);
            if (!isLoopback(host) && isPrivateHost(host)) return new Resolution(DIRECT_REMOTE_HTTP, Owner.REMOTE_ORIGIN, Evidence.PRIVATE_HOST, Confidence.CONFIRMED, safeScheme(scheme), false, Location.LAN_PRIVATE);
            if (!isLoopback(host)) return new Resolution(DIRECT_REMOTE_HTTP, Owner.REMOTE_ORIGIN, Evidence.REMOTE_HOST, Confidence.CONFIRMED, safeScheme(scheme), false, Location.REMOTE);
            PlaybackRouteRegistry.AppOwner appOwner = PlaybackRouteRegistry.findAppOwner(uri.getPort());
            if (appOwner != null) return new Resolution(APP_LOCAL_SERVICE, owner(appOwner), Evidence.REGISTERED_APP_PORT, Confidence.CONFIRMED, safeScheme(scheme), true, Location.APP_INTERNAL_SERVICE);
            return new Resolution(EXTERNAL_LOOPBACK_PROXY, Owner.EXTERNAL_OR_UNKNOWN_LOOPBACK, Evidence.UNREGISTERED_LOOPBACK_PORT, Confidence.INFERRED, safeScheme(scheme), true, Location.EXTERNAL_LOOPBACK);
        } catch (IllegalArgumentException e) {
            return new Resolution(OTHER, Owner.UNKNOWN, Evidence.INVALID_URL, Confidence.UNKNOWN, "other", false, Location.UNKNOWN);
        }
    }

    public int effectivePreloadThreads(int requestedThreads) {
        int requested = Math.min(Math.max(requestedThreads, PreloadSetting.MIN_THREADS), PreloadSetting.MAX_THREADS);
        return switch (this) {
            case DIRECT_REMOTE_HTTP, APP_LOCAL_SERVICE -> requested;
            case EXTERNAL_LOOPBACK_PROXY, OTHER -> PreloadSetting.MIN_THREADS;
        };
    }

    private static boolean isLoopback(String host) {
        if (host == null) return false;
        String normalized = host.trim().toLowerCase(java.util.Locale.US);
        if (normalized.startsWith("[") && normalized.endsWith("]")) normalized = normalized.substring(1, normalized.length() - 1);
        if (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "0:0:0:0:0:0:0:1".equals(normalized);
    }

    private static boolean isLocalScheme(String scheme) {
        return "file".equalsIgnoreCase(scheme)
                || "content".equalsIgnoreCase(scheme)
                || "asset".equalsIgnoreCase(scheme)
                || "android.resource".equalsIgnoreCase(scheme)
                || "data".equalsIgnoreCase(scheme);
    }

    /**
     * Classifies only literal address ranges.  It intentionally does not resolve
     * host names, so a host that cannot be proven private remains remote/unknown.
     */
    private static boolean isPrivateHost(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase(java.util.Locale.US);
        if (normalized.startsWith("[") && normalized.endsWith("]")) normalized = normalized.substring(1, normalized.length() - 1);
        if (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.contains(":")) {
            return normalized.startsWith("fc") || normalized.startsWith("fd")
                    || normalized.startsWith("fe8") || normalized.startsWith("fe9")
                    || normalized.startsWith("fea") || normalized.startsWith("feb");
        }
        String[] parts = normalized.split("\\.");
        if (parts.length != 4) return false;
        try {
            int[] octets = new int[4];
            for (int i = 0; i < parts.length; i++) {
                octets[i] = Integer.parseInt(parts[i]);
                if (octets[i] < 0 || octets[i] > 255) return false;
            }
            int first = octets[0];
            int second = octets[1];
            if (first == 10 || first == 127 || (first == 169 && second == 254) || (first == 192 && second == 168)) return true;
            return first == 172 && second >= 16 && second <= 31;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static Owner owner(PlaybackRouteRegistry.AppOwner owner) {
        return switch (owner) {
            case MAIN_SERVER -> Owner.APP_MAIN_SERVER;
            case HLS_PROXY -> Owner.APP_HLS_PROXY;
            case MULTI_THREAD_PROXY -> Owner.APP_MULTI_THREAD_PROXY;
        };
    }

    private static String safeScheme(String scheme) {
        if ("http".equalsIgnoreCase(scheme)) return "http";
        if ("https".equalsIgnoreCase(scheme)) return "https";
        return scheme == null ? "none" : "other";
    }

    public enum Owner {
        REMOTE_ORIGIN("remote-origin"),
        APP_MAIN_SERVER("app-main-server"),
        APP_HLS_PROXY("app-hls-proxy"),
        APP_MULTI_THREAD_PROXY("app-multi-thread-proxy"),
        EXTERNAL_OR_UNKNOWN_LOOPBACK("external-or-unknown-loopback"),
        UNKNOWN("unknown");

        private final String label;

        Owner(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Evidence {
        REMOTE_HOST("remote-host"),
        PRIVATE_HOST("private-host"),
        REGISTERED_APP_PORT("registered-app-port"),
        UNREGISTERED_LOOPBACK_PORT("unregistered-loopback-port"),
        LOCAL_SCHEME("local-scheme"),
        NON_HTTP_SCHEME("non-http-scheme"),
        INVALID_HTTP_URL("invalid-http-url"),
        INVALID_URL("invalid-url"),
        EMPTY_URL("empty-url");

        private final String label;

        Evidence(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Confidence {
        CONFIRMED("confirmed"),
        INFERRED("inferred"),
        UNKNOWN("unknown");

        private final String label;

        Confidence(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Location {
        LOCAL("local"),
        LAN_PRIVATE("lan-private"),
        REMOTE("remote"),
        APP_INTERNAL_SERVICE("app-internal-service"),
        EXTERNAL_LOOPBACK("external-loopback"),
        UNKNOWN("unknown");

        private final String label;

        Location(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Resolution(PlaybackRoute route, Owner owner, Evidence evidence, Confidence confidence, String scheme, boolean loopback, Location location) {

        public Resolution(PlaybackRoute route, Owner owner, Evidence evidence, Confidence confidence, String scheme, boolean loopback) {
            this(route, owner, evidence, confidence, scheme, loopback, inferLocation(route));
        }

        public Resolution {
            route = route == null ? OTHER : route;
            owner = owner == null ? Owner.UNKNOWN : owner;
            evidence = evidence == null ? Evidence.INVALID_URL : evidence;
            confidence = confidence == null ? Confidence.UNKNOWN : confidence;
            scheme = scheme == null ? "none" : scheme;
            location = location == null ? inferLocation(route) : location;
        }

        private static Location inferLocation(PlaybackRoute route) {
            if (route == null) return Location.UNKNOWN;
            return switch (route) {
                case APP_LOCAL_SERVICE -> Location.APP_INTERNAL_SERVICE;
                case EXTERNAL_LOOPBACK_PROXY -> Location.EXTERNAL_LOOPBACK;
                default -> Location.UNKNOWN;
            };
        }

        public String logSummary() {
            return "route=" + route +
                    " owner=" + owner.label() +
                    " evidence=" + evidence.label() +
                    " confidence=" + confidence.label() +
                    " scheme=" + scheme +
                    " location=" + location.label() +
                    " loopback=" + loopback +
                    " " + PlaybackRouteCapabilities.resolve(this).logSummary();
        }
    }
}
