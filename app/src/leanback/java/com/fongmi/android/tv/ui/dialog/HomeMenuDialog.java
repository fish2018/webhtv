package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterHomeMenuBinding;
import com.fongmi.android.tv.databinding.DialogHomeMenuBinding;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 首页菜单键的「选项弹窗」，列出除自身之外的其它菜单键动作供直接选择。
 */
public class HomeMenuDialog extends BaseAlertDialog {

    private static final String TAG = "home_menu_dialog";
    // 9 项按 3 列排布，正好一屏显示完，与参考实现一致
    private static final int GRID_COUNT = 3;

    private DialogHomeMenuBinding binding;
    private Listener listener;

    public interface Listener {

        /**
         * @param index select_home_menu_key 中的下标，取值 1..9
         */
        void onHomeMenuItem(int index);
    }

    public static HomeMenuDialog create() {
        return new HomeMenuDialog();
    }

    public void show(FragmentActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        // 菜单键连按时避免叠开多个弹窗
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) {
            if (fragment instanceof HomeMenuDialog) return;
        }
        if (activity instanceof Listener) listener = (Listener) activity;
        show(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogHomeMenuBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        // 列数由 dialog_home_menu.xml 的 spanCount 声明，这里只需与之对齐的间距
        binding.recycler.addItemDecoration(new SpaceItemDecoration(GRID_COUNT, 16));
        binding.recycler.setAdapter(new MenuAdapter());
    }

    @Override
    public void onStart() {
        super.onStart();
        setWidth(0.6f);
        binding.recycler.post(() -> {
            RecyclerView.ViewHolder holder = binding.recycler.findViewHolderForAdapterPosition(0);
            if (holder == null) binding.recycler.requestFocus();
            else holder.itemView.requestFocus();
        });
    }

    private void onItemClick(int position) {
        // position 为 NO_POSITION 时不能继续，否则 position + 1 == 0 会再次弹出本弹窗
        if (position == RecyclerView.NO_POSITION) return;
        Listener target = getListener();
        dismiss();
        if (target != null) target.onHomeMenuItem(position + 1);
    }

    /**
     * 进程恢复后弹窗由系统重建，listener 会丢失，此时回退到宿主 Activity。
     */
    private Listener getListener() {
        if (listener != null) return listener;
        return getActivity() instanceof Listener ? (Listener) getActivity() : null;
    }

    private class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.ViewHolder> {

        private final List<String> items;

        MenuAdapter() {
            items = new ArrayList<>(Arrays.asList(ResUtil.getStringArray(R.array.select_home_menu_key)));
            if (!items.isEmpty()) items.remove(0);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(AdapterHomeMenuBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.binding.text.setText(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private class ViewHolder extends RecyclerView.ViewHolder {

            private final AdapterHomeMenuBinding binding;

            ViewHolder(@NonNull AdapterHomeMenuBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
                binding.text.setOnClickListener(view -> onItemClick(getBindingAdapterPosition()));
            }
        }
    }
}
