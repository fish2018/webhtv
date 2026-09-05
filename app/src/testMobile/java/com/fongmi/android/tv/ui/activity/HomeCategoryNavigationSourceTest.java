package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeCategoryNavigationSourceTest {

    @Test
    public void homeLayoutHostsCategoryContentBelowThePersistentTypeRow() throws Exception {
        String layout = read(source("leanback", "res", "layout", "activity_home.xml"));

        assertTrue("home layout must keep the type row", layout.contains("android:id=\"@+id/typeRecycler\""));
        assertTrue("home layout must expose an inline category fragment host", layout.contains("android:id=\"@+id/categoryContainer\""));
        assertTrue("the inline category host must start hidden", layout.contains("android:id=\"@+id/categoryContainer\"\n                android:layout_width=\"match_parent\"\n                android:layout_height=\"match_parent\"\n                android:visibility=\"gone\""));
    }

    @Test
    public void homeTypeIsPrependedOnlyWhenDefaultVodLoadingIsEnabled() throws Exception {
        String source = homeActivity();
        String setTypes = method(source, "private void setTypes(Result result)", "private void updateTypeRecyclerVisibility()");

        assertTrue("home type must use the existing synthetic home id", source.contains("home.setTypeId(\"home\");"));
        assertTrue("home type must use the localized home label", source.contains("home.setTypeName(getString(R.string.home));"));
        assertTrue("default VOD loading must gate the synthetic home item", setTypes.contains("if (Setting.isHomeVodAutoLoad()) items.add(createHomeType());"));
        assertTrue("source categories must follow the home item", setTypes.indexOf("items.add(createHomeType())") < setTypes.indexOf("items.addAll(result.getTypes())"));
    }

    @Test
    public void movingTypeFocusSwitchesInlineContentWithoutPressingOk() throws Exception {
        String source = homeActivity();
        String initEvent = method(source, "protected void initEvent()", "private void updateToolbarVisibility(boolean visible)");
        String schedule = method(source, "private void scheduleTypeSwitch(int position)", "private void switchType()");
        String switchType = method(source, "private void switchType()", "private void showHomeContent()");
        String click = method(source, "public void onItemClick(Class item)", "public void onRefresh(Class item)");

        assertTrue("type selection must schedule a content switch from focus", initEvent.contains("scheduleTypeSwitch(position);"));
        assertTrue("rapid D-pad movement must debounce category loads", schedule.contains("postDelayed(mTypeSwitch, TYPE_SWITCH_DELAY_MS)"));
        assertTrue("home focus must restore the existing home content", switchType.contains("if (item.isHome()) showHomeContent();"));
        assertTrue("category focus must show the category inline", switchType.contains("else showCategoryContent(item);"));
        assertFalse("OK on a category must not launch a second activity", click.contains("VodActivity.start"));
        assertTrue("OK on an active category must retain filter behavior", click.contains("updateFilter(item);"));
    }

    @Test
    public void inlineCategoryEdgesSwitchCategoriesWithRowSpecificFocus() throws Exception {
        String home = homeActivity();
        String folder = read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "fragment", "FolderFragment.java"));
        String type = read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "fragment", "TypeFragment.java"));
        String row = read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "custom", "CustomRowPresenter.java"));
        String forwardEdge = method(folder, "public void onContentHorizontalEdge(int contentRow, boolean towardEnd)", "public boolean requestContentFocus()");
        String edge = method(home, "public void onCategoryContentHorizontalEdge(Class item, int contentRow, boolean towardEnd)", "private Class getAdjacentCategory(Class item, boolean towardEnd)");
        String adjacent = method(home, "private Class getAdjacentCategory(Class item, boolean towardEnd)", "private void showHomeContent()");

        assertTrue("category card rows must report horizontal edge navigation", type.contains("new CustomRowPresenter(16, this::onContentHorizontalEdge)"));
        assertTrue("filter rows must retain their edge focus without switching categories", type.contains("HorizontalGridView.FOCUS_SCROLL_ALIGNED, true)"));
        assertTrue("row grids must consume the boundary key and report its logical direction", row.contains("edgeListener.onHorizontalEdge(towardEnd)") && row.contains("isHorizontalEdge(grid, event)"));
        assertTrue("content rows must be relative to the filter-row offset", type.contains("getContentRow()") && type.contains("mBinding.recycler.getSelectedPosition() - (filterVisible ? mFilters.size() : 0)"));
        assertTrue("folder fragments must forward content-edge navigation through a host contract", folder.contains("interface CategoryEdgeHost") && forwardEdge.contains("host.onCategoryContentHorizontalEdge(mType, contentRow, towardEnd);"));
        assertTrue("nested folder pages must not switch the top-level category", forwardEdge.contains("if (getChildFragmentManager().getBackStackEntryCount() > 0) return;"));
        assertTrue("right and left edges must select the adjacent category in opposite directions", adjacent.contains("int target = position + (towardEnd ? 1 : -1);"));
        assertTrue("the synthetic home item must not be treated as an adjacent category", adjacent.contains("candidate.isHome()"));
        assertTrue("first-row edges must focus the adjacent category's first card", edge.contains("if (contentRow == 0)") && edge.contains("focusFirstCard(item);"));
        assertTrue("lower-row edges must focus the adjacent category button", edge.contains("else focusCategoryButton(item);"));
        assertTrue("cached category pages must be visible before receiving the first-card request", adjacent.contains("getSupportFragmentManager().executePendingTransactions();") && adjacent.contains("mFolder.requestContentFocus(0);"));
    }

    @Test
    public void firstCategoryContentRowMovesUpToSelectedCategoryHeader() throws Exception {
        String grid = read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "custom", "CustomVerticalGridView.java"));
        String dispatch = method(grid, "public boolean dispatchKeyEvent(@NonNull KeyEvent event)", "private boolean focusHeader()");
        String focusHeader = method(grid, "private boolean focusHeader()", "public boolean moveToTop()");

        assertTrue("up from the first content row must use the configured header instead of geometric focus search", dispatch.contains("if (KeyUtil.isUpKey(event) && focusHeader()) return true;"));
        assertTrue("only the first content row may route up to the header", focusHeader.contains("getSelectedPosition() != 0"));
        assertTrue("the selected category row must be restored before focus moves", focusHeader.contains("showHeader();"));
        assertTrue("the first configured focusable header must receive focus", focusHeader.contains("if (view.requestFocus()) return true;"));
    }

    @Test
    public void quickOkAppliesFilterOnlyAfterTheFocusedCategoryBecomesCurrent() throws Exception {
        String source = homeActivity();
        String folder = read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "fragment", "FolderFragment.java"));
        String click = method(source, "public void onItemClick(Class item)", "public void onRefresh(Class item)");
        String showCategory = method(source, "private void showCategoryContent(Class item, boolean toggleFilter)", "private void restoreTypeFocus(boolean keepTypeFocus, Class item)");
        String updateFilter = method(source, "private void updateFilter(Class item)", "public void closeFilter()");
        String toggleFilter = method(folder, "public void toggleFilter(boolean visible)", "public boolean requestContentFocus()");

        assertTrue("OK must synchronously target the focused category", click.contains("showCategoryContent(item, true);"));
        assertTrue("OK may toggle immediately only when that category is committed", click.contains("else if (isCurrentCategory(item)) updateFilter(item);"));
        assertFalse("OK must not toggle whichever folder happened to be current", click.contains("else updateFilter(item);"));
        assertTrue("OK must finish an already queued category transaction instead of adding it twice", showCategory.contains("getSupportFragmentManager().executePendingTransactions();"));
        assertTrue("filter changes must wait for the target fragment transaction", showCategory.contains("if (toggleFilter && target == mFolder && isCurrentCategory(item)) updateFilter(item);"));
        assertTrue("the filter helper must reject a stale category", updateFilter.contains("!isCurrentCategory(item)"));
        assertTrue("a new folder must retain filter intent until its child exists", toggleFilter.contains("pendingFilterVisible = visible;"));
        assertTrue("the child transaction must consume retained filter intent", folder.contains("transaction.runOnCommit(this::applyPendingFilter);"));
    }

    @Test
    public void pendingTypeSwitchIsCancelledWhileHomeIsPaused() throws Exception {
        String source = homeActivity();
        String resumeSwitch = method(source, "private void resumeTypeSwitch()", "private void switchType()");
        String switchType = method(source, "private void switchType()", "private void showHomeContent()");
        String resume = method(source, "protected void onResume()", "protected void onPause()");
        String pause = method(source, "protected void onPause()", "protected void onBackInvoked()");

        assertTrue("a resumed home must reschedule the currently selected type", resume.contains("resumeTypeSwitch();"));
        assertTrue("resume must use the actual selected position", resumeSwitch.contains("mBinding.typeRecycler.getSelectedPosition()"));
        assertTrue("pause must cancel the delayed fragment switch", pause.contains("mBinding.typeRecycler.removeCallbacks(mTypeSwitch);"));
        assertTrue("a saved FragmentManager must reject a delayed transaction", switchType.contains("getSupportFragmentManager().isStateSaved()"));
    }

    @Test
    public void nativeHomePagingCollapsesTheTypeRowWithTheToolbar() throws Exception {
        String source = homeActivity();
        String initEvent = method(source, "protected void initEvent()", "private void updateToolbarVisibility(boolean visible)");
        String typeVisibility = method(source, "private void updateTypeRecyclerVisibility()", "private void syncTypeItems()");

        assertTrue("native home paging must derive one shared header state from the selected row", initEvent.contains("boolean headerVisible = isTopRow(position);"));
        assertTrue("native home paging must collapse the type row below the top rows", initEvent.contains("updateTypeRecyclerVisibility(headerVisible);"));
        assertTrue("native home paging must keep the toolbar aligned with the type row", initEvent.contains("updateToolbarVisibility(headerVisible);"));
        assertTrue("scroll visibility must still honor whether home categories are enabled", typeVisibility.contains("enabled && headerVisible"));
    }

    @Test
    public void inlineCategoryPagesDoNotStealFocusFromTheTypeRow() throws Exception {
        String home = homeActivity();
        String type = read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "fragment", "TypeFragment.java"));
        String showCategory = method(home, "private void showCategoryContent(Class item)", "private void restoreTypeFocus(boolean keepTypeFocus, Class item)");
        String restoreFocus = method(home, "private void restoreTypeFocus(boolean keepTypeFocus, Class item)", "private void syncCategorySite()");
        String clearCategory = method(home, "private void clearCategoryContent()", "private void updateToolbarVisibility(boolean visible)");
        String hiddenChanged = method(type, "public void onHiddenChanged(boolean hidden)", "public boolean requestContentFocus()");

        assertTrue("the switch must remember whether the type row owned focus", showCategory.contains("boolean keepTypeFocus = mBinding.typeRecycler.hasFocus();"));
        assertTrue("removing category pages must invalidate pending content focus", clearCategory.contains("fragment instanceof FolderFragment folder") && clearCategory.contains("folder.clearContentFocusRequest();"));
        assertTrue("focus restoration must wait until the fragment transaction completes", showCategory.contains("transaction.runOnCommit(() -> {"));
        assertTrue("the completed transaction must restore type focus", showCategory.contains("restoreTypeFocus(keepTypeFocus, item);"));
        assertFalse("focus restoration must not leave a frame where cached content can cover the home chrome", restoreFocus.contains("mBinding.typeRecycler.post("));
        assertTrue("a stale category switch must not reclaim focus", restoreFocus.contains("mBinding.typeRecycler.getSelectedPosition() != position"));
        assertTrue("the selected type item must regain focus after its page appears", restoreFocus.contains("mBinding.typeRecycler.requestFocus();"));
        assertTrue("category switches must restore the type row before reclaiming focus", restoreFocus.contains("mBinding.typeRecycler.setVisibility(View.VISIBLE);"));
        assertTrue("category switches must restore the toolbar with the focused type row", restoreFocus.contains("updateToolbarVisibility(true);"));
        assertFalse("nested folder navigation must retain its existing content-focus behavior", hiddenChanged.contains("shouldFocusContentOnShow"));
    }

    @Test
    public void inlineCategoryPagingCollapsesTheVisibleHomeHeader() throws Exception {
        String home = homeActivity();
        String folder = read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "fragment", "FolderFragment.java"));
        String type = read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "fragment", "TypeFragment.java"));
        String grid = read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "custom", "CustomVerticalGridView.java"));
        String headerChanged = method(home, "public void onScrollHeaderVisibilityChanged(boolean visible)", "private void openCategory(Class item)");
        String updateToolbar = method(home, "private void updateToolbarVisibility(boolean visible)", "private void syncNativeContentInset()");
        String folderHeaderChanged = method(folder, "public void onScrollHeaderVisibilityChanged(boolean visible)", "public boolean requestContentFocus()");

        assertTrue("home must provide the visible inline chrome to the category grid", home.contains("FolderFragment.ScrollHeaderHost"));
        assertTrue("home paging must collapse both the category row and toolbar", home.contains("return new int[]{R.id.typeRecycler, R.id.toolbar};"));
        assertTrue("folder hosts must expose optional scroll-header ids", folder.contains("public interface ScrollHeaderHost"));
        assertTrue("folder hosts must expose header visibility changes", folder.contains("void onScrollHeaderVisibilityChanged(boolean visible);"));
        assertTrue("folder pages must resolve scroll-header ids through their host", folder.contains("public int[] getScrollHeaderIds()"));
        assertTrue("folder pages must forward header visibility to capable hosts", folderHeaderChanged.contains("host.onScrollHeaderVisibilityChanged(visible);"));
        assertTrue(
                "non-home folder hosts must retain the legacy recycler header fallback",
                folder.contains("new int[]{R.id.recycler}"));
        assertTrue(
                "the category grid must use its actual host header instead of a hard-coded activity id",
                type.contains("mBinding.recycler.setHeader(getActivity(), getParent().getScrollHeaderIds());"));
        assertFalse(
                "inline category paging must not keep targeting HomeActivity's hidden home recycler",
                type.contains("mBinding.recycler.setHeader(getActivity(), R.id.recycler);"));
        assertTrue("the category grid must register its folder as the header visibility listener", type.contains("mBinding.recycler.setHeaderVisibilityListener(getParent()::onScrollHeaderVisibilityChanged);"));
        assertTrue("header state must be based on the configured primary header", grid.contains("views.get(0).getVisibility() == View.VISIBLE"));
        assertTrue("header visibility changes must notify the configured host", grid.contains("headerVisibilityListener.onHeaderVisibilityChanged(visible);"));
        assertTrue("the home host must route header changes through the toolbar state helper", headerChanged.contains("updateToolbarVisibility(visible);"));
        assertTrue("the toolbar helper must synchronize the native content inset", updateToolbar.contains("syncNativeContentInset();"));
        assertTrue("the toolbar helper must also synchronize overlay constraints", updateToolbar.contains("syncWebOverlayLayout();"));
        assertTrue("move-to-top must focus whichever configured header can accept focus", grid.contains("if (view.requestFocus()) break;"));
    }

    @Test
    public void folderFragmentDependsOnAHostContractInsteadOfVodActivity() throws Exception {
        String folder = read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "fragment", "FolderFragment.java"));
        String home = homeActivity();
        String vod = read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VodActivity.java"));

        assertTrue("folder fragment must define the minimal filter-host contract", folder.contains("public interface FilterHost"));
        assertTrue("folder fragment must resolve the host by capability", folder.contains("instanceof FilterHost"));
        assertFalse("folder fragment must not cast every host to VodActivity", folder.contains("(VodActivity) getActivity()"));
        assertTrue("home must implement the folder filter contract", home.contains("FolderFragment.FilterHost"));
        assertTrue("the existing VOD screen must implement the same contract", vod.contains("FolderFragment.FilterHost"));
    }

    private static String homeActivity() throws Exception {
        return read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "HomeActivity.java"));
    }

    private static String method(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue("Missing method: " + start, from >= 0);
        assertTrue("Missing method boundary: " + end, to > from);
        return source.substring(from, to);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static Path source(String... parts) {
        Path path = Path.of("src");
        for (String part : parts) path = path.resolve(part);
        if (Files.exists(path)) return path;
        path = Path.of("app", "src");
        for (String part : parts) path = path.resolve(part);
        return path;
    }
}
