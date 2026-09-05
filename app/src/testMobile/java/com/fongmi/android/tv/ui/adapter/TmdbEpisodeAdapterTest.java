package com.fongmi.android.tv.ui.adapter;

import com.fongmi.android.tv.ui.helper.TmdbEpisodeGridPolicy;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TmdbEpisodeAdapterTest {

    @Test
    public void nativeEnhancedNonCardGridUsesStandardEpisodeHeight() {
        assertEquals(TmdbEpisodeGridPolicy.GRID_CARD_HEIGHT_DP,
                TmdbEpisodeAdapter.nativeEnhancedGridCardHeightDp(false, false));
        assertEquals(TmdbEpisodeGridPolicy.GRID_CARD_HEIGHT_DP,
                TmdbEpisodeAdapter.nativeEnhancedGridCardHeightDp(true, false));
        assertEquals(TmdbEpisodeGridPolicy.NATIVE_MOBILE_GRID_CARD_HEIGHT_DP,
                TmdbEpisodeAdapter.nativeEnhancedGridCardHeightDp(true, true));
        assertEquals(TmdbEpisodeGridPolicy.NATIVE_GRID_CARD_HEIGHT_DP,
                TmdbEpisodeAdapter.nativeEnhancedGridCardHeightDp(false, true));
    }

    @Test
    public void nativeEnhancedGridUsesOneHeightForEveryItemOnThePage() throws Exception {
        String source = tmdbEpisodeAdapterSource();

        assertTrue("native-enhanced grid sizing must use one page-wide TMDB data decision",
                source.contains("private boolean pageHasTmdbEpisodeData;")
                        && source.contains("pageHasTmdbEpisodeData = hasPageTmdbEpisodeData();")
                        && source.contains("applyCardSize(holder, compact, pageHasTmdbEpisodeData);")
                        && source.contains("private boolean hasPageTmdbEpisodeData()")
                        && !source.contains("applyCardSize(holder, compact, tmdbEpisode != null);"));
    }

    @Test
    public void nativeEnhancedPhoneGridUsesReadableCleanTitle() {
        assertEquals("2. 觉醒", TmdbEpisodeAdapter.nativeEnhancedIndexTitle("完美世界_02 2. 觉醒", "2. 觉醒", true, TmdbEpisodeAdapter.Mode.GRID));
        assertEquals(14f, TmdbEpisodeAdapter.nativeEnhancedIndexTextSize(true, TmdbEpisodeAdapter.Mode.GRID), 0f);
    }

    @Test
    public void nativeEnhancedFileSizeUsesBadgeWithoutDuplicatingTitle() {
        assertEquals("2. 觉醒", TmdbEpisodeAdapter.nativeEnhancedIndexTitle("[5.37G] 2. 觉醒", "2. 觉醒", "[5.37G]", false, TmdbEpisodeAdapter.Mode.LIST));
        assertEquals("[5.37G]", TmdbEpisodeAdapter.nativeEnhancedFileSizeBadge("[5.37G]", "2. 觉醒"));
        assertEquals("", TmdbEpisodeAdapter.nativeEnhancedFileSizeBadge("[5.37G]", "[5.37G] 2. 觉醒"));
    }

    @Test
    public void nativeEnhancedLargePagesKeepSharedFallbackArtwork() throws Exception {
        String source = tmdbEpisodeAdapterSource();
        int method = source.indexOf("private boolean shouldSuppressSharedFallbackVisuals()");

        assertTrue("native-enhanced TMDB episode cards must keep fallback artwork visible",
                method >= 0
                        && source.indexOf("return false;", method) > method
                        && source.contains("allowFallback && !suppressSharedFallback ? fallbackStillUrl : \"\""));
    }

    @Test
    public void nativeEnhancedEpisodeCardsBindFileSizeBadge() throws Exception {
        String source = tmdbEpisodeAdapterSource();
        int nativeBranch = source.indexOf("if (isNativeEnhanced())");
        int nextBranch = source.indexOf("} else if (mode == Mode.GRID)", nativeBranch);
        String nativeBody = nativeBranch >= 0 && nextBranch > nativeBranch ? source.substring(nativeBranch, nextBranch) : "";

        assertTrue("native-enhanced TMDB episode cards should bind the file-size badge instead of always hiding it",
                nativeBody.contains("boolean showDate = !TextUtils.isEmpty(holder.binding.date.getText()) && mode == Mode.GRID;")
                        && nativeBody.contains("bindFileSize(holder, nativeEnhancedFileSizeBadge(fileSize, cleanTitle), showDate);")
                        && source.contains("extractFileSize(episode.getRawDisplayName())")
                        && source.contains("withSourceFileSize(episode.getRawDisplayName(), title")
                        && !nativeBody.contains("holder.binding.fileSize.setVisibility(View.GONE);"));
    }

    @Test
    public void episodeCardStyleChangesRebindWithoutRepeatedLayoutRequests() throws Exception {
        String source = tmdbEpisodeAdapterSource();
        int setMode = source.indexOf("public void setMode(Mode mode)");
        int setGridSpan = source.indexOf("public void setGridSpanCount(int gridSpanCount)");
        int applyCardSize = source.indexOf("private void applyCardSize(ViewHolder holder, boolean compact, boolean hasTmdbEpisodeData)");

        assertTrue("TMDB episode adapter must rebind visible cards when grid/list style changes",
                setMode >= 0
                        && source.indexOf("if (this.mode == value) return;", setMode) > setMode
                        && source.indexOf("if (!items.isEmpty()) notifyDataSetChanged();", setMode) > setMode
                        && setGridSpan > setMode
                        && source.indexOf("if (!items.isEmpty() && mode == Mode.GRID) notifyDataSetChanged();", setGridSpan) > setGridSpan);
        assertTrue("TMDB episode card binding must avoid setLayoutParams when size and margins are unchanged",
                applyCardSize >= 0
                        && source.indexOf("boolean layoutChanged", applyCardSize) > applyCardSize
                        && source.indexOf("if (layoutChanged) root.setLayoutParams(params);", applyCardSize) > applyCardSize
                        && source.indexOf("if (scrimParams.height != scrimHeight)", applyCardSize) > applyCardSize);
    }

    @Test
    public void gridEpisodeCardsUseSymmetricHorizontalMargins() throws Exception {
        String source = tmdbEpisodeAdapterSource();
        int method = source.indexOf("private void applyCardSize(ViewHolder holder, boolean compact, boolean hasTmdbEpisodeData)");
        int methodEnd = source.indexOf("private boolean nativeEnhancedMobileGrid", method);
        String body = method >= 0 && methodEnd > method ? source.substring(method, methodEnd) : "";

        assertTrue("grid episode cards must split their spacing between start and end so outer margins stay balanced",
                body.contains("int gridSpacing = dp(holder.itemView, standardGridItem ? 8 : isNativeEnhanced() ? 12 : 8);")
                        && body.contains("int marginStart = mode == Mode.GRID ? gridSpacing / 2 : 0;")
                        && body.contains("marginParams.setMarginStart(marginStart);")
                        && body.contains("marginParams.getMarginStart() != marginStart"));
    }

    @Test
    public void unchangedEpisodeViewportDoesNotNotifyWholePageAgain() throws Exception {
        String source = tmdbEpisodeAdapterSource();
        int method = source.indexOf("public void setItems(List<Episode> episodes, Map<Integer, TmdbEpisode> tmdbEpisodes, Map<Episode, Integer> numbers, Episode selected)");
        int clear = source.indexOf("items.clear();", method);
        int notify = source.indexOf("notifyDataSetChanged();", clear);

        assertTrue("TMDB episode adapter must skip full rebinds when the page data is unchanged",
                source.indexOf("if (!forceRefresh && !displaySettingsChanged && sameItems(episodes, tmdbEpisodes, numbers))", method) > method
                        && source.indexOf("setSelected(selected);", method) > method
                        && source.indexOf("private boolean sameItems(", notify) > notify);
    }

    @Test
    public void episodeDisplaySettingChangesRebindUnchangedViewport() throws Exception {
        String source = tmdbEpisodeAdapterSource();
        int method = source.indexOf("public boolean setItems(List<Episode> episodes, Map<Integer, TmdbEpisode> tmdbEpisodes, Map<Episode, Integer> numbers, Episode selected, boolean forceRefresh)");
        int update = source.indexOf("boolean displaySettingsChanged = updateDisplaySettings();", method);
        int skip = source.indexOf("sameItems(episodes, tmdbEpisodes, numbers)", method);
        int displayMethod = source.indexOf("private boolean updateDisplaySettings()");
        int displayMethodEnd = source.indexOf("private boolean sameEpisodes", displayMethod);
        String displayBody = displayMethod >= 0 && displayMethodEnd > displayMethod ? source.substring(displayMethod, displayMethodEnd) : "";

        assertTrue("TMDB episode adapter must not skip rebinds when filename/file-size display settings changed",
                method >= 0
                        && update > method
                        && update < skip
                        && source.indexOf("if (!forceRefresh && !displaySettingsChanged && sameItems(episodes, tmdbEpisodes, numbers))", method) > method
                        && displayBody.contains("Setting.getTmdbEpisodeShowScrapedName()")
                        && displayBody.contains("Setting.isTmdbEpisodeFileSize()")
                        && displayBody.contains("showScrapedName = currentShowScrapedName;")
                        && displayBody.contains("showFileSize = currentShowFileSize;"));
    }

    @Test
    public void episodeDisplaySettingRefreshRebindsVisibleItems() throws Exception {
        String source = tmdbEpisodeAdapterSource();
        int method = source.indexOf("public void refreshDisplaySettings(RecyclerView recyclerView)");
        int methodEnd = source.indexOf("public int getPosition", method);
        String body = method >= 0 && methodEnd > method ? source.substring(method, methodEnd) : "";

        assertTrue("TMDB episode adapter must expose an immediate display refresh for visible holders after dialogs",
                body.contains("updateDisplaySettings();")
                        && body.contains("recyclerView.getChildViewHolder(recyclerView.getChildAt(index))")
                        && body.contains("onBindViewHolder((ViewHolder) holder, position);"));
    }

    @Test
    public void episodeFileNameToggleAfterEpisodeDialogPostsVisibleItemRefresh() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void toggleEpisodeFileName()");
        int methodEnd = source.indexOf("private void updateEpisodeViewModeButton", method);
        String body = method >= 0 && methodEnd > method ? source.substring(method, methodEnd) : "";
        int persistSetting = body.indexOf("Setting.putTmdbEpisodeShowScrapedName(showScraped);");
        int updateButton = body.indexOf("updateEpisodeFileNameButton();");
        int updatePlayLabel = body.indexOf("updatePlayLabel();");
        int postedRefresh = body.indexOf("binding.episodeContainer.post(() -> episodeAdapter.refreshDisplaySettings(binding.episodeContainer));");

        assertTrue("TMDB detail filename toggle must update labels, then post one visible-card refresh after a dialog closes",
                persistSetting >= 0
                        && persistSetting < updateButton
                        && updateButton < updatePlayLabel
                        && updatePlayLabel < postedRefresh
                        && !body.contains("rerenderEpisodeViewportOnly(false, false, true);"));
    }

    @Test
    public void themeRefreshUpdatesLightAndAccentWithSingleRebind() throws Exception {
        String adapter = tmdbEpisodeAdapterSource();
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int method = adapter.indexOf("public void setTheme(boolean light, int activeStrokeColor)");
        int methodEnd = adapter.indexOf("public void setFallbackStillUrl", method);
        String body = methodEnd > method ? adapter.substring(method, methodEnd) : adapter.substring(method);

        assertTrue("theme changes should update light and active stroke together",
                method >= 0
                        && body.contains("boolean changed = this.light != light || this.activeStrokeColor != activeStrokeColor;")
                        && body.contains("this.light = light;")
                        && body.contains("this.activeStrokeColor = activeStrokeColor;")
                        && body.contains("if (changed) notifyDataSetChanged();"));
        assertTrue("detail theme refresh should avoid separate full rebinds for light and active stroke",
                activity.contains("episodeAdapter.setTheme(lightTheme, colors.accent);")
                        && !activity.contains("episodeAdapter.setLight(lightTheme);\n            episodeAdapter.setActiveStrokeColor(colors.accent);"));
    }

    @Test
    public void nativeEnhancedEpisodeCardsDoNotShowSelectedGrayOverlay() throws Exception {
        String adapter = tmdbEpisodeAdapterSource();
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "adapter_tmdb_episode.xml"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
        int nativeFocus = adapter.indexOf("private void applyNativeEnhancedCardFocus");
        int nativeFocusEnd = adapter.indexOf("private boolean isNativeEnhanced()", nativeFocus);
        String nativeFocusBody = nativeFocus >= 0 && nativeFocusEnd > nativeFocus ? adapter.substring(nativeFocus, nativeFocusEnd) : "";

        assertTrue("episode cards should not stack platform ripple/focus overlays over the still image",
                layout.contains("android:defaultFocusHighlightEnabled=\"false\"")
                        && layout.contains("android:stateListAnimator=\"@null\"")
                        && layout.contains("app:rippleColor=\"@android:color/transparent\"")
                        && !layout.contains("?attr/selectableItemBackground"));
        assertTrue("native-enhanced selected episodes should use stroke only, not Material selected/checked state overlays",
                nativeFocusBody.contains("setSelected(false);")
                        && nativeFocusBody.contains("setActivated(false);")
                        && nativeFocusBody.contains("setChecked(false);")
                        && nativeFocusBody.contains("activated ? activeStrokeColor : 0x00000000")
                        && !nativeFocusBody.contains("setSelected(activated)"));
    }

    private static String tmdbEpisodeAdapterSource() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbEpisodeAdapter.java"));
        return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
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
}
