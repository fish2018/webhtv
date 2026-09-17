package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TouchOptimizationHelperSourceTest {

    @Test
    public void storesOriginalStateOnceAndRestoresRecyclerChildren() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/helper/TouchOptimizationHelper.java");

        assertTrue(source.contains("WeakHashMap"));
        assertTrue(source.contains("containsKey"));
        assertTrue(source.contains("sync(view)"));
        assertFalse(source.contains("View.generateViewId"));
    }

    @Test
    public void inputViewsAreSkippedAsSubtrees() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/helper/TouchOptimizationHelper.java");

        assertTrue(source.contains("if (isInputView(view)) return;"));
        assertTrue(source.contains("view.onCheckIsTextEditor()"));
    }

    @Test
    public void mobileFlavorDoesNotTraverseViews() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/helper/TouchOptimizationHelper.java");

        assertTrue(source.contains("if (!Util.isLeanback()) return;"));
    }

    @Test
    public void touchOptimizationToggleCanEnableModeWithOneTouch() throws Exception {
        Path layout = path("app/src/leanback/res/layout/activity_setting_personal.xml");
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(layout.toFile());
        var nodes = document.getElementsByTagName("androidx.appcompat.widget.LinearLayoutCompat");

        for (int i = 0; i < nodes.getLength(); i++) {
            var attributes = nodes.item(i).getAttributes();
            var id = attributes.getNamedItem("android:id");
            if (id == null || !"@+id/touchOptimization".equals(id.getNodeValue())) continue;
            assertTrue("true".equals(attributes.getNamedItem("android:focusable").getNodeValue()));
            assertTrue("false".equals(attributes.getNamedItem("android:focusableInTouchMode").getNodeValue()));
            return;
        }
        throw new AssertionError("touchOptimization row not found");
    }

    @Test
    public void touchOptimizationRowIsRemovedFromRootSettingScreen() throws Exception {
        Path layout = path("app/src/leanback/res/layout/activity_setting.xml");
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(layout.toFile());

        assertFalse(hasId(document, "@+id/touchOptimization"));
        assertFalse(hasId(document, "@+id/touchOptimizationText"));
    }

    @Test
    public void activityAppliesOptimizationToNewContentAndRegistersFragmentsEarly() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/base/BaseActivity.java");
        int register = source.indexOf("registerFragmentLifecycleCallbacks();");
        int content = source.indexOf("setContentView(getBinding().getRoot());");
        int method = source.indexOf("public void setContentView(View view)");
        int methodEnd = source.indexOf("protected FragmentActivity getActivity()", method);
        int wall = source.indexOf("addCustomWall();", method);
        int sync = source.indexOf("TouchOptimizationHelper.sync(getWindow().getDecorView());", method);

        assertTrue(register >= 0 && content >= 0 && register < content);
        assertTrue(method >= 0 && methodEnd > method);
        assertTrue(method < wall && wall < sync && sync < methodEnd);
    }

    @Test
    public void recyclerListenersAndLeanbackFocusModesAreTouchGated() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/helper/TouchOptimizationHelper.java");

        assertTrue(source.contains("if (optimize) attachListener(recycler);"));
        assertTrue(source.contains("setOnTouchInterceptListener"));
        assertTrue(source.contains("setFocusScrollStrategy"));
        assertTrue(source.contains("setFocusSearchDisabled"));
        assertTrue(source.contains("onInterceptTouchEvent"));
        assertTrue(source.contains("FOCUS_SCROLL_ITEM"));
        assertTrue(source.contains("state.touchSettling = true;"));
        assertTrue(source.contains("restoreGrid(grid);"));
        assertTrue(source.contains("onScrollStateChanged"));
    }

    @Test
    public void tvSearchRestoresPerSitePixelPositionAndPlayerUsesTvTouchContract() throws Exception {
        String collect = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java");
        String home = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java");
        String video = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
        String widget = read("app/src/leanback/res/layout/view_widget_vod.xml");
        assertTrue(collect.contains("Map<String, SearchPosition> mSearchPositions"));
        assertTrue(collect.contains("saveSearchPosition(getActiveSiteKey())"));
        assertTrue(collect.contains("getTop() - mBinding.recycler.getPaddingTop()"));
        assertTrue(collect.contains("scrollToPositionWithOffset"));
        assertTrue(collect.contains("mSearchPositions.clear()"));
        assertTrue(collect.contains("TouchOptimizationHelper.isTouchActive(parent)"));
        assertTrue(collect.contains("mBoundSearchSiteKey"));
        assertTrue(collect.contains("if (!siteKey.equals(mBoundSearchSiteKey))"));
        assertTrue(home.contains("mTypeSelectionFromTouch"));
        assertTrue(home.contains("if (Setting.isHomeVodAutoLoad() && !mTypeSelectionFromTouch) scheduleTypeSwitch(position);"));
        assertFalse(collect.contains("private void scrollSearchToTop()"));
        assertTrue(video.contains("private boolean onVideoTouch(View view, MotionEvent event)"));
        assertTrue(video.contains("if (!Setting.isTouchOptimized()) return mKeyDown.onTouchEvent(event);"));
        assertTrue(video.contains("TV_TOUCH_HORIZONTAL"));
        assertTrue(video.contains("applyTvBrightness"));
        assertTrue(video.contains("applyTvVolume"));
        assertTrue(video.contains("mBinding.widget.brightProgress"));
        assertTrue(video.contains("mBinding.widget.volumeProgress"));
        assertTrue(video.contains("} else if (action == MotionEvent.ACTION_CANCEL) {"));
        assertTrue(video.contains("if (action == MotionEvent.ACTION_UP) {"));
        assertTrue(video.contains("if (seeking && player() != null) onSeekEnd(mTvTouchSeek)"));
        assertTrue(video.contains("mTvTouchDownX <= Math.max(1, mBinding.video.getWidth()) / 2f"));
        assertTrue(video.contains("mKeyDown.releaseSpeed()"));
        assertTrue(widget.contains("@+id/bright"));
        assertTrue(widget.contains("@+id/volume"));
    }

    @Test
    public void highFrequencyDirectDialogsAndPlaybackSheetsAreSynchronized() throws Exception {
        String helper = read("app/src/main/java/com/fongmi/android/tv/ui/helper/TouchOptimizationHelper.java");
        String lightDialog = read("app/src/main/java/com/fongmi/android/tv/ui/dialog/LightDialog.java");
        String siteDialog = read("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/SiteDialog.java");
        String videoActivity = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");

        assertTrue(helper.contains("public static void sync(Dialog dialog)"));
        assertTrue(lightDialog.contains("TouchOptimizationHelper.sync(dialog);"));
        assertTrue(siteDialog.contains("TouchOptimizationHelper.sync(directDialog);"));
        assertTrue(videoActivity.contains("private void syncAudioDialog(Dialog dialog)"));
        assertTrue(videoActivity.contains("TouchOptimizationHelper.sync(dialog);"));
    }

    @Test
    public void touchOptimizationToggleCanEnableModeWithOneTouchMobile() throws Exception {
        Path layout = path("app/src/mobile/res/layout/fragment_setting_personal.xml");
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(layout.toFile());
        var nodes = document.getElementsByTagName("androidx.appcompat.widget.LinearLayoutCompat");

        for (int i = 0; i < nodes.getLength(); i++) {
            var attributes = nodes.item(i).getAttributes();
            var id = attributes.getNamedItem("android:id");
            if (id == null || !"@+id/touchOptimization".equals(id.getNodeValue())) continue;
            assertTrue("true".equals(attributes.getNamedItem("android:focusable").getNodeValue()));
            assertTrue("false".equals(attributes.getNamedItem("android:focusableInTouchMode").getNodeValue()));
            return;
        }
        throw new AssertionError("touchOptimization row not found");
    }

    @Test
    public void personalSettingsBindTouchOptimizationToggle() throws Exception {
        String leanback = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingPersonalActivity.java");
        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingPersonalFragment.java");
        String root = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingActivity.java");

        assertTrue(leanback.contains("mBinding.touchOptimization.setOnClickListener(this::setTouchOptimization);"));
        assertTrue(leanback.contains("mBinding.touchOptimizationText.setText(getSwitch(Setting.isTouchOptimized()));"));
        assertTrue(leanback.contains("TouchOptimizationHelper.sync(getWindow().getDecorView());"));
        assertTrue(mobile.contains("mBinding.touchOptimization.setOnClickListener(this::setTouchOptimization);"));
        assertTrue(mobile.contains("mBinding.touchOptimizationText.setText(getSwitch(Setting.isTouchOptimized()));"));
        assertFalse(root.contains("mBinding.touchOptimization"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(path(path), StandardCharsets.UTF_8);
    }

    private static Path path(String value) {
        Path direct = Path.of(value);
        return Files.exists(direct) ? direct : Path.of(value.substring("app/".length()));
    }

    private static Node findById(Document document, String id) {
        var nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            var attribute = nodes.item(i).getAttributes().getNamedItem("android:id");
            if (attribute != null && id.equals(attribute.getNodeValue())) return nodes.item(i);
        }
        throw new AssertionError(id + " not found");
    }

    private static int elementIndex(Node node) {
        int index = 0;
        for (Node sibling = node.getPreviousSibling(); sibling != null; sibling = sibling.getPreviousSibling()) {
            if (sibling.getNodeType() == Node.ELEMENT_NODE) index++;
        }
        return index;
    }

    private static boolean hasId(Document document, String id) {
        var nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            var attribute = nodes.item(i).getAttributes().getNamedItem("android:id");
            if (attribute != null && id.equals(attribute.getNodeValue())) return true;
        }
        return false;
    }
}
