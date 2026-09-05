package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerControlFocusIntegrationTest {

    @Test
    public void sharedFocusHelperProvidesDefaultFocusAndEscapeTrap() throws Exception {
        Path helperPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "PlayerControlFocusHelper.java"));
        String helper = new String(Files.readAllBytes(helperPath), StandardCharsets.UTF_8);

        assertTrue("helper must expose default focus restoration",
                helper.contains("public static boolean ensureFocus"));
        assertTrue("helper must expose key handling for visible control overlays",
                helper.contains("public static boolean handleKey"));
        assertTrue("helper must detect focus inside a control root",
                helper.contains("public static boolean containsFocus"));
        assertTrue("direction keys that would leave the control root must be consumed",
                helper.contains("!isDescendant(root, next)") && helper.contains("return true;"));
    }

    @Test
    public void tmdbInlineFullscreenControlsRestoreAndTrapFocus() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);

        int show = activity.indexOf("private void showInlineControls(boolean show, boolean focus)");
        int focusDefault = activity.indexOf("focusInlineDefaultControl();", show);
        int dispatch = activity.indexOf("public boolean dispatchKeyEvent(KeyEvent event)");
        int hiddenPlaybackDispatch = activity.indexOf("handleInlineFullscreenHiddenKey(event)", dispatch);
        int inlineDispatch = activity.indexOf("handleInlineKey(event)", dispatch);
        int detailDispatch = activity.indexOf("handleDetailEpisodeNavigationKey(event)", dispatch);
        int handle = activity.indexOf("private boolean handleInlineKey(KeyEvent event)");
        int focusTrap = activity.indexOf("handleInlineControlFocusKey(event)", handle);
        int hiddenPlayback = activity.indexOf("private boolean handleInlineFullscreenHiddenKey(KeyEvent event)");
        int hiddenPlaybackPredicate = activity.indexOf("private boolean isInlineFullscreenHiddenPlaybackKey(KeyEvent event)");

        assertTrue(activityPath + " must import the shared helper",
                activity.contains("import com.fongmi.android.tv.ui.helper.PlayerControlFocusHelper;"));
        assertTrue("inline controls must restore focus even when callers pass focus=false",
                show >= 0 && focusDefault > show);
        assertTrue("fullscreen hidden inline playback keys must run before detail navigation can consume DPAD_UP/DOWN",
                dispatch >= 0 && hiddenPlaybackDispatch > dispatch && hiddenPlaybackDispatch < detailDispatch);
        assertTrue("detail episode navigation must keep DPAD focus before inline controls restore lost focus",
                dispatch >= 0 && detailDispatch > dispatch && inlineDispatch > detailDispatch);
        assertTrue("visible inline controls must trap direction/enter focus inside the overlay",
                handle >= 0 && focusTrap > handle);
        assertTrue("hidden fullscreen inline playback keys must delegate to inline playback handling and consume stale detail focus",
                hiddenPlayback > handle
                        && activity.indexOf("if (handleInlineKey(event)) return true;", hiddenPlayback) > hiddenPlayback
                        && activity.indexOf("return true;", hiddenPlayback) > hiddenPlayback);
        assertTrue("hidden fullscreen inline playback keys must include up/down wake keys only while controls are hidden",
                hiddenPlaybackPredicate > hiddenPlayback
                        && activity.indexOf("isInlinePlayerMode()", hiddenPlaybackPredicate) > hiddenPlaybackPredicate
                        && activity.indexOf("inlineStarted", hiddenPlaybackPredicate) > hiddenPlaybackPredicate
                        && activity.indexOf("inlineFullscreen", hiddenPlaybackPredicate) > hiddenPlaybackPredicate
                        && activity.indexOf("isInlineControlsVisible()", hiddenPlaybackPredicate) > hiddenPlaybackPredicate
                        && activity.indexOf("KeyUtil.isUpKey(event)", hiddenPlaybackPredicate) > hiddenPlaybackPredicate
                        && activity.indexOf("KeyUtil.isDownKey(event)", hiddenPlaybackPredicate) > hiddenPlaybackPredicate);
        assertTrue("leanback detail-player fullscreen must disable the system focus highlight that covers video when controls hide",
                activity.contains("private boolean isLeanbackInlinePlayerPanel()")
                        && activity.contains("return Util.isLeanback() && (isFusionMode() || isPlayerMode());")
                        && activity.contains("binding.playerPanel.setDefaultFocusHighlightEnabled(false);")
                        && activity.contains("binding.playerPanel.setRippleColor(ColorStateList.valueOf(0x00000000));"));
    }

    @Test
    public void tmdbInlineControlsAutoHideAfterTenSecondsOnTvEvenWhenFocused() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);

        int delay = activity.indexOf("private long inlineControlsHideDelayMs()");
        int hide = activity.indexOf("private void hideInlineControlsIfIdle()", delay);
        int hideEnd = activity.indexOf("private void setInlineHideCallback()", hide);
        int touch = activity.indexOf("private void touchInlineControls()", hideEnd);
        int touchEnd = activity.indexOf("private void focusInlineDefaultControl()", touch);

        assertTrue("inline controls must define a dedicated ten-second TV idle timeout",
                activity.contains("INLINE_CONTROLS_HIDE_DELAY_MS = TimeUnit.SECONDS.toMillis(10)"));
        assertTrue("inline control delay, idle, and interaction methods must be present in source order",
                delay >= 0 && hide > delay && hideEnd > hide && touch > hideEnd && touchEnd > touch);
        String delayBody = activity.substring(delay, hide);
        String hideBody = activity.substring(hide, hideEnd);
        String touchBody = activity.substring(touch, touchEnd);
        assertTrue("mobile inline controls must retain the existing global timeout",
                delayBody.contains("Util.isMobile() ? Constant.INTERVAL_HIDE : INLINE_CONTROLS_HIDE_DELAY_MS"));
        assertFalse("persistent TV focus must not postpone idle hiding",
                hideBody.contains("hasFocusedChild"));
        assertTrue("idle hiding must use the platform-specific timeout",
                hideBody.contains("long delay = inlineControlsHideDelayMs()")
                        && hideBody.contains("idle < delay")
                        && hideBody.contains("delay - idle"));
        assertTrue("real control interaction must restart the platform-specific timeout",
                touchBody.contains("App.post(inlineHideControls, inlineControlsHideDelayMs());"));
    }

    @Test
    public void tmdbInlinePlayerChoiceOffersExternalDispatchLikeNativeEnhanced() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);

        int show = activity.indexOf("private boolean showInlinePlayerChoice()");
        int switchMethod = activity.indexOf("private void switchInlinePlayer", show);

        assertTrue("inline player choice must build from the shared kernel list in priority order",
                show >= 0 && activity.indexOf("String[] kernels = PlayerKernelDialog.kernels(getResources());", show) > show);
        assertTrue("the picked row must map back through the kernel order before switching",
                show >= 0 && activity.indexOf("switchInlinePlayer(PlayerSetting.kernelAt(which))", show) > show);
        assertTrue("inline player choice must append an external dispatch option",
                show >= 0
                        && activity.indexOf("String[] items = Arrays.copyOf(kernels, kernels.length + 1);", show) > show
                        && activity.indexOf("items[kernels.length] = getString(R.string.player_kernel_external);", show) > show);
        assertTrue("inline player choice dialog must show the expanded item list",
                show >= 0 && activity.indexOf("setItems(items", show) > show);
        assertTrue("inline player choice dialog should stay compact on mobile like native enhanced",
                show >= 0 && switchMethod > show
                        && activity.indexOf("setTitle(R.string.player_kernel)", show) < 0);
        assertTrue("choosing the appended external option must dispatch to external player flow",
                show >= 0 && switchMethod > show
                        && activity.indexOf("openInlineExternal()", show) > show
                        && activity.indexOf("openInlineExternal()", show) < switchMethod);
    }

    @Test
    public void sharedPlayerKernelDialogOffersExternalDispatchWhereverPlaybackRuns() throws Exception {
        Path dialogPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "PlayerKernelDialog.java"));
        String dialog = new String(Files.readAllBytes(dialogPath), StandardCharsets.UTF_8);

        assertTrue(dialogPath + " must expose an external dispatch callback so playback screens can append the option",
                dialog.contains("public interface ExternalListener"));
        assertTrue("the external row must be appended past the kernel array rather than replacing a kernel",
                dialog.contains("Arrays.copyOf(kernel, kernel.length + 1)")
                        && dialog.contains("items[kernel.length] = label;"));
        assertTrue("the external label must come from resources instead of a hardcoded literal",
                dialog.contains("R.string.player_kernel_external") && !dialog.contains("\"外调\""));
        int notify = dialog.indexOf("private static void notifySelected(");
        assertTrue(dialogPath + " is missing notifySelected", notify >= 0);
        int dispatch = dialog.indexOf("if (external != null && selected >= count)", notify);
        int clamp = dialog.indexOf("PlayerSetting.kernelAt(selected)", notify);
        assertTrue("notifySelected must dispatch the appended row externally", dispatch >= 0);
        assertTrue("notifySelected must map the picked row back to a kernel through the order table", clamp >= 0);
        assertTrue("the external dispatch must be decided before the kernel clamp can rewrite the index",
                dispatch < clamp);
        assertTrue("screens that pass no external callback must keep the plain kernel list",
                dialog.contains("if (external == null) return kernel;"));

        assertPlaybackScreenOffersExternalDispatch(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java")));
        assertPlaybackScreenOffersExternalDispatch(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java")));
        assertPlaybackScreenOffersExternalDispatch(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "CastActivity.java")));
        assertPlaybackScreenOffersExternalDispatch(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
    }

    private static void assertPlaybackScreenOffersExternalDispatch(Path sourcePath) throws Exception {
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int kernel = source.indexOf("private void onPlayerKernel()");
        int external = source.indexOf("private void onExternalPlayer()");

        assertTrue(sourcePath + " is missing onPlayerKernel", kernel >= 0);
        assertTrue(sourcePath + " is missing onExternalPlayer", external >= 0);

        String kernelBody = enclosingMethodBody(source, kernel);
        String externalBody = enclosingMethodBody(source, external);

        assertTrue(sourcePath + " must offer external dispatch in its player kernel dialog",
                kernelBody.contains("this::onExternalPlayer"));
        assertTrue(sourcePath + " must launch the external player from onExternalPlayer",
                externalBody.contains("PlayerHelper.choose("));
        assertTrue(sourcePath + " must mark the redirect so returning from the external player is handled",
                externalBody.contains("setRedirect(true);"));
        assertFalse(sourcePath + " must not keep the orphaned hand-rolled kernel chooser",
                source.contains("private void onChoose()"));
    }

    private static String enclosingMethodBody(String source, int declaration) {
        int open = source.indexOf('{', declaration);
        if (open < 0) return "";
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return source.substring(open, index + 1);
        }
        return "";
    }

    @Test
    public void regularPlaybackControlsTrapFocusOnMobileAndLeanback() throws Exception {
        Path mobilePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        Path leanbackPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);

        assertControlFocusTrap(mobilePath, mobile, "mBinding.control.play");
        assertControlFocusTrap(leanbackPath, leanback, "getFocus2()");
    }

    private static void assertControlFocusTrap(Path sourcePath, String source, String defaultFocus) {
        int show = source.indexOf("private void showControl");
        int dispatch = source.indexOf("public boolean dispatchKeyEvent(KeyEvent event)");

        assertTrue(sourcePath + " must import PlayerControlFocusHelper",
                source.contains("import com.fongmi.android.tv.ui.helper.PlayerControlFocusHelper;"));
        assertTrue(sourcePath + " must ensure a default control focus when the overlay is shown",
                show >= 0 && source.indexOf("PlayerControlFocusHelper.ensureFocus", show) > show
                        && source.indexOf(defaultFocus, show) > show);
        assertTrue(sourcePath + " must intercept control focus keys before default focus search can leave the overlay",
                dispatch >= 0 && source.indexOf("PlayerControlFocusHelper.handleKey", dispatch) > dispatch);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findMobileJavaPath() {
        Path moduleRelative = Path.of("src", "mobile", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "java");
    }

    private static Path findLeanbackJavaPath() {
        Path moduleRelative = Path.of("src", "leanback", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "java");
    }
}
