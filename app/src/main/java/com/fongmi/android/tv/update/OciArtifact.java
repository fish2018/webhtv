package com.fongmi.android.tv.update;

import java.util.Locale;
import java.util.regex.Pattern;

public final class OciArtifact {

    private static final Pattern REPOSITORY = Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*(?:/[a-z0-9]+(?:[._-][a-z0-9]+)*)*");
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-fA-F]{64}");

    public final String registry;
    public final String repository;
    public final String reference;
    public final String manifestDigest;
    public final String layerDigest;
    public final long size;

    public OciArtifact(String registry, String repository, String reference, String manifestDigest, String layerDigest, long size) {
        this.registry = normalizeRegistry(registry);
        this.repository = repository == null ? "" : repository.trim().toLowerCase(Locale.ROOT);
        this.reference = reference == null ? "" : reference.trim();
        this.manifestDigest = normalizeDigest(manifestDigest);
        this.layerDigest = normalizeDigest(layerDigest);
        this.size = size;
    }

    public boolean isValid() {
        return !registry.isEmpty() && REPOSITORY.matcher(repository).matches() && !reference.isEmpty() && isDigest(manifestDigest) && isDigest(layerDigest) && size > 0;
    }

    public String getRegistryOrigin() {
        return "https://" + registry;
    }

    public static boolean isDigest(String value) {
        return value != null && DIGEST.matcher(value).matches();
    }

    private static String normalizeRegistry(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("https://")) text = text.substring("https://".length());
        while (text.endsWith("/")) text = text.substring(0, text.length() - 1);
        if (text.contains("/") || text.contains("@") || text.contains("?") || text.contains("#")) return "";
        return text;
    }

    private static String normalizeDigest(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
