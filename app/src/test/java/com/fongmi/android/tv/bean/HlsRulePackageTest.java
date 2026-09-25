package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HlsRulePackageTest {

    private static final String PACKAGE_JSON = "{"
            + "\"schemaVersion\":2,"
            + "\"packageId\":\"builtin-hls\","
            + "\"version\":2,"
            + "\"rules\":[{"
            + "\"id\":\"builtin.example.v1\","
            + "\"name\":\"示例规则\","
            + "\"version\":2,"
            + "\"enabledByDefault\":false,"
            + "\"playlistHostSuffixes\":[\"video.example.com\"],"
            + "\"hostSuffixes\":[\"ads.example.com\"],"
            + "\"minimumSignals\":1"
            + "}]}";

    @Test
    public void parsesVersionedBuiltinRulePackage() {
        HlsRulePackage rulePackage = HlsRulePackage.parse(PACKAGE_JSON);

        assertEquals(2, rulePackage.getSchemaVersion());
        assertEquals("builtin-hls", rulePackage.getPackageId());
        assertEquals(2, rulePackage.getVersion());
        assertEquals(1, rulePackage.getRules().size());
        assertEquals("builtin.example.v1", rulePackage.getRules().get(0).getId());
    }

    @Test
    public void builtinRuleRequiresExplicitOverrideWhenDefaultIsOff() {
        HlsAdRule rule = HlsRulePackage.parse(PACKAGE_JSON).getRules().get(0);

        String key = HlsRuleState.key("builtin", "builtin-hls", rule.getId());
        assertFalse(key.contains("builtin-hls"));
        assertFalse(HlsRuleState.resolveEnabled(rule, key, Map.of()));
        assertTrue(HlsRuleState.resolveEnabled(rule, key, Map.of(key, true)));
    }

    @Test
    public void malformedPackageFallsBackToEmpty() {
        HlsRulePackage rulePackage = HlsRulePackage.parse("not-json");

        assertTrue(rulePackage.getRules().isEmpty());
    }
}
