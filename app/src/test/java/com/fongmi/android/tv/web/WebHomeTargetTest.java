package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebHomeTargetTest {

    @Test
    public void resolve_siteHomePageAlwaysWins() {
        WebHomeTarget target = WebHomeTarget.resolve(
                "https://site.example/home", true, "https://theme.example/home");

        assertEquals(WebHomeTarget.Mode.SITE, target.getMode());
        assertEquals("https://site.example/home", target.getUrl());
        assertTrue(target.injectsSiteExtensions());
    }

    @Test
    public void resolve_usesEnabledGlobalThemeWhenSiteHasNoHomePage() {
        WebHomeTarget target = WebHomeTarget.resolve("", true, "https://theme.example/home");

        assertEquals(WebHomeTarget.Mode.GLOBAL, target.getMode());
        assertEquals("https://theme.example/home", target.getUrl());
        assertFalse(target.injectsSiteExtensions());
    }

    @Test
    public void resolve_requiresLocalConsentForRemoteGlobalTheme() {
        String url = "https://theme.example/home";

        assertNull(WebHomeTarget.resolve("", true, url, ""));
        assertNull(WebHomeTarget.resolve("", true, url, "https://other.example/home"));
        assertEquals(url, WebHomeTarget.resolve("", true, url, " " + url + " ").getUrl());
    }

    @Test
    public void remoteIpv6ThemeUsesBracketedOriginRule() {
        String url = "https://[2606:4700:4700::1111]/theme.json";

        WebHomeTarget target = WebHomeTarget.resolve("", true, url, url);

        assertEquals("https://[2606:4700:4700::1111]", target.getOriginRule());
    }

    @Test
    public void routePreservesOpaqueVodIdWhitespace() {
        WebThemeRoute route = WebThemeRoute.detail("  opaque-id  ", " title ", " pic ", " remarks ");

        assertEquals("  opaque-id  ", route.getVodId());
    }

    @Test
    public void resolve_usesBuiltInEclipseWhenEnabledUrlIsBlank() {
        WebHomeTarget target = WebHomeTarget.resolve(null, true, "  ");

        assertEquals(WebHomeTarget.Mode.GLOBAL, target.getMode());
        assertEquals(WebHomeTarget.ECLIPSE_URL, target.getUrl());
    }

    @Test
    public void resolve_migratesLegacyBundledHomeToTheV2Manifest() {
        WebHomeTarget target = WebHomeTarget.resolve("", true, WebHomeTarget.ECLIPSE_HOME_URL);

        assertEquals(WebHomeTarget.ECLIPSE_URL, target.getUrl());
        assertTrue(target.isManifest());
    }

    @Test
    public void resolve_returnsNullWhenNeitherSiteNorGlobalThemeIsAvailable() {
        assertNull(WebHomeTarget.resolve("", false, WebHomeTarget.ECLIPSE_URL));
    }

    @Test
    public void resolve_rejectsUnsafeGlobalScheme() {
        assertNull(WebHomeTarget.resolve("", true, "javascript:alert(1)"));
        assertNull(WebHomeTarget.resolve("", true, "file:///sdcard/theme.html"));
    }

    @Test
    public void safeThemeUrl_acceptsHttpsAndTheBundledThemeOnly() {
        assertTrue(WebHomeTarget.isSafeThemeUrl("https://theme.example/home.html"));
        assertTrue(WebHomeTarget.isSafeThemeUrl(WebHomeTarget.ECLIPSE_URL));
        assertFalse(WebHomeTarget.isSafeThemeUrl("http://theme.example/home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://127.0.0.1/home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://192.168.1.10/home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://100.64.0.1/home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://169.254.1.1/home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://192.0.2.1/home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://224.0.0.1/home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://[::1]/home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://[fc00::1]/home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://2130706433/home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://0x7f000001/home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://0177.0.0.1/home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://127.1/home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://localhost/home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://localhost./home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://theme.local./home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://127.0.0.1./home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://user:password@theme.example/home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://theme.example:70000/home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https://"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("http://"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("https:///missing-host"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("file:///android_asset/"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("file:///android_asset/other.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("ftp://theme.example/home.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("file:///sdcard/theme.html"));
        assertFalse(WebHomeTarget.isSafeThemeUrl("data:text/html,hello"));
    }

    @Test
    public void sourceIdentity_reloadsGlobalThemeForAChangedContentSource() {
        WebHomeTarget target = WebHomeTarget.resolve("", true, WebHomeTarget.ECLIPSE_URL);

        assertEquals("global:source-a", target.identity("source-a"));
        assertEquals("global:source-b", target.identity("source-b"));
    }

    @Test
    public void remoteTheme_navigationAndOriginRuleStayOnConfiguredOrigin() {
        WebHomeTarget target = WebHomeTarget.resolve("", true, "https://theme.example:8443/path/home.html");

        assertTrue(target.isRemoteGlobal());
        assertEquals("https://theme.example:8443", target.getOriginRule());
        assertTrue(target.allowsMainFrameUrl("https://theme.example:8443/next?tab=1#hero"));
        assertFalse(target.allowsMainFrameUrl("https://theme.example/next"));
        assertFalse(target.allowsMainFrameUrl("https://cdn.theme.example:8443/next"));
        assertFalse(target.allowsMainFrameUrl("http://theme.example:8443/next"));
        assertFalse(target.allowsMainFrameUrl("https:missing-host"));
    }

    @Test
    public void manifestPageTarget_usesResolvedEntryAndCarriesPageCapabilities() {
        WebHomeTarget configured = WebHomeTarget.resolve("", true, "https://theme.example/theme.json");
        WebThemeManifest manifest = WebThemeManifest.parse(configured.getUrl(), "{"
                + "\"schemaVersion\":2,\"id\":\"maple.eclipse\",\"version\":\"2\",\"minHostApi\":2,"
                + "\"pages\":{\"detail\":{\"entry\":\"detail.html\",\"contract\":\"vod.detail@1\"}},"
                + "\"permissions\":{\"detail\":[\"vod.detail\"]}}", "mobile");

        WebHomeTarget detail = WebHomeTarget.forManifestPage(configured, manifest, WebThemePage.DETAIL);

        assertTrue(configured.isManifest());
        assertTrue(detail.isV2());
        assertEquals(WebThemePage.DETAIL, detail.getPage());
        assertEquals("https://theme.example/detail.html", detail.getUrl());
        assertTrue(detail.getPermissions().contains("vod.detail"));
        assertTrue(detail.identity("source-a").contains("maple.eclipse:2:detail"));
    }
}
