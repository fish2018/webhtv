package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbVideoPopupWiringTest {

    @Test
    public void playbackActivitySupportsOverlayPlaybackWithoutTransientLifecycle() throws Exception {
        String playback = read("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "activity", "PlaybackActivity.java");
        String policy = read("src", "main", "java", "com", "fongmi", "android", "tv", "player", "PlaybackServiceReleasePolicy.java");

        assertTrue(playback.contains("public final boolean pauseForOverlayPlayback()"));
        assertTrue(playback.contains("public final void resumeAfterOverlayPlayback(boolean shouldResume)"));
        String pause = methodBody(playback, "public final boolean pauseForOverlayPlayback()");
        assertTrue(pause.contains("active.isPlaying()"));
        assertTrue(pause.contains("if (!shouldResume) return false;"));
        String resume = methodBody(playback, "public final void resumeAfterOverlayPlayback(boolean shouldResume)");
        assertTrue(resume.contains("if (!shouldResume"));
        assertTrue(resume.contains("active.getPlayWhenReady()"));

        assertFalse(playback.contains("EXTRA_TRANSIENT_PLAYBACK"));
        assertFalse(playback.contains("REQUEST_TRANSIENT_PLAYBACK"));
        assertFalse(playback.contains("launchTransientPlayback"));
        assertFalse(playback.contains("TransientPlaybackCoordinator"));
        assertFalse(playback.contains("TransientPlaybackSnapshot"));
        assertTrue(policy.contains("decide(boolean owner, boolean keepAlive, boolean hasConsumer)"));
        assertFalse(policy.contains("transientPlayback"));
    }

    @Test
    public void obsoleteTransientActivityChainIsRemoved() throws Exception {
        String mobileManifest = read("src", "mobile", "AndroidManifest.xml");
        String leanbackManifest = read("src", "leanback", "AndroidManifest.xml");
        String mobile = read("src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        String leanback = read("src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        String viewModel = read("src", "main", "java", "com", "fongmi", "android", "tv", "model", "SiteViewModel.java");

        assertFalse(exists("src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "activity", "TransientVideoActivity.java"));
        assertFalse(exists("src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "TransientVideoActivity.java"));
        assertFalse(exists("src", "main", "java", "com", "fongmi", "android", "tv", "player", "TransientPlaybackCoordinator.java"));
        assertFalse(exists("src", "main", "java", "com", "fongmi", "android", "tv", "player", "TransientPlaybackSnapshot.java"));
        assertFalse(mobileManifest.contains("TransientVideoActivity"));
        assertFalse(leanbackManifest.contains("TransientVideoActivity"));
        assertFalse(mobile.contains("createTransientIntent"));
        assertFalse(leanback.contains("createTransientIntent"));
        assertFalse(mobile.contains("isTransientPlayback"));
        assertFalse(leanback.contains("isTransientPlayback"));
        assertFalse(viewModel.contains("boolean isolated"));
    }

    @Test
    public void relatedVideoUsesIsolatedWindowedPopupAndRestoresHostPlayback() throws Exception {
        String helper = read("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "helper", "TmdbVideoPlayback.java");
        assertTrue(exists("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "TmdbVideoPlayerDialog.java"));
        assertTrue(exists("src", "main", "res", "layout", "dialog_tmdb_video_player.xml"));
        String dialog = read("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "TmdbVideoPlayerDialog.java");
        String layout = read("src", "main", "res", "layout", "dialog_tmdb_video_player.xml");
        String siteApi = read("src", "main", "java", "com", "fongmi", "android", "tv", "api", "SiteApi.java");

        assertTrue(helper.contains("if (!(activity instanceof FragmentActivity)) return false;"));
        assertTrue(helper.contains("TmdbVideoPlayerDialog.show((FragmentActivity) activity, launch)"));
        assertFalse(helper.contains("createTransientIntent"));
        assertFalse(helper.contains("launchTransientPlayback"));

        assertTrue(dialog.contains("public static boolean show(FragmentActivity activity, TmdbVideoPlayback.Launch launch)"));
        assertTrue(dialog.contains("if (current instanceof TmdbVideoPlayerDialog)"));
        assertTrue(dialog.contains("replaceLaunch(launch)"));
        assertTrue(dialog.contains("pauseForOverlayPlayback()"));
        assertTrue(dialog.contains("resumeAfterOverlayPlayback(resumeParent)"));
        assertTrue(dialog.contains("SiteApi.playerContentIsolated(launch.getKey(), launch.getPlayFlag(), launch.getPlayEpisodeUrl(), PlayerSetting.EXO)"));
        assertFalse(dialog.contains("SiteApi.playerContent("));
        assertTrue(dialog.contains("requestGeneration != generation"));
        assertTrue(dialog.contains("private boolean retryExpiredSource(PlaybackException error)"));
        assertTrue(dialog.contains("if (status != 403 && status != 410) return false;"));
        assertTrue(dialog.contains("retryPosition = player == null ? C.TIME_UNSET : player.getCurrentPosition();"));
        assertTrue(dialog.contains("player.seekTo(resumePosition);"));
        assertTrue(dialog.contains("sourceRefreshAttempted = true;"));
        assertTrue(dialog.contains("if (retryExpiredSource(error)) return;"));
        assertTrue(dialog.contains("if (fullscreen) setFullscreen(false);"));
        assertTrue(dialog.contains("else dismissAllowingStateLoss();"));
        assertTrue(dialog.contains("if (parentResumed) return;"));
        assertTrue(dialog.contains("activity.isChangingConfigurations()"));

        String fullscreen = methodBody(dialog, "private void setFullscreen(boolean fullscreen)");
        assertFalse(fullscreen.contains("resolve()"));
        assertFalse(fullscreen.contains("preparePlayer("));
        assertFalse(fullscreen.contains("releasePlayer()"));
        assertFalse(fullscreen.contains("new ExoPlayer.Builder"));
        String windowMode = methodBody(dialog, "private void applyWindowMode()");
        assertTrue(windowMode.contains("window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)"));
        assertTrue(windowMode.contains("params.topMargin = fullscreen ? 0 : dp(52)"));

        assertTrue(siteApi.contains("playerContentIsolated(@NonNull String key, @NonNull String flag, @NonNull String id, int playerType)"));
        assertTrue(siteApi.contains("return playerContent(key, flag, id, playerType, new Source(), false);"));
        assertOrder(layout, "android:id=\"@+id/tmdbVideoPlayer\"", "android:id=\"@+id/tmdbVideoLoading\"", "android:id=\"@+id/tmdbVideoError\"");
        assertTrue(layout.contains("android:id=\"@+id/tmdbVideoClose\""));
        assertTrue(layout.contains("android:id=\"@+id/tmdbVideoFullscreen\""));
        assertTrue(layout.contains("android:nextFocusRight=\"@id/tmdbVideoFullscreen\""));
        assertTrue(layout.contains("app:use_controller=\"true\""));
    }

    @Test
    public void relatedVideoRowsAppearImmediatelyAfterPhotosAndBeforeRecommendations() throws Exception {
        String header = read("src", "main", "res", "layout", "view_tmdb_header.xml");
        assertOrder(header, "android:id=\"@+id/tmdbPhotos\"", "android:id=\"@+id/tmdbRelatedVideosLabel\"", "android:id=\"@+id/tmdbRelatedVideos\"", "android:id=\"@+id/tmdbRecommendationsLabel\"");

        String detail = read("src", "main", "res", "layout", "activity_tmdb_detail.xml");
        assertOrder(detail, "android:id=\"@+id/episodePhotoList\"", "android:id=\"@+id/relatedVideoTitle\"", "android:id=\"@+id/relatedVideoList\"", "android:id=\"@+id/castTitle\"", "android:id=\"@+id/relatedTitle\"");

        String leanback = read("src", "leanback", "res", "layout", "activity_video.xml");
        assertOrder(leanback, "android:id=\"@+id/tmdbPhotos\"", "android:id=\"@+id/tmdbRelatedVideosLabel\"", "android:id=\"@+id/tmdbRelatedVideos\"", "android:id=\"@+id/tmdbCrewLabel\"", "android:id=\"@+id/tmdbRecommendationsLabel\"");

        String leanbackActivity = read("src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        assertOrderAfter(leanbackActivity, "private void bindTmdbData()", "java.util.List<String> photos", "java.util.List<TmdbVideo> relatedVideos", "java.util.List<com.fongmi.android.tv.bean.TmdbPerson> creators", "java.util.List<com.fongmi.android.tv.bean.TmdbItem> recommendations");
        String bindTmdbData = methodBody(leanbackActivity, "private void bindTmdbData()");
        assertOrder(bindTmdbData, "lastVisibleGrid = mBinding.tmdbPhotos", "lastVisibleGrid = mBinding.tmdbRelatedVideos", "lastVisibleGrid = mBinding.tmdbCrew", "lastVisibleGrid = mBinding.tmdbRecommendations");
    }

    @Test
    public void chineseResourcesNameTheRelatedVideoSection() throws Exception {
        String simplified = read("src", "main", "res", "values-zh-rCN", "strings.xml");
        String traditional = read("src", "main", "res", "values-zh-rTW", "strings.xml");
        assertTrue(simplified.contains("<string name=\"tmdb_related_videos_label\">"));
        assertTrue(traditional.contains("<string name=\"tmdb_related_videos_label\">"));
    }

    private static void assertOrder(String source, String... values) {
        int previous = -1;
        for (String value : values) {
            int current = source.indexOf(value);
            assertTrue("Missing or out-of-order value: " + value, current > previous);
            previous = current;
        }
    }

    private static void assertOrderAfter(String source, String anchor, String... values) {
        int previous = source.indexOf(anchor);
        assertTrue("Missing anchor: " + anchor, previous >= 0);
        for (String value : values) {
            int current = source.indexOf(value, previous + 1);
            assertTrue("Missing or out-of-order value after " + anchor + ": " + value, current > previous);
            previous = current;
        }
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing method: " + signature, start >= 0);
        int open = source.indexOf('{', start);
        assertTrue("Missing method body: " + signature, open >= 0);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char value = source.charAt(i);
            if (value == '{') depth++;
            if (value == '}' && --depth == 0) return source.substring(open + 1, i);
        }
        throw new AssertionError("Unclosed method: " + signature);
    }

    private static String read(String... parts) throws Exception {
        Path root = Files.exists(Path.of("src", "main")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(Path.of("", parts)), StandardCharsets.UTF_8);
    }

    private static boolean exists(String... parts) {
        Path root = Files.exists(Path.of("src", "main")) ? Path.of("") : Path.of("app");
        return Files.exists(root.resolve(Path.of("", parts)));
    }
}
