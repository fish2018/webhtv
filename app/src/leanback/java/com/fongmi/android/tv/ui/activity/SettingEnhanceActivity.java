package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.databinding.ActivitySettingEnhanceBinding;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.OneKeySyncDialog;
import com.fongmi.android.tv.ui.dialog.ShellProxyDialog;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.crawler.SpiderDebug;

public class SettingEnhanceActivity extends BaseActivity {

    private ActivitySettingEnhanceBinding mBinding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingEnhanceActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingEnhanceBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.driveCheck.requestFocus();
        mBinding.shellProxyConfig.setVisibility(View.GONE);
        setText();
    }

    @Override
    protected void initEvent() {
        mBinding.driveCheck.setOnClickListener(this::setDriveCheck);
        mBinding.debugLog.setOnClickListener(this::setDebugLog);
        mBinding.shellProxy.setOnClickListener(this::setShellProxy);
        mBinding.shellProxyConfig.setOnClickListener(v -> ShellProxyDialog.show(this, this::setText));
        mBinding.oneKeySync.setOnClickListener(v -> OneKeySyncDialog.create().show(this));
    }

    private void setText() {
        mBinding.driveCheckText.setText(getSwitch(Setting.isDriveCheck()));
        mBinding.debugLogText.setText(getSwitch(Setting.isDebugLog()));
        mBinding.shellProxyText.setText(Setting.getShellProxyUrl().isEmpty() ? getString(R.string.none) : Uri.parse(Setting.getShellProxyUrl()).getScheme());
        mBinding.shellProxyConfigText.setText(Setting.getShellProxyUrl());
    }

    private void setDriveCheck(View view) {
        Setting.putDriveCheck(!Setting.isDriveCheck());
        mBinding.driveCheckText.setText(getSwitch(Setting.isDriveCheck()));
    }

    private void setDebugLog(View view) {
        Setting.putDebugLog(!Setting.isDebugLog());
        mBinding.debugLogText.setText(getSwitch(Setting.isDebugLog()));
        if (!Setting.isDebugLog()) return;
        Server.get().start();
        String url = Server.get().getAddress("/debug/logs");
        SpiderDebug.log("debug", "open logs url=%s lan=%s", url, Server.get().getAddress(false) + "/debug/logs");
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Notify.show(url);
        }
    }

    private void setShellProxy(View view) {
        ShellProxyDialog.show(this, this::setText);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setText();
    }
}
