package com.fongmi.android.tv.ui.audio;

import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.media3.common.C;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Task;

import java.util.Objects;

public final class AudioHistory {

    private AudioHistory() {
    }

    public static String buildPlaybackKey(String siteKey, String vodId) {
        return siteKey + AppDatabase.SYMBOL + vodId + AppDatabase.SYMBOL + VodConfig.getCid();
    }

    public static String buildLegacyPlaybackKey(String siteKey, String vodId) {
        return siteKey + AppDatabase.SYMBOL + vodId;
    }

    @Nullable
    public static History find(String siteKey, String vodId) {
        return findCurrent(buildPlaybackKey(siteKey, vodId), siteKey);
    }

    public static String getVodId(String playbackKey) {
        String[] splits = Objects.toString(playbackKey, "").split(AppDatabase.SYMBOL);
        return splits.length > 1 ? splits[1] : "";
    }

    public static boolean canUse(String playbackKey, String siteKey) {
        return !TextUtils.isEmpty(siteKey) && !TextUtils.isEmpty(getVodId(playbackKey));
    }

    public static void saveTrack(Record record, long position, long duration) {
        if (Setting.isIncognito() || record == null || !record.canUse()) return;
        long targetPosition = normalizeTime(position);
        long targetDuration = normalizeTime(duration);
        Task.execute(() -> saveTrackSync(record, targetPosition, targetDuration));
    }

    public static void syncProgress(String playbackKey, String siteKey, long position, long duration) {
        if (Setting.isIncognito() || !canUse(playbackKey, siteKey)) return;
        if (position <= 0 || duration <= 0 || duration == C.TIME_UNSET) return;
        Task.execute(() -> {
            History history = findCurrent(playbackKey, siteKey);
            if (history == null) return;
            if (!playbackKey.equals(history.getKey())) history.replace(playbackKey);
            history.setPosition(position);
            history.setDuration(duration);
            history.setCreateTime(System.currentTimeMillis());
            history.save();
        });
    }

    public static void syncProgress(Record record, long position, long duration) {
        if (record == null) return;
        syncProgress(record.playbackKey, record.siteKey, position, duration);
    }

    @Nullable
    private static History findCurrent(String playbackKey, String siteKey) {
        History history = History.find(playbackKey);
        String vodId = getVodId(playbackKey);
        if (history == null && !TextUtils.isEmpty(siteKey) && !TextUtils.isEmpty(vodId)) history = History.find(buildLegacyPlaybackKey(siteKey, vodId));
        return history;
    }

    private static void saveTrackSync(Record record, long position, long duration) {
        History history = findCurrent(record.playbackKey, record.siteKey);
        boolean refresh = history == null;
        if (history == null) {
            history = new History();
            history.setKey(record.playbackKey);
        } else if (!record.playbackKey.equals(history.getKey())) {
            history.replace(record.playbackKey);
            refresh = true;
        }
        boolean changedTrack = !TextUtils.equals(record.episodeUrl, history.getEpisodeUrl()) || !TextUtils.equals(record.vodRemarks, history.getVodRemarks());
        boolean same = isSameEpisode(history, record.vodRemarks, record.episodeUrl);
        history.setCid(VodConfig.getCid());
        history.setVodName(record.vodName);
        history.setVodPic(record.vodPic);
        history.setVodFlag(record.vodFlag);
        history.setVodRemarks(record.vodRemarks);
        history.setEpisodeUrl(record.episodeUrl);
        if (!same) history.setTmdbEpisodePosition(null);
        history.setPosition(same && history.getPosition() > 0 ? history.getPosition() : position);
        history.setDuration(same && history.getDuration() > 0 ? history.getDuration() : duration);
        history.setCreateTime(System.currentTimeMillis());
        history.save();
        if (refresh || changedTrack) App.post(RefreshEvent::history);
    }

    static boolean isSameEpisode(History history, String episodeName, String episodeUrl) {
        if (history == null) return false;
        return Episode.create(episodeName, episodeUrl).matchesPlayback(history.getEpisode());
    }

    private static long normalizeTime(long value) {
        return value <= 0 || value == C.TIME_UNSET ? 0 : value;
    }

    public static final class Record {

        private final String playbackKey;
        private final String siteKey;
        private final String vodFlag;
        private final String vodName;
        private final String vodPic;
        private final String vodRemarks;
        private final String episodeUrl;

        public Record(String playbackKey, String siteKey, String vodFlag, String vodName, String vodPic, String vodRemarks, String episodeUrl) {
            this.playbackKey = Objects.toString(playbackKey, "");
            this.siteKey = Objects.toString(siteKey, "");
            this.vodFlag = Objects.toString(vodFlag, "");
            this.vodName = Objects.toString(vodName, "");
            this.vodPic = Objects.toString(vodPic, "");
            this.vodRemarks = Objects.toString(vodRemarks, "");
            this.episodeUrl = Objects.toString(episodeUrl, "");
        }

        public boolean canUse() {
            return AudioHistory.canUse(playbackKey, siteKey);
        }

        public String playbackKey() {
            return playbackKey;
        }

        public String trackKey() {
            return playbackKey + "\n" + vodRemarks + "\n" + episodeUrl;
        }
    }
}
