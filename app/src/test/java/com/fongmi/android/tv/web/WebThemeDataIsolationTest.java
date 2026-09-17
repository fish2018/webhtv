package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebThemeDataIsolationTest {

    @Test
    public void trustedAndBundledPagesStayOnTheDefaultProfile() {
        WebHomeTarget trusted = WebHomeTarget.resolve("https://site.example/home", false, "");
        WebHomeTarget bundled = WebHomeTarget.resolve("", true, WebHomeTarget.ECLIPSE_URL,
                WebHomeTarget.ECLIPSE_URL);

        assertEquals(WebThemeDataIsolation.DEFAULT_PROFILE,
                WebThemeDataIsolation.profileFor(trusted).name());
        assertEquals(WebThemeDataIsolation.DEFAULT_PROFILE,
                WebThemeDataIsolation.profileFor(bundled).name());
        assertFalse(WebThemeDataIsolation.profileFor(trusted).isolated());
        assertFalse(WebThemeDataIsolation.profileFor(bundled).isolated());
    }

    @Test
    public void remoteOriginGetsStableOpaqueProfileAcrossProcessRecreation() {
        WebHomeTarget firstProcess = remote("https://theme.example/theme.json");
        WebHomeTarget nextProcess = remote("https://theme.example/pages/home.html?revision=2");

        WebThemeDataIsolation.DataProfile first = WebThemeDataIsolation.profileFor(firstProcess);
        WebThemeDataIsolation.DataProfile next = WebThemeDataIsolation.profileFor(nextProcess);

        assertTrue(first.isolated());
        assertEquals(first, next);
        assertTrue(first.name().startsWith(WebThemeDataIsolation.REMOTE_PROFILE_PREFIX));
        assertFalse(first.name().contains("theme.example"));
    }

    @Test
    public void differentRemoteOriginsNeverReuseOneProfile() {
        WebThemeDataIsolation.DataProfile first = WebThemeDataIsolation.profileFor(
                remote("https://theme-a.example/theme.json"));
        WebThemeDataIsolation.DataProfile second = WebThemeDataIsolation.profileFor(
                remote("https://theme-b.example/theme.json"));
        WebThemeDataIsolation.DataProfile alternatePort = WebThemeDataIsolation.profileFor(
                remote("https://theme-a.example:8443/theme.json"));

        assertNotEquals(first.name(), second.name());
        assertNotEquals(first.name(), alternatePort.name());
    }

    @Test
    public void profileSwitchRequiresANewWebViewDataPartition() {
        WebThemeDataIsolation.DataProfile trusted = WebThemeDataIsolation.trustedProfile();
        WebThemeDataIsolation.DataProfile first = WebThemeDataIsolation.profileFor(
                remote("https://theme-a.example/theme.json"));
        WebThemeDataIsolation.DataProfile sameOrigin = WebThemeDataIsolation.profileFor(
                remote("https://theme-a.example/home.html"));
        WebThemeDataIsolation.DataProfile otherOrigin = WebThemeDataIsolation.profileFor(
                remote("https://theme-b.example/theme.json"));

        assertTrue(WebThemeDataIsolation.requiresReplacement(trusted, first));
        assertFalse(WebThemeDataIsolation.requiresReplacement(first, sameOrigin));
        assertTrue(WebThemeDataIsolation.requiresReplacement(first, otherOrigin));
        assertTrue(WebThemeDataIsolation.requiresReplacement(first, trusted));
    }

    private static WebHomeTarget remote(String url) {
        return WebHomeTarget.resolve("", true, url, url);
    }
}
