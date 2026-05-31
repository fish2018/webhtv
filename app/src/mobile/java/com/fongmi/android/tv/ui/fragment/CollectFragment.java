package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentCollectBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.FolderActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.CollectAdapter;
import com.fongmi.android.tv.ui.adapter.SearchAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.List;

public class CollectFragment extends BaseFragment implements MenuProvider, CollectAdapter.OnClickListener, SearchAdapter.OnClickListener, CustomScroller.Callback {

    private FragmentCollectBinding mBinding;
    private CollectAdapter mCollectAdapter;
    private SearchAdapter mSearchAdapter;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private List<Site> mSites;

    public static CollectFragment newInstance(String keyword) {
        return newInstance(keyword, "");
    }

    public static CollectFragment newInstance(String keyword, String siteKey) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        args.putString("siteKey", siteKey);
        CollectFragment fragment = new CollectFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKeyword() {
        return getArguments().getString("keyword");
    }

    private String getSiteKey() {
        String siteKey = getArguments().getString("siteKey");
        return siteKey == null ? "" : siteKey;
    }

    private boolean isSiteSearch() {
        return !TextUtils.isEmpty(getSiteKey());
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
        activity.setTitle(isSiteSearch() ? getString(R.string.search_result_current, getKeyword()) : getKeyword());
    }

