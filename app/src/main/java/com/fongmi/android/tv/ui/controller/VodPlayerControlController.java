package com.fongmi.android.tv.ui.controller;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerSetting;

public class VodPlayerControlController {

    private final Host host;

    public VodPlayerControlController(Host host) {
        this.host = host;
    }

    private PlayerManager player() {
        return host.player();
    }

    private boolean hasPlayer() {
        return player() != null && !player().isEmpty();
    }

    public boolean hasDanmakuControl() {
        return player() != null && (DanmakuSetting.isLoad() || player().haveDanmaku());
    }

    public void applyDanmakuSetting() {
        if (player() != null) player().setDanmakuEnabled(DanmakuSetting.isShow());
        updateDanmakuState();
    }

    public void disableDanmaku() {
        if (player() != null) player().setDanmakuEnabled(false);
    }

    public void updateDanmakuState() {
        host.onDanmakuStateChanged(DanmakuSetting.isShow());
    }

    public void onDanmakuButton() {
        if (player() == null) return;
        if (!player().haveDanmaku()) {
            host.showDanmakuDialog();
            return;
        }
        DanmakuSetting.putShow(!DanmakuSetting.isShow());
        applyDanmakuSetting();
    }

    public boolean showPlayerInfo() {
        if (!hasPlayer()) return false;
        host.showPlayerInfoDialog();
        return true;
    }

    public void updateSize(TextView view, boolean visibleArea) {
        String size = hasPlayer() ? player().getSizeText() : "";
        view.setText(size);
        view.setVisibility(visibleArea && PlayerSetting.isDisplaySize() && !TextUtils.isEmpty(size) ? View.VISIBLE : View.GONE);
    }

    public interface Host {

        PlayerManager player();

        void showDanmakuDialog();

        void showPlayerInfoDialog();

        void onDanmakuStateChanged(boolean show);
    }
}
