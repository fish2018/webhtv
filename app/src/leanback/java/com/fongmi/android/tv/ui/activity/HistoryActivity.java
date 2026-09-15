package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.ActivityHistoryBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.ui.adapter.HistoryAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.dialog.BaseGlassDialog;
import com.fongmi.android.tv.utils.KeyUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class HistoryActivity extends BaseActivity implements HistoryAdapter.OnClickListener {

    private ActivityHistoryBinding mBinding;
    private HistoryAdapter mAdapter;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, HistoryActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHistoryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setRecyclerView();
        setTitleBar();
        getHistory();
    }

    private void setTitleBar() {
        mBinding.btnDelete.setOnClickListener(v -> {
            if (mAdapter.getItemCount() > 0) mAdapter.setDelete(true);
        });
        mBinding.btnClear.setOnClickListener(v -> {
            if (mAdapter.getItemCount() > 0) showClearDialog();
        });
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setAdapter(mAdapter = new HistoryAdapter(this));
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn()));
        mBinding.recycler.addItemDecoration(new SpaceItemDecoration(Product.getColumn(), 16));
    }

    private void getHistory() {
        mAdapter.setItems(History.get(), () -> {
            mBinding.progressLayout.showContent(true, mAdapter.getItemCount());
            focusFirstItem();
        });
    }

    private void focusFirstItem() {
        if (mAdapter.getItemCount() <= 0) return;
        mBinding.recycler.post(() -> {
            View view = mBinding.recycler.getLayoutManager().findViewByPosition(0);
            if (view != null) view.requestFocus();
            else mBinding.recycler.requestFocus();
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.HISTORY) getHistory();
    }

    @Override
    public void onItemClick(History item) {
        VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic(), null, item.getWallPic());
    }

    @Override
    public void onItemDelete(History item) {
        mAdapter.remove(item.deleteAndSync(), () -> {
            if (mAdapter.getItemCount() == 0) mAdapter.setDelete(false);
        });
    }

    @Override
    public boolean onLongClick() {
        mAdapter.setDelete(true);
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event) && mAdapter.getItemCount() > 0) showClearDialog();
        return super.dispatchKeyEvent(event);
    }

    private void showClearDialog() {
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("清空播放历史")
                .setMessage("是否清空播放历史，删除后无法恢复！")
                .setPositiveButton(android.R.string.ok, (d, which) -> clearHistory())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(BaseGlassDialog.glassBackground());
            View content = w.getDecorView().findViewById(android.R.id.content);
            if (content != null) content.setBackground(null);
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).requestFocus();
    }

    private void clearHistory() {
        AppDatabase.get().getHistoryDao().delete();
        RefreshEvent.history();
    }

    @Override
    protected void onBackInvoked() {
        if (mAdapter.isDelete()) mAdapter.setDelete(false);
        else super.onBackInvoked();
    }
}
