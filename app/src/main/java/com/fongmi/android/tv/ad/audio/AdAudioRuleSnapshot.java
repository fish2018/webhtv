package com.fongmi.android.tv.ad.audio;

import java.util.List;

public record AdAudioRuleSnapshot(String sourceId, String version,
                                  AudioFingerprintRuleSet ruleSet,
                                  List<String> warnings, String lastError,
                                  ProbeRuleSidecar probeSidecar) {

    public AdAudioRuleSnapshot(String sourceId, String version,
                               AudioFingerprintRuleSet ruleSet,
                               List<String> warnings, String lastError) {
        this(sourceId, version, ruleSet, warnings, lastError, null);
    }

    public AdAudioRuleSnapshot {
        if (sourceId == null || version == null || ruleSet == null
                || warnings == null || lastError == null) {
            throw new IllegalArgumentException("snapshot fields are required");
        }
        warnings = List.copyOf(warnings);
    }

    public boolean hasRules() {
        return !ruleSet.rules().isEmpty();
    }

    public boolean hasError() {
        return !lastError.isEmpty();
    }

    public boolean probeAvailable() {
        return probeSidecar != null;
    }
}
