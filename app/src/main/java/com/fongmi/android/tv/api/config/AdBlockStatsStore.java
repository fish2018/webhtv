package com.fongmi.android.tv.api.config;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.AdBlockStats;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.RuleHitRecord;
import com.fongmi.android.tv.bean.UserAdRule;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.playback.PlaybackRuntime;
import com.fongmi.android.tv.utils.HlsManifestCleaner;
import com.fongmi.android.tv.utils.RuleIdUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Prefers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 广告拦截统计存储
 * 存储于 SharedPreferences（key: ad_block_stats），JSON 对象
 */
public class AdBlockStatsStore {

    private static final String PREF_KEY = "ad_block_stats";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static AdBlockStats cache;

    /**
     * 加载统计数据
     */
    public static synchronized AdBlockStats load() {
        if (cache != null) return cache;
        try {
            String json = Prefers.getString(PREF_KEY, "");
            if (TextUtils.isEmpty(json)) {
                cache = new AdBlockStats();
                return cache;
            }
            cache = App.gson().fromJson(json, AdBlockStats.class);
            if (cache == null) cache = new AdBlockStats();
            return cache;
        } catch (Throwable e) {
            SpiderDebug.log("ad-block-stats", "load failed: %s", e.getMessage());
            cache = new AdBlockStats();
            return cache;
        }
    }

    /**
     * 保存统计数据（同步更新缓存，异步写入）
     */
    public static void save(AdBlockStats stats) {
        if (stats == null) return;
        cache = stats;
        try {
            Prefers.put(PREF_KEY, App.gson().toJson(stats));
        } catch (Exception e) {
            SpiderDebug.log("ad-block-stats", "save failed: %s", e.getMessage());
        }
    }

    /**
     * 记录一次拦截
     */
    public static void recordBlock(String siteKey, String ruleId) {
        recordBlock(siteKey, "OTHER", ruleId);
    }

    public static void recordBlock(String siteKey, String pipeline, String ruleId) {
        recordBlock(siteKey, pipeline, ruleId, "", 0);
    }

    public static void recordBlock(String siteKeyOrDomain, String pipeline, String ruleId,
                                   String adDomain, double segmentDurationSeconds) {
        SiteIdentity identity = resolveSiteIdentity(siteKeyOrDomain, adDomain);
        executor.execute(() -> {
            AdBlockStats stats = load();
            stats.incrementBlocks(identity.siteKey(), pipeline, ruleId, 1);
            stats.recordBlockLog(System.currentTimeMillis(), identity.siteKey(), identity.siteName(),
                    identity.siteDomain(), pipeline, adDomain, ruleId, 0, segmentDurationSeconds);
            save(stats);
        });
    }

    /** Records a complete HLS-cleaning result atomically on the existing stats executor. */
    public static void recordBlocks(String siteKey, Map<String, Long> ruleCounts, long fallbackCount) {
        recordBlocks(siteKey, "HLS", ruleCounts, fallbackCount);
    }

    public static void recordBlocks(String siteKey, String pipeline, Map<String, Long> ruleCounts, long fallbackCount) {
        recordBlocks(siteKey, pipeline, ruleCounts, fallbackCount, siteKey, 0);
    }

    public static void recordBlocks(String sourceName, String pipeline, Map<String, Long> ruleCounts,
                                    long fallbackCount, String adDomain, double totalDurationSeconds) {
        recordBlocks(sourceName, pipeline, ruleCounts, fallbackCount, adDomain,
                totalDurationSeconds, List.of());
    }

    public static void recordBlocks(String siteKeyOrDomain, String pipeline, Map<String, Long> ruleCounts,
                                    long fallbackCount, String siteDomain, double totalDurationSeconds,
                                    List<HlsManifestCleaner.RemovedSegment> removedSegments) {
        SiteIdentity identity = resolveSiteIdentity(siteKeyOrDomain, siteDomain);
        executor.execute(() -> {
            AdBlockStats stats = load();
            Map<String, Long> detailedCounts = new HashMap<>();
            if (ruleCounts != null) {
                for (Map.Entry<String, Long> entry : ruleCounts.entrySet()) {
                    long count = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
                    stats.incrementBlocks(identity.siteKey(), pipeline, entry.getKey(), count);
                    if (count > 0) detailedCounts.put(entry.getKey(), count);
                }
            }
            long safeFallbackCount = Math.max(0, fallbackCount);
            stats.incrementBlocks(identity.siteKey(), pipeline, "hls.legacy-fallback", safeFallbackCount);
            if (safeFallbackCount > 0) detailedCounts.put("hls.legacy-fallback", safeFallbackCount);

            long blockedAt = System.currentTimeMillis();
            if (removedSegments != null && !removedSegments.isEmpty()) {
                for (HlsManifestCleaner.RemovedSegment segment : removedSegments) {
                    stats.recordBlockLog(blockedAt, identity.siteKey(), identity.siteName(), identity.siteDomain(),
                            pipeline, segment.adDomain(), segment.ruleId(), segment.startSeconds(), segment.durationSec());
                }
            } else {
                stats.recordBlockLogs(blockedAt, identity.siteKey(), identity.siteName(), identity.siteDomain(),
                        pipeline, siteDomain, detailedCounts, totalDurationSeconds);
            }
            save(stats);
        });
    }

