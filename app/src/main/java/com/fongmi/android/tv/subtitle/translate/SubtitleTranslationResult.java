package com.fongmi.android.tv.subtitle.translate;

import com.fongmi.android.tv.subtitle.model.SubtitleAsset;

public final class SubtitleTranslationResult {

    private final Status status;
    private final SubtitleAsset sourceAsset;
    private final SubtitleAsset translatedAsset;
    private final String reason;
    private final int cueCount;
    private final int chunkCount;
    private final long costMs;

    private SubtitleTranslationResult(Status status, SubtitleAsset sourceAsset, SubtitleAsset translatedAsset, String reason, int cueCount, int chunkCount, long costMs) {
        this.status = status == null ? Status.ERROR : status;
        this.sourceAsset = sourceAsset;
        this.translatedAsset = translatedAsset;
        this.reason = reason == null ? "" : reason;
        this.cueCount = cueCount;
        this.chunkCount = chunkCount;
        this.costMs = costMs;
    }

    public static SubtitleTranslationResult of(Status status, SubtitleAsset sourceAsset, SubtitleAsset translatedAsset, String reason, int cueCount, int chunkCount, long costMs) {
        return new SubtitleTranslationResult(status, sourceAsset, translatedAsset, reason, cueCount, chunkCount, costMs);
    }

    public Status getStatus() {
        return status;
    }

    public SubtitleAsset getSourceAsset() {
        return sourceAsset;
    }

    public SubtitleAsset getTranslatedAsset() {
        return translatedAsset;
    }

    public String getReason() {
        return reason;
    }

    public int getCueCount() {
        return cueCount;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public long getCostMs() {
        return costMs;
    }

    public enum Status {
        TRANSLATED,
        CACHE_HIT,
        SKIPPED,
        ERROR,
        CANCELED
    }
}
