package com.fongmi.android.tv.ad.audio;

import java.util.Map;
import java.util.Objects;

public interface AdAudioSignalProvider extends AutoCloseable {

    enum ProviderState {
        DISABLED,
        IDLE,
        RUNNING,
        DEGRADED,
        CLOSED
    }

    enum ResetReason {
        SEEK,
        SOURCE_CHANGED,
        AUDIO_FLUSH,
        ENGINE_REBUILD,
        RELEASE
    }

    enum ErrorCode {
        UNSUPPORTED_MEDIA,
        RULES_UNAVAILABLE,
        START_FAILED,
        ANALYSIS_FAILED,
        RESOURCE_LIMIT
    }

    record SessionContext(long sessionId, long generation,
                          String mediaId, String mediaUrl,
                          Map<String, String> headers) {
        public SessionContext {
            requireTimeline(sessionId, generation);
            mediaId = requireText(mediaId, "mediaId", 512, true);
            mediaUrl = requireText(mediaUrl, "mediaUrl", 8_192, true);
            Objects.requireNonNull(headers, "headers");
            if (headers.size() > 64) {
                throw new IllegalArgumentException("too many media headers");
            }
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requireText(entry.getKey(), "header name", 256, false);
                requireText(entry.getValue(), "header value", 8_192, true);
            }
            headers = Map.copyOf(headers);
        }
    }

    record HostPosition(long sessionId, long generation,
                        long positionMs, long durationMs,
                        boolean seekable, boolean live) {
        public HostPosition {
            requireTimeline(sessionId, generation);
            if (positionMs < 0L || durationMs < -1L) {
                throw new IllegalArgumentException("invalid host position");
            }
        }
    }

    record TimelineReset(long sessionId, long generation,
                         ResetReason reason, long mediaAnchorMs) {
        public TimelineReset {
            requireTimeline(sessionId, generation);
            Objects.requireNonNull(reason, "reason");
            if (mediaAnchorMs < 0L) {
                throw new IllegalArgumentException("mediaAnchorMs must not be negative");
            }
        }
    }

    record AdAudioCandidate(long sessionId, long generation,
                            String ruleId, String ruleVersion,
                            long startMs, long endMs,
                            boolean fullMatch, double similarity,
                            String providerId) {
        public AdAudioCandidate {
            requireTimeline(sessionId, generation);
            ruleId = requireText(ruleId, "ruleId", 128, false);
            ruleVersion = requireText(ruleVersion, "ruleVersion", 128, false);
            providerId = requireText(providerId, "providerId", 64, false);
            if (startMs < 0L || endMs <= startMs) {
                throw new IllegalArgumentException("invalid candidate interval");
            }
            if (!Double.isFinite(similarity) || similarity < 0d || similarity > 1d) {
                throw new IllegalArgumentException("similarity must be between 0 and 1");
            }
        }
    }

    record ProviderError(String providerId, ErrorCode code, String detail) {
        public ProviderError {
            providerId = requireText(providerId, "providerId", 64, false);
            Objects.requireNonNull(code, "code");
            detail = requireText(detail, "detail", 512, true);
        }
    }

    interface Listener {
        void onCandidate(AdAudioCandidate candidate);

        void onProviderError(ProviderError error);

        default void onTimelineReset(TimelineReset reset) {
        }
    }

    String id();

    void start(SessionContext context, AdAudioRuleSnapshot rules, Listener listener);

    void onHostPosition(HostPosition position);

    void onTimelineReset(TimelineReset reset);

    void setEnabled(boolean enabled);

    ProviderState state();

    @Override
    void close();

    private static void requireTimeline(long sessionId, long generation) {
        if (sessionId < 0L || generation < 0L) {
            throw new IllegalArgumentException("session and generation must not be negative");
        }
    }

    private static String requireText(String value, String name,
                                      int maxLength, boolean allowEmpty) {
        Objects.requireNonNull(value, name);
        if ((!allowEmpty && value.isEmpty()) || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
