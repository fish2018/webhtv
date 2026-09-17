package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AboutDialogLayoutTest {

    @Test
    public void aboutDialogWindowWrapsItsContentInsteadOfForcingFullHeight() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "AboutDialog.java")));

        assertTrue("About dialog window height should wrap its content so short text does not float above the buttons",
                source.contains("params.height = WindowManager.LayoutParams.WRAP_CONTENT;"));
        assertFalse("About dialog must not force a near-full-screen window height",
                source.contains("getDialogHeight"));
    }

    @Test
    public void aboutDialogUsesOneScrollableDialogShell() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "AboutDialog.java")));
        String layout = read(findMainResPath().resolve(Path.of("layout", "dialog_about.xml")));

        assertTrue("About dialog should install its styled content directly into the window",
                source.contains("dialog.setContentView(binding.getRoot());"));
        assertFalse("About dialog content must not be wrapped in a second padded dialog shell",
                source.contains("LightDialog.create(activity, null, binding.getRoot())"));
        assertTrue("About dialog root should wrap its content so the window can size to the disclaimer",
                layout.contains("android:layout_height=\"wrap_content\""));
        assertTrue("About dialog should set maxHeight dynamically in code based on screen size",
                source.contains("binding.contentScroll.setMaxHeight(maxScrollHeight);"));
        String contentScroll = layout.substring(layout.indexOf("android:id=\"@+id/contentScroll\""));
        assertFalse("About dialog should not hardcode maxHeight in XML — it must adapt to screen size",
                contentScroll.contains("app:maxHeight="));
    }

    @Test
    public void aboutDialogConstrainsScrollableContentBeforeFinalWindowSizing() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "AboutDialog.java")));

        int constrainScroll = source.indexOf("binding.contentScroll.setMaxHeight(maxScrollHeight);");
        int remeasureRoot = source.indexOf("binding.getRoot().measure(widthSpec, heightSpec);", constrainScroll);
        int finalWindowLayout = source.lastIndexOf("window.setLayout(params.width, dialogHeight);");

        assertTrue("The disclaimer must be constrained before the complete dialog is measured again",
                constrainScroll >= 0 && remeasureRoot > constrainScroll);
        assertTrue("The final window height must use the post-constraint root measurement so action buttons stay visible",
                finalWindowLayout > remeasureRoot);
        assertTrue("When fixed dialog chrome is tall, the dialog should sacrifice outer margin instead of clipping actions",
                source.contains("Math.min(availableHeight, Math.max(chromeHeight + 1, availableHeight - ResUtil.dp2px(DIALOG_VERTICAL_MARGIN_DP)))"));
        assertFalse("A 200dp scroll minimum can exceed the remaining TV height and push all action buttons outside the window",
                source.contains("Math.max(ResUtil.dp2px(200), availableHeight - chromeHeight"));
    }

    @Test
    public void lightAlertDialogWidthUsesPhysicalWidthAndHeight() {
        assertEquals(998, LightDialog.resolveAlertWidth(1920, 1080));
        assertEquals(1120, LightDialog.resolveAlertWidth(2560, 1080));
        assertEquals(972, LightDialog.resolveAlertWidth(1080, 1920));
    }

    @Test
    public void lightAlertDialogListHeightTracksPhysicalScreenHeight() {
        assertEquals(626, LightDialog.resolveAlertListMaxHeight(1080));
        assertEquals(418, LightDialog.resolveAlertListMaxHeight(720));
        assertEquals(886, LightDialog.resolveAlertWindowMaxHeight(1080));
        assertEquals(590, LightDialog.resolveAlertWindowMaxHeight(720));
        assertEquals(698, LightDialog.resolveAlertWindowHeight(496, 278, 480, 886));
        assertEquals(506, LightDialog.resolveAlertWindowHeight(496, 278, 288, 886));
        assertEquals(886, LightDialog.resolveAlertWindowHeight(700, 300, 700, 886));
    }

    @Test
    public void githubProxyDialogExposesPersistentEnableSwitch() throws Exception {
        String layout = read(findMainResPath().resolve(Path.of("layout", "dialog_github_proxy.xml")));
        String dialog = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "AboutDialog.java")));
        String setting = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "setting", "Setting.java")));
        String proxy = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "utils", "GithubProxy.java")));

        assertTrue(layout.contains("<androidx.appcompat.widget.SwitchCompat"));
        assertFalse(layout.contains("<com.google.android.material.switchmaterial.SwitchMaterial"));
        assertTrue(layout.contains("android:id=\"@+id/enabled\""));
        assertTrue(dialog.contains("binding.enabled.setChecked(Setting.isGithubProxyEnabled());"));
        assertTrue(dialog.contains("Setting.putGithubProxyEnabled(isChecked)"));
        assertTrue(setting.contains("Prefers.getBoolean(\"github_proxy_enabled\", true)"));
        assertTrue(proxy.contains("Setting.isGithubProxyEnabled()"));
    }

    @Test
    public void githubProxyRemoveActionUsesIconButtonOnNarrowScreens() throws Exception {
        String layout = read(findMainResPath().resolve(Path.of("layout", "adapter_github_proxy.xml")));
        String remove = layout.substring(layout.indexOf("android:id=\"@+id/remove\""));
        remove = remove.substring(0, remove.indexOf("/>"));

        assertTrue("GitHub proxy remove action should be an icon button to avoid wrapped text on phones",
                layout.contains("<androidx.appcompat.widget.AppCompatImageButton")
                        && remove.contains("android:layout_width=\"44dp\"")
                        && remove.contains("android:layout_height=\"44dp\""));
        assertTrue("GitHub proxy remove action should keep an accessible label",
                remove.contains("android:contentDescription=\"@string/setting_github_proxy_remove\""));
        assertTrue("GitHub proxy remove action should use the existing delete icon",
                remove.contains("android:src=\"@drawable/ic_action_delete\""));
        assertFalse("GitHub proxy remove action should not render text that can wrap on narrow screens",
                remove.contains("android:text="));
        assertFalse("GitHub proxy remove action should not use weighted narrow columns",
                remove.contains("android:layout_weight="));
    }

    @Test
    public void mobileGithubProxyActionsDoNotRequireFocusBeforeClick() throws Exception {
        String layout = read(findMobileResPath().resolve(Path.of("layout", "adapter_github_proxy.xml")));
        String text = layout.substring(layout.indexOf("android:id=\"@+id/text\""));
        text = text.substring(0, text.indexOf("/>"));
        String remove = layout.substring(layout.indexOf("android:id=\"@+id/remove\""));
        remove = remove.substring(0, remove.indexOf("/>"));

        assertFalse("Mobile GitHub proxy source should activate on the first tap, not first acquire focus",
                text.contains("android:focusable=\"true\"")
                        || text.contains("android:focusableInTouchMode=\"true\""));
        assertFalse("Mobile GitHub proxy remove should run on the first tap, not first acquire focus",
                remove.contains("android:focusable=\"true\"")
                        || remove.contains("android:focusableInTouchMode=\"true\""));
    }

    @Test
    public void githubProxyDialogFillsTheScreenOnTv() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "AboutDialog.java")));

        assertTrue(source.contains("configureGithubProxyWindow(activity, githubDialog, binding);"));
        // 电视端改为铺满全屏，不再按屏幕比例手算宽高——手算需要同时算对屏宽高、content
        // 高度与各类 inset，任何一项在某些盒子上出错就会裁掉底部内容。
        assertFalse("TV GitHub proxy dialog should no longer derive its window size from screen ratios",
                source.contains("resolveGithubProxyWidth")
                        || source.contains("resolveGithubProxyHeight")
                        || source.contains("resolveGithubProxyListHeight"));
        assertTrue("TV GitHub proxy dialog should fill the screen",
                source.contains("params.height = WindowManager.LayoutParams.MATCH_PARENT;"));
    }

    @Test
    public void aboutDialogFillsTheScreenOnTvButKeepsWrapContentOnPhones() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "AboutDialog.java")));

        // 电视端走独立的全屏分支，手机端保持原有 wrap_content 行为不变。
        assertTrue("TV should take a dedicated fullscreen path",
                source.contains("if (Util.isLeanback()) return configureFullscreenWindow(window, binding);"));
        assertTrue("The scroll area should absorb leftover space so the action buttons stay pinned",
                source.contains("weight = 1"));
        assertTrue("Phones must keep sizing the window to their content",
                source.contains("params.height = WindowManager.LayoutParams.WRAP_CONTENT;"));
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

    private static Path findMobileResPath() {
        Path moduleRelative = Path.of("src", "mobile", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "res");
    }
}
