package com.fongmi.android.tv.ui.activity;

import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityCrashBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.AppVersion;
import com.fongmi.android.tv.utils.CrashRestartMode;

import java.util.Objects;

import cat.ereza.customactivityoncrash.CustomActivityOnCrash;

public class CrashActivity extends BaseActivity {

    private ActivityCrashBinding mBinding;

    @Override
    protected boolean customWall() {
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCrashBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initEvent() {
        mBinding.details.setOnClickListener(v -> showError());
        mBinding.restart.setOnClickListener(v -> restartApplication());
        mBinding.silentRestart.setOnClickListener(v -> {
            CrashRestartMode.arm();
            restartApplication();
        });
    }

    private void restartApplication() {
        CustomActivityOnCrash.restartApplication(this, Objects.requireNonNull(CustomActivityOnCrash.getConfigFromIntent(getIntent())));
    }

    private void showError() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.crash_details_title)
                .setMessage(getString(R.string.crash_details_message,
                        AppVersion.fullName(),
                        CustomActivityOnCrash.getAllErrorDetailsFromIntent(this, getIntent())))
                .setPositiveButton(R.string.crash_details_close, null)
                .show();
    }
}
