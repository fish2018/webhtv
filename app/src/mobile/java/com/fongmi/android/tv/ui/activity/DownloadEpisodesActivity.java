package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.DownloadItem;
import com.fongmi.android.tv.databinding.ActivityDownloadEpisodesBinding;
import com.fongmi.android.tv.ui.adapter.DownloadEpisodeListAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.DownloadManager;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 單部影片下的所有下載劇集列表。
 */
public class DownloadEpisodesActivity extends BaseActivity implements DownloadEpisodeListAdapter.OnClickListener, DownloadManager.Listener {

    private static final String EXTRA_GROUP = "group";
    private static final String EXTRA_NAME = "name";

    private ActivityDownloadEpisodesBinding mBinding;
    private DownloadEpisodeListAdapter mAdapter;
    private String mGroupId;
    private long mLastUpdate;

    public static void start(Activity activity, String groupId, String name) {
        Intent intent = new Intent(activity, DownloadEpisodesActivity.class);
        intent.putExtra(EXTRA_GROUP, groupId);
        intent.putExtra(EXTRA_NAME, name);
        activity.startActivity(intent);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityDownloadEpisodesBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(android.os.Bundle savedInstanceState) {
        mGroupId = getIntent().getStringExtra(EXTRA_GROUP);
        mBinding.title.setText(getIntent().getStringExtra(EXTRA_NAME));
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setLayoutManager(new LinearLayoutManager(this));
        mBinding.recycler.setAdapter(mAdapter = new DownloadEpisodeListAdapter(this));
        DownloadManager.get().addListener(this);
        refresh();
    }

    private void refresh() {
        if (TextUtils.isEmpty(mGroupId)) return;
        mAdapter.setItems(DownloadManager.get().getItems(mGroupId));
        if (mAdapter.getItemCount() == 0) finish();
    }

    @Override
    public void onItemClick(DownloadItem item) {
        if (!item.isDone()) return;
        play(item);
    }

    @Override
    public void onItemAction(DownloadItem item) {
        if (item.isDone()) play(item);
        else DownloadManager.get().toggle(item);
    }

    private void play(DownloadItem item) {
        if (!item.exists()) {
            Notify.show(R.string.download_failed);
            DownloadManager.get().remove(item);
            return;
        }
        VideoActivity.file(this, item.getPath(), item.getVodName() + " " + item.getEpisodeName());
    }

    @Override
    public void onItemDelete(DownloadItem item) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.download_delete_episode)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> DownloadManager.get().remove(item))
                .show();
    }

    @Override
    public void onDownloadUpdate(@NonNull DownloadItem item) {
        // 與參考 v2 一致：每次回調整列表刷新（notifyDataSetChanged），避免單條更新與整表刷新
        // 混用造成進度/速度文字殘留（重影）。m3u8 以分片為單位回調頻繁，這裡做 1 秒節流。
        if (!item.getGroupId().equals(mGroupId)) return;
        long now = System.currentTimeMillis();
        if (now - mLastUpdate < 1000) return;
        mLastUpdate = now;
        refresh();
    }

    @Override
    public void onDownloadChange() {
        refresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DownloadManager.get().removeListener(this);
    }
}
