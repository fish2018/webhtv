package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class VodActivityCategoryEdgeTest {

    @Test
    public void standaloneVodEdgesSwitchPagerCategoriesAndFocusTheNewCategory() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VodActivity.java");

        assertTrue("standalone VOD must receive category edge events", source.contains("FolderFragment.CategoryEdgeHost"));
        assertTrue("edge navigation must resolve the adjacent pager position", source.contains("int target = position + (towardEnd ? 1 : -1);"));
        assertTrue("edge navigation must switch the standalone VOD pager", source.contains("mBinding.pager.setCurrentItem(target);"));
        assertTrue("every content row must return to the category strip after the page switch", source.contains("mPendingCategoryFocus = true;"));
        assertTrue("category edge switches must scroll the new page to its top", source.contains("getFragment().scrollContentToTop(generation);"));
        assertTrue("category edge switches must restore focus after the page callback", source.contains("mBinding.recycler.post(mPendingCategoryFocusRunnable);") && source.contains("holder.itemView.requestFocus();"));
        assertTrue("edge navigation must cancel a queued category pager update", source.contains("App.removeCallbacks(mRunnable);"));
    }

    @Test
    public void hiddenCategoryHeaderIsRevealedBeforeRestoringFocusAndScrolling() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VodActivity.java");
        int start = source.indexOf("if (mPendingCategoryFocus)");
        int runnable = source.indexOf("mPendingCategoryFocusRunnable = () -> {", start);
        int post = source.indexOf("mBinding.recycler.post(mPendingCategoryFocusRunnable);", runnable);
        int show = source.indexOf("mBinding.recycler.setVisibility(View.VISIBLE);", runnable);
        int focus = source.indexOf("mBinding.recycler.requestFocus();", runnable);
        int top = source.indexOf("getFragment().scrollContentToTop(generation);", runnable);

        assertTrue("the host must schedule the category-focus callback", start >= 0 && runnable > start && post > runnable);
        assertTrue("the host must reveal the header before restoring focus", show > runnable && focus > show);
        assertTrue("scrolling the new page must not retain focus on an old content row", top > focus);
        assertTrue("a stale page callback must not focus a different category", source.substring(runnable, post).contains("mBinding.recycler.getSelectedPosition() == position"));
    }

    @Test
    public void pagerFocusCallbacksCarryGenerationAndLifecycleInvalidation() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VodActivity.java");

        assertTrue("standalone VOD must track focus generations", source.contains("private int focusGeneration;"));
        assertTrue("standalone VOD must retain the exact pending runnable", source.contains("private Runnable mPendingCategoryFocusRunnable;"));
        assertTrue("pager focus must reject stale callbacks", source.contains("if (generation != focusGeneration) return;"));
        assertTrue("pager focus invalidation must remove the exact runnable", source.contains("removeCallbacks(mPendingCategoryFocusRunnable)"));
        assertTrue("pager pause must invalidate pending focus", source.contains("protected void onPause()") && source.contains("invalidatePendingFocusRequests();"));
        assertTrue("pager destruction must invalidate pending focus", source.contains("protected void onDestroy()") && source.contains("invalidatePendingFocusRequests();"));
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
