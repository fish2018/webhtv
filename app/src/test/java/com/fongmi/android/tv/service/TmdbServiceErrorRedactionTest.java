package com.fongmi.android.tv.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbServiceErrorRedactionTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";

    @Test
    public void redactsApiKeyAndBearerFromNetworkErrorText() {
        String message = "failed url=https://api.themoviedb.org/3/movie/550?api_key=" + KEY + "&language=zh-CN Authorization: Bearer " + KEY;

        String redacted = TmdbService.redactMessage(message);

        assertFalse(redacted.contains(KEY));
        assertTrue(redacted.contains("api_key=<redacted>"));
        assertTrue(redacted.contains("Bearer <redacted>"));
    }

    @Test
    public void redactsAlternateCredentialQueryNamesCaseInsensitively() {
        String message = "https://api.tmdb.org/3/x?access_token=secret-token&foo=1&apikey=second-secret";

        String redacted = TmdbService.redactMessage(message);

        assertFalse(redacted.contains("secret-token"));
        assertFalse(redacted.contains("second-secret"));
        assertTrue(redacted.contains("access_token=<redacted>"));
        assertTrue(redacted.contains("apikey=<redacted>"));
    }

    @Test
    public void nullOrBlankErrorTextGetsStableNonSensitiveMessage() {
        assertEquals("TMDB request failed", TmdbService.redactMessage(null));
        assertEquals("TMDB request failed", TmdbService.redactMessage("   "));
    }
}
