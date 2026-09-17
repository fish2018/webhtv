package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class MobileAdRuleManageDialogTest {

    @Test
    public void switchKeepsPersistedStateUntilConfirmationCompletes() throws Exception {
        String adapter = read("app/src/mobile/java/com/fongmi/android/tv/ui/adapter/AdRuleAdapter.java");
        int method = adapter.indexOf("private void onToggleClick");
        int restore = adapter.indexOf("holder.binding.toggle.setChecked(item.isEnabled());", method);
        int dispatch = adapter.indexOf("listener.on", restore);

        assertTrue("toggle must be restored before the confirmation callback is dispatched", method >= 0 && restore > method && dispatch > restore);
    }

    @Test
    public void everyRuleTypeOpensConcreteDetails() throws Exception {
        String dialog = read("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/AdRuleManageDialog.java");

        assertTrue(dialog.contains("showRuleDetail(item.getName(), item.getSummary(), item.getHosts(), item.getRegex(), item.getExclude(), editAction)"));
        assertTrue(dialog.contains("showRuleDetail(rule.getName(), \"\", rule.getHosts(), rule.getRegex(), rule.getExclude(), null)"));
        assertTrue(dialog.contains("private String getHlsRuleDetail(HlsRuleConfig.Entry item)"));
        assertTrue(dialog.contains("playlistHostRegex") && dialog.contains("minDuration") && dialog.contains("minimumSignals"));
    }

    @Test
    public void legacyFallbackShowsConcreteCriteriaAndRemainsToggleable() throws Exception {
        String dialog = read("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/AdRuleManageDialog.java");
        String adapter = read("app/src/mobile/java/com/fongmi/android/tv/ui/adapter/AdRuleAdapter.java");

        assertTrue(dialog.contains("HlsRuleConfig.LEGACY_FALLBACK_DETAIL"));
        assertTrue(adapter.contains("HlsRuleConfig.LEGACY_FALLBACK_SUMMARY"));
        assertTrue(adapter.contains("listener.onHlsToggleClick"));
    }

    @Test
    public void mobileRuleRowsUseReadableTouchFriendlyCards() throws Exception {
        String layout = read("app/src/mobile/res/layout/adapter_ad_rule.xml");

        assertTrue(layout.contains("com.google.android.material.card.MaterialCardView"));
        assertTrue(layout.contains("app:cardBackgroundColor=\"@color/dialog_outlined_button_bg\""));
        assertTrue(layout.contains("app:strokeColor=\"@color/dialog_outlined_button_stroke\""));
        assertTrue(layout.contains("android:minHeight=\"64dp\""));
        assertTrue(layout.contains("android:contentDescription=\"@string/ad_rule_delete_confirm\""));
    }

    @Test
    public void candidateImportOffersBatchSelectionAndExplicitActivation() throws Exception {
        String dialog = read("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/AdRuleManageDialog.java");
        String layout = read("app/src/mobile/res/layout/dialog_ad_rule_manage.xml");

        assertTrue(dialog.contains("R.string.ad_rule_select_low_risk")
                && dialog.contains("R.string.ad_rule_select_all")
                && dialog.contains("R.string.ad_rule_invert_selection"));
        assertTrue(dialog.contains("importSelectedCandidates(candidates, selected, true)")
                && dialog.contains("importSelectedCandidates(candidates, selected, false)"));
        assertTrue(dialog.contains("ImportedAdRuleCandidateStore.ignoreCandidates(ids)")
                && dialog.contains(".setNegativeButton(android.R.string.cancel, null)"));
        assertTrue(layout.contains("android:id=\"@+id/importBulkActions\"")
                && layout.contains("android:id=\"@+id/enableImported\"")
                && layout.contains("android:id=\"@+id/disableImported\""));
    }

    @Test
    public void committedToggleRefreshesOnlyItsCurrentRow() throws Exception {
        String dialog = read("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/AdRuleManageDialog.java");
        String adapter = read("app/src/mobile/java/com/fongmi/android/tv/ui/adapter/AdRuleAdapter.java");

        assertTrue(dialog.contains("adapter.refreshUserEnabled(item)")
                && dialog.contains("adapter.refreshDefaultEnabled(ruleId, enabled)")
                && dialog.contains("adapter.refreshHlsEnabled(item.key(), enabled)"));
        assertTrue(adapter.contains("notifyItemChanged(i, enabled)")
                && adapter.contains("List<Object> payloads"));
    }

    private static String read(String file) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return Files.readString(root.resolve(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
