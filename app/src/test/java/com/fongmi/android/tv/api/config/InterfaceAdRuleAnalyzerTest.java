package com.fongmi.android.tv.api.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.bean.ImportedAdRuleCandidate;
import com.fongmi.android.tv.bean.Rule;

import org.junit.Test;

import java.util.List;

public class InterfaceAdRuleAnalyzerTest {

    @Test
    public void extractsAdsAsLowRiskCandidates() {
        List<ImportedAdRuleCandidate> result = InterfaceAdRuleAnalyzer.analyze(
                "嗷呜", "https://example.com/config?token=secret",
                List.of("mozai.4gtv.tv"), List.of());

        assertEquals(1, result.size());
        ImportedAdRuleCandidate item = result.get(0);
        assertEquals(ImportedAdRuleCandidate.CLASS_AD_BLOCK, item.getClassification());
        assertEquals(ImportedAdRuleCandidate.RISK_LOW, item.getRiskLevel());
        assertEquals(List.of("mozai.4gtv.tv"), item.getHosts());
        assertFalse(item.getSourceConfigUrlHash().contains("secret"));
    }

    @Test
    public void mapsExternalExcludeToAdRegex() {
        Rule rule = Rule.create("广告过滤", List.of("video.example.com"),
                List.of("main-content"), List.of("/ads/.*\\.ts"));

        List<ImportedAdRuleCandidate> result = InterfaceAdRuleAnalyzer.analyze(
                "饭太硬", "https://example.com/config", List.of(), List.of(rule));

        assertEquals(1, result.size());
        assertEquals(ImportedAdRuleCandidate.CLASS_AD_BLOCK, result.get(0).getClassification());
        assertEquals(List.of("/ads/.*\\.ts"), result.get(0).getRegex());
        assertEquals(List.of("main-content"), result.get(0).getExclude());
    }

    @Test
    public void rejectsSniffAndScriptRules() {
        Rule sniff = Rule.create("抖音嗅探", List.of("douyin.com"), List.of("is_play_url="), List.of());
        Rule script = Rule.create("网页点击", List.of("example.com"), List.of(), List.of());
        setScript(script, List.of("document.querySelector('button').click()"));

        List<ImportedAdRuleCandidate> result = InterfaceAdRuleAnalyzer.analyze(
                "嗷呜", "https://example.com/config", List.of(), List.of(sniff, script));

        assertTrue(result.isEmpty());
    }

    @Test
    public void rejectsDangerousRegexAndDeduplicates() {
        Rule broad = Rule.create("bad", List.of("example.com"), List.of(), List.of("(a+)+$"));

        List<ImportedAdRuleCandidate> result = InterfaceAdRuleAnalyzer.analyze(
                "source", "https://example.com/config",
                List.of("ads.example.com", "ADS.EXAMPLE.COM"), List.of(broad));

        assertEquals(1, result.size());
        assertEquals("ads.example.com", result.get(0).getHosts().get(0));
    }

    @Test
    public void fingerprintPreservesFieldSemantics() {
        String ad = InterfaceAdRuleAnalyzer.fingerprint(List.of(), List.of("pattern"), List.of());
        String protection = InterfaceAdRuleAnalyzer.fingerprint(List.of(), List.of(), List.of("pattern"));
        assertFalse(ad.equals(protection));
    }

    private static void setScript(Rule rule, List<String> scripts) {
        try {
            java.lang.reflect.Field field = Rule.class.getDeclaredField("script");
            field.setAccessible(true);
            field.set(rule, scripts);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
