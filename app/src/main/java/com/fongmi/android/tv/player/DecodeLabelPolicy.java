package com.fongmi.android.tv.player;

import androidx.annotation.Nullable;

/**
 * Decides how to label the decode profile when the configured profile and the decoder that is
 * actually running disagree.
 *
 * <p>Classification is deliberately NOT done here. {@link PlaybackMediaFactsMapper} already
 * resolves the running decoder into a {@link PlaybackAutoContext.DecodeMode}, and it does so
 * better than a name heuristic can: it trusts the kind each engine reports about itself (IJK
 * via {@code FFP_PROPV_DECODER_*}, MPV via {@code hwdec}) and only falls back to parsing the
 * decoder name when the engine reports {@code UNKNOWN}. Reusing that fact keeps one source of
 * truth and covers every kernel rather than just Exo.
 */
public final class DecodeLabelPolicy {

    private DecodeLabelPolicy() {
    }

    /**
     * Returns whether a session configured for hardware decode is in fact decoding in
     * software. An unknown mode never produces a claim, so an unresolved decoder
     * under-reports instead of showing a wrong mismatch.
     */
    public static boolean isHardwareProfileRunningSoftware(
            boolean hardwareProfile, @Nullable PlaybackAutoContext.DecodeMode actual) {
        return hardwareProfile && actual == PlaybackAutoContext.DecodeMode.SOFTWARE;
    }

    /**
     * Decode label reflecting reality. On a mismatch both sides are shown so it is visible at
     * a glance, rather than requiring the user to cross-read the video row's decoder name.
     *
     * <p>{@code softLabel} must be supplied by the caller from the same localized
     * {@code select_decode} array the configured label came from. Hardcoding it here would
     * emit mixed-language text such as {@code Hard→软解} on a non-Chinese locale, or mixed
     * simplified/traditional text on zh-rTW.
     */
    public static String decodeLabel(
            String configuredLabel,
            String softLabel,
            boolean hardwareProfile,
            @Nullable PlaybackAutoContext.DecodeMode actual) {
        if (!isHardwareProfileRunningSoftware(hardwareProfile, actual)) return configuredLabel;
        if (softLabel == null || softLabel.isBlank()) return configuredLabel;
        return configuredLabel + "→" + softLabel;
    }
}
