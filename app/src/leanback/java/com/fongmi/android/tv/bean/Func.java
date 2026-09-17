package com.fongmi.android.tv.bean;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.impl.Diffable;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.utils.ResUtil;

public class Func implements Diffable<Func> {

    private final int resId;
    private final String text;
    private int drawable;

    public static Func create(int resId) {
        return new Func(resId);
    }

    public Func(int resId) {
        this.resId = resId;
        this.setDrawable();
        this.text = resolveText(resId);
    }

    public int getResId() {
        return resId;
    }

    public int getDrawable() {
        return drawable;
    }

    public String getText() {
        return text;
    }

    public void setDrawable() {
        if (resId == R.string.home_vod) this.drawable = R.drawable.ic_home_vod;
        else if (resId == R.string.home_live) this.drawable = R.drawable.ic_home_live;
        else if (resId == R.string.home_keep) this.drawable = R.drawable.ic_home_keep;
        else if (resId == R.string.home_push) this.drawable = R.drawable.ic_home_push;
        else if (resId == R.string.home_search) this.drawable = R.drawable.ic_home_search;
        else if (resId == R.string.home_setting) this.drawable = R.drawable.ic_home_setting;
        else if (resId == R.string.home_cast) this.drawable = R.drawable.ic_home_push;
        else if (resId == R.string.home_history_button) this.drawable = R.drawable.ic_setting_history;
        else if (resId == R.string.home_custom_csp) this.drawable = R.drawable.ic_site_config;
    }

    private static String resolveText(int resId) {
        if (resId != R.string.home_custom_csp) return ResUtil.getString(resId);
        CustomCspSetting.Status status = CustomCspSetting.status();
        if (!status.available()) return ResUtil.getString(resId);
        return ResUtil.getString(status.enabled() ? R.string.home_custom_csp_on : R.string.home_custom_csp_off);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Func it)) return false;
        return getResId() == it.getResId();
    }

    @Override
    public boolean isSameItem(Func other) {
        return equals(other);
    }

    @Override
    public boolean isSameContent(Func other) {
        return equals(other) && getText().equals(other.getText()) && getDrawable() == other.getDrawable();
    }
}
