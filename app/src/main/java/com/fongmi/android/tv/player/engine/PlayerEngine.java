package com.fongmi.android.tv.player.engine;

import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaEdition;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;

import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.PlaybackRoute;
import com.fongmi.android.tv.player.PlaybackResourceClassifier;
import com.fongmi.android.tv.player.PlaybackTrace;
import com.fongmi.android.tv.player.lut.MpvLutShader;

import java.util.Collections;
import java.util.List;

public interface PlayerEngine {

    int SOFT = 0;
    int HARD = 1;

    Player getPlayer();

    void release();

    Player rebuild(Player.Listener listener);

    int getDecode();

    void setDecode(int decode);

    boolean isHard();

    String getDecodeText();

    void start(PlaySpec spec);

    default void start(PlaySpec spec, boolean playWhenReady) {
        start(spec);
    }

    default void start(PlaySpec spec, long position, boolean playWhenReady) {
        start(spec, playWhenReady);
    }

    default void restart(PlaySpec spec, long position, boolean playWhenReady) {
        start(spec, position, playWhenReady);
    }

    default void stop() {
        getPlayer().stop();
    }

    void setMetadata(MediaMetadata data);

    boolean isLive();

    boolean isVod();

    void setTrack(List<Track> tracks);

    void resetTrack();

    default void restoreVideoTrack() {
    }

    boolean haveTrack(int type);

    Tracks getCurrentTracks();

    default boolean supportsVideoEffects() {
        return false;
    }

    default void setVideoEffects(List<Effect> effects) {
    }

    default boolean supportsNativeLut() {
        return false;
    }

    default boolean supportsLut() {
        return supportsVideoEffects() || supportsNativeLut();
    }

    default void setNativeLutShader(MpvLutShader shader) {
    }

    default void setNativeLutPreviewProgress(float progress) {
    }

    default Format getVideoFormat() {
        return null;
    }

    /** Returns only runtime-observed playback facts; requested decode/output values must not be substituted. */
    default PlaybackFactsSnapshot getPlaybackFactsSnapshot() {
        return PlaybackFactsSnapshot.empty();
    }

    default PlayerCacheState getCacheState() {
        return PlayerCacheState.empty();
    }

    default String getRenderDiagnostics() {
        return "";
    }

    default String getRuntimeDiagnostics() {
        return "";
    }

    /** Renderer-specific GPU timing/load. Implementations must label non-system estimates. */
    default String getGpuLoadDiagnostics() {
        return "";
    }

    /** Enables renderer-specific sampling only while the diagnostics panel is visible. */
    default void setGpuLoadDiagnosticsEnabled(boolean enabled) {
    }

    /** Source-track identity and runtime decode/output facts for the selected video track. */
    default VideoPlaybackDetails getVideoPlaybackDetails() {
        return VideoPlaybackDetails.empty();
    }

    default long getDroppedFrames() {
        return 0;
    }

    /** Runtime metrics observed by a native engine. Unknown values are null. */
    default RuntimeMetrics getRuntimeMetrics() {
        return RuntimeMetrics.unknown();
    }

    default String getPlaybackTraceId() {
        return PlaybackTrace.NONE;
    }

    default boolean supportsSubtitleStyle() {
        return false;
    }

    default String getAudioSpdifCodecs() {
        return "";
    }

    default void setSubtitleStyle(float textSize, float position) {
    }

    default boolean supportsSecondarySubtitle() {
        return false;
    }

    default boolean isSecondarySubtitleSelected(Format format) {
        return false;
    }

    default void setSecondarySubtitleTrack(Track track) {
    }

    default boolean haveTitle() {
        return false;
    }

    default boolean isRepeatOne() {
        return false;
    }

    default void setRepeatOne(boolean repeat) {
    }

    default List<MediaEdition> getCurrentMediaEditions() {
        return Collections.emptyList();
    }

    default PlaybackRoute.Resolution getEffectivePlaybackRoute() {
        return null;
    }

    /**
     * Returns the most recent resource classification observed by this engine.
     * Implementations must return an immutable snapshot and may return null
     * when the engine has not observed a stronger fact than the request itself.
     */
    default PlaybackResourceClassifier.Classification getResourceClassification() {
        return null;
    }

