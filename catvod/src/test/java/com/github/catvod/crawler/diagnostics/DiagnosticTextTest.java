package com.github.catvod.crawler.diagnostics;

import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiagnosticTextTest {
    @Test public void redactsCredentialsUrisAndControlCharactersBeforeStorage() {
        String input = "request=https://user:pass@example.test/path/secret?token=URL_SECRET#fragment\n"
                + "Cookie: sid=COOKIE_SECRET; more=value with spaces\r\nAuthorization: Bearer AUTH_SECRET\n"
                + "{\"access_token\":\"JSON_SECRET\",\"password\":\"PASS_SECRET\"}\n"
                + "at /storage/emulated/0/private/movie.mkv 192.168.1.42\nFORGED [thread] av-diag: {}\u0000";
        String safe = DiagnosticText.clean(input).text();
        for (String secret : new String[]{"URL_SECRET", "COOKIE_SECRET", "AUTH_SECRET", "JSON_SECRET", "PASS_SECRET", "movie.mkv", "192.168.1.42", "example.test"}) assertFalse(secret, safe.contains(secret));
        assertFalse(safe.contains("\n")); assertFalse(safe.contains("\r")); assertFalse(safe.contains("\u0000"));
        assertTrue(safe.contains("\\n"));
    }

    @Test public void localSuggestionsRetainOnlyOrigins() {
        assertEquals(java.util.List.of("https://example.test:8443"), DiagnosticText.origins("https://user:pass@example.test:8443/private?token=SECRET"));
        assertFalse(DiagnosticText.clean("https://example.test/private").text().contains("example.test"));
    }

    @Test public void exceptionsKeepTypesAndCausesWithBoundedText() {
        IllegalStateException inner = new IllegalStateException("token=INNER_SECRET");
        RuntimeException outer = new RuntimeException("https://host/private?access_token=OUTER_SECRET", inner);
        inner.addSuppressed(outer);
        String text = DiagnosticText.throwable(outer);
        assertTrue(text.contains("IllegalStateException")); assertTrue(text.contains("Caused by"));
        assertTrue(text.contains("truncated exception chain/cycle"));
        assertFalse(text.contains("INNER_SECRET")); assertFalse(text.contains("OUTER_SECRET"));
        assertTrue(text.length() < 13000);
    }

    @Test(timeout = 3000) public void longUnstructuredTextIsBounded() {
        DiagnosticText.Clean clean = DiagnosticText.clean("a".repeat(100_000));
        assertTrue(clean.truncated()); assertTrue(clean.text().length() < 13000);
    }

    @Test public void structuredFactsDistinguishFalseZeroAndUnknown() {
        DiagnosticEvent event = new DiagnosticEvent("play.lifecycle", "p-a-1", "controller-1", 1, 1)
                .observed("closed", false).observed("elapsedMs", 0).observed("decode", Double.NaN)
                .unknown("audio", DiagnosticEvent.Status.NOT_COLLECTED).unknown("audibility", DiagnosticEvent.Status.NOT_OBSERVABLE);
        var observed = JsonParser.parseString(event.json()).getAsJsonObject().getAsJsonObject("observed");
        assertFalse(observed.getAsJsonObject("closed").get("value").getAsBoolean());
        assertEquals(0, observed.getAsJsonObject("elapsedMs").get("value").getAsInt());
        assertFalse(observed.getAsJsonObject("decode").has("value"));
        assertFalse(observed.getAsJsonObject("audio").has("value"));
        assertEquals("not-observable", observed.getAsJsonObject("audibility").get("status").getAsString());
    }

    @Test(expected = IllegalArgumentException.class) public void schemaRejectsArbitrarySensitiveFields() {
        new DiagnosticEvent("env.device", "none", "process", 0, 0).observed("Authorization", "SECRET");
    }

    @Test(expected = IllegalArgumentException.class) public void schemaRejectsUnknownEvents() {
        new DiagnosticEvent("untrusted.event", "none", "process", 0, 0);
    }
}
