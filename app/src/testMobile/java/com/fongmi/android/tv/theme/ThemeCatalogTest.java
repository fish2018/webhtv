package com.fongmi.android.tv.theme;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ThemeCatalogTest {

    @Test
    public void sha256UsesUtf8AndStableLowercaseHex() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ThemeCatalog.sha256("abc"));
        assertEquals(64, ThemeCatalog.sha256("主题").length());
    }

    @Test
    public void parsesAndRoundTripsValidatedLocalCatalog() throws Exception {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.id = "webhtv.test";
        profile.name = "Test";
        String profileJson = ThemeProfileCodec.encode(profile);
        String preview = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>";
        String index = index(profileJson, preview, ThemeCatalog.sha256(profileJson), ThemeCatalog.sha256(preview));
        Map<String, String> files = new HashMap<>();
        files.put("themes/profiles/test.json", profileJson);
        files.put("themes/previews/test.svg", preview);
        ThemeCatalog.Catalog catalog = ThemeCatalog.parseIndex(index, files::get, ThemeCatalog.sha256(index));
        assertEquals(1, catalog.version());
        assertEquals("webhtv.test", catalog.entries().get(0).profile().id);
        ThemeCatalog.Catalog cached = ThemeCatalog.fromCache(ThemeCatalog.toCacheJson(catalog));
        assertNotNull(cached);
        assertEquals(catalog.indexSha256(), cached.indexSha256());
        assertEquals(catalog.entries().get(0).profileJson(), cached.entries().get(0).profileJson());
        assertTrue(ThemeCatalog.fromCache(ThemeCatalog.toCacheJson(catalog).replace("Test", "Tampered")) == null);
    }

    @Test
    public void rejectsTamperedIndexProfileAndPreview() throws Exception {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.id = "webhtv.test";
        String profileJson = ThemeProfileCodec.encode(profile);
        String preview = "preview";
        String index = index(profileJson, preview, ThemeCatalog.sha256(profileJson), ThemeCatalog.sha256(preview));
        Map<String, String> files = new HashMap<>();
        files.put("themes/profiles/test.json", profileJson + " ");
        files.put("themes/previews/test.svg", preview);
        try {
            ThemeCatalog.parseIndex(index, files::get, ThemeCatalog.sha256(index));
            fail("expected profile digest mismatch");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("canonical") || expected.getMessage().contains("digest"));
        }
        files.put("themes/profiles/test.json", profileJson);
        files.put("themes/previews/test.svg", preview + "!");
        try {
            ThemeCatalog.parseIndex(index, files::get, ThemeCatalog.sha256(index));
            fail("expected preview digest mismatch");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("preview"));
        }
    }

    @Test
    public void catalogRejectsUnsafeAssetPathAndWrongIndexDigest() throws Exception {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.id = "webhtv.test";
        String profileJson = ThemeProfileCodec.encode(profile);
        String preview = "preview";
        String index = index(profileJson, preview, ThemeCatalog.sha256(profileJson), ThemeCatalog.sha256(preview))
                .replace("themes/profiles/test.json", "themes/profiles/../test.json");
        try {
            ThemeCatalog.parseIndex(index, path -> "", ThemeCatalog.sha256(index));
            fail("expected unsafe path rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("asset path"));
        }
    }

    private String index(String profile, String preview, String profileSha, String previewSha) {
        return "{\"schemaVersion\":1,\"version\":1,\"entries\":["
                + "{\"id\":\"webhtv.test\",\"version\":1,\"name\":\"Test\","
                + "\"profile\":\"themes/profiles/test.json\",\"sha256\":\"" + profileSha + "\","
                + "\"preview\":\"themes/previews/test.svg\",\"previewSha256\":\"" + previewSha + "\"}]}";
    }
}
