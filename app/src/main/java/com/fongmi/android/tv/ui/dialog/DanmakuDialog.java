package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.databinding.DialogDanmakuBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.ui.adapter.DanmakuAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Util;

public final class DanmakuDialog extends BaseBottomSheetDialog implements DanmakuAdapter.OnClickListener {

    private final DanmakuAdapter adapter;
    private DialogDanmakuBinding binding;
    private PlayerManager player;
    private String siteKey;
    private String vodId;
    private String rawTitle;
    private String episodeName;

    public interface Host {

        boolean isDanmakuFullscreen();
    }

    public static DanmakuDialog create() {
        return new DanmakuDialog();
    }

    public DanmakuDialog() {
        this.adapter = new DanmakuAdapter(this);
    }

    public DanmakuDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public DanmakuDialog identity(String siteKey, String vodId, String rawTitle, String episodeName) {
        this.siteKey = clean(siteKey);
        this.vodId = clean(vodId);
        this.rawTitle = clean(rawTitle);
        this.episodeName = clean(episodeName);
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof DanmakuDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDanmakuBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter.addAll(player.getDanmakus()));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.recycler.post(() -> binding.recycler.scrollToPosition(adapter.getSelected()));
        binding.recycler.setVisibility(adapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
        binding.search.setVisibility(player.getMetadata() == null || !DanmakuSetting.hasValidApiUrl() ? View.GONE : View.VISIBLE);
        binding.load.setChecked(DanmakuSetting.isLoad());
        updateLoadUi(DanmakuSetting.isLoad());
    }

    @Override
    protected void initEvent() {
        binding.search.setOnClickListener(this::onSearch);
        binding.load.setOnCheckedChangeListener((button, checked) -> onLoadChanged(checked));
        binding.choose.setOnClickListener(this::onChoose);
        binding.setting.setOnClickListener(this::onSetting);
    }

    private void onLoadChanged(boolean enabled) {
        DanmakuSetting.putLoad(enabled);
        DanmakuSetting.putShow(enabled);
        player.setDanmakuEnabled(enabled);
        updateLoadUi(enabled);
    }

    private void updateLoadUi(boolean enabled) {
        binding.recycler.setAlpha(enabled ? 1f : 0.45f);
    }

    private void onSearch(View view) {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        dismissAllowingStateLoss();
        if (shouldUseInputDialog(activity)) DanmakuSearchInputDialog.create().player(player).identity(siteKey, vodId, rawTitle, episodeName).restoreParent(true).show(activity);
        else DanmakuSearchDialog.create().player(player).identity(siteKey, vodId, rawTitle, episodeName).restoreParent(true).show(activity);
    }

    private boolean shouldUseInputDialog(FragmentActivity activity) {
        return Util.isMobile() && (!(activity instanceof Host) || !((Host) activity).isDanmakuFullscreen());
    }

    public void refresh() {
        if (binding == null || player == null) return;
        adapter.clear();
        adapter.addAll(player.getDanmakus());
        binding.recycler.setVisibility(adapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
        if (adapter.getItemCount() > 0) binding.recycler.post(() -> binding.recycler.scrollToPosition(adapter.getSelected()));
    }

    private void onChoose(View view) {
        FileChooser.from(launcher).show(new String[]{"text/*"});
        player.pause();
    }

    private void onSetting(View view) {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        dismissAllowingStateLoss();
        DanmakuSettingDialog.create().player(player).restoreParent(true).show(activity);
    }

    @Override
    public void onItemClick(Danmaku item) {
        player.setDanmaku(item.isSelected() ? Danmaku.empty() : item);
        dismiss();
    }

    @Override
    protected boolean transparent() {
        return true;
    }

    @Override
    protected boolean stableOverlay() {
        return true;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        player.setDanmaku(Danmaku.from(FileChooser.getPathFromUri(result.getData().getData())));
        dismiss();
    });
}
