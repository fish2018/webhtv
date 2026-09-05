package com.fongmi.android.tv.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.FragmentSettingPersonalBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.AppBranding;
import com.fongmi.android.tv.setting.AutoBackupPolicy;
import com.fongmi.android.tv.setting.GroupRuleConfig;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.AppBrandingActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.GroupRuleDialog;
import com.fongmi.android.tv.ui.dialog.SpeedSettingDialog;
import com.fongmi.android.tv.ui.dialog.SliderNumberDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public class SettingPersonalFragment extends BaseFragment {

    private FragmentSettingPersonalBinding mBinding;
    private String[] searchUi;
    private String[] searchColumn;
    private String[] siteColumn;
    private String[] globalHistoryMode;
    private String[] searchResultSort;

    public static SettingPersonalFragment newInstance() {
        return new SettingPersonalFragment();
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingPersonalBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setText();
    }

    @Override
    protected void initEvent() {
        mBinding.searchThread.setOnClickListener(this::setSearchThread);
        mBinding.autoBackup.setOnClickListener(this::setAutoBackup);
        mBinding.playBackToDetail.setOnClickListener(this::setPlayBackToDetail);
        mBinding.episodeHistory.setOnClickListener(this::setEpisodeHistory);
        mBinding.globalHistory.setOnClickListener(this::setGlobalHistory);
        mBinding.playSpeed.setOnClickListener(this::setPlaySpeed);
        mBinding.groupRule.setOnClickListener(this::setGroupRule);
        mBinding.searchUi.setOnClickListener(this::setSearchUi);
        mBinding.searchColumn.setOnClickListener(this::setSearchColumn);
        mBinding.siteColumn.setOnClickListener(this::setSiteColumn);
        mBinding.searchResultSort.setOnClickListener(this::setSearchResultSort);
        mBinding.resetApp.setOnClickListener(this::showResetAppDialog);
        mBinding.appBranding.setOnClickListener(this::startAppBranding);
    }

    private void setText() {
        mBinding.searchThreadText.setText(String.valueOf(Setting.getSearchThread()));
        mBinding.autoBackupText.setText(getSwitch(isAutoBackupEnabled()));
        mBinding.playBackToDetailText.setText(getSwitch(Setting.isPlayBackToDetail()));
        mBinding.episodeHistoryText.setText(getSwitch(Setting.isEpisodeHistory()));
        mBinding.globalHistoryText.setText((globalHistoryMode = getResources().getStringArray(R.array.select_global_history_mode))[Setting.getGlobalHistoryMode()]);
        mBinding.playSpeedText.setText(getSpeedText(PlayerSetting.getDefaultSpeed()));
        mBinding.groupRuleText.setText(getString(R.string.setting_group_rule_summary, GroupRuleConfig.enabledCount(), GroupRuleConfig.totalCount()));
        mBinding.searchUiText.setText((searchUi = getResources().getStringArray(R.array.select_search_ui))[Setting.getSearchUi()]);
        mBinding.searchColumnText.setText(getSearchColumnText());
        mBinding.siteColumnText.setText((siteColumn = getResources().getStringArray(R.array.select_site_column))[Setting.getSiteColumn() - 1]);
        mBinding.searchResultSortText.setText((searchResultSort = getResources().getStringArray(R.array.select_search_result_sort))[Setting.getSearchResultSort()]);
        mBinding.appBrandingText.setText(AppBranding.getSummary(requireContext()));
    }

    private String getSearchColumnText() {
        searchColumn = getResources().getStringArray(R.array.select_search_column);
        int column = Setting.getSearchColumn();
        if (column >= 0 && column < searchColumn.length) {
            return searchColumn[column];
        }
        return searchColumn[0];
    }

    private String getSpeedText(float speed) {
        return String.format(Locale.US, "%.2f", speed);
    }

    private void setSearchThread(View view) {
        SliderNumberDialog.show(requireActivity(), R.string.setting_search_thread, Setting.getSearchThread(), 1, 100, value -> {
            Setting.putSearchThread(value);
            setText();
        });
    }

    private void setAutoBackup(View view) {
        if (isAutoBackupEnabled()) {
            Setting.putAutoBackup(false);
            setText();
            return;
        }
        PermissionUtil.requestFile(this, allGranted -> {
            if (!allGranted) {
                Notify.show(R.string.backup_permission_denied);
                return;
            }
            Setting.putAutoBackup(true);
            setText();
        });
    }

    private boolean isAutoBackupEnabled() {
        return AutoBackupPolicy.isEffective(Setting.isAutoBackup(), Setting.hasFileAccess());
    }

    private void setPlayBackToDetail(View view) {
        Setting.putPlayBackToDetail(!Setting.isPlayBackToDetail());
        setText();
    }

    private void setEpisodeHistory(View view) {
        Setting.putEpisodeHistory(!Setting.isEpisodeHistory());
        setText();
    }

    private void setGlobalHistory(View view) {
        Setting.putGlobalHistoryMode((Setting.getGlobalHistoryMode() + 1) % globalHistoryMode.length);
        RefreshEvent.history();
        setText();
    }

    private void setPlaySpeed(View view) {
        SpeedSettingDialog.show(requireActivity(), R.string.setting_play_speed, PlayerSetting.getDefaultSpeed(), 0.5f, 5f, 0.25f, value -> {
            PlayerSetting.putDefaultSpeed(value);
            setText();
        });
    }

    private void setGroupRule(View view) {
        GroupRuleDialog.create(requireActivity()).onChanged(this::setText).show();
    }

    private void setSearchUi(View view) {
        Setting.putSearchUi((Setting.getSearchUi() + 1) % searchUi.length);
        setText();
    }

    private void setSearchColumn(View view) {
        int current = Setting.getSearchColumn();
        int next = (current + 1) % searchColumn.length;
        Setting.putSearchColumn(next);
        setText();
    }

    private void setSiteColumn(View view) {
        Setting.putSiteColumn(Setting.getSiteColumn() == 1 ? 2 : 1);
        setText();
    }

    private void setSearchResultSort(View view) {
        Setting.putSearchResultSort((Setting.getSearchResultSort() + 1) % searchResultSort.length);
        setText();
    }

    private void showResetAppDialog(View view) {
        new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.dialog_reset_app)
                .setMessage(R.string.dialog_reset_app_data)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> resetApp())
                .show();
    }

    private void startAppBranding(View view) {
        AppBrandingActivity.start(requireActivity());
    }

    private void resetApp() {
        if (!Util.resetApp()) Notify.show(R.string.reset_app_failed);
    }

    @Override
    public void onResume() {
        super.onResume();
        setText();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) setText();
    }
}
