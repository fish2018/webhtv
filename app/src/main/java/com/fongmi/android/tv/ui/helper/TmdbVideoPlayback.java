package com.fongmi.android.tv.ui.helper;

import android.app.Activity;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.TmdbVideo;
import com.fongmi.android.tv.ui.dialog.TmdbVideoPlayerDialog;
import com.fongmi.android.tv.utils.PushParser;
import com.fongmi.android.tv.utils.ResUtil;

import java.io.Serializable;

/** Converts a validated TMDB video into an isolated windowed playback request. */
public final class TmdbVideoPlayback {

    private TmdbVideoPlayback() {
    }

    public static Launch create(TmdbVideo video, String playFlag) {
        if (video == null) return null;
        String url = video.getWatchUrl();
        if (url == null || url.isEmpty()) return null;
        String name = video.getName();
        if (name == null || name.isEmpty()) name = video.getDisplayType();
        PushParser.Parsed parsed = PushParser.of(url, name);
        if (parsed.getUrl().isEmpty()) return null;
        return new Launch(
                SiteApi.PUSH,
                parsed.getId(),
                parsed.getName(),
                video.getThumbnailUrl(),
                video.getDisplayType() + " · " + video.getScopeLabel(),
                playFlag,
                parsed.getName(),
                parsed.getUrl(),
                false);
    }

    public static boolean play(Activity activity, TmdbVideo video) {
        if (!(activity instanceof FragmentActivity)) return false;
        Launch launch = create(video, ResUtil.getString(R.string.push));
        if (launch == null) return false;
        return TmdbVideoPlayerDialog.show((FragmentActivity) activity, launch);
    }

    public static final class Launch implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String key;
        private final String id;
        private final String name;
        private final String pic;
        private final String mark;
        private final String playFlag;
        private final String playEpisodeName;
        private final String playEpisodeUrl;
        private final boolean resumeFromHistory;

        private Launch(String key, String id, String name, String pic, String mark, String playFlag,
                       String playEpisodeName, String playEpisodeUrl, boolean resumeFromHistory) {
            this.key = key;
            this.id = id;
            this.name = name;
            this.pic = pic;
            this.mark = mark;
            this.playFlag = playFlag;
            this.playEpisodeName = playEpisodeName;
            this.playEpisodeUrl = playEpisodeUrl;
            this.resumeFromHistory = resumeFromHistory;
        }

        public String getKey() { return key; }
        public String getId() { return id; }
        public String getName() { return name; }
        public String getPic() { return pic; }
        public String getMark() { return mark; }
        public String getPlayFlag() { return playFlag; }
        public String getPlayEpisodeName() { return playEpisodeName; }
        public String getPlayEpisodeUrl() { return playEpisodeUrl; }
        public boolean isResumeFromHistory() { return resumeFromHistory; }
    }
}
