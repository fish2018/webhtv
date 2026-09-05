package com.fongmi.android.tv.ad.feedback;

import com.fongmi.android.tv.bean.M3u8Evidence;
import com.fongmi.android.tv.utils.HlsManifestCleaner;
import com.fongmi.android.tv.utils.M3u8Parser;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link AdFeedbackController.Host} 的通用实现，三端共用。
 *
 * <p>Activity 只需提供播放状态、剧集元数据和三个调度/展示回调；读取本地配置、
 * 抓取 playlist、维护站点域名基线都在这里完成。见设计文档第 5.2 节。
 */
public final class AdFeedbackHostAdapter implements AdFeedbackController.Host {

    /** 播放器状态。{@code hls} 由调用方判定，避免本类依赖 media3。 */
    public interface Playback {
        long positionMs();

        long durationMs();

        String playUrl();

        Map<String, String> headers();

        boolean hls();

        /** 执行本次跳过，通常委托给 {@code PlayerManager.skipUserAdInterval}。 */
        boolean skipInterval(long startMs, long endMs, String feedbackId);

        /** 音频/语音通道已有候选的起点，无则返回负数。 */
        default long audioCandidateStartMs() {
            return -1L;
        }
    }

    /** 当前剧集元数据。 */
    public interface Metadata {
        String siteKey();

        String siteName();

        String vodName();

        String flagName();

        String episodeName();
    }

    /** 线程调度与界面展示。 */
    public interface Ui {
        void runBackground(Runnable task);

        void runOnUi(Runnable task);

        void showSession(AdFeedbackSession session);
    }

    /**
     * 本地配置读取入口，默认指向 {@link AdFeedbackDataSource}，测试可替换。
     */
    public record Deps(Supplier<List<String>> blacklistedHosts,
                       Supplier<List<String>> interfaceCandidateHosts,
                       Supplier<String> interfaceSourceName,
                       Supplier<List<String>> protectingExcludes,
                       Supplier<List<ExistingRuleClassifier.RuleState>> hlsRuleStates,
                       Supplier<List<HlsManifestCleaner.Rule>> activeHlsRules,
                       SitePlaylistHostBaseline baseline) {

        public static Deps defaults() {
            return new Deps(
                    AdFeedbackDataSource::blacklistedHosts,
                    AdFeedbackDataSource::interfaceCandidateHosts,
                    AdFeedbackDataSource::interfaceSourceName,
                    AdFeedbackDataSource::protectingExcludes,
                    AdFeedbackDataSource::hlsRuleStates,
                    AdFeedbackDataSource::activeHlsRules,
                    SiteHostBaselineStore.get());
        }
    }

    private final Playback playback;
    private final Metadata metadata;
    private final Ui ui;
    private final Deps deps;
    /** 最近一次抓到的 playlist 证据，短按推断起点时复用，避免重复请求。 */
    private volatile M3u8Evidence cachedEvidence;

    public AdFeedbackHostAdapter(Playback playback, Metadata metadata, Ui ui) {
        this(playback, metadata, ui, Deps.defaults());
    }

    public AdFeedbackHostAdapter(Playback playback, Metadata metadata, Ui ui, Deps deps) {
        this.playback = Objects.requireNonNull(playback, "playback");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.deps = deps == null ? Deps.defaults() : deps;
    }

    /** 供短按路径复用的缓存证据，可能为 null。 */
    public M3u8Evidence cachedEvidence() {
        return cachedEvidence;
    }

    /** 换源或换集时清掉缓存，避免把上一集的切片结构用于本集。 */
    public void invalidateEvidence() {
        cachedEvidence = null;
    }

