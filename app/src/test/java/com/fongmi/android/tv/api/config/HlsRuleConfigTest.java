package com.fongmi.android.tv.api.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.RuleHitRecord;
import com.fongmi.android.tv.bean.UserAdRule;
import com.fongmi.android.tv.utils.HlsAdblockPipeline;
import com.fongmi.android.tv.utils.HlsManifestCleaner;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class HlsRuleConfigTest {

    @Test
    public void legacyHostsScopePlaylistAndExcludeMatchesAdSegmentUrl() {
        List<HlsManifestCleaner.Rule> compiled = new ArrayList<>();
        HlsRuleConfig.compileLegacyRules(
                List.of(Rule.create("优质", List.of(".vvvip-plays33.cc"), List.of(), List.of("16.4666"))),
                compiled);
        String manifest = manifest();

        HlsAdblockPipeline.Outcome matched = HlsAdblockPipeline.apply(
                "https://foo.vvvip-plays33.cc/index.m3u8", manifest, compiled, false);
        HlsAdblockPipeline.Outcome outsideHost = HlsAdblockPipeline.apply(
                "https://video.example.com/index.m3u8", manifest, compiled, false);

        assertTrue(matched.structured());
        assertFalse(matched.manifest().contains("16.4666-ad.ts"));
        assertFalse(outsideHost.structured());
        assertTrue(outsideHost.manifest().contains("16.4666-ad.ts"));
    }

    @Test
    public void legacyCustomRuleHitResolvesToCustomRuleName() {
        UserAdRule custom = UserAdRule.createManual("我的自定义广告规则");
        custom.setHosts(List.of(".vvvip-plays33.cc"));
        custom.setRegex(List.of("16\\.4666-ad\\.ts"));
        List<HlsManifestCleaner.Rule> compiled = new ArrayList<>();
        HlsRuleConfig.compileLegacyRules(List.of(custom.toRule()), compiled);

        HlsAdblockPipeline.Outcome outcome = HlsAdblockPipeline.apply(
                "https://foo.vvvip-plays33.cc/index.m3u8", manifest(), compiled, true);
        String ruleId = outcome.removedSegmentDetails().get(0).ruleId();
        RuleHitRecord record = new RuleHitRecord();

        assertTrue(outcome.structured());
        assertFalse(outcome.legacy());
        assertFalse(HlsRuleConfig.LEGACY_FALLBACK_KEY.equals(ruleId));
        assertTrue(AdBlockStatsStore.fillConfiguredRuleInfo(
                record, ruleId, List.of(custom), List.of()));
        assertEquals("我的自定义广告规则", record.getRuleName());
        assertEquals("手动", record.getRuleSource());
    }

    @Test
    public void legacyRuleSourcesInvalidateCompiledCacheWhenConfigChanges() throws Exception {
        String userRules = readSource("UserAdRuleStore.java");
        String disabledRules = readSource("DisabledDefaultRuleStore.java");

        assertTrue(userRules.contains("RuleConfig.get().invalidate();"));
        assertTrue(userRules.contains("HlsRuleConfig.invalidate();"));
        assertTrue(disabledRules.contains("RuleConfig.get().invalidate();"));
        assertTrue(disabledRules.contains("HlsRuleConfig.invalidate();"));
    }

    private static String readSource(String fileName) throws Exception {
        return Files.readString(Path.of("src/main/java/com/fongmi/android/tv/api/config", fileName), StandardCharsets.UTF_8);
    }

    private static String manifest() {
        return "#EXTM3U\n"
                + "#EXTINF:7.0,\nhttps://cdn.example.com/16.4666-ad.ts\n"
                + "#EXTINF:8.0,\nhttps://cdn.example.com/main-1.ts\n"
                + "#EXTINF:8.0,\nhttps://cdn.example.com/main-2.ts\n"
                + "#EXT-X-ENDLIST\n";
    }
}
