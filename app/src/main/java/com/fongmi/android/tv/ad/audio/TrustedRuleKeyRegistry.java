package com.fongmi.android.tv.ad.audio;

import com.google.crypto.tink.PublicKeyVerify;

import java.util.Optional;
import java.util.regex.Pattern;

@FunctionalInterface
public interface TrustedRuleKeyRegistry {

    Optional<Entry> find(String keyId);

    enum Status {
        ACTIVE,
        RETIRED,
        REVOKED
    }

    record Entry(String keyId, String algorithm, long notBeforeEpochMs,
                 long notAfterEpochMs, Status status, PublicKeyVerify verifier) {

        private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");
        private static final Pattern ALGORITHM_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,32}");

        public Entry {
            if (keyId == null || !ID_PATTERN.matcher(keyId).matches()) {
                throw new IllegalArgumentException("keyId is invalid");
            }
            if (algorithm == null || !ALGORITHM_PATTERN.matcher(algorithm).matches()) {
                throw new IllegalArgumentException("algorithm is invalid");
            }
            if (notBeforeEpochMs < 0 || notAfterEpochMs < notBeforeEpochMs) {
                throw new IllegalArgumentException("key validity is invalid");
            }
            if (status == null || verifier == null) {
                throw new IllegalArgumentException("key status and verifier are required");
            }
        }
    }
}
