package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VideoActivityRuntimeModeWiringTest {

    @Test
    public void mobileAndLeanbackInitializeRuntimeModeBeforeAdapterSetup() throws Exception {
        for (String flavor : new String[]{"mobile", "leanback"}) {
            String source = read(flavor);
            int init = source.indexOf("initializeRuntimeDetailMode();");
            int adapter = source.indexOf("initTmdbMode();", init);
            assertTrue(flavor + " must initialize runtime mode before adapter setup", init >= 0 && adapter > init);
            if (flavor.equals("leanback")) {
                int prepare = source.indexOf("prepareInitialDetailShell();", init);
                assertTrue("leanback must initialize runtime mode before shell setup", prepare > init);
            }
            assertTrue(source.contains("private int runtimeDetailMode = Setting.getDetailOpenMode();"));
            assertTrue(source.contains("private boolean isRuntimeFusionMode()"));
            assertTrue(source.contains("private boolean isRuntimeOriginalEnhancedMode()"));
            assertTrue(source.contains("private boolean isRuntimeDirectMode()"));
        }
    }

    @Test
    public void detailLoadUsesRuntimePolicyAndLoadsSourceWithoutNetwork() throws Exception {
        String adapter = Files.readString(root().resolve(Path.of("app", "src", "main", "java", "com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java")), StandardCharsets.UTF_8);
        String loadSource = method(adapter, "public void loadSource(TmdbBundle bundle", "private void applySourceBundle");
        String applySource = method(adapter, "private void applySourceBundle", "private void fillInitialSourceGaps");

        assertTrue(loadSource.contains("resetLoadState();"));
        assertTrue(loadSource.contains("sourceOnly = !tmdbConfig.isReady();"));
        assertTrue(applySource.contains("enrichVod(sourceVod, tmdbItem, tmdbDetail);"));
        assertTrue(loadSource.contains("notifyVodChanged(sourceVod, generation, RefreshEvent.Type.VOD_CORE);"));
        assertFalse("source-only load must not call the network service", loadSource.contains("tmdbService."));

        for (String flavor : new String[]{"mobile", "leanback"}) {
            String source = read(flavor);
            String setDetail = method(source, "private void setDetail(Vod item)", flavor.equals("mobile")
                    ? "private void setText(Vod item)" : "private boolean shouldLoadTmdbDetail()");
            assertTrue(flavor + " must resolve runtime policy from normalized source data",
                    setDetail.contains("DetailRuntimeModePolicy.resolve(new DetailRuntimeModePolicy.Input("));
            assertTrue(setDetail.contains("runtimeSourceOnly = decision.sourceOnly();"));
            assertTrue(setDetail.contains("mTmdbUIAdapter.loadSource(sourceBundle, item, sourcePayload);"));
            assertTrue(setDetail.contains("sourceState == TmdbSourceState.RENDERABLE && sourceBundle != null"));
        }
    }

    @Test
    public void directRuntimeModeKeepsNativeEpisodeAndShellSemantics() throws Exception {
        for (String flavor : new String[]{"mobile", "leanback"}) {
            String source = read(flavor);
            assertFalse(source.contains("Setting.isFusionDetailPage()"));
            assertFalse(source.contains("Setting.isOriginalEnhancedDetailPage()"));
            assertFalse(source.contains("Setting.isDirectDetailPage()"));
            assertTrue(source.contains("isRuntimeFusionMode()"));
            assertTrue(source.contains("isRuntimeOriginalEnhancedMode()"));
            assertTrue(source.contains("isRuntimeDirectMode()"));
        }
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue("missing method " + startMarker, start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static String read(String flavor) throws Exception {
        return Files.readString(root().resolve(Path.of("app", "src", flavor, "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")), StandardCharsets.UTF_8);
    }

    private static Path root() {
        return Files.exists(Path.of("app", "src", "main")) ? Path.of(".") : Path.of("..");
    }
}
