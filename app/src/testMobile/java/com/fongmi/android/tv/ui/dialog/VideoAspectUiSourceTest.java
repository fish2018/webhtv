package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VideoAspectUiSourceTest {

    @Test
    public void scaleArraysKeepStableModeIds() throws Exception {
        assertScaleArray(mainRes("values", "strings.xml"));
        assertScaleArray(mainRes("values-zh-rCN", "strings.xml"));
        assertScaleArray(mainRes("values-zh-rTW", "strings.xml"));
    }

    @Test
    public void playerControlLayoutsUseOneAspectPickerEntry() throws Exception {
        assertSingleAspectEntry(source("mobile", "res", "layout", "dialog_control.xml"));
        assertSingleAspectEntry(source("leanback", "res", "layout", "dialog_control.xml"));
        assertSingleAspectEntry(source("mobile", "res", "layout", "dialog_live_control.xml"));
    }

    @Test
    public void playbackEntryPointsUseSharedSingleChoicePicker() throws Exception {
        String playback = read(source("main", "java", "com", "fongmi", "android", "tv", "ui", "activity", "PlaybackActivity.java"));
        assertTrue(playback.contains("VideoAspectModeDialog.show("));
        assertFalse(playback.contains("selectResizeMode("));

        assertPickerEntry(source("mobile", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "ControlDialog.java"));
        assertPickerEntry(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "ControlDialog.java"));
        assertDisplayedModeLookup(source("mobile", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "ControlDialog.java"));
        assertDisplayedModeLookup(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "ControlDialog.java"));
        assertPickerEntry(source("mobile", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "LiveControlDialog.java"));
    }

    @Test
    public void playerSettingsUseSharedSingleChoicePicker() throws Exception {
        String mobile = read(source("mobile", "java", "com", "fongmi", "android", "tv", "ui", "fragment", "SettingPlayerFragment.java"));
        String leanback = read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "SettingPlayerActivity.java"));
        assertTrue(mobile.contains("VideoAspectModeDialog.show("));
        assertTrue(leanback.contains("VideoAspectModeDialog.show("));
    }

    @Test
    public void customRatioDialogDefinesTvFocusNavigation() throws Exception {
        String dialog = read(source("main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "VideoAspectRatioDialog.java"));
        assertTrue(dialog.contains("setNextFocusRightId"));
        assertTrue(dialog.contains("setNextFocusDownId"));
        assertTrue(dialog.contains("KeyEvent.KEYCODE_DPAD_DOWN"));
        assertTrue(dialog.contains("KeyEvent.KEYCODE_DPAD_UP"));
        assertTrue(dialog.contains("if (leanback) view.setFocusableInTouchMode(true)"));
        assertFalse(dialog.contains("setFocusableInTouchMode(leanback)"));
    }

    @Test
    public void choiceDialogAdaptsHeightForMobileAndKeepsSequentialDpadFocus() throws Exception {
        String dialog = read(source("main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "ChoiceDialog.java"));
        int adaptation = dialog.indexOf("adaptListHeight(window);");
        int leanbackFocus = dialog.indexOf("if (Util.isLeanback())");
        assertTrue("choice list height must adapt inside the post-layout callback", dialog.contains("window.getDecorView().post(() -> {"));
        assertTrue("choice list height must adapt before Leanback-only focus work", adaptation >= 0 && leanbackFocus > adaptation);
        assertTrue("choice list height must use the post-layout visible window frame", dialog.contains("getWindowVisibleDisplayFrame(frame)"));
        assertFalse("choice list height must not treat WRAP_CONTENT decor height as the viewport", dialog.contains("adaptiveListHeight(window.getDecorView().getHeight()"));
        assertFalse("choice list height must not depend on stale orientation metrics", dialog.contains("ResUtil.getScreenHeight(requireContext())"));
        assertTrue("choice list height must measure the scroll view's direct container", dialog.contains("scroll.getParent() instanceof ViewGroup rootGroup"));
        assertTrue("choice list height must derive fixed chrome from laid-out root and scroll heights", dialog.contains("calculateDialogChromeHeight(root.getMeasuredHeight(), target.getMeasuredHeight())"));
        assertTrue("choice list height must recheck after the first layout changes its height", dialog.contains("scroll.post(() -> adaptListHeight(window)"));
        assertTrue("TV choice list must move focus to the next real item", dialog.contains("focusAdjacentItem(position, 1)"));
        assertTrue("TV choice list must move focus to the previous real item", dialog.contains("focusAdjacentItem(position, -1)"));
        assertTrue("the last item must still allow focus to enter the action buttons", dialog.contains("focusFirstAction(root)"));
    }

    @Test
    public void choiceDialogHeightKeepsSafeMarginWithoutTrustingClippedWindowHeight() {
        assertEquals(351, ChoiceDialog.calculateDialogChromeHeight(1075, 724));
        assertEquals(0, ChoiceDialog.calculateDialogChromeHeight(300, 400));
        assertEquals(640, ChoiceDialog.calculateAdaptiveListHeight(1000, 56, 1000, 320, 40));
        assertEquals(24, ChoiceDialog.calculateAdaptiveListHeight(1000, 56, 100, 36, 40));
        assertEquals(240, ChoiceDialog.calculateAdaptiveListHeight(240, 56, 1000, 320, 40));
        assertEquals(697, ChoiceDialog.calculateAdaptiveListHeight(724, 56, 1080, 351, 32));
        assertEquals(16, ChoiceDialog.calculateAdaptiveListHeight(56, 56, 376, 320, 40));
        assertEquals(68, ChoiceDialog.calculateAdaptiveListHeight(108, 56, 428, 320, 40));
    }

    @Test
    public void playbackAndMpvApplyNativeAspectPolicy() throws Exception {
        String playback = read(source("main", "java", "com", "fongmi", "android", "tv", "ui", "activity", "PlaybackActivity.java"));
        assertTrue(playback.contains("VideoAspectMode.resolve("));
        assertTrue(playback.contains("player().setVideoAspect("));

        String mpv = read(source("main", "java", "androidx", "media3", "mpvplayer", "MpvPlayer.java"));
        assertTrue(mpv.contains("\"keepaspect\""));
        assertTrue(mpv.contains("\"video-aspect-override\""));
    }

    @Test
    public void backupIncludesCustomAspectPreferences() throws Exception {
        String backup = read(source("main", "java", "com", "fongmi", "android", "tv", "bean", "Backup.java"));
        assertTrue(backup.contains("\"custom_aspect_width\""));
        assertTrue(backup.contains("\"custom_aspect_height\""));
    }

    private static void assertScaleArray(Path path) throws Exception {
        String xml = read(path);
        int start = xml.indexOf("<string-array name=\"select_scale\">");
        int end = xml.indexOf("</string-array>", start);
        assertTrue("select_scale missing in " + path, start >= 0 && end > start);
        String array = xml.substring(start, end);
        assertEquals("select_scale must contain eight stable mode labels in " + path, 8, count(array, "<item>"));
        assertTrue(array.contains("21:9"));
    }

    private static void assertSingleAspectEntry(Path path) throws Exception {
        String xml = read(path);
        assertTrue(path + " missing scale picker", xml.contains("@+id/scale"));
        assertFalse(path + " still exposes mode buttons", xml.contains("@+id/scale_0"));
        assertFalse(path + " still exposes mode buttons", xml.contains("@+id/scale_7"));
    }

    private static void assertPickerEntry(Path path) throws Exception {
        String source = read(path);
        assertTrue(path + " must open VideoAspectModeDialog", source.contains("VideoAspectModeDialog.show("));
        assertFalse(path + " must not manage eight scale buttons", source.contains("binding.scale7"));
    }

    private static void assertDisplayedModeLookup(Path path) throws Exception {
        String source = read(path);
        assertTrue(path + " must preserve temporary or preview scale modes", source.contains("TextUtils.equals(scale[mode], current)"));
    }

    private static int count(String source, String token) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(token, index)) >= 0; index += token.length()) count++;
        return count;
    }

    private static Path mainRes(String folder, String file) {
        return source("main", "res", folder, file);
    }

    private static Path source(String... parts) {
        Path root = Path.of("").toAbsolutePath();
        Path path = Files.isDirectory(root.resolve("src")) ? root.resolve("src") : root.resolve(Path.of("app", "src"));
        for (String part : parts) path = path.resolve(part);
        return path;
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
