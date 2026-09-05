package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HttpByteRangeTest {

    @Test
    public void parsesOpenEndedExplicitAndSuffixRanges() {
        assertRange("bytes=0-", 1_000, 0, 999);
        assertRange("bytes=100-199", 1_000, 100, 199);
        assertRange("bytes=900-2000", 1_000, 900, 999);
        assertRange("bytes=-500", 1_000, 500, 999);
        assertRange("bytes=-2000", 1_000, 0, 999);
    }

    @Test
    public void reportsAbsentUnsupportedAndMultipleRangesExplicitly() {
        assertStatus(null, 1_000, HttpByteRange.ParseStatus.ABSENT);
        assertStatus("  ", 1_000, HttpByteRange.ParseStatus.ABSENT);
        assertStatus("items=0-1", 1_000, HttpByteRange.ParseStatus.UNSUPPORTED_UNIT);
        assertStatus("bytes=0-0,2-2", 1_000, HttpByteRange.ParseStatus.MULTIPLE_RANGES);
    }

    @Test
    public void rejectsMalformedUnsatisfiableAndOverflowingRanges() {
        assertStatus("bytes=", 1_000, HttpByteRange.ParseStatus.MALFORMED);
        assertStatus("bytes=200-100", 1_000, HttpByteRange.ParseStatus.MALFORMED);
        assertStatus("bytes=abc-def", 1_000, HttpByteRange.ParseStatus.MALFORMED);
        assertStatus("bytes=1000-", 1_000, HttpByteRange.ParseStatus.UNSATISFIABLE);
        assertStatus("bytes=-0", 1_000, HttpByteRange.ParseStatus.UNSATISFIABLE);
        assertStatus("bytes=0-", 0, HttpByteRange.ParseStatus.UNSATISFIABLE);
        assertStatus("bytes=9223372036854775808-", Long.MAX_VALUE,
                HttpByteRange.ParseStatus.NUMBER_OVERFLOW);
    }

    @Test
    public void exposesInclusiveLengthAndNormalizedHeaders() {
        HttpByteRange range = valid("bytes=100-199", 1_000);

        assertEquals(100, range.length());
        assertEquals("bytes=100-199", range.requestHeader());
        assertEquals("bytes 100-199/1000", range.contentRange());
    }

    private static void assertRange(String header, long totalLength, long start, long end) {
        HttpByteRange range = valid(header, totalLength);
        assertEquals(start, range.startInclusive());
        assertEquals(end, range.endInclusive());
        assertEquals(totalLength, range.totalLength());
    }

    private static HttpByteRange valid(String header, long totalLength) {
        HttpByteRange.ParseResult result = HttpByteRange.parse(header, totalLength);
        assertTrue(result.isValid());
        assertEquals(HttpByteRange.ParseStatus.VALID, result.status());
        return result.range();
    }

    private static void assertStatus(String header, long totalLength, HttpByteRange.ParseStatus status) {
        HttpByteRange.ParseResult result = HttpByteRange.parse(header, totalLength);
        assertEquals(status, result.status());
        assertFalse(result.isValid());
        assertNull(result.range());
    }
}
