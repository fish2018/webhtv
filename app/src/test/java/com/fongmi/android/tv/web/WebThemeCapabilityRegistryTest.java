package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class WebThemeCapabilityRegistryTest {

    @Test
    public void registryProducesCanonicalCapabilitiesFromThePagePermissionIntersection() {
        Set<String> declared = new HashSet<>(Arrays.asList(
                "app.search", "navigation.openDetail", "vod.home", "vod.detail", "net.request"));

        assertEquals(List.of(
                "theme.info@1",
                "ui.getViewport@1",
                "navigation.back@1",
                "navigation.reload@1",
                "vod.home@1",
                "navigation.openDetail@1",
                "app.search@1"),
                WebThemeCapabilityRegistry.capabilities(WebThemePage.HOME, declared));
        assertEquals(Set.of("vod.home", "navigation.openDetail", "app.search"),
                WebThemeCapabilityRegistry.filterPermissions(WebThemePage.HOME, declared));
    }

    @Test
    public void registryMapsBridgeMethodsToTheirRequiredManifestPermissions() {
        Set<String> declared = Set.of("vod.detail", "favorite.read", "history.read", "player.playVod");

        assertTrue(WebThemeCapabilityRegistry.allowsMethod(WebThemePage.DETAIL, declared, "favorite.status"));
        assertTrue(WebThemeCapabilityRegistry.allowsMethod(WebThemePage.DETAIL, declared, "history.item"));
        assertTrue(WebThemeCapabilityRegistry.allowsMethod(WebThemePage.DETAIL, declared, "player.playVod"));
        assertTrue(WebThemeCapabilityRegistry.allowsMethod(WebThemePage.DETAIL, declared,
                "navigation.openNativeDetail"));
        assertFalse(WebThemeCapabilityRegistry.allowsMethod(WebThemePage.DETAIL, declared, "favorite.set"));
        assertFalse(WebThemeCapabilityRegistry.allowsMethod(WebThemePage.HOME, declared, "vod.detail"));
    }

    @Test
    public void registryKeepsTheExistingV1RemoteAllowlist() {
        assertTrue(WebThemeCapabilityRegistry.allowsLegacyMethod("vod.home"));
        assertTrue(WebThemeCapabilityRegistry.allowsLegacyMethod("player.playVod"));
        assertTrue(WebThemeCapabilityRegistry.allowsLegacyMethod("app.openSetting"));
        assertTrue(WebThemeCapabilityRegistry.allowsLegacyMethod("navigation.reload"));

        assertFalse(WebThemeCapabilityRegistry.allowsLegacyMethod("theme.info"));
        assertFalse(WebThemeCapabilityRegistry.allowsLegacyMethod("navigation.openDetail"));
        assertFalse(WebThemeCapabilityRegistry.allowsLegacyMethod("favorite.set"));
        assertFalse(WebThemeCapabilityRegistry.allowsLegacyMethod("net.request"));
    }

    @Test
    public void everyPageContractIsBackedByTheRegistry() {
        for (WebThemePage page : WebThemePage.values()) {
            String contract = page.getContract();
            String permission = contract.substring(0, contract.lastIndexOf('@'));
            assertTrue(WebThemeCapabilityRegistry.allowsPermission(page, permission));
            assertTrue(WebThemeCapabilityRegistry.capabilities(page, Set.of(permission)).contains(contract));
        }
    }
}
