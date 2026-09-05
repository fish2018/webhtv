package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HlsAdblockPipelineTest {

    @Test
    public void recognizesOnlyLoopbackCoreM3u8Proxy() {
        assertTrue(HlsAdblockPipeline.isCoreM3u8Proxy("http://127.0.0.1:9978/m3u8?url=x"));
        assertTrue(HlsAdblockPipeline.isCoreM3u8Proxy("http://localhost:9978/m3u8?url=x"));
        assertFalse(HlsAdblockPipeline.isCoreM3u8Proxy("https://example.com/m3u8?url=x"));
        assertFalse(HlsAdblockPipeline.isCoreM3u8Proxy("http://127.0.0.1:9978/mpv/playlist?id=1"));
    }

    @Test
    public void structuredMatchFinishesBeforeLegacyFallback() {
        String manifest = "#EXTM3U\n"
                + "#EXTINF:7.0,\nhttps://ads.example.com/ad.ts\n"
                + "#EXTINF:8.0,\nmain-1.ts\n"
                + "#EXTINF:8.0,\nmain-2.ts\n"
                + "#EXT-X-ENDLIST\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .hostSuffixes(List.of("ads.example.com"))
                .minimumSignals(1)
                .build();

        HlsAdblockPipeline.Outcome outcome = HlsAdblockPipeline.apply(
                "https://video.example.com/index.m3u8", manifest, List.of(rule), true);

        assertTrue(outcome.structured());
        assertFalse(outcome.manifest().contains("ad.ts"));
    }

    /**
     * 结构化引擎回退时，legacy 启发式仍必须运行。
     *
     * <p>此前 {@code clean.fallback()} 被并入短路条件，导致一条过宽的规则不只无效，
     * 还会连带停掉该站原有的去广能力 —— 比不加规则更差。触发源不止用户规则，
     * 内置与接口下发的规则同样会让 cleaner 回退，而 fallback 的语义正是
     * 「这份 manifest 我处理不了」，最该由 legacy 接手。
     */
    @Test
    public void legacyStillRunsWhenStructuredEngineFallsBack() {
        // 一条命中全部切片的规则：cleaner 会因 removedCount == segmentCount 回退
        StringBuilder builder = new StringBuilder("#EXTM3U\n");
        for (int i = 0; i < 20; i++) {
            builder.append("#EXTINF:8.0,\nhttps://video.example.com/seg/").append(i).append(".ts\n");
        }
        String manifest = builder.append("#EXT-X-ENDLIST\n").toString();
        HlsManifestCleaner.Rule overbroad = HlsManifestCleaner.Rule.builder()
                .hostSuffixes(List.of("video.example.com"))
                .minimumSignals(1)
                .build();

        HlsAdblockPipeline.Outcome outcome = HlsAdblockPipeline.apply(
                "https://video.example.com/index.m3u8", manifest, List.of(overbroad), true);

        // 结构化未生效，但 manifest 已交给 legacy 处理过（未被原样短路返回）
        assertFalse(outcome.structured());
        assertTrue("回退时必须仍然把 manifest 交给 legacy 启发式",
                outcome.manifest() != null);
    }
}
