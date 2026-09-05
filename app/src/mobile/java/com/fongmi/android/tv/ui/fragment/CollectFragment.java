package com.fongmi.android.tv.ui.fragment;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.textview.MaterialTextView;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentCollectBinding;
import com.fongmi.android.tv.model.SearchProgress;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteGroupOrderStore;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.ui.activity.FolderActivity;
import com.fongmi.android.tv.ui.activity.HistoryResumeCoordinator;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.CollectAdapter;
import com.fongmi.android.tv.ui.adapter.SearchAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.ui.dialog.SliderNumberDialog;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SearchGridLayout;
import com.fongmi.android.tv.utils.SearchPageState;
import com.fongmi.android.tv.utils.SearchResultFilter;
import com.fongmi.android.tv.utils.SearchSourceVisibility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CollectFragment extends BaseFragment implements MenuProvider, CollectAdapter.OnClickListener, SearchAdapter.OnClickListener, CustomScroller.Callback {

    private static final int MENU_GROUP_ALL = 1;
    private static final int MENU_GROUP_OFFSET = 100;
    private static final int GROUP_POPUP_ITEM_HEIGHT = 44;
    private static final int GROUP_POPUP_MAX_ITEMS = 8;
    private static final int GRID_ITEM_MARGIN_DP = 4;
    private static final int GRID_TOP_PADDING_DP = 8;
    private static final int GRID_MIN_ITEM_WIDTH_DP = 96;
    private static final int SOURCE_ROW_HEIGHT_DP = 56;

    private FragmentCollectBinding mBinding;
    private CollectAdapter mCollectAdapter;
    private SearchAdapter mSearchAdapter;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private List<Site> mSites;
    private List<String> mGroups;
    private final List<Collect> mAllCollectItems;
    private final Map<String, List<Vod>> mVisibleItems;
    private final Map<String, Integer> mSiteOrder;
    private String mFilterGroup;
    private int mSimilarity;
    private boolean mSearchCompleted;
    private final SearchPageState mPaging;
    private PopupWindow groupPopup;
    private int collectWidth;

    public CollectFragment() {
        mAllCollectItems = new ArrayList<>();
        mVisibleItems = new HashMap<>();
        mSiteOrder = new HashMap<>();
        mPaging = new SearchPageState();
        mFilterGroup = "";
    }

    public static CollectFragment newInstance(String keyword) {
        return newInstance(keyword, null, "");
    }

    public static CollectFragment newInstance(String keyword, String siteKey) {
        return newInstance(keyword, siteKey, "", null, null);
    }

    public static CollectFragment newInstance(String keyword, String siteKey, String group) {
        return newInstance(keyword, siteKey, group, null, null);
    }

    public static CollectFragment newInstance(String keyword, String siteKey, String group, String pic, String wallPic) {
        return newInstance(keyword, siteKey, group, pic, wallPic, -1, null, -1);
    }

    public static CollectFragment newInstance(String keyword, String siteKey, String group, String pic, String wallPic, int historyResumeCid, String historyResumeKey, int historyResumeTargetCid) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        args.putString("siteKey", siteKey);
        args.putString("group", group);
        args.putString("pic", pic);
        args.putString("wallPic", wallPic);
        args.putInt("historyResumeCid", historyResumeCid);
        args.putString("historyResumeKey", historyResumeKey);
        args.putInt("historyResumeTargetCid", historyResumeTargetCid);
        CollectFragment fragment = new CollectFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private int getHistoryResumeCid() {
        return getArguments().getInt("historyResumeCid", -1);
    }

    private String getHistoryResumeKey() {
        return getArguments().getString("historyResumeKey");
    }

    private int getHistoryResumeTargetCid() {
        return getArguments().getInt("historyResumeTargetCid", -1);
    }

    private boolean isHistoryResume() {
        return getHistoryResumeCid() >= 0 && getHistoryResumeTargetCid() >= 0 && !TextUtils.isEmpty(getHistoryResumeKey());
    }

    private String getKeyword() {
        return getArguments().getString("keyword");
    }

    private String getSiteKey() {
        return getArguments().getString("siteKey");
    }

    private String getSearchGroup() {
        String group = getArguments().getString("group");
        return group == null ? "" : group;
    }

    private boolean isSiteSearch() {
        return !TextUtils.isEmpty(getSiteKey());
    }

    private boolean isGroupSearch() {
        return !TextUtils.isEmpty(getSearchGroup());
    }

    private String getPic() {
        return getArguments().getString("pic");
    }

    private String getWallPic() {
        return getArguments().getString("wallPic");
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initMenu() {
        if (isHidden()) return;
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        activity.setSupportActionBar(mBinding.toolbar);
        activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        activity.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        activity.setTitle(getTitleText());
    }

    private String getTitleText() {
        if (isSiteSearch()) return getString(com.fongmi.android.tv.R.string.search_result_current, getKeyword());
        if (isGroupSearch()) return getString(com.fongmi.android.tv.R.string.search_result_group, getSearchGroup(), getKeyword());
        return getString(com.fongmi.android.tv.R.string.search_result_all, getKeyword());
    }

    @Override
    protected void initView() {
        mScroller = new CustomScroller(this);
        mSimilarity = Setting.getSearchSimilarity();
        setSites();
        setWidth();
        setRecyclerView();
        setViewModel();
        search();
    }

    @Override
    protected void initEvent() {
        mBinding.toolbar.setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putBoolean("edit", true);
            getParentFragmentManager().setFragmentResult("result", result);
            getParentFragmentManager().popBackStack();
        });
    }

    private void setRecyclerView() {
        mBinding.collect.setItemAnimator(null);
        mBinding.collect.setHasFixedSize(true);
        mBinding.collect.setAdapter(mCollectAdapter = new CollectAdapter(this, isSearchLandscape()));
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.addOnScrollListener(mScroller);
        mBinding.recycler.setAdapter(mSearchAdapter = new SearchAdapter(this));
        setResultLayout(false);
        mBinding.recycler.post(() -> setResultLayout(false));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class).init();
        mViewModel.getSearch().observe(this, this::setCollect);
        mViewModel.getSearchProgress().observe(this, this::setSearchProgress);
        mViewModel.getResult().observe(this, this::setSearch);
    }

    private void setSites() {
        String siteKey = getSiteKey();
        String group = getSearchGroup();
        mSites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) {
            if (!site.isSearchable()) continue;
            if (!TextUtils.isEmpty(siteKey) && !site.getKey().equals(siteKey)) continue;
            if (!TextUtils.isEmpty(group) && !site.inGroup(group)) continue;
            mSites.add(site);
        }
        // 固定模式严格按配置顺序,跳过健康度排序
        if (Setting.getSearchResultSort() != 1) SiteHealthStore.sortSites(mSites);
        mSiteOrder.clear();
        for (int i = 0; i < mSites.size(); i++) mSiteOrder.put(mSites.get(i).getKey(), i);
        mGroups = isSiteSearch() || isGroupSearch() ? new ArrayList<>() : SiteGroupOrderStore.sort(Site.getGroups(mSites));
    }

    private void setWidth() {
        int width = 0;
        int space = ResUtil.dp2px(48);
        int maxWidth = ResUtil.getScreenWidth() / 2 - ResUtil.dp2px(40);
        for (Site site : mSites) width = Math.max(width, ResUtil.getTextWidth(site.getDisplayName(), 14));
        int contentWidth = width + space;
        int minWidth = ResUtil.dp2px(120);
        collectWidth = Math.max(minWidth, Math.min(contentWidth, maxWidth));
    }

    private boolean isSearchLandscape() {
        return Setting.getSearchUi() == 0;
    }

    private void setSearchLayout() {
        boolean horizontal = isSearchLandscape();
        mCollectAdapter.setHorizontal(horizontal);
        mBinding.content.setOrientation(horizontal ? LinearLayoutCompat.VERTICAL : LinearLayoutCompat.HORIZONTAL);

        LinearLayoutCompat.LayoutParams collectParams = (LinearLayoutCompat.LayoutParams) mBinding.collect.getLayoutParams();
        collectParams.width = horizontal ? ViewGroup.LayoutParams.MATCH_PARENT : collectWidth;
        collectParams.height = horizontal ? ResUtil.dp2px(SOURCE_ROW_HEIGHT_DP) : ViewGroup.LayoutParams.MATCH_PARENT;
        collectParams.weight = 0;
        collectParams.topMargin = horizontal ? 0 : -ResUtil.dp2px(8);
        mBinding.collect.setLayoutParams(collectParams);
        setCollectLayoutManager(horizontal);
        mBinding.collect.setPadding(ResUtil.dp2px(8), 0, horizontal ? ResUtil.dp2px(8) : 0, ResUtil.dp2px(8));

        LinearLayoutCompat.LayoutParams resultParams = (LinearLayoutCompat.LayoutParams) mBinding.resultContainer.getLayoutParams();
        resultParams.width = horizontal ? ViewGroup.LayoutParams.MATCH_PARENT : 0;
        resultParams.height = horizontal ? 0 : ViewGroup.LayoutParams.MATCH_PARENT;
        resultParams.weight = 1;
        mBinding.resultContainer.setLayoutParams(resultParams);
    }

    private void setCollectLayoutManager(boolean horizontal) {
        int orientation = horizontal ? LinearLayoutManager.HORIZONTAL : LinearLayoutManager.VERTICAL;
        if (mBinding.collect.getLayoutManager() instanceof LinearLayoutManager manager && manager.getOrientation() == orientation) return;
        mBinding.collect.setLayoutManager(new LinearLayoutManager(requireContext(), orientation, false));
    }

    private void search() {
        mSearchCompleted = false;
        mPaging.clear();
        mAllCollectItems.clear();
        mVisibleItems.clear();
        mAllCollectItems.add(Collect.all());
        if (Setting.getSearchResultSort() == 1) {
            for (Site site : mSites) mAllCollectItems.add(new Collect(site, new ArrayList<>()));
        }
        applyFilters("all", true);
        if (!mSites.isEmpty()) mViewModel.searchContent(mSites, getKeyword(), false);
    }

    private int getCount() {
        int column = Setting.getSearchColumn();
        if (column == 0) return (ResUtil.isPad() || ResUtil.isLand(requireActivity())) ? 2 : 1;
        return Math.max(1, Math.min(column, 2));
    }

    private boolean isGrid() {
        return getCount() == 2;
    }

    private int getSpanCount() {
        if (!isGrid()) return 1;
        int column = Product.getColumn(requireActivity());
        int targetWidth = Product.getSpec(requireActivity(), column)[0];
        int available = getResultWidth() - getResultPadding();
        int margin = ResUtil.dp2px(GRID_ITEM_MARGIN_DP);
        int minWidth = ResUtil.dp2px(GRID_MIN_ITEM_WIDTH_DP);
        return SearchGridLayout.resolveSpanCount(column, targetWidth, available, minWidth, margin);
    }

    private int getResultWidth() {
        int width = mBinding.recycler.getWidth();
        return width > 0 ? width : ResUtil.getScreenWidth(requireActivity()) - (isSearchLandscape() ? 0 : collectWidth);
    }

    private int getResultPadding() {
        return mBinding.recycler.getPaddingStart() + mBinding.recycler.getPaddingEnd();
    }

    private int[] getGridSize() {
        int span = getSpanCount();
        int margin = ResUtil.dp2px(GRID_ITEM_MARGIN_DP);
        int width = SearchGridLayout.resolveItemWidth(getResultWidth(), getResultPadding(), span, margin);
        return new int[]{width, (int) (width / 0.75f), margin};
    }

    private void setResultLayout(boolean scrollTop) {
        setWidth();
        setSearchLayout();
        setResultPadding();
        int span = getSpanCount();
        ((GridLayoutManager) (mBinding.recycler.getLayoutManager())).setSpanCount(span);
        mSearchAdapter.setGrid(isGrid(), getGridSize());
        if (scrollTop) mBinding.recycler.scrollToPosition(0);
    }

    private void setResultPadding() {
        int top = isGrid() ? ResUtil.dp2px(GRID_TOP_PADDING_DP) : 0;
        mBinding.recycler.setPadding(mBinding.recycler.getPaddingStart(), top, mBinding.recycler.getPaddingEnd(), mBinding.recycler.getPaddingBottom());
    }

    private void onColumnToggle() {
        Setting.putSearchColumn(Setting.getSearchColumn() == 1 ? 2 : 1);
        setResultLayout(true);
        requireActivity().invalidateOptionsMenu();
    }

    private void setCollect(Result result) {
        if (result == null || result.getList().isEmpty()) return;
        String activeSiteKey = getActiveSiteKey();
        List<Vod> items = new ArrayList<>(result.getList());
        Collect raw = addMasterCollect(items);
        raw.setPage(mPaging.getPage(raw.getSite().getKey()));
        mPaging.recordInitial(raw.getSite().getKey(), result.getPageCount(), SearchPageState.pageToken(items));
        boolean visiblePageHasItems = appendVisibleItems(raw.getSite().getKey(), items);
        if (matchFilter(raw.getSite())) {
            String pageSiteKey = raw.getSite().getKey();
            applyFilters(activeSiteKey, false, () -> {
                maybeLoadNextPage(pageSiteKey, true, visiblePageHasItems);
                if (!pageSiteKey.equals(getActiveSiteKey())) maybeLoadSelectedPage();
            });
        }
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

    private boolean appendVisibleItems(String siteKey, List<Vod> items) {
        List<Vod> visible = SearchResultFilter.filter(items, getKeyword(), mSimilarity);
        mVisibleItems.computeIfAbsent(siteKey, key -> new ArrayList<>()).addAll(visible);
        return !visible.isEmpty();
    }

    private void rebuildVisibleItems() {
        mVisibleItems.clear();
        for (int i = 1; i < mAllCollectItems.size(); i++) {
            Collect raw = mAllCollectItems.get(i);
            mVisibleItems.put(raw.getSite().getKey(), SearchResultFilter.filter(raw.getList(), getKeyword(), mSimilarity));
        }
    }

    private List<Vod> getVisibleItems(String siteKey) {
        List<Vod> visible = mVisibleItems.get(siteKey);
        return visible == null ? new ArrayList<>() : new ArrayList<>(visible);
    }

    private Collect findCollect(List<Collect> items, String siteKey) {
        for (Collect item : items) if (item.getSite().getKey().equals(siteKey)) return item;
        return null;
    }

    private boolean matchFilter(Site site) {
        return TextUtils.isEmpty(mFilterGroup) || site.inGroup(mFilterGroup);
    }

    private void setSearchProgress(SearchProgress progress) {
        if (progress == null) return;
        mCollectAdapter.setProgress(progress.current(), progress.total());
        mSearchCompleted = progress.total() > 0 && progress.current() >= progress.total();
        if (mSearchCompleted && mCollectAdapter.getItemCount() > 0) updateEmptyState(mCollectAdapter.getActivated());
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
        if (!completion.accepted()) {
            if (mCollectAdapter.getItemCount() > 0) updateEmptyState(mCollectAdapter.getActivated());
            if (!completion.siteKey().equals(getActiveSiteKey())) maybeLoadSelectedPage();
            return;
        }
        String activeSiteKey = getActiveSiteKey();
        List<Vod> items = new ArrayList<>(result.getList());
        raw = addMasterCollect(items);
        String pageSiteKey = raw.getSite().getKey();
        raw.setPage(mPaging.getPage(pageSiteKey));
        boolean visiblePageHasItems = appendVisibleItems(pageSiteKey, items);
        if (matchFilter(raw.getSite())) {
            applyFilters(activeSiteKey, false, () -> {
                maybeLoadNextPage(pageSiteKey, true, visiblePageHasItems);
                if (!pageSiteKey.equals(getActiveSiteKey())) maybeLoadSelectedPage();
            });
        } else maybeLoadSelectedPage();
    }

    @Override
    public void onItemClick(int position, Collect item) {
        mCollectAdapter.setSelected(position);
        restoreScroller(item.getSite().getKey());
        mSearchAdapter.setItems(new ArrayList<>(item.getList()), () -> {
            if (!item.getSite().getKey().equals(getActiveSiteKey())) return;
            Collect current = mCollectAdapter.getActivated();
            mBinding.recycler.scrollToPosition(0);
            updateEmptyState(current);
            maybeLoadNextPage(current.getSite().getKey(), hasRawResults(current.getSite().getKey()), !current.getList().isEmpty());
        });
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isFolder()) {
            if (isHistoryResume()) FolderActivity.start(requireActivity(), item.getSiteKey(), Result.folder(item), getHistoryResumeCid(), getHistoryResumeKey(), getHistoryResumeTargetCid());
            else FolderActivity.start(requireActivity(), item.getSiteKey(), Result.folder(item));
            return;
        }
        String pic = item.getPic().isEmpty() ? getPic() : item.getPic();
        if (isHistoryResume()) HistoryResumeCoordinator.openSelected(requireActivity(), getHistoryResumeCid(), getHistoryResumeKey(), getHistoryResumeTargetCid(), item);
        else VideoActivity.collect(requireActivity(), item.getSiteKey(), item.getId(), item.getName(), pic, getWallPic(), getKeyword());
    }

    @Override
    public boolean onLoadMore(String page) {
        Collect activated = mCollectAdapter.getActivated();
        String siteKey = activated.getSite().getKey();
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
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.menu_collect, menu);
    }

    private String getSimilarityFilterTitle() {
        if (mSimilarity == 0) return getString(R.string.search_filter_similarity_unlimited);
        if (mSimilarity == 100) return getString(R.string.search_filter_similarity_exact);
        return getString(R.string.search_filter_similarity_value, mSimilarity);
    }

    @Override
    public void onPrepareMenu(@NonNull Menu menu) {
        MenuItem group = menu.findItem(R.id.action_group);
        if (group != null) {
            group.setVisible(canFilterGroup());
            group.setTitle(TextUtils.isEmpty(mFilterGroup) ? getString(R.string.search_scope_all) : mFilterGroup);
        }
        MenuItem similarity = menu.findItem(R.id.action_similarity);
        if (similarity != null) similarity.setTitle(getSimilarityFilterTitle());
        MenuItem item = menu.findItem(R.id.action_column);
        if (item == null) return;
        Drawable icon = ContextCompat.getDrawable(requireContext(), getCount() == 1 ? R.drawable.ic_site_double_column : R.drawable.ic_site_single_column);
        if (icon == null) return;
        icon = icon.mutate();
        icon.setTint(Color.WHITE);
        item.setIcon(icon);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) requireActivity().getOnBackPressedDispatcher().onBackPressed();
        if (menuItem.getItemId() == R.id.action_group) {
            onGroupFilter();
            return true;
        }
        if (menuItem.getItemId() == R.id.action_similarity) {
            onSimilarityFilter();
            return true;
        }
        if (menuItem.getItemId() == R.id.action_column) {
            onColumnToggle();
            return true;
        }
        return true;
    }

    private boolean canFilterGroup() {
        return !isSiteSearch() && !isGroupSearch() && mGroups != null && !mGroups.isEmpty();
    }

    private void onSimilarityFilter() {
        SliderNumberDialog.show(requireActivity(), R.string.search_filter_similarity, mSimilarity, 0, 100, "%", R.string.search_filter_similarity_hint, value -> {
            mSimilarity = value;
            Setting.putSearchSimilarity(value);
            rebuildVisibleItems();
            applyFilters(getActiveSiteKey());
            requireActivity().invalidateOptionsMenu();
        });
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
        mScroller.checkMore(null);
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

    private void onGroupFilter() {
        if (!canFilterGroup()) return;
        View anchor = mBinding.toolbar.findViewById(com.fongmi.android.tv.R.id.action_group);
        showGroupPopup(anchor == null ? mBinding.toolbar : anchor);
    }

    private void showGroupPopup(View anchor) {
        if (groupPopup != null) groupPopup.dismiss();
        int width = getGroupPopupWidth();
        int height = getGroupPopupHeight();
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setBackground(getGroupPopupBackground());
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayoutCompat content = new LinearLayoutCompat(requireContext());
        content.setOrientation(LinearLayoutCompat.VERTICAL);
        content.setPadding(0, ResUtil.dp2px(6), 0, ResUtil.dp2px(6));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addGroupPopupItem(content, getString(com.fongmi.android.tv.R.string.search_scope_all), MENU_GROUP_ALL);
        for (int i = 0; i < mGroups.size(); i++) addGroupPopupItem(content, mGroups.get(i), MENU_GROUP_OFFSET + i);
        groupPopup = new PopupWindow(scroll, width, height, true);
        groupPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        groupPopup.setOutsideTouchable(true);
        groupPopup.setElevation(ResUtil.dp2px(6));
        groupPopup.showAsDropDown(anchor, anchor.getWidth() - width, 0);
    }

    private GradientDrawable getGroupPopupBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(ResUtil.dp2px(6));
        return drawable;
    }

    private int getGroupPopupWidth() {
        int width = ResUtil.getTextWidth(getString(com.fongmi.android.tv.R.string.search_scope_all), 16);
        for (String group : mGroups) width = Math.max(width, ResUtil.getTextWidth(group, 16));
        int contentWidth = width + ResUtil.dp2px(36);
        int maxWidth = ResUtil.getScreenWidth(requireContext()) - ResUtil.dp2px(32);
        return Math.min(contentWidth, maxWidth);
    }

    private int getGroupPopupHeight() {
        int itemHeight = ResUtil.dp2px(GROUP_POPUP_ITEM_HEIGHT);
        int padding = ResUtil.dp2px(12);
        int contentHeight = (mGroups.size() + 1) * itemHeight + padding;
        int maxHeight = Math.min(ResUtil.getScreenHeight(requireContext()) - mBinding.toolbar.getHeight() - ResUtil.dp2px(32), GROUP_POPUP_MAX_ITEMS * itemHeight + padding);
        return Math.min(contentHeight, Math.max(itemHeight + padding, maxHeight));
    }

    private void addGroupPopupItem(LinearLayoutCompat content, String text, int itemId) {
        MaterialTextView view = new MaterialTextView(requireContext());
        view.setText(text);
        view.setSingleLine(true);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        view.setTextColor(0xFF202124);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        view.setPadding(ResUtil.dp2px(18), 0, ResUtil.dp2px(18), 0);
        view.setBackgroundResource(getSelectableItemBackground());
        view.setOnClickListener(v -> {
            if (groupPopup != null) groupPopup.dismiss();
            onGroupFilterSelected(itemId);
        });
        content.addView(view, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(GROUP_POPUP_ITEM_HEIGHT)));
    }

    private int getSelectableItemBackground() {
        TypedValue value = new TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
    }

    private boolean onGroupFilterSelected(int itemId) {
        if (itemId == MENU_GROUP_ALL) {
            setFilterGroup("");
        } else if (itemId >= MENU_GROUP_OFFSET) {
            int index = itemId - MENU_GROUP_OFFSET;
            if (index >= 0 && index < mGroups.size()) setFilterGroup(mGroups.get(index));
        }
        return true;
    }

    private void setFilterGroup(String group) {
        mFilterGroup = group == null ? "" : group;
        applyFilters(getActiveSiteKey());
        requireActivity().invalidateOptionsMenu();
    }

    private String getActiveSiteKey() {
        if (mCollectAdapter == null || mCollectAdapter.getItemCount() == 0) return "all";
        return mCollectAdapter.getActivated().getSite().getKey();
    }

    private void applyFilters(String activeSiteKey) {
        applyFilters(activeSiteKey, true, null);
    }

    private void applyFilters(String activeSiteKey, boolean scrollTop) {
        applyFilters(activeSiteKey, scrollTop, null);
    }

    private void applyFilters(String activeSiteKey, boolean scrollTop, Runnable after) {
        List<Collect> items = getFilteredCollectItems(activeSiteKey);
        mCollectAdapter.setItems(items, () -> {
            mCollectAdapter.setSelected(mCollectAdapter.getPosition());
            Collect current = mCollectAdapter.getActivated();
            mSearchAdapter.setItems(new ArrayList<>(current.getList()), () -> {
                if (scrollTop) mBinding.recycler.scrollToPosition(0);
                Collect latest = mCollectAdapter.getActivated();
                updateEmptyState(latest);
                String siteKey = latest.getSite().getKey();
                Collect raw = findCollect(mAllCollectItems, siteKey);
                maybeLoadNextPage(siteKey, raw != null && !raw.getList().isEmpty(), !latest.getList().isEmpty());
                if (after != null) after.run();
            });
            restoreScroller(current.getSite().getKey());
        });
    }

    private List<Collect> getFilteredCollectItems(String activeSiteKey) {
        List<Collect> items = new ArrayList<>();
        Collect all = Collect.all();
        all.setSelected("all".equals(activeSiteKey));
        items.add(all);
        for (int i = 1; i < mAllCollectItems.size(); i++) {
            Collect raw = mAllCollectItems.get(i);
            if (!matchFilter(raw.getSite())) continue;
            String siteKey = raw.getSite().getKey();
            List<Vod> visible = getVisibleItems(siteKey);
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

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) requireActivity().removeMenuProvider(this);
        else initMenu();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mViewModel.stopSearch();
        mPaging.clear();
        if (groupPopup != null) groupPopup.dismiss();
        SiteHealthStore.flush();
        mAllCollectItems.clear();
        mVisibleItems.clear();
        requireActivity().removeMenuProvider(this);
    }
}
