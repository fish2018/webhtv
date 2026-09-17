package com.fongmi.android.tv.player;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PlaybackResourceClassifierTest {

    private PlaybackRouteRegistry.Registration registration;

    @Before
    public void setUp() {
        registration = PlaybackRouteRegistry.registerAppService(9977, PlaybackRouteRegistry.AppOwner.HLS_PROXY);
    }

    @After
    public void tearDown() {
        registration.close();
    }

    @Test
    public void classifiesLocalLanRemoteAndLoopbackWithoutDns() {
        assertEquals(PlaybackAutoContext.PathKind.LOCAL,
                PlaybackResourceClassifier.classifyRequest("file:///sdcard/movie.mkv", null, null).playerPath());
        assertEquals(PlaybackAutoContext.PathKind.LAN_PRIVATE,
                PlaybackResourceClassifier.classifyRequest("http://192.168.1.20/movie.mp4", null, null).playerPath());
        assertEquals(PlaybackAutoContext.PathKind.REMOTE,
                PlaybackResourceClassifier.classifyRequest("https://cdn.example/movie.mp4", null, null).playerPath());
        assertEquals(PlaybackAutoContext.PathKind.APP_INTERNAL_SERVICE,
                PlaybackResourceClassifier.classifyRequest("http://127.0.0.1:9977/mpv/index.m3u8", "hls", "hls").playerPath());
        assertEquals(PlaybackAutoContext.PathKind.EXTERNAL_LOOPBACK,
                PlaybackResourceClassifier.classifyRequest("http://127.0.0.1:7777/video.mp4", null, null).playerPath());
        assertEquals(PlaybackAutoContext.PathKind.EXTERNAL_LOOPBACK,
                PlaybackResourceClassifier.classifyRequest("http://127.0.0.10:7777/video.mp4", null, null).playerPath());
    }

    @Test
    public void requestProtocolUsesMimeAndFormatAndKeepsUnknownConservative() {
        assertEquals(PlaybackAutoContext.Protocol.HLS,
                PlaybackResourceClassifier.classifyRequest("https://host/media", "application/vnd.apple.mpegurl", null).protocol());
        assertEquals(PlaybackAutoContext.Protocol.DASH,
                PlaybackResourceClassifier.classifyRequest("https://host/media", null, "dash").protocol());
        assertEquals(PlaybackAutoContext.Protocol.RTSP,
                PlaybackResourceClassifier.classifyRequest("rtsp://camera/live", null, null).protocol());
        assertEquals(PlaybackAutoContext.PathKind.REMOTE,
                PlaybackResourceClassifier.classifyRequest("rtsp://camera/live", null, null).playerPath());
        assertEquals(PlaybackAutoContext.Protocol.RTSP,
                PlaybackResourceClassifier.classifyRequest("rtsp://camera/live", "video/mp4", null).protocol());
        assertEquals(PlaybackAutoContext.Protocol.RTMP,
                PlaybackResourceClassifier.classifyRequest("rtmp://encoder/live", null, null).protocol());
        assertEquals(PlaybackAutoContext.Protocol.LOCAL,
                PlaybackResourceClassifier.classifyRequest("file:///sdcard/movie.mp4", "video/mp4", null).protocol());
        assertEquals(PlaybackAutoContext.Protocol.UNKNOWN,
                PlaybackResourceClassifier.classifyRequest("https://host/media", "hls", "dash").protocol());
    }

    @Test
    public void hlsUrlCompatibilityRecognizesLiveAliases() {
        assertTrue(PlaybackResourceClassifier.isHlsUrl("https://example.test/play?type=hls&id=1"));
        assertTrue(PlaybackResourceClassifier.isHlsUrl("https://example.test/live/stream?id=1"));
        assertTrue(PlaybackResourceClassifier.isHlsUrl("https://example.test/tv/live.php?id=1"));
        assertFalse(PlaybackResourceClassifier.isHlsUrl("https://example.test/video.mp4"));
    }

    @Test
    public void hlsVodRecordsEndListAndSegmentEvidence() {
        String playlist = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\npart-1.ts\n#EXT-X-ENDLIST\n";
        PlaybackResourceClassifier.Classification result = PlaybackResourceClassifier.classifyHls(
                "http://127.0.0.1:9977/mpv/index.m3u8", "https://origin.example/vod.m3u8", playlist);

        assertEquals(PlaybackAutoContext.Protocol.HLS, result.protocol());
        assertEquals(PlaybackAutoContext.StreamKind.VOD, result.streamKind());
        assertEquals(PlaybackAutoContext.TransferUnit.SEGMENT, result.transferUnit());
        assertEquals(PlaybackAutoContext.ManifestKind.HLS_MEDIA, result.manifest().kind());
        assertEquals(Boolean.TRUE, result.manifest().endList());
        assertEquals(Long.valueOf(6000), result.manifest().targetDurationMs());
        assertEquals(PlaybackAutoContext.PathKind.APP_INTERNAL_SERVICE, result.playerPath());
        assertEquals(PlaybackAutoContext.PathKind.REMOTE, result.upstreamPath());
        assertEquals(PlaybackAutoContext.UpstreamState.VISIBLE, result.upstreamState());
    }

    @Test
    public void hlsLowLatencyRecordsPartAndHoldBack() {
        String playlist = "#EXTM3U\n#EXT-X-TARGETDURATION:2\n"
                + "#EXT-X-SERVER-CONTROL:CAN-BLOCK-RELOAD=YES,PART-HOLD-BACK=1.2\n"
                + "#EXT-X-PART-INF:PART-TARGET=0.333\n"
                + "#EXT-X-PART:DURATION=0.333,URI=part.ts\n";
        PlaybackResourceClassifier.Classification result = PlaybackResourceClassifier.classifyHls(
                "https://origin.example/live.m3u8", null, playlist);

        assertEquals(PlaybackAutoContext.StreamKind.LOW_LATENCY_LIVE, result.streamKind());
        assertEquals(PlaybackAutoContext.TransferUnit.PART, result.transferUnit());
        assertEquals(Boolean.TRUE, result.manifest().lowLatency());
        assertEquals(Long.valueOf(333), result.manifest().partDurationMs());
        assertEquals(Long.valueOf(1200), result.manifest().holdBackMs());
        PlaybackAutoContext.ResourceFacts facts = result.toResourceFacts(100);
        assertEquals(PlaybackAutoContext.ExpiryRule.TTL, facts.manifest().expiryRule());
        assertFalse(facts.manifest().isExpired(6099));
        assertTrue(facts.manifest().isExpired(6100));
    }

    @Test
    public void explicitVodWithoutEndListRemainsUnknown() {
        PlaybackResourceClassifier.Classification result =
                PlaybackResourceClassifier.classifyHls(
                        "https://origin.example/vod.m3u8",
                        null,
                        "#EXTM3U\n#EXT-X-PLAYLIST-TYPE:VOD\n"
                                + "#EXT-X-TARGETDURATION:6\n#EXTINF:6,\na.ts\n");

        assertEquals(PlaybackAutoContext.ManifestKind.HLS_MEDIA,
                result.manifest().kind());
        assertEquals(PlaybackAutoContext.StreamKind.UNKNOWN, result.streamKind());
        assertEquals(Boolean.FALSE, result.manifest().endList());
    }

    @Test
    public void partHoldBackWinsWhenBothHoldBackValuesArePresent() {
        PlaybackResourceClassifier.Classification result =
                PlaybackResourceClassifier.classifyHls(
                        "https://origin.example/live.m3u8",
                        null,
                        "#EXTM3U\n#EXT-X-TARGETDURATION:2\n"
                                + "#EXT-X-SERVER-CONTROL:HOLD-BACK=6,PART-HOLD-BACK=1.2\n"
                                + "#EXT-X-PART-INF:PART-TARGET=0.333\n"
                                + "#EXT-X-PART:DURATION=0.250,URI=p.m4s\n");

        assertEquals(PlaybackAutoContext.StreamKind.LOW_LATENCY_LIVE,
                result.streamKind());
        assertEquals(Long.valueOf(333), result.manifest().partDurationMs());
        assertEquals(Long.valueOf(1200), result.manifest().holdBackMs());
    }

    @Test
    public void blockingReloadAloneDoesNotDeclareLowLatency() {
        PlaybackResourceClassifier.Classification result =
                PlaybackResourceClassifier.classifyHls(
                        "https://origin.example/live.m3u8",
                        null,
                        "#EXTM3U\n#EXT-X-TARGETDURATION:6\n"
                                + "#EXT-X-SERVER-CONTROL:CAN-BLOCK-RELOAD=YES\n"
                                + "#EXTINF:6,\na.ts\n");

        assertEquals(PlaybackAutoContext.StreamKind.LIVE, result.streamKind());
        assertEquals(PlaybackAutoContext.TransferUnit.SEGMENT,
                result.transferUnit());
        assertEquals(Boolean.FALSE, result.manifest().lowLatency());
    }

    @Test
    public void completedEventPlaylistTransitionsToVod() {
        String active = "#EXTM3U\n#EXT-X-PLAYLIST-TYPE:EVENT\n"
                + "#EXT-X-TARGETDURATION:6\n#EXTINF:6,\na.ts\n";
        PlaybackResourceClassifier.Classification live =
                PlaybackResourceClassifier.classifyHls(
                        "https://origin.example/event.m3u8", null, active);
        PlaybackResourceClassifier.Classification completed =
                PlaybackResourceClassifier.classifyHls(
                        "https://origin.example/event.m3u8", null,
                        active + "#EXT-X-ENDLIST\n");

        assertEquals(PlaybackAutoContext.StreamKind.LIVE, live.streamKind());
        assertEquals(PlaybackAutoContext.StreamKind.VOD, completed.streamKind());
        assertEquals(Boolean.TRUE, completed.manifest().endList());
    }

    @Test
    public void hlsMasterDoesNotGuessLiveOrVod() {
        String playlist = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000000\nlow/index.m3u8\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=2000000\nhigh/index.m3u8\n";
        PlaybackResourceClassifier.Classification result = PlaybackResourceClassifier.classifyHls(
                "https://cdn.example/master.m3u8", null, playlist);

        assertEquals(PlaybackAutoContext.ManifestKind.HLS_MASTER, result.manifest().kind());
        assertEquals(2, result.manifest().variantCount().intValue());
        assertEquals(PlaybackAutoContext.StreamKind.UNKNOWN, result.streamKind());
    }

    @Test
    public void dashStaticDynamicAndLowLatencyAreDistinguished() {
        PlaybackResourceClassifier.Classification vod = PlaybackResourceClassifier.classifyDash(
                "https://cdn.example/movie.mpd", null, "<MPD type='static'><Period><SegmentTemplate/></Period></MPD>");
        PlaybackResourceClassifier.Classification live = PlaybackResourceClassifier.classifyDash(
                "https://cdn.example/live.mpd", null, "<MPD type=\"dynamic\" availabilityTimeOffset=\"0.1\"><Period/></MPD>");

        assertEquals(PlaybackAutoContext.ManifestKind.DASH_STATIC, vod.manifest().kind());
        assertEquals(PlaybackAutoContext.StreamKind.VOD, vod.streamKind());
        assertEquals(PlaybackAutoContext.ManifestKind.DASH_DYNAMIC, live.manifest().kind());
        assertEquals(PlaybackAutoContext.StreamKind.LOW_LATENCY_LIVE, live.streamKind());
        assertEquals(PlaybackAutoContext.TransferUnit.PART, live.transferUnit());
    }

    @Test
    public void dataSourceFragmentMimeDoesNotReplaceHlsOrDashProtocol() {
        PlaybackResourceClassifier.Classification fragment = PlaybackResourceClassifier.classifyDataSource(
                "https://cdn.example/chunk.m4s",
                "https://edge.example/chunk.m4s",
                "video/mp4",
                Map.of("Accept-Ranges", List.of("bytes")));
        PlaybackResourceClassifier.Classification hls = PlaybackResourceClassifier.merge(
                PlaybackResourceClassifier.classifyRequest("https://cdn.example/live.m3u8", "hls", "hls"), fragment);
        PlaybackResourceClassifier.Classification dash = PlaybackResourceClassifier.merge(
                PlaybackResourceClassifier.classifyRequest("https://cdn.example/live.mpd", "dash", "dash"), fragment);

        assertEquals(PlaybackAutoContext.Protocol.HLS, hls.protocol());
        assertEquals(PlaybackAutoContext.TransferUnit.SEGMENT, hls.transferUnit());
        assertEquals(PlaybackAutoContext.Protocol.DASH, dash.protocol());
        assertEquals(PlaybackAutoContext.TransferUnit.SEGMENT, dash.transferUnit());
        assertEquals(PlaybackAutoContext.RangeSupport.SUPPORTED, dash.rangeSupport());
    }

    @Test
    public void republishingOldLiveManifestDoesNotRefreshItsTtl() {
        PlaybackResourceClassifier.Classification result = PlaybackResourceClassifier.classifyHls(
                "https://origin.example/live.m3u8",
                null,
                "#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXTINF:2,\na.ts\n",
                100);

        PlaybackAutoContext.ResourceFacts facts = result.toResourceFacts(5_000);
        assertEquals(100, facts.manifest().sampledAtElapsedMs());
        assertFalse(facts.manifest().isExpired(6_099));
        assertTrue(facts.manifest().isExpired(6_100));
    }

    @Test
    public void requestCanSeparateProxyAndUpstreamBeforeManifestLoads() {
        PlaybackResourceClassifier.Classification result = PlaybackResourceClassifier.classify(
                "http://127.0.0.1:9977/mpv/index.m3u8",
                "https://origin.example/live.m3u8",
                "application/vnd.apple.mpegurl",
                "hls",
                Map.of(),
                null);

        assertEquals(PlaybackAutoContext.PathKind.APP_INTERNAL_SERVICE, result.playerPath());
        assertEquals(PlaybackAutoContext.PathKind.REMOTE, result.upstreamPath());
        assertEquals(PlaybackAutoContext.UpstreamState.VISIBLE, result.upstreamState());
    }

    @Test
    public void rangeEvidenceRequiresBytesOrValidContentRange() {
        PlaybackResourceClassifier.Classification supported = PlaybackResourceClassifier.classifyRequest(
                "https://cdn.example/movie.mp4", "video/mp4", null,
                Map.of("Accept-Ranges", List.of("bytes")));
        PlaybackResourceClassifier.Classification invalid = PlaybackResourceClassifier.classifyRequest(
                "https://cdn.example/movie.mp4", "video/mp4", null,
                Map.of("Content-Range", List.of("broken")));

        assertEquals(PlaybackAutoContext.RangeSupport.SUPPORTED, supported.rangeSupport());
        assertEquals(PlaybackAutoContext.RangeSupport.UNSUPPORTED, invalid.rangeSupport());
    }

    @Test
    public void externalLoopbackWithoutUpstreamIsOpaque() {
        PlaybackResourceClassifier.Classification result = PlaybackResourceClassifier.classifyRequest(
                "http://127.0.0.1:7788/index.m3u8", "hls", "hls");
        assertEquals(PlaybackAutoContext.PathKind.EXTERNAL_LOOPBACK, result.playerPath());
        assertEquals(PlaybackAutoContext.PathKind.UNKNOWN, result.upstreamPath());
        assertEquals(PlaybackAutoContext.UpstreamState.OPAQUE, result.upstreamState());
    }

    @Test
    public void appProxyPointingAtExternalLoopbackStillTreatsRealUpstreamAsOpaque() {
        PlaybackResourceClassifier.Classification result = PlaybackResourceClassifier.classifyHls(
                "http://127.0.0.1:9977/mpv/index.m3u8",
                "http://127.0.0.1:7788/opaque/index.m3u8",
                "#EXTM3U\n#EXTINF:2,\na.ts\n");
        assertEquals(PlaybackAutoContext.PathKind.APP_INTERNAL_SERVICE, result.playerPath());
        assertEquals(PlaybackAutoContext.PathKind.EXTERNAL_LOOPBACK, result.upstreamPath());
        assertEquals(PlaybackAutoContext.UpstreamState.OPAQUE, result.upstreamState());
    }

    @Test
    public void resultFactsAndLogsContainNoSensitiveUrlMaterial() {
        PlaybackResourceClassifier.Classification result = PlaybackResourceClassifier.classifyHls(
                "https://secret.example/private/index.m3u8?token=abc", null, "#EXTM3U\n#EXT-X-ENDLIST\n");
        String summary = result.logSummary();
        assertFalse(summary.contains("secret.example"));
        assertFalse(summary.contains("private"));
        assertFalse(summary.contains("token"));
        assertFalse(summary.contains("abc"));
        assertTrue(result.toResourceFacts(100).protocol().isUsable(100));
        assertNull(result.toResourceFacts(100).manifest().value().targetDurationMs());
    }

    @Test
    public void manifestObservationWinsOverRequestUnknownButKeepsPlayerPath() {
        PlaybackResourceClassifier.Classification request = PlaybackResourceClassifier.classifyRequest(
                "http://127.0.0.1:9977/mpv/index.m3u8", "hls", "hls");
        PlaybackResourceClassifier.Classification manifest = PlaybackResourceClassifier.classifyHls(
                "http://127.0.0.1:9977/mpv/index.m3u8", "https://origin.example/live.m3u8", "#EXTM3U\n#EXTINF:2,\na.ts\n");
        PlaybackResourceClassifier.Classification merged = PlaybackResourceClassifier.merge(request, manifest);
        assertEquals(PlaybackAutoContext.StreamKind.LIVE, merged.streamKind());
        assertEquals(PlaybackAutoContext.PathKind.APP_INTERNAL_SERVICE, merged.playerPath());
        assertEquals(PlaybackAutoContext.PathKind.REMOTE, merged.upstreamPath());
        assertTrue(merged.actualManifest());
    }
}