    /**
     * 当前播放位置，取不到时返回 -1 而非 0 —— 0 会被误当成合法的
     * 「片头位置」，而 -1 会让 {@code AdFeedbackController.submit} 直接拒绝。
     */
    public long safePositionMs() {
        try {
            long position = playback.positionMs();
            return position > 0 ? position : -1L;
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    /**
     * 记录一次成功播放的域名，喂给站点基线。
     *
     * <p>必须在播放起播后调用：{@link DomainReputationClassifier} 无基线数据时
     * 一律弃权（避免首次播放即误判），不接线会让整条域名通道成为死代码。
     */
    public void recordPlaybackHost() {
        try {
            String host = AdEvidenceCollector.hostOf(playback.playUrl());
            if (host.isEmpty()) return;
            deps.baseline().record(metadata.siteKey(), host);
        } catch (RuntimeException ignored) {
            // 基线只是归因的辅助输入，记录失败不应影响播放
        }
    }

    @Override
    public long positionMs() {
        return safePositionMs();
    }

    @Override
    public long durationMs() {
        // 与 safePositionMs 对称：PlayerManager.getDuration() 也是裸
        // player.getDuration()，播放器 release 后 player 为 null 会 NPE
        try {
            return playback.durationMs();
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    @Override
    public AdEvidenceCollector.Context context() {
        return new AdEvidenceCollector.Context(
                metadata.siteKey(), metadata.siteName(), metadata.vodName(),
                metadata.flagName(), metadata.episodeName(), playback.playUrl(), playback.hls());
    }

    @Override
    public M3u8Evidence fetchEvidence() {
        if (!playback.hls()) return null;
        String url = playback.playUrl();
        if (url == null || url.isBlank()) return null;
        try {
            M3u8Evidence evidence = M3u8Parser.parse(url, playback.headers());
            if (evidence != null && !evidence.isEmpty()) cachedEvidence = evidence;
            return evidence;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public boolean skipInterval(long startMs, long endMs, String feedbackId) {
        return playback.skipInterval(startMs, endMs, feedbackId);
    }

    @Override
    public List<String> blacklistedHosts() {
        return orEmpty(deps.blacklistedHosts());
    }

    @Override
    public List<String> siteBaselineHosts() {
        try {
            return deps.baseline().hosts(metadata.siteKey());
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    @Override
    public List<String> interfaceCandidateHosts() {
        return orEmpty(deps.interfaceCandidateHosts());
    }

    @Override
    public String interfaceSourceName() {
        try {
            String name = deps.interfaceSourceName().get();
            return name == null ? "" : name;
        } catch (RuntimeException e) {
            return "";
        }
    }

    @Override
    public List<ExistingRuleClassifier.RuleState> hlsRuleStates() {
        try {
            List<ExistingRuleClassifier.RuleState> states = deps.hlsRuleStates().get();
            return states == null ? List.of() : states;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /**
     * 与其他 {@code orEmpty} 式读取不同，这里**不把失败折成空列表**：空列表意味着
     * 「站内确无规则生效」，会让自检跳过叠加校验；读不到时的真实状态是「不知道」，
     * 必须原样传 {@code null} 让自检拒绝。折成空列表是 fail-open。
     */
    @Override
    public List<HlsManifestCleaner.Rule> activeHlsRules() {
        try {
            return deps.activeHlsRules().get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public List<String> protectingExcludes() {
        return orEmpty(deps.protectingExcludes());
    }

    @Override
    public boolean legacyHeuristicActive() {
        // HlsAdblockPipeline 在两个调用点都传 legacyFallback=true，
        // 且内置规则默认全部关闭，所以点播 HLS 上旧启发式实际总是生效。
        return playback.hls();
    }

    @Override
    public long audioCandidateStartMs() {
        return playback.audioCandidateStartMs();
    }

    @Override
    public void runBackground(Runnable task) {
        ui.runBackground(task);
    }

    @Override
    public void runOnUi(Runnable task) {
        ui.runOnUi(task);
    }

    @Override
    public void showSession(AdFeedbackSession session) {
        ui.showSession(session);
    }

    private static List<String> orEmpty(Supplier<List<String>> supplier) {
        try {
            List<String> value = supplier.get();
            return value == null ? List.of() : value;
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
