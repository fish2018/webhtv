package com.fongmi.android.tv.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WebHomeInteractionWiringTest {

    @Test
    public void eclipseSourcePillIsFocusableAndOpensSitePicker() throws Exception {
        String home = read("src/main/assets/webhome/eclipse.html");

        assertTrue(home.contains("<button class=\"source-pill\" data-focus=\"source\" id=\"sourceName\""));
        assertTrue(home.contains(".source-pill:focus"));
        assertTrue(home.contains("callApp('openSite', '此操作需要在应用内使用')"));
    }

    @Test
    public void openSiteIsExposedByBothSdkVariantsAndBothBridges() throws Exception {
        String controller = read("src/main/java/com/fongmi/android/tv/web/HomeWebController.java");
        String legacyBridge = read("src/main/java/com/fongmi/android/tv/web/HomeWebBridge.java");
        String themeBridge = read("src/main/java/com/fongmi/android/tv/web/WebHomeThemeBridge.java");

        assertTrue(controller.contains("openSite:function(){return invoke('app.openSite',{});}"));
        assertTrue(controller.contains("openSite:()=>invoke('app.openSite',{})"));
        assertTrue(controller.contains("openSite:window.fongmi.app.openSite"));
        assertTrue(legacyBridge.contains("case \"app.openSite\" -> openSite();"));
        assertTrue(themeBridge.contains("case \"app.openSite\" -> openSite(active);"));
    }

    @Test
    public void leanbackConfirmKeyClicksFocusedWebNodeAndBothFlavorsOpenPicker() throws Exception {
        String leanback = read("src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java");
        String mobile = read("src/mobile/java/com/fongmi/android/tv/ui/fragment/VodFragment.java");

        assertTrue(ordered(leanback,
                "if (KeyUtil.isEnterKey(event)) return dispatchWebConfirmKey(event);",
                "if (mWeb.dispatchKeyEvent(event)) return true;"));
        assertTrue(leanback.contains("mWeb.dispatchFocusedClick();"));
        assertTrue(leanback.contains("mWeb.dispatchFocusedLongPress();"));
        assertTrue(leanback.contains("public void openSite() {\n        showDialog();"));
        assertTrue(mobile.contains("public void openSite() {\n        SiteDialog.create().change().show(this);"));
    }

    @Test
    public void revokedBridgeForcesReloadBeforeThePageCanBeReused() {
        assertTrue(HomeWebController.requiresPageReload(false, false, "home", "home", "site", "site"));
        assertFalse(HomeWebController.requiresPageReload(false, true, "home", "home", "site", "site"));
    }

    @Test
    public void leanbackDoesNotRefreshNativeCardSizeOrRevealAnUnreadyWebHome() throws Exception {
        String leanback = read("src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java");

        assertTrue(leanback.contains("case SIZE:\n                if (mWeb != null && mWeb.isVisible()) return;\n                getVideo();"));
        assertTrue(leanback.contains("if (mWeb.isReady()) {\n                mBinding.progressLayout.showContent();\n                showWebOverlay();\n            }"));
        assertTrue(leanback.contains("else {\n                hideWebOverlay();\n                mBinding.progressLayout.showProgress();\n            }"));
    }

    @Test
    public void bridgeRejectionsAreDiagnosableInsteadOfSilent() throws Exception {
        String bridge = read("src/main/java/com/fongmi/android/tv/web/HomeWebBridge.java");

        assertTrue(bridge.contains("invoke failed method=%s error=%s session=%s current=%s"));
    }

    @Test
    public void leanbackHidesStaleWebHomeUntilBridgeIsReady() throws Exception {
        String leanback = read("src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java");

        assertTrue(leanback.contains("public void onWebLoading() {\n        cancelWebConfirmKey();\n        hideWebOverlay();\n        mBinding.progressLayout.showProgress();"));
        assertFalse(leanback.contains("public void onWebLoading() {\n        showWebOverlay();"));
        assertTrue(leanback.contains("mWeb != null && mWeb.isVisible() && mBinding.webOverlay.getVisibility() == View.VISIBLE"));
    }

    private static boolean ordered(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second, Math.max(0, firstIndex + first.length()));
        return firstIndex >= 0 && secondIndex > firstIndex;
    }

    private static String read(String relative) throws Exception {
        Path path = Path.of(relative);
        if (!Files.exists(path)) path = Path.of("app").resolve(relative);
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}