package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class FollowingUiSourceTest {

    @Test
    public void followingTvHeaderScrollsWithContent() throws Exception {
        String layout = read("app/src/leanback/res/layout/activity_following.xml");

        assertTrue(layout.contains("androidx.core.widget.NestedScrollView"));
        assertTrue(layout.contains("android:nestedScrollingEnabled=\"false\""));
        assertTrue(layout.contains("android:layout_height=\"wrap_content\""));
    }

    @Test
    public void followingPageKeepsTvSafeTopInset() throws Exception {
        String layout = read("app/src/main/res/layout/activity_following.xml");

        assertTrue(layout.contains("android:paddingTop=\"28dp\""));
        assertFalse(layout.contains("android:paddingTop=\"12dp\""));
    }

    @Test
    public void mobileFollowingHeaderWrapsControlsAndKeepsSummaryVisible() throws Exception {
        String layout = read("app/src/mobile/res/layout/activity_following.xml");

        assertTrue(layout.contains("com.google.android.flexbox.FlexboxLayout"));
        assertTrue(layout.contains("android:id=\"@+id/actions\""));
        assertTrue(layout.contains("app:flexWrap=\"wrap\""));
        assertTrue(layout.contains("android:id=\"@+id/summary\""));
        assertTrue(layout.contains("android:layout_width=\"match_parent\"\n            android:layout_height=\"wrap_content\"\n            android:layout_marginTop=\"6dp\""));
    }

    @Test
    public void mobileFollowingCardWrapsAllActionsInsteadOfClippingThem() throws Exception {
        String layout = read("app/src/mobile/res/layout/item_following.xml");

        assertTrue(layout.contains("com.google.android.flexbox.FlexboxLayout"));
        assertTrue(layout.contains("app:flexWrap=\"wrap\""));
        assertTrue(layout.contains("app:justifyContent=\"flex_start\""));
        assertTrue(layout.contains("app:layout_flexGrow=\"0\""));
        assertTrue(layout.contains("app:layout_flexBasisPercent=\"@fraction/following_action_basis\""));
        assertTrue(read("app/src/mobile/res/values/following_fractions.xml").contains(">30%<"));
        assertTrue(read("app/src/mobile/res/values-sw600dp/following_fractions.xml").contains(">18%<"));
        assertTrue(layout.indexOf("@+id/error") < layout.indexOf("com.google.android.flexbox.FlexboxLayout"));
        assertFalse(layout.contains("HorizontalScrollView"));
        assertFalse(layout.contains("app:justifyContent=\"center\""));
        assertFalse(layout.contains("app:justifyContent=\"space_evenly\""));
        assertFalse(layout.contains("app:justifyContent=\"space_around\""));
        for (String id : List.of("nextSeason", "continuePlay", "check", "read", "notify", "sourceChange", "delete")) {
            assertTrue(layout.contains("android:id=\"@+id/" + id + "\""));
        }
    }

    @Test
    public void mobileBottomNavigationPlacesFollowingBetweenLiveAndSettings() throws Exception {
        String menu = read("app/src/mobile/res/menu/menu_nav.xml");
        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/activity/HomeActivity.java");

        assertTrue(menu.indexOf("@+id/live") < menu.indexOf("@+id/following"));
        assertTrue(menu.indexOf("@+id/following") < menu.indexOf("@+id/setting"));
        assertTrue(mobile.contains("FollowingSettings.isEnabled()"));
        assertTrue(mobile.contains("FollowingActivity.start(this"));
        assertTrue(mobile.contains("updateFollowingBadge()"));
        assertTrue(mobile.contains("FollowingPlaybackBridge.cachedUnreadCount()"));
        assertTrue(mobile.contains("FollowingPlaybackBridge.refreshUnreadCountAsync"));
        assertFalse(mobile.contains("FollowingStore.unreadCount()"));
    }

    @Test
    public void leanbackHomeButtonUsesStableIdEightAndDefaultOrder() throws Exception {
        String homeButton = read("app/src/leanback/java/com/fongmi/android/tv/bean/HomeButton.java");
        String func = read("app/src/leanback/java/com/fongmi/android/tv/bean/Func.java");
        String home = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java");

        assertTrue(homeButton.contains("new HomeButton(8, R.string.home_following)"));
        assertTrue(homeButton.contains("ALL = \"0,1,2,3,8,4,5,6,7,9\""));
        assertTrue(homeButton.indexOf("ids.add(\"8\")") > homeButton.indexOf("ids.add(\"3\")"));
        assertTrue(func.contains("ic_home_following"));
        assertTrue(home.contains("FollowingActivity.start(this, null)"));
    }

    @Test
    public void followingContinueUsesFlavorPlaybackRouterToPreserveSourceBinding() throws Exception {
        String activity = read("app/src/main/java/com/fongmi/android/tv/ui/activity/FollowingActivity.java");
        int method = activity.indexOf("public void onContinue(Following item, FollowingSource source)");
        int end = activity.indexOf("public void onFollowNextSeason", method);
        String body = activity.substring(method, end);

        assertTrue(body.contains("VideoActivity.startFromFollowingHistory(this, history)"));
        assertFalse(body.contains("VideoActivity.startFromHistory(this, history)"));
        assertFalse(body.contains("TmdbDetailActivity.startFromHistory(this, history)"));

        for (String path : List.of(
                "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java",
                "app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java")) {
            String video = read(path);
            int route = video.indexOf("public static void startFromFollowingHistory(Activity activity, History item)");
            int routeEnd = video.indexOf("public static void startFromHistory(Activity activity, History item)", route);
            String routeBody = video.substring(route, routeEnd);
            assertTrue(routeBody.contains("startFromHistory(activity, item)"));
        }
    }

    @Test
    public void followingScreenExposesOfficialSourceAndUserStatesSeparately() throws Exception {
        String adapter = read("app/src/main/java/com/fongmi/android/tv/ui/adapter/FollowingAdapter.java");
        assertTrue(adapter.contains("following_official"));
        assertTrue(adapter.contains("following_source"));
        assertTrue(adapter.contains("following_watched"));
        assertTrue(adapter.contains("following_unwatched"));
    }

    @Test
    public void followingHasUpdateFilterAndUserControlledSchedulerSwitch() throws Exception {
        String activity = read("app/src/main/java/com/fongmi/android/tv/ui/activity/FollowingActivity.java");
        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingPersonalFragment.java");
        String leanback = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingPersonalActivity.java");
        String mobileLayout = read("app/src/mobile/res/layout/fragment_setting_personal.xml");
        String leanbackLayout = read("app/src/leanback/res/layout/activity_setting_personal.xml");
        String func = read("app/src/leanback/java/com/fongmi/android/tv/bean/Func.java");
        String settings = read("app/src/main/java/com/fongmi/android/tv/following/FollowingSettings.java");
        String bridge = read("app/src/main/java/com/fongmi/android/tv/following/FollowingPlaybackBridge.java");
        String notifier = read("app/src/main/java/com/fongmi/android/tv/following/FollowingNotifier.java");

        assertTrue(activity.contains("FILTER_UPDATES"));
        assertTrue(activity.contains("FILTER_UNWATCHED"));
        assertTrue(activity.contains("FILTER_ENDED"));
        assertTrue(activity.contains("FILTER_FAILED"));
        assertTrue(activity.contains("following_filter_empty"));
        assertTrue(mobile.contains("FollowingScheduler.cancelAll(requireContext())"));
        assertTrue(leanback.contains("FollowingScheduler.cancelAll(this)"));
        assertTrue(mobile.contains("ConfigEvent.common();"));
        assertTrue(leanback.contains("ConfigEvent.common();"));
        assertTrue(mobileLayout.contains("@+id/following"));
        assertTrue(mobileLayout.contains("@+id/followingText"));
        assertTrue(settings.contains("Prefers.getBoolean(ENABLED, false)"));
        assertTrue(leanbackLayout.contains("@+id/following"));
        assertTrue(leanbackLayout.contains("@+id/followingText"));
        assertTrue(bridge.contains("volatile int cachedUnreadCount"));
        assertTrue(bridge.contains("FollowingStore.unreadCount()"));
        assertTrue(func.contains("FollowingPlaybackBridge.cachedUnreadCount()"));
        assertFalse(func.contains("FollowingStore.unreadCount()"));
        assertTrue(func.contains(" + \" · \" + unread"));
        assertTrue(notifier.contains("App.activity() != null"));
    }

    @Test
    public void newSeasonCreatesOnlyANewSeasonIdentity() throws Exception {
        String activity = read("app/src/main/java/com/fongmi/android/tv/ui/activity/FollowingActivity.java");
        String adapter = read("app/src/main/java/com/fongmi/android/tv/ui/adapter/FollowingAdapter.java");
        String layout = read("app/src/main/res/layout/item_following.xml");

        assertTrue(activity.contains("onFollowNextSeason"));
        assertTrue(activity.contains("FollowingIdentity.identityKey(item.seriesKey, item.latestReleasedSeason)"));
        assertTrue(activity.contains("next.trackedSeason = item.latestReleasedSeason"));
        assertTrue(adapter.contains("onFollowNextSeason"));
        assertTrue(layout.contains("@+id/nextSeason"));
    }

    @Test
    public void cardOpensDetailActionsForContinueCheckReadSourceAndCancel() throws Exception {
        String activity = read("app/src/main/java/com/fongmi/android/tv/ui/activity/FollowingActivity.java");
        String adapter = read("app/src/main/java/com/fongmi/android/tv/ui/adapter/FollowingAdapter.java");

        assertTrue(adapter.contains("onOpenDetail"));
        assertTrue(activity.contains("onOpenDetail"));
        assertTrue(activity.contains("following_continue"));
        assertTrue(activity.contains("following_check"));
        assertTrue(activity.contains("following_read"));
        assertTrue(activity.contains("following_change_source"));
        assertTrue(activity.contains("following_cancel"));
    }

    @Test
    public void followingReadStateIsManualAndSupportsMarkAllRead() throws Exception {
        String activity = read("app/src/main/java/com/fongmi/android/tv/ui/activity/FollowingActivity.java");
        String adapter = read("app/src/main/java/com/fongmi/android/tv/ui/adapter/FollowingAdapter.java");
        String leanbackLayout = read("app/src/leanback/res/layout/activity_following.xml");
        String mobileLayout = read("app/src/mobile/res/layout/activity_following.xml");
        String strings = read("app/src/main/res/values-zh-rCN/strings.xml");

        assertFalse(activity.contains("VISIBLE_READ_DELAY_MS"));
        assertFalse(activity.contains("markVisibleReadNow"));
        assertFalse(activity.contains("addOnScrollListener(new RecyclerView.OnScrollListener()"));
        assertTrue(activity.contains("binding.readAll.setOnClickListener(view -> markAllRead())"));
        assertTrue(activity.contains("adapter.markReadLocally(keys)"));
        assertTrue(activity.contains("FollowingPlaybackBridge.markReadAllAsync(keys"));
        assertTrue(adapter.contains("getItems()"));
        assertTrue(leanbackLayout.contains("@+id/readAll"));
        assertTrue(mobileLayout.contains("@+id/readAll"));
        int checkId = leanbackLayout.indexOf("android:id=\"@+id/check\"");
        int readAllId = leanbackLayout.indexOf("android:id=\"@+id/readAll\"", checkId);
        int filterId = leanbackLayout.indexOf("android:id=\"@+id/filter\"", readAllId);
        assertTrue(leanbackLayout.substring(checkId, readAllId).contains("android:nextFocusRight=\"@id/readAll\""));
        assertTrue(leanbackLayout.substring(readAllId, filterId).contains("android:nextFocusLeft=\"@id/check\""));
        assertTrue(leanbackLayout.substring(readAllId, filterId).contains("android:nextFocusRight=\"@id/filter\""));
        assertTrue(leanbackLayout.substring(filterId).contains("android:nextFocusLeft=\"@id/readAll\""));
        assertTrue(strings.contains("<string name=\"following_read_all\">全部已读</string>"));
    }

    @Test
    public void detailAndPlaybackScreensWireFollowingActionsOffTheMainThread() throws Exception {
        String detail = read("app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java");
        String store = read("app/src/main/java/com/fongmi/android/tv/following/FollowingStore.java");
        String header = read("app/src/main/res/layout/view_tmdb_header.xml");
        String mobile = read("app/src/mobile/res/layout/activity_video.xml")
                + read("app/src/mobile/res/layout-land/activity_video.xml")
                + read("app/src/mobile/res/layout-sw600dp/activity_video.xml")
                + read("app/src/mobile/res/layout-sw600dp-land/activity_video.xml");
        String leanback = read("app/src/leanback/res/layout/activity_video.xml");
        String mobileActivity = read("app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
        String leanbackActivity = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
        String followingActivity = read("app/src/main/java/com/fongmi/android/tv/ui/activity/FollowingActivity.java");
        String followingAdapter = read("app/src/main/java/com/fongmi/android/tv/ui/adapter/FollowingAdapter.java");
        String strings = read("app/src/main/res/values-zh-rCN/strings.xml");

        assertTrue(detail.contains("FollowingPlaybackBridge.findAsync"));
        assertTrue(detail.contains("FollowingPlaybackBridge.resolveTmdbAsync"));
        assertTrue(detail.contains("followingSnapshot()"));
        assertTrue(detail.contains("item.enabled = true"));
        assertFalse(detail.contains("FollowingStore.findByTmdb"));
        assertFalse(detail.contains("FollowingStore.findBySource"));
        assertTrue(header.contains("@+id/tmdbFollowing"));
        assertTrue(mobile.contains("@+id/following"));
        assertTrue(leanback.contains("@+id/following"));
        assertTrue(followingInitiallyGone(read("app/src/mobile/res/layout/activity_video.xml")));
        assertTrue(followingInitiallyGone(read("app/src/mobile/res/layout-land/activity_video.xml")));
        assertTrue(followingInitiallyGone(read("app/src/mobile/res/layout-sw600dp/activity_video.xml")));
        assertTrue(followingInitiallyGone(read("app/src/mobile/res/layout-sw600dp-land/activity_video.xml")));
        assertTrue(followingInitiallyGone(read("app/src/leanback/res/layout/activity_video.xml")));
        assertTrue(followingInitiallyGone(read("app/src/main/res/layout/view_tmdb_header.xml")));
        assertTrue(mobileActivity.contains("onFollowing()"));
        assertTrue(leanbackActivity.contains("onFollowing()"));
        assertTrue(followingActivity.contains("FollowingPlaybackBridge.deleteAsync"));
        assertFalse(followingActivity.contains("FollowingStore.delete(item.identityKey)"));
        assertTrue(followingActivity.contains("Task.execute(() -> {\n            List<Following> items = FollowingStore.list();"));
        assertFalse(followingActivity.contains("VISIBLE_READ_DELAY_MS"));
        assertFalse(followingActivity.contains("markVisibleReadNow()"));
        assertTrue(followingAdapter.contains("item.hasUpdate && item.unwatchedCount > 0"));
        assertTrue(followingAdapter.contains("markReadLocally"));
        assertTrue(strings.contains("<string name=\"following_read\">标记已读</string>"));
        assertTrue(mobile.contains("ic_home_following_shadow"));
        assertFalse(mobile.contains("drawable/ic_home_following\""));
        assertTrue(store.contains("history == null || !FollowingSettings.isEnabled()"));
        assertTrue(store.contains("migrateSourceToTmdb"));
    }

    private static String read(String relative) throws Exception {
        Path path = Path.of(relative);
        if (!Files.exists(path) && relative.startsWith("app/")) path = Path.of(relative.substring(4));
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static boolean followingInitiallyGone(String xml) {
        int id = xml.indexOf("@+id/following");
        if (id < 0) id = xml.indexOf("@+id/tmdbFollowing");
        if (id < 0) return false;
        int next = xml.indexOf(">", id);
        return next > id && xml.substring(id, next).contains("android:visibility=\"gone\"");
    }
}
