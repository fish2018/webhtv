package com.fongmi.android.tv.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.setting.TmdbSitePolicy;

import org.junit.Test;

public class WebHomeInlineVodStoreTest {

    @Test
    public void shouldRefreshYoutubeEpisode_acceptsFirst403Or410() {
        assertTrue(WebHomeInlineVodStore.shouldRefreshYoutubeEpisode(
                WebHomeInlineVodStore.KEY, "https://WWW.YouTube.COM/watch?v=test", 403, false));
        assertTrue(WebHomeInlineVodStore.shouldRefreshYoutubeEpisode(
                WebHomeInlineVodStore.KEY, "https://youtu.be/test", 410, false));
    }

    @Test
    public void shouldRefreshYoutubeEpisode_rejectsRepeatedOrUnrelatedErrors() {
        assertFalse(WebHomeInlineVodStore.shouldRefreshYoutubeEpisode(
                WebHomeInlineVodStore.KEY, "https://www.youtube.com/watch?v=test", 403, true));
        assertFalse(WebHomeInlineVodStore.shouldRefreshYoutubeEpisode(
                WebHomeInlineVodStore.KEY, "https://www.youtube.com/watch?v=test", 404, false));
        assertFalse(WebHomeInlineVodStore.shouldRefreshYoutubeEpisode(
                "other", "https://www.youtube.com/watch?v=test", 403, false));
        assertFalse(WebHomeInlineVodStore.shouldRefreshYoutubeEpisode(
                WebHomeInlineVodStore.KEY, "https://example.com/video", 403, false));
    }

    @Test
    public void tmdbPolicy_usesInlineOriginSite() {
        String id = "inline-youtube-test";
        WebHomeInlineVodStore.rememberOriginSite(id, Site.get("youtube-html", "油管 HTML"));

        TmdbConfig blockedByKey = TmdbConfig.objectFrom("{\"excludeKeywordsConfigured\":true,\"disabledSites\":[\"youtube-html\"]}");
        TmdbConfig blockedByName = TmdbConfig.objectFrom("{\"excludeKeywordsConfigured\":true,\"disabledSites\":[\"油管 HTML\"]}");
        TmdbConfig allowed = TmdbConfig.objectFrom("{\"excludeKeywordsConfigured\":true,\"disabledSites\":[\"other-site\"]}");
        TmdbConfig blockedInlineRoute = TmdbConfig.objectFrom("{\"excludeKeywordsConfigured\":true,\"disabledSites\":[\"webhome_inline\"]}");

        assertFalse(TmdbSitePolicy.isEnabled(blockedByKey, WebHomeInlineVodStore.KEY, id));
        assertFalse(TmdbSitePolicy.isEnabled(blockedByName, WebHomeInlineVodStore.KEY, id));
        assertTrue(TmdbSitePolicy.isEnabled(allowed, WebHomeInlineVodStore.KEY, id));
        assertFalse(TmdbSitePolicy.isEnabled(blockedInlineRoute, WebHomeInlineVodStore.KEY, "missing-inline-id"));
    }

}
