package com.fongmi.android.tv.history;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class GlobalHistorySettingSourceTest {

    @Test
    public void settingDefinesThreeStateGlobalHistoryModeDefaultingOff() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/setting/Setting.java");

        assertTrue(source.contains("GLOBAL_HISTORY_OFF = 0"));
        assertTrue(source.contains("GLOBAL_HISTORY_AUTO = 1"));
        assertTrue(source.contains("GLOBAL_HISTORY_SEARCH = 2"));
        assertTrue(source.contains("Prefers.getInt(\"global_history_mode\", GLOBAL_HISTORY_OFF)"));
        assertTrue(source.contains("Prefers.put(\"global_history_mode\""));
    }

    @Test
    public void bothPersonalSettingScreensExposeGlobalHistoryMode() throws Exception {
        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingPersonalFragment.java");
        String leanback = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingPersonalActivity.java");
        String mobileLayout = read("app/src/mobile/res/layout/fragment_setting_personal.xml");
        String leanbackLayout = read("app/src/leanback/res/layout/activity_setting_personal.xml");

        assertTrue(mobile.contains("mBinding.globalHistory"));
        assertTrue(mobile.contains("select_global_history_mode"));
        assertTrue(leanback.contains("mBinding.globalHistory"));
        assertTrue(leanback.contains("select_global_history_mode"));
        assertTrue(mobileLayout.contains("@+id/globalHistory"));
        assertTrue(leanbackLayout.contains("@+id/globalHistory"));
    }

    @Test
    public void backupIncludesBothGlobalAndTmdbHistoryPreferences() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/bean/Backup.java");

        assertTrue(source.contains("\"global_history_mode\""));
        assertTrue(source.contains("\"history_aggregation_by_tmdb\""));
    }

    private static String read(String relative) throws Exception {
        Path path = Path.of(relative);
        if (!Files.exists(path) && relative.startsWith("app/")) path = Path.of(relative.substring(4));
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
