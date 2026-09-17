package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.FragmentEpisodeBinding;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;

import java.util.ArrayList;
import java.util.List;

public class EpisodeFragment extends BaseFragment implements EpisodeAdapter.OnClickListener {

    private FragmentEpisodeBinding mBinding;

    private int getSpanCount() {
        return getArguments().getInt("spanCount");
    }

    private ArrayList<Episode> getItems() {
        return getArguments().getParcelableArrayList("items");
    }

    private boolean useTmdbCard() {
        return getArguments().getBoolean("tmdbCard", false);
    }

    public static EpisodeFragment newInstance(int spanCount, List<Episode> items) {
        return newInstance(spanCount, items, false);
    }

    public static EpisodeFragment newInstance(int spanCount, List<Episode> items, boolean tmdbCard) {
        Bundle args = new Bundle();
        args.putInt("spanCount", spanCount);
        args.putParcelableArrayList("items", new ArrayList<>(items));
        args.putBoolean("tmdbCard", tmdbCard);
        EpisodeFragment fragment = new EpisodeFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentEpisodeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setRecyclerView();
    }

    private void setRecyclerView() {
        EpisodeAdapter adapter;
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setLayoutManager(new GridLayoutManager(getContext(), getSpanCount()));
        mBinding.recycler.addItemDecoration(new SpaceItemDecoration(getSpanCount(), 8));
        mBinding.recycler.setAdapter(adapter = new EpisodeAdapter(this, ViewType.GRID, getItems()));
        adapter.setUseTmdbCard(useTmdbCard());
        mBinding.recycler.scrollToPosition(adapter.getPosition());
    }

    @Override
    public void onItemClick(Episode item) {
        Bundle result = new Bundle();
        result.putParcelable("episode", item);
        getParentFragmentManager().setFragmentResult("result", result);
    }
}
