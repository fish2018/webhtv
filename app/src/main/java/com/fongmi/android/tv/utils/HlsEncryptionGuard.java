package com.fongmi.android.tv.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects HLS playlists whose segment decryption IV is implied by the media sequence
 * number, which makes them unsafe to rewrite by any means that removes segments.
 *
 * <p>When {@code #EXT-X-KEY} enables encryption but omits {@code IV=}, RFC 8216 §5.2 says
 * the IV is the segment's media sequence number. Removing a segment renumbers every
 * segment after it, so each one is then decrypted with the wrong IV and the stream turns
 * to garbage — not a mis-cut, a black or corrupt picture.
 *
 * <p>The predicates below mirror {@code media3}'s forked {@code HlsPlaylistParser}
 * exactly, because the only thing that matters is what <em>that</em> parser will conclude
 * from the same bytes. Two of its properties are easy to get wrong, and both were:
 *
 * <ul>
 *   <li>{@code TAG_KEY} is {@code "#EXT-X-KEY"} <em>without</em> the colon, so
 *       {@code "#EXT-X-KEY :METHOD=AES-128,…"} still parses as a key line.</li>
 *   <li>{@code REGEX_METHOD} and {@code REGEX_IV} are case-sensitive and anchored to the
 *       attribute name, so a lowercase {@code iv=} inside a quoted {@code URI} neither
 *       pins the IV for media3 nor should count as pinned here. Matching on an
 *       upper-cased substring got this backwards and let corrupting playlists through.</li>
 * </ul>
 */
final class HlsEncryptionGuard {

    /** {@code media3 HlsPlaylistParser.TAG_KEY} — deliberately without the trailing colon. */
    private static final String TAG_KEY = "#EXT-X-KEY";
    /** Mirrors {@code media3 HlsPlaylistParser.REGEX_METHOD}. */
    private static final Pattern REGEX_METHOD = Pattern.compile(
            "METHOD=(NONE|AES-128|SAMPLE-AES|SAMPLE-AES-CENC|SAMPLE-AES-CTR)\\s*(?:,|$)");
    /** Mirrors {@code media3 HlsPlaylistParser.REGEX_IV}. */
    private static final Pattern REGEX_IV = Pattern.compile("IV=([^,.*]+)");

    private HlsEncryptionGuard() {
    }

    /**
     * @return whether any key line enables encryption without pinning an explicit IV
     */
    static boolean hasImpliedSegmentIv(String manifest) {
        if (manifest == null) return false;
        // Split the way BufferedReader.readLine does — a lone CR ends a line too. Splitting
        // on "\n" alone collapses a CR-only playlist into one giant line, hiding its keys.
        for (String line : manifest.split("\r\n|\n|\r", -1)) {
            if (!line.startsWith(TAG_KEY)) continue;
            Matcher method = REGEX_METHOD.matcher(line);
            // No recognisable METHOD: media3's parseStringAttr throws and the whole playlist
            // fails to parse, so it is not something we could have corrupted. Ignore it.
            if (!method.find()) continue;
            if ("NONE".equals(method.group(1))) continue;
            if (!REGEX_IV.matcher(line).find()) return true;
        }
        return false;
    }
}
