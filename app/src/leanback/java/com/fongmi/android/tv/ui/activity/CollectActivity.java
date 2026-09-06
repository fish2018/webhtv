package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityCollectBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.ui.adapter.CollectAdapter;
import com.fongmi.android.tv.ui.adapter.SearchAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SearchModeStore;
import com.github.catvod.crawler.SpiderDebug;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CollectActivity extends BaseActivity implements CollectAdapter.OnClickListener, SearchAdapter.OnClickListener, CustomScroller.Callback {

    private ActivityCollectBinding mBinding;
    private CollectAdapter mCollectAdapter;
    private SearchAdapter mSearchAdapter;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private RecyclerView.OnScrollListener mImageScrollListener;
    private List<Site> mSites;
    private final List<Vod> mPendingItems = new ArrayList<>();
    private Runnable mApplyCollect;
    private int mPendingCollectPosition = RecyclerView.NO_POSITION;
    private boolean mScrolling;
    private boolean mLeavingForPlayback;

    public static void start(Activity activity, String keyword) {
        start(activity, keyword, null);
    }

    public static void start(Activity activity, String keyword, String siteKey) {
        start(activity, keyword, siteKey, null, null);
    }

    public static void start(Activity activity, String keyword, String siteKey, String pic, String wallPic) {
        Intent intent = new Intent(activity, CollectActivity.class);
        intent.putExtra("keyword", keyword);
        intent.putExtra("siteKey", siteKey);
        intent.putExtra("pic", pic);
        intent.putExtra("wallPic", wallPic);
        activity.startActivity(intent);
    }

    private String getKeyword() {
        return Objects.toString(getIntent().getStringExtra("keyword"), "");
    }

    private String getSiteKey() {
        return Objects.toString(getIntent().getStringExtra("siteKey"), "");
    }

    private String getPic() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    private String getWallPic() {
        return Objects.toString(getIntent().getStringExtra("wallPic"), "");
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getIntent().putExtras(intent);
        if (mViewModel != null) mViewModel.stopSearch();
        saveKeyword();
        search();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setRecyclerView();
        setViewModel();
        saveKeyword();
        setSites();
        search();
    }

    private void setRecyclerView() {
        int count = getCount();
        mScroller = new CustomScroller(this);
        mBinding.collect.setHasFixedSize(true);
        mBinding.collect.setItemAnimator(null);
        mBinding.collect.setVerticalSpacing(ResUtil.dp2px(12));
        mBinding.collect.setAdapter(mCollectAdapter = new CollectAdapter(this));
        mBinding.collect.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                scheduleCollect(position, 260);
            }
        });
        mBinding.collect.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN || keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            return focusFirstSearchResult();
        });
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setItemViewCacheSize(count * 3);
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, count));
        mBinding.recycler.addOnScrollListener(mScroller);
        mBinding.recycler.addOnScrollListener(mImageScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!canLoadImage()) return;
                ensureSearchRows(count, 2);
                preloadNextRows(count);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (!canLoadImage()) return;
                boolean scrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
                if (scrolling == mScrolling) return;
                mScrolling = scrolling;
                if (mScrolling) {
                    ensureSearchRows(count, 2);
                    preloadNextRows(count);
                } else {
                    Glide.with(CollectActivity.this).resumeRequests();
                    flushPendingItems();
                    ensureSearchRows(count, 2);
                    preloadNextRows(count);
                }
            }
        });
        mBinding.recycler.setAdapter(mSearchAdapter = new SearchAdapter(this, getItemWidth(count), getItemHeight(count)));
    }

    private boolean canLoadImage() {
        return !isFinishing() && !isDestroyed();
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class).init();
        mViewModel.getSearch().observe(this, this::setCollect);
        mViewModel.getResult().observe(this, this::setSearch);
    }

    private void saveKeyword() {
        List<String> items = Setting.getKeyword().isEmpty() ? new ArrayList<>() : App.gson().fromJson(Setting.getKeyword(), TypeToken.getParameterized(List.class, String.class).getType());
        items.remove(getKeyword());
        items.add(0, getKeyword());
        if (items.size() > 9) items.remove(9);
        Setting.putKeyword(App.gson().toJson(items));
    }

    private void setSites() {
        mSites = new ArrayList<>(SearchModeStore.filterSites(VodConfig.get().getSites(), getSiteKey()));
        SiteHealthStore.sortSites(mSites);
    }

    private void search() {
        removeApplyCollect();
        mCollectAdapter.clear();
        mSearchAdapter.clear();
        mPendingItems.clear();
        mScroller.reset();
        mBinding.result.setText(getString(R.string.collect_result, getKeyword()));
        if (mSites.isEmpty()) return;
        mCollectAdapter.add(Collect.all());
        mViewModel.searchContent(mSites, getKeyword(), false);
    }

    private int getCount() {
        return Product.getColumn();
    }

    private int getItemWidth(int count) {
        int width = ResUtil.getScreenWidth() - ResUtil.dp2px(220);
        int spacing = ResUtil.dp2px(8) * (count - 1);
        return (width - spacing) / count;
    }

    private int getItemHeight(int count) {
        return (int) (getItemWidth(count) / 0.72f);
    }

    private void setCollect(Result result) {
        if (mLeavingForPlayback) return;
        if (result == null || result.getList().isEmpty()) return;
        mCollectAdapter.add(Collect.create(result.getList()));
        mCollectAdapter.add(result.getList());
        if (mCollectAdapter.getPosition() == 0) addSearchItems(result.getList());
    }

    private void setSearch(Result result) {
        if (mLeavingForPlayback) return;
        if (result == null) return;
        mScroller.endLoading(result);
        Collect activated = mCollectAdapter.getActivated();
        boolean same = !result.getList().isEmpty() && activated.getSite().equals(result.getVod().getSite());
        if (same) activated.getList().addAll(result.getList());
        if (same) addSearchItems(result.getList());
    }

    private void addSearchItems(List<Vod> items) {
        if (mScrolling) mPendingItems.addAll(items);
        else mSearchAdapter.appendSource(items, getCount() * 4);
    }

    private void flushPendingItems() {
        if (mPendingItems.isEmpty()) return;
        mSearchAdapter.appendSource(new ArrayList<>(mPendingItems), getCount() * 4);
        mPendingItems.clear();
    }

    private void preloadNextRows(int count) {
        RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
        if (!(manager instanceof GridLayoutManager layoutManager)) return;
        int first = layoutManager.findFirstVisibleItemPosition();
        int last = layoutManager.findLastVisibleItemPosition();
        if (first < 0 || last < 0) return;
        int direction = last >= first ? last + 1 : first + 1;
        mSearchAdapter.ensureLoaded(direction, count * 2);
    }

    private void ensureSearchRows(int count, int rows) {
        RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
        if (!(manager instanceof GridLayoutManager layoutManager)) return;
        int last = layoutManager.findLastVisibleItemPosition();
        if (last < 0) return;
        mSearchAdapter.ensureLoaded(last + 1, count * rows);
    }

    @Override
    public void onItemClick(int position, Collect item) {
        scheduleCollect(position, 0);
    }

    @Override
    public boolean onCollectKey(int position, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        return focusFirstSearchResult();
    }

    private void scheduleCollect(int position, long delayMillis) {
        if (position < 0 || position >= mCollectAdapter.getItemCount()) return;
        Collect item = mCollectAdapter.get(position);
        boolean same = mCollectAdapter.getPosition() == position;
        mCollectAdapter.setSelected(position);
        mScroller.reset();
        mScroller.setPage(item.getPage());
        mPendingItems.clear();
        if (same && delayMillis > 0 && mPendingCollectPosition == position) return;
        applyCollectDeferred(position, item, delayMillis);
    }

    private void applyCollectDeferred(int position, Collect item, long delayMillis) {
        removeApplyCollect();
        mPendingCollectPosition = position;
        mApplyCollect = () -> {
            if (isFinishing() || isDestroyed()) return;
            if (mCollectAdapter.getPosition() != position) return;
            setSearchItemsLazy(new ArrayList<>(item.getList()));
        };
        App.post(mApplyCollect, delayMillis);
    }

    private void removeApplyCollect() {
        if (mApplyCollect != null) App.removeCallbacks(mApplyCollect);
        mApplyCollect = null;
        mPendingCollectPosition = RecyclerView.NO_POSITION;
    }

    private void setSearchItemsLazy(List<Vod> items) {
        mSearchAdapter.setSource(items, getCount() * 4);
        mBinding.recycler.post(() -> {
            scrollSearchToTop();
            ensureSearchRows(getCount(), 2);
            preloadNextRows(getCount());
        });
    }

    private void scrollSearchToTop() {
        RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
        if (manager instanceof GridLayoutManager layoutManager) layoutManager.scrollToPositionWithOffset(0, 0);
        else mBinding.recycler.scrollToPosition(0);
    }

    private boolean focusFirstSearchResult() {
        if (mSearchAdapter == null || mSearchAdapter.getItemCount() == 0) return false;
        mBinding.recycler.post(() -> {
            scrollSearchToTop();
            mBinding.recycler.post(() -> {
                RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
                View target = manager == null ? null : manager.findViewByPosition(0);
                if (target != null) target.requestFocus();
                else mBinding.recycler.requestFocus();
            });
        });
        return true;
    }

    @Override
    public void onItemClick(Vod item) {
        long start = System.currentTimeMillis();
        setResult(Activity.RESULT_OK);
        mLeavingForPlayback = true;
        removeApplyCollect();
        SpiderDebug.log("collect-flow", "item click site=%s id=%s name=%s folder=%s", item.getSiteKey(), item.getId(), item.getName(), item.isFolder());
        if (item.isFolder()) {
            VodActivity.start(this, item.getSiteKey(), Result.folder(item));
        } else {
            String pic = item.getPic().isEmpty() ? getPic() : item.getPic();
            VideoActivity.collect(this, item.getSiteKey(), item.getId(), item.getName(), pic, getWallPic());
        }
        SpiderDebug.log("collect-flow", "activity launch requested cost=%dms", System.currentTimeMillis() - start);
        App.post(() -> {
            long cleanup = System.currentTimeMillis();
            if (mViewModel != null) mViewModel.stopSearch();
            mPendingItems.clear();
            if (canLoadImage()) Glide.with(this).pauseRequests();
            SpiderDebug.log("collect-flow", "leave cleanup cost=%dms", System.currentTimeMillis() - cleanup);
        }, 200);
    }

    @Override
    public boolean onItemKey(int position, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || position < 0) return false;
        int count = getCount();
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return onSearchDown(position, count);
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return position < count;
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return position % count == count - 1;
        return false;
    }

    private boolean onSearchDown(int position, int count) {
        int next = position + count;
        if (next + count >= mSearchAdapter.getItemCount()) flushPendingItems();
        mSearchAdapter.ensureLoaded(next + 1, count * 3);
        boolean bottom = next >= mSearchAdapter.getItemCount();
        if (bottom) mScroller.checkMore();
        return bottom;
    }

    @Override
    public boolean onLoadMore(String page) {
        Collect activated = mCollectAdapter.getActivated();
        if ("all".equals(activated.getSite().getKey())) return false;
        mViewModel.searchContent(activated.getSite(), getKeyword(), false, page);
        activated.setPage(Integer.parseInt(page));
        return true;
    }

    @Override
    protected void onBackInvoked() {
        removeApplyCollect();
        mViewModel.stopSearch();
        super.onBackInvoked();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mLeavingForPlayback = false;
        if (canLoadImage()) Glide.with(this).resumeRequests();
    }

    @Override
    protected void onDestroy() {
        if (mBinding != null) {
            mBinding.recycler.removeOnScrollListener(mScroller);
            if (mImageScrollListener != null) mBinding.recycler.removeOnScrollListener(mImageScrollListener);
        }
        if (mViewModel != null) mViewModel.stopSearch();
        removeApplyCollect();
        mPendingItems.clear();
        SiteHealthStore.flush();
        super.onDestroy();
    }
}
