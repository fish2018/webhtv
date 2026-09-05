package com.fongmi.android.tv.player.engine;

import androidx.media3.common.MimeTypes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaEdition;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.mpvplayer.MpvPlayer;
import androidx.media3.mpvplayer.MpvPlayerConfig;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.PlaybackRoute;
import com.fongmi.android.tv.player.PlaybackResourceClassifier;
import com.fongmi.android.tv.player.PlaybackTrace;
import com.fongmi.android.tv.player.codec.CodecCapabilityInspector;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.exo.TrackUtil;
import com.fongmi.android.tv.player.lut.MpvLutShader;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.player.mpv.MpvConfigStore;
import com.fongmi.android.tv.player.mpv.MpvAutoControlPolicy;
import com.fongmi.android.tv.player.mpv.MpvAutoOutputPolicy;
import com.fongmi.android.tv.player.mpv.MpvVulkanBackendPolicy;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.MpvPerformanceSetting;
import com.fongmi.android.tv.setting.PlaybackPerformanceCatalog;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import is.xyz.mpv.MPVLib;

@UnstableApi
public class MpvPlayerEngine implements PlayerEngine {

    static final String DV7_PRESERVE = "preserve";
    static final String DV7_P81 = "p81";
    static final String DV7_HDR10 = "hdr10";

    private MpvPlayer player;
    private PlaySpec spec;
    private boolean playWhenReady;
    private boolean retriedFormat;
    private boolean surfaceDirect;
    private Boolean surfaceDirectOverride;
    private boolean lutAllowed = true;
    private Boolean vulkanRenderOverride;
    private String vulkanBackendOverride;
    private String vulkanBackend = MpvVulkanBackendPolicy.AUTO;
    private boolean vulkanRenderer;
    private String hwdecOverride;
    private String configuredHwdec = "no";
    private String dv7HandlingOption;
    private boolean dv7P81FallbackTried;
    private boolean initialSubtitleSurfaceRequested;
    private String initialSubtitleTrackId;
    private final BiConsumer<Integer, Integer> videoSizeProbeListener;
    private int decode;

