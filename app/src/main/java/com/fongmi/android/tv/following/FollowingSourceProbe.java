package com.fongmi.android.tv.following;

import android.text.TextUtils;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.Vod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FollowingSourceProbe {

    public FollowingSourceSnapshot probe(Following item, FollowingSource source) throws Exception {
        if (item == null || source == null) throw new IllegalArgumentException("following/source == null");
        Result result = SiteApi.detailContent(source.siteKey, source.vodId, true);
        Vod vod = result == null ? null : result.getVod();
        Flag flag = chooseFlag(vod, source.vodFlag, item.trackedSeason);
        long now = System.currentTimeMillis();
        FollowingSourceSnapshot snapshot = new FollowingSourceSnapshot();
        snapshot.source = source;
        snapshot.probedAt = now;
        if (vod != null) {
            if (TextUtils.isEmpty(source.vodName)) source.vodName = vod.getName();
            if (TextUtils.isEmpty(source.vodPic)) source.vodPic = vod.getPic();
        }
        if (flag == null) {
            source.lastProbeAt = now;
            source.lastError = "来源没有可播放线路";
            throw new IllegalStateException(source.lastError);
        }
        source.vodFlag = flag.getFlag();
        source.playableSeason = Math.max(0, item.trackedSeason);
        Set<String> urls = new HashSet<>();
        int max = 0;
        int count = 0;
        for (Episode episode : flag.getEpisodes()) {
            if (episode == null || TextUtils.isEmpty(episode.getUrl())) continue;
            if (!matchesSeason(episode, item.trackedSeason)) continue;
            if (!urls.add(episode.getUrl())) continue;
            int number = episodeNumber(episode);
            if (number <= 0) continue;
            count++;
            max = Math.max(max, number);
        }
        source.playableEpisode = max;
        source.playableCount = count;
        source.lastProbeAt = now;
        source.lastError = "";
        snapshot.playableEpisode = max;
        snapshot.playableCount = count;
        return snapshot;
    }

    static Flag chooseFlag(Vod vod, String preferred) {
        return chooseFlag(vod, preferred, -1);
    }

    static Flag chooseFlag(Vod vod, String preferred, int trackedSeason) {
        if (vod == null || vod.getFlags() == null) return null;
        Flag preferredFlag = null;
        Flag fallback = null;
        int fallbackCount = -1;
        for (Flag flag : vod.getFlags()) {
            if (flag == null) continue;
            if (!TextUtils.isEmpty(preferred) && preferred.equals(flag.getFlag())) {
                preferredFlag = flag;
                continue;
            }
            int count = validCount(flag, trackedSeason);
            if (count > fallbackCount) {
                fallback = flag;
                fallbackCount = count;
            }
        }
        if (preferredFlag != null && validCount(preferredFlag, trackedSeason) > 0) return preferredFlag;
        return fallbackCount > 0 ? fallback : null;
    }

    private static int validCount(Flag flag, int trackedSeason) {
        int count = 0;
        for (Episode episode : flag.getEpisodes()) {
            if (episode == null || TextUtils.isEmpty(episode.getUrl())) continue;
            if (trackedSeason >= 0 && !matchesSeason(episode, trackedSeason)) continue;
            count++;
        }
        return count;
    }

    static int episodeNumber(Episode episode) {
        TmdbEpisode tmdb = episode == null ? null : episode.getTmdbEpisode();
        if (tmdb != null && tmdb.getNumber() > 0) return tmdb.getNumber();
        return episode == null ? 0 : episode.getNumber();
    }

    static boolean matchesSeason(Episode episode, int trackedSeason) {
        TmdbEpisode tmdb = episode == null ? null : episode.getTmdbEpisode();
        return tmdb == null || tmdb.getSeasonNumber() < 0 || tmdb.getSeasonNumber() == trackedSeason;
    }

    public static FollowingMetadataSnapshot metadata(Following item, FollowingSource source, long now) {
        FollowingMetadataSnapshot snapshot = new FollowingMetadataSnapshot();
        snapshot.source = "source-probe";
        snapshot.status = FollowingMetadataSnapshot.UNKNOWN;
        snapshot.latestReleasedSeason = Math.max(0, item.trackedSeason);
        snapshot.latestReleasedEpisode = Math.max(0, source.playableEpisode);
        snapshot.seasonTotalEpisodes = Math.max(0, source.playableCount);
        snapshot.seasonReleasedEpisodes = Math.max(0, source.playableEpisode);
        snapshot.fetchedAt = now;
        return snapshot;
    }

    public static final class FollowingSourceSnapshot {
        public FollowingSource source;
        public int playableEpisode;
        public int playableCount;
        public long probedAt;
    }
}
