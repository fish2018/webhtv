package com.fongmi.android.tv.player;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PlaybackRouteTest {

    private PlaybackRouteRegistry.Registration registration;

    @Before
    public void setUp() {
        registration = PlaybackRouteRegistry.registerAppService(9988, PlaybackRouteRegistry.AppOwner.MAIN_SERVER);
    }

    @After
    public void tearDown() {
        registration.close();
    }

    @Test
    public void classifiesAppLocalServiceByOwnedPort() {
        PlaybackRoute.Resolution resolution = PlaybackRoute.resolve("http://127.0.0.1:9988/media");
        assertEquals(PlaybackRoute.APP_LOCAL_SERVICE, resolution.route());
        assertEquals(PlaybackRoute.Owner.APP_MAIN_SERVER, resolution.owner());
        assertEquals(PlaybackRoute.Evidence.REGISTERED_APP_PORT, resolution.evidence());
        assertEquals(PlaybackRoute.APP_LOCAL_SERVICE, PlaybackRoute.classify("http://localhost:9988/proxy"));
    }

    @Test
    public void classifiesRegisteredDynamicHlsProxyAsAppOwned() {
        try (PlaybackRouteRegistry.Registration hls = PlaybackRouteRegistry.registerAppService(7788, PlaybackRouteRegistry.AppOwner.HLS_PROXY)) {
            PlaybackRoute.Resolution resolution = PlaybackRoute.resolve("http://127.0.0.1:7788/mpv/index.m3u8?token=secret");

            assertEquals(PlaybackRoute.APP_LOCAL_SERVICE, resolution.route());
            assertEquals(PlaybackRoute.Owner.APP_HLS_PROXY, resolution.owner());
            assertEquals(PlaybackRoute.Confidence.CONFIRMED, resolution.confidence());
        }
    }

    @Test
    public void classifiesRegisteredMultiThreadProxyAsAppOwned() {
        try (PlaybackRouteRegistry.Registration proxy = PlaybackRouteRegistry.registerAppService(
                7790,
                PlaybackRouteRegistry.AppOwner.MULTI_THREAD_PROXY)) {
            PlaybackRoute.Resolution resolution = PlaybackRoute.resolve("http://127.0.0.1:7790/v1/stream/opaque");

            assertEquals(PlaybackRoute.APP_LOCAL_SERVICE, resolution.route());
            assertEquals(PlaybackRoute.Owner.APP_MULTI_THREAD_PROXY, resolution.owner());
            assertEquals(PlaybackRoute.Confidence.CONFIRMED, resolution.confidence());
        }
    }

    @Test
    public void classifiesOtherLoopbackPortAsExternalProxy() {
        PlaybackRoute.Resolution resolution = PlaybackRoute.resolve("http://127.0.0.1:7777/video");
        assertEquals(PlaybackRoute.EXTERNAL_LOOPBACK_PROXY, resolution.route());
        assertEquals(PlaybackRoute.Owner.EXTERNAL_OR_UNKNOWN_LOOPBACK, resolution.owner());
        assertEquals(PlaybackRoute.Confidence.INFERRED, resolution.confidence());
        assertEquals(PlaybackRoute.EXTERNAL_LOOPBACK_PROXY, PlaybackRoute.classify("http://localhost:8080/video"));
    }

    @Test
    public void classifiesRemoteHttpSeparately() {
        assertEquals(PlaybackRoute.DIRECT_REMOTE_HTTP, PlaybackRoute.classify("https://cdn.example.com/movie.mkv"));
        assertEquals(PlaybackRoute.OTHER, PlaybackRoute.classify("file:///storage/movie.mkv"));
    }

    @Test
    public void preservesLegacyRouteWhileExposingLanAndLocalLocationFacts() {
        PlaybackRoute.Resolution lan = PlaybackRoute.resolve("http://192.168.1.12/movie.mp4");
        assertEquals(PlaybackRoute.DIRECT_REMOTE_HTTP, lan.route());
        assertEquals(PlaybackRoute.Location.LAN_PRIVATE, lan.location());
        assertEquals(PlaybackRoute.Evidence.PRIVATE_HOST, lan.evidence());
        assertEquals(PlaybackRoute.Location.LOCAL, PlaybackRoute.resolve("file:///storage/movie.mkv").location());
    }

    @Test
    public void externalProxyAlwaysUsesOnePreloadThreadWhileAppRouteKeepsCustomValue() {
        assertEquals(1, PlaybackRoute.EXTERNAL_LOOPBACK_PROXY.effectivePreloadThreads(4));
        assertEquals(4, PlaybackRoute.APP_LOCAL_SERVICE.effectivePreloadThreads(10));
    }

    @Test
    public void directRemoteKeepsConfiguredFiniteConcurrency() {
        assertEquals(1, PlaybackRoute.DIRECT_REMOTE_HTTP.effectivePreloadThreads(1));
        assertEquals(2, PlaybackRoute.DIRECT_REMOTE_HTTP.effectivePreloadThreads(2));
        assertEquals(3, PlaybackRoute.DIRECT_REMOTE_HTTP.effectivePreloadThreads(3));
        assertEquals(4, PlaybackRoute.DIRECT_REMOTE_HTTP.effectivePreloadThreads(4));
        assertEquals(4, PlaybackRoute.DIRECT_REMOTE_HTTP.effectivePreloadThreads(10));
    }

    @Test
    public void appOwnedAndDirectRoutesDefaultToOneThread() {
        assertEquals(1, PlaybackRoute.APP_LOCAL_SERVICE.effectivePreloadThreads(1));
        assertEquals(1, PlaybackRoute.DIRECT_REMOTE_HTTP.effectivePreloadThreads(1));
    }

    @Test
    public void unknownRouteUsesConservativeSingleThread() {
        assertEquals(1, PlaybackRoute.OTHER.effectivePreloadThreads(4));
    }

    @Test
    public void staleRegistrationCannotRemoveNewOwnerForSamePort() {
        PlaybackRouteRegistry.Registration first = PlaybackRouteRegistry.registerAppService(8899, PlaybackRouteRegistry.AppOwner.MAIN_SERVER);
        PlaybackRouteRegistry.Registration second = PlaybackRouteRegistry.registerAppService(8899, PlaybackRouteRegistry.AppOwner.HLS_PROXY);

        first.close();
        assertEquals(PlaybackRoute.Owner.APP_HLS_PROXY, PlaybackRoute.resolve("http://127.0.0.1:8899/video").owner());
        second.close();
        assertEquals(PlaybackRoute.EXTERNAL_LOOPBACK_PROXY, PlaybackRoute.classify("http://127.0.0.1:8899/video"));
    }

    @Test
    public void routeLogSummaryDoesNotContainHostPathOrToken() {
        String summary = PlaybackRoute.resolve("https://secret.example.com/private/movie.mkv?token=abc").logSummary();

        assertFalse(summary.contains("secret.example.com"));
        assertFalse(summary.contains("private"));
        assertFalse(summary.contains("token"));
        assertFalse(summary.contains("abc"));
    }
}
