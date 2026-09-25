package com.fongmi.android.tv.api;

import com.fongmi.android.tv.bean.Result;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SiteApiTest {

    @Test
    public void resolvePushPlayerUrlStripsLocalTitleSuffix() {
        String id = "file:///storage/emulated/0/AIUI/S60217-19165505-3.mp4|本地视频：01";

        assertEquals("file:///storage/emulated/0/AIUI/S60217-19165505-3.mp4", SiteApi.resolvePushPlayerUrl(id));
    }

    @Test
    public void resolvePushPlayerUrlStripsLocalIdentifierSuffix() {
        String id = "file:///storage/emulated/0/AIUI/S60217-19165505-3.mp4|01";

        assertEquals("file:///storage/emulated/0/AIUI/S60217-19165505-3.mp4", SiteApi.resolvePushPlayerUrl(id));
    }

    @Test
    public void resolvePushPlayerUrlKeepsHeaderSuffix() {
        String id = "https://cdn.test/movie.mp4|User-Agent=WebHTV";

        assertEquals(id, SiteApi.resolvePushPlayerUrl(id));
    }

    @Test
    public void isLocalFileUrlAcceptsFileSchemeCaseInsensitively() {
        assertTrue(SiteApi.isLocalFileUrl("file:///storage/emulated/0/Download/movie.mp4"));
        assertTrue(SiteApi.isLocalFileUrl("FILE:///storage/emulated/0/Download/movie.mp4"));
    }

    @Test
    public void isLocalFileUrlRejectsRemoteUrl() {
        assertFalse(SiteApi.isLocalFileUrl("https://cdn.test/movie.mp4"));
    }

    @Test
    public void pushedWebPageRequiresSniffing() {
        assertTrue(SiteApi.shouldSniffPushUrl("https://shdy2.com/play/50292-1-1.html", false));
    }

    @Test
    public void pushedRemoteMediaStaysDirect() {
        assertFalse(SiteApi.shouldSniffPushUrl("https://cdn.test/video/movie.m3u8?token=demo", true));
    }

    @Test
    public void pushedLocalFileStaysDirect() {
        assertFalse(SiteApi.shouldSniffPushUrl("file:///storage/emulated/0/Download/movie.mp4", false));
    }

    @Test
    public void pushedNonHttpMediaStaysDirect() {
        assertFalse(SiteApi.shouldSniffPushUrl("rtmp://stream.test/live/channel", false));
    }

    @Test
    public void failedConfiguredPushFallsBackToBuiltInHandling() {
        Result result = new Result();
        result.setUrl("https://shdy2.com/play/50292-1-1.html");
        result.setMsg("Request failed with status code 403");

        assertTrue(SiteApi.shouldFallbackPushSiteResult(SiteApi.PUSH, result));
    }

    @Test
    public void successfulConfiguredPushKeepsConfiguredResult() {
        Result result = new Result();
        result.setUrl("https://cdn.test/video/movie.m3u8");

        assertFalse(SiteApi.shouldFallbackPushSiteResult(SiteApi.PUSH, result));
    }

    @Test
    public void regularSiteErrorsNeverUsePushFallback() {
        Result result = new Result();
        result.setMsg("upstream error");

        assertFalse(SiteApi.shouldFallbackPushSiteResult("regular_site", result));
    }
}
