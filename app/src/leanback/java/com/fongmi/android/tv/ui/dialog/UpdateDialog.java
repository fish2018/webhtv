package com.fongmi.android.tv.ui.dialog;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Update;
import com.fongmi.android.tv.databinding.DialogUpdateBinding;
import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.utils.AppVersion;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.MarkdownText;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class UpdateDialog extends BaseAlertDialog {

    private static final int FULLSCREEN_INSET_DP = 32;

    private DialogUpdateBinding binding;
    private UpdateListener listener;
    private Update stable;
    private Update beta;
    private String selected = Update.CHANNEL_STABLE;
    private boolean stableExpanded = true;
    private boolean betaExpanded;
    private boolean downloading;

    public static UpdateDialog create() {
        return new UpdateDialog();
    }

    public UpdateDialog stable(Update stable) {
        this.stable = stable;
        return this;
    }

    public UpdateDialog beta(Update beta) {
        this.beta = beta;
        return this;
    }

    public UpdateDialog selected(String selected) {
        this.selected = selected;
        boolean betaAvailable = hasBeta();
        this.stableExpanded = !betaAvailable && !Update.CHANNEL_BETA.equals(selected);
        this.betaExpanded = betaAvailable && Update.CHANNEL_BETA.equals(selected);
        return this;
    }

    public UpdateDialog listener(UpdateListener listener) {
        this.listener = listener;
        return this;
    }

    public UpdateDialog show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
        return this;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogUpdateBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog_NoInset).setView(getBinding().getRoot()).setCancelable(false);
    }

    @Override
    protected void initView() {
        binding.progress.setMax(100);
        render();
    }

    @Override
    protected void initEvent() {
        binding.close.setOnClickListener(this::close);
        binding.stableItem.setOnClickListener(view -> toggle(Update.CHANNEL_STABLE));
        binding.betaItem.setOnClickListener(view -> toggle(Update.CHANNEL_BETA));
        binding.stableItem.setOnKeyListener((view, keyCode, event) -> onItemKey(Update.CHANNEL_STABLE, view, keyCode, event));
        binding.betaItem.setOnKeyListener((view, keyCode, event) -> onItemKey(Update.CHANNEL_BETA, view, keyCode, event));
        binding.stableConfirm.setOnClickListener(view -> update(Update.CHANNEL_STABLE, view));
        binding.betaConfirm.setOnClickListener(view -> update(Update.CHANNEL_BETA, view));
        binding.cancel.setOnClickListener(this::action);
    }

    @Override
    public void onStart() {
        super.onStart();
        setCancelable(false);
        if (getDialog() != null) {
            getDialog().setCanceledOnTouchOutside(false);
            getDialog().setOnKeyListener((dialog, keyCode, event) -> onDialogKey(keyCode, event));
        }
        clearWindowInset();
        configureDialogLayout();
        binding.stableItem.requestFocus();
    }

    private void select(String channel) {
        selected = channel;
        if (listener != null) listener.onChannel(channel);
    }

    private void toggle(String channel) {
        if (isExpanded(channel)) {
            update(channel, getItem(channel));
            return;
        }
        selected = channel;
        stableExpanded = Update.CHANNEL_STABLE.equals(channel);
        betaExpanded = Update.CHANNEL_BETA.equals(channel);
        if (listener != null) listener.onChannel(channel);
        render();
    }

    private void action(View view) {
        if (downloading) {
            if (listener != null) listener.onCancel(view);
            return;
        }
        Update update = getSelected();
        if (update != null && update.hasUpdate()) update(selected, view);
        else if (listener != null) listener.onCancel(view);
    }

    private void close(View view) {
        if (downloading) return;
        if (listener != null) listener.onClose();
        dismissAllowingStateLoss();
    }

    private void update(String channel, View view) {
        select(channel);
        if (listener != null) listener.onConfirm(view);
    }

    private void render() {
        normalizeSelection();
        binding.betaItem.setVisibility(hasBeta() ? View.VISIBLE : View.GONE);
        renderItem(Update.CHANNEL_STABLE, stable);
        if (hasBeta()) renderItem(Update.CHANNEL_BETA, beta);
        renderAction();
        updateFocusLinks();
        binding.close.setVisibility(View.VISIBLE);
        binding.progressPanel.setVisibility(View.GONE);
        downloading = false;
    }

    private void renderItem(String channel, Update update) {
        boolean stableChannel = Update.CHANNEL_STABLE.equals(channel);
        boolean expanded = stableChannel ? stableExpanded : betaExpanded;
        View item = stableChannel ? binding.stableItem : binding.betaItem;
        View content = stableChannel ? binding.stableContent : binding.betaContent;
        item.setSelected(expanded);
        content.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (stableChannel) {
            binding.stableVersion.setText(getVersion(update));
            binding.stableStatus.setText(getStatus(update));
            binding.stableExpand.setVisibility(hasBeta() && !expanded ? View.VISIBLE : View.GONE);
            binding.stableExpand.setText(R.string.update_expand);
            binding.stableDesc.setText(MarkdownText.render(getBody(update), getString(R.string.update_no_notes)));
            binding.stableConfirm.setEnabled(update != null && update.hasUpdate());
            binding.stableConfirm.setText(R.string.update_confirm);
            binding.stableConfirm.setVisibility(View.GONE);
        } else {
            binding.betaVersion.setText(getVersion(update));
            binding.betaStatus.setText(getStatus(update));
            binding.betaExpand.setVisibility(!expanded ? View.VISIBLE : View.GONE);
            binding.betaExpand.setText(R.string.update_expand);
            binding.betaDesc.setText(MarkdownText.render(getBody(update), getString(R.string.update_no_notes)));
            binding.betaConfirm.setEnabled(update != null && update.hasUpdate());
            binding.betaConfirm.setText(R.string.update_confirm);
            binding.betaConfirm.setVisibility(View.GONE);
        }
    }

    private void renderAction() {
        Update update = getSelected();
        if (update == null || !update.hasUpdate()) binding.cancel.setText(R.string.about_acknowledge);
        else binding.cancel.setText(hasBeta() ? getString(R.string.update_confirm_channel, getSelectedName()) : getString(R.string.update_confirm));
    }

    private void updateFocusLinks() {
        int nextAfterStable = hasBeta() ? R.id.betaItem : R.id.cancel;
        binding.close.setFocusable(true);
        binding.close.setNextFocusUpId(R.id.close);
        binding.close.setNextFocusLeftId(R.id.close);
        binding.close.setNextFocusRightId(R.id.close);
        binding.close.setNextFocusDownId(R.id.stableItem);
        binding.stableItem.setNextFocusUpId(R.id.close);
        binding.stableItem.setNextFocusDownId(nextAfterStable);
        binding.stableConfirm.setNextFocusDownId(nextAfterStable);
        binding.betaItem.setNextFocusUpId(R.id.stableItem);
        binding.betaItem.setNextFocusDownId(R.id.cancel);
        binding.betaConfirm.setNextFocusUpId(R.id.betaItem);
        binding.betaConfirm.setNextFocusDownId(R.id.cancel);
        binding.cancel.setNextFocusUpId(hasBeta() ? R.id.betaItem : R.id.stableItem);
        binding.cancel.setNextFocusDownId(R.id.cancel);
        binding.cancel.setNextFocusLeftId(R.id.cancel);
        binding.cancel.setNextFocusRightId(R.id.cancel);
    }

    private boolean onDialogKey(int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_BACK || event.getAction() != KeyEvent.ACTION_UP) return false;
        if (downloading) action(binding.cancel);
        else close(binding.close);
        return true;
    }

    private boolean onItemKey(String channel, View view, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || !isExpanded(channel)) return false;
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            update(channel, view);
            return true;
        }
        if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
        if (!hasLongNotes(channel)) return false;
        int direction = keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1;
        if (!binding.listScroll.canScrollVertically(direction)) return false;
        binding.listScroll.smoothScrollBy(0, direction * ResUtil.dp2px(96));
        return true;
    }

    private boolean hasLongNotes(String channel) {
        return (Update.CHANNEL_BETA.equals(channel) ? binding.betaDesc : binding.stableDesc).getLineCount() > 10;
    }

    private boolean isExpanded(String channel) {
        return Update.CHANNEL_BETA.equals(channel) ? betaExpanded : stableExpanded;
    }

    private View getItem(String channel) {
        return Update.CHANNEL_BETA.equals(channel) ? binding.betaItem : binding.stableItem;
    }

    private boolean hasBeta() {
        return beta != null && beta.hasManifest();
    }

    private void normalizeSelection() {
        if (hasBeta()) return;
        selected = Update.CHANNEL_STABLE;
        stableExpanded = true;
        betaExpanded = false;
    }

    private Update getSelected() {
        return Update.CHANNEL_BETA.equals(selected) ? beta : stable;
    }

    private String getSelectedName() {
        return getString(Update.CHANNEL_BETA.equals(selected) ? R.string.update_channel_beta : R.string.update_channel_stable);
    }

    private void clearWindowInset() {
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // ColorDrawable.getPadding() 返回 false，setBackgroundDrawable 不会重置已有 padding，
        // 所以此前 InsetDrawable 写进 DecorView 的 inset padding 必须显式清掉，否则窗口高度
        // 与内容可用高度会差出一个 inset，底部的进度面板和按钮会被裁掉。
        window.getDecorView().setPadding(0, 0, 0, 0);
    }

    /**
     * 铺满全屏，不再手算窗口高度。
     *
     * 原先要同时算对屏宽高、content 高度、visibleFrame、Material 背景 inset 和 chrome 高度，
     * 每项都有设备差异，任何一项出错就裁掉底部的进度面板和取消按钮。铺满后这些变量全部无关：
     * listScroll 带 layout_weight 吃掉剩余空间，progressPanel 和 cancel 作为固定高子项永远保住。
     */
    private void configureWindow() {
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        window.setAttributes(params);
        // XML 里 root 是 wrap_content，铺满全屏必须显式改成 match_parent，
        // 否则窗口虽然全屏、内容仍只占中间一小块。
        ViewGroup.LayoutParams rootParams = binding.getRoot().getLayoutParams();
        if (rootParams != null) {
            rootParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            rootParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            binding.getRoot().setLayoutParams(rootParams);
        }
    }

    /**
     * 电视存在 overscan，正文不能贴边。在 XML 既有 padding 之上再加一圈安全边距。
     *
     * 用 tag 记在 root 上而不是记在 Fragment 实例字段上：配置变更（HDMI 分辨率切换、字体缩放）
     * 后 Fragment 实例保留、binding 却会重新 inflate，实例字段会让新 root 永远拿不到边距。
     */
    private void applyFullscreenInset() {
        View root = binding.getRoot();
        if (Boolean.TRUE.equals(root.getTag(R.id.update_fullscreen_inset))) return;
        root.setTag(R.id.update_fullscreen_inset, Boolean.TRUE);
        int inset = ResUtil.dp2px(FULLSCREEN_INSET_DP);
        root.setPadding(
                root.getPaddingLeft() + inset,
                root.getPaddingTop() + inset,
                root.getPaddingRight() + inset,
                root.getPaddingBottom() + inset);
    }

    private void configureDialogLayout() {
        applyFullscreenInset();
        binding.getRoot().requestLayout();
        configureWindow();
    }

    private String getVersion(Update update) {
        return update != null && update.hasManifest() ? AppVersion.stripPrefix(update.name) : getString(R.string.update_status_unavailable);
    }

    private String getStatus(Update update) {
        if (update == null || !update.hasManifest()) return getString(R.string.update_status_unavailable);
        return update.hasUpdate() ? getString(R.string.update_status_available) : getString(R.string.update_status_latest);
    }

    private String getBody(Update update) {
        if (update == null || !update.hasManifest()) return getString(R.string.update_channel_unavailable);
        if (!TextUtils.isEmpty(update.getText())) return update.getText();
        if (!update.hasUpdate()) return getString(R.string.update_channel_latest);
        return update.getText();
    }

    public boolean setProgress(int progress) {
        return setProgress(progress, 0, 0, 0, 0);
    }

    public boolean setProgress(int progress, long bytes, long total, long speed, long elapsed) {
        if (!isProgressTarget()) return false;
        boolean requestFocus = !downloading;
        downloading = true;
        boolean indeterminate = progress < 0;
        int value = Math.max(0, Math.min(100, progress));
        binding.stableItem.setEnabled(false);
        binding.betaItem.setEnabled(false);
        binding.stableConfirm.setEnabled(false);
        binding.betaConfirm.setEnabled(false);
        binding.cancel.setEnabled(true);
        binding.close.setVisibility(View.GONE);
        binding.progressPanel.setVisibility(View.VISIBLE);
        binding.progress.setIndeterminate(indeterminate);
        if (!indeterminate) binding.progress.setProgress(value);
        binding.progressText.setText(getProgressText(indeterminate, value, bytes, total, speed, elapsed));
        binding.cancel.setText(R.string.update_cancel);
        // 全屏 + listScroll 带 weight，progressPanel 由 VISIBLE 引起的高度变化由 LinearLayout
        // 自行吸收，不再需要探测面板高度后重设窗口——那套逻辑还会因文案逐秒变长变短而抖动。
        if (requestFocus) binding.cancel.requestFocus();
        return true;
    }

    private boolean isProgressTarget() {
        return binding != null && isAdded() && getContext() != null && getDialog() != null;
    }

    private String getProgressText(boolean indeterminate, int value, long bytes, long total, long speed, long elapsed) {
        if (speed <= 0 || elapsed <= 0) return indeterminate ? getString(R.string.update_downloading_unknown) : getString(R.string.update_downloading, value);
        String speedText = FileUtil.byteCountToDisplaySize(speed);
        String elapsedText = formatDuration(elapsed);
        if (!indeterminate && total > 0 && bytes >= 0) {
            long remaining = Math.max(0, total - bytes) * 1000 / speed;
            return getString(R.string.update_downloading_detail_remaining, value, speedText, formatDuration(remaining), elapsedText);
        }
        return indeterminate ? getString(R.string.update_downloading_detail_unknown, speedText, elapsedText) : getString(R.string.update_downloading_detail, value, speedText, elapsedText);
    }

    private String formatDuration(long time) {
        String text = Util.timeMs(Math.max(0, time));
        return TextUtils.isEmpty(text) ? "00:00" : text;
    }
}
