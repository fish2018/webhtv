package com.fongmi.android.tv.ui.fragment;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.databinding.FragmentSettingEnhanceBinding;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.ProxySetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.FeatureConfigDialog;
import com.fongmi.android.tv.ui.dialog.OneKeySyncDialog;
import com.fongmi.android.tv.ui.dialog.ShellProxyDialog;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingEnhanceFragment extends BaseFragment {

    private static final int[] SEARCH_THREADS = {1, 2, 4, 6, 8, 10, 12, 16, 20, 32};

    private FragmentSettingEnhanceBinding mBinding;

    public static SettingEnhanceFragment newInstance() {
        return new SettingEnhanceFragment();
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingEnhanceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setText();
    }

    @Override
    protected void initEvent() {
        mBinding.detailOpenMode.setOnClickListener(this::setDetailOpenMode);
        mBinding.searchThread.setOnClickListener(this::setSearchThread);
        mBinding.tmdbConfig.setOnClickListener(this::setTmdbConfig);
        mBinding.driveCheck.setOnClickListener(this::setDriveCheck);
        mBinding.debugLog.setOnClickListener(this::setDebugLog);
        mBinding.shellProxy.setOnClickListener(this::setShellProxy);
        mBinding.shellProxyConfig.setOnClickListener(this::setShellProxyConfig);
        mBinding.oneKeySync.setOnClickListener(this::setOneKeySync);
    }

    private void setText() {
        mBinding.detailOpenModeText.setText(getDetailOpenMode());
        mBinding.searchThreadText.setText(String.valueOf(Setting.getSearchThread()));
        mBinding.tmdbConfigText.setText(TmdbConfig.objectFrom(Setting.getTmdbConfig()).isReady() ? R.string.setting_configured : R.string.setting_unconfigured);
        mBinding.driveCheckText.setText(getSwitch(Setting.isDriveCheck()));
        mBinding.debugLogText.setText(getSwitch(Setting.isDebugLog()));
        mBinding.shellProxyText.setText(getSwitch(Setting.isShellProxy()));
        mBinding.shellProxyConfig.setVisibility(Setting.isShellProxy() ? View.VISIBLE : View.GONE);
        mBinding.shellProxyConfigText.setText(getShellProxyConfigText());
    }

    private String getShellProxyConfigText() {
        int count = ProxySetting.getRules().size();
        return count > 0 ? getString(R.string.setting_proxy_rule_count, count) : Setting.getShellProxyUrl();
    }

    private String getDetailOpenMode() {
        return getDetailOpenModes()[Setting.getDetailOpenMode()];
    }

    private String[] getDetailOpenModes() {
        return new String[]{getString(R.string.setting_detail_open_fusion), getString(R.string.setting_detail_open_enhanced), getString(R.string.setting_detail_open_direct)};
    }

    private void setSearchThread(View view) {
        int index = 0;
        for (int i = 0; i < SEARCH_THREADS.length; i++) if (SEARCH_THREADS[i] == Setting.getSearchThread()) index = i;
        Setting.putSearchThread(SEARCH_THREADS[(index + 1) % SEARCH_THREADS.length]);
        mBinding.searchThreadText.setText(String.valueOf(Setting.getSearchThread()));
    }

    private void setDetailOpenMode(View view) {
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_detail_open_mode).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(getDetailOpenModes(), Setting.getDetailOpenMode(), (dialog, which) -> {
            if (requiresTmdb(which) && !Setting.isTmdbReady()) {
                dialog.dismiss();
                Notify.show(R.string.detail_tmdb_need_key);
                FeatureConfigDialog.create(requireActivity()).type(FeatureConfigDialog.TMDB).onDismiss(() -> {
                    if (Setting.isTmdbReady()) Setting.putDetailOpenMode(which);
                    setText();
                }).show();
                return;
            }
            Setting.putDetailOpenMode(which);
            mBinding.detailOpenModeText.setText(getDetailOpenMode());
            dialog.dismiss();
        }).show();
    }

    private boolean requiresTmdb(int mode) {
        return mode == Setting.DETAIL_OPEN_FUSION || mode == Setting.DETAIL_OPEN_ENHANCED;
    }

    private void setTmdbConfig(View view) {
        FeatureConfigDialog.create(requireActivity()).type(FeatureConfigDialog.TMDB).onDismiss(this::setText).show();
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
        Setting.putShellProxy(!Setting.isShellProxy());
        setText();
    }

    private void setShellProxyConfig(View view) {
        ShellProxyDialog.show(this, this::setText);
    }

    private void setOneKeySync(View view) {
        OneKeySyncDialog.create().show(requireActivity());
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) setText();
    }
}
