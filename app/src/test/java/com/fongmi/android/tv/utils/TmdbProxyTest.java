package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbProxyTest {

    @Test
    public void normalizesValidEndpointAndAddressPool() {
        assertEquals("https://mirror.example.com/tmdb", TmdbProxy.normalizeConfig("mirror.example.com/tmdb/3"));
        assertEquals("https://a.example.com",
                TmdbProxy.normalizeConfig("https://a.example.com;https://a.example.com, b.example.com"));
    }

    @Test
    public void removesKnownInvalidRoutes() {
        assertEquals("", TmdbProxy.normalizeConfig("worker-pool"));
        assertEquals("", TmdbProxy.normalizeConfig("https://tmdb.nastool.org"));
        assertEquals("", TmdbProxy.normalizeConfig("ftp://mirror.example.com"));
        assertTrue(TmdbProxy.isRemovedRoute("worker-pool"));
        assertTrue(TmdbProxy.isRemovedRoute("https://tmdb.nastool.org/"));
    }

    @Test
    public void exposesOnlyTestedBuiltInApiAndImageRoutes() {
        assertEquals(3, TmdbProxy.apiOptions().size());
        assertEquals(4, TmdbProxy.imageOptions().size());
        assertEquals(TmdbProxy.AUTO, TmdbProxy.apiOptions().get(0).value);
        assertEquals(TmdbProxy.AUTO, TmdbProxy.imageOptions().get(0).value);
        assertEquals(TmdbProxy.ITV666, TmdbProxy.valueForInput("itv666 API 代理", TmdbProxy.apiOptions()));
        assertEquals("itv666 图片代理", TmdbProxy.displayImage(TmdbProxy.ITV666));
        assertEquals("wsrv.nl 图片代理", TmdbProxy.displayImage(TmdbProxy.WSRV_IMAGE));
        assertEquals("https://wsrv.nl/?url=https://image.tmdb.org/t/p/w342/poster.png",
                TmdbProxy.imageUrl(TmdbProxy.imageBaseFor(TmdbProxy.WSRV_IMAGE, "w342"), "/poster.png"));
    }

    @Test
    public void recognizesOfficialApiAndImageHosts() {
        assertTrue(TmdbProxy.isOfficialApiHost("https://api.tmdb.org/3"));
        assertTrue(TmdbProxy.isOfficialApiHost("https://api.themoviedb.org"));
        assertTrue(TmdbProxy.isOfficialImageHost("https://image.tmdb.org/t/p/w342"));
        assertFalse(TmdbProxy.isOfficialApiHost("https://mirror.example.com"));
        assertFalse(TmdbProxy.isOfficialImageHost("https://mirror.example.com"));
    }
    @Test
    public void autoAndWsrvRoutesNormalizeForRuntime() {
        assertTrue(TmdbProxy.isAuto(TmdbProxy.AUTO));
        assertEquals("https://api.tmdb.org/3", TmdbProxy.apiBaseFor(TmdbProxy.OFFICIAL_API));
        assertEquals("https://wsrv.nl/?url=https://image.tmdb.org/t/p/w342/poster.png",
                TmdbProxy.imageUrl(TmdbProxy.imageBaseFor(TmdbProxy.WSRV_IMAGE, "w342"), "/poster.png"));
    }

}
