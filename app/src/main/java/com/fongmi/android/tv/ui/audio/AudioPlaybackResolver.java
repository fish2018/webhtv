package com.fongmi.android.tv.ui.audio;

import android.text.TextUtils;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;

public class AudioPlaybackResolver {

    public static Resolved resolveSite(String key, String id, String name, String pic, String mark) throws Exception {
        Result detail = SiteApi.detailContent(key, id);
        Vod vod = detail.getVod();
        vod.checkName(name);
        vod.checkPic(pic);
        Flag flag = selectFlag(key, id, vod, mark);
        if (flag == null || flag.getEpisodes().isEmpty()) throw new IllegalStateException("没有可播放的音频");
        Episode episode = selectEpisode(key, id, flag, mark);
        int index = Math.max(0, flag.getEpisodes().indexOf(episode));
        Result result = SiteApi.playerContent(key, flag.getFlag(), episode.getUrl());
        return new Resolved(key, id, vod, flag, episode, index, result, VodConfig.get().getSite(key).getTimeout());
    }

    private static Flag selectFlag(String key, String id, Vod vod, String mark) {
        History history = AudioHistory.find(key, id);
        String targetFlag = history == null ? "" : history.getVodFlag();
        if (!TextUtils.isEmpty(targetFlag)) {
            for (Flag flag : vod.getFlags()) if (targetFlag.equals(flag.getFlag())) return flag;
        }
        if (!TextUtils.isEmpty(mark)) {
            for (Flag flag : vod.getFlags()) if (mark.equals(flag.getFlag())) return flag;
        }
        return vod.getFlags().isEmpty() ? null : vod.getFlags().get(0);
    }

    private static Episode selectEpisode(String key, String id, Flag flag, String mark) {
        History history = AudioHistory.find(key, id);
        Episode episode = history == null ? null : flag.find(history.getVodRemarks(), false);
        if (episode != null) return episode;
        if (!TextUtils.isEmpty(mark)) episode = flag.find(mark, false);
        if (episode != null) return episode;
        return flag.getEpisodes().get(0);
    }

    public static class Resolved {

        private final String siteKey;
        private final String vodId;
        private final Vod vod;
        private final Flag flag;
        private final Episode episode;
        private final int index;
        private final Result result;
        private final long timeout;

        private Resolved(String siteKey, String vodId, Vod vod, Flag flag, Episode episode, int index, Result result, long timeout) {
            this.siteKey = siteKey;
            this.vodId = vodId;
            this.vod = vod;
            this.flag = flag;
            this.episode = episode;
            this.index = index;
            this.result = result;
            this.timeout = timeout;
        }

        public String getSiteKey() {
            return siteKey;
        }

        public String getVodId() {
            return vodId;
        }

        public Vod getVod() {
            return vod;
        }

        public Flag getFlag() {
            return flag;
        }

        public Episode getEpisode() {
            return episode;
        }

        public int getIndex() {
            return index;
        }

        public Result getResult() {
            return result;
        }

        public long getTimeout() {
            return timeout;
        }
    }
}
