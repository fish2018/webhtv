package com.fongmi.android.tv.ui.utils;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import com.fongmi.android.tv.utils.ResUtil;

/**
 * TmdbDetailActivity 布局工具方法集
 *
 * 从 TmdbDetailActivity 提取的无状态布局辅助方法,用于:
 * - 设置视图边距/内边距/尺寸
 * - 创建简单 Drawable
 *
 * 第一批重构: 提取 6 个纯静态方法,减少主类 ~30 行
 */
public class TmdbDetailLayoutUtils {

    /**
     * 设置视图边距 (DP 单位)
     * 原: TmdbDetailActivity.setMarginsDp() (1600-1602 行)
     * 调用次数: ~89 处
     */
    public static void setMarginsDp(View view, int left, int top, int right, int bottom) {
        setMarginsPx(view, ResUtil.dp2px(left), ResUtil.dp2px(top), ResUtil.dp2px(right), ResUtil.dp2px(bottom));
    }

    /**
     * 设置视图边距 (像素单位)
     * 原: TmdbDetailActivity.setMarginsPx() (1604-1609 行)
     * 调用次数: ~15 处 (含被 setMarginsDp 调用)
     */
    public static void setMarginsPx(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams marginParams)) return;
        marginParams.setMargins(left, top, right, bottom);
        view.setLayoutParams(marginParams);
    }

    /**
     * 设置视图高度 (DP 单位)
     * 原: TmdbDetailActivity.setHeightDp() (1611-1615 行)
     * 调用次数: ~52 处
     */
    public static void setHeightDp(View view, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = ResUtil.dp2px(height);
        view.setLayoutParams(params);
    }

    /**
     * 设置视图宽度 (像素单位)
     * 原: TmdbDetailActivity.setWidthPx() (1617-1621 行)
     * 调用次数: ~23 处
     */
    public static void setWidthPx(View view, int width) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = width;
        view.setLayoutParams(params);
    }

    /**
     * 设置视图宽度为 MATCH_PARENT
     * 原: TmdbDetailActivity.setWidthMatch() (1623-1627 行)
     * 调用次数: ~18 处
     */
    public static void setWidthMatch(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        view.setLayoutParams(params);
    }

    /**
     * 设置视图内边距 (DP 单位)
     * 原: TmdbDetailActivity.setPaddingDp() (1589-1591 行)
     * 调用次数: ~37 处
     */
    public static void setPaddingDp(View view, int left, int top, int right, int bottom) {
        view.setPadding(ResUtil.dp2px(left), ResUtil.dp2px(top), ResUtil.dp2px(right), ResUtil.dp2px(bottom));
    }

    /**
     * 创建纯色 Drawable
     * 原: TmdbDetailActivity.colorDrawable() (1629-1631 行)
     * 调用次数: ~12 处
     */
    public static Drawable colorDrawable(int color) {
        return new ColorDrawable(color);
    }
}
