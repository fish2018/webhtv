package com.fongmi.android.tv.player.mpv;

import com.fongmi.android.tv.player.PlaybackAutoContext;

/** Pure admission policy for MPV automatic HLS VOD proxy preloading. */
public final class MpvPreloadPolicy {

    public static final long PAUSE_RATIO_PERMILLE = 1_150L;
    public static final long RESUME_RATIO_PERMILLE = 1_400L;
    public static final long LOW_BUFFER_MS = 8_000L;
    public static final long SAFE_BUFFER_MS = 15_000L;

    private MpvPreloadPolicy() {
    }

    public static Assessment assess(Request request) {
        Request current = request == null ? Request.inactive() : request;
        if (!current.automatic()) return Assessment.inactive(current, Reason.NOT_AUTOMATIC);
        if (!current.mpvKernel()) return Assessment.inactive(current, Reason.NOT_MPV);
        if (!current.performanceOptionsPriority()) {
            return Assessment.inactive(current, Reason.CONFIG_PRIORITY);
        }
        if (!current.preloadConfigured()) return block(current, Reason.PRELOAD_DISABLED, 0);
        if (!current.protocolUsable()) return block(current, Reason.PROTOCOL_UNKNOWN, 0);
        if (current.protocol() != PlaybackAutoContext.Protocol.HLS) {
            return block(current, Reason.NOT_HLS, 0);
        }
        if (!current.streamKindUsable()) return block(current, Reason.STREAM_UNKNOWN, 0);
        if (current.streamKind() != PlaybackAutoContext.StreamKind.VOD) {
            return block(current, Reason.NOT_VOD, 0);
        }
        if (!current.playerPathUsable() || !current.upstreamPathUsable()) {
            return block(current, Reason.PATH_UNKNOWN, 0);
        }
        if (current.playerPath() == PlaybackAutoContext.PathKind.EXTERNAL_LOOPBACK
                || current.upstreamPath() == PlaybackAutoContext.PathKind.EXTERNAL_LOOPBACK) {
            return block(current, Reason.EXTERNAL_LOOPBACK, 0);
        }
        if (!current.upstreamStateUsable()) {
            return block(current, Reason.UPSTREAM_UNKNOWN, 0);
        }
        if (current.upstreamState() == PlaybackAutoContext.UpstreamState.OPAQUE) {
            return block(current, Reason.UPSTREAM_OPAQUE, 0);
        }
        if (current.upstreamState() != PlaybackAutoContext.UpstreamState.VISIBLE) {
            return block(current, Reason.UPSTREAM_UNKNOWN, 0);
        }
        if (!current.resourcePreloadAllowed()) {
            return block(current, Reason.RESOURCE_GATE, 0);
        }
        if (!current.cacheEnabled()) return block(current, Reason.CACHE_DISABLED, 0);
        if (current.cacheCircuitOpen()) return block(current, Reason.CACHE_CIRCUIT_OPEN, 0);
        if (!current.cacheStorageKnown()) {
            return block(current, Reason.CACHE_STORAGE_UNKNOWN, 0);
        }
        if (!current.bufferUsable()) return block(current, Reason.BUFFER_UNKNOWN, 0);
        // Foreground requests are independently prioritized and cancel active
        // preload work. Keep the disk worker admitted during short buffer dips
        // so paused playback can continue filling the disk timeline.
        if (current.buffering()) return bootstrap(current, Reason.BUFFERING);
        if (current.rebufferRisk()) return bootstrap(current, Reason.REBUFFER);
        if (current.bufferDeclining()) return bootstrap(current, Reason.BUFFER_DECLINING);
        if (current.bufferedDurationMs() < LOW_BUFFER_MS) {
            return bootstrap(current, Reason.LOW_BUFFER);
        }
        if (current.foregroundRequests() > 0) {
            return new Assessment(true, Signal.SUSPEND, Reason.FOREGROUND_ACTIVE,
                    0, current);
        }
        if (!current.cacheBudgetAvailable()) {
            return block(current, Reason.CACHE_BUDGET_EXHAUSTED, 0);
        }
        if (!current.throughputKnown()) {
            return new Assessment(true, Signal.BOOTSTRAP,
                    Reason.THROUGHPUT_BOOTSTRAP, 0, current);
        }
        if (!current.throughputFresh()) {
            return new Assessment(true, Signal.BOOTSTRAP,
                    Reason.THROUGHPUT_REFRESH, 0, current);
        }
        if (current.upstreamBitsPerSecond() <= 0) {
            return new Assessment(true, Signal.BOOTSTRAP,
                    Reason.THROUGHPUT_BOOTSTRAP, 0, current);
        }
        if (current.selectedBitsPerSecond() <= 0) {
            return block(current, Reason.SELECTED_BITRATE_UNKNOWN, 0);
        }
        long ratio = ratioPermille(
                current.upstreamBitsPerSecond(), current.selectedBitsPerSecond());
        if (ratio < PAUSE_RATIO_PERMILLE) {
            return new Assessment(true, Signal.BOOTSTRAP, Reason.RATIO_LOW,
                    ratio, current);
        }
        if (ratio >= RESUME_RATIO_PERMILLE
                && current.bufferedDurationMs() >= SAFE_BUFFER_MS) {
            return new Assessment(true, Signal.RECOVER, Reason.RECOVERY_EVIDENCE,
                    ratio, current);
        }
        return new Assessment(true, Signal.HOLD, Reason.HYSTERESIS_HOLD,
                ratio, current);
    }

