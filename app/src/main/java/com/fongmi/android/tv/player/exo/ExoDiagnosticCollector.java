package com.fongmi.android.tv.player.exo;

import android.os.Handler;
import android.os.SystemClock;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;

import com.fongmi.android.tv.player.PlaybackDiagnosticCollector;
import com.fongmi.android.tv.player.PlaybackDiagnosticCollector.Context;
import com.fongmi.android.tv.player.SystemAudioDiagnosticCollector;
import com.github.catvod.crawler.diagnostics.DiagnosticEvent;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import static com.github.catvod.crawler.diagnostics.DiagnosticEvent.Status.*;

/** Public Media3 hooks, separate from PlaybackAnalyticsListener's compatibility/UI state. */
public final class ExoDiagnosticCollector implements AnalyticsListener {
    private static final Map<ExoPlayer, WeakReference<ExoDiagnosticCollector>> PLAYERS = new WeakHashMap<>();
    private static final Map<Context, WeakReference<PlaybackDiagnosticCollector>> MEDIA = new WeakHashMap<>();
    final PlaybackDiagnosticCollector log = new PlaybackDiagnosticCollector("exo", "1.11.0-alpha01-fongmi");
    final ExoDiagnosticCodecSnapshotCache codecSnapshots = new ExoDiagnosticCodecSnapshotCache();
    private WeakReference<ExoPlayer> player = new WeakReference<>(null);
    private Handler handler;
    private final Runnable tick = this::sample;
    private DecoderCounters videoCounters, audioCounters;
    private Context videoOwner, audioOwner;
    private long seekEpoch;
    private final com.fongmi.android.tv.player.OutputProgressDiagnostic videoProgress = new com.fongmi.android.tv.player.OutputProgressDiagnostic(true);

    void attach(ExoPlayer value) {
        player = new WeakReference<>(value);
        synchronized (PLAYERS) { PLAYERS.put(value, new WeakReference<>(this)); }
        Media3DiagnosticBridge.bind(value, log, null, log.instanceId());
        value.addAnalyticsListener(this);
        handler = new Handler(value.getApplicationLooper());
        handler.postDelayed(tick, 5000);
    }

    public static void effects(ExoPlayer player, List<androidx.media3.common.Effect> effects) {
        if (!PlaybackDiagnosticCollector.enabled()) return;
        ExoDiagnosticCollector collector;
        synchronized (PLAYERS) {
            WeakReference<ExoDiagnosticCollector> reference = PLAYERS.get(player);
            collector = reference == null ? null : reference.get();
        }
        if (collector == null) return;
        collector.log.emit("video.effects", "ExoPlayer.setVideoEffects", e -> e.observed("count", effects.size()).observed("phase", "requested"));
        for (int i = 0; i < effects.size(); i++) {
            int index = i;
            collector.log.emit("video.effects", "ExoPlayer.setVideoEffects", e -> e.observed("candidateIndex", index)
                    .observed("value", effects.get(index).getClass().getSimpleName()).observed("phase", "requested"));
        }
    }

    public static MediaItem prepare(ExoPlayer player, MediaItem item, String trace) {
        ExoDiagnosticCollector collector;
        synchronized (PLAYERS) {
            WeakReference<ExoDiagnosticCollector> reference = PLAYERS.get(player);
            collector = reference == null ? null : reference.get();
        }
        if (collector == null) return item;
        Context owner = collector.log.begin(trace, "foreground");
        collector.codecSnapshots.clear();
        synchronized (MEDIA) { MEDIA.put(owner, new WeakReference<>(collector.log)); }
        collector.log.protectedMedia(item.localConfiguration != null && item.localConfiguration.drmConfiguration != null);
        collector.seekEpoch = 0;
        // The private tag follows EventTime's media item through replace/seek/reprepare.
        // Keep an existing application tag intact; its events then explicitly lack association.
        if (item.localConfiguration == null || item.localConfiguration.tag != null) return item;
        return item.buildUpon().setTag(owner).build();
    }

    static PlaybackDiagnosticCollector mediaLog(Context owner) {
        synchronized (MEDIA) {
            WeakReference<PlaybackDiagnosticCollector> value = MEDIA.get(owner);
            return value == null ? null : value.get();
        }
    }

    private static Context owner(Timeline timeline, int windowIndex) {
        if (timeline.isEmpty() || windowIndex < 0 || windowIndex >= timeline.getWindowCount()) return null;
        MediaItem item = timeline.getWindow(windowIndex, new Timeline.Window()).mediaItem;
        return item.localConfiguration != null && item.localConfiguration.tag instanceof Context context ? context : null;
    }

