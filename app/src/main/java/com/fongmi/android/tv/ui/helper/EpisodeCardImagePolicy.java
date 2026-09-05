package com.fongmi.android.tv.ui.helper;

import android.text.TextUtils;

public final class EpisodeCardImagePolicy {

    private EpisodeCardImagePolicy() {
    }

    /**
     * 宽设备优先横向 backdrop，窄设备优先纵向海报；首选比例缺失时退到另一比例。
     */
    public static String fallbackFor(String wideFallback, String narrowFallback, boolean wide) {
        String preferred = wide ? wideFallback : narrowFallback;
        String alternate = wide ? narrowFallback : wideFallback;
        return TextUtils.isEmpty(preferred) ? alternate : preferred;
    }
}
