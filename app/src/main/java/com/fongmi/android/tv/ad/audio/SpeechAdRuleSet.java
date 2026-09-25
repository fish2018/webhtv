package com.fongmi.android.tv.ad.audio;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, deduplicated collection of speech-ad rules. */
public final class SpeechAdRuleSet {

    static final int MAX_RULES = 256;

    private final List<SpeechAdRule> rules;
    private final Map<String, SpeechAdRule> byId;

    public SpeechAdRuleSet(List<SpeechAdRule> rules) {
        if (rules == null || rules.size() > MAX_RULES) {
            throw new IllegalArgumentException("speech rule count is invalid");
        }
        LinkedHashMap<String, SpeechAdRule> unique = new LinkedHashMap<>();
        for (SpeechAdRule rule : rules) {
            SpeechAdRule value = Objects.requireNonNull(rule, "rule");
            SpeechAdRule previous = unique.putIfAbsent(value.id(), value);
            if (previous != null && !previous.canonical().equals(value.canonical())) {
                throw new IllegalArgumentException("duplicate speech rule id");
            }
        }
        this.byId = Map.copyOf(unique);
        this.rules = List.copyOf(new ArrayList<>(unique.values()));
    }

    public static SpeechAdRuleSet empty() {
        return new SpeechAdRuleSet(List.of());
    }

    public List<SpeechAdRule> rules() {
        return rules;
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public SpeechAdRule find(String id) {
        return byId.get(id);
    }

    public String toText() {
        StringBuilder result = new StringBuilder();
        for (SpeechAdRule rule : rules) {
            if (result.length() > 0) result.append('\n');
            result.append(rule.canonical());
        }
        if (result.length() > SpeechAdRuleCodec.MAX_INPUT_BYTES
                || result.toString().getBytes(StandardCharsets.UTF_8).length
                > SpeechAdRuleCodec.MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("speech rules document is too large");
        }
        return result.toString();
    }
}
