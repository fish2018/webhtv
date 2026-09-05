package com.fongmi.android.tv.ad.feedback;

import java.util.Locale;
import java.util.Objects;

/**
 * 单个 HLS 切片的去敏事实。只保留 host 与 path，不含 query 参数与 token。
 *
 * @param index               在 media playlist 中的下标，从 0 开始
 * @param host                切片主机名，解析失败时为空串
 * @param path                切片路径，已去掉 query
 * @param durationSec         #EXTINF 时长
 * @param discontinuityBefore 该切片之前是否有 #EXT-X-DISCONTINUITY
 */
public record SegmentFact(int index, String host, String path,
                          double durationSec, boolean discontinuityBefore) {

    public SegmentFact {
        if (index < 0) throw new IllegalArgumentException("index must not be negative");
        host = host == null ? "" : host;
        path = path == null ? "" : path;
        if (durationSec < 0) durationSec = 0;
    }

    /** host 是否以给定后缀结尾，用于与 playlist 域名和黑名单比对。 */
    public boolean hostEndsWith(String suffix) {
        if (host.isEmpty() || suffix == null || suffix.isEmpty()) return false;
        String lower = host.toLowerCase(Locale.US);
        String target = suffix.toLowerCase(Locale.US);
        return lower.equals(target) || lower.endsWith("." + target);
    }

    /** host 是否与另一切片同域。 */
    public boolean sameHost(SegmentFact other) {
        return other != null && !host.isEmpty() && Objects.equals(host, other.host);
    }
}
