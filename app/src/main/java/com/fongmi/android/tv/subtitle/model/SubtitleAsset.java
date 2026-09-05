package com.fongmi.android.tv.subtitle.model;

public final class SubtitleAsset {

    private final String uri;
    private final String localPath;
    private final String displayName;
    private final String language;
    private final String mimeType;
    private final int selectionFlag;
    private final boolean fromCache;
    private final long expireAt;

    public SubtitleAsset(String uri, String localPath, String displayName, String language, String mimeType, int selectionFlag, boolean fromCache, long expireAt) {
        this.uri = uri == null ? "" : uri;
        this.localPath = localPath == null ? "" : localPath;
        this.displayName = displayName == null ? "" : displayName;
        this.language = language == null ? "" : language;
        this.mimeType = mimeType == null ? "" : mimeType;
        this.selectionFlag = selectionFlag;
        this.fromCache = fromCache;
        this.expireAt = expireAt;
    }

    public String getUri() {
        return uri;
    }

    public String getLocalPath() {
        return localPath;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLanguage() {
        return language;
    }

    public String getMimeType() {
        return mimeType;
    }

    public int getSelectionFlag() {
        return selectionFlag;
    }

    public boolean isFromCache() {
        return fromCache;
    }

    public long getExpireAt() {
        return expireAt;
    }
}
