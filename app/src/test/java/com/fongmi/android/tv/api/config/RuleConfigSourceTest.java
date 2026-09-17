package com.fongmi.android.tv.api.config;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RuleConfigSourceTest {

    @Test
    public void sourcePrefersUserProvidedConfigName() {
        assertEquals("点播接口 · 我的接口", RuleConfig.formatSource("点播接口", "我的接口", "https://example.com/config.json"));
    }

    @Test
    public void sourceFallsBackToConfigUrl() {
        assertEquals("直播接口 · https://example.com/live.json", RuleConfig.formatSource("直播接口", "", "https://example.com/live.json"));
    }
}
