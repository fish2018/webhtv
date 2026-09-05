package com.fongmi.android.tv.ad.feedback;

import com.fongmi.android.tv.bean.M3u8Evidence;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 把 {@code M3u8Parser} 已产出的 playlist 证据 + 用户区间，组装成
 * {@link AdIntervalEvidence}。纯计算，不发起网络请求，不触碰 Android 类型。
 *
 * <p>见设计文档第 6.1、7.1 节。
 */
public final class AdEvidenceCollector {

    /**
     * 区间外切片完整保留：分类器会用它证明区间外存在/不存在对照。
     * M3u8Evidence 的输入来自 HlsManifestCleaner 同样的有界 manifest（2 MiB / 20,000 行），
     * 不能截断后再把 anyMatch/noneMatch 当成整个 playlist 的结论。
     */

    private AdEvidenceCollector() {
    }

    /**
     * 播放上下文，全部由调用方从 Activity/PlayerManager 取出，保持本类可单测。
     *
     * @param siteKey      站点标识
     * @param siteName     站点名称
     * @param vodName      剧名
     * @param flagName     线路名
     * @param episodeName  集名
     * @param playUrl      播放地址，仅用于取 host/path，不入证据
     * @param hls          是否 HLS
     */
    public record Context(String siteKey, String siteName, String vodName,
                          String flagName, String episodeName, String playUrl, boolean hls) {

        public Context {
            siteKey = siteKey == null ? "" : siteKey;
            siteName = siteName == null ? "" : siteName;
            vodName = vodName == null ? "" : vodName;
            flagName = flagName == null ? "" : flagName;
            episodeName = episodeName == null ? "" : episodeName;
            playUrl = playUrl == null ? "" : playUrl;
        }
    }

    /**
     * 组装区间证据。
     *
     * @param evidence            playlist 证据，可为 null（非 HLS 或抓取失败）
     * @param blacklistedHosts    现有广告域名黑名单，用于填充 matchedExistingHosts
     * @param legacyHeuristicActive 旧启发式引擎当次是否生效
     */
    public static AdIntervalEvidence collect(Context context, M3u8Evidence evidence,
                                             long startMs, long endMs, StartOrigin origin,
                                             List<String> blacklistedHosts,
                                             boolean legacyHeuristicActive) {
        if (context == null) throw new IllegalArgumentException("context is required");
        String playlistHost = hostOf(context.playUrl());
        String urlPath = pathOf(context.playUrl());

        List<SegmentFact> inside = new ArrayList<>();
        List<SegmentFact> outside = new ArrayList<>();
        boolean bounded = false;

        if (evidence != null && !evidence.isEmpty()) {
            List<Float> durations = evidence.getDurations();
            List<String> segments = evidence.getSegments();
            Set<Integer> discontinuities = new LinkedHashSet<>();
            for (Integer index : evidence.getDiscontinuities()) {
                if (index != null) discontinuities.add(index);
            }
            Set<Integer> insideIndices = new LinkedHashSet<>(
                    AdIntervalMapper.insideIndices(durations, startMs, endMs));

            int limit = Math.min(segments.size(), durations.size());
            for (int i = 0; i < limit; i++) {
                SegmentFact fact = factOf(i, segments.get(i), durations.get(i),
                        discontinuities.contains(i));
                if (insideIndices.contains(i)) inside.add(fact);
                else outside.add(fact);
            }
            bounded = boundedByDiscontinuity(insideIndices, discontinuities, limit);
        }

        boolean crossDomain = !inside.isEmpty() && !playlistHost.isEmpty()
                && inside.stream().allMatch(fact ->
                        !fact.host().isEmpty() && !fact.hostEndsWith(playlistHost));

        return new AdIntervalEvidence(
                startMs, endMs, origin,
                context.siteKey(), context.siteName(), context.vodName(),
                context.flagName(), context.episodeName(),
                playlistHost, urlPath, context.hls(),
                inside, outside, bounded, crossDomain,
                List.of(), legacyHeuristicActive,
                matchedHosts(inside, blacklistedHosts),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());
    }

    /**
     * 区间首尾是否都贴着断点边界。首端要求区间第一个切片自身带断点，
     * 尾端要求区间后的第一个切片带断点（即广告块结束、正片重新开始）。
     */
    private static boolean boundedByDiscontinuity(Set<Integer> insideIndices,
                                                  Set<Integer> discontinuities, int total) {
        if (insideIndices.isEmpty() || discontinuities.isEmpty()) return false;
        int first = Integer.MAX_VALUE;
        int last = Integer.MIN_VALUE;
        for (Integer index : insideIndices) {
            first = Math.min(first, index);
            last = Math.max(last, index);
        }
        boolean head = discontinuities.contains(first);
        // 区间正好到 playlist 结尾时，没有「之后的切片」，视为尾端成立
        boolean tail = last + 1 >= total || discontinuities.contains(last + 1);
        return head && tail;
    }

    private static List<String> matchedHosts(List<SegmentFact> inside, List<String> blacklist) {
        if (inside.isEmpty() || blacklist == null || blacklist.isEmpty()) return List.of();
        Set<String> matched = new LinkedHashSet<>();
        for (SegmentFact fact : inside) {
            if (fact.host().isEmpty()) continue;
            String host = fact.host().toLowerCase(Locale.US);
            for (String entry : blacklist) {
                if (entry == null || entry.isBlank()) continue;
                String target = entry.toLowerCase(Locale.US).trim();
                if (host.equals(target) || host.endsWith("." + target) || host.contains(target)) {
                    matched.add(fact.host());
                    break;
                }
            }
        }
        return List.copyOf(matched);
    }

    private static SegmentFact factOf(int index, String url, Float duration, boolean discontinuity) {
        double seconds = duration == null ? 0d : duration;
        return new SegmentFact(index, hostOf(url), pathOf(url), seconds, discontinuity);
    }

    /** 取 host，解析失败返回空串。相对 URI 没有 host，属正常情况。 */
    static String hostOf(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? "" : host;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** 取去掉 query 的 path。相对 URI 直接返回其自身去参数部分。 */
    static String pathOf(String url) {
        if (url == null || url.isBlank()) return "";
        String trimmed = url.trim();
        try {
            String path = URI.create(trimmed).getPath();
            if (path != null && !path.isEmpty()) return path;
        } catch (RuntimeException ignored) {
            // 落到下面的字符串截断
        }
        int query = trimmed.indexOf('?');
        return query < 0 ? trimmed : trimmed.substring(0, query);
    }
}