    public MpvPlayerEngine(int decode, boolean lutAllowed, Player.Listener listener, BiConsumer<Integer, Integer> videoSizeProbeListener) {
        this.decode = decode;
        this.lutAllowed = lutAllowed;
        this.videoSizeProbeListener = videoSizeProbeListener;
        resetDv7HandlingForNewItem();
        this.player = buildPlayer(listener);
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void release() {
        player.release();
    }

    public void prepareTerminalRelease() {
        player.prepareTerminalRelease();
    }

    @Override
    public Player rebuild(Player.Listener listener) {
        player.release();
        PlaybackTrace.log("player-engine", getPlaybackTraceId(), "rebuild mpv decode=%d", decode);
        return player = buildPlayer(listener);
    }

    public void prepareSubtitleForNewItem(@Nullable Track track) {
        boolean requested = track != null;
        initialSubtitleSurfaceRequested = requested;
        initialSubtitleTrackId = persistedSubtitleTrackId(track);
        player.setInitialOsdSurfaceRequested(requested);
        player.setInitialTrackSelectionGateRequested(requested);
        player.setInitialSubtitleTrackId(initialSubtitleTrackId);
    }

    public void retainSubtitleSurfaceForCurrentItem() {
        if (initialSubtitleSurfaceRequested) return;
        initialSubtitleSurfaceRequested = true;
        player.setInitialOsdSurfaceRequested(true);
    }

    public void completeInitialSubtitleTrackRestore() {
        player.releaseInitialTrackSelectionGate();
    }

    @Override
    public boolean isRepeatOne() {
        return player.getRepeatMode() == Player.REPEAT_MODE_ONE;
    }

    @Override
    public void setRepeatOne(boolean repeat) {
        player.setRepeatMode(repeat ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    @Override
    public int getDecode() {
        return decode;
    }

    @Override
    public void setDecode(int decode) {
        this.decode = decode;
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
        start(spec, true);
    }

    @Override
    public void start(PlaySpec spec, boolean playWhenReady) {
        start(spec, 0, playWhenReady);
    }

    @Override
    public void start(PlaySpec spec, long position, boolean playWhenReady) {
        this.spec = spec;
        this.playWhenReady = playWhenReady;
        this.retriedFormat = false;
        player.setPlaybackTraceId(spec.getPlaybackTraceId());
        PlaybackTrace.log("player-engine", getPlaybackTraceId(), "start mpv decode=%d position=%d play=%s urlLen=%d headers=%d", decode, position, playWhenReady, spec.getUrl() == null ? 0 : spec.getUrl().length(), spec.getHeaders() == null ? 0 : spec.getHeaders().size());
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
        return player.getDuration() < TimeUnit.MINUTES.toMillis(1) || player.isCurrentMediaItemLive();
    }

    @Override
    public boolean isVod() {
        return player.getDuration() > TimeUnit.MINUTES.toMillis(1) && !player.isCurrentMediaItemLive();
    }

    @Override
    public void setTrack(List<Track> tracks) {
        for (Track track : tracks) {
            if (track.isDisabled() && track.getType() == C.TRACK_TYPE_TEXT) {
                initialSubtitleTrackId = null;
                player.setInitialSubtitleTrackId(null);
                player.setTrackSelection(C.TRACK_TYPE_TEXT, "no");
                continue;
            }
            String id = resolveMpvTrackId(track);
            if (id != null) {
                if (track.getType() == C.TRACK_TYPE_TEXT) {
                    initialSubtitleTrackId = id;
                    player.setInitialSubtitleTrackId(id);
                }
                player.setTrackSelection(track.getType(), id);
            } else {
                SpiderDebug.log("mpv", "select track failed: no mpv id type=%d name=%s format=%s", track.getType(), track.getName(), track.getFormat());
            }
        }
    }

    @Override
    public void resetTrack() {
        player.resetTrackSelection();
    }

    @Override
    public void restoreVideoTrack() {
        player.restoreVideoTrackSelection();
    }

    @Override
    public boolean haveTrack(int type) {
        return TrackUtil.count(getCurrentTracks(), type) > 0;
    }

    @Override
    public Tracks getCurrentTracks() {
        return player.getCurrentTracksSnapshot();
    }

    public VideoSize getVideoSizeSnapshot() {
        return player.getVideoSizeSnapshot();
    }

    @Override
    public Format getVideoFormat() {
        return TrackUtil.selectedFormat(getCurrentTracks(), C.TRACK_TYPE_VIDEO);
    }

    @Override
    public void setVideoAspect(float aspectRatio, boolean stretch) {
        player.setVideoAspect(aspectRatio, stretch);
    }

    @Override
    public PlaybackFactsSnapshot getPlaybackFactsSnapshot() {
        Format video = TrackUtil.explicitlySelectedFormat(getCurrentTracks(), C.TRACK_TYPE_VIDEO);
        Format audio = TrackUtil.explicitlySelectedFormat(getCurrentTracks(), C.TRACK_TYPE_AUDIO);
        String hwdec = player.getObservedHwdecCurrent();
        String currentVo = player.getObservedCurrentVideoOutput();
        return new PlaybackFactsSnapshot(
                video,
                audio,
                video,
                audio,
                "",
                "",
                decoderKind(hwdec, player.hasObservedHwdecCurrent()),
                null,
                hwdec,
                currentVo,
                null);
    }

    @Override
    public RuntimeMetrics getRuntimeMetrics() {
        PlayerCacheState cache = player.getCachedCacheState();
        long inputBytesPerSecond = cache.rawInputBytesPerSecond();
        long bandwidth = inputBytesPerSecond > Long.MAX_VALUE / 8L
                ? Long.MAX_VALUE : inputBytesPerSecond * 8L;
        Format video = TrackUtil.explicitlySelectedFormat(getCurrentTracks(), C.TRACK_TYPE_VIDEO);
        Format audio = TrackUtil.explicitlySelectedFormat(getCurrentTracks(), C.TRACK_TYPE_AUDIO);
        long mediaBitrate = safeAdd(formatBitrate(video), formatBitrate(audio));
        float frameRate = player.getObservedDisplayFrameRate();
        return new RuntimeMetrics(
                bandwidth > 0 ? bandwidth : null,
                mediaBitrate > 0 ? mediaBitrate : null,
                frameRate > 0 ? frameRate : null,
                player.hasObservedDroppedFrames() ? player.getObservedDroppedFrames() : null);
    }

    public MpvPlayer.FrameTimingSnapshot getFrameTimingSnapshot() {
        return player.getFrameTimingSnapshot();
    }

    /** True while the current BUFFERING window was opened by a seek rather than a stall. */
    public boolean isSeekBuffering() {
        return player.isSeekBuffering();
    }

    @Override
    public boolean supportsNativeLut() {
        return !surfaceDirect;
    }

    public boolean isSurfaceDirect() {
        return surfaceDirect;
    }

    public void setSurfaceDirectOverride(@Nullable Boolean value) {
        surfaceDirectOverride = value;
    }

    // 直播等场景会禁用 LUT，此时不能因为全局 LUT 开关而放弃电视直出，
    // 否则与 PlayerManager 的 lutAllowed && LutSetting.isEnabled() 判断相反，
    // 会在启播后多触发一次播放器重建。仅影响下一次 buildConfig()。
    public void setLutAllowed(boolean allowed) {
        lutAllowed = allowed;
    }

    public void setVulkanBackendOverride(@Nullable String value) {
        vulkanBackendOverride = value;
    }

    public void setVulkanRenderOverride(@Nullable Boolean value) {
        vulkanRenderOverride = value;
    }

    public boolean isVulkanRenderer() {
        return vulkanRenderer;
    }

    public boolean shouldFallbackVulkanToStable() {
        return vulkanRenderer && MpvVulkanBackendPolicy.isAutomaticConfig()
                && !MpvVulkanBackendPolicy.STABLE.equals(vulkanBackend);
    }

    public void forceMediaCodecCopy() {
        hwdecOverride = "mediacodec-copy";
    }

    public boolean clearHwdecOverride() {
        boolean changed = hwdecOverride != null;
        hwdecOverride = null;
        return changed;
    }

    public boolean isMediaCodecCopyOnly() {
        return "mediacodec-copy".equals(configuredHwdec);
    }

    public MpvPlayer.AutoCacheBaselineResult applyAutoCacheBaseline(
            String traceId, long forwardBytes, long backBytes) {
        player.setPlaybackTraceId(traceId);
        return player.applyAutoCacheBaseline(forwardBytes, backBytes);
    }

    /** Observer-only native cache counters; this never performs a synchronous property query. */
    public PlayerCacheState getAutoCacheSnapshot() {
        return player.getCachedCacheState();
    }

    public void clearAutoCacheBaseline() {
        player.clearAutoCacheBaseline();
    }

    public boolean updateAutomaticPreloadControl(
            boolean automatic,
            boolean resourceAllowed,
            boolean trafficAllowed) {
        return player.updateAutomaticPreloadControl(
                automatic, resourceAllowed, trafficAllowed);
    }

    public void requestAutomaticHlsPreload(long positionMs) {
        player.requestAutomaticHlsPreload(positionMs);
    }

    public void stopAutomaticHlsPreload() {
        player.updateAutomaticPreloadControl(true, false, false);
    }

    public MpvPlayer.AutoHlsBitrateResult applyAutoHlsBitrate(
            String traceId, String option) {
        player.setPlaybackTraceId(traceId);
        return player.applyAutoHlsBitrate(option);
    }

    public void clearAutoHlsBitrate() {
        player.clearAutoHlsBitrate();
    }

    /** Cached track/proxy HLS state; this method never performs a native query. */
    public MpvPlayer.AutoHlsRuntimeSnapshot getAutoHlsRuntimeSnapshot() {
        return player.getAutoHlsRuntimeSnapshot();
    }

    /** Proxy-only upstream and disk facts; this never performs a native query. */
    public MpvPlayer.AutoHlsPreloadRuntimeSnapshot getAutoHlsPreloadRuntimeSnapshot() {
        return player.getAutoHlsPreloadRuntimeSnapshot();
    }

    @Override
    public void setNativeLutShader(MpvLutShader shader) {
        player.setLutShader(shader);
    }

    @Override
    public void setNativeLutPreviewProgress(float progress) {
        player.setLutPreviewProgress(progress);
    }

    @Override
    public PlayerCacheState getCacheState() {
        return player.getCacheState();
    }

    @Override
    public String getRenderDiagnostics() {
        return player.getRenderDiagnostics();
    }

    @Override
    public String getRuntimeDiagnostics() {
        return player.getRuntimeDiagnostics();
    }

    @Override
    public String getGpuLoadDiagnostics() {
        return player.getGpuLoadDiagnostics();
    }

    @Override
    public void setGpuLoadDiagnosticsEnabled(boolean enabled) {
        player.setGpuLoadDiagnosticsEnabled(enabled);
    }

    @Override
    public VideoPlaybackDetails getVideoPlaybackDetails() {
        MpvPlayer.VideoTrackDiagnostics details =
                player.getSelectedVideoTrackDiagnostics();
        // A failed direct MediaCodec attempt can make mpv report vid=no while
        // retaining the actual video track in track-list. Preserve that source
        // metadata so automatic output can move to GPU instead of treating a
        // failed Dolby Vision stream as ordinary video.
        if (details == null || (details.dolbyVisionProfile() <= 0
                && details.sourceCodecs().isEmpty())) {
            MpvPlayer.VideoTrackDiagnostics available =
                    player.getAvailableVideoTrackDiagnostics();
            if (available != null && (!available.sourceCodecs().isEmpty()
                    || available.dolbyVisionProfile() > 0)) {
                details = available;
            }
        }
        String currentVo = player.getObservedCurrentVideoOutput();
        boolean fallbackConfigured = isConfiguredDv7Hdr10Fallback(
                details, isHard(), isDv7Hdr10Active());
        return new VideoPlaybackDetails(
                details.sourceCodecs(),
                details.dolbyVisionProfile(),
                details.dolbyVisionLevel(),
                details.decodedCodec(),
                details.decoderName(),
                player.getObservedHwdecCurrent(),
                details.outputColorInfo(),
                isDolbyVisionHdr10Fallback(details, currentVo)
                        || fallbackConfigured,
                details.sourceDolbyVisionProfile() == 7 && isDv7P81Active());
    }

    static boolean isConfiguredDv7Hdr10Fallback(
            MpvPlayer.VideoTrackDiagnostics details,
            boolean hardDecode,
            boolean fallbackEnabled) {
        return details != null && details.dolbyVisionProfile() == 7
                && hardDecode && fallbackEnabled;
    }

    static boolean isDolbyVisionHdr10Fallback(
            MpvPlayer.VideoTrackDiagnostics details, String currentVo) {
        if (details == null || details.dolbyVisionProfile() != 7
                || currentVo == null) return false;
        String output = currentVo.trim().toLowerCase(java.util.Locale.US);
        return output.equals("gpu") || output.startsWith("gpu-next");
    }

    public boolean resetDv7HandlingForNewItem() {
        String previous = dv7HandlingOption;
        MpvAutoOutputPolicy.DolbyVisionSupport nativeDv7 =
                CodecCapabilityInspector.dolbyVisionProfileSupport(
                        App.get(), 7, 6, null, null, 0, 0);
        MpvAutoOutputPolicy.DolbyVisionSupport profile81 =
                PlaybackPerformanceSetting.getMpvDv7HandlingMode()
                        == PlaybackPerformanceSetting.DV7_HANDLING_P81
                        ? CodecCapabilityInspector.dolbyVisionProfileSupport(
                        App.get(), 8, 6, null, null, 0, 0)
                        : MpvAutoOutputPolicy.DolbyVisionSupport.UNKNOWN;
        dv7HandlingOption = selectDv7Handling(nativeDv7, profile81,
                PlaybackPerformanceSetting.getMpvDv7HandlingMode());
        dv7P81FallbackTried = false;
        return !dv7HandlingOption.equals(previous);
    }

    public boolean updateDv7Handling(
            MpvAutoOutputPolicy.DolbyVisionSupport nativeDv7,
            MpvAutoOutputPolicy.DolbyVisionSupport profile81) {
        String selected = selectDv7Handling(nativeDv7, profile81,
                PlaybackPerformanceSetting.getMpvDv7HandlingMode());
        if (selected.equals(dv7HandlingOption)) return false;
        dv7HandlingOption = selected;
        dv7P81FallbackTried = false;
        return true;
    }

    static String selectDv7Handling(
            MpvAutoOutputPolicy.DolbyVisionSupport nativeDv7,
            MpvAutoOutputPolicy.DolbyVisionSupport profile81,
            int preferredMode) {
        if (nativeDv7 == MpvAutoOutputPolicy.DolbyVisionSupport.SUPPORTED) {
            return DV7_PRESERVE;
        }
        if (preferredMode == PlaybackPerformanceSetting.DV7_HANDLING_P81
                && profile81 == MpvAutoOutputPolicy.DolbyVisionSupport.SUPPORTED) {
            return DV7_P81;
        }
        return DV7_HDR10;
    }

    public boolean prepareDv7P81Hdr10Fallback() {
        if (!isDv7P81Active() || dv7P81FallbackTried) return false;
        dv7P81FallbackTried = true;
        dv7HandlingOption = DV7_HDR10;
        PlaybackTrace.log("mpv-dv", getPlaybackTraceId(),
                "P8.1 failed; prepare one-shot HDR10 fallback");
        return true;
    }

    public boolean isDv7NativeActive() {
        return DV7_PRESERVE.equals(dv7HandlingOption);
    }

    public boolean isDv7P81Active() {
        return DV7_P81.equals(dv7HandlingOption);
    }

    public boolean isDv7Hdr10Active() {
        return DV7_HDR10.equals(dv7HandlingOption);
    }

    public String getDv7HandlingOption() {
        return dv7HandlingOption;
    }

    @Override
    public long getDroppedFrames() {
        return player.getDroppedFrames();
    }

    @Override
    public String getPlaybackTraceId() {
        return spec == null ? PlaybackTrace.NONE : spec.getPlaybackTraceId();
    }

    @Override
    public PlaybackRoute.Resolution getEffectivePlaybackRoute() {
        PlaybackRoute.Resolution current = player.getPlaybackRouteResolution();
        if (current.route() != PlaybackRoute.OTHER) return current;
        return spec == null ? current : spec.getPlaybackRoute();
    }

    @Override
    public PlaybackResourceClassifier.Classification getResourceClassification() {
        return player.getResourceClassification();
    }

    @Override
    public boolean supportsSubtitleStyle() {
        return true;
    }

    @Override
    public String getAudioSpdifCodecs() {
        return player.getAudioSpdifCodecs();
    }

    @Override
    public void setSubtitleStyle(float textSize, float position) {
        player.setSubtitleStyle(textSize, position);
    }

    @Override
    public boolean supportsSecondarySubtitle() {
        return !surfaceDirect;
    }

    @Override
    public boolean isSecondarySubtitleSelected(Format format) {
        return format != null && player.isSecondarySubtitleSelected(parseMpvTrackId(format.id));
    }

    @Override
    public void setSecondarySubtitleTrack(Track track) {
        if (track == null) return;
        if (track.isDisabled() && track.getType() == C.TRACK_TYPE_TEXT) {
            player.setSecondarySubtitleTrackSelection("no");
            return;
        }
        String id = resolveMpvTrackId(track);
        if (id != null) {
            player.setSecondarySubtitleTrackSelection(id);
        } else {
            SpiderDebug.log("mpv", "select secondary subtitle failed: no mpv id name=%s format=%s", track.getName(), track.getFormat());
        }
    }

    @Override
    public boolean haveTitle() {
        return !getCurrentMediaEditions().isEmpty();
    }

    @Override
    public List<MediaEdition> getCurrentMediaEditions() {
        return player.getCurrentMediaEditions();
    }

    @Override
    public boolean selectEdition(MediaEdition edition) {
        return player.selectEdition(edition);
    }

    private String findMpvTrackId(Track track) {
        if (track == null || track.getFormat() == null) return null;
        List<Format> candidates = new ArrayList<>();
        for (Tracks.Group group : getCurrentTracks().getGroups()) {
            if (group.getType() != track.getType()) continue;
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) continue;
                candidates.add(group.getTrackFormat(i));
            }
        }
        Format matched = findPersistedMpvTrack(track, candidates);
        if (matched == null) return null;
        String id = parseMpvTrackId(matched.id);
        if (id != null) {
            SpiderDebug.log("mpv",
                    "restore persisted track matched type=%d id=%s name=%s format=%s",
                    track.getType(), id, track.getName(), track.getFormat());
        }
        return id;
    }

    private String resolveMpvTrackId(Track track) {
        if (track == null) return null;
        String id = parseMpvTrackId(track.getPlayerId());
        // playerId is supplied by the currently visible track list. Only persisted
        // preferences from an earlier playback need the legacy description fallback.
        return id != null ? id : findMpvTrackId(track);
    }

    @Nullable
    static Format findPersistedMpvTrack(Track track, List<Format> candidates) {
        if (track == null || track.getFormat() == null
                || candidates == null || candidates.isEmpty()) return null;
        List<PersistedTrackCandidate> descriptors = new ArrayList<>();
        for (Format format : candidates) {
            descriptors.add(format == null ? null : new PersistedTrackCandidate(
                    PlayerHelper.describeFormat(format),
                    format.id,
                    format.sampleMimeType,
                    format.codecs,
                    format.sampleRate,
                    format.channelCount,
                    format.language,
                    format.label));
        }
        int index = findPersistedMpvTrackIndex(track.getFormat(), descriptors);
        return index >= 0 ? candidates.get(index) : null;
    }

    static int findPersistedMpvTrackIndex(
            String persisted, List<PersistedTrackCandidate> candidates) {
        if (persisted == null || candidates == null || candidates.isEmpty()) return -1;
        int bestIndex = -1;
        int bestScore = -1;
        for (int index = 0; index < candidates.size(); index++) {
            PersistedTrackCandidate candidate = candidates.get(index);
            if (candidate == null) continue;
            if (persisted.equals(candidate.description())) return index;
            int score = persistedTrackMatchScore(persisted, candidate);
            if (score <= bestScore) continue;
            bestIndex = index;
            bestScore = score;
        }
        return bestScore >= 40 ? bestIndex : -1;
    }

    static int persistedTrackMatchScore(
            String persisted, PersistedTrackCandidate candidate) {
        if (persisted == null || candidate == null) return -1;
        boolean persistedHasMime = hasPersistedMimeToken(persisted);
        if (persistedHasMime && (candidate.sampleMimeType() == null
                || !hasPersistedToken(persisted, candidate.sampleMimeType()))) return -1;
        int score = persistedHasMime ? 100 : 0;
        if (candidate.codecs() != null && hasPersistedToken(persisted, candidate.codecs())) score += 40;
        if (candidate.sampleRate() > 0 && hasPersistedToken(persisted, String.valueOf(candidate.sampleRate()))) score += 10;
        if (candidate.channelCount() > 0 && hasPersistedToken(persisted, String.valueOf(candidate.channelCount()))) score += 10;
        if (candidate.language() != null && hasPersistedToken(persisted, candidate.language())) score += 5;
        if (candidate.label() != null && hasPersistedToken(persisted, candidate.label())) score += 3;
        String persistedId = firstPersistedToken(persisted);
        String candidateId = parseMpvTrackId(candidate.id());
        if (candidateId != null && candidateId.equals(parseMpvTrackId(persistedId))) score++;
        return score;
    }

    record PersistedTrackCandidate(
            String description,
            String id,
            String sampleMimeType,
            String codecs,
            int sampleRate,
            int channelCount,
            String language,
            String label) {
    }

    private static boolean hasPersistedMimeToken(String persisted) {
        for (String token : persisted.split(",")) {
            String value = token.trim().toLowerCase(java.util.Locale.US);
            if (value.startsWith("audio/") || value.startsWith("video/")
                    || value.startsWith("text/") || value.startsWith("application/")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPersistedToken(String persisted, String expected) {
        if (expected == null || expected.isBlank()) return false;
        for (String token : persisted.split(",")) {
            if (token.trim().equalsIgnoreCase(expected.trim())) return true;
        }
        return false;
    }

    private static String firstPersistedToken(String persisted) {
        int comma = persisted.indexOf(',');
        return (comma < 0 ? persisted : persisted.substring(0, comma)).trim();
    }

    @Nullable
    static String persistedSubtitleTrackId(@Nullable Track track) {
        if (track == null || track.getType() != C.TRACK_TYPE_TEXT
                || !track.isSelected() || track.isDisabled()
                || track.getFormat() == null) return null;
        String token = firstPersistedToken(track.getFormat());
        int separator = token.indexOf(':');
        if (separator <= 0 || separator + 1 >= token.length()) return null;
        try {
            if (Integer.parseInt(token.substring(0, separator))
                    != C.TRACK_TYPE_TEXT) return null;
        } catch (NumberFormatException ignored) {
            return null;
        }
        String id = parseMpvTrackId(token.substring(separator + 1));
        return id == null || id.isBlank() || "auto".equalsIgnoreCase(id)
                || "no".equalsIgnoreCase(id) ? null : id;
    }

    private static String parseMpvTrackId(String id) {
        if (id == null) return null;
        int index = id.indexOf(':');
        return index >= 0 && index + 1 < id.length() ? id.substring(index + 1) : id;
    }

    @Override
    public String getErrorMessage(PlaybackException e) {
        String message = e.getMessage();
        if (startsWith(message, MpvPlayer.ERROR_HLS_PLAYBACK_FAILED)) return ResUtil.getString(R.string.error_play_mpv_hls_unsupported);
        if (startsWith(message, MpvPlayer.ERROR_LOAD_FAILED)) return ResUtil.getString(R.string.error_play_mpv_load_failed);
        if (startsWith(message, MpvPlayer.ERROR_NETWORK_FAILED)) return ResUtil.getString(R.string.error_play_mpv_network_failed);
        if (startsWith(message, MpvPlayer.ERROR_DRM_UNSUPPORTED)) return ResUtil.getString(R.string.error_play_mpv_drm_unsupported);
        if (startsWith(message, MpvPlayer.ERROR_UNEXPECTED_IMAGE)) return ResUtil.getString(R.string.error_play_mpv_unexpected_image);
        if (startsWith(message, MpvPlayer.ERROR_NO_AV_DATA)) return ResUtil.getString(R.string.error_play_mpv_no_av);
        if (startsWith(message, MpvPlayer.ERROR_INVALID_MEDIA_DATA)) return ResUtil.getString(R.string.error_play_mpv_invalid_data);
        if (startsWith(message, MpvPlayer.ERROR_DECODE_FAILED)) return ResUtil.getString(R.string.error_play_mpv_decode_failed);
        if (startsWith(message, MpvPlayer.ERROR_VIDEO_OUTPUT_FAILED)) return ResUtil.getString(R.string.error_play_mpv_video_output);
        return e.getMessage();
    }

    @Override
    public ErrorAction handleError(PlaybackException e) {
        PlaybackTrace.log("player-engine", getPlaybackTraceId(), "handleError mpv code=%d message=%s format=%s retried=%s urlLen=%d", e.errorCode, e.getMessage(), spec == null ? null : spec.getFormat(), retriedFormat, spec == null || spec.getUrl() == null ? 0 : spec.getUrl().length());
        if (shouldRetryFormat(e)) return retryFormat();
        return ErrorAction.FATAL;
    }

    private boolean shouldRetryFormat(PlaybackException e) {
        if (retriedFormat || spec == null || spec.getFormat() != null) return false;
        String message = e.getMessage();
        if (isTerminalMpvError(message)) return false;
        return e.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                || e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                || e.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
                || e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                || e.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED;
    }

    private ErrorAction retryFormat() {
        retriedFormat = true;
        spec.setFormat(MimeTypes.APPLICATION_M3U8);
        long position = Math.max(0, player.getCurrentPosition());
        PlaybackTrace.log("player-engine", getPlaybackTraceId(), "retryFormat mpv newFormat=%s position=%d", spec.getFormat(), position);
        player.stop();
        MediaItem item = ExoUtil.getMediaItem(spec, decode);
        if (position > 0) player.setMediaItem(item, position);
        else player.setMediaItem(item);
        player.prepare();
        if (playWhenReady) player.play();
        else player.pause();
        return ErrorAction.RECOVERED;
    }

    private boolean isTerminalMpvError(String message) {
        return startsWith(message, MpvPlayer.ERROR_HLS_PLAYBACK_FAILED)
                || startsWith(message, MpvPlayer.ERROR_NETWORK_FAILED)
                || startsWith(message, MpvPlayer.ERROR_DRM_UNSUPPORTED)
                || startsWith(message, MpvPlayer.ERROR_UNEXPECTED_IMAGE)
                || startsWith(message, MpvPlayer.ERROR_NO_AV_DATA)
                || startsWith(message, MpvPlayer.ERROR_INVALID_MEDIA_DATA)
                || startsWith(message, MpvPlayer.ERROR_DECODE_FAILED)
                || startsWith(message, MpvPlayer.ERROR_VIDEO_OUTPUT_FAILED);
    }

    private boolean startsWith(String message, String prefix) {
        return message != null && message.startsWith(prefix);
    }

    private DecoderKind decoderKind(String hwdec, boolean observed) {
        if (!observed) return DecoderKind.UNKNOWN;
        if (hwdec == null || hwdec.isBlank()) return DecoderKind.SOFTWARE;
        String value = hwdec.trim().toLowerCase(java.util.Locale.US);
        return value.equals("no") || value.equals("none") || value.equals("software")
                ? DecoderKind.SOFTWARE : DecoderKind.HARDWARE;
    }

    private MpvPlayer buildPlayer(Player.Listener listener) {
        MpvPlayer player = new MpvPlayer(App.get(), buildConfig());
        player.setInitialOsdSurfaceRequested(initialSubtitleSurfaceRequested);
        player.setInitialTrackSelectionGateRequested(
                initialSubtitleSurfaceRequested);
        player.setInitialSubtitleTrackId(initialSubtitleTrackId);
        if (PlaybackPerformanceSetting.isAuto(
                PlayerSetting.MPV,
                PlaybackPerformanceCatalog.PRELOAD)) {
            player.updateAutomaticPreloadControl(true, false, false);
        }
        player.setVideoSizeProbeListener(videoSizeProbeListener);
        player.addListener(listener);
        return player;
    }

    private MpvPlayerConfig buildConfig() {
        MpvConfigStore.ensureReady();
        boolean zeroCopyBlocked = MpvPerformanceSetting.isZeroCopyBlocked();
        boolean autoDirectEligible = !zeroCopyBlocked && MpvAutoOutputPolicy.canStartSurfaceDirect(
                decode == HARD,
                Util.isLeanback(),
                MpvPerformanceSetting.isInterpolation() || lutAllowed && LutSetting.isEnabled(),
                MpvConfigStore.hasGpuVideoProcessing());
        surfaceDirect = surfaceDirectOverride == null
                ? MpvPerformanceSetting.shouldUseSurfaceDirect(autoDirectEligible, Util.isLeanback(), decode == HARD)
                : surfaceDirectOverride && decode == HARD && !zeroCopyBlocked;
        boolean requestVulkan = vulkanRenderOverride != null
                ? vulkanRenderOverride
                : PlayerSetting.getMpvRender() == PlayerSetting.MPV_RENDER_VULKAN;
        boolean nativeVulkan = MPVLib.isBundledVulkanEnabled(App.get());
        boolean deviceVulkan = MPVLib.isDeviceVulkan13Capable(App.get());
        boolean useVulkan = !surfaceDirect && requestVulkan && nativeVulkan && deviceVulkan;
        vulkanRenderer = useVulkan;
        String configuredBackend = MpvVulkanBackendPolicy.configuredBackend();
        String appBackendOverride = MpvVulkanBackendPolicy.appOverride();
        boolean automaticBackend = configuredBackend.isEmpty()
                || MpvVulkanBackendPolicy.AUTO.equals(configuredBackend);
        String automaticOverride = vulkanBackendOverride != null
                ? vulkanBackendOverride : MpvVulkanBackendPolicy.automaticOverride();
        vulkanBackend = automaticBackend && !automaticOverride.isEmpty()
                ? automaticOverride
                : configuredBackend.isEmpty() ? MpvVulkanBackendPolicy.AUTO : configuredBackend;
        boolean useGpuNext = !surfaceDirect && (useVulkan || decode != HARD);
        if (requestVulkan && !surfaceDirect && !useVulkan) SpiderDebug.log("player-engine", "mpv render requested=vulkan but unavailable native=%s device=%s; fallback=opengl", nativeVulkan, deviceVulkan);
        String hwdec = surfaceDirect ? "mediacodec" : resolveGpuHwdec(zeroCopyBlocked);
        configuredHwdec = hwdec;
        SpiderDebug.log("player-engine", "mpv output mode=%s direct=%s zeroCopyBlocked=%s hwdec=%s render requested=%s nativeVulkan=%s deviceVulkan=%s decode=%s actual=%s/%s", MpvPerformanceSetting.getOutputModeText(), surfaceDirect, zeroCopyBlocked, hwdec, requestVulkan ? "vulkan" : "opengl", nativeVulkan, deviceVulkan, decode == HARD ? "hard" : "soft", surfaceDirect ? "surface" : useVulkan ? "vulkan" : "opengl", surfaceDirect ? "mediacodec_embed" : useGpuNext ? "gpu-next" : "gpu");
        MpvPlayerConfig.Builder builder = MpvPlayerConfig.builder(App.get())
                .configDir(MpvConfigStore.configDir())
                .hwdec(hwdec)
                .audioSpdif(resolveAudioSpdifCodecs())
                .logLevel(MpvPerformanceSetting.isVerboseLog() ? "all=v" : "all=warn")
                .demuxerMaxBytes(getDemuxerMaxBytes())
                .demuxerMaxBackBytes(getDemuxerMaxBackBytes())
                .cacheSeconds(getCacheTargetSeconds())
                .demuxerReadaheadSeconds(MpvPlayerConfig.DEFAULT_DEMUXER_READAHEAD_SECONDS)
                .demuxerHysteresisSeconds(MpvPlayerConfig.DEFAULT_DEMUXER_HYSTERESIS_SECONDS)
                .rebufferMs(MpvPerformanceSetting.getRebufferMs())
                .performanceOptionsPriority(MpvPerformanceSetting.isPerformancePriority())
                .automaticCacheTime(PlaybackPerformanceSetting.isAuto(
                        PlayerSetting.MPV,
                        PlaybackPerformanceCatalog.BUFFER_TIME))
                .automaticHlsVariant(PlaybackPerformanceSetting.isAuto(
                        PlayerSetting.MPV,
                        PlaybackPerformanceCatalog.MPV_HLS_BITRATE))
                .deferStartupTrackRefresh(DV7_P81.equals(getDv7HandlingOption()))
                .option("framedrop", MpvPerformanceSetting.getFrameDropOption())
                .option("video-sync", MpvPerformanceSetting.getSyncOption())
                .option("interpolation", MpvPerformanceSetting.isInterpolation() ? "yes" : "no")
                .option("hls-bitrate", MpvPerformanceSetting.getHlsBitrateOption())
                .option("demuxer-dovi-profile7",
                        getDv7HandlingOption());
        if (useVulkan && !appBackendOverride.isEmpty()) {
            builder.option(MpvVulkanBackendPolicy.OPTION, appBackendOverride);
        } else if (useVulkan && automaticBackend && !automaticOverride.isEmpty()) {
            builder.option(MpvVulkanBackendPolicy.OPTION, automaticOverride);
        }
        applySoftDecodeOptions(builder);
        if (surfaceDirect) {
            builder.vo("mediacodec_embed")
                    .option("sid", "no")
                    .gpuApi("")
                    .openglEs(false);
        } else if (useVulkan) {
            builder.vo("gpu-next")
                    .gpuContext("androidvk")
                    .gpuApi("vulkan")
                    .openglEs(false);
        } else if (useGpuNext) {
            // The legacy gpu renderer restores the original pre-Dolby-Vision
            // color representation. Software-decoded Profile 5 frames need
            // gpu-next/libplacebo to apply their per-frame DOVI mapping.
            builder.vo("gpu-next")
                    .gpuContext("android")
                    .gpuApi("opengl")
                    .openglEs(true);
        } else {
            // Keep the OpenGL override complete. Leaving gpu-api empty would
            // allow gpu-api=vulkan from mpv.conf to survive the performance
            // overlay and create an invalid android + Vulkan mixed context.
            builder.vo("gpu")
                    .gpuContext("android")
                    .gpuApi("opengl")
                    .openglEs(true);
        }
        return builder.build();
    }

    private String resolveGpuHwdec(boolean zeroCopyBlocked) {
        if (decode != HARD) return "no";
        if (zeroCopyBlocked) return "mediacodec-copy";
        return hwdecOverride == null ? MpvPerformanceSetting.getHwdecOption() : hwdecOverride;
    }

    private void applySoftDecodeOptions(MpvPlayerConfig.Builder builder) {
        int mode = MpvPerformanceSetting.getSoftTuneMode();
        if (mode == MpvPerformanceSetting.SOFT_TUNE_OFF) return;
        // MPV can silently fall back from MediaCodec while the engine still represents a hard-decode request.
        // Prime the libavcodec fallback so 4K software decoding does not start with the expensive defaults.
        builder.option("vd-lavc-fast", "yes");
        builder.option("vd-lavc-threads", "0");
        builder.option("vd-lavc-skiploopfilter", mode == MpvPerformanceSetting.SOFT_TUNE_AGGRESSIVE ? "nonkey" : "nonref");
    }

    private String resolveAudioSpdifCodecs() {
        boolean enabled = PlayerSetting.isAudioPassThrough(PlayerSetting.MPV);
        String codecs = enabled ? MpvAudioCapabilities.getAudioSpdifCodecs(App.get()) : "";
        SpiderDebug.log("mpv-audio", "configured enabled=%s codecs=%s",
                enabled, codecs.isEmpty() ? "pcm" : codecs);
        return codecs;
    }

    private long getDemuxerMaxBytes() {
        if (PlaybackPerformanceSetting.isAuto(
                PlayerSetting.MPV,
                PlaybackPerformanceCatalog.BUFFER_BYTES)) {
            return MpvAutoControlPolicy.MIN_FORWARD_BYTES;
        }
        int bytes = PlayerSetting.getBufferBytes(PlayerSetting.MPV);
        return bytes > 0 ? bytes : MpvPlayerConfig.DEFAULT_DEMUXER_BYTES;
    }

    private long getDemuxerMaxBackBytes() {
        if (PlaybackPerformanceSetting.isAuto(
                PlayerSetting.MPV,
                PlaybackPerformanceCatalog.BACK_BUFFER)) {
            return MpvAutoControlPolicy.INITIAL_BACK_BYTES;
        }
        if (PlayerSetting.getBackBufferMs(PlayerSetting.MPV) <= 0) return 0;
        long forward = getDemuxerMaxBytes();
        return switch (PlayerSetting.getBackBufferOption(PlayerSetting.MPV)) {
            case 1 -> Math.max(16L * 1024 * 1024, forward / 4);
            case 2 -> Math.max(32L * 1024 * 1024, forward / 2);
            case 3 -> forward;
            default -> 0;
        };
    }

    private int getDemuxerReadAheadSeconds() {
        return Math.min(120, Math.max(15, PlayerSetting.getBuffer(PlayerSetting.MPV) * 4));
    }

    private int getCacheTargetSeconds() {
        return Math.min(60, Math.max(15, PlayerSetting.getBuffer(PlayerSetting.MPV) * 3));
    }

    public long getConfiguredForwardCacheBytes() {
        return getDemuxerMaxBytes();
    }

    public long getConfiguredBackCacheBytes() {
        return getDemuxerMaxBackBytes();
    }

    private static long formatBitrate(Format format) {
        if (format == null) return 0;
        if (format.averageBitrate > 0) return format.averageBitrate;
        if (format.peakBitrate > 0) return format.peakBitrate;
        return Math.max(0, format.bitrate);
    }

    private static long safeAdd(long first, long second) {
        if (first <= 0) return Math.max(0, second);
        if (second <= 0) return first;
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }
}