    public static int resolveExecutorThreads(boolean automatic, int configuredThreads) {
        if (automatic) return 1;
        return Math.clamp(configuredThreads, 1, 4);
    }

    public static boolean ownsProxyControl(
            boolean automatic,
            boolean performanceOptionsPriority) {
        return automatic && performanceOptionsPriority;
    }

    private static Assessment block(Request request, Reason reason, long ratio) {
        return new Assessment(true, Signal.BLOCK, reason, ratio, request);
    }

    private static Assessment bootstrap(Request request, Reason reason) {
        return new Assessment(true, Signal.BOOTSTRAP, reason, 0, request);
    }

    private static long ratioPermille(long throughput, long selectedBitrate) {
        if (throughput <= 0 || selectedBitrate <= 0) return 0;
        long quotient = throughput / selectedBitrate;
        long remainder = throughput % selectedBitrate;
        if (quotient > Long.MAX_VALUE / 1_000L) return Long.MAX_VALUE;
        long whole = quotient * 1_000L;
        long fraction = remainder > Long.MAX_VALUE / 1_000L
                ? (long) ((double) remainder * 1_000d / selectedBitrate)
                : remainder * 1_000L / selectedBitrate;
        return whole > Long.MAX_VALUE - fraction ? Long.MAX_VALUE : whole + fraction;
    }

    public record Request(
            boolean automatic,
            boolean mpvKernel,
            boolean performanceOptionsPriority,
            boolean preloadConfigured,
            PlaybackAutoContext.Protocol protocol,
            boolean protocolUsable,
            PlaybackAutoContext.StreamKind streamKind,
            boolean streamKindUsable,
            PlaybackAutoContext.PathKind playerPath,
            boolean playerPathUsable,
            PlaybackAutoContext.PathKind upstreamPath,
            boolean upstreamPathUsable,
            PlaybackAutoContext.UpstreamState upstreamState,
            boolean upstreamStateUsable,
            boolean resourcePreloadAllowed,
            boolean cacheEnabled,
            boolean cacheStorageKnown,
            boolean cacheBudgetAvailable,
            boolean cacheCircuitOpen,
            long upstreamBitsPerSecond,
            boolean throughputKnown,
            boolean throughputFresh,
            long throughputSampleAtElapsedMs,
            long selectedBitsPerSecond,
            boolean bufferUsable,
            long bufferedDurationMs,
            long runtimeSampleAtElapsedMs,
            int rebufferCount,
            boolean buffering,
            boolean bufferDeclining,
            boolean rebufferRisk,
            int foregroundRequests,
            long contextRevision) {

        public Request {
            protocol = protocol == null ? PlaybackAutoContext.Protocol.UNKNOWN : protocol;
            streamKind = streamKind == null
                    ? PlaybackAutoContext.StreamKind.UNKNOWN : streamKind;
            playerPath = playerPath == null
                    ? PlaybackAutoContext.PathKind.UNKNOWN : playerPath;
            upstreamPath = upstreamPath == null
                    ? PlaybackAutoContext.PathKind.UNKNOWN : upstreamPath;
            upstreamState = upstreamState == null
                    ? PlaybackAutoContext.UpstreamState.UNKNOWN : upstreamState;
            upstreamBitsPerSecond = Math.max(0, upstreamBitsPerSecond);
            selectedBitsPerSecond = Math.max(0, selectedBitsPerSecond);
            bufferedDurationMs = Math.max(0, bufferedDurationMs);
            rebufferCount = Math.max(0, rebufferCount);
            foregroundRequests = Math.max(0, foregroundRequests);
            contextRevision = Math.max(0, contextRevision);
        }

        public Request withRuntimeRisk(boolean declining, boolean rebuffer) {
            return new Request(
                    automatic, mpvKernel, performanceOptionsPriority,
                    preloadConfigured, protocol, protocolUsable, streamKind,
                    streamKindUsable, playerPath, playerPathUsable, upstreamPath,
                    upstreamPathUsable, upstreamState, upstreamStateUsable,
                    resourcePreloadAllowed, cacheEnabled, cacheStorageKnown,
                    cacheBudgetAvailable, cacheCircuitOpen,
                    upstreamBitsPerSecond, throughputKnown, throughputFresh,
                    throughputSampleAtElapsedMs, selectedBitsPerSecond,
                    bufferUsable, bufferedDurationMs, runtimeSampleAtElapsedMs,
                    rebufferCount, buffering,
                    bufferDeclining || declining, rebufferRisk || rebuffer,
                    foregroundRequests, contextRevision);
        }

        static Request inactive() {
            return new Request(false, false, false, false,
                    PlaybackAutoContext.Protocol.UNKNOWN, false,
                    PlaybackAutoContext.StreamKind.UNKNOWN, false,
                    PlaybackAutoContext.PathKind.UNKNOWN, false,
                    PlaybackAutoContext.PathKind.UNKNOWN, false,
                    PlaybackAutoContext.UpstreamState.UNKNOWN, false,
                    false, false, false, false, false,
                    0, false, false, -1, 0,
                    false, 0, -1, 0,
                    false, false, false, 0, 0);
        }
    }

