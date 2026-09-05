package com.fongmi.android.tv.ui.player;

import android.view.View;
import android.widget.TextView;

import com.fongmi.android.tv.databinding.ActivityTmdbDetailBinding;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.ui.custom.MiniProgressView;

public final class VodPlayerChrome {

    private static final float DEFAULT_OSD_MINI_SP = 14f;

    final View osdRoot;
    final TextView osdTopLeft;
    final TextView osdTopRight;
    final TextView osdBottomLeft;
    final TextView osdBottomRight;
    final TextView osdDiagnostics;
    final MiniProgressView osdMiniProgress;
    final View playParams;
    final TextView clockView;
    final float osdMiniSp;
    final View prev;
    final View next;
    final View quality;
    final View parse;
    final View speed;
    final View scale;
    final View lut;
    final View refresh;
    final View changeSource;
    final View repeat;
    final View display;
    final View decode;
    final View textTrack;
    final View audioTrack;
    final View videoTrack;
    final View opening;
    final View ending;
    final View danmakuToggle;
    final View danmaku;
    final View chapter;
    final View external;
    final View episodes;
    final View fullscreen;
    final View cast;
    final View info;
    final View controls;
    final View codecCapability;

    public VodPlayerChrome(
            View osdRoot,
            TextView osdTopLeft,
            TextView osdTopRight,
            TextView osdBottomLeft,
            TextView osdBottomRight,
            TextView osdDiagnostics,
            MiniProgressView osdMiniProgress,
            View playParams,
            TextView clockView,
            float osdMiniSp) {
        this(
                osdRoot,
                osdTopLeft,
                osdTopRight,
                osdBottomLeft,
                osdBottomRight,
                osdDiagnostics,
                osdMiniProgress,
                playParams,
                clockView,
                osdMiniSp,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public VodPlayerChrome(
            View osdRoot,
            TextView osdTopLeft,
            TextView osdTopRight,
            TextView osdBottomLeft,
            TextView osdBottomRight,
            TextView osdDiagnostics,
            MiniProgressView osdMiniProgress,
            View playParams,
            View prev,
            View next,
            View quality,
            View parse,
            View speed,
            View scale,
            View lut,
            View refresh,
            View changeSource,
            View repeat,
            View display,
            View decode,
            View textTrack,
            View audioTrack,
            View videoTrack,
            View opening,
            View ending,
            View danmakuToggle,
            View danmaku,
            View chapter,
            View external,
            View episodes,
            View fullscreen,
            View cast,
            View info,
            View controls,
            View codecCapability) {
        this(
                osdRoot,
                osdTopLeft,
                osdTopRight,
                osdBottomLeft,
                osdBottomRight,
                osdDiagnostics,
                osdMiniProgress,
                playParams,
                null,
                DEFAULT_OSD_MINI_SP,
                prev,
                next,
                quality,
                parse,
                speed,
                scale,
                lut,
                refresh,
                changeSource,
                repeat,
                display,
                decode,
                textTrack,
                audioTrack,
                videoTrack,
                opening,
                ending,
                danmakuToggle,
                danmaku,
                chapter,
                external,
                episodes,
                fullscreen,
                cast,
                info,
                controls,
                codecCapability);
    }

    private VodPlayerChrome(
            View osdRoot,
            TextView osdTopLeft,
            TextView osdTopRight,
            TextView osdBottomLeft,
            TextView osdBottomRight,
            TextView osdDiagnostics,
            MiniProgressView osdMiniProgress,
            View playParams,
            TextView clockView,
            float osdMiniSp,
            View prev,
            View next,
            View quality,
            View parse,
            View speed,
            View scale,
            View lut,
            View refresh,
            View changeSource,
            View repeat,
            View display,
            View decode,
            View textTrack,
            View audioTrack,
            View videoTrack,
            View opening,
            View ending,
            View danmakuToggle,
            View danmaku,
            View chapter,
            View external,
            View episodes,
            View fullscreen,
            View cast,
            View info,
            View controls,
            View codecCapability) {
        this.osdRoot = osdRoot;
        this.osdTopLeft = osdTopLeft;
        this.osdTopRight = osdTopRight;
        this.osdBottomLeft = osdBottomLeft;
        this.osdBottomRight = osdBottomRight;
        this.osdDiagnostics = osdDiagnostics;
        this.osdMiniProgress = osdMiniProgress;
        this.playParams = playParams;
        this.clockView = clockView;
        this.osdMiniSp = osdMiniSp;
        this.prev = prev;
        this.next = next;
        this.quality = quality;
        this.parse = parse;
        this.speed = speed;
        this.scale = scale;
        this.lut = lut;
        this.refresh = refresh;
        this.changeSource = changeSource;
        this.repeat = repeat;
        this.display = display;
        this.decode = decode;
        this.textTrack = textTrack;
        this.audioTrack = audioTrack;
        this.videoTrack = videoTrack;
        this.opening = opening;
        this.ending = ending;
        this.danmakuToggle = danmakuToggle;
        this.danmaku = danmaku;
        this.chapter = chapter;
        this.external = external;
        this.episodes = episodes;
        this.fullscreen = fullscreen;
        this.cast = cast;
        this.info = info;
        this.controls = controls;
        this.codecCapability = codecCapability;
    }

    public static VodPlayerChrome fromTmdbDetail(ActivityTmdbDetailBinding binding) {
        return new VodPlayerChrome(
                binding.osd.getRoot(),
                binding.osd.osdTopLeft,
                binding.osd.osdTopRight,
                binding.osd.osdBottomLeft,
                binding.osd.osdBottomRight,
                binding.osd.osdDiagnostics,
                binding.osd.osdMiniProgress,
                binding.playerPlayParams,
                binding.playerPrev,
                binding.playerNext,
                binding.playerQuality,
                binding.playerParse,
                binding.playerSpeed,
                binding.playerScale,
                binding.playerLut,
                binding.playerRefresh,
                binding.playerChangeSource,
                binding.playerRepeat,
                binding.playerDisplay,
                binding.playerDecode,
                binding.playerTextTrack,
                binding.playerAudioTrack,
                binding.playerVideoTrack,
                binding.playerOpening,
                binding.playerEnding,
                binding.playerDanmakuToggle,
                binding.playerDanmaku,
                binding.playerChapter,
                binding.playerExternal,
                binding.playerEpisodes,
                binding.playerFullscreenAction,
                binding.playerCast,
                binding.playerInfo,
                binding.playerControls,
                binding.playerCodecCapability);
    }

    public static VodPlayerChrome fromVideo(ActivityVideoBinding binding, TextView clockView, float osdMiniSp) {
        return new VodPlayerChrome(
                binding.osd.getRoot(),
                binding.osd.osdTopLeft,
                binding.osd.osdTopRight,
                binding.osd.osdBottomLeft,
                binding.osd.osdBottomRight,
                binding.osd.osdDiagnostics,
                binding.osd.osdMiniProgress,
                null,
                clockView,
                osdMiniSp);
    }
}
