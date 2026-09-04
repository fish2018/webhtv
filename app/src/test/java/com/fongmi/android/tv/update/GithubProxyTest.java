package com.fongmi.android.tv.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class GithubProxyTest {

    private static final String ASSET = "https://github.com/fish2018/webhtv/releases/download/v1/mobile-arm64_v8a.apk";

    @Test
    public void rewritesKnownProxyModes() {
        assertEquals(
                "https://github.chenc.dev/github.com/fish2018/webhtv/releases/download/v1/mobile-arm64_v8a.apk",
                GithubProxy.resolve("github_chenc", "", "").rewrite(ASSET));
        assertEquals(
                "https://gh.acmsz.top/https://github.com/fish2018/webhtv/releases/download/v1/mobile-arm64_v8a.apk",
                GithubProxy.resolve("gh_acmsz", "", "").rewrite(ASSET));
    }

    @Test
    public void rejectsUnsafeCustomOrigins() {
        assertThrows(IllegalArgumentException.class, () -> GithubProxy.resolve(GithubProxy.CUSTOM, "http://proxy.example", GithubProxy.MODE_FULL_URL));
        assertThrows(IllegalArgumentException.class, () -> GithubProxy.resolve(GithubProxy.CUSTOM, "https://user:pass@proxy.example", GithubProxy.MODE_FULL_URL));
        assertThrows(IllegalArgumentException.class, () -> GithubProxy.resolve(GithubProxy.CUSTOM, "https://proxy.example/path", GithubProxy.MODE_FULL_URL));
    }
}
