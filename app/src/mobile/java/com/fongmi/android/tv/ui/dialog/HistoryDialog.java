package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.DialogHistoryBinding;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.ui.adapter.ConfigAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class HistoryDialog extends BaseAlertDialog implements ConfigAdapter.OnClickListener {

    private DialogHistoryBinding binding;
    private ConfigListener listener;
    private ConfigAdapter adapter;
    private ItemTouchHelper sortTouchHelper;

    private int type;
    private boolean readOnly;
    private boolean manage;

    public static HistoryDialog create() {
        return new HistoryDialog();
    }

    public HistoryDialog vod() {
        type = 0;
        return this;
    }

    public HistoryDialog live() {
        type = 1;
        return this;
    }

    public HistoryDialog wall() {
        type = 2;
        return this;
    }

    public HistoryDialog readOnly() {
        readOnly = true;
        return this;
    }

    public HistoryDialog manage() {
        manage = true;
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
    }

    private boolean isFull() {
        return getParentFragment() == null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        listener = isFull() ? (ConfigListener) context : (ConfigListener) getParentFragment();
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogHistoryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new ConfigAdapter(this);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        if (isFull()) binding.recycler.setMaxHeight(ResUtil.dp2px(264));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        binding.recycler.setAdapter(adapter.readOnly(readOnly).protectCurrent(manage).addAll(type, getConfig()));
        binding.add.setVisibility(manage ? View.VISIBLE : View.GONE);
        binding.add.setOnClickListener(v -> onAdd());
        if (type == 0 && !readOnly) attachSortHelper();
    }

    private void onAdd() {
        ConfigDialog dialog = ConfigDialog.create();
        if (type == 0) dialog.vod();
        else if (type == 1) dialog.live();
        else dialog.wall();
        if (getParentFragment() != null) dialog.show(getParentFragment());
        else dialog.show(requireActivity());
        dismiss();
    }

    private void attachSortHelper() {
        sortTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean isLongPressDragEnabled() { return false; }
            @Override public boolean onMove(@NonNull RecyclerView view, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                return adapter.drag(from.getBindingAdapterPosition(), to.getBindingAdapterPosition());
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) { }
        });
        sortTouchHelper.attachToRecyclerView(binding.recycler);
    }

    private Config getConfig() {
        return switch (type) {
            case 0 -> VodConfig.get().getConfig();
            case 1 -> LiveConfig.get().getConfig();
            case 2 -> WallConfig.get().getConfig();
            default -> Config.create(type);
        };
    }

    @Override
    public void onTextClick(Config item) {
        listener.setConfig(item);
        dismiss();
    }

    @Override
    public boolean onTextLongClick(ConfigAdapter.ViewHolder holder) {
        if (type != 0 || readOnly || sortTouchHelper == null) return false;
        sortTouchHelper.startDrag(holder);
        return true;
    }

    @Override
    public void onEditClick(Config item) {
        ConfigDialog dialog = ConfigDialog.create().target(item).edit();
        if (type == 0) dialog.vod();
        else if (type == 1) dialog.live();
        else dialog.wall();
        if (getParentFragment() != null) dialog.show(getParentFragment());
        else dialog.show(requireActivity().getSupportFragmentManager(), null);
        dismiss();
    }

    @Override
    public void onDeleteClick(Config item) {
        if (adapter.isProtectedCurrent(item)) {
            Notify.show(R.string.config_current_delete_message);
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.config_delete_title)
                .setMessage(getString(R.string.config_delete_message, item.getDesc()))
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    if (adapter.remove(item) == 0) dismiss();
                })
                .show();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0 && !manage) dismiss();
        else configureWindow();
    }

    private void configureWindow() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        Window window = getDialog().getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(requireContext());
        int width = Math.min(Math.round(ResUtil.getScreenWidth(requireContext()) * (land ? 0.5f : 0.84f)), ResUtil.dp2px(520));
        params.width = Math.max(width, ResUtil.dp2px(320));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
    }
}
