package com.fongmi.android.tv.web;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Single source of truth for WebTheme bridge capabilities and permissions. */
public final class WebThemeCapabilityRegistry {

    private static final int CONTRACT_VERSION = 1;
    private static final List<Capability> CAPABILITIES = List.of(
            builtin("theme.info", false, WebThemePage.HOME, WebThemePage.DETAIL),
            builtin("ui.getViewport", true, WebThemePage.HOME, WebThemePage.DETAIL),
            builtin("navigation.back", true, WebThemePage.HOME, WebThemePage.DETAIL),
            builtin("navigation.reload", true, WebThemePage.HOME, WebThemePage.DETAIL),
            builtin("navigation.openNativeDetail", false, WebThemePage.DETAIL),
            permission("vod.home", "vod.home", true, WebThemePage.HOME),
            permission("vod.category", "vod.category", true, WebThemePage.HOME),
            permission("navigation.openDetail", "navigation.openDetail", false, WebThemePage.HOME),
            permission("vod.detail", "vod.detail", false, WebThemePage.DETAIL),
            permission("favorite.status", "favorite.read", false, WebThemePage.DETAIL),
            permission("favorite.set", "favorite.write", false, WebThemePage.DETAIL),
            permission("history.item", "history.read", false, WebThemePage.DETAIL),
            permission("player.playVod", "player.playVod", true, WebThemePage.DETAIL),
            permission("app.search", "app.search", true, WebThemePage.HOME, WebThemePage.DETAIL),
            permission("app.openVod", "app.openVod", true, WebThemePage.HOME),
            permission("app.openSite", "app.openSite", true, WebThemePage.HOME),
            permission("app.openSetting", "app.openSetting", true, WebThemePage.HOME),
            permission("person.open", "person.open", false, WebThemePage.DETAIL),
            permission("image.preview", "image.preview", false, WebThemePage.DETAIL),
            permission("image.save", "image.save", false, WebThemePage.DETAIL),
            permission("recommendation.open", "recommendation.open", false, WebThemePage.DETAIL),
            permission("recommendation.info", "recommendation.info", false, WebThemePage.DETAIL),
            permission("recommendation.feedback", "recommendation.feedback", false, WebThemePage.DETAIL),
            permission("external.open", "external.open", false, WebThemePage.DETAIL),
            permission("episode.info", "episode.info", false, WebThemePage.DETAIL));
    private static final Map<String, Capability> BY_METHOD = indexByMethod();

    private WebThemeCapabilityRegistry() {
    }

    public static boolean allowsLegacyMethod(String method) {
        Capability capability = BY_METHOD.get(value(method));
        return capability != null && capability.legacyAllowed;
    }

    public static boolean allowsMethod(WebThemePage page, Set<String> permissions, String method) {
        Capability capability = BY_METHOD.get(value(method));
        if (page == null || capability == null || !capability.pages.contains(page)) return false;
        return !capability.manifestRequired || has(permissions, capability.permission);
    }

    public static boolean allowsPermission(WebThemePage page, String permission) {
        if (page == null || permission == null) return false;
        for (Capability capability : CAPABILITIES) {
            if (capability.manifestRequired && capability.pages.contains(page)
                    && permission.equals(capability.permission)) return true;
        }
        return false;
    }

    public static Set<String> supportedPermissions(WebThemePage page) {
        if (page == null) return Collections.emptySet();
        LinkedHashSet<String> supported = new LinkedHashSet<>();
        for (Capability capability : CAPABILITIES) {
            if (capability.manifestRequired && capability.pages.contains(page)) {
                supported.add(capability.permission);
            }
        }
        return Collections.unmodifiableSet(supported);
    }

    public static Set<String> filterPermissions(WebThemePage page, Set<String> permissions) {
        if (page == null || permissions == null || permissions.isEmpty()) return Collections.emptySet();
        LinkedHashSet<String> filtered = new LinkedHashSet<>();
        for (Capability capability : CAPABILITIES) {
            if (capability.manifestRequired && capability.pages.contains(page)
                    && permissions.contains(capability.permission)) {
                filtered.add(capability.permission);
            }
        }
        return Collections.unmodifiableSet(filtered);
    }

    public static List<String> capabilities(WebThemePage page, Set<String> permissions) {
        if (page == null) return Collections.emptyList();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Capability capability : CAPABILITIES) {
            if (allowsMethod(page, permissions, capability.method)) result.add(capability.capabilityId());
        }
        return List.copyOf(result);
    }

    static List<CompatibilityEntry> compatibilityEntries() {
        return CAPABILITIES.stream()
                .map(capability -> new CompatibilityEntry(
                        capability.method,
                        capability.permission,
                        capability.contractVersion,
                        capability.legacyAllowed,
                        capability.manifestRequired,
                        capability.pages))
                .toList();
    }

    private static Capability builtin(String method, boolean legacyAllowed, WebThemePage... pages) {
        return new Capability(method, null, CONTRACT_VERSION, legacyAllowed, false, pages);
    }

    private static Capability permission(String method, String permission, boolean legacyAllowed,
            WebThemePage... pages) {
        return new Capability(method, permission, CONTRACT_VERSION, legacyAllowed, true, pages);
    }

    private static Map<String, Capability> indexByMethod() {
        LinkedHashMap<String, Capability> result = new LinkedHashMap<>();
        for (Capability capability : CAPABILITIES) {
            if (result.put(capability.method, capability) != null) {
                throw new IllegalStateException("Duplicate WebTheme method: " + capability.method);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static boolean has(Set<String> permissions, String permission) {
        return permissions != null && permissions.contains(permission);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    record CompatibilityEntry(String method, String permission, int contractVersion,
            boolean legacyAllowed, boolean manifestRequired, Set<WebThemePage> pages) {

        CompatibilityEntry {
            permission = permission == null ? "" : permission;
            pages = Collections.unmodifiableSet(EnumSet.copyOf(pages));
        }

        String capabilityId() {
            return (manifestRequired ? permission : method) + "@" + contractVersion;
        }
    }

    private static final class Capability {
        private final String method;
        private final String permission;
        private final int contractVersion;
        private final boolean legacyAllowed;
        private final boolean manifestRequired;
        private final Set<WebThemePage> pages;

        private Capability(String method, String permission, int contractVersion, boolean legacyAllowed,
                boolean manifestRequired, WebThemePage... pages) {
            this.method = method;
            this.permission = permission;
            this.contractVersion = contractVersion;
            this.legacyAllowed = legacyAllowed;
            this.manifestRequired = manifestRequired;
            EnumSet<WebThemePage> supported = EnumSet.noneOf(WebThemePage.class);
            Collections.addAll(supported, pages);
            this.pages = Collections.unmodifiableSet(supported);
        }

        String capabilityId() {
            return (manifestRequired ? permission : method) + "@" + contractVersion;
        }
    }
}
