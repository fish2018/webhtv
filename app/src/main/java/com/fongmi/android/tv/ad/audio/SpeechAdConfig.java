package com.fongmi.android.tv.ad.audio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public record SpeechAdConfig(boolean enabled, SpeechAdKeywordSet keywords,
                             SpeechAdRuleSet rules, int skipSeconds,
                             AdSkipPolicyController.Mode mode) {
    public static final String DEFAULT_KEYWORDS =
            "麻将来了,澳门,赌场,娱乐城,荷官,百家乐,老虎机,时时彩,六合彩,彩票,下注,投注,首充,提现,棋牌,捕鱼,斗地主";

    /**
     * Keeps the pre-V2 constructor contract for callers that only configure the
     * original flat keyword list.
     */
    public SpeechAdConfig(boolean enabled, SpeechAdKeywordSet keywords,
                          int skipSeconds, AdSkipPolicyController.Mode mode) {
        this(enabled, keywords, SpeechAdRuleSet.empty(), skipSeconds, mode);
    }

    public SpeechAdConfig {
        if (keywords == null) throw new NullPointerException("keywords");
        if (rules == null) throw new NullPointerException("rules");
        skipSeconds = Math.max(1, Math.min(120, skipSeconds));
        if (mode == null) mode = AdSkipPolicyController.Mode.PROMPT;
    }

    public static SpeechAdConfig defaults() {
        return create(false, DEFAULT_KEYWORDS, 15, "PROMPT");
    }

    public static SpeechAdConfig create(boolean enabled, String keywords,
                                        int skipSeconds, String mode) {
        return create(enabled, keywords, SpeechAdRuleSet.empty(), skipSeconds, mode);
    }

    /** Creates a config with the bounded V2 speech-rule snapshot. */
    public static SpeechAdConfig create(boolean enabled, String keywords,
                                        SpeechAdRuleSet rules,
                                        int skipSeconds, String mode) {
        AdSkipPolicyController.Mode parsed;
        try {
            parsed = AdSkipPolicyController.Mode.valueOf(mode);
        } catch (RuntimeException ignored) {
            parsed = AdSkipPolicyController.Mode.PROMPT;
        }
        return new SpeechAdConfig(enabled, SpeechAdKeywordSet.parse(keywords),
                rules, skipSeconds, parsed);
    }

    /**
     * Parses a local V2 rule document at the configuration boundary. A malformed
     * document is rejected as a whole by {@link SpeechAdRuleCodec}; callers keep
     * the previous config rather than silently enabling a partial rule set.
     */
    public static SpeechAdConfig createWithRules(boolean enabled, String keywords,
                                                 String ruleDocument,
                                                 int skipSeconds, String mode) {
        return create(enabled, keywords, SpeechAdRuleCodec.parse(ruleDocument),
                skipSeconds, mode);
    }

    public boolean hasSpeechRules() {
        return !keywords.isEmpty() || !rules.isEmpty();
    }

    /** Stable routing suffix; rule contents never enter logs or diagnostics. */
    public String rulesVersion() {
        if (rules.isEmpty()) return "";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rules.toText().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                result.append(String.format("%02x", digest[i] & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
