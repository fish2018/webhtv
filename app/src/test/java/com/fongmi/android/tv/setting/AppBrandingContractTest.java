package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppBrandingContractTest {

    @Test
    public void normalizeIconModeFallsBackToCurrentForUnknownValues() {
        assertEquals(AppBranding.ICON_CURRENT, AppBranding.normalizeIconMode(-1));
        assertEquals(AppBranding.ICON_CURRENT, AppBranding.normalizeIconMode(99));
        assertEquals(AppBranding.ICON_CURRENT, AppBranding.normalizeIconMode(AppBranding.ICON_CURRENT));
        assertEquals(AppBranding.ICON_HISTORY, AppBranding.normalizeIconMode(AppBranding.ICON_HISTORY));
        assertEquals(AppBranding.ICON_CURRENT, AppBranding.normalizeIconMode(2));
    }

    @Test
    public void launcherAliasUsesActivityNamespaceInsteadOfApplicationId() {
        String homeActivity = "com.fongmi.android.tv.ui.activity.HomeActivity";

        assertEquals(homeActivity + "Current",
                AppBranding.launcherAliasClassName(homeActivity, AppBranding.ICON_CURRENT));
        assertEquals(homeActivity + "History",
                AppBranding.launcherAliasClassName(homeActivity, AppBranding.ICON_HISTORY));
        assertEquals(homeActivity + "Current",
                AppBranding.launcherAliasClassName(homeActivity, 2));
    }

    @Test
    public void bothPersonalSettingsExposeAppBrandingEntry() throws Exception {
        String mobile = read("app/src/mobile/res/layout/fragment_setting_personal.xml");
        String leanback = read("app/src/leanback/res/layout/activity_setting_personal.xml");

        assertTrue(mobile.contains("@+id/appBranding"));
        assertTrue(mobile.contains("@+id/appBrandingText"));
        assertTrue(leanback.contains("@+id/appBranding"));
        assertTrue(leanback.contains("@+id/appBrandingText"));
    }

    @Test
    public void homeLogoAlwaysUsesSelectedBranding() throws Exception {
        String appBranding = read("app/src/main/java/com/fongmi/android/tv/setting/AppBranding.java");
        String home = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java");

        assertTrue(appBranding.contains("public static void applyLogo(@NonNull ImageView view)"));
        assertTrue(appBranding.contains("view.setImageDrawable(logoDrawable(view.getContext()));"));
        assertTrue(appBranding.contains("getActivityIcon(launcherIntent(context).getComponent())"));
        // 首帧落品牌图标后要让配置自带的线路 logo 覆盖上来，否则自定义线路图标失效
        assertTrue(appBranding.contains("ImgUtil.logo(view, logo);"));
        assertTrue(appBranding.contains("Prefers.getPrefers().edit().putInt(ICON_KEY, normalizeIconMode(mode)).commit();"));
        assertFalse(appBranding.contains("ICON_CUSTOM"));
        assertFalse(appBranding.contains("Shortcut"));
        assertFalse(appBranding.contains("CustomIcon"));
        assertTrue(appBranding.contains("R.mipmap.ic_launcher"));
        assertTrue(appBranding.contains("R.drawable.ic_launcher_history"));
        assertTrue(home.contains("AppBranding.applyLogo(mBinding.logo);"));
        assertFalse(home.contains("ImgUtil.logo(mBinding.logo);"));

        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/fragment/VodFragment.java");
        assertTrue(mobile.contains("AppBranding.applyLogo(mBinding.logo);"));
        assertFalse(mobile.contains("ImgUtil.logo(mBinding.logo);"));
    }

    @Test
    public void homeTitlePrefersSiteAndConfigNameOverAppName() throws Exception {
        String appBranding = read("app/src/main/java/com/fongmi/android/tv/setting/AppBranding.java");

        // 固定返回应用名会让所有站源都显示成「默影视」
        assertTrue(appBranding.contains("if (homeName != null && !homeName.trim().isEmpty()) return homeName;"));
        assertTrue(appBranding.contains("if (configName != null && !configName.trim().isEmpty()) return configName;"));
    }

    @Test
    public void homeLogoIsBrandedBeforeFirstFrame() throws Exception {
        String layout = read("app/src/leanback/res/layout/activity_home.xml");
        String home = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java");

        assertFalse(layout.contains("android:src=\"@drawable/ic_logo\""));

        int initView = home.indexOf("protected void initView(Bundle savedInstanceState)");
        int firstFrame = home.indexOf("runAfterFirstFrame(this::initAfterFirstFrame);", initView);
        int logo = home.indexOf("setLogo();", initView);

        assertTrue(initView > 0);
        assertTrue(logo > initView);
        assertTrue(logo < firstFrame);
    }

    @Test
    public void sharedBrandingLayoutUsesResourcesAvailableToBothFlavors() throws Exception {
        String layout = read("app/src/main/res/layout/activity_app_branding.xml");

        assertTrue(layout.contains("android:background=\"?attr/selectableItemBackground\""));
        assertTrue(layout.contains("@drawable/ic_action_back"));
        assertTrue(!layout.contains("@drawable/selector_item"));
        assertTrue(!layout.contains("@drawable/ic_detail_back"));
    }

    @Test
    public void sharedBrandingUiIsIconOnly() throws Exception {
        String layout = read("app/src/main/res/layout/activity_app_branding.xml");

        assertTrue(layout.contains("@string/app_branding_icon_select"));
        assertFalse(layout.contains("iconCustom"));
        assertFalse(layout.contains("selectImage"));
        assertFalse(layout.contains("customPreview"));
        assertFalse(layout.contains("@+id/nameLayout"));
        assertFalse(layout.contains("@string/app_branding_name_hint"));
        assertFalse(layout.contains("@string/app_branding_summary"));

        String chinese = read("app/src/main/res/values-zh-rCN/strings.xml");
        String english = read("app/src/main/res/values/strings.xml");
        String traditional = read("app/src/main/res/values-zh-rTW/strings.xml");

        assertTrue(english.contains("<string name=\"setting_app_branding\">App icon</string>"));
        assertTrue(chinese.contains("<string name=\"setting_app_branding\">APP 图标</string>"));
        assertTrue(traditional.contains("<string name=\"setting_app_branding\">APP 圖示</string>"));
        assertTrue(chinese.contains("<string name=\"app_name\">默影视</string>"));
        assertTrue(english.contains("<string name=\"app_name\">默影视</string>"));
        assertTrue(traditional.contains("<string name=\"app_name\">默影視</string>"));
        assertFalse(chinese.contains("app_name_history"));
        assertFalse(english.contains("app_name_history"));
        assertFalse(traditional.contains("app_name_history"));
        assertFalse(chinese.contains("app_branding_summary"));
        assertFalse(chinese.contains("app_branding_name_hint"));
        assertFalse(english.contains("app_branding_summary"));
        assertFalse(english.contains("app_branding_name_hint"));
        assertFalse(traditional.contains("app_branding_summary"));
        assertFalse(traditional.contains("app_branding_name_hint"));
    }

    @Test
    public void customIconFeatureIsRemoved() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/setting/AppBranding.java");
        String activity = read("app/src/main/java/com/fongmi/android/tv/ui/activity/AppBrandingActivity.java");
        String manifest = read("app/src/main/AndroidManifest.xml");

        assertFalse(source.contains("ICON_CUSTOM"));
        assertFalse(source.contains("Shortcut"));
        assertFalse(source.contains("CustomIcon"));
        assertFalse(activity.contains("imagePicker"));
        assertFalse(activity.contains("iconCustom"));
        assertFalse(manifest.contains("INSTALL_SHORTCUT"));
        assertFalse(manifest.contains("UNINSTALL_SHORTCUT"));
    }

    @Test
    public void mobileManifestRegistersSharedBrandingActivity() throws Exception {
        String mobile = read("app/src/mobile/AndroidManifest.xml");

        assertTrue(mobile.contains("android:name=\".ui.activity.AppBrandingActivity\""));
    }

    @Test
    public void bothProductManifestsExposeOnlyAliasLauncherEntries() throws Exception {
        String mobile = read("app/src/mobile/AndroidManifest.xml");
        String leanback = read("app/src/leanback/AndroidManifest.xml");

        assertLauncherAliases(mobile, false);
        assertLauncherAliases(leanback, true);

        assertEquals(2, countOccurrences(mobile, "android:label=\"@string/app_name\""));
        assertEquals(2, countOccurrences(leanback, "android:label=\"@string/app_name\""));
        assertEquals(0, countOccurrences(mobile, "@string/app_name_history"));
        assertEquals(0, countOccurrences(leanback, "@string/app_name_history"));
    }

    private static void assertLauncherAliases(String manifest, boolean leanback) {
        assertTrue(manifest.contains(".ui.activity.HomeActivityCurrent"));
        assertTrue(manifest.contains(".ui.activity.HomeActivityHistory"));
        assertTrue(manifest.contains("android:targetActivity=\".ui.activity.HomeActivity\""));
        assertTrue(manifest.contains("android:enabled=\"true\""));
        assertTrue(manifest.contains("android:enabled=\"false\""));
        if (leanback) assertTrue(manifest.contains("android.intent.category.LEANBACK_LAUNCHER"));
    }

    private static String read(String relative) throws Exception {
        Path path = Path.of(relative);
        if (!Files.exists(path) && relative.startsWith("app/")) path = Path.of(relative.substring(4));
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
