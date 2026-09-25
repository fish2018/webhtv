package com.fongmi.android.tv.playback;

public class PlaybackRemoteSyncResult {

    public boolean success;
    public int fetched;
    public int applied;
    public int deleted;
    public int skipped;
    public int failed;
    public String message;
    public String configKey;
    public String nextSince;
    public java.util.Map<String, String> cursors;
    public String identityState;
    public String identityEpoch;
    public boolean identitySupported;
    public String identityCanonicalKey;
    public String identityMatchedBy;
    public String identityMessage;

    public static PlaybackRemoteSyncResult success(PlaybackProgressBatchResult batch) {
        return success(batch, "", "");
    }

    public static PlaybackRemoteSyncResult success(PlaybackProgressBatchResult batch, String configKey, String nextSince) {
        return success(batch, configKey, nextSince, null);
    }

    public static PlaybackRemoteSyncResult success(PlaybackProgressBatchResult batch, String configKey, String nextSince, java.util.Map<String, String> cursors) {
        PlaybackRemoteSyncResult result = new PlaybackRemoteSyncResult();
        result.success = true;
        result.fetched = batch == null ? 0 : batch.total;
        result.applied = batch == null ? 0 : batch.applied;
        result.deleted = batch == null ? 0 : batch.deleted;
        result.skipped = batch == null ? 0 : batch.skipped;
        result.failed = batch == null ? 0 : batch.failed;
        result.message = "";
        result.configKey = configKey == null ? "" : configKey;
        result.nextSince = nextSince == null ? "" : nextSince;
        result.cursors = cursors;
        result.identityState = "unknown";
        result.identityEpoch = "";
        result.identitySupported = false;
        result.identityCanonicalKey = "";
        result.identityMatchedBy = "";
        result.identityMessage = "";
        return result;
    }

    public static PlaybackRemoteSyncResult failure(String message) {
        PlaybackRemoteSyncResult result = new PlaybackRemoteSyncResult();
        result.success = false;
        result.message = message == null ? "" : message;
        result.configKey = "";
        result.nextSince = "";
        result.cursors = null;
        result.identityState = "unknown";
        result.identityEpoch = "";
        result.identitySupported = false;
        result.identityCanonicalKey = "";
        result.identityMatchedBy = "";
        result.identityMessage = "";
        return result;
    }
}
