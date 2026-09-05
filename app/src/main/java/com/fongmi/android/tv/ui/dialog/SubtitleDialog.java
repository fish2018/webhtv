package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.ui.SubtitleView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.databinding.DialogSubtitleBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.bassaer.library.MDColor;

public final class SubtitleDialog extends BaseBottomSheetDialog {

    private static final float DEFAULT_TEXT_SIZE = 0.0533f;
    private static final float TEXT_STEP = 0.002f;
    private static final float POSITION_STEP = 0.005f;
    private static final float NATIVE_POSITION_STEP = 0.05f;

    private DialogSubtitleBinding binding;
    private SubtitleView subtitleView;
    private Runnable searchAction;
    private PlayerManager player;

    public static SubtitleDialog create() {
        return new SubtitleDialog();
    }

    public SubtitleDialog view(SubtitleView subtitleView) {
        this.subtitleView = subtitleView;
        return this;
    }

    public SubtitleDialog search(Runnable searchAction) {
        this.searchAction = searchAction;
        return this;
    }

    public SubtitleDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof SubtitleDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    private boolean isFull() {
        return Util.isFullscreen(getActivity());
    }

    @Override
    protected boolean transparent() {
        return isFull();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogSubtitleBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        int count = binding.getRoot().getChildCount();
        if (isFull()) for (int i = 0; i < count; i++) ((ImageView) binding.getRoot().getChildAt(i)).getDrawable().setTint(MDColor.WHITE);
        binding.search.setVisibility(searchAction == null ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void initEvent() {
        binding.up.setOnClickListener(this::onUp);
        binding.down.setOnClickListener(this::onDown);
        binding.large.setOnClickListener(this::onLarge);
        binding.small.setOnClickListener(this::onSmall);
        binding.reset.setOnClickListener(this::onReset);
        binding.search.setOnClickListener(this::onSearch);
    }

    private void onUp(View view) {
        if (isNativeSubtitleStyle()) {
            setNativePosition(PlayerSetting.getSubtitlePosition() + NATIVE_POSITION_STEP);
            return;
        }
        if (subtitleView == null) return;
        subtitleView.addPosition(POSITION_STEP);
        PlayerSetting.putSubtitlePosition(subtitleView.getPosition());
    }

    private void onDown(View view) {
        if (isNativeSubtitleStyle()) {
            setNativePosition(PlayerSetting.getSubtitlePosition() - NATIVE_POSITION_STEP);
            return;
        }
        if (subtitleView == null) return;
        subtitleView.subPosition(POSITION_STEP);
        PlayerSetting.putSubtitlePosition(subtitleView.getPosition());
    }

    private void onLarge(View view) {
        if (isNativeSubtitleStyle()) {
            setNativeTextSize(currentTextSize() + TEXT_STEP);
            return;
        }
        if (subtitleView == null) return;
        subtitleView.addTextSize(TEXT_STEP);
        PlayerSetting.putSubtitleTextSize(subtitleView.getTextSize());
    }

    private void onSmall(View view) {
        if (isNativeSubtitleStyle()) {
            setNativeTextSize(currentTextSize() - TEXT_STEP);
            return;
        }
        if (subtitleView == null) return;
        subtitleView.subTextSize(TEXT_STEP);
        PlayerSetting.putSubtitleTextSize(subtitleView.getTextSize());
    }

    private void onReset(View view) {
        PlayerSetting.putSubtitleTextSize(0.0f);
        PlayerSetting.putSubtitlePosition(0.0f);
        if (isNativeSubtitleStyle()) {
            player.setSubtitleStyle(0.0f, 0.0f);
        } else if (subtitleView != null) {
            subtitleView.reset();
        }
    }

    private boolean isNativeSubtitleStyle() {
        return player != null && player.supportsSubtitleStyle();
    }

    private float currentTextSize() {
        float value = PlayerSetting.getSubtitleTextSize();
        return value <= 0 ? DEFAULT_TEXT_SIZE : value;
    }

    private void setNativeTextSize(float value) {
        value = Math.max(0.02f, Math.min(0.12f, value));
        PlayerSetting.putSubtitleTextSize(value);
        player.setSubtitleStyle(value, PlayerSetting.getSubtitlePosition());
    }

    private void setNativePosition(float value) {
        value = Math.max(-0.5f, Math.min(1.0f, value));
        PlayerSetting.putSubtitlePosition(value);
        player.setSubtitleStyle(PlayerSetting.getSubtitleTextSize(), value);
    }

    private void onSearch(View view) {
        if (searchAction == null) return;
        dismiss();
        App.post(searchAction::run, 100);
    }

    @Override
    public void onResume() {
        super.onResume();
        getDialog().getWindow().setLayout(ResUtil.dp2px(isFull() ? 232 : 216), -1);
    }
}
