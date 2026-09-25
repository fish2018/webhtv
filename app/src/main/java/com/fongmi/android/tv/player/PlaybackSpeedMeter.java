package com.fongmi.android.tv.player;

import android.net.TrafficStats;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.exo.PlaybackAnalyticsListener;
import com.github.catvod.net.OkTrafficCounter;

import java.text.DecimalFormat;
import java.util.function.LongSupplier;

/**
 * Download speed for the playback readouts, counted in-process rather than read from
 * the platform.
 *
 * <p>Per-UID {@link TrafficStats} needs kernel support (xt_qtaguid or the netd eBPF
 * path) that some vendor ROMs — TV boxes and projectors in particular — never
 * compiled in.  There every reader gets {@code UNSUPPORTED} or a value frozen at a
 * constant, so a readout built on it alone shows nothing at all.
 *
 * <p>Three sources are tried in order, so something usable is available at every stage
 * of playback:
 * <ol>
 *   <li>{@link OkTrafficCounter}, which covers scraping and manifest fetches before a
 *       kernel exists, plus the kernels whose transfers pass through Java;
 *   <li>{@code TrafficStats}, which alone can still see traffic from native engine
 *       sockets that never reach Java;
 *   <li>the active playback kernel's own throughput, for a native engine socket on a
 *       ROM where neither counter above can observe anything.
 * </ol>
 *
 * <p>The measured deltas rank above the kernel because every engine reports a
 * <em>smoothed estimate</em> that freezes rather than decays when transfers stop: a
 * filled buffer leaves media3's sliding percentile, ffmpeg's {@code tcp_speed} and
 * mpv's {@code raw-input-rate} all holding their last reading indefinitely. Trusting
 * that first pinned the readout to a constant for the rest of playback. The kernel is
 * therefore consulted only until {@code TrafficStats} has proven it counts on this
 * device — that proof is what separates a ROM which cannot observe its own traffic from
 * a stream that is simply idle, and only {@code TrafficStats} can supply it because it
 * alone sees native sockets.
 */
public final class PlaybackSpeedMeter {

    /** Which source produced the current sample. */
    public enum Source {
        KERNEL,
        OK_HTTP,
        TRAFFIC_STATS,
        NONE
    }

    /** Sentinel for a byte counter the platform cannot supply. */
    static final long UNSUPPORTED = -1;

    private static final DecimalFormat SPEED_FORMAT = new DecimalFormat("#.0");
    private static final String UNIT_KB = " KB/s";
    private static final String UNIT_MB = " MB/s";

    private final LongSupplier byteCounter;
    private final LongSupplier okHttpCounter;
    private final LongSupplier clock;

    private long lastRxBytes = UNSUPPORTED;
    private long lastOkHttpBytes = UNSUPPORTED;
    private long lastTimeStamp;
    /**
     * Whether {@code TrafficStats} has ever reported bytes arriving on this device.
     * Until it does, it may be absent or frozen at a constant, and since it is the only
     * counter that can see a native engine socket, a zero delta then means "cannot see"
     * rather than "nothing arrived".
     */
    private boolean trafficStatsProvenLive;
    private long bytesPerSecond;
    private Source source = Source.NONE;

    public PlaybackSpeedMeter() {
        this(PlaybackSpeedMeter::readUidRxBytes, OkTrafficCounter::totalBytes, System::currentTimeMillis);
    }

    PlaybackSpeedMeter(LongSupplier byteCounter, LongSupplier okHttpCounter, LongSupplier clock) {
        this.byteCounter = byteCounter;
        this.okHttpCounter = okHttpCounter;
        this.clock = clock;
    }

    /**
     * Samples the current download speed. Call at a steady interval — the
     * {@code TrafficStats} fallback derives speed from the delta between calls.
     */
    public void sample(@Nullable PlayerManager player) {
        if (player != null && PlaybackDiagnosticsSourcePolicy.isLocal(player.getUrl())) {
            reset();
            return;
        }
        observe(kernelBitsPerSecond(player));
    }

