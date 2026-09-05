package com.fongmi.android.tv.ui.detail;

import android.view.View;

/**
 * TMDB 详情页模式控制器的抽象基类。
 * <p>
 * 提供三种模式的共享实现，子类通过覆盖抽象方法和 protected 方法来表达差异。
 */
public abstract class BaseTmdbDetailModeController implements TmdbDetailModeController {

    protected final DetailModeHost host;

    public BaseTmdbDetailModeController(DetailModeHost host) {
        this.host = host;
    }

    @Override
    public void bind() {
        // 默认空实现，子类按需覆盖
    }

    @Override
    public void applyTheme() {
        // 默认空实现，子类按需覆盖
    }

    @Override
    public void onContentLoaded() {
        // 默认空实现，子类按需覆盖
    }

    @Override
    public void onPlaybackStarted() {
        // 默认空实现，子类按需覆盖
    }

    @Override
    public boolean shouldShowInlinePlayer() {
        return showInlinePlayer();
    }

    @Override
    public boolean shouldAutoPlay() {
        return autoPlay();
    }

    @Override
    public boolean handleBack() {
        return false; // 默认不处理，交给 Activity
    }

    @Override
    public void release() {
        // 默认空实现，子类按需覆盖
    }

    // ===== 子类覆盖的差异点 =====

    /**
     * 是否显示内联播放器。
     */
    protected abstract boolean showInlinePlayer();

    /**
     * 是否自动播放。
     */
    protected abstract boolean autoPlay();

    /**
     * heroSpacer 可见性。
     */
    protected abstract int heroSpacerVisibility();
}
