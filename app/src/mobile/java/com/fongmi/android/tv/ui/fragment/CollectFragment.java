package com.fongmi.android.tv.ui.fragment;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
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
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

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
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.ui.activity.FolderActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.CollectAdapter;
import com.fongmi.android.tv.ui.adapter.SearchAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.utils.MobileWindow;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SearchModeStore;

import java.util.ArrayList;
import java.util.List;

public class CollectFragment extends BaseFragment implements MenuProvider, CollectAdapter.OnClickListener, SearchAdapter.OnClickListener, CustomScroller.Callback {

    private static final int GRID_ITEM_MARGIN_DP = 4;
    private static final int GRID_TOP_PADDING_DP = 8;

    private FragmentCollectBinding mBinding;
    private CollectAdapter mCollectAdapter;
    private SearchAdapter mSearchAdapter;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private List<Site> mSites;
    private final List<Collect> mCollects = new ArrayList<>();
    private final List<Vod> mAllResults = new ArrayList<>();
    private int collectWidth;

    public static CollectFragment newInstance(String keyword) {
        return newInstance(keyword, null);
    }

    public static CollectFragment newInstance(String keyword, String siteKey) {
        return newInstance(keyword, siteKey, null, null);
    }

    public static CollectFragment newInstance(String keyword, String siteKey, String pic, String wallPic) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        args.putString("siteKey", siteKey);
        args.putString("pic", pic);
        args.putString("wallPic", wallPic);
        CollectFragment fragment = new CollectFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKeyword() {
        return getArguments().getString("keyword");
    }

    private String getSiteKey() {
        return getArguments().getString("siteKey");
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
        activity.setTitle(getKeyword());
    }

    @Override
    protected void initView() {
        mScroller = new CustomScroller(this);
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
        mBinding.collect.setAdapter(mCollectAdapter = new CollectAdapter(this));
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
        mSites = new ArrayList<>(SearchModeStore.filterSites(VodConfig.get().getSites(), getSiteKey()));
        SiteHealthStore.sortSites(mSites);
    }

    private void setWidth() {
        int width = 0;
        int space = ResUtil.dp2px(48);
        int maxWidth = ResUtil.getScreenWidth() / 2 - ResUtil.dp2px(40);
        for (Site site : mSites) width = Math.max(width, ResUtil.getTextWidth(site.getName(), 14));
        int contentWidth = width + space;
        int minWidth = ResUtil.dp2px(120);
        int finalWidth = Math.max(minWidth, Math.min(contentWidth, maxWidth));
        collectWidth = finalWidth;
        ViewGroup.LayoutParams params = mBinding.collect.getLayoutParams();
        params.width = finalWidth;
        mBinding.collect.setLayoutParams(params);
    }

    private void search() {
        if (mSites.isEmpty()) return;
        mCollects.clear();
        mAllResults.clear();
        mCollects.add(Collect.all());
        mCollectAdapter.setItems(new ArrayList<>(mCollects), () -> mViewModel.searchContent(mSites, getKeyword(), false));
    }

    private int getCount() {
        return Setting.getSearchColumn();
    }

    private boolean isGrid() {
        return getCount() == 2;
    }

    private int getSpanCount() {
        if (!isGrid()) return 1;
        if (!MobileWindow.isWide(requireActivity())) return 2;
        int column = Product.getColumn(requireActivity());
        int targetWidth = Product.getSpec(requireActivity(), column)[0];
        int available = getResultWidth() - getResultPadding();
        int span = targetWidth > 0 ? available / targetWidth : 2;
        return Math.max(2, Math.min(column, span));
    }

    private int getResultWidth() {
        int width = mBinding.recycler.getWidth();
        return width > 0 ? width : ResUtil.getScreenWidth(requireActivity()) - collectWidth;
    }

    private int getResultPadding() {
        return mBinding.recycler.getPaddingStart() + mBinding.recycler.getPaddingEnd();
    }

    private int[] getGridSize() {
        int span = getSpanCount();
        int margin = ResUtil.dp2px(GRID_ITEM_MARGIN_DP);
        int space = getResultPadding() + margin * 2 * span;
        int width = (getResultWidth() - space) / span;
        width = Math.max(ResUtil.dp2px(96), width);
        return new int[]{width, (int) (width / 0.75f), margin};
    }

    private void setResultLayout(boolean scrollTop) {
        setWidth();
        int span = getSpanCount();
        ((GridLayoutManager) (mBinding.recycler.getLayoutManager())).setSpanCount(span);
        setResultPadding();
        mSearchAdapter.setGrid(isGrid(), getGridSize());
        if (scrollTop) mBinding.recycler.scrollToPosition(0);
    }

    private void setResultPadding() {
        int top = isGrid() ? ResUtil.dp2px(GRID_TOP_PADDING_DP) : 0;
        mBinding.recycler.setPadding(mBinding.recycler.getPaddingStart(), top, mBinding.recycler.getPaddingEnd(), mBinding.recycler.getPaddingBottom());
    }

    private void onColumnToggle() {
        Setting.putSearchColumn(getCount() == 1 ? 2 : 1);
        setResultLayout(true);
        requireActivity().invalidateOptionsMenu();
    }

    private void setCollect(Result result) {
        if (result == null || result.getList().isEmpty()) return;
        List<Vod> items = new ArrayList<>(result.getList());
        mAllResults.addAll(items);
        mCollects.get(0).getList().addAll(items);
        mCollects.add(Collect.create(items));
        mCollectAdapter.setItems(new ArrayList<>(mCollects));
        if (mCollectAdapter.getPosition() == 0) mSearchAdapter.setItems(new ArrayList<>(mAllResults));
    }

    private void setSearchProgress(SearchProgress progress) {
        if (progress != null) mCollectAdapter.setProgress(progress.current(), progress.total());
    }

    private void setSearch(Result result) {
        if (result == null) return;
        mScroller.endLoading(result);
        boolean same = !result.getList().isEmpty() && mCollectAdapter.getActivated().getSite().equals(result.getVod().getSite());
        if (same) mCollectAdapter.getActivated().getList().addAll(result.getList());
        if (same) mSearchAdapter.setItems(new ArrayList<>(mCollectAdapter.getActivated().getList()));
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
        else {
            String pic = item.getPic().isEmpty() ? getPic() : item.getPic();
            VideoActivity.collect(requireActivity(), item.getSiteKey(), item.getId(), item.getName(), pic, getWallPic());
        }
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
        if (menuItem.getItemId() == R.id.action_column) onColumnToggle();
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
        SiteHealthStore.flush();
        requireActivity().removeMenuProvider(this);
    }
}
