package com.fongmi.android.tv.ad.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AudioFingerprintRule {

    static final int MAX_ID_LENGTH = 128;
    static final int MIN_SEQUENCE_FRAMES = 4;
    static final int MAX_SEQUENCE_FRAMES = 4_096;
    static final int MAX_VARIANTS = 4;
    private static final long MIN_DURATION_MS = 1_000L;
    private static final long MAX_DURATION_MS = 10L * 60L * 1_000L;

    private final String id;
    private final long durationMs;
    private final long anchorOffsetMs;
    private final long anchorDurationMs;
    private final int[] fingerprint;
    private final List<int[]> variants;

    public AudioFingerprintRule(String id, long durationMs, long anchorOffsetMs,
                                long anchorDurationMs, int[] fingerprint, List<int[]> variants) {
        this.id = validateId(id);
        if (durationMs < MIN_DURATION_MS || durationMs > MAX_DURATION_MS) {
            throw new IllegalArgumentException("durationMs out of range");
        }
        if (anchorOffsetMs < 0 || anchorDurationMs < MIN_DURATION_MS
                || anchorOffsetMs > durationMs - anchorDurationMs) {
            throw new IllegalArgumentException("anchor range is invalid");
        }
        this.durationMs = durationMs;
        this.anchorOffsetMs = anchorOffsetMs;
        this.anchorDurationMs = anchorDurationMs;
        this.fingerprint = copySequence(fingerprint);
        this.variants = copyVariants(variants);
    }

    public String id() {
        return id;
    }

    public long durationMs() {
        return durationMs;
    }

    public long anchorOffsetMs() {
        return anchorOffsetMs;
    }

    public long anchorDurationMs() {
        return anchorDurationMs;
    }

    public int[] fingerprint() {
        return fingerprint.clone();
    }

    public List<int[]> variants() {
        return copyForCaller(variants);
    }

    public List<int[]> allSequences() {
        List<int[]> sequences = new ArrayList<>(variants.size() + 1);
        sequences.add(fingerprint.clone());
        for (int[] variant : variants) sequences.add(variant.clone());
        return Collections.unmodifiableList(sequences);
    }

    private static String validateId(String value) {
        if (value == null) throw new IllegalArgumentException("rule id is required");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("rule id length is invalid");
        }
        for (int i = 0; i < normalized.length(); i++) {
            if (Character.isISOControl(normalized.charAt(i))) {
                throw new IllegalArgumentException("rule id contains a control character");
            }
        }
        return normalized;
    }

    private static List<int[]> copyVariants(List<int[]> source) {
        if (source == null || source.isEmpty()) return List.of();
        if (source.size() > MAX_VARIANTS) throw new IllegalArgumentException("too many variants");
        List<int[]> result = new ArrayList<>(source.size());
        for (int[] variant : source) result.add(copySequence(variant));
        return Collections.unmodifiableList(result);
    }

    private static int[] copySequence(int[] source) {
        if (source == null || source.length < MIN_SEQUENCE_FRAMES || source.length > MAX_SEQUENCE_FRAMES) {
            throw new IllegalArgumentException("fingerprint sequence length is invalid");
        }
        return source.clone();
    }

    private static List<int[]> copyForCaller(List<int[]> source) {
        if (source.isEmpty()) return List.of();
        List<int[]> result = new ArrayList<>(source.size());
        for (int[] sequence : source) result.add(sequence.clone());
        return Collections.unmodifiableList(result);
    }
}
