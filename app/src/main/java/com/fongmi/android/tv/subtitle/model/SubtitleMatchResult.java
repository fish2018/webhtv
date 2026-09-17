package com.fongmi.android.tv.subtitle.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SubtitleMatchResult {

    private final SubtitleMatchStatus status;
    private final SubtitleCandidate selected;
    private final SubtitleAsset asset;
    private final List<SubtitleCandidate> candidates;
    private final String reason;
    private final boolean cacheHit;

    public SubtitleMatchResult(SubtitleMatchStatus status, SubtitleCandidate selected, SubtitleAsset asset, List<SubtitleCandidate> candidates, String reason, boolean cacheHit) {
        this.status = status == null ? SubtitleMatchStatus.NO_MATCH : status;
        this.selected = selected;
        this.asset = asset;
        this.candidates = candidates == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(candidates));
        this.reason = reason == null ? "" : reason;
        this.cacheHit = cacheHit;
    }

    public static SubtitleMatchResult matched(SubtitleCandidate selected, SubtitleAsset asset, List<SubtitleCandidate> candidates) {
        return new SubtitleMatchResult(SubtitleMatchStatus.MATCHED, selected, asset, candidates, "", false);
    }

    public static SubtitleMatchResult noMatch(List<SubtitleCandidate> candidates, String reason) {
        return new SubtitleMatchResult(SubtitleMatchStatus.NO_MATCH, null, null, candidates, reason, false);
    }

    public static SubtitleMatchResult skipped(String reason) {
        return new SubtitleMatchResult(SubtitleMatchStatus.SKIPPED, null, null, Collections.emptyList(), reason, false);
    }

    public static SubtitleMatchResult error(String reason) {
        return new SubtitleMatchResult(SubtitleMatchStatus.ERROR, null, null, Collections.emptyList(), reason, false);
    }

    public static SubtitleMatchResult canceled() {
        return new SubtitleMatchResult(SubtitleMatchStatus.CANCELED, null, null, Collections.emptyList(), "canceled", false);
    }

    public SubtitleMatchStatus getStatus() {
        return status;
    }

    public SubtitleCandidate getSelected() {
        return selected;
    }

    public SubtitleAsset getAsset() {
        return asset;
    }

    public List<SubtitleCandidate> getCandidates() {
        return candidates;
    }

    public String getReason() {
        return reason;
    }

    public boolean isCacheHit() {
        return cacheHit;
    }
}
