package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.databinding.ActivitySettingAppearanceBinding;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class SettingAppearanceActivity extends BaseActivity {

    private ActivitySettingAppearanceBinding mBinding;
    private String[] uiScale;
    private String[] language;
    private String[] size;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingAppearanceActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingAppearanceBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.uiScale.requestFocus();
        setText();
    }

    private void setText() {
        mBinding.uiScaleText.setText((uiScale = ResUtil.getStringArray(R.array.select_ui_scale))[Setting.getUiScaleIndex()]);
        mBinding.languageText.setText((language = ResUtil.getStringArray(R.array.select_language))[Setting.getLanguageIndex()]);
        mBinding.sizeText.setText((size = ResUtil.getStringArray(R.array.select_size))[getSizeIndex()]);
        setWallText();
    }

    @Override
    protected void initEvent() {
        mBinding.uiScale.setOnClickListener(this::setUiScale);
        mBinding.language.setOnClickListener(this::setLanguage);
        mBinding.size.setOnClickListener(this::setSize);
        mBinding.wall.setOnClickListener(this::onWall);
        mBinding.wall.setOnLongClickListener(this::onWallEdit);
        mBinding.wallHome.setOnClickListener(this::setWallDefault);
        mBinding.wallRefresh.setOnClickListener(this::setWallRefresh);
        mBinding.wallHistory.setOnClickListener(this::onWallHistory);
    }

    private void setUiScale(View view) {
        int index = (Setting.getUiScaleIndex() + 1) % uiScale.length;
        Setting.putUiScaleIndex(index);
        RefreshEvent.uiScale();
    }

    private void setLanguage(View view) {
        int index = (Setting.getLanguageIndex() + 1) % language.length;
        Setting.putLanguageIndex(index);
        RefreshEvent.language();
    }

    private void setSize(View view) {
        int index = (getSizeIndex() + 1) % size.length;
        PlayerSetting.putSize(index);
        RefreshEvent.size();
        setText();
    }

    private int getSizeIndex() {
        return Math.max(0, Math.min(PlayerSetting.getSize(), size.length - 1));
    }

    private void onWall(View view) {
        ConfigDialog.create().wall().show(this);
    }

    private boolean onWallEdit(View view) {
        ConfigDialog.create().wall().edit().show(this);
        return true;
    }

    private void setWallDefault(View view) {
        Setting.putWall(Setting.nextDefaultWall());
        Setting.putWallType(0);
        setWallText();
        ConfigEvent.wall();
    }

    private void setWallRefresh(View view) {
        Setting.putWall(0);
        WallConfig.get().load(getCallback());
    }

    private boolean onWallHistory(View view) {
        HistoryDialog.create().wall().show(this);
        return true;
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(getActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                setWallText();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    private void setWallText() {
        mBinding.wallText.setText(Setting.getWallDesc(WallConfig.getDesc()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() == ConfigEvent.Type.WALL) {
            setWallText();
        }
    }
}
