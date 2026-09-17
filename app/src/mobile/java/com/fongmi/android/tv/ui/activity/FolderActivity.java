package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.databinding.ActivityFolderBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.FolderFragment;

public class FolderActivity extends BaseActivity {

    private ActivityFolderBinding mBinding;

    public static void start(Activity activity, String key, Result result) {
        start(activity, key, result, -1, null, -1);
    }

    public static void start(Activity activity, String key, Result result, int historyResumeCid, String historyResumeKey, int historyResumeTargetCid) {
        if (result == null || result.getTypes().isEmpty()) return;
        Intent intent = new Intent(activity, FolderActivity.class);
        intent.putExtra("key", key);
        intent.putExtra("result", result);
        intent.putExtra("historyResumeCid", historyResumeCid);
        intent.putExtra("historyResumeKey", historyResumeKey);
        intent.putExtra("historyResumeTargetCid", historyResumeTargetCid);
        activity.startActivity(intent);
    }

    private String getKey() {
        return getIntent().getStringExtra("key");
    }

    private Result getResult() {
        return getIntent().getParcelableExtra("result");
    }

    private int getHistoryResumeCid() {
        return getIntent().getIntExtra("historyResumeCid", -1);
    }

    private String getHistoryResumeKey() {
        return getIntent().getStringExtra("historyResumeKey");
    }

    private int getHistoryResumeTargetCid() {
        return getIntent().getIntExtra("historyResumeTargetCid", -1);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityFolderBinding.inflate(getLayoutInflater());
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setSupportActionBar(mBinding.toolbar);
        Class type = getResult().getTypes().get(0);
        setTitle(type.getTypeName());
        getSupportFragmentManager().beginTransaction().replace(R.id.container, FolderFragment.newInstance(getKey(), type, 8, getHistoryResumeCid(), getHistoryResumeKey(), getHistoryResumeTargetCid()), "0").commit();
    }

    private FolderFragment getFragment() {
        return (FolderFragment) getSupportFragmentManager().findFragmentByTag("0");
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) onBackInvoked();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onBackInvoked() {
        if (getFragment().canBack()) getFragment().goBack();
        else super.onBackInvoked();
    }
}
