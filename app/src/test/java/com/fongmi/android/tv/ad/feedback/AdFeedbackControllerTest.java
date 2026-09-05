package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.bean.HlsAdRule;
import com.fongmi.android.tv.bean.M3u8Evidence;
import com.fongmi.android.tv.utils.HlsManifestCleaner;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AdFeedbackControllerTest {

    /** 10 个 10s 切片，下标 3-5 来自广告域名，断点在 3 与 6。 */
    /**
     * 30 片 playlist，下标 3-5 是广告。片数要够 —— 规则自检会用真实 cleaner
     * 跑一遍，10 片里删 3 片已达 30%，逼近 35% 的删除比例上限。
     */
    private static M3u8Evidence adBlockPlaylist() {
        List<String> segments = new ArrayList<>();
        List<Float> durations = new ArrayList<>();
        List<Boolean> switches = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            boolean ad = i >= 3 && i <= 5;
            segments.add(ad ? "https://ad-cdn.other.com/ads/" + i + ".ts"
                    : "https://v.example.com/seg/" + i + ".ts");
            durations.add(ad ? 6.4f : 10f);
            switches.add(ad);
        }
        return M3u8Evidence.create(segments, List.of(3, 6), durations, switches);
    }

    /**
     * 控制器必须把 {@code host.activeHlsRules()} 传给分类器做叠加校验。
     *
     * <p>没有这条测试，把 {@code classify} 里的 activeRules 换成 {@code List.of()}
     * 整个套件依然全绿 —— 串接一旦断开不会有任何红灯。
     */
    @Test
    public void passesActiveRulesIntoClassifiersForStackingCheck() {
        FakeHost host = new FakeHost();
        host.playlist = adBlockPlaylist();
        // 先确认这份证据在无已启用规则时能产出方案
        AdFeedbackController baseline = new AdFeedbackController(host);
        baseline.onMarkedInterval(30_000, 49_200);
        assertTrue("前置条件：无叠加时应有可落地方案",
                host.shownSessions.get(1).hasActionablePlan());

        // 一条已启用规则删掉另外 9 片正片（下标 6-14，各 10s）。与新规则的 3 片
        // 合并后同时越过两道闸门：12/30 = 40% > 35%，且 90 + 19.2 = 109.2s > 90s。
        // 刻意让两道都越界，避免测试卡在单一闸门的边界上。
        FakeHost stacked = new FakeHost();
        stacked.playlist = adBlockPlaylist();
        stacked.activeRules = List.of(HlsAdRule.createUserRule(
                "active", "active", List.of("v.example.com"), List.of(),
                List.of("^[^?#]*/seg/(?:6|7|8|9|10|11|12|13|14)\\.ts"),
                null, null, false, false, 1).compile());
        AdFeedbackController controller = new AdFeedbackController(stacked);

        controller.onMarkedInterval(30_000, 49_200);

        assertFalse("叠加后越过回退闸门，不该再给出可落地方案",
                stacked.shownSessions.get(1).hasActionablePlan());
    }

    /**
     * {@code activeHlsRules()} 返回 {@code null}（无法确定站内规则）时必须弃权，
     * 而不是当成「没有规则」继续放行。
     */
    @Test
    public void abstainsWhenHostCannotDetermineActiveRules() {
        FakeHost host = new FakeHost();
        host.playlist = adBlockPlaylist();
        host.activeRules = null;
        AdFeedbackController controller = new AdFeedbackController(host);

        controller.onMarkedInterval(30_000, 49_200);

        assertFalse("规则集未知时不该给出可落地方案",
                host.shownSessions.get(1).hasActionablePlan());
    }

    @Test
    public void showsPendingSessionBeforeAnalysisThenVerdict() {
        FakeHost host = new FakeHost();
        host.playlist = adBlockPlaylist();
        AdFeedbackController controller = new AdFeedbackController(host);

        AdFeedbackSession session = controller.onMarkedInterval(30_000, 49_200);

        assertNotNull(session);
        // 第一屏先出，第二屏带归因
        assertEquals(2, host.shownSessions.size());
        assertFalse(host.shownSessions.get(0).analysisComplete());
        assertTrue(host.shownSessions.get(1).analysisComplete());
        assertTrue(host.shownSessions.get(1).hasActionablePlan());
    }

    @Test
    public void skipsImmediatelyAndRecordsOutcome() {
        FakeHost host = new FakeHost();
        host.playlist = adBlockPlaylist();
        AdFeedbackController controller = new AdFeedbackController(host);

        AdFeedbackSession session = controller.onMarkedInterval(30_000, 49_200);

        assertTrue(session.skipApplied());
        assertEquals(List.of("30000-49200"), host.skipCalls);
        assertEquals(1, controller.diagnostics()
                .count(AdFeedbackDiagnostics.Code.IMMEDIATE_SKIP_APPLIED));
    }

    @Test
    public void recordsRejectedSkipWithoutFailingTheFeedback() {
        FakeHost host = new FakeHost();
        host.playlist = adBlockPlaylist();
        host.skipResult = false;
        AdFeedbackController controller = new AdFeedbackController(host);

        AdFeedbackSession session = controller.onMarkedInterval(30_000, 49_200);

        // 未跳过不影响归因继续
        assertFalse(session.skipApplied());
        assertTrue(host.shownSessions.get(1).hasActionablePlan());
        assertEquals(1, controller.diagnostics()
                .count(AdFeedbackDiagnostics.Code.IMMEDIATE_SKIP_REJECTED));
    }

    @Test
    public void quickReportInfersStartFromNearestDiscontinuity() {
        FakeHost host = new FakeHost();
        host.playlist = adBlockPlaylist();
        host.position = 60_000;
        AdFeedbackController controller = new AdFeedbackController(host);

        AdFeedbackSession session = controller.onQuickReport(host.playlist);

        assertNotNull(session);
        // 断点在下标 3 与 6。广告切片是 6.4s，故下标 6 起点为
        // 3×10 + 3×6.4 = 49.2s，是 60s 之前最近的那个断点。
        assertEquals(49_200L, session.startMs());
        assertEquals(StartOrigin.DISCONTINUITY, session.startOrigin());
        assertEquals(1, controller.diagnostics()
                .count(AdFeedbackDiagnostics.Code.START_INFERRED_FROM_DISCONTINUITY));
    }

    @Test
    public void quickReportPicksEarlierDiscontinuityWhenCloserOneIsAhead() {
        FakeHost host = new FakeHost();
        host.playlist = adBlockPlaylist();
        // 位置落在广告块中间：只有下标 3 的断点在它之前
        host.position = 40_000;
        AdFeedbackController controller = new AdFeedbackController(host);

        AdFeedbackSession session = controller.onQuickReport(host.playlist);

        assertEquals(30_000L, session.startMs());
        assertEquals(StartOrigin.DISCONTINUITY, session.startOrigin());
    }

    @Test
    public void quickReportFallsBackToWindowWithoutPlaylist() {
        FakeHost host = new FakeHost();
        host.position = 200_000;
        AdFeedbackController controller = new AdFeedbackController(host);

        AdFeedbackSession session = controller.onQuickReport(null);

        assertEquals(200_000L - AdIntervalMapper.DEFAULT_FALLBACK_WINDOW_MS, session.startMs());
        assertEquals(StartOrigin.FALLBACK_WINDOW, session.startOrigin());
        assertEquals(1, controller.diagnostics()
                .count(AdFeedbackDiagnostics.Code.START_INFERRED_FROM_FALLBACK));
    }

    @Test
    public void prerollReportEarlyInPlaybackClampsStartToZero() {
        // 片头广告是最常见场景：位置远小于回溯窗口，起点必须钳到 0 而非变负
        FakeHost host = new FakeHost();
        host.position = 12_000;
        AdFeedbackController controller = new AdFeedbackController(host);

        AdFeedbackSession session = controller.onQuickReport(null);

        assertNotNull("片头位置也必须能反馈", session);
        assertEquals(0L, session.startMs());
        assertEquals(12_000L, session.endMs());
        assertEquals(List.of("0-12000"), host.skipCalls);
    }

    @Test
    public void unavailablePositionIsRejectedNotTreatedAsZero() {
        // 播放器已释放时 safePositionMs 返回 -1，不能被当成合法的片头位置
        FakeHost host = new FakeHost();
        host.position = -1;
        AdFeedbackController controller = new AdFeedbackController(host);

        assertNull(controller.onQuickReport(null));
        assertTrue(host.skipCalls.isEmpty());
        assertTrue(host.shownSessions.isEmpty());
    }

    @Test
    public void invalidateDropsInFlightAnalysisFromPreviousEpisode() {
        // 换集后旧归因回来时不得覆盖 UI —— 否则用户会看到上一集的结论
        FakeHost host = new FakeHost();
        host.playlist = adBlockPlaylist();
        host.deferBackground = true;
        AdFeedbackController controller = new AdFeedbackController(host);

        controller.onMarkedInterval(30_000, 49_200);
        int shownBefore = host.shownSessions.size();
        controller.invalidate();
        host.deferBackground = false;
        host.backgroundTasks.get(0).run();

        assertEquals("过期归因不得再刷新界面", shownBefore, host.shownSessions.size());
    }

    @Test
    public void rejectsInvalidIntervals() {
        FakeHost host = new FakeHost();
        AdFeedbackController controller = new AdFeedbackController(host);

        assertNull(controller.onMarkedInterval(40_000, 40_000));
        assertNull(controller.onMarkedInterval(50_000, 40_000));
        assertNull(controller.onMarkedInterval(-1_000, 40_000));
        // 起点超出总时长
        assertNull(controller.onMarkedInterval(700_000, 800_000));
        assertTrue(host.skipCalls.isEmpty());
        assertTrue(host.shownSessions.isEmpty());
    }

    @Test
    public void quickReportAtZeroPositionIsRejected() {
        FakeHost host = new FakeHost();
        host.position = 0;
        AdFeedbackController controller = new AdFeedbackController(host);

        assertNull(controller.onQuickReport(null));
    }

    @Test
    public void surfacesAlreadyHandledDiagnosisWhenHostIsBlacklisted() {
        FakeHost host = new FakeHost();
        host.playlist = adBlockPlaylist();
        host.blacklist = List.of("ad-cdn.other.com");
        AdFeedbackController controller = new AdFeedbackController(host);

        controller.onMarkedInterval(30_000, 49_200);

        AdFeedbackSession result = host.shownSessions.get(1);
        assertTrue(result.verdict().diagnostics().stream()
                .anyMatch(a -> a.category() == AdCategory.ALREADY_HANDLED));
        assertEquals(1, controller.diagnostics()
                .count(AdFeedbackDiagnostics.Code.ALREADY_HANDLED_DETECTED));
    }

    @Test
    public void surfacesEnablingDisabledRuleAsAnOption() {
        FakeHost host = new FakeHost();
        host.playlist = adBlockPlaylist();
        host.ruleStates = List.of(new ExistingRuleClassifier.RuleState(
                "builtin:x", "x-rule", "实验规则", false, true, List.of("ad-cdn.other.com"),
                ExistingRuleClassifierTest.compiledRule("ad-cdn.other.com")));
        AdFeedbackController controller = new AdFeedbackController(host);

        controller.onMarkedInterval(30_000, 49_200);

        // 启用已有规则的成本最低，但仲裁是置信度 0.7 + 成本 0.3 的加权：
        // HLS 与域名通道同类合并后置信度 0.91，压过既有规则的 0.75。
        // 成本优先只在置信度接近时生效，那条约束由
        // AdAttributionArbiterTest.enablingExistingRuleOutranksNewRuleEvenAtLowerConfidence 锁定。
        // 这里只要求它作为可选方案出现，不被丢弃。
        AdAttributionArbiter.Verdict verdict = host.shownSessions.get(1).verdict();
        List<AdAttribution> all = new ArrayList<>();
        all.add(verdict.preferred());
        all.addAll(verdict.alternatives());
        assertTrue("启用已有规则必须作为可选方案保留",
                all.stream().anyMatch(a -> a.remediation() == RemediationKind.ENABLE_EXISTING_RULE));
    }

    @Test
    public void fallsBackToSessionSkipWhenNoChannelHasEvidence() {
        FakeHost host = new FakeHost();
        // 无 playlist、无基线、无规则：全部通道弃权
        host.legacyActive = true;
        AdFeedbackController controller = new AdFeedbackController(host);

        controller.onMarkedInterval(30_000, 49_200);

        AdFeedbackSession result = host.shownSessions.get(1);
        assertTrue(result.analysisComplete());
        assertFalse(result.hasActionablePlan());
        assertEquals(3, controller.diagnostics()
                .count(AdFeedbackDiagnostics.Code.CHANNEL_ABSTAINED));
    }

    @Test
    public void evidenceFailureStillCompletesWithEmptyVerdict() {
        FakeHost host = new FakeHost();
        host.throwOnFetch = true;
        AdFeedbackController controller = new AdFeedbackController(host);

        controller.onMarkedInterval(30_000, 49_200);

        AdFeedbackSession result = host.shownSessions.get(1);
        assertTrue(result.analysisComplete());
        assertTrue(result.verdict().empty());
        assertEquals(1, controller.diagnostics()
                .count(AdFeedbackDiagnostics.Code.EVIDENCE_COLLECT_FAILED));
    }

    @Test
    public void staleAnalysisDoesNotOverwriteNewerFeedback() {
        FakeHost host = new FakeHost();
        host.playlist = adBlockPlaylist();
        host.deferBackground = true;
        AdFeedbackController controller = new AdFeedbackController(host);

        controller.onMarkedInterval(30_000, 49_200);
        controller.onMarkedInterval(70_000, 90_000);
        // 按倒序执行：先跑第二次的分析，再跑第一次的过期分析
        List<Runnable> pending = new ArrayList<>(host.backgroundTasks);
        host.deferBackground = false;
        pending.get(1).run();
        pending.get(0).run();

        // 最后一次展示必须属于第二个区间
        AdFeedbackSession last = host.shownSessions.get(host.shownSessions.size() - 1);
        assertEquals(70_000L, last.startMs());
    }

    private static final class FakeHost implements AdFeedbackController.Host {
        private final List<String> skipCalls = new ArrayList<>();
        private final List<AdFeedbackSession> shownSessions = new ArrayList<>();
        private final List<Runnable> backgroundTasks = new ArrayList<>();
        private M3u8Evidence playlist;
        private long position = 45_000;
        private boolean skipResult = true;
        private boolean legacyActive;
        private boolean throwOnFetch;
        private boolean deferBackground;
        private List<String> blacklist = List.of();
        private List<ExistingRuleClassifier.RuleState> ruleStates = List.of();
        private List<HlsManifestCleaner.Rule> activeRules = List.of();

        @Override
        public long positionMs() {
            return position;
        }

        @Override
        public long durationMs() {
            return 600_000L;
        }

        @Override
        public AdEvidenceCollector.Context context() {
            return new AdEvidenceCollector.Context("site", "站点", "剧名", "线路", "第 1 集",
                    "https://v.example.com/play/index.m3u8", true);
        }

        @Override
        public M3u8Evidence fetchEvidence() {
            if (throwOnFetch) throw new IllegalStateException("fetch failed");
            return playlist;
        }

        @Override
        public boolean skipInterval(long startMs, long endMs, String feedbackId) {
            skipCalls.add(startMs + "-" + endMs);
            return skipResult;
        }

        @Override
        public List<String> blacklistedHosts() {
            return blacklist;
        }

        @Override
        public List<String> siteBaselineHosts() {
            return List.of("v.example.com");
        }

        @Override
        public List<String> interfaceCandidateHosts() {
            return List.of();
        }

        @Override
        public String interfaceSourceName() {
            return "";
        }

        @Override
        public List<ExistingRuleClassifier.RuleState> hlsRuleStates() {
            return ruleStates;
        }

        @Override
        public List<HlsManifestCleaner.Rule> activeHlsRules() {
            return activeRules;
        }

        @Override
        public List<String> protectingExcludes() {
            return List.of();
        }

        @Override
        public boolean legacyHeuristicActive() {
            return legacyActive;
        }

        @Override
        public void runBackground(Runnable task) {
            backgroundTasks.add(task);
            if (!deferBackground) task.run();
        }

        @Override
        public void runOnUi(Runnable task) {
            task.run();
        }

        @Override
        public void showSession(AdFeedbackSession session) {
            shownSessions.add(session);
        }
    }
}
