package com.fongmi.android.tv.ad.audio;

public record SpeechAdConfig(boolean enabled, SpeechAdKeywordSet keywords,
                             int skipSeconds, AdSkipPolicyController.Mode mode) {
    public static final String DEFAULT_KEYWORDS =
            "麻将来了,澳门,赌场,娱乐城,荷官,百家乐,老虎机,时时彩,六合彩,彩票,下注,投注,首充,提现,棋牌,捕鱼,斗地主";

    public SpeechAdConfig {
        if (keywords == null) throw new NullPointerException("keywords");
        skipSeconds = Math.max(1, Math.min(120, skipSeconds));
        if (mode == null) mode = AdSkipPolicyController.Mode.PROMPT;
    }

    public static SpeechAdConfig defaults() {
        return create(false, DEFAULT_KEYWORDS, 15, "PROMPT");
    }

    public static SpeechAdConfig create(boolean enabled, String keywords,
                                        int skipSeconds, String mode) {
        AdSkipPolicyController.Mode parsed;
        try {
            parsed = AdSkipPolicyController.Mode.valueOf(mode);
        } catch (RuntimeException ignored) {
            parsed = AdSkipPolicyController.Mode.PROMPT;
        }
        return new SpeechAdConfig(enabled, SpeechAdKeywordSet.parse(keywords), skipSeconds, parsed);
    }
}