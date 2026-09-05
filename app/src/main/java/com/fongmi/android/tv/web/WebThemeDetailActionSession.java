package com.fongmi.android.tv.web;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Short-lived opaque references for native-only TMDB detail actions. */
final class WebThemeDetailActionSession {

    private static final int MAX_PEOPLE = 64;
    private static final int MAX_IMAGES = 64;
    private static final int MAX_EPISODES = 500;
    private static final int MAX_RECOMMENDATIONS = 128;
    private static final int MAX_EXTERNAL_LINKS = 16;

    private final Map<String, TmdbPerson> personByRef = new HashMap<>();
    private final Map<String, String> personRefByIdentity = new HashMap<>();
    private final Map<String, String> imageByRef = new HashMap<>();
    private final LinkedHashMap<String, String> imageRefByUrl = new LinkedHashMap<>();
    private final Map<String, Episode> episodeByRef = new HashMap<>();
    private final Map<String, String> episodeRefByIdentity = new HashMap<>();
    private final Map<String, Recommendation> recommendationByRef = new HashMap<>();
    private final Map<String, String> recommendationRefByIdentity = new HashMap<>();
    private final Map<String, String> recommendationIdentityByRef = new HashMap<>();
    private final Map<String, ExternalLink> externalByRef = new HashMap<>();
    private final Map<String, String> externalRefByUrl = new HashMap<>();
    private final Set<String> hiddenRecommendations = new HashSet<>();

    synchronized String issuePerson(TmdbPerson person) {
        if (person == null || person.getPersonId() <= 0) return "";
        String identity = String.valueOf(person.getPersonId());
        String existing = personRefByIdentity.get(identity);
        if (existing != null) {
            personByRef.put(existing, person);
            return existing;
        }
        if (personByRef.size() >= MAX_PEOPLE) return "";
        String ref = ref("person");
        personByRef.put(ref, person);
        personRefByIdentity.put(identity, ref);
        return ref;
    }

    synchronized TmdbPerson resolvePerson(String ref) {
        return personByRef.get(value(ref));
    }

    synchronized String issueImage(String url) {
        String safe = safeHttpUrl(url, true);
        if (safe.isEmpty()) return "";
        String existing = imageRefByUrl.get(safe);
        if (existing != null) return existing;
        if (imageByRef.size() >= MAX_IMAGES) return "";
        String ref = ref("image");
        imageByRef.put(ref, safe);
        imageRefByUrl.put(safe, ref);
        return ref;
    }

    synchronized ImageSelection resolveImage(String ref) {
        String url = imageByRef.get(value(ref));
        if (url == null) return null;
        List<String> gallery = new ArrayList<>(imageRefByUrl.keySet());
        return new ImageSelection(url, List.copyOf(gallery), gallery.indexOf(url));
    }

    synchronized String issueEpisode(Episode episode) {
        String identity = episodeIdentity(episode);
        if (identity.isEmpty()) return "";
        String existing = episodeRefByIdentity.get(identity);
        if (existing != null) {
            episodeByRef.put(existing, episode);
            return existing;
        }
        if (episodeByRef.size() >= MAX_EPISODES) return "";
        String ref = ref("episode");
        episodeByRef.put(ref, episode);
        episodeRefByIdentity.put(identity, ref);
        return ref;
    }

    synchronized Episode resolveEpisode(String ref) {
        return episodeByRef.get(value(ref));
    }

    synchronized String issueRecommendation(TmdbItem item, String source) {
        String identity = recommendationIdentity(item, source);
        if (identity.isEmpty() || hiddenRecommendations.contains(identity)) return "";
        String existing = recommendationRefByIdentity.get(identity);
        Recommendation recommendation = new Recommendation(item, normalizedSource(source));
        if (existing != null) {
            recommendationByRef.put(existing, recommendation);
            return existing;
        }
        if (recommendationByRef.size() >= MAX_RECOMMENDATIONS) return "";
        String ref = ref("recommendation");
        recommendationByRef.put(ref, recommendation);
        recommendationRefByIdentity.put(identity, ref);
        recommendationIdentityByRef.put(ref, identity);
        return ref;
    }

    synchronized Recommendation resolveRecommendation(String ref) {
        return recommendationByRef.get(value(ref));
    }

    synchronized Recommendation markNotInterested(String ref) {
        String safeRef = value(ref);
        Recommendation recommendation = recommendationByRef.get(safeRef);
        String identity = recommendationIdentityByRef.get(safeRef);
        if (recommendation == null || identity == null) return null;
        hiddenRecommendations.add(identity);
        return recommendation;
    }

