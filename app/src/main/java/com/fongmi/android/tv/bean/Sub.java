package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.utils.Trans;
import com.google.gson.annotations.SerializedName;

public class Sub {

    @SerializedName("url")
    private String url;
    @SerializedName("name")
    private String name;
    @SerializedName("lang")
    private String lang;
    @SerializedName("format")
    private String format;
    @SerializedName("flag")
    private int flag;

    public static Sub from(String path) {
        Sub sub = new Sub();
        sub.url = path;
        sub.name = UrlUtil.path(path);
        sub.flag = C.SELECTION_FLAG_DEFAULT;
        sub.format = PlayerHelper.getSubtitleMimeType(sub.name);
        return sub;
    }

    public static Sub create(String name, String url, String lang, String format) {
        Sub sub = new Sub();
        sub.name = name;
        sub.url = url;
        sub.lang = lang;
        sub.format = format;
        return sub;
    }

    public String getUrl() {
        return isEmpty(url) ? "" : url;
    }

    public String getName() {
        return isEmpty(name) ? "" : name;
    }

    public String getLang() {
        return isEmpty(lang) ? "" : lang;
    }

    public String getFormat() {
        return isEmpty(format) ? "" : format;
    }

    public int getFlag() {
        return flag == 0 ? C.SELECTION_FLAG_DEFAULT : flag;
    }

    public int getRawFlag() {
        return flag;
    }

    public boolean isForced() {
        return (flag & C.SELECTION_FLAG_FORCED) != 0;
    }

    private boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public void trans() {
        if (Trans.pass()) return;
        this.name = Trans.s2t(name);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Sub it)) return false;
        return getUrl().equals(it.getUrl());
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }
}
