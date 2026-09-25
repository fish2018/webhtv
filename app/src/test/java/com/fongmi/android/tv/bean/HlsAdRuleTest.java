package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.utils.HlsManifestCleaner;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HlsAdRuleTest {

    @Test
    public void compilesJsonRuleIntoIndependentHlsMatcher() {
        String json = "{"
                + "\"id\":\"test.preroll.v1\","
                + "\"playlistHostSuffixes\":[\"video.example.com\"],"
                + "\"hostSuffixes\":[\"ads.example.com\"],"
                + "\"segmentUrlRegex\":[\"/preroll/\"],"
                + "\"minDuration\":6.5,"
                + "\"maxDuration\":7.5,"
                + "\"minimumSignals\":3,"
                + "\"enabled\":true"
                + "}";
        HlsAdRule configured = new Gson().fromJson(json, HlsAdRule.class);
        String manifest = "#EXTM3U\n"
                + "#EXTINF:7.0,\nhttps://ads.example.com/preroll/ad.ts\n"
                + "#EXTINF:8.0,\nmain-1.ts\n"
                + "#EXTINF:8.0,\nmain-2.ts\n"
                + "#EXT-X-ENDLIST\n";

        HlsManifestCleaner.Result result = HlsManifestCleaner.clean(
                "https://video.example.com/index.m3u8", manifest, List.of(configured.compile()));

        assertTrue(result.changed());
        assertFalse(result.manifest().contains("ad.ts"));
    }
}
