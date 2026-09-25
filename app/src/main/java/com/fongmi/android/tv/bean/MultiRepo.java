package com.fongmi.android.tv.bean;
 
import android.text.TextUtils;
 
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
 
import com.fongmi.android.tv.App;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
 
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
 
/**
 * 多仓仓库条目：name 仓库名称, url 仓库链接, cache 缓存内容, etag/lastModified 缓存标识
 */
public class MultiRepo {
 
    @SerializedName("name")
    private String name;
    @SerializedName("url")
    private String url;
    @SerializedName("cache")
    private String cache;
    @SerializedName("etag")
    private String etag;
    @SerializedName("lastModified")
    private String lastModified;
    @SerializedName("time")
    private long time;
 
    public static List<MultiRepo> arrayFrom(String str) {
        if (TextUtils.isEmpty(str)) return new ArrayList<>();
        try {
            Type listType = TypeToken.getParameterized(List.class, MultiRepo.class).getType();
            List<MultiRepo> items = App.gson().fromJson(str, listType);
            return items == null ? new ArrayList<>() : items;
        } catch (Throwable e) {
            return new ArrayList<>();
        }
    }
 
    public static String toJson(List<MultiRepo> items) {
        return App.gson().toJson(items);
    }
 
    public MultiRepo() {
    }
 
    public MultiRepo(String name, String url) {
        this.name = name;
        this.url = url;
    }
 
    public String getName() {
        return TextUtils.isEmpty(name) ? getUrl() : name;
    }
 
    public void setName(String name) {
        this.name = name;
    }
 
    public String getUrl() {
        return TextUtils.isEmpty(url) ? "" : url;
    }
 
    public void setUrl(String url) {
        this.url = url;
    }
 
    public String getCache() {
        return cache == null ? "" : cache;
    }
 
    public void setCache(String cache) {
        this.cache = cache;
    }
 
    public String getEtag() {
        return etag == null ? "" : etag;
    }
 
    public void setEtag(String etag) {
        this.etag = etag;
    }
 
    public String getLastModified() {
        return lastModified == null ? "" : lastModified;
    }
 
    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }
 
    public long getTime() {
        return time;
    }
 
    public void setTime(long time) {
        this.time = time;
    }
 
    public MultiRepo name(String name) {
        this.name = name;
        return this;
    }
 
    public MultiRepo url(String url) {
        this.url = url;
        return this;
    }
 
    public boolean isEmpty() {
        return TextUtils.isEmpty(getUrl());
    }
 
    /**
     * 检查仓库内容缓存是否有可用数据
     */
    public boolean hasCache() {
        return !TextUtils.isEmpty(getCache());
    }
 
    @NonNull
    @Override
    public String toString() {
        return "MultiRepo{name='" + name + "', url='" + url + "'}";
    }
 
    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MultiRepo other)) return false;
        return getUrl().equals(other.getUrl());
    }
}
