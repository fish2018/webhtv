package com.fongmi.android.tv.player.exo.ass;

import org.mozilla.universalchardet.UniversalDetector;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Complete ASS scripts and bounded CodecPrivate normalization; packets use AssPacketInput. */
public final class AssInput {
    public static final String EXTERNAL_ID_PREFIX = "webhtv-ass-external:";
    public static final int MAX_INPUT_BYTES = 4 * 1024 * 1024;
    public static final int MAX_UTF8_BYTES = 8 * 1024 * 1024;
    public static final int MAX_EVENTS = 20000;
    public static final int MAX_STYLES = 512;

    static boolean isExternalId(String id) {
        if (id == null || id.length() > 256) return false;
        int offset = 0;
        // MergingMediaPeriod prefixes Format IDs with the child index. This is
        // only admission; the complete Stream/period/generation remains the identity.
        while (!id.startsWith(EXTERNAL_ID_PREFIX, offset)) {
            int start = offset;
            while (offset < id.length() && id.charAt(offset) >= '0' && id.charAt(offset) <= '9') offset++;
            if (offset == start || offset == id.length() || id.charAt(offset++) != ':') return false;
        }
        offset += EXTERNAL_ID_PREFIX.length();
        if (offset == id.length()) return false;
        for (; offset < id.length(); offset++) {
            if (id.charAt(offset) < '0' || id.charAt(offset) > '9') return false;
        }
        return true;
    }

    static byte[] copy(ByteBuffer input) {
        int length = input.remaining();
        if (length <= 0 || length > MAX_INPUT_BYTES) throw new IllegalArgumentException("script-bytes");
        byte[] copy = new byte[length];
        input.duplicate().get(copy);
        return copy;
    }

    static byte[] normalize(byte[] bytes) {
        return normalize(bytes, false);
    }

    static byte[] normalizeHeader(byte[] bytes) {
        return normalize(bytes, true);
    }

    private static byte[] normalize(byte[] bytes, boolean header) {
        if (bytes.length == 0 || bytes.length > MAX_INPUT_BYTES) throw new IllegalArgumentException("script-bytes");
        Charset charset;
        int offset = 0;
        if (bytes.length >= 4 && bytes[0] == 0 && bytes[1] == 0
                && bytes[2] == (byte) 0xfe && bytes[3] == (byte) 0xff) {
            charset = Charset.forName("UTF-32BE"); offset = 4;
        } else if (bytes.length >= 4 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe
                && bytes[2] == 0 && bytes[3] == 0) {
            charset = Charset.forName("UTF-32LE"); offset = 4;
        } else if (bytes.length >= 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe) {
            charset = StandardCharsets.UTF_16LE; offset = 2;
        } else if (bytes.length >= 2 && bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff) {
            charset = StandardCharsets.UTF_16BE; offset = 2;
        } else if (bytes.length >= 3 && bytes[0] == (byte) 0xef
                && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) {
            charset = StandardCharsets.UTF_8; offset = 3;
        } else {
            UniversalDetector detector = new UniversalDetector(null);
            detector.handleData(bytes, 0, bytes.length);
            detector.dataEnd();
            String name = detector.getDetectedCharset();
            charset = name == null ? StandardCharsets.UTF_8 : Charset.forName(name);
        }
        String text = new String(bytes, offset, bytes.length - offset, charset);
        // Match terminal-NUL tolerance after decoding, never on raw UTF-16 bytes.
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == 0) end--;
        text = text.substring(0, end);
        if (text.indexOf('\0') >= 0) throw new IllegalArgumentException("embedded-nul");
        String lower = text.toLowerCase(Locale.ROOT);
        if (!lower.contains("[script info]") || !header && !lower.contains("[events]"))
            throw new IllegalArgumentException("not-full-script");
        if (lower.contains("[fonts]") || lower.contains("[graphics]"))
            throw new IllegalArgumentException("inline-attachments-not-admitted");
        int events = 0, styles = 0;
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (header && trimmed.regionMatches(true, 0, "Dialogue:", 0, 9))
                throw new IllegalArgumentException("codec-private-events");
            if (trimmed.regionMatches(true, 0, "Dialogue:", 0, 9) && ++events > MAX_EVENTS)
                throw new IllegalArgumentException("event-count");
            if (trimmed.regionMatches(true, 0, "Style:", 0, 6) && ++styles > MAX_STYLES)
                throw new IllegalArgumentException("style-count");
        }
        byte[] result = text.getBytes(StandardCharsets.UTF_8);
        if (result.length > MAX_UTF8_BYTES) throw new IllegalArgumentException("utf8-bytes");
        return result;
    }

    static long timeMs(long positionUs, long streamOffsetUs, long textOffsetUs) {
        return Math.floorDiv(Math.subtractExact(Math.subtractExact(positionUs, streamOffsetUs), textOffsetUs), 1000);
    }

    private AssInput() { }
}
