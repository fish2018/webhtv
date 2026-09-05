package com.fongmi.android.tv.ui.custom;

import androidx.core.view.OneShotPreDrawListener;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 条目被移除后把 D-pad 焦点交回列表。
 * RecyclerView 自身的焦点恢复救不了这种场景：移除 scrap 时已经先 clearFocus()，
 * 焦点被窗口兜底给了布局里第一个可聚焦控件（历史页是右上角的删除按钮），
 * 等到 recoverFocusFromState() 时 hasFocus() 已为 false 直接返回；
 * 适配器也没有 stable id，基于 id 的恢复路径同样是死的。
 */
public final class FocusRestorer {

    private OneShotPreDrawListener pending;
    private int generation;

    /**
     * 网格中移除 position 后焦点应落到哪一项。
     * 原位仍有条目就留在原位（后面的条目补位）；原位已越界说明删的是末位，
     * 此时同行还有条目就退到新末位，整行被删空则上移一行保持同列。
     */
    public static int nextPosition(int position, int count, int spanCount) {
        if (position < 0 || count <= 0) return RecyclerView.NO_POSITION;
        if (position < count) return position;
        int span = Math.max(1, spanCount);
        int last = count - 1;
        if (last / span == position / span) return last;
        return Math.min(last, Math.max(0, position - span));
    }

    public void restore(RecyclerView recycler, int position) {
        cancel();
        if (recycler == null || position < 0) return;
        schedule(recycler, position, generation, true);
    }

    /** 取消排队中的恢复，包含已经进入滚动重试阶段的那一次。 */
    public void cancel() {
        generation++;
        if (pending != null) pending.removeListener();
        pending = null;
    }

    private void schedule(RecyclerView recycler, int position, int scheduled, boolean retry) {
        pending = OneShotPreDrawListener.add(recycler, () -> {
            if (scheduled != generation) return;
            pending = null;
            if (!recycler.isShown()) return;
            if (focus(recycler, position)) return;
            if (!retry) {
                recycler.requestFocus();
                return;
            }
            // 目标行尚未 layout（列表已滚动过）时先滚过去；scrollToPosition 只是 requestLayout，
            // 必须再等一次 pre-draw 才能拿到 holder，用 post 会跑在这次 layout 之前
            recycler.scrollToPosition(position);
            schedule(recycler, position, scheduled, false);
        });
    }

    private static boolean focus(RecyclerView recycler, int position) {
        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
        return holder != null && holder.itemView.requestFocus();
    }
}
