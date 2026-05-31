package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.VerticalGridView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ActivityCollectBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.CollectAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomViewPager;
import com.fongmi.android.tv.ui.fragment.CollectFragment;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

public class CollectActivity extends BaseActivity implements CollectAdapter.OnClickListener {

    private ActivityCollectBinding mBinding;
    private CollectAdapter mAdapter;
    private SiteViewModel mViewModel;
    private List<Site> mSites;
    private View mOldView;
    private boolean mKeepActionFocus;

    public static void start(Activity activity, String keyword) {
        start(activity, keyword, "");
    }

    public static void start(Activity activity, String keyword, String siteKey) {
        Intent intent = new Intent(activity, CollectActivity.class);
        intent.putExtra("keyword", keyword);
        if (!TextUtils.isEmpty(siteKey)) intent.putExtra("siteKey", siteKey);
        activity.startActivity(intent);
    }

    private CollectFragment getFragment() {
        return getFragment(getPager().getCurrentItem());
    }

    private CollectFragment getFragment(int position) {
        return (CollectFragment) getPager().getAdapter().instantiateItem(getPager(), position);
    }

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    private String getSiteKey() {
        String siteKey = getIntent().getStringExtra("siteKey");
        return siteKey == null ? "" : siteKey;
    }

    private boolean isSiteSearch() {
        return !TextUtils.isEmpty(getSiteKey());
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getIntent().putExtras(intent);
        mAdapter.clear();
        setSites();
        setPager();
        search();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setRecyclerView();
        setViewModel();
        saveKeyword();
        setSites();
        setPager();
        search();
    }