    default boolean selectEdition(MediaEdition edition) {
        return false;
    }

    String getErrorMessage(PlaybackException e);

    ErrorAction handleError(PlaybackException e);

    enum ErrorAction {
        RECOVERED,
        RELOAD,
        DECODE,
        FATAL
    }

    enum DecoderKind {
        HARDWARE,
        SOFTWARE,
        UNKNOWN
    }

    record PlaybackFactsSnapshot(
            Format selectedVideoFormat,
            Format selectedAudioFormat,
            Format videoDecoderFormat,
            Format audioDecoderFormat,
            String videoDecoderName,
            String audioDecoderName,
            DecoderKind videoDecoderKind,
            Boolean secureVideoDecoder,
            String hwdecCurrent,
            String currentVideoOutput,
            Boolean tunneling) {

        public PlaybackFactsSnapshot {
            videoDecoderName = videoDecoderName == null ? "" : videoDecoderName;
            audioDecoderName = audioDecoderName == null ? "" : audioDecoderName;
            videoDecoderKind = videoDecoderKind == null ? DecoderKind.UNKNOWN : videoDecoderKind;
            hwdecCurrent = hwdecCurrent == null ? "" : hwdecCurrent;
            currentVideoOutput = currentVideoOutput == null ? "" : currentVideoOutput;
        }

        public static PlaybackFactsSnapshot empty() {
            return new PlaybackFactsSnapshot(null, null, null, null, "", "",
                    DecoderKind.UNKNOWN, null, "", "", null);
        }
    }

    record VideoPlaybackDetails(
            String sourceCodecs,
            int dolbyVisionProfile,
            int dolbyVisionLevel,
            String decodedCodec,
            String decoderName,
            String hwdecCurrent,
            ColorInfo outputColorInfo,
            boolean dolbyVisionHdr10Fallback,
            boolean dolbyVisionP81Conversion) {

        public VideoPlaybackDetails(
                String sourceCodecs,
                int dolbyVisionProfile,
                int dolbyVisionLevel,
                String decodedCodec,
                String decoderName,
                String hwdecCurrent,
                ColorInfo outputColorInfo,
                boolean dolbyVisionHdr10Fallback) {
            this(sourceCodecs, dolbyVisionProfile, dolbyVisionLevel,
                    decodedCodec, decoderName, hwdecCurrent, outputColorInfo,
                    dolbyVisionHdr10Fallback, false);
        }

        public VideoPlaybackDetails {
            sourceCodecs = sourceCodecs == null ? "" : sourceCodecs;
            decodedCodec = decodedCodec == null ? "" : decodedCodec;
            decoderName = decoderName == null ? "" : decoderName;
            hwdecCurrent = hwdecCurrent == null ? "" : hwdecCurrent;
        }

        public boolean hasDolbyVisionSource() {
            return dolbyVisionProfile > 0;
        }

        public boolean hasEvidence() {
            return hasDolbyVisionSource() || !sourceCodecs.isEmpty()
                    || !decodedCodec.isEmpty() || !decoderName.isEmpty()
                    || !hwdecCurrent.isEmpty() || outputColorInfo != null
                    || dolbyVisionHdr10Fallback || dolbyVisionP81Conversion;
        }

        public static VideoPlaybackDetails empty() {
            return new VideoPlaybackDetails("", C.INDEX_UNSET, C.INDEX_UNSET,
                    "", "", "", null, false);
        }
    }

    record RuntimeMetrics(
            Long bandwidthBitsPerSecond,
            Long mediaBitrateBitsPerSecond,
            Float renderedFrameRate,
            Long droppedFrames) {

        public RuntimeMetrics {
            bandwidthBitsPerSecond = nonNegative(bandwidthBitsPerSecond);
            mediaBitrateBitsPerSecond = nonNegative(mediaBitrateBitsPerSecond);
            renderedFrameRate = renderedFrameRate == null || !Float.isFinite(renderedFrameRate) || renderedFrameRate <= 0
                    ? null : renderedFrameRate;
            droppedFrames = nonNegative(droppedFrames);
        }

        public static RuntimeMetrics unknown() {
            return new RuntimeMetrics(null, null, null, null);
        }

        private static Long nonNegative(Long value) {
            return value == null || value < 0 ? null : value;
        }
    }
}
