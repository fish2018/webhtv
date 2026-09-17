package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.Vod;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;

/** Caches uniform source-season scans for one detail/playback screen. */
public final class SourceEpisodeSeasonCache {

    private final Map<Flag, Integer> flagSeasons = new IdentityHashMap<>();
    private final Map<Vod, Integer> vodSeasons = new IdentityHashMap<>();
    private final ToIntFunction<Episode> episodeResolver;

    public SourceEpisodeSeasonCache() {
        this(SourceEpisodeSeasonCache::resolveEpisodeSeason);
    }

    SourceEpisodeSeasonCache(ToIntFunction<Episode> episodeResolver) {
        this.episodeResolver = Objects.requireNonNull(episodeResolver);
    }

    public int resolve(Flag flag) {
        if (flag == null || flag.getEpisodes() == null) return -1;
        Integer cached = flagSeasons.get(flag);
        if (cached != null) return cached;
        Integer season = null;
        for (Episode episode : flag.getEpisodes()) {
            int candidate = episodeResolver.applyAsInt(episode);
            if (candidate < 0) continue;
            if (season != null && season != candidate) {
                flagSeasons.put(flag, -1);
                return -1;
            }
            season = candidate;
        }
        int resolved = season == null ? -1 : season;
        flagSeasons.put(flag, resolved);
        return resolved;
    }

    public int resolve(Vod item) {
        if (item == null || item.getFlags() == null) return -1;
        Integer cached = vodSeasons.get(item);
        if (cached != null) return cached;
        Integer season = null;
        for (Flag flag : item.getFlags()) {
            int candidate = resolve(flag);
            if (candidate < 0) continue;
            if (season != null && season != candidate) {
                vodSeasons.put(item, -1);
                return -1;
            }
            season = candidate;
        }
        int resolved = season == null ? -1 : season;
        vodSeasons.put(item, resolved);
        return resolved;
    }

    public void clear() {
        flagSeasons.clear();
        vodSeasons.clear();
    }

    /**
     * 该线路是否混排了多个季（各集解析出的季号不止一个）。
     * 这种线路里集名不足以唯一标识一集（两季都有"第01集"），播放位置缓存必须放弃使用，
     * 否则没看过的一集会读到另一季同名集的进度。注意：整条线路都解析不出季号（单季剧常态）
     * 不算混排，此时集名在该 vodId 内唯一，缓存照常可用。
     */
    public boolean hasMixedSeasons(Flag flag) {
        if (flag == null || flag.getEpisodes() == null) return false;
        Integer season = null;
        for (Episode episode : flag.getEpisodes()) {
            int candidate = episodeResolver.applyAsInt(episode);
            if (candidate < 0) continue;
            if (season != null && season != candidate) return true;
            season = candidate;
        }
        return false;
    }

    private static int resolveEpisodeSeason(Episode episode) {
        int candidate = EpisodeSeasonPolicy.resolveExplicitSourceSeason(episode == null ? "" : episode.getName());
        if (candidate >= 0) return candidate;
        TmdbEpisode tmdbEpisode = episode == null ? null : episode.getTmdbEpisode();
        return tmdbEpisode != null && tmdbEpisode.getNumber() > 0 ? tmdbEpisode.getSeasonNumber() : -1;
    }
}
