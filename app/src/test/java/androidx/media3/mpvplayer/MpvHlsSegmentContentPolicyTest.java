package androidx.media3.mpvplayer;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvHlsSegmentContentPolicyTest {

    @Test
    public void liveSegmentsAtPlaylistEndpointStayMediaBeforeAndAfterResponse() {
        String playlist = """
                #EXTM3U
                #EXT-X-MEDIA-SEQUENCE:1785796058
                #EXT-X-TARGETDURATION:6
                #EXTINF:6.000,
                cctv5.m3u8?ts=segment-1
                #EXTINF:4.000,
                cctv5.m3u8?ts=segment-2
                """;

        HlsPlaylistRewriter.Result result = HlsPlaylistRewriter.rewrite(
                playlist, null, (uri, cacheable, context) -> {
                    assertEquals(HlsPlaylistRewriter.UriRole.MEDIA_SEGMENT, context.role());
                    assertFalse(MpvHlsSegmentContentPolicy.isPlaylist(context.role(), uri, null));
                    assertFalse(MpvHlsSegmentContentPolicy.isPlaylist(context.role(), uri, "video/mp2t"));
                    assertFalse(MpvHlsSegmentContentPolicy.isPlaylist(
                            context.role(), uri, "application/vnd.apple.mpegurl"));
                    return new HlsPlaylistRewriter.MappedUri(uri, uri);
                });

        assertEquals(2, result.segments().size());
        assertEquals(playlist, result.text());
    }

    @Test
    public void byteRangeAndPartialSegmentsRetainTheirMediaRole() {
        String playlist = """
                #EXTM3U
                #EXT-X-PART:DURATION=0.5,URI="live.m3u8?part=1"
                #EXT-X-PRELOAD-HINT:TYPE=PART,URI="live.m3u8?part=2"
                #EXTINF:4,
                #EXT-X-BYTERANGE:1024@188
                live.m3u8?ts=1
                """;
        int[] ranges = {0};
        int[] items = {0};

        HlsPlaylistRewriter.Result result = HlsPlaylistRewriter.rewrite(
                playlist, null, (uri, cacheable, context) -> {
                    items[0]++;
                    if (context.byteRange()) ranges[0]++;
                    // serveItem preserves Range whenever this request is media.
                    assertFalse(MpvHlsSegmentContentPolicy.isPlaylist(context.role(), uri, null));
                    return new HlsPlaylistRewriter.MappedUri(uri, uri);
                });

        assertEquals(3, items[0]);
        assertEquals(1, ranges[0]);
        assertEquals(playlist, result.text());
    }

    @Test
    public void declaredVariantsRemainPlaylistsWithoutUrlOrMimeHints() {
        HlsPlaylistRewriter.Result result = HlsPlaylistRewriter.rewrite("""
                #EXTM3U
                #EXT-X-STREAM-INF:BANDWIDTH=4500000
                stream?quality=hd
                """, null, (uri, cacheable, context) -> {
                    assertTrue(MpvHlsSegmentContentPolicy.isPlaylist(context.role(), uri, null));
                    assertTrue(MpvHlsSegmentContentPolicy.isPlaylist(
                            context.role(), uri, "application/octet-stream"));
                    return new HlsPlaylistRewriter.MappedUri(uri, uri);
                });

        assertEquals(1, result.variants().size());
    }

    @Test
    public void unclassifiedUrisKeepExistingPlaylistHints() {
        HlsPlaylistRewriter.UriRole role = HlsPlaylistRewriter.UriRole.OTHER;
        assertTrue(MpvHlsSegmentContentPolicy.isPlaylist(role, "audio.M3U8?lang=zh", null));
        assertTrue(MpvHlsSegmentContentPolicy.isPlaylist(role, "subtitles.m3u", null));
        assertTrue(MpvHlsSegmentContentPolicy.isPlaylist(role, "rendition", "application/x-mpegURL"));
        assertFalse(MpvHlsSegmentContentPolicy.isPlaylist(role, "key?name=secret.m3u8", null));
        assertFalse(MpvHlsSegmentContentPolicy.isPlaylist(role, "init.mp4", "video/mp4"));
        assertFalse(MpvHlsSegmentContentPolicy.isPlaylist(role, null, null));
    }

    @Test
    public void jpgMediaSegmentIsProbedForPngWrapper() {
        assertTrue(MpvHlsSegmentContentPolicy.shouldProbePngPrefix("image/jpg", true));
        assertTrue(MpvHlsSegmentContentPolicy.shouldProbePngPrefix("image/jpeg", true));
    }

    @Test
    public void pngKeepsExistingProbeBehavior() {
        assertTrue(MpvHlsSegmentContentPolicy.shouldProbePngPrefix("image/png", true));
        assertTrue(MpvHlsSegmentContentPolicy.shouldProbePngPrefix("image/png", false));
    }

    @Test
    public void normalMediaAndNonSegmentImagesAreNotChanged() {
        assertFalse(MpvHlsSegmentContentPolicy.shouldProbePngPrefix("video/mp2t", true));
        assertFalse(MpvHlsSegmentContentPolicy.shouldProbePngPrefix("video/mp4", true));
        assertFalse(MpvHlsSegmentContentPolicy.shouldProbePngPrefix("image/jpeg", false));
        assertFalse(MpvHlsSegmentContentPolicy.shouldProbePngPrefix(null, true));
    }

    @Test
    public void findsTransportStreamAfterPngAndTsRawMarker() {
        byte[] data = wrappedTransportStream("\u0000\u001dO\u00dftEXtTS_RAW\u0000");

        assertEquals(39, MpvHlsSegmentContentPolicy.findPngWrappedTransportStreamOffset(data, data.length));
    }

    @Test
    public void rejectsPngWithoutAlignedTransportStreamPackets() {
        byte[] data = wrappedTransportStream("");
        data[24 + 188] = 0;

        assertEquals(-1, MpvHlsSegmentContentPolicy.findPngWrappedTransportStreamOffset(data, data.length));
    }

    @Test
    public void preloadStreamStripsWrapperAndUsesExactTransformedLength()
            throws IOException {
        byte[] data = wrappedTransportStream("\u0000\u001dO\u00dftEXtTS_RAW\u0000");
        MpvHlsProxy.PngPrefixStrippingInputStream input =
                new MpvHlsProxy.PngPrefixStrippingInputStream(
                        new ByteArrayInputStream(data),
                        "https://video.test/0.png", false);

        int strippedBytes = input.initializeAndGetStrippedPrefixBytes();

        assertEquals(39, strippedBytes);
        assertEquals(data.length - strippedBytes,
                MpvHlsSegmentContentPolicy.strippedContentLength(
                        data.length, strippedBytes));
        assertArrayEquals(Arrays.copyOfRange(data, strippedBytes, data.length),
                input.readAllBytes());
    }

    @Test
    public void invalidOrUnknownWrappedLengthIsNotReserved() {
        assertEquals(-1,
                MpvHlsSegmentContentPolicy.strippedContentLength(-1, 68));
        assertEquals(-1,
                MpvHlsSegmentContentPolicy.strippedContentLength(1_024, 0));
        assertEquals(-1,
                MpvHlsSegmentContentPolicy.strippedContentLength(68, 68));
    }

    private static byte[] wrappedTransportStream(String marker) {
        byte[] pngSignature = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] iend = new byte[]{0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82};
        byte[] markerBytes = marker.getBytes(StandardCharsets.ISO_8859_1);
        int offset = pngSignature.length + 8 + iend.length + markerBytes.length;
        byte[] data = new byte[offset + 188 * 2 + 1];
        System.arraycopy(pngSignature, 0, data, 0, pngSignature.length);
        System.arraycopy(iend, 0, data, pngSignature.length + 8, iend.length);
        System.arraycopy(markerBytes, 0, data, pngSignature.length + 8 + iend.length, markerBytes.length);
        data[offset] = 0x47;
        data[offset + 188] = 0x47;
        data[offset + 376] = 0x47;
        return data;
    }
}
