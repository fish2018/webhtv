package com.fongmi.android.tv.ui.player;

import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.controller.VodPlayerControlController;
import com.fongmi.android.tv.ui.custom.PlayerOsdController;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.PiP;

public class VodPlayerUiController {

    private final VodPlayerControlController controlController;
    private final PlayerOsdController osd;
    private final Clock clock;
    private final PiP pip;
    private final VodPlayerUiHost host;
    private final VodPlayerChrome chrome;

    public VodPlayerUiController(VodPlayerUiHost host, VodPlayerChrome chrome, Clock.Callback clockCallback) {
        this.host = host;
        this.chrome = chrome;
        this.controlController = new VodPlayerControlController(new VodPlayerControlController.Host() {
            @Override
            public PlayerManager player() {
                return host.player();
            }

            @Override
            public void showDanmakuDialog() {
                host.showDanmakuDialog();
            }

            @Override
            public void showPlayerInfoDialog() {
                host.showPlayerInfoDialog();
            }

            @Override
            public void onDanmakuStateChanged(boolean show) {
                host.onDanmakuStateChanged(show);
            }
        });
        this.pip = new PiP();
        this.clock = chrome.clockView == null ? Clock.create() : Clock.create(chrome.clockView);
        if (clockCallback != null) this.clock.setCallback(clockCallback);
        this.clock.start();
        this.osd = new PlayerOsdController(
                chrome.osdRoot,
                chrome.osdTopLeft,
                chrome.osdTopRight,
                chrome.osdBottomLeft,
                chrome.osdBottomRight,
                chrome.osdDiagnostics,
                chrome.osdMiniProgress,
                new PlayerOsdController.Source() {
                    @Override
                    public PlayerManager getPlayer() {
                        return host.player();
                    }

                    @Override
                    public String getTitle() {
                        return host.osdTitle();
                    }
                },
                chrome.osdMiniSp
        );
        this.osd.setPersistentSuppressed(host.suppressPersistentOsd());
    }

    public void bindInlineActions() {
        chrome.prev.setOnClickListener(view -> host.playPrevious());
        chrome.next.setOnClickListener(view -> host.playNext());
        chrome.quality.setOnClickListener(view -> host.showQuality());
        chrome.parse.setOnClickListener(view -> host.cycleParse());
        chrome.speed.setOnClickListener(view -> host.changeSpeed());
        chrome.speed.setOnLongClickListener(view -> host.resetSpeed());
        chrome.scale.setOnClickListener(view -> host.cycleScale());
        chrome.lut.setOnClickListener(view -> host.showLut());
        chrome.refresh.setOnClickListener(view -> host.refreshPlayback());
        chrome.changeSource.setOnClickListener(view -> host.changeSource());
        chrome.changeSource.setOnLongClickListener(view -> host.openSourceSearch());
        chrome.repeat.setOnClickListener(view -> host.toggleRepeat());
        chrome.display.setOnClickListener(view -> host.showDisplay());
        chrome.decode.setOnClickListener(view -> host.toggleDecode());
        chrome.playParams.setOnClickListener(view -> host.togglePlayParams());
        if (chrome.codecCapability != null) chrome.codecCapability.setOnClickListener(view -> host.showCodecCapability());
        chrome.textTrack.setOnClickListener(host::showTrack);
        chrome.textTrack.setOnLongClickListener(view -> host.showSubtitle());
        chrome.audioTrack.setOnClickListener(host::showTrack);
        chrome.videoTrack.setOnClickListener(host::showTrack);
        chrome.opening.setOnClickListener(view -> host.setOpeningFromPosition());
        chrome.opening.setOnLongClickListener(view -> host.resetOpening());
        chrome.ending.setOnClickListener(view -> host.setEndingFromPosition());
        chrome.ending.setOnLongClickListener(view -> host.resetEnding());
        chrome.danmakuToggle.setOnClickListener(view -> host.toggleDanmaku());
        chrome.danmaku.setOnClickListener(view -> host.showDanmakuDialog());
        chrome.danmaku.setOnLongClickListener(view -> host.onDanmakuLongClick());
        chrome.chapter.setOnClickListener(view -> host.showChapter());
        chrome.external.setOnClickListener(view -> host.showPlayerChoice());
        chrome.external.setOnLongClickListener(view -> host.showPlayerChoice());
        chrome.episodes.setOnClickListener(view -> host.showEpisodes());
        chrome.fullscreen.setOnClickListener(view -> host.toggleFullscreen());
        chrome.cast.setOnClickListener(view -> host.cast());
        chrome.info.setOnClickListener(view -> host.showMediaInfo());
        chrome.controls.setOnTouchListener(host::onControlsTouch);
    }

    public VodPlayerControlController controlController() {
        return controlController;
    }

    public PlayerOsdController osd() {
        return osd;
    }

    public Clock clock() {
        return clock;
    }

    public PiP pip() {
        return pip;
    }

    public void onStart() {
        if (host.restoreDiagnosticsOnStart()) osd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());
        if (chrome.playParams != null) chrome.playParams.setSelected(osd.isDiagnosticsVisible());
        osd.start();
    }

    public void onStop() {
        osd.stop();
    }

    public void release() {
        clock.release();
        osd.release();
    }
}
