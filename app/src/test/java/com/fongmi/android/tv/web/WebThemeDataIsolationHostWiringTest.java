package com.fongmi.android.tv.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WebThemeDataIsolationHostWiringTest {

    @Test
    public void mobileHostOnlyManipulatesTheControllersCurrentWebView() throws Exception {
        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/fragment/VodFragment.java");

        assertFalse(mobile.contains("mBinding.homeWeb.setVisibility"));
        String margin = section(mobile, "private void setHomeWebTopMargin", "private void requestNormalChrome");
        assertFalse(margin.contains("mBinding.homeWeb"));
        assertTrue(mobile.contains("mWeb.hide()"));
        assertTrue(mobile.contains("mWeb.setTopMargin(margin)"));
    }

    @Test
    public void detailAndDebugHostsDoNotKeepUsingDestroyedBindingViews() throws Exception {
        String detail = read("app/src/main/java/com/fongmi/android/tv/ui/activity/WebThemeDetailActivity.java");
        String debug = read("app/src/main/java/com/fongmi/android/tv/ui/dialog/WebHomeExtensionDebugDialog.java");

        assertFalse(detail.contains("mBinding.web.requestFocus()"));
        assertFalse(detail.contains("mBinding.web.postDelayed"));
        assertFalse(detail.contains("mBinding.web.removeCallbacks"));
        assertTrue(detail.contains("controller.requestFocus(\"detail-ready\")"));
        assertTrue(detail.contains("mBinding.getRoot().postDelayed"));
        assertTrue(debug.contains("controller.show()"));
        assertFalse(debug.contains("binding.web.setVisibility(View.VISIBLE)"));
    }

    private static String section(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        assertTrue("Missing source token: " + startToken, start >= 0);
        assertTrue("Missing source token after " + startToken + ": " + endToken, end > start);
        return source.substring(start, end);
    }
    private static String read(String path) throws Exception {
        Path file = Path.of(path);
        if (!Files.exists(file) && path.startsWith("app/")) file = Path.of(path.substring(4));
        return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