    private static SiteIdentity resolveSiteIdentity(String siteKeyOrDomain, String siteDomain) {
        if (!TextUtils.isEmpty(siteKeyOrDomain)) {
            try {
                Site site = VodConfig.get().getSite(siteKeyOrDomain);
                if (site != null && !site.isEmpty()) {
                    return new SiteIdentity(site.getKey(), site.getDisplayName(), siteDomain);
                }
            } catch (Throwable ignored) {
            }
        }
        PlaybackRuntime.SiteIdentity current = PlaybackRuntime.currentSiteIdentity(siteDomain);
        if (!TextUtils.isEmpty(current.siteKey())) {
            return new SiteIdentity(current.siteKey(), current.siteName(), current.siteDomain());
        }
        String fallbackKey = TextUtils.isEmpty(siteKeyOrDomain) ? siteDomain : siteKeyOrDomain;
        String fallbackName = TextUtils.isEmpty(fallbackKey) ? current.siteName() : fallbackKey;
        return new SiteIdentity(fallbackKey, fallbackName, current.siteDomain());
    }

    public static String getRuleDisplayName(String ruleId) {
        RuleHitRecord record = new RuleHitRecord();
        fillRuleInfo(record, ruleId);
        return TextUtils.isEmpty(record.getRuleName()) ? ruleId : record.getRuleName();
    }

    private record SiteIdentity(String siteKey, String siteName, String siteDomain) {}

    /**
     * 记录一次 AI 反馈
     */
    public static void recordFeedback(String siteKey) {
        executor.execute(() -> {
            AdBlockStats stats = load();
            stats.incrementAiFeedback();
            save(stats);
        });
    }

    /**
     * 记录一次 AI 分析结果
     */
    public static void recordAiAnalysis(boolean success) {
        executor.execute(() -> {
            AdBlockStats stats = load();
            if (success) {
                stats.incrementAiSuccess();
            } else {
                stats.incrementAiFailed();
            }
            save(stats);
        });
    }

    /**
     * 获取统计数据
     */
    public static AdBlockStats getStats() {
        return load();
    }

    /**
     * 获取指定站点的拦截次数
     */
    public static long getSiteBlockedCount(String siteKey) {
        return load().getSiteBlockedCount(siteKey);
    }

    /**
     * 获取 Top N 命中规则
     */
    public static List<RuleHitRecord> getTopRules(int limit) {
        AdBlockStats stats = load();
        Map<String, Long> ruleCounts = stats.getRuleCounts();
        if (ruleCounts.isEmpty()) return new ArrayList<>();

        // 收集所有规则信息
        Map<String, RuleHitRecord> records = new HashMap<>();
        for (Map.Entry<String, Long> entry : ruleCounts.entrySet()) {
            String ruleId = entry.getKey();
            long count = toLong(entry.getValue());

            RuleHitRecord record = new RuleHitRecord();
            record.setRuleId(ruleId);
            record.setHitCount(count);

            // 查找规则名称和来源
            fillRuleInfo(record, ruleId);

            records.put(ruleId, record);
        }

        // 按命中次数排序，返回 Top N
        return records.values().stream()
                .sorted(Comparator.comparingLong(RuleHitRecord::getHitCount).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 填充规则信息（名称、来源）
     */
    private static void fillRuleInfo(RuleHitRecord record, String ruleId) {
        if ("hls.legacy-fallback".equals(ruleId)) {
            record.setRuleName("内置兜底规则（HLS 路径/文件名少数派、不连续短块）");
            record.setRuleSource("HLS");
            return;
        }

        for (HlsRuleConfig.Entry entry : HlsRuleConfig.getEntries()) {
            if (ruleId.equals(entry.id())) {
                record.setRuleName(TextUtils.isEmpty(entry.name()) ? entry.id() : entry.name());
                record.setRuleSource("HLS");
                return;
            }
        }

        // 查找用户自定义规则（用户规则以 UUID 作为 id）
        List<UserAdRule> userRules = UserAdRuleStore.load();
        for (UserAdRule rule : userRules) {
            if (ruleId.equals(rule.getId())) {
                record.setRuleName(rule.getName());
                record.setRuleSource("ai".equals(rule.getSource()) ? "AI" : "手动");
                return;
            }
        }

        // 查找默认规则（默认规则以 computeRuleId 计算的 id 标识）
        List<Rule> defaultRules = RuleConfig.get().getDefaultRules();
        for (Rule rule : defaultRules) {
            if (ruleId.equals(RuleIdUtil.computeRuleId(rule))) {
                record.setRuleName(rule.getName());
                record.setRuleSource("默认");
                return;
            }
        }

        // 未找到规则
        record.setRuleName("未知规则");
        record.setRuleSource("未知");
    }

    /**
     * 重置统计
     */
    public static void reset() {
        executor.execute(() -> {
            AdBlockStats stats = new AdBlockStats();
            save(stats);
        });
    }

    /**
     * 清空缓存（用于测试）
     */
    public static void clearCache() {
        cache = null;
    }

    /**
     * 安全地将 Map 中的值转为 long。
     * Gson 在泛型签名缺失时可能把数字反序列化为 Double，直接拆箱会 ClassCastException，此处兼容处理。
     */
    private static long toLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        return 0L;
    }
}
