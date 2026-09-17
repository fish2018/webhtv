package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import com.fongmi.android.tv.utils.SearchGridLayout;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchResultLayoutTest {

    @Test
    public void collectActivityUsesSearchLayoutSettingForCardRatio() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "CollectActivity.java"));
        String source = read(sourcePath);
        Path layoutPath = findLeanbackResPath().resolve(Path.of("layout", "activity_collect.xml"));
        String layout = read(layoutPath);

        assertTrue("TV search results must read the portrait/landscape layout setting",
                source.contains("Setting.getSearchUi()"));
        assertTrue("TV search results need a horizontal site row for landscape layout",
                layout.contains("android:id=\"@+id/collectHorizontal\""));
        assertTrue("Horizontal site row must not consume the result area height",
                layout.contains("android:layout_height=\"56dp\""));
        assertTrue("Landscape layout must show the horizontal site row",
                source.contains("mBinding.collectHorizontal.setVisibility(horizontal ? android.view.View.VISIBLE : android.view.View.GONE);"));
        assertTrue("Landscape layout must hide the vertical side site list",
                source.contains("mBinding.collect.setVisibility(horizontal ? android.view.View.GONE : android.view.View.VISIBLE);"));
        assertTrue("Landscape result grid should align with the title and horizontal site row",
                source.contains("mBinding.recycler.setPadding(ResUtil.dp2px(horizontal ? 24 : 0), 0, ResUtil.dp2px(24), ResUtil.dp2px(24));"));
        assertTrue("Landscape result grid should honor the configured global image size",
                source.contains("return Product.getColumn();"));
        assertTrue("Landscape layout should keep the normal poster card ratio while using the wider result area",
                source.contains("SEARCH_CARD_RATIO"));
        assertTrue("Changing the layout setting should rebuild the result grid",
                source.contains("updateRecyclerLayout();"));
    }

    @Test
    public void tvSearchDefaultsFocusToAllSourceAfterStartingSearch() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "CollectActivity.java"));
        String source = read(sourcePath);
        int newIntentStart = source.indexOf("protected void onNewIntent(Intent intent)");
        int initViewStart = source.indexOf("protected void initView(Bundle savedInstanceState)");
        int initEventStart = source.indexOf("protected void initEvent()");
        int focusStart = source.indexOf("private void focusInitialSource()");
        int similarityStart = source.indexOf("private void onSimilarityFilter()", focusStart);
        assertTrue("TV search must define an initial source-focus policy", focusStart >= 0 && similarityStart > focusStart);
        String newIntent = source.substring(newIntentStart, initViewStart);
        String initView = source.substring(initViewStart, initEventStart);
        String initialFocus = source.substring(focusStart, similarityStart);

        assertTrue("A reused search result activity must focus sources after starting the new search",
                newIntent.indexOf("focusInitialSource();") > newIntent.indexOf("search();"));
        assertTrue("A newly opened search result activity must focus sources after starting the search",
                initView.indexOf("focusInitialSource();") > initView.indexOf("search();"));
        assertTrue("TV search must target the visible source selector for the active layout",
                initialFocus.contains("BaseGridView collect = isSearchLandscape() ? mBinding.collectHorizontal : mBinding.collect;"));
        assertTrue("The first All source must receive focus even before its view holder is laid out",
                initialFocus.contains("collect.setSelectedPosition(0, holder -> holder.itemView.requestFocus());"));
        assertTrue("Searches without sources must retain the former layout-button fallback",
                initialFocus.contains("if (mCollectAdapter.getItemCount() == 0) {")
                        && initialFocus.contains("mBinding.searchColumn.requestFocus();"));
        assertFalse("Similarity must not remain the default TV search focus",
                initView.contains("mBinding.similarityFilter.requestFocus();"));
    }

    @Test
    public void gridModeUsesGlobalImageSizeForColumnCount() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "CollectActivity.java"));
        String source = read(sourcePath);

        assertTrue("TV search grid must use the global image-size setting",
                source.contains("return Product.getColumn();"));
        assertFalse("TV search grid must not ignore image size by targeting a fixed 120dp card width",
                source.contains("int itemWidth = ResUtil.dp2px(120);"));
    }

    @Test
    public void mobileSearchLayoutSettingControlsSourcePlacement() throws Exception {
        Path fragmentPath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "fragment", "CollectFragment.java"));
        String fragment = read(fragmentPath);
        Path adapterPath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "CollectAdapter.java"));
        String adapter = read(adapterPath);
        Path layoutPath = findMobileResPath().resolve(Path.of("layout", "fragment_collect.xml"));
        String layout = read(layoutPath);

        assertTrue("Mobile search results must read the portrait/landscape layout setting",
                fragment.contains("Setting.getSearchUi() == 0"));
        assertTrue("Mobile search layout needs a container whose orientation can change",
                layout.contains("android:id=\"@+id/content\""));
        assertTrue("Mobile search layout needs a weighted result container",
                layout.contains("android:id=\"@+id/resultContainer\""));
        assertTrue("Landscape search must place sources above results",
                fragment.contains("mBinding.content.setOrientation(horizontal ? LinearLayoutCompat.VERTICAL : LinearLayoutCompat.HORIZONTAL);"));
        assertTrue("Landscape search must choose a horizontal source selector orientation",
                fragment.contains("int orientation = horizontal ? LinearLayoutManager.HORIZONTAL : LinearLayoutManager.VERTICAL;"));
        assertTrue("Search layout refresh must propagate its direction to the source adapter",
                fragment.contains("mCollectAdapter.setHorizontal(horizontal);"));
        assertTrue("Horizontal source chips must wrap their content instead of filling the row",
                adapter.contains("horizontal ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT"));
        assertTrue("Search layout changes must update existing source ViewHolders",
                adapter.contains("public void setHorizontal(boolean horizontal)"));
        assertTrue("Source ViewHolder width must be rebound after a layout-direction change",
                adapter.contains("setItemWidth(holder);"));
    }

    @Test
    public void mobileSearchLayoutKeepsSourceScrollAndStableRowHeight() throws Exception {
        Path fragmentPath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "fragment", "CollectFragment.java"));
        String fragment = read(fragmentPath);

        assertTrue("Horizontal source row must use a stable fixed height",
                fragment.contains("SOURCE_ROW_HEIGHT_DP"));
        assertTrue("Horizontal source row height must not depend on RecyclerView wrap-content measurement",
                fragment.contains("collectParams.height = horizontal ? ResUtil.dp2px(SOURCE_ROW_HEIGHT_DP) : ViewGroup.LayoutParams.MATCH_PARENT;"));
        assertTrue("Search result layout refresh must reuse a source LayoutManager with the same orientation",
                fragment.contains("manager.getOrientation() == orientation"));
        assertTrue("Source LayoutManager replacement must be isolated behind an orientation guard",
                fragment.contains("setCollectLayoutManager(horizontal);"));
        assertFalse("Grid/list toggles must not unconditionally replace the source LayoutManager",
                fragment.contains("mBinding.collect.setLayoutManager(new LinearLayoutManager(requireContext(), horizontal ? LinearLayoutManager.HORIZONTAL : LinearLayoutManager.VERTICAL, false));"));
    }

    @Test
    public void mobileSearchGridUsesImageSizeOnPhoneAndWideWindows() throws Exception {
        Path fragmentPath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "fragment", "CollectFragment.java"));
        String fragment = read(fragmentPath);

        assertTrue("Mobile search grid must derive its target card width from the global image-size setting",
                fragment.contains("Product.getColumn(requireActivity())"));
        assertFalse("Phone search must not bypass the image-size setting with a fixed two-column return",
                fragment.contains("if (!MobileWindow.isWide(requireActivity())) return 2;"));
        assertTrue("Landscape search width fallback must use the full window width",
                fragment.contains("ResUtil.getScreenWidth(requireActivity()) - (isSearchLandscape() ? 0 : collectWidth)"));
    }

    @Test
    public void mobileSearchGridKeepsSmallCardsInsideTheirGridCells() throws Exception {
        Path fragmentPath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "fragment", "CollectFragment.java"));
        String fragment = read(fragmentPath);

        assertTrue("Mobile search must cap the span count using the minimum readable card width",
                fragment.contains("GRID_MIN_ITEM_WIDTH_DP"));
        assertTrue("The configured image size and safe card width must both limit the span count",
                fragment.contains("SearchGridLayout.resolveSpanCount(column, targetWidth, available, minWidth, margin)"));
        assertTrue("Grid item width calculation must stay positive in extremely narrow windows",
                fragment.contains("SearchGridLayout.resolveItemWidth(getResultWidth(), getResultPadding(), span, margin)"));
        assertFalse("Grid item width must not be expanded beyond its GridLayoutManager cell",
                fragment.contains("width = Math.max(ResUtil.dp2px(96), width);"));
    }

    @Test
    public void listModeUsesCompactSearchRowsInsteadOfPosterHeight() throws Exception {
        Path collectPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "CollectActivity.java"));
        String collect = read(collectPath);
        Path adapterPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "SearchAdapter.java"));
        String adapter = read(adapterPath);
        Path listLayoutPath = findLeanbackResPath().resolve(Path.of("layout", "adapter_search_list.xml"));
        String listLayout = read(listLayoutPath);

        assertTrue("TV list mode should have a fixed compact row height",
                collect.contains("SEARCH_LIST_ROW_HEIGHT_DP"));
        assertTrue("TV list mode must not calculate height from full-width poster ratio",
                collect.contains("return isListMode(count) ? ResUtil.dp2px(SEARCH_LIST_ROW_HEIGHT_DP) : (int) (getItemWidth(count) / SEARCH_CARD_RATIO);"));
        assertTrue("Search adapter must receive whether it is rendering compact list rows",
                collect.contains("new SearchAdapter(this, getItemWidth(count), getItemHeight(count), isListMode(count))"));
        assertTrue("TV search adapter should inflate a dedicated list row layout",
                adapter.contains("AdapterSearchListBinding"));
        assertTrue("Compact list row should keep a modest fixed XML preview height",
                listLayout.contains("android:layout_height=\"116dp\""));
        assertTrue("Compact list row should use a side poster instead of a full-width image",
                listLayout.contains("android:layout_toEndOf=\"@+id/image\""));
    }

    @Test
    public void tvDeferredSourceActivationUsesStableSiteKey() throws Exception {
        Path activityPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "CollectActivity.java"));
        String activity = read(activityPath);

        assertTrue("Delayed source activation must track the selected site instead of a movable adapter position",
                activity.contains("private String mPendingCollectSiteKey = \"\";"));
        assertTrue("The delayed activation API must receive a stable site key",
                activity.contains("private void applyCollectDeferred(String siteKey, long delayMillis)"));
        assertTrue("Source selection must schedule delayed activation with the selected site key",
                activity.contains("applyCollectDeferred(siteKey, delayMillis);"));
        assertTrue("Delayed source activation must resolve the current selection by its stable key",
                activity.contains("Collect activated = mCollectAdapter.findActivated(siteKey);"));
        assertTrue("Delayed work for a source that is no longer active must be discarded",
                activity.contains("if (activated == null) return;"));
        assertFalse("Inserting an earlier source must not cancel delayed activation through a stale numeric position",
                activity.contains("mCollectAdapter.getPosition() != position"));
    }

    @Test
    public void personalSettingsExposeSearchLayoutSwitch() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "SettingPersonalActivity.java"));
        String source = read(sourcePath);
        Path layoutPath = findLeanbackResPath().resolve(Path.of("layout", "activity_setting_personal.xml"));
        String layout = read(layoutPath);
        int searchUi = layout.indexOf("android:id=\"@+id/searchUi\"");
        int searchColumn = layout.indexOf("android:id=\"@+id/searchColumn\"");
        String searchUiSection = layout.substring(searchUi, searchColumn);

        assertTrue("TV personal settings must bind the search layout switch",
                source.contains("mBinding.searchUi.setOnClickListener(this::setSearchUi);"));
        assertTrue("TV personal settings must show the current search layout label",
                source.contains("mBinding.searchUiText.setText((searchUi = getResources().getStringArray(R.array.select_search_ui))[Setting.getSearchUi()]);"));
        assertFalse("Search layout setting should be visible in TV personal settings",
                searchUiSection.contains("android:visibility=\"gone\""));
    }

    @Test
    public void personalSettingsExposePlaybackSpeedControlsOnTvAndMobile() throws Exception {
        Path tvSourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "SettingPersonalActivity.java"));
        String tvSource = read(tvSourcePath);
        Path tvLayoutPath = findLeanbackResPath().resolve(Path.of("layout", "activity_setting_personal.xml"));
        String tvLayout = read(tvLayoutPath);
        Path mobileSourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "fragment", "SettingPersonalFragment.java"));
        String mobileSource = read(mobileSourcePath);
        Path mobileLayoutPath = findMobileResPath().resolve(Path.of("layout", "fragment_setting_personal.xml"));
        String mobileLayout = read(mobileLayoutPath);

        assertTrue("TV personal settings must include playback speed entry",
                tvLayout.contains("android:id=\"@+id/playSpeed\""));
        assertTrue("TV personal settings must open playback speed dialog",
                tvSource.contains("mBinding.playSpeed.setOnClickListener(this::setPlaySpeed);"));
        assertTrue("TV playback speed setting must persist the default speed",
                tvSource.contains("PlayerSetting.putDefaultSpeed(value);"));
        assertFalse("TV personal settings must not duplicate the existing long-press speed setting",
                tvLayout.contains("@string/player_speed"));

        assertTrue("Mobile personal settings must include playback speed entry",
                mobileLayout.contains("android:id=\"@+id/playSpeed\""));
        assertTrue("Mobile personal settings must open playback speed dialog",
                mobileSource.contains("mBinding.playSpeed.setOnClickListener(this::setPlaySpeed);"));
        assertTrue("Mobile playback speed setting must persist the default speed",
                mobileSource.contains("PlayerSetting.putDefaultSpeed(value);"));
        assertFalse("Mobile personal settings must not duplicate the existing long-press speed setting",
                mobileLayout.contains("@string/player_speed"));
    }

    @Test
    public void traditionalChineseResourcesIncludeSearchLayoutOptions() throws Exception {
        Path stringsPath = findMainResPath().resolve(Path.of("values-zh-rTW", "strings.xml"));
        String strings = read(stringsPath);

        assertTrue("Traditional Chinese resources must include the search layout options",
                strings.contains("<string-array name=\"select_search_ui\">"));
        assertTrue("Traditional Chinese resources must include the playback speed label",
                strings.contains("<string name=\"setting_play_speed\">"));
    }

    @Test
    public void mobileSearchGridKeepsSmallCardsInTwoColumnsWhenThePaneIsNarrow() {
        int availableWidth = 648;
        int minItemWidth = 324;
        int itemMargin = 13;

        assertEquals(2, SearchGridLayout.resolveSpanCount(5, 138, availableWidth, minItemWidth, itemMargin));
        assertEquals(1, SearchGridLayout.resolveSpanCount(5, 138, 0, minItemWidth, itemMargin));
        assertEquals(1, SearchGridLayout.resolveSpanCount(0, 0, -1, 0, -1));
        assertEquals(1, SearchGridLayout.resolveSpanCount(2, 459, availableWidth, minItemWidth, itemMargin));
    }

    @Test
    public void mobileSearchGridKeepsConfiguredColumnsWhenSafeWidthAllowsThem() {
        int availableWidth = 1053;
        int minItemWidth = 324;
        int itemMargin = 13;

        assertEquals(3, SearchGridLayout.resolveSpanCount(5, 138, availableWidth, minItemWidth, itemMargin));
        assertEquals(2, SearchGridLayout.resolveSpanCount(2, 459, availableWidth, minItemWidth, itemMargin));
    }

    @Test
    public void mobileSearchGridAlwaysUsesAPositiveItemWidth() {
        assertEquals(298, SearchGridLayout.resolveItemWidth(675, 27, 2, 13));
        assertEquals(1, SearchGridLayout.resolveItemWidth(20, 40, 2, 13));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findLeanbackJavaPath() {
        Path moduleRelative = Path.of("src", "leanback", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "java");
    }

    private static Path findLeanbackResPath() {
        Path moduleRelative = Path.of("src", "leanback", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "res");
    }

    private static Path findMainResPath() {
        Path moduleRelative = Path.of("src", "main", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "res");
    }

    private static Path findMobileJavaPath() {
        Path moduleRelative = Path.of("src", "mobile", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "java");
    }

    private static Path findMobileResPath() {
        Path moduleRelative = Path.of("src", "mobile", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "res");
    }
}
