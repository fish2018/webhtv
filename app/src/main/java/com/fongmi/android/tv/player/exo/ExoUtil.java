package com.fongmi.android.tv.player.exo;

import android.content.Context;
import android.graphics.Color;
import android.media.MediaCrypto;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Range;
import android.view.Display;
import android.view.accessibility.CaptioningManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.audio.AudioCapabilities;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackAutoContextStore;
import com.fongmi.android.tv.player.PlaybackExperimentCoordinator;
import com.fongmi.android.tv.player.PlaybackExperimentPolicy;
import com.fongmi.android.tv.player.PlaybackSystemConditionCoordinator;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.player.track.LangUtil;
import com.fongmi.android.tv.setting.ExoPerformanceSetting;
import com.fongmi.android.tv.setting.ExoFrameSchedulingExperimentSetting;
import com.fongmi.android.tv.setting.PlaybackExperimentSetting;
import com.fongmi.android.tv.setting.PlaybackPerformanceCatalog;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.CompatFfmpegAudioRenderer;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegVideoRenderer;

public class ExoUtil {

    static final long ENHANCED_LATE_THRESHOLD_TO_DROP_INPUT_US = 5_000L;
    private static final long ENHANCED_ADAPT_COOLDOWN_MS = 15_000L;
    private static final int ENHANCED_DROPPED_FRAMES_THRESHOLD = 24;
    private static final int ENHANCED_DROPPED_FRAMES_PER_SECOND_THRESHOLD = 4;
    private static final int FFMPEG_SKIP_FRAME_NONREF = 8;
    private static final int FFMPEG_SKIP_LOOP_FILTER_ALL = 48;
    private static final int FFMPEG_LOWRES_HALF = 1;
    private static volatile EnhancedVideoProfile enhancedVideoProfile;
    private static volatile ExoPlaybackCapability.Report playbackCapabilityReport;

    public static void setPlayerView(PlayerView view) {
        view.setRender(PlayerSetting.getRender());
        view.getSubtitleView().setStyle(getCaptionStyle());
        view.getSubtitleView().setApplyEmbeddedStyles(true);
        view.getSubtitleView().setApplyEmbeddedFontSizes(false);
        if (PlayerSetting.getSubtitlePosition() != 0) view.getSubtitleView().setBottomPosition(PlayerSetting.getSubtitlePosition());
        if (PlayerSetting.getSubtitleTextSize() != 0) view.getSubtitleView().setFractionalTextSize(PlayerSetting.getSubtitleTextSize());
    }

    public static ExoPlayer buildPlayer(int decode, Player.Listener listener) {
        return buildPlayer(decode, listener, false);
    }

    public static ExoPlayer buildPlayer(int decode, Player.Listener listener, boolean tunnelingFallbackAttempted) {
        return buildPlayer(decode, listener, tunnelingFallbackAttempted, null);
    }

    public static ExoPlayer buildPlayer(
            int decode,
            Player.Listener listener,
            boolean tunnelingFallbackAttempted,
            @Nullable ExoDecoderRuntimeSession decoderRuntimeSession) {
        return buildPlayer(
                decode,
                listener,
                tunnelingFallbackAttempted,
                decoderRuntimeSession,
                ExoFrameSchedulingPlayerSettings.capture(decode));
    }

    public static ExoPlayer buildPlayer(
            int decode,
            Player.Listener listener,
            boolean tunnelingFallbackAttempted,
            @Nullable ExoDecoderRuntimeSession decoderRuntimeSession,
            ExoFrameSchedulingPlayerSettings frameSchedulingSettings) {
        return buildPlayer(
                decode,
                listener,
                tunnelingFallbackAttempted,
                decoderRuntimeSession,
                frameSchedulingSettings,
                null);
    }

