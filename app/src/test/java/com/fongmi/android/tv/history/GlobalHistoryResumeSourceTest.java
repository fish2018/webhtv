package com.fongmi.android.tv.history;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GlobalHistoryResumeSourceTest {

    @Test
    public void allNativeHistoryEntriesUseTheSharedCoordinator() throws Exception {
        assertContains("app/src/mobile/java/com/fongmi/android/tv/ui/activity/HistoryActivity.java", "HistoryResumeCoordinator.open(this, item)");
        assertContains("app/src/leanback/java/com/fongmi/android/tv/ui/activity/HistoryActivity.java", "HistoryResumeCoordinator.open(this, item)");
        assertContains("app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java", "HistoryResumeCoordinator.open(this, item)");
    }

    @Test
    public void bothPlayersAcceptExplicitCrossConfigResumeHistory() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            String source = read("app/src/" + mode + "/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
            assertTrue(source.contains("EXTRA_RESUME_HISTORY_CID"));
            assertTrue(source.contains("EXTRA_RESUME_HISTORY_KEY"));
            assertTrue(source.contains("startFromResolvedHistory"));
            assertTrue(source.contains("resumeHistory.forPlaybackKey(getHistoryKey(), VodConfig.getCid())"));
        }
    }

    @Test
    public void directHistoryLaunchPinsClickedRecordAcrossLateTmdbMatch() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            String source = read("app/src/" + mode + "/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
            int reloadStart = source.indexOf("private boolean reloadHistoryAfterTmdbMatch(TmdbItem matched)");
            int reloadEnd = source.indexOf("private void resumeHistoryAfterTmdbMatch", reloadStart);
            String reload = source.substring(reloadStart, reloadEnd);

            assertTrue(mode + " history launch must pass the exact clicked record", source.contains("item.getEpisodeUrl(), item)"));
            assertTrue(mode + " history launch must preserve the source config id", source.contains("intent.putExtra(EXTRA_RESUME_HISTORY_CID, resumeHistory.getCid())"));
            assertTrue(mode + " history launch must preserve the seasonal source reference", source.contains("intent.putExtra(EXTRA_RESUME_HISTORY_KEY, HistoryResumePayload.encode(resumeHistory))"));
            assertTrue(mode + " late TMDB matching must not replace an explicit history selection", reload.contains("hasIntentResumeHistory()"));
            assertFalse(mode + " plain resume requests must still allow late TMDB matching", reload.contains("isResumeFromHistory()"));
        }
    }

    @Test
    public void standaloneDetailHistoryKeepsTheClickedEpisodeIdentity() throws Exception {
        String detail = read("app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java");

        assertTrue(detail.contains("public static void startFromHistory(Activity activity, History item)"));
        assertTrue(detail.contains("public static void startFromResolvedHistory(Activity activity, History source, Vod target, Flag flag, Episode episode)"));
        assertTrue(detail.contains("resumeHistory = getIntentResumeHistory();"));
        assertTrue(detail.contains("resumeHistory.forPlaybackKey(getHistoryKey(), VodConfig.getCid())"));
        assertTrue(detail.contains("isResumeFromHistory() ? getIntentResumeHistory() : null"));
    }

    @Test
    public void historySourceLabelWaitsForVodConfigAndRefreshesWhenReady() throws Exception {
        String history = read("app/src/main/java/com/fongmi/android/tv/bean/History.java");
        assertTrue(history.contains("if (getCid() != VodConfig.getCid()) return ResUtil.getString(R.string.history_other_config);"));
        assertTrue(history.contains("if (VodConfig.get().getSites().isEmpty()) return \"\";"));
        assertTrue(history.contains("ResUtil.getString(R.string.history_source_unavailable)"));

        for (String mode : new String[]{"mobile", "leanback"}) {
            String activity = read("app/src/" + mode + "/java/com/fongmi/android/tv/ui/activity/HistoryActivity.java");
            assertTrue(mode + " history page must observe VOD config completion", activity.contains("public void onConfigEvent(ConfigEvent event)"));
            assertTrue(mode + " history page must refresh labels only after VOD config events", activity.contains("if (event.isVod() && mAdapter != null) {") && activity.contains("mAdapter.notifyDataSetChanged();"));
        }

        String defaults = read("app/src/main/res/values/strings.xml");
        String chinese = read("app/src/main/res/values-zh-rCN/strings.xml");
        assertTrue(defaults.contains("<string name=\"history_source_unavailable\">Source unavailable</string>"));
        assertTrue(chinese.contains("<string name=\"history_other_config\">其他配置</string>"));
        assertTrue(chinese.contains("<string name=\"history_source_unavailable\">接口已失效</string>"));
    }
    @Test
    public void resolvedPlaybackPreservesHistoryBackdrop() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            String source = read("app/src/" + mode + "/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
            assertTrue(source.contains("intent.putExtra(\"wallPic\", source.getWallPic())"));
        }
    }

    @Test
    public void resolvedHistoryUsesTheSameDetailModeRoutingAsOrdinaryHistory() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            String source = read("app/src/" + mode + "/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
            int start = source.indexOf("public static void startFromResolvedHistory");
            int end = source.indexOf("\n    public static", start + 1);
            String method = source.substring(start, end);
            String detailGuard = mode.equals("leanback")
                    ? "shouldOpenLegacyTmdbDetail(target.getSiteKey(), target.getId(), false)"
                    : "shouldOpenLegacyTmdbDetail(target.getSiteKey(), target.getId())";
            int guard = method.indexOf(detailGuard);
            int detailLaunch = method.indexOf("TmdbDetailActivity.startFromResolvedHistory(activity, source, target, flag, episode)");
            int directLaunch = method.indexOf("new Intent(activity, VideoActivity.class)");

            assertTrue(mode + " resolved history must honor the configured detail mode", guard >= 0);
            assertTrue(mode + " resolved history must enter the standard detail route before direct playback", detailLaunch > guard && directLaunch > detailLaunch);
        }
    }

    @Test
    public void manualSearchDoesNotDropResumeContextOnFolderResults() throws Exception {
        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/fragment/CollectFragment.java");
        String leanback = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java");

        assertTrue(mobile.contains("FolderActivity.start(requireActivity(), item.getSiteKey(), Result.folder(item), getHistoryResumeCid(), getHistoryResumeKey(), getHistoryResumeTargetCid())"));
        assertTrue(leanback.contains("VodActivity.start(this, item.getSiteKey(), Result.folder(item), 0, getHistoryResumeCid(), getHistoryResumeKey(), getHistoryResumeTargetCid())"));
        assertFalse(mobile.contains("Notify.show(R.string.history_source_episode_missing)"));
    }

    @Test
    public void manualSearchCarriesHistoryContextUntilVodSelection() throws Exception {
        assertContains("app/src/mobile/java/com/fongmi/android/tv/ui/activity/SearchActivity.java", "directFromHistory");
        assertContains("app/src/mobile/java/com/fongmi/android/tv/ui/fragment/CollectFragment.java", "HistoryResumeCoordinator.openSelected");
        assertContains("app/src/leanback/java/com/fongmi/android/tv/ui/activity/SearchActivity.java", "directFromHistory");
        assertContains("app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java", "HistoryResumeCoordinator.openSelected");
    }

    @Test
    public void manualSearchRejectsResultsAfterTheVodConfigChanges() throws Exception {
        String coordinator = read("app/src/main/java/com/fongmi/android/tv/ui/activity/HistoryResumeCoordinator.java");
        assertTrue(coordinator.contains("int targetCid, Vod selected"));
        assertTrue(coordinator.contains("if (VodConfig.getCid() != targetCid)"));
        assertTrue(coordinator.contains("showSearchFallback(activity, current, targetCid)"));
        assertTrue(coordinator.contains("openSearch(activity, history.getCid(), HistoryResumePayload.encode(history), targetCid, history.getVodName())"));

        for (String path : new String[]{
                "app/src/mobile/java/com/fongmi/android/tv/ui/fragment/CollectFragment.java",
                "app/src/mobile/java/com/fongmi/android/tv/ui/fragment/TypeFragment.java",
                "app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java",
                "app/src/leanback/java/com/fongmi/android/tv/ui/fragment/TypeFragment.java"}) {
            String source = read(path);
            assertTrue(path + " must carry the target cid", source.contains("getHistoryResumeTargetCid()"));
            assertTrue(path + " must pass the target cid to the coordinator", source.contains("getHistoryResumeTargetCid(), item"));
        }
    }

    @Test
    public void automaticResolverSkipsNonPlayableSearchEntries() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/history/HistorySourceResolver.java");

        assertTrue(source.contains("vod == null || vod.isFolder() || vod.isAction()"));
        assertTrue(source.contains("selected.isFolder() || selected.isAction()"));
    }

    @Test
    public void resolvedDetailFallsBackToTheValidatedCandidateVodId() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/history/HistorySourceResolver.java");

        assertTrue(source.contains("if (detail.getId().isEmpty()) detail.setId(candidate.getId())"));
    }

    @Test
    public void manualSearchPreservesHistoryContextThroughFolders() throws Exception {
        assertContains("app/src/mobile/java/com/fongmi/android/tv/ui/activity/FolderActivity.java", "historyResumeCid");
        assertContains("app/src/mobile/java/com/fongmi/android/tv/ui/fragment/FolderFragment.java", "historyResumeKey");
        assertContains("app/src/mobile/java/com/fongmi/android/tv/ui/fragment/TypeFragment.java", "HistoryResumeCoordinator.openSelected");
        assertContains("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VodActivity.java", "historyResumeCid");
        assertContains("app/src/leanback/java/com/fongmi/android/tv/ui/fragment/FolderFragment.java", "historyResumeKey");
        assertContains("app/src/leanback/java/com/fongmi/android/tv/ui/fragment/TypeFragment.java", "HistoryResumeCoordinator.openSelected");
    }

    @Test
    public void indexFolderItemsContinueHistoryAwareSearchInsteadOfResolvingAsVod() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            String source = read("app/src/" + mode + "/java/com/fongmi/android/tv/ui/fragment/TypeFragment.java");
            int method = source.indexOf("public void onItemClick(Vod item)");
            int indexBranch = source.indexOf("getSite().isIndex()", method);
            int selectedBranch = source.indexOf("HistoryResumeCoordinator.openSelected", method);

            assertTrue(indexBranch > method && indexBranch < selectedBranch);
            assertTrue(source.indexOf("HistoryResumeCoordinator.openSearch", indexBranch) > indexBranch);
        }
    }

    @Test
    public void automaticResolverUsesTheConfiguredSearchPool() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/history/HistorySourceResolver.java");

        assertTrue(source.contains("new ExecutorCompletionService<>(Task.searchPoolExecutor())"));
    }

    @Test
    public void automaticResumeUsesFullSearchAndPersistentLightProgress() throws Exception {
        String resolver = read("app/src/main/java/com/fongmi/android/tv/history/HistorySourceResolver.java");
        String coordinator = read("app/src/main/java/com/fongmi/android/tv/ui/activity/HistoryResumeCoordinator.java");

        assertTrue(resolver.contains("SiteApi.searchContent(site, history.getVodName(), false, \"1\")"));
        assertTrue(coordinator.contains("ProgressBar"));
        assertTrue(coordinator.contains("R.style.Theme_WebHTV_LightDialog"));
        assertTrue(coordinator.contains("setCancelable(false)"));
        assertTrue(coordinator.contains("dismiss(loading)"));
        assertFalse(coordinator.contains("Notify.show(R.string.history_source_searching)"));
    }

    @Test
    public void resolvedPlaybackReloadsHistoryBeforeLaunching() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/activity/HistoryResumeCoordinator.java");

        assertTrue(source.contains("History current = HistoryResumePayload.restore"));
        assertTrue(source.contains("VideoActivity.startFromResolvedHistory(activity, current"));
        assertTrue(source.contains("Notify.show(R.string.history_record_missing)"));
    }

    @Test
    public void quarterlyResumeReferenceSurvivesDirectAndSearchLaunches() throws Exception {
        String coordinator = read("app/src/main/java/com/fongmi/android/tv/ui/activity/HistoryResumeCoordinator.java");
        String detail = read("app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java");
        String mobileSearch = read("app/src/mobile/java/com/fongmi/android/tv/ui/activity/SearchActivity.java");
        String leanbackSearch = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java");

        assertTrue(coordinator.contains("HistoryResumePayload.restore(sourceCid, sourceKey)"));
        assertTrue(coordinator.contains("HistoryResumePayload.restore(history.getCid(), resumePayload)"));
        assertTrue(detail.contains("HistoryResumePayload.restore("));
        assertTrue(mobileSearch.contains("HistoryResumePayload.encode(history)"));
        assertTrue(leanbackSearch.contains("HistoryResumePayload.encode(history)"));
        for (String mode : new String[]{"mobile", "leanback"}) {
            String player = read("app/src/" + mode + "/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
            assertTrue(player.contains("HistoryResumePayload.restore("));
            assertTrue(player.contains("EXTRA_TMDB_PLAY_FLAG_KEY"));
            assertTrue(player.contains("TmdbUIAdapter.selectPlaybackFlag"));
        }
    }

    @Test
    public void asynchronousSourceResolutionNeverCombinesOldEpisodeWithNewProgress() throws Exception {
        String coordinator = read("app/src/main/java/com/fongmi/android/tv/ui/activity/HistoryResumeCoordinator.java");

        assertTrue(coordinator.contains("if (!isSameResumeVersion(history, current))"));
        assertTrue(coordinator.contains("openSelected(activity, sourceCid, sourceKey, targetCid, selected);"));
        assertTrue(coordinator.contains("resolveAuto(activity, current);"));
    }

    @Test
    public void bothPlayersAbortWhenExplicitResumeHistoryDisappears() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            String source = read("app/src/" + mode + "/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");

            assertTrue(source.contains("hasIntentResumeHistory() && resumeHistory == null"));
            assertTrue(source.contains("Notify.show(R.string.history_record_missing)"));
            assertTrue(source.contains("if (!checkHistory(item)) return;"));
        }
    }

    @Test
    public void automaticDetailValidationHonorsTheOverallDeadline() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/history/HistorySourceResolver.java");

        assertTrue(source.contains("future.get(remaining, TimeUnit.NANOSECONDS)"));
        assertTrue(source.contains("future.cancel(true)"));
    }

    @Test
    public void automaticSearchPreservesCancellationInterrupts() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/history/HistorySourceResolver.java");
        int start = source.indexOf("private static List<Candidate> searchCandidates");
        int end = source.indexOf("private static Resolved resolveCandidateBefore", start);
        String method = source.substring(start, end);

        assertTrue(method.contains("catch (InterruptedException e)"));
        assertTrue(method.contains("Thread.currentThread().interrupt()"));
        assertFalse(source.contains("catch (Throwable"));

        start = source.indexOf("private static List<Candidate> searchSite");
        end = source.indexOf("private static Resolved resolveCandidate", start);
        method = source.substring(start, end);
        assertTrue(method.contains("catch (InterruptedException e)"));
        assertTrue(method.contains("Thread.currentThread().interrupt()"));

        start = source.indexOf("private static Resolved resolveCandidate(");
        end = source.indexOf("private static TmdbItem cachedTmdb", start);
        method = source.substring(start, end);
        assertTrue(method.contains("catch (InterruptedException e)"));
        assertTrue(method.contains("Thread.currentThread().interrupt()"));
    }

    @Test
    public void unstableTmdbMetadataDoesNotFanOutDeletion() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/bean/History.java");
        int start = source.indexOf("private History deleteRelated(boolean global)");
        int end = source.indexOf("private static void notifyChanged()", start);
        String method = source.substring(start, end);

        assertTrue(method.contains("if (!identity.isEmpty() && !identity.startsWith(\"source:\") && Setting.isHistoryAggregationEffective())"));
        assertFalse(method.contains("findByTmdbId(getCid(), getTmdbId())"));
    }

    @Test
    public void seasonAwareDeletionDoesNotFanOutToOtherSeasons() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/bean/History.java");
        int start = source.indexOf("private History deleteRelated(boolean global)");
        int end = source.indexOf("private static void notifyChanged()", start);
        String method = source.substring(start, end);

        assertTrue("season-aware history deletion must retain the exact display identity",
                method.contains("identity.equals(HistoryDisplayPolicy.tmdbIdentity(item))"));
    }

    @Test
    public void playbackAggregationUsesMediaTypeQualifiedTmdbIdentity() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/bean/History.java");
        int start = source.indexOf("private static History findPlaybackByTmdb(");
        int end = source.indexOf("private static", start + 1);
        String method = source.substring(start, end);

        assertTrue(method.contains("findByTmdbIdentity"));
        assertFalse(method.contains("findByTmdbId("));
    }

    @Test
    public void exactKeyPlaybackHistoryUsesCurrentRouteRebinding() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/bean/History.java");
        int start = source.indexOf("private static History findPlayback(String key, List<String> vodNames");
        int end = source.indexOf("private static History findPlaybackByTmdb", start);
        String method = source.substring(start, end);

        assertTrue(method.contains("return copyForPlaybackKey(history, key, flags, history);"));
        assertFalse(method.contains("if (isSeasonEligible(history, key, expectedSeason)) return history;"));
    }

    @Test
    public void globalClearOnlyDeletesTracksOwnedByHistoryRecords() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/bean/History.java");
        int start = source.indexOf("public static void deleteForDisplay()");
        int end = source.indexOf("public static void sync(", start);
        String method = source.substring(start, end);

        assertTrue(method.contains("getHistoryDao().findAll()"));
        assertTrue(method.contains("PlaybackProgressWriter.deleteAllFromUser(cid)"));
        assertFalse(method.contains("getTrackDao().delete(item.getKey())"));
        assertFalse(method.contains("getTrackDao().deleteAll()"));
    }

    @Test
    public void globalDisplayQueryIsIsolatedFromGenericHistoryGet() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/bean/History.java");
        assertTrue(source.contains("public static List<History> getForDisplay()"));
        assertTrue(source.contains("HistoryDao().findAll()") || source.contains("getHistoryDao().findAll()"));
        assertTrue(source.contains("if (!Setting.isGlobalHistoryEnabled()) return get();"));
    }

    @Test
    public void resolvedPlaybackRebindsHistoryToTargetMetadata() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            String source = read("app/src/" + mode + "/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
            int start = source.indexOf("private boolean checkHistory(Vod item)");
            int end = source.indexOf("private void enrichHistoryMeta", start);
            String method = source.substring(start, end);

            assertTrue(method.contains("mHistory.setVodName(item.getName())"));
            assertTrue(method.contains("mHistory.setVodPic(getInitialArtwork(item))"));
        }
    }

    @Test
    public void explicitResumeDoesNotDeleteSourceHistoryInIncognitoMode() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            String source = read("app/src/" + mode + "/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
            assertTrue(source.contains("resumeHistory == null && Setting.isIncognito()"));
        }
    }

    @Test
    public void leanbackSearchStaysUsableWhenSelectedSourceCannotResume() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java");
        int method = source.indexOf("public void onItemClick(Vod item)");
        int resume = source.indexOf("if (!item.isFolder() && isHistoryResume())", method);
        int leaving = source.indexOf("mLeavingForPlayback = true", method);
        assertTrue("history selection must resolve before the search page enters leaving state", resume > method && resume < leaving);
    }

    private static void assertContains(String path, String token) throws Exception {
        assertTrue(path + " must contain " + token, read(path).contains(token));
    }

    private static String read(String relative) throws Exception {
        Path path = Path.of(relative);
        if (!Files.exists(path) && relative.startsWith("app/")) path = Path.of(relative.substring(4));
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
