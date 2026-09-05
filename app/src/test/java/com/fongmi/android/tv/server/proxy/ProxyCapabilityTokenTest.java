package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ProxyCapabilityTokenTest {

    @Test
    public void generatesUrlSafeProcessTokenAndMatchesOnlyExactValue() {
        ProxyCapabilityToken token = ProxyCapabilityToken.generate();

        assertEquals(43, token.value().length());
        assertTrue(token.value().matches("[A-Za-z0-9_-]+"));
        assertTrue(token.matches(token.value()));
        assertFalse(token.matches(null));
        assertFalse(token.matches("invalid"));
        assertFalse(token.matches(token.value() + "x"));
    }

    @Test
    public void generatedTokensAreIndependentAndCannotBeReusedAfterMutation() {
        ProxyCapabilityToken first = ProxyCapabilityToken.generate();
        ProxyCapabilityToken second = ProxyCapabilityToken.generate();

        assertNotEquals(first.value(), second.value());
        assertFalse(first.matches(second.value()));
        assertFalse(first.matches(first.value().substring(1)));
    }

    @Test
    public void malformedAndNonCanonicalValuesAreRejected() {
        ProxyCapabilityToken token = ProxyCapabilityToken.generate();

        assertFalse(token.matches("!@#$%^&*()"));
        assertFalse(token.matches(" " + token.value()));
        assertFalse(token.matches(token.value() + "="));
    }
}