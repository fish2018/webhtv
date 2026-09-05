package com.fongmi.android.tv.ad.feedback;

import com.fongmi.android.tv.bean.M3u8Evidence;
import com.fongmi.android.tv.utils.HlsManifestCleaner;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 区间反馈的编排器：接收区间 → 立即跳过 → 采集证据 → 多通道归因 → 仲裁 → 回调 UI。
 *
 * <p>三端（leanback / mobile / TmdbDetail）共用本类，Activity 只实现 {@link Host}。
 * 见设计文档第 5.2 节。所有 Android 依赖都在 {@link Host} 后面，因此本类可单测。
 */
public final class AdFeedbackController {

    /** Activity 侧需要提供的能力。 */
    public interface Host {
        /** 当前播放位置，毫秒。 */
        long positionMs();

        /** 总时长，毫秒；未知时返回非正数。 */
        long durationMs();

        /** 采集上下文。 */
        AdEvidenceCollector.Context context();

        /** 抓取 playlist 证据，阻塞 I/O，由 {@link #runBackground} 调度。可返回 null。 */
        M3u8Evidence fetchEvidence();

        /** 执行本次跳过，返回是否真的 seek 了。 */
        boolean skipInterval(long startMs, long endMs, String feedbackId);

        /** 现有广告域名黑名单。 */
        List<String> blacklistedHosts();

        /** 本站历史正常域名。 */
        List<String> siteBaselineHosts();

        /** 接口学习的待审候选域名。 */
        List<String> interfaceCandidateHosts();

        /** 命中接口候选时展示的来源名。 */
        String interfaceSourceName();

        /** 已知 HLS 规则状态。 */
        List<ExistingRuleClassifier.RuleState> hlsRuleStates();

        /**
         * 当前已启用的 HLS 规则，新规则要与它们叠加后仍然安全 ——
         * {@code HlsManifestCleaner} 的三道回退闸门作用于合并后的删除总量。
         *
         * <p>{@code null} 表示无法确定（存在模拟不了的已启用规则，或读取配置失败），
         * 与空列表语义不同：前者让自检拒绝新规则，后者表示站内确无规则生效。
         * 刻意不给默认实现 —— 默认返回空列表等于代替实现者宣称「没有规则生效」，
         * 那是它无从知道的事，而这个方向恰好是不安全的那一侧。
         */
        List<HlsManifestCleaner.Rule> activeHlsRules();

        /** 正片保护正则。 */
        List<String> protectingExcludes();

        /** 旧启发式引擎当次是否生效。 */
        boolean legacyHeuristicActive();

        /** 音频/语音通道已有候选的起点，无则返回负数。 */
        default long audioCandidateStartMs() {
            return -1L;
        }

        /** 短按回溯窗口。 */
        default long fallbackWindowMs() {
            return AdIntervalMapper.DEFAULT_FALLBACK_WINDOW_MS;
        }

        /** 后台执行阻塞任务。 */
        void runBackground(Runnable task);

        /** 回到主线程更新 UI。 */
        void runOnUi(Runnable task);

        /** 展示或刷新反馈对话框。 */
        void showSession(AdFeedbackSession session);
    }

    private final Host host;
    private final AdFeedbackDiagnostics diagnostics;
    /**
     * 防止过期回调覆盖新一次反馈的结果。写在主线程（{@link #submit}）、
     * 读在后台线程投递回主线程的 lambda 里，故必须 volatile。
     */
    private volatile int generation;

    public AdFeedbackController(Host host) {
        this(host, new AdFeedbackDiagnostics());
    }

    public AdFeedbackController(Host host, AdFeedbackDiagnostics diagnostics) {
        if (host == null) throw new IllegalArgumentException("host is required");
        this.host = host;
        this.diagnostics = diagnostics == null ? new AdFeedbackDiagnostics() : diagnostics;
    }

    public AdFeedbackDiagnostics diagnostics() {
        return diagnostics;
    }

    /**
     * 作废所有在途归因。换源、换集或播放器重建时必须调用 —— 否则后台
     * {@code fetchEvidence()} 返回后仍会用上一集的证据弹窗，用户按「保存规则」
     * 写入的是上一集的域名与切片条件。
     */
    public void invalidate() {
        generation++;
    }

    /**
     * 短按：以当前位置为终点，回溯推断起点。
     *
     * @return 本次反馈的会话，播放状态不允许时返回 null
     */
    public AdFeedbackSession onQuickReport(M3u8Evidence cachedEvidence) {
        long endMs = host.positionMs();
        if (endMs <= 0) return null;
        AdIntervalMapper.InferredStart start = AdIntervalMapper.inferStart(
                cachedEvidence, endMs, host.audioCandidateStartMs(), host.fallbackWindowMs());
        return submit(start.startMs(), endMs, start.origin());
    }

    /**
     * 长按标记模式：用户显式框选的区间。
     *
     * @param startMs 标记模式下打的起点
     * @param endMs   终点，由调用方取当前播放位置；取不到时传负数即被拒绝
     */
    public AdFeedbackSession onMarkedInterval(long startMs, long endMs) {
        return submit(startMs, endMs, StartOrigin.USER_MARKED);
    }

    /**
     * 提交一个区间：立即尝试跳过，然后在后台归因。
     */
    public AdFeedbackSession submit(long startMs, long endMs, StartOrigin origin) {
        if (endMs <= startMs || startMs < 0) return null;
        long duration = host.durationMs();
        if (duration > 0 && startMs >= duration) return null;

        String feedbackId = "fb-" + UUID.randomUUID();
        diagnostics.record(AdFeedbackDiagnostics.Code.INTERVAL_SUBMITTED);
        diagnostics.recordStartOrigin(origin);

        boolean skipped = host.skipInterval(startMs, endMs, feedbackId);
        diagnostics.record(skipped
                ? AdFeedbackDiagnostics.Code.IMMEDIATE_SKIP_APPLIED
                : AdFeedbackDiagnostics.Code.IMMEDIATE_SKIP_REJECTED);

        AdFeedbackSession session = AdFeedbackSession.pending(
                feedbackId, startMs, endMs, origin, skipped);
        host.showSession(session);

        int current = ++generation;
        host.runBackground(() -> analyse(session, current));
        return session;
    }

    private void analyse(AdFeedbackSession session, int expectedGeneration) {
        AdAttributionArbiter.Verdict verdict;
        try {
            verdict = classify(session);
        } catch (RuntimeException e) {
            diagnostics.record(AdFeedbackDiagnostics.Code.EVIDENCE_COLLECT_FAILED);
            verdict = new AdAttributionArbiter.Verdict(null, List.of(), List.of());
        }
        AdAttributionArbiter.Verdict result = verdict;
        host.runOnUi(() -> {
            if (expectedGeneration != generation) return;
            host.showSession(session.withVerdict(result));
        });
    }

    private AdAttributionArbiter.Verdict classify(AdFeedbackSession session) {
        M3u8Evidence playlist = host.fetchEvidence();
        List<String> blacklist = host.blacklistedHosts();
        AdIntervalEvidence evidence = AdEvidenceCollector.collect(
                host.context(), playlist, session.startMs(), session.endMs(),
                session.startOrigin(), blacklist, host.legacyHeuristicActive());

        // 已启用规则参与自检：新规则叠加上去后不得越过 cleaner 的回退闸门
        List<HlsManifestCleaner.Rule> activeRules = host.activeHlsRules();

        List<AdAttribution> attributions = new ArrayList<>();
        attributions.add(HlsSegmentClassifier.classify(evidence, activeRules));
        attributions.add(DomainReputationClassifier.classify(evidence,
                new DomainReputationClassifier.Input(blacklist, host.siteBaselineHosts(),
                        host.interfaceCandidateHosts(), host.interfaceSourceName()),
                activeRules));
        attributions.add(ExistingRuleClassifier.classify(evidence,
                new ExistingRuleClassifier.Input(host.hlsRuleStates(),
                        host.legacyHeuristicActive(), host.protectingExcludes()),
                activeRules));

        for (AdAttribution attribution : attributions) {
            if (attribution == null) diagnostics.record(AdFeedbackDiagnostics.Code.CHANNEL_ABSTAINED);
            else if (attribution.category() == AdCategory.ALREADY_HANDLED) {
                diagnostics.record(AdFeedbackDiagnostics.Code.ALREADY_HANDLED_DETECTED);
            }
        }

        AdAttributionArbiter.Verdict verdict = AdAttributionArbiter.arbitrate(attributions);
        if (verdict.empty()) diagnostics.record(AdFeedbackDiagnostics.Code.ARBITER_NO_PLAN);
        return verdict;
    }
}
