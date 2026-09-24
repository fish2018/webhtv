package com.fongmi.android.tv.player;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.StuckPlayerException;
import androidx.media3.mpvplayer.MpvPlayer;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public final class PlaybackErrorClassifier {

    private PlaybackErrorClassifier() {
    }

    public static Failure classify(PlaybackException error, PlaybackRoute.Resolution route) {
        PlaybackRoute.Resolution resolution = route == null ? PlaybackRoute.resolve(null) : route;
        if (error == null) return failure(Stage.UNKNOWN, Confidence.UNKNOWN, "missing-error", PlaybackException.ERROR_CODE_UNSPECIFIED, resolution);
        String message = error.getMessage();

        Failure marked = classifyStableMarker(message, error.errorCode, resolution);
        if (marked != null) return marked;

        // The player can time out while data is already buffered. Its stuck detector
        // alone identifies neither network I/O nor a particular audio/video renderer.
        if (hasCause(error, StuckPlayerException.class)) {
            return failure(Stage.UNKNOWN, Confidence.CONFIRMED, "playback-stuck",
                    error.errorCode, resolution);
        }

        if (isLocalConnectFailure(error, resolution)) {
            String evidence = hasCause(error, ConnectException.class) || hasCause(error, NoRouteToHostException.class)
                    ? "local-connect-exception"
                    : "local-connect-error-code";
            return failure(Stage.LOCAL_ENDPOINT, Confidence.CONFIRMED, evidence, error.errorCode, resolution);
        }

        Stage exact = exactStage(error.errorCode);
        if (exact != null) return failure(exact, Confidence.CONFIRMED, "media3-error-code", error.errorCode, resolution);

        if (isHttpRoute(resolution) && isNetworkCause(error)) {
            return failure(Stage.NETWORK_IO, Confidence.INFERRED, "network-cause", error.errorCode, resolution);
        }
        return failure(Stage.UNKNOWN, Confidence.UNKNOWN, "unclassified", error.errorCode, resolution);
    }

    private static Failure classifyStableMarker(String message, int errorCode, PlaybackRoute.Resolution route) {
        if (startsWith(message, MpvPlayer.ERROR_NETWORK_FAILED)) return failure(Stage.NETWORK_IO, Confidence.CONFIRMED, "mpv-network-marker", errorCode, route);
        if (startsWith(message, MpvPlayer.ERROR_HLS_PLAYBACK_FAILED)) return failure(Stage.MEDIA_PARSING, Confidence.INFERRED, "mpv-hls-input-marker", errorCode, route);
        if (startsWith(message, MpvPlayer.ERROR_UNEXPECTED_IMAGE)) return failure(Stage.MEDIA_PARSING, Confidence.CONFIRMED, "mpv-image-marker", errorCode, route);
        if (startsWith(message, MpvPlayer.ERROR_NO_AV_DATA)) return failure(Stage.MEDIA_PARSING, Confidence.CONFIRMED, "mpv-no-av-marker", errorCode, route);
        if (startsWith(message, MpvPlayer.ERROR_INVALID_MEDIA_DATA)) return failure(Stage.MEDIA_PARSING, Confidence.CONFIRMED, "mpv-invalid-media-marker", errorCode, route);
        if (startsWith(message, MpvPlayer.ERROR_DECODE_FAILED)) return failure(Stage.DECODER, Confidence.CONFIRMED, "mpv-decode-marker", errorCode, route);
        if (startsWith(message, MpvPlayer.ERROR_VIDEO_OUTPUT_FAILED)) return failure(Stage.OUTPUT, Confidence.CONFIRMED, "mpv-video-output-marker", errorCode, route);
        if (startsWith(message, MpvPlayer.ERROR_DRM_UNSUPPORTED)) return failure(Stage.DRM, Confidence.CONFIRMED, "mpv-drm-marker", errorCode, route);
        return null;
    }

    private static Stage exactStage(int errorCode) {
        return switch (errorCode) {
            // IJK also maps its native timed-out errors to this legacy code. Keep
            // that contract; typed Media3 playback stalls are handled above.
            case PlaybackException.ERROR_CODE_TIMEOUT,
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> Stage.NETWORK_IO;
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> Stage.MEDIA_PARSING;
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> Stage.DECODER;
            case PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
                    PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED,
                    PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED,
                    PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED,
                    PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED -> Stage.OUTPUT;
            case PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
                    PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
                    PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
                    PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
                    PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED,
                    PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
                    PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION,
                    PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> Stage.DRM;
            default -> null;
        };
    }

    private static boolean isLocalConnectFailure(PlaybackException error, PlaybackRoute.Resolution route) {
        if (!route.loopback()) return false;
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED || error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) return true;
        return hasCause(error, ConnectException.class) || hasCause(error, NoRouteToHostException.class);
    }

    private static boolean isHttpRoute(PlaybackRoute.Resolution route) {
        return route.route() == PlaybackRoute.DIRECT_REMOTE_HTTP || route.loopback();
    }

    private static boolean isNetworkCause(Throwable error) {
        return hasCause(error, ConnectException.class)
                || hasCause(error, NoRouteToHostException.class)
                || hasCause(error, SocketTimeoutException.class)
                || hasCause(error, UnknownHostException.class)
                || hasCause(error, IOException.class);
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 12) {
            if (type.isInstance(current)) return true;
            Throwable next = current.getCause();
            if (next == current) break;
            current = next;
        }
        return false;
    }

    private static boolean startsWith(String value, String prefix) {
        return value != null && value.startsWith(prefix);
    }

    private static Failure failure(Stage stage, Confidence confidence, String evidence, int errorCode, PlaybackRoute.Resolution route) {
        return new Failure(stage, confidence, evidence, PlaybackException.getErrorCodeName(errorCode), route);
    }

    public enum Stage {
        LOCAL_ENDPOINT("local-endpoint"),
        NETWORK_IO("network-io"),
        MEDIA_PARSING("media-parsing"),
        DECODER("decoder"),
        OUTPUT("output"),
        DRM("drm"),
        UNKNOWN("unknown");

        private final String label;

        Stage(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Confidence {
        CONFIRMED("confirmed"),
        INFERRED("inferred"),
        UNKNOWN("unknown");

        private final String label;

        Confidence(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Failure(Stage stage, Confidence confidence, String evidence, String errorCode, PlaybackRoute.Resolution route) {

        public String logSummary() {
            return "stage=" + stage.label() +
                    " confidence=" + confidence.label() +
                    " evidence=" + evidence +
                    " errorCode=" + errorCode +
                    " " + route.logSummary();
        }
    }
}
