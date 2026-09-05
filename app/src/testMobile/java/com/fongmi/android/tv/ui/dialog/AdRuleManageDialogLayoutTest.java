package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdRuleManageDialogLayoutTest {

    @Test
    public void leanbackRuleManagerUsesReadableLightDialogColors() throws Exception {
        String dialog = read(projectRoot().resolve("app/src/leanback/res/layout/dialog_ad_rule_manage.xml"));
        String item = read(projectRoot().resolve("app/src/leanback/res/layout/adapter_ad_rule.xml"));
        String stats = read(projectRoot().resolve("app/src/leanback/res/layout/dialog_ad_block_stats.xml"));
        String statsItem = read(projectRoot().resolve("app/src/leanback/res/layout/adapter_ad_stats_item.xml"));
        String source = read(projectRoot().resolve("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/AdRuleManageDialog.java"));

        assertFalse("light rule dialog must not render white text on its white panel",
                dialog.contains("android:textColor=\"@color/white\"")
                        || dialog.contains("android:textColor=\"@color/white_50\""));
        assertFalse("light rule rows must not render white text or icons",
                item.contains("android:textColor=\"@color/white\"")
                        || item.contains("android:textColor=\"@color/white_50\"")
                        || item.contains("app:tint=\"@color/white\""));
        assertTrue("TV rule row content, switch and delete action must all accept DPAD focus",
                item.contains("android:id=\"@+id/text\"")
                        && item.contains("android:id=\"@+id/toggle\"")
                        && item.contains("android:id=\"@+id/delete\"")
                        && count(item, "android:focusable=\"true\"") >= 3);
        assertTrue("TV rule row focus must have a visible light-dialog highlight",
                item.contains("android:background=\"@drawable/selector_light_dialog_item\"")
                        && count(item, "android:background=\"@drawable/selector_dialog_switch\"") >= 2);
        assertTrue(dialog.contains("Widget.WebHTV.LightDialog.Button.Outlined"));
        assertFalse("statistics dialog must not use white text on its light panel",
                stats.contains("android:textColor=\"@color/white\"")
                        || stats.contains("android:textColor=\"@color/white_50\""));
        assertFalse("statistics rows must remain readable on the light panel",
                statsItem.contains("android:textColor=\"@color/white\"")
                        || statsItem.contains("android:textColor=\"@color/white_50\""));
        assertTrue("candidate picker must use the full light dialog theme",
                source.contains("new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog)\n                .setTitle(R.string.ad_rule_import_title)"));
        assertTrue("rule manager buttons must bridge DPAD focus into the first rule row",
                source.contains("binding.stats.setOnKeyListener")
                        && source.contains("binding.importCandidates.getVisibility() == View.VISIBLE")
                        && source.contains("focusFirstRule()"));
        String editSource = read(projectRoot().resolve("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/AdRuleEditDialog.java"));
        assertTrue("TV rule editor should wire an explicit vertical DPAD focus chain",
                editSource.contains("wireTextDpadFocus(binding.name, null, binding.hosts)")
                        && editSource.contains("wireTextDpadFocus(binding.hosts, binding.name, binding.regex)")
                        && editSource.contains("wireTextDpadFocus(binding.regex, binding.hosts, binding.exclude)")
                        && editSource.contains("wireTextDpadFocus(binding.exclude, binding.regex, binding.confirm)"));
    }

    @Test
    public void leanbackRuleEditorAndPreviewUseLightDialogPalette() throws Exception {
        String edit = read(projectRoot().resolve("app/src/leanback/res/layout/dialog_ad_rule_edit.xml"));
        String preview = read(projectRoot().resolve("app/src/leanback/res/layout/dialog_ad_rule_preview.xml"));

        assertFalse("rule editor must not render white labels or input text on its white panel",
                edit.contains("android:textColor=\"@color/white")
                        || edit.contains("android:textColorHint=\"@color/white"));
        assertTrue("rule editor inputs need an explicit light-dialog stroke",
                edit.contains("app:boxStrokeColor=\"@color/dialog_outlined_button_stroke\""));
        assertTrue("rule editor cancel action must expose a TV focus state",
                edit.contains("style=\"@style/Widget.WebHTV.LightDialog.Button.Outlined\""));
        assertTrue("rule editor confirm action must expose a TV focus state",
                edit.contains("app:backgroundTint=\"@color/dialog_primary_button_bg\""));

        assertFalse("rule preview must not render white text on its white panel",
                preview.contains("android:textColor=\"@color/white"));
        assertTrue("rule preview actions must use the light-dialog button palette",
                preview.contains("@color/dialog_primary_button_bg")
                        && preview.contains("Widget.WebHTV.LightDialog.Button.Outlined"));
    }

    @Test
    public void adSettingsCountUserAndConfiguredRules() throws Exception {
        String tv = read(projectRoot().resolve("app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingAdActivity.java"));
        String mobile = read(projectRoot().resolve("app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingAdFragment.java"));
        String tvLayout = read(projectRoot().resolve("app/src/leanback/res/layout/activity_setting_ad.xml"));

        assertTrue(tv.contains("UserAdRuleStore.load().size() + RuleConfig.get().getDefaultRules().size()"));
        assertTrue(mobile.contains("UserAdRuleStore.load().size() + RuleConfig.get().getDefaultRules().size()"));
        assertTrue("rule manager must live on the dedicated ad page",
                tvLayout.contains("@+id/adRuleManage"));
        assertFalse("rule manager must never be hidden on the dedicated ad page",
                tvLayout.contains("android:id=\"@+id/adRuleManage\"\r\n"
                        + "            android:layout_width=\"match_parent\"\r\n"
                        + "            android:layout_height=\"wrap_content\"\r\n"
                        + "            android:layout_marginTop=\"16dp\"\r\n"
                        + "            android:background=\"@drawable/selector_item\"\r\n"
                        + "            android:focusable=\"true\"\r\n"
                        + "            android:focusableInTouchMode=\"true\"\r\n"
                        + "            android:orientation=\"horizontal\"\r\n"
                        + "            android:visibility=\"gone\""));
    }

    @Test
    public void ruleDeletionRequiresLightDialogConfirmation() throws Exception {
        String tv = read(projectRoot().resolve("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/AdRuleManageDialog.java"));
        String mobile = read(projectRoot().resolve("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/AdRuleManageDialog.java"));

        assertDeleteConfirmation(tv, true);
        assertDeleteConfirmation(mobile, false);
    }

    @Test
    public void leanbackRuleRowsKeepCommittedToggleStateAndOpenScrollableDetails() throws Exception {
        String adapter = read(projectRoot().resolve("app/src/leanback/java/com/fongmi/android/tv/ui/adapter/AdRuleAdapter.java"));
        String dialog = read(projectRoot().resolve("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/AdRuleManageDialog.java"));
        String row = read(projectRoot().resolve("app/src/leanback/res/layout/adapter_ad_rule.xml"));
        String detail = read(projectRoot().resolve("app/src/leanback/res/layout/dialog_ad_rule_detail.xml"));

        assertTrue("TV switches must visually keep the persisted value until confirmation succeeds",
                adapter.contains("binding.toggle.setChecked(item.isEnabled());")
                        && adapter.contains("listener.onUserToggleClick(item.getUserRule(), !item.isEnabled())")
                        && adapter.contains("listener.onDefaultToggleClick(item.getDefaultRuleId(), !item.isEnabled())")
                        && adapter.contains("listener.onHlsToggleClick(item.getHlsRule(), !item.isEnabled())"));
        assertTrue("every TV rule type should open the shared detail surface",
                dialog.contains("showUserRuleDetail(item)")
                        && dialog.contains("showDefaultRuleDetail(rule)")
                        && dialog.contains("showHlsRuleDetail(item)"));
        assertTrue("rule details must expose all legacy sections including scripts",
                dialog.contains("rule.getScript()")
                        && dialog.contains("R.string.ad_rule_detail_script"));
        assertTrue("HLS detail must contain the complete serialized rule",
                dialog.contains("item.detail()"));
        assertTrue("long rule content must be readable with a remote",
                detail.contains("androidx.core.widget.NestedScrollView")
                        && detail.contains("android:scrollbars=\"vertical\"")
                        && detail.contains("android:focusable=\"true\""));
        assertTrue("TV detail scroll must hand focus to the confirm button at the bottom",
                dialog.contains("detail.scroll.setNextFocusDownId(positive.getId())")
                        && dialog.contains("positive.setNextFocusUpId(detail.scroll.getId())")
                        && dialog.contains("detail.scroll.canScrollVertically(1)")
                        && dialog.contains("positive.requestFocus()"));
        assertFalse("TV detail must not initially trap focus inside the scroll view",
                dialog.contains("detail.scroll.requestFocus()"));
        assertTrue("TV rule rows should render as one coherent card with a detail affordance",
                row.contains("android:background=\"@drawable/shape_ad_rule_card\"")
                        && row.contains("@string/ad_rule_detail_hint"));
    }

    private static void assertDeleteConfirmation(String source, boolean expectSafeTvFocus) {
        int start = source.indexOf("public void onDeleteClick(UserAdRule item)");
        int end = source.indexOf("\n    @Override", start + 1);
        String handler = source.substring(start, end);

        assertTrue(handler.contains("new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog)"));
        assertTrue(handler.contains(".setPositiveButton(R.string.ad_rule_delete_confirm, (dialog, which) -> deleteUserRule(item))"));
        assertTrue(handler.contains(".setNegativeButton(android.R.string.cancel, null)"));
        assertFalse("delete must only happen after confirmation", handler.contains("UserAdRuleStore.delete"));
        if (expectSafeTvFocus) assertTrue(handler.contains("getButton(DialogInterface.BUTTON_NEGATIVE).requestFocus()"));
        assertTrue(source.contains("private void deleteUserRule(UserAdRule item)")
                && source.contains("UserAdRuleStore.delete(item.getId())"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static Path projectRoot() {
        return Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
    }

    private static int count(String text, String value) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(value, at)) >= 0; at += value.length()) count++;
        return count;
    }
}