    private Context owner(EventTime time) { return owner(time.timeline, time.windowIndex); }

    private void event(EventTime time, String name, Consumer<DiagnosticEvent> facts) {
        if (!PlaybackDiagnosticCollector.enabled()) return;
        Context owner = owner(time);
        log.emit(owner, name, "media3-analytics", owner == null ? "unresolved" : "event-time-media", e -> {
            e.observed("positionMs", time.eventPlaybackPositionMs).observed("bufferedMs", time.totalBufferedDurationMs)
                    .observed("ageMs", Math.max(0, SystemClock.elapsedRealtime() - time.realtimeMs));
            facts.accept(e);
        });
    }

    static void format(DiagnosticEvent e, Format f) {
        if (f == null) { e.unknown("format", UNAVAILABLE); return; }
        e.observed("mime", f.sampleMimeType).observed("codecs", f.codecs)
                .observed("width", f.width > 0 ? f.width : null).observed("height", f.height > 0 ? f.height : null)
                .observed("frameRate", f.frameRate > 0 ? f.frameRate : null)
                .observed("sampleRate", f.sampleRate > 0 ? f.sampleRate : null)
                .observed("channels", f.channelCount > 0 ? f.channelCount : null)
                .observed("encoding", f.pcmEncoding != Format.NO_VALUE ? f.pcmEncoding : null)
                .observed("bitrate", f.bitrate > 0 ? f.bitrate : null)
                .observed("language", f.language).observed("flags", f.selectionFlags)
                .observed("secure", f.cryptoType != C.CRYPTO_TYPE_NONE);
    }

    private void sample() {
        ExoPlayer current = player.get();
        if (current == null) return;
        try {
            if (PlaybackDiagnosticCollector.enabled()) {
                log.baseline();
                Context owner = owner(current.getCurrentTimeline(), current.getCurrentMediaItemIndex());
                log.emit(owner, "play.clock", "media3-player", "current-timeline", e -> e
                        .observed("positionMs", current.getCurrentPosition()).observed("bufferedMs", current.getTotalBufferedDuration())
                        .observed("state", current.getPlaybackState()).observed("playWhenReady", current.getPlayWhenReady())
                        .observed("isPlaying", current.isPlaying()).observed("suppressionReason", current.getPlaybackSuppressionReason())
                        .observed("speed", current.getPlaybackParameters().speed).observed("seekEpoch", seekEpoch)
                        .unknown("physicalDisplay", NOT_OBSERVABLE));
                counters(videoOwner, videoCounters, true);
                counters(audioOwner, audioCounters, false);
                log.emit(owner, "audio.sync", "media3-player-clock", "current-timeline", e -> e
                        .observed("positionMs", current.getCurrentPosition()).observed("speed", current.getPlaybackParameters().speed)
                        .observed("seekEpoch", seekEpoch).observed("property", "avsync").unknown("value", NOT_OBSERVABLE)
                        .observed("metricScope", "player media clock; decoder PTS recorded separately, not a physical A/V sync measurement"));
                Format selected = current.getVideoFormat();
                String gate = !current.isPlaying() ? "paused-buffering-or-suppressed"
                        : owner != videoOwner || videoCounters == null ? "video-owner-or-counter-unavailable"
                        : selected == null || selected.frameRate < 1 ? "no-motion-video-or-unknown-cadence"
                        : videoCounters.queuedInputBufferCount == 0 ? "no-video-input-observed" : null;
                videoProgress.sample(log, owner, seekEpoch, SystemClock.elapsedRealtime(),
                        videoCounters == null ? Double.NaN : videoCounters.renderedOutputBufferCount, gate,
                        "renderer submitted buffers; known cadence >= 1 fps; three 5-second samples");
                SystemAudioDiagnosticCollector.snapshot(log, owner);
            }
        } catch (RuntimeException ignored) { com.github.catvod.crawler.DebugLogStore.collectorFailure(); }
        handler.postDelayed(tick, 5000);
    }

