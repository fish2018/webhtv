package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/** Keeps the ad-rule ranking user-facing by resolving stored rule IDs to display names. */
public class SiteHealthReportDialogSourceTest {

    @Test
    public void ruleRankingUsesResolvedDisplayNames() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/fongmi/android/tv/ui/dialog/SiteHealthReportDialog.java"));

        assertTrue(source.contains("ruleDimensionSummary(report.adBlockedByRule)"));
        assertTrue(source.contains("AdBlockStatsStore.getRuleDisplayName(entry.getKey())"));
        assertFalse(source.contains("dimensionSummary(report.adBlockedByRule)"));
    }
}
