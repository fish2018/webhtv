package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbDetailActivityLayoutTest {

    @Test
    public void seasonSourceRoutesRefreshAndCarrySnapshotSelection() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = Files.readString(sourcePath, StandardCharsets.UTF_8);

        assertTrue(source.contains("refreshSeasonSourceRoutes();"));
        assertTrue(source.contains("switchSourceDetail(source, item)"));
        assertTrue(source.contains("source.seasonNumber()"));
        assertTrue(source.contains("source.sourceFlag()"));
        assertTrue(source.contains("source.episodeUrl()"));
        assertTrue(source.contains("source.resumeHistoryPayload()"));
    }

    @Test
    public void seasonSourceSwitchReplacesThePreviousSeasonResumePayload() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int sourceOverload = source.indexOf("private void switchSourceDetail(SourceMatch");
        int switchStart = source.indexOf("private void switchSourceDetail(", sourceOverload + 1);
        String switchBody = source.substring(switchStart, source.indexOf("private String sourceSwitchMark"));

        assertTrue("source switching must remove the previous season resume marker",
                switchBody.contains("intent.removeExtra(EXTRA_RESUME_FROM_HISTORY);")
                        && switchBody.contains("intent.removeExtra(EXTRA_RESUME_HISTORY_CID);")
                        && switchBody.contains("intent.removeExtra(EXTRA_RESUME_HISTORY_KEY);"));
        assertTrue("a season source may install only a currently restorable target payload",
                switchBody.contains("HistoryResumePayload.restore(resumeHistoryCid, resumeHistoryPayload)")
                        && switchBody.contains("intent.putExtra(EXTRA_RESUME_HISTORY_KEY, resumeHistoryPayload);"));
    }

    @Test
    public void automaticTmdbMatchUsesResolvedMediaTitleBeforeSearching() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int load = source.indexOf("private TmdbLoadResult loadTmdbResult()");
        int helper = source.indexOf("private AutoTmdbMatch searchResolvedTmdbMatch()");
        int queryFilter = source.indexOf("private List<String> automaticTmdbQueries");
        int exactTie = source.indexOf("private boolean shouldAcceptFirstExactTmdbCandidate");

        assertTrue(sourcePath + " is missing loadTmdbResult", load >= 0);
        assertTrue("automatic TMDB detail matching must use resolved title candidates before search",
                source.indexOf("AutoTmdbMatch autoMatch = searchResolvedTmdbMatch();", load) > load);
        assertTrue("automatic TMDB detail matching must run MediaTitleResolver for ai-title diagnostics",
                helper > load && source.indexOf("MediaTitleResolver resolver = new MediaTitleResolver();", helper) > helper);
        assertTrue("automatic TMDB detail matching must not fall back to obfuscated raw titles when parser cleaned them",
                queryFilter > helper && source.indexOf("shouldSkipRawTmdbQuery(rawTitle, resolution)", queryFilter) > queryFilter);
        int originalSearch = source.indexOf("AutoTmdbMatch match = searchResolvedTmdbMatch(rawTitle, resolution, attempted);", helper);
        int cleaned = source.indexOf("resolver.queryCleanedTitles(request, 4)", originalSearch);
        int aiFallback = source.indexOf("resolver.resolveWithAiFallback(aiRequest)", originalSearch);
        assertTrue("automatic TMDB detail matching must try code-cleaned title candidates before AI fallback",
                originalSearch > helper && cleaned > originalSearch && aiFallback > cleaned);
        assertTrue("automatic TMDB detail matching must accept exact same-title ties from TMDB search order",
                exactTie > 0 && source.indexOf("shouldAcceptFirstExactTmdbCandidate(best, second, keyword, sourceVod)", load) > load);
    }

    @Test
    public void searchResultKeywordFlowsIntoBothTmdbAutoMatchPaths() throws Exception {
        String collect = readFlavorJava("leanback", "com", "fongmi", "android", "tv", "ui", "activity", "CollectActivity.java");
        assertTrue("search result cards must pass the original search keyword to VideoActivity",
                collect.contains("VideoActivity.collect(this, item.getSiteKey(), item.getId(), item.getName(), pic, getWallPic(), getKeyword());"));

        String mobileFragment = readFlavorJava("mobile", "com", "fongmi", "android", "tv", "ui", "fragment", "CollectFragment.java");
        assertTrue("mobile search result cards must pass the original search keyword to VideoActivity",
                mobileFragment.contains("VideoActivity.collect(requireActivity(), item.getSiteKey(), item.getId(), item.getName(), pic, getWallPic(), getKeyword());"));

        String leanbackFragment = readFlavorJava("leanback", "com", "fongmi", "android", "tv", "ui", "fragment", "CollectFragment.java");
        assertTrue("leanback search result cards must pass the original search keyword to VideoActivity",
                leanbackFragment.contains("VideoActivity.collect(requireActivity(), item.getSiteKey(), item.getId(), item.getName(), item.getPic(), null, getKeyword());"));

        for (String flavor : List.of("leanback", "mobile")) {
            String source = readFlavorJava(flavor, "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
            int keywordGetter = source.indexOf("getSearchKeyword()");
            int autoMatch = source.indexOf("mTmdbUIAdapter.autoMatch(item.getName(), item, getSearchKeyword())");
            int extra = source.indexOf("search_keyword");

            assertTrue(flavor + " must read the search keyword from the detail Intent", keywordGetter >= 0 && extra >= 0);
            assertTrue(flavor + " must pass the search keyword into TMDB auto matching", autoMatch > keywordGetter);
        }
    }

    @Test
    public void independentTmdbDetailUsesSearchKeywordAfterCardNameAndBeforeCleanedAiFallback() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int getter = source.indexOf("private String getTmdbSearchKeyword()");
        int match = source.indexOf("private AutoTmdbMatch searchResolvedTmdbMatch(String rawTitle, @Nullable Vod sourceVod)");
        int card = source.indexOf("searchResolvedTmdbMatch(rawTitle, resolution, attempted)", match);
        int keyword = source.indexOf("searchResolvedTmdbMatch(rawTitle, searchKeyword,", card);
        int cleaned = source.indexOf("resolver.queryCleanedTitles(request, 4)", keyword);
        int ai = source.indexOf("resolver.resolveWithAiFallback(aiRequest)", cleaned);

        assertTrue("independent TMDB detail must read the search keyword extra", getter >= 0 && source.indexOf("search_keyword", getter) > getter);
        assertTrue("independent TMDB detail must keep card-name matching first", match >= 0 && card > match);
        assertTrue("independent TMDB detail must try the search keyword after card-name matching", keyword > card);
        assertTrue("independent TMDB detail must clean titles after search-keyword matching", cleaned > keyword);
        assertTrue("independent TMDB detail must keep AI as the last fallback", ai > cleaned);
    }

    @Test
    public void automaticTmdbMatchSkipsStaleCacheWhenParsedTitleDiffers() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int cached = source.indexOf("private TmdbItem getCachedTmdbMatch()");
        int compatible = source.indexOf("private boolean isCachedTmdbMatchCompatible");

        assertTrue(sourcePath + " is missing getCachedTmdbMatch", cached >= 0);
        assertTrue("cached TMDB matches must be checked against the current parsed title",
                compatible > cached && source.indexOf("if (!isCachedTmdbMatchCompatible(item)) return null;", cached) > cached);
        assertTrue("stale cached title F must not override parsed title 凡人修仙传",
                source.indexOf("new MediaTitleParser().cleanTitle(getTmdbRawTitle())", compatible) > compatible
                        && source.indexOf("normalize(item.getTitle()).equals(normalize(parsedTitle))", compatible) > compatible);
    }

    @Test
    public void playbackTmdbItemKeepsMatchedTitleForNativeEnhancedHeader() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int method = source.indexOf("private TmdbItem playbackTmdbItem()");
        int end = source.indexOf("private Vod playbackTmdbVod()", method);
        String body = source.substring(method, end);

        assertTrue("native enhanced playback must pass the matched TMDB title, not the noisy source title",
                body.contains("matchedTmdbTitle()"));
        assertTrue("native enhanced playback item must not replace TMDB title with vod.getName()",
                !body.contains("TextUtils.isEmpty(vod.getName()) ? matchedTmdbItem.getTitle() : vod.getName()"));
    }

    @Test
    public void tmdbDetailNormalizesCachedTitleBeforeNativeEnhancedPlayback() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int loadBundle = source.indexOf("private TmdbBundle loadTmdbBundle(TmdbItem item)");
        int normalize = source.indexOf("private TmdbItem normalizeTmdbItemTitle", loadBundle);
        int detailTitle = source.indexOf("private String tmdbDetailTitle", normalize);
        int playbackName = source.indexOf("private String playbackHistoryName()");
        int enrichVod = source.indexOf("private void enrichVod()");
        int manual = source.indexOf("private void applyManualTmdb(TmdbItem item)");
        int canonical = source.indexOf("private void applyTmdbResultNow(TmdbLoadResult result)");

        assertTrue("TMDB detail loading must normalize stale cached item titles from the detail payload",
                loadBundle >= 0 && source.indexOf("item = normalizeTmdbItemTitle(item, detail);", loadBundle) > loadBundle);
        assertTrue("title normalization must prefer detail name/title over cached item title",
                normalize > loadBundle && detailTitle > normalize
                        && source.indexOf("tmdbDetailTitle(item, detail)", normalize) > normalize
                        && source.indexOf("string(detail, \"name\")", detailTitle) > detailTitle
                        && source.indexOf("string(detail, \"title\")", detailTitle) > detailTitle);
        assertTrue("native enhanced playback history name must use normalized TMDB title",
                playbackName >= 0 && source.indexOf("coalesce(matchedTmdbTitle()", playbackName) > playbackName);
        assertTrue("detail page vod title must use normalized TMDB title",
                enrichVod >= 0 && source.indexOf("String title = matchedTmdbTitle();", enrichVod) > enrichVod);
        assertTrue("manual matching must route the normalized bundle through the matcher-saving refresh pipeline",
                manual >= 0 && source.indexOf("applyTmdbResultNow(new TmdbLoadResult(bundle, List.of()));", manual) > manual
                        && canonical >= 0 && source.indexOf("saveTmdbMatch(bundle.item());", canonical) > canonical);
    }

    @Test
    public void manualTmdbReplacementUsesCompleteResultRefreshPipeline() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int manual = source.indexOf("private void applyManualTmdb(TmdbItem item)");
        int manualEnd = source.indexOf("private void resetManualTmdbPresentation()", manual);
        int reset = manualEnd;
        int resetEnd = source.indexOf("private void enrichVod()", reset);
        int canonical = source.indexOf("private void applyTmdbResultNow(TmdbLoadResult result)");
        int canonicalEnd = source.indexOf("private TmdbBundle loadTmdbBundle", canonical);

        assertTrue("manual and canonical TMDB refresh methods must be present in source order",
                manual >= 0 && manualEnd > manual && resetEnd > reset && canonical >= 0 && canonicalEnd > canonical);
        String manualBody = source.substring(manual, manualEnd);
        String resetBody = source.substring(reset, resetEnd);
        String canonicalBody = source.substring(canonical, canonicalEnd);
        int clearPresentation = manualBody.indexOf("resetManualTmdbPresentation();");
        int applyResult = manualBody.indexOf("applyTmdbResultNow(new TmdbLoadResult(bundle, List.of()));");
        int episodeMediaReset = canonicalBody.indexOf("lastEpisodeMediaSeason = Integer.MIN_VALUE;");
        int episodeRender = canonicalBody.indexOf("renderEpisodes();");

        assertTrue("manual TMDB replacement must clear stale presentation before applying the new result",
                clearPresentation >= 0 && applyResult > clearPresentation);
        assertTrue("manual TMDB replacement must reset season selection and cached episode rendering",
                resetBody.contains("tmdbEpisodeDetailGeneration++;")
                        && resetBody.contains("selectedEpisode = null;")
                        && resetBody.contains("selectedSeasonNumber = -1;")
                        && resetBody.contains("clearEpisodeRenderCaches();")
                        && resetBody.contains("resetEpisodeRange();"));
        assertTrue("manual TMDB replacement must clear all season-level TMDB caches to prevent stale data leaks",
                resetBody.contains("tmdbSeasonEpisodes.clear();")
                        && resetBody.contains("tmdbSeasonCast.clear();")
                        && resetBody.contains("tmdbSeasonPhotos.clear();")
                        && resetBody.contains("loadingSeasons.clear();"));
        assertTrue("manual TMDB replacement must immediately remove old episode cards and TMDB rails",
                resetBody.contains("episodeAdapter.setItems(List.of(), Map.of(), null);")
                        && resetBody.contains("episodePhotoAdapter.setItems(List.of());")
                        && resetBody.contains("castAdapter.setItems(List.of());")
                        && resetBody.contains("creatorAdapter.setItems(List.of());")
                        && resetBody.contains("relatedAdapter.setItems(List.of());")
                        && resetBody.contains("personalTmdbAdapter.setItems(List.of());")
                        && resetBody.contains("personalDoubanAdapter.setItems(List.of());")
                        && resetBody.contains("personalAiAdapter.setItems(List.of());"));
        assertFalse("manual TMDB replacement must not keep a partial duplicate binding pipeline",
                manualBody.contains("applyTmdbBundle(bundle);") || manualBody.contains("bindPage();") || manualBody.contains("loadTmdbMediaBlocks(bundle);"));
        assertTrue("manual TMDB replacement must defer the second episode viewport rebind until the search dialog closes",
                manualBody.contains("scheduleManualTmdbEpisodeRebind(applyGeneration, bundle.item());"));
        assertTrue("the canonical TMDB refresh must invalidate the episode media season before re-rendering cards",
                episodeMediaReset >= 0 && episodeRender > episodeMediaReset);
        assertTrue("the canonical TMDB refresh must reload cast, photos, recommendations, and season media",
                canonicalBody.contains("binding.getRoot().post(() -> loadTmdbMediaBlocks(bundle));"));
    }

    @Test
    public void manualTmdbMatchReloadsCrossSourceHistoryBeforeRenderingEpisodes() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int canonical = source.indexOf("private void applyTmdbResultNow(TmdbLoadResult result)");
        int canonicalEnd = source.indexOf("private TmdbBundle loadTmdbBundle", canonical);
        assertTrue("canonical TMDB refresh must exist", canonical >= 0 && canonicalEnd > canonical);
        String body = source.substring(canonical, canonicalEnd);
        int applyBundle = body.indexOf("applyTmdbBundle(bundle);");
        int reloadHistory = body.indexOf("reloadHistoryAfterTmdbMatch();");
        int renderEpisodes = body.indexOf("renderEpisodes();");

        assertTrue("TMDB identity must be applied before history is re-resolved",
                applyBundle >= 0 && reloadHistory > applyBundle);
        assertTrue("cross-source history must be re-resolved before episode cards and resume labels render",
                renderEpisodes > reloadHistory);
        assertTrue("history reload must use the explicit matched TMDB identity",
                source.contains("History.findPlayback(getHistoryKey(), List.of(vod.getName(), getNameText()), vod.getFlags(), matchedTmdbItem, sourceTitleSeasonNumber())"));
    }

    @Test
    public void tmdbHistoryReloadReappliesUntouchedPlaybackSelection() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int canonical = source.indexOf("private void applyTmdbResultNow(TmdbLoadResult result)");
        int canonicalEnd = source.indexOf("private TmdbBundle loadTmdbBundle", canonical);
        String canonicalBody = source.substring(canonical, canonicalEnd);
        int reloadHistory = canonicalBody.indexOf("reloadHistoryAfterTmdbMatch();");
        int reapplySelection = canonicalBody.indexOf("applyReloadedHistorySelection();");
        int renderEpisodes = canonicalBody.indexOf("renderEpisodes();");
        int reapply = source.indexOf("private void applyReloadedHistorySelection()");
        int reapplyEnd = source.indexOf("private float getInlinePlaybackSpeed()", reapply);

        assertTrue("TMDB history reload must reapply its resolved line before rendering episodes",
                reloadHistory >= 0 && reapplySelection > reloadHistory && renderEpisodes > reapplySelection);
        assertTrue("reloaded history selection helper must exist", reapply >= 0 && reapplyEnd > reapply);
        String reapplyBody = source.substring(reapply, reapplyEnd);
        assertTrue("async history must not override a user's manual playback selection",
                source.contains("private boolean playbackSelectionTouched;")
                        && source.contains("playbackSelectionTouched = false;")
                        && reapplyBody.contains("if (playbackSelectionTouched")
                        && reapplyBody.contains("selectedFlag = findInitialFlag(vod.getFlags());")
                        && reapplyBody.contains("selectedEpisode = null;")
                        && reapplyBody.contains("renderFlagSelection();"));
        String normalizedSource = source.replace("\r\n", "\n");
        assertTrue("line and episode interactions must mark the playback selection as user-controlled",
                normalizedSource.contains("playbackSelectionTouched = true;\n                selectedFlag = flag;")
                        && normalizedSource.contains("playbackSelectionTouched = true;\n        selectedEpisode = episode;"));
    }

    @Test
    public void asyncSeasonLoadForcesEpisodeRebindAfterLayoutSettles() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int fetch = source.indexOf("private void fetchSeasonIfNeeded(int seasonNumber, boolean refresh)");
        int fetchEnd = source.indexOf("private void refreshFirstSeasonIfStaleSplit", fetch);

        assertTrue("TMDB season fetch method must be present", fetch >= 0 && fetchEnd > fetch);
        String fetchBody = source.substring(fetch, fetchEnd);
        int storeEpisodes = fetchBody.indexOf("tmdbSeasonEpisodes.put(seasonNumber, episodes);");
        int renderEpisodes = fetchBody.indexOf("renderEpisodes();", storeEpisodes);
        int forceRebind = fetchBody.indexOf("binding.episodeContainer.post(() -> {", renderEpisodes);

        assertTrue("async TMDB season data must render before scheduling a forced visible-card rebind",
                storeEpisodes >= 0 && renderEpisodes > storeEpisodes && forceRebind > renderEpisodes);
        assertTrue("the delayed rebind must ignore stale TMDB requests and force every visible card to refresh",
                fetchBody.indexOf("if (!isTmdbRequestCurrent(generation, item)) return;", forceRebind) > forceRebind
                        && fetchBody.indexOf("rerenderEpisodeViewportOnly(false, true, true);", forceRebind) > forceRebind);
    }

    @Test
    public void manualTmdbEpisodeRebindWaitsForWindowFocusBeforeNextFrame() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int schedule = source.indexOf("private void scheduleManualTmdbEpisodeRebind(int generation, TmdbItem item)");
        int flush = source.indexOf("private void flushPendingManualTmdbEpisodeRebind()", schedule);
        int applyManual = source.indexOf("private void applyManualTmdb(TmdbItem item)", flush);
        int windowFocus = source.indexOf("public void onWindowFocusChanged(boolean hasFocus)");
        int windowFocusEnd = source.indexOf("protected void onResume()", windowFocus);

        assertTrue("manual TMDB episode rebind helpers and window focus callback must be present",
                schedule >= 0 && flush > schedule && applyManual > flush && windowFocus >= 0 && windowFocusEnd > windowFocus);
        String scheduleBody = source.substring(schedule, flush);
        String flushBody = source.substring(flush, applyManual);
        String windowFocusBody = source.substring(windowFocus, windowFocusEnd);

        assertTrue("manual rebind scheduling must remember only the latest TMDB request and wait while the dialog owns focus",
                source.contains("private int pendingManualTmdbEpisodeRebindGeneration = -1;")
                        && source.contains("private TmdbItem pendingManualTmdbEpisodeRebindItem;")
                        && scheduleBody.contains("pendingManualTmdbEpisodeRebindGeneration = generation;")
                        && scheduleBody.contains("pendingManualTmdbEpisodeRebindItem = item;")
                        && scheduleBody.contains("if (hasWindowFocus()) flushPendingManualTmdbEpisodeRebind();")
                        && !scheduleBody.contains("rerenderEpisodeViewportOnly("));
        assertTrue("regaining Activity window focus must flush the deferred rebind",
                windowFocusBody.indexOf("super.onWindowFocusChanged(hasFocus);") >= 0
                        && windowFocusBody.indexOf("if (hasFocus) flushPendingManualTmdbEpisodeRebind();")
                        > windowFocusBody.indexOf("super.onWindowFocusChanged(hasFocus);"));
        assertTrue("the deferred rebind must run on the next frame and reject destroyed or stale Activity state",
                flushBody.contains("binding.episodeContainer.postOnAnimation(() -> {")
                        && flushBody.contains("if (isFinishing() || isDestroyed()) return;")
                        && flushBody.contains("if (generation != tmdbApplyGeneration || !isSameTmdbItem(item, matchedTmdbItem)) return;")
                        && flushBody.contains("rerenderEpisodeViewportOnly(false, true, true);"));
    }

    @Test
    public void fusionDetailBackdropDrawsBehindSystemBars() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void applyDetailEdgeToEdge()");
        int init = source.indexOf("protected void initView(Bundle savedInstanceState)");
        int theme = source.indexOf("private void applyDetailTheme()");

        assertTrue(sourcePath + " is missing applyDetailEdgeToEdge", method >= 0);
        assertTrue("TMDB detail must draw the backdrop behind the system bars",
                source.indexOf("WindowCompat.setDecorFitsSystemWindows(window, false)", method) > method);
        assertTrue("TMDB detail status bar must stay transparent over the backdrop",
                source.indexOf("window.setStatusBarColor(Color.TRANSPARENT)", method) > method);
        assertTrue("TMDB detail navigation bar must stay transparent over the backdrop",
                source.indexOf("window.setNavigationBarColor(Color.TRANSPARENT)", method) > method);
        assertTrue("TMDB detail must keep system bar icon contrast in sync with the detail theme",
                source.indexOf("setAppearanceLightStatusBars", method) > method);
        assertTrue("TMDB detail must configure edge-to-edge during initialization",
                source.indexOf("applyDetailEdgeToEdge();", init) > init);
        assertTrue("TMDB detail must re-apply edge-to-edge after theme changes",
                source.indexOf("applyDetailEdgeToEdge();", theme) > theme);
    }

    @Test
    public void fusionInlinePlayerButtonsUsePlayerButtonSettings() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void applyInlinePlayerButtonSettings()");
        int update = source.indexOf("private void updateInlineButtons(boolean playing)");
        int call = source.indexOf("applyInlinePlayerButtonSettings();", update);

        assertTrue(sourcePath + " is missing applyInlinePlayerButtonSettings", method >= 0);
        assertTrue("inline player buttons must apply settings after dynamic visibility is recalculated", call > update);
        assertTrue("wide fusion buttons must use PlayerButtonSetting order and visibility",
                source.indexOf("PlayerButtonSetting.applyOrder((ViewGroup) binding.playerActionRow.getChildAt(0)", method) > method);
        assertTrue("fusion fullscreen button must be mapped to player button settings",
                source.indexOf("buttons.put(PlayerButtonSetting.FULLSCREEN, binding.playerFullscreenAction)", method) > method);
        assertTrue("fusion refresh button must be mapped so hiding reset hides refresh",
                source.indexOf("buttons.put(PlayerButtonSetting.RESET, binding.playerRefresh)", method) > method);
        assertTrue("fusion source button must be mapped to the change setting",
                source.indexOf("buttons.put(PlayerButtonSetting.CHANGE, binding.playerChangeSource)", method) > method);
    }

    @Test
    public void fusionOverlayButtonsDoNotFollowPlayerButtonSettings() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        // 融合模式全屏播放器的悬浮/图标按钮只根据集数、锁定、功能可用性显示，不受「播放器按钮设置」控制
        // （PlayerButtonSetting 只控制 playerActionRow 的横向文字按钮）。这些按钮的可见性行在
        // updateInlineButtons(...) 中，用 detailControlView(R.id.X).setVisibility(...) 更新。
        for (String id : List.of("prev", "next", "fullscreen", "cast", "danmaku")) {
            int line = source.indexOf("detailControlView(R.id." + id + ", View.class).setVisibility(");
            assertTrue("missing detailControlView visibility line for R.id." + id, line >= 0);
            int lineEnd = source.indexOf(';', line);
            String stmt = source.substring(line, lineEnd);
            assertFalse("fusion overlay button R.id." + id + " must not follow PlayerButtonSetting", stmt.contains("PlayerButtonSetting"));
        }
    }

    @Test
    public void fusionInlineSettingsButtonOpensFullPlayerControls() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int setup = source.indexOf("private void setupMobileInlineControl()");
        int setupEnd = source.indexOf("private void setupMobileInlineParse()", setup);
        String body = source.substring(setup, setupEnd);

        assertTrue("fusion settings button must open the full player control dialog",
                body.contains("detailControlView(R.id.setting, View.class).setOnClickListener(guarded(this::showInlineControlDialog));"));
        assertTrue("fusion settings button must not open the display-only dialog",
                !body.contains("detailControlView(R.id.setting, View.class).setOnClickListener(guarded(this::showInlineDisplay));"));

        Path dialogPath = Path.of("src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "ControlDialog.java");
        if (!Files.exists(dialogPath)) dialogPath = Path.of("app").resolve(dialogPath);
        String dialog = new String(Files.readAllBytes(dialogPath), StandardCharsets.UTF_8);
        int inline = dialog.indexOf("public ControlDialog inline(TmdbDetailActivity activity)");
        int inlineEnd = dialog.indexOf("public ControlDialog history(History history)", inline);
        String inlineBody = dialog.substring(inline, inlineEnd);

        assertTrue("fusion control dialog must resolve duplicate button IDs from the inline action root",
                inlineBody.contains("activity.inlineControlDialogAction(R.id.danmaku)")
                        && !inlineBody.contains("activity.findViewById(R.id.danmaku)"));
    }

    @Test
    public void inlinePlayerLayoutsExposeOnlySupportedActionsInStableOrder() throws Exception {
        String nativeLayout = readLeanbackLayout("view_control_vod_action.xml");
        String fusionLayout = readLayout("activity_tmdb_detail.xml");
        List<String> nativeOrder = List.of("next", "prev", "episodes", "reset", "search", "change2", "fullscreen", "player", "decode", "playParams", "panDiagnostic", "codecCapability", "speed", "scale", "actionQuality", "lut", "karaoke", "immersiveAudio", "text", "audio", "video", "opening", "ending", "danmaku", "adFeedback", "title", "cast", "timer", "repeat");
        List<String> fusionOrder = List.of("playerNext", "playerPrev", "playerEpisodes", "playerRefresh", "playerChangeSource", "playerSearch", "playerFullscreenAction", "playerExternal", "playerDecode", "playerPlayParams", "playerMultiThreadProxy", "playerCodecCapability", "playerSpeed", "playerScale", "playerQuality", "playerLut", "playerParse", "playerDisplay", "playerTextTrack", "playerAudioTrack", "playerVideoTrack", "playerOpening", "playerEnding", "playerDanmaku", "playerAdFeedback", "playerChapter", "playerRepeat");

        assertAndroidIdOrder("native leanback player control order", nativeLayout, nativeOrder);
        assertAndroidIdOrder("fusion inline player control order", fusionLayout, fusionOrder);
        for (String id : List.of("actionParse", "display")) {
            assertFalse("native leanback layout must not expose unbound action " + id, nativeLayout.contains("@+id/" + id));
        }
        for (String id : List.of("playerPanDiagnostic", "playerKaraoke", "playerImmersiveAudio", "playerCastAction", "playerTimer")) {
            assertFalse("fusion layout must not expose unsupported action " + id, fusionLayout.contains("@+id/" + id));
        }

        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int method = source.indexOf("private void setupHorizontalFocusChain()");
        int scale = source.indexOf("binding.playerScale", method);
        int quality = source.indexOf("binding.playerQuality", method);
        int lut = source.indexOf("binding.playerLut", method);

        assertTrue("fusion inline focus chain must keep 画质 before LUT like native leanback player",
                method >= 0 && scale > method && scale < quality && quality < lut);
    }

    @Test
    public void inlinePlayerExposesAdFeedbackAcrossDetailPlaybackModes() throws Exception {
        String layout = readLayout("activity_tmdb_detail.xml");
        String mobileAction = readLayout("view_control_vod_action_tmdb.xml");
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");

        assertAndroidIdHasAttribute("fusion inline ad feedback action", layout, "playerAdFeedback", "android:text=\"@string/play_ad_feedback\"");
        assertAndroidIdHasAttribute("mobile inline ad feedback action", mobileAction, "adFeedback", "android:text=\"@string/play_ad_feedback\"");

        int update = source.indexOf("private void updateInlineButtons(boolean playing)");
        int apply = source.indexOf("applyInlinePlayerButtonSettings();", update);
        assertTrue("inline ad feedback visibility must be recalculated before rebuilding the action focus chain",
                update >= 0
                        && source.indexOf("binding.playerAdFeedback.setVisibility(inlineAdFeedback ? View.VISIBLE : View.GONE);", update) > update
                        && source.indexOf("detailActionView(R.id.adFeedback, View.class).setVisibility(inlineAdFeedback ? View.VISIBLE : View.GONE);", update) > update
                        && apply > source.indexOf("binding.playerAdFeedback.setVisibility", update));
        assertTrue("wide and mobile inline controls must dispatch the ad feedback action",
                source.contains("binding.playerAdFeedback.setOnClickListener(guarded(this::onInlineAdFeedback));")
                        && source.contains("detailActionView(R.id.adFeedback, View.class).setOnClickListener(guarded(this::onInlineAdFeedback));"));
        assertTrue("detail inline ad feedback must only require a seekable VOD timeline, not AI config",
                source.contains("private boolean isInlineAdFeedbackEnabled()")
                        && source.contains("Setting.isAdblock() && !player().isLive() && player().getDuration() > 0"));
        assertFalse("AI must no longer gate the ad feedback button",
                source.contains("Setting.isAiConfigReady() && Setting.isAdblock() && Setting.isAiAdDetection()"));
        assertTrue("detail inline playback must route ad feedback through the shared controller",
                source.contains("private void submitInlineAdFeedback()")
                        && source.contains("controller.onMarkedInterval(start, end)")
                        && source.contains("controller.onQuickReport(mAdFeedbackHost.cachedEvidence())")
                        && source.contains("new AdFeedbackController(mAdFeedbackHost)"));
        assertTrue("marked interval must read the position before clearing the mark so a released player keeps it",
                source.contains("long end = mAdFeedbackHost.safePositionMs();")
                        && source.indexOf("long end = mAdFeedbackHost.safePositionMs();")
                                < source.indexOf("long start = mAdMarkStartMs;"));
        assertTrue("source or episode switch must invalidate in-flight attribution",
                source.contains("private void resetAdFeedback()")
                        && source.contains("mAdFeedback.invalidate();")
                        && source.contains("mAdFeedbackHost.invalidateEvidence();")
                        && source.contains("resetAdFeedback();"));
        assertTrue("playback host must feed the site baseline or the domain channel stays dead",
                source.contains("private void recordAdFeedbackHost()")
                        && source.contains("mAdFeedbackHost.recordPlaybackHost();")
                        && source.contains("recordAdFeedbackHost(); // 播放地址已确定，记入本站域名基线"));
        assertTrue("the feedback dialog must be closed on destroy to avoid leaking the activity",
                source.contains("if (mAdFeedbackDialog != null) mAdFeedbackDialog.close();"));
        assertTrue("long press must arm the interval marking mode",
                source.contains("private boolean onInlineAdFeedbackLongPress()")
                        && source.contains("binding.playerAdFeedback.setOnLongClickListener(view -> onInlineAdFeedbackLongPress());")
                        && source.contains("detailActionView(R.id.adFeedback, View.class).setOnLongClickListener(view -> onInlineAdFeedbackLongPress());"));
        assertTrue("confirmed plans must be applied through the shared applier",
                source.contains("AdRulePlanApplier.apply(")
                        && source.contains("case APPLIED -> R.string.ad_interval_rule_saved;"));
        assertFalse("the duplicated per-activity AI request builder must be gone",
                source.contains("private AdDetectionRequest buildInlineAdDetectionRequest()")
                        || source.contains("new AiAdDetectionService(config).analyze(request)"));
    }

    /**
     * 内嵌快搜靠反射调用两个 flavor 各自的 QuickSearchDialog，编译器管不到。
     * 任一方法被改名/删掉都会静默退化成"点搜索没反应"，只能在这里钉住契约。
     */
    @Test
    public void inlineQuickSearchReflectionTargetsExistInBothFlavors() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");

        assertTrue("inline search must reuse the native incremental search stream, not a one-shot dialog",
                source.contains("inlineSearchModel().searchContent(sites, keyword, true);")
                        && source.contains("inlineSearchModel().getSearch().observe(this, result ->")
                        && source.contains("inlineSearchModel().getSearchProgress().observe(this, progress ->"));

        for (String flavor : List.of("leanback", "mobile")) {
            String dialog = readFlavorJava(flavor, "com", "fongmi", "android", "tv", "ui", "dialog", "QuickSearchDialog.java");
            assertTrue(flavor + " QuickSearchDialog must keep create() for inline search reflection",
                    dialog.contains("public static QuickSearchDialog create()"));
            assertTrue(flavor + " QuickSearchDialog must keep listener(QuickAdapter.OnClickListener) for inline search reflection",
                    dialog.contains("public QuickSearchDialog listener(QuickAdapter.OnClickListener listener)"));
            assertTrue(flavor + " QuickSearchDialog must keep items(List<Vod>) for inline search reflection",
                    dialog.contains("public QuickSearchDialog items(List<Vod> items)"));
            assertTrue(flavor + " QuickSearchDialog must keep show(FragmentActivity) for inline search reflection",
                    dialog.contains("public void show(FragmentActivity activity)"));
            assertTrue(flavor + " QuickSearchDialog must keep addAll(List<Vod>) for incremental inline search results",
                    dialog.contains("public void addAll(List<Vod> items)"));

            String adapter = readFlavorJava(flavor, "com", "fongmi", "android", "tv", "ui", "adapter", "QuickAdapter.java");
            assertTrue(flavor + " QuickAdapter must keep OnClickListener.onItemClick(Vod) for the inline search proxy",
                    adapter.contains("public interface OnClickListener") && adapter.contains("void onItemClick(Vod item);"));
        }

        String leanbackDialog = readFlavorJava("leanback", "com", "fongmi", "android", "tv", "ui", "dialog", "QuickSearchDialog.java");
        assertTrue("leanback QuickSearchDialog must keep setProgress for the TV site-progress readout",
                leanbackDialog.contains("public void setProgress(int current, int total, boolean finished)"));

        String mobileDialog = readFlavorJava("mobile", "com", "fongmi", "android", "tv", "ui", "dialog", "QuickSearchDialog.java");
        assertTrue("mobile QuickSearchDialog must keep the in-sheet re-search hooks used by inline search",
                mobileDialog.contains("public interface OnSearchListener")
                        && mobileDialog.contains("public QuickSearchDialog searchListener(OnSearchListener listener)")
                        && mobileDialog.contains("public void clear()"));

        assertTrue("inline search proxies must forward Object methods, otherwise hashCode/equals unbox a null",
                source.contains("if (method.getDeclaringClass() == Object.class) return method.invoke(this, args);"));
        assertTrue("closing inline search must dismiss the dialog, not just drop the reference",
                source.contains("invokeQuiet(dialog, \"dismissAllowingStateLoss\", new Class<?>[0]);"));
        int close = source.indexOf("private void closeInlineSearch()");
        assertTrue("inline search must clear its dialog reference before dismissing, or onDismiss re-enters closeInlineSearch",
                close >= 0
                        && source.indexOf("inlineQuickSearchDialog = null;", close) < source.indexOf("invokeQuiet(dialog, \"dismissAllowingStateLoss\"", close));
        assertTrue("reloading the detail page must tear down any open inline search",
                source.indexOf("closeInlineSearch();", source.indexOf("private void resetDetailState()")) > source.indexOf("private void resetDetailState()"));
    }

    /**
     * release 开启 minify 后混淆会改掉方法名，反射直接失效并静默退回全局搜索页，
     * 而 debug 包永远复现不出来 —— 只能靠这里守住 keep 规则。
     */
    @Test
    public void inlineQuickSearchReflectionTargetsSurviveMinification() throws Exception {
        Path rulesPath = Path.of("app", "proguard-rules.pro");
        if (!Files.exists(rulesPath)) rulesPath = Path.of("proguard-rules.pro");
        String rules = new String(Files.readAllBytes(rulesPath), StandardCharsets.UTF_8);

        // -keepclassmembernames implies allowshrinking, so reflection-only methods could still be
        // removed. The rule must be -keepclassmembers to survive R8.
        assertTrue("proguard must keep QuickSearchDialog members (not just names) for inline search reflection",
                rules.contains("-keepclassmembers class com.fongmi.android.tv.ui.dialog.QuickSearchDialog {"));
        for (String member : List.of("create()", "show(androidx.fragment.app.FragmentActivity)", "addAll(java.util.List)",
                "clear()", "listener(***)", "items(java.util.List)",
                "setProgress(int, int, boolean)", "searchListener(***)", "dismissListener(***)")) {
            assertTrue("proguard QuickSearchDialog keep rule is missing " + member, rules.contains(member));
        }
        assertTrue("proguard must keep the inherited dismissAllowingStateLoss reached by inline search reflection",
                rules.contains("-keepclassmembers class * extends androidx.fragment.app.DialogFragment {")
                        && rules.contains("public void dismissAllowingStateLoss();"));
        assertTrue("proguard must keep the QuickAdapter click interface used by the inline search proxy",
                rules.contains("-keep interface com.fongmi.android.tv.ui.adapter.QuickAdapter$OnClickListener { *; }"));
        assertTrue("proguard must keep QuickSearchDialog nested listener interfaces used by the inline search proxy",
                rules.contains("-keep interface com.fongmi.android.tv.ui.dialog.QuickSearchDialog$* { *; }"));
    }

    @Test
    public void tvDetailActionButtonsUseUnifiedSourceKeepTmdbThemeOrder() throws Exception {
        String layout = readLayout("activity_tmdb_detail.xml");

        assertAndroidIdOrder("fusion detail action order", layout, List.of("changeSource", "keepFusion", "rematchFusion", "themeMode"));
        assertAndroidIdOrder("panel detail action order", layout, List.of("changeSourceDetail", "keep", "rematch", "themeModeDetail"));
    }

    @Test
    public void themeActionButtonsHaveFallbackTextBeforeRuntimeThemeRefresh() throws Exception {
        String detailLayout = readLayout("activity_tmdb_detail.xml");
        String headerLayout = readLayout("view_tmdb_header.xml");
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int init = source.indexOf("private void initPage()");
        int labelRefresh = source.indexOf("updateThemeModeButtonLabels();", init);
        int visibilityRefresh = source.indexOf("updateDetailThemeButtonVisibility();", init);

        assertAndroidIdHasAttribute("top theme action", detailLayout, "themeModeTop", "android:text=\"@string/detail_theme_light\"");
        assertAndroidIdHasAttribute("fusion theme action", detailLayout, "themeMode", "android:text=\"@string/detail_theme_light\"");
        assertAndroidIdHasAttribute("panel theme action", detailLayout, "themeModeDetail", "android:text=\"@string/detail_theme_light\"");
        assertAndroidIdHasAttribute("TMDB header theme action", headerLayout, "tmdbThemeToggle", "android:text=\"@string/detail_theme_light\"");
        assertTrue("TMDB detail must set theme action labels before applying their initial visibility",
                init >= 0 && labelRefresh > init && visibilityRefresh > labelRefresh);
    }

    @Test
    public void defaultDetailPlaybackDefersLaunchUntilAfterCurrentInputDispatch() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int method = source.indexOf("private void playDefaultPlayback()");
        int methodEnd = source.indexOf("private ", method + 1);
        String body = source.substring(method, methodEnd);

        assertTrue("detail playback must reject repeated taps while a launch is pending",
                body.contains("if (defaultPlaybackLaunchPending) return;"));
        assertTrue("detail playback must leave the current click/input dispatch before launching VideoActivity",
                body.contains("ActivityLaunch.postOnAnimation(this, () ->"));
    }

    @Test
    public void colorfulDetailDoesNotKeepPlaybackServiceBoundBetweenEpisodeLaunches() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int method = source.indexOf("protected boolean shouldBindPlaybackService()");
        int methodEnd = source.indexOf("private ", method + 1);
        String body = method >= 0 && methodEnd > method ? source.substring(method, methodEnd) : "";

        assertTrue("detail page must decide whether its mode owns an inline player", method >= 0);
        assertTrue("colorful detail must leave PlaybackService ownership to each standalone VideoActivity",
                body.contains("return isFusionMode() || isPlayerMode();"));
    }

    @Test
    public void inlinePlaybackPublishesViewingRecordLifecycle() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int update = source.indexOf("private void updateInlineHistory(Episode item)");
        int updateEnd = source.indexOf("private ", update + 1);
        String updateBody = source.substring(update, updateEnd);
        int progress = source.indexOf("public void onTimeChanged(long time)");
        int progressEnd = source.indexOf("private final PlaybackService.NavigationCallback", progress);
        String progressBody = source.substring(progress, progressEnd);
        int stop = source.indexOf("private void stopInlinePlaybackSync()");
        assertTrue("TmdbDetailActivity is missing inline playback sync finalization", stop >= 0);
        int stopEnd = source.indexOf("private ", stop + 1);
        String stopBody = source.substring(stop, stopEnd);
        int close = source.indexOf("private void closeDetailFullscreenPlayer()");
        int closeEnd = source.indexOf("private ", close + 1);
        String closeBody = source.substring(close, closeEnd);
        int destroy = source.indexOf("protected void onDestroy()");
        int destroyEnd = source.indexOf("private ", destroy + 1);
        String destroyBody = source.substring(destroy, destroyEnd);
        int navigation = source.indexOf("private final PlaybackService.NavigationCallback mNavigationCallback");
        int navigationEnd = source.indexOf("private void updateInlineHistory(Episode item)", navigation);
        String navigationBody = source.substring(navigation, navigationEnd);
        int finish = source.indexOf("private void finishPlaybackToHome()");
        int finishEnd = source.indexOf("private ", finish + 1);
        String finishBody = source.substring(finish, finishEnd);

        assertTrue("inline playback must publish the selected history before player state callbacks", updateBody.contains("PlaybackEventCollector.get().updateHistory(history);"));
        assertTrue("inline playback must publish periodic progress for webhook and current-playback API", progressBody.contains("PlaybackEventCollector.get().onProgress(history, player());"));
        assertTrue("inline playback finalization must refresh history before sending stop", stopBody.contains("PlaybackEventCollector.get().updateHistory(history);")
                && stopBody.contains("PlaybackEventCollector.get().onStop(player());"));
        assertTrue("switching episode or flag must finalize the previous viewing record",
                updateBody.contains("if (inlineStarted && (!sameEpisode || !sameFlag)) stopInlinePlaybackSync();"));
        assertTrue("media-session stop must run the complete inline playback exit path", navigationBody.contains("finishPlaybackToHome();"));
        assertTrue("the media-session stop path must save, publish stop, and stop the player",
                finishBody.indexOf("saveInlineHistory();") >= 0
                        && finishBody.indexOf("stopInlinePlaybackSync();") > finishBody.indexOf("saveInlineHistory();")
                        && finishBody.indexOf("finishPlayback();") > finishBody.indexOf("stopInlinePlaybackSync();"));
        assertTrue("closing the detail player must send a final viewing-record event", closeBody.contains("stopInlinePlaybackSync();"));
        assertTrue("destroying an active inline player must send a final viewing-record event", destroyBody.contains("stopInlinePlaybackSync();"));
    }

    @Test
    public void inlinePlaybackReconnectRestoresExistingResultBeforeRetryingSourceResolution() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int disconnect = source.indexOf("public void onServiceDisconnected(ComponentName name)");
        int disconnectEnd = source.indexOf("@Override", disconnect + 1);
        int restore = source.indexOf("private void restoreInlinePlaybackIfNeeded()");
        int restoreEnd = source.indexOf("private ", restore + 1);
        int service = source.indexOf("protected void onServiceConnected()");
        int controller = source.indexOf("protected void onControllerConnected()");

        assertTrue("detail playback must observe service disconnects", disconnect >= 0 && disconnectEnd > disconnect);
        String disconnectBody = source.substring(disconnect, disconnectEnd);
        assertTrue("disconnecting inline playback must persist the last known position before detaching",
                disconnectBody.contains("saveInlineHistory();")
                        && disconnectBody.indexOf("saveInlineHistory();") < disconnectBody.indexOf("super.onServiceDisconnected(name);"));
        assertTrue("disconnecting inline playback must mark the session for reconnect before detaching",
                disconnectBody.contains("inlinePlaybackReconnectPending = true;")
                        && disconnectBody.indexOf("inlinePlaybackReconnectPending = true;") < disconnectBody.indexOf("super.onServiceDisconnected(name);"));
        assertTrue("inline playback must have a dedicated reconnect restore path", restore >= 0 && restoreEnd > restore);
        String restoreBody = source.substring(restore, restoreEnd);
        assertTrue("reconnect restore must require the previous inline result and an empty player",
                restoreBody.contains("inlinePlaybackReconnectPending")
                        && restoreBody.contains("currentInlineResult != null")
                        && restoreBody.contains("player().isEmpty()"));
        assertTrue("reconnect restore must resume from the position saved before detaching",
                restoreBody.contains("startInlinePlayer(currentInlineResult, getInlineResumePosition());"));
        assertTrue("service reconnect must attempt to restore an interrupted inline session",
                service >= 0 && source.indexOf("restoreInlinePlaybackIfNeeded();", service) > service);
        assertTrue("controller reconnect must attempt to restore an interrupted inline session",
                controller >= 0 && source.indexOf("restoreInlinePlaybackIfNeeded();", controller) > controller);
    }

    @Test
    public void inlinePlaybackDoesNotResolveSourceAgainWhileServiceReconnects() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int toggle = source.indexOf("private void toggleInlinePlayback()");
        int toggleEnd = source.indexOf("private void toggleInlineControls()", toggle);
        String body = toggle >= 0 && toggleEnd > toggle ? source.substring(toggle, toggleEnd) : "";

        assertTrue("playback toggle must wait for service reconnect instead of resolving the source again",
                body.contains("inlinePlaybackReconnectPending")
                        && body.indexOf("inlinePlaybackReconnectPending") < body.indexOf("onPlay();"));
    }

    @Test
    public void playbackPageHidesThemeActionsWhileEnhancedDetailKeepsThem() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int method = source.indexOf("private void updateDetailThemeButtonVisibility()");
        int methodEnd = source.indexOf("private void applyTemplateCardChrome", method);
        String body = source.substring(method, methodEnd);

        assertTrue("playback page must be detected independently from the detail style",
                body.contains("boolean playbackPage = isAutoPlayMode() || detailPlayerActive;"));
        assertTrue("playback page must hide the fusion-row theme action",
                body.contains("binding.themeMode.setVisibility(playbackPage ? View.GONE : (fusionMode ? (showMobileButton || showLargeScreenButton ? View.VISIBLE : View.GONE) : (showLargeScreenButton ? View.VISIBLE : View.GONE)));"));
        assertTrue("enhanced detail page must keep its theme action, but playback pages must hide it",
                body.contains("binding.themeModeDetail.setVisibility(fusionMode || playbackPage ? View.GONE : (showMobileButton || showLargeScreenButton ? View.VISIBLE : View.GONE));"));
    }

    @Test
    public void fusionInlineFullscreenConsoleMatchesNativeLeanbackStructure() throws Exception {
        String layout = readLayout("activity_tmdb_detail.xml");
        int bottom = layout.indexOf("android:id=\"@+id/playerBottom\"");
        int actionRow = layout.indexOf("android:id=\"@+id/playerActionRow\"", bottom);
        int seek = layout.indexOf("android:id=\"@+id/seek\"", actionRow);
        int detailHost = layout.indexOf("android:id=\"@+id/detailControlHost\"", bottom);

        assertTrue("fusion inline fullscreen console must keep native bottom scrim", bottom >= 0
                && layout.indexOf("android:background=\"@drawable/shape_controller_scrim\"", bottom) > bottom
                && layout.indexOf("android:paddingStart=\"24dp\"", bottom) > bottom
                && layout.indexOf("android:paddingTop=\"24dp\"", bottom) > bottom
                && layout.indexOf("android:paddingEnd=\"24dp\"", bottom) > bottom
                && layout.indexOf("android:paddingBottom=\"16dp\"", bottom) > bottom);
        assertTrue("fusion inline fullscreen console must place the action row before the seek row",
                bottom >= 0 && actionRow > bottom && seek > actionRow && detailHost > seek);
        assertTrue("fusion inline fullscreen seek row must be a full-width native CustomSeekView child",
                containsViewAttribute(layout, seek, "android:layout_width=\"match_parent\"")
                        && containsViewAttribute(layout, seek, "android:layout_marginTop=\"12dp\"")
                        && !containsViewAttribute(layout, seek, "android:layout_weight=\"1\"")
                        && !containsViewAttribute(layout, seek, "android:paddingEnd=\"8dp\""));
        int fullscreenIcon = layout.indexOf("android:id=\"@+id/playerFullscreen\"", bottom);
        assertTrue("fusion inline fullscreen console must not keep the extra fullscreen icon beside the seek bar",
                detailHost > bottom && (fullscreenIcon < 0 || fullscreenIcon > detailHost));
        assertNativeControlButton(layout, "playerNext", "8dp");
        assertNativeControlButton(layout, "playerPrev", "8dp");
        assertNativeControlButton(layout, "playerEpisodes", "12dp");
        assertNativeControlButton(layout, "playerRefresh", "8dp");
        assertNativeControlButton(layout, "playerChangeSource", "8dp");
        assertNativeControlButton(layout, "playerFullscreenAction", "8dp");
        assertNativeControlButton(layout, "playerQuality", "12dp");
        assertNativeControlButton(layout, "playerLut", "8dp");
    }

    @Test
    public void fusionInlineQualityAndVideoTrackLogicMatchesNativeLeanbackPlayer() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int canQuality = source.indexOf("private boolean canChangeInlineQuality()");
        int showQuality = source.indexOf("private void showInlineQuality()");
        int changeQuality = source.indexOf("private void changeInlineQuality(int position)");
        int updateButtons = source.indexOf("private void updateInlineButtons(boolean playing)");
        int updateMobileButtons = source.indexOf("private void updateMobileInlineButtons(boolean playing, boolean hasPlayer, int episodeCount, boolean hasTitle)");

        assertTrue("fusion quality button must only follow native URL multi-quality availability",
                canQuality >= 0
                        && source.indexOf("return hasInlineUrlQuality();", canQuality) > canQuality
                        && source.indexOf("isInlineVideoTrackAsQuality", canQuality) < 0);
        assertTrue("fusion quality dialog must not open the video-track dialog",
                showQuality >= 0
                        && changeQuality > showQuality
                        && source.indexOf("TrackDialog.create().type(C.TRACK_TYPE_VIDEO)", showQuality) < 0);
        assertTrue("fusion video-track button must stay independent from URL quality on TV",
                updateButtons >= 0
                        && source.indexOf("binding.playerVideoTrack.setVisibility(hasPlayer && player().haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE)", updateButtons) > updateButtons);
        assertTrue("fusion video-track button must stay independent from URL quality on mobile",
                updateMobileButtons >= 0
                        && source.indexOf("detailActionView(R.id.video, View.class).setVisibility(hasPlayer && player().haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE)", updateMobileButtons) > updateMobileButtons);
    }

    @Test
    public void fusionInlineLutQuickFocusMatchesNativeLeanbackPlayer() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        String layout = readLayout("activity_tmdb_detail.xml");
        int dispatch = source.indexOf("public boolean dispatchKeyEvent(KeyEvent event)");
        int focus = source.indexOf("private boolean focusInlineLutQuickContent()");
        int recyclerItem = source.indexOf("private boolean focusRecyclerItem(RecyclerView recycler)");
        int firstChild = source.indexOf("private boolean focusFirstChild(View view)");
        int controls = layout.indexOf("android:id=\"@+id/playerControls\"");
        int detailControls = layout.indexOf("android:id=\"@+id/detailControlHost\"");
        int lutQuick = layout.indexOf("android:id=\"@+id/lutQuick\"");

        assertTrue("fusion LUT quick panel must consume back and remote keys before detail/player controls",
                dispatch >= 0
                        && source.indexOf("binding.lutQuick.hideIfVisible()", dispatch) > dispatch
                        && source.indexOf("dispatchInlineLutQuickKey(event)", dispatch) > dispatch);
        assertTrue("fusion LUT quick panel must be layered above fullscreen control overlays",
                controls >= 0 && detailControls > controls && lutQuick > detailControls);
        assertTrue("fusion LUT quick panel must focus selected entry before falling back like native leanback",
                focus >= 0
                        && source.indexOf("binding.lutQuick.focusSelectedEntry()", focus) > focus
                        && source.indexOf("focusRecyclerItem(recycler)", focus) > focus
                        && source.indexOf("focusFirstChild(binding.lutQuick)", focus) > focus);
        assertTrue("fusion LUT quick panel must include native recycler focus fallback helpers",
                recyclerItem > focus && firstChild > recyclerItem);
    }

    @Test
    public void mobileFusionInlinePlayerActionLayoutExposesConfigContainer() throws Exception {
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "view_control_vod_action_tmdb.xml"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);

        assertTrue("mobile fusion action row must expose @id/container for PlayerButtonSetting.applyOrder",
                layout.contains("android:id=\"@+id/container\""));
    }

    @Test
    public void inlineControlsRemainAvailableBeforePlaybackStartsOrWhileLoading() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int toggle = source.indexOf("private void toggleInlineControls()");
        int show = source.indexOf("private void showInlineControls(boolean show, boolean focus)");
        int hide = source.indexOf("private void hideInlineControls()", show);
        String toggleBody = toggle >= 0 && show > toggle ? source.substring(toggle, show) : "";
        String showBody = show >= 0 && hide > show ? source.substring(show, hide) : "";

        assertTrue(sourcePath + " is missing inline controls methods", toggle >= 0 && show > toggle && hide > show);
        assertFalse("single tap controls must not require a started player", toggleBody.contains("!inlineStarted"));
        assertFalse("controls must remain available while initial loading or an error is visible", showBody.contains("!inlineStarted"));
        assertFalse("loading must not suppress controls needed for retry/fullscreen", showBody.contains("shouldBlockInlineControlsForLoading()"));
    }

    @Test
    public void inlineGesturesStayInteractiveBeforePlaybackStarts() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int touch = source.indexOf("private boolean onInlineTouch(View view, MotionEvent event)");
        int seeking = source.indexOf("public void onSeeking(long time)", touch);
        int singleTap = source.indexOf("public void onSingleTap()");
        int doubleTap = source.indexOf("public void onDoubleTap()", singleTap);
        int nextGesture = source.indexOf("public void onTouchEnd()", doubleTap);
        int flingUp = source.indexOf("public void onFlingUp()");
        int flingDown = source.indexOf("public void onFlingDown()", flingUp);
        String touchBody = touch >= 0 && seeking > touch ? source.substring(touch, seeking) : "";
        String singleTapBody = singleTap >= 0 && doubleTap > singleTap ? source.substring(singleTap, doubleTap) : "";
        String doubleTapBody = doubleTap >= 0 && nextGesture > doubleTap ? source.substring(doubleTap, nextGesture) : "";
        String flingUpBody = flingUp >= 0 && flingDown > flingUp ? source.substring(flingUp, flingDown) : "";

        assertTrue("inline touch must continue routing tap gestures before playback starts", touchBody.contains("inlineGestureDetector.onTouchEvent(event);"));
        assertFalse("inline touch must not require a started player", touchBody.contains("!inlineStarted"));
        assertFalse("inline touch must not require an attached player service", touchBody.contains("service() == null"));
        assertFalse("inline touch must not require a prepared player", touchBody.contains("player().isEmpty()"));
        assertTrue("playback-only fling gestures must remain guarded without a player",
                flingUpBody.contains("!inlineStarted") && flingUpBody.contains("player().isEmpty()"));
        assertTrue("single tap must toggle controls even before playback starts", singleTapBody.contains("toggleInlineControls();"));
        assertFalse("single tap must not repeatedly restart playback", singleTapBody.contains("onPlay();"));
        assertTrue("double tap must enter inline fullscreen before playback starts",
                doubleTapBody.contains("if (!inlineFullscreen)") && doubleTapBody.contains("enterInlineFullscreen();"));
        assertFalse("double tap must not restart an unstarted playback request", doubleTapBody.contains("if (!inlineStarted)"));
    }

    @Test
    public void repeatedEpisodeTapDoesNotRestartSamePendingInlinePlayback() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int helper = source.indexOf("private boolean isSamePendingInlinePlayback(Episode episode)");
        int select = source.indexOf("private void selectInlineEpisode(Episode episode)");
        int guard = source.indexOf("if (isSamePendingInlinePlayback(episode)) return;", select);
        int cancel = source.indexOf("cancelPendingInlinePlayback();", select);
        int adapter = source.indexOf("episodeAdapter = new TmdbEpisodeAdapter");
        int click = source.indexOf("public void onItemClick(Episode episode)", adapter);
        int longClick = source.indexOf("public void onItemLongClick", click);
        int toggle = source.indexOf("private void toggleInlinePlayback()");
        int toggleEnd = source.indexOf("private void toggleInlineControls()", toggle);
        String detailClickBody = click >= 0 && longClick > click ? source.substring(click, longClick) : "";
        String toggleBody = toggle >= 0 && toggleEnd > toggle ? source.substring(toggle, toggleEnd) : "";

        assertTrue("pending inline playback identity helper is missing", helper >= 0 && helper < select);
        assertTrue("same pending episode must be ignored before the active request is cancelled", guard > select && cancel > guard);
        assertTrue("detail episode cards must reuse the pending-playback dedupe path", detailClickBody.contains("selectInlineEpisode(episode);"));
        assertFalse("detail episode cards must not cancel and restart the same pending request directly",
                detailClickBody.contains("cancelPendingInlinePlayback();") || detailClickBody.contains("onPlay();"));
        assertTrue("play/retry controls must ignore the same episode while its request is already pending",
                toggleBody.contains("if (isSamePendingInlinePlayback(selectedEpisode)) return;")
                        && toggleBody.indexOf("if (isSamePendingInlinePlayback(selectedEpisode)) return;") < toggleBody.indexOf("onPlay();"));
    }

    @Test
    public void inlineFullscreenRemainsAvailableWithoutAPlayerInstance() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int toggle = source.indexOf("private void toggleInlineFullscreen()");
        int enterPip = source.indexOf("private void enterInlinePiP(boolean force)", toggle);
        String toggleBody = toggle >= 0 && enterPip > toggle ? source.substring(toggle, enterPip) : "";

        assertTrue("inline fullscreen toggle is missing", toggle >= 0 && enterPip > toggle);
        assertFalse("fullscreen layout must not depend on an attached player service", toggleBody.contains("service() == null"));
        assertFalse("fullscreen layout must not depend on a prepared player", toggleBody.contains("player().isEmpty()"));
        assertTrue("desktop fullscreen action must stay enabled before playback starts",
                source.contains("setButtonEnabled(binding.playerFullscreenAction, true);"));
        assertTrue("mobile fullscreen action must stay enabled before playback starts",
                source.contains("setButtonEnabled(detailControlView(R.id.fullscreen, View.class), true);"));
    }

    @Test
    public void inlinePlayerPersistentDisplayUsesUnifiedPlayerOsd() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String controller = readJava("com", "fongmi", "android", "tv", "ui", "player", "VodPlayerUiController.java");
        int method = source.indexOf("private void updateInlineDisplayPanel()");
        int end = source.indexOf("private void setButtonEnabled", method);
        String body = method >= 0 && end > method ? source.substring(method, end) : "";
        int initOsd = controller.indexOf("this.osd = new PlayerOsdController(");
        int suppressPersistent = source.indexOf("public boolean suppressPersistentOsd()");
        int suppressPersistentEnd = source.indexOf("@Override", suppressPersistent + 1);
        String suppressPersistentBody = suppressPersistent >= 0 && suppressPersistentEnd > suppressPersistent
                ? source.substring(suppressPersistent, suppressPersistentEnd) : "";
        int showControls = source.indexOf("private void showInlineControls(boolean show, boolean focus)");
        int hideControls = source.indexOf("private void hideInlineControls()", showControls);
        int hideDisplay = source.indexOf("private void hideInlineDisplayPanel()", hideControls);
        String showControlsBody = showControls >= 0 && hideControls > showControls ? source.substring(showControls, hideControls) : "";
        String hideControlsBody = hideControls >= 0 && hideDisplay > hideControls ? source.substring(hideControls, hideDisplay) : "";

        assertTrue(sourcePath + " is missing updateInlineDisplayPanel", method >= 0);
        assertTrue("inline playback should delegate persistent display policy to the shared PlayerOsdController",
                initOsd >= 0
                        && controller.indexOf("this.osd.setPersistentSuppressed(host.suppressPersistentOsd());", initOsd) > initOsd
                        && suppressPersistentBody.contains("return false;"));
        assertTrue("inline controls should suppress the shared OSD only on mobile, whose control bar renders its own title and time",
                showControlsBody.contains("inlineOsd.setSuppressed(Util.isMobile());")
                        && hideControlsBody.contains("inlineOsd.setSuppressed(false);"));
        assertTrue("the retired legacy display panel must not render alongside the shared OSD",
                !body.contains("binding.playerDisplay")
                        && !body.contains("Traffic.setSpeed"));
    }

    @Test
    public void mobileInlineCastReflectionKeepsR8MethodNames() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        Path proguardPath = findAppModulePath().resolve("proguard-rules.pro");
        String proguard = new String(Files.readAllBytes(proguardPath), StandardCharsets.UTF_8);
        int inlineCast = source.indexOf("protected void onInlineCast()");
        int keepRule = proguard.indexOf("-keepclassmembernames class com.fongmi.android.tv.ui.dialog.CastDialog");

        assertTrue(sourcePath + " is missing onInlineCast", inlineCast >= 0);
        assertTrue("inline cast reflects CastDialog.create", source.indexOf("getMethod(\"create\")", inlineCast) > inlineCast);
        assertTrue("inline cast reflects CastDialog.history", source.indexOf("getMethod(\"history\", History.class)", inlineCast) > inlineCast);
        assertTrue("inline cast reflects CastDialog.video", source.indexOf("getMethod(\"video\", videoClass)", inlineCast) > inlineCast);
        assertTrue("inline cast reflects CastDialog.fm", source.indexOf("getMethod(\"fm\", boolean.class)", inlineCast) > inlineCast);
        assertTrue("inline cast reflects CastDialog.show", source.indexOf("getMethod(\"show\", androidx.fragment.app.FragmentActivity.class)", inlineCast) > inlineCast);
        assertTrue("release R8 must keep CastDialog method names used by inline cast reflection", keepRule >= 0);
        assertTrue("release R8 must keep CastDialog.create name", proguard.indexOf("public static com.fongmi.android.tv.ui.dialog.CastDialog create();", keepRule) > keepRule);
        assertTrue("release R8 must keep CastDialog.history name", proguard.indexOf("public com.fongmi.android.tv.ui.dialog.CastDialog history(com.fongmi.android.tv.bean.History);", keepRule) > keepRule);
        assertTrue("release R8 must keep CastDialog.video name", proguard.indexOf("public com.fongmi.android.tv.ui.dialog.CastDialog video(com.fongmi.android.tv.bean.CastVideo);", keepRule) > keepRule);
        assertTrue("release R8 must keep CastDialog.fm name", proguard.indexOf("public com.fongmi.android.tv.ui.dialog.CastDialog fm(boolean);", keepRule) > keepRule);
        assertTrue("release R8 must keep CastDialog.show name", proguard.indexOf("public void show(androidx.fragment.app.FragmentActivity);", keepRule) > keepRule);
    }

    @Test
    public void mobileFusionDetailKeepsInlinePlayerActionsInsideOverlay() throws Exception {
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "activity_tmdb_detail.xml"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
        int playerSpacer = layout.indexOf("android:id=\"@+id/playerPanelSpacer\"");
        int dock = layout.indexOf("android:id=\"@+id/mobileFusionPlayerActionDock\"");
        int fusionActions = layout.indexOf("android:id=\"@+id/fusionActions\"");

        assertTrue("mobile fusion detail may keep a hidden legacy action dock for binding compatibility", dock >= 0);
        assertTrue("playerPanelSpacer (reserves inline player space in pageContent) must appear before action dock",
                playerSpacer >= 0 && playerSpacer < dock);
        assertTrue("mobile fusion player action dock must remain hidden between player spacer and detail actions", playerSpacer < dock && dock < fusionActions);

        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int update = source.indexOf("private void updateMobileInlineButtons(boolean playing");
        int dockMethod = source.indexOf("private void hideMobileFusionPlayerActionDock()");
        int restoreMethod = source.indexOf("private void restoreMobileInlinePlayerAction()");

        assertTrue(sourcePath + " is missing hideMobileFusionPlayerActionDock", dockMethod >= 0);
        assertTrue(sourcePath + " is missing restoreMobileInlinePlayerAction", restoreMethod >= 0);
        assertTrue("mobile inline buttons must hide the below-player action dock before choosing overlay visibility",
                source.indexOf("hideMobileFusionPlayerActionDock();", update) > update);
        assertTrue("non-fullscreen fusion detail must not move the shared action row into a visible below-player dock",
                !source.contains("binding.mobileFusionPlayerActionDock.addView(detailActionRoot"));
        assertTrue("source switches must restore the action row to the control overlay and hide the dock",
                source.indexOf("hideMobileFusionPlayerActionDock();", source.indexOf("private void resetDetailState()")) > source.indexOf("private void resetDetailState()"));
        assertTrue("fullscreen and non-fusion modes must keep the action row in the control overlay",
                source.indexOf("restoreMobileInlinePlayerAction();", dockMethod) > dockMethod);
    }

    @Test
    public void mobileFusionControlOverlayRoutesBlankTouchesToGestureDetector() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int touchHandler = source.indexOf("private boolean onInlineControlTouch(View view, MotionEvent event)");
        int nextMethod = source.indexOf("private boolean onInlinePanelKey", touchHandler);
        String handlerBody = touchHandler >= 0 && nextMethod > touchHandler ? source.substring(touchHandler, nextMethod) : "";

        assertTrue(sourcePath + " is missing onInlineControlTouch", touchHandler >= 0);
        assertTrue("the fusion control overlay must forward blank touches to the player gesture detector",
                handlerBody.contains("return onInlineTouch(view, event);"));
    }

    @Test
    public void fusionDetailBackdropCropsToFillScreen() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private boolean shouldCropBackdrop()");
        assertTrue(sourcePath + " is missing shouldCropBackdrop", method >= 0);

        int methodEnd = source.indexOf("\n    }", method);
        String body = source.substring(method, methodEnd);
        assertTrue("Fusion detail must center-crop artwork so portrait screens do not show top/bottom background bars",
                body.contains("return true;"));
    }

    @Test
    public void unmatchedTmdbDetailUsesAppWallpaperBackdrop() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int bind = source.indexOf("private void bindBackdrop()");
        int surface = source.indexOf("private void applyBackdropSurface(ThemeColors colors)");
        int wallpaper = source.indexOf("private boolean useAppWallpaperBackdrop()");
        int customWall = source.indexOf("protected boolean customWall()");

        assertTrue(sourcePath + " is missing bindBackdrop", bind >= 0);
        assertTrue(sourcePath + " is missing applyBackdropSurface", surface >= 0);
        assertTrue(sourcePath + " is missing useAppWallpaperBackdrop", wallpaper >= 0);
        assertTrue(sourcePath + " is missing customWall", customWall >= 0);
        int customWallEnd = source.indexOf("\n    }", customWall);
        assertTrue("unmatched TMDB detail needs the app wallpaper layer behind its transparent fallback",
                source.substring(customWall, customWallEnd).contains("return true;"));
        assertTrue("unmatched TMDB detail must not use the source poster as the large backdrop fallback",
                source.indexOf("bindBackdropImage(vod.getName(), wallpaperBackdrop ? \"\" : tmdbBackdropUrl(), wallpaperBackdrop ? \"\" : vod.getPic());", bind) > bind);
        assertTrue("unmatched TMDB detail must keep the hero layer visible so the wallpaper can be shaded",
                source.indexOf("TextUtils.isEmpty(image) && !useAppWallpaperBackdrop() ? View.GONE : View.VISIBLE") > bind);
        assertTrue("unmatched TMDB detail must make the detail background transparent over the app wallpaper",
                source.indexOf("useAppWallpaperBackdrop() ? Color.TRANSPARENT : backdropFallbackBackground(colors)", surface) > surface);
        assertTrue("wallpaper fallback must only apply after source detail has loaded and no TMDB detail matched",
                source.indexOf("return vod != null && matchedTmdbDetail == null;", wallpaper) > wallpaper);
    }

    @Test
    public void detailLoadsPersonalAiCacheBeforeSlowMediaBlocksFinish() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void loadTmdbMediaBlocks(TmdbBundle bundle)");
        int bind = source.indexOf("bindTmdbSection();", method);
        int earlyCache = source.indexOf("loadTmdbPersonalAiCache(bundle, currentVod, generation);", method);
        int task = source.indexOf("detailTasks.submit(Task.recommendationExecutor(), () ->", method);
        int merge = source.indexOf("relatedItems.clear();", method);
        int fullAi = source.indexOf("loadTmdbPersonalAi(bundle, currentVod", method);

        assertTrue(sourcePath + " is missing loadTmdbMediaBlocks", method >= 0);
        assertTrue("TMDB detail must bind the loading section before early AI cache lookup", bind > method);
        assertTrue("TMDB detail must read AI cache before slow media block loading starts", earlyCache > bind && earlyCache < task);
        assertTrue("TMDB detail must keep the early AI row while merging slow media blocks",
                merge > method && fullAi > merge && !source.substring(merge, fullAi).contains("personalAiItems.clear();"));
    }

    @Test
    public void earlyPersonalAiCacheRendersBeforeAsynchronousRatingEnrichment() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void loadTmdbPersonalAiCache(TmdbBundle bundle");
        int nextMethod = source.indexOf("private void loadTmdbPersonalAi(TmdbBundle bundle", method);
        String body = source.substring(method, nextMethod);
        int cachedApply = body.indexOf("applyTmdbPersonalAi(bundle, cachedAi, generation, false);");
        int enrich = body.indexOf("service.enrichTmdbPageRatingsAsync(cached.getPage(), enrichedPage -> {");
        int enrichedItems = body.indexOf("TmdbRecommendationRows.personalAi(enrichedPage.getItems()", enrich);
        int enrichedApply = body.indexOf("applyTmdbRatingEnrichment(bundle, personalAiItems, enrichedAi, generation);", enrichedItems);

        assertTrue(sourcePath + " is missing early personal AI cache loading", method >= 0 && nextMethod > method);
        assertTrue("cached AI cards must render immediately before rating enrichment",
                cachedApply >= 0 && enrich > cachedApply);
        assertTrue("cached AI rating enrichment must merge into the visible row without restoring hidden cards",
                enrichedItems > enrich && enrichedApply > enrichedItems);
    }
    @Test
    public void detailLoadsUseLifecycleScopeAndSeparateTmdbExecutor() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int load = source.indexOf("private void loadContent(@Nullable TmdbBundle reusableBundle)");
        int helper = source.indexOf("private boolean shouldLoadInitialStandaloneTmdbDetailInSinglePass", load);
        String body = source.substring(load, helper);
        int destroy = source.indexOf("protected void onDestroy()");
        int destroyEnd = source.indexOf("public String getSubtitlePlaybackKey()", destroy);
        String destroyBody = source.substring(destroy, destroyEnd);

        assertTrue("standalone detail must own a cancellable lifecycle task scope",
                source.contains("private final Task.Scope detailTasks = new Task.Scope(Task.executor());"));
        assertTrue("a new detail must cancel all work left by the previous load",
                body.contains("detailTasks.cancelAll();"));
        assertTrue("source detail coordination must be lifecycle scoped",
                body.contains("detailTasks.submit(() -> {"));
        assertTrue("TMDB detail work must not be queued behind coordinators on the same five-thread executor",
                body.contains("detailTasks.submitCallable(Task.largeExecutor(), this::loadTmdbResult)")
                        && !body.contains("Task.executor().submit(this::loadTmdbResult)"));
        assertTrue("leaving a detail must invalidate callbacks and interrupt its outstanding work",
                destroyBody.contains("loadGeneration++;") && destroyBody.contains("detailTasks.close();"));
    }
    @Test
    public void standaloneOmdbRatingUsesSharedLifecycleScopedClient() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
        int fetch = source.indexOf("private void fetchOmdbRating(String key)");
        int next = source.indexOf("private void addOmdbRatingChips", fetch);
        String body = source.substring(fetch, next);

        assertTrue("standalone detail OMDb work must stay inside the lifecycle scope",
                body.contains("detailTasks.submit(Task.recommendationExecutor(), () -> {"));
        assertTrue("standalone detail OMDb work must use the shared service",
                body.contains("OmdbService.fetch(imdb, omdbApiKey)"));
        assertFalse("standalone detail must not create one OkHttp client per OMDb request",
                body.contains("new okhttp3.OkHttpClient.Builder()"));
    }
    @Test
    public void standaloneDetailAppliesInitialTmdbResultInSinglePass() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int load = source.indexOf("private void loadContent(@Nullable TmdbBundle reusableBundle)");
        int helper = source.indexOf("private boolean shouldLoadInitialStandaloneTmdbDetailInSinglePass");
        int apply = source.indexOf("private void applyTmdbResult(TmdbLoadResult result)");

        assertTrue(sourcePath + " is missing standalone single-pass initial TMDB loading", load >= 0 && helper > load && apply > helper);
        assertTrue("standalone TMDB detail should wait for the initial TMDB bundle before the first page bind",
                source.indexOf("boolean singlePassStandaloneTmdb = shouldLoadInitialStandaloneTmdbDetailInSinglePass(reusableBundle, tmdbFuture);", load) > load
                        && source.indexOf("if (!singlePassStandaloneTmdb || finalVod == null)", load) > load
                        && source.indexOf("applyLoaded(finalVod, finalResult == null ? null : finalResult.bundle(), finalResult == null ? new ArrayList<>() : finalResult.searchItems(), finalError, true);", load) > load);
        assertTrue("single-pass loading must only apply to standalone TMDB detail without a reusable bundle",
                source.indexOf("return reusableBundle == null && tmdbFuture != null && activeTmdbBundle == null && Setting.isStandaloneTmdbDetailMode(getDetailMode());", helper) > helper);
        assertTrue("standalone initial loading must not use a delayed second TMDB rebind",
                !source.contains("INITIAL_STANDALONE_TMDB_RESULT_DEFER_MS")
                        && !source.contains("postDelayed(this::flushInitialStandaloneTmdbResult"));
    }

    @Test
    public void standaloneDetailPreloadsInitialSeasonBeforeFirstBind() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int load = source.indexOf("private void loadContent(@Nullable TmdbBundle reusableBundle)");
        int preload = source.indexOf("private TmdbLoadResult preloadInitialStandaloneSeason");
        int fetch = source.indexOf("private void fetchSeasonIfNeeded(int seasonNumber, boolean refresh)");

        assertTrue(sourcePath + " is missing initial standalone season preload", load >= 0 && preload > load && fetch > preload);
        assertTrue("standalone initial detail should preload the current season before the first UI bind",
                source.indexOf("if (singlePassStandaloneTmdb) loadedResult = preloadInitialStandaloneSeason(loadedResult, finalVod);", load) > load
                        && source.indexOf("tmdbService.season(bundle.item(), seasonNumber, tmdbConfig, bundle.detail(), false);", preload) > preload
                        && source.indexOf("seasonEpisodes.put(seasonNumber, episodes);", preload) > preload
                        && source.indexOf("return new TmdbLoadResult(withSeason, result.searchItems());", preload) > preload);
        assertTrue("initial season preload must use the same current-season data shape that fetchSeasonIfNeeded would later fill",
                source.indexOf("seasonCounts.put(seasonNumber, episodes.size());", preload) > preload
                        && source.indexOf("seasonCast.put(seasonNumber, tmdbService.seasonCast(season, tmdbConfig));", preload) > preload
                        && source.indexOf("seasonPhotos.put(seasonNumber, tmdbService.seasonPhotos(season, tmdbConfig));", preload) > preload);
    }

    @Test
    public void episodeDetailDismissRestoresLongPressedCardFocus() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int show = source.indexOf("private void showTmdbEpisodeDetail(Episode episode, int episodeNumber, RecyclerView returnRecycler)");
        int restore = source.indexOf("private void restoreEpisodeDetailFocus(RecyclerView recycler, Episode episode)", show);

        assertTrue("TMDB episode detail must define an exact-card focus restore helper", show >= 0 && restore > show);
        assertTrue("each episode list must provide its own recycler as the focus return target",
                source.contains("showTmdbEpisodeDetail(episode, episodeNumber, binding.episodeContainer);")
                        && source.contains("showTmdbEpisodeDetail(episode, episodeNumber, recycler);"));
        int dismiss = source.indexOf("OnDismissListener dismissListener", show);
        int movie = source.indexOf("// 电影场景", dismiss);
        String dismissBody = source.substring(dismiss, movie);
        assertTrue("closing episode detail must rerender and then restore the long-pressed episode card",
                dismissBody.contains("rerenderEpisodeViewportOnly(false, true, true);")
                        && dismissBody.contains("returnRecycler.post(() -> restoreEpisodeDetailFocus(returnRecycler, episode));"));
        int restoreEnd = source.indexOf("\n    private ", restore + 1);
        String restoreBody = source.substring(restore, restoreEnd);
        assertTrue("focus restoration must resolve the episode's exact adapter position instead of defaulting to the first column",
                restoreBody.contains("if (!(adapter instanceof TmdbEpisodeAdapter episodeAdapter)) return;")
                        && restoreBody.contains("int position = episodeAdapter.getPosition(episode);")
                        && restoreBody.contains("focusTmdbRecyclerItem(recycler, position);"));
    }

    @Test
    public void standaloneEpisodeModeToggleDoesNotForceSelectedScroll() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void toggleEpisodeViewMode()");
        int nextMethod = source.indexOf("private void updateEpisodeViewModeButton()", method);

        assertTrue(sourcePath + " is missing toggleEpisodeViewMode", method >= 0 && nextMethod > method);
        String body = source.substring(method, nextMethod);
        assertTrue("standalone TMDB episode mode toggle should preserve scroll instead of forcing selected-item alignment",
                body.contains("rerenderEpisodeViewportOnly(false);")
                        && !body.contains("rerenderEpisodeViewportOnly(true);"));
    }

    @Test
    public void detailThemeToggleRestylesDynamicViewsWithoutRebuildingEpisodes() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int cycle = source.indexOf("private void cycleThemeMode()");
        int cycleEnd = source.indexOf("private void applyDetailTheme()", cycle);
        int refresh = source.indexOf("private void refreshDetailThemeDynamicViews()");
        int refreshEnd = source.indexOf("private void clearExternalLinks()", refresh);

        assertTrue(sourcePath + " is missing cycleThemeMode", cycle >= 0 && cycleEnd > cycle);
        assertTrue(sourcePath + " is missing refreshDetailThemeDynamicViews", refresh >= 0 && refreshEnd > refresh);
        String cycleBody = source.substring(cycle, cycleEnd);
        String refreshBody = source.substring(refresh, refreshEnd);

        assertTrue("theme toggle should preserve episode scroll and card data instead of rebuilding the detail sections",
                cycleBody.contains("applyDetailTheme();")
                        && cycleBody.contains("refreshDetailThemeDynamicViews();")
                        && !cycleBody.contains("bindMeta();")
                        && !cycleBody.contains("bindExternalLinks();")
                        && !cycleBody.contains("renderSeasonSelection();")
                        && !cycleBody.contains("renderEpisodes();"));
        assertTrue("theme toggle still needs to restyle dynamic chips and external links in place",
                refreshBody.contains("styleMetaChips();")
                        && refreshBody.contains("styleExternalLinks();")
                        && refreshBody.contains("renderFlagSelection();")
                        && refreshBody.contains("updateSeasonButtonStates();")
                        && refreshBody.contains("updateEpisodeRangeButtonStates();"));
    }

    @Test
    public void standaloneEpisodeViewportRerenderOnlyNumbersCurrentPage() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void rerenderEpisodeViewportOnly(boolean scrollToSelection)");
        int nextMethod = source.indexOf("private void updateEpisodeRangeButtonStates()", method);

        assertTrue(sourcePath + " is missing rerenderEpisodeViewportOnly", method >= 0 && nextMethod > method);
        String body = source.substring(method, nextMethod);
        assertTrue("episode mode toggles must number only the current card page, not every visible episode",
                body.contains("List<Episode> pageItems = ranges.size() > 1 ? EpisodeRangePolicy.slice(displayEpisodes, ranges.get(episodeRangeIndex)) : displayEpisodes;")
                        && body.contains("Map<Episode, Integer> numbers = episodeNumbers(pageItems, episodes);")
                        && body.indexOf("Map<Episode, Integer> numbers = episodeNumbers(pageItems, episodes);") > body.indexOf("List<Episode> pageItems ="));
        assertTrue("episode mode toggles must not rebuild episode-number maps for every visible episode",
                !body.contains("episodeNumbers(visibleEpisodes, episodes);"));
    }

    @Test
    public void episodeSeasonResolutionIsCachedAcrossViewportOnlyRerenders() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int applyLoaded = source.indexOf("private void applyLoaded(Vod loadedVod, TmdbBundle bundle, List<TmdbItem> searchItems, String error, boolean allowMatchDialog)");
        int applyLoadedEnd = source.indexOf("private TmdbLoadResult loadTmdbResult()", applyLoaded);
        int applyBundle = source.indexOf("private void applyTmdbBundle(TmdbBundle bundle)");
        int applyBundleEnd = source.indexOf("private void showTmdbMatchDialog", applyBundle);
        int clearRenderCaches = source.indexOf("private void clearEpisodeRenderCaches()");
        int clearSeasonCache = source.indexOf("private void clearSeasonResolutionCache()", clearRenderCaches);
        int sourceResolver = source.indexOf("private List<Integer> sourceSeasonNumbers(List<Episode> episodes)", clearSeasonCache);
        int availableResolver = source.indexOf("private List<Integer> availableSeasonNumbers(List<Episode> episodes)", sourceResolver);
        int seasonRender = source.indexOf("private void renderSeasonSelection()", availableResolver);
        int bindSeason = source.indexOf("private void bindSeasonEpisodes(List<Episode> sourceEpisodes)", seasonRender);
        int bindTmdb = source.indexOf("private void bindTmdbEpisodes(List<Episode> sourceEpisodes, int tmdbSeason)", bindSeason);
        int fetchSeason = source.indexOf("private void fetchSeasonIfNeeded(int seasonNumber, boolean refresh)", bindTmdb);
        int fetchSeasonEnd = source.indexOf("private void refreshFirstSeasonIfStaleSplit", fetchSeason);
        int sourceEpisodeResolver = source.indexOf("private int explicitSourceSeasonNumber(Episode episode)", fetchSeasonEnd);
        int sourceEpisodeResolverEnd = source.indexOf("private int sourceSeasonNumber(String text)", sourceEpisodeResolver);

        assertTrue(sourcePath + " is missing cached episode-season resolution",
                applyLoaded >= 0 && applyLoadedEnd > applyLoaded
                        && applyBundle >= 0 && applyBundleEnd > applyBundle
                        && clearRenderCaches >= 0 && clearSeasonCache > clearRenderCaches && sourceResolver > clearSeasonCache
                        && availableResolver > sourceResolver && seasonRender > availableResolver
                        && bindSeason > seasonRender && bindTmdb > bindSeason && fetchSeason > bindTmdb && fetchSeasonEnd > fetchSeason
                        && sourceEpisodeResolver > fetchSeasonEnd && sourceEpisodeResolverEnd > sourceEpisodeResolver);
        String applyLoadedBody = source.substring(applyLoaded, applyLoadedEnd);
        String applyBundleBody = source.substring(applyBundle, applyBundleEnd);
        String clearRenderBody = source.substring(clearRenderCaches, clearSeasonCache);
        String clearSeasonBody = source.substring(clearSeasonCache, sourceResolver);
        String sourceResolverBody = source.substring(sourceResolver, availableResolver);
        String availableResolverBody = source.substring(availableResolver, seasonRender);
        String bindSeasonBody = source.substring(bindSeason, bindTmdb);
        String fetchSeasonBody = source.substring(fetchSeason, fetchSeasonEnd);
        String sourceEpisodeResolverBody = source.substring(sourceEpisodeResolver, sourceEpisodeResolverEnd);

        assertTrue("source season parsing must be cached by episode-list identity instead of reparsing every episode for every card",
                source.contains("private List<Episode> sourceSeasonCacheSource;")
                        && source.contains("private List<Integer> sourceSeasonCache = List.of();")
                        && sourceResolverBody.contains("if (episodes == sourceSeasonCacheSource) return sourceSeasonCache;")
                        && sourceResolverBody.contains("sourceSeasonCache = List.copyOf(sourceSeasonNumbers);"));
        assertTrue("episode-name season parsing must survive render-cache invalidation and refresh when an episode name changes",
                source.contains("private record SourceEpisodeSeason(String name, int season)")
                        && source.contains("private final Map<Episode, SourceEpisodeSeason> sourceEpisodeSeasonCache = new IdentityHashMap<>();")
                        && clearRenderBody.contains("clearSeasonResolutionCache();")
                        && !clearRenderBody.contains("sourceEpisodeSeasonCache.clear();")
                        && sourceEpisodeResolverBody.contains("SourceEpisodeSeason cached = sourceEpisodeSeasonCache.get(episode);")
                        && sourceEpisodeResolverBody.contains("TextUtils.equals(cached.name(), name)")
                        && sourceEpisodeResolverBody.contains("sourceEpisodeSeasonCache.put(episode, new SourceEpisodeSeason(name, sourceSeason));"));
        assertTrue("cached source-season values must be unboxed before comparison so equal values outside the Integer cache are not treated as different seasons",
                sourceEpisodeResolverBody.contains("for (int candidate : sourceSeasons)")
                        && !sourceEpisodeResolverBody.contains("for (Integer candidate : sourceSeasons)"));
        assertTrue("replacing the source must clear parsed episode-season entries separately from viewport caches",
                source.contains("private void clearSourceEpisodeSeasonCache()")
                        && source.contains("clearSourceEpisodeSeasonCache();")
                        && source.indexOf("clearSourceEpisodeSeasonCache();") < clearRenderCaches
                        && !applyLoadedBody.contains("clearSourceEpisodeSeasonCache();")
                        && applyLoadedBody.contains("clearEpisodeRenderCaches();"));
        assertTrue("full-list position and visibility calculations must reuse the cached season vector",
                sourceResolverBody.contains("sourceSeasonNumbers.add(explicitSourceSeasonNumber(episode));")
                        && !sourceResolverBody.contains("sourceSeasonNumbers.add(sourceSeasonNumber(episode));")
                        && source.contains("List<Integer> sourceSeasons = sourceSeasonNumbers(flag.getEpisodes());")
                        && source.contains("private int sourceSeasonNumberAt(List<Episode> episodes, int index, Episode episode)")
                        && source.contains("sourceSeasonNumberAt(episodes, index, episode)")
                        && source.contains("for (int i = 0; i < episodes.size(); i++) if (sourceSeasons.get(i) == selectedSeasonNumber)"));
        assertTrue("available-season filtering must be cached across grid/list viewport-only rerenders",
                source.contains("private List<Episode> availableSeasonCacheSource;")
                        && source.contains("private Flag availableSeasonCacheFlag;")
                        && source.contains("private List<Integer> availableSeasonCache = List.of();")
                        && availableResolverBody.contains("if (episodes == availableSeasonCacheSource && selectedFlag == availableSeasonCacheFlag) return availableSeasonCache;")
                        && availableResolverBody.contains("availableSeasonCache = EpisodeSeasonPolicy.resolveAvailableSeasons("));
        assertTrue("episode and TMDB-season mutations must invalidate both availability and visible-episode caches",
                applyBundleBody.contains("seasonEpisodeCounts.putAll(bundle.seasonCounts());")
                        && applyBundleBody.contains("clearSeasonResolutionCache();")
                        && clearRenderBody.contains("clearSeasonResolutionCache();")
                        && clearSeasonBody.contains("sourceSeasonCacheSource = null;")
                        && clearSeasonBody.contains("availableSeasonCacheSource = null;")
                        && clearSeasonBody.contains("clearVisibleEpisodeCache();")
                        && bindSeasonBody.contains("bindTmdbEpisodes(sourceEpisodes, tmdbSeason);")
                        && bindSeasonBody.contains("clearSeasonResolutionCache();")
                        && fetchSeasonBody.contains("seasonEpisodeCounts.put(seasonNumber, episodes.size());")
                        && fetchSeasonBody.contains("clearSeasonResolutionCache();"));
    }

    @Test
    public void standaloneEpisodeReverseUsesViewportOnlyRefreshForLargeLists() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int toggle = source.indexOf("private void toggleEpisodeReverse()");
        int nextToggle = source.indexOf("private void toggleEpisodeViewMode()", toggle);
        int rerender = source.indexOf("private void rerenderEpisodeViewportOnly(boolean scrollToSelection, boolean rebuildRanges)");
        int updateStates = source.indexOf("private void updateEpisodeRangeButtonStates()", rerender);

        assertTrue(sourcePath + " is missing reverse episode viewport helpers", toggle >= 0 && nextToggle > toggle && rerender >= 0 && updateStates > rerender);
        String toggleBody = source.substring(toggle, nextToggle);
        String rerenderBody = source.substring(rerender, updateStates);

        assertTrue("episode reverse should keep the long-list render path lightweight instead of rebinding seasons/TMDB metadata",
                toggleBody.contains("resetEpisodeRange();")
                        && toggleBody.contains("rerenderEpisodeViewportOnly(true, true);")
                        && !toggleBody.contains("renderEpisodes();"));
        assertTrue("episode reverse still needs to rebuild range labels because the visible order changes",
                rerenderBody.contains("if (rebuildRanges) renderEpisodeRanges(ranges);")
                        && rerenderBody.contains("else updateEpisodeRangeButtonStates();")
                        && rerenderBody.contains("List<Episode> pageItems = ranges.size() > 1 ? EpisodeRangePolicy.slice(displayEpisodes, ranges.get(episodeRangeIndex)) : displayEpisodes;")
                        && rerenderBody.indexOf("List<Episode> pageItems =") > rerenderBody.indexOf("if (rebuildRanges) renderEpisodeRanges(ranges);"));
    }

    @Test
    public void standaloneMobileEpisodeCardPagesUseLargerButBoundedGroups() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int constant = source.indexOf("private static final int STANDALONE_MOBILE_EPISODE_CARD_PAGE_MAX_SIZE = 36;");
        int method = source.indexOf("private int episodeCardPageMaxSize()");
        int nextMethod = source.indexOf("private boolean shouldRefreshEpisodeMediaSection", method);

        assertTrue(sourcePath + " is missing standalone mobile episode card page sizing", constant >= 0 && method > constant && nextMethod > method);
        String body = source.substring(method, nextMethod);
        assertTrue("standalone mobile TMDB detail should use the larger bounded page size while other modes keep the default",
                body.contains("Util.isMobile() && Setting.isStandaloneTmdbDetailMode(getDetailMode()) ? STANDALONE_MOBILE_EPISODE_CARD_PAGE_MAX_SIZE : EpisodeRangePolicy.CARD_PAGE_MAX_SIZE"));
    }

    @Test
    public void openingDetailBindsTmdbEpisodesWithoutRepeatedIndexOf() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void bindTmdbEpisodes(List<Episode> sourceEpisodes, int tmdbSeason)");
        int nextMethod = source.indexOf("private int tmdbEpisodeDataSeason", method);

        assertTrue(sourcePath + " is missing bindTmdbEpisodes", method >= 0 && nextMethod > method);
        String body = source.substring(method, nextMethod);
        assertTrue("opening detail should index source episodes once before binding TMDB episode metadata",
                body.contains("Map<Episode, Integer> indices = episodeIndices(sourceEpisodes);")
                        && body.contains("EpisodePosition position = episodePosition(episode, sourceEpisodes, index);"));
        assertTrue("opening detail must not call indexOf for every episode unless the identity index misses",
                body.contains("if (index < 0) index = sourceEpisodes.indexOf(episode);")
                        && !body.contains("EpisodePosition position = episodePosition(episode, sourceEpisodes);"));
    }

    @Test
    public void seasonNavigationUsesOnlyCurrentFlagAvailableSeasons() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int resolver = source.indexOf("private List<Integer> availableSeasonNumbers(List<Episode> episodes)");
        int render = source.indexOf("private void renderSeasonSelection()", resolver);
        int update = source.indexOf("private void updateSeasonButtonStates()", render);
        int compute = source.indexOf("private List<Episode> computeVisibleEpisodes(List<Episode> episodes)", update);
        int seasonForEpisode = source.indexOf("private int seasonForEpisode(Episode episode, List<Episode> episodes)", compute);
        int next = source.indexOf("private boolean onRecommendationLongClick", seasonForEpisode);
        int episodePosition = source.indexOf("private EpisodePosition episodePosition(Episode episode, List<Episode> episodes, int index)", next);
        int positionEnd = source.indexOf("private int linearEpisodeNumber", episodePosition);

        assertTrue(sourcePath + " is missing current-line season availability integration",
                resolver >= 0 && render > resolver && update > render && compute > update && seasonForEpisode > compute && next > seasonForEpisode
                        && episodePosition > next && positionEnd > episodePosition);
        String resolverBody = source.substring(resolver, render);
        String renderBody = source.substring(render, update);
        String computeBody = source.substring(compute, seasonForEpisode);
        String seasonBody = source.substring(seasonForEpisode, next);
        String positionBody = source.substring(episodePosition, positionEnd);
        assertTrue("available seasons must be resolved from each current-line episode plus TMDB metadata",
                resolverBody.contains("sourceSeasonNumbers(episodes)")
                        && resolverBody.contains("EpisodeSeasonPolicy.resolveAvailableSeasons("));
        assertTrue("current-line labels must participate in season resolution before generic detail titles",
                source.contains("selectedFlag == null ? -1 : EpisodeSeasonPolicy.resolveExplicitSourceSeason(selectedFlag.getShow())")
                        && source.contains("int titleSeason = EpisodeSeasonPolicy.resolveExplicitSourceSeason(initialFlag.getShow());"));
        assertTrue("season chips must iterate only the current line's available seasons",
                renderBody.contains("List<Integer> availableSeasons = availableSeasonNumbers(episodes);")
                        && renderBody.contains("for (Integer season : availableSeasons)")
                        && !renderBody.contains("for (Integer season : seasonNumbers)"));
        assertTrue("unknown or single-season lines must keep all source episodes instead of exposing empty TMDB seasons",
                computeBody.contains("List<Integer> availableSeasons = availableSeasonNumbers(episodes);")
                        && computeBody.contains("if (availableSeasons.size() <= 1 || selectedSeasonNumber < 0) return episodes;"));
        assertTrue("initial season selection must use available seasons instead of unrelated TMDB seasons",
                seasonBody.contains("List<Integer> availableSeasons = availableSeasonNumbers(episodes);")
                        && seasonBody.contains("if (availableSeasons.isEmpty()) return -1;")
                        && !seasonBody.contains("if (seasonNumbers.isEmpty()) return -1;"));
        assertTrue("TMDB episode metadata positions must use the same current-line availability result",
                positionBody.contains("List<Integer> availableSeasons = availableSeasonNumbers(episodes);")
                        && positionBody.contains("if (availableSeasons.isEmpty()) return new EpisodePosition(-1")
                        && !positionBody.contains("if (seasonNumbers.size() <= 1"));
    }

    @Test
    public void singleAvailableSeasonKeepsReadOnlySeasonContext() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int render = source.indexOf("private void renderEpisodes()");
        int renderEnd = source.indexOf("private List<EpisodeRangePolicy.Range> buildCardEpisodeRanges", render);
        int bindMeta = source.indexOf("private void bindMeta()");
        int bindRatings = source.indexOf("private void bindRatings()", bindMeta);
        int contextNumber = source.indexOf("private int currentSeasonContextNumber()", bindMeta);
        int refreshContext = source.indexOf("private void refreshSeasonContext()", contextNumber);

        assertTrue(sourcePath + " is missing read-only season context integration",
                render >= 0 && renderEnd > render && bindMeta >= 0 && bindRatings > bindMeta
                        && contextNumber > bindMeta && refreshContext > contextNumber && bindRatings > refreshContext);
        String renderBody = source.substring(render, renderEnd);
        String metaBody = source.substring(bindMeta, contextNumber);
        String contextBody = source.substring(contextNumber, bindRatings);
        int emptyBranch = renderBody.indexOf("if (!hasEpisodes)");
        int emptyRefresh = renderBody.indexOf("refreshSeasonContext();", emptyBranch);
        int selectedRefresh = renderBody.lastIndexOf("refreshSeasonContext();");
        assertTrue("single-season context must be refreshed for empty and playable episode states",
                emptyBranch >= 0 && emptyRefresh > emptyBranch && selectedRefresh > emptyRefresh);
        assertTrue("the hero metadata row must keep the current season visible without duplicating season-scoped episode metadata",
                metaBody.contains("TmdbEpisodeInfo episodeInfo = tmdbEpisodeInfo();")
                        && metaBody.contains("addMetaChip(episodeInfo.detailText(this));")
                        && metaBody.contains("if (!episodeInfo.isSeasonScoped())")
                        && metaBody.contains("addMetaChip(currentSeasonContextLabel());"));
        assertTrue("the standalone season action must render a localized current-season label",
                contextBody.contains("availableSeasonNumbers(episodes)")
                        && contextBody.contains("R.string.detail_season_format")
                        && source.contains("binding.episodeTitle.setText(detailSeasonButtonLabel())")
                        && contextBody.contains("bindMeta();"));

        String defaults = new String(Files.readAllBytes(findMainResPath().resolve(Path.of("values", "strings.xml"))), StandardCharsets.UTF_8);
        String simplified = new String(Files.readAllBytes(findMainResPath().resolve(Path.of("values-zh-rCN", "strings.xml"))), StandardCharsets.UTF_8);
        String traditional = new String(Files.readAllBytes(findMainResPath().resolve(Path.of("values-zh-rTW", "strings.xml"))), StandardCharsets.UTF_8);
        assertTrue(defaults.contains("<string name=\"tmdb_season_button\">Season</string>"));
        assertTrue(simplified.contains("<string name=\"tmdb_season_button\">季度</string>"));
        assertTrue(traditional.contains("<string name=\"tmdb_season_button\">季度</string>"));
    }

    @Test
    public void switchingLongStandaloneEpisodeFlagsReusesEpisodeRenderCaches() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int indices = source.indexOf("private Map<Episode, Integer> episodeIndices(List<Episode> episodes)");
        int clear = source.indexOf("private void clearEpisodeRenderCaches()", indices);
        int explicit = source.indexOf("private boolean hasExplicitSeasonNumbers(List<Episode> episodes)");
        int next = source.indexOf("private int sourceEpisodeNumber", explicit);
        int visible = source.indexOf("private List<Episode> visibleEpisodes(List<Episode> episodes)");
        int visibleEnd = source.indexOf("private List<Episode> computeVisibleEpisodes", visible);

        assertTrue(sourcePath + " is missing episode render cache methods", indices >= 0 && clear > indices && explicit > clear && next > explicit && visible > next && visibleEnd > visible);
        String indexBody = source.substring(indices, clear);
        String clearBody = source.substring(clear, explicit);
        String explicitBody = source.substring(explicit, next);
        String visibleBody = source.substring(visible, visibleEnd);
        assertTrue("long episode flag switches should reuse the identity index for the same episode list",
                indexBody.contains("if (episodes == episodeIndexSource) return episodeIndexCache;")
                        && indexBody.contains("episodeIndexSource = episodes;")
                        && indexBody.contains("episodeIndexCache = indices;"));
        assertTrue("long episode flag switches should not rescan every title for explicit season numbers on each episode",
                explicitBody.contains("if (episodes == explicitSeasonSource) return explicitSeasonCache;")
                        && explicitBody.contains("explicitSeasonSource = episodes;")
                        && explicitBody.contains("explicitSeasonCache = true;"));
        assertTrue("long episode order/view toggles should reuse the current season's visible episode list",
                visibleBody.contains("if (episodes == visibleEpisodeSource && selectedSeasonNumber == visibleEpisodeSeason) return visibleEpisodeCache;")
                        && visibleBody.contains("visibleEpisodeCache = computeVisibleEpisodes(episodes);")
                        && clearBody.contains("clearVisibleEpisodeCache();"));
        assertTrue("new detail loads must clear cached episode-list render state",
                source.indexOf("resetEpisodeRange();", source.indexOf("clearEpisodeRenderCaches();")) > 0
                        && source.indexOf("clearEpisodeRenderCaches();", source.indexOf("TmdbEpisodeSorter.sort(vod);")) > 0
                        && source.indexOf("clearEpisodeRenderCaches();", source.indexOf("enrichVod();")) > 0);
        int seasonCountUpdate = source.indexOf("seasonEpisodeCounts.put(seasonNumber, episodes.size());");
        int seasonCacheClear = source.indexOf("clearSeasonResolutionCache();", seasonCountUpdate);
        int seasonRender = source.indexOf("if (seasonNumber == tmdbEpisodeDataSeason", seasonCacheClear);
        assertTrue("season count updates must invalidate cached season resolution and visible episode slices before rerendering",
                seasonCountUpdate >= 0 && seasonCacheClear > seasonCountUpdate && seasonRender > seasonCacheClear);
    }

    @Test
    public void compactCinemaDetailKeepsPosterVisible() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void applyCinemaDetailTemplate()");
        int nextMethod = source.indexOf("private boolean isCompactWidth()", method);

        assertTrue(sourcePath + " is missing applyCinemaDetailTemplate", method >= 0 && nextMethod > method);
        String body = source.substring(method, nextMethod);
        assertTrue("compact immersive/cinema detail must not hide the poster again",
                body.contains("binding.posterCard.setVisibility(compact ? View.VISIBLE : View.GONE);"));
        assertTrue("compact immersive/cinema poster should use a stable small size beside the title",
                body.contains("new LinearLayout.LayoutParams(ResUtil.dp2px(92), ResUtil.dp2px(138))")
                        && body.contains("binding.posterCard.setLayoutParams(posterParams);"));
        assertTrue("compact immersive/cinema title area must share the row with the poster instead of occupying full width",
                body.contains("new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)")
                        && body.contains("infoParams.setMarginStart(compact ? ResUtil.dp2px(14) : 0);")
                        && body.contains("if (compact)")
                        && containsMethodCallIgnoringReceiver(body, "setWidthMatch(binding.detailActions)"));
    }

    @Test
    public void fusionDetailShowsFocusedPersonalAiReason() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        Path adapterPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbRailAdapter.java"));
        String adapter = new String(Files.readAllBytes(adapterPath), StandardCharsets.UTF_8);
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "activity_tmdb_detail.xml"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);

        int aiList = layout.indexOf("android:id=\"@+id/personalAiList\"");
        int aiReason = layout.indexOf("android:id=\"@+id/personalAiReason\"");
        int externalLinksTitle = layout.indexOf("android:id=\"@+id/externalLinksTitle\"");
        int tmdbStatus = layout.indexOf("android:id=\"@+id/tmdbStatus\"");
        assertTrue("TMDB detail must keep the AI reason directly below the smart recommendation row",
                aiList >= 0 && aiReason > aiList && externalLinksTitle > aiReason && tmdbStatus > externalLinksTitle);
        int aiReasonEnd = layout.indexOf("/>", aiReason);
        String aiReasonTag = layout.substring(aiReason, aiReasonEnd);
        assertTrue("The recommendation reason needs bottom spacing before the external-links heading",
                aiReasonTag.contains("android:layout_marginBottom=\"16dp\"")
                        && aiReasonTag.contains("android:includeFontPadding=\"false\""));
        int externalLinksTitleEnd = layout.indexOf("/>", externalLinksTitle);
        String externalLinksTitleTag = layout.substring(externalLinksTitle, externalLinksTitleEnd);
        assertTrue("The external-links heading needs its own top spacing after the recommendation reason",
                externalLinksTitleTag.contains("android:layout_marginTop=\"20dp\""));
        assertTrue("TMDB detail must listen for smart recommendation card focus",
                activity.contains("personalAiAdapter.setOnItemFocusListener(this::showAiRecommendationReason);"));
        int reasonMethod = activity.indexOf("private void showAiRecommendationReason(TmdbItem item, boolean focused)");
        int reasonMethodEnd = activity.indexOf("private void scrollAiRecommendationReasonIntoView()", reasonMethod);
        String reasonBody = activity.substring(reasonMethod, reasonMethodEnd);
        assertTrue("TMDB detail must prefer the dedicated recommendation reason and keep legacy overview fallback",
                reasonBody.contains("item.getRecommendationReason()")
                        && reasonBody.contains("item.getOverview()")
                        && reasonBody.indexOf("item.getRecommendationReason()") < reasonBody.indexOf("item.getOverview()")
                        && reasonBody.contains("binding.personalAiReason.setText(getString(R.string.ai_recommendation_reason_preview, reason));"));
        assertTrue("TMDB detail must hide stale recommendation reasons when the smart row is absent",
                activity.contains("showAiRecommendationReason(null, false);"));
        assertTrue("TMDB detail must scroll the reason into view when the focused card sits near the bottom of the wide layout",
                activity.contains("scrollAiRecommendationReasonIntoView();")
                        && activity.contains("offsetDescendantRectToMyCoords(binding.personalAiReason, rect)")
                        && activity.contains("binding.scroll.smoothScrollBy(0, bottomGap);"));
        assertTrue("TMDB rail cards must report focus changes to the detail screen",
                adapter.contains("public interface FocusListener")
                        && adapter.contains("public void setOnItemFocusListener(FocusListener listener)")
                        && adapter.contains("focusListener.onItemFocus(item, focused)")
                        && adapter.contains("holder.root.hasFocus() && focusListener != null"));
    }

    @Test
    public void keepStateShowsAddedLabelWhenAlreadyKept() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void updateKeepState()");
        assertTrue(sourcePath + " is missing updateKeepState", method >= 0);

        int methodEnd = source.indexOf("\n    }", method);
        String body = source.substring(method, methodEnd);
        assertTrue("TMDB detail must show the current favorite state, not the removal result label",
                body.contains("TmdbDetailLabels.keepLabel(kept)") && !body.contains("R.string.keep_del"));
        assertTrue("TMDB detail must keep all favorite buttons visually selected together",
                body.contains("binding.keep.setSelected(kept)")
                        && body.contains("binding.keepTop.setSelected(kept)")
                        && body.contains("binding.keepFusion.setSelected(kept)"));
    }

    @Test
    public void lightActionButtonsStayReadableOnBackdropAndPanels() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int theme = source.indexOf("private void applyDetailTheme()");
        int themeEnd = source.indexOf("private void styleSourceValue()", theme);
        int helper = source.indexOf("private void setDetailActionButton(MaterialButton button, ThemeColors colors)");
        assertTrue(sourcePath + " is missing applyDetailTheme", theme >= 0 && themeEnd > theme);
        assertTrue(sourcePath + " is missing setDetailActionButton", helper >= 0);

        String themeBody = source.substring(theme, themeEnd);
        assertTrue("light detail actions must use the readable action button helper",
                themeBody.contains("setDetailActionButton(binding.keep, colors);")
                        && themeBody.contains("setDetailActionButton(binding.keepTop, colors);")
                        && themeBody.contains("setDetailActionButton(binding.keepFusion, colors);")
                        && themeBody.contains("setDetailActionButton(binding.rematch, colors);")
                        && themeBody.contains("setDetailActionButton(binding.rematchTop, colors);")
                        && themeBody.contains("setDetailActionButton(binding.rematchFusion, colors);")
                        && themeBody.contains("setDetailActionButton(binding.changeSource, colors);")
                        && themeBody.contains("setDetailActionButton(binding.changeSourceDetail, colors);"));

        int helperEnd = source.indexOf("private void setButton(MaterialButton button, int background, int stroke, int text)", helper);
        assertTrue("setDetailActionButton must be placed before setButton", helperEnd > helper);
        String helperBody = source.substring(helper, helperEnd);
        assertTrue("light action buttons need an opaque surface instead of the translucent control color",
                helperBody.contains("if (lightTheme)")
                        && helperBody.contains("button.setAlpha(1f);")
                        && helperBody.contains("0xFFFFFFFF")
                        && helperBody.contains("colors.chipActive")
                        && helperBody.contains("colors.lineStrong")
                        && helperBody.contains("colors.primary"));
    }

    @Test
    public void detailRatingChipsKeepReadableBrandColorsAfterThemeTint() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int theme = source.indexOf("private void applyDetailTheme()");
        int add = source.indexOf("private void addRatingChip");
        int style = source.indexOf("private void styleDetailRatingChip");
        int readable = source.indexOf("private int readableDetailRatingColor");

        assertTrue(sourcePath + " is missing detail rating chip styling helpers",
                theme >= 0 && add >= 0 && style >= 0 && readable >= 0);
        assertTrue("theme refresh must restyle existing rating chips after tintTextTree recolors text",
                source.indexOf("tintTextTree(binding.getRoot(), colors);", theme) > theme
                        && source.indexOf("styleDetailRatingChips();", theme) > source.indexOf("tintTextTree(binding.getRoot(), colors);", theme));
        assertTrue("rating chips must keep their source color so async ratings and theme refreshes can restyle them",
                source.indexOf("new RatingChipTag(platform, color)", add) > add
                        && source.indexOf("styleDetailRatingChip(chip, color);", add) > add);
        assertTrue("dark detail rating chips need a dark glass surface instead of translucent theme chips over bright artwork",
                source.indexOf("background.setColor(lightTheme ? ratingChipBackground(colors) : 0x6610141A);", style) > style
                        && source.indexOf("background.setStroke(ResUtil.dp2px(1), lightTheme ? colors.line : 0x33FFFFFF);", style) > style);
        assertTrue("light detail rating chips must darken yellow green and red source colors for contrast",
                source.indexOf("return 0xFF0F7A4A;", readable) > readable
                        && source.indexOf("return 0xFF8A5A00;", readable) > readable
                        && source.indexOf("return 0xFFB42318;", readable) > readable);
    }

    @Test
    public void detailEpisodeToolsAndCardsKeepDistinctFocusChrome() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        Path selectorPath = findMainResPath().resolve(Path.of("drawable", "selector_episode_card.xml"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        String selector = new String(Files.readAllBytes(selectorPath), StandardCharsets.UTF_8);
        int focusDetailEpisode = activity.indexOf("private boolean focusDetailEpisode(int position)");
        int focusDetailEpisodeEnd = activity.indexOf("private int detailEpisodeSpanCount()", focusDetailEpisode);
        int align = activity.indexOf("private void alignDetailEpisodeFocusedRow");
        int alignEnd = activity.indexOf("private void toggleEpisodeReverse", align);
        String focusBody = focusDetailEpisode >= 0 && focusDetailEpisodeEnd > focusDetailEpisode ? activity.substring(focusDetailEpisode, focusDetailEpisodeEnd) : "";
        String alignBody = align >= 0 && alignEnd > align ? activity.substring(align, alignEnd) : "";

        assertTrue("episode reverse/list tools should refresh focus chrome without inheriting episode-card selected state",
                activity.contains("setEpisodeToolButton(binding.episodeReverse, colors);")
                        && activity.contains("setEpisodeToolButton(binding.episodeViewMode, colors);")
                        && activity.contains("private void applyEpisodeToolButtonsFocus()")
                        && activity.contains("applyEpisodeToolButtonFocus(binding.episodeReverse, colors);")
                        && activity.contains("applyEpisodeToolButtonFocus(binding.episodeViewMode, colors);")
                        && activity.contains("button.setStrokeColor(ColorStateList.valueOf(focused ? FOCUS_STROKE : colors.lineStrong));"));
        assertTrue("episode tool delayed refocus must not steal focus back from the sibling tool",
                activity.contains("isEpisodeToolFocusedOtherThan(button)")
                        && activity.contains("retryDetailButtonFocus(button, previousFocus)")
                        && activity.contains("if (focus != null && previousFocus != null && focus != previousFocus) return;"));
        assertTrue("episode-card selector must let focused state override current-playing selected state",
                selector.indexOf("android:state_focused=\"true\"") >= 0
                        && selector.indexOf("android:state_focused=\"true\"") < selector.indexOf("android:state_selected=\"true\""));
        assertTrue("episode DPAD movement should focus an already visible card without forcing RecyclerView to re-scroll and rebind",
                focusBody.contains("RecyclerView.ViewHolder visibleHolder = binding.episodeContainer.findViewHolderForAdapterPosition(target);")
                        && focusBody.contains("if (visibleHolder != null)")
                        && focusBody.indexOf("findViewHolderForAdapterPosition(target)") < focusBody.indexOf("scrollEpisodeToPosition(rowStart, ResUtil.dp2px(8));"));
        assertTrue("outer detail scroll alignment should wait for stable focus before moving the page",
                alignBody.contains("focusedView.post(() ->")
                        && alignBody.contains("if (binding == null || getCurrentFocus() != focusedView) return;")
                        && alignBody.contains("binding.episodeContainer.getChildAdapterPosition(focusedView) != position"));
        assertTrue("focused episode cards should be minimally scrolled fully into view instead of top-aligning the whole row",
                alignBody.contains("private void alignDetailEpisodeFocusedCardNow(View focusedView)")
                        && alignBody.contains("if (rect.bottom > bottom) targetY += rect.bottom - bottom;")
                        && alignBody.contains("else if (rect.top < top) targetY += rect.top - top;")
                        && !alignBody.contains("isDetailEpisodeRowFullyVisible"));
    }

    @Test
    public void detailEpisodeTitleSharesToolFocusChromeAndNavigation() throws Exception {
        String layout = readLayout("activity_tmdb_detail.xml");
        String activity = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int label = layout.indexOf("android:id=\"@+id/episodeLabel\"");
        int title = layout.indexOf("android:id=\"@+id/episodeTitle\"");
        int titleTag = layout.lastIndexOf("<com.google.android.material.button.MaterialButton", title);
        int previousTagEnd = layout.lastIndexOf('>', title);

        assertTrue("episode section title and season action must be separate controls",
                label >= 0 && label < title);
        assertTrue("episode title must use the same Material button surface as its neighboring tools",
                title >= 0 && titleTag > previousTagEnd);
        assertFalse("episode title must not retain the circular borderless focus ripple",
                containsViewAttribute(layout, title, "android:background=\"?attr/selectableItemBackgroundBorderless\""));
        assertTrue("season action must keep its label on one fully visible line inside the compact header",
                containsViewAttribute(layout, title, "android:singleLine=\"true\"")
                        && containsViewAttribute(layout, title, "android:insetTop=\"0dp\"")
                        && containsViewAttribute(layout, title, "android:insetBottom=\"0dp\""));
        assertTrue("episode title must use the shared episode-tool focus chrome when it is actionable",
                activity.contains("setEpisodeTitleButton(binding.episodeTitle, colors);")
                        && activity.contains("private void applyEpisodeTitleButtonFocus(MaterialButton button, ThemeColors colors)"));
        assertTrue("unfocused season action must blend into the episode heading without a persistent chip surface",
                activity.contains("button.setBackgroundTintList(ColorStateList.valueOf(focused ? colors.control : Color.TRANSPARENT));")
                        && activity.contains("button.setStrokeWidth(focused ? ResUtil.dp2px(FOCUS_STROKE_DP) : 0);"));
        assertTrue("season action must show only a short season label instead of combining it with the episode heading",
                activity.contains("binding.episodeTitle.setText(detailSeasonButtonLabel());")
                        && activity.contains("private String detailSeasonButtonLabel()")
                        && activity.contains("R.string.detail_season_format")
                        && activity.contains("R.string.tmdb_season_button"));
        assertFalse("combined episode and season text must not be assigned to the season action",
                activity.contains("binding.episodeTitle.setText(season < 0 ? getString(R.string.detail_episode) : getString(R.string.detail_episode_season_context, season));"));

        int init = activity.indexOf("private void initPage()");
        int initEnd = activity.indexOf("private void initFusionPlayer()", init);
        String initBody = init >= 0 && initEnd > init ? activity.substring(init, initEnd) : "";
        assertTrue("episode title must handle the same DPAD navigation as the neighboring tools",
                initBody.contains("binding.episodeTitle.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeToolKey(view, keyCode, event));")
                        && initBody.contains("binding.episodeTitle.setNextFocusRightId(R.id.episodeReverse);")
                        && initBody.contains("binding.episodeReverse.setNextFocusLeftId(R.id.episodeTitle);"));

        int focusTools = activity.indexOf("private boolean focusDetailEpisodeToolButton(int direction)");
        int focusButton = activity.indexOf("private boolean focusDetailButton(View button, int direction)", focusTools);
        String focusToolsBody = focusTools >= 0 && focusButton > focusTools ? activity.substring(focusTools, focusButton) : "";
        assertTrue("episode cards moving up must target the leftmost actionable episode-header button first",
                focusToolsBody.indexOf("focusDetailButton(binding.episodeTitle, direction)") >= 0
                        && focusToolsBody.indexOf("focusDetailButton(binding.episodeTitle, direction)")
                        < focusToolsBody.indexOf("focusDetailButton(binding.episodeReverse, direction)"));
        assertTrue("disabled episode titles must be skipped instead of swallowing directional focus",
                activity.contains("!button.isFocusable()"));
        assertTrue("global detail navigation must recognize episode title as part of the tool group",
                activity.contains("view == binding.episodeTitle || view == binding.episodeReverse"));
    }

    @Test
    public void inlineEpisodesReuseSharedNativeEnhancedAdaptivePanel() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        Path adapterPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbEpisodeAdapter.java"));
        String adapter = new String(Files.readAllBytes(adapterPath), StandardCharsets.UTF_8);
        Path policyPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbEpisodeGridPolicy.java"));
        String policy = new String(Files.readAllBytes(policyPath), StandardCharsets.UTF_8);

        int method = activity.indexOf("private void showInlineEpisodes()");
        int sharedMethod = activity.indexOf("private void showNativeEnhancedInlineEpisodes()", method);
        int sharedMethodEnd = activity.indexOf("private TextView createNativeEnhancedInlineSectionTitle", sharedMethod);

        assertTrue(activityPath + " is missing showInlineEpisodes", method >= 0);
        assertTrue(activityPath + " is missing showNativeEnhancedInlineEpisodes", sharedMethod >= 0 && sharedMethodEnd > sharedMethod);

        String dispatchBody = activity.substring(method, sharedMethod);
        String panelBody = activity.substring(sharedMethod, sharedMethodEnd);

        assertTrue("mobile standalone TMDB detail modes should use the shared native-enhanced episode panel",
                dispatchBody.contains("if (!Util.isMobile() || Setting.isStandaloneTmdbDetailMode(getDetailMode()))")
                        && dispatchBody.contains("showNativeEnhancedInlineEpisodes();"));
        assertTrue("native-enhanced inline episode panel should include line chips, page chips, and TMDB episode cards",
                panelBody.contains("createNativeEnhancedInlineChipButton(flag.getShow())")
                        && panelBody.contains("createNativeEnhancedInlineChipButton(ranges.get(i).label())")
                        && panelBody.contains("new TmdbEpisodeAdapter")
                        && panelBody.contains("adapter.setNativeEnhanced(true);"));
        assertTrue("native-enhanced inline episode panel should use one responsive layout policy instead of forked dialogs",
                panelBody.contains("NativeEnhancedInlineEpisodeLayout layout = nativeEnhancedInlineEpisodeLayout();")
                        && panelBody.contains("NestedScrollView scroll = new NestedScrollView(this);")
                        && panelBody.contains("scroll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));")
                        && panelBody.contains("scroll.addView(panel, new NestedScrollView.LayoutParams")
                        && panelBody.contains("recycler.setNestedScrollingEnabled(false);")
                        && panelBody.contains("updateNativeEnhancedInlineEpisodeLayoutManager(recycler, layout.spanCount())")
                        && panelBody.contains("adapter.setDisplayMode(TmdbEpisodeAdapter.Mode.GRID, layout.spanCount())")
                        && panelBody.contains("new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)")
                        && panelBody.contains("setView(scroll)")
                        && panelBody.contains("window.getDecorView().setPadding(0, 0, 0, 0)")
                        && panelBody.contains("window.setGravity(layout.gravity())")
                        && panelBody.contains("window.setLayout(layout.windowWidth(), WindowManager.LayoutParams.MATCH_PARENT)"));
        assertTrue("native-enhanced inline episode panel should move focus by whole grid rows so remote down never lands on a clipped row",
                panelBody.contains("int target = position + layout.spanCount();")
                        && panelBody.contains("position - layout.spanCount()")
                        && activity.contains("private void alignNativeEnhancedInlineEpisodeRow(NestedScrollView scroll, RecyclerView recycler, int position, int spanCount)")
                        && activity.contains("int rowStart = Math.max(0, position - position % span);")
                        && activity.contains("scroll.scrollTo(0, Math.max(0, targetY));"));
        assertTrue("native-enhanced inline episode chips should use the current detail theme instead of the old white video selector",
                activity.contains("private TextView createNativeEnhancedInlineChipButton(String text)")
                        && activity.contains("new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(34))")
                        && activity.contains("button.setMinWidth(ResUtil.dp2px(64));")
                        && activity.contains("ThemeColors colors = currentThemeColors();")
                        && activity.contains("background.setColor(focused ? colors.control : selected ? colors.chipActive : colors.chip);")
                        && activity.contains("background.setStroke(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : selected ? 2 : CHIP_STROKE_DP), focused ? FOCUS_STROKE : selected ? colors.accent : colors.line);")
                        && activity.contains("button.setTextColor(colors.primary);")
                        && activity.contains("button.setBackground(background);")
                        && activity.contains("button.setActivated(selected);")
                        && !activity.contains("button.setBackgroundResource(R.drawable.shape_video_item);")
                        && !activity.contains("ContextCompat.getColorStateList(this, R.color.selector_video_text);"));
        assertTrue("native-enhanced inline episodes should keep mobile native columns and adaptive TV columns",
                activity.contains("private int nativeEnhancedInlineEpisodeSpanCount()")
                        && activity.contains("TmdbEpisodeGridPolicy.nativeEnhancedSpanCount(Util.isMobile(), ResUtil.isPad(), ResUtil.isLand(this), getResources().getConfiguration().screenWidthDp)")
                        && policy.contains("public static int nativeEnhancedSpanCount(boolean mobile, boolean pad, boolean landscape, int screenWidthDp)")
                        && policy.contains("if (mobile) return pad ? landscape ? 4 : 3 : landscape ? 3 : 2;")
                        && policy.contains("public static int tvAdaptiveSpanCount(int screenWidthDp)")
                        && policy.contains("if (screenWidthDp >= 1100) return 5;")
                        && policy.contains("if (screenWidthDp >= 600) return 4;")
                        && policy.contains("return 3;")
                        && activity.contains("WindowManager.LayoutParams.MATCH_PARENT")
                        && !activity.contains("Gravity.END | Gravity.CENTER_VERTICAL")
                        && !activity.contains("0.50f"));
        assertTrue("detail-page episode cards should use the same native-enhanced adaptive card mechanism",
                activity.contains("episodeAdapter.setNativeEnhanced(true);")
                        && activity.contains("private int episodeSpanCount()")
                        && activity.contains("return nativeEnhancedInlineEpisodeSpanCount();")
                        && activity.contains("binding.episodeViewMode.setVisibility(View.VISIBLE);")
                        && activity.contains("Setting.putTmdbEpisodeGridMode(episodeGridMode);")
                        && !activity.contains("if (shouldForceAdaptiveEpisodeGrid()) episodeGridMode = true;")
                        && !activity.contains("if (shouldForceAdaptiveEpisodeGrid()) return;"));
        assertTrue("native-enhanced card styling must not be disabled on mobile fusion overlays",
                adapter.contains("private boolean isNativeEnhanced()")
                        && adapter.contains("return nativeEnhanced;")
                        && !adapter.contains("return nativeEnhanced && !Util.isMobile();"));
        assertTrue("mobile native-enhanced cards should use the same compact TMDB card proportions as original enhanced",
                adapter.contains("private int nativeEnhancedGridCardHeight(View view)")
                        && adapter.contains("return TmdbEpisodeGridPolicy.nativeGridCardHeightDp(isPhoneWidth(view));")
                        && adapter.contains("private int nativeEnhancedGridScrimHeight(View view)")
                        && adapter.contains("return TmdbEpisodeGridPolicy.nativeGridScrimHeightDp(isPhoneWidth(view));")
                        && policy.contains("public static final int NATIVE_GRID_CARD_HEIGHT_DP = 248;")
                        && policy.contains("public static final int NATIVE_MOBILE_GRID_CARD_HEIGHT_DP = 190;")
                        && policy.contains("public static final int NATIVE_GRID_SCRIM_HEIGHT_DP = 148;")
                        && policy.contains("public static final int NATIVE_MOBILE_GRID_SCRIM_HEIGHT_DP = 104;"));
        assertTrue("detail-page episode cards should keep focused grid cards fully visible inside the outer scroll view",
                activity.contains("episodeAdapter.setOnFocusChangeListener(this::onDetailEpisodeFocusChange);")
                        && activity.contains("episodeAdapter.setOnKeyListener(this::onDetailEpisodeKey);")
                        && activity.contains("button.setOnKeyListener((view, keyCode, event) -> onDetailFlagKey(keyCode, event));")
                        && activity.contains("button.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeRangeKey(view, keyCode, event));")
                        && activity.contains("private boolean onDetailEpisodeKey(View view, int keyCode, KeyEvent event)")
                        && activity.contains("TmdbEpisodeGridPolicy.verticalFocusTarget(position, span, episodeAdapter.getItemCount(), down)")
                        && activity.contains("if (target == TmdbEpisodeGridPolicy.NO_FOCUS_TARGET)")
                        && activity.contains("private boolean focusDetailEpisodeRangeButton()")
                        && activity.contains("private boolean focusDetailEpisode(int position)")
                        && activity.contains("private void alignDetailEpisodeFocusedRow(View focusedView, int position)")
                        && activity.contains("private void alignDetailEpisodeFocusedCardNow(View focusedView)")
                        && activity.contains("binding.scroll.scrollTo(0, targetY);")
                        && adapter.contains("private View.OnFocusChangeListener focusChangeListener;")
                        && adapter.contains("public void setOnFocusChangeListener(View.OnFocusChangeListener focusChangeListener)")
                        && adapter.contains("holder.binding.getRoot().setOnFocusChangeListener((view, focused) -> {")
                        && adapter.contains("if (focusChangeListener != null) focusChangeListener.onFocusChange(view, focused);"));
    }

    @Test
    public void detailEpisodeBottomRowDpadDownFocusesFirstVisibleTmdbRow() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int episodeKey = activity.indexOf("private boolean onDetailEpisodeKey");
        int flagFocus = activity.indexOf("private boolean focusDetailFlagButton()", episodeKey);
        String episodeKeyBody = flagFocus > episodeKey ? activity.substring(episodeKey, flagFocus) : "";
        int firstTmdb = activity.indexOf("private boolean focusFirstVisibleTmdbRow()", episodeKey);
        int focusRecycler = activity.indexOf("private boolean focusTmdbRecycler(RecyclerView recycler)", firstTmdb);
        String firstTmdbBody = focusRecycler > firstTmdb ? activity.substring(firstTmdb, focusRecycler) : "";

        assertTrue(activityPath + " is missing onDetailEpisodeKey", episodeKey >= 0);
        assertTrue("detail episode bottom row DPAD_DOWN must leave the episode grid instead of consuming the key",
                episodeKeyBody.contains("if (target == TmdbEpisodeGridPolicy.NO_FOCUS_TARGET)")
                        && episodeKeyBody.contains("return focusFirstVisibleTmdbRow();"));
        assertTrue(activityPath + " is missing focusFirstVisibleTmdbRow", firstTmdb >= 0);
        assertTrue("TMDB photo row should be the first focus target below episodes",
                firstTmdbBody.indexOf("binding.episodePhotoList") >= 0
                        && firstTmdbBody.indexOf("binding.episodePhotoList") < firstTmdbBody.indexOf("binding.castList"));
        assertTrue("TMDB row focusing must request focus on a concrete RecyclerView item",
                activity.indexOf("holder.itemView.requestFocus();", focusRecycler) > focusRecycler);
    }

    @Test
    public void detailEpisodeGridDpadUpDownUsesFullHeightRecyclerViewUntilBoundary() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int move = activity.indexOf("private boolean moveDetailEpisodeFocus");
        int moveEnd = activity.indexOf("private boolean focusFirstVisibleTmdbRow()", move);
        int focus = activity.indexOf("private boolean focusDetailEpisode(int position)");
        int focusEnd = activity.indexOf("private int detailEpisodeSpanCount()", focus);
        int focusChange = activity.indexOf("private void onDetailEpisodeFocusChange");
        int flagKey = activity.indexOf("private boolean onDetailFlagKey", focusChange);
        int viewport = activity.indexOf("private void updateEpisodeViewport");
        int viewportEnd = activity.indexOf("private void updateEpisodeLayoutForCurrentItems", viewport);

        assertTrue(activityPath + " is missing detail episode focus helpers", move >= 0 && moveEnd > move && focus > moveEnd && focusEnd > focus && focusChange >= 0 && flagKey > focusChange && viewport >= 0 && viewportEnd > viewport);
        String moveBody = activity.substring(move, moveEnd);
        String focusBody = activity.substring(focus, focusEnd);
        String focusChangeBody = activity.substring(focusChange, flagKey);
        String viewportBody = activity.substring(viewport, viewportEnd);

        assertTrue("card-to-card DPAD_UP should let RecyclerView keep its native focus and scroll behavior",
                moveBody.contains("TmdbEpisodeGridPolicy.verticalFocusTarget(position, span, episodeAdapter.getItemCount(), down)")
                        && moveBody.contains("return false;")
                        && !moveBody.contains("return focusDetailEpisode(position - span"));
        assertTrue("card-to-card DPAD_DOWN should let RecyclerView keep its native focus and scroll behavior",
                moveBody.contains("if (target == TmdbEpisodeGridPolicy.NO_FOCUS_TARGET)")
                        && moveBody.contains("return focusFirstVisibleTmdbRow();")
                        && !moveBody.contains("return focusDetailEpisode(target"));
        assertTrue("boundary DPAD_DOWN should still leave the episode grid only when no next row exists",
                moveBody.contains("if (target == TmdbEpisodeGridPolicy.NO_FOCUS_TARGET)")
                        && moveBody.contains("return focusFirstVisibleTmdbRow();"));
        assertTrue("manual episode entry should still focus and align a concrete card",
                focusBody.contains("RecyclerView.ViewHolder visibleHolder = binding.episodeContainer.findViewHolderForAdapterPosition(target);")
                        && focusBody.contains("visibleHolder.itemView.requestFocus();")
                        && focusBody.contains("alignDetailEpisodeFocusedRow(visibleHolder.itemView, target);"));
        assertTrue("card-to-card moves must not use viewport-preserve state that can fight RecyclerView focus restoration",
                !activity.contains("preserveDetailEpisodeViewportOnce")
                        && !focusBody.contains("preserveOuterScroll")
                        && !focusChangeBody.contains("consumeDetailEpisodeViewportPreserve"));
        assertTrue("nested episode grids should keep outer detail scroll fixed while RecyclerView handles internal row focus",
                focusChangeBody.contains("if (binding.episodeContainer.isNestedScrollingEnabled()) return;"));
        assertTrue("detail episode grid should expand to all rows instead of creating a nested 3-row scroll window",
                viewportBody.contains("params.height = ViewGroup.LayoutParams.WRAP_CONTENT;")
                        && viewportBody.contains("binding.episodeContainer.setNestedScrollingEnabled(false);")
                        && !viewportBody.contains("TmdbEpisodeGridPolicy.layout("));
    }

    @Test
    public void detailEpisodeHorizontalFocusSkipsSameRowOuterAlignment() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int focusChange = activity.indexOf("private void onDetailEpisodeFocusChange");
        int flagKey = activity.indexOf("private boolean onDetailFlagKey", focusChange);
        int clearFocusRow = activity.indexOf("private void clearDetailEpisodeFocusRowIfNeeded", focusChange);
        int rowStart = activity.indexOf("private int detailEpisodeRowStart", clearFocusRow);
        String focusChangeBody = focusChange >= 0 && flagKey > focusChange ? activity.substring(focusChange, flagKey) : "";

        assertTrue(activityPath + " is missing detail episode focus row tracking", focusChange >= 0 && flagKey > focusChange && clearFocusRow > focusChange && rowStart > clearFocusRow);
        assertTrue("same-row DPAD_LEFT/RIGHT should keep the outer detail scroll anchored to avoid edge-row flicker",
                activity.contains("private int lastDetailEpisodeFocusRowStart = RecyclerView.NO_POSITION;")
                        && focusChangeBody.contains("int rowStart = detailEpisodeRowStart(position);")
                        && focusChangeBody.contains("boolean sameFocusedRow = rowStart == lastDetailEpisodeFocusRowStart;")
                        && focusChangeBody.contains("lastDetailEpisodeFocusRowStart = rowStart;")
                        && focusChangeBody.contains("if (sameFocusedRow) return;"));
        assertTrue("leaving the episode grid must reset row tracking so the next entry can align normally",
                focusChangeBody.contains("if (!focused) {")
                        && focusChangeBody.contains("clearDetailEpisodeFocusRowIfNeeded(view);")
                        && activity.contains("lastDetailEpisodeFocusRowStart = RecyclerView.NO_POSITION;"));
    }

    @Test
    public void detailEpisodeGridModeDpadLeftRightStaysInsideGridRow() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int move = activity.indexOf("private boolean moveDetailEpisodeFocus");
        int firstTmdb = activity.indexOf("private boolean focusFirstVisibleTmdbRow()", move);
        String moveBody = move >= 0 && firstTmdb > move ? activity.substring(move, firstTmdb) : "";

        assertTrue(activityPath + " is missing moveDetailEpisodeFocus", move >= 0 && firstTmdb > move);
        assertTrue("grid-mode DPAD_LEFT should move within the row and consume the row-start boundary",
                moveBody.contains("if (KeyUtil.isLeftKey(event))")
                        && moveBody.contains("if (position % span == 0) return true;")
                        && moveBody.contains("return focusDetailEpisode(position - 1);"));
        assertTrue("grid-mode DPAD_RIGHT should move within the row and consume row/end boundaries",
                moveBody.contains("if (KeyUtil.isRightKey(event))")
                        && moveBody.contains("position >= episodeAdapter.getItemCount() - 1 || position % span == span - 1")
                        && moveBody.contains("return focusDetailEpisode(position + 1);")
                        && !moveBody.contains("KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event)) return false;"));
    }

    @Test
    public void detailEpisodeRangeDpadDownUsesButtonPositionInsteadOfSelectedEpisode() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int rangeKey = activity.indexOf("private boolean onDetailEpisodeRangeKey");
        int toolKey = activity.indexOf("private boolean onDetailEpisodeToolKey", rangeKey);
        int focusBelow = activity.indexOf("private boolean focusDetailEpisodeBelow", toolKey);
        int nearest = activity.indexOf("private int nearestVisibleDetailEpisodePositionBelow", focusBelow);
        int focusSelected = activity.indexOf("private boolean focusDetailEpisode()", nearest);

        assertTrue(activityPath + " is missing detail episode range spatial focus helpers",
                rangeKey >= 0 && toolKey > rangeKey && focusBelow > toolKey && nearest > focusBelow && focusSelected > nearest);
        String rangeBody = activity.substring(rangeKey, toolKey);
        String focusBelowBody = activity.substring(focusBelow, nearest);
        String nearestBody = activity.substring(nearest, focusSelected);

        assertTrue("episode range button key listeners should pass the focused button into DPAD handling",
                activity.contains("button.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeRangeKey(view, keyCode, event));")
                        && activity.contains("return onDetailEpisodeRangeKey(focus, event.getKeyCode(), event);"));
        assertTrue("DPAD_DOWN from an episode range button should use the button's screen position, not the selected episode",
                rangeBody.contains("return focusDetailEpisodeBelow(view);")
                        && !rangeBody.contains("return focusDetailEpisode();"));
        assertTrue("spatial range-to-card focus should fall back to the first visible episode before using position 0",
                focusBelowBody.contains("int target = nearestVisibleDetailEpisodePositionBelow(source);")
                        && focusBelowBody.contains("if (target == RecyclerView.NO_POSITION) target = firstVisibleDetailEpisodePosition();")
                        && focusBelowBody.contains("return focusDetailEpisode(target);"));
        assertTrue("spatial range-to-card focus should compare visible episode cards in outer scroll coordinates",
                nearestBody.contains("binding.scroll.offsetDescendantRectToMyCoords(source, sourceRect);")
                        && nearestBody.contains("binding.episodeContainer.getChildCount()")
                        && nearestBody.contains("binding.episodeContainer.getChildAdapterPosition(child)")
                        && nearestBody.contains("Math.abs(rect.centerX() - sourceRect.centerX())"));
    }

    @Test
    public void detailEpisodeRangeFocusActivatesPageEvenWhenIndexStateAlreadyMatches() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int focusChange = activity.indexOf("private void setEpisodeRangeFocusChange");
        int activate = activity.indexOf("private void activateFocusedEpisodeRange", focusChange);
        int restore = activity.indexOf("private void restoreEpisodeRangeFocus", activate);
        int rerender = activity.indexOf("private void rerenderEpisodeViewportOnly");
        int updateStates = activity.indexOf("private void updateEpisodeRangeButtonStates", rerender);
        int selectRange = activity.indexOf("private void selectEpisodeRange", updateStates);
        int resolveRange = activity.indexOf("private int resolveEpisodeRangeIndex", selectRange);
        String focusBody = focusChange >= 0 && activate > focusChange ? activity.substring(focusChange, activate) : "";
        String activateBody = activate >= 0 && restore > activate ? activity.substring(activate, restore) : "";
        String rerenderBody = rerender >= 0 && updateStates > rerender ? activity.substring(rerender, updateStates) : "";
        String updateStatesBody = updateStates >= 0 && selectRange > updateStates ? activity.substring(updateStates, selectRange) : "";
        String selectBody = selectRange >= 0 && resolveRange > selectRange ? activity.substring(selectRange, resolveRange) : "";

        assertTrue(activityPath + " is missing episode range focus activation helpers",
                focusChange >= 0 && activate > focusChange && restore > activate && rerender >= 0 && updateStates > rerender && selectRange > updateStates && resolveRange > selectRange);
        assertTrue("episode range focus should activate the focused page instead of waiting for click",
                focusBody.contains("if (!focused) return;")
                        && focusBody.contains("activateFocusedEpisodeRange(index);")
                        && !focusBody.contains("index == episodeRangeIndex) return"));
        assertTrue("focused range activation should only skip work when both selected index and rendered page already match",
                activateBody.contains("if (index == episodeRangeIndex && index == renderedEpisodeRangeIndex) return;")
                        && activateBody.contains("pendingEpisodeRangeFocus = index;")
                        && activateBody.contains("binding.episodeRangeContainer.post(() ->")
                        && activateBody.contains("if (binding == null || pendingEpisodeRangeFocus != index) return;")
                        && activateBody.contains("selectEpisodeRange(index, false);"));
        assertTrue("episode viewport rendering should remember which range page is actually displayed",
                activity.contains("private int renderedEpisodeRangeIndex = -1;")
                        && rerenderBody.contains("renderedEpisodeRangeIndex = ranges.size() > 1 ? episodeRangeIndex : -1;"));
        assertTrue("episode range selection must not notify the adapter while RecyclerView is laying out or scrolling",
                selectBody.contains("binding.episodeContainer.isComputingLayout()")
                        && selectBody.contains("binding.episodeContainer.post(() -> selectEpisodeRange(index, scrollToSelection));"));
        assertTrue("updating selected range state must restore the range focus listener that setChipState replaces",
                updateStatesBody.contains("setChipState(button, i == episodeRangeIndex);")
                        && updateStatesBody.contains("setEpisodeRangeFocusChange(button, i);")
                        && updateStatesBody.indexOf("setChipState(button, i == episodeRangeIndex);") < updateStatesBody.indexOf("setEpisodeRangeFocusChange(button, i);"));
    }

    @Test
    public void tmdbArtworkRowsSeparateBackdropsAndPostersWhileSlidesRemainResponsive() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        Path headerPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java"));
        Path adapterPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbPhotoAdapter.java"));
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "activity_tmdb_detail.xml"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        String header = new String(Files.readAllBytes(headerPath), StandardCharsets.UTF_8);
        String adapter = new String(Files.readAllBytes(adapterPath), StandardCharsets.UTF_8);
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);

        assertTrue("detail artwork panel should render a dedicated poster row directly below backdrops",
                layout.contains("android:id=\"@+id/posterTitle\"")
                        && layout.contains("android:id=\"@+id/posterList\"")
                        && layout.indexOf("@+id/posterTitle") > layout.indexOf("@+id/episodePhotoList"));
        assertTrue("detail activity should keep card rows separate from responsive backdrop slides",
                activity.contains("private final List<String> detailTmdbPosters")
                        && activity.contains("private final List<String> detailBackdropSlides")
                        && activity.contains("tmdbService.backdrops(")
                        && activity.contains("tmdbService.posters(")
                        && activity.contains("tmdbService.photos(")
                        && activity.contains("posterAdapter = new TmdbPhotoAdapter(")
                        && activity.contains("detailBackdropSlides) addBackdropSlideItem"));
        assertTrue("shared header should bind posters separately and use the responsive list only for its slideshow",
                header.contains("adapter.getPosters()")
                        && header.contains("adapter.getBackgroundPhotos()")
                        && header.contains("posterAdapter = new com.fongmi.android.tv.ui.adapter.TmdbPhotoAdapter(true)"));
        assertTrue("poster row should use portrait dimensions without changing the shared backdrop card layout",
                adapter.contains("params.width = ResUtil.dp2px(148);")
                        && adapter.contains("params.height = ResUtil.dp2px(222);")
                        && adapter.contains("R.layout.adapter_tmdb_photo"));
    }

    @Test
    public void mobileTmdbPhotoViewersKeepCurrentOrientationUntilUserRotates() throws Exception {
        String detail = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        String person = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbPersonActivity.java");

        assertPhotoViewerKeepsCurrentOrientation(detail, "detail artwork viewer");
        assertPhotoViewerKeepsCurrentOrientation(person, "person photo viewer");
    }

    private static void assertPhotoViewerKeepsCurrentOrientation(String source, String label) {
        String load = javaBlockAt(source, "private void loadPhotoImage(");
        assertTrue(label + " must keep FIT_CENTER rendering", load.contains(".fitCenter()"));
        assertFalse(label + " must not rotate the activity after an image loads", load.contains("applyPhotoOrientation("));
        assertFalse(label + " must not call setRequestedOrientation while loading an image", load.contains("setRequestedOrientation("));
        assertFalse(label + " must not inspect image dimensions to choose orientation", load.contains("getIntrinsicWidth()") || load.contains("getIntrinsicHeight()"));
        assertFalse(label + " must not keep an automatic orientation helper", source.contains("private void applyPhotoOrientation("));

        String actionMarker = source.contains("private View createPhotoMobileActions(")
                ? "private View createPhotoMobileActions("
                : "private View createPhotoActions(";
        String actions = javaBlockAt(source, actionMarker);
        String toggle = javaBlockAt(source, "private void togglePhotoOrientation(");
        assertTrue(label + " must keep the explicit rotate control", actions.contains("R.string.detail_image_rotate")
                && (actions.contains("togglePhotoOrientation(photoOrientation)") || actions.contains("togglePhotoOrientation()")));
        assertTrue(label + " must rotate only from the explicit control", toggle.contains("setRequestedOrientation(target);")
                && countOccurrences(source, "togglePhotoOrientation(") == 2);
        assertFalse(label + " must rotate from the actual orientation, not a stale request", toggle.contains("getRequestedOrientation()"));
        assertTrue(label + " must toggle the current visible orientation",
                toggle.contains("actual == Configuration.ORIENTATION_LANDSCAPE")
                        && toggle.contains("? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT")
                        && toggle.contains(": ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE"));
        assertTrue(label + " must restore the entry orientation when closed", source.contains("setRequestedOrientation(originalOrientation);"));
    }

    @Test
    public void mobileLegacyDetailPhotoCardsOpenOnFirstTapAcrossDetailFlows() throws Exception {
        Path adapterPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbPhotoAdapter.java"));
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String adapter = new String(Files.readAllBytes(adapterPath), StandardCharsets.UTF_8);
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int holderConstructor = adapter.indexOf("public ViewHolder(@NonNull android.view.View itemView)");
        int bind = adapter.indexOf("void bind(", holderConstructor);

        assertTrue(adapterPath + " is missing the shared photo holder", holderConstructor >= 0 && bind > holderConstructor);
        String holderBody = adapter.substring(holderConstructor, bind);
        assertTrue("mobile legacy photo cards must bypass first-tap focus while TV keeps layout focus",
                holderBody.contains("if (!Util.isLeanback()) {")
                        && holderBody.contains("itemView.setFocusable(false);")
                        && holderBody.contains("itemView.setFocusableInTouchMode(false);")
                        && !holderBody.contains("!legacyMode && !Util.isLeanback()"));
        assertTrue("main photos plus movie and episode detail dialogs must share the corrected photo adapter",
                activity.contains("episodePhotoAdapter = new TmdbPhotoAdapter(this::showPhotoDialog);")
                        && activity.contains("new TmdbPhotoAdapter((position, url) -> showPhotoDialog(position, url, new ArrayList<>()))")
                        && activity.contains("new TmdbPhotoAdapter((position, url) -> showPhotoDialog(position, url, photos))"));
        assertTrue("detail photo clicks must still open the full-screen original image dialog",
                activity.contains("private void showPhotoDialog(int position, String url, List<String> sourcePhotos)")
                        && activity.contains("Dialog dialog = new Dialog(this);"));
    }

    @Test
    public void detailPhotoCardsUseUnifiedMaterialFocusStrokeAndAlignedCorners() throws Exception {
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "adapter_tmdb_photo.xml"));
        Path adapterPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbPhotoAdapter.java"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
        String adapter = new String(Files.readAllBytes(adapterPath), StandardCharsets.UTF_8);

        assertTrue("photo card root should own both clipping and focus stroke so rounded corners line up",
                layout.contains("<com.google.android.material.card.MaterialCardView")
                        && layout.contains("app:cardCornerRadius=\"8dp\"")
                        && layout.contains("app:strokeWidth=\"1dp\"")
                        && layout.contains("app:strokeColor=\"#33FFFFFF\""));
        assertTrue("photo cards should not stack the old selector or platform focus highlight over the card radius",
                layout.contains("android:defaultFocusHighlightEnabled=\"false\"")
                        && layout.contains("android:stateListAnimator=\"@null\"")
                        && !layout.contains("@drawable/selector_tmdb_card")
                        && !layout.contains("?attr/selectableItemBackground"));
        assertTrue("photo adapter should use the shared TMDB card focus helper for the yellow focus stroke",
                adapter.contains("private final MaterialCardView card;")
                        && adapter.contains("card = (MaterialCardView) itemView;")
                        && adapter.contains("TmdbCardFocusHelper.bind(card")
                        && adapter.contains("light ? 0x33647480 : 0x33FFFFFF"));
    }

    @Test
    public void detailTmdbHorizontalCardsDoNotUseGrayStateOverlays() throws Exception {
        String[] layouts = {
                "adapter_tmdb_cast.xml",
                "adapter_tmdb_person.xml",
                "adapter_tmdb_person_photo.xml",
                "adapter_tmdb_rail_item.xml",
                "adapter_tmdb_rail_landscape.xml",
                "adapter_tmdb_recommendation_landscape.xml",
                "adapter_tmdb_work.xml",
                "item_tmdb_person_photo.xml",
                "item_tmdb_person_work.xml"
        };

        for (String file : layouts) {
            String layout = readLayout(file);
            assertTrue(file + " should use a Material card root so focus is drawn by stroke",
                    layout.contains("<com.google.android.material.card.MaterialCardView"));
            assertTrue(file + " should disable platform focus/state overlays",
                    layout.contains("android:defaultFocusHighlightEnabled=\"false\"")
                            && layout.contains("android:stateListAnimator=\"@null\"")
                            && layout.contains("app:rippleColor=\"@android:color/transparent\""));
            assertTrue(file + " should not put selector/ripple drawables over card content",
                    !layout.contains("?attr/selectableItemBackground")
                            && !layout.contains("@drawable/selector_tmdb_card")
                            && !layout.contains("@drawable/selector_tmdb_cast_focus"));
        }

        String helper = readJava("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbCardFocusHelper.java");
        assertTrue("shared TMDB card focus helper should clear gray state overlays before applying visible foreground focus",
                helper.contains("card.setSelected(false);")
                        && helper.contains("card.setActivated(false);")
                        && helper.contains("card.setChecked(false);")
                        && helper.contains("card.setForeground(null);")
                        && helper.contains("card.setRippleColor(ColorStateList.valueOf(0x00000000));"));
        assertTrue("shared TMDB card focus helper should draw a transparent foreground border above card content",
                helper.contains("private static final int FOCUS_STROKE = 0xFFFFD166;")
                        && helper.contains("card.setStrokeColor(focused ? FOCUS_STROKE : strokeColor);")
                        && helper.contains("card.setForeground(focused ? foregroundBorder(card, FOCUS_STROKE, FOCUS_STROKE_DP) : null);")
                        && helper.contains("drawable.setColor(Color.TRANSPARENT);")
                        && !helper.contains("FOCUS_SCALE")
                        && !helper.contains("scaleX(")
                        && !helper.contains("scaleY("));

        String castAdapter = readJava("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbCastAdapter.java");
        assertTrue("cast/creator cards should use the same stroke-only helper instead of foreground activation",
                castAdapter.contains("TmdbCardFocusHelper.bind(card")
                        && !castAdapter.contains("setForeground(")
                        && !castAdapter.contains("setActivated(focused)"));

        String personPhotoAdapter = readJava("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbPersonPhotoAdapter.java");
        String personWorkAdapter = readJava("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbPersonWorkAdapter.java");
        assertTrue("person photo cards should also use stroke-only focus",
                personPhotoAdapter.contains("TmdbCardFocusHelper.bind(card"));
        assertTrue("person work cards should also use stroke-only focus",
                personWorkAdapter.contains("TmdbCardFocusHelper.bind(card"));
    }

    @Test
    public void detailEpisodeListModeDpadUpDownLeavesHorizontalEpisodeRow() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int move = activity.indexOf("private boolean moveDetailEpisodeFocus");
        int firstTmdb = activity.indexOf("private boolean focusFirstVisibleTmdbRow()", move);
        String moveBody = move >= 0 && firstTmdb > move ? activity.substring(move, firstTmdb) : "";
        int listBranch = moveBody.indexOf("if (!episodeGridMode) return moveDetailEpisodeListFocus(position, event);");
        int gridBranch = moveBody.indexOf("int span = detailEpisodeSpanCount()");
        int listHelper = activity.indexOf("private boolean moveDetailEpisodeListFocus");
        int listHelperEnd = activity.indexOf("private boolean focusFirstVisibleTmdbRow()", listHelper);
        String listBody = listHelper >= 0 && listHelperEnd > listHelper ? activity.substring(listHelper, listHelperEnd) : "";

        assertTrue(activityPath + " is missing moveDetailEpisodeFocus", move >= 0);
        assertTrue("detail list-mode episodes are a horizontal row, so DPAD handling must happen before grid span math",
                listBranch >= 0 && gridBranch > listBranch);
        assertTrue("list-mode DPAD_UP should leave the horizontal episode row toward range/tools/lines",
                listBody.contains("if (focusDetailEpisodeRangeButton()) return true;")
                        && listBody.contains("if (focusDetailEpisodeToolButton(View.FOCUS_UP)) return true;")
                        && listBody.contains("return focusDetailFlagButton();"));
        assertTrue("list-mode DPAD_DOWN should leave the horizontal episode row toward the first visible TMDB row",
                listBody.contains("return focusFirstVisibleTmdbRow();"));
    }

    @Test
    public void detailEpisodeListModeDpadLeftRightStaysInsideHorizontalEpisodeRow() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int episodeKey = activity.indexOf("private boolean onDetailEpisodeKey");
        int episodeKeyEnd = activity.indexOf("private boolean moveDetailEpisodeFocus", episodeKey);
        int containerKey = activity.indexOf("private boolean onDetailEpisodeContainerKey");
        int containerKeyEnd = activity.indexOf("private boolean isFocusInside", containerKey);
        int listHelper = activity.indexOf("private boolean moveDetailEpisodeListFocus");
        int listHelperEnd = activity.indexOf("private boolean focusFirstVisibleTmdbRow()", listHelper);
        String episodeKeyBody = episodeKey >= 0 && episodeKeyEnd > episodeKey ? activity.substring(episodeKey, episodeKeyEnd) : "";
        String containerKeyBody = containerKey >= 0 && containerKeyEnd > containerKey ? activity.substring(containerKey, containerKeyEnd) : "";
        String listBody = listHelper >= 0 && listHelperEnd > listHelper ? activity.substring(listHelper, listHelperEnd) : "";

        assertTrue(activityPath + " is missing detail episode list focus helpers",
                episodeKey >= 0 && episodeKeyEnd > episodeKey && containerKey >= 0 && containerKeyEnd > containerKey && listHelper >= 0 && listHelperEnd > listHelper);
        assertTrue("episode card key handling must capture DPAD_LEFT/RIGHT before Android focus search can leave the row",
                episodeKeyBody.contains("KeyUtil.isLeftKey(event)")
                        && episodeKeyBody.contains("KeyUtil.isRightKey(event)")
                        && containerKeyBody.contains("KeyUtil.isLeftKey(event)")
                        && containerKeyBody.contains("KeyUtil.isRightKey(event)"));
        assertTrue("list-mode DPAD_LEFT should move to the previous episode and consume the first-card boundary",
                listBody.contains("if (KeyUtil.isLeftKey(event))")
                        && listBody.contains("if (position <= 0) return true;")
                        && listBody.contains("return focusDetailEpisode(position - 1);"));
        assertTrue("list-mode DPAD_RIGHT should move to the next episode and consume the last-card boundary",
                listBody.contains("if (KeyUtil.isRightKey(event))")
                        && listBody.contains("position >= episodeAdapter.getItemCount() - 1")
                        && listBody.contains("return focusDetailEpisode(position + 1);"));
    }

    @Test
    public void detailTmdbHorizontalRowsMoveWithinRowAndConsumeBoundaries() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int navigation = activity.indexOf("private boolean handleDetailEpisodeNavigationKey");
        int detailRows = activity.indexOf("private RecyclerView detailTmdbRecyclerContainingFocus", navigation);
        int rowKey = activity.indexOf("private boolean onDetailTmdbRowKey", detailRows);
        int focusItem = activity.indexOf("private boolean focusTmdbRecyclerItem", rowKey);
        int episodeKey = activity.indexOf("private boolean onDetailEpisodeContainerKey", rowKey);
        String navigationBody = navigation >= 0 && detailRows > navigation ? activity.substring(navigation, detailRows) : "";
        String detailRowsBody = detailRows >= 0 && rowKey > detailRows ? activity.substring(detailRows, rowKey) : "";
        String rowKeyBody = rowKey >= 0 && episodeKey > rowKey ? activity.substring(rowKey, episodeKey) : "";

        assertTrue(activityPath + " is missing TMDB horizontal row key helpers",
                navigation >= 0 && detailRows > navigation && rowKey > detailRows && focusItem > rowKey && episodeKey > focusItem);
        assertTrue("detail navigation should route TMDB horizontal card rows through the shared boundary guard",
                navigationBody.contains("RecyclerView tmdbRow = detailTmdbRecyclerContainingFocus(focus);")
                        && navigationBody.contains("if (tmdbRow != null) return onDetailTmdbRowKey(tmdbRow, focus, event);"));
        assertTrue("TMDB horizontal rows should include stills, people, related, and personal recommendation rails",
                detailRowsBody.contains("binding.episodePhotoList")
                        && detailRowsBody.contains("binding.castList")
                        && detailRowsBody.contains("binding.creatorList")
                        && detailRowsBody.contains("binding.relatedList")
                        && detailRowsBody.contains("binding.personalTmdbList")
                        && detailRowsBody.contains("binding.personalDoubanList")
                        && detailRowsBody.contains("binding.personalAiList"));
        assertTrue("TMDB horizontal row DPAD_LEFT/RIGHT should explicitly move to adjacent cards and consume first/last boundaries",
                rowKeyBody.contains("if (!KeyUtil.isLeftKey(event) && !KeyUtil.isRightKey(event)) return false;")
                        && rowKeyBody.contains("if (!KeyUtil.isActionDown(event)) return true;")
                        && rowKeyBody.contains("int target = KeyUtil.isLeftKey(event) ? position - 1 : position + 1;")
                        && rowKeyBody.contains("if (target < 0 || target >= adapter.getItemCount()) return true;")
                        && rowKeyBody.contains("focusTmdbRecyclerItem(recycler, target);")
                        && rowKeyBody.contains("RecyclerView.ViewHolder visibleHolder = recycler.findViewHolderForAdapterPosition(target);")
                        && rowKeyBody.contains("visibleHolder.itemView.requestFocus();")
                        && rowKeyBody.contains("recycler.scrollToPosition(target);")
                        && rowKeyBody.contains("holder.itemView.requestFocus();"));
    }

    @Test
    public void detailFocusableButtonGroupsUseExplicitDpadNavigation() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int navigation = activity.indexOf("private boolean handleDetailEpisodeNavigationKey");
        int detailRows = activity.indexOf("private RecyclerView detailTmdbRecyclerContainingFocus", navigation);
        int flagKey = activity.indexOf("private boolean onDetailFlagKey");
        int rangeKey = activity.indexOf("private boolean onDetailEpisodeRangeKey");
        int toolKey = activity.indexOf("private boolean onDetailEpisodeToolKey");
        int episodeKey = activity.indexOf("private boolean onDetailEpisodeKey");
        int seasonKey = activity.indexOf("private boolean onDetailSeasonKey");
        int horizontal = activity.indexOf("private boolean onDetailHorizontalButtonGroupKey");
        int vertical = activity.indexOf("private boolean onDetailExternalLinksKey");
        int focusTarget = activity.indexOf("private View horizontalFocusTarget");
        String navigationBody = navigation >= 0 && detailRows > navigation ? activity.substring(navigation, detailRows) : "";
        String flagKeyBody = flagKey >= 0 && rangeKey > flagKey ? activity.substring(flagKey, rangeKey) : "";
        String rangeKeyBody = rangeKey >= 0 && toolKey > rangeKey ? activity.substring(rangeKey, toolKey) : "";
        String toolKeyBody = toolKey >= 0 && episodeKey > toolKey ? activity.substring(toolKey, episodeKey) : "";
        String seasonKeyBody = seasonKey >= 0 && horizontal > seasonKey ? activity.substring(seasonKey, horizontal) : "";
        String horizontalBody = horizontal >= 0 && vertical > horizontal ? activity.substring(horizontal, vertical) : "";
        String verticalBody = vertical >= 0 && focusTarget > vertical ? activity.substring(vertical, focusTarget) : "";

        assertTrue(activityPath + " is missing explicit detail button navigation helpers",
                navigation >= 0 && detailRows > navigation && horizontal >= 0 && vertical > horizontal && focusTarget > vertical);
        assertTrue("activity-level dispatch should guard every focusable detail button group before Android focus search runs",
                navigationBody.contains("isFocusInside(focus, binding.headerBar)")
                        && navigationBody.contains("onDetailHorizontalButtonGroupKey(binding.headerBar, null, focus, event)")
                        && navigationBody.contains("isFocusInside(focus, binding.fusionActions)")
                        && navigationBody.contains("onDetailHorizontalButtonGroupKey(binding.fusionActions, null, focus, event)")
                        && navigationBody.contains("isFocusInside(focus, binding.detailActions)")
                        && navigationBody.contains("onDetailHorizontalButtonGroupKey(binding.detailActions, null, focus, event)")
                        && navigationBody.contains("isFocusInside(focus, binding.seasonContainer)")
                        && navigationBody.contains("onDetailSeasonKey(focus, event)")
                        && navigationBody.contains("isFocusInside(focus, binding.externalLinksContainer)")
                        && navigationBody.contains("onDetailExternalLinksKey(focus, event)"));
        assertTrue("line, episode-page, episode-tool, and season buttons should handle DPAD_LEFT/RIGHT inside their own row",
                flagKeyBody.contains("onDetailHorizontalButtonGroupKey(binding.flagContainer, binding.flagScroll, focus, event)")
                        && rangeKeyBody.contains("onDetailHorizontalButtonGroupKey(binding.episodeRangeContainer, binding.episodeRangeScroll, view, event)")
                        && toolKeyBody.contains("onDetailHorizontalButtonGroupKey(binding.episodeHeader, null, view, event)")
                        && seasonKeyBody.contains("onDetailHorizontalButtonGroupKey(binding.seasonContainer, null, focus, event)"));
        assertTrue("season buttons should move vertically inside the season grid before leaving it",
                seasonKeyBody.contains("if (KeyUtil.isUpKey(event)) return focusDetailSeasonSibling(focus, true) || focusDetailEpisodeToolButton(View.FOCUS_UP) || focusDetailFlagButton();")
                        && seasonKeyBody.contains("return focusDetailSeasonSibling(focus, false) || focusDetailEpisodeRangeButton() || focusDetailEpisode();")
                        && seasonKeyBody.contains("private boolean focusDetailSeasonSibling(View focus, boolean up)")
                        && seasonKeyBody.contains("View target = FocusFinder.getInstance().findNextFocus(binding.seasonContainer, focus, direction);")
                        && seasonKeyBody.contains("return target.requestFocus(direction);"));
        assertTrue("horizontal button groups should move only to same-row neighbors and consume row boundaries",
                horizontalBody.contains("if (!KeyUtil.isLeftKey(event) && !KeyUtil.isRightKey(event)) return false;")
                        && horizontalBody.contains("if (!KeyUtil.isActionDown(event)) return true;")
                        && horizontalBody.contains("View target = horizontalFocusTarget(group, focus, KeyUtil.isLeftKey(event));")
                        && horizontalBody.contains("if (target == null) return true;")
                        && horizontalBody.contains("target.requestFocus(KeyUtil.isLeftKey(event) ? View.FOCUS_LEFT : View.FOCUS_RIGHT);")
                        && horizontalBody.contains("scrollHorizontalChildIntoView(scroll, target);"));
        assertTrue("external link buttons should move vertically inside the list and consume horizontal keys",
                verticalBody.contains("if (KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event)) return true;")
                        && verticalBody.contains("return moveDetailFocusVertically(binding.externalLinksContainer, focus, KeyUtil.isUpKey(event));"));
    }

    @Test
    public void detailExternalLinkFirstRowDpadUpReturnsToRecommendationCardRow() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int externalKey = activity.indexOf("private boolean onDetailExternalLinksKey");
        int horizontalTarget = activity.indexOf("private View horizontalFocusTarget", externalKey);
        int lastTmdb = activity.indexOf("private boolean focusLastVisibleTmdbRow");
        int firstTmdb = activity.indexOf("private boolean focusFirstVisibleTmdbRow", lastTmdb);
        String externalBody = externalKey >= 0 && horizontalTarget > externalKey ? activity.substring(externalKey, horizontalTarget) : "";
        String lastTmdbBody = lastTmdb >= 0 && firstTmdb > lastTmdb ? activity.substring(lastTmdb, firstTmdb) : "";

        assertTrue(activityPath + " is missing external-link upward focus helpers",
                externalKey >= 0 && horizontalTarget > externalKey && lastTmdb >= 0 && firstTmdb > lastTmdb);
        assertTrue("first external link DPAD_UP should leave the link list and focus the card row above it",
                externalBody.contains("if (KeyUtil.isUpKey(event) && detailFocusableIndex(binding.externalLinksContainer, focus) == 0) {")
                        && externalBody.contains("if (focusLastVisibleTmdbRow()) return true;")
                        && externalBody.contains("return false;")
                        && externalBody.contains("return moveDetailFocusVertically(binding.externalLinksContainer, focus, KeyUtil.isUpKey(event));"));
        assertTrue("external-link upward fallback should prefer the last visible TMDB row before the external links",
                lastTmdbBody.indexOf("focusTmdbRecycler(binding.personalAiList)") >= 0
                        && lastTmdbBody.indexOf("focusTmdbRecycler(binding.personalAiList)") < lastTmdbBody.indexOf("focusTmdbRecycler(binding.personalDoubanList)")
                        && lastTmdbBody.indexOf("focusTmdbRecycler(binding.personalDoubanList)") < lastTmdbBody.indexOf("focusTmdbRecycler(binding.personalTmdbList)")
                        && lastTmdbBody.indexOf("focusTmdbRecycler(binding.personalTmdbList)") < lastTmdbBody.indexOf("focusTmdbRecycler(binding.relatedList)")
                        && lastTmdbBody.indexOf("focusTmdbRecycler(binding.relatedList)") < lastTmdbBody.indexOf("focusTmdbRecycler(binding.creatorList)")
                        && lastTmdbBody.indexOf("focusTmdbRecycler(binding.creatorList)") < lastTmdbBody.indexOf("focusTmdbRecycler(binding.castList)")
                        && lastTmdbBody.indexOf("focusTmdbRecycler(binding.castList)") < lastTmdbBody.indexOf("focusTmdbRecycler(binding.episodePhotoList)"));
    }

    @Test
    public void leanbackDetailOverviewRendersAsPlainFullText() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int init = activity.indexOf("protected void initView(Bundle savedInstanceState)");
        int setup = activity.indexOf("private void setupOverviewInteraction()");
        int setupEnd = activity.indexOf("private void setupInlineControlFocus()", setup);
        int shouldFull = activity.indexOf("private boolean shouldShowFullOverview()");
        int overflow = activity.indexOf("private boolean isOverviewOverflowing()", shouldFull);
        String initBody = init >= 0 && setup > init ? activity.substring(init, setup) : "";
        String setupBody = setup >= 0 && setupEnd > setup ? activity.substring(setup, setupEnd) : "";
        String shouldFullBody = shouldFull >= 0 && overflow > shouldFull ? activity.substring(shouldFull, overflow) : "";

        assertTrue(activityPath + " is missing overview interaction setup helpers",
                init >= 0 && setup > init && setupEnd > setup && shouldFull >= 0 && overflow > shouldFull);
        assertTrue("TMDB detail initialization should route overview click/focus setup through one helper",
                initBody.contains("setupOverviewInteraction();")
                        && !initBody.contains("binding.overview.setOnClickListener(view -> toggleOverview());")
                        && !initBody.contains("binding.overviewToggle.setOnClickListener(view -> toggleOverview());"));
        assertTrue("mobile can keep tap-to-expand, but leanback overview must stay plain non-focusable text",
                setupBody.contains("if (Util.isMobile())")
                        && setupBody.contains("binding.overview.setOnClickListener(view -> toggleOverview());")
                        && setupBody.contains("binding.overviewToggle.setOnClickListener(view -> toggleOverview());")
                        && setupBody.contains("binding.overview.setOnClickListener(null);")
                        && setupBody.contains("binding.overview.setClickable(false);")
                        && setupBody.contains("binding.overview.setFocusable(false);")
                        && setupBody.contains("binding.overview.setFocusableInTouchMode(false);")
                        && setupBody.contains("binding.overviewToggle.setOnClickListener(null);")
                        && setupBody.contains("binding.overviewToggle.setClickable(false);")
                        && setupBody.contains("binding.overviewToggle.setFocusable(false);")
                        && setupBody.contains("binding.overviewToggle.setFocusableInTouchMode(false);"));
        assertTrue("leanback detail overview should show all text instead of exposing a fold/unfold focus target",
                shouldFullBody.contains("return !Util.isMobile();"));
    }

    @Test
    public void detailEpisodeDownToTmdbRowsUsesImmediateFocusWithoutScrollFlicker() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int focusRecycler = activity.indexOf("private boolean focusTmdbRecycler(RecyclerView recycler)");
        int scrollHelper = activity.indexOf("private void scrollDetailChildIntoView", focusRecycler);
        String body = focusRecycler >= 0 && scrollHelper > focusRecycler ? activity.substring(focusRecycler, scrollHelper) : "";

        assertTrue(activityPath + " is missing focusTmdbRecycler", focusRecycler >= 0);
        assertTrue("DPAD_DOWN from episode cards should stop row scrolling before moving into TMDB rows",
                body.contains("recycler.stopScroll();"));
        assertTrue("DPAD_DOWN from episode cards should align the TMDB row immediately instead of animating the outer scroll",
                body.contains("scrollDetailChildIntoViewNow(recycler, 12);")
                        && !body.contains("scrollDetailChildIntoView(recycler, 12);"));
        assertTrue("DPAD_DOWN from episode cards should not delay focus long enough to show a visual blink",
                body.contains("recycler.post(() ->")
                        && !body.contains("postDelayed"));
    }

    @Test
    public void detailEpisodeToolButtonsUseSharedFocusStroke() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int setup = activity.indexOf("private void setEpisodeToolButton(MaterialButton button, ThemeColors colors)");
        int setupEnd = activity.indexOf("private void applyEpisodeToolButtonsFocus()", setup);
        int apply = activity.indexOf("private void applyEpisodeToolButtonFocus(MaterialButton button, ThemeColors colors)");
        int applyEnd = activity.indexOf("private void tintTextTree", apply);
        String setupBody = setup >= 0 && setupEnd > setup ? activity.substring(setup, setupEnd) : "";
        String applyBody = apply >= 0 && applyEnd > apply ? activity.substring(apply, applyEnd) : "";

        assertTrue(activityPath + " is missing episode tool button setup", setup >= 0 && apply >= 0);
        assertTrue("episode tool buttons should clear selected/activated state so the current-playing accent cannot bleed into reverse/grid",
                setupBody.contains("button.setSelected(false);")
                        && setupBody.contains("button.setActivated(false);"));
        assertTrue("episode tool buttons should suppress Material ripple color so focus does not flash with the generic accent",
                setupBody.contains("button.setRippleColor(ColorStateList.valueOf(0x00000000));"));
        assertTrue("episode tool focus refresh should keep text and icons on the neutral detail theme color",
                applyBody.contains("button.setTextColor(colors.primary);")
                        && applyBody.contains("button.setIconTint(ColorStateList.valueOf(colors.primary));"));
        assertTrue("episode tool focus refresh should use the shared yellow focus stroke and themed idle stroke",
                applyBody.contains("focused ? FOCUS_STROKE : colors.lineStrong")
                        && !applyBody.contains("focused ? colors.accent : colors.lineStrong"));
    }

    @Test
    public void detailEpisodeHeaderToolsStayVisibleAndDpadReachable() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "activity_tmdb_detail.xml"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
        int flagKey = activity.indexOf("private boolean onDetailFlagKey");
        int rangeKey = activity.indexOf("private boolean onDetailEpisodeRangeKey");
        int episodeKey = activity.indexOf("private boolean onDetailEpisodeKey");
        String flagKeyBody = flagKey >= 0 && rangeKey > flagKey ? activity.substring(flagKey, rangeKey) : "";
        String rangeKeyBody = rangeKey >= 0 && episodeKey > rangeKey ? activity.substring(rangeKey, episodeKey) : "";
        int helper = activity.indexOf("private boolean focusDetailEpisodeToolButton(int direction)");
        int nextHelper = activity.indexOf("private boolean focusDetailFlagButton()", helper);
        String episodeKeyBody = episodeKey >= 0 && helper > episodeKey ? activity.substring(episodeKey, helper) : "";
        String helperBody = helper >= 0 && nextHelper > helper ? activity.substring(helper, nextHelper) : "";
        int dispatch = activity.indexOf("public boolean dispatchKeyEvent(KeyEvent event)");
        int inlineKey = activity.indexOf("private boolean handleInlineKey(KeyEvent event)", dispatch);
        String dispatchBody = dispatch >= 0 && inlineKey > dispatch ? activity.substring(dispatch, inlineKey) : "";

        assertTrue("detail layout must include reverse, filename, and grid/list controls",
                layout.indexOf("android:id=\"@+id/episodeReverse\"") >= 0
                        && layout.indexOf("android:id=\"@+id/episodeReverse\"") < layout.indexOf("android:id=\"@+id/episodeFileName\"")
                        && layout.indexOf("android:id=\"@+id/episodeFileName\"") < layout.indexOf("android:id=\"@+id/episodeViewMode\""));
        assertTrue("detail episode view-mode button must stay visible on TV detail pages",
                activity.contains("binding.episodeViewMode.setVisibility(View.VISIBLE);")
                        && !activity.contains("binding.episodeViewMode.setVisibility(shouldForceAdaptiveEpisodeGrid() ? View.GONE : View.VISIBLE);"));
        assertTrue("line-row DPAD_DOWN should still reach the episode header tool button before episode pages",
                flagKeyBody.contains("if (focusDetailEpisodeToolButton(View.FOCUS_DOWN)) return true;")
                        && flagKeyBody.indexOf("focusDetailEpisodeToolButton(View.FOCUS_DOWN)") < flagKeyBody.indexOf("focusDetailEpisodeRangeButton()"));
        assertTrue("episode-page DPAD_UP should return to seasons before header tools or lines",
                rangeKeyBody.contains("if (KeyUtil.isUpKey(event)) return focusDetailSeasonButton() || focusDetailEpisodeToolButton(View.FOCUS_UP) || focusDetailFlagButton();"));
        assertTrue("top-row episode DPAD_UP should fall back to the header tools before lines",
                episodeKeyBody.contains("if (focusDetailEpisodeRangeButton()) return true;")
                        && episodeKeyBody.contains("if (focusDetailEpisodeToolButton(View.FOCUS_UP)) return true;")
                        && episodeKeyBody.indexOf("focusDetailEpisodeRangeButton()") < episodeKeyBody.indexOf("focusDetailEpisodeToolButton(View.FOCUS_UP)")
                        && episodeKeyBody.indexOf("focusDetailEpisodeToolButton(View.FOCUS_UP)") < episodeKeyBody.indexOf("focusDetailFlagButton()"));
        assertTrue("episode header tool focusing should include every visible header tool",
                helperBody.contains("return focusDetailButton(binding.episodeTitle, direction)")
                        && helperBody.contains("|| focusDetailButton(binding.episodeReverse, direction)")
                        && helperBody.contains("|| focusDetailButton(binding.episodeFileName, direction)")
                        && helperBody.contains("|| focusDetailButton(binding.episodeViewMode, direction);")
                        && helperBody.contains("button.requestFocus(direction);"));
        assertTrue("episode header tools should move down to seasons or episode ranges without returning to lines",
                activity.contains("binding.episodeTitle.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeToolKey(view, keyCode, event));")
                        && activity.contains("binding.episodeReverse.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeToolKey(view, keyCode, event));")
                        && activity.contains("binding.episodeFileName.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeToolKey(view, keyCode, event));")
                        && activity.contains("binding.episodeViewMode.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeToolKey(view, keyCode, event));")
                        && activity.contains("if (KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event)) return onDetailHorizontalButtonGroupKey(binding.episodeHeader, null, view, event);")
                        && activity.contains("return focusDetailSeasonButton() || focusDetailEpisodeRangeButton() || focusDetailEpisode();"));
        assertTrue("activity-level key dispatch should guard the detail episode focus chain when child listeners are bypassed",
                dispatchBody.contains("if (handleDetailEpisodeNavigationKey(event)) return true;")
                        && dispatchBody.contains("isFocusInside(focus, binding.flagScroll)") && dispatchBody.contains("onDetailFlagKey(event.getKeyCode(), event)")
                        && dispatchBody.contains("isEpisodeToolButton(focus)") && dispatchBody.contains("onDetailEpisodeToolKey(focus, event.getKeyCode(), event)")
                        && dispatchBody.contains("isFocusInside(focus, binding.episodeRangeScroll)") && dispatchBody.contains("onDetailEpisodeRangeKey(focus, event.getKeyCode(), event)")
                        && dispatchBody.contains("isFocusInside(focus, binding.episodeContainer)") && dispatchBody.contains("onDetailEpisodeContainerKey(focus, event)")
                        && activity.contains("binding.episodeContainer.findContainingViewHolder(focus)")
                        && activity.contains("return moveDetailEpisodeFocus(position, event);"));
    }


    @Test
    public void mobileDetailEpisodeHeaderUsesCompactSeasonLabelAndShrinkableTools() throws Exception {
        String layout = readLayout("activity_tmdb_detail.xml");
        Path mobileRes = findMobileResPath();
        String defaults = new String(Files.readAllBytes(mobileRes.resolve(Path.of("values", "strings.xml"))), StandardCharsets.UTF_8);
        String simplified = new String(Files.readAllBytes(mobileRes.resolve(Path.of("values-zh-rCN", "strings.xml"))), StandardCharsets.UTF_8);
        String traditional = new String(Files.readAllBytes(mobileRes.resolve(Path.of("values-zh-rTW", "strings.xml"))), StandardCharsets.UTF_8);

        assertTrue("mobile detail must shorten the season selector label",
                defaults.contains("<string name=\"detail_episode_season_context\">Season %1$d</string>")
                        && simplified.contains("<string name=\"detail_episode_season_context\">第%1$d季</string>")
                        && traditional.contains("<string name=\"detail_episode_season_context\">第%1$d季</string>"));
        assertAndroidIdHasAttribute("episode season selector", layout, "episodeTitle", "android:ellipsize=\"end\"");
        assertAndroidIdHasAttribute("episode season selector", layout, "episodeTitle", "android:maxLines=\"1\"");
        for (String id : List.of("episodeReverse", "episodeFileName", "episodeViewMode")) {
            assertAndroidIdHasAttribute(id, layout, id, "android:layout_width=\"40dp\"");
            assertAndroidIdHasAttribute(id, layout, id, "android:minWidth=\"0dp\"");
        }
    }

    @Test
    public void mobileDetailEpisodeHeaderUsesIconOnlyToolbarWithStateAwareAccessibility() throws Exception {
        String layout = readLayout("activity_tmdb_detail.xml");
        String activity = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");

        assertTrue("detail episode toolbar must expose a state-aware reverse icon helper",
                activity.contains("private void updateEpisodeReverseButton()")
                        && activity.contains("binding.episodeReverse.setContentDescription(getString(episodeReverse ? R.string.detail_episode_forward : R.string.detail_episode_reverse));"));
        assertTrue("detail episode toolbar must update reverse state without restoring text labels",
                !activity.contains("binding.episodeReverse.setText(episodeReverse ? R.string.detail_episode_forward : R.string.detail_episode_reverse);")
                        && !activity.contains("binding.episodeViewMode.setText(switchToList ? R.string.detail_episode_view_list : R.string.detail_episode_view_grid);")
                        && !activity.contains("binding.episodeFileName.setText(showScraped ? R.string.detail_episode_file_name_original : R.string.detail_episode_file_name_scraped);"));

        assertIconOnlyEpisodeTool(layout, "episodeReverse", "ic_action_sort_asc", "detail_episode_reverse");
        assertIconOnlyEpisodeTool(layout, "episodeFileName", "ic_action_name_full", "detail_episode_file_name_scraped_action");
        assertIconOnlyEpisodeTool(layout, "episodeViewMode", "ic_site_list", "detail_episode_view_grid_action");
    }


    @Test
    public void nativeEnhancedEpisodeHeaderInitializesAllIconToolsBeforeFirstViewportBind() throws Exception {
        String activity = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int render = activity.indexOf("private void renderEpisodes()");
        int initialize = activity.indexOf("updateEpisodeToolButtons();", render);
        int bindViewport = activity.indexOf("applyEpisodeViewport(pagedDisplayEpisodes", render);
        int helper = activity.indexOf("private void updateEpisodeToolButtons()");
        int nextHelper = activity.indexOf("private void updateEpisodeReverseButton()", helper);
        String helperBody = helper >= 0 && nextHelper > helper ? activity.substring(helper, nextHelper) : "";

        assertTrue("native enhanced episode header must initialize every icon tool before the first viewport bind",
                render >= 0 && initialize > render && bindViewport > initialize);
        for (String id : List.of("episodeReverse", "episodeFileName", "episodeViewMode")) {
            assertTrue("native enhanced episode header must explicitly restore " + id + " visibility",
                    helperBody.contains("binding." + id + ".setVisibility(View.VISIBLE);"));
        }
        assertTrue("native enhanced episode header must synchronize all icon and accessibility states together",
                helperBody.contains("updateEpisodeReverseButton();")
                        && helperBody.contains("updateEpisodeFileNameButton();")
                        && helperBody.contains("updateEpisodeViewModeButton();"));
    }

    @Test
    public void inlineEpisodeModeToggleClicksImmediatelyOnMobileWhileTvKeepsFocusNavigation() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);

        int mobileMethod = activity.indexOf("private MaterialButton createInlineEpisodeModeButton()");
        int mobileMethodEnd = activity.indexOf("private void updateInlineEpisodeModeButton(MaterialButton button)", mobileMethod);
        int sharedMethod = activity.indexOf("private void showNativeEnhancedInlineEpisodes()");
        int sharedMethodEnd = activity.indexOf("private boolean moveEpisodeDialogPageFocus(", sharedMethod);

        assertTrue(activityPath + " is missing createInlineEpisodeModeButton", mobileMethod >= 0 && mobileMethodEnd > mobileMethod);
        assertTrue(activityPath + " is missing showNativeEnhancedInlineEpisodes", sharedMethod >= 0 && sharedMethodEnd > sharedMethod);

        String mobileBody = activity.substring(mobileMethod, mobileMethodEnd);
        String sharedBody = activity.substring(sharedMethod, sharedMethodEnd);

        assertTrue("mobile inline episode mode toggle should switch on the first tap instead of becoming touch-focusable",
                !mobileBody.contains("button.setFocusableInTouchMode(true);"));
        assertTrue("native-enhanced inline episode panel should keep remote-driven focus navigation",
                sharedBody.contains("button.setOnKeyListener(flagKeyListener);")
                        && sharedBody.contains("adapter.setOnKeyListener")
                        && sharedBody.contains("focusNativeEnhancedInlineEpisode(scroll, recycler, adapter, layout.spanCount())"));
    }

    @Test
    public void leanbackFusionEpisodeFocusTakesPriorityOverInlineSeekKeys() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);

        int dispatch = activity.indexOf("public boolean dispatchKeyEvent(KeyEvent event)");
        int dispatchEnd = activity.indexOf("private boolean handleDetailEpisodeNavigationKey(KeyEvent event)", dispatch);
        int seek = activity.indexOf("private boolean canInlineKeySeek(KeyEvent event)");
        int seekEnd = activity.indexOf("private boolean canInlineSeek()", seek);

        assertTrue(activityPath + " is missing dispatchKeyEvent", dispatch >= 0 && dispatchEnd > dispatch);
        assertTrue(activityPath + " is missing canInlineKeySeek", seek >= 0 && seekEnd > seek);

        String dispatchBody = activity.substring(dispatch, dispatchEnd);
        String seekBody = activity.substring(seek, seekEnd);
        int detailNavigation = dispatchBody.indexOf("if (handleDetailEpisodeNavigationKey(event)) return true;");
        int inlineKey = dispatchBody.indexOf("if (handleInlineKey(event)) return true;");

        assertTrue("detail episode focus must handle DPAD before inline playback maps LEFT/RIGHT to seek",
                detailNavigation >= 0 && inlineKey > detailNavigation);
        assertTrue("DPAD LEFT/RIGHT seek must only run when the player panel owns focus, so episode dialogs can navigate horizontally",
                seekBody.contains("if (isInlineMediaSeekKey(event)) return true;")
                        && seekBody.contains("if (isInlineControlsVisible()) return false;")
                        && seekBody.contains("View focus = getCurrentFocus();")
                        && seekBody.contains("focus == binding.playerPanel")
                        && seekBody.contains("inlineFullscreen && (focus == null || isFocusInside(focus, binding.playerPanel))")
                        && !seekBody.contains("inlineFullscreen || getCurrentFocus() == binding.playerPanel"));
    }

    @Test
    public void inlinePlayerConfirmSeparatesPanelClickAndHiddenKeyBehavior() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);

        int confirm = activity.indexOf("private void onInlinePanelConfirm()");
        int helper = activity.indexOf("private void enterInlineFullscreenOrShowControlsOnConfirm()", confirm);
        int nextMethod = activity.indexOf("private void toggleInlinePlayback()", helper);
        int toggle = activity.indexOf("private void toggleInlinePlayback()");
        int toggleEnd = activity.indexOf("private void toggleInlineControls()", toggle);
        int handle = activity.indexOf("private boolean handleInlineKey(KeyEvent event)");
        int handleEnd = activity.indexOf("private boolean handleInlineFullscreenHiddenKey(KeyEvent event)", handle);
        int hiddenPredicate = activity.indexOf("private boolean isInlineFullscreenHiddenPlaybackKey(KeyEvent event)", handleEnd);
        int hiddenPredicateEnd = activity.indexOf("private boolean handleInlineControlFocusKey(KeyEvent event)", hiddenPredicate);

        assertTrue(activityPath + " is missing onInlinePanelConfirm", confirm >= 0);
        assertTrue(activityPath + " is missing enterInlineFullscreenOrShowControlsOnConfirm", helper > confirm && nextMethod > helper);
        assertTrue(activityPath + " is missing toggleInlinePlayback", toggle >= 0 && toggleEnd > toggle);
        assertTrue(activityPath + " is missing handleInlineKey", handle >= 0 && handleEnd > handle);
        assertTrue(activityPath + " is missing fullscreen hidden-key predicate", hiddenPredicate > handleEnd && hiddenPredicateEnd > hiddenPredicate);

        String confirmBody = activity.substring(confirm, helper);
        String helperBody = activity.substring(helper, nextMethod);
        String handleBody = activity.substring(handle, handleEnd);
        String hiddenPredicateBody = activity.substring(hiddenPredicate, hiddenPredicateEnd);
        int enter = handleBody.indexOf("if (KeyUtil.isEnterKey(event))");
        int playbackGuard = handleBody.indexOf("if (!inlineStarted || service() == null || player() == null || player().isEmpty())");
        String enterBody = javaBlockAt(handleBody, "if (KeyUtil.isEnterKey(event))");

        assertFalse("TV confirm must not restart playback while the player is loading or stopped", confirmBody.contains("onPlay();"));
        assertFalse("TV confirm behavior must not depend on the stale inlineStarted flag", confirmBody.contains("inlineStarted"));
        assertFalse("global TV inline key routing must remain available before playback starts",
                handleBody.contains("if (!isInlinePlayerMode() || !inlineStarted) return false;"));
        assertTrue("fullscreen DPAD center must be handled before playback-only key guards", enter >= 0 && playbackGuard > enter);
        assertFalse("hidden fullscreen key routing must not require a started player or attached service",
                hiddenPredicateBody.contains("!inlineStarted") || hiddenPredicateBody.contains("service() == null"));
        assertTrue("embedded TV player panel click should enter fullscreen before exposing controls",
                confirmBody.contains("enterInlineFullscreenOrShowControlsOnConfirm();"));
        assertTrue("fullscreen player panel click fallback should expose controls without changing playback",
                confirmBody.contains("showInlineControls(true);")
                        && !confirmBody.contains("toggleInlinePlayback();"));
        String toggleCall = "toggleInlinePlayback();";
        String mobileFallback = "showInlineControls(true);";
        String normalizedEnter = enterBody.replaceAll("\\s+", " ").trim();
        String expectedEnter = "if (KeyUtil.isEnterKey(event)) { if (KeyUtil.isActionUp(event)) { "
                + "if (Util.isLeanback()) " + toggleCall + " else " + mobileFallback
                + " } return true; }";
        assertTrue("fullscreen confirm must only perform the platform-specific action on ACTION_UP",
                normalizedEnter.equals(expectedEnter));
        assertTrue("fullscreen confirm must toggle playback exactly once in the complete enter branch",
                enterBody.indexOf(toggleCall) >= 0
                        && enterBody.indexOf(toggleCall) == enterBody.lastIndexOf(toggleCall));
        assertTrue("mobile fullscreen confirm must expose controls exactly once in the complete enter branch",
                enterBody.indexOf(mobileFallback) >= 0
                        && enterBody.indexOf(mobileFallback) == enterBody.lastIndexOf(mobileFallback));
        assertTrue("TV fullscreen playback toggles must keep controls hidden like the native leanback player",
                activity.substring(toggle, toggleEnd)
                        .contains("if (Util.isLeanback() && inlineFullscreen) hideInlineControls();"));
        // 控制栏首个按钮必须是"下一集"：原先排在它前面的播放/暂停按钮会拖慢遥控器连播，
        // 且从未登记进 PlayerButtonSetting 因而设置项管不到，已移除。播放/暂停由上面
        // expectedEnter 钉住的全屏确认键路径承担，与影视原生控制栏首项为 next 一致。
        // 钉"第一位"而非"旧 id 不存在"：在"下一集"前插任何新按钮都必须红。
        String fusionLayout = readLayout("activity_tmdb_detail.xml");
        String idAttribute = "android:id=\"@+id/";
        int actionRow = fusionLayout.indexOf(idAttribute + "playerActionRow\"");
        assertTrue("detail layout must contain playerActionRow", actionRow >= 0);
        int firstControlId = fusionLayout.indexOf(idAttribute, actionRow + 1);
        assertTrue("playerActionRow must contain at least one control", firstControlId > actionRow);
        int firstControlStart = firstControlId + idAttribute.length();
        String firstControl = fusionLayout.substring(firstControlStart, fusionLayout.indexOf('"', firstControlStart));
        assertEquals("下一集 must stay the first inline control so the remote reaches it in one press",
                "playerNext", firstControl);
        // 匹配用法形态而非裸名字：否则一句提及旧按钮的注释就会误红。
        assertFalse("removed play/pause action must not be re-wired in the detail activity",
                activity.contains("binding.playerPlaybackAction"));
        assertTrue("TV inline confirm should enter fullscreen before falling back to the controls overlay",
                helperBody.contains("if (Util.isLeanback() && canEnterInlineFullscreenOnConfirm())")
                        && helperBody.contains("enterInlineFullscreen();")
                        && helperBody.contains("private boolean canEnterInlineFullscreenOnConfirm()")
                        && helperBody.contains("!inlinePiPLayout")
                        && helperBody.contains("!isInPictureInPictureMode()")
                        && helperBody.contains("showInlineControls(true);")
                        && helperBody.indexOf("enterInlineFullscreen();") < helperBody.indexOf("showInlineControls(true);"));
    }

    @Test
    public void tmdbEpisodeDataIsBoundBackToSourceEpisodesAndRefreshesByDataSeason() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        Path servicePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "service", "TmdbService.java"));
        String service = new String(Files.readAllBytes(servicePath), StandardCharsets.UTF_8);

        int bindSeason = activity.indexOf("private void bindSeasonEpisodes(List<Episode> sourceEpisodes)");
        int dataSeason = activity.indexOf("private int tmdbEpisodeDataSeason(List<Episode> sourceEpisodes)", bindSeason);
        int fetchSeason = activity.indexOf("private void fetchSeasonIfNeeded(int seasonNumber)");
        int updateSkeleton = activity.indexOf("private void updateEpisodeSkeleton()");

        assertTrue(activityPath + " is missing bindSeasonEpisodes", bindSeason >= 0 && dataSeason > bindSeason);
        assertTrue(activityPath + " is missing fetchSeasonIfNeeded", fetchSeason >= 0 && updateSkeleton > fetchSeason);

        String bindBody = activity.substring(bindSeason, dataSeason);
        String fetchBody = activity.substring(fetchSeason, updateSkeleton);

        assertTrue("detail episodes should bind matched TMDB objects back onto source Episode items for playback cards and dialogs",
                bindBody.contains("bindTmdbEpisodes(sourceEpisodes, tmdbSeason);")
                        && activity.contains("TmdbEpisode tmdbEpisode = tmdbEpisodes.get(position.number());")
                        && activity.contains("TmdbEpisodeMatcher.shouldApplyMapped(episode, tmdbEpisode, position.season(), position.number())")
                        && activity.contains("episode.setMappedTmdbEpisode(tmdbEpisode);")
                        && activity.contains("episode.setTmdbEpisode(valid ? tmdbEpisode : null);"));
        assertTrue("season fetch completion should refresh against the active TMDB data season, not only the selected source season",
                fetchBody.contains("seasonNumber == tmdbEpisodeDataSeason(selectedFlag == null ? null : selectedFlag.getEpisodes())"));
        assertTrue("stale split-season TMDB caches should trigger a one-shot fresh first-season probe for long single-season shows",
                bindBody.contains("refreshFirstSeasonIfStaleSplit(sourceEpisodes);")
                        && activity.contains("List<TmdbEpisode> cachedEpisodes = tmdbSeasonEpisodes.get(firstSeason);")
                        && activity.contains("int cachedCount = cachedEpisodes == null ? 0 : cachedEpisodes.size();")
                        && activity.contains("if (cachedCount >= neededCount) return;")
                        && activity.contains("fetchSeasonIfNeeded(firstSeason, true);")
                        && activity.contains("seasonEpisodeCounts.put(seasonNumber, episodes.size());")
                        && service.contains("season(@NonNull TmdbItem item, int seasonNumber, @NonNull TmdbConfig config, JsonObject detail, boolean refresh)")
                        && service.contains("refresh ? null : readFirstCache(lookupFiles, ttl, \"detail\".equals(type))")
                        && service.contains("readFirstCache(lookupFiles, Long.MAX_VALUE)"));
    }

    @Test
    public void inlineFullscreenExitRestoresEmbeddedPlayerLayout() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);

        int restore = activity.indexOf("private void restoreInlinePlayerPanelAfterOverlay()");
        int exitFullscreen = activity.indexOf("private void exitInlineFullscreen()");
        int exitPiP = activity.indexOf("private void exitInlinePiPLayout()");
        int enterFullscreen = activity.indexOf("private void enterInlineFullscreen()");
        int enterPiP = activity.indexOf("private void enterInlinePiPLayout()");
        int focusPlayerPanel = activity.indexOf("private void focusInlinePlayerPanel()");
        int backFromFullscreen = activity.indexOf("private void backFromInlineFullscreen()");
        int handleInlineKey = activity.indexOf("private boolean handleInlineKey(KeyEvent event)");
        int onBackInvoked = activity.indexOf("protected void onBackInvoked()");

        assertTrue(activityPath + " is missing restoreInlinePlayerPanelAfterOverlay", restore >= 0);
        assertTrue(activityPath + " is missing exitInlineFullscreen", exitFullscreen >= 0);
        assertTrue(activityPath + " is missing exitInlinePiPLayout", exitPiP >= 0);
        assertTrue(activityPath + " is missing enterInlineFullscreen", enterFullscreen >= 0);
        assertTrue(activityPath + " is missing enterInlinePiPLayout", enterPiP >= 0);
        assertTrue(activityPath + " is missing focusInlinePlayerPanel", focusPlayerPanel >= 0);
        assertTrue(activityPath + " is missing backFromInlineFullscreen", backFromFullscreen >= 0);
        assertTrue(activityPath + " is missing handleInlineKey", handleInlineKey >= 0);
        assertTrue(activityPath + " is missing onBackInvoked", onBackInvoked >= 0);

        String restoreBody = activity.substring(restore, exitFullscreen);
        String focusBody = activity.substring(focusPlayerPanel, activity.indexOf("private void setDetailActionButton", focusPlayerPanel));
        String enterFullscreenBody = activity.substring(enterFullscreen, activity.indexOf("private void applyInlineShortDramaMode()", enterFullscreen));
        String fullscreenBody = activity.substring(exitFullscreen, exitPiP);
        String backFromFullscreenBody = activity.substring(backFromFullscreen, activity.indexOf("private void finishPlaybackToHome()", backFromFullscreen));
        String enterPiPBody = activity.substring(enterPiP, exitPiP);
        String pipBody = activity.substring(exitPiP, activity.indexOf("private void scheduleMobileInlineSideControlMarginUpdate()", exitPiP));
        String keyBody = activity.substring(handleInlineKey, activity.indexOf("private boolean handleInlineSeekKey", handleInlineKey));
        String backBody = activity.substring(onBackInvoked, activity.indexOf("private void saveInlineHistory()", onBackInvoked));

        assertTrue("fullscreen/PiP exits must reset the player surface back to embedded match-parent sizing",
                restoreBody.contains("setInlineVideoFrame(binding.exo, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);")
                        && restoreBody.contains("setInlineVideoFrame(binding.danmaku, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);")
                        && restoreBody.contains("binding.playerPanel.setTranslationZ(0f);")
                        && restoreBody.contains("setPlayerCard(lightTheme ? ThemeColors.light() : ThemeColors.dark());"));
        assertTrue("embedded player restore must force a layout pass for the PlayerView, SurfaceView, danmaku, and detail scroll",
                restoreBody.contains("binding.playerPanel.requestLayout();")
                        && restoreBody.contains("binding.exo.requestLayout();")
                        && restoreBody.contains("View surface = binding.exo.getVideoSurfaceView();")
                        && restoreBody.contains("if (surface != null) surface.requestLayout();")
                        && restoreBody.contains("binding.danmaku.requestLayout();")
                        && restoreBody.contains("binding.scroll.requestLayout();"));
        assertTrue("fullscreen exit calls restoreInlinePlayerPanelAfterOverlay to reset surface sizing and theme without reparent",
                fullscreenBody.contains("applyInlinePlayerEmbeddedLayout();")
                        && fullscreenBody.contains("resetInlineShortDramaMode();")
                        && fullscreenBody.contains("restoreInlinePlayerPanelAfterOverlay();")
                        && !fullscreenBody.contains("closeDetailFullscreenPlayer();")
                        && !fullscreenBody.contains("playerParent.addView(binding.playerPanel")
                        && !fullscreenBody.contains("binding.root.addView(binding.playerPanel"));
        assertTrue("PiP exit calls restoreInlinePlayerPanelAfterOverlay without reparent",
                pipBody.contains("restoreInlinePlayerPanelAfterOverlay();")
                        && !pipBody.contains("inlinePiPParent.addView(binding.playerPanel"));
        assertTrue("detail-player fullscreen Back must close playback back to the detail page on TV and mobile, while fusion keeps embedded exit",
                backFromFullscreenBody.contains("if (isPlayerMode())")
                        && backFromFullscreenBody.indexOf("exitInlineFullscreen();") < backFromFullscreenBody.indexOf("closeDetailFullscreenPlayer();")
                        && backFromFullscreenBody.contains("return;")
                        && !backFromFullscreenBody.contains("Util.isLeanback() && isPlayerMode()")
                        && !backFromFullscreenBody.contains("finishPlaybackToHome();")
                        && !backFromFullscreenBody.contains("Setting.isPlayBackToDetail()")
                        && focusBody.contains("if (!isInlinePlayerMode()) return;")
                        && !focusBody.contains("if (!isFusionMode()) return;"));
        assertTrue("leanback fullscreen Back should hide visible controls before exiting fullscreen",
                keyBody.indexOf("KeyUtil.isBackKey(event) && Util.isLeanback() && inlineFullscreen") >= 0
                        && keyBody.indexOf("KeyUtil.isBackKey(event) && isInlineControlsVisible()") < keyBody.indexOf("KeyUtil.isBackKey(event) && Util.isLeanback() && inlineFullscreen")
                        && keyBody.contains("if (KeyUtil.isActionUp(event)) backFromInlineFullscreen();")
                        && backBody.indexOf("if (Util.isLeanback() && inlineFullscreen)") >= 0
                        && backBody.indexOf("if (isInlineControlsVisible())") < backBody.indexOf("if (Util.isLeanback() && inlineFullscreen)")
                        && backBody.contains("backFromInlineFullscreen();"));
    }

    @Test
    public void mobileInlinePipClearsEmbeddedOffsetAndStopsPlaybackWhenClosed() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);

        int enterPipLayout = activity.indexOf("private void enterInlinePiPLayout()");
        int exitPipLayout = activity.indexOf("private void exitInlinePiPLayout()", enterPipLayout);
        int onPipChanged = activity.indexOf("public void onPictureInPictureModeChanged(");
        int onPipChangedEnd = activity.indexOf("protected boolean onSourceHttpError", onPipChanged);
        int finishClosedPip = activity.indexOf("private void finishIfInlinePipClosed()");
        int finishClosedPipEnd = activity.indexOf("protected boolean onSourceHttpError", finishClosedPip);

        assertTrue(activityPath + " is missing enterInlinePiPLayout", enterPipLayout >= 0 && exitPipLayout > enterPipLayout);
        assertTrue(activityPath + " is missing onPictureInPictureModeChanged", onPipChanged >= 0 && onPipChangedEnd > onPipChanged);
        assertTrue(activityPath + " is missing finishIfInlinePipClosed", finishClosedPip >= 0 && finishClosedPipEnd > finishClosedPip);

        String enterBody = activity.substring(enterPipLayout, exitPipLayout);
        String pipChangedBody = activity.substring(onPipChanged, onPipChangedEnd);
        String finishBody = activity.substring(finishClosedPip, finishClosedPipEnd);
        assertTrue("PiP layout must clear the portrait embedded translation so the video stays centered in the system window",
                enterBody.contains("binding.playerPanel.setTranslationY(0f);"));
        assertTrue("leaving PiP must defer close detection until the Activity lifecycle settles",
                pipChangedBody.contains("App.post(this::finishIfInlinePipClosed, 0);"));
        assertTrue("closing PiP must save progress, stop inline synchronization, and release playback while expanding keeps it alive",
                finishBody.contains("getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)")
                        && finishBody.contains("PipExitDecision.shouldFinishAfterPipExit(")
                        && finishBody.contains("saveInlineHistory();")
                        && finishBody.contains("stopInlinePlaybackSync();")
                        && finishBody.contains("finishPlayback();"));
    }

    @Test
    public void inlinePlayerScaleSurvivesPlayerCoreRebuild() throws Exception {
        String activity = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int setScale = activity.indexOf("private void setInlineScale(int scale)");
        int previewScale = activity.indexOf("private void setInlinePreviewScale(int scale)", setScale);

        assertTrue("TmdbDetailActivity is missing setInlineScale", setScale >= 0);
        assertTrue("TmdbDetailActivity is missing setInlinePreviewScale", previewScale > setScale);

        String setScaleBody = activity.substring(setScale, previewScale);
        assertTrue("inline scale must update PlaybackActivity's requested resize mode so a player-core rebuild restores the same style",
                setScaleBody.contains("applyResizeMode(scale);"));
        assertFalse("inline scale must not bypass PlaybackActivity's resize-mode state",
                setScaleBody.contains("binding.exo.setResizeMode(scale);"));
    }

    @Test
    public void inlinePlayerPanelStaysInRootWithoutReparent() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8).replace("\r\n", "\n");

        int enter = activity.indexOf("private void enterInlineFullscreen()");
        int exit = activity.indexOf("private void exitInlineFullscreen()");
        int sync = activity.indexOf("private void syncInlinePlayerToSpacer()");
        int applyEmbedded = activity.indexOf("private void applyInlinePlayerEmbeddedLayout()");
        int applyFullscreen = activity.indexOf("private void applyInlinePlayerFullscreenLayout()");

        assertTrue(activityPath + " is missing enterInlineFullscreen", enter >= 0);
        assertTrue(activityPath + " is missing exitInlineFullscreen", exit >= 0);
        assertTrue("playerPanel must stay in root — no reparent, no addView/removeView in fullscreen enter/exit",
                !activity.contains("binding.root.addView(binding.playerPanel")
                        && !activity.contains("binding.root.removeView(binding.playerPanel")
                        && !activity.contains("playerParent.addView(binding.playerPanel"));
        assertTrue("playerPanel must apply FrameLayout.LayoutParams (root-level) for both embedded and fullscreen modes",
                applyEmbedded >= 0 && applyFullscreen >= 0
                        && activity.contains("new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT"));
        assertTrue("embedded mode must sync playerPanel.translationY to spacer position via scroll listener",
                sync >= 0
                        && activity.contains("binding.scroll.setOnScrollChangeListener")
                        && activity.contains("syncInlinePlayerToSpacer()")
                        && activity.contains("binding.playerPanelSpacer"));
        String enterBody = activity.substring(enter, exit);
        String exitBody = activity.substring(exit, activity.indexOf("private void enterInlinePiPLayout()", exit));
        assertTrue("fullscreen enter must switch to fullscreen LayoutParams (铺满 root), not reparent",
                enterBody.contains("applyInlinePlayerFullscreenLayout();")
                        && !enterBody.contains("reattachVideoSurfaceAfterReparent"));
        assertTrue("fullscreen exit must switch back to embedded LayoutParams (with translationY sync), not reparent",
                exitBody.contains("applyInlinePlayerEmbeddedLayout();")
                        && !exitBody.contains("reattachVideoSurfaceAfterReparent"));
    }

    @Test
    public void mobileInlineFullscreenKeepsLiveVideoVisibleDuringLayoutChanges() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8).replace("\r\n", "\n");

        int enter = activity.indexOf("private void enterInlineFullscreen()");
        int exit = activity.indexOf("private void exitInlineFullscreen()");
        int exitEnd = activity.indexOf("private void enterInlinePiPLayout()", exit);

        assertTrue(activityPath + " is missing enterInlineFullscreen", enter >= 0);
        assertTrue(activityPath + " is missing exitInlineFullscreen", exit >= 0 && exitEnd > exit);
        String enterBody = activity.substring(enter, exit);
        String exitBody = activity.substring(exit, exitEnd);
        assertTrue("layout-only fullscreen entry and exit must keep the live video visible instead of covering it with a cached frame",
                !enterBody.contains("showInlineTransitionFrame();")
                        && !exitBody.contains("showInlineTransitionFrame();")
                        && !enterBody.contains("scheduleInlineTransitionFrameTimeout();")
                        && !exitBody.contains("scheduleInlineTransitionFrameTimeout();"));
        assertTrue("the root-resident player must not retain the obsolete PixelCopy transition-frame pipeline",
                !activity.contains("captureInlineTransitionFrame()")
                        && !activity.contains("PixelCopy.request(")
                        && !activity.contains("inlineTransitionBitmap")
                        && !activity.contains("inlineTransitionFrame"));
    }

    @Test
    public void fusionPlayerSpacerOnlyAcceptsFocusOnLeanback() throws Exception {
        String layout = readLayout("activity_tmdb_detail.xml");
        String activity = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        String controller = readJava("com", "fongmi", "android", "tv", "ui", "detail", "FusionDetailController.java");

        int spacerStart = layout.indexOf("android:id=\"@+id/playerPanelSpacer\"");
        int spacerEnd = layout.indexOf("/>", spacerStart);
        assertTrue("detail layout must contain playerPanelSpacer", spacerStart >= 0 && spacerEnd > spacerStart);
        String spacerTag = layout.substring(spacerStart, spacerEnd);
        assertTrue("transparent spacer must not be a mobile/touch focus target by default",
                spacerTag.contains("android:focusable=\"false\"")
                        && spacerTag.contains("android:focusableInTouchMode=\"false\""));
        assertTrue("transparent spacer must be hidden from accessibility services",
                spacerTag.contains("android:importantForAccessibility=\"no\""));

        int setupStart = activity.indexOf("private void setupInlineFocusNavigation()");
        int setupEnd = activity.indexOf("private void setupHorizontalFocusChain()", setupStart);
        String setupBody = activity.substring(setupStart, setupEnd);
        int mobileGuard = setupBody.indexOf("if (Util.isMobile()) return;");
        int enableFocus = setupBody.indexOf("binding.playerPanelSpacer.setFocusable(true);");
        int disableTouchFocus = setupBody.indexOf("binding.playerPanelSpacer.setFocusableInTouchMode(false);");
        int focusBridge = setupBody.indexOf("binding.playerPanelSpacer.setOnFocusChangeListener");
        assertTrue("spacer focus must be enabled only after the mobile guard",
                mobileGuard >= 0 && enableFocus > mobileGuard);
        assertTrue("TV spacer must remain outside touch-mode focus and then install the focus bridge",
                disableTouchFocus > enableFocus && focusBridge > disableTouchFocus);
        assertTrue("fusion mode must keep the spacer visible so it reserves player space",
                controller.contains("binding.playerPanelSpacer.setVisibility(View.VISIBLE);"));
    }

    @Test
    public void fusionFocusBridgeDoesNotShipTemporaryDebugLogs() throws Exception {
        String activity = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");

        assertTrue("temporary TmdbDetailFocus logs must be removed before merge",
                !activity.contains("TmdbDetailFocus"));
    }

    @Test
    public void nativeEnhancedEpisodeCardsUseUnifiedTvFocusAndPlayingState() throws Exception {
        Path adapterPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbEpisodeAdapter.java"));
        String adapter = new String(Files.readAllBytes(adapterPath), StandardCharsets.UTF_8);
        Path selectorPath = findMainResPath().resolve(Path.of("drawable", "selector_episode_card.xml"));
        String selector = new String(Files.readAllBytes(selectorPath), StandardCharsets.UTF_8);

        int method = adapter.indexOf("private void applyNativeEnhancedCardFocus");
        assertTrue(adapterPath + " is missing native enhanced card focus styling", method >= 0);
        assertTrue("native enhanced episode focus must use the same yellow stroke as TV buttons",
                adapter.contains("private static final int FOCUS_STROKE = 0xFFFFD166;")
                        && adapter.indexOf("holder.binding.getRoot().setStrokeColor(focused ? FOCUS_STROKE : activated ? activeStrokeColor : 0x00000000);", method) > method
                        && adapter.indexOf("Drawable foreground = focused", method) > method
                        && adapter.indexOf("TmdbCardFocusHelper.foregroundBorder(holder.binding.getRoot(), FOCUS_STROKE, FOCUS_STROKE_DP)", method) > method
                        && adapter.indexOf("holder.binding.getRoot().setForeground(foreground);", method) > method);
        assertTrue("currently playing episode cards must keep the green active border when not focused",
                adapter.contains("private int activeStrokeColor = 0xFF2CC56F;")
                        && adapter.indexOf("activated ? ACTIVE_STROKE_DP : 0", method) > method);
        assertTrue("focused episode cards must avoid scale focus because detail rows clip enlarged cards",
                !adapter.contains("FOCUS_SCALE")
                        && adapter.indexOf("scaleX(", method) < 0
                        && adapter.indexOf("scaleY(", method) < 0);
        assertTrue("legacy episode foreground selector must also keep focus yellow and playing green",
                selector.contains("android:color=\"#FFD166\"")
                        && selector.contains("android:color=\"#2CC56F\""));
    }

    @Test
    public void currentInlineEpisodeCardEntersFullscreenWithoutReloading() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int onPlay = source.indexOf("private void onPlay()");
        int playDetailFullscreen = source.indexOf("private void playDetailFullscreen()");
        int playInline = source.indexOf("private void playInline()");
        int stop = source.indexOf("private void stopInlinePlayerForReload()");
        int start = source.indexOf("private void startInlinePlayer(Result result)");
        String onPlayBody = source.substring(onPlay, playDetailFullscreen);
        String detailBody = source.substring(playDetailFullscreen, playInline);
        String stopBody = source.substring(stop, start);
        String startBody = source.substring(start, source.indexOf("private void searchInlineDanmaku", start));

        assertTrue("current inline episode clicks must reuse playback before fusion reloads",
                onPlayBody.indexOf("enterInlineFullscreenIfCurrentInlinePlayback(selectedEpisode)") < onPlayBody.indexOf("if (isFusionMode()) playInline();"));
        assertTrue("detail-player fullscreen entry must not reload the already playing episode",
                detailBody.contains("boolean current = isCurrentInlinePlayback(selectedEpisode);")
                        && detailBody.contains("if (!current) playInline();"));
        assertTrue("current inline playback identity must include episode, site key, and line flag",
                source.contains("private Episode inlinePlaybackEpisode;")
                        && source.contains("private String inlinePlaybackKey = \"\";")
                        && source.contains("private String inlinePlaybackFlag = \"\";")
                        && source.contains("TextUtils.equals(getKeyText(), inlinePlaybackKey)")
                        && source.contains("TextUtils.equals(selectedFlag.getFlag(), inlinePlaybackFlag)"));
        assertTrue("current inline playback identity must be cleared before a real reload",
                stopBody.contains("inlinePlaybackEpisode = null;")
                        && stopBody.contains("inlinePlaybackKey = \"\";")
                        && stopBody.contains("inlinePlaybackFlag = \"\";"));
        assertTrue("current inline playback identity must be recorded when playback starts",
                startBody.contains("inlinePlaybackEpisode = selectedEpisode;")
                        && startBody.contains("inlinePlaybackKey = getKeyText();")
                        && startBody.contains("inlinePlaybackFlag = selectedFlag == null ? \"\" : selectedFlag.getFlag();"));
    }

    @Test
    public void inlinePlaybackSpeedUsesPersonalDefaultAndRestoresAfterHold() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int speedEnd = source.indexOf("public void onSpeedEnd()");
        int onBright = source.indexOf("public void onBright(int progress)", speedEnd);
        int initHistory = source.indexOf("private void initHistory()");
        int helper = source.indexOf("private float getInlinePlaybackSpeed()", initHistory);
        int updatePlayLabel = source.indexOf("private void updatePlayLabel()", helper);
        int start = source.indexOf("private void startInlinePlayer(Result result, long resumePosition)");
        int searchDanmaku = source.indexOf("private void searchInlineDanmaku(Result result)", start);
        int changeSpeed = source.indexOf("private void changeInlineSpeed()");
        int setSpeed = source.indexOf("private void setInlineSpeed(float speed)", changeSpeed);
        int resetSpeed = source.indexOf("private boolean resetInlineSpeed()", setSpeed);
        int refreshPlayback = source.indexOf("private void refreshInlinePlayback()", resetSpeed);
        int normalize = source.indexOf("private float normalizeInlineSpeed(float speed)");
        int setText = source.indexOf("private void setInlineSpeedText(CharSequence text)", normalize);
        int prepare = source.indexOf("protected void onPrepare()");
        int resume = source.indexOf("private long getInlineResumePosition()", prepare);

        assertTrue("native enhanced speed release handler must exist", speedEnd >= 0 && onBright > speedEnd);
        assertTrue("native enhanced speed initialization helper must exist", initHistory >= 0 && helper > initHistory && updatePlayLabel > helper);
        assertTrue("native enhanced startInlinePlayer must exist", start >= 0 && searchDanmaku > start);
        assertTrue("native enhanced manual speed handlers must exist", changeSpeed >= 0 && setSpeed > changeSpeed && resetSpeed > setSpeed && refreshPlayback > resetSpeed);
        assertTrue("native enhanced normalizeInlineSpeed must exist", normalize >= 0 && setText > normalize);
        assertTrue("native enhanced onPrepare must exist", prepare >= 0 && resume > prepare);
        String speedEndBody = source.substring(speedEnd, onBright);
        String initBody = source.substring(initHistory, helper);
        String helperBody = source.substring(helper, updatePlayLabel);
        String startBody = source.substring(start, searchDanmaku);
        String changeSpeedBody = source.substring(changeSpeed, setSpeed);
        String resetSpeedBody = source.substring(resetSpeed, refreshPlayback);
        String normalizeBody = source.substring(normalize, setText);
        String prepareBody = source.substring(prepare, resume);

        assertFalse("native enhanced startup must not create a per-show speed override",
                initBody.contains("resetInitialPlaybackSpeed()"));
        assertTrue("native enhanced helper must preserve explicit 1.0x and otherwise use the personal default",
                helperBody.contains("history.getPlaybackSpeed(PlayerSetting.getDefaultSpeed())"));
        assertTrue("native enhanced playback start must apply the resolved playback speed",
                startBody.contains("setInlineSpeed(getInlinePlaybackSpeed());"));
        assertTrue("native enhanced prepare must reapply the resolved playback speed",
                prepareBody.contains("setInlineSpeed(getInlinePlaybackSpeed());"));
        assertTrue("native enhanced long-press release must restore the resolved playback speed",
                speedEndBody.contains("history == null ? inlineGestureSpeed : getInlinePlaybackSpeed()"));
        assertTrue("native enhanced speed cycle must save a per-show override",
                changeSpeedBody.contains("history.setUserSpeed(player().getSpeed())"));
        assertTrue("native enhanced speed toggle must save a per-show override",
                resetSpeedBody.contains("history.setUserSpeed(player().getSpeed())"));
        assertTrue("native enhanced persistent speed toggle must use the current show's effective speed",
                resetSpeedBody.contains("player().toggleSpeed(getInlinePlaybackSpeed())"));
        assertTrue("native enhanced invalid speed fallback must use personal default speed",
                normalizeBody.contains("PlayerSetting.getDefaultSpeed()"));
    }

    @Test
    public void fusionInlinePlayerDelegatesSharedUiSetup() throws Exception {
        String activity = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        String chrome = readJava("com", "fongmi", "android", "tv", "ui", "player", "VodPlayerChrome.java");
        String controller = readJava("com", "fongmi", "android", "tv", "ui", "player", "VodPlayerUiController.java");
        int method = activity.indexOf("private void initFusionPlayer()");
        int mobile = activity.indexOf("private void setupMobileInlineControl()", method);
        String body = activity.substring(method, mobile);

        assertTrue("fusion inline player should delegate shared UI setup to VodPlayerUiController",
                body.contains("inlinePlayerUi = new VodPlayerUiController"));
        assertTrue("fusion inline player should pass a chrome object instead of wiring OSD views inline",
                body.contains("VodPlayerChrome.fromTmdbDetail(binding)"));
        assertTrue("fusion inline player should keep legacy fields backed by the shared controller during migration",
                body.contains("inlineControlController = inlinePlayerUi.controlController();")
                        && body.contains("inlinePiP = inlinePlayerUi.pip();")
                        && body.contains("inlineClock = inlinePlayerUi.clock();")
                        && body.contains("inlineOsd = inlinePlayerUi.osd();"));
        assertTrue("fusion inline player should delegate reusable TV control bindings to VodPlayerUiController",
                body.contains("inlinePlayerUi.bindInlineActions();")
                        && !body.contains("binding.playerPrev.setOnClickListener")
                        && !body.contains("binding.playerControls.setOnTouchListener(this::onInlineControlTouch);"));
        assertTrue("shared chrome must expose the reusable TV control views",
                chrome.contains("binding.playerPrev")
                        && chrome.contains("binding.playerQuality")
                        && chrome.contains("binding.playerDanmaku")
                        && chrome.contains("binding.playerFullscreenAction")
                        && chrome.contains("binding.playerControls"));
        assertTrue("shared player UI controller must bind reusable TV control actions through the host contract",
                controller.contains("public void bindInlineActions()")
                        && controller.contains("chrome.prev.setOnClickListener(view -> host.playPrevious());")
                        && controller.contains("chrome.quality.setOnClickListener(view -> host.showQuality());")
                        && controller.contains("chrome.speed.setOnLongClickListener(view -> host.resetSpeed());")
                        && controller.contains("chrome.textTrack.setOnClickListener(host::showTrack);")
                        && controller.contains("chrome.danmaku.setOnLongClickListener(view -> host.onDanmakuLongClick());")
                        && controller.contains("chrome.fullscreen.setOnClickListener(view -> host.toggleFullscreen());")
                        && controller.contains("chrome.controls.setOnTouchListener(host::onControlsTouch);"));
        assertTrue("shared player UI controller must own the reusable playback UI helpers",
                controller.contains("new VodPlayerControlController")
                        && controller.contains("new PlayerOsdController")
                        && controller.contains("Clock.create()")
                        && controller.contains("new PiP()"));
        assertTrue("shared player UI lifecycle must own OSD start/stop/release",
                controller.contains("osd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics())")
                        && controller.contains("osd.start();")
                        && controller.contains("osd.stop();")
                        && controller.contains("osd.release();"));
    }

    @Test
    public void tvInlinePauseFeedbackStaysVisibleAcrossDetailPlayerStatePaths() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int show = source.indexOf("private void showInlinePauseInfo()");
        int sync = source.indexOf("private void syncInlinePauseInfo(boolean playing)");
        int condition = source.indexOf("private boolean shouldShowInlinePauseInfo(boolean playing)", sync);
        int hide = source.indexOf("private void hideInlinePauseInfo()", condition);
        int seekEnd = source.indexOf("public void onSeekEnd(long time)", hide);
        int touchEnd = source.indexOf("public void onTouchEnd()");
        int touchEndEnd = source.indexOf("private void hideInlineGestureOverlays()", touchEnd);
        int toggle = source.indexOf("private void toggleInlinePlayback()");
        int toggleEnd = source.indexOf("private void toggleInlineControls()", toggle);
        int enter = source.indexOf("private void enterInlineFullscreen()");
        int enterEnd = source.indexOf("private boolean shouldShowDetailFullscreenControlsOnReady()", enter);
        int playing = source.indexOf("protected void onPlayingChanged(boolean isPlaying)");
        int playingEnd = source.indexOf("protected void onSizeChanged(VideoSize size)", playing);
        int ready = source.indexOf("protected void onStateChanged(int state)");
        int readyEnd = source.indexOf("protected void onTracksChanged()", ready);
        int playWhenReady = source.indexOf("public void onPlayWhenReadyChanged(boolean playWhenReady, int reason)");
        int playWhenReadyEnd = source.indexOf("protected void onSizeChanged(VideoSize size)", playWhenReady);

        assertTrue("detail player must expose a reusable pause-feedback synchronizer", sync >= 0 && show >= 0 && condition > sync && hide > condition);
        String syncBody = source.substring(sync, condition);
        String conditionBody = source.substring(condition, hide);
        String showBody = source.substring(show, sync);
        String hideBody = source.substring(hide, seekEnd);
        String touchEndBody = source.substring(touchEnd, touchEndEnd);
        String toggleBody = source.substring(toggle, toggleEnd);
        String enterBody = source.substring(enter, enterEnd);
        String playingBody = source.substring(playing, playingEnd);
        String readyBody = source.substring(ready, readyEnd);
        String playWhenReadyBody = source.substring(playWhenReady, playWhenReadyEnd);

        assertTrue("pause feedback synchronizer must choose exactly one visible state",
                syncBody.contains("if (shouldShowInlinePauseInfo(playing)) showInlinePauseInfo();")
                        && syncBody.contains("else hideInlinePauseInfo();"));
        assertTrue("pause feedback must only appear for active leanback fullscreen playback",
                conditionBody.contains("Util.isLeanback()")
                        && conditionBody.contains("inlineFullscreen")
                        && conditionBody.contains("inlineStarted")
                        && conditionBody.contains("!playing")
                        && !conditionBody.contains("!player().isPlaying()")
                        && conditionBody.contains("isPaused()"));
        assertTrue("pause feedback must render above every inline control layer",
                showBody.contains("binding.gestureSeek.setVisibility(View.VISIBLE);")
                        && showBody.contains("binding.gestureSeek.bringToFront();"));
        assertTrue("clearing pause feedback must hide the center prompt",
                hideBody.contains("inlinePauseInfo = false;")
                        && hideBody.contains("binding.gestureSeek.setVisibility(View.GONE);"));
        assertTrue("a touch-up after double-tap pause must not immediately remove the pause prompt",
                touchEndBody.contains("if (inlinePauseInfo)")
                        && touchEndBody.contains("binding.gestureSeek.bringToFront();")
                        && touchEndBody.contains("return;"));
        assertTrue("TV fullscreen play/pause action must dismiss controls like the native leanback player",
                toggleBody.contains("if (Util.isLeanback() && inlineFullscreen) hideInlineControls();"));
        assertTrue("entering fullscreen while already paused must restore the pause prompt",
                enterBody.contains("syncInlinePauseInfo(playing);"));
        assertTrue("playback and READY callbacks must both synchronize pause feedback",
                playingBody.contains("syncInlinePauseInfo(isPlaying);")
                        && readyBody.contains("syncInlinePauseInfo(player().isPlaying());"));
        assertTrue("playWhenReady changes must cover engines that do not emit a second isPlaying callback",
                playWhenReadyBody.contains("super.onPlayWhenReadyChanged(playWhenReady, reason);")
                        && playWhenReadyBody.contains("syncInlinePauseInfo(playWhenReady);"));
    }

    @Test
    public void duplicateNamedFlagsUseUrlThenStableKeyAndObjectIdentity() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        String standalone = source.substring(source.indexOf("private Flag initialStandaloneFlag"),
                source.indexOf("private Episode initialStandaloneEpisode"));
        String initial = source.substring(source.indexOf("private Flag findInitialFlag"),
                source.indexOf("private Episode findIntentPlaybackEpisode"));
        String episode = source.substring(source.indexOf("private Episode findIntentPlaybackEpisode"),
                source.indexOf("private MaterialButton createChipButton"));

        assertTrue("standalone preload must resolve duplicate flags with the stable key and exact episode URL",
                standalone.contains("getIntentPlaybackEpisodeUrl()")
                        && standalone.contains("getIntentPlaybackFlagKey()")
                        && standalone.contains("TmdbUIAdapter.selectPlaybackFlag("));
        assertTrue("detail binding must resolve duplicate flags with the stable key and exact episode URL",
                initial.contains("getIntentPlaybackEpisodeUrl()")
                        && initial.contains("getIntentPlaybackFlagKey()")
                        && initial.contains("TmdbUIAdapter.selectPlaybackFlag("));
        assertTrue("standalone preload must reuse the stable key restored from seasonal history",
                standalone.contains("saved.getSourceBindingKey()")
                        && standalone.contains("saved.getEpisodeUrl()")
                        && standalone.contains("saved.getVodFlag()"));
        assertTrue("detail binding must reuse the stable key restored from seasonal history",
                initial.contains("history.getSourceBindingKey()")
                        && initial.contains("history.getEpisodeUrl()")
                        && initial.contains("history.getVodFlag()"));
        assertTrue("episode lookup must allow its exact URL to override a stale duplicate flag key",
                episode.indexOf("findEpisodeByUrl(getIntentPlaybackEpisodeUrl()") < episode.indexOf("getIntentPlaybackFlagKey()"));
        assertFalse("duplicate flag selection and focus must not rely on Flag.equals, which compares names only",
                source.contains("equals(selectedFlag)"));
        assertTrue(source.contains("flags.get(i) == selectedFlag")
                && source.contains("flag == selectedFlag"));
        String seasonSources = source.substring(source.indexOf("private List<SourceMatch> findSeasonHistorySources"),
                source.indexOf("private int currentSeasonSourceScope"));
        assertTrue("persisted duplicate flag keys must win over legacy same-name route binding lookup",
                seasonSources.indexOf("TmdbUIAdapter.isFlagKey(saved.getSourceBindingKey())")
                        < seasonSources.indexOf("TextUtils.equals(saved.getVodFlag(), binding.getSourceFlag())"));
    }

    @Test
    public void seasonSourceRouteChipsAreDistinguishableFromRealFlags() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        String bindRoutes = javaBlockAt(source, "private void bindSeasonSourceRoutes(");
        String label = javaBlockAt(source, "private String seasonSourceRouteLabel(");

        assertTrue("route chips must be labelled as source switches, not as playback lines",
                label.contains("R.string.detail_source_route_chip"));
        assertTrue("a same-site route must be named by its entry so it cannot read as the header source label",
                label.contains("R.string.detail_source_route_chip_entry"));
        assertTrue("route chip labels must honour the user's custom site name like the header does",
                label.contains("getDisplayName()"));
        assertFalse("route chip labels must not fall back to the raw site name",
                label.contains("site().getName()"));
        assertTrue("an entry with no usable name must still get a non-empty label",
                label.contains("TextUtils.isEmpty(entryName)"));
        assertFalse("a same-site route stays reachable in one click, so it must not be filtered out",
                bindRoutes.contains("currentKey)) continue"));
        assertTrue("duplicate entry names must not produce two identical chips",
                bindRoutes.contains("distinctChipLabel("));
        int capture = bindRoutes.indexOf("String currentKey = getKeyText();");
        int submit = bindRoutes.indexOf("detailTasks.submit(");
        assertTrue("the intent site key must be bound to this submission, not read back later",
                capture >= 0 && submit > capture);
    }

    @Test
    public void initialStandaloneFlagLoadsSeasonBindingBeforeRoutesAndEpisodes() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        String bindFlags = source.substring(source.indexOf("private void bindFlags()"),
                source.indexOf("private void bindSeasonSourceRoutes"));
        String applyBundle = source.substring(source.indexOf("private void applyTmdbBundle"),
                source.indexOf("private void showTmdbMatchDialog"));

        int select = bindFlags.indexOf("selectedFlag = currentFlag;");
        int loadBinding = bindFlags.indexOf("loadTmdbSeasonBinding();", select);
        int bindRoutes = bindFlags.indexOf("bindSeasonSourceRoutes(routeGeneration);", loadBinding);
        int renderEpisodes = bindFlags.indexOf("renderEpisodes();", loadBinding);

        assertTrue("initial flag must load its stable-key season binding before routes and episodes",
                select >= 0 && loadBinding > select && bindRoutes > loadBinding && renderEpisodes > loadBinding);
        assertTrue("bundle application must not read a route binding before the initial flag exists",
                applyBundle.contains("if (selectedFlag != null) loadTmdbSeasonBinding();"));
    }

    @Test
    public void knownSeasonAutoFallbackUsesOnlyValidatedSeasonRoutes() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        String manual = source.substring(source.indexOf("private void changeSource()"),
                source.indexOf("private SourceMatch searchAutoChangeSource"));
        String automatic = source.substring(source.indexOf("private void tryAutoChangeSource()"),
                source.indexOf("private boolean openGlobalSourceSearch"));
        String guardedSearch = source.substring(source.indexOf("private SourceMatch searchAutoChangeSource"),
                source.indexOf("private void tryAutoChangeSource()"));
        String seasonScope = source.substring(source.indexOf("private int currentSeasonSourceScope"),
                source.indexOf("private SourceMatch searchChangeSource(Site"));

        assertTrue("manual source changes may retain ordinary search", manual.contains("searchChangeSource(keyword)"));
        assertTrue("automatic source changes must use the season-aware guard", automatic.contains("searchAutoChangeSource(keyword)"));
        assertTrue("known TV seasons must only reuse validated season history routes",
                guardedSearch.contains("currentSeasonSourceScope() >= 0")
                        && guardedSearch.contains("return findSeasonHistorySource();"));
        assertTrue("explicit current-line season evidence must beat stale history season state",
                seasonScope.indexOf("sourceTitleSeasonNumber()") < seasonScope.indexOf("history != null"));
    }

    @Test
    public void tmdbSeasonCacheWritesHoldOneLockForTheWholeMutation() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        String load = source.substring(source.indexOf("private void loadTmdbSeasonBinding"),
                source.indexOf("private void updateTmdbSeasonActionVisibility"));
        String save = source.substring(source.indexOf("private void saveTmdbSeasonBinding"),
                source.indexOf("private void clearTmdbSeasonBinding"));
        String clear = source.substring(source.indexOf("private void clearTmdbSeasonBinding"),
                source.indexOf("private String selectedSeasonFlagKey"));

        assertTrue(save.contains("synchronized (Setting.class)")
                && save.indexOf("Setting.getTmdbSeasonMatchCache()") < save.indexOf("Setting.putTmdbSeasonMatchCache(cache)"));
        assertTrue(clear.contains("synchronized (Setting.class)")
                && clear.indexOf("Setting.getTmdbSeasonMatchCache()") < clear.indexOf("Setting.putTmdbSeasonMatchCache(cache)"));
        assertTrue("pruning stale sibling routes must persist even when the active binding did not change",
                load.contains("boolean cacheChanged = cache.pruneRouteBindings")
                        && load.contains("cacheChanged |= cache.recordRouteBinding")
                        && load.contains("if (cacheChanged) Setting.putTmdbSeasonMatchCache(cache)"));
    }

    @Test
    public void automaticSeasonChoiceRestoresUnboundEpisodeResolution() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        String seasonChoice = source.substring(source.indexOf("public void onSeason(int seasonNumber)"),
                source.indexOf("private void analyzeTmdbSeasonWithAi"));
        String clear = source.substring(source.indexOf("private void clearTmdbSeasonBinding"),
                source.indexOf("private String selectedSeasonFlagKey"));

        assertTrue("automatic choice must leave manual season state before episode re-render",
                clear.contains("selectedSeasonNumber = -1;")
                        && clear.indexOf("selectedSeasonNumber = -1;") < clear.indexOf("refreshEpisodesAfterSeasonBinding()"));
        assertTrue("manual season choice must keep its explicit season selected",
                seasonChoice.contains("selectedSeasonNumber = seasonNumber;")
                        && seasonChoice.indexOf("selectedSeasonNumber = seasonNumber;") < seasonChoice.indexOf("refreshEpisodesAfterSeasonBinding()"));
    }

    @Test
    public void automaticEpisodeMetadataUsesResolvedFallbackSeason() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        String dataSeason = source.substring(source.indexOf("private int tmdbEpisodeDataSeason"),
                source.indexOf("private void fetchSeasonIfNeeded(int seasonNumber)"));
        String episodeDetail = source.substring(source.indexOf("private void showTmdbEpisodeDetail"),
                source.indexOf("private EpisodePosition historyEpisodePosition"));

        assertTrue("empty auto grouping must reuse the resolver's unique season for episode data",
                dataSeason.contains("tmdbSeasonChoiceResolution().getSelectedSeason()"));
        assertTrue("episode detail must use the same resolved fallback season as episode data",
                episodeDetail.contains("int detailSeasonNumber = tmdbEpisodeDataSeason(")
                        && episodeDetail.contains("int displaySeasonNumber = detailSeasonNumber;")
                        && episodeDetail.contains("int seasonNumber = detailSeasonNumber;"));
    }

    @Test
    public void manualSeasonBindingClearsOnlySelectedFlagMetadata() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        String clear = source.substring(source.indexOf("private void clearBoundTmdbEpisodeMetadata"),
                source.indexOf("private void showManualTmdbMatchDialog"));

        assertTrue(clear.contains("selectedFlag.getEpisodes()"));
        assertFalse("manual remapping must preserve metadata already loaded for sibling flags",
                clear.contains("vod.getFlags()"));
    }

    @Test
    public void explicitFlagSelectionPersistsIndependentlyOfPlaybackProgress() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        String initial = javaBlockAt(source, "private Flag findInitialFlag(");
        String preferred = javaBlockAt(source, "private Flag findPreferredFlag(");
        String save = javaBlockAt(source, "private void savePreferredFlag(");
        String bindFlags = javaBlockAt(source, "private void bindFlags(");
        String switchInline = javaBlockAt(source, "private void switchNativeEnhancedInlineFlag(");
        String updateHistory = javaBlockAt(source, "private void updateInlineHistory(");

        // 线路偏好必须独立落盘：History.vodFlag 只在起播且 position>0 时才写，
        // 详情直放模式下「切了线路没起播」或「从播放器返回后再切」都写不进去，
        // 进程被杀后重进就会退回 flags.get(0)。
        assertTrue("explicit flag taps must persist the selection without waiting for playback",
                bindFlags.contains("savePreferredFlag(flag)"));
        assertTrue("native enhanced inline flag switches must persist the selection too",
                switchInline.contains("savePreferredFlag(flag)"));
        assertTrue("starting playback must refresh the preference so cross-line resume cannot leave it stale",
                updateHistory.contains("savePreferredFlag(selectedFlag)"));

        assertTrue("the persisted preference must be consulted when resolving the initial flag",
                initial.contains("findPreferredFlag(flags)"));
        assertTrue("season preload must resolve the flag the same way or it prefetches the wrong season",
                javaBlockAt(source, "private Flag initialStandaloneFlag(").contains("findPreferredFlag(flags)"));
        assertTrue("an explicit intent target must still outrank the stored preference",
                initial.indexOf("TmdbUIAdapter.selectPlaybackFlag(") < initial.indexOf("findPreferredFlag("));
        assertTrue("the stored preference must outrank the history fallback and the flags.get(0) default",
                initial.indexOf("findPreferredFlag(") < initial.indexOf("history.getSourceBindingKey()")
                        && initial.indexOf("findPreferredFlag(") < initial.indexOf("flags.get(0)"));

        assertTrue("preference lookup must key off the stable flag key to separate same-named lines",
                preferred.contains("TmdbUIAdapter.flagKey(flags.get(i), i)"));
        assertTrue("preference lookup must degrade to the flag name when source ordering shifts",
                preferred.indexOf("TmdbUIAdapter.flagKey(flags.get(i), i)")
                        < preferred.indexOf("flag.getFlag()"));
        assertTrue("writes must record both the stable key and the flag name",
                save.contains("TmdbUIAdapter.flagKey(flag, index)") && save.contains("flag.getFlag()"));
        assertTrue("an unknown flag index must not be written as a stable key, Flag.stableKey clamps it to #0",
                save.contains("index < 0 ? \"\" : TmdbUIAdapter.flagKey(flag, index)"));
        assertTrue("the preference file must be flushed off the main thread",
                save.contains("Task.execute(() -> FlagPreferenceCache.get().save())"));
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findMainResPath() {
        Path moduleRelative = Path.of("src", "main", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "res");
    }

    private static String readLayout(String file) throws Exception {
        Path layoutPath = findMainResPath().resolve(Path.of("layout", file));
        return new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
    }

    private static String readLeanbackLayout(String file) throws Exception {
        Path layoutPath = findLeanbackResPath().resolve(Path.of("layout", file));
        return new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
    }

    private static String readJava(String first, String... more) throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of(first, more));
        return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
    }

    private static String readFlavorJava(String flavor, String first, String... more) throws Exception {
        Path moduleRelative = Path.of("src", flavor, "java");
        Path base = Files.exists(moduleRelative) ? moduleRelative : Path.of("app", "src", flavor, "java");
        return new String(Files.readAllBytes(base.resolve(Path.of(first, more))), StandardCharsets.UTF_8);
    }

    private static Path findLeanbackResPath() {
        Path moduleRelative = Path.of("src", "leanback", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "res");
    }


    private static Path findMobileResPath() {
        Path moduleRelative = Path.of("src", "mobile", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "res");
    }

    private static Path findAppModulePath() {
        if (Files.exists(Path.of("proguard-rules.pro"))) return Path.of(".");
        return Path.of("app");
    }

    private static void assertAndroidIdOrder(String label, String layout, List<String> ids) {
        int previous = -1;
        for (String id : ids) {
            int index = layout.indexOf("android:id=\"@+id/" + id + "\"");
            assertTrue(label + " is missing @+id/" + id, index >= 0);
            assertTrue(label + " should keep @+id/" + id + " after the previous mapped control", index > previous);
            previous = index;
        }
    }

    private static void assertIconOnlyEpisodeTool(String layout, String id, String icon, String contentDescription) {
        int index = layout.indexOf("android:id=\"@+id/" + id + "\"");
        assertTrue(id + " is missing from the detail layout", index >= 0);
        assertTrue(id + " must use the " + icon + " icon", containsViewAttribute(layout, index, "app:icon=\"@drawable/" + icon + "\""));
        assertTrue(id + " must render as icon-only", containsViewAttribute(layout, index, "app:iconGravity=\"textStart\""));
        assertTrue(id + " must remove icon padding", containsViewAttribute(layout, index, "app:iconPadding=\"0dp\""));
        assertTrue(id + " must expose an action description", containsViewAttribute(layout, index, "android:contentDescription=\"@string/" + contentDescription + "\""));
        assertTrue(id + " must not keep a visible text label", !containsViewAttribute(layout, index, "android:text="));
    }


    private static void assertAndroidIdHasAttribute(String label, String layout, String id, String attribute) {
        int index = layout.indexOf("android:id=\"@+id/" + id + "\"");
        assertTrue(label + " is missing @+id/" + id, index >= 0);
        assertTrue(label + " must include " + attribute, containsViewAttribute(layout, index, attribute));
    }

    private static void assertNativeControlButton(String layout, String id, String marginEnd) {
        int index = layout.indexOf("android:id=\"@+id/" + id + "\"");
        assertTrue("fusion inline control @+id/" + id + " must use the native material text control tag",
                layout.lastIndexOf("<com.google.android.material.textview.MaterialTextView", index) > 0);
        assertTrue("fusion inline control @+id/" + id + " must use @style/Control",
                containsViewAttribute(layout, index, "style=\"@style/Control\""));
        assertTrue("fusion inline control @+id/" + id + " must match native margin " + marginEnd,
                containsViewAttribute(layout, index, "android:layout_marginEnd=\"" + marginEnd + "\""));
    }

    private static boolean containsViewAttribute(String layout, int idIndex, String attribute) {
        if (idIndex < 0) return false;
        int tagEnd = layout.indexOf("/>", idIndex);
        if (tagEnd < 0) tagEnd = layout.indexOf(">", idIndex);
        return tagEnd > idIndex && layout.substring(idIndex, tagEnd).contains(attribute);
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static String javaBlockAt(String source, String marker) {
        int markerIndex = source.indexOf(marker);
        int openBrace = markerIndex < 0 ? -1 : source.indexOf('{', markerIndex + marker.length());
        if (openBrace < 0) return "";
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return source.substring(markerIndex, i + 1);
        }
        return "";
    }

    /**
     * 检查源码中是否包含方法调用,忽略接收者前缀(this. / TmdbDetailLayoutUtils. / 等)
     *
     * 让文本断言只关心"做了什么"(结果),不关心"怎么调用的"(实现细节)。
     * 例如: containsMethodCallIgnoringReceiver(body, "setWidthMatch(binding.detailActions)")
     * 会匹配:
     *   - setWidthMatch(binding.detailActions)
     *   - this.setWidthMatch(binding.detailActions)
     *   - TmdbDetailLayoutUtils.setWidthMatch(binding.detailActions)
     *
     * 方案 1 核心:让重构搬方法时,只要行为不变,测试就不误伤。
     */
    private static boolean containsMethodCallIgnoringReceiver(String source, String methodCallWithArgs) {
        // 从输入里剥掉接收者前缀,只保留 "方法名(参数...)"。
        // 例如输入 "TmdbDetailLayoutUtils.setWidthMatch(binding.detailActions)"
        // 或 "this.setWidthMatch(binding.detailActions)" 都归一化成
        // "setWidthMatch(binding.detailActions)"。
        int openParen = methodCallWithArgs.indexOf('(');
        if (openParen < 0) return false;

        String receiverAndName = methodCallWithArgs.substring(0, openParen);
        String argsAndRest = methodCallWithArgs.substring(openParen);
        int lastDot = receiverAndName.lastIndexOf('.');
        String methodName = lastDot < 0 ? receiverAndName : receiverAndName.substring(lastDot + 1);

        // 源码侧的接收者(this. / ClassName.)只是方法名前面的前缀,
        // 子串匹配 "methodName(args)" 天然忽略它 —— 无需正则、无需截断。
        return source.contains(methodName + argsAndRest);
    }
}