    private void counters(Context owner, DecoderCounters c, boolean video) {
        if (c == null || !PlaybackDiagnosticCollector.enabled()) return;
        c.ensureUpdated();
        log.emit(owner, video ? "video.output.summary" : "audio.decoder.output", "media3-decoder-counters", "renderer-enable-lifetime", e -> e
                .observed("inputBuffers", c.queuedInputBufferCount).observed("submittedBuffers", c.renderedOutputBufferCount)
                .observed("skippedBuffers", c.skippedOutputBufferCount).observed("droppedBuffers", c.droppedBufferCount)
                .observed("metricScope", "renderer counters since enable; submitted does not mean physical presentation")
                .unknown("physicalDisplay", NOT_OBSERVABLE));
        if (c.queuedInputBufferCount > 0) log.evidence(owner, video, 1);
        if (c.renderedOutputBufferCount > 0) log.evidence(owner, video, video ? 3 : 2);
    }

    @Override public void onVideoEnabled(EventTime t, DecoderCounters c) { videoCounters = c; videoOwner = owner(t); }
    @Override public void onAudioEnabled(EventTime t, DecoderCounters c) { audioCounters = c; audioOwner = owner(t); }
    @Override public void onVideoDisabled(EventTime t, DecoderCounters c) { counters(owner(t), c, true); videoCounters = null; }
    @Override public void onAudioDisabled(EventTime t, DecoderCounters c) { counters(owner(t), c, false); audioCounters = null; }

    @Override public void onVideoDecoderInitialized(EventTime t, String name, long time, long duration) {
        event(t, "video.decoder.attempt", e -> e.observed("decoderName", name).observed("durationMs", duration)
                .observed("phase", "initialized").observed("stage", "create-configure-start aggregate"));
    }
    @Override public void onAudioDecoderInitialized(EventTime t, String name, long time, long duration) {
        event(t, "audio.decoder.attempt", e -> e.observed("decoderName", name).observed("durationMs", duration).observed("phase", "initialized"));
    }
    @Override public void onVideoInputFormatChanged(EventTime t, Format f, DecoderReuseEvaluation reuse) { inputFormat(t, f, reuse, true); }
    @Override public void onAudioInputFormatChanged(EventTime t, Format f, DecoderReuseEvaluation reuse) { inputFormat(t, f, reuse, false); }
    private void inputFormat(EventTime t, Format f, DecoderReuseEvaluation reuse, boolean video) {
        event(t, video ? "video.configure" : "audio.track", e -> {
            format(e, f);
            e.observed("stage", "renderer-input");
            if (reuse != null) e.observed("reuseResult", reuse.result).observed("discardReasons", reuse.discardReasons)
                    .observed("decoderName", reuse.decoderName);
        });
    }

