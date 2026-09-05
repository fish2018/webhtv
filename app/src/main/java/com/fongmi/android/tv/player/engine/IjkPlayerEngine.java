package com.fongmi.android.tv.player.engine;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.PlaybackTrace;
import com.fongmi.android.tv.player.PlaybackResourceClassifier;
import com.fongmi.android.tv.player.PlaybackRoute;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.exo.TrackUtil;
import com.fongmi.android.tv.player.ijk.IjkBufferPolicy;
import com.fongmi.android.tv.player.ijk.IjkDecodePressurePolicy;
import com.fongmi.android.tv.player.ijk.IjkRealtimeRecoveryPolicy;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.List;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

@UnstableApi
public class IjkPlayerEngine implements PlayerEngine {

    private IjkSimplePlayer player;
    private PlaySpec spec;
    private int decode;

    public IjkPlayerEngine(int decode, Player.Listener listener) {
        this.player = buildPlayer(decode, listener);
        this.decode = decode;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void release() {
        player.release();
    }

    @Override
    public Player rebuild(Player.Listener listener) {
        player.release();
        PlaybackTrace.log("player-engine", getPlaybackTraceId(), "rebuild ijk decode=%d", decode);
        return player = buildPlayer(decode, listener);
    }

    @Override
    public int getDecode() {
        return decode;
    }

    @Override
    public void setDecode(int decode) {
        this.decode = decode;
        player.setDecode(decode);
    }

    @Override
    public boolean isHard() {
        return decode == HARD;
    }

    @Override
    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    @Override
    public void start(PlaySpec spec) {
        start(spec, C.TIME_UNSET, true);
    }

    @Override
    public void start(PlaySpec spec, boolean playWhenReady) {
        start(spec, C.TIME_UNSET, playWhenReady);
    }

    @Override
    public void start(PlaySpec spec, long position, boolean playWhenReady) {
        this.spec = spec;
        PlaybackTrace.log("player-engine", getPlaybackTraceId(), "start ijk decode=%d position=%d play=%s urlLen=%d headers=%d", decode, position, playWhenReady, spec.getUrl() == null ? 0 : spec.getUrl().length(), spec.getHeaders() == null ? 0 : spec.getHeaders().size());
        MediaItem item = ExoUtil.getMediaItem(spec, decode);
        if (position > 0) player.setMediaItem(item, position);
        else player.setMediaItem(item);
        player.prepare();
        if (playWhenReady) player.play();
        else player.pause();
    }

    @Override
    public void restart(PlaySpec spec, long position, boolean playWhenReady) {
        player.stop();
        start(spec, position, playWhenReady);
    }

    public void stageAutomaticInputBufferConfig(IjkBufferPolicy.Config config) {
        player.stageAutomaticInputBufferConfig(config);
    }

    public IjkBufferPolicy.Config getAppliedInputBufferConfig() {
        return player.getAppliedInputBufferConfig();
    }

    public Long getLiveLagLowerBoundMs() {
        return player.getLiveLagLowerBoundSnapshot();
    }

    public IjkRealtimeRecoveryPolicy.QueueSnapshot getRealtimeQueueSnapshot() {
        return player.getRealtimeQueueSnapshot();
    }

    public void stageAutomaticDecodeControlConfig(
            IjkDecodePressurePolicy.Config config) {
        player.stageAutomaticDecodeControlConfig(config);
    }

    public IjkDecodePressurePolicy.Config getAppliedDecodeControlConfig() {
        return player.getAppliedDecodeControlConfig();
    }

    public IjkDecodePressurePolicy.DecodeSnapshot getDecodePressureSnapshot() {
        return player.getDecodePressureSnapshot();
    }

    public ErrorSnapshot getLastErrorSnapshot() {
        return player.getLastErrorSnapshot();
    }

    public DropRateSnapshot getDropRateSnapshot() {
        return player.getDropRateSnapshot();
    }

    @Override
    public void stop() {
        player.stop();
    }

    @Override
    public void setMetadata(MediaMetadata data) {
        MediaItem current = player.getCurrentMediaItem();
        if (current != null) player.replaceMediaItem(player.getCurrentMediaItemIndex(), current.buildUpon().setMediaMetadata(data).build());
    }

    @Override
    public boolean isLive() {
        return player.getStreamSceneDecision().live();
    }

    @Override
    public boolean isVod() {
        return player.getStreamSceneDecision().vod();
    }

    @Override
    public PlaybackResourceClassifier.Classification getResourceClassification() {
        return player.getResourceClassification();
    }

    @Override
    public PlaybackRoute.Resolution getEffectivePlaybackRoute() {
        PlaybackRoute.Resolution current = player.getPlaybackRouteResolution();
        if (current.route() != PlaybackRoute.OTHER) return current;
        return spec == null ? current : spec.getPlaybackRoute();
    }

    @Override
    public void setTrack(List<Track> tracks) {
    }

    @Override
    public void resetTrack() {
    }

    @Override
    public boolean haveTrack(int type) {
        return TrackUtil.count(getCurrentTracks(), type) > 0;
    }

    @Override
    public Tracks getCurrentTracks() {
        return player.getCurrentTracksSnapshot();
    }

    @Override
    public Format getVideoFormat() {
        return TrackUtil.selectedFormat(getCurrentTracks(), C.TRACK_TYPE_VIDEO);
    }

    @Override
    public PlaybackFactsSnapshot getPlaybackFactsSnapshot() {
        Format video = player.getSelectedVideoFormatSnapshot();
        Format audio = player.getSelectedAudioFormatSnapshot();
        return new PlaybackFactsSnapshot(
                video,
                audio,
                video,
                audio,
                player.getVideoCodecInfoSnapshot(),
                player.getAudioCodecInfoSnapshot(),
                decoderKind(player.getVideoDecoderSnapshot()),
                null,
                "",
                "",
                null);
    }

    @Override
    public RuntimeMetrics getRuntimeMetrics() {
        long tcpBytesPerSecond = player.getTcpSpeedSnapshot();
        long bandwidth = tcpBytesPerSecond > Long.MAX_VALUE / 8L
                ? Long.MAX_VALUE : tcpBytesPerSecond * 8L;
        long bitrate = player.getBitrateSnapshot();
        IjkDecodePressurePolicy.DecodeSnapshot decode =
                player.getDecodePressureSnapshot();
        return new RuntimeMetrics(
                bandwidth > 0 ? bandwidth : null,
                bitrate > 0 ? bitrate : null,
                decode.available() ? decode.outputFps() : null,
                null);
    }

    @Override
    public String getPlaybackTraceId() {
        return spec == null ? PlaybackTrace.NONE : spec.getPlaybackTraceId();
    }

    @Override
    public String getErrorMessage(PlaybackException e) {
        return e == null
                ? "IJK playback failed"
                : PlaybackException.getErrorCodeName(e.errorCode);
    }

    @Override
    public ErrorAction handleError(PlaybackException e) {
        PlaybackTrace.log(
                "player-engine",
                getPlaybackTraceId(),
                "handleError ijk code=%d errorType=%s action=fatal",
                e == null ? PlaybackException.ERROR_CODE_UNSPECIFIED
                        : e.errorCode,
                e == null ? "none" : e.getClass().getSimpleName());
        return ErrorAction.FATAL;
    }

    private IjkSimplePlayer buildPlayer(int decode, Player.Listener listener) {
        IjkSimplePlayer player = new IjkSimplePlayer(decode);
        player.addListener(listener);
        return player;
    }

    private DecoderKind decoderKind(int decoder) {
        return switch (decoder) {
            case IjkMediaPlayer.FFP_PROPV_DECODER_MEDIACODEC -> DecoderKind.HARDWARE;
            case IjkMediaPlayer.FFP_PROPV_DECODER_AVCODEC -> DecoderKind.SOFTWARE;
            default -> DecoderKind.UNKNOWN;
        };
    }

    public enum OpenStage {
        NONE("none"),
        SOURCE_SET("source-set"),
        HTTP_OPENING("http-opening"),
        HTTP_OPENED("http-opened"),
        INPUT_OPENED("input-opened"),
        STREAM_INFO("stream-info"),
        COMPONENT_OPENED("component-opened"),
        PREPARED("prepared"),
        FIRST_FRAME("first-frame");

        private final String label;

        OpenStage(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record ErrorSnapshot(
            int what,
            int extra,
            boolean prepared,
            OpenStage stage,
            int httpStatus,
            long nativeOffset,
            boolean longUrlProxied) {

        public static ErrorSnapshot none() {
            return new ErrorSnapshot(
                    0, 0, false, OpenStage.NONE, 0, -1, false);
        }
    }

    public record DropRateSnapshot(boolean available, int permille) {

        public DropRateSnapshot {
            permille = Math.max(0, permille);
        }

        public static DropRateSnapshot unknown() {
            return new DropRateSnapshot(false, 0);
        }
    }
}
