package com.fongmi.android.tv.ui.dialog;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.fragment.app.FragmentActivity;
import androidx.annotation.NonNull;
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
    private ConfigAdapter adapter;
    private ConfigListener listener;
    private boolean readOnly;
    private boolean manage;
    private int type;
    private ItemTouchHelper sortTouchHelper;

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
        if (activity instanceof ConfigListener) listener = (ConfigListener) activity;
    }

    public void show(FragmentActivity activity, ConfigListener listener) {
        show(activity.getSupportFragmentManager(), null);
        this.listener = listener;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogHistoryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new ConfigAdapter(this);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
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
        if (listener == null) return;
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
        else dialog.show(requireActivity());
        dismiss();
    }

    @Override
    public void onDeleteClick(Config item) {
        if (adapter.isProtectedCurrent(item)) {
            Notify.show(R.string.config_current_delete_message);
            return;
        }
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.config_delete_title)
                .setMessage(getString(R.string.config_delete_message, item.getDesc()))
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (target, which) -> {
                    if (adapter.remove(item) == 0) dismiss();
                })
                .create();
        dialog.setOnShowListener(target -> dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).requestFocus());
        dialog.show();
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
        int screenWidth = ResUtil.getScreenWidth(requireContext());
        int screenHeight = ResUtil.getScreenHeight(requireContext());
        int width = screenWidth - ResUtil.dp2px(48);
        int height = screenHeight - ResUtil.dp2px(48);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = width;
        params.height = height;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
        binding.getRoot().setMinimumHeight(height);
        ViewGroup.LayoutParams recyclerParams = binding.recycler.getLayoutParams();
        recyclerParams.height = 0;
        if (recyclerParams instanceof androidx.appcompat.widget.LinearLayoutCompat.LayoutParams linearParams) linearParams.weight = 1;
        binding.recycler.setLayoutParams(recyclerParams);
    }
}
