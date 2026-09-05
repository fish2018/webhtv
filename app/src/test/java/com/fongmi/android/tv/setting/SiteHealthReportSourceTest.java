package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SiteHealthReportSourceTest {

    @Test
    public void siteHealthStoreExposesFourStageReportWithoutChangingSortScore() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "setting", "SiteHealthStore.java")));

        assertTrue(source.contains("public static void recordSearch"));
        assertTrue(source.contains("public static void recordDetail"));
        assertTrue(source.contains("public static void recordParse"));
        assertTrue(source.contains("public static void recordPlay"));
        assertTrue(source.contains("public static void clear(String siteKey)"));
        assertTrue(source.contains("public static Report report()"));
        assertTrue(source.contains("public static class Summary"));
        assertTrue(source.contains("public static class Row"));
        assertTrue(source.contains("public static class Stage"));
        assertTrue(source.contains("lastFailAt"));
        assertTrue(source.contains("searchReasons"));
        assertTrue(source.contains("detailReasons"));
        assertTrue(source.contains("parseReasons"));
        assertTrue(source.contains("playReasons"));

        String score = methodBody(source, "private double score()");
        assertFalse("Sort score should not depend on parse metrics in the report-only slice", score.contains("parseSuccess"));
        assertFalse("Sort score should not depend on parse metrics in the report-only slice", score.contains("parseFail"));
    }

    @Test
    public void playerManagerRecordsParseHealthForSuccessAndFailure() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "player", "PlayerManager.java")));

        assertTrue(source.contains("import com.fongmi.android.tv.setting.SiteHealthStore;"));
        assertTrue(source.contains("private long parseHealthStartedAt;"));
        assertTrue(source.contains("private boolean parseHealthRecorded;"));
        assertTrue(methodBody(source, "public void onParseSuccess").contains("recordParseHealth(true"));
        assertTrue(methodBody(source, "public void onParseError").contains("recordParseHealth(false"));
        assertTrue(methodBody(source, "private void recordParseHealth").contains("SiteHealthStore.recordParse"));
    }

    @Test
    public void siteHealthDialogProvidesReportEntry() throws Exception {
        String dialogSource = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "SiteHealthDialog.java")));
        String reportSource = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "SiteHealthReportDialog.java")));
        String dialogLayout = read(mainResPath().resolve(Path.of("layout", "dialog_site_health.xml")));
        String reportLayout = read(mainResPath().resolve(Path.of("layout", "dialog_site_health_report.xml")));
        String strings = read(mainResPath().resolve(Path.of("values", "strings.xml")));

        assertTrue(dialogSource.contains("SiteHealthReportDialog.show"));
        assertTrue(reportSource.contains("private Filter filter"));
        assertTrue(reportSource.contains("private Sort sort"));
        assertTrue(reportSource.contains("binding.filterAll.setOnClickListener"));
        assertTrue(reportSource.contains("binding.sortFailures.setOnClickListener"));
        assertTrue(reportSource.contains("binding.sortRecent.setOnClickListener"));
        assertTrue(reportSource.contains("binding.sortRate.setOnClickListener"));
        assertTrue(reportSource.contains("binding.sortSamples.setOnClickListener"));
        assertTrue(reportSource.contains("root.setOnClickListener"));
        assertTrue(reportSource.contains("reasonLabel("));
        assertTrue(reportSource.contains("recentErrors("));
        assertTrue(reportSource.contains("confirmClearSite("));
        assertTrue(reportSource.contains("SiteHealthStore.clear(row.siteKey)"));
        assertTrue(dialogLayout.contains("@+id/report"));
        assertTrue(dialogLayout.contains("@string/site_health_report_view"));
        assertTrue(reportLayout.contains("@+id/filterAll"));
        assertTrue(reportLayout.contains("@+id/filterBad"));
        assertTrue(reportLayout.contains("@+id/filterWarn"));
        assertTrue(reportLayout.contains("@+id/sortFailures"));
        assertTrue(reportLayout.contains("@+id/sortRecent"));
        assertTrue(reportLayout.contains("@+id/sortRate"));
        assertTrue(reportLayout.contains("@+id/sortSamples"));
        assertTrue(reportLayout.contains("@+id/rows"));
        assertTrue(strings.contains("name=\"site_health_report_title\""));
        assertTrue(strings.contains("name=\"site_health_filter_all\""));
        assertTrue(strings.contains("name=\"site_health_sort_failures\""));
        assertTrue(strings.contains("name=\"site_health_report_recent_errors\""));
        assertTrue(strings.contains("name=\"site_health_clear_site\""));
        assertTrue(strings.contains("name=\"site_health_reason_timeout\""));
        assertTrue(strings.contains("name=\"site_health_stage_parse\""));
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(signature + " is missing", start >= 0);
        int brace = source.indexOf('{', start);
        assertTrue(signature + " has no body", brace >= 0);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') depth--;
            if (depth == 0) return source.substring(brace, i + 1);
        }
        throw new AssertionError(signature + " is not closed");
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path mainJavaPath() {
        return sourcePath("main", "java");
    }

    private static Path mainResPath() {
        return sourcePath("main", "res");
    }

    private static Path sourcePath(String sourceSet, String kind) {
        Path moduleRelative = Path.of("src", sourceSet, kind);
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", sourceSet, kind);
    }
}