    public record Assessment(
            boolean active,
            Signal signal,
            Reason reason,
            long ratioPermille,
            Request request) {

        public Assessment {
            signal = signal == null ? Signal.BLOCK : signal;
            reason = reason == null ? Reason.THROUGHPUT_UNKNOWN : reason;
            ratioPermille = Math.max(0, ratioPermille);
            request = request == null ? Request.inactive() : request;
        }

        static Assessment inactive(Request request, Reason reason) {
            return new Assessment(false, Signal.INACTIVE, reason, 0, request);
        }
    }

    public enum Signal {
        INACTIVE("inactive"),
        BLOCK("block"),
        SUSPEND("suspend"),
        BOOTSTRAP("bootstrap"),
        HOLD("hold"),
        RECOVER("recover");

        private final String label;

        Signal(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Reason {
        NOT_AUTOMATIC("not-automatic"),
        NOT_MPV("not-mpv"),
        CONFIG_PRIORITY("config-priority"),
        PRELOAD_DISABLED("preload-disabled"),
        PROTOCOL_UNKNOWN("protocol-unknown"),
        NOT_HLS("not-hls"),
        STREAM_UNKNOWN("stream-unknown"),
        NOT_VOD("not-vod"),
        PATH_UNKNOWN("path-unknown"),
        EXTERNAL_LOOPBACK("external-loopback"),
        UPSTREAM_UNKNOWN("upstream-unknown"),
        UPSTREAM_OPAQUE("upstream-opaque"),
        RESOURCE_GATE("resource-gate"),
        CACHE_DISABLED("cache-disabled"),
        CACHE_STORAGE_UNKNOWN("cache-storage-unknown"),
        CACHE_BUDGET_EXHAUSTED("cache-budget-exhausted"),
        CACHE_CIRCUIT_OPEN("cache-circuit-open"),
        THROUGHPUT_UNKNOWN("throughput-unknown"),
        THROUGHPUT_STALE("throughput-stale"),
        THROUGHPUT_BOOTSTRAP("throughput-bootstrap"),
        THROUGHPUT_REFRESH("throughput-refresh"),
        SELECTED_BITRATE_UNKNOWN("selected-bitrate-unknown"),
        BUFFER_UNKNOWN("buffer-unknown"),
        BUFFERING("buffering"),
        REBUFFER("rebuffer"),
        BUFFER_DECLINING("buffer-declining"),
        LOW_BUFFER("low-buffer"),
        RATIO_LOW("ratio-low"),
        FOREGROUND_ACTIVE("foreground-active"),
        RECOVERY_EVIDENCE("recovery-evidence"),
        HYSTERESIS_HOLD("hysteresis-hold");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
