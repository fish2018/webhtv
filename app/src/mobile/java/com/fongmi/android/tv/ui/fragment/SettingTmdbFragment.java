package com.fongmi.android.tv.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.FragmentSettingTmdbBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.LightDialog;
import com.fongmi.android.tv.ui.dialog.TmdbSourceDialog;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingTmdbFragment extends BaseFragment {

    private static final int[] DETAIL_OPEN_MODES = {Setting.DETAIL_OPEN_ORIGINAL_ENHANCED, Setting.DETAIL_OPEN_FUSION, Setting.DETAIL_OPEN_ENHANCED, Setting.DETAIL_OPEN_PLAYER, Setting.DETAIL_OPEN_DIRECT};
    private static final int[] DETAIL_THEME_MODES = {Setting.DETAIL_STYLE_NATIVE, Setting.DETAIL_STYLE_PROFILE, Setting.DETAIL_STYLE_CINEMA};

    private FragmentSettingTmdbBinding mBinding;
    private String[] tmdbMatchMode;

    public static SettingTmdbFragment newInstance() {
        return new SettingTmdbFragment();
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingTmdbBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setText();
    }

    @Override
    protected void initEvent() {
        mBinding.tmdbSource.setOnClickListener(this::setTmdbSource);
        mBinding.detailInteractionMode.setOnClickListener(this::setDetailOpenMode);
        mBinding.detailThemeMode.setOnClickListener(this::setDetailThemeMode);
        mBinding.tmdbMatchMode.setOnClickListener(this::setTmdbMatchMode);
        mBinding.tmdbEpisodeFileSize.setOnClickListener(this::setTmdbEpisodeFileSize);
        mBinding.historyAggregation.setOnClickListener(this::setHistoryAggregation);
    }

    private void setText() {
        mBinding.tmdbSourceText.setText(getString(Setting.isTmdbReady() ? R.string.setting_configured : R.string.setting_unconfigured));
        mBinding.detailInteractionModeText.setText(getDetailOpenModeText());
        mBinding.detailThemeMode.setVisibility(Setting.isTmdbMode(Setting.getDetailOpenMode()) ? View.VISIBLE : View.GONE);
        mBinding.detailThemeModeText.setText(getDetailThemeModeText());
        mBinding.tmdbMatchModeText.setText((tmdbMatchMode = getResources().getStringArray(R.array.select_tmdb_match_mode))[Setting.getTmdbMatchMode()]);
        mBinding.tmdbEpisodeFileSizeText.setText(getSwitch(Setting.isTmdbEpisodeFileSize()));
        mBinding.historyAggregation.setVisibility(Setting.isTmdbReady() ? View.VISIBLE : View.GONE);
        mBinding.historyAggregationText.setText(getSwitch(Setting.isHistoryAggregationByTmdb()));
    }

    private void setTmdbSource(View view) {
        TmdbSourceDialog.create(requireActivity()).onDismiss(this::setText).show();
    }

    private String getDetailOpenModeText() {
        String[] labels = getDetailOpenModes();
        int mode = Setting.getDetailOpenMode();
        for (int i = 0; i < DETAIL_OPEN_MODES.length; i++) if (DETAIL_OPEN_MODES[i] == mode) return labels[i];
        return labels[0];
    }

    private String[] getDetailOpenModes() {
        return new String[]{getString(R.string.setting_detail_open_original_enhanced), getString(R.string.setting_detail_open_fusion), getString(R.string.setting_detail_open_enhanced), getString(R.string.setting_detail_open_player), getString(R.string.setting_detail_open_direct)};
    }

    private String getDetailThemeModeText() {
        String[] labels = getDetailThemeModes();
        int mode = Setting.getTmdbDetailStyle();
        for (int i = 0; i < DETAIL_THEME_MODES.length; i++) if (DETAIL_THEME_MODES[i] == mode) return labels[i];
        return labels[0];
    }

    private String[] getDetailThemeModes() {
        return new String[]{getString(R.string.setting_detail_theme_native), getString(R.string.setting_detail_theme_profile), getString(R.string.setting_detail_theme_cinema)};
    }

    private void setDetailOpenMode(View view) {
        AlertDialog alert = new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog).setTitle(R.string.setting_detail_open_mode).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(getDetailOpenModes(), getDetailOpenModeIndex(), (dialog, which) -> {
            int mode = DETAIL_OPEN_MODES[which];
            if (Setting.isTmdbMode(mode) && !Setting.isTmdbReady()) {
                dialog.dismiss();
                Notify.show(R.string.detail_tmdb_need_key);
                TmdbSourceDialog.create(requireActivity()).onDismiss(() -> {
                    if (Setting.isTmdbReady()) Setting.putDetailOpenMode(mode);
                    setText();
                }).show();
                return;
            }
            Setting.putDetailOpenMode(mode);
            setText();
            dialog.dismiss();
        }).show();
        LightDialog.apply(alert);
    }

    private int getDetailOpenModeIndex() {
        int mode = Setting.getDetailOpenMode();
        for (int i = 0; i < DETAIL_OPEN_MODES.length; i++) if (DETAIL_OPEN_MODES[i] == mode) return i;
        return 0;
    }

    private void setDetailThemeMode(View view) {
        if (!Setting.isTmdbMode(Setting.getDetailOpenMode())) return;
        AlertDialog alert = new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog).setTitle(R.string.setting_detail_theme_mode).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(getDetailThemeModes(), getDetailThemeModeIndex(), (dialog, which) -> {
            Setting.putTmdbDetailStyle(DETAIL_THEME_MODES[which]);
            setText();
            dialog.dismiss();
        }).show();
        LightDialog.apply(alert);
    }

    private int getDetailThemeModeIndex() {
        int mode = Setting.getTmdbDetailStyle();
        for (int i = 0; i < DETAIL_THEME_MODES.length; i++) if (DETAIL_THEME_MODES[i] == mode) return i;
        return 0;
    }

    private void setTmdbMatchMode(View view) {
        Setting.putTmdbMatchMode((Setting.getTmdbMatchMode() + 1) % tmdbMatchMode.length);
        setText();
    }

    private void setTmdbEpisodeFileSize(View view) {
        Setting.putTmdbEpisodeFileSize(!Setting.isTmdbEpisodeFileSize());
        setText();
    }

    private void setHistoryAggregation(View view) {
        Setting.putHistoryAggregationByTmdb(!Setting.isHistoryAggregationByTmdb());
        RefreshEvent.history();
        setText();
    }

    @Override
    public void onResume() {
        super.onResume();
        setText();
    }
}
