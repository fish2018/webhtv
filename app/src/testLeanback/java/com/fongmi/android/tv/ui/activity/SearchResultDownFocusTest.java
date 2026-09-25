package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchResultDownFocusTest {

    @Test
    public void validDownPressFocusesTheNextResultImmediately() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java");
        int start = source.indexOf("private boolean onSearchDown(int position, int count)");
        int end = source.indexOf("boolean onLoadMore", start);

        assertTrue("result-row navigation must define its down policy", start >= 0 && end > start);
        String down = source.substring(start, end);
        assertFalse("a valid down press must not defer movement to the system focus search",
                down.contains("if (bottom) mScroller.checkMore();\n            return bottom;"));
        assertTrue("a valid down press must return true before a new row is focused",
                down.contains("focusSearchTarget(next);") && down.contains("return true;"));
        assertTrue("navigation must still load the next row before focusing it",
                down.contains("mSearchAdapter.ensureLoaded(next + 1, count * 3);"));
    }

    @Test
    public void deferredResultFocusScrollsFirstAndRejectsStaleRequests() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java");
        int start = source.indexOf("private void focusSearchTarget(int position)");
        int end = source.indexOf("private boolean canFocusSearchResult(int position)");

        assertTrue("search results must define a deferred focus policy", start >= 0 && end > start);
        String focus = source.substring(start, end);
        assertTrue("a missing target must scroll to the requested row before focusing it",
                focus.contains("scrollToSearchResult(position);"));
        assertTrue("focus must be restored after the target is laid out",
                focus.contains("mBinding.recycler.post(") && focus.contains("requestSearchResultFocus(position, generation);"));
        assertTrue("a focused target must be aligned fully into view",
                focus.contains("alignSearchResultCard(position, target);")
                        && focus.contains("alignSearchResultCard(position, laidOutTarget);"));
        assertTrue("focus must be requested after the explicit row layout completes",
                focus.contains("private void requestSearchResultFocus(int position, int generation)")
                        && focus.contains("if (target != null) target.requestFocus();"));
        assertTrue("a later down press must invalidate a queued focus request",
                focus.contains("int generation = ++mSearchFocusGeneration;")
                        && focus.contains("if (generation != mSearchFocusGeneration"));
    }

    @Test
    public void downNavigationAlignsTheFocusedResultCardToTheViewportBottom() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java");
        int start = source.indexOf("private void alignSearchResultCard(int position, View focusedView)");
        int end = source.indexOf("private void preloadNextRows(int count)");

        assertTrue("search results must define focused-card alignment", start >= 0 && end > start);
        String align = source.substring(start, end);
        assertTrue("down navigation must align the complete target row instead of relying on default focus scrolling",
                align.contains("int rowStart = Math.max(0, position - position % spanCount);")
                        && align.contains("layoutManager.scrollToPositionWithOffset(rowStart, offset);"));
        assertTrue("the target card bottom must be placed inside the result viewport",
                align.contains("int bottom = mBinding.recycler.getHeight() - mBinding.recycler.getPaddingBottom() - padding;")
                        && align.contains("int offset = Math.max(mBinding.recycler.getPaddingTop(), bottom - cardHeight);"));
        assertTrue("default focus animation must not overwrite the explicit row position",
                align.contains("mBinding.recycler.stopScroll();"));
    }

    @Test
    public void scrollCallbacksDeferResultLoadingUntilAfterLayout() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java");
        int listenerStart = source.indexOf("public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy)");
        int listenerEnd = source.indexOf("public void onScrollStateChanged", listenerStart);
        assertTrue("scroll callback must defer all row preparation", listenerStart >= 0 && listenerEnd > listenerStart);
        String listener = source.substring(listenerStart, listenerEnd);
        assertTrue("onScrolled must post row preparation after the callback returns",
                listener.contains("recyclerView.post(() ->")
                        && listener.contains("ensureSearchRows(count, 2);")
                        && listener.contains("preloadNextRows(count);"));
        int start = source.indexOf("private void preloadNextRows(int count)");
        int end = source.indexOf("@Override\n    public void onItemClick", start);

        assertTrue("search scroll helpers must be present", start >= 0 && end > start);
        String scroll = source.substring(start, end);
        assertTrue("scroll callbacks must route adapter changes through the layout-safe helper",
                scroll.contains("ensureSearchItemsLoaded(direction, count * 2);")
                        && scroll.contains("ensureSearchItemsLoaded(last + 1, count * rows);"));
        assertTrue("adapter insertion must be posted when RecyclerView is computing its layout",
                scroll.contains("if (mBinding.recycler.isComputingLayout())")
                        && scroll.contains("mBinding.recycler.post(() -> ensureSearchItemsLoaded(position, count));"));
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
