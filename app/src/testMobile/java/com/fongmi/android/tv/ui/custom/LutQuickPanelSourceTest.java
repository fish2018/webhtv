package com.fongmi.android.tv.ui.custom;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class LutQuickPanelSourceTest {

    @Test
    public void lutQuickPanelKeepsChipTextWhiteWhenBackgroundStateChanges() throws Exception {
        String source = readMainJava("com", "fongmi", "android", "tv", "ui", "custom", "LutQuickPanel.java");
        int method = source.indexOf("private void applyBg(MaterialTextView view, boolean selected, boolean focused)");
        int end = source.indexOf("private int dp(int value)", method);

        assertTrue("LutQuickPanel must refresh chip text color together with background state",
                method >= 0 && end > method && source.indexOf("view.setTextColor(Color.WHITE);", method) > method
                        && source.indexOf("view.setTextColor(Color.WHITE);", method) < end);
    }

    @Test
    public void lutQuickPanelRaisesItselfAbovePlayerControlsWhenShown() throws Exception {
        String source = readMainJava("com", "fongmi", "android", "tv", "ui", "custom", "LutQuickPanel.java");
        int method = source.indexOf("private void show()");
        int visible = source.indexOf("setVisibility(VISIBLE);", method);

        assertTrue("LutQuickPanel must move above fullscreen controls before becoming visible",
                method >= 0 && visible > method && source.indexOf("bringToFront();", method) > method
                        && source.indexOf("bringToFront();", method) < visible);
    }

    @Test
    public void lutQuickPanelKeepsChipTextWhiteImmediatelyAfterLabelChanges() throws Exception {
        String source = readMainJava("com", "fongmi", "android", "tv", "ui", "custom", "LutQuickPanel.java");
        int helper = source.indexOf("private void setChipText(MaterialTextView view,");
        int renderList = source.indexOf("private void renderList(List<LutPreset> presets)");
        int createPanel = source.indexOf("private View createPanel()");
        int cycleDelay = source.indexOf("private void cycleDelay()");

        assertTrue("LutQuickPanel chip labels must reapply white text after setText",
                helper >= 0
                        && source.indexOf("view.setTextColor(Color.WHITE);", helper) > helper
                        && source.indexOf("setChipText(delay, ResUtil.getString", renderList) > renderList
                        && source.indexOf("setChipText(reset, R.string.lut_reset)", createPanel) > createPanel
                        && source.indexOf("setChipText(close, R.string.lut_close)", createPanel) > createPanel
                        && source.indexOf("setChipText(importView, R.string.lut_local)", createPanel) > createPanel
                        && source.indexOf("setChipText(dirView, R.string.lut_directory)", createPanel) > createPanel
                        && source.indexOf("setChipText(delay, ResUtil.getString", cycleDelay) > cycleDelay);
    }

    @Test
    public void lutQuickPanelRefreshesAllChipTextWhiteAfterShow() throws Exception {
        String source = readMainJava("com", "fongmi", "android", "tv", "ui", "custom", "LutQuickPanel.java");
        int chips = source.indexOf("private final List<MaterialTextView> chips");
        int show = source.indexOf("private void show()");
        int panelPost = source.indexOf("panel.post(() ->", show);
        int refresh = source.indexOf("private void refreshChipTextColors()");
        int chip = source.indexOf("private MaterialTextView chip()");

        assertTrue("LutQuickPanel must refresh every static chip after the panel is attached and shown",
                chips >= 0
                        && refresh > show
                        && source.indexOf("refreshChipTextColors();", show) > show
                        && source.indexOf("refreshChipTextColors();", panelPost) > panelPost
                        && source.indexOf("chips.add(view);", chip) > chip
                        && source.indexOf("view.setTextColor(Color.WHITE);", refresh) > refresh);
    }

    private static String readMainJava(String first, String... more) throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of(first, more));
        return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