    @Override public void onTracksChanged(EventTime t, Tracks tracks) {
        if (!PlaybackDiagnosticCollector.enabled()) return;
        int groupId = 0;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() == C.TRACK_TYPE_VIDEO) log.evidence(owner(t), true, 0);
            if (group.getType() == C.TRACK_TYPE_AUDIO) log.evidence(owner(t), false, 0);
            int id = groupId++;
            for (int i = 0; i < group.length; i++) {
                int index = i;
                event(t, "media.tracks", e -> {
                    format(e, group.getTrackFormat(index));
                    e.observed("trackId", id + ":" + index).observed("trackType", group.getType())
                            .observed("selected", group.isTrackSelected(index)).observed("support", group.getTrackSupport(index));
                });
            }
        }
    }

    @Override public void onTimelineChanged(EventTime time, int reason) {
        if (time.timeline.isEmpty()) return;
        Timeline.Window window = time.timeline.getWindow(time.windowIndex, new Timeline.Window());
        event(time, "media.container", e -> e.observed("durationMs", window.getDurationMs() == C.TIME_UNSET ? null : window.getDurationMs())
                .observed("seekable", window.isSeekable).observed("live", window.isLive()).observed("reason", reason)
                .observed("source", "Media3 timeline").unknown("backend", NOT_COLLECTED));
    }

    @Override public void onRenderedFirstFrame(EventTime t, Object output, long time) {
        event(t, "video.first-output", e -> e.observed("stage", "media3-first-frame-callback")
                .observed("surfaceId", PlaybackDiagnosticCollector.objectId(output, "surface"))
                .unknown("physicalDisplay", NOT_OBSERVABLE));
        log.evidence(owner(t), true, 3);
    }
    @Override public void onAudioPositionAdvancing(EventTime t, long time) {
        event(t, "audio.output.playhead", e -> e.observed("stage", "media3-position-advancing").unknown("audibility", NOT_OBSERVABLE));
        log.evidence(owner(t), false, 4);
    }
    @Override public void onVideoCodecError(EventTime t, Exception error) { log.error(owner(t), "video-codec", "runtime", error); }
    @Override public void onAudioCodecError(EventTime t, Exception error) { log.error(owner(t), "audio-codec", "runtime", error); }
    @Override public void onAudioSinkError(EventTime t, Exception error) { log.error(owner(t), "audio-sink", "sink-callback", error); }
    @Override public void onPlayerError(EventTime t, PlaybackException error) { log.error(owner(t), "media3-player", "player-error", error); }
    @Override public void onDrmSessionManagerError(EventTime t, Exception error) { log.error(owner(t), "drm", "session", error); }
    @Override public void onDrmSessionAcquired(EventTime t, int state) { event(t, "drm.state", e -> e.observed("state", state).observed("phase", "acquired")); }
    @Override public void onDrmKeysLoaded(EventTime t) { event(t, "drm.state", e -> e.observed("phase", "keys-loaded")); }
    @Override public void onDrmSessionReleased(EventTime t) { event(t, "drm.state", e -> e.observed("phase", "released")); }
    @Override public void onPositionDiscontinuity(EventTime t, Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
        seekEpoch++;
        event(t, "play.clock", e -> e.observed("reason", reason).observed("seekEpoch", seekEpoch).observed("phase", "discontinuity"));
    }
    @Override public void onPlaybackStateChanged(EventTime t, int state) { event(t, "play.lifecycle", e -> e.observed("state", state)); }
    @Override public void onPlayWhenReadyChanged(EventTime t, boolean value, int reason) {
        event(t, "play.lifecycle", e -> e.observed("playWhenReady", value).observed("reason", reason));
    }
    @Override public void onPlaybackSuppressionReasonChanged(EventTime t, int reason) {
        event(t, "audio.focus", e -> e.observed("focusOwner", "Media3").observed("suppressionReason", reason)
                .unknown("focusRequest", NOT_COLLECTED).unknown("muted", NOT_OBSERVABLE));
    }
    @Override public void onVolumeChanged(EventTime t, float value) { event(t, "audio.volume", e -> e.observed("volume", value).observed("stage", "player-volume")); }
    @Override public void onAudioUnderrun(EventTime t, int bytes, long bufferMs, long elapsed) {
        event(t, "audio.error", e -> e.severity("warn").observed("stage", "underrun").observed("bufferSize", bytes)
                .observed("bufferUnit", "bytes").observed("durationMs", bufferMs).observed("lastAgeMs", elapsed));
    }
    @Override public void onLoadStarted(EventTime t, LoadEventInfo info, MediaLoadData data) { load(t, info, "input.open"); }
    @Override public void onLoadCompleted(EventTime t, LoadEventInfo info, MediaLoadData data) { load(t, info, "input.read.summary"); }
    @Override public void onLoadError(EventTime t, LoadEventInfo info, MediaLoadData data, IOException error, boolean canceled) {
        load(t, info, "input.read.summary"); log.error(owner(t), "input", canceled ? "canceled" : "load", error);
    }
    private void load(EventTime t, LoadEventInfo info, String name) {
        event(t, name, e -> {
            e.observed("loadTaskId", info.loadTaskId).observed("rangeStart", info.dataSpec.position)
                    .observed("rangeLength", info.dataSpec.length < 0 ? null : info.dataSpec.length)
                    .observed("bytes", info.bytesLoaded).observed("durationMs", info.loadDurationMs);
            for (Map.Entry<String, List<String>> header : info.responseHeaders.entrySet()) {
                if (header.getKey() == null || header.getValue().isEmpty()) continue;
                String field = switch (header.getKey().toLowerCase(java.util.Locale.ROOT)) {
                    case "content-type" -> "contentTypeHeader";
                    case "content-length" -> "contentLength";
                    case "content-range" -> "contentRange";
                    default -> null;
                };
                if (field != null) e.observed(field, header.getValue().get(0));
            }
            e.unknown("httpCode", NOT_COLLECTED);
        });
    }

    @Override public void onPlayerReleased(EventTime t) {
        if (handler != null) handler.removeCallbacks(tick);
        codecSnapshots.clear();
        counters(videoOwner, videoCounters, true); counters(audioOwner, audioCounters, false);
        log.end("player-released");
        synchronized (PLAYERS) { ExoPlayer p = player.get(); if (p != null) PLAYERS.remove(p); }
        player.clear();
    }
}
