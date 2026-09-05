package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerPlaybackRegressionSourceTest {

    @Test
    public void customPlayerButtonOrderPreservesSpacerAndRefreshesVisibleFocusChain() throws Exception {
        String source = readMainJava("com", "fongmi", "android", "tv", "setting", "PlayerButtonSetting.java");
        int applyOrder = source.indexOf("public static void applyOrder(ViewGroup container, Map<String, View> views)");
        int applyVisibility = source.indexOf("public static void applyVisibility(Map<String, View> views)");
        int reorder = source.indexOf("private static void reorderViewsPreservingUnmanagedChildren");
        int focus = source.indexOf("private static void updateFocusNavigation(Map<String, View> views)");

        assertTrue("custom order must preserve the spacer and other unmanaged controls in their original child slots",
                applyOrder >= 0
                        && source.indexOf("reorderViewsPreservingUnmanagedChildren(container, ordered);", applyOrder) > applyOrder
                        && reorder > applyOrder
                        && source.indexOf("container.removeAllViews();", reorder) > reorder
                        && source.indexOf("if (managed.contains(child))", reorder) > reorder);
        assertTrue("visibility refreshes must rebuild a circular focus chain from the container's currently visible children",
                applyVisibility >= 0
                        && source.indexOf("updateFocusNavigation(views);", applyVisibility) > applyVisibility
                        && focus >= 0
                        && source.indexOf("view.getVisibility() == View.VISIBLE && view.isFocusable()", focus) > focus
                        && source.indexOf("View prev = i > 0 ? focusable.get(i - 1) : focusable.get(focusable.size() - 1);", focus) > focus
                        && source.indexOf("View next = i < focusable.size() - 1 ? focusable.get(i + 1) : focusable.get(0);", focus) > focus
                        && source.indexOf("current.setNextFocusLeftId(prev.getId());", focus) > focus
                        && source.indexOf("current.setNextFocusRightId(next.getId());", focus) > focus);
    }

    @Test
    public void runtimePlayerActionsRefreshFocusAfterVisibilityChanges() throws Exception {
        String leanback = readLeanbackJava("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        String mobile = readMobileJava("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");

        assertFocusRefreshAfter(leanback,
                "private void setEpisodeAdapter(List<Episode> items, boolean scrollToCurrent)",
                "mBinding.control.action.episodes.setVisibility");
        assertFocusRefreshAfter(leanback,
                "private void setQualityVisible(boolean visible)",
                "mBinding.control.action.actionQuality.setVisibility");
        assertFocusRefreshAfter(leanback,
                "private void setAdFeedbackVisible()",
                "mBinding.control.action.adFeedback.setVisibility");
        assertFocusRefreshAfter(mobile,
                "private void setQualityVisible(boolean visible)",
                "mBinding.control.action.actionQuality.setVisibility");
        assertFocusRefreshAfter(mobile,
                "private void setAdFeedbackVisible()",
                "mBinding.control.action.adFeedback.setVisibility");

        int refresh = leanback.indexOf("private void applyActionButtonVisibility()");
        int refreshEnd = leanback.indexOf("\n    private ", refresh + 1);
        int immersive = leanback.indexOf("updateImmersiveAudioAction();", refresh);
        int diagnostic = leanback.indexOf("updatePanDiagnosticAction();", refresh);
        int rebuild = leanback.indexOf("PlayerButtonSetting.applyVisibility(mActionButtons);", refresh);
        assertTrue("leanback must update runtime-only action visibility before rebuilding the full focus chain",
                refresh >= 0 && refreshEnd > refresh
                        && immersive > refresh && immersive < rebuild
                        && diagnostic > refresh && diagnostic < rebuild
                        && rebuild < refreshEnd);
    }

    @Test
    public void panDiagnosticRespectsTheConfiguredPlayerButtonOrder() throws Exception {
        String leanback = readLeanbackJava("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        int setup = leanback.indexOf("private void setupActionButtons()");
        int applyOrder = leanback.indexOf("PlayerButtonSetting.applyOrder(mBinding.control.action.container, mActionButtons);", setup);

        assertTrue("pan diagnostic must remain in the configurable action catalog",
                setup >= 0
                        && applyOrder > setup
                        && leanback.indexOf("addActionButton(PlayerButtonSetting.PAN_DIAGNOSTIC, mBinding.control.action.panDiagnostic);", setup) < applyOrder);
        assertFalse("manual pan diagnostic placement is dead once the action is managed by PlayerButtonSetting",
                leanback.contains("private void placePanDiagnosticAction()"));
        assertFalse("configured order must not be overwritten by a second manual pan diagnostic placement",
                leanback.contains("placePanDiagnosticAction();"));
    }

    @Test
    public void allPlayerActionButtonsUseTheSharedSettingsCatalog() throws Exception {
        String settings = readMainJava("com", "fongmi", "android", "tv", "setting", "PlayerButtonSetting.java");
        String leanback = readLeanbackJava("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        String mobile = readMobileJava("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        String tmdb = readMainJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");

        String[] catalog = {
                "new Item(QUALITY, R.string.detail_quality)",
                "new Item(PAN_DIAGNOSTIC, R.string.pan_diagnostic_entry)",
                "new Item(KARAOKE, R.string.player_karaoke_mode)",
                "new Item(IMMERSIVE_AUDIO, R.string.player_immersive_audio_mode)",
                "new Item(SEARCH, R.string.play_search)",
                "new Item(PARSE, R.string.parse)",
                "new Item(DISPLAY, R.string.control_display)",
                "new Item(AD_FEEDBACK, R.string.play_ad_feedback)",
                "new Item(CAST, R.string.play_cast)",
                "new Item(TIMER, R.string.play_timer)"
        };
        for (String item : catalog) assertTrue("player button settings catalog is missing " + item, settings.contains(item));

        String[] leanbackMappings = {
                "addActionButton(PlayerButtonSetting.SEARCH, mBinding.control.action.search);",
                "addActionButton(PlayerButtonSetting.PAN_DIAGNOSTIC, mBinding.control.action.panDiagnostic);",
                "addActionButton(PlayerButtonSetting.QUALITY, mBinding.control.action.actionQuality);",
                "addActionButton(PlayerButtonSetting.KARAOKE, mBinding.control.action.karaoke);",
                "addActionButton(PlayerButtonSetting.IMMERSIVE_AUDIO, mBinding.control.action.immersiveAudio);",
                "addActionButton(PlayerButtonSetting.AD_FEEDBACK, mBinding.control.action.adFeedback);",
                "addActionButton(PlayerButtonSetting.CAST, mBinding.control.action.cast);",
                "addActionButton(PlayerButtonSetting.TIMER, mBinding.control.action.timer);"
        };
        for (String mapping : leanbackMappings) assertTrue("leanback VideoActivity is missing " + mapping, leanback.contains(mapping));

        String[] mobileMappings = {
                "addActionButton(PlayerButtonSetting.QUALITY, mBinding.control.action.actionQuality);",
                "addActionButton(PlayerButtonSetting.KARAOKE, mBinding.control.action.karaoke);",
                "addActionButton(PlayerButtonSetting.AD_FEEDBACK, mBinding.control.action.adFeedback);"
        };
        for (String mapping : mobileMappings) assertTrue("mobile VideoActivity is missing " + mapping, mobile.contains(mapping));

        String[] tmdbMappings = {
                "buttons.put(PlayerButtonSetting.QUALITY, binding.playerQuality);",
                "buttons.put(PlayerButtonSetting.PARSE, binding.playerParse);",
                "buttons.put(PlayerButtonSetting.AD_FEEDBACK, binding.playerAdFeedback);",
                "buttons.put(PlayerButtonSetting.DISPLAY, binding.playerDisplay);",
                "buttons.put(PlayerButtonSetting.QUALITY, detailActionView(R.id.actionQuality, View.class));",
                "buttons.put(PlayerButtonSetting.AD_FEEDBACK, detailActionView(R.id.adFeedback, View.class));"
        };
        for (String mapping : tmdbMappings) assertTrue("TmdbDetailActivity is missing " + mapping, tmdb.contains(mapping));

        assertTrue("mobile parser controls must obey the shared parse visibility setting",
                mobile.contains("PlayerButtonSetting.isVisible(PlayerButtonSetting.PARSE)")
                        && !mobile.contains("mBinding.control.parse.setVisibility(isFullscreen() && isUseParse() ? View.VISIBLE : View.GONE);"));
        assertTrue("TmdbDetailActivity configurable inline controls must obey shared visibility settings",
                tmdb.contains("PlayerButtonSetting.isVisible(PlayerButtonSetting.CAST)")
                        && tmdb.contains("PlayerButtonSetting.isVisible(PlayerButtonSetting.PARSE)"));
        assertTrue("leanback parser controls must obey the shared parse visibility setting",
                leanback.contains("PlayerButtonSetting.isVisible(PlayerButtonSetting.PARSE)"));
    }

    @Test
    public void mediaSessionStopStopsPlaybackWithoutExitingThePlaybackScreen() throws Exception {
        String source = readMainJava("com", "fongmi", "android", "tv", "service", "PlaybackService.java");
        int wrap = source.indexOf("private ForwardingPlayer wrap(Player base)");
        int stop = source.indexOf("public void stop()", wrap);
        int nextOverride = source.indexOf("@NonNull", stop);

        assertTrue("PlaybackService must keep the MediaSession stop override available for regression checking",
                wrap >= 0 && stop > wrap && nextOverride > stop);
        String stopOverride = source.substring(stop, nextOverride);
        assertTrue("MediaSession STOP must stop and clear playback without invoking the navigation callback",
                stopOverride.contains("stopAndClear();"));
        assertFalse("MediaSession STOP must not be treated as an explicit request to exit the playback Activity",
                stopOverride.contains("dispatchStop();"));
    }

    @Test
    public void mobileRotateButtonRequestsOrientationFromTheToggledState() throws Exception {
        String source = readMobileJava("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        int rotate = source.indexOf("private void onRotate()");
        int rotateEnd = source.indexOf("\n    private ", rotate + 1);
        int toggle = source.indexOf("setRotate(!isRotate());", rotate);
        int request = source.indexOf("PlaybackOrientation.getRotateOrientation(isRotate())", rotate);

        assertTrue("mobile rotate button must choose the requested orientation after toggling its state",
                rotate >= 0
                        && rotateEnd > rotate
                        && toggle > rotate
                        && request > toggle
                        && request < rotateEnd);
        assertFalse("mobile rotate button must not re-request the current portrait layout orientation",
                source.substring(rotate, rotateEnd).contains("PlaybackOrientation.getRotateOrientation(isPort())"));
    }

    @Test
    public void mobileFullscreenExitRebindsEpisodesAfterOrientationSettles() throws Exception {
        String source = readMobileJava("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        int restore = source.indexOf("private void restoreEmbeddedVideoLayoutAfterFullscreen()");
        int refresh = source.indexOf("private void refreshEpisodeLayoutAfterFullscreen()");
        int configuration = source.indexOf("public void onConfigurationChanged(@NonNull Configuration newConfig)");

        assertTrue("fullscreen exit must schedule an episode rebind after the embedded layout is restored",
                restore >= 0
                        && source.indexOf("mBinding.episode.post(this::refreshEpisodeLayoutAfterFullscreen);", restore) > restore
                        && source.indexOf("mBinding.episode.postDelayed(this::refreshEpisodeLayoutAfterFullscreen, 180);", restore) > restore);
        assertTrue("episode refresh must rebuild orientation-dependent spans and rebind stale first-screen holders",
                refresh > restore
                        && source.indexOf("updateEpisodeLayout(mEpisodeAdapter.getItems());", refresh) > refresh
                        && source.indexOf("mEpisodeAdapter.notifyItemRangeChanged(0, mEpisodeAdapter.getItemCount());", refresh) > refresh
                        && source.indexOf("mBinding.episode.requestLayout();", refresh) > refresh);
        assertTrue("configuration completion must run the same refresh in case rotation settles after the delayed callback",
                configuration > refresh
                        && source.indexOf("if (!isFullscreen()) refreshEpisodeLayoutAfterFullscreen();", configuration) > configuration);
    }

    @Test
    public void playbackNavigationCallbackCleanupCannotClearNewOwnerOrLeakStaleOwner() throws Exception {
        String service = readMainJava("com", "fongmi", "android", "tv", "service", "PlaybackService.java");
        String activity = readMainJava("com", "fongmi", "android", "tv", "ui", "activity", "PlaybackActivity.java");

        int clear = service.indexOf("public void clearNavigationCallback(NavigationCallback expected)");
        int clearEnd = service.indexOf("\n    private ", clear + 1);
        int guard = service.indexOf("if (navigationCallback != expected) return;", clear);
        int reset = service.indexOf("setNavigationCallback(null, null);", clear);
        assertTrue("callback cleanup must reject stale owners before clearing the active callback",
                clear >= 0 && clearEnd > clear && guard > clear && guard < reset && reset < clearEnd);

        int dispatch = service.indexOf("private void dispatch(Consumer<NavigationCallback> action)");
        int dispatchEnd = service.indexOf("\n    private ", dispatch + 1);
        String dispatchBlock = service.substring(dispatch, dispatchEnd);
        assertTrue("queued navigation actions must recheck callback ownership before invoking an old owner",
                dispatchBlock.contains("if (navigationCallback == callback) action.accept(callback);"));

        int release = activity.indexOf("private void releaseService(boolean owner)");
        int releaseEnd = activity.indexOf("\n    private ", release + 1);
        String releaseBlock = activity.substring(release, releaseEnd);
        assertTrue("activity teardown must always attempt identity-safe callback cleanup",
                releaseBlock.contains("mService.clearNavigationCallback(getNavigationCallback());"));
        assertFalse("callback cleanup must not depend on a playback-key ownership check",
                releaseBlock.contains("if (owner) mService.clearNavigationCallback(getNavigationCallback());"));
    }

    private static void assertFocusRefreshAfter(String source, String methodSignature, String visibilityMutation) {
        int method = source.indexOf(methodSignature);
        int methodEnd = source.indexOf("\n    private ", method + 1);
        int mutation = source.indexOf(visibilityMutation, method);
        int refresh = source.indexOf("applyActionButtonVisibility();", mutation);
        assertTrue(methodSignature + " must rebuild focus after changing a runtime action's visibility",
                method >= 0 && methodEnd > method
                        && mutation > method && mutation < methodEnd
                        && refresh > mutation && refresh < methodEnd);
    }

    private static String readLeanbackJava(String... parts) throws Exception {
        return read(Path.of("src", "leanback", "java"), Path.of("app", "src", "leanback", "java"), parts);
    }

    private static String readMainJava(String... parts) throws Exception {
        return read(Path.of("src", "main", "java"), Path.of("app", "src", "main", "java"), parts);
    }

    private static String readMobileJava(String... parts) throws Exception {
        return read(Path.of("src", "mobile", "java"), Path.of("app", "src", "mobile", "java"), parts);
    }

    private static String read(Path moduleRoot, Path workspaceRoot, String... parts) throws Exception {
        Path path = moduleRoot;
        for (String part : parts) path = path.resolve(part);
        if (!Files.exists(path)) {
            path = workspaceRoot;
            for (String part : parts) path = path.resolve(part);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
