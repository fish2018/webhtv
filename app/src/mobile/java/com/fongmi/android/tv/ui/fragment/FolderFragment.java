package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.databinding.FragmentFolderBinding;
import com.fongmi.android.tv.ui.base.BaseFragment;

import java.util.HashMap;
import java.util.Optional;

public class FolderFragment extends BaseFragment {

    private Class mType;

    public static FolderFragment newInstance(String key, Class type, int y) {
        return newInstance(key, type, y, -1, null, -1);
    }

    public static FolderFragment newInstance(String key, Class type, int y, int historyResumeCid, String historyResumeKey, int historyResumeTargetCid) {
        Bundle args = new Bundle();
        args.putInt("y", y);
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

    private int getY() {
        return getArguments().getInt("y");
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

    private VodFragment getParent() {
        return (VodFragment) getParentFragment();
    }

    private TypeFragment getChild() {
        return (TypeFragment) getChildFragmentManager().findFragmentById(R.id.container);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return FragmentFolderBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mType = getType();
        getChildFragmentManager().beginTransaction().replace(R.id.container, TypeFragment.newInstance(getKey(), mType.getTypeId(), mType.getStyle(), getExtend(), mType.isFolder(), getY(), getHistoryResumeCid(), getHistoryResumeKey(), getHistoryResumeTargetCid())).commit();
    }

    private HashMap<String, String> getExtend() {
        HashMap<String, String> extend = new HashMap<>();
        for (Filter filter : mType.getFilters()) if (filter.getInit() != null) extend.put(filter.getKey(), filter.setSelected(filter.getInit()));
        return extend;
    }

    public void openFolder(String typeId, HashMap<String, String> extend) {
        TypeFragment next = TypeFragment.newInstance(getKey(), typeId, mType.getStyle(), extend, mType.isFolder(), getY(), getHistoryResumeCid(), getHistoryResumeKey(), getHistoryResumeTargetCid());
        FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        Optional.ofNullable(getChild()).ifPresent(ft::hide);
        ft.add(R.id.container, next);
        ft.addToBackStack(null);
        ft.commit();
    }

    public Result getResult() {
        return getParent().getResult();
    }

    public void onRefresh() {
        Optional.ofNullable(getChild()).ifPresent(TypeFragment::onRefresh);
    }

    public void scrollToTop() {
        Optional.ofNullable(getChild()).ifPresent(TypeFragment::scrollToTop);
    }

    public void setFilter(String key, Value value) {
        Optional.ofNullable(getChild()).ifPresent(f -> f.setFilter(key, value));
    }

    public boolean canBack() {
        return getChildFragmentManager().getBackStackEntryCount() > 0;
    }

    public void goBack() {
        getChildFragmentManager().popBackStack();
    }
}
