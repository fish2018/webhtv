package com.fongmi.android.tv.ad.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AudioFingerprintRuleSet {

    public static final int SCHEMA_VERSION = 2;
    public static final String ALGORITHM_ID = "spectral-sequence-v2";
    public static final int MAX_RULES = 2_048;

    private final AudioFingerprintConfig config;
    private final List<AudioFingerprintRule> rules;

    public AudioFingerprintRuleSet(AudioFingerprintConfig config, List<AudioFingerprintRule> rules) {
        if (config == null) throw new IllegalArgumentException("config is required");
        if (rules == null) throw new IllegalArgumentException("rules are required");
        if (rules.size() > MAX_RULES) throw new IllegalArgumentException("too many rules");
        Set<String> ids = new HashSet<>();
        List<AudioFingerprintRule> copy = new ArrayList<>(rules.size());
        for (AudioFingerprintRule rule : rules) {
            if (rule == null) throw new IllegalArgumentException("rule is required");
            if (!ids.add(rule.id())) throw new IllegalArgumentException("duplicate rule id: " + rule.id());
            copy.add(rule);
        }
        this.config = config;
        this.rules = Collections.unmodifiableList(copy);
    }

    public static AudioFingerprintRuleSet empty() {
        return new AudioFingerprintRuleSet(AudioFingerprintConfig.standard(), List.of());
    }

    public AudioFingerprintConfig config() {
        return config;
    }

    public List<AudioFingerprintRule> rules() {
        return rules;
    }
}
