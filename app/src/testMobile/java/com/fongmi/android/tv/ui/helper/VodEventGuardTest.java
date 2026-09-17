package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VodEventGuardTest {

    @Test
    public void matchesSameSiteAndId() {
        assertTrue(VodEventGuard.matches(vod("site-a", "123"), "site-a", "123"));
    }

    @Test
    public void matchesLoadedVodIdWhenIntentKeepsStablePushUrl() {
        String intentId = "https://pan.quark.cn/s/a082fb2c7d82|一拳超人";
        String loadedId = "1$4383$1";

        assertTrue(VodEventGuard.matches(vod("push_agent", loadedId), "push_agent", intentId, loadedId));
        assertFalse(VodEventGuard.matches(vod("push_agent", "2$999$1"), "push_agent", intentId, loadedId));
        assertFalse(VodEventGuard.matches(vod("other_site", loadedId), "push_agent", intentId, loadedId));
    }

    @Test
    public void matchesIntentPageSuffix() {
        assertTrue(VodEventGuard.matches(vod("site-a", "123"), "site-a", "123/40"));
        assertEquals("123", VodEventGuard.stripPageSuffix("123/40"));
    }

    @Test
    public void preservesLeadingSlashIds() {
        assertEquals("/index.php/id/1", VodEventGuard.stripPageSuffix("/index.php/id/1"));
    }

    @Test
    public void rejectsDifferentSiteOrId() {
        assertFalse(VodEventGuard.matches(vod("site-a", "123"), "site-b", "123"));
        assertFalse(VodEventGuard.matches(vod("site-a", "123"), "site-a", "124"));
    }


    @Test
    public void alignsIdentityLessCachedVodToCurrentPlayback() {
        String currentId = "https://pan.quark.cn/s/a082fb2c7d82|一拳超人";
        Vod cached = vod("", "/1$4383$1");

        assertFalse(VodEventGuard.matches(cached, "push_agent", currentId));
        assertEquals(cached, VodEventGuard.alignCachedIdentity(cached, "push_agent", currentId));
        assertEquals(currentId, cached.getId());
        assertTrue(VodEventGuard.matches(cached, "push_agent", currentId));
    }

    @Test
    public void doesNotAlignIdentifiedStaleVod() {
        Vod stale = vod("site-a", "123");

        VodEventGuard.alignCachedIdentity(stale, "site-b", "456");

        assertEquals("123", stale.getId());
        assertFalse(VodEventGuard.matches(stale, "site-b", "456"));
    }

    @Test
    public void allowsMissingSourceIdentityFields() {
        assertTrue(VodEventGuard.matches(vod("", ""), "site-a", "123"));
    }

    @Test
    public void matchesFileUriWhenEventAddsLeadingSlash() {
        String id = "file:///data/user/0/app/files/video.mp4";
        assertTrue(VodEventGuard.matches(vod("", "/" + id), "push_agent", id));
        assertEquals(id, VodEventGuard.stripPageSuffix(id));
        assertEquals(id, VodEventGuard.stripPageSuffix("/" + id));
    }

    @Test
    public void matchesPushUriWhenCurrentIdContainsDisplayTitle() {
        String url = "https://www.youtube.com/watch?v=V-O-uBaHk3c";
        assertTrue(VodEventGuard.matches(vod("", "/" + url), "push_agent", url + "|Final Trailer"));
    }

    @Test
    public void preservesNetworkUriPaths() {
        String id = "https://example.com/video/1";
        assertEquals(id, VodEventGuard.stripPageSuffix(id));
        assertFalse(VodEventGuard.matches(vod("site-a", "https://example.com/video/2"), "site-a", id));
    }

    private static Vod vod(String siteKey, String id) {
        Vod vod = new Vod();
        vod.setSite(Site.get(siteKey, siteKey));
        vod.setId(id);
        return vod;
    }
}
