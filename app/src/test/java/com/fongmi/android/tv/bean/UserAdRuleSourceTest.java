package com.fongmi.android.tv.bean;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;

import org.junit.Test;

public class UserAdRuleSourceTest {

    @Test
    public void oldRuleWithoutSourceRemainsManual() {
        UserAdRule rule = new Gson().fromJson("{\"name\":\"旧规则\"}", UserAdRule.class);

        assertEquals("手动添加 · 域名 0 · 广告规则 0 · 白名单 0", rule.getSummary());
    }

    @Test
    public void importedRuleKeepsInterfaceName() {
        ImportedAdRuleCandidate candidate = new ImportedAdRuleCandidate();
        candidate.setName("接口规则");
        candidate.setSourceType("rule");
        candidate.setSourceConfigName("我的接口");

        UserAdRule rule = UserAdRule.fromImportedCandidate(candidate);

        assertEquals("接口导入 · 我的接口 · 域名 0 · 广告规则 0 · 白名单 0", rule.getSummary());
    }
}
