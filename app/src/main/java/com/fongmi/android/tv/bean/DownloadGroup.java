package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class DownloadGroup implements Serializable {

    private final String id;
    private final String key;
    private final String vodId;
    private final String vodName;
    private final String vodPic;
    private final List<DownloadItem> items;

    public DownloadGroup(DownloadItem item) {
        this.id = item.getGroupId();
        this.key = item.getKey();
        this.vodId = item.getVodId();
        this.vodName = item.getVodName();
        this.vodPic = item.getVodPic();
        this.items = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getVodId() {
        return vodId;
    }

    public String getVodName() {
        return vodName;
    }

    public String getVodPic() {
        return vodPic;
    }

    public List<DownloadItem> getItems() {
        return items;
    }

    public void add(DownloadItem item) {
        items.add(item);
    }

    public int getTotal() {
        return items.size();
    }

    public int getDone() {
        int count = 0;
        for (DownloadItem item : items) if (item.isDone()) ++count;
        return count;
    }

    public boolean hasActive() {
        for (DownloadItem item : items) if (item.isActive()) return true;
        return false;
    }

    public String getBadge() {
        return getDone() + "/" + getTotal();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DownloadGroup)) return false;
        return getId().equals(((DownloadGroup) obj).getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return getVodName() + " " + getBadge();
    }
}
