package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class HomeSiteSwitchClearsTypesTest {

    @Test
    public void siteSwitchClearsOldTypeButtonsBeforeHomeReload() throws Exception {
        String home = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java");
        String config = read("app/src/main/java/com/fongmi/android/tv/api/config/VodConfig.java");

        int setSite = method(home, "public void setSite(Site item)");
        String setHomeCall = "VodConfig.get().setHome(item);";
        int setSiteEnd = home.indexOf(setHomeCall, setSite) + setHomeCall.length();
        int configSetHome = method(config, "public void setHome(Site site)");
        String configRefreshCall = "RefreshEvent.home();";
        int configSetHomeEnd = config.indexOf(configRefreshCall, configSetHome) + configRefreshCall.length();
        int refresh = method(home, "public void onRefreshEvent(RefreshEvent event)");
        int refreshEnd = home.indexOf("@Subscribe", refresh + 1);
        int getVideo = method(home, "private void getVideo(boolean forceNative)");
        int getVideoEnd = method(home, "private void showWebOverlay()");
        int syncCategory = method(home, "private void syncCategorySite()");
        int syncCategoryEnd = method(home, "private void clearCategoryContent()");
        int clear = method(home, "private void clearCategoryContent()");
        int clearEnd = method(home, "private void clearStaleSiteTypes()");
        int helper = method(home, "private void clearStaleSiteTypes()");
        int helperEnd = method(home, "private void updateToolbarVisibility(boolean visible)");

        assertTrue(setSite >= 0);
        assertTrue(setSiteEnd > setSite);
        assertTrue(home.substring(setSite, setSiteEnd).contains("VodConfig.get().setHome(item);"));
        assertTrue(configSetHome >= 0);
        assertTrue(configSetHomeEnd > configSetHome);
        assertTrue(config.substring(configSetHome, configSetHomeEnd).contains("RefreshEvent.home();"));
        assertTrue(refresh >= 0);
        assertTrue(refreshEnd > refresh);
        assertTrue(home.substring(refresh, refreshEnd).contains("getVideo();"));
        assertTrue(getVideo >= 0);
        assertTrue(getVideoEnd > getVideo);
        assertTrue(home.substring(getVideo, getVideoEnd).contains("syncCategorySite();"));
        assertTrue(syncCategory >= 0);
        assertTrue(syncCategoryEnd > syncCategory);
        assertTrue(home.substring(syncCategory, syncCategoryEnd).contains("if (TextUtils.equals(mCategorySiteKey, key)) return;"));
        assertTrue(home.substring(syncCategory, syncCategoryEnd).contains("clearCategoryContent();"));
        assertTrue(clear >= 0);
        assertTrue(clearEnd > clear);
        assertTrue(helper >= 0);
        assertTrue(helperEnd > helper);
        assertTrue(home.substring(helper, helperEnd).contains("mTypeAdapter.addAll(Collections.emptyList());"));
        assertTrue(home.substring(helper, helperEnd).contains("mPendingTypePosition = -1;"));
        assertTrue(home.substring(helper, helperEnd).contains("mBinding.typeRecycler.setVisibility(View.GONE);"));
    }

    private static int method(String source, String signature) {
        return source.indexOf(signature);
    }

    private static String read(String relative) throws Exception {
        Path path = Path.of(relative);
        if (!Files.exists(path) && relative.startsWith("app/")) path = Path.of(relative.substring(4));
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
