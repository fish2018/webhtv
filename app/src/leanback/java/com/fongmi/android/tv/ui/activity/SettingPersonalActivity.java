package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.HomeButton;
import com.fongmi.android.tv.databinding.ActivitySettingPersonalBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.AppBranding;
import com.fongmi.android.tv.setting.AutoBackupPolicy;
import com.fongmi.android.tv.setting.GroupRuleConfig;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.GroupRuleDialog;
import com.fongmi.android.tv.ui.dialog.HomeButtonDialog;
import com.fongmi.android.tv.ui.dialog.HomeMenuKeyDialog;
import com.fongmi.android.tv.ui.dialog.SpeedSettingDialog;
import com.fongmi.android.tv.ui.dialog.SliderNumberDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public class SettingPersonalActivity extends BaseActivity {

    private ActivitySettingPersonalBinding mBinding;
    private String[] fullscreenMenuKey;
    private String[] homeMenuKey;
    private String[] searchUi;
    private String[] searchColumn;
    private String[] searchResultSort;
    private String[] globalHistoryMode;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingPersonalActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingPersonalBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.homeVodAutoLoad.requestFocus();
        setText();
    }

    @Override
    protected void initEvent() {
        mBinding.homeVodAutoLoad.setOnClickListener(this::setHomeVodAutoLoad);
        mBinding.homeSiteLock.setOnClickListener(this::setHomeSiteLock);
        mBinding.autoBackup.setOnClickListener(this::setAutoBackup);
        mBinding.homeButtons.setOnClickListener(this::onHomeButtons);
        mBinding.fullscreenMenuKey.setOnClickListener(this::setFullscreenMenuKey);
        mBinding.homeMenuKey.setOnClickListener(this::setHomeMenuKey);
        mBinding.playBackToDetail.setOnClickListener(this::setPlayBackToDetail);
        mBinding.episodeHistory.setOnClickListener(this::setEpisodeHistory);
        mBinding.globalHistory.setOnClickListener(this::setGlobalHistory);
        mBinding.playSpeed.setOnClickListener(this::setPlaySpeed);
        mBinding.groupRule.setOnClickListener(this::setGroupRule);
        mBinding.homeHistory.setOnClickListener(this::setHomeHistory);
        mBinding.searchThread.setOnClickListener(this::setSearchThread);
        mBinding.searchUi.setOnClickListener(this::setSearchUi);
        mBinding.searchResultSort.setOnClickListener(this::setSearchResultSort);
        // mBinding.searchColumn.setOnClickListener(this::setSearchColumn); // 在搜索页面切换更方便
        mBinding.appBranding.setOnClickListener(this::startAppBranding);
        mBinding.resetApp.setOnClickListener(this::showResetAppDialog);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setText();
    }

    private void setText() {
        mBinding.homeVodAutoLoadText.setText(getSwitch(Setting.isHomeVodAutoLoad()));
        mBinding.homeSiteLockText.setText(getSwitch(Setting.isHomeSiteLock()));
        mBinding.autoBackupText.setText(getSwitch(isAutoBackupEnabled()));
        mBinding.homeButtonsText.setText(getString(R.string.home_buttons_selected, HomeButton.getButtons().size(), HomeButton.all().size()));
        mBinding.fullscreenMenuKeyText.setText((fullscreenMenuKey = getResources().getStringArray(R.array.select_fullscreen_menu_key))[Setting.getFullscreenMenuKey()]);
        mBinding.homeMenuKeyText.setText((homeMenuKey = getResources().getStringArray(R.array.select_home_menu_key))[Setting.getHomeMenuKey()]);
        mBinding.playBackToDetailText.setText(getSwitch(Setting.isPlayBackToDetail()));
        mBinding.episodeHistoryText.setText(getSwitch(Setting.isEpisodeHistory()));
        mBinding.globalHistoryText.setText((globalHistoryMode = getResources().getStringArray(R.array.select_global_history_mode))[Setting.getGlobalHistoryMode()]);
        mBinding.playSpeedText.setText(getSpeedText(PlayerSetting.getDefaultSpeed()));
        mBinding.groupRuleText.setText(getString(R.string.setting_group_rule_summary, GroupRuleConfig.enabledCount(), GroupRuleConfig.totalCount()));
        mBinding.homeHistoryText.setText(getSwitch(Setting.isHomeHistory()));
        mBinding.searchThreadText.setText(String.valueOf(Setting.getSearchThread()));
        mBinding.searchUiText.setText((searchUi = getResources().getStringArray(R.array.select_search_ui))[Setting.getSearchUi()]);
        mBinding.searchResultSortText.setText((searchResultSort = getResources().getStringArray(R.array.select_search_result_sort))[Setting.getSearchResultSort()]);
        // mBinding.searchColumnText.setText(getSearchColumnText()); // 在搜索页面切换更方便
        mBinding.appBrandingText.setText(AppBranding.getSummary(this));
    }

    private String getSearchColumnText() {
        searchColumn = getResources().getStringArray(R.array.select_search_column);
        int column = Setting.getSearchColumn();
        if (column >= 0 && column < searchColumn.length) {
            return searchColumn[column];
        }
        return searchColumn[0]; // 默认返回第一项
    }

    private String getSpeedText(float speed) {
        return String.format(Locale.US, "%.2f", speed);
    }

    private void setHomeVodAutoLoad(View view) {
        Setting.putHomeVodAutoLoad(!Setting.isHomeVodAutoLoad());
        setText();
    }

    private void setHomeSiteLock(View view) {
        Setting.putHomeSiteLock(!Setting.isHomeSiteLock());
        setText();
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

    private void onHomeButtons(View view) {
        HomeButtonDialog.show(this, this::setText);
    }

    private void setFullscreenMenuKey(View view) {
        Setting.putFullscreenMenuKey((Setting.getFullscreenMenuKey() + 1) % fullscreenMenuKey.length);
        setText();
    }

    private void setHomeMenuKey(View view) {
        HomeMenuKeyDialog.show(this, this::setText);
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
        SpeedSettingDialog.show(this, R.string.setting_play_speed, PlayerSetting.getDefaultSpeed(), 0.5f, 5f, 0.25f, value -> {
            PlayerSetting.putDefaultSpeed(value);
            setText();
        });
    }

    private void setGroupRule(View view) {
        GroupRuleDialog.create(this).onChanged(this::setText).show();
    }

    private void setHomeHistory(View view) {
        Setting.putHomeHistory(!Setting.isHomeHistory());
        RefreshEvent.history();
        setText();
    }

    private void setSearchThread(View view) {
        SliderNumberDialog.show(this, R.string.setting_search_thread, Setting.getSearchThread(), 1, 100, value -> {
            Setting.putSearchThread(value);
            setText();
        });
    }

    private void setSearchUi(View view) {
        Setting.putSearchUi((Setting.getSearchUi() + 1) % searchUi.length);
        setText();
    }

    private void setSearchResultSort(View view) {
        Setting.putSearchResultSort((Setting.getSearchResultSort() + 1) % searchResultSort.length);
        setText();
    }

    private void showResetAppDialog(View view) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_reset_app)
                .setMessage(R.string.dialog_reset_app_data)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> resetApp())
                .show();
    }

    private void startAppBranding(View view) {
        AppBrandingActivity.start(this);
    }

    private void resetApp() {
        if (!Util.resetApp()) Notify.show(R.string.reset_app_failed);
    }

    // 在搜索页面切换更方便，此处不再提供设置入口
    /*
    private void setSearchColumn(View view) {
        int current = Setting.getSearchColumn();
        int next = (current + 1) % 3; // 0: 自适应, 1: 1列, 2: 默认5列
        Setting.putSearchColumn(next);
        setText();
    }
    */

}
