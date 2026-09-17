package com.fongmi.android.tv.ui.detail;

import android.view.View;

import com.fongmi.android.tv.databinding.ActivityTmdbDetailBinding;

/**
 * 炫彩详情模式 Controller。
 * <p>
 * 特点：
 * - 不显示内联播放器
 * - 不自动播放
 * - detailActions 可见，fusionActions 隐藏
 * - heroSpacer 可见
 */
public class EnhancedDetailController extends BaseTmdbDetailModeController {

    public EnhancedDetailController(DetailModeHost host) {
        super(host);
    }

    @Override
    protected boolean showInlinePlayer() {
        return false;
    }

    @Override
    protected boolean autoPlay() {
        return false;
    }

    @Override
    protected int heroSpacerVisibility() {
        return View.VISIBLE;
    }

    @Override
    public void applyInitialLayout() {
        ActivityTmdbDetailBinding binding = (ActivityTmdbDetailBinding) host.binding();

        // 炫彩详情模式布局设置
        binding.playerPanel.setVisibility(View.GONE);
        binding.heroSpacer.setVisibility(View.VISIBLE);
        binding.fusionActions.setVisibility(View.GONE);
        binding.detailActions.setVisibility(View.VISIBLE);
    }
}
