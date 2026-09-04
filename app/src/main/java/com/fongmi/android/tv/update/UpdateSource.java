package com.fongmi.android.tv.update;

public final class UpdateSource {

    public static final String GITHUB = "github";
    public static final String OCI = "oci";

    private UpdateSource() {
    }

    public static String normalize(String value) {
        if (GITHUB.equals(value)) return GITHUB;
        return OCI;
    }
}
