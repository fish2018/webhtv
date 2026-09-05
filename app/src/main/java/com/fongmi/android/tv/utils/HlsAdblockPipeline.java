package com.fongmi.android.tv.utils;

import androidx.media3.exoplayer.hls.playlist.HlsAdsParser;

import com.github.catvod.crawler.SpiderDebug;

import java.net.URI;
import java.util.List;

public final class HlsAdblockPipeline {

    private HlsAdblockPipeline() {}

    public static Outcome apply(String url, String manifest, List<HlsManifestCleaner.Rule> rules, boolean legacyFallback) {
        // Encrypted playlists whose IV is implied by the media sequence number must not be
        // rewritten by *either* engine, so the check lives here rather than inside one of
        // them. Putting it in HlsManifestCleaner was actively harmful: the cleaner's only
        // way to decline is Result.fallback, and fallback is precisely what invites the
        // legacy heuristic in below — measured, that turned "25 ads removed" into "200 main
        // segments deleted by the heuristic, and every retained segment decrypting with the
        // wrong IV". Declining here stops both.
        if (HlsEncryptionGuard.hasImpliedSegmentIv(manifest)) {
            SpiderDebug.log("hls-adblock", "declined reason=implied-segment-iv url=%s", url);
            return new Outcome(manifest, false, false, 0, 0);
        }
        HlsManifestCleaner.Result clean = HlsManifestCleaner.clean(url, manifest, rules);
        if (clean.changed()) {
            return new Outcome(clean.manifest(), true, false, clean.removedSegments(), clean.removedDurationSec());
        }
        // 结构化引擎回退时仍要让 legacy 兜底。fallback 的语义是「这份 manifest
        // 我处理不了」，恰恰最该由启发式接手；此前把它并入短路条件，等于一条
        // 过宽的规则不只无效，还会连带停掉该站原有的去广能力 —— 比不加规则更差。
        // 触发源不止用户规则，内置与接口下发的规则同样会让 cleaner 回退。
        if (!legacyFallback || manifest == null || !manifest.contains("#EXT-X-ENDLIST")) {
            return new Outcome(manifest, false, false, 0, 0);
        }
        try {
            String filtered = HlsAdsParser.process(manifest);
            return new Outcome(filtered, false, !filtered.equals(manifest), 0, 0);
        } catch (Throwable ignored) {
            return new Outcome(manifest, false, false, 0, 0);
        }
    }

    public static boolean isCoreM3u8Proxy(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            boolean loopback = "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
            return loopback && "/m3u8".equals(uri.getPath());
        } catch (RuntimeException e) {
            return false;
        }
    }

    public record Outcome(String manifest, boolean structured, boolean legacy, int removedSegments, double removedDurationSec) {}
}
