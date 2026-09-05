package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class GithubProxyTest {

    @Test
    public void applyPrefixesGithubRawUrl() {
        String url = "https://raw.githubusercontent.com/FGBLH/GHK/refs/heads/main/a.json";

        assertEquals("https://ghfast.top/" + url, GithubProxy.apply(url, "https://ghfast.top/"));
    }

    @Test
    public void applyLeavesGithubDownloadsAloneWhenDisabled() {
        String releaseUrl = "https://github.com/Silent1566/webhtv/releases/latest/download/mobile-arm64_v8a.apk";
        String rawUrl = "https://raw.githubusercontent.com/FGBLH/GHK/refs/heads/main/a.json";

        assertEquals(releaseUrl, GithubProxy.apply(releaseUrl, "https://ghfast.top/", false));
        assertEquals(rawUrl, GithubProxy.apply(rawUrl, "https://ghfast.top/", false));
    }

    @Test
    public void applyPrefixesGithubReleaseDownloadUrl() {
        String url = "https://github.com/YaChengMu/webhtv/releases/latest/download/mobile-arm64_v8a.apk";

        assertEquals("https://ghfast.top/" + url, GithubProxy.apply(url, "https://ghfast.top/"));
    }

    @Test
    public void applyPrefixesFixedUpdateChannelUrl() {
        String url = Github.getChannelAsset("mobile-arm64_v8a-beta.json");

        assertEquals("https://ghfast.top/" + url, GithubProxy.apply(url, "https://ghfast.top/"));
    }

    @Test
    public void applyLeavesGithubPageUrlAlone() {
        String url = "https://github.com/YaChengMu/webhtv";

        assertEquals(url, GithubProxy.apply(url, "https://ghfast.top/"));
    }

    @Test
    public void applyLeavesNonGithubUrlAlone() {
        String url = "https://example.com/a.json";

        assertEquals(url, GithubProxy.apply(url, "https://ghfast.top/"));
    }

    @Test
    public void applyDoesNotDoubleProxy() {
        String url = "https://ghfast.top/https://raw.githubusercontent.com/FGBLH/GHK/refs/heads/main/a.json";

        assertEquals(url, GithubProxy.apply(url, "https://ghfast.top/"));
    }

    @Test
    public void normalizeConfigKeepsFirstValidSources() {
        assertEquals("https://ghfast.top/\nhttps://99z.top/", GithubProxy.normalizeConfig("bad\nhttps://ghfast.top\nhttps://99z.top/"));
    }

    @Test
    public void addSourcesDeduplicatesExistingSources() {
        String configured = GithubProxy.addSources(
                "https://ghfast.top/\nhttps://example.com/\nhttps://ghfast.top",
                "https://example.com/\nhttps://99z.top"
        );

        assertEquals("https://ghfast.top/\nhttps://example.com/\nhttps://99z.top/",
                configured);
    }

    @Test
    public void probeUrlUsesNormalizedSourceAndFixedTarget() {
        assertEquals("https://ghfast.top/https://github.com/Silent1566/webhtv/releases/download/update-channel/update.json",
                GithubProxy.probeUrl("https://ghfast.top"));
    }

    @Test
    public void configRewritesFullUrlAndStripScheme() {
        String url = "https://github.com/Silent1566/webhtv/releases/download/v1/app.apk";

        assertEquals("https://ghfast.top/" + url,
                GithubProxy.config("https://ghfast.top", GithubProxy.MODE_FULL_URL, true).rewrite(url));
        assertEquals("https://github.chenc.dev/github.com/Silent1566/webhtv/releases/download/v1/app.apk",
                GithubProxy.config("https://github.chenc.dev", GithubProxy.MODE_STRIP_SCHEME, true).rewrite(url));
    }

    @Test
    public void disabledConfigUsesDirectTarget() {
        String url = "https://github.com/Silent1566/webhtv/releases/download/v1/app.apk";

        assertEquals(url, GithubProxy.config("https://ghfast.top", GithubProxy.MODE_FULL_URL, false).rewrite(url));
    }

    @Test
    public void configRejectsUnsafeTarget() {
        assertThrows(IllegalArgumentException.class,
                () -> GithubProxy.config("https://ghfast.top", GithubProxy.MODE_FULL_URL, true)
                        .rewrite("http://github.com/Silent1566/webhtv/releases/download/v1/app.apk"));
    }
}