    public static ExoPlayer buildPlayer(
            int decode,
            Player.Listener listener,
            boolean tunnelingFallbackAttempted,
            @Nullable ExoDecoderRuntimeSession decoderRuntimeSession,
            ExoFrameSchedulingPlayerSettings frameSchedulingSettings,
            @Nullable ExoDolbyVisionPlaybackState dolbyVisionPlaybackState) {
        ExoFrameSchedulingPlayerSettings schedulingSettings =
                frameSchedulingSettings == null
                        ? ExoFrameSchedulingPlayerSettings.capture(decode)
                        : frameSchedulingSettings;
        boolean automaticProfile =
                PlaybackPerformanceSetting.isAuto(PlayerSetting.EXO);
        boolean automaticBandwidth = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.EXO,
                PlaybackPerformanceCatalog.BANDWIDTH_METER);
        EnhancedVideoProfile profile = getEnhancedVideoProfile(decode);
        List<EnhancedVideoProfile> profiles = getEnhancedVideoProfiles(decode);
        DefaultTrackSelector trackSelector = buildTrackSelector(decode, tunnelingFallbackAttempted);
        ExoDecoderRuntimeSession.OutputConfig decoderOutput =
                ExoDecoderRuntimeProfiles.currentOutput(
                        isTunnelingEnabled(decode, tunnelingFallbackAttempted));
        ExoPlayer.Builder builder = new ExoPlayer.Builder(App.get())
                .setTrackSelector(trackSelector)
                .setRenderersFactory(buildPlaybackRenderersFactory(
                        decode,
                        automaticProfile ? decoderRuntimeSession : null,
                        decoderOutput,
                        schedulingSettings,
                        dolbyVisionPlaybackState))
                .setMediaSourceFactory(buildMediaSourceFactory(
                        dolbyVisionPlaybackState))
                .setVideoChangeFrameRateStrategy(ExoPerformanceSetting.getFrameRateStrategy());
        if (PlaybackPerformanceSetting.isHighBufferEnabled()) builder.setLoadControl(buildEnhancedLoadControl());
        else ExoPlaybackDiagnostics.logDefaultLoadControl(PlaybackPerformanceSetting.getProfile(PlayerSetting.EXO));
        if (PlaybackPerformanceSetting.isBandwidthMeterEnabled()) {
            builder.setBandwidthMeter(automaticBandwidth
                    ? buildAutomaticBandwidthMeter(App.get())
                    : buildEnhancedBandwidthMeter(App.get()));
        }
        if (schedulingSettings.dynamicSchedulingEnabled()) {
            builder.experimentalSetDynamicSchedulingEnabled(true);
        }
        ExoPlayer player = builder.build();
        PlaybackAnalyticsListener.reset();
        PlaybackAnalyticsListener analyticsListener = new PlaybackAnalyticsListener();
        player.addAnalyticsListener(analyticsListener);
        player.setVideoFrameMetadataListener(analyticsListener);
        if (PlaybackPerformanceSetting.isAdaptiveDowngradeEnabled()) {
            if (PlaybackPerformanceSetting.isAuto(
                    PlayerSetting.EXO,
                    PlaybackPerformanceCatalog.ADAPTIVE_DOWNGRADE)
                    && PlaybackPerformanceSetting.isTrackLimitEnabled()) {
                player.addAnalyticsListener(new AutomaticVideoConstraintController(trackSelector, profile, profiles));
            } else {
                player.addAnalyticsListener(new LegacyAdaptiveVideoProfileController(trackSelector, profile, profiles));
            }
        }
        if (BuildConfig.DEBUG) player.addAnalyticsListener(new EventLogger());
        player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        player.setHandleAudioBecomingNoisy(true);
        player.setPlayWhenReady(true);
        player.addListener(listener);
        return player;
    }

    public static MediaItem getMediaItem(PlaySpec spec, int decode) {
        MediaItem.Builder builder = new MediaItem.Builder().setUri(spec.getUri());
        builder.setSubtitleConfigurations(buildSubtitleConfigs(spec.getSubs()));
        builder.setDrmConfiguration(buildDrmConfig(spec.getDrm()));
        builder.setRequestMetadata(buildRequestMetadata(spec));
        builder.setMediaMetadata(spec.getMetadata());
        builder.setAdblock(Setting.isAdblock());
        builder.setMimeType(spec.getFormat());
        builder.setImageDurationMs(15000);
        builder.setMediaId(spec.getKey());
        builder.setDecode(decode);
        return builder.build();
    }

    public static String getMimeType(int errorCode) {
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED || errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) return MimeTypes.APPLICATION_M3U8;
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED) return MimeTypes.APPLICATION_OCTET_STREAM;
        return null;
    }

    public static Map<String, String> extractHeaders(MediaItem item) {
        Bundle extras = item.requestMetadata.extras;
        if (extras == null) return new HashMap<>();
        return extras.keySet().stream().filter(key -> extras.getString(key) != null).collect(Collectors.toMap(key -> key, extras::getString));
    }

    private static int getVideoRenderMode(int decode) {
        return decode == PlayerEngine.HARD ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
    }

    private static int getAudioRenderMode() {
        return DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
    }

    private static boolean isAudioPrefer(int decode) {
        return decode != PlayerEngine.SOFT && PlayerSetting.isAudioPrefer(PlayerSetting.EXO);
    }

    private static CaptionStyleCompat getCaptionStyle() {
        return PlayerSetting.isCaption() ? CaptionStyleCompat.createFromCaptionStyle(((CaptioningManager) App.get().getSystemService(Context.CAPTIONING_SERVICE)).getUserStyle()) : new CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null);
    }

    private static DefaultTrackSelector buildTrackSelector(int decode, boolean tunnelingFallbackAttempted) {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(App.get());
        DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters();
        if (PlayerSetting.isPreferAAC(PlayerSetting.EXO)) builder.setPreferredAudioMimeType(MimeTypes.AUDIO_AAC);
        builder.setPreferredTextLanguages(LangUtil.getPreferredTextLanguages());
        ExoTunnelingPolicy.Decision tunneling = getTunnelingDecision(decode, tunnelingFallbackAttempted);
        builder.setTunnelingEnabled(tunneling.enabled());
        if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-tunnel", "requested=%s enabled=%s reason=%s decode=%d render=%d lut=%s fallback=%s", PlayerSetting.isTunnel(), tunneling.enabled(), tunneling.reason(), decode, PlayerSetting.getRender(), LutSetting.isEnabled(), tunnelingFallbackAttempted);
        if (PlaybackPerformanceSetting.isTrackLimitEnabled()) {
            applyEnhancedVideoProfile(builder, getEnhancedVideoProfile(decode));
        } else {
            builder.setForceHighestSupportedBitrate(true);
        }
        trackSelector.setParameters(builder.build());
        return trackSelector;
    }

    public static boolean isTunnelingEnabled(int decode, boolean tunnelingFallbackAttempted) {
        return getTunnelingDecision(decode, tunnelingFallbackAttempted).enabled();
    }

    public static String getTunnelingRuntimeKey() {
        return getTunnelingRuntimeKey(PlayerEngine.HARD);
    }

    public static String getTunnelingRuntimeKey(int decode) {
        ExoPlaybackCapability.DecoderCapability decoder = getPlaybackCapabilityReport().decoder();
        return Build.MANUFACTURER + "|" + Build.MODEL + "|" + decoder.name() + "|" + decoder.mimeType() + "|decode=" + decode;
    }

    private static ExoTunnelingPolicy.Decision getTunnelingDecision(int decode, boolean tunnelingFallbackAttempted) {
        ExoTunnelingPolicy.Request request = new ExoTunnelingPolicy.Request(
                PlayerSetting.isTunnel(),
                PlayerSetting.getRender() == PlayerSetting.RENDER_SURFACE,
                decode != PlayerEngine.SOFT,
                true,
                true,
                LutSetting.isEnabled(),
                false,
                ExoTunnelingRuntimeState.isBlacklisted(getTunnelingRuntimeKey(decode)),
                false,
                true,
                tunnelingFallbackAttempted);
        return ExoTunnelingPolicy.resolve(request);
    }

    private static void applyEnhancedVideoProfile(DefaultTrackSelector.Parameters.Builder builder, EnhancedVideoProfile profile) {
        applyVideoLimit(builder, new ExoAutomaticVideoConstraintPolicy.Limit(
                profile.width(), profile.height(), profile.frameRate(), profile.maxVideoBitrate()));
    }

    private static void applyVideoLimit(
            DefaultTrackSelector.Parameters.Builder builder,
            ExoAutomaticVideoConstraintPolicy.Limit limit) {
        builder.setMaxVideoSize(limit.width(), limit.height());
        builder.setViewportSize(limit.width(), limit.height(), true);
        builder.setMaxVideoBitrate(limit.maxVideoBitrate());
        builder.setMaxVideoFrameRate(limit.frameRate());
        builder.setExceedVideoConstraintsIfNecessary(true);
        builder.setAllowVideoNonSeamlessAdaptiveness(true);
        builder.setAllowVideoMixedMimeTypeAdaptiveness(true);
        builder.setForceHighestSupportedBitrate(false);
    }

    public static EnhancedVideoProfile getEnhancedVideoProfile() {
        return getEnhancedVideoProfile(PlayerEngine.HARD);
    }

    private static EnhancedVideoProfile getEnhancedVideoProfile(int decode) {
        if (decode == PlayerEngine.SOFT) return detectSoftVideoProfile(App.get());
        EnhancedVideoProfile profile = enhancedVideoProfile;
        if (profile != null) return profile;
        synchronized (ExoUtil.class) {
            profile = enhancedVideoProfile;
            if (profile == null) enhancedVideoProfile = profile = detectEnhancedVideoProfile(App.get());
        }
        return profile;
    }

    private static List<EnhancedVideoProfile> getEnhancedVideoProfiles(int decode) {
        return decode == PlayerEngine.SOFT ? EnhancedVideoProfile.softTargets() : EnhancedVideoProfile.targets();
    }

    private static EnhancedVideoProfile detectEnhancedVideoProfile(Context context) {
        ExoPlaybackCapability.Report report = getPlaybackCapabilityReport(context);
        DisplayProfile display = getDisplayProfile(report.display());
        CodecVideoProfile codec = toCodecVideoProfile(report.decoder());
        EnhancedVideoProfile profile = codec.supported() ? codec.profile() : EnhancedVideoProfile.low();
        return logEnhancedVideoProfile(profile, display, codec);
    }

    public static ExoPlaybackCapability.Report getPlaybackCapabilityReport() {
        return getPlaybackCapabilityReport(App.get());
    }

    private static ExoPlaybackCapability.Report getPlaybackCapabilityReport(Context context) {
        ExoPlaybackCapability.Report report = playbackCapabilityReport;
        if (report != null) return report;
        synchronized (ExoUtil.class) {
            report = playbackCapabilityReport;
            if (report == null) playbackCapabilityReport = report = detectPlaybackCapability(context);
        }
        return report;
    }

    private static ExoPlaybackCapability.Report detectPlaybackCapability(Context context) {
        DisplayProfile display = getDisplayProfile(context);
        CodecVideoProfile codec = chooseCodecVideoProfile(MediaFormat.MIMETYPE_VIDEO_HEVC, display);
        ExoPlaybackCapability.DisplayCapability displayCapability = new ExoPlaybackCapability.DisplayCapability(display.width(), display.height(), display.currentWidth(), display.currentHeight(), display.currentRefreshRate());
        ExoPlaybackCapability.DecoderCapability decoderCapability = new ExoPlaybackCapability.DecoderCapability(codec.name(), MediaFormat.MIMETYPE_VIDEO_HEVC, codec.profile().width(), codec.profile().height(), codec.profile().frameRate(), codec.profile().bitrate(), codec.profile().maxVideoBitrate(), codec.supported(), codec.performancePoint());
        ExoPlaybackCapability.Report report = ExoPlaybackCapability.Report.deviceOnly(displayCapability, decoderCapability);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-capability", "display=max%dx%d current=%dx%d@%.3f decoder=%s %s", display.width(), display.height(), display.currentWidth(), display.currentHeight(), display.currentRefreshRate(), codec.name(), codec.profileText());
        return report;
    }

    private static CodecVideoProfile toCodecVideoProfile(ExoPlaybackCapability.DecoderCapability decoder) {
        EnhancedVideoProfile profile = new EnhancedVideoProfile(decoder.width(), decoder.height(), decoder.bitrate(), decoder.frameRate(), decoder.maxVideoBitrate());
        return new CodecVideoProfile(decoder.name(), profile, decoder.performancePoint());
    }

    private static EnhancedVideoProfile detectSoftVideoProfile(Context context) {
        DisplayProfile display = getDisplayProfile(context);
        for (EnhancedVideoProfile target : EnhancedVideoProfile.softTargets()) {
            if (display.supports(target)) return logSoftVideoProfile(target, display);
        }
        return logSoftVideoProfile(EnhancedVideoProfile.low(), display);
    }

    private static EnhancedVideoProfile logSoftVideoProfile(EnhancedVideoProfile profile, DisplayProfile display) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-enhance", "soft profile=%dx%d@%d bitrate=%d display=%dx%d", profile.width(), profile.height(), profile.frameRate(), profile.bitrate(), display.width(), display.height());
        return profile;
    }

    private static CodecVideoProfile chooseCodecVideoProfile(String mimeType, DisplayProfile display) {
        for (EnhancedVideoProfile target : EnhancedVideoProfile.targets()) {
            if (!display.supports(target)) continue;
            CodecVideoProfile codec = getBestCodecVideoProfile(mimeType, target);
            if (codec.supported()) return codec;
        }
        return CodecVideoProfile.unsupported();
    }

    private static CodecVideoProfile getBestCodecVideoProfile(String mimeType, EnhancedVideoProfile target) {
        CodecVideoProfile best = CodecVideoProfile.unsupported();
        for (android.media.MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (info.isEncoder() || !isHardwareCodec(info)) continue;
            android.media.MediaCodecInfo.VideoCapabilities caps = getVideoCapabilities(info, mimeType);
            if (caps == null) continue;
            EnhancedVideoProfile supported = getSupportedProfile(caps, target);
            if (supported == null) continue;
            CodecVideoProfile profile = new CodecVideoProfile(info.getName(), supported, hasPerformancePoint(caps, supported));
            if (profile.compareTo(best) > 0) best = profile;
        }
        return best;
    }

    private static EnhancedVideoProfile getSupportedProfile(android.media.MediaCodecInfo.VideoCapabilities caps, EnhancedVideoProfile target) {
        if (!supportsSize(caps, target.width(), target.height())) return null;
        int maxVideoBitrate = getSupportedTrackBitrate(caps, target.maxVideoBitrate());
        if (supportsPerformance(caps, target) || supportsRate(caps, target)) return target.withTrackBitrate(maxVideoBitrate);
        return target.withFrameRate(30).withTrackBitrate(maxVideoBitrate);
    }

    private static android.media.MediaCodecInfo.VideoCapabilities getVideoCapabilities(android.media.MediaCodecInfo info, String mimeType) {
        try {
            return info.getCapabilitiesForType(mimeType).getVideoCapabilities();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean supportsSize(android.media.MediaCodecInfo.VideoCapabilities caps, int width, int height) {
        try {
            return caps.isSizeSupported(width, height) || caps.isSizeSupported(height, width);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean supportsRate(android.media.MediaCodecInfo.VideoCapabilities caps, EnhancedVideoProfile profile) {
        try {
            return caps.areSizeAndRateSupported(profile.width(), profile.height(), profile.frameRate()) || caps.areSizeAndRateSupported(profile.height(), profile.width(), profile.frameRate());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean supportsPerformance(android.media.MediaCodecInfo.VideoCapabilities caps, EnhancedVideoProfile profile) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasPerformancePoint(caps, profile);
    }

    private static boolean hasPerformancePoint(android.media.MediaCodecInfo.VideoCapabilities caps, EnhancedVideoProfile profile) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        try {
            android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint target = new android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint(profile.width(), profile.height(), profile.frameRate());
            for (android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint point : caps.getSupportedPerformancePoints()) {
                if (point.covers(target)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static int getSupportedTrackBitrate(android.media.MediaCodecInfo.VideoCapabilities caps, int fallback) {
        try {
            Range<Integer> range = caps.getBitrateRange();
            return range == null ? fallback : Math.max(1_000_000, range.getUpper());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean isHardwareCodec(android.media.MediaCodecInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return info.isHardwareAccelerated();
        String name = info.getName().toLowerCase();
        return !name.contains("google") && !name.contains("android") && !name.contains("ffmpeg") && !name.contains("software") && !name.startsWith("c2.android");
    }

    private static DisplayProfile getDisplayProfile(Context context) {
        int width = ResUtil.getScreenWidth(context);
        int height = ResUtil.getScreenHeight(context);
        int currentWidth = width;
        int currentHeight = height;
        float currentRefreshRate = 0;
        Display display = ResUtil.getDisplay(context);
        if (display != null) {
            Display.Mode mode = display.getMode();
            if (mode != null) {
                currentWidth = Math.max(mode.getPhysicalWidth(), mode.getPhysicalHeight());
                currentHeight = Math.min(mode.getPhysicalWidth(), mode.getPhysicalHeight());
                currentRefreshRate = mode.getRefreshRate();
                width = Math.max(width, Math.max(mode.getPhysicalWidth(), mode.getPhysicalHeight()));
                height = Math.max(height, Math.min(mode.getPhysicalWidth(), mode.getPhysicalHeight()));
            }
            for (Display.Mode supported : display.getSupportedModes()) {
                width = Math.max(width, Math.max(supported.getPhysicalWidth(), supported.getPhysicalHeight()));
                height = Math.max(height, Math.min(supported.getPhysicalWidth(), supported.getPhysicalHeight()));
            }
        }
        return new DisplayProfile(Math.max(width, height), Math.min(width, height), currentWidth, currentHeight, currentRefreshRate);
    }

    private static DisplayProfile getDisplayProfile(ExoPlaybackCapability.DisplayCapability display) {
        return new DisplayProfile(display.maxWidth(), display.maxHeight(), display.currentWidth(), display.currentHeight(), display.currentRefreshRate());
    }

    private static EnhancedVideoProfile logEnhancedVideoProfile(EnhancedVideoProfile profile, DisplayProfile display, CodecVideoProfile codec) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-enhance", "profile=%dx%d@%d networkBitrate=%d trackLimit=%d display=%dx%d codec=%s codecProfile=%s performancePoint=%s", profile.width(), profile.height(), profile.frameRate(), profile.bitrate(), profile.maxVideoBitrate(), display.width(), display.height(), codec.name(), codec.profileText(), codec.performancePoint());
        return profile;
    }

    private static LoadControl buildEnhancedLoadControl() {
        int profile = PlaybackPerformanceSetting.getProfile(PlayerSetting.EXO);
        boolean auto = profile == PlaybackPerformanceSetting.PROFILE_AUTO;
        ExoBufferBudget.Budget budget = getBufferBudget();
        int configuredTargetBytes = PlayerSetting.getBufferBytes(PlayerSetting.EXO);
        int backBufferMs = PlayerSetting.getBackBufferMs(PlayerSetting.EXO);
        if (auto) {
            boolean automaticBufferTime = PlaybackPerformanceSetting.isAuto(
                    PlayerSetting.EXO,
                    PlaybackPerformanceCatalog.BUFFER_TIME);
            boolean automaticBufferBytes = PlaybackPerformanceSetting.isAuto(
                    PlayerSetting.EXO,
                    PlaybackPerformanceCatalog.BUFFER_BYTES);
            boolean automaticBackBuffer = PlaybackPerformanceSetting.isAuto(
                    PlayerSetting.EXO,
                    PlaybackPerformanceCatalog.BACK_BUFFER);
            boolean automaticStartBuffer = PlaybackPerformanceSetting.isAuto(
                    PlayerSetting.EXO,
                    PlaybackPerformanceCatalog.EXO_START_BUFFER);
            boolean automaticRebuffer = PlaybackPerformanceSetting.isAuto(
                    PlayerSetting.EXO,
                    PlaybackPerformanceCatalog.EXO_REBUFFER);
            boolean automaticPrioritizeTime = PlaybackPerformanceSetting.isAuto(
                    PlayerSetting.EXO,
                    PlaybackPerformanceCatalog.EXO_PRIORITIZE_TIME);
            ExoLoadControlPolicy.AutomaticConfiguration defaults =
                    ExoLoadControlPolicy.automatic(
                            ExoPerformanceSetting.getAutoDefaultStartBufferMs());
            ExoLoadControlPolicy.BufferDurations configuredDurations =
                    ExoLoadControlPolicy.resolve(
                            PlaybackPerformanceSetting.PROFILE_CUSTOM,
                            PlayerSetting.getBuffer(PlayerSetting.EXO));
            boolean configuredPrioritizeTime =
                    ExoPerformanceSetting.isPrioritizeTime();
            ExoLoadControlPolicy.AutomaticConfiguration configuration =
                    new ExoLoadControlPolicy.AutomaticConfiguration(
                            automaticBufferTime
                                    ? defaults.streaming() : configuredDurations,
                            automaticBufferTime
                                    ? defaults.local() : configuredDurations,
                            automaticStartBuffer
                                    ? defaults.streamingStartBufferMs()
                                    : ExoPerformanceSetting.getStartBufferMs(),
                            automaticRebuffer
                                    ? defaults.streamingRebufferMs()
                                    : ExoPerformanceSetting.getRebufferMs(),
                            automaticStartBuffer
                                    ? defaults.localStartBufferMs()
                                    : ExoPerformanceSetting.getStartBufferMs(),
                            automaticRebuffer
                                    ? defaults.localRebufferMs()
                                    : ExoPerformanceSetting.getRebufferMs(),
                            automaticPrioritizeTime
                                    ? defaults.streamingPrioritizeTime()
                                    : configuredPrioritizeTime,
                            automaticPrioritizeTime
                                    ? defaults.localPrioritizeTime()
                                    : configuredPrioritizeTime);
            ExoPlaybackDiagnostics.logAutoLoadControl(profile, configuration, budget, backBufferMs);
            AutoTargetLoadControl loadControl = new AutoTargetLoadControl(
                    configuration,
                    backBufferMs,
                    configuredTargetBytes,
                    budget,
                    automaticBufferBytes,
                    automaticBackBuffer);
            return new AutoLoadControl(
                    loadControl,
                    configuration,
                    automaticStartBuffer,
                    automaticRebuffer);
        }
        ExoLoadControlPolicy.BufferDurations durations = getBufferDurations();
        int startBufferMs = ExoPerformanceSetting.getStartBufferMs();
        int rebufferMs = ExoPerformanceSetting.getRebufferMs();
        boolean prioritizeTime = ExoLoadControlPolicy.prioritizeTime(ExoPerformanceSetting.isPrioritizeTime());
        ExoPlaybackDiagnostics.logLoadControl(profile, durations, budget, startBufferMs, rebufferMs, backBufferMs, prioritizeTime, false);
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(durations.minBufferMs(), durations.maxBufferMs(), startBufferMs, rebufferMs)
                .setTargetBufferBytes(budget.effectiveTargetBytes())
                .setBackBuffer(backBufferMs, true)
                .setPrioritizeTimeOverSizeThresholds(prioritizeTime)
                .build();
    }

    private static ExoLoadControlPolicy.BufferDurations getBufferDurations() {
        return ExoLoadControlPolicy.resolve(PlaybackPerformanceSetting.getProfile(PlayerSetting.EXO), PlayerSetting.getBuffer(PlayerSetting.EXO));
    }

    static ExoBufferBudget.Budget getBufferBudget() {
        int configured = PlayerSetting.getBufferBytes(PlayerSetting.EXO);
        int requested = ExoBufferBudget.resolveRequestedTargetBytes(configured);
        return ExoBufferBudget.resolve(App.get(), requested);
    }

    static int getEffectiveTargetBufferBytes() {
        if (!PlaybackPerformanceSetting.isAuto(
                PlayerSetting.EXO,
                PlaybackPerformanceCatalog.BUFFER_BYTES)) {
            return getBufferBudget().effectiveTargetBytes();
        }
        return ExoTargetBufferCoordinator.process().currentTargetBytesOr(
                ExoTargetBufferPolicy.MIN_TARGET_BYTES);
    }

    static DefaultBandwidthMeter buildEnhancedBandwidthMeter(@Nullable Context context) {
        return new DefaultBandwidthMeter.Builder(context)
                .setSlidingWindowMaxWeight(4_000)
                .build();
    }

    static BandwidthMeter buildAutomaticBandwidthMeter(@Nullable Context context) {
        return new ExoPathAwareBandwidthMeter(context);
    }

    private static RenderersFactory buildPlaybackRenderersFactory(
            int decode,
            @Nullable ExoDecoderRuntimeSession decoderRuntimeSession,
            ExoDecoderRuntimeSession.OutputConfig decoderOutput,
            ExoFrameSchedulingPlayerSettings frameSchedulingSettings,
            @Nullable ExoDolbyVisionPlaybackState dolbyVisionPlaybackState) {
        return buildRenderersFactory(
                getAudioRenderMode(),
                getVideoRenderMode(decode),
                isAudioPrefer(decode),
                PlayerSetting.isVideoPrefer(PlayerSetting.EXO),
                decode == PlayerEngine.SOFT
                        && PlaybackPerformanceSetting.isSoftVideoTuneEnabled(),
                decoderRuntimeSession,
                decoderOutput,
                frameSchedulingSettings,
                dolbyVisionPlaybackState);
    }

    static RenderersFactory buildRenderersFactory() {
        int codecQueueMode = ExoPerformanceSetting.getCodecQueueMode();
        boolean dynamicSchedulingEnabled = PlaybackPerformanceSetting
                .isDynamicSchedulingEnabled();
        return buildRenderersFactory(
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER,
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER,
                PlayerSetting.isAudioPrefer(PlayerSetting.EXO),
                PlayerSetting.isVideoPrefer(PlayerSetting.EXO),
                false,
                null,
                ExoDecoderRuntimeSession.OutputConfig.unknown(),
                new ExoFrameSchedulingPlayerSettings(
                        ExoFrameSchedulingExperimentPolicy.stableDecision(
                                PlaybackPerformanceSetting
                                        .isVideoDurationProgressEnabled(),
                                dynamicSchedulingEnabled,
                                codecQueueMode
                                        == ExoPerformanceSetting
                                        .CODEC_QUEUE_SYNC),
                        dynamicSchedulingEnabled,
                        codecQueueMode),
                null);
    }

    private static RenderersFactory buildRenderersFactory(
            int audioRenderMode,
            int videoRenderMode,
            boolean audioPrefer,
            boolean videoPrefer,
            boolean softVideoTune,
            @Nullable ExoDecoderRuntimeSession decoderRuntimeSession,
            ExoDecoderRuntimeSession.OutputConfig decoderOutput,
            ExoFrameSchedulingPlayerSettings frameSchedulingSettings,
            @Nullable ExoDolbyVisionPlaybackState dolbyVisionPlaybackState) {
        ExoFrameSchedulingExperimentPolicy.Decision frameSchedulingDecision =
                frameSchedulingSettings.decision();
        DefaultRenderersFactory factory = new FfmpegRenderersFactory(
                App.get(),
                audioRenderMode,
                videoRenderMode,
                audioPrefer,
                videoPrefer,
                softVideoTune,
                decoderRuntimeSession,
                decoderOutput,
                frameSchedulingDecision,
                dolbyVisionPlaybackState) {
            @Override
            protected AudioSink buildAudioSink(@NonNull Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
                return ExoUtil.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams);
            }
        };
        if (frameSchedulingSettings.codecQueueMode()
                == ExoPerformanceSetting.CODEC_QUEUE_ASYNC) {
            factory.forceEnableMediaCodecAsynchronousQueueing();
        } else if (frameSchedulingSettings.codecQueueMode()
                == ExoPerformanceSetting.CODEC_QUEUE_SYNC) {
            factory.forceDisableMediaCodecAsynchronousQueueing();
        }
        ExoFrameSchedulingRendererSettings.from(frameSchedulingDecision)
                .apply(factory);
        if (PlaybackPerformanceSetting.isLateDropInputEnabled()) factory.experimentalSetLateThresholdToDropDecoderInputUs(ENHANCED_LATE_THRESHOLD_TO_DROP_INPUT_US);
        return factory.setEnableDecoderFallback(PlaybackPerformanceSetting.isDecoderFallbackEnabled()).setExtensionRendererMode(Math.max(audioRenderMode, videoRenderMode));
    }

    public static ExoFrameSchedulingExperimentPolicy.Decision
    resolveFrameSchedulingDecision(int decode) {
        return resolveFrameSchedulingDecision(
                decode,
                PlaybackPerformanceSetting.isDynamicSchedulingEnabled(),
                ExoPerformanceSetting.getCodecQueueMode());
    }

    static ExoFrameSchedulingExperimentPolicy.Decision
    resolveFrameSchedulingDecision(
            int decode,
            boolean dynamicSchedulingEnabled,
            int queueMode) {
        return ExoFrameSchedulingExperimentPolicy.decide(
                new ExoFrameSchedulingExperimentPolicy.Input(
                        PlaybackPerformanceSetting.isAuto(
                                PlayerSetting.EXO,
                                PlaybackPerformanceCatalog.DURATION_PROGRESS),
                        decode == PlayerEngine.HARD,
                        PlaybackExperimentSetting.isAllowed(
                                PlaybackExperimentPolicy.Action
                                        .EXO_FRAME_SCHEDULING_AB),
                        dynamicSchedulingEnabled,
                        queueMode == ExoPerformanceSetting.CODEC_QUEUE_SYNC,
                        PlaybackPerformanceSetting
                                .isVideoDurationProgressEnabled(),
                        ExoFrameSchedulingExperimentSetting.getResolution()));
    }

    private static AudioSink buildAudioSink(Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
        boolean passthrough = PlayerSetting.isAudioPassThrough(PlayerSetting.EXO);
        if (SpiderDebug.isEnabled()) {
            AudioCapabilities capabilities = AudioCapabilities.getCapabilities(
                    context.getApplicationContext(), AudioAttributes.DEFAULT, null);
            int speakerChannels = 0;
            for (Integer channelMask : capabilities.getSpeakerLayoutChannelMasks()) {
                if (channelMask != null) speakerChannels = Math.max(
                        speakerChannels, Integer.bitCount(channelMask));
            }
            SpiderDebug.log("exo-audio",
                    "configured passthrough=%s maxChannels=%d speakerChannels=%d speakerMasks=%s ac3=%s",
                    passthrough,
                    capabilities.getMaxChannelCount(),
                    speakerChannels,
                    capabilities.getSpeakerLayoutChannelMasks(),
                    capabilities.supportsEncoding(C.ENCODING_AC3));
        }
        DefaultAudioSink.Builder builder = new DefaultAudioSink.Builder(context).setEnableFloatOutput(enableFloatOutput).setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams);
        if (!passthrough) builder.setAudioOutputProvider(new AudioTrackAudioOutputProvider.Builder(null).build());
        return builder.build();
    }

    private static MediaSource.Factory buildMediaSourceFactory(
            @Nullable ExoDolbyVisionPlaybackState dolbyVisionPlaybackState) {
        return new MediaSourceFactory(dolbyVisionPlaybackState);
    }

    private static MediaItem.RequestMetadata buildRequestMetadata(PlaySpec spec) {
        return new MediaItem.RequestMetadata.Builder().setMediaUri(spec.getUri()).setExtras(PlayerHelper.toBundle(spec.getHeaders())).build();
    }

    private static List<MediaItem.SubtitleConfiguration> buildSubtitleConfigs(List<Sub> subs) {
        List<MediaItem.SubtitleConfiguration> configs = new ArrayList<>();
        if (subs != null) for (Sub sub : subs) configs.add(buildSubConfig(sub));
        return configs;
    }

    private static MediaItem.SubtitleConfiguration buildSubConfig(Sub sub) {
        return new MediaItem.SubtitleConfiguration.Builder(Uri.parse(UrlUtil.convert(sub.getUrl()))).setLabel(sub.getName()).setMimeType(sub.getFormat()).setSelectionFlags(sub.getFlag()).setLanguage(sub.getLang()).build();
    }

    private static MediaItem.DrmConfiguration buildDrmConfig(Drm drm) {
        return drm == null ? null : new MediaItem.DrmConfiguration.Builder(drm.getUUID()).setMultiSession(!C.CLEARKEY_UUID.equals(drm.getUUID())).setForceDefaultLicenseUri(drm.isForceKey()).setLicenseRequestHeaders(drm.getHeader()).setLicenseUri(drm.getKey()).build();
    }

    private static class FfmpegRenderersFactory extends DefaultRenderersFactory {

        private final int audioRenderMode;
        private final int videoRenderMode;
        private final boolean audioPrefer;
        private final boolean videoPrefer;
        private final boolean softVideoTune;
        @Nullable private final ExoDecoderRuntimeSession decoderRuntimeSession;
        private final ExoDecoderRuntimeSession.OutputConfig decoderOutput;
        private final ExoFrameSchedulingExperimentPolicy.Decision
                frameSchedulingDecision;
        @Nullable private final ExoDolbyVisionPlaybackState
                dolbyVisionPlaybackState;

        FfmpegRenderersFactory(
                Context context,
                int audioRenderMode,
                int videoRenderMode,
                boolean audioPrefer,
                boolean videoPrefer,
                boolean softVideoTune,
                @Nullable ExoDecoderRuntimeSession decoderRuntimeSession,
                ExoDecoderRuntimeSession.OutputConfig decoderOutput,
                ExoFrameSchedulingExperimentPolicy.Decision
                        frameSchedulingDecision,
                @Nullable ExoDolbyVisionPlaybackState
                        dolbyVisionPlaybackState) {
            super(context);
            this.audioRenderMode = audioRenderMode;
            this.videoRenderMode = videoRenderMode;
            this.audioPrefer = audioPrefer;
            this.videoPrefer = videoPrefer;
            this.softVideoTune = softVideoTune;
            this.decoderRuntimeSession = decoderRuntimeSession;
            this.decoderOutput = decoderOutput;
            this.frameSchedulingDecision = frameSchedulingDecision;
            this.dolbyVisionPlaybackState = dolbyVisionPlaybackState;
        }

        @Override
        protected void buildAudioRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, AudioSink audioSink, Handler eventHandler, AudioRendererEventListener eventListener, ArrayList<Renderer> out) {
            super.buildAudioRenderers(context, audioRenderMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, out);
            if (audioRenderMode == EXTENSION_RENDERER_MODE_OFF) return;
            try {
                out.add(getExtensionRendererIndex(audioRenderMode, audioPrefer, out), new CompatFfmpegAudioRenderer(context, eventHandler, eventListener, audioSink, softVideoTune));
            } catch (Throwable ignored) {
            }
        }

        @Override
        protected void buildVideoRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, Handler eventHandler, VideoRendererEventListener eventListener, long allowedVideoJoiningTimeMs, ArrayList<Renderer> out) {
            MediaCodecSelector videoCodecSelector = getVideoCodecSelector(mediaCodecSelector);
            try {
                ExoDv5GpuRenderer dv5Renderer = ExoDv5GpuRendererFactory.create(
                        PlaybackExperimentSetting.isDomainEnabled(
                                PlaybackExperimentPolicy.Domain.EXO),
                        context,
                        getCodecAdapterFactory(),
                        videoCodecSelector,
                        allowedVideoJoiningTimeMs,
                        enableDecoderFallback,
                        eventHandler,
                        eventListener,
                        frameSchedulingDecision);
                if (dv5Renderer != null) out.add(dv5Renderer);
            } catch (Throwable ignored) {
            }
            if (decoderRuntimeSession != null
                    && videoRenderMode == EXTENSION_RENDERER_MODE_OFF) {
                out.add(new ExoRuntimeAwareVideoRenderer(
                        context,
                        getCodecAdapterFactory(),
                        videoCodecSelector,
                        allowedVideoJoiningTimeMs,
                        enableDecoderFallback,
                        eventHandler,
                        eventListener,
                        decoderRuntimeSession,
                        decoderOutput,
                        frameSchedulingDecision));
            } else {
                super.buildVideoRenderers(context, videoRenderMode, videoCodecSelector, enableDecoderFallback, eventHandler, eventListener, allowedVideoJoiningTimeMs, out);
            }
            // Keep the platform DV renderer first; use the HDR10 view only when it
            // cannot claim the DV5/DV7 track. This preserves native DV playback.
            try {
                out.add(new DolbyVisionHdr10FallbackRenderer(
                        context,
                        getCodecAdapterFactory(),
                        videoCodecSelector,
                        allowedVideoJoiningTimeMs,
                        enableDecoderFallback,
                        eventHandler,
                        eventListener,
                        frameSchedulingDecision,
                        PlaybackPerformanceSetting
                        .isDv7FallbackAllowed(),
                        dolbyVisionPlaybackState));
            } catch (Throwable ignored) {
            }
            if (videoRenderMode == EXTENSION_RENDERER_MODE_OFF) return;
            try {
                out.add(getExtensionRendererIndex(videoRenderMode, videoPrefer, out), buildFfmpegVideoRenderer(allowedVideoJoiningTimeMs, eventHandler, eventListener));
            } catch (Throwable ignored) {
            }
        }

        private FfmpegVideoRenderer buildFfmpegVideoRenderer(long allowedVideoJoiningTimeMs, Handler eventHandler, VideoRendererEventListener eventListener) {
            if (!softVideoTune) return new FfmpegVideoRenderer(allowedVideoJoiningTimeMs, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
            return new FfmpegVideoRenderer(allowedVideoJoiningTimeMs, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY, Runtime.getRuntime().availableProcessors(), 4, 4, FFMPEG_SKIP_FRAME_NONREF, FFMPEG_SKIP_LOOP_FILTER_ALL, FFMPEG_LOWRES_HALF);
        }

        private MediaCodecSelector getVideoCodecSelector(MediaCodecSelector mediaCodecSelector) {
            if (videoRenderMode != EXTENSION_RENDERER_MODE_OFF) return mediaCodecSelector;
            return (mimeType, requiresSecureDecoder, requiresTunnelingDecoder) -> {
                List<MediaCodecInfo> infos = mediaCodecSelector.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder);
                if (mimeType == null || !mimeType.startsWith("video/")) return infos;
                List<MediaCodecInfo> hardwareInfos = new ArrayList<>();
                for (MediaCodecInfo info : infos) if (info.hardwareAccelerated) hardwareInfos.add(info);
                return hardwareInfos;
            };
        }

        private int getExtensionRendererIndex(int extensionRendererMode, boolean prefer, ArrayList<Renderer> out) {
            int index = out.size();
            if (index > 0 && (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER || prefer)) index--;
            return index;
        }
    }

    private static final class DolbyVisionHdr10FallbackRenderer extends MediaCodecVideoRenderer {

        private final boolean dv7FallbackEnabled;
        @Nullable private final ExoDolbyVisionPlaybackState playbackState;
        @Nullable private Format pendingSourceFormat;
        @Nullable private Format pendingOutputFormat;

        DolbyVisionHdr10FallbackRenderer(Context context, MediaCodecAdapter.Factory factory, MediaCodecSelector selector, long joiningMs, boolean decoderFallback, Handler handler, VideoRendererEventListener listener, ExoFrameSchedulingExperimentPolicy.Decision frameSchedulingDecision, boolean dv7FallbackEnabled, @Nullable ExoDolbyVisionPlaybackState playbackState) {
            super(ExoFrameSchedulingRendererSettings.from(frameSchedulingDecision)
                    .apply(new Builder(context)
                            .setCodecAdapterFactory(factory)
                            .setMediaCodecSelector(selector)
                            .setAllowedJoiningTimeMs(joiningMs)
                            .setEnableDecoderFallback(decoderFallback)
                            .setEventHandler(handler)
                            .setEventListener(listener)
                            .setMaxDroppedFramesToNotify(
                                    DefaultRenderersFactory
                                            .MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)));
            this.dv7FallbackEnabled = dv7FallbackEnabled;
            this.playbackState = playbackState;
        }

        @Override public String getName() { return "MediaCodecVideoRenderer-DV-HDR10"; }

        @Override
        protected int supportsFormat(MediaCodecSelector selector, Format format) throws androidx.media3.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException {
            if (!shouldUseDolbyVisionHdr10Fallback(format, dv7FallbackEnabled)) return C.FORMAT_UNSUPPORTED_TYPE;
            Format hdr10 = asHdr10(format);
            int support = super.supportsFormat(selector, hdr10);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-dv", "DV HDR10 fallback support=%d codecs=%s size=%dx%d", support, format.codecs, format.width, format.height);
            return support;
        }

        @Override
        protected List<MediaCodecInfo> getDecoderInfos(MediaCodecSelector selector, Format format, boolean secure) throws androidx.media3.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException {
            if (!shouldUseDolbyVisionHdr10Fallback(format, dv7FallbackEnabled)) return List.of();
            return super.getDecoderInfos(selector, asHdr10(format), secure);
        }

        @Override
        protected MediaCodecAdapter.Configuration getMediaCodecConfiguration(MediaCodecInfo info, Format format, MediaCrypto crypto, float rate) {
            if (!shouldUseDolbyVisionHdr10Fallback(format, dv7FallbackEnabled)) {
                return super.getMediaCodecConfiguration(info, format, crypto, rate);
            }
            pendingSourceFormat = format;
            pendingOutputFormat = asHdr10(format);
            return super.getMediaCodecConfiguration(info, pendingOutputFormat, crypto, rate);
        }

        @Override
        protected void onCodecInitialized(String name, MediaCodecAdapter.Configuration configuration, long initializedTimestampMs, long initializationDurationMs) {
            super.onCodecInitialized(name, configuration, initializedTimestampMs, initializationDurationMs);
            if (playbackState != null && pendingSourceFormat != null
                    && pendingOutputFormat != null) {
                playbackState.activate(pendingSourceFormat, pendingOutputFormat);
            }
        }

        @Override
        protected void onDisabled() {
            try {
                super.onDisabled();
            } finally {
                pendingSourceFormat = null;
                pendingOutputFormat = null;
                if (playbackState != null) playbackState.resetAttempt();
            }
        }

        private Format asHdr10(Format format) {
            if (playbackState != null
                    && playbackState.isHdr10FallbackRequested()
                    && DolbyVisionP81ExtractorsFactory.isProfile7(format)) {
                return DolbyVisionP81ExtractorsFactory.asHdr10Fallback(format);
            }
            ColorInfo color = format.colorInfo == null
                    ? new ColorInfo.Builder().setColorSpace(C.COLOR_SPACE_BT2020).setColorRange(C.COLOR_RANGE_LIMITED).setColorTransfer(C.COLOR_TRANSFER_ST2084).build()
                    : format.colorInfo.buildUpon().setColorSpace(C.COLOR_SPACE_BT2020).setColorRange(C.COLOR_RANGE_LIMITED).setColorTransfer(C.COLOR_TRANSFER_ST2084).build();
            return format.buildUpon().setSampleMimeType(MimeTypes.VIDEO_H265).setCodecs(null).setColorInfo(color).build();
        }
    }

    static boolean shouldUseDolbyVisionHdr10Fallback(
            Format format, boolean dv7FallbackEnabled) {
        if (format == null
                || !MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType)
                || format.codecs == null) return false;
        if (ExoDv5GpuMappingPolicy.isProfile5(
                format.sampleMimeType, format.codecs)) {
            ExoDv5GpuMappingPolicy.Decision decision =
                    ExoDv5GpuMappingPolicy.decide(
                            new ExoDv5GpuMappingPolicy.Input(
                                    true,
                                    format.cryptoType != C.CRYPTO_TYPE_NONE,
                                    false,
                                    false,
                                    Build.VERSION.SDK_INT,
                                    false,
                                    false,
                                    false,
                                    false,
                                    false,
                                    false,
                                    true));
            return decision.route()
                    == ExoDv5GpuMappingPolicy.Route.LEGACY_HDR10_FALLBACK;
        }
        String codecs = format.codecs.toLowerCase(java.util.Locale.US);
        return dv7FallbackEnabled && (codecs.startsWith("dvhe.07.")
                || codecs.startsWith("dvh1.07."));
    }

    private static class AutomaticVideoConstraintController implements AnalyticsListener {

        private final DefaultTrackSelector trackSelector;
        private final EnhancedVideoProfile baselineProfile;
        private final ExoAutomaticVideoConstraintPolicy.Limit baselineLimit;
        private final List<ExoAutomaticVideoConstraintPolicy.Limit> tiers;
        private final PlaybackSystemConditionCoordinator.Registration systemConditionRegistration;
        private final PlaybackExperimentCoordinator.Registration experimentRegistration;
        private final Runnable refreshRunnable;
        private PlaybackAutoContext.SessionToken boundSession;
        private ExoAutomaticVideoConstraintPolicy.State constraintState;
        private ExoAutomaticVideoConstraintPolicy.Limit appliedLimit;
        private ExoAutomaticVideoConstraintPolicy.Decision lastDecision;
        private ExoAutomaticVideoConstraintPolicy.Action lastLoggedAction;
        private Format selectedFormat;
        private int playbackState;
        private boolean playing;
        private boolean adaptiveVideo;
        private int selectedVideoCandidates;
        private int availableVideoFormats;
        private volatile boolean released;

        AutomaticVideoConstraintController(
                DefaultTrackSelector trackSelector,
                EnhancedVideoProfile baselineProfile,
                List<EnhancedVideoProfile> profiles) {
            this.trackSelector = trackSelector;
            this.baselineProfile = baselineProfile;
            this.baselineLimit = toLimit(baselineProfile);
            this.tiers = profiles.stream().map(AutomaticVideoConstraintController::toLimit).toList();
            this.boundSession = PlaybackAutoContext.SessionToken.none();
            this.constraintState = ExoAutomaticVideoConstraintPolicy.initial(baselineLimit);
            this.appliedLimit = baselineLimit;
            this.playbackState = Player.STATE_IDLE;
            this.refreshRunnable = () -> refresh("system-condition", -1);
            this.systemConditionRegistration = PlaybackSystemConditionCoordinator.process()
                    .addListener(update -> App.post(refreshRunnable, 0));
            this.experimentRegistration = PlaybackExperimentCoordinator.process()
                    .addListener(update -> App.post(
                            this::onExperimentPolicyChanged, 0));
        }

        @Override
        public void onPlaybackStateChanged(EventTime eventTime, @Player.State int state) {
            playbackState = state;
            refresh("playback-state", eventTime.currentPlaybackPositionMs);
        }

        @Override
        public void onIsPlayingChanged(EventTime eventTime, boolean isPlaying) {
            playing = isPlaying;
            refresh("playing", eventTime.currentPlaybackPositionMs);
        }

        @Override
        public void onTracksChanged(EventTime eventTime, Tracks tracks) {
            bindEventSession();
            TrackShape shape = inspectTracks(tracks);
            adaptiveVideo = shape.adaptiveVideo();
            selectedVideoCandidates = shape.selectedVideoCandidates();
            availableVideoFormats = shape.availableVideoFormats();
            if (shape.selectedFormat() != null) selectedFormat = shape.selectedFormat();
            refresh("tracks", eventTime.currentPlaybackPositionMs);
        }

        @Override
        public void onVideoInputFormatChanged(
                EventTime eventTime,
                Format format,
                @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
            bindEventSession();
            selectedFormat = format;
            refresh("video-format", eventTime.currentPlaybackPositionMs);
            logSelectedTrack(eventTime, format);
        }

        @Override
        public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
            if (droppedFrames < ENHANCED_DROPPED_FRAMES_THRESHOLD
                    && getDroppedFramesPerSecond(droppedFrames, elapsedMs)
                    < ENHANCED_DROPPED_FRAMES_PER_SECOND_THRESHOLD) {
                return;
            }
            applyFault(
                    ExoAutomaticVideoConstraintPolicy.Fault.DROPPED_FRAMES,
                    eventTime.currentPlaybackPositionMs,
                    droppedFrames,
                    elapsedMs,
                    "none");
        }

        @Override
        public void onVideoCodecError(EventTime eventTime, Exception videoCodecError) {
            applyFault(
                    ExoAutomaticVideoConstraintPolicy.Fault.CODEC_ERROR,
                    eventTime.currentPlaybackPositionMs,
                    0,
                    0,
                    videoCodecError == null ? "unknown" : videoCodecError.getClass().getSimpleName());
        }

        @Override
        public void onEvents(Player player, AnalyticsListener.Events events) {
            playbackState = player.getPlaybackState();
            playing = player.isPlaying();
            refresh("events", player.getCurrentPosition());
        }

        @Override
        public void onPlayerReleased(EventTime eventTime) {
            released = true;
            App.removeCallbacks(refreshRunnable);
            systemConditionRegistration.close();
            experimentRegistration.close();
        }

        private void refresh(String trigger, long positionMs) {
            if (released) return;
            long now = android.os.SystemClock.elapsedRealtime();
            ExoAutomaticVideoConstraintPolicy.Input input = buildInput(now);
            if (input == null) return;
            acceptDecision(
                    ExoAutomaticVideoConstraintPolicy.evaluate(constraintState, input),
                    trigger,
                    positionMs,
                    0,
                    0,
                    "none");
        }

        private void applyFault(
                ExoAutomaticVideoConstraintPolicy.Fault fault,
                long positionMs,
                int droppedFrames,
                long elapsedMs,
                String errorType) {
            if (released) return;
            long now = android.os.SystemClock.elapsedRealtime();
            ExoAutomaticVideoConstraintPolicy.Input input = buildInput(now);
            if (input == null) return;
            acceptDecision(
                    ExoAutomaticVideoConstraintPolicy.onFault(constraintState, input, fault),
                    "decode-fault",
                    positionMs,
                    droppedFrames,
                    elapsedMs,
                    safeErrorType(errorType));
        }

        @Nullable
        private ExoAutomaticVideoConstraintPolicy.Input buildInput(long now) {
            PlaybackAutoContext context = currentExoContext(now);
            if (context == null) return null;
            if (!context.session().equals(boundSession)) bindSession(context.session());
            ExoAutomaticVideoConstraintPolicy.Environment environment =
                    ExoAutomaticVideoConstraintPolicy.environment(context, now);
            return new ExoAutomaticVideoConstraintPolicy.Input(
                    baselineLimit,
                    tiers,
                    environment,
                    playbackState == Player.STATE_READY && playing,
                    selectedTrack(selectedFormat),
                    resourceMode(context, now),
                    now);
        }

        private void bindSession(PlaybackAutoContext.SessionToken session) {
            boundSession = session;
            constraintState = ExoAutomaticVideoConstraintPolicy.initial(baselineLimit);
            lastDecision = null;
            lastLoggedAction = null;
            selectedFormat = null;
            adaptiveVideo = false;
            selectedVideoCandidates = 0;
            availableVideoFormats = 0;
            apply(baselineLimit);
        }

        private void bindEventSession() {
            PlaybackAutoContext context = currentExoContext(
                    android.os.SystemClock.elapsedRealtime());
            if (context == null || context.session().equals(boundSession)) return;
            bindSession(context.session());
        }

        @Nullable
        private PlaybackAutoContext currentExoContext(long now) {
            PlaybackAutoContext context = PlaybackAutoContextStore.process().snapshot();
            if (!context.active()
                    || !context.session().traceId().equals(
                            PlaybackAnalyticsListener.getPlaybackTraceId())) {
                return null;
            }
            if (context.kernel().isUsable(now)
                    && context.kernel().value() != PlaybackAutoContext.Kernel.EXO) {
                return null;
            }
            return context;
        }

        private void acceptDecision(
                ExoAutomaticVideoConstraintPolicy.Decision decision,
                String trigger,
                long positionMs,
                int droppedFrames,
                long elapsedMs,
                String errorType) {
            if (!PlaybackExperimentSetting.isAllowed(
                    PlaybackExperimentPolicy.Action.EXO_VIDEO_CONSTRAINT)) {
                applyStableExperimentPolicy();
                return;
            }
            constraintState = decision.state();
            lastDecision = decision;
            if (decision.changed()) apply(decision.state().effective());
            boolean shouldLog = decision.changed()
                    || decision.faultSignal() != ExoAutomaticVideoConstraintPolicy.Fault.NONE
                    || decision.action() != lastLoggedAction;
            if (shouldLog) {
                lastLoggedAction = decision.action();
                SpiderDebug.log("exo-enhance", "automatic constraint trigger=%s action=%s mode=%s reasons=%s baseline=%s target=%s effective=%s adaptiveVideo=%s selectedVideoCandidates=%d availableVideoFormats=%d stable=%s thermal=%s power=%s networkCost=%s dataSaver=%s costScope=%s decode=%s fault=%s faultActive=%s faultStepped=%s droppedFrames=%d elapsedMs=%d errorType=%s position=%d",
                        trigger,
                        decision.action().label(),
                        decision.resourceMode().label(),
                        decision.environment().reasonLabel(),
                        baselineLimit.label(),
                        decision.rawTarget().label(),
                        decision.state().effective().label(),
                        adaptiveVideo,
                        selectedVideoCandidates,
                        availableVideoFormats,
                        playbackState == Player.STATE_READY && playing,
                        decision.environment().thermal().label(),
                        decision.environment().power().label(),
                        decision.environment().networkCost().label(),
                        decision.environment().dataSaver().label(),
                        decision.environment().costScope().label(),
                        decision.environment().decodeMode().label(),
                        decision.faultSignal().label(),
                        decision.faultActive(),
                        decision.faultStepped(),
                        droppedFrames,
                        elapsedMs,
                        errorType,
                        positionMs);
            }
            if (decision.reevaluateAfterMs() > 0) {
                App.post(refreshRunnable, decision.reevaluateAfterMs());
            } else {
                App.removeCallbacks(refreshRunnable);
            }
        }

        private void onExperimentPolicyChanged() {
            if (released) return;
            if (!PlaybackExperimentSetting.isAllowed(
                    PlaybackExperimentPolicy.Action.EXO_VIDEO_CONSTRAINT)) {
                applyStableExperimentPolicy();
                return;
            }
            refresh("experiment-enabled", -1);
        }

        private void applyStableExperimentPolicy() {
            App.removeCallbacks(refreshRunnable);
            constraintState = ExoAutomaticVideoConstraintPolicy.initial(
                    baselineLimit);
            lastDecision = null;
            lastLoggedAction = null;
            apply(baselineLimit);
        }

        private void apply(ExoAutomaticVideoConstraintPolicy.Limit limit) {
            if (limit.equals(appliedLimit)) return;
            DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters();
            applyVideoLimit(builder, limit);
            trackSelector.setParameters(builder.build());
            appliedLimit = limit;
        }

        private void logSelectedTrack(EventTime eventTime, Format format) {
            ExoAutomaticVideoConstraintPolicy.Decision decision = lastDecision;
            ExoAutomaticVideoConstraintPolicy.Limit effective = appliedLimit;
            int selectedBitrate = ExoPlaybackDiagnostics.trackConstraintBitrate(format);
            ExoAdaptiveVideoBitratePolicy.SelectionCheck check =
                    ExoAdaptiveVideoBitratePolicy.checkSelectedTrack(
                            effective.maxVideoBitrate(), selectedBitrate);
            SpiderDebug.log("exo-enhance", "automatic selected mode=%s reasons=%s baseline=%s effective=%s baselineNominalBitrate=%d selected=%dx%d@%.3f selectedBitrate=%d selectedBitrateSource=%s averageBitrate=%d peakBitrate=%d capStatus=%s exceedAllowed=true adaptiveVideo=%s selectedVideoCandidates=%d availableVideoFormats=%d position=%d",
                    decision == null ? ExoAutomaticVideoConstraintPolicy.ResourceMode.UNKNOWN.label()
                            : decision.resourceMode().label(),
                    decision == null ? "baseline" : decision.environment().reasonLabel(),
                    baselineLimit.label(),
                    effective.label(),
                    baselineProfile.bitrate(),
                    format.width,
                    format.height,
                    format.frameRate,
                    check.selectedBitrate(),
                    ExoPlaybackDiagnostics.trackConstraintBitrateSource(format),
                    Math.max(0, format.averageBitrate),
                    Math.max(0, format.peakBitrate),
                    check.status().label(),
                    adaptiveVideo,
                    selectedVideoCandidates,
                    availableVideoFormats,
                    eventTime.currentPlaybackPositionMs);
        }

        private ExoAutomaticVideoConstraintPolicy.ResourceMode resourceMode(
                PlaybackAutoContext context,
                long now) {
            PlaybackAutoContext.Protocol protocol = context.resource().protocol().isUsable(now)
                    ? context.resource().protocol().value() : PlaybackAutoContext.Protocol.UNKNOWN;
            boolean manifestAdaptive = false;
            PlaybackAutoContext.Fact<PlaybackAutoContext.ManifestFacts> manifest =
                    context.resource().manifest();
            if (manifest.isUsable(now)) {
                Integer variants = manifest.value().variantCount();
                manifestAdaptive = variants != null && variants > 1;
            }
            if (adaptiveVideo || manifestAdaptive) {
                return ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS;
            }
            if (protocol == PlaybackAutoContext.Protocol.HLS
                    || protocol == PlaybackAutoContext.Protocol.DASH) {
                return ExoAutomaticVideoConstraintPolicy.ResourceMode.SEGMENTED_SINGLE;
            }
            if (protocol == PlaybackAutoContext.Protocol.PROGRESSIVE_HTTP) {
                return ExoAutomaticVideoConstraintPolicy.ResourceMode.PROGRESSIVE_SINGLE;
            }
            if (protocol == PlaybackAutoContext.Protocol.UNKNOWN) {
                return ExoAutomaticVideoConstraintPolicy.ResourceMode.UNKNOWN;
            }
            return ExoAutomaticVideoConstraintPolicy.ResourceMode.OTHER_SINGLE;
        }

        private static ExoAutomaticVideoConstraintPolicy.SelectedTrack selectedTrack(
                @Nullable Format format) {
            if (format == null) return ExoAutomaticVideoConstraintPolicy.SelectedTrack.unknown();
            return new ExoAutomaticVideoConstraintPolicy.SelectedTrack(
                    format.width,
                    format.height,
                    format.frameRate > 0 && Float.isFinite(format.frameRate)
                            ? Math.round(format.frameRate) : 0,
                    ExoPlaybackDiagnostics.trackConstraintBitrate(format));
        }

        private static TrackShape inspectTracks(@Nullable Tracks tracks) {
            if (tracks == null || tracks.isEmpty()) return TrackShape.unknown();
            boolean adaptive = false;
            int selectedCandidates = 0;
            int availableFormats = 0;
            Format selected = null;
            for (Tracks.Group group : tracks.getGroups()) {
                if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
                availableFormats = safeAdd(availableFormats, group.length);
                if (!group.isSelected()) continue;
                selectedCandidates = Math.max(selectedCandidates, group.length);
                adaptive |= group.length > 1 && group.isAdaptiveSupported();
                for (int i = 0; i < group.length; i++) {
                    if (group.isTrackSelected(i)) {
                        selected = group.getTrackFormat(i);
                        break;
                    }
                }
            }
            return new TrackShape(adaptive, selectedCandidates, availableFormats, selected);
        }

        private static int safeAdd(int first, int second) {
            return first > Integer.MAX_VALUE - second ? Integer.MAX_VALUE : first + second;
        }

        private static int getDroppedFramesPerSecond(int droppedFrames, long elapsedMs) {
            if (elapsedMs <= 0) return droppedFrames;
            return (int) (droppedFrames * 1000L / elapsedMs);
        }

        private static String safeErrorType(String value) {
            if (value == null || value.isBlank() || value.length() > 96) return "unknown";
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-') continue;
                return "unknown";
            }
            return value;
        }

        private static ExoAutomaticVideoConstraintPolicy.Limit toLimit(
                EnhancedVideoProfile profile) {
            return new ExoAutomaticVideoConstraintPolicy.Limit(
                    profile.width(), profile.height(), profile.frameRate(), profile.maxVideoBitrate());
        }

        private record TrackShape(
                boolean adaptiveVideo,
                int selectedVideoCandidates,
                int availableVideoFormats,
                @Nullable Format selectedFormat) {

            private static TrackShape unknown() {
                return new TrackShape(false, 0, 0, null);
            }
        }
    }

    private static class LegacyAdaptiveVideoProfileController implements AnalyticsListener {

        private final DefaultTrackSelector trackSelector;
        private final List<EnhancedVideoProfile> profiles;
        private EnhancedVideoProfile profile;
        private int profileIndex;
        private boolean everReady;
        private long lastAdaptMs;

        LegacyAdaptiveVideoProfileController(DefaultTrackSelector trackSelector, EnhancedVideoProfile profile, List<EnhancedVideoProfile> profiles) {
            this.trackSelector = trackSelector;
            this.profiles = profiles;
            this.profile = profile;
            this.profileIndex = getProfileIndex(profile);
        }

        @Override
        public void onPlaybackStateChanged(EventTime eventTime, @Player.State int state) {
            if (state == Player.STATE_READY) {
                everReady = true;
            } else if (state == Player.STATE_BUFFERING && everReady) {
                maybeDowngrade("rebuffer", eventTime, 0);
            }
        }

        @Override
        public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
            if (droppedFrames < ENHANCED_DROPPED_FRAMES_THRESHOLD && getDroppedFramesPerSecond(droppedFrames, elapsedMs) < ENHANCED_DROPPED_FRAMES_PER_SECOND_THRESHOLD) return;
            maybeDowngrade("droppedFrames=" + droppedFrames + "/" + elapsedMs + "ms", eventTime, 0);
        }

        @Override
        public void onBandwidthEstimate(EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
            if (!ExoAdaptiveVideoBitratePolicy.shouldDowngrade(profile.bitrate(), bitrateEstimate)) return;
            maybeDowngrade("bandwidth=" + bitrateEstimate, eventTime, bitrateEstimate);
        }

        @Override
        public void onVideoInputFormatChanged(EventTime eventTime, Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
            int selectedBitrate = ExoPlaybackDiagnostics.trackConstraintBitrate(format);
            ExoAdaptiveVideoBitratePolicy.SelectionCheck check = ExoAdaptiveVideoBitratePolicy.checkSelectedTrack(profile.maxVideoBitrate(), selectedBitrate);
            SpiderDebug.log("exo-enhance", "adaptive selected profile=%dx%d@%d profileBitrate=%d requestedCap=%d selected=%dx%d@%.3f selectedBitrate=%d selectedBitrateSource=%s averageBitrate=%d peakBitrate=%d capStatus=%s exceedAllowed=true position=%d",
                    profile.width(), profile.height(), profile.frameRate(), profile.bitrate(), check.requestedCap(), format.width, format.height, format.frameRate,
                    check.selectedBitrate(), ExoPlaybackDiagnostics.trackConstraintBitrateSource(format), Math.max(0, format.averageBitrate), Math.max(0, format.peakBitrate), check.status().label(), eventTime.currentPlaybackPositionMs);
        }

        private int getDroppedFramesPerSecond(int droppedFrames, long elapsedMs) {
            if (elapsedMs <= 0) return droppedFrames;
            return (int) (droppedFrames * 1000L / elapsedMs);
        }

        private void maybeDowngrade(String reason, EventTime eventTime, long bitrateEstimate) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (profileIndex >= profiles.size() - 1 || now - lastAdaptMs < ENHANCED_ADAPT_COOLDOWN_MS) return;
            EnhancedVideoProfile next = profiles.get(++profileIndex);
            if (bitrateEstimate > 0) next = next.withBandwidthCap(bitrateEstimate);
            apply(next);
            lastAdaptMs = now;
            SpiderDebug.log("exo-enhance", "adaptive downgrade reason=%s profile=%dx%d@%d profileBitrate=%d requestedCap=%d position=%d", reason, next.width(), next.height(), next.frameRate(), next.bitrate(), next.maxVideoBitrate(), eventTime.currentPlaybackPositionMs);
        }

        private void apply(EnhancedVideoProfile profile) {
            this.profile = profile;
            DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters();
            applyEnhancedVideoProfile(builder, profile);
            trackSelector.setParameters(builder.build());
        }

        private int getProfileIndex(EnhancedVideoProfile profile) {
            for (int i = 0; i < profiles.size(); i++) {
                EnhancedVideoProfile target = profiles.get(i);
                if (profile.width() >= target.width() && profile.height() >= target.height()) return i;
            }
            return profiles.size() - 1;
        }
    }

    public record EnhancedVideoProfile(int width, int height, int bitrate, int frameRate, int maxVideoBitrate) {

        public EnhancedVideoProfile(int width, int height, int bitrate, int frameRate) {
            this(width, height, bitrate, frameRate, bitrate);
        }

        private static List<EnhancedVideoProfile> targets() {
            return List.of(
                    new EnhancedVideoProfile(3840, 2160, 20_000_000, 60),
                    new EnhancedVideoProfile(2560, 1440, 12_000_000, 60),
                    new EnhancedVideoProfile(1920, 1080, 8_000_000, 60),
                    new EnhancedVideoProfile(1280, 720, 4_000_000, 30),
                    low()
            );
        }

        private static List<EnhancedVideoProfile> softTargets() {
            return List.of(
                    new EnhancedVideoProfile(1920, 1080, 6_000_000, 30),
                    new EnhancedVideoProfile(1280, 720, 3_000_000, 30),
                    low()
            );
        }

        private static EnhancedVideoProfile low() {
            return new EnhancedVideoProfile(854, 480, 1_500_000, 30);
        }

        private EnhancedVideoProfile withFrameRate(int frameRate) {
            return new EnhancedVideoProfile(width, height, bitrate, frameRate, maxVideoBitrate);
        }

        EnhancedVideoProfile withBandwidthCap(long bitrateEstimate) {
            return withTrackBitrate(ExoAdaptiveVideoBitratePolicy.resolveTrackBitrateCap(maxVideoBitrate, bitrateEstimate));
        }

        private EnhancedVideoProfile withTrackBitrate(int maxVideoBitrate) {
            return new EnhancedVideoProfile(width, height, bitrate, frameRate, maxVideoBitrate);
        }
    }

    private record DisplayProfile(int width, int height, int currentWidth, int currentHeight, float currentRefreshRate) {

        private boolean supports(EnhancedVideoProfile profile) {
            return width >= profile.width() && height >= profile.height();
        }
    }

    private record CodecVideoProfile(String name, EnhancedVideoProfile profile, boolean performancePoint) implements Comparable<CodecVideoProfile> {

        private static CodecVideoProfile unsupported() {
            return new CodecVideoProfile("none", new EnhancedVideoProfile(0, 0, 0, 0), false);
        }

        private boolean supported() {
            return !"none".equals(name);
        }

        private String profileText() {
            return supported() ? profile.width() + "x" + profile.height() + "@" + profile.frameRate() : "unsupported";
        }

        @Override
        public int compareTo(CodecVideoProfile other) {
            int pixels = Integer.compare(profile.width() * profile.height(), other.profile.width() * other.profile.height());
            if (pixels != 0) return pixels;
            int frameRate = Integer.compare(profile.frameRate(), other.profile.frameRate());
            if (frameRate != 0) return frameRate;
            int bitrate = Integer.compare(profile.bitrate(), other.profile.bitrate());
            if (bitrate != 0) return bitrate;
            return Boolean.compare(performancePoint, other.performancePoint);
        }
    }
}
