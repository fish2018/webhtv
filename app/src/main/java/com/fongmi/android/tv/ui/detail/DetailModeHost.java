package com.fongmi.android.tv.ui.detail;

import android.content.Context;

import androidx.viewbinding.ViewBinding;

/**
 * 详情页模式 Controller 的宿主接口。
 * <p>
 * Activity 实现此接口，向模式 Controller 暴露必要的能力。
 * <p>
 * 设计原则：
 * - 每次只为已迁移代码添加方法，避免接口过大
 * - 禁止预留暂时不用的方法
 * - 随迁移进度逐步扩充（Phase 2 起步只需 context + binding）
 */
public interface DetailModeHost {

    Context context();

    /**
     * 返回 ActivityTmdbDetailBinding
     */
    ViewBinding binding();
}
