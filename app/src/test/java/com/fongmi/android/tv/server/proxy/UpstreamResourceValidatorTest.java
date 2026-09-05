package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UpstreamResourceValidatorTest {

    @Test
    public void prefersStrongEtagAndUsesItForIfRangeAndChunkMatching() {
        UpstreamResourceValidator validator = UpstreamResourceValidator.select(
                "\"asset-v1\"",
                "Mon, 10 Aug 2026 08:00:00 GMT");

        assertEquals(UpstreamResourceValidator.Type.STRONG_ETAG, validator.type());
        assertEquals("\"asset-v1\"", validator.ifRangeValue());
        assertTrue(validator.isUsable());
        assertTrue(validator.isStrong());
        assertTrue(validator.matches("\"asset-v1\"", "changed-date"));
        assertFalse(validator.matches("\"asset-v2\"", "Mon, 10 Aug 2026 08:00:00 GMT"));
        assertFalse(validator.matches(null, "Mon, 10 Aug 2026 08:00:00 GMT"));
    }

    @Test
    public void ignoresWeakEtagAndFallsBackToLastModified() {
        String modified = "Mon, 10 Aug 2026 08:00:00 GMT";
        UpstreamResourceValidator validator = UpstreamResourceValidator.select("W/\"asset-v1\"", modified);

        assertEquals(UpstreamResourceValidator.Type.LAST_MODIFIED, validator.type());
        assertEquals(modified, validator.ifRangeValue());
        assertTrue(validator.isUsable());
        assertFalse(validator.isStrong());
        assertTrue(validator.matches("\"different-etag\"", modified));
        assertFalse(validator.matches("W/\"asset-v1\"", "Tue, 11 Aug 2026 08:00:00 GMT"));
    }

    @Test
    public void representsMissingOrMalformedValidatorsExplicitly() {
        UpstreamResourceValidator missing = UpstreamResourceValidator.select("not-an-etag", "  ");

        assertEquals(UpstreamResourceValidator.Type.NONE, missing.type());
        assertFalse(missing.isUsable());
        assertFalse(missing.isStrong());
        assertNull(missing.ifRangeValue());
        assertTrue(missing.matches(null, null));
        assertTrue(missing.matches("\"anything\"", "any-date"));
    }
}