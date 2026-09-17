package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class HomeSiteLockSourceTest {

    @Test
    public void settingDefaultsToUnlockedAndPersistsThePreference() throws Exception {
        String setting = read(source("main", "java", "com", "fongmi", "android", "tv", "setting", "Setting.java"));

        assertTrue(setting.contains("public static boolean isHomeSiteLock()"));
        assertTrue(setting.contains("return Prefers.getBoolean(\"home_site_lock\", false);"));
        assertTrue(setting.contains("public static void putHomeSiteLock(boolean homeSiteLock)"));
        assertTrue(setting.contains("Prefers.put(\"home_site_lock\", homeSiteLock);"));
    }

    @Test
    public void personalSettingsExposeTheHomeSiteLockSwitch() throws Exception {
        String activity = read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "SettingPersonalActivity.java"));
        String layout = read(source("leanback", "res", "layout", "activity_setting_personal.xml"));

        assertTrue(activity.contains("mBinding.homeSiteLock.setOnClickListener(this::setHomeSiteLock);"));
        assertTrue(activity.contains("mBinding.homeSiteLockText.setText(getSwitch(Setting.isHomeSiteLock()));"));
        assertTrue(activity.contains("Setting.putHomeSiteLock(!Setting.isHomeSiteLock());"));
        assertTrue(layout.contains("android:id=\"@+id/homeSiteLock\""));
        assertTrue(layout.contains("android:id=\"@+id/homeSiteLockText\""));
        assertTrue(layout.contains("android:text=\"@string/setting_home_site_lock\""));

        assertTrue(read(source("main", "res", "values", "strings.xml")).contains("<string name=\"setting_home_site_lock\">Home source lock</string>"));
        assertTrue(read(source("main", "res", "values-zh-rCN", "strings.xml")).contains("<string name=\"setting_home_site_lock\">首页源锁定</string>"));
        assertTrue(read(source("main", "res", "values-zh-rTW", "strings.xml")).contains("<string name=\"setting_home_site_lock\">首頁源鎖定</string>"));
    }

    @Test
    public void lockConsumesBothHorizontalKeysWithoutSwitchingSites() throws Exception {
        String title = read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "custom", "CustomTitleView.java"));
        String hasEvent = method(title, "private boolean hasEvent(KeyEvent event)", "@Override\n    protected void onFocusChanged");
        String dispatch = method(title, "public boolean dispatchKeyEvent(KeyEvent event)", "private void onKeyDown");

        assertTrue(title.contains("private boolean isSiteSwitchKey(KeyEvent event)"));
        assertTrue(hasEvent.contains("if (getHome().isEmpty()) return false;"));
        assertTrue(hasEvent.contains("if (KeyUtil.isUpKey(event)) return !coolDown;"));
        assertTrue(hasEvent.contains("return isSiteSwitchKey(event);"));
        assertTrue("locked left/right keys must be consumed instead of entering default focus navigation",
                dispatch.contains("if (Setting.isHomeSiteLock() && isSiteSwitchKey(event)) return true;"));
        assertTrue(title.contains("listener.setSite(getSite(false))"));
        assertTrue(title.contains("listener.setSite(getSite(true))"));
    }

    @Test
    public void homeTitleShowsAndRefreshesTheLockIndicator() throws Exception {
        String title = read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "custom", "CustomTitleView.java"));
        String home = read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "HomeActivity.java"));
        String layout = read(source("leanback", "res", "layout", "activity_home.xml"));
        String icon = read(source("leanback", "res", "drawable", "ic_home_lock.xml"));

        assertTrue(title.contains("public void setSiteLocked(boolean locked)"));
        assertTrue(title.contains("setCompoundDrawablesRelativeWithIntrinsicBounds(locked ? R.drawable.ic_home_lock : 0, 0, 0, 0);"));
        assertTrue(layout.contains("android:drawablePadding=\"6dp\""));
        assertTrue(icon.contains("<vector"));
        assertTrue(home.contains("private void syncHomeSiteLock()"));
        assertTrue(home.contains("mBinding.title.setSiteLocked(Setting.isHomeSiteLock());"));

        String initView = method(home, "protected void initView(Bundle savedInstanceState)", "private void initAfterFirstFrame()");
        String onResume = method(home, "protected void onResume()", "protected void onPause()");
        assertTrue(initView.contains("syncHomeSiteLock();"));
        assertTrue(onResume.contains("syncHomeSiteLock();"));
    }

    private static String method(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue("Missing method: " + start, from >= 0);
        assertTrue("Missing method boundary: " + end, to > from);
        return source.substring(from, to);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static Path source(String... parts) {
        Path path = Path.of("src");
        for (String part : parts) path = path.resolve(part);
        if (Files.exists(path)) return path;
        path = Path.of("app", "src");
        for (String part : parts) path = path.resolve(part);
        return path;
    }
}
