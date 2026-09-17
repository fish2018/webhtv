package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class AiConfigPromptLayoutTest {

    @Test
    public void promptEditorIncludesGroupRulePromptWithResetAndFocusWiring() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "AiConfigDialog.java")));
        String layout = read(findMainResPath().resolve(Path.of("layout", "dialog_ai_prompt_config.xml")));

        assertTrue(layout.contains("@+id/groupRulePrompt"));
        assertTrue(layout.contains("@string/dialog_ai_group_rule_prompt_label"));
        assertTrue(source.contains("config.getGroupRulePrompt()"));
        assertTrue(source.contains("config.setGroupRulePrompt(text(groupRulePrompt))"));
        assertTrue(source.contains("AiConfig.DEFAULT_GROUP_RULE_PROMPT"));
        assertTrue(source.contains("wirePromptEditorFocus(groupRulePrompt, adDetectionPrompt, positive)"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findMainResPath() {
        Path moduleRelative = Path.of("src", "main", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "res");
    }
}
