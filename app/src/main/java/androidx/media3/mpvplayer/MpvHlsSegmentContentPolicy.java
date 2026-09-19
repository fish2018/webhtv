package androidx.media3.mpvplayer;

import java.util.Locale;

final class MpvHlsSegmentContentPolicy {

    private static final byte[] PNG_SIGNATURE = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PNG_IEND = new byte[]{0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82};
    private static final int TS_PACKET_BYTES = 188;

    private MpvHlsSegmentContentPolicy() {
    }

    static boolean isPlaylist(HlsPlaylistRewriter.UriRole role, String url, String contentType) {
        // Dynamic endpoints may serve both playlists and segments at *.m3u8.
        // The role declared by the parent playlist takes precedence over hints.
        if (role == HlsPlaylistRewriter.UriRole.MEDIA_SEGMENT) return false;
        if (role == HlsPlaylistRewriter.UriRole.VARIANT_PLAYLIST) return true;
        String mime = contentType == null ? "" : contentType.toLowerCase(Locale.US);
        if (mime.contains("mpegurl") || mime.contains("m3u8")) return true;
        String lower = url == null ? "" : url.toLowerCase(Locale.US);
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        return lower.endsWith(".m3u8") || lower.endsWith(".m3u");
    }

    static boolean shouldProbePngPrefix(String contentType, boolean mediaSegment) {
        String mime = contentType == null ? "" : contentType.trim().toLowerCase(Locale.US);
        if (mime.startsWith("image/png")) return true;
        return mediaSegment && mime.startsWith("image/");
    }

    static int findPngWrappedTransportStreamOffset(byte[] data, int length) {
        int safeLength = Math.max(0, Math.min(length, data == null ? 0 : data.length));
        if (!startsWithPngSignature(data, safeLength)) return -1;
        int iend = indexOf(data, safeLength, PNG_IEND);
        if (iend < 0) return -1;
        int start = iend + PNG_IEND.length;
        for (int offset = start; offset + TS_PACKET_BYTES < safeLength; offset++) {
            if (data[offset] != 0x47 || data[offset + TS_PACKET_BYTES] != 0x47) continue;
            if (offset + TS_PACKET_BYTES * 2 < safeLength
                    && data[offset + TS_PACKET_BYTES * 2] != 0x47) continue;
            return offset;
        }
        return -1;
    }

    static boolean startsWithPngSignature(byte[] data, int length) {
        int safeLength = Math.max(0, Math.min(length, data == null ? 0 : data.length));
        return startsWith(data, safeLength, PNG_SIGNATURE);
    }

    static long strippedContentLength(long contentLength, int strippedPrefixBytes) {
        if (contentLength <= 0
                || strippedPrefixBytes <= 0
                || strippedPrefixBytes >= contentLength) return -1;
        return contentLength - strippedPrefixBytes;
    }

    private static boolean startsWith(byte[] data, int length, byte[] prefix) {
        if (data == null || length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (data[i] != prefix[i]) return false;
        return true;
    }

    private static int indexOf(byte[] data, int length, byte[] needle) {
        int end = length - needle.length;
        for (int i = 0; i <= end; i++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }
}
