package com.fongmi.android.tv.player.exo;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import com.fongmi.android.tv.api.config.HlsRuleConfig;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.HlsAdblockPipeline;
import com.fongmi.android.tv.utils.HlsManifestCleaner;
import com.github.catvod.crawler.SpiderDebug;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Applies the HLS ad removal pipeline to playlist responses on the Exo path.
 *
 * <p>Exo has no local HLS proxy, so {@link HlsManifestCleaner} never ran there —
 * the only ad removal was the legacy {@code HlsAdsParser} heuristic invoked inside
 * the forked {@code HlsPlaylistParser}. User and interface rules were therefore
 * silently inert on the default kernel. Cleaning here gives Exo the same
 * {@link HlsAdblockPipeline} policy that IJK already gets through its proxy.
 *
 * <p>This class owns the <em>whole</em> pipeline: {@code ExoUtil} deliberately stops
 * passing {@code adblock=true} into the {@code MediaItem}, so the fork's heuristic no
 * longer runs behind our back. That is not tidiness — chaining them is actively
 * destructive. {@code HlsAdsParser.findMinorityGroup} gates on "largest group must
 * exceed 50% of segments" with no cap on how much it may delete, and removing ad
 * segments shrinks the total it measures against. Measured: main content split across
 * two CDN hosts (260 + 240 segments) plus 25 ads is left untouched by the heuristic on
 * the raw playlist (520 &lt;= 525), but after structured cleaning 520 &gt; 500 opens the
 * gate and it deletes the 240-segment group — 48% of the film, silently. Dropping the
 * ad host can also collapse two structural groups into one, which pushes input into
 * {@code findAdsByPrefixAnalysis}, a path it could never have reached. Running the two
 * engines through {@link HlsAdblockPipeline} instead keeps them mutually exclusive:
 * the heuristic only ever sees a manifest the structured engine did not change.
 *
 * <p>VOD only, gated on {@code #EXT-X-ENDLIST}. Live playlists must not be cleaned
 * here: {@link HlsManifestCleaner} bumps {@code #EXT-X-MEDIA-SEQUENCE} by the number
 * of removed head segments, but its fallback gates (removal ratio, removed duration,
 * and the requirement that removals be contiguous at the head) flip between
 * consecutive reloads of a sliding window — a new ad break always appears at the
 * window tail first, which is non-contiguous and therefore falls back to the raw
 * sequence. The emitted sequence then oscillates, media3's
 * {@code HlsMediaPlaylist.isNewerThan} silently discards the "older" reload, and after
 * {@code targetDuration * 3.5} playback dies with {@code PlaylistStuckException}.
 * The same gate is why {@code MpvHlsProxy.applyAdblock} checks {@code isVodPlaylist}.
 *
 * <p>Sits above the cache so that editing a rule takes effect on the next load
 * instead of waiting for eviction.
 *
 * <p>Playlists are recognised by sniffing the {@code #EXTM3U} header rather than by
 * URL shape: one factory serves both manifest and segment loads, and media3 gives
 * no reliable data-type hint at this layer. Non-playlist responses only pay for a
 * {@value #SNIFF_BYTES}-byte lookahead.
 */
final class HlsPlaylistCleaningDataSource implements DataSource {

    private static final byte[] PLAYLIST_HEADER = "#EXTM3U".getBytes(StandardCharsets.US_ASCII);
    /** {@code String.trim} does not strip U+FEFF, so it has to go before cleaning. */
    private static final String BOM = "﻿";
    /** Room for a byte order mark, stray leading whitespace and the header itself. */
    private static final int SNIFF_BYTES = 64;
    /**
     * Mirrors {@code HlsManifestCleaner}'s own 2 MiB ceiling: past that the cleaner
     * bails out anyway, so buffering the response would cost memory for nothing.
     */
    private static final int MAX_PLAYLIST_BYTES = 2 * 1024 * 1024;
    private static final int CHUNK_BYTES = 8 * 1024;

    private final DataSource upstream;
    private final Supplier<List<HlsManifestCleaner.Rule>> rules;
    private final BooleanSupplier adblockEnabled;

    @Nullable private byte[] buffer;
    private int bufferLength;
    private int bufferPos;

    HlsPlaylistCleaningDataSource(DataSource upstream) {
        this(upstream, HlsPlaylistCleaningDataSource::configuredRules, Setting::isAdblock);
    }

    HlsPlaylistCleaningDataSource(DataSource upstream,
                                  Supplier<List<HlsManifestCleaner.Rule>> rules,
                                  BooleanSupplier adblockEnabled) {
        this.upstream = upstream;
        this.rules = rules;
        this.adblockEnabled = adblockEnabled;
    }

    /**
     * Reading the rule config must never break playback. Degrading to an empty rule
     * set still lets the legacy heuristic run, which is what happened before this
     * class existed.
     */
    private static List<HlsManifestCleaner.Rule> configuredRules() {
        try {
            return HlsRuleConfig.getRules();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    @Override
    public void addTransferListener(@NonNull TransferListener transferListener) {
        upstream.addTransferListener(transferListener);
    }

    @Override
    public long open(@NonNull DataSpec dataSpec) throws IOException {
        return open(dataSpec, dataSpec.uri == null ? "" : dataSpec.uri.toString(),
                dataSpec.position, dataSpec.length);
    }

    /**
     * The request fields are passed in rather than read off {@code dataSpec} so that
     * unit tests can drive this without constructing a {@link DataSpec} —
     * {@code Uri.parse} is an unmocked android.jar stub on the JVM.
     */
    long open(@Nullable DataSpec dataSpec, String url, long position, long length)
            throws IOException {
        buffer = null;
        bufferLength = 0;
        bufferPos = 0;
        long upstreamLength = upstream.open(dataSpec);
        if (!canInspect(url, position, length)) return upstreamLength;

        byte[] head = new byte[SNIFF_BYTES];
        int headLength = readUpTo(head);
        if (!startsWithPlaylistHeader(head, headLength)) {
            // Not a playlist: hand back the bytes already consumed, then stream on.
            buffer = head;
            bufferLength = headLength;
            return upstreamLength;
        }

        byte[] raw = readAll(head, headLength);
        // Oversized: readAll already parked what it read in buffer; pass the rest through.
        if (raw == null) return upstreamLength;

        String text = new String(raw, StandardCharsets.UTF_8);
        String cleaned = cleanedOrNull(url, text);
        buffer = cleaned == null ? raw : cleaned.getBytes(StandardCharsets.UTF_8);
        bufferLength = buffer.length;
        // The response was drained, so the served length is exactly what we hold.
        // Reporting the upstream length would look like a truncated response to Exo,
        // because cleaning only ever makes the playlist shorter.
        return bufferLength;
    }

    @Override
    public int read(@NonNull byte[] target, int offset, int length) throws IOException {
        if (length == 0) return 0;
        if (buffer != null && bufferPos < bufferLength) {
            int count = Math.min(length, bufferLength - bufferPos);
            System.arraycopy(buffer, bufferPos, target, offset, count);
            bufferPos += count;
            return count;
        }
        return upstream.read(target, offset, length);
    }

    @Nullable
    @Override
    public Uri getUri() {
        return upstream.getUri();
    }

    @NonNull
    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return upstream.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        buffer = null;
        bufferLength = 0;
        bufferPos = 0;
        upstream.close();
    }

    /**
     * Only whole-response reads can be rewritten: a ranged request addresses byte
     * offsets in the original body, and cleaning changes the length.
     */
    private boolean canInspect(String url, long position, long length) {
        if (position != 0 || length != C.LENGTH_UNSET) return false;
        // The nano /m3u8 proxy already cleaned its output; doing it twice would apply
        // the rules to the same playlist again, doubling the removal count and
        // possibly crossing the cleaner's fallback gates.
        if (HlsAdblockPipeline.isCoreM3u8Proxy(url)) return false;
        try {
            return adblockEnabled.getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Fills {@code target}, stopping early only at end of input. */
    private int readUpTo(byte[] target) throws IOException {
        int total = 0;
        while (total < target.length) {
            int read = upstream.read(target, total, target.length - total);
            if (read == C.RESULT_END_OF_INPUT) break;
            total += read;
        }
        return total;
    }

    /**
     * Reads the rest of the response, or returns null when it outgrows
     * {@link #MAX_PLAYLIST_BYTES} — in that case the bytes consumed so far are left
     * in {@link #buffer} so the caller can pass the response through untouched.
     */
    @Nullable
    private byte[] readAll(byte[] head, int headLength) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(headLength, 1024));
        out.write(head, 0, headLength);
        byte[] chunk = new byte[CHUNK_BYTES];
        while (true) {
            int read = upstream.read(chunk, 0, chunk.length);
            if (read == C.RESULT_END_OF_INPUT) return out.toByteArray();
            out.write(chunk, 0, read);
            if (out.size() > MAX_PLAYLIST_BYTES) {
                buffer = out.toByteArray();
                bufferLength = buffer.length;
                return null;
            }
        }
    }

    /**
     * @return the cleaned playlist, or null when nothing changed.
     *
     * <p>Runs the full pipeline ({@code legacyFallback=true}) because the fork's own
     * heuristic pass is switched off for Exo — see the class javadoc. The pipeline
     * keeps the two engines mutually exclusive: the heuristic runs only when the
     * structured rules did not change the manifest.
     *
     * <p>A leading byte order mark is dropped before cleaning:
     * {@code HlsManifestCleaner} requires {@code trim()} to reveal {@code #EXTM3U},
     * and {@code String.trim} does not strip U+FEFF, so a BOM would make it bail out
     * on a playlist this class already recognised. Serving the cleaned text without
     * the BOM is safe — the playlist parser discards it either way — and when nothing
     * is cleaned the original bytes are served untouched.
     */
    @Nullable
    private String cleanedOrNull(String url, String text) {
        String payload = text.startsWith(BOM) ? text.substring(BOM.length()) : text;
        // Live windows must keep their upstream media sequence; see class javadoc.
        if (!isVodPlaylist(payload)) return null;
        try {
            HlsAdblockPipeline.Outcome outcome =
                    HlsAdblockPipeline.apply(url, payload, rules.get(), true);
            if (outcome.manifest() == null || TextUtils.equals(outcome.manifest(), payload)) return null;
            SpiderDebug.log("exo-adblock", "filtered bytes=%d->%d structured=%s legacy=%s removed=%d",
                    text.length(), outcome.manifest().length(), outcome.structured(),
                    outcome.legacy(), outcome.removedSegments());
            return outcome.manifest();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Same criterion as {@code MpvHlsProxy.isVodPlaylist}: a media playlist without
     * {@code #EXT-X-ENDLIST} is a live window and must not be rewritten.
     *
     * <p>Case-sensitive on purpose, matching {@code HlsManifestCleaner} and media3's
     * own parser ({@code line.equals(TAG_ENDLIST)}). Accepting a lowercase spelling
     * here would be worse than rejecting it: this gate would classify the playlist as
     * VOD and hand it over, while the cleaner would still see it as live, take the
     * media-sequence bump branch, and reintroduce exactly the cross-reload sequence
     * regression the gate exists to prevent.
     *
     * <p>A multivariant playlist has no {@code #EXT-X-ENDLIST} either and is therefore
     * skipped too, which is correct — it carries no segments to remove, and its
     * variants are fetched as separate media playlists that do reach this code.
     */
    private static boolean isVodPlaylist(String text) {
        return text.contains("#EXT-X-ENDLIST");
    }

    private static boolean startsWithPlaylistHeader(byte[] data, int length) {
        int index = 0;
        // Skip the UTF-8 byte order mark, which the playlist parser also tolerates.
        if (length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB
                && (data[2] & 0xFF) == 0xBF) index = 3;
        while (index < length && Character.isWhitespace(data[index])) index++;
        if (length - index < PLAYLIST_HEADER.length) return false;
        for (int i = 0; i < PLAYLIST_HEADER.length; i++) {
            if (data[index + i] != PLAYLIST_HEADER[i]) return false;
        }
        return true;
    }

    static final class Factory implements DataSource.Factory {

        private final DataSource.Factory upstream;

        Factory(DataSource.Factory upstream) {
            this.upstream = upstream;
        }

        @NonNull
        @Override
        public DataSource createDataSource() {
            return new HlsPlaylistCleaningDataSource(upstream.createDataSource());
        }
    }
}
