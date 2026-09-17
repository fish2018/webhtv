package com.fongmi.android.tv.history;

import android.text.TextUtils;

import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbMatchCache;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.ui.helper.EpisodeSeasonPolicy;
import com.fongmi.android.tv.utils.SearchResultFilter;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class HistorySourceResolver {

    public static final int REJECTED = Integer.MIN_VALUE;
    public static final int TMDB_MATCH_SCORE = 1000;
    private static final int EXACT_TITLE_SCORE = 300;
    private static final int MAX_CANDIDATES = 8;

    private HistorySourceResolver() {
    }

    public static Resolved resolveAuto(History history) {
        if (history == null) return null;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Constant.TIMEOUT_SEARCH);
        List<Candidate> candidates = searchCandidates(history, deadline);
        candidates.sort(Comparator.comparingInt(Candidate::score).reversed().thenComparingInt(Candidate::siteOrder));
        for (int i = 0; i < Math.min(MAX_CANDIDATES, candidates.size()); i++) {
            Candidate candidate = candidates.get(i);
            Resolved resolved = resolveCandidateBefore(history, candidate.vod(), candidate.tmdb(), true, deadline);
            if (resolved != null) return resolved;
        }
        return null;
    }

    public static Resolved resolveSelected(History history, Vod selected) {
        if (history == null || selected == null || selected.isFolder() || selected.isAction() || selected.getSite() == null || selected.getSite().isEmpty()) return null;
        TmdbItem tmdb = cachedTmdb(selected);
        if (hasIdentityConflict(history, tmdb) || (!isSameIdentity(history, tmdb) && hasYearConflict(history, selected))) return null;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Constant.TIMEOUT_VOD);
        return resolveCandidateBefore(history, selected, tmdb, false, deadline);
    }

    private static List<Candidate> searchCandidates(History history, long deadline) {
        List<Site> sites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) {
            if (site != null && !site.isEmpty() && site.isSearchable() && site.isChangeable()) sites.add(site);
        }
        SiteHealthStore.sortSites(sites);
        Task.applySearchThread(Setting.getSearchThread());
        ExecutorCompletionService<List<Candidate>> completion = new ExecutorCompletionService<>(Task.searchPoolExecutor());
        List<Future<List<Candidate>>> futures = new ArrayList<>();
        for (int i = 0; i < sites.size(); i++) {
            Site site = sites.get(i);
            int order = i;
            futures.add(completion.submit(() -> searchSite(history, site, order)));
        }
        List<Candidate> result = new ArrayList<>();
        try {
            for (int i = 0; i < futures.size(); i++) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                Future<List<Candidate>> future = completion.poll(remaining, TimeUnit.NANOSECONDS);
                if (future == null) break;
                List<Candidate> candidates = future.get();
                if (candidates != null) result.addAll(candidates);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
        } finally {
            for (Future<List<Candidate>> future : futures) future.cancel(true);
        }
        return result;
    }

    private static Resolved resolveCandidateBefore(History history, Vod candidate, TmdbItem candidateTmdb, boolean automatic, long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) return null;
        Future<Resolved> future = Task.searchPoolExecutor().submit(() -> resolveCandidate(history, candidate, candidateTmdb, automatic));
        try {
            return future.get(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            future.cancel(true);
        }
    }

    private static List<Candidate> searchSite(History history, Site site, int siteOrder) {
        try {
            Result result = SiteApi.searchContent(site, history.getVodName(), false, "1");
            List<Candidate> candidates = new ArrayList<>();
            for (Vod vod : result == null ? List.<Vod>of() : result.getList()) {
                if (vod == null || vod.isFolder() || vod.isAction()) continue;
                vod.setSite(site);
                TmdbItem tmdb = cachedTmdb(vod);
                int score = scoreCandidate(history, vod, tmdb, true);
                if (isAutomaticScore(score)) candidates.add(new Candidate(vod, score, siteOrder, tmdb));
            }
            candidates.sort(Comparator.comparingInt(Candidate::score).reversed());
            return candidates.size() <= 3 ? candidates : new ArrayList<>(candidates.subList(0, 3));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static Resolved resolveCandidate(History history, Vod candidate, TmdbItem candidateTmdb, boolean automatic) {
        Site site = candidate.getSite();
        if (site == null || site.isEmpty() || candidate.getId().isEmpty()) return null;
        try {
            Result result = SiteApi.detailContent(site.getKey(), candidate.getId());
            if (result == null || result.getList().isEmpty()) return null;
            Vod detail = result.getVod();
            detail.setSite(site);
            if (detail.getId().isEmpty()) detail.setId(candidate.getId());
            detail.checkName(candidate.getName());
            detail.checkPic(candidate.getPic());
            TmdbItem detailTmdb = cachedTmdb(detail);
            TmdbItem resolvedTmdb = detailTmdb == null ? candidateTmdb : detailTmdb;
            int candidateSeason = EpisodeSeasonPolicy.resolveSourceSeason(candidate.getName(), candidate.getRemarks());
            int detailSeason = EpisodeSeasonPolicy.resolveSourceSeason(detail.getName(), detail.getRemarks());
            if (candidateSeason >= 0 && detailSeason >= 0 && candidateSeason != detailSeason) return null;
            int resolvedSeason = detailSeason >= 0 ? detailSeason : candidateSeason;
            if (automatic && !canAutoReuseSeason(
                    history, resolvedSeason, candidateKey(detail), isSameIdentity(history, resolvedTmdb))) return null;
            int score = scoreCandidate(history, detail.getName(), detail.getYear(), resolvedTmdb);
            if (score == REJECTED || (automatic && !isAutomaticScore(score))) return null;
            EpisodeMatch match = automatic
                    ? findAutomaticEpisode(detail.getFlags(), history, candidateKey(detail), resolvedSeason)
                    : findEpisode(detail.getFlags(), history);
            return match == null ? null : new Resolved(detail, match.flag(), match.episode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static TmdbItem cachedTmdb(Vod candidate) {
        if (!Setting.isHistoryAggregationEffective() || candidate == null || candidate.getSite() == null) return null;
        TmdbMatchCache cache = Setting.getTmdbMatchCache();
        return cache.find(candidate.getSiteKey(), candidate.getId(), candidate.getName());
    }

    public static int scoreCandidate(History history, Vod candidate, TmdbItem candidateTmdb) {
        return scoreCandidate(history, candidate, candidateTmdb, false);
    }

    private static int scoreCandidate(History history, Vod candidate, TmdbItem candidateTmdb, boolean automatic) {
        if (candidate == null) return REJECTED;
        if (automatic) {
            int candidateSeason = EpisodeSeasonPolicy.resolveSourceSeason(candidate.getName(), candidate.getRemarks());
            if (!canAutoReuseSeason(history, candidateSeason, candidateKey(candidate),
                    isSameIdentity(history, candidateTmdb))) return REJECTED;
        }
        return scoreCandidate(history, candidate.getName(), candidate.getYear(), candidateTmdb);
    }

    static int scoreAutomaticCandidate(
            History history,
            String candidateKey,
            String candidateTitle,
            String candidateYear,
            TmdbItem candidateTmdb) {
        int candidateSeason = EpisodeSeasonPolicy.resolveSourceSeason(candidateTitle);
        if (!canAutoReuseSeason(history, candidateSeason, candidateKey,
                isSameIdentity(history, candidateTmdb))) return REJECTED;
        return scoreCandidate(history, candidateTitle, candidateYear, candidateTmdb);
    }

    static boolean canAutoReuseSeason(History history, int candidateSeason, String candidateKey) {
        return canAutoReuseSeason(history, candidateSeason, candidateKey, false);
    }

    private static boolean canAutoReuseSeason(
            History history,
            int candidateSeason,
            String candidateKey,
            boolean sameTmdbIdentity) {
        if (history == null) return false;
        if (!"tv".equals(normalizeMediaType(history.getMediaType()))) return true;
        int savedSeason = history.getTmdbSeasonNumber();
        boolean savedKnown = savedSeason > 0 || (savedSeason == 0 && history.getTmdbEpisodeNumber() > 0);
        if (!savedKnown) return sameSource(history, candidateKey);
        if (candidateSeason < 0) return sameTmdbIdentity || sameSource(history, candidateKey);
        return savedSeason == candidateSeason;
    }

    private static boolean sameSource(History history, String candidateKey) {
        if (history == null || TextUtils.isEmpty(candidateKey)) return false;
        String sourceKey = history.getSiteKey() + AppDatabase.SYMBOL + history.getVodId();
        return TextUtils.equals(history.getKey(), candidateKey)
                || TextUtils.equals(sourceKey, candidateKey)
                || candidateKey.startsWith(sourceKey + AppDatabase.SYMBOL);
    }

    private static String candidateKey(Vod candidate) {
        if (candidate == null) return "";
        return candidate.getSiteKey() + AppDatabase.SYMBOL + candidate.getId();
    }

    static int scoreCandidate(History history, String candidateTitle, String candidateYearValue, TmdbItem candidateTmdb) {
        if (history == null) return REJECTED;
        String historyTitle = history.getVodName();
        if (historyTitle.isEmpty() || candidateTitle == null || candidateTitle.isEmpty()) return REJECTED;

        boolean tmdbConfirmed = isSameIdentity(history, candidateTmdb);
        if (hasIdentityConflict(history, candidateTmdb)) return REJECTED;

        int score;
        if (tmdbConfirmed) score = TMDB_MATCH_SCORE + EXACT_TITLE_SCORE;
        else if (SearchResultFilter.matches(historyTitle, candidateTitle, 100)) score = EXACT_TITLE_SCORE;
        else if (SearchResultFilter.matches(historyTitle, candidateTitle, 70)) score = 180;
        else return REJECTED;

        String historyYear = normalizeYear(history.getYear());
        String candidateYear = normalizeYear(candidateYearValue);
        if (!tmdbConfirmed && !historyYear.isEmpty() && !candidateYear.isEmpty()) {
            if (!historyYear.equals(candidateYear)) return REJECTED;
            score += 100;
        }
        return score;
    }

    static boolean isAutomaticScore(int score) {
        return score >= EXACT_TITLE_SCORE;
    }

    public static EpisodeMatch findEpisode(List<Flag> flags, History history) {
        return findEpisode(flags, history, false, "");
    }

    static EpisodeMatch findAutomaticEpisode(List<Flag> flags, History history, String candidateKey) {
        return findAutomaticEpisode(flags, history, candidateKey, -1);
    }

    static EpisodeMatch findAutomaticEpisode(
            List<Flag> flags, History history, String candidateKey, int candidateSeason) {
        return findEpisode(flags, history, true, candidateKey, candidateSeason);
    }

    private static EpisodeMatch findEpisode(
            List<Flag> flags,
            History history,
            boolean automatic,
            String candidateKey) {
        return findEpisode(flags, history, automatic, candidateKey, -1);
    }

    private static EpisodeMatch findEpisode(
            List<Flag> flags,
            History history,
            boolean automatic,
            String candidateKey,
            int candidateSeason) {
        if (flags == null || flags.isEmpty() || history == null) return null;
        List<Flag> compatible = compatibleSeasonFlags(flags, history, automatic, candidateKey, candidateSeason);
        if (compatible.isEmpty()) return null;
        List<Flag> ordered = new ArrayList<>(compatible.size());
        for (Flag flag : compatible) if (flag.getFlag().equals(history.getVodFlag())) ordered.add(flag);
        for (Flag flag : compatible) if (!containsIdentity(ordered, flag)) ordered.add(flag);

        Episode saved = history.getEpisode();
        int episodeNumber = history.getTmdbEpisodeNumber();
        if (episodeNumber <= 0) episodeNumber = Util.getEpisodeNumber(history.getVodRemarks());
        for (Flag flag : ordered) {
            List<Episode> episodes = seasonCompatibleEpisodes(flag, history);
            if (episodes.isEmpty()) continue;
            Flag scoped = flag;
            if (episodes.size() != flag.getEpisodes().size()) {
                scoped = new Flag(flag.getFlag());
                scoped.getEpisodes().addAll(episodes);
            }
            Episode episode = scoped.find(saved, true);
            if (episode != null && episode.matchesPlayback(saved)) return new EpisodeMatch(flag, episode);
        }
        if (episodeNumber > 0) return null;
        for (Flag flag : ordered) {
            if (!flag.getEpisodes().isEmpty()) return new EpisodeMatch(flag, flag.getEpisodes().get(0));
        }
        return null;
    }

    private static boolean containsIdentity(List<Flag> flags, Flag target) {
        for (Flag flag : flags) if (flag == target) return true;
        return false;
    }

    private static List<Episode> seasonCompatibleEpisodes(Flag flag, History history) {
        if (flag == null || flag.getEpisodes() == null) {
            return flag == null || flag.getEpisodes() == null ? List.of() : flag.getEpisodes();
        }
        int savedSeason = history == null ? -1 : history.getTmdbSeasonNumber();
        boolean savedKnown = history != null && "tv".equals(normalizeMediaType(history.getMediaType()))
                && (savedSeason > 0 || savedSeason == 0 && history.getTmdbEpisodeNumber() > 0);
        Set<Integer> flagSeasons = explicitFlagSeasons(flag);
        if (!savedKnown || flagSeasons.size() <= 1) return flag.getEpisodes();
        List<Episode> result = new ArrayList<>();
        for (Episode episode : flag.getEpisodes()) {
            if (episode != null && EpisodeSeasonPolicy.resolveExplicitSourceSeason(episode.getName()) == savedSeason) {
                result.add(episode);
            }
        }
        return result;
    }

    private static List<Flag> compatibleSeasonFlags(
            List<Flag> flags,
            History history,
            boolean automatic,
            String candidateKey,
            int candidateSeason) {
        int savedSeason = history.getTmdbSeasonNumber();
        boolean savedKnown = "tv".equals(normalizeMediaType(history.getMediaType()))
                && (savedSeason > 0 || savedSeason == 0 && history.getTmdbEpisodeNumber() > 0);
        List<Flag> usable = new ArrayList<>();
        List<Flag> exact = new ArrayList<>();
        List<Flag> unknown = new ArrayList<>();
        for (Flag flag : flags) {
            if (flag == null) continue;
            usable.add(flag);
            if (!savedKnown) continue;
            Set<Integer> flagSeasons = explicitFlagSeasons(flag);
            if (flagSeasons.contains(savedSeason)) exact.add(flag);
            else if (flagSeasons.isEmpty()) unknown.add(flag);
        }
        if (!savedKnown) return usable;
        if (!exact.isEmpty()) return exact;
        return !automatic || sameSource(history, candidateKey) || candidateSeason == savedSeason
                ? unknown : List.of();
    }

    private static Set<Integer> explicitFlagSeasons(Flag flag) {
        Set<Integer> seasons = new LinkedHashSet<>();
        addExplicitSeason(seasons, EpisodeSeasonPolicy.resolveExplicitSourceSeason(flag.getFlag()));
        addExplicitSeason(seasons, EpisodeSeasonPolicy.resolveExplicitSourceSeason(flag.getShow()));
        if (flag.getEpisodes() != null) {
            for (Episode episode : flag.getEpisodes()) {
                if (episode != null) addExplicitSeason(seasons,
                        EpisodeSeasonPolicy.resolveExplicitSourceSeason(episode.getName()));
            }
        }
        return seasons;
    }

    private static void addExplicitSeason(Set<Integer> seasons, int season) {
        if (season >= 0) seasons.add(season);
    }

    private static boolean hasYearConflict(History history, Vod candidate) {
        String historyYear = normalizeYear(history.getYear());
        String candidateYear = normalizeYear(candidate.getYear());
        return !historyYear.isEmpty() && !candidateYear.isEmpty() && !historyYear.equals(candidateYear);
    }

    private static boolean isSameIdentity(History history, TmdbItem candidateTmdb) {
        return hasTmdbIdentity(history)
                && candidateTmdb != null
                && candidateTmdb.getTmdbId() > 0
                && history.getTmdbId() == candidateTmdb.getTmdbId()
                && sameMediaType(history.getMediaType(), candidateTmdb.getMediaType());
    }
    private static boolean hasIdentityConflict(History history, TmdbItem candidateTmdb) {
        if (!hasTmdbIdentity(history) || candidateTmdb == null || candidateTmdb.getTmdbId() <= 0) return false;
        return history.getTmdbId() != candidateTmdb.getTmdbId() || !sameMediaType(history.getMediaType(), candidateTmdb.getMediaType());
    }

    private static boolean hasTmdbIdentity(History history) {
        return history.getTmdbId() > 0 && !normalizeMediaType(history.getMediaType()).isEmpty();
    }

    private static boolean sameMediaType(String first, String second) {
        String left = normalizeMediaType(first);
        String right = normalizeMediaType(second);
        return !left.isEmpty() && left.equals(right);
    }

    private static String normalizeMediaType(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("movie") || normalized.equals("tv") ? normalized : "";
    }

    private static String normalizeYear(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        for (int i = 0; i + 3 < trimmed.length(); i++) {
            String part = trimmed.substring(i, i + 4);
            if (part.chars().allMatch(Character::isDigit)) return part;
        }
        return "";
    }

    private record Candidate(Vod vod, int score, int siteOrder, TmdbItem tmdb) {
    }

    public record EpisodeMatch(Flag flag, Episode episode) {
    }

    public record Resolved(Vod vod, Flag flag, Episode episode) {
    }
}