    @Override
    protected void initView() {
        mScroller = new CustomScroller(this);
        setRecyclerView();
        setViewModel();
        setSites();
        setCollectLayout();
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
        mBinding.collect.setAdapter(mCollectAdapter = new CollectAdapter(this, isHorizontalUi()));
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.addOnScrollListener(mScroller);
        mBinding.recycler.setAdapter(mSearchAdapter = new SearchAdapter(this));
        updateSpanCount();
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class).init();
        mViewModel.getSearch().observe(this, this::setCollect);
        mViewModel.getResult().observe(this, this::setSearch);
    }

    private void setSites() {
        if (!isSiteSearch()) {
            mSites = VodConfig.get().getSites().stream().filter(Site::isSearchable).toList();
            return;
        }
        Site site = VodConfig.get().getSite(getSiteKey());
        mSites = site.isSearchable() ? List.of(site) : List.of();
    }

    private void setCollectLayout() {
        boolean horizontal = isHorizontalUi();
        int gap = ResUtil.dp2px(8);
        mBinding.content.setOrientation(horizontal ? LinearLayoutCompat.VERTICAL : LinearLayoutCompat.HORIZONTAL);
        mBinding.collect.setLayoutManager(new LinearLayoutManager(requireActivity(), horizontal ? LinearLayoutManager.HORIZONTAL : LinearLayoutManager.VERTICAL, false));
        LinearLayoutCompat.LayoutParams collectParams = (LinearLayoutCompat.LayoutParams) mBinding.collect.getLayoutParams();
        LinearLayoutCompat.LayoutParams recyclerParams = (LinearLayoutCompat.LayoutParams) mBinding.recycler.getLayoutParams();
        collectParams.width = horizontal ? ViewGroup.LayoutParams.MATCH_PARENT : getCollectWidth();
        collectParams.height = horizontal ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
        collectParams.weight = 0;
        collectParams.topMargin = -gap;
        recyclerParams.width = horizontal ? ViewGroup.LayoutParams.MATCH_PARENT : 0;
        recyclerParams.height = horizontal ? 0 : ViewGroup.LayoutParams.MATCH_PARENT;
        recyclerParams.weight = 1;
        recyclerParams.topMargin = horizontal ? 0 : -gap;
        mBinding.collect.setPadding(gap, 0, horizontal ? gap : 0, horizontal ? 0 : gap);
        mBinding.recycler.setPadding(horizontal ? gap : 0, 0, gap, gap);
        mBinding.collect.setLayoutParams(collectParams);
        mBinding.recycler.setLayoutParams(recyclerParams);
    }

    private int getCollectWidth() {
        int width = 0;
        int space = ResUtil.dp2px(48);
        int maxWidth = ResUtil.getScreenWidth() / (getCount() + 1) - ResUtil.dp2px(40);
        for (Site site : mSites) width = Math.max(width, ResUtil.getTextWidth(site.getName(), 14));
        int contentWidth = width + space;
        int minWidth = ResUtil.dp2px(120);
        return Math.max(minWidth, Math.min(contentWidth, maxWidth));
    }

    private boolean isHorizontalUi() {
        return Setting.getSearchUi() == 0;
    }

    private String getSearchUi() {
        return getResources().getStringArray(R.array.select_search_ui)[Setting.getSearchUi()];
    }

    private String getSearchColumn() {
        return getResources().getStringArray(R.array.select_search_column)[Setting.getSearchColumn()];
    }

    private void setSearchUi() {
        int position = mCollectAdapter.getPosition();
        Setting.putSearchUi((Setting.getSearchUi() + 1) % getResources().getStringArray(R.array.select_search_ui).length);
        mCollectAdapter.setHorizontal(isHorizontalUi());
        setCollectLayout();
        mBinding.collect.post(() -> mBinding.collect.scrollToPosition(position));
        requireActivity().invalidateOptionsMenu();
    }

    private void setSearchColumn() {
        int column = Setting.getSearchColumn();
        int length = getResources().getStringArray(R.array.select_search_column).length;
        Setting.putSearchColumn((column + 1) % length);
        updateSpanCount();
        mBinding.recycler.post(() -> mBinding.recycler.scrollToPosition(0));
        requireActivity().invalidateOptionsMenu();
    }

    private void search() {
        if (mSites.isEmpty()) {
            if (isSiteSearch()) Notify.show(R.string.detail_site_not_searchable);
            return;
        }
        mCollectAdapter.setItems(List.of(Collect.all()), () -> mViewModel.searchContent(mSites, getKeyword(), false));
    }

    private int getCount() {
        if (Setting.getSearchColumn() > 0) return Setting.getSearchColumn();
        return ResUtil.isLand(requireActivity()) || ResUtil.isPad() ? 3 : 1;
    }

    private void updateSpanCount() {
        ((GridLayoutManager) (mBinding.recycler.getLayoutManager())).setSpanCount(getCount());
    }

    private void setCollect(Result result) {
        if (result == null || result.getList().isEmpty()) return;
        if (mCollectAdapter.getPosition() == 0) mSearchAdapter.addAll(result.getList());
        mCollectAdapter.add(Collect.create(result.getList()));
        mCollectAdapter.add(result.getList());
    }

    private void setSearch(Result result) {
        if (result == null) return;
        mScroller.endLoading(result);
        boolean same = !result.getList().isEmpty() && mCollectAdapter.getActivated().getSite().equals(result.getVod().getSite());
        if (same) mCollectAdapter.getActivated().getList().addAll(result.getList());
        if (same) mSearchAdapter.addAll(result.getList());
    }

    @Override
    public void onItemClick(int position, Collect item) {
        mSearchAdapter.setItems(item.getList(), () -> mBinding.recycler.scrollToPosition(0));
        mCollectAdapter.setSelected(position);
        mScroller.setPage(item.getPage());
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isFolder()) FolderActivity.start(requireActivity(), item.getSiteKey(), Result.folder(item));
        else VideoActivity.collect(requireActivity(), item.getSiteKey(), item.getId(), item.getName(), item.getPic());
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
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.menu_collect, menu);
    }

    @Override
    public void onPrepareMenu(@NonNull Menu menu) {
        menu.findItem(R.id.action_layout).setTitle(getSearchUi());
        menu.findItem(R.id.action_column).setTitle(getSearchColumn());
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) requireActivity().getOnBackPressedDispatcher().onBackPressed();
        if (menuItem.getItemId() == R.id.action_layout) setSearchUi();
        if (menuItem.getItemId() == R.id.action_column) setSearchColumn();
        return true;
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
        requireActivity().removeMenuProvider(this);
    }
}
