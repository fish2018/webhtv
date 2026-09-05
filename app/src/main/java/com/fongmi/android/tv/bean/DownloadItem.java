package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.github.catvod.utils.Util;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.Serializable;
import java.util.Map;

public class DownloadItem implements Serializable {

    public static final int STATE_WAITING = 0;
    public static final int STATE_RUNNING = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_DONE = 3;
    public static final int STATE_ERROR = 4;

    @SerializedName("id")
    private String id;
    @SerializedName("key")
    private String key;
    @SerializedName("vodId")
    private String vodId;
    @SerializedName("vodName")
    private String vodName;
    @SerializedName("vodPic")
    private String vodPic;
    @SerializedName("flag")
    private String flag;
    @SerializedName("episodeName")
    private String episodeName;
    @SerializedName("episodeUrl")
    private String episodeUrl;
    @SerializedName("url")
    private String url;
    @SerializedName("path")
    private String path;
    @SerializedName("headers")
    private Map<String, String> headers;
    @SerializedName("state")
    private int state;
    @SerializedName("current")
    private long current;
    @SerializedName("total")
    private long total;
    @SerializedName("speed")
    private long speed;
    @SerializedName("m3u8")
    private boolean m3u8;
    @SerializedName("segmentTotal")
    private int segmentTotal;
    @SerializedName("segmentDone")
    private int segmentDone;
    @SerializedName("createTime")
    private long createTime;

    public static DownloadItem create(String key, Vod vod, String flag, String episodeName, String episodeUrl) {
        DownloadItem item = new DownloadItem();
        item.setId(buildId(key, vod.getId(), flag, episodeUrl));
        item.setKey(key);
        item.setVodId(vod.getId());
        item.setVodName(vod.getName());
        item.setVodPic(vod.getPic());
        item.setFlag(flag);
        item.setEpisodeName(episodeName);
        item.setEpisodeUrl(episodeUrl);
        item.setState(STATE_WAITING);
        item.setCreateTime(System.currentTimeMillis());
        return item;
    }

    public static String buildId(String key, String vodId, String flag, String episodeUrl) {
        return Util.md5(key + "_" + vodId + "_" + flag + "_" + episodeUrl);
    }

    public static DownloadItem objectFrom(String str) {
        if (TextUtils.isEmpty(str)) return null;
        try {
            return new Gson().fromJson(str, DownloadItem.class);
        } catch (Exception e) {
            return null;
        }
    }

    public String getId() {
        return TextUtils.isEmpty(id) ? "" : id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKey() {
        return TextUtils.isEmpty(key) ? "" : key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getVodId() {
        return TextUtils.isEmpty(vodId) ? "" : vodId;
    }

    public void setVodId(String vodId) {
        this.vodId = vodId;
    }

    public String getVodName() {
        return TextUtils.isEmpty(vodName) ? "" : vodName;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getVodPic() {
        return TextUtils.isEmpty(vodPic) ? "" : vodPic;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public String getFlag() {
        return TextUtils.isEmpty(flag) ? "" : flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public String getEpisodeName() {
        return TextUtils.isEmpty(episodeName) ? "" : episodeName;
    }

    public void setEpisodeName(String episodeName) {
        this.episodeName = episodeName;
    }

    public String getEpisodeUrl() {
        return TextUtils.isEmpty(episodeUrl) ? "" : episodeUrl;
    }

    public void setEpisodeUrl(String episodeUrl) {
        this.episodeUrl = episodeUrl;
    }

    public String getUrl() {
        return TextUtils.isEmpty(url) ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPath() {
        return TextUtils.isEmpty(path) ? "" : path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public long getCurrent() {
        return current;
    }

    public void setCurrent(long current) {
        this.current = current;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getSpeed() {
        return speed;
    }

    public void setSpeed(long speed) {
        this.speed = speed;
    }

    public boolean isM3u8() {
        return m3u8;
    }

    public void setM3u8(boolean m3u8) {
        this.m3u8 = m3u8;
    }

    public int getSegmentTotal() {
        return segmentTotal;
    }

    public void setSegmentTotal(int segmentTotal) {
        this.segmentTotal = segmentTotal;
    }

    public int getSegmentDone() {
        return segmentDone;
    }

    public void setSegmentDone(int segmentDone) {
        this.segmentDone = segmentDone;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public boolean isDone() {
        return getState() == STATE_DONE;
    }

    public boolean isRunning() {
        return getState() == STATE_RUNNING;
    }

    public boolean isWaiting() {
        return getState() == STATE_WAITING;
    }

    public boolean isPaused() {
        return getState() == STATE_PAUSED;
    }

    public boolean isError() {
        return getState() == STATE_ERROR;
    }

    public boolean isActive() {
        return isRunning() || isWaiting();
    }

    public int getProgress() {
        if (isDone()) return 100;
        if (isM3u8() && getSegmentTotal() > 0) return (int) (getSegmentDone() * 100L / getSegmentTotal());
        if (getTotal() <= 0) return 0;
        return (int) Math.min(100, getCurrent() * 100 / getTotal());
    }

    public File getFile() {
        return TextUtils.isEmpty(path) ? null : new File(path);
    }

    public boolean exists() {
        File file = getFile();
        return file != null && file.exists() && file.length() > 0;
    }

    public String getGroupId() {
        return Util.md5(getKey() + "_" + getVodId());
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DownloadItem)) return false;
        return getId().equals(((DownloadItem) obj).getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return toJson();
    }
}
