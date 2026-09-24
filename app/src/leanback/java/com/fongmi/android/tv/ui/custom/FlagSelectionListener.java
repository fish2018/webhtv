package com.fongmi.android.tv.ui.custom;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;

public final class FlagSelectionListener extends OnChildViewHolderSelectedListener implements Runnable {

    private final HorizontalGridView view;
    private final FlagAdapter adapter;
    private final FlagAdapter.OnClickListener listener;
    private Flag pendingItem;

    public FlagSelectionListener(HorizontalGridView view, FlagAdapter adapter, FlagAdapter.OnClickListener listener) {
        this.view = view;
        this.adapter = adapter;
        this.listener = listener;
    }

    @Override
    public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
        view.removeCallbacks(this);
        pendingItem = position >= 0 && position < adapter.getItemCount() ? adapter.get(position) : null;
        // Leanback may dispatch selection inside layout. The whole route switch can
        // refresh this grid through focus changes and seamless episode selection.
        if (pendingItem != null) view.post(this);
    }

    @Override
    public void run() {
        if (!view.isAttachedToWindow() || view.getAdapter() != adapter) {
            pendingItem = null;
            return;
        }
        if (view.isComputingLayout()) {
            view.postOnAnimation(this);
            return;
        }
        Flag item = pendingItem;
        pendingItem = null;
        int position = view.getSelectedPosition();
        // A newer selection or a refreshed route list makes the queued item stale.
        if (item != null && position >= 0 && position < adapter.getItemCount() && adapter.get(position) == item) {
            listener.onItemClick(item);
        }
    }
}