    @Override
    protected void initEvent() {
        mBinding.searchColumn.setOnClickListener(this::setSearchColumn);
        mBinding.searchUi.setOnClickListener(this::setSearchUi);
        mBinding.searchColumn.setOnKeyListener(this::focusRecyclerOnDown);
        mBinding.searchUi.setOnKeyListener(this::focusRecyclerOnDown);
        mBinding.horiPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                getRecycler().setSelectedPosition(position);
                getRecycler().requestFocus();
            }
        });
        mBinding.vertPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                getRecycler().setSelectedPosition(position);
                getRecycler().requestFocus();
            }
        });
        mBinding.horiRecycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChildSelected(child);
            }
        });
        mBinding.horiRecycler.setOnKeyListener(this::focusResultOnDown);
        mBinding.vertRecycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChildSelected(child);
            }
        });
        mBinding.vertRecycler.setOnKeyListener(this::focusResultOnDown);
    }

    private boolean focusRecyclerOnDown(View view, int keyCode, KeyEvent event) {
        if (!KeyUtil.isActionDown(event) || !KeyUtil.isDownKey(event)) return false;
        getRecycler().setSelectedPosition(0);
        getRecycler().requestFocus();
        return true;
    }

    private boolean focusResultOnDown(View view, int keyCode, KeyEvent event) {
        if (!KeyUtil.isActionDown(event) || !KeyUtil.isDownKey(event)) return false;
        getFragment().requestResultFocus();
        return true;
    }

    private void setRecyclerView() {
        mAdapter = new CollectAdapter(this);
        setupRecycler(mBinding.horiRecycler);
        setupRecycler(mBinding.vertRecycler);
        applySearchUi();
    }

    private void setupRecycler(BaseGridView recycler) {
        if (recycler instanceof HorizontalGridView view) {
            view.setHorizontalSpacing(ResUtil.dp2px(16));
            view.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        } else if (recycler instanceof VerticalGridView view) {
            view.setVerticalSpacing(ResUtil.dp2px(16));
        }
        recycler.setAdapter(mAdapter);
    }

    private void applySearchUi() {
        mBinding.horiLayout.setVisibility(Setting.getSearchUi() == 0 ? View.VISIBLE : View.GONE);
        mBinding.vertLayout.setVisibility(Setting.getSearchUi() == 1 ? View.VISIBLE : View.GONE);
        mBinding.searchColumn.setImageResource(getSearchColumnIcon());
        mBinding.searchColumn.setContentDescription(getSearchColumnAction());
        mBinding.searchUi.setImageResource(getSearchUiIcon());
        mBinding.searchUi.setContentDescription(getSearchUiAction());
    }

    private CustomViewPager getPager() {
        return Setting.getSearchUi() == 0 ? mBinding.horiPager : mBinding.vertPager;
    }

    private BaseGridView getRecycler() {
        return Setting.getSearchUi() == 0 ? mBinding.horiRecycler : mBinding.vertRecycler;
    }

    private String getSearchUi() {
        return getResources().getStringArray(R.array.select_search_ui)[Setting.getSearchUi()];
    }

    private String getSearchUiAction() {
        return getString(R.string.setting_search_ui) + ": " + getSearchUiTarget();
    }

    private String getSearchUiTarget() {
        return getResources().getStringArray(R.array.select_search_ui)[(Setting.getSearchUi() + 1) % getResources().getStringArray(R.array.select_search_ui).length];
    }

    private int getSearchUiIcon() {
        return Setting.getSearchUi() == 0 ? R.drawable.ic_search_ui_vertical : R.drawable.ic_search_ui_horizontal;
    }

    private String getSearchColumnAction() {
        return getString(R.string.setting_search_column) + ": " + getSearchColumnTarget();
    }

    private String getSearchColumnTarget() {
        return getResources().getStringArray(R.array.select_search_column)[isSearchList() ? 0 : 1];
    }

    private int getSearchColumnIcon() {
        return isSearchList() ? R.drawable.ic_site_grid : R.drawable.ic_site_list;
    }

    private boolean isSearchList() {
        return Setting.getSearchColumn() == 1;
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getSearch().observe(this, result -> {
            if (result.getList().isEmpty()) return;
            Collect all = getAll();
            if (all != null) all.getList().addAll(result.getList());
            getFragment(0).addVideo(result.getList());
            mAdapter.add(Collect.create(result.getList()));
            getPager().getAdapter().notifyDataSetChanged();
        });
    }

    private void saveKeyword() {
        List<String> items = Setting.getKeyword().isEmpty() ? new ArrayList<>() : App.gson().fromJson(Setting.getKeyword(), new TypeToken<List<String>>() {}.getType());
        items.remove(getKeyword());
        items.add(0, getKeyword());
        if (items.size() > 9) items.remove(9);
        Setting.putKeyword(App.gson().toJson(items));
    }

    private void setSites() {
        if (!isSiteSearch()) {
            mSites = VodConfig.get().getSites().stream().filter(Site::isSearchable).toList();
            return;
        }
        Site site = VodConfig.get().getSite(getSiteKey());
        mSites = site.isSearchable() ? List.of(site) : List.of();
    }

    private void setPager() {
        resetPagerFragments();
        getPager().setAdapter(new PageAdapter(getSupportFragmentManager()));
    }

    private void resetPagerFragments() {
        FragmentManager fm = getSupportFragmentManager();
        var transaction = fm.beginTransaction();
        boolean changed = false;
        for (Fragment fragment : fm.getFragments()) {
            if (fragment instanceof CollectFragment) {
                transaction.remove(fragment);
                changed = true;
            }
        }
        if (changed) transaction.commitNowAllowingStateLoss();
    }

    private void setSearchUi(View view) {
        int position = Math.max(0, getRecycler().getSelectedPosition());
        mKeepActionFocus = true;
        Setting.putSearchUi((Setting.getSearchUi() + 1) % getResources().getStringArray(R.array.select_search_ui).length);
        applySearchUi();
        rebuildPager(position, view);
    }

    private void setSearchColumn(View view) {
        Setting.putSearchColumn(isSearchList() ? 0 : 1);
        applySearchUi();
        refreshFragments();
        view.requestFocus();
    }

    private Collect getAll() {
        return mAdapter.getItemCount() == 0 ? null : mAdapter.get(0);
    }

    private void rebuildPager(int position, View focus) {
        setPager();
        mOldView = null;
        App.post(() -> {
            if (mAdapter.getItemCount() == 0) return;
            int next = Math.min(position, mAdapter.getItemCount() - 1);
            getRecycler().setSelectedPosition(next);
            getPager().setCurrentItem(next, false);
            if (focus != null) focus.requestFocus();
            else getRecycler().requestFocus();
        }, 100);
    }

    private void refreshFragments() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof CollectFragment collect) collect.setColumn();
        }
    }

    private void search() {
        if (mSites.isEmpty()) {
            if (isSiteSearch()) Notify.show(R.string.detail_site_not_searchable);
            return;
        }
        mAdapter.add(Collect.all());
        getPager().getAdapter().notifyDataSetChanged();
        mBinding.result.setText(getString(isSiteSearch() ? R.string.search_result_current : R.string.collect_result, getKeyword()));
        mViewModel.searchContent(mSites, getKeyword(), false);
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child) {
        if (mOldView != null) mOldView.setSelected(false);
        if ((mOldView = child != null ? child.itemView : null) == null) return;
        mOldView.setSelected(true);
        App.post(mRunnable, 100);
    }

    @Override
    public void onItemClick(int position, Collect item) {
        if (position < 0 || position >= mAdapter.getItemCount()) return;
        getRecycler().setSelectedPosition(position);
        getPager().setCurrentItem(position, false);
        getRecycler().requestFocus();
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            if (mKeepActionFocus) {
                mKeepActionFocus = false;
                return;
            }
            getPager().setCurrentItem(getRecycler().getSelectedPosition());
        }
    };

    @Override
    protected void onBackInvoked() {
        mViewModel.stopSearch();
        super.onBackInvoked();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return CollectFragment.newInstance(getKeyword(), mAdapter.get(position));
        }

        @Override
        public int getCount() {
            return mAdapter.getItemCount();
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        }

        @Nullable
        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void restoreState(@Nullable Parcelable state, @Nullable ClassLoader loader) {
        }
    }
}
