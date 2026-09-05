package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.utils.HlsManifestCleaner;

import org.junit.Test;

import java.util.List;
import java.util.regex.Pattern;

/** pathOnlyPattern 必须通过 HlsManifestCleaner 的正则安全检查并只匹配 path。 */
public class PathOnlyPatternSafetyTest {

    @Test
    public void patternCompilesInsideCleanerRule() {
        // looksDangerous 拒绝 .*.* / .+.+ / 括号量词嵌套；^[^?]*\Q..\E 都不满足
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .playlistHostSuffixes(List.of("v.example.com"))
                .segmentUrlPatterns(List.of(HlsSegmentClassifier.pathOnlyPattern("/ads/")))
                .minimumSignals(1)
                .build();
        // build() 会预编译正则，未抛异常即通过安全检查
        org.junit.Assert.assertNotNull(rule);
    }

    @Test
    public void matchesPathButNotQueryOrHost() {
        Pattern p = Pattern.compile(HlsSegmentClassifier.pathOnlyPattern("/ads/"));
        assertTrue(p.matcher("https://v.example.com/ads/1.ts").find());
        // query 里的 hint 不得命中 —— 这是校验与执行语义一致的关键
        assertFalse(p.matcher("https://v.example.com/seg/1.ts?ref=/ads/").find());
        // 域名含 ads 不命中
        assertFalse(p.matcher("https://ads.example.com/seg/1.ts").find());
        // 无斜杠包围不命中
        assertFalse(p.matcher("https://v.example.com/upload/ads1.ts").find());
        assertFalse(p.matcher("https://v.example.com/downloads/1.ts").find());
    }
}
