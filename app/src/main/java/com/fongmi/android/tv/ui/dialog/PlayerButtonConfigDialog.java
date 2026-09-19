package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterPlayerButtonConfigBinding;
import com.fongmi.android.tv.databinding.DialogPlayerButtonConfigBinding;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class PlayerButtonConfigDialog extends BaseAlertDialog {

    private DialogPlayerButtonConfigBinding binding;
    private ButtonAdapter adapter;
    private Runnable callback;

    public static void show(Fragment fragment, Runnable callback) {
        PlayerButtonConfigDialog dialog = new PlayerButtonConfigDialog();
        dialog.callback = callback;
        dialog.show(fragment.getChildFragmentManager(), null);
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        PlayerButtonConfigDialog dialog = new PlayerButtonConfigDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogPlayerButtonConfigBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        int screenWidth = ResUtil.getScreenWidth(requireContext());
        int screenHeight = ResUtil.getScreenHeight(requireContext());
        boolean land = ResUtil.isLand(requireContext());
        WindowManager.LayoutParams params = window.getAttributes();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        params.width = (int) (screenWidth * (land ? 0.56f : 0.92f));
        params.height = (int) (screenHeight * (land ? 0.9f : 0.72f));
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
        binding.recycler.post(() -> {
            if (adapter != null) adapter.focus(0, R.id.visible);
            else binding.recycler.requestFocus();
        });
    }

    @Override
    protected void initView() {
        adapter = new ButtonAdapter();
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setItemAnimator(null);
        binding.recycler.setAdapter(adapter);
        if (Util.isMobile()) attachTouchHelper();
        adapter.reload();
        setSummary();
    }

    private void attachTouchHelper() {
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
                return adapter.drag(source.getBindingAdapterPosition(), target.getBindingAdapterPosition());
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        });
        helper.attachToRecyclerView(binding.recycler);
    }

    @Override
    protected void initEvent() {
        binding.reset.setOnClickListener(view -> {
            PlayerButtonSetting.reset();
            adapter.reload();
            setSummary();
            notifyChanged();
        });
        binding.close.setOnClickListener(view -> dismiss());
    }

    private void setSummary() {
        binding.summary.setText(getString(R.string.player_button_config_summary, PlayerButtonSetting.getVisibleCount(), PlayerButtonSetting.getTotalCount()));
    }

    private void notifyChanged() {
        if (callback != null) callback.run();
    }

    private class ButtonAdapter extends RecyclerView.Adapter<ButtonAdapter.ViewHolder> {

        private final List<PlayerButtonSetting.Item> items = new ArrayList<>();

        ButtonAdapter() {
            setHasStableIds(true);
        }

        void reload() {
            items.clear();
            items.addAll(PlayerButtonSetting.getItems());
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(AdapterPlayerButtonConfigBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PlayerButtonSetting.Item item = items.get(position);
            holder.binding.name.setText(item.name());
            holder.binding.visible.setImageResource(item.visible() ? R.drawable.ic_player_button_visible : R.drawable.ic_player_button_hidden);
            holder.binding.visible.setContentDescription(getString(item.visible() ? R.string.setting_show : R.string.setting_hide));
            holder.binding.visible.setSelected(item.visible());
            holder.binding.visible.setActivated(item.visible());
            holder.binding.up.setText(null);
            holder.binding.down.setText(null);
            holder.binding.up.setEnabled(position > 0);
            holder.binding.up.setFocusable(position > 0);
            holder.binding.down.setEnabled(position < items.size() - 1);
            holder.binding.down.setFocusable(position < items.size() - 1);
            holder.binding.top.setEnabled(position > 0);
            holder.binding.top.setFocusable(position > 0);
            holder.binding.root.setAlpha(1f);
            holder.binding.name.setAlpha(item.visible() ? 1f : 0.55f);
            holder.binding.root.setOnClickListener(view -> toggle(holder.getBindingAdapterPosition(), R.id.visible));
            holder.binding.visible.setOnClickListener(view -> toggle(holder.getBindingAdapterPosition(), view.getId()));
            holder.binding.up.setOnClickListener(view -> move(-1, holder.getBindingAdapterPosition(), view.getId()));
            holder.binding.down.setOnClickListener(view -> move(1, holder.getBindingAdapterPosition(), view.getId()));
            holder.binding.top.setOnClickListener(view -> moveToTop(holder.getBindingAdapterPosition(), view.getId()));
            setFocusNavigation(holder);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public long getItemId(int position) {
            if (position < 0 || position >= items.size()) return RecyclerView.NO_ID;
            return items.get(position).id().hashCode() & 0xffffffffL;
        }

        private void toggle(int position, int focusId) {
            if (position == RecyclerView.NO_POSITION || position < 0 || position >= items.size()) return;
            PlayerButtonSetting.Item item = items.get(position);
            boolean visible = !item.visible();
            PlayerButtonSetting.putVisible(item.id(), visible);
            items.set(position, item.withVisible(visible));
            notifyItemChanged(position);
            setSummary();
            focus(position, focusId);
            notifyChanged();
        }

        private void move(int offset, int position, int focusId) {
            if (position == RecyclerView.NO_POSITION) return;
            int target = Math.max(0, Math.min(items.size() - 1, position + offset));
            if (position == target) return;
            PlayerButtonSetting.Item item = items.remove(position);
            items.add(target, item);
            PlayerButtonSetting.putOrder(getIds());
            notifyItemMoved(position, target);
            notifyItemRangeChanged(Math.min(position, target), Math.abs(position - target) + 1);
            focus(target, focusId);
            notifyChanged();
        }

        private void moveToTop(int position, int focusId) {
            if (position == RecyclerView.NO_POSITION || position <= 0) return;
            PlayerButtonSetting.Item item = items.remove(position);
            items.add(0, item);
            PlayerButtonSetting.putOrder(getIds());
            notifyItemMoved(position, 0);
            notifyItemRangeChanged(0, position + 1);
            focus(0, focusId);
            notifyChanged();
        }

        private boolean drag(int fromPosition, int toPosition) {
            if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) return false;
            if (fromPosition < 0 || toPosition < 0 || fromPosition >= items.size() || toPosition >= items.size()) return false;
            if (fromPosition == toPosition) return false;
            PlayerButtonSetting.Item item = items.remove(fromPosition);
            items.add(toPosition, item);
            PlayerButtonSetting.putOrder(getIds());
            notifyItemMoved(fromPosition, toPosition);
            notifyItemRangeChanged(Math.min(fromPosition, toPosition), Math.abs(fromPosition - toPosition) + 1);
            notifyChanged();
            return true;
        }

        private void focus(int position, int focusId) {
            if (position < 0 || position >= items.size()) {
                binding.recycler.requestFocus();
                return;
            }
            binding.recycler.post(() -> requestFocus(position, focusId, true));
        }

        private void requestFocus(int position, int focusId, boolean allowScroll) {
            RecyclerView.ViewHolder viewHolder = binding.recycler.findViewHolderForAdapterPosition(position);
            if (!(viewHolder instanceof ViewHolder holder)) {
                if (allowScroll) {
                    RecyclerView.LayoutManager layoutManager = binding.recycler.getLayoutManager();
                    if (layoutManager instanceof LinearLayoutManager manager) manager.scrollToPositionWithOffset(position, binding.recycler.getPaddingTop());
                    else binding.recycler.scrollToPosition(position);
                    binding.recycler.post(() -> requestFocus(position, focusId, false));
                }
                return;
            }
            if (!requestFocus(holder, focusId)) binding.recycler.requestFocus();
        }

        private boolean requestFocus(ViewHolder holder, int focusId) {
            View view = holder.itemView.findViewById(focusId);
            return requestFocus(view) || requestFocus(holder.binding.visible) || requestFocus(holder.binding.up) || requestFocus(holder.binding.down) || requestFocus(holder.binding.top);
        }

        private boolean requestFocus(View view) {
            return view != null && view.isShown() && view.isEnabled() && view.isFocusable() && view.requestFocus();
        }

        private void setFocusNavigation(ViewHolder holder) {
            boolean up = holder.binding.up.isEnabled();
            boolean down = holder.binding.down.isEnabled();
            boolean top = holder.binding.top.isEnabled();
            holder.binding.visible.setNextFocusLeftId(R.id.visible);
            holder.binding.visible.setNextFocusRightId(up ? R.id.up : down ? R.id.down : top ? R.id.top : R.id.visible);
            holder.binding.up.setNextFocusLeftId(R.id.visible);
            holder.binding.up.setNextFocusRightId(down ? R.id.down : top ? R.id.top : R.id.up);
            holder.binding.down.setNextFocusLeftId(up ? R.id.up : R.id.visible);
            holder.binding.down.setNextFocusRightId(top ? R.id.top : R.id.down);
            holder.binding.top.setNextFocusLeftId(down ? R.id.down : up ? R.id.up : R.id.visible);
            holder.binding.top.setNextFocusRightId(R.id.top);
        }

        private List<String> getIds() {
            List<String> ids = new ArrayList<>();
            for (PlayerButtonSetting.Item item : items) ids.add(item.id());
            return ids;
        }

        private class ViewHolder extends RecyclerView.ViewHolder {

            private final AdapterPlayerButtonConfigBinding binding;

            ViewHolder(AdapterPlayerButtonConfigBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
