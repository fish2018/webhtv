package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class GroupRuleDialogLayoutTest {

    @Test
    public void groupRuleDialogMatchesAiConfigTvChromeAndFocus() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "GroupRuleDialog.java")));
        String layout = read(findMainResPath().resolve(Path.of("layout", "dialog_group_rule_edit.xml")));
        String previewLayout = read(findMainResPath().resolve(Path.of("layout", "dialog_ai_group_rule_preview.xml")));

        assertTrue("Group rule editor should use the same light panel background as AI config",
                layout.contains("android:background=\"#F6F8FC\""));
        assertTrue("Group rule editor should group fields into light dialog cards",
                layout.contains("com.google.android.material.card.MaterialCardView")
                        && layout.contains("app:cardCornerRadius=\"18dp\"")
                        && layout.contains("app:strokeColor=\"#D8E0EA\""));
        assertTrue("Group rule editor inputs should use AI config's stable TV heights",
                layout.contains("android:layout_height=\"44dp\""));

        assertTrue("Group rule dialog should use LightDialog theme",
                source.contains("Theme_WebHTV_LightDialog") && source.contains("LightDialog.apply"));
        assertTrue("Group rule editor should wire an explicit DPAD focus chain after AlertDialog buttons exist",
                source.contains("wireTextDpadFocus(nameInput, null, regexInput, null, null)")
                        && source.contains("wireTextDpadFocus(regexInput, nameInput, positive, null, null)"));
        assertTrue("Group rule editor should keep text editing usable by only leaving horizontal inputs at cursor edges",
                source.contains("private static void wireTextDpadFocus(EditText view, View up, View down, View left, View right)")
                        && source.contains("isCursorAtStart(view)")
                        && source.contains("isCursorAtEnd(view)"));
        assertTrue("AI rule mutations should use the interface snapshot captured when the dialog opened",
                source.contains("AiGroupRuleStore.update(configKey, rule)")
                        && source.contains("showEditor(rule, configKey)")
                        && source.contains("AiGroupRuleStore.delete(configKey, existing.getId())"));
        assertTrue("AI rule mutations must not resolve the interface key only when the user clicks",
                !source.contains("AiGroupRuleStore.update(currentConfigKey(), rule)")
                        && !source.contains("AiGroupRuleStore.delete(currentConfigKey(), existing.getId())"));
        assertTrue("AI preview rule container type must match the AppCompat layout class",
                previewLayout.contains("androidx.appcompat.widget.LinearLayoutCompat")
                        && source.contains("LinearLayoutCompat rulesContainer")
                        && source.contains("new LinearLayoutCompat.LayoutParams"));
        assertTrue("AI preview must not cast LinearLayoutCompat to framework LinearLayout",
                !source.contains("LinearLayout rulesContainer"));
        assertTrue("AI preview should use adaptive window sizing and delayed initial focus",
                source.contains("configureAiPreviewWindow(dialog, contentScroll)")
                        && source.contains("AiGroupRulePreviewSizing.calculate")
                        && source.contains("initial.post(() -> requestFocus(initial))"));
        assertTrue("TV candidate checkboxes and dialog buttons should participate in touch-mode focus",
                source.contains("check.setFocusableInTouchMode(Util.isLeanback())")
                        && source.contains("button.setFocusableInTouchMode(Util.isLeanback())"));
        assertTrue("Preview text must not become a selectable TV focus trap",
                !previewLayout.contains("android:textIsSelectable=\"true\""));
        assertTrue("AI preview should intercept DPAD at the dialog level before NestedScrollView consumes it",
                source.contains("dialog.setOnKeyListener")
                        && source.contains("handleAiPreviewKey(dialog, keyCode, event, checks, positive, neutral, negative)"));
        assertTrue("Adaptive preview should set the actual dialog height, not only the inner scroll height",
                source.contains("params.height = size.dialogHeight()")
                        && source.contains("window.setLayout(size.width(), size.dialogHeight())"));
        assertTrue("AI preview should wire every candidate downward to the next candidate or dialog buttons",
                source.contains("wireAiPreviewFocus(checks, positive, neutral, negative)")
                        && source.contains("View down = i + 1 < checks.size() ? checks.get(i + 1) : neutral")
                        && source.contains("wireDpadFocus(checks.get(i), up, down, null, null)"));
        assertTrue("AI preview dialog buttons should have an explicit horizontal DPAD chain",
                source.contains("wireDpadFocus(neutral, last, null, null, negative)")
                        && source.contains("wireDpadFocus(negative, last, null, neutral, positive)")
                        && source.contains("wireDpadFocus(positive, last, null, negative, null)"));
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
