package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HttpContentRangeTest {

    @Test
    public void parsesValidAndUnsatisfiedContentRanges() {
        HttpContentRange.ParseResult valid = HttpContentRange.parse("bytes 100-199/1000");
        assertTrue(valid.isValid());
        assertEquals(100, valid.range().startInclusive());
        assertEquals(199, valid.range().endInclusive());
        assertEquals(1_000, valid.range().totalLength());

        HttpContentRange.ParseResult unsatisfied = HttpContentRange.parse("bytes */1000");
        assertEquals(HttpContentRange.ParseStatus.UNSATISFIED, unsatisfied.status());
        assertFalse(unsatisfied.isValid());
        assertNull(unsatisfied.range());
        assertEquals(1_000, unsatisfied.totalLength());
    }

    @Test
    public void rejectsMissingUnknownMalformedAndOverflowingMetadata() {
        assertStatus(null, HttpContentRange.ParseStatus.MISSING);
        assertStatus("items 0-1/10", HttpContentRange.ParseStatus.UNSUPPORTED_UNIT);
        assertStatus("bytes 0-1/*", HttpContentRange.ParseStatus.UNKNOWN_TOTAL);
        assertStatus("bytes 1-0/10", HttpContentRange.ParseStatus.MALFORMED);
        assertStatus("bytes 0-10/10", HttpContentRange.ParseStatus.MALFORMED);
        assertStatus("bytes 9223372036854775808-1/10", HttpContentRange.ParseStatus.NUMBER_OVERFLOW);
    }

    private static void assertStatus(String header, HttpContentRange.ParseStatus expected) {
        HttpContentRange.ParseResult result = HttpContentRange.parse(header);
        assertEquals(expected, result.status());
        assertFalse(result.isValid());
        assertNull(result.range());
    }
}
