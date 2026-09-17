package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.utils.HlsManifestCleaner;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BuiltinHlsRuleFixtureTest {

    @Test
    public void builtinRulesAreDefaultOffAndHavePositiveAndNegativeFixtures() throws Exception {
        HlsRulePackage rulePackage = HlsRulePackage.parse(Files.readString(
                projectRoot().resolve("app/src/main/assets/rules/hls_rules.json"), StandardCharsets.UTF_8));

        assertEquals(3, rulePackage.getRules().size());
        verify(rulePackage, "builtin.hls.baofeng.preroll", "https://s5.bfzycdn.example/video/index.m3u8", 3.0);
        verify(rulePackage, "builtin.hls.liangzi.preroll", "https://vip.lz-cdn.example/video/index.m3u8", 6.433333);
        verify(rulePackage, "builtin.hls.feifan.preroll", "https://vip.ffzy-play.example/video/index.m3u8", 6.666667);
    }

    private static void verify(HlsRulePackage rulePackage, String id, String playlistUrl, double duration) {
        HlsAdRule rule = rulePackage.getRules().stream().filter(item -> id.equals(item.getId())).findFirst().orElseThrow();
        assertFalse(rule.isEnabledByDefault());

        String positive = manifest(duration, true);
        HlsManifestCleaner.Result removed = HlsManifestCleaner.clean(playlistUrl, positive, List.of(rule.compile()));
        assertTrue(id, removed.changed());
        assertFalse(id, removed.manifest().contains("ad.ts"));

        String negative = manifest(duration, false);
        HlsManifestCleaner.Result retained = HlsManifestCleaner.clean(playlistUrl, negative, List.of(rule.compile()));
        assertFalse(id, retained.changed());
        assertEquals(id, negative, retained.manifest());

        HlsManifestCleaner.Result wrongHost = HlsManifestCleaner.clean(
                "https://video.example.com/index.m3u8", positive, List.of(rule.compile()));
        assertFalse(id, wrongHost.changed());
        assertEquals(id, positive, wrongHost.manifest());
    }

    private static String manifest(double duration, boolean discontinuity) {
        return "#EXTM3U\n"
                + (discontinuity ? "#EXT-X-DISCONTINUITY\n" : "")
                + "#EXTINF:" + duration + ",\n"
                + "ad.ts\n"
                + "#EXTINF:8.0,\nmain-1.ts\n"
                + "#EXTINF:8.0,\nmain-2.ts\n"
                + "#EXTINF:8.0,\nmain-3.ts\n"
                + "#EXT-X-ENDLIST\n";
    }

    private static Path projectRoot() {
        return Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
    }
}
