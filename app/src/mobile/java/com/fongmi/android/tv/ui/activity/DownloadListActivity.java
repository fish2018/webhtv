package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.DownloadGroup;
import com.fongmi.android.tv.bean.DownloadItem;
import com.fongmi.android.tv.databinding.ActivityDownloadListBinding;
import com.fongmi.android.tv.ui.adapter.DownloadVodAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.DownloadManager;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * 下載管理首頁，以影片維度聚合展示所有下載任務。
 */
public class DownloadListActivity extends BaseActivity implements DownloadVodAdapter.OnClickListener, DownloadManager.Listener {

    private ActivityDownloadListBinding mBinding;
    private DownloadVodAdapter mAdapter;
    private long mLastUpdate;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, DownloadListActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityDownloadListBinding.inflate(getLayoutInflater());
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void initView(android.os.Bundle savedInstanceState) {
        setSupportActionBar(mBinding.toolbar);
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, ResUtil.isLand(this) ? 5 : 3));
        mBinding.recycler.setAdapter(mAdapter = new DownloadVodAdapter(this));
        DownloadManager.get().addListener(this);
        refresh();
    }

    @Override
    protected void initEvent() {
        mBinding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.clear) clearFinished();
            return true;
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) finish();
        return super.onOptionsItemSelected(item);
    }

    private void refresh() {
        List<DownloadGroup> groups = DownloadManager.get().getGroups();
        mAdapter.setItems(groups);
        mBinding.empty.setVisibility(groups.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void clearFinished() {
        List<DownloadItem> done = new java.util.ArrayList<>();
        for (DownloadItem item : DownloadManager.get().getItems()) if (item.isDone()) done.add(item);
        if (done.isEmpty()) return;
        DownloadManager.get().remove(done);
    }

    @Override
    public void onItemClick(DownloadGroup group) {
        DownloadEpisodesActivity.start(this, group.getId(), group.getVodName());
    }

    @Override
    public void onItemDelete(DownloadGroup group) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.download_delete_group)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> DownloadManager.get().removeGroup(group))
                .show();
    }

    @Override
    public void onDownloadChange() {
        refresh();
    }

    @Override
    public void onDownloadUpdate(@NonNull DownloadItem item) {
        // 進度回調頻繁，聚合視圖僅需低頻刷新徽章
        long now = System.currentTimeMillis();
        if (now - mLastUpdate < 1000) return;
        mLastUpdate = now;
        refresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DownloadManager.get().removeListener(this);
    }
}
