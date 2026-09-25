package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BuiltinHlsRuleFixtureTest {

    @Test
    public void builtinPackageParsesAndContainsNoExperimentalInterceptionRules() throws Exception {
        HlsRulePackage rulePackage = HlsRulePackage.parse(Files.readString(
                projectRoot().resolve("app/src/main/assets/rules/hls_rules.json"), StandardCharsets.UTF_8));

        assertEquals(2, rulePackage.getSchemaVersion());
        assertEquals("webhtv-builtin-hls", rulePackage.getPackageId());
        assertEquals(1, rulePackage.getVersion());
        assertTrue(rulePackage.getRules().isEmpty());
    }

    private static Path projectRoot() {
        return Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
    }
}
