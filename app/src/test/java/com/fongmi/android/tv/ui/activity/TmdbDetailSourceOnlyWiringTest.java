package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbDetailSourceOnlyWiringTest {

    @Test
    public void standaloneRoutesUseConfiguredModeInsteadOfKeyReadiness() throws Exception {
        String mobile = read("mobile", Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = read("leanback", Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));

        for (String source : new String[]{mobile, leanback}) {
            String method = source.substring(source.indexOf("private static boolean shouldOpenLegacyTmdbDetail"),
                    source.indexOf("public static void startDirectResolved", source.indexOf("private static boolean shouldOpenLegacyTmdbDetail")));
            assertTrue(method.contains("Setting.isTmdbDetailModeConfigured()"));
            assertFalse(method.contains("Setting.isTmdbDetailPage()"));
        }
    }

    @Test
    public void loadContentResolvesRuntimePolicyBeforeCreatingNetworkTasks() throws Exception {
        String source = readMainActivity();
        String load = method(source, "private void loadContent(@Nullable TmdbBundle reusableBundle)", "private boolean shouldLoadInitialStandaloneTmdbDetailInSinglePass");

        int classify = load.indexOf("TmdbSourceAvailability.classify(finalVod, sourcePayload, sourceBundle)");
        int resolve = load.indexOf("DetailRuntimeModePolicy.resolve(new DetailRuntimeModePolicy.Input");
        int direct = load.indexOf("decision.runtimeMode() == Setting.DETAIL_OPEN_DIRECT");
        int sourceOnly = load.indexOf("if (!decision.networkAllowed()) return;");
        int future = load.indexOf("Future<TmdbLoadResult> tmdbFuture = decision.networkAllowed()");

        assertTrue("source state must be classified from normalized payload", classify >= 0 && resolve > classify);
        assertTrue("direct/source-only decisions must precede task creation", direct > resolve && sourceOnly > direct && future > sourceOnly);
    }

    @Test
    public void sourceOnlyModeBlocksTmdbNetworkAndCacheWrites() throws Exception {
        String source = readMainActivity();

        assertTrue(source.contains("private boolean isTmdbNetworkAllowed()"));
        assertTrue(source.contains("if (bundle != null && isTmdbNetworkAllowed()) saveTmdbMatch(bundle.item());"));
        assertTrue(source.contains("if (isTmdbNetworkAllowed()) loadTmdbMediaBlocks(bundle);"));
        assertTrue(source.contains("if (!isTmdbNetworkAllowed()) return;"));
        String relatedVideos = method(source, "private void loadRelatedVideosForCurrentContext", "private void bindTmdbSection");
        assertTrue(relatedVideos.contains("if (isTmdbSourceOnly()) {"));
        assertTrue(relatedVideos.contains("TmdbSourceAdapter.videos("));
        assertTrue(relatedVideos.contains("if (!isTmdbNetworkAllowed() || !tmdbConfig.isReady()) return;"));
        assertTrue(source.contains("if (isTmdbNetworkAllowed()) {") || source.contains("if (!isTmdbNetworkAllowed()) {"));
    }

    @Test
    public void directFallbackReusesLoadedVodWithAnExplicitRuntimeMode() throws Exception {
        String detail = readMainActivity();
        String fallback = method(detail, "private void fallbackToOriginalDetail", "private boolean isTmdbNetworkAllowed");
        assertTrue(fallback.contains("VideoActivity.startDirectResolved(this, loadedVod);"));
        assertTrue(fallback.contains("finish();"));

        for (String flavor : new String[]{"mobile", "leanback"}) {
            String source = read(flavor, Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
            String start = method(source, "public static void startDirectResolved", "public static void start(Activity activity, String url)");
            assertTrue(start.contains("EXTRA_DETAIL_RUNTIME_MODE, Setting.DETAIL_OPEN_DIRECT"));
            assertTrue(start.contains("putDetailVodCache(intent, vod);"));
            assertTrue(start.contains("intent.putExtra(\"collect\", false);"));
            assertFalse(start.contains("auto_play"));
        }
    }

    private static String readMainActivity() throws Exception {
        return readMain(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue("missing method " + startMarker, start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static String readMain(Path relative) throws Exception {
        return Files.readString(root().resolve("app").resolve("src").resolve("main").resolve("java").resolve(relative), StandardCharsets.UTF_8);
    }

    private static String read(String flavor, Path relative) throws Exception {
        return Files.readString(root().resolve("app").resolve("src").resolve(flavor).resolve("java").resolve(relative), StandardCharsets.UTF_8);
    }

    private static Path root() {
        return Files.exists(Path.of("app", "src", "main")) ? Path.of(".") : Path.of("..");
    }
}
