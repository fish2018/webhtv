package com.fongmi.android.tv.web;

public enum WebThemePage {
    HOME("home", "vod.home@1"),
    DETAIL("detail", "vod.detail@1");

    private final String key;
    private final String contract;

    WebThemePage(String key, String contract) {
        this.key = key;
        this.contract = contract;
    }

    public String getKey() {
        return key;
    }

    public String getContract() {
        return contract;
    }
}
