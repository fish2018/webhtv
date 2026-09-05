package com.fongmi.android.tv.ui.detail;

import android.view.View;

import com.fongmi.android.tv.databinding.ActivityTmdbDetailBinding;

/**
 * 详情直放模式 Controller。
 * <p>
 * 特点：
 * - 不显示内联播放器（初始）
 * - 自动播放（进入后立即全屏播放）
 * - detailActions 可见，fusionActions 隐藏
 * - heroSpacer 可见
 */
public class PlayerDetailController extends BaseTmdbDetailModeController {

    public PlayerDetailController(DetailModeHost host) {
        super(host);
    }

    @Override
    protected boolean showInlinePlayer() {
        return false;
    }

    @Override
    protected boolean autoPlay() {
        return true; // 详情直放模式自动播放
    }

    @Override
    protected int heroSpacerVisibility() {
        return View.VISIBLE;
    }

    @Override
    public void applyInitialLayout() {
        ActivityTmdbDetailBinding binding = (ActivityTmdbDetailBinding) host.binding();

        // 详情直放模式布局设置（与炫彩详情类似，但后续会自动触发播放）
        binding.playerPanel.setVisibility(View.GONE);
        binding.heroSpacer.setVisibility(View.VISIBLE);
        binding.fusionActions.setVisibility(View.GONE);
        binding.detailActions.setVisibility(View.VISIBLE);
    }
}
