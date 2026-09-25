package com.fongmi.android.tv.ad.audio;

public final class SignedRulePackageException extends Exception {

    public enum Code {
        PACKAGE_TOO_LARGE,
        PACKAGE_MALFORMED,
        UNSUPPORTED_PACKAGE_SCHEMA,
        PACKAGE_ID_MISMATCH,
        UNSUPPORTED_SIGNATURE_ALGORITHM,
        UNKNOWN_KEY,
        KEY_NOT_VALID,
        PAYLOAD_DIGEST_MISMATCH,
        PAYLOAD_INVALID,
        SIGNATURE_INVALID,
        PACKAGE_NOT_YET_VALID,
        PACKAGE_EXPIRED,
        REVISION_ROLLBACK,
        REVISION_CONFLICT,
        CACHE_IO_FAILED,
        NO_VALID_SIGNED_PACKAGE
    }

    private final Code code;

    public SignedRulePackageException(Code code) {
        super(requireCode(code).name());
        this.code = code;
    }

    public Code code() {
        return code;
    }

    private static Code requireCode(Code code) {
        if (code == null) throw new IllegalArgumentException("code is required");
        return code;
    }
}
