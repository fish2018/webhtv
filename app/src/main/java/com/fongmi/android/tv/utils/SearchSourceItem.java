package com.fongmi.android.tv.utils;

public class SearchSourceItem {

    public final int mode;
    public final int titleRes;
    public final boolean configurable;
    public String subtitle;

    public SearchSourceItem(int mode, int titleRes, boolean configurable) {
        this.mode = mode;
        this.titleRes = titleRes;
        this.configurable = configurable;
    }
}