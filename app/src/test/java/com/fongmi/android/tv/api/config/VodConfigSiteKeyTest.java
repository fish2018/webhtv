package com.fongmi.android.tv.api.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

/** Keeps saved following/history source routes compatible with configuration key-case changes. */
public class VodConfigSiteKeyTest {

    @Test
    public void getSiteFallsBackToCaseInsensitiveKey() throws Exception {
        Method method = VodConfig.class.getDeclaredMethod("getSite", String.class);
        method.setAccessible(true);
        VodConfig config = new VodConfig();
        setSites(config, List.of(site("Wogg"), site("Libvio")));

        assertEquals("Wogg", siteKey(method, config, "wogg"));
        assertEquals("Libvio", siteKey(method, config, "LIBVIO"));
        assertEquals("Wogg", siteKey(method, config, "Wogg"));
        assertTrue(((com.fongmi.android.tv.bean.Site) method.invoke(config, "missing")).isEmpty());
    }

    private static String siteKey(Method method, VodConfig config, String key) throws Exception {
        return ((com.fongmi.android.tv.bean.Site) method.invoke(config, key)).getKey();
    }

    private static void setSites(VodConfig config, List<com.fongmi.android.tv.bean.Site> sites) throws Exception {
        Method setter = VodConfig.class.getDeclaredMethod("setSites", List.class);
        setter.setAccessible(true);
        setter.invoke(config, sites);
    }

    private static com.fongmi.android.tv.bean.Site site(String key) {
        com.fongmi.android.tv.bean.Site site = new com.fongmi.android.tv.bean.Site();
        site.setKey(key);
        return site;
    }
}
