package com.fongmi.android.tv.player;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.StuckPlayerException;
import androidx.media3.mpvplayer.MpvPlayer;

import android.os.Bundle;

import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PlaybackErrorClassifierTest {

    @Test
    public void classifiesRegisteredAppPortConnectFailure() {
        try (PlaybackRouteRegistry.Registration ignored = PlaybackRouteRegistry.registerAppService(7788, PlaybackRouteRegistry.AppOwner.MAIN_SERVER)) {
            PlaybackErrorClassifier.Failure failure = PlaybackErrorClassifier.classify(
                    error("connect failed", new ConnectException("Connection refused"), PlaybackException.ERROR_CODE_IO_UNSPECIFIED),
                    PlaybackRoute.resolve("http://127.0.0.1:7788/video"));

            assertEquals(PlaybackErrorClassifier.Stage.LOCAL_ENDPOINT, failure.stage());
            assertEquals(PlaybackRoute.Owner.APP_MAIN_SERVER, failure.route().owner());
            assertEquals(PlaybackErrorClassifier.Confidence.CONFIRMED, failure.confidence());
        }
    }

    @Test
    public void classifiesExternalLoopbackConnectionCodeAsLocalEndpoint() {
        PlaybackErrorClassifier.Failure failure = PlaybackErrorClassifier.classify(
                error("connection failed", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED),
                PlaybackRoute.resolve("http://127.0.0.1:8899/video"));

        assertEquals(PlaybackErrorClassifier.Stage.LOCAL_ENDPOINT, failure.stage());
        assertEquals(PlaybackRoute.Owner.EXTERNAL_OR_UNKNOWN_LOOPBACK, failure.route().owner());
    }

    @Test
    public void doesNotCallGenericProxyIoAPortFailure() {
        try (PlaybackRouteRegistry.Registration ignored = PlaybackRouteRegistry.registerAppService(7799, PlaybackRouteRegistry.AppOwner.HLS_PROXY)) {
            PlaybackErrorClassifier.Failure failure = PlaybackErrorClassifier.classify(
                    error("upstream read failed", new IOException("upstream reset"), PlaybackException.ERROR_CODE_IO_UNSPECIFIED),
                    PlaybackRoute.resolve("http://127.0.0.1:7799/mpv/index.m3u8"));

            assertEquals(PlaybackErrorClassifier.Stage.NETWORK_IO, failure.stage());
            assertEquals(PlaybackErrorClassifier.Confidence.INFERRED, failure.confidence());
        }
    }

    @Test
    public void doesNotCallMpvUpstreamNetworkMarkerAPortFailure() {
        try (PlaybackRouteRegistry.Registration ignored = PlaybackRouteRegistry.registerAppService(7798, PlaybackRouteRegistry.AppOwner.HLS_PROXY)) {
            PlaybackErrorClassifier.Failure failure = PlaybackErrorClassifier.classify(
                    error(MpvPlayer.ERROR_NETWORK_FAILED + ": upstream HTTP failure", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED),
                    PlaybackRoute.resolve("http://127.0.0.1:7798/mpv/index.m3u8"));

            assertEquals(PlaybackErrorClassifier.Stage.NETWORK_IO, failure.stage());
            assertEquals("mpv-network-marker", failure.evidence());
        }
    }

    @Test
    public void classifiesRemoteHttpAndParserFailuresByExactCode() {
        PlaybackErrorClassifier.Failure network = PlaybackErrorClassifier.classify(
                error("HTTP 503", null, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS),
                PlaybackRoute.resolve("https://cdn.example.com/video.mkv"));
        PlaybackErrorClassifier.Failure parser = PlaybackErrorClassifier.classify(
                error("bad container", null, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED),
                PlaybackRoute.resolve("https://cdn.example.com/video.mkv"));

        assertEquals(PlaybackErrorClassifier.Stage.NETWORK_IO, network.stage());
        assertEquals(PlaybackErrorClassifier.Stage.MEDIA_PARSING, parser.stage());
    }

    @Test
    public void classifiesDecoderOutputAndDrmByExactCode() {
        PlaybackRoute.Resolution route = PlaybackRoute.resolve("https://cdn.example.com/video.mkv");

        assertEquals(PlaybackErrorClassifier.Stage.DECODER, PlaybackErrorClassifier.classify(error("decoder", null, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED), route).stage());
        assertEquals(PlaybackErrorClassifier.Stage.OUTPUT, PlaybackErrorClassifier.classify(error("audio", null, PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED), route).stage());
        assertEquals(PlaybackErrorClassifier.Stage.DRM, PlaybackErrorClassifier.classify(error("drm", null, PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED), route).stage());
    }

    @Test
    public void usesStableMpvMarkersWithoutInspectingFreeFormDetails() {
        PlaybackRoute.Resolution route = PlaybackRoute.resolve("https://cdn.example.com/video.mkv");

        assertEquals(PlaybackErrorClassifier.Stage.MEDIA_PARSING, PlaybackErrorClassifier.classify(error(MpvPlayer.ERROR_NO_AV_DATA + ": details", null, PlaybackException.ERROR_CODE_DECODING_FAILED), route).stage());
        assertEquals(PlaybackErrorClassifier.Stage.DECODER, PlaybackErrorClassifier.classify(error(MpvPlayer.ERROR_DECODE_FAILED + ": details", null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED), route).stage());
        assertEquals(PlaybackErrorClassifier.Stage.OUTPUT, PlaybackErrorClassifier.classify(error(MpvPlayer.ERROR_VIDEO_OUTPUT_FAILED + ": details", null, PlaybackException.ERROR_CODE_UNSPECIFIED), route).stage());
    }

    @Test
    public void leavesUnprovenFailureUnknownAndKeepsTracePrivate() {
        PlaybackErrorClassifier.Failure failure = PlaybackErrorClassifier.classify(
                error("token=secret at https://private.example.com/movie", null, PlaybackException.ERROR_CODE_UNSPECIFIED),
                PlaybackRoute.resolve("https://private.example.com/movie?token=secret"));
        String summary = failure.logSummary();

        assertEquals(PlaybackErrorClassifier.Stage.UNKNOWN, failure.stage());
        assertFalse(summary.contains("private.example.com"));
        assertFalse(summary.contains("movie"));
        assertFalse(summary.contains("secret"));
        assertFalse(summary.contains("token"));
    }

    @Test
    public void typedPlaybackStall_isNeitherNetworkNorUnprovenAudioFailure() {
        PlaybackRoute.Resolution route = PlaybackRoute.resolve("https://cdn.example/video");
        for (int type : new int[]{StuckPlayerException.STUCK_PLAYING_NO_PROGRESS,
                StuckPlayerException.STUCK_BUFFERING_NO_PROGRESS,
                StuckPlayerException.STUCK_PLAYING_NOT_ENDING}) {
            PlaybackErrorClassifier.Failure failure = PlaybackErrorClassifier.classify(
                    error("timeout", new IllegalStateException(new StuckPlayerException(type, 10_000)),
                            PlaybackException.ERROR_CODE_TIMEOUT), route);
            assertEquals(PlaybackErrorClassifier.Stage.UNKNOWN, failure.stage());
            assertEquals("playback-stuck", failure.evidence());
        }
    }

    @Test
    public void untypedTimeout_preservesLegacyIjkAndNetworkClassification() {
        PlaybackRoute.Resolution route = PlaybackRoute.resolve("https://cdn.example/video");
        assertEquals(PlaybackErrorClassifier.Stage.NETWORK_IO, PlaybackErrorClassifier.classify(
                error("timeout", null, PlaybackException.ERROR_CODE_TIMEOUT), route).stage());
        assertEquals(PlaybackErrorClassifier.Stage.NETWORK_IO, PlaybackErrorClassifier.classify(
                error("timeout", new SocketTimeoutException(), PlaybackException.ERROR_CODE_TIMEOUT), route).stage());
        assertEquals(PlaybackErrorClassifier.Stage.NETWORK_IO, PlaybackErrorClassifier.classify(
                error("Player stuck", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT), route).stage());
    }

    private static PlaybackException error(String message, Throwable cause, int code) {
        return new TestPlaybackException(message, cause, code);
    }

    private static final class TestPlaybackException extends PlaybackException {

        private TestPlaybackException(String message, Throwable cause, int code) {
            super(message, cause, code, Bundle.EMPTY, 0);
        }
    }
}
