package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.google.gson.annotations.SerializedName;

public class SyncOptions {

    @SerializedName("config")
    private boolean config = true;
    @SerializedName("spider")
    private boolean spider = true;
    @SerializedName("search")
    private boolean search = true;
    @SerializedName("history")
    private boolean history = true;
    @SerializedName("keep")
    private boolean keep = true;
    @SerializedName("webHome")
    private boolean webHome = true;
    @SerializedName("settings")
    private boolean settings;

    public static SyncOptions defaults() {
        return new SyncOptions();
    }

    public static SyncOptions objectFrom(String json) {
        try {
            SyncOptions options = App.gson().fromJson(json, SyncOptions.class);
            return options == null ? defaults() : options;
        } catch (Exception e) {
            return defaults();
        }
    }

    public boolean isConfig() {
        return config;
    }

    public SyncOptions config(boolean config) {
        this.config = config;
        return this;
    }

    public boolean isSpider() {
        return spider;
    }

    public SyncOptions spider(boolean spider) {
        this.spider = spider;
        return this;
    }

    public boolean isSearch() {
        return search;
    }

    public SyncOptions search(boolean search) {
        this.search = search;
        return this;
    }

    public boolean isHistory() {
        return history;
    }

    public SyncOptions history(boolean history) {
        this.history = history;
        return this;
    }

    public boolean isKeep() {
        return keep;
    }

    public SyncOptions keep(boolean keep) {
        this.keep = keep;
        return this;
    }

    public boolean isWebHome() {
        return webHome;
    }

    public SyncOptions webHome(boolean webHome) {
        this.webHome = webHome;
        return this;
    }

    public boolean isSettings() {
        return settings;
    }

    public SyncOptions settings(boolean settings) {
        this.settings = settings;
        return this;
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }
}
