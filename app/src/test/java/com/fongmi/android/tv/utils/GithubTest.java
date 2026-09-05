package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GithubTest {

    @Test
    public void updateChannelAssetsShareProxyableGithubReleaseDomain() {
        String stable = Github.getChannelAsset("mobile-arm64_v8a.json");
        String beta = Github.getChannelAsset("mobile-arm64_v8a-beta.json");

        assertEquals("github.com", URI.create(stable).getHost());
        assertEquals(URI.create(stable).getHost(), URI.create(beta).getHost());
        assertTrue(stable.contains("/releases/download/update-channel/"));
        assertTrue(beta.contains("/releases/download/update-channel/"));
        assertEquals("https://github.com/YaChengMu/webhtv/releases/download/update-channel/mobile-arm64_v8a.json", stable);
        assertEquals("https://github.com/YaChengMu/webhtv/releases/download/update-channel/mobile-arm64_v8a-beta.json", beta);
    }


    @Test
    public void cnbManifestPathRemainsAnIndependentFallback() {
        assertEquals(
                "https://cnb.cool/fish2035/webhtv-release/-/git/raw/main/apk/mobile-arm64_v8a-beta.json",
                Github.getCnbMirrorAsset("mobile-arm64_v8a-beta.json"));
    }
}
