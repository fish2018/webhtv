package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class WebThemeManifestTest {

    private static final String URL = "https://theme.example/theme.json";

    @Test
    public void parse_resolvesSupportedPagesAndTopLevelPermissions() {
        WebThemeManifest manifest = WebThemeManifest.parse(URL, "{"
                + "\"schemaVersion\":2,"
                + "\"id\":\"maple.eclipse\","
                + "\"name\":\"Eclipse\","
                + "\"version\":\"2.0.0\","
                + "\"minHostApi\":2,"
                + "\"targets\":[\"mobile\",\"leanback\"],"
                + "\"pages\":{"
                + "  \"home\":{\"entry\":\"pages/home.html\",\"contract\":\"vod.home@1\",\"fallback\":\"native\"},"
                + "  \"detail\":{\"entry\":\"/pages/detail.html\",\"contract\":\"vod.detail@1\",\"fallback\":\"native\"}"
                + "},"
                + "\"permissions\":{"
                + "  \"home\":[\"vod.home\",\"navigation.openDetail\"],"
                + "  \"detail\":[\"vod.detail\",\"favorite.read\",\"player.playVod\"]"
                + "}"
                + "}", "mobile");

        assertEquals("maple.eclipse", manifest.getId());
        assertEquals("2.0.0", manifest.getVersion());
        assertEquals(2, manifest.getMinHostApi());
        assertEquals("https://theme.example/pages/home.html", manifest.getPage(WebThemePage.HOME).getEntryUrl());
        assertEquals("vod.detail@1", manifest.getPage(WebThemePage.DETAIL).getContract());
        assertTrue(manifest.getPage(WebThemePage.DETAIL).getPermissions().contains("favorite.read"));
        assertFalse(manifest.getPage(WebThemePage.DETAIL).getPermissions().contains("favorite.write"));
    }

    @Test
    public void parse_keepsValidPagesWhenAnotherPageEscapesTheManifestOrigin() {
        WebThemeManifest manifest = WebThemeManifest.parse(URL, "{"
                + "\"schemaVersion\":2,\"id\":\"safe.theme\",\"version\":\"1\",\"minHostApi\":2,"
                + "\"pages\":{"
                + "  \"home\":{\"entry\":\"home.html\",\"contract\":\"vod.home@1\"},"
                + "  \"detail\":{\"entry\":\"https://evil.example/detail.html\",\"contract\":\"vod.detail@1\"}"
                + "},"
                + "\"permissions\":{\"home\":[\"vod.home\"],\"detail\":[\"vod.detail\"]}}", "leanback");

        assertEquals("https://theme.example/home.html", manifest.getPage(WebThemePage.HOME).getEntryUrl());
        assertNull(manifest.getPage(WebThemePage.DETAIL));
    }

    @Test
    public void parse_rejectsUnsupportedSchemaHostTargetAndOversizedInput() {
        assertEquals(3, WebThemeManifest.HOST_API_VERSION);
        assertInvalid("{\"schemaVersion\":1,\"id\":\"x\",\"version\":\"1\",\"minHostApi\":2}", "mobile");
        assertInvalid("{\"schemaVersion\":2,\"id\":\"x\",\"version\":\"1\",\"minHostApi\":4}", "mobile");
        assertInvalid("{\"schemaVersion\":\"2\",\"id\":\"x\",\"version\":\"1\",\"minHostApi\":2}", "mobile");
        assertInvalid("{\"schemaVersion\":2,\"id\":\"x\",\"version\":\"1\",\"minHostApi\":2.5}", "mobile");
        assertInvalid("{\"schemaVersion\":2,\"id\":\"x\",\"version\":\"1\",\"minHostApi\":\"2\"}", "mobile");
        assertInvalid("{\"schemaVersion\":2,\"id\":\"x\",\"version\":\"1\",\"minHostApi\":2,\"targets\":[\"leanback\"]}", "mobile");
        assertThrows(IllegalArgumentException.class, () -> WebThemeManifest.parse(URL,
                " ".repeat(WebThemeManifest.MAX_MANIFEST_BYTES + 1), "mobile"));
    }

    @Test
    public void parse_acceptsBundledManifestEntriesOnlyInsideBundledThemeDirectory() {
        WebThemeManifest manifest = WebThemeManifest.parse(WebHomeTarget.ECLIPSE_URL, "{"
                + "\"schemaVersion\":2,\"id\":\"maple.eclipse\",\"version\":\"2\",\"minHostApi\":2,"
                + "\"pages\":{\"detail\":{\"entry\":\"eclipse-detail.html\",\"contract\":\"vod.detail@1\"}},"
                + "\"permissions\":{\"detail\":[\"vod.detail\"]}}", "mobile");

        assertEquals(WebHomeTarget.ECLIPSE_DETAIL_URL, manifest.getPage(WebThemePage.DETAIL).getEntryUrl());

        WebThemeManifest escaped = WebThemeManifest.parse(WebHomeTarget.ECLIPSE_URL, "{"
                + "\"schemaVersion\":2,\"id\":\"maple.eclipse\",\"version\":\"2\",\"minHostApi\":2,"
                + "\"pages\":{\"detail\":{\"entry\":\"../other.html\",\"contract\":\"vod.detail@1\"}},"
                + "\"permissions\":{\"detail\":[\"vod.detail\"]}}", "mobile");
        assertNull(escaped.getPage(WebThemePage.DETAIL));
    }

    @Test
    public void parse_rejectsPageThatDoesNotDeclareItsContractPermission() {
        WebThemeManifest manifest = WebThemeManifest.parse(URL, "{"
                + "\"schemaVersion\":2,\"id\":\"least.privilege\",\"version\":\"1\",\"minHostApi\":2,"
                + "\"pages\":{\"home\":{\"entry\":\"home.html\",\"contract\":\"vod.home@1\"}},"
                + "\"permissions\":{\"home\":[\"navigation.openDetail\"]}}", "mobile");

        assertNull(manifest.getPage(WebThemePage.HOME));
    }

    @Test
    public void bundledHomeDeclaresEveryNavigationCapabilityItUses() throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        String json = Files.readString(root.resolve("src/main/assets/webhome/theme.json"), StandardCharsets.UTF_8);
        WebThemeManifest manifest = WebThemeManifest.parse(WebHomeTarget.ECLIPSE_URL, json, "mobile");

        assertTrue(manifest.getPage(WebThemePage.HOME).getPermissions().contains("app.search"));
        assertTrue(manifest.getPage(WebThemePage.HOME).getPermissions().contains("app.openVod"));
        assertTrue(manifest.getPage(WebThemePage.HOME).getPermissions().contains("app.openSite"));
        assertTrue(manifest.getPage(WebThemePage.HOME).getPermissions().contains("app.openSetting"));
    }

    @Test
    public void bundledDetailDeclaresEveryTmdbActionCapabilityItUses() throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        String json = Files.readString(root.resolve("src/main/assets/webhome/theme.json"), StandardCharsets.UTF_8);
        WebThemeManifest manifest = WebThemeManifest.parse(WebHomeTarget.ECLIPSE_URL, json, "mobile");

        assertEquals(3, manifest.getMinHostApi());
        assertTrue(manifest.getPage(WebThemePage.DETAIL).getPermissions().contains("person.open"));
        assertTrue(manifest.getPage(WebThemePage.DETAIL).getPermissions().contains("image.preview"));
        assertTrue(manifest.getPage(WebThemePage.DETAIL).getPermissions().contains("image.save"));
        assertTrue(manifest.getPage(WebThemePage.DETAIL).getPermissions().contains("recommendation.open"));
        assertTrue(manifest.getPage(WebThemePage.DETAIL).getPermissions().contains("recommendation.info"));
        assertTrue(manifest.getPage(WebThemePage.DETAIL).getPermissions().contains("recommendation.feedback"));
        assertTrue(manifest.getPage(WebThemePage.DETAIL).getPermissions().contains("external.open"));
        assertTrue(manifest.getPage(WebThemePage.DETAIL).getPermissions().contains("episode.info"));
    }

    @Test
    public void parseFiltersUnsupportedPermissionsAndMarksReservedFields() {
        WebThemeManifest manifest = WebThemeManifest.parse(URL, "{"
                + "\"schemaVersion\":2,\"id\":\"reserved.theme\",\"version\":\"1\",\"minHostApi\":3,"
                + "\"pages\":{\"home\":{\"entry\":\"home.html\",\"contract\":\"vod.home@1\"}},"
                + "\"permissions\":{\"home\":[\"vod.home\",\"vod.detail\",\"net.request\"]},"
                + "\"player\":{\"engine\":\"native\",\"chrome\":\"tokens\"},"
                + "\"tokens\":{\"color.background\":\"#000000\"}"
                + "}", "mobile");

        assertEquals(java.util.Set.of("vod.home"), manifest.getPage(WebThemePage.HOME).getPermissions());
        assertEquals(java.util.Set.of("player", "tokens"), manifest.getReservedFields());
    }

    @Test
    public void parseRejectsReservedFieldsWithNonObjectValues() {
        assertInvalid("{\"schemaVersion\":2,\"id\":\"x\",\"version\":\"1\",\"minHostApi\":3,\"player\":\"native\"}",
                "mobile");
        assertInvalid("{\"schemaVersion\":2,\"id\":\"x\",\"version\":\"1\",\"minHostApi\":3,\"tokens\":[]}",
                "mobile");
    }
    private static void assertInvalid(String json, String target) {
        assertThrows(IllegalArgumentException.class, () -> WebThemeManifest.parse(URL, json, target));
    }
}
