package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.regex.Pattern;

/**
 * 路径正则必须同时排除 query 与 fragment。
 *
 * <p>证据侧用 {@code URI.getPath()}，query 和 fragment 都被丢弃，所以
 * {@code SegmentContrast.pathAbsentOutside} 与 {@code RuleSelfCheck} 都看不见它们。
 * 只挡 {@code ?} 时，正片 URL 形如 {@code /seg/99.ts#/ads/x} 会被跨 {@code #} 命中，
 * 实测真实 cleaner 会多删一片正片且 {@code fallback=false}，错误不被兜住。
 */
public class PathFragmentTest {

    @Test
    public void patternRejectsBothQueryAndFragment() {
        Pattern p = Pattern.compile(HlsSegmentClassifier.pathOnlyPattern("/ads/"));

        assertTrue("真实广告目录段必须命中", p.matcher("https://v.example.com/ads/1.ts").find());

        assertFalse("query 里的 hint 不得命中",
                p.matcher("https://v.example.com/seg/1.ts?ref=/ads/").find());
        assertFalse("fragment 里的 hint 不得命中",
                p.matcher("https://v.example.com/seg/99.ts#/ads/x").find());
        assertFalse("query + fragment 组合同样不得命中",
                p.matcher("https://v.example.com/seg/9.ts?t=1#/ads/x").find());
        assertFalse("域名含 ads 不得命中",
                p.matcher("https://ads.example.com/seg/1.ts").find());
        assertFalse("无斜杠包围不得命中",
                p.matcher("https://v.example.com/upload/ads1.ts").find());
    }
}
