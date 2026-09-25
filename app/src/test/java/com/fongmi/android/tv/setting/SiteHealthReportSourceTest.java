package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SiteHealthReportSourceTest {

    @Test
    public void siteHealthStoreExposesFourStageReportWithoutChangingSortScore() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "setting", "SiteHealthStore.java")));

        assertTrue(source.contains("public static void recordHome"));
        assertTrue(source.contains("public static void recordCategory"));
        String api = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "api", "SiteApi.java")));
        assertTrue(api.contains("SiteHealthStore.recordHome(site, true"));
        assertTrue(api.contains("SiteHealthStore.recordHome(site, false"));
        assertTrue(api.contains("SiteHealthStore.recordCategory(key, true"));
        assertTrue(api.contains("SiteHealthStore.recordCategory(key, false"));
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
        assertTrue(source.contains("AdBlockStatsStore.getStats()"));
        assertTrue(source.contains("public final long adBlockedTotal"));
        assertTrue(source.contains("public final Map<String, Long> adBlockedByPipeline"));

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
        assertTrue(reportSource.contains("binding.clearAll.setOnClickListener"));
        assertTrue(reportSource.contains("root.setOnClickListener"));
        assertTrue(reportSource.contains("reasonLabel("));
        assertTrue(reportSource.contains("recentErrors("));
        assertTrue(reportSource.contains("confirmClearSite("));
        assertTrue(reportSource.contains("SiteHealthStore.clear(row.siteKey)"));
        assertTrue(reportSource.contains("confirmClearAll()"));
        assertTrue(reportSource.contains("SiteHealthStore.clear()"));
        String m3u8Source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "server", "process", "M3u8.java")));
        String mpvProxySource = read(mainJavaPath().resolve(Path.of("androidx", "media3", "mpvplayer", "MpvHlsProxy.java")));
        assertTrue(m3u8Source.contains("AdBlockStatsStore.recordBlocks("));
        assertTrue(m3u8Source.contains("clean.ruleCounts()"));
        assertTrue(m3u8Source.contains("HlsAdblockNotice.shouldNotify("));
        assertTrue(mpvProxySource.contains("AdBlockStatsStore.recordBlocks("));
        assertTrue(mpvProxySource.contains("HlsAdblockNotice.shouldNotify("));
        assertTrue(mpvProxySource.contains("Notify.show("));
        assertTrue(m3u8Source.contains("Notify.show("));
        String statsStoreSource = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "api", "config", "AdBlockStatsStore.java")));
        assertTrue(statsStoreSource.contains("HlsRuleConfig.getEntries()"));
        assertTrue(statsStoreSource.contains("hls.legacy-fallback"));
        assertTrue(statsStoreSource.contains("内置兜底规则"));
        String clearConfirmationBody = methodBody(reportSource, "private void showClearConfirmation(");
        assertTrue(clearConfirmationBody.contains("R.style.Theme_WebHTV_LightDialog"));
        assertTrue(clearConfirmationBody.contains("LightDialog.apply(dialog)"));
        String refreshBody = methodBody(reportSource, "private void refreshReport()");
        assertTrue(refreshBody.indexOf("report = SiteHealthStore.report()") < refreshBody.indexOf("render()"));
        assertTrue(methodBody(reportSource, "private void confirmClearSite(").contains("binding.root.post(this::refreshReport)"));
        assertTrue(methodBody(reportSource, "private void confirmClearAll()").contains("binding.root.post(this::refreshReport)"));
        assertTrue(dialogLayout.contains("@+id/report"));
        assertTrue(dialogLayout.contains("@string/site_health_report_view"));
        assertTrue(reportLayout.contains("@+id/search"));
        assertTrue(reportLayout.contains("@string/site_health_report_search_hint"));
        assertTrue(reportSource.contains("binding.search.addTextChangedListener"));
        assertTrue(reportSource.contains("row.siteName.toLowerCase(Locale.ROOT).contains(query)"));
        assertTrue(reportSource.contains("R.string.site_health_stage_home, row.home"));
        assertTrue(reportSource.contains("R.string.site_health_stage_category, row.category"));
        assertTrue(reportSource.contains("row.home.lastFailAt"));
        assertTrue(reportSource.contains("row.category.lastFailAt"));
        assertTrue(reportLayout.contains("@+id/filterAll"));
        assertTrue(reportLayout.contains("@+id/filterBad"));
        assertTrue(reportLayout.contains("@+id/filterWarn"));
        assertTrue(reportLayout.contains("@+id/sortFailures"));
        assertTrue(reportLayout.contains("@+id/sortRecent"));
        assertTrue(reportLayout.contains("@+id/sortRate"));
        assertTrue(reportLayout.contains("@+id/sortSamples"));
        assertTrue(reportLayout.contains("@+id/clearAll"));
        assertTrue(reportLayout.contains("@+id/rows"));
        assertTrue(strings.contains("name=\"site_health_report_title\""));
        assertTrue(strings.contains("name=\"site_health_filter_all\""));
        assertTrue(strings.contains("name=\"site_health_sort_failures\""));
        assertTrue(strings.contains("name=\"site_health_report_recent_errors\""));
        assertTrue(strings.contains("name=\"site_health_clear_site\""));
        assertTrue(strings.contains("name=\"site_health_clear_all\""));
        assertTrue(strings.contains("name=\"site_health_clear_all_title\""));
        assertTrue(strings.contains("name=\"site_health_clear_all_message\""));
        assertTrue(strings.contains("name=\"site_health_reason_timeout\""));
        assertTrue(strings.contains("name=\"site_health_stage_parse\""));
    }

    @Test
    public void healthReportUsesAdStatsStyleNearFullScreenSizing() throws Exception {
        String dialog = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "SiteHealthReportDialog.java")));

        assertTrue(dialog.contains("int margin = ResUtil.dp2px(ResUtil.isLand(requireContext()) ? 24 : 16);"));
        assertTrue(dialog.contains("ResUtil.getScreenWidth(requireContext()) - margin * 2"));
        assertTrue(dialog.contains("ResUtil.getScreenHeight(requireContext()) - margin * 2"));
        assertTrue(dialog.contains("window.getDecorView().setPadding(0, 0, 0, 0)"));
        assertTrue(!dialog.contains("ResUtil.getScreenWidth(requireContext()) * (ResUtil.isLand(requireContext()) ? 0.62f : 0.94f)"));
        assertTrue(!dialog.contains("ResUtil.getScreenHeight(requireContext()) * (ResUtil.isLand(requireContext()) ? 0.78f : 0.82f)"));
    }

    @Test
    public void healthReportExposesSiteRuleAndPipelineAdDimensions() throws Exception {
        String store = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "setting", "SiteHealthStore.java")));
        String dialog = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "SiteHealthReportDialog.java")));

        assertTrue(store.contains("public final Map<String, Long> adBlockedBySite;"));
        assertTrue(store.contains("public final Map<String, Long> adBlockedByRule;"));
        assertTrue(store.contains("public final Map<String, Long> adBlockedByPipeline;"));
        assertTrue(dialog.contains("R.string.ad_site_rank"));
        assertTrue(dialog.contains("R.string.ad_rule_rank"));
        assertTrue(dialog.contains("R.string.ad_pipeline_rank"));
        assertTrue(dialog.contains("int playCount = row.playAttempts"));
        assertTrue(dialog.contains("site_health_report_ad_blocked, playCount, blocked"));
    }

    @Test
    public void playbackActivityBridgesExoFirstFrameToGenericFirstFrameHook() throws Exception {
        String playback = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "PlaybackActivity.java")));
        String exoFirstFrame = methodBody(playback, "public void onExoFirstFrame()");

        assertTrue(exoFirstFrame.contains("PlaybackActivity.this.onExoFirstFrame()"));
        assertTrue(exoFirstFrame.contains("PlaybackActivity.this.onFirstFrameRendered()"));
    }

    @Test
    public void playbackHealthCountsSuccessOnlyAfterFirstFrame() throws Exception {
        String leanback = read(sourcePath("leanback", "java").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String mobile = read(sourcePath("mobile", "java").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));

        for (String source : new String[] {leanback, mobile}) {
            String firstFrame = methodBody(source, "protected void onFirstFrameRendered()");
            String stateChanged = methodBody(source, "protected void onStateChanged(int state)");
            assertTrue(firstFrame.contains("recordPlayHealth(true, \"\")"));
            assertFalse(stateChanged.contains("recordPlayHealth(true, \"\")"));
        }
    }

    @Test
    public void playbackHealthRecordsTerminalErrorsAndReloadFailures() throws Exception {
        String leanback = read(sourcePath("leanback", "java").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String mobile = read(sourcePath("mobile", "java").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));

        for (String source : new String[] {leanback, mobile}) {
            String error = methodBody(source, "protected void onError(String msg)");
            String reload = methodBody(source, "protected void onReload(String msg)");
            assertTrue(error.contains("recordPlayHealth(false, msg)"));
            assertTrue(reload.contains("recordPlayHealth(false, msg)"));
        }
    }

    @Test
    public void terminalPlayerFailureReachesHostAfterFallbacksAreExhausted() throws Exception {
        String player = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "player", "PlayerManager.java")));
        String onPlayerError = methodBody(player, "public void onPlayerError(@NonNull PlaybackException e)");

        int fallback = onPlayerError.indexOf("if (fallbackPlayback(e)) return;");
        int terminalCallback = onPlayerError.indexOf("callback.onError(getPlaybackErrorMessage(failure))", fallback);
        assertTrue("fallback handling is missing", fallback >= 0);
        assertTrue("terminal errors must reach the host after fallback exhaustion", terminalCallback > fallback);
    }

    @Test
    public void playbackAttemptsUseIndependentCounterForAdBlockRatio() throws Exception {
        String store = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "setting", "SiteHealthStore.java")));
        String dialog = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "SiteHealthReportDialog.java")));
        String leanback = read(sourcePath("leanback", "java").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String mobile = read(sourcePath("mobile", "java").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));

        assertTrue(store.contains("public static void recordPlayAttempt(String key)"));
        assertTrue(store.contains("public final int playAttempts"));
        assertTrue(dialog.contains("int playCount = row.playAttempts"));
        for (String source : new String[] {leanback, mobile}) {
            String begin = methodBody(source, "private void beginPlayHealth()");
            assertTrue(begin.contains("SiteHealthStore.recordPlayAttempt(playHealthKey)"));
            assertEquals(1, count(begin, "recordPlayAttempt("));
        }
    }

    @Test
    public void tmdbDetailInlinePlaybackRecordsAttemptsAndTerminalHealth() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")));
        String start = methodBody(source, "private void startInlinePlayer(Result result, long resumePosition)");
        String firstFrame = methodBody(source, "protected void onFirstFrameRendered()");
        String error = methodBody(source, "protected void onError(String msg)");
        String recorder = methodBody(source, "private void recordInlinePlayHealth(boolean success, String error)");

        assertTrue(start.contains("SiteHealthStore.recordPlayAttempt(inlinePlayHealthKey)"));
        assertEquals(1, count(start, "recordPlayAttempt("));
        assertTrue(firstFrame.contains("recordInlinePlayHealth(true, \"\")"));
        assertTrue(error.contains("recordInlinePlayHealth(false, msg)"));
        assertTrue(recorder.contains("if (inlinePlayHealthRecorded) return"));
        assertTrue(recorder.contains("SiteHealthStore.recordPlay("));
    }

    private static int count(String source, String needle) {
        int count = 0;
        for (int offset = 0; (offset = source.indexOf(needle, offset)) >= 0; offset += needle.length()) count++;
        return count;
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
