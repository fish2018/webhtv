package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogMpvConfigManagerBinding;
import com.fongmi.android.tv.player.mpv.MpvConfigStore;
import com.fongmi.android.tv.ui.adapter.MpvConfigProfileAdapter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;

public class MpvConfigDialog extends BaseAlertDialog implements MpvConfigProfileAdapter.Listener, MpvConfigCreateDialog.Listener {

    private DialogMpvConfigManagerBinding binding;
    private MpvConfigProfileAdapter adapter;
    private Runnable callback;
    private String target = MpvConfigStore.TARGET_MPV_CONF;

    public static void show(Fragment fragment, Runnable callback) {
        MpvConfigDialog dialog = new MpvConfigDialog();
        dialog.callback = callback;
        dialog.show(fragment.getChildFragmentManager(), "mpv-config-manager");
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        MpvConfigDialog dialog = new MpvConfigDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), "mpv-config-manager");
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogMpvConfigManagerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new MpvConfigProfileAdapter(this);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setAdapter(adapter);
        setupTabs();
        reload();
        setupTvFocus();
    }

    @Override
    protected void initEvent() {
        binding.close.setOnClickListener(view -> dismiss());
        binding.create.setOnClickListener(view -> MpvConfigCreateDialog.show(getChildFragmentManager(), target, this));
    }

    private void setupTabs() {
        binding.tabs.setTabMode(TabLayout.MODE_FIXED);
        binding.tabs.setTabGravity(TabLayout.GRAVITY_FILL);
        binding.tabs.setSelectedTabIndicatorColor(Color.parseColor("#1A73E8"));
        binding.tabs.setTabTextColors(Color.parseColor("#5F6368"), Color.parseColor("#1A73E8"));
        binding.tabs.setTabRippleColor(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        binding.tabs.setUnboundedRipple(false);
        for (String label : targets()) binding.tabs.addTab(binding.tabs.newTab().setText(label));
        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String[] targets = targets();
                if (tab.getPosition() < 0 || tab.getPosition() >= targets.length) return;
                target = targets[tab.getPosition()];
                reload();
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    private void setupTvFocus() {
        if (!Util.isLeanback()) return;
        tvFocusable(binding.create);
        tvFocusable(binding.close);
        binding.recycler.setFocusable(false);
        binding.create.setNextFocusRightId(R.id.close);
        binding.close.setNextFocusLeftId(R.id.create);
        View.OnKeyListener headerKey = (view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN || keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            return focusSelectedTab();
        };
        binding.create.setOnKeyListener(headerKey);
        binding.close.setOnKeyListener(headerKey);
        binding.tabs.post(this::configureTabFocus);
    }

    private void configureTabFocus() {
        if (!Util.isLeanback() || binding == null || binding.tabs.getChildCount() == 0) return;
        View strip = binding.tabs.getChildAt(0);
        if (!(strip instanceof ViewGroup)) return;
        ViewGroup tabs = (ViewGroup) strip;
        for (int i = 0; i < tabs.getChildCount(); i++) {
            View tab = tabs.getChildAt(i);
            tvFocusable(tab);
            tab.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return binding.create.requestFocus();
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return focusFirstProfile();
                return false;
            });
        }
    }

    private boolean focusSelectedTab() {
        if (binding == null || binding.tabs.getChildCount() == 0) return false;
        View strip = binding.tabs.getChildAt(0);
        if (!(strip instanceof ViewGroup)) return false;
        ViewGroup tabs = (ViewGroup) strip;
        int position = Math.max(0, binding.tabs.getSelectedTabPosition());
        if (position >= tabs.getChildCount()) position = 0;
        View tab = tabs.getChildAt(position);
        return tab != null && tab.requestFocus();
    }

    private boolean focusFirstProfile() {
        if (adapter.getItemCount() == 0) return binding.create.requestFocus();
        RecyclerView.ViewHolder holder = binding.recycler.findViewHolderForAdapterPosition(0);
        if (holder != null) return holder.itemView.requestFocus();
        binding.recycler.scrollToPosition(0);
        return true;
    }

    private static void tvFocusable(View view) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
    }

    private String[] targets() {
        return new String[]{MpvConfigStore.TARGET_MPV_CONF, MpvConfigStore.TARGET_INPUT_CONF, MpvConfigStore.TARGET_SCRIPTS};
    }

    private void reload() {
        List<MpvConfigStore.ConfigProfile> profiles = MpvConfigStore.profiles(target);
        boolean scripts = MpvConfigStore.TARGET_SCRIPTS.equals(target);
        adapter.submit(profiles, scripts);
        binding.recycler.setVisibility(profiles.isEmpty() ? View.GONE : View.VISIBLE);
        binding.empty.setVisibility(profiles.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onSelect(MpvConfigStore.ConfigProfile profile) {
        if (MpvConfigStore.TARGET_SCRIPTS.equals(target)) {
            openEditor(profile);
            return;
        }
        if (profile.active) return;
        String selectedTarget = target;
        runAsync(() -> {
            MpvConfigStore.selectProfile(selectedTarget, profile.id);
            return profile.name;
        }, name -> {
            if (TextUtils.equals(target, selectedTarget)) reload();
            Notify.show(getString(R.string.mpv_config_switched, name));
            notifyChanged();
        });
    }

    @Override
    public void onMore(View anchor, MpvConfigStore.ConfigProfile profile) {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.shape_mpv_action_menu);
        PopupWindow popup = new PopupWindow(content, ResUtil.dp2px(176), WindowManager.LayoutParams.WRAP_CONTENT, true);
        content.addView(actionItem(R.string.mpv_config_edit, R.drawable.ic_git_cloud_edit, false, () -> {
            popup.dismiss();
            openEditor(profile);
        }));
        if (!profile.isDefault() && !profile.isCustomButton() && !MpvConfigStore.TARGET_SCRIPTS.equals(target)) content.addView(actionItem(R.string.mpv_config_rename, R.drawable.ic_mpv_rename, false, () -> {
            popup.dismiss();
            showRename(profile);
        }));
        if (!profile.isDefault()) content.addView(actionItem(R.string.mpv_config_delete, R.drawable.ic_setting_delete, true, () -> {
            popup.dismiss();
            confirmDelete(profile);
        }));
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(ResUtil.dp2px(10));
        showActionMenu(popup, content, anchor);
        content.post(() -> {
            if (content.getChildCount() > 0) content.getChildAt(0).requestFocus();
        });
    }

    private void showActionMenu(PopupWindow popup, LinearLayout content, View anchor) {
        int menuWidth = ResUtil.dp2px(176);
        int menuHeight = ResUtil.dp2px(48 * content.getChildCount());
        int edgeMargin = ResUtil.dp2px(12);
        int[] location = new int[2];
        Rect visibleFrame = new Rect();
        anchor.getLocationOnScreen(location);
        anchor.getWindowVisibleDisplayFrame(visibleFrame);
        int belowTop = location[1] + anchor.getHeight() / 2;
        boolean showAbove = belowTop + menuHeight + edgeMargin > visibleFrame.bottom;
        int yOffset = showAbove ? -menuHeight - anchor.getHeight() / 2 : -anchor.getHeight() / 2;
        popup.showAsDropDown(anchor, anchor.getWidth() - menuWidth, yOffset);
    }

    private View actionItem(int textRes, int iconRes, boolean danger, Runnable action) {
        MaterialTextView item = new MaterialTextView(requireContext());
        item.setText(textRes);
        item.setTextSize(14);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(ResUtil.dp2px(14), 0, ResUtil.dp2px(14), 0);
        item.setCompoundDrawablePadding(ResUtil.dp2px(12));
        item.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
        int color = Color.parseColor(danger ? "#C5221F" : "#202124");
        item.setTextColor(color);
        TextViewCompat.setCompoundDrawableTintList(item, android.content.res.ColorStateList.valueOf(color));
        TypedValue value = new TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        item.setBackgroundResource(value.resourceId);
        item.setClickable(true);
        item.setFocusable(true);
        item.setFocusableInTouchMode(Util.isLeanback());
        item.setOnClickListener(view -> action.run());
        item.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(48)));
        return item;
    }

    private void confirmDelete(MpvConfigStore.ConfigProfile profile) {
        ChoiceDialog.showConfirm(this, R.string.mpv_config_delete_title, getString(R.string.mpv_config_delete_message, profile.name), R.string.mpv_config_delete, () -> runAsync(() -> MpvConfigStore.deleteProfile(target, profile.id), ignored -> {
            reload();
            notifyChanged();
        }));
    }

    private void showRename(MpvConfigStore.ConfigProfile profile) {
        String selectedTarget = target;
        MpvConfigRenameDialog.show(getChildFragmentManager(), profile.name, name -> {
            try {
                MpvConfigStore.renameProfile(selectedTarget, profile.id, name);
                if (TextUtils.equals(target, selectedTarget)) reload();
                Notify.show(R.string.mpv_config_renamed);
                notifyChanged();
                return true;
            } catch (Throwable e) {
                Notify.show(message(e));
                return false;
            }
        });
    }

    @Override
    public void onText(String name, boolean buttonEnabled, String trigger) {
        String template;
        if (MpvConfigStore.TARGET_SCRIPTS.equals(target)) template = "-- WebHTV MPV script\n\n";
        else if (MpvConfigStore.TARGET_INPUT_CONF.equals(target)) template = "# WebHTV input.conf\n\n";
        else template = "# WebHTV mpv.conf\n\n";
        String displayName = TextUtils.isEmpty(name) ? getString(R.string.mpv_config_untitled) : name;
        showEditor(null, displayName, template, true, buttonEnabled, trigger);
    }

    private void onScriptButtonSaved() {
        reload();
        notifyChanged();
    }

    @Override
    public void onImport(String name, String path, boolean buttonEnabled, String trigger) {
        String selectedTarget = target;
        runAsync(() -> {
            String id = MpvConfigStore.importProfile(selectedTarget, path, name);
            if (MpvConfigStore.TARGET_SCRIPTS.equals(selectedTarget)) MpvConfigStore.saveScriptTrigger(id, buttonEnabled, trigger);
            else MpvConfigStore.selectProfile(selectedTarget, id);
            return id;
        }, id -> {
            if (TextUtils.equals(target, selectedTarget)) reload();
            Notify.show(R.string.mpv_config_profile_saved);
            notifyChanged();
        });
    }

    private void openEditor(MpvConfigStore.ConfigProfile profile) {
        Notify.progress(requireContext());
        Task.execute(() -> {
            try {
                String content = MpvConfigStore.profileContent(target, profile.id);
                App.post(() -> {
                    Notify.dismiss();
                    if (MpvConfigStore.TARGET_SCRIPTS.equals(target)) {
                        MpvConfigCreateDialog.showScriptSettings(getChildFragmentManager(), profile.id,
                                profile.name, content, MpvConfigStore.scriptButton(profile.id), this::onScriptButtonSaved);
                    } else {
                        String name = profile.isDefault() ? getString(R.string.mpv_config_default_copy) : profile.name;
                        showEditor(profile.id, name, content, profile.isDefault(), false, "startup");
                    }
                });
            } catch (Throwable e) {
                App.post(() -> {
                    Notify.dismiss();
                    Notify.show(message(e));
                });
            }
        });
    }

    private void showEditor(String id, String name, String content, boolean creating, boolean buttonEnabled, String trigger) {
        MpvConfigEditorDialog.show(getChildFragmentManager(), name, content, creating, text -> {
            try {
                String savedId = MpvConfigStore.saveTextProfile(target, id, name, text);
                if (creating) {
                    if (MpvConfigStore.TARGET_SCRIPTS.equals(target)) MpvConfigStore.saveScriptTrigger(savedId, buttonEnabled, trigger);
                    else MpvConfigStore.selectProfile(target, savedId);
                }
                if (binding != null) binding.getRoot().post(this::reload);
                Notify.show(R.string.mpv_config_profile_saved);
                notifyChanged();
                return true;
            } catch (Throwable e) {
                Notify.show(message(e));
                return false;
            }
        });
    }

    private void notifyChanged() {
        if (callback != null) callback.run();
    }

    private <T> void runAsync(ThrowingSupplier<T> supplier, Success<T> success) {
        Notify.progress(requireContext());
        Task.execute(() -> {
            try {
                T value = supplier.get();
                App.post(() -> {
                    Notify.dismiss();
                    success.accept(value);
                });
            } catch (Throwable e) {
                App.post(() -> {
                    Notify.dismiss();
                    Notify.show(message(e));
                });
            }
        });
    }

    private String message(Throwable error) {
        return TextUtils.isEmpty(error.getMessage()) ? error.getClass().getSimpleName() : error.getMessage();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) return;
        boolean land = ResUtil.isLand(requireContext());
        WindowManager.LayoutParams params = window.getAttributes();
        float width = Util.isLeanback() ? 0.68f : land ? 0.62f : 0.94f;
        params.width = Math.min((int) (ResUtil.getScreenWidth(requireContext()) * width), ResUtil.dp2px(760));
        params.height = (int) (ResUtil.getScreenHeight(requireContext()) * (land ? 0.88f : 0.82f));
        params.dimAmount = 0.58f;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
        ViewGroup.LayoutParams rootParams = binding.getRoot().getLayoutParams();
        if (rootParams != null) {
            rootParams.height = params.height;
            binding.getRoot().setLayoutParams(rootParams);
        }
        binding.getRoot().post(() -> window.setLayout(params.width, params.height));
        if (Util.isLeanback()) binding.create.post(() -> binding.create.requestFocus());
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private interface Success<T> {
        void accept(T value);
    }
}
