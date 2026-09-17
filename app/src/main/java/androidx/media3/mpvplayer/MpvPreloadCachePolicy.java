package androidx.media3.mpvplayer;

import com.fongmi.android.tv.player.PlaybackAutoContext;

/** Pure policy for extending MPV's forward cache during playback and allowed pauses. */
final class MpvPreloadCachePolicy {

    private static final int MAX_TARGET_SECONDS = 24 * 60 * 60;

    private MpvPreloadCachePolicy() {
    }

    static Decision resolve(Request request) {
        Request current = request == null ? Request.inactive() : request;
        if (!current.preloadConfigured()) return hold(current, Reason.PRELOAD_DISABLED);
        if (current.paused() && !current.pauseAllowed()) {
            return hold(current, Reason.PAUSE_POLICY);
        }
        if (!current.performanceOptionsPriority()) return hold(current, Reason.CONFIG_PRIORITY);
        if (!current.cacheEnabled()) return hold(current, Reason.CACHE_DISABLED);
        if (!supportsForwardPreload(current)) return hold(current, Reason.NOT_PROGRESSIVE);
        if (current.streamKind() == PlaybackAutoContext.StreamKind.LIVE
                || current.streamKind() == PlaybackAutoContext.StreamKind.LOW_LATENCY_LIVE) {
            return hold(current, Reason.LIVE_STREAM);
        }
        if (current.streamKind() != PlaybackAutoContext.StreamKind.VOD
                && current.durationMs() <= 0) {
            return hold(current, Reason.DURATION_UNKNOWN);
        }
        long remainingMs = current.durationMs() > 0
                ? Math.max(0, current.durationMs() - current.positionMs()) : 0;
        int targetSeconds;
        if (current.aheadSeconds() == 0) {
            if (remainingMs <= 0) return hold(current, Reason.DURATION_UNKNOWN);
            targetSeconds = ceilSeconds(remainingMs);
        } else {
            targetSeconds = current.aheadSeconds();
            if (current.durationMs() > 0) {
                targetSeconds = Math.min(targetSeconds, ceilSeconds(remainingMs));
            }
        }
        targetSeconds = Math.clamp(targetSeconds, 0, MAX_TARGET_SECONDS);
        if (targetSeconds <= current.baselineSeconds()) {
            return hold(current, Reason.ALREADY_AHEAD);
        }
        long targetBytes = Math.max(
                current.baselineBytes(), current.preloadCapacityBytes());
        return new Decision(true, targetSeconds, targetBytes,
                Reason.EXTEND_CACHE, current);
    }

    private static boolean supportsForwardPreload(Request request) {
        return supportsForwardPreload(request.protocol(), request.playerPath());
    }

    static boolean supportsForwardPreload(
            PlaybackAutoContext.Protocol protocol,
            PlaybackAutoContext.PathKind playerPath) {
        PlaybackAutoContext.Protocol resolvedProtocol = protocol == null
                ? PlaybackAutoContext.Protocol.UNKNOWN : protocol;
        PlaybackAutoContext.PathKind resolvedPath = playerPath == null
                ? PlaybackAutoContext.PathKind.UNKNOWN : playerPath;
        if (resolvedProtocol == PlaybackAutoContext.Protocol.PROGRESSIVE_HTTP) {
            return true;
        }
        if (resolvedProtocol != PlaybackAutoContext.Protocol.UNKNOWN) return false;
        // Match Exo/IJK: an opaque HTTP(S) URL is still a cacheable network
        // source. File extensions and MIME hints improve diagnostics, but must
        // never be a prerequisite for forward buffering.
        return resolvedPath == PlaybackAutoContext.PathKind.REMOTE
                || resolvedPath == PlaybackAutoContext.PathKind.LAN_PRIVATE
                || resolvedPath == PlaybackAutoContext.PathKind.EXTERNAL_LOOPBACK
                || resolvedPath == PlaybackAutoContext.PathKind.APP_INTERNAL_SERVICE;
    }

    private static Decision hold(Request request, Reason reason) {
        return new Decision(false, request.baselineSeconds(),
                request.baselineBytes(), reason, request);
    }

    private static int ceilSeconds(long milliseconds) {
        if (milliseconds <= 0) return 0;
        long seconds = milliseconds / 1000 + (milliseconds % 1000 == 0 ? 0 : 1);
        return seconds >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }

    record Request(
            boolean paused,
            boolean preloadConfigured,
            boolean pauseAllowed,
            boolean performanceOptionsPriority,
            boolean cacheEnabled,
            PlaybackAutoContext.Protocol protocol,
            PlaybackAutoContext.StreamKind streamKind,
            PlaybackAutoContext.PathKind playerPath,
            int baselineSeconds,
            long baselineBytes,
            int aheadSeconds,
            long preloadCapacityBytes,
            long positionMs,
            long durationMs) {

        Request {
            protocol = protocol == null ? PlaybackAutoContext.Protocol.UNKNOWN : protocol;
            streamKind = streamKind == null
                    ? PlaybackAutoContext.StreamKind.UNKNOWN : streamKind;
            playerPath = playerPath == null
                    ? PlaybackAutoContext.PathKind.UNKNOWN : playerPath;
            baselineSeconds = Math.max(0, baselineSeconds);
            baselineBytes = Math.max(0, baselineBytes);
            aheadSeconds = Math.max(0, aheadSeconds);
            preloadCapacityBytes = Math.max(0, preloadCapacityBytes);
            positionMs = Math.max(0, positionMs);
            durationMs = Math.max(0, durationMs);
        }

        static Request inactive() {
            return new Request(false, false, false, false, false,
                    PlaybackAutoContext.Protocol.UNKNOWN,
                    PlaybackAutoContext.StreamKind.UNKNOWN,
                    PlaybackAutoContext.PathKind.UNKNOWN,
                    0, 0, 0, 0, 0, 0);
        }
    }

    record Decision(
            boolean apply,
            int targetSeconds,
            long targetBytes,
            Reason reason,
            Request request) {

        Decision {
            targetSeconds = Math.max(0, targetSeconds);
            targetBytes = Math.max(0, targetBytes);
            reason = reason == null ? Reason.PRELOAD_DISABLED : reason;
            request = request == null ? Request.inactive() : request;
        }
    }

    enum Reason {
        PRELOAD_DISABLED,
        PAUSE_POLICY,
        CONFIG_PRIORITY,
        CACHE_DISABLED,
        NOT_PROGRESSIVE,
        LIVE_STREAM,
        DURATION_UNKNOWN,
        ALREADY_AHEAD,
        EXTEND_CACHE
    }
}
