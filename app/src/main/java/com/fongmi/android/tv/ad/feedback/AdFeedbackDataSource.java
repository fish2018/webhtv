package com.fongmi.android.tv.ad.feedback;

import com.fongmi.android.tv.api.config.HlsRuleConfig;
import com.fongmi.android.tv.api.config.ImportedAdRuleCandidateStore;
import com.fongmi.android.tv.api.config.RuleConfig;
import com.fongmi.android.tv.api.config.UserAdRuleStore;
import com.fongmi.android.tv.bean.HlsAdRule;
import com.fongmi.android.tv.bean.ImportedAdRuleCandidate;
import com.fongmi.android.tv.bean.UserAdRule;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.HlsManifestCleaner;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 把项目里的静态单例数据读成分类器需要的入参。
 *
 * <p>三个分类器刻意做成纯函数以便单测，因此「读 SharedPreferences」这件事
 * 集中在这里。见设计文档第 5.1 节。
 */
public final class AdFeedbackDataSource {

    /**
     * 与 {@code HlsSegmentClassifier.pathOnlyPattern} 生成的前缀一致。
     * 只有以它开头的切片正则，其匹配范围才与自检的合成 manifest 语义相同。
     */
    static final String ANCHORED_PATH_PREFIX = "^[^?#]*";

    /** 本地 Gson，避免依赖 Application 生命周期，使 compiledOf 可被单测。 */
    private static final Gson GSON = new Gson();

    private AdFeedbackDataSource() {
    }

