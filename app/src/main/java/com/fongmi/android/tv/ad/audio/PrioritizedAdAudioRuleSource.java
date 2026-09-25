package com.fongmi.android.tv.ad.audio;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PrioritizedAdAudioRuleSource implements AdAudioRuleSource {

    private static final String LOCAL_SOURCE_INVALID = "LOCAL_SOURCE_INVALID";

    private final AdAudioRuleSource localSource;
    private final AdAudioRuleSource signedSource;

    public PrioritizedAdAudioRuleSource(
            AdAudioRuleSource localSource, AdAudioRuleSource signedSource) {
        if (localSource == null || signedSource == null) {
            throw new IllegalArgumentException("local and signed sources are required");
        }
        this.localSource = localSource;
        this.signedSource = signedSource;
    }

    @Override
    public AdAudioRuleSnapshot load() {
        AdAudioRuleSnapshot local = loadLocal();
        if (isUsable(local)) return local;

        AdAudioRuleSnapshot signed = signedSource.load();
        if (isUsable(signed)) {
            if (!local.hasError()) return signed;
            return copy(signed, signed.ruleSet(),
                    mergedWarnings(signed.warnings(), local.warnings(), LOCAL_SOURCE_INVALID), "");
        }

        if (local.hasError()) {
            return copy(local, AudioFingerprintRuleSet.empty(),
                    mergedWarnings(local.warnings(), signed.warnings(), LOCAL_SOURCE_INVALID),
                    local.lastError());
        }
        if (signed.hasError()) {
            return copy(signed, AudioFingerprintRuleSet.empty(), signed.warnings(),
                    signed.lastError());
        }
        return signed;
    }

    private AdAudioRuleSnapshot loadLocal() {
        try {
            AdAudioRuleSnapshot loaded = localSource.load();
            return loaded == null ? invalidLocalSnapshot() : loaded;
        } catch (RuntimeException e) {
            return invalidLocalSnapshot();
        }
    }

    private static AdAudioRuleSnapshot invalidLocalSnapshot() {
        return new AdAudioRuleSnapshot(
                "local", "", AudioFingerprintRuleSet.empty(), List.of(), "RULE_LOAD_FAILED");
    }

    private static boolean isUsable(AdAudioRuleSnapshot snapshot) {
        return snapshot.hasRules() && !snapshot.hasError();
    }

    private static AdAudioRuleSnapshot copy(
            AdAudioRuleSnapshot source, AudioFingerprintRuleSet ruleSet,
            List<String> warnings, String error) {
        return new AdAudioRuleSnapshot(
                source.sourceId(), source.version(), ruleSet, warnings, error);
    }

    private static List<String> mergedWarnings(
            List<String> first, List<String> second, String additional) {
        Set<String> merged = new LinkedHashSet<>(first);
        merged.addAll(second);
        merged.add(additional);
        return new ArrayList<>(merged);
    }
}
