package com.fongmi.android.tv.ui.detail;

/**
 * TMDB 详情页模式控制器接口。
 * <p>
 * 封装沉浸融合/炫彩详情/详情直放三种模式的差异行为。
 * <p>
 * 设计目标：
 * - 让模式差异集中管理，避免在 Activity 中散落 if-else
 * - 新增模式时，只需新建一个实现类并在工厂方法中注册
 */
public interface TmdbDetailModeController {

    /**
     * 绑定 View 和设置监听器。
     * <p>
     * 在 Activity onCreate 时调用，用于设置按钮点击、焦点监听等。
     */
    void bind();

    /**
     * 设置初始布局可见性。
     * <p>
     * 在 Activity onCreate 时调用，用于显示/隐藏不同模式特有的 UI 组件。
     */
    void applyInitialLayout();

    /**
     * 应用主题和样式。
     * <p>
     * 在主题切换或初始化时调用。
     */
    void applyTheme();

    /**
     * TMDB 内容加载完成回调。
     * <p>
     * 当 TMDB API 返回详情数据后调用。
     */
    void onContentLoaded();

    /**
     * 播放开始回调。
     * <p>
     * 当播放器开始播放时调用（内联或全屏）。
     */
    void onPlaybackStarted();

    /**
     * 是否显示内联播放器。
     * <p>
     * 仅沉浸融合模式返回 true。
     */
    boolean shouldShowInlinePlayer();

    /**
     * 是否自动播放。
     * <p>
     * 仅详情直放模式返回 true。
     */
    boolean shouldAutoPlay();

    /**
     * 处理返回键。
     * <p>
     * @return true 表示已处理，Activity 不再处理；false 表示未处理，交给 Activity
     */
    boolean handleBack();

    /**
     * 释放资源。
     * <p>
     * 在 Activity onDestroy 时调用。
     */
    void release();
}