    /** Applies one sample given the kernel's reported bitrate, 0 when it has none. */
    void observe(long kernelBitsPerSecond) {
        long now = clock.getAsLong();
        long elapsedMs = lastTimeStamp <= 0 || now <= lastTimeStamp ? 0 : now - lastTimeStamp;
        // Advance every baseline on each sample, whichever source ends up winning, so a
        // later handover measures one interval instead of everything since startup.
        long okHttpDelta = advanceOkHttpBaseline();
        long trafficDelta = advanceTrafficStatsBaseline();
        lastTimeStamp = now;

        if (elapsedMs <= 0) {
            // No measurable interval yet; keep the previous readout rather than show 0.
            return;
        }
        // Each counter undercounts what the other sees: OkHttp misses native engine
        // sockets, TrafficStats misses nothing but may be unsupported or frozen. The
        // larger delta is the better lower bound on what actually arrived.
        long delta = Math.max(okHttpDelta, trafficDelta);
        if (trafficDelta > 0) trafficStatsProvenLive = true;
        if (delta > 0) {
            source = trafficDelta > okHttpDelta ? Source.TRAFFIC_STATS : Source.OK_HTTP;
            bytesPerSecond = delta * 1000L / elapsedMs;
            return;
        }
        // Nothing was measured this interval. TrafficStats is the only counter that can
        // see a native engine socket, so once it has proven it counts on this device its
        // zero is the truth — a filled buffer fetches nothing — and the readout falls to
        // zero rather than holding the last value. Until then it may be absent or frozen
        // at a constant, and a native stream would be invisible to both counters however
        // fast it runs. That, and only that, is what the kernel tier covers.
        //
        // OkHttp cannot stand in for that proof: it never sees native sockets, so its
        // deltas say nothing about whether one is transferring.
        if (!trafficStatsProvenLive && kernelBitsPerSecond > 0) {
            bytesPerSecond = kernelBitsPerSecond / 8L;
            source = Source.KERNEL;
            return;
        }
        if (okHttpDelta < 0 && trafficDelta < 0) {
            bytesPerSecond = 0;
            source = Source.NONE;
            return;
        }
        // A counter is readable and reported nothing: an idle interval, not a missing
        // source. Attribute it to whichever counter could see it, and show zero.
        source = trafficDelta >= 0 ? Source.TRAFFIC_STATS : Source.OK_HTTP;
        bytesPerSecond = 0;
    }

    public long getBytesPerSecond() {
        return bytesPerSecond;
    }

    public Source getSource() {
        return source;
    }

    /** True when neither the kernel nor the system counter produced a usable sample. */
    public boolean isUnavailable() {
        return source == Source.NONE;
    }

    public String getText() {
        return source == Source.NONE ? "" : format(bytesPerSecond);
    }

    /** Clears the readout and re-bases the counters to now. */
    public void reset() {
        bytesPerSecond = 0;
        source = Source.NONE;
        markBaselines();
    }

    /** Formats bytes per second the way the playback readouts have always shown it. */
    public static String format(long bytesPerSecond) {
        long kbps = Math.max(0, bytesPerSecond / 1024);
        return kbps < 1000 ? kbps + UNIT_KB : SPEED_FORMAT.format(kbps / 1024f) + UNIT_MB;
    }

    private long kernelBitsPerSecond(@Nullable PlayerManager player) {
        if (player == null) return 0;
        try {
            return player.isExo()
                    ? PlaybackAnalyticsListener.getSnapshot().bandwidthEstimate()
                    : nativeBitsPerSecond(player);
        } catch (Throwable error) {
            // A kernel that cannot answer is expected; fall through to TrafficStats.
            return 0;
        }
    }

    private static long nativeBitsPerSecond(PlayerManager player) {
        PlayerEngine.RuntimeMetrics metrics = player.getRuntimeMetrics();
        if (metrics == null) return 0;
        Long bits = metrics.bandwidthBitsPerSecond();
        return bits == null ? 0 : bits;
    }

    /**
     * Returns bytes read through OkHttp since the last sample, or {@link #UNSUPPORTED}
     * when no baseline was established yet.
     */
    private long advanceOkHttpBaseline() {
        long total = Math.max(0, okHttpCounter.getAsLong());
        long previous = lastOkHttpBytes;
        lastOkHttpBytes = total;
        if (previous < 0 || total < previous) return UNSUPPORTED;
        return total - previous;
    }

    /**
     * Returns bytes counted by TrafficStats since the last sample, or
     * {@link #UNSUPPORTED} when the counter is missing, reset, or unbaselined.
     */
    private long advanceTrafficStatsBaseline() {
        long total = byteCounter.getAsLong();
        long previous = lastRxBytes;
        lastRxBytes = total;
        if (total < 0 || previous < 0 || total < previous) return UNSUPPORTED;
        return total - previous;
    }

    private void markBaselines() {
        lastOkHttpBytes = Math.max(0, okHttpCounter.getAsLong());
        lastRxBytes = byteCounter.getAsLong();
        lastTimeStamp = clock.getAsLong();
    }

    private static long readUidRxBytes() {
        long total = TrafficStats.getUidRxBytes(App.get().getApplicationInfo().uid);
        return total == TrafficStats.UNSUPPORTED ? UNSUPPORTED : total;
    }
}