    /** 现有广告域名黑名单：VOD ads + Live ads + 用户规则 hosts。 */
    public static List<String> blacklistedHosts() {
        try {
            return List.copyOf(RuleConfig.get().getAds());
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** 待审接口候选里被判为广告域名的条目。 */
    public static List<String> interfaceCandidateHosts() {
        try {
            Set<String> hosts = new LinkedHashSet<>();
            for (ImportedAdRuleCandidate candidate : ImportedAdRuleCandidateStore.pending()) {
                if (candidate == null) continue;
                hosts.addAll(candidate.getHosts());
            }
            return List.copyOf(hosts);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** 第一条待审候选的来源接口名，用于证据展示。 */
    public static String interfaceSourceName() {
        try {
            for (ImportedAdRuleCandidate candidate : ImportedAdRuleCandidateStore.pending()) {
                if (candidate == null) continue;
                String name = candidate.getSourceConfigName();
                if (!name.isEmpty()) return name;
            }
        } catch (RuntimeException ignored) {
            // 读取失败不影响归因
        }
        return "";
    }

    /**
     * 用户规则中的正片保护正则。这些规则可能误保护了广告切片，
     * 是 {@link ExistingRuleClassifier} 的诊断输入之一。
     */
    public static List<String> protectingExcludes() {
        try {
            List<String> excludes = new ArrayList<>();
            for (UserAdRule rule : UserAdRuleStore.load()) {
                if (rule == null || !rule.isEnabled()) continue;
                excludes.addAll(rule.getExclude());
            }
            return List.copyOf(excludes);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /**
     * 已知 HLS 规则状态。{@code HlsAdRule} 未暴露 hostSuffixes 的 getter，
     * 因此从 {@link HlsRuleConfig.Entry#detail()} 的 JSON 里取。
     */
    public static List<ExistingRuleClassifier.RuleState> hlsRuleStates() {
        try {
            List<ExistingRuleClassifier.RuleState> states = new ArrayList<>();
            for (HlsRuleConfig.Entry entry : HlsRuleConfig.getEntries()) {
                if (entry == null) continue;
                states.add(new ExistingRuleClassifier.RuleState(
                        entry.key(), entry.id(), entry.name(),
                        entry.enabled(), entry.valid(),
                        hostSuffixesOf(entry.detail()),
                        compiledOf(entry.detail())));
            }
            return List.copyOf(states);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /**
     * 当前已启用的 HLS 规则，供叠加校验预测回退闸门。
     *
     * <p>返回 {@code null} 表示**无法确定**，调用方必须因此拒绝新规则 —— 不是
     * 「没有规则」。两者的区别是安全性的关键：叠加校验靠「合并后是否越过闸门」下
     * 结论，而 {@code removedCount} 对规则集单调，于是
     *
     * <ul>
     *   <li>用**子集**判出「会回退」→ 全集必然也回退，拒绝方向安全；</li>
     *   <li>用**子集**判出「不回退」→ 对全集一无所知，放行方向不安全。</li>
     * </ul>
     *
     * <p>所以凡是有一条已启用规则无法被自检忠实模拟，就整体返回 {@code null}，
     * 而不是把它剔除后拿剩下的规则去预测。带未锚定 {@code segmentUrlRegex} 的规则
     * 正属此类：合成 manifest 的 URI 不含 query 与 fragment（见
     * {@link #hasUnanchoredSegmentRegex}），这类规则在合成上命中 0 片，真实运行却
     * 可能命中大量切片。此前把它们静默剔除，等于让「合并后 3/30 不回退」这种结论
     * 建立在比生产小得多的规则集上，恰好放过「叠加后整份 manifest 回退」——
     * 正是叠加校验要拦的那件事。
     *
     * <p>去广告总开关关闭时返回空列表：此时 {@code HlsAdblockPipeline} 根本不会被
     * 调用，所有规则确实删 0 片，「没有规则生效」是真话而非近似。
     *
     * <p>刻意不按播放内核过滤：结构化净化目前只在 IJK 路径与本地 {@code /m3u8}
     * 代理上执行（MPV 会为时间戳完整性丢弃净化结果，EXO/系统内核走 legacy），
     * 但内核可在播放中回退切换，而规则一旦保存就长期生效。按当次内核放宽，等于
     * 让规则的安全性取决于保存那一刻的内核，换内核后即失效。
     */
    public static List<HlsManifestCleaner.Rule> activeHlsRules() {
        try {
            if (!Setting.isAdblock()) return List.of();
            List<HlsManifestCleaner.Rule> simulatable = new ArrayList<>();
            for (HlsRuleConfig.Entry entry : HlsRuleConfig.getEntries()) {
                if (entry == null || !entry.enabled() || !entry.valid()) continue;
                HlsManifestCleaner.Rule compiled = compiledOf(entry.detail());
                // compiledOf 已内含未锚定正则的拒绝。拿不到编译产物就无法预测它
                // 的删除量，只能整体弃权 —— 剔除它再预测是不安全的方向。
                if (compiled == null) return null;
                simulatable.add(compiled);
            }
            return List.copyOf(simulatable);
        } catch (RuntimeException e) {
            // 读配置失败同样是「无法确定」，不能退化成「没有规则」
            return null;
        }
    }

    /**
     * 从规则详情 JSON 还原已编译的规则，供 {@code RuleSelfCheck} 实跑验证。
     *
     * <p>{@code HlsRuleConfig.Entry} 只暴露 detail JSON，不给编译产物；这里重新
     * 反序列化并 compile。失败返回 null，调用方会因此不建议启用该规则。
     *
     * <p>用本地 Gson 而非 {@code App.gson()}：后者在纯 JVM 单测里
     * {@code App.get()} 为 null 会抛 NPE，被 catch 吞掉后静默让整个通道弃权，
     * 这条路径因此既没测也测不了。
     *
     * <p>带未锚定 {@code segmentUrlRegex} 的规则一律拒绝，见
     * {@link #hasUnanchoredSegmentRegex}。
     */
    static HlsManifestCleaner.Rule compiledOf(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) return null;
        if (hasUnanchoredSegmentRegex(detailJson)) return null;
        try {
            HlsAdRule rule = GSON.fromJson(detailJson, HlsAdRule.class);
            return rule == null ? null : rule.compile();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 规则的切片正则是否未锚定到 path。
     *
     * <p>{@code RuleSelfCheck} 的合成 manifest 用 {@code SegmentFact.path()} 拼 URL，
     * 而那是 {@code URI.getPath()} 的产物 —— 按去敏要求 query 与 fragment 都被丢弃。
     * 于是合成 URL 永不带 {@code ?}/{@code #}，而真实 {@code matchesPattern} 匹配的是
     * 含 query 与 fragment 的完整 URL。
     *
     * <p>本项目自己生成的正则用 {@code ^[^?#]*} 前缀对齐了这个差异（见
     * {@code HlsSegmentClassifier.pathOnlyPattern}），但接口下发或内置的第三方规则
     * 不会有这个锚定。实测一条 {@code segmentUrlRegex=["/ads/"]} 的既有规则在
     * 「正片 URL 带 ?ref=/ads/」时自检放行、真实运行多删正片且 {@code fallback=false}
     * —— 错误不被回退兜住，用户直接看到跳帧。
     *
     * <p>因此凡是带 {@code segmentUrlRegex} 且未显式排除 query/fragment 的规则，
     * 都不能用自检结论为它背书。
     */
    static boolean hasUnanchoredSegmentRegex(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) return false;
        try {
            JsonElement parsed = JsonParser.parseString(detailJson);
            if (!parsed.isJsonObject()) return false;
            JsonElement regex = parsed.getAsJsonObject().get("segmentUrlRegex");
            if (regex == null || !regex.isJsonArray()) return false;
            for (JsonElement element : regex.getAsJsonArray()) {
                if (element == null || element.isJsonNull()) continue;
                String value = element.getAsString();
                if (value == null || value.isBlank()) continue;
                if (!value.startsWith(ANCHORED_PATH_PREFIX)) return true;
            }
            return false;
        } catch (RuntimeException e) {
            // 解析不出来时保守判为未锚定
            return true;
        }
    }

    /** 从规则详情 JSON 里解析 hostSuffixes。解析失败返回空列表，不抛异常。 */
    static List<String> hostSuffixesOf(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) return List.of();
        try {
            JsonElement parsed = JsonParser.parseString(detailJson);
            if (!parsed.isJsonObject()) return List.of();
            JsonObject object = parsed.getAsJsonObject();
            JsonElement hosts = object.get("hostSuffixes");
            if (hosts == null || !hosts.isJsonArray()) return List.of();
            JsonArray array = hosts.getAsJsonArray();
            List<String> result = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                if (element == null || element.isJsonNull()) continue;
                String value = element.getAsString();
                if (value != null && !value.isBlank()) result.add(value.trim());
            }
            return List.copyOf(result);
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
