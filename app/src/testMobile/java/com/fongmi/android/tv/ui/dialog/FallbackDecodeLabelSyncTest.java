package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * 播放失败回退（软解后切核心）只发 onPlayerRebuild，不走 onPrepare。三条播放链路的
 * 「解码」标签都必须挂在 rebuild 回调上，否则内核在变、软硬解一直停在起播时的值。
 */
public class FallbackDecodeLabelSyncTest {

    @Test
    public void controlDialogSyncsDecodeTextNotOnlyItsVisibility() throws Exception {
        assertMethodContains(read(dialog("mobile")), "public void setPlayer()",
                "binding.decode.setText(controls.decode.getText());",
                "binding.decode.setVisibility(controls.decode.getVisibility());");
        assertMethodContains(read(dialog("leanback")), "public void setPlayer()",
                "binding.decode.setText(parent.control.action.decode.getText());",
                "binding.decode.setVisibility(parent.control.action.decode.getVisibility());");
    }

    /** 两个 flavor 的原生播放页都必须刷主控制栏，并且把设置弹窗一起带上。 */
    @Test
    public void nativePlaybackRefreshesKernelDecodeAndTheOpenDialogOnRebuild() throws Exception {
        for (String flavor : List.of("mobile", "leanback")) {
            String source = read(videoActivity(flavor));
            assertMethodContains(source, "protected void onPlayerRebuilt()",
                    "setPlayerKernel();", "setDecode();", "refreshControlDialog();");
            assertMethodContains(source, "private void refreshControlDialog()",
                    "if (fragment instanceof ControlDialog dialog) dialog.setPlayer();");
        }
    }

    /** 内嵌播放器要同时刷宿主标签和开着的弹窗，弹窗在 mobile 源集所以只能反射。 */
    @Test
    public void fusionInlinePlaybackRefreshesItsLabelsAndDialogOnRebuild() throws Exception {
        String source = read(tmdbDetailActivity());
        assertMethodContains(source, "protected void onPlayerRebuilt()",
                "updateInlineButtons(", "refreshInlineControlDialog();");
        assertMethodContains(source, "private void refreshInlineControlDialog()",
                "com.fongmi.android.tv.ui.dialog.ControlDialog", "getMethod(\"setPlayer\").invoke(fragment)");
    }

    /** 宿主的解码标签必须来自引擎实时值，否则弹窗抄到的仍是旧文字。 */
    @Test
    public void hostDecodeLabelsReadTheLiveEngineValue() throws Exception {
        for (String flavor : List.of("mobile", "leanback")) {
            assertMethodContains(read(videoActivity(flavor)), "private void setDecode()",
                    "player().getDecodeText()");
        }
        assertMethodContains(read(tmdbDetailActivity()), "private CharSequence inlineDecodeText(boolean hasPlayer)",
                "player().getDecodeText()");
    }

    /** updateInlineButtons 是内嵌链路刷解码标签的唯一入口，断言里不能只认方法名。 */
    @Test
    public void fusionInlineButtonRefreshActuallyWritesTheDecodeLabel() throws Exception {
        String source = read(tmdbDetailActivity());
        assertMethodContains(source, "private void updateInlineButtons(boolean playing)",
                "setInlineDecodeText(inlineDecodeText(hasPlayer));");
        assertMethodContains(source, "private void setInlineDecodeText(CharSequence text)",
                "binding.playerDecode.setText(text);");
    }

    /**
     * 本方法必须排在 [onPrepare, getInlineResumePosition) 之外：TmdbDetailActivityLayoutTest
     * 用这段源码切片断言 onPrepare 的内容，插在中间会把本方法并进那段切片里。
     */
    @Test
    public void fusionRebuildHookStaysOutsideThePrepareSourceSlice() throws Exception {
        String source = read(tmdbDetailActivity());
        int rebuild = source.indexOf("protected void onPlayerRebuilt()");
        int prepare = source.indexOf("protected void onPrepare()");
        int resume = source.indexOf("private long getInlineResumePosition()");
        assertTrue("missing onPlayerRebuilt()", rebuild >= 0);
        assertTrue("missing onPrepare()", prepare >= 0);
        assertTrue("missing getInlineResumePosition()", resume > prepare);
        assertTrue("onPlayerRebuilt() must sit either before onPrepare() or after getInlineResumePosition(),"
                        + " otherwise it pollutes the source slice TmdbDetailActivityLayoutTest uses for onPrepare",
                rebuild < prepare || rebuild > resume);
    }

    /** 轨道要等新引擎 prepare 完才回来，三条链路都得在轨道/标题就绪后再刷一次弹窗。 */
    @Test
    public void openDialogIsAlsoRefreshedOnceTracksAndTitlesSettle() throws Exception {
        for (String flavor : List.of("mobile", "leanback")) {
            String source = read(videoActivity(flavor));
            assertMethodContains(source, "protected void onTracksChanged()", "refreshControlDialog();");
            assertMethodContains(source, "protected void onTitlesChanged()", "refreshControlDialog();");
        }
        assertMethodContains(read(tmdbDetailActivity()), "protected void onTracksChanged()",
                "refreshInlineControlDialog();");
    }

    /**
     * setPlayer 必须走 setTitleVisible 而不是直接 setTrackVisible：章节可见性参与轨道行计算，
     * 只抄轨道会让 onTitlesChanged 那次刷新对章节完全无效。
     */
    @Test
    public void dialogRefreshCoversTheTitleChipNotOnlyTheTrackRow() throws Exception {
        for (String flavor : List.of("mobile", "leanback")) {
            String source = read(dialog(flavor));
            assertMethodContains(source, "public void setPlayer()", "setTitleVisible();");
            assertMethodContains(source, "public void setTitleVisible()", "setTrackVisible();");
        }
    }

    private static Path dialog(String flavor) {
        return moduleRoot().resolve(Path.of("src", flavor, "java", "com", "fongmi",
                "android", "tv", "ui", "dialog", "ControlDialog.java"));
    }

    private static Path videoActivity(String flavor) {
        return moduleRoot().resolve(Path.of("src", flavor, "java", "com", "fongmi",
                "android", "tv", "ui", "activity", "VideoActivity.java"));
    }

    private static Path tmdbDetailActivity() {
        return moduleRoot().resolve(Path.of("src", "main", "java", "com", "fongmi",
                "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void assertMethodContains(String source, String signature, String... snippets) {
        int start = source.indexOf(signature);
        assertTrue("missing method " + signature, start >= 0);
        int open = source.indexOf('{', start);
        assertTrue("missing method body for " + signature, open >= 0);
        int depth = 0;
        int end = -1;
        for (int i = open; i < source.length(); i++) {
            char value = source.charAt(i);
            if (value == '{') depth++;
            else if (value == '}' && --depth == 0) {
                end = i + 1;
                break;
            }
        }
        assertTrue("unterminated method " + signature, end > open);
        String method = source.substring(start, end);
        for (String snippet : snippets) {
            assertTrue(signature + " must contain " + snippet, method.contains(snippet));
        }
    }

    private static Path moduleRoot() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return Path.of(".");
        return Path.of("app");
    }
}
