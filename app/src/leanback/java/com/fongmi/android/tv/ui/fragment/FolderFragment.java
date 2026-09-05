package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Cache;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.databinding.FragmentFolderBinding;
import com.fongmi.android.tv.ui.base.BaseFragment;

import java.util.HashMap;
import java.util.Optional;

public class FolderFragment extends BaseFragment {

    public interface FilterHost {

        void closeFilter();
    }

    public interface ScrollHeaderHost {

        int[] getScrollHeaderIds();

        void onScrollHeaderVisibilityChanged(boolean visible);
    }

    public interface CategoryEdgeHost {

        void onCategoryContentHorizontalEdge(Class item, int contentRow, boolean towardEnd);
    }

    private FragmentFolderBinding mBinding;
    private Boolean pendingFilterVisible;
    private Integer pendingContentRow;
    private Class mType;

    public static FolderFragment newInstance(String key, Class type) {
        return newInstance(key, type, -1, null, -1);
    }

    public static FolderFragment newInstance(String key, Class type, int historyResumeCid, String historyResumeKey, int historyResumeTargetCid) {
        Bundle args = new Bundle();
        args.putString("key", key);
        args.putParcelable("type", type);
        args.putInt("historyResumeCid", historyResumeCid);
        args.putString("historyResumeKey", historyResumeKey);
        args.putInt("historyResumeTargetCid", historyResumeTargetCid);
        FolderFragment fragment = new FolderFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKey() {
        return getArguments().getString("key");
    }

    public Class getType() {
        return getArguments().getParcelable("type");
    }

    public int getHistoryResumeCid() {
        return getArguments().getInt("historyResumeCid", -1);
    }

    public String getHistoryResumeKey() {
        return getArguments().getString("historyResumeKey");
    }

    public int getHistoryResumeTargetCid() {
        return getArguments().getInt("historyResumeTargetCid", -1);
    }

    private TypeFragment getChild() {
        return (TypeFragment) getChildFragmentManager().findFragmentById(R.id.container);
    }

    private FilterHost getParent() {
        return getActivity() instanceof FilterHost host ? host : null;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentFolderBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mType = getType();
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction().replace(R.id.container, TypeFragment.newInstance(getKey(), mType.getTypeId(), mType.getStyle(), getExtend(), mType.isFolder(), getHistoryResumeCid(), getHistoryResumeKey(), getHistoryResumeTargetCid()));
        transaction.runOnCommit(this::applyPendingFilter);
        transaction.runOnCommit(this::applyPendingContentFocus);
        transaction.commit();
    }

    private HashMap<String, String> getExtend() {
        HashMap<String, String> extend = new HashMap<>();
        for (Filter filter : Cache.get(mType)) if (filter.getInit() != null) extend.put(filter.getKey(), filter.getInit());
        return extend;
    }

    public void openFolder(String typeId, HashMap<String, String> extend) {
        TypeFragment next = TypeFragment.newInstance(getKey(), typeId, mType.getStyle(), extend, mType.isFolder(), getHistoryResumeCid(), getHistoryResumeKey(), getHistoryResumeTargetCid());
        FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        Optional.ofNullable(getParent()).ifPresent(FilterHost::closeFilter);
        Optional.ofNullable(getChild()).ifPresent(ft::hide);
        ft.add(R.id.container, next);
        ft.addToBackStack(null);
        ft.commit();
    }

    public void toggleFilter(boolean visible) {
        pendingFilterVisible = visible;
        applyPendingFilter();
    }

    private void applyPendingFilter() {
        if (pendingFilterVisible == null) return;
        TypeFragment child = getChild();
        if (child == null) return;
        child.toggleFilter(pendingFilterVisible);
        pendingFilterVisible = null;
    }

    public int[] getScrollHeaderIds() {
        return getActivity() instanceof ScrollHeaderHost host ? host.getScrollHeaderIds() : new int[]{R.id.recycler};
    }

    public void onScrollHeaderVisibilityChanged(boolean visible) {
        if (getActivity() instanceof ScrollHeaderHost host) host.onScrollHeaderVisibilityChanged(visible);
    }

    public void onContentHorizontalEdge(int contentRow, boolean towardEnd) {
        if (getChildFragmentManager().getBackStackEntryCount() > 0) return;
        if (getActivity() instanceof CategoryEdgeHost host) host.onCategoryContentHorizontalEdge(mType, contentRow, towardEnd);
    }

    public boolean requestContentFocus() {
        TypeFragment child = getChild();
        return child != null && child.requestContentFocus();
    }

    public void requestContentFocus(int contentRow) {
        pendingContentRow = Math.max(0, contentRow);
        applyPendingContentFocus();
    }

    private void applyPendingContentFocus() {
        if (pendingContentRow == null) return;
        TypeFragment child = getChild();
        if (child == null) return;
        child.requestContentFocus(pendingContentRow);
        pendingContentRow = null;
    }

    public void clearContentFocusRequest() {
        pendingContentRow = null;
        TypeFragment child = getChild();
        if (child != null) child.clearContentFocusRequest();
    }

    public void onRefresh() {
        Optional.ofNullable(getChild()).ifPresent(TypeFragment::onRefresh);
    }

    public boolean canBack() {
        return getChildFragmentManager().getBackStackEntryCount() > 0;
    }

    public void goBack() {
        getChildFragmentManager().popBackStack();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (mBinding != null && !isVisibleToUser) Optional.ofNullable(getChild()).ifPresent(f -> f.setUserVisibleHint(false));
    }
}
