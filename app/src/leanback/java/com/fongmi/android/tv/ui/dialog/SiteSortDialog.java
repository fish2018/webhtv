package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogSiteSortBinding;
import com.fongmi.android.tv.setting.SiteBlockSetting;
import com.fongmi.android.tv.setting.SiteOrderStore;
import com.fongmi.android.tv.ui.adapter.SiteSortAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class SiteSortDialog extends BaseGlassDialog implements SiteSortAdapter.OnClickListener {

    private DialogSiteSortBinding binding;
    private SiteSortAdapter adapter;
    private OnConfirmListener listener;

    public interface OnConfirmListener {
        void onConfirm();
    }

    public static SiteSortDialog create() {
        return new SiteSortDialog();
    }

    public SiteSortDialog setListener(OnConfirmListener listener) {
        this.listener = listener;
        return this;
    }

    public void show(FragmentActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        show(activity.getSupportFragmentManager(), SiteSortDialog.class.getSimpleName());
    }

    @Override
    protected androidx.viewbinding.ViewBinding getBinding() {
        binding = DialogSiteSortBinding.inflate(LayoutInflater.from(getActivity()));
        return binding;
    }

    @Override
    protected void initView() {
        adapter = new SiteSortAdapter(this);
        binding.recycler.setLayoutManager(new LinearLayoutManager(getActivity()));
        binding.recycler.setAdapter(adapter);
        loadSites();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (listener != null) listener.onConfirm();
    }

    private void loadSites() {
        // 仅加载未被配置隐藏（hide != 1）的站点，避免 hide 站点进入排序列表并被写入排序记忆
        List<Site> allSites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) {
            if (site != null && !site.isHide()) allSites.add(site);
        }
        // 应用记忆排序，记忆中没有的按手机逻辑（文件源在前，接口源在后）
        SiteOrderStore.sortSites(allSites);
        adapter.addAll(allSites);
    }

    @Override
    public void onItemClick(Site item) {
        // 点击站点项切换显示/隐藏
        int position = adapter.getItems().indexOf(item);
        if (position >= 0) adapter.toggleHide(position);
    }

    @Override
    protected float getWidthRatio() {
        return 0.7f;
    }
}
