package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.BaseGridView;
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
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.playback.HistoryResumePayload;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityCollectBinding;
import com.fongmi.android.tv.model.SearchProgress;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteGroupOrderStore;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.ui.adapter.CollectAdapter;
import com.fongmi.android.tv.ui.adapter.SearchAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.ui.dialog.SliderNumberDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SearchPageState;
import com.fongmi.android.tv.utils.SearchResultFilter;
import com.fongmi.android.tv.utils.SearchSourceVisibility;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.textview.MaterialTextView;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CollectActivity extends BaseActivity implements CollectAdapter.OnClickListener, SearchAdapter.OnClickListener, CustomScroller.Callback {

    private static final float SEARCH_CARD_RATIO = 0.72f;
    private static final int SEARCH_LIST_ROW_HEIGHT_DP = 116;
    private static final int GROUP_POPUP_ITEM_HEIGHT_DP = 52;
    private static final int GROUP_POPUP_ITEM_GAP_DP = 2;
    private static final int GROUP_POPUP_MAX_ITEMS = 7;
    private static final int GROUP_POPUP_MIN_WIDTH_DP = 184;
    private static final int GROUP_POPUP_PADDING_DP = 8;

    private ActivityCollectBinding mBinding;
    private CollectAdapter mCollectAdapter;
    private SearchAdapter mSearchAdapter;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private RecyclerView.OnScrollListener mImageScrollListener;
    private List<Site> mSites;
    private List<String> mGroups = new ArrayList<>();
    private final List<Collect> mAllCollectItems = new ArrayList<>();
    private String mFilterGroup = "";
    private PopupWindow mGroupPopup;
    private int mSimilarity;
    private boolean mSearchCompleted;
    private final SearchPageState mPaging = new SearchPageState();
    private final List<Vod> mPendingItems = new ArrayList<>();
    private Runnable mApplyCollect;
    private String mPendingCollectSiteKey = "";
    private boolean mScrolling;
    private boolean mLeavingForPlayback;

    public static void start(Activity activity, String keyword) {
        start(activity, keyword, null, null);
    }

    public static void start(Activity activity, String keyword, String siteKey) {
        start(activity, keyword, siteKey, null, null, null);
    }

    public static void start(Activity activity, String keyword, String siteKey, String group) {
        start(activity, keyword, siteKey, group, null, null);
    }

    public static void start(Activity activity, String keyword, String siteKey, String group, String pic, String wallPic) {
        Intent intent = new Intent(activity, CollectActivity.class);
        intent.putExtra("keyword", keyword);
        intent.putExtra("siteKey", siteKey);
        intent.putExtra("group", group);
        intent.putExtra("pic", pic);
        intent.putExtra("wallPic", wallPic);
        activity.startActivity(intent);
    }

    public static void startFromHistory(Activity activity, History history) {
        startFromHistory(activity, history, history.getVodName(), VodConfig.getCid());
    }

    public static void startFromHistory(Activity activity, History history, String keyword) {
        startFromHistory(activity, history, keyword, VodConfig.getCid());
    }

    public static void startFromHistory(Activity activity, History history, String keyword, int targetCid) {
        Intent intent = new Intent(activity, CollectActivity.class);
        intent.putExtra("keyword", keyword == null || keyword.isEmpty() ? history.getVodName() : keyword);
        intent.putExtra("pic", history.getVodPic());
        intent.putExtra("wallPic", history.getWallPic());
        intent.putExtra("historyResumeCid", history.getCid());
        intent.putExtra("historyResumeKey", HistoryResumePayload.encode(history));
        intent.putExtra("historyResumeTargetCid", targetCid);
        activity.startActivity(intent);
    }

    private int getHistoryResumeCid() {
        return getIntent().getIntExtra("historyResumeCid", -1);
    }

    private String getHistoryResumeKey() {
        return Objects.toString(getIntent().getStringExtra("historyResumeKey"), "");
    }

    private int getHistoryResumeTargetCid() {
        return getIntent().getIntExtra("historyResumeTargetCid", -1);
    }

    private boolean isHistoryResume() {
        return getHistoryResumeCid() >= 0 && getHistoryResumeTargetCid() >= 0 && !getHistoryResumeKey().isEmpty();
    }

    private String getKeyword() {
        return Objects.toString(getIntent().getStringExtra("keyword"), "");
    }

    private String getSiteKey() {
        return Objects.toString(getIntent().getStringExtra("siteKey"), "");
    }

    private String getGroup() {
        return Objects.toString(getIntent().getStringExtra("group"), "");
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
        mFilterGroup = "";
        mSimilarity = Setting.getSearchSimilarity();
        saveKeyword();
        setSites();
        updateFilterControls();
        search();
        focusInitialSource();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mSimilarity = Setting.getSearchSimilarity();
        setRecyclerView();
        setViewModel();
        saveKeyword();
        setSites();
        setSearchColumn();
        updateFilterControls();
        search();
        focusInitialSource();
    }

    @Override
    protected void initEvent() {
        mBinding.searchGroup.setOnClickListener(this::showGroupPopup);
        mBinding.similarityFilter.setOnClickListener(v -> onSimilarityFilter());
        mBinding.searchColumn.setOnClickListener(v -> toggleSearchColumn());
        mBinding.searchGroup.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return mBinding.similarityFilter.requestFocus();
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return focusBelowTop();
            return keyCode == KeyEvent.KEYCODE_DPAD_LEFT;
        });
        mBinding.similarityFilter.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && mBinding.searchGroup.getVisibility() == View.VISIBLE) return mBinding.searchGroup.requestFocus();
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return mBinding.searchColumn.requestFocus();
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return focusBelowTop();
            return false;
        });
        mBinding.searchColumn.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return mBinding.similarityFilter.requestFocus();
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return focusBelowTop();
            return false;
        });
    }

    private void setRecyclerView() {
        int count = getCount();
        mScroller = new CustomScroller(this);
        mCollectAdapter = new CollectAdapter(this);
        mBinding.collect.setHasFixedSize(true);
        mBinding.collect.setItemAnimator(null);
        mBinding.collect.setVerticalSpacing(ResUtil.dp2px(12));
        mBinding.collect.setAdapter(mCollectAdapter);
        mBinding.collect.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                scheduleCollect(position, 260);
            }
        });
        mBinding.collectHorizontal.setHasFixedSize(true);
        mBinding.collectHorizontal.setItemAnimator(null);
        mBinding.collectHorizontal.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.collectHorizontal.setAdapter(mCollectAdapter);
        mBinding.collectHorizontal.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                scheduleCollect(position, 260);
            }
        });
        setSearchLayout();
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
        mBinding.recycler.setAdapter(mSearchAdapter = new SearchAdapter(this, getItemWidth(count), getItemHeight(count), isListMode(count)));
    }

    private boolean canLoadImage() {
        return !isFinishing() && !isDestroyed();
    }

    private void setSearchLayout() {
        boolean horizontal = isSearchLandscape();
        mBinding.collectHorizontal.setVisibility(horizontal ? android.view.View.VISIBLE : android.view.View.GONE);
        mBinding.collect.setVisibility(horizontal ? android.view.View.GONE : android.view.View.VISIBLE);
        mBinding.recycler.setPadding(ResUtil.dp2px(horizontal ? 24 : 0), 0, ResUtil.dp2px(24), ResUtil.dp2px(24));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class).init();
        mViewModel.getSearch().observe(this, this::setCollect);
        mViewModel.getSearchProgress().observe(this, this::setSearchProgress);
        mViewModel.getResult().observe(this, this::setSearch);
    }

    private void setSearchProgress(SearchProgress progress) {
        if (progress == null) return;
        mCollectAdapter.setProgress(progress.current(), progress.total());
        mSearchCompleted = progress.total() > 0 && progress.current() >= progress.total();
        if (mSearchCompleted && mCollectAdapter.getItemCount() > 0) updateEmptyState(mCollectAdapter.getActivated());
    }

    private void saveKeyword() {
        List<String> items = Setting.getKeyword().isEmpty() ? new ArrayList<>() : App.gson().fromJson(Setting.getKeyword(), TypeToken.getParameterized(List.class, String.class).getType());
        items.remove(getKeyword());
        items.add(0, getKeyword());
        if (items.size() > 9) items.remove(9);
        Setting.putKeyword(App.gson().toJson(items));
    }

    private void setSites() {
        String siteKey = getSiteKey();
        String group = getGroup();
        mSites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) {
            if (!site.isSearchable()) continue;
            if (!siteKey.isEmpty() && !site.getKey().equals(siteKey)) continue;
            if (!group.isEmpty() && !site.inGroup(group)) continue;
            mSites.add(site);
        }
        if (Setting.getSearchResultSort() != 1) SiteHealthStore.sortSites(mSites);
        mGroups = TextUtils.isEmpty(siteKey) && TextUtils.isEmpty(group) ? SiteGroupOrderStore.sort(Site.getGroups(mSites)) : new ArrayList<>();
    }

    private boolean focusBelowTop() {
        if (isSearchLandscape()) {
            focusSelectedCollect();
            return true;
        }
        if (focusFirstResult()) return true;
        focusSelectedCollect();
        return true;
    }

    private boolean canFilterGroup() {
        return TextUtils.isEmpty(getSiteKey()) && TextUtils.isEmpty(getGroup()) && !mGroups.isEmpty();
    }

    private String getSimilarityFilterTitle() {
        if (mSimilarity == 0) return getString(R.string.search_filter_similarity_unlimited);
        if (mSimilarity == 100) return getString(R.string.search_filter_similarity_exact);
        return getString(R.string.search_filter_similarity_value, mSimilarity);
    }

    private void updateFilterControls() {
        mBinding.searchGroup.setVisibility(canFilterGroup() ? View.VISIBLE : View.GONE);
        if (TextUtils.isEmpty(mFilterGroup)) mBinding.searchGroup.setText(R.string.search_scope_all);
        else mBinding.searchGroup.setText(mFilterGroup);
        String value = getSimilarityFilterTitle();
        mBinding.similarityFilter.setText(value);
        mBinding.similarityFilter.setSelected(mSimilarity > 0);
        mBinding.similarityFilter.setContentDescription(getString(R.string.search_filter_similarity_description, value, getString(R.string.search_filter_similarity_hint)));
    }

    private void focusInitialSource() {
        if (mCollectAdapter.getItemCount() == 0) {
            mBinding.searchColumn.requestFocus();
            return;
        }
        BaseGridView collect = isSearchLandscape() ? mBinding.collectHorizontal : mBinding.collect;
        collect.setSelectedPosition(0, holder -> holder.itemView.requestFocus());
    }

    private void onSimilarityFilter() {
        String activeSiteKey = getActiveSiteKey();
        SliderNumberDialog.show(this, R.string.search_filter_similarity, mSimilarity, 0, 100, "%", R.string.search_filter_similarity_hint, value -> {
            mSimilarity = value;
            Setting.putSearchSimilarity(value);
            updateFilterControls();
            applyFilters(activeSiteKey);
        });
    }

    private void showGroupPopup(View anchor) {
        if (!canFilterGroup()) return;
        if (mGroupPopup != null) mGroupPopup.dismiss();
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundResource(R.drawable.shape_search_scope_popup);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = ResUtil.dp2px(GROUP_POPUP_PADDING_DP);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addGroupPopupItem(content, getString(R.string.search_scope_all), "");
        for (String group : mGroups) addGroupPopupItem(content, group, group);
        mGroupPopup = new PopupWindow(scroll, groupPopupWidth(anchor), groupPopupHeight(), true);
        mGroupPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        mGroupPopup.setOutsideTouchable(true);
        mGroupPopup.setElevation(ResUtil.dp2px(12));
        mGroupPopup.setOnDismissListener(() -> {
            mGroupPopup = null;
            anchor.requestFocus();
        });
        mGroupPopup.showAsDropDown(anchor, anchor.getWidth() - mGroupPopup.getWidth(), ResUtil.dp2px(8), Gravity.NO_GRAVITY);
    }

    private int groupPopupWidth(View anchor) {
        int width = ResUtil.getTextWidth(getString(R.string.search_scope_all), 18);
        for (String group : mGroups) width = Math.max(width, ResUtil.getTextWidth(group, 18));
        width = Math.max(ResUtil.dp2px(GROUP_POPUP_MIN_WIDTH_DP), width + ResUtil.dp2px(56));
        return Math.min(Math.max(anchor.getWidth(), width), ResUtil.getScreenWidth() - ResUtil.dp2px(48));
    }

    private int groupPopupHeight() {
        int itemHeight = ResUtil.dp2px(GROUP_POPUP_ITEM_HEIGHT_DP);
        int gap = ResUtil.dp2px(GROUP_POPUP_ITEM_GAP_DP);
        int padding = ResUtil.dp2px(GROUP_POPUP_PADDING_DP);
        int rowHeight = itemHeight + gap * 2;
        int contentHeight = (mGroups.size() + 1) * rowHeight + padding * 2;
        return Math.min(contentHeight, GROUP_POPUP_MAX_ITEMS * rowHeight + padding * 2);
    }

    private void addGroupPopupItem(LinearLayout parent, String text, String group) {
        MaterialTextView view = new MaterialTextView(this);
        view.setText(text);
        view.setTextColor(ContextCompat.getColorStateList(this, R.color.selector_search_scope_text));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setIncludeFontPadding(false);
        view.setFocusable(true);
        view.setBackgroundResource(R.drawable.selector_search_scope_item);
        int padding = ResUtil.dp2px(20);
        view.setPadding(padding, 0, padding, 0);
        view.setSelected(Objects.equals(mFilterGroup, group));
        view.setOnClickListener(v -> {
            if (mGroupPopup != null) mGroupPopup.dismiss();
            setFilterGroup(group);
        });
        int gap = ResUtil.dp2px(GROUP_POPUP_ITEM_GAP_DP);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(GROUP_POPUP_ITEM_HEIGHT_DP));
        params.setMargins(gap, gap, gap, gap);
        parent.addView(view, params);
        if (Objects.equals(mFilterGroup, group)) view.post(view::requestFocus);
    }

    private void setFilterGroup(String group) {
        String activeSiteKey = getActiveSiteKey();
        mFilterGroup = Objects.toString(group, "");
        updateFilterControls();
        applyFilters(activeSiteKey);
        if (!mFilterGroup.isEmpty()) Notify.show(getString(R.string.search_scope_group_hint, mFilterGroup));
    }

    private boolean matchFilter(Site site) {
        return TextUtils.isEmpty(mFilterGroup) || site.inGroup(mFilterGroup);
    }

    private Collect addMasterCollect(List<Vod> items) {
        Site site = items.get(0).getSite();
        Collect collect = findCollect(mAllCollectItems, site.getKey());
        if (collect == null) {
            collect = new Collect(site, new ArrayList<>());
            mAllCollectItems.add(collect);
        }
        collect.getList().addAll(items);
        return collect;
    }

    private Collect findCollect(List<Collect> items, String siteKey) {
        for (Collect item : items) if (item.getSite().getKey().equals(siteKey)) return item;
        return null;
    }

    private String getActiveSiteKey() {
        if (mCollectAdapter == null || mCollectAdapter.getItemCount() == 0) return "all";
        return mCollectAdapter.getActivated().getSite().getKey();
    }

    private List<Collect> getFilteredCollectItems(String activeSiteKey) {
        List<Collect> items = new ArrayList<>();
        Collect all = Collect.all();
        all.setSelected("all".equals(activeSiteKey));
        items.add(all);
        for (int i = 1; i < mAllCollectItems.size(); i++) {
            Collect raw = mAllCollectItems.get(i);
            if (!matchFilter(raw.getSite())) continue;
            List<Vod> visible = SearchResultFilter.filter(raw.getList(), getKeyword(), mSimilarity);
            if (!SearchSourceVisibility.shouldShow(!visible.isEmpty())) continue;
            Collect item = new Collect(raw.getSite(), visible);
            item.setPage(mPaging.getPage(raw.getSite().getKey()));
            item.setSelected(raw.getSite().getKey().equals(activeSiteKey));
            all.getList().addAll(visible);
            items.add(item);
        }
        if (getSelectedCollect(items) == all) all.setSelected(true);
        return items;
    }

    private Collect getSelectedCollect(List<Collect> items) {
        for (Collect item : items) if (item.isSelected()) return item;
        return items.get(0);
    }

    private void applyFilters(String activeSiteKey) {
        removeApplyCollect();
        mPendingItems.clear();
        List<Collect> items = getFilteredCollectItems(activeSiteKey);
        Collect activated = getSelectedCollect(items);
        mCollectAdapter.setItems(items);
        int position = items.indexOf(activated);
        mCollectAdapter.setSelected(Math.max(0, position));
        RecyclerView collect = isSearchLandscape() ? mBinding.collectHorizontal : mBinding.collect;
        if (position >= 0) {
            if (collect instanceof androidx.leanback.widget.HorizontalGridView horizontal) horizontal.setSelectedPosition(position);
            if (collect instanceof androidx.leanback.widget.VerticalGridView vertical) vertical.setSelectedPosition(position);
        }
        restoreScroller(activated.getSite().getKey());
        setSearchItemsLazy(new ArrayList<>(activated.getList()));
        updateEmptyState(activated);
        mBinding.recycler.post(this::maybeLoadSelectedPage);
    }

    private void updateEmptyState(Collect activated) {
        String siteKey = activated.getSite().getKey();
        boolean loading = "all".equals(siteKey) ? mPaging.hasPending() : mPaging.isPending(siteKey);
        boolean show = mSimilarity > 0 && mSearchCompleted && !loading && activated.getList().isEmpty() && hasRawResults(siteKey);
        if (show) {
            String text = mSimilarity == 100 ? getString(R.string.search_filter_empty_exact) : getString(R.string.search_filter_empty, mSimilarity);
            mBinding.empty.setText(text);
        }
        mBinding.empty.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void restoreScroller(String siteKey) {
        if (mPaging.isPending(siteKey)) return;
        mScroller.reset();
        if ("all".equals(siteKey)) return;
        mScroller.setPage(mPaging.getPage(siteKey));
        mScroller.setEnable(mPaging.getPageCount(siteKey));
    }

    private void maybeLoadNextPage(String siteKey, boolean rawPageHasItems, boolean visiblePageHasItems) {
        if (!siteKey.equals(getActiveSiteKey())) return;
        if (!mPaging.shouldContinue(siteKey, mSimilarity > 0, rawPageHasItems, visiblePageHasItems)) return;
        restoreScroller(siteKey);
        mScroller.checkMore();
        if (mPaging.isPending(siteKey)) updateEmptyState(mCollectAdapter.getActivated());
    }

    private void maybeLoadSelectedPage() {
        if (mCollectAdapter == null || mCollectAdapter.getItemCount() == 0) return;
        Collect activated = mCollectAdapter.getActivated();
        String siteKey = activated.getSite().getKey();
        Collect raw = findCollect(mAllCollectItems, siteKey);
        maybeLoadNextPage(siteKey, raw != null && !raw.getList().isEmpty(), !activated.getList().isEmpty());
    }

    private boolean hasRawResults(String siteKey) {
        if (!"all".equals(siteKey)) {
            Collect raw = findCollect(mAllCollectItems, siteKey);
            return raw != null && !raw.getList().isEmpty();
        }
        for (int i = 1; i < mAllCollectItems.size(); i++) {
            Collect raw = mAllCollectItems.get(i);
            if (matchFilter(raw.getSite()) && !raw.getList().isEmpty()) return true;
        }
        return false;
    }

    private void search() {
        mSearchCompleted = false;
        mPaging.clear();
        removeApplyCollect();
        mCollectAdapter.clear();
        mSearchAdapter.clear();
        mPendingItems.clear();
        mAllCollectItems.clear();
        mScroller.reset();
        mBinding.result.setText(getResultTitle());
        mBinding.empty.setVisibility(View.GONE);
        if (mSites.isEmpty()) return;
        mAllCollectItems.add(Collect.all());
        if (Setting.getSearchResultSort() == 1) {
            for (Site site : mSites) mAllCollectItems.add(Collect.create(site));
        }
        applyFilters("all");
        mViewModel.searchContent(mSites, getKeyword(), false);
    }

    private String getResultTitle() {
        if (!getGroup().isEmpty()) return getString(R.string.search_result_group, getGroup(), getKeyword());
        if (!getSiteKey().isEmpty()) return getString(R.string.search_result_current, getKeyword());
        return getString(R.string.collect_result, getKeyword());
    }

    private int getCount() {
        int column = Setting.getSearchColumn();
        if (column == 1) return 1; // 1列 (列表模式)
        if (column == 2) return 2; // 2列
        return getAutoCount(); // 自适应
    }

    private int getAutoCount() {
        return Product.getColumn();
    }

    private boolean isSearchLandscape() {
        return Setting.getSearchUi() == 0;
    }

    private boolean isListMode(int count) {
        return count == 1;
    }

    private void setSearchColumn() {
        int iconRes = getSearchColumnIcon();
        mBinding.searchColumn.setImageResource(iconRes);
        String description = getSearchColumnDescription();
        mBinding.searchColumn.setContentDescription(description);
    }

    private int getSearchColumnIcon() {
        int column = Setting.getSearchColumn();
        if (column == 1) return R.drawable.ic_site_list; // 列表模式 (1列)
        return R.drawable.ic_site_grid; // 网格模式 (自适应或默认)
    }

    private String getSearchColumnDescription() {
        String[] options = getResources().getStringArray(R.array.select_search_column);
        int column = Setting.getSearchColumn();
        String current = column == 1 ? options[1] : options[0]; // 0: 自适应, 1: 1列
        int nextColumn = column == 1 ? 0 : 1;
        String next = nextColumn == 1 ? options[1] : options[0];
        return getString(R.string.setting_search_column) + ": " + current + " → " + next;
    }

    private void toggleSearchColumn() {
        int current = Setting.getSearchColumn();
        int next = current == 1 ? 0 : 1; // 0: 自适应 ↔ 1: 1列
        Setting.putSearchColumn(next);
        setSearchColumn();
        updateRecyclerLayout();
    }

    private void updateRecyclerLayout() {
        int count = getCount();
        GridLayoutManager layoutManager = (GridLayoutManager) mBinding.recycler.getLayoutManager();
        if (layoutManager != null) {
            layoutManager.setSpanCount(count);
        }
        mSearchAdapter = new SearchAdapter(this, getItemWidth(count), getItemHeight(count), isListMode(count));
        mBinding.recycler.setAdapter(mSearchAdapter);
        mBinding.recycler.setItemViewCacheSize(count * 3);

        // 重新加载当前选中的收藏项
        if (mCollectAdapter != null && mCollectAdapter.getPosition() >= 0) {
            Collect activated = mCollectAdapter.getActivated();
            if (activated != null) {
                setSearchItemsLazy(new ArrayList<>(activated.getList()));
            }
        }
    }

    private int getItemWidth(int count) {
        int width = getResultWidth();
        int spacing = ResUtil.dp2px(8) * (count - 1);
        return (width - spacing) / count;
    }

    private int getResultWidth() {
        return ResUtil.getScreenWidth() - ResUtil.dp2px(isSearchLandscape() ? 48 : 220);
    }

    private int getItemHeight(int count) {
        return isListMode(count) ? ResUtil.dp2px(SEARCH_LIST_ROW_HEIGHT_DP) : (int) (getItemWidth(count) / SEARCH_CARD_RATIO);
    }

    private void setCollect(Result result) {
        if (mLeavingForPlayback || result == null || result.getList().isEmpty()) return;
        mPaging.recordInitial(result.getVod().getSiteKey(), result.getPageCount(), SearchPageState.pageToken(result.getList()));
        applySearchResult(result);
    }

    private void setSearch(Result result) {
        if (result == null) return;
        boolean hasItems = !result.getList().isEmpty();
        String resultSiteKey = hasItems ? result.getVod().getSiteKey() : "";
        SearchPageState.Completion completion = mPaging.complete(resultSiteKey, hasItems, result.getPageCount(), SearchPageState.pageToken(result.getList()));
        if (!completion.handled()) return;
        Collect raw = findCollect(mAllCollectItems, completion.siteKey());
        if (completion.accepted() && raw != null) raw.setPage(completion.page());
        restoreScroller(getActiveSiteKey());
        if (mLeavingForPlayback) return;
        if (!completion.accepted()) {
            if (mCollectAdapter.getItemCount() > 0) updateEmptyState(mCollectAdapter.getActivated());
            if (!completion.siteKey().equals(getActiveSiteKey())) maybeLoadSelectedPage();
            return;
        }
        applySearchResult(result);
        if (!completion.siteKey().equals(getActiveSiteKey())) maybeLoadSelectedPage();
    }

    private void applySearchResult(Result result) {
        List<Vod> rawItems = new ArrayList<>(result.getList());
        Collect raw = addMasterCollect(rawItems);
        raw.setPage(mPaging.getPage(raw.getSite().getKey()));
        if (!matchFilter(raw.getSite())) return;
        List<Vod> visible = SearchResultFilter.filter(rawItems, getKeyword(), mSimilarity);
        updateProjectedCollect(raw, visible);
        if (visible.isEmpty()) {
            updateEmptyState(mCollectAdapter.getActivated());
            maybeLoadNextPage(raw.getSite().getKey(), true, false);
            return;
        }
        String activeSiteKey = getActiveSiteKey();
        mCollectAdapter.add(visible);
        if ("all".equals(activeSiteKey) || raw.getSite().getKey().equals(activeSiteKey)) addSearchItems(visible);
        updateEmptyState(mCollectAdapter.getActivated());
    }

    private int getProjectedCollectPosition(String siteKey) {
        int position = 1;
        for (int i = 1; i < mAllCollectItems.size(); i++) {
            Collect item = mAllCollectItems.get(i);
            if (item.getSite().getKey().equals(siteKey)) break;
            if (mCollectAdapter.findCollectIndex(item.getSite().getKey()) >= 0) position++;
        }
        return position;
    }

    private void updateProjectedCollect(Collect raw, List<Vod> visible) {
        int index = mCollectAdapter.findCollectIndex(raw.getSite().getKey());
        if (index < 0) {
            if (visible.isEmpty()) return;
            Collect collect = new Collect(raw.getSite(), new ArrayList<>(visible));
            collect.setPage(mPaging.getPage(raw.getSite().getKey()));
            mCollectAdapter.add(getProjectedCollectPosition(raw.getSite().getKey()), collect);
        } else {
            Collect collect = mCollectAdapter.get(index);
            collect.setPage(mPaging.getPage(raw.getSite().getKey()));
            if (!visible.isEmpty()) collect.getList().addAll(visible);
            mCollectAdapter.update(index, collect);
        }
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
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (isSearchLandscape()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                mBinding.searchColumn.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return focusFirstResult();
            if (position == 0 && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return true;
            return false;
        }
        // 在第一项按上键，跳转到切换按钮
        if (position == 0 && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            mBinding.searchColumn.requestFocus();
            return true;
        }
        return false;
    }

    private void scheduleCollect(int position, long delayMillis) {
        if (position < 0 || position >= mCollectAdapter.getItemCount()) return;
        Collect item = mCollectAdapter.get(position);
        String siteKey = item.getSite().getKey();
        boolean same = siteKey.equals(getActiveSiteKey());
        mCollectAdapter.setSelected(position);
        restoreScroller(siteKey);
        mPendingItems.clear();
        if (same && delayMillis > 0 && siteKey.equals(mPendingCollectSiteKey)) return;
        applyCollectDeferred(siteKey, delayMillis);
    }

    private void applyCollectDeferred(String siteKey, long delayMillis) {
        removeApplyCollect();
        mPendingCollectSiteKey = siteKey;
        mApplyCollect = () -> {
            if (isFinishing() || isDestroyed()) return;
            Collect activated = mCollectAdapter.findActivated(siteKey);
            if (activated == null) return;
            setSearchItemsLazy(new ArrayList<>(activated.getList()));
            updateEmptyState(activated);
            maybeLoadSelectedPage();
        };
        App.post(mApplyCollect, delayMillis);
    }

    private void removeApplyCollect() {
        if (mApplyCollect != null) App.removeCallbacks(mApplyCollect);
        mApplyCollect = null;
        mPendingCollectSiteKey = "";
    }

    private void setSearchItemsLazy(List<Vod> items) {
        mBinding.recycler.scrollToPosition(0);
        mSearchAdapter.setSource(items, getCount() * 4);
        mBinding.recycler.post(() -> {
            ensureSearchRows(getCount(), 2);
            preloadNextRows(getCount());
        });
    }

    @Override
    public void onItemClick(Vod item) {
        if (!item.isFolder() && isHistoryResume()) {
            HistoryResumeCoordinator.openSelected(this, getHistoryResumeCid(), getHistoryResumeKey(), getHistoryResumeTargetCid(), item);
            return;
        }
        long start = System.currentTimeMillis();
        setResult(Activity.RESULT_OK);
        mLeavingForPlayback = true;
        mPaging.cancelPending();
        restoreScroller(getActiveSiteKey());
        removeApplyCollect();
        SpiderDebug.log("collect-flow", "item click site=%s id=%s name=%s folder=%s", item.getSiteKey(), item.getId(), item.getName(), item.isFolder());
        if (item.isFolder()) {
            if (isHistoryResume()) VodActivity.start(this, item.getSiteKey(), Result.folder(item), 0, getHistoryResumeCid(), getHistoryResumeKey(), getHistoryResumeTargetCid());
            else VodActivity.start(this, item.getSiteKey(), Result.folder(item));
        } else {
            String pic = item.getPic().isEmpty() ? getPic() : item.getPic();
            VideoActivity.collect(this, item.getSiteKey(), item.getId(), item.getName(), pic, getWallPic(), getKeyword());
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
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return onSearchUp(position, count);
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return position % count == count - 1;
        return false;
    }

    private boolean onSearchUp(int position, int count) {
        if (position < count) {
            if (isSearchLandscape()) focusSelectedCollect();
            else mBinding.searchColumn.requestFocus();
            return true;
        }
        return false;
    }

    private boolean focusFirstResult() {
        if (mBinding.recycler.getChildCount() == 0) return false;
        mBinding.recycler.getChildAt(0).requestFocus();
        return true;
    }

    private void focusSelectedCollect() {
        RecyclerView collect = isSearchLandscape() ? mBinding.collectHorizontal : mBinding.collect;
        int position = mCollectAdapter != null ? mCollectAdapter.getPosition() : 0;
        RecyclerView.ViewHolder holder = collect.findViewHolderForAdapterPosition(position);
        if (holder != null) holder.itemView.requestFocus();
        else collect.requestFocus();
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
        String siteKey = getActiveSiteKey();
        if ("all".equals(siteKey)) return false;
        Collect raw = findCollect(mAllCollectItems, siteKey);
        if (raw == null || raw.getList().isEmpty()) return false;
        int requestedPage;
        try {
            requestedPage = Integer.parseInt(page);
        } catch (NumberFormatException e) {
            return false;
        }
        if (!mPaging.begin(siteKey, requestedPage)) return false;
        try {
            mViewModel.searchContent(raw.getSite(), getKeyword(), false, page);
        } catch (RuntimeException e) {
            mPaging.cancelPending();
            restoreScroller(siteKey);
            return false;
        }
        return true;
    }

    @Override
    protected void onBackInvoked() {
        removeApplyCollect();
        mPaging.cancelPending();
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
        mPaging.clear();
        removeApplyCollect();
        if (mGroupPopup != null) mGroupPopup.dismiss();
        mPendingItems.clear();
        mAllCollectItems.clear();
        SiteHealthStore.flush();
        super.onDestroy();
    }
}
