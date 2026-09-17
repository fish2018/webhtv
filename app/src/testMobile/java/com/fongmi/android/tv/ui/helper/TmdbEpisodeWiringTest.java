package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class TmdbEpisodeWiringTest {

    @Test
    public void standaloneDetailModesShareEpisodeInfoAcrossDetailAndPlayback() throws Exception {
        String activity = read(mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")));

        assertTrue(activity.contains("TmdbEpisodeInfo episodeInfo = tmdbEpisodeInfo();"));
        assertTrue(activity.contains("addMetaChip(episodeInfo.detailText(this));"));
        assertTrue(activity.contains("if (!episodeInfo.isSeasonScoped())"));
        assertTrue(activity.contains("addMetaChip(currentSeasonContextLabel());"));
        assertTrue(activity.contains("historyEpisodeTitle(selectedEpisode)"));
        assertTrue(activity.contains("item.setRemarks(coalesce(tmdbEpisodeInfo().detailText(this), getMarkText(), vod.getRemarks()));"));
    }

    @Test
    public void standaloneDetailEpisodeInfoTracksCurrentResolvedSeason() throws Exception {
        String activity = read(mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")));

        int start = activity.indexOf("private TmdbEpisodeInfo tmdbEpisodeInfo()");
        int end = activity.indexOf("private String releaseDate()", start);
        String method = activity.substring(start, end);

        assertTrue(method.contains("int sourceSeason = currentSeasonContextNumber();"));
    }

    @Test
    public void embeddedTmdbHeaderShowsEpisodeInfoInNativeAndFusionLayouts() throws Exception {
        String header = read(mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java")));
        String layout = read(mainRes().resolve(Path.of("layout", "view_tmdb_header.xml")));

        assertTrue(header.contains("String episodeInfo = adapter.getEpisodeDetailText();"));
        assertTrue(header.contains("buildFusionSubtitle(detail, adapter.getRatingText(), episodeInfo)"));
        assertTrue(header.contains("!adapter.getEpisodeInfo().isSeasonScoped()"));
        assertTrue(layout.contains("android:id=\"@+id/tmdbMeta\""));
        assertTrue(layout.contains("android:maxLines=\"2\""));
    }

    @Test
    public void bothVideoActivitiesAppendCompactEpisodeInfoToExistingOsdTitle() throws Exception {
        String leanback = read(flavorJava("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String mobile = read(flavorJava("mobile").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));

        assertTrue(leanback.contains("String episodeInfo = tmdbEpisodeCompactText();"));
        assertTrue(leanback.contains("setText(mBinding.remark, 0, episodeInfo);"));
        assertTrue(mobile.contains("String episodeInfo = tmdbEpisodeCompactText();"));
    }

    @Test
    public void detailDirectPlaybackConsumesPreloadedTmdbEpisodeRemarkInBothVideoActivities() throws Exception {
        String leanback = read(flavorJava("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String mobile = read(flavorJava("mobile").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));

        assertTrue(leanback.contains("private String getTmdbVodRemark()"));
        assertTrue(leanback.contains("applyIntentTmdbVodRemark(item);"));
        assertTrue(leanback.contains("item.setRemarks(remark);"));
        assertTrue(leanback.contains("getIntent().removeExtra(\"tmdb_vod_remark\");"));
        assertTrue(mobile.contains("private String getTmdbVodRemark()"));
        assertTrue(mobile.contains("applyIntentTmdbVodRemark(item);"));
        assertTrue(mobile.contains("item.setRemarks(remark);"));
        assertTrue(mobile.contains("getIntent().removeExtra(\"tmdb_vod_remark\");"));
    }

    @Test
    public void leanbackNativeEnhancedKeepsEpisodeInfoVisibleAfterPlayerRefresh() throws Exception {
        String leanback = read(flavorJava("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));

        assertTrue(leanback.contains("mBinding.remark.setVisibility(shouldShowVideoDetailRemark(visible) ? View.VISIBLE : View.GONE);"));
        assertTrue(leanback.contains("private boolean shouldShowVideoDetailRemark(boolean visible)"));
        assertTrue(leanback.contains("String episodeInfo = mTmdbUIAdapter.getEpisodeDetailText();"));
        assertTrue(leanback.contains("TextUtils.equals(mBinding.remark.getText(), episodeInfo)"));
    }

    @Test
    public void manualSeasonChangesInvalidateStaleMetadataAndRefreshCardsImmediately() throws Exception {
        String adapter = read(mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java")));
        String detail = read(mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")));
        String leanback = read(flavorJava("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String mobile = read(flavorJava("mobile").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));

        assertTrue(adapter.contains("private volatile int episodeMetadataGeneration;"));
        assertTrue(adapter.contains("discard removed season source=%s tmdb=%d season=%s"));
        assertTrue(adapter.contains("manual.getMode() == TmdbSeasonMatchCache.Mode.MANUAL_SEASON"));
        assertTrue(adapter.contains("int metadataGeneration = ++episodeMetadataGeneration;"));
        assertTrue(adapter.contains("loadEpisodeTitlesAsync(vod, tmdbItem, generation, metadataGeneration, selectedSeason);"));
        assertTrue(adapter.contains("isCurrentEpisodeMetadataRequest(generation, metadataGeneration, selectedSeason)"));
        assertTrue(adapter.contains("applyEpisodeTitles(vod, item, selectedSeason, generation, metadataGeneration)"));
        assertTrue(detail.contains("private void refreshEpisodesAfterSeasonBinding()"));
        assertTrue(detail.contains("clearBoundTmdbEpisodeMetadata();"));
        assertTrue(detail.contains("private void clearBoundTmdbEpisodeMetadata()"));
        assertTrue(detail.contains("episode.setTmdbEpisode(null);"));
        assertTrue(detail.contains("rerenderEpisodeViewportOnly(false, true, true);"));
        assertTrue(mobile.contains("if (mTmdbUIAdapter.clearManualSeasonBinding()) refreshTmdbEpisodeTitles();"));
        assertTrue(mobile.contains("if (mTmdbUIAdapter.keepOriginalEpisodeList()) refreshTmdbEpisodeTitles();"));
        assertTrue(mobile.contains("if (mTmdbUIAdapter.applyManualSeason(seasonNumber)) refreshTmdbEpisodeTitles();"));
        assertTrue(leanback.contains("if (mTmdbUIAdapter.clearManualSeasonBinding()) refreshTmdbEpisodeTitles();"));
        assertTrue(leanback.contains("if (mTmdbUIAdapter.keepOriginalEpisodeList()) refreshTmdbEpisodeTitles();"));
        assertTrue(leanback.contains("if (mTmdbUIAdapter.applyManualSeason(seasonNumber)) refreshTmdbEpisodeTitles();"));
    }

    @Test
    public void leanbackSeasonSelectorParticipatesInRemoteFocusNavigation() throws Exception {
        String leanback = read(flavorJava("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));

        int focusOrders = leanback.indexOf("private List<Integer> getEpisodeFocusOrders()");
        int focusUpdate = leanback.indexOf("private void updateFocus()", focusOrders);
        String orders = leanback.substring(focusOrders, focusUpdate);
        assertTrue(orders.contains("R.id.episodeTitle"));

        int headerFocus = leanback.indexOf("private void updateEpisodeHeaderFocus()");
        int headerKeys = leanback.indexOf("private boolean onEpisodeHeaderToolKey", headerFocus);
        String header = leanback.substring(headerFocus, headerKeys);
        assertTrue(header.contains("mBinding.episodeTitle.setNextFocusUpId"));
        assertTrue(header.contains("mBinding.episodeTitle.setNextFocusDownId"));
        assertTrue(header.contains("mBinding.episodeReverse.setNextFocusLeftId"));
        assertTrue(leanback.contains("private boolean isEpisodeFocusTarget(View view)"));
        assertTrue(leanback.contains("view.isFocusable()"));
        assertTrue(leanback.contains("mBinding.episodeTitle.requestFocus(View.FOCUS_LEFT)"));
        assertTrue(leanback.replace("\r\n", "\n").contains("mEpisodeGridAdapter.notifyDataSetChanged();\n        updateFocus();"));

        int coreRefresh = leanback.indexOf("else if (event.getType() == RefreshEvent.Type.VOD_CORE)");
        int recommendationRefresh = leanback.indexOf("else if (event.getType() == RefreshEvent.Type.VOD_RECOMMENDATIONS)", coreRefresh);
        String coreRefreshBlock = leanback.substring(coreRefresh, recommendationRefresh);
        assertTrue(coreRefreshBlock.contains("updateEpisodeSeasonContext();"));
        assertTrue(coreRefreshBlock.contains("updateFocus();"));
    }

    @Test
    public void pushSourcesUseConservativeTmdbSeasonResolution() throws Exception {
        String adapter = read(mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java")));
        String leanback = read(flavorJava("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String mobile = read(flavorJava("mobile").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));

        assertTrue(adapter.contains("!SiteApi.PUSH.equals(cacheSiteKey(sourceVod))"));
        assertTrue(adapter.contains("TmdbSeasonResolver.resolve("));
        assertTrue(adapter.contains("TmdbMatchPolicy.shouldAutoMatchPushTitle(videoName)"));
        assertTrue(adapter.contains("EpisodeSeasonPolicy.resolveExplicitSourceSeason("));
        assertTrue(leanback.contains("SiteApi.PUSH.equals(getKey())"));
        assertTrue(mobile.contains("SiteApi.PUSH.equals(getKey())"));
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static Path mainJava() {
        Path moduleRelative = Path.of("src", "main", "java");
        return Files.exists(moduleRelative) ? moduleRelative : Path.of("app", "src", "main", "java");
    }

    private static Path mainRes() {
        Path moduleRelative = Path.of("src", "main", "res");
        return Files.exists(moduleRelative) ? moduleRelative : Path.of("app", "src", "main", "res");
    }

    private static Path flavorJava(String flavor) {
        Path moduleRelative = Path.of("src", flavor, "java");
        return Files.exists(moduleRelative) ? moduleRelative : Path.of("app", "src", flavor, "java");
    }
}
