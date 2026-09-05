package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RangeResponseValidatorTest {

    @Test
    public void accepts206WhenMetadataAndBodyLengthMatchRequestedRange() {
        HttpByteRange requested = new HttpByteRange(100, 199, 1_000);

        RangeResponseValidator.Result result = RangeResponseValidator.validate(
                requested,
                206,
                "bytes 100-199/1000",
                100,
                "identity");

        assertTrue(result.isAccepted());
        assertEquals(RangeResponseValidator.Status.ACCEPTED, result.status());
        assertEquals(100, result.contentRange().length());
    }

    @Test
    public void maps200ToSingleConnectionFallbackAnd416ToUnsatisfiable() {
        HttpByteRange requested = new HttpByteRange(0, 99, 1_000);

        RangeResponseValidator.Result fallback = RangeResponseValidator.validate(
                requested, 200, null, 1_000, null);
        assertEquals(RangeResponseValidator.Status.FALLBACK_SINGLE_CONNECTION, fallback.status());
        assertFalse(fallback.isAccepted());

        RangeResponseValidator.Result unsatisfiable = RangeResponseValidator.validate(
                requested, 416, "bytes */1000", 0, null);
        assertEquals(RangeResponseValidator.Status.UNSATISFIABLE, unsatisfiable.status());
        assertFalse(unsatisfiable.isAccepted());
    }

    @Test
    public void rejectsWrongRangeLengthEncodingAndUnexpectedStatus() {
        HttpByteRange requested = new HttpByteRange(100, 199, 1_000);

        assertEquals(RangeResponseValidator.Status.CONTENT_RANGE_MISMATCH,
                RangeResponseValidator.validate(requested, 206, "bytes 0-99/1000", 100, null).status());
        assertEquals(RangeResponseValidator.Status.BODY_LENGTH_MISMATCH,
                RangeResponseValidator.validate(requested, 206, "bytes 100-199/1000", 99, null).status());
        assertEquals(RangeResponseValidator.Status.ENCODED_RESPONSE,
                RangeResponseValidator.validate(requested, 206, "bytes 100-199/1000", 100, "gzip").status());
        assertEquals(RangeResponseValidator.Status.MISSING_CONTENT_RANGE,
                RangeResponseValidator.validate(requested, 206, null, 100, null).status());
        assertEquals(RangeResponseValidator.Status.UNEXPECTED_STATUS,
                RangeResponseValidator.validate(requested, 500, null, -1, null).status());
    }
}