    synchronized boolean isRecommendationHidden(TmdbItem item, String source) {
        String identity = recommendationIdentity(item, source);
        return !identity.isEmpty() && hiddenRecommendations.contains(identity);
    }

    synchronized String issueExternal(String label, String url) {
        String safe = safeHttpUrl(url, false);
        if (safe.isEmpty()) return "";
        String existing = externalRefByUrl.get(safe);
        URI uri = URI.create(safe);
        String host = normalizedHost(uri.getHost());
        if (existing != null) {
            externalByRef.put(existing, new ExternalLink(value(label), safe, host));
            return existing;
        }
        if (externalByRef.size() >= MAX_EXTERNAL_LINKS) return "";
        String ref = ref("external");
        externalByRef.put(ref, new ExternalLink(value(label), safe, host));
        externalRefByUrl.put(safe, ref);
        return ref;
    }

    synchronized ExternalLink resolveExternal(String ref) {
        return externalByRef.get(value(ref));
    }

    synchronized void clear() {
        personByRef.clear();
        personRefByIdentity.clear();
        imageByRef.clear();
        imageRefByUrl.clear();
        episodeByRef.clear();
        episodeRefByIdentity.clear();
        recommendationByRef.clear();
        recommendationRefByIdentity.clear();
        recommendationIdentityByRef.clear();
        externalByRef.clear();
        externalRefByUrl.clear();
        hiddenRecommendations.clear();
    }

    private static String episodeIdentity(Episode episode) {
        if (episode == null || value(episode.getUrl()).isEmpty()) return "";
        return value(episode.getName()) + '\u0000' + value(episode.getUrl());
    }

    private static String recommendationIdentity(TmdbItem item, String source) {
        if (item == null || value(item.getTitle()).isEmpty()) return "";
        String media = value(item.getMediaType()).toLowerCase(Locale.ROOT);
        String itemIdentity = item.getTmdbId() > 0 ? String.valueOf(item.getTmdbId())
                : value(item.getTitle()).toLowerCase(Locale.ROOT);
        return normalizedSource(source) + ':' + media + ':' + itemIdentity;
    }

    private static String normalizedSource(String source) {
        return switch (value(source).toLowerCase(Locale.ROOT)) {
            case "tmdb", "douban", "ai" -> value(source).toLowerCase(Locale.ROOT);
            default -> "related";
        };
    }

    private static String safeHttpUrl(String raw, boolean allowPrivateHost) {
        String value = value(raw);
        if (value.isEmpty() || value.length() > 4096) return "";
        try {
            URI uri = URI.create(value);
            String scheme = value(uri.getScheme()).toLowerCase(Locale.ROOT);
            String host = value(uri.getHost()).toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || host.isEmpty()) return "";
            if (uri.getUserInfo() != null || (!allowPrivateHost && isPrivateHost(host))) return "";
            return uri.toString();
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static boolean isPrivateHost(String host) {
        String value = host.toLowerCase(Locale.ROOT);
        if (value.startsWith("[") && value.endsWith("]")) value = value.substring(1, value.length() - 1);
        int zone = value.indexOf('%');
        if (zone >= 0) value = value.substring(0, zone);
        if ("localhost".equals(value) || value.endsWith(".localhost")) return true;
        if (value.indexOf(':') >= 0) return isPrivateIpv6(value);
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return value.matches("[0-9.]+") || value.matches("(?i)0x[0-9a-f]+");
        }
        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            for (String part : parts) {
                if (part.length() > 1 && part.charAt(0) == '0') return true;
                int number = Integer.parseInt(part);
                if (number < 0 || number > 255) return true;
            }
            return first == 0 || first == 10 || first == 127 || first >= 224
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168);
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    private static boolean isPrivateIpv6(String value) {
        try {
            String firstPart = value.substring(0, value.indexOf(':'));
            int first = firstPart.isEmpty() ? 0 : Integer.parseInt(firstPart, 16);
            if ((first & 0xfe00) == 0xfc00 || (first & 0xffc0) == 0xfe80 || (first & 0xff00) == 0xff00) {
                return true;
            }
            InetAddress address = InetAddress.getByName(value);
            return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress();
        } catch (Exception ignored) {
            return true;
        }
    }

    private static String normalizedHost(String host) {
        String value = value(host).toLowerCase(Locale.ROOT);
        return value.startsWith("www.") ? value.substring(4) : value;
    }

    private static String ref(String type) {
        return type + '-' + UUID.randomUUID();
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    record ImageSelection(String url, List<String> gallery, int index) {
    }

    record Recommendation(TmdbItem item, String source) {
    }

    record ExternalLink(String label, String url, String host) {
    }
}
