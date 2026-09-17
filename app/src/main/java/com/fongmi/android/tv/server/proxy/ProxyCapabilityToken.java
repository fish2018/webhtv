package com.fongmi.android.tv.server.proxy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Process-scoped capability credential for local proxy control endpoints.
 * The raw token is intentionally kept out of persistence and diagnostic logging.
 */
public final class ProxyCapabilityToken {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] encodedValue;
    private final String value;

    private ProxyCapabilityToken(byte[] secret) {
        this.value = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        this.encodedValue = value.getBytes(StandardCharsets.US_ASCII);
    }

    public static ProxyCapabilityToken generate() {
        byte[] secret = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(secret);
        try {
            return new ProxyCapabilityToken(secret);
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    public String value() {
        return value;
    }

    public boolean matches(String candidate) {
        if (candidate == null) return false;
        byte[] candidateBytes = candidate.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(encodedValue, candidateBytes);
    }

    @Override
    public String toString() {
        return "ProxyCapabilityToken{redacted}";
    }
}