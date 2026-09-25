package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class TmdbRuntimeCredentialWiringTest {

    @Test
    public void adapterRefreshesEffectiveConfigAndInvalidatesOldSubscriptionTasks() throws Exception {
        String adapter = read("src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java");

        assertTrue(adapter.contains("tmdbConfig = TmdbConfig.effectiveCurrent()"));
        assertTrue(adapter.contains("SubscriptionTmdbCredentialStore.isCurrent(subscriptionScope)"));
        assertTrue(adapter.contains("public void invalidateSubscription()"));
        assertTrue(adapter.indexOf("refreshRuntimeConfig();", adapter.indexOf("public void autoMatch")) > 0);
        int invalidate = adapter.indexOf("public void invalidateSubscription()");
        int refresh = adapter.indexOf("refreshRuntimeConfig();", invalidate);
        int scope = adapter.indexOf("subscriptionScope =", invalidate);
        assertTrue("subscription invalidation must refresh effective credentials before capturing the new scope",
                refresh > invalidate && scope > refresh);
        int cachedLoad = adapter.indexOf("public void load(TmdbItem item, Vod vod, TmdbDetailCache.Entry cached)");
        int cachedRefresh = adapter.indexOf("refreshRuntimeConfig();", cachedLoad);
        int cachedReset = adapter.indexOf("resetLoadState();", cachedLoad);
        assertTrue("cached detail loading must refresh the effective credential before resetting state",
                cachedRefresh > cachedLoad && cachedReset > cachedRefresh);
    }

    @Test
    public void mobileAndLeanbackUseEffectiveConfigAndInvalidateOnVodConfigChange() throws Exception {
        String mobile = read("src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
        String leanback = read("src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");

        assertTrue(mobile.contains("TmdbConfig tmdbConfig = TmdbConfig.effectiveCurrent()"));
        assertTrue(leanback.contains("TmdbConfig tmdbConfig = TmdbConfig.effectiveCurrent()"));
        assertTrue(mobile.contains("mTmdbUIAdapter.invalidateSubscription()"));
        assertTrue(leanback.contains("mTmdbUIAdapter.invalidateSubscription()"));
    }

    @Test
    public void detailActivityRefreshesAfterSourceIngressAndReloadsOnSubscriptionChange() throws Exception {
        String detail = read("src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java");
        int source = detail.indexOf("Result result = SiteApi.detailContent(key, id)");
        int effective = detail.indexOf("tmdbConfig = TmdbConfig.effectiveCurrent()", source);

        assertTrue(effective > source);
        assertTrue(detail.contains("public void onConfigEvent(ConfigEvent event)"));
        assertTrue(detail.contains("loadContent(null)"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
