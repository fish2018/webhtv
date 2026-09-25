package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class AdBlockStatsDialogLayoutTest {

    @Test
    public void statsDialogUsesNearFullScreenTabbedRoot() throws Exception {
        for (String flavor : new String[] {"mobile", "leanback"}) {
            String layout = read(findRepositoryRoot().resolve(Path.of("app", "src", flavor, "res", "layout", "dialog_ad_block_stats.xml")));
            int rootStart = layout.indexOf("<androidx.appcompat.widget.LinearLayoutCompat");
            String root = layout.substring(rootStart, layout.indexOf('>', rootStart) + 1);

            assertTrue(flavor + " statistics dialog should use the available height",
                    root.contains("android:layout_height=\"match_parent\""));
            assertTrue(layout.contains("android:id=\"@+id/statsTabs\""));
            assertTrue(layout.contains("android:id=\"@+id/overviewPage\""));
            assertTrue(layout.contains("android:id=\"@+id/sitePage\""));
            assertTrue(layout.contains("android:id=\"@+id/rulePage\""));
            assertTrue(layout.contains("android:id=\"@+id/pipelinePage\""));
        }
    }

    @Test
    public void statsDialogUsesFixedSafetyMarginsInsteadOfScreenPercentages() throws Exception {
        Path root = findRepositoryRoot();
        String mobile = read(root.resolve(Path.of("app", "src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "AdBlockStatsDialog.java")));
        String leanback = read(root.resolve(Path.of("app", "src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "AdBlockStatsDialog.java")));

        assertTrue(mobile.contains("metrics.widthPixels - horizontalMargin * 2"));
        assertTrue(mobile.contains("metrics.heightPixels - verticalMargin * 2"));
        assertTrue(mobile.contains("window.getDecorView().setPadding(0, 0, 0, 0)"));
        assertTrue(mobile.contains("params.height = height"));
        assertTrue(mobile.contains("binding.getRoot().setMinimumHeight(height)"));
        assertTrue(leanback.contains("ResUtil.getScreenWidth(activity) - horizontalMargin * 2"));
        assertTrue(leanback.contains("binding.getRoot().setMinimumHeight(height)"));
        assertTrue(leanback.contains("ResUtil.getScreenHeight(activity) - verticalMargin * 2"));
        assertTrue(!mobile.contains("metrics.widthPixels * 0.94f"));
        assertTrue(!mobile.contains("metrics.heightPixels * 0.92f"));
        assertTrue(!leanback.contains("ResUtil.getScreenWidth(activity) * 0.94f"));
        assertTrue(!leanback.contains("ResUtil.getScreenHeight(activity) * 0.92f"));
    }

    @Test
    public void statsDialogKeepsAllThreeCoreMetrics() throws Exception {
        for (String flavor : new String[] {"mobile", "leanback"}) {
            String layout = read(findRepositoryRoot().resolve(Path.of("app", "src", flavor, "res", "layout", "dialog_ad_block_stats.xml")));

            assertTrue(layout.contains("android:id=\"@+id/totalBlocked\""));
            assertTrue(layout.contains("android:id=\"@+id/aiFeedbackCount\""));
            assertTrue(layout.contains("android:id=\"@+id/aiSuccessRate\""));
        }
    }

    @Test
    public void statsDialogDisablesOverscrollAndRemovesGrayCards() throws Exception {
        for (String flavor : new String[] {"mobile", "leanback"}) {
            String layout = read(findRepositoryRoot().resolve(Path.of("app", "src", flavor, "res", "layout", "dialog_ad_block_stats.xml")));

            assertTrue(layout.contains("android:overScrollMode=\"never\""));
            assertTrue(!layout.contains("MaterialCardView"));
            assertTrue(!layout.contains("app:cardBackgroundColor=\"@color/black_10\""));
        }
    }

    @Test
    public void statsDialogIncludesSiteDimensionOverview() throws Exception {
        for (String flavor : new String[] {"mobile", "leanback"}) {
            String layout = read(findRepositoryRoot().resolve(Path.of("app", "src", flavor, "res", "layout", "dialog_ad_block_stats.xml")));

            assertTrue(layout.contains("android:id=\"@+id/siteCount\""));
            assertTrue(layout.contains("android:id=\"@+id/topSite\""));
            assertTrue(layout.contains("android:id=\"@+id/topSiteShare\""));
        }
    }

    @Test
    public void statsDialogKeepsAllThreeRankingDimensionsInTheScrollableContent() throws Exception {
        for (String flavor : new String[] {"mobile", "leanback"}) {
            String layout = read(findRepositoryRoot().resolve(Path.of("app", "src", flavor, "res", "layout", "dialog_ad_block_stats.xml")));

            assertTrue(layout.contains("android:id=\"@+id/siteRankRecycler\""));
            assertTrue(layout.contains("android:id=\"@+id/ruleRankRecycler\""));
            assertTrue(layout.contains("android:id=\"@+id/pipelineRankRecycler\""));
        }
    }

    @Test
    public void statsDialogIncludesDetailedLogAndChartTabsForBothFlavors() throws Exception {
        Path root = findRepositoryRoot();
        for (String flavor : new String[] {"mobile", "leanback"}) {
            String layout = read(root.resolve(Path.of("app", "src", flavor, "res", "layout", "dialog_ad_block_stats.xml")));
            String dialog = read(root.resolve(Path.of("app", "src", flavor, "java", "com", "fongmi", "android", "tv", "ui", "dialog", "AdBlockStatsDialog.java")));

            assertTrue(layout.contains("android:id=\"@+id/logPage\""));
            assertTrue(layout.contains("android:id=\"@+id/logRecycler\""));
            assertTrue(layout.contains("android:id=\"@+id/chartPage\""));
            assertTrue(layout.contains("android:id=\"@+id/chartView\""));
            assertTrue(layout.contains("com.fongmi.android.tv.widget.AdBlockChartView"));
            assertTrue(dialog.contains("binding.chartView.setEntries"));
            assertTrue(dialog.contains("R.string.ad_stats_log"));
            assertTrue(dialog.contains("R.string.ad_stats_chart"));
            assertTrue(dialog.contains("binding.logPage.setVisibility"));
            assertTrue(dialog.contains("binding.chartPage.setVisibility"));
        }
    }

    @Test
    public void blockLogUsesARealTableWithEveryRequestedFieldForBothFlavors() throws Exception {
        Path root = findRepositoryRoot();
        for (String flavor : new String[] {"mobile", "leanback"}) {
            String dialogLayout = read(root.resolve(Path.of("app", "src", flavor, "res", "layout", "dialog_ad_block_stats.xml")));
            String rowLayout = read(root.resolve(Path.of("app", "src", flavor, "res", "layout", "adapter_ad_block_log.xml")));
            String dialogSource = read(root.resolve(Path.of("app", "src", flavor, "java", "com", "fongmi", "android", "tv", "ui", "dialog", "AdBlockStatsDialog.java")));

            assertTrue(dialogLayout.contains("android:id=\"@+id/logTableScroll\""));
            assertTrue(dialogLayout.contains("@string/ad_log_site_name"));
            assertTrue(dialogLayout.contains("@string/ad_log_site_domain"));
            assertTrue(dialogLayout.contains("@string/ad_log_rule_domain"));
            assertTrue(dialogLayout.contains("@string/ad_log_blocked_at"));
            assertTrue(dialogLayout.contains("@string/ad_log_segment_start"));
            assertTrue(dialogLayout.contains("@string/ad_log_segment_end"));
            assertTrue(dialogLayout.contains("@string/ad_log_segment_duration"));
            assertTrue(dialogLayout.contains("android:id=\"@+id/logRecycler\""));
            assertTrue(dialogLayout.contains("android:layout_width=\"1660dp\""));
            assertTrue(rowLayout.contains("android:id=\"@+id/siteName\"") && rowLayout.contains("android:textStyle=\"bold\""));
            assertTrue(rowLayout.contains("android:id=\"@+id/siteDomain\""));
            assertTrue(rowLayout.contains("android:id=\"@+id/ruleDomain\""));
            assertTrue(rowLayout.contains("android:id=\"@+id/blockedAt\""));
            assertTrue(rowLayout.contains("android:id=\"@+id/segmentStart\""));
            assertTrue(rowLayout.contains("android:id=\"@+id/segmentEnd\""));
            assertTrue(rowLayout.contains("android:id=\"@+id/segmentDuration\""));
            assertTrue(dialogSource.contains("AdapterAdBlockLogBinding.inflate"));
            assertTrue(dialogSource.contains("getSegmentEndSeconds()"));
            assertTrue(dialogSource.contains("segmentDuration.setText(item.hasSegmentTiming()"));
        }
    }

    @Test
    public void rankingTabsSupportExpandingGroupedBlockLogsForBothFlavors() throws Exception {
        Path root = findRepositoryRoot();
        for (String flavor : new String[] {"mobile", "leanback"}) {
            String dialog = read(root.resolve(Path.of("app", "src", flavor, "java", "com", "fongmi", "android", "tv", "ui", "dialog", "AdBlockStatsDialog.java")));

            assertTrue(dialog.contains("class ExpandableLogAdapter"));
            assertTrue(dialog.contains("getBlockLogsBySource"));
            assertTrue(dialog.contains("getBlockLogsBySite"));
            assertTrue(dialog.contains("getBlockLogsByRule"));
            assertTrue(dialog.contains("getBlockLogsByPipeline"));
            assertTrue(dialog.contains("itemView.setOnClickListener"));
        }
    }

    @Test
    public void mobileStatsDialogUsesReadableTextOnWhiteSurface() throws Exception {
        Path root = findRepositoryRoot();
        String dialog = read(root.resolve(Path.of("app", "src", "mobile", "res", "layout", "dialog_ad_block_stats.xml")));
        String item = read(root.resolve(Path.of("app", "src", "mobile", "res", "layout", "adapter_ad_stats_item.xml")));

        assertTrue("White dialog surface must not use white primary text",
                !dialog.contains("android:textColor=\"@color/white\"")
                        && !item.contains("android:textColor=\"@color/white\""));
        assertTrue("White dialog surface must not use translucent white secondary text",
                !dialog.contains("android:textColor=\"@color/white_50\"")
                        && !item.contains("android:textColor=\"@color/white_50\""));
    }

    @Test
    public void simplifiedChineseBlockLogUsesTheRequestedFieldLabels() throws Exception {
        String strings = read(findRepositoryRoot().resolve(Path.of("app", "src", "main", "res", "values-zh-rCN", "strings.xml")));

        assertTrue(strings.contains(">切片开始时间</string>"));
        assertTrue(strings.contains(">切片结束时间</string>"));
        assertTrue(strings.contains(">切片时长</string>"));
    }

    @Test
    public void simplifiedChineseStatsDialogLocalizesPipelineRankingTitle() throws Exception {
        String strings = read(findRepositoryRoot().resolve(Path.of("app", "src", "main", "res", "values-zh-rCN", "strings.xml")));

        assertTrue("The pipeline ranking title should be localized for the Chinese TV/mobile UI",
                strings.contains("<string name=\"ad_pipeline_rank\">播放链路</string>"));
    }

    @Test
    public void touchFilterOptionsDoNotConsumeTheFirstTapForFocus() throws Exception {
        String dialog = read(findRepositoryRoot().resolve(Path.of("app", "src", "main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "AdBlockLogFilterDialog.java")));

        assertTrue(dialog.contains("Configuration.TOUCHSCREEN_NOTOUCH"));
        assertTrue(dialog.contains("checkBox.setFocusable(!touchDevice)"));
        assertTrue(dialog.contains("checkBox.setFocusableInTouchMode(!touchDevice)"));
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve(Path.of("app", "src", "mobile", "res", "layout", "dialog_ad_block_stats.xml")))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root not found");
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
