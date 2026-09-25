package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RangeResponseValidatorResourceIdentityTest {

    @Test
    public void acceptsMatchingStrongEtagAndRejectsChangedResource() {
        HttpByteRange requested = new HttpByteRange(0, 99, 1_000);
        UpstreamResourceValidator expected = UpstreamResourceValidator.select("\"v1\"", null);

        RangeResponseValidator.Result accepted = RangeResponseValidator.validate(
                requested, 206, "bytes 0-99/1000", 100, "identity", expected, "\"v1\"", null);
        RangeResponseValidator.Result changed = RangeResponseValidator.validate(
                requested, 206, "bytes 0-99/1000", 100, "identity", expected, "\"v2\"", null);
        RangeResponseValidator.Result missing = RangeResponseValidator.validate(
                requested, 206, "bytes 0-99/1000", 100, "identity", expected, null, null);

        assertTrue(accepted.isAccepted());
        assertEquals(RangeResponseValidator.Status.RESOURCE_CHANGED, changed.status());
        assertEquals(RangeResponseValidator.Status.RESOURCE_CHANGED, missing.status());
    }

    @Test
    public void mapsIfRangePreconditionFailureAndAcceptsMatchingLastModified() {
        HttpByteRange requested = new HttpByteRange(100, 199, 1_000);
        String modified = "Mon, 10 Aug 2026 08:00:00 GMT";
        UpstreamResourceValidator expected = UpstreamResourceValidator.select("W/\"v1\"", modified);

        RangeResponseValidator.Result precondition = RangeResponseValidator.validate(
                requested, 412, null, -1, null, expected, null, null);
        RangeResponseValidator.Result accepted = RangeResponseValidator.validate(
                requested, 206, "bytes 100-199/1000", 100, "", expected, "W/\"different\"", modified);

        assertEquals(RangeResponseValidator.Status.PRECONDITION_FAILED, precondition.status());
        assertTrue(accepted.isAccepted());
    }
}