package com.fongmi.android.tv.ad.feedback;

import com.fongmi.android.tv.api.config.HlsRuleStateStore;
import com.fongmi.android.tv.api.config.UserAdRuleStore;
import com.fongmi.android.tv.api.config.UserHlsRuleStore;
import com.fongmi.android.tv.bean.HlsAdRule;
import com.fongmi.android.tv.bean.UserAdRule;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 把仲裁选出的方案写成真实规则。
 *
 * <p>数据全部取自 {@link RulePayload}，不从证据文本里解析 —— 证据是给人看的、
 * 随语言变化。见设计文档第 6.2 节。
 */
public final class AdRulePlanApplier {

    private AdRulePlanApplier() {
    }

    /** 落地结果。 */
    public enum Outcome {
        /** 已写入并立即生效。 */
        APPLIED,
        /** 方案不可落地或数据为空。 */
        SKIPPED,
        /** 规则非法或写入失败。 */
        FAILED
    }

    /**
     * @param plan    仲裁首选方案
     * @param siteKey 当前站点，用于收窄用户规则作用域
     */
    public static Outcome apply(AdAttribution plan, String siteKey) {
        if (plan == null || !plan.actionable()) return Outcome.SKIPPED;
        try {
            return switch (plan.remediation()) {
                case ENABLE_EXISTING_RULE -> enableExisting(plan.payload());
                case HLS_STRUCTURED_RULE -> saveHlsRule(plan, siteKey);
                case HOST_BLACKLIST -> saveUserRule(plan, siteKey, true);
                case URL_REGEX_RULE -> saveUserRule(plan, siteKey, false);
                default -> Outcome.SKIPPED;
            };
        } catch (RuntimeException e) {
            return Outcome.FAILED;
        }
    }

    private static Outcome enableExisting(RulePayload payload) {
        if (payload.ruleKey().isEmpty()) return Outcome.SKIPPED;
        HlsRuleStateStore.setEnabled(payload.ruleKey(), true);
        return Outcome.APPLIED;
    }

    /**
     * 写入一条用户 HLS 规则。这是唯一能真正删除广告切片的机制 ——
     * 域名黑名单只在 WebView 层生效，不拦播放器直连的切片请求。
     */
    private static Outcome saveHlsRule(AdAttribution plan, String siteKey) {
        RulePayload payload = plan.payload();
        if (payload.hosts().isEmpty() && payload.regex().isEmpty()) return Outcome.SKIPPED;
        // 没有作用域的规则会跨站点生效，HlsAdRule.compile() 也会拒绝
        if (payload.playlistHostSuffixes().isEmpty()) return Outcome.SKIPPED;
        HlsAdRule rule = HlsAdRule.createUserRule(
                UUID.randomUUID().toString(), name(plan, siteKey),
                payload.playlistHostSuffixes(), payload.hosts(), payload.regex(),
                payload.hasDurationRange() ? payload.durationMin() : null,
                payload.hasDurationRange() ? payload.durationMax() : null,
                payload.requireDiscontinuity(), payload.requireCrossDomain(),
                // 门限由分类器按实际可编码信号数算好，不能在这里再抬高 ——
                // 超过实际信号数会让 compile() 拒绝整条规则
                payload.minimumSignals());
        return UserHlsRuleStore.add(rule) ? Outcome.APPLIED : Outcome.FAILED;
    }

    private static Outcome saveUserRule(AdAttribution plan, String siteKey, boolean hostMode) {
        RulePayload payload = plan.payload();
        List<String> values = hostMode ? payload.hosts() : payload.regex();
        if (values.isEmpty()) return Outcome.SKIPPED;
        UserAdRule rule = UserAdRule.createManual(name(plan, siteKey));
        rule.setSource(UserAdRule.SOURCE_AI);
        rule.setSiteKey(siteKey);
        if (hostMode) rule.setHosts(values);
        else rule.setRegex(values);
        UserAdRuleStore.add(rule);
        return Outcome.APPLIED;
    }

    private static String name(AdAttribution plan, String siteKey) {
        String site = siteKey == null || siteKey.isBlank() ? "unknown" : siteKey;
        return String.format(Locale.US, "区间反馈 %s %s",
                site, plan.category().name().toLowerCase(Locale.US).replace('_', '-'));
    }
}
