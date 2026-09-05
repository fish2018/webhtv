package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class VideoActivityHistoryTitleTest {

    @Test
    public void videoActivitiesPersistDisplayedEpisodeTitlesAndRefreshAsyncEnrichment() throws Exception {
        for (Path sourcePath : List.of(videoActivity("mobile"), videoActivity("leanback"))) {
            String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
            String intentSelection = methodBody(source, "private void applyIntentPlaybackSelection(Vod item)");
            String directTmdbLaunch = methodBody(source, "public static void startDirectTmdb(Activity activity, String key, String id, String name, String pic, String mark, ArrayList<String> episodeTitles, TmdbItem item, Vod tmdbVod, Vod detailVod, String tmdbDetailCacheKey, String playFlag, String playFlagKey, String playEpisodeName, String playEpisodeUrl, int playSeasonNumber, int playEpisodeNumber, History resumeHistory)");
            String saveHistory = methodBody(source, "private void saveHistory(boolean exit)");
            String updateHistory = methodBody(source, "private void updateHistory(Episode item)");
            String updateVod = methodBody(source, "private void updateVod(Vod item)");
            String refreshTitle = methodBody(source, "private boolean refreshCurrentHistoryEpisodeTitle()");

            assertTrue(sourcePath + " must persist the displayed/scraped title for intent-selected episodes",
                    intentSelection.contains("mHistory.setVodRemarks(getHistoryEpisodeName(historyEpisode));"));
            assertTrue(sourcePath + " must persist a bound TMDB episode position without clearing a same-episode fallback",
                    intentSelection.contains("historyEpisode.getTmdbEpisode() != null || !sameEpisode")
                            && intentSelection.contains("mHistory.setTmdbEpisodePosition(historyEpisode)"));
            assertTrue(sourcePath + " must forward the canonical TMDB season and episode from the detail page",
                    directTmdbLaunch.contains("EXTRA_TMDB_PLAY_SEASON_NUMBER")
                            && directTmdbLaunch.contains("EXTRA_TMDB_PLAY_EPISODE_NUMBER"));
            assertTrue(sourcePath + " must persist the forwarded canonical TMDB position for the selected episode",
                    intentSelection.contains("withIntentTmdbEpisodeIdentity(episode)"));
            assertTrue(sourcePath + " must compare episodes by URL before falling back to source names or numbers",
                    updateHistory.contains("historyEpisode.matchesPlayback(mHistory.getEpisode())"));
            assertTrue(sourcePath + " must persist the displayed/scraped title whenever playback changes episodes",
                    updateHistory.contains("mHistory.setVodRemarks(getHistoryEpisodeName(item));"));
            assertTrue(sourcePath + " must replace the TMDB episode position when playback changes episodes",
                    updateHistory.contains("historyEpisode.getTmdbEpisode() != null || !sameEpisode")
                            && updateHistory.contains("mHistory.setTmdbEpisodePosition(historyEpisode)"));
            assertTrue(sourcePath + " must keep position-cache keys on stable source episode names when saving",
                    saveHistory.contains("getCurrentHistoryEpisodeCacheName()"));
            assertTrue(sourcePath + " must keep position-cache keys stable when switching episodes",
                    updateHistory.contains("getCurrentHistoryEpisodeCacheName()"));
            String cacheName = methodBody(source, "private String getCurrentHistoryEpisodeCacheName()");
            assertTrue(sourcePath + " must skip cache writes instead of falling back to a scraped display title",
                    cacheName.contains("return \"\";") && !cacheName.contains("return mHistory.getVodRemarks();"));
            assertTrue(sourcePath + " must refresh the current history title after asynchronous TMDB enrichment",
                    updateVod.contains("boolean episodeTitleChanged = refreshCurrentHistoryEpisodeTitle();"));
            assertTrue(sourcePath + " must save a history-only TMDB title refresh",
                    updateVod.contains("keyChanged || pic || name || episodeTitleChanged"));
            assertTrue(sourcePath + " must resolve the enriched episode against the current history URL",
                    refreshTitle.contains("flag.find(mHistory.getEpisode(), true)"));
            assertTrue(sourcePath + " must copy the resolved TMDB history title into history",
                    refreshTitle.contains("getHistoryEpisodeName(episode)"));
            assertTrue(sourcePath + " must only update the persisted position from a genuinely bound TMDB episode",
                    refreshTitle.contains("historyEpisode.getTmdbEpisode() != null && mHistory.setTmdbEpisodePosition(historyEpisode)"));
            String historyTitle = methodBody(source, "private String getHistoryEpisodeName(Episode episode)");
            assertTrue(sourcePath + " must resolve history titles from durable TMDB metadata/title tables",
                    historyTitle.contains("EpisodeHistoryTitleResolver.resolve(")
                            && historyTitle.contains("getEpisodeTitles()"));
            assertTrue(sourcePath + " must not depend on the mutable presentation displayName",
                    !historyTitle.contains("getDisplayName()") && !historyTitle.contains("getHistoryName()"));
            if (source.contains("private void updateFastTmdbPlaybackHistory(Flag flag, Episode episode)")) {
                String fastPlaybackHistory = methodBody(source, "private void updateFastTmdbPlaybackHistory(Flag flag, Episode episode)");
                assertTrue(sourcePath + " must also preserve scraped titles on the TV fast-playback path",
                        fastPlaybackHistory.contains("mHistory.setVodRemarks(getHistoryEpisodeName(historyEpisode));"));
                assertTrue(sourcePath + " must persist the forwarded canonical position on the TV fast-playback path",
                        fastPlaybackHistory.contains("withIntentTmdbEpisodeIdentity(episode)"));
            }
        }
    }

    @Test
    public void allDetailPlaybackModesKeepTheirScrapedHistoryPath() throws Exception {
        Path sourcePath = mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
        String initHistory = methodBody(source, "private void initHistory()");
        String onPlay = methodBody(source, "private void onPlay()");
        String inlineHistory = methodBody(source, "private void updateInlineHistory(Episode item)");
        String refreshHistory = methodBody(source, "private void refreshCurrentHistoryEpisodeTitle()");
        String sameEpisode = methodBody(source, "private boolean isHistoryEpisode(Episode episode, History item)");
        String defaultPlayback = methodBody(source, "private void playDefaultPlayback()");
        String fastTitles = methodBody(source, "private ArrayList<String> fastPlaybackEpisodeTitles()");
        String inlineCacheKey = methodBody(source, "private String inlineEpisodeCacheKey(Episode episode)");
        String currentCacheKey = methodBody(source, "private String currentInlineHistoryCacheKey()");

        assertTrue("standalone TMDB detail must load aggregated progress from the matched TMDB identity",
                initHistory.contains("vod.getFlags(), matchedTmdbItem"));
        assertTrue("fusion/player/colorful modes must update the TMDB-formatted history title before branching",
                onPlay.contains("updateInlineHistory(selectedEpisode);"));
        assertTrue("fusion mode must keep inline playback", onPlay.contains("if (isFusionMode()) playInline();"));
        assertTrue("player-detail mode must keep fullscreen inline playback", onPlay.contains("else if (isPlayerMode()) playDetailFullscreen();"));
        assertTrue("colorful detail mode must keep the external VideoActivity path", onPlay.contains("else playDefaultPlayback();"));
        assertTrue("inline modes must store the formatted scraped episode title",
                inlineHistory.contains("history.setVodRemarks(historyEpisodeTitle(item));"));
        assertTrue("inline modes must persist the canonical TMDB episode position",
                inlineHistory.contains("setHistoryTmdbEpisodePosition(history, item)"));
        assertTrue("detail refresh must persist the canonical position after TMDB enrichment",
                refreshHistory.contains("setHistoryTmdbEpisodePosition(saved, selectedEpisode)"));
        assertTrue("detail playback must prefer persisted TMDB episode identity over matching source labels",
                sameEpisode.contains("item.getTmdbEpisodeNumber() > 0")
                        && sameEpisode.contains("episode.matchesPlayback(saved)"));
        assertTrue("external colorful playback must forward the scraped episode title table",
                defaultPlayback.contains("fastPlaybackEpisodeTitles()"));
        assertTrue("external colorful playback must forward the canonical TMDB season and episode",
                defaultPlayback.contains("position.season()") && defaultPlayback.contains("position.number()"));
        assertTrue("the colorful fast-playback payload must contain the TMDB title rather than the raw source name",
                fastTitles.contains("tmdbEpisodeTitle(number)")
                        && !fastTitles.contains("playbackEpisodeName()"));
        assertTrue("standalone detail history lookup must reject another TMDB season",
                initHistory.contains("matchedTmdbItem, sourceTitleSeasonNumber()"));
        assertTrue("inline episode cache keys must include the resolved season",
                inlineCacheKey.contains("EpisodeSeasonPolicy.episodePositionCacheKey"));
        assertTrue("saving the previous inline episode must use the current history identity",
                currentCacheKey.contains("history.getTmdbSeasonNumber()")
                        && inlineHistory.contains("currentInlineHistoryCacheKey()")
                        && inlineHistory.contains("inlineEpisodeCacheKey(item)"));
    }

    @Test
    public void originalEnhancedCarriesSeasonContextIntoEpisodeHeaderAndPlaybackIdentity() throws Exception {
        Path cachePath = mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "SourceEpisodeSeasonCache.java"));
        String cacheSource = Files.readString(cachePath, StandardCharsets.UTF_8);
        String episodeResolution = methodBody(cacheSource, "private static int resolveEpisodeSeason(Episode episode)");
        for (Path sourcePath : List.of(videoActivity("mobile"), videoActivity("leanback"))) {
            String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
            String checkHistory = methodBody(source, "private boolean checkHistory(Vod item)");
            String resolveSeason = methodBody(source, "private int currentSourceSeasonNumber(Vod item)");
            String seasonIdentity = methodBody(source, "private Episode withSourceSeasonEpisodeIdentity(Episode episode)");
            String cacheName = methodBody(source, "private String episodePositionCacheName(Episode episode, int preferredSeason)");
            String flagSeason = methodBody(source, "private int resolveSourceEpisodeSeason(Flag flag)");
            String vodSeason = methodBody(source, "private int resolveSourceEpisodeSeason(Vod item)");
            String setDetail = methodBody(source, "private void setDetail(Vod item)");
            String updateVod = methodBody(source, "private void updateVod(Vod item)");
            assertTrue(sourcePath + " must expose a season-aware episode header",
                    source.contains("private void updateEpisodeSeasonContext()")
                            && source.contains("R.string.detail_episode_season_context"));
            assertTrue(sourcePath + " must resolve source season before matching playback history",
                    source.contains("currentSourceSeasonNumber()")
                            && source.contains("currentSourceSeasonNumber(item)"));
            assertTrue(sourcePath + " must prioritize the selected source line over the overall title",
                    resolveSeason.indexOf("EpisodeSeasonPolicy.resolveExplicitSourceSeason(sourceFlag == null ? \"\" : sourceFlag.getShow())") >= 0
                            && resolveSeason.indexOf("sourceFlag == null ? \"\" : sourceFlag.getShow()")
                            < resolveSeason.indexOf("getName(), mSourceVodName"));
            assertTrue(sourcePath + " must preserve explicit TMDB season zero for specials",
                    resolveSeason.contains("if (season >= 0) return season;")
                            && !resolveSeason.contains("if (season > 0) return season;")
                            && seasonIdentity.contains("if (season < 0) return episode;")
                            && cacheName.contains("if (season < 0) season = currentSourceSeasonNumber();"));
            assertTrue(sourcePath + " must reuse the shared long-series season cache",
                    source.contains("private final SourceEpisodeSeasonCache mSourceEpisodeSeasonCache = new SourceEpisodeSeasonCache();")
                            && flagSeason.contains("mSourceEpisodeSeasonCache.resolve(flag)")
                            && vodSeason.contains("mSourceEpisodeSeasonCache.resolve(item)"));
            assertTrue(sourcePath + " must invalidate cached season scans when source or TMDB data changes",
                    setDetail.contains("mSourceEpisodeSeasonCache.clear();")
                            && updateVod.contains("mSourceEpisodeSeasonCache.clear();"));
            assertTrue("shared season resolution must prefer explicit source episode names over previously bound TMDB metadata",
                    episodeResolution.indexOf("EpisodeSeasonPolicy.resolveExplicitSourceSeason(episode == null ? \"\" : episode.getName())") >= 0
                            && episodeResolution.indexOf("EpisodeSeasonPolicy.resolveExplicitSourceSeason(episode == null ? \"\" : episode.getName())")
                            < episodeResolution.indexOf("episode.getTmdbEpisode()"));
            assertTrue(sourcePath + " must pass the resolved source season into ordinary playback history lookup",
                    checkHistory.contains("item.getFlags(), tmdbItem, currentSourceSeasonNumber(item)"));
            assertTrue(sourcePath + " must stamp season-aware episode identity before saving history",
                    source.contains("withSourceSeasonEpisodeIdentity(")
                            && source.contains("EpisodeSeasonPolicy.episodePositionCacheKey("));
        }
    }

    @Test
    public void tmdbEnrichmentUsesCapturedSourceSeasonInsteadOfAlwaysBindingSeasonOne() throws Exception {
        Path sourcePath = mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
        assertTrue("TMDB adapter must expose the captured source season", source.contains("getSourceSeasonNumber()"));
        assertTrue("TMDB adapter must keep exactly one volatile source-season field",
                source.contains("private volatile int sourceSeasonNumber = -1;")
                        && !source.contains("private int sourceSeasonNumber = -1;"));
        String detailSync = methodBody(source,
                "private void loadDetailSync(Vod vod, TmdbItem item, JsonObject cachedDetail, List<TmdbPerson> cachedCast, int generation)");
        String captureSeason = methodBody(source, "private void captureSourceSeason(Vod sourceVod, String sourceTitle)");
        String captureActiveFlag = methodBody(source, "private void captureActiveFlagSeasonEvidence(Vod sourceVod)");
        assertTrue("TMDB source-season capture must preserve explicit season zero for specials",
                captureSeason.contains("boolean pushSource = SiteApi.PUSH.equals(cacheSiteKey(sourceVod));")
                        && captureSeason.contains("? EpisodeSeasonPolicy.resolveExplicitSourceSeason(")
                        && captureSeason.contains(": EpisodeSeasonPolicy.resolveSourceSeason(")
                        && source.contains("addExplicitSeason(seasons, EpisodeSeasonPolicy.resolveExplicitSourceSeason(")
                        && captureActiveFlag.contains("explicitSourceSeasons(sourceFlag)")
                        && source.contains("if (season >= 0 && !seasons.contains(season))"));
        assertTrue("TMDB source-season capture must not treat source-line ordinals as seasons",
                source.contains("EpisodeSeasonPolicy.resolveExplicitSourceSeason(flag.getShow())"));
        assertTrue("TMDB source-season capture must prefer source episode names over bound metadata",
                source.contains("episode == null ? \"\" : episode.getName()")
                        && captureActiveFlag.contains("List<Integer> explicit = explicitSourceSeasons(sourceFlag);")
                        && captureActiveFlag.indexOf("List<Integer> explicit = explicitSourceSeasons(sourceFlag);")
                        < captureActiveFlag.indexOf("TmdbEpisode tmdbEpisode ="));
        assertTrue("bound TMDB metadata must remain a history fallback instead of becoming explicit source-season evidence",
                captureActiveFlag.contains("List<Integer> metadata = new ArrayList<>();")
                        && captureActiveFlag.contains("addExplicitSeason(metadata, tmdbEpisode.getSeasonNumber());")
                        && captureActiveFlag.contains("explicitSourceSeasons = List.copyOf(explicit);")
                        && captureActiveFlag.contains("metadata.size() == 1 ? metadata.get(0) : -1"));
        Path detailPath = mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String detailSource = Files.readString(detailPath, StandardCharsets.UTF_8);
        String detailExplicitSeason = methodBody(detailSource, "private int explicitSourceSeasonNumber(Episode episode)");
        String detailEpisodeSeason = methodBody(detailSource, "private int sourceSeasonNumber(Episode episode)");
        assertTrue("standalone detail must prefer source episode names over bound TMDB metadata",
                detailExplicitSeason.contains("EpisodeSeasonPolicy.resolveExplicitSourceSeason(episode.getName())")
                        && detailEpisodeSeason.indexOf("explicitSourceSeasonNumber(episode)") >= 0
                        && detailEpisodeSeason.indexOf("explicitSourceSeasonNumber(episode)")
                        < detailEpisodeSeason.indexOf("episode.getTmdbEpisode()"));
        String detailTitleSeason = methodBody(detailSource, "private int sourceTitleSeasonNumber()");
        assertTrue("standalone detail must not treat the selected source-line ordinal as a season",
                detailTitleSeason.contains("EpisodeSeasonPolicy.resolveExplicitSourceSeason(selectedFlag.getShow())"));
        assertTrue("TMDB detail loading must preserve a previously captured source season",
                detailSync.contains("resolveSeason(vod, item, detail);"));
        assertTrue("TMDB detail loading must not overwrite the captured season from only the VOD name",
                !detailSync.contains("sourceSeasonNumber = vod == null ? -1 : new MediaTitleParser().seasonNumber(vod.getName());"));
        assertTrue("TMDB adapter must use structured season resolution", source.contains("TmdbSeasonResolver.resolve(")
                && source.contains("seasonResolution.getSelectedSeason()"));
        assertTrue("TMDB adapter must not hard-code season 1 for every source", !source.contains("tmdbService.season(item, 1,"));
    }
    private static Path videoActivity(String sourceSet) {
        Path moduleRelative = Path.of("src", sourceSet, "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app").resolve(moduleRelative);
    }

    private static Path mainJava() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app").resolve(moduleRelative);
    }

    private static String methodBody(String source, String startToken) {
        int start = source.indexOf(startToken);
        assertTrue("Missing source token: " + startToken, start >= 0);
        int open = source.indexOf('{', start + startToken.length());
        assertTrue("Missing method body for: " + startToken, open > start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("Unclosed method body for: " + startToken);
    }
}
