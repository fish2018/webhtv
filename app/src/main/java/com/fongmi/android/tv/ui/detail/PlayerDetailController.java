package com.fongmi.android.tv.ui.detail;

import android.view.View;

import com.fongmi.android.tv.databinding.ActivityTmdbDetailBinding;

/**
 * 详情直放模式 Controller。
 * <p>
 * 特点：
 * - 不显示内联播放器（初始）
 * - 由用户点击播放后进入全屏
 * - detailActions 可见，fusionActions 隐藏
 * - heroSpacer 可见
 */
public class PlayerDetailController extends BaseTmdbDetailModeController {

    public PlayerDetailController(DetailModeHost host) {
        super(host);
    }

    @Override
    public boolean isPlayerMode() {
        return true;
    }

    @Override
    protected boolean showInlinePlayer() {
        return false;
    }

    @Override
    protected boolean autoPlay() {
        return false; // 详情直放必须由用户点击播放
    }

    @Override
    protected int heroSpacerVisibility() {
        return View.VISIBLE;
    }

    @Override
    public boolean shouldBindPlaybackService() {
        return true;
    }


    @Override
    public void onExitFullscreen() {
        host.closeDetailFullscreenPlayer();
    }

    @Override
    public void applyInitialLayout() {
        ActivityTmdbDetailBinding binding = (ActivityTmdbDetailBinding) host.binding();

        // 详情直放模式布局设置，等待用户点击播放
        binding.playerPanel.setVisibility(View.GONE);
        binding.heroSpacer.setVisibility(View.VISIBLE);
        binding.fusionActions.setVisibility(View.GONE);
        binding.detailActions.setVisibility(View.VISIBLE);
    }
    @Override
    public void play() {
        host.playDetailFullscreen();
    }

}
