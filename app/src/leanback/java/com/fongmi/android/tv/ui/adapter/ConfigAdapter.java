package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.setting.InterfaceOrderStore;
import com.fongmi.android.tv.databinding.AdapterConfigBinding;

import java.util.List;

public class ConfigAdapter extends RecyclerView.Adapter<ConfigAdapter.ViewHolder> {

    private final OnClickListener listener;
    private List<Config> mItems;
    private boolean readOnly;
    private boolean protectCurrent;
    private Config current;

    public ConfigAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onTextClick(Config item);

        boolean onTextLongClick(ViewHolder holder);

        void onEditClick(Config item);

        void onDeleteClick(Config item);
    }

    public ConfigAdapter readOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    public ConfigAdapter protectCurrent(boolean protectCurrent) {
        this.protectCurrent = protectCurrent;
        return this;
    }

    public ConfigAdapter addAll(int type) {
        return addAll(type, null);
    }

    public ConfigAdapter addAll(int type, Config current) {
        this.current = current;
        mItems = type == 0 ? InterfaceOrderStore.sortVodConfigs(Config.getAll(type)) : Config.getAll(type);
        String currentUrl = current == null ? null : current.getUrl();
        if (!readOnly && !protectCurrent && !TextUtils.isEmpty(currentUrl)) mItems.removeIf(item -> TextUtils.equals(item.getUrl(), currentUrl));
        return this;
    }

    private boolean isCurrent(Config item) {
        if (!protectCurrent || current == null) return false;
        if (current.getId() > 0 && item.getId() == current.getId()) return true;
        return current.getType() == item.getType()
                && !TextUtils.isEmpty(current.getInterfaceKey())
                && TextUtils.equals(current.getInterfaceKey(), item.getInterfaceKey());
    }

    public boolean isProtectedCurrent(Config item) {
        return isCurrent(item);
    }

    public int remove(Config item) {
        int position = mItems.indexOf(item);
        if (position == -1) return -1;
        item.delete();
        mItems.remove(position);
        if (item.getType() == 0) InterfaceOrderStore.saveVodConfigs(mItems);
        notifyItemRemoved(position);
        return getItemCount();
    }

    public boolean drag(int from, int to) {
        if (from < 0 || to < 0 || from >= mItems.size() || to >= mItems.size() || from == to) return false;
        Config item = mItems.remove(from);
        mItems.add(to, item);
        notifyItemMoved(from, to);
        if (mItems.size() > 0) InterfaceOrderStore.saveVodConfigs(mItems);
        return true;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterConfigBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Config item = mItems.get(position);
        boolean current = isCurrent(item);
        holder.binding.text.setText(item.getDesc());
        // 当前接口不能重复执行“使用”，但必须保持可聚焦，否则遥控器向下移动时会
        // 跳过整行，用户既看不到当前项焦点，也无法继续到它右侧的编辑/删除操作。
        holder.binding.text.setEnabled(true);
        holder.binding.text.setFocusable(true);
        bindVerticalFocus(holder.binding.text, position);
        holder.binding.text.setOnClickListener(v -> {
            if (!current) listener.onTextClick(item);
        });
        holder.binding.text.setOnLongClickListener(v -> !current && listener.onTextLongClick(holder));
        holder.binding.edit.setVisibility(readOnly ? View.GONE : View.VISIBLE);
        bindVerticalFocus(holder.binding.edit, position);
        holder.binding.edit.setOnClickListener(v -> listener.onEditClick(item));
        holder.binding.delete.setVisibility(readOnly ? View.GONE : View.VISIBLE);
        holder.binding.delete.setAlpha(current ? 0.38f : 1f);
        bindVerticalFocus(holder.binding.delete, position);
        holder.binding.delete.setOnClickListener(v -> listener.onDeleteClick(item));
    }

    private void bindVerticalFocus(View view, int position) {
        view.setOnKeyListener((source, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return moveFocus(source, position + 1);
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return moveFocus(source, position - 1);
            return false;
        });
    }

    private boolean moveFocus(View source, int position) {
        if (position < 0 || position >= getItemCount()) return false;
        RecyclerView recycler = findRecycler(source);
        if (recycler == null) return false;
        requestFocus(recycler, position);
        return true;
    }

    private void requestFocus(RecyclerView recycler, int position) {
        FocusRequest focus = new FocusRequest(recycler, position);
        recycler.stopScroll();
        // 目标行可能尚未挂载；监听它实际挂载的时机，避免提前回调无效后焦点停在原行。
        focus.listenForTarget();
        if (recycler.findViewHolderForAdapterPosition(position) == null) {
            RecyclerView.LayoutManager layoutManager = recycler.getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager linearLayoutManager) {
                linearLayoutManager.scrollToPositionWithOffset(position, recycler.getPaddingTop());
            } else if (layoutManager != null) {
                layoutManager.scrollToPosition(position);
            }
        }
        // 已挂载的目标直接处理；未挂载的目标由 FocusRequest 的 attach 回调处理。
        recycler.postOnAnimation(focus::run);
    }

    private class FocusRequest implements RecyclerView.OnChildAttachStateChangeListener {

        private final RecyclerView recycler;
        private final int position;
        private boolean listening;
        private boolean finished;

        private FocusRequest(RecyclerView recycler, int position) {
            this.recycler = recycler;
            this.position = position;
        }

        private void listenForTarget() {
            recycler.addOnChildAttachStateChangeListener(this);
            listening = true;
        }

        private void run() {
            if (finished) return;
            RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
            if (holder == null) return;
            View target = holder.itemView.findViewById(R.id.text);
            if (target != null && target.getVisibility() == View.VISIBLE && target.isFocusable()) {
                finish(target);
            } else {
                finish(holder.itemView);
            }
        }

        private void finish(View target) {
            finished = true;
            if (listening) {
                recycler.removeOnChildAttachStateChangeListener(this);
                listening = false;
            }
            target.requestFocus();
        }

        @Override
        public void onChildViewAttachedToWindow(@NonNull View view) {
            if (recycler.getChildAdapterPosition(view) == position) run();
        }

        @Override
        public void onChildViewDetachedFromWindow(@NonNull View view) {
        }
    }

    private RecyclerView findRecycler(View source) {
        ViewParent parent = source.getParent();
        while (parent != null && !(parent instanceof RecyclerView)) parent = parent.getParent();
        return parent instanceof RecyclerView ? (RecyclerView) parent : null;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterConfigBinding binding;

        public ViewHolder(@NonNull AdapterConfigBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
