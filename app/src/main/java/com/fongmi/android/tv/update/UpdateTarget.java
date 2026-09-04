package com.fongmi.android.tv.update;

public final class UpdateTarget {

    public enum Kind {
        GITHUB,
        OCI
    }

    public final Kind kind;
    public final String url;
    public final String endpoint;
    public final OciArtifact artifact;

    private UpdateTarget(Kind kind, String url, String endpoint, OciArtifact artifact) {
        this.kind = kind;
        this.url = url;
        this.endpoint = endpoint;
        this.artifact = artifact;
    }

    public static UpdateTarget github(String url) {
        return new UpdateTarget(Kind.GITHUB, UpdateUrl.requireHttpsUrl(url), "", null);
    }

    public static UpdateTarget oci(String endpoint, OciArtifact artifact) {
        if (artifact == null || !artifact.isValid()) throw new IllegalArgumentException("Invalid OCI artifact");
        return new UpdateTarget(Kind.OCI, "", UpdateUrl.requireHttpsOrigin(endpoint), artifact);
    }
}
