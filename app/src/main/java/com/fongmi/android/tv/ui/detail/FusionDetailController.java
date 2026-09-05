package com.fongmi.android.tv.ui.detail;

import android.view.View;

import com.fongmi.android.tv.databinding.ActivityTmdbDetailBinding;

/**
 * 沉浸融合模式 Controller。
 * <p>
 * 特点：
 * - 显示内联播放器
 * - 不自动播放
 * - 特殊按钮布局（fusionActions 可见，detailActions 隐藏）
 * - heroSpacer 隐藏
 */
public class FusionDetailController extends BaseTmdbDetailModeController {

    public FusionDetailController(DetailModeHost host) {
        super(host);
    }

    @Override
    protected boolean showInlinePlayer() {
        return true;
    }

    @Override
    protected boolean autoPlay() {
        return false; // 融合模式不自动播放
    }

    @Override
    protected int heroSpacerVisibility() {
        return View.GONE;
    }

    @Override
    public void applyInitialLayout() {
        ActivityTmdbDetailBinding binding = (ActivityTmdbDetailBinding) host.binding();

        // 融合模式特有布局设置（从 TmdbDetailActivity line 595-602 迁移）
        binding.playerPanel.setVisibility(View.VISIBLE);
        // spacer 必须与 playerPanel 同步可见：它在滚动流里占据播放器空间（否则 root 层的
        // playerPanel 会压在下方按钮上），同时作为焦点桥梁让方向键能从周围按钮导航回播放器。
        binding.playerPanelSpacer.setVisibility(View.VISIBLE);
        binding.heroSpacer.setVisibility(View.GONE);
        binding.fusionActions.setVisibility(View.VISIBLE);
        binding.detailActions.setVisibility(View.GONE);
    }

    @Override
    public void onPlaybackStarted() {
        // TODO: 融合模式特有的播放开始逻辑
        // 目前留空，等 Phase 2 迁移具体逻辑
    }
}
