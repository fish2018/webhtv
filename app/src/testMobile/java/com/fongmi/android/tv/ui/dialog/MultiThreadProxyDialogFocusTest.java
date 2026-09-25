package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class MultiThreadProxyDialogFocusTest {

    @Test
    public void everyInteractiveControlBelongsToAnExplicitDpadFocusGraph() throws Exception {
        String source = dialogSource();

        assertTrue(source.contains("binding.enabled.requestFocus();"));
        assertTrue(source.contains("wireRemoteFocus();"));
        assertTrue(source.contains("wireDpadFocus(binding.enabled, null, binding.rangeConcurrency, null, null);"));
        assertTrue(source.contains("wireDpadFocus(binding.rangeConcurrency, binding.enabled, binding.shardCount, null, null);"));
        assertTrue(source.contains("wireDpadFocus(binding.shardCount, binding.rangeConcurrency, binding.extractCurrentDomain, null, null);"));
        assertTrue(source.contains("wireDpadFocus(binding.extractCurrentDomain, binding.shardCount, binding.domainRules, null, null);"));
        assertTrue(source.contains("wireMultilineDpadFocus(binding.domainRules, binding.extractCurrentDomain, binding.save);"));
        assertTrue(source.contains("wireDpadFocus(binding.cancel, binding.domainRules, null, null, binding.save);"));
        assertTrue(source.contains("wireDpadFocus(binding.save, binding.domainRules, null, binding.cancel, null);"));
    }

    @Test
    public void actionButtonsUseVisibleFocusStateSelectors() throws Exception {
        String layout = layoutSource();
        String extract = controlTag(layout, "extractCurrentDomain");
        String cancel = controlTag(layout, "cancel");
        String save = controlTag(layout, "save");

        assertTrue(extract.contains("app:backgroundTint=\"@color/dialog_outlined_button_bg\""));
        assertTrue(extract.contains("app:strokeColor=\"@color/dialog_outlined_button_stroke\""));
        assertTrue(cancel.contains("app:backgroundTint=\"@color/dialog_outlined_button_bg\""));
        assertTrue(cancel.contains("app:strokeColor=\"@color/dialog_outlined_button_stroke\""));
        assertTrue(save.contains("app:backgroundTint=\"@color/dialog_tonal_button_bg\""));
        assertTrue(resourceSource("color/dialog_outlined_button_bg.xml").contains("android:state_focused=\"true\""));
        assertTrue(resourceSource("color/dialog_outlined_button_stroke.xml").contains("android:state_focused=\"true\""));
        assertTrue(resourceSource("color/dialog_tonal_button_bg.xml").contains("android:state_focused=\"true\""));
    }

    @Test
    public void multilineRulesOnlyLeaveAtTheFirstOrLastVisualLine() throws Exception {
        String source = dialogSource();

        assertTrue(source.contains("keyCode == KeyEvent.KEYCODE_DPAD_UP && upTarget != null && isCursorAtFirstLine(input)"));
        assertTrue(source.contains("keyCode == KeyEvent.KEYCODE_DPAD_DOWN && downTarget != null && isCursorAtLastLine(input)"));
        assertTrue(source.contains("layout.getLineForOffset(selection(input)) <= 0"));
        assertTrue(source.contains("layout.getLineForOffset(selection(input)) >= layout.getLineCount() - 1"));
    }

    private static String dialogSource() throws Exception {
        return new String(Files.readAllBytes(findMainJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "dialog", "MultiThreadProxyDialog.java"))),
                StandardCharsets.UTF_8);
    }

    private static String layoutSource() throws Exception {
        return resourceSource("layout/dialog_multi_thread_proxy.xml");
    }

    private static String resourceSource(String relativePath) throws Exception {
        return Files.readString(findMainResPath().resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static String controlTag(String layout, String id) {
        int idAt = layout.indexOf("android:id=\"@+id/" + id + "\"");
        int start = layout.lastIndexOf('<', idAt);
        int end = layout.indexOf("/>", idAt);
        return layout.substring(start, end);
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
