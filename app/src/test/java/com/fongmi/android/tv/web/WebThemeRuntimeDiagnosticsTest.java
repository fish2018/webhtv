package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.io.IOException;

public class WebThemeRuntimeDiagnosticsTest {

    @Test
    public void structuredEventUsesStableFieldsAndRedactsUrlSecrets() {
        WebHomeTarget target = WebHomeTarget.resolve("", true,
                "https://themes.example/theme.json",
                "https://themes.example/theme.json");

        String line = WebThemeRuntimeDiagnostics.format(
                WebThemeRuntimeDiagnostics.Event.MANIFEST_LOAD_FAILED,
                7,
                11,
                WebThemePage.DETAIL,
                target,
                "https://user:password@themes.example/theme.json?token=secret#part",
                WebThemeRuntimeDiagnostics.Reason.MANIFEST_IO,
                0);

        assertEquals("event=manifest_load_failed operation=7 generation=11 page=detail "
                + "mode=manifest_remote reason=manifest_io code=0 "
                + "url=https://themes.example/theme.json", line);
        assertFalse(line.contains("password"));
        assertFalse(line.contains("secret"));
        assertFalse(line.contains("#part"));
    }

    @Test
    public void manifestFailuresUseBoundedStableReasonCodes() {
        assertEquals(WebThemeRuntimeDiagnostics.Reason.PAGE_UNAVAILABLE,
                WebThemeRuntimeDiagnostics.manifestFailure(new IllegalArgumentException("private payload")));
        assertEquals(WebThemeRuntimeDiagnostics.Reason.MANIFEST_INVALID,
                WebThemeRuntimeDiagnostics.manifestFailure(
                        new IOException("private payload", new IllegalArgumentException("secret"))));
        assertEquals(WebThemeRuntimeDiagnostics.Reason.MANIFEST_IO,
                WebThemeRuntimeDiagnostics.manifestFailure(new IOException("https://host/path?token=secret")));
        assertEquals(WebThemeRuntimeDiagnostics.Reason.MANIFEST_IO,
                WebThemeRuntimeDiagnostics.manifestFailure(null));
    }

    @Test
    public void resolvedManifestCanDeclareLastKnownGoodWithoutExposingRefreshDetails() {
        WebHomeTarget target = WebHomeTarget.resolve("", true,
                "https://themes.example/theme.json",
                "https://themes.example/theme.json");

        String line = WebThemeRuntimeDiagnostics.format(
                WebThemeRuntimeDiagnostics.Event.MANIFEST_LOAD_RESOLVED,
                8,
                12,
                WebThemePage.HOME,
                target,
                "https://themes.example/theme.json?token=secret",
                WebThemeRuntimeDiagnostics.Reason.LAST_KNOWN_GOOD,
                0);

        assertEquals("event=manifest_load_resolved operation=8 generation=12 page=home "
                + "mode=manifest_remote reason=last_known_good code=0 "
                + "url=https://themes.example/theme.json", line);
        assertFalse(line.contains("secret"));
    }

    @Test
    public void rollbackUsesDedicatedLowCardinalityEventAndReason() {
        String line = WebThemeRuntimeDiagnostics.format(
                WebThemeRuntimeDiagnostics.Event.MANIFEST_ROLLBACK,
                9,
                13,
                WebThemePage.HOME,
                null,
                "https://themes.example/theme.json?token=secret",
                WebThemeRuntimeDiagnostics.Reason.ROLLBACK,
                0);

        assertEquals("event=manifest_rollback operation=9 generation=13 page=home "
                + "mode=none reason=rollback code=0 url=https://themes.example/theme.json", line);
        assertFalse(line.contains("secret"));
    }

    @Test
    public void consolePersistenceUsesMetadataWithoutPageControlledMessage() {
        String line = WebThemeRuntimeDiagnostics.formatConsole(
                "ERROR",
                42,
                "https://theme.example/app.js?token=private#console",
                "<secret>");

        assertEquals("level=error line=42 message_length=8 url=https://theme.example/app.js", line);
        assertFalse(line.contains("private"));
        assertFalse(line.contains("<secret>"));
    }

    @Test
    public void localAndLegacyTargetsHaveLowCardinalityModes() {
        WebHomeTarget localManifest = WebHomeTarget.resolve("", true,
                WebHomeTarget.ECLIPSE_URL, WebHomeTarget.ECLIPSE_URL);
        WebHomeTarget remoteLegacy = WebHomeTarget.resolve("", true,
                "https://themes.example/home.html", "https://themes.example/home.html");
        WebHomeTarget site = WebHomeTarget.resolve("https://source.example/home", false, "", "");

        assertEquals("manifest_local", WebThemeRuntimeDiagnostics.mode(localManifest));
        assertEquals("v1_remote", WebThemeRuntimeDiagnostics.mode(remoteLegacy));
        assertEquals("site", WebThemeRuntimeDiagnostics.mode(site));
        assertEquals("none", WebThemeRuntimeDiagnostics.mode(null));
    }
}
