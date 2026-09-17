package com.fongmi.android.tv.ui.custom;

import android.annotation.SuppressLint;
import android.view.KeyEvent;
import android.view.View;

import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.RowPresenter;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.utils.ResUtil;

public class CustomRowPresenter extends ListRowPresenter {

    public interface OnHorizontalEdgeListener {

        void onHorizontalEdge(boolean towardEnd);
    }

    private final int spacing;
    private final int strategy;
    private final boolean keepFocusInRow;
    private final OnHorizontalEdgeListener edgeListener;

    public CustomRowPresenter(int spacing) {
        this(spacing, false);
    }

    public CustomRowPresenter(int spacing, boolean keepFocusInRow) {
        this(spacing, FocusHighlight.ZOOM_FACTOR_SMALL, HorizontalGridView.FOCUS_SCROLL_ITEM, keepFocusInRow, null);
    }

    public CustomRowPresenter(int spacing, OnHorizontalEdgeListener edgeListener) {
        this(spacing, FocusHighlight.ZOOM_FACTOR_SMALL, HorizontalGridView.FOCUS_SCROLL_ITEM, true, edgeListener);
    }

    @SuppressLint("RestrictedApi")
    public CustomRowPresenter(int spacing, int focusZoomFactor) {
        this(spacing, focusZoomFactor, HorizontalGridView.FOCUS_SCROLL_ITEM, false, null);
    }

    public CustomRowPresenter(int spacing, int focusZoomFactor, int strategy) {
        this(spacing, focusZoomFactor, strategy, false, null);
    }

    public CustomRowPresenter(int spacing, int focusZoomFactor, int strategy, boolean keepFocusInRow) {
        this(spacing, focusZoomFactor, strategy, keepFocusInRow, null);
    }

    private CustomRowPresenter(int spacing, int focusZoomFactor, int strategy, boolean keepFocusInRow, OnHorizontalEdgeListener edgeListener) {
        super(focusZoomFactor);
        this.spacing = spacing;
        this.strategy = strategy;
        this.keepFocusInRow = keepFocusInRow;
        this.edgeListener = edgeListener;
        setShadowEnabled(false);
        setSelectEffectEnabled(false);
        setKeepChildForeground(false);
    }

    @Override
    @SuppressLint("RestrictedApi")
    protected void initializeRowViewHolder(RowPresenter.ViewHolder holder) {
        super.initializeRowViewHolder(holder);
        ViewHolder vh = (ViewHolder) holder;
        HorizontalGridView grid = vh.getGridView();
        grid.setFocusScrollStrategy(strategy);
        grid.setHorizontalSpacing(ResUtil.dp2px(spacing));
        if (keepFocusInRow) grid.setOnKeyInterceptListener(event -> onKeyIntercept(grid, event));
    }

    private boolean onKeyIntercept(HorizontalGridView grid, KeyEvent event) {
        if (!isHorizontalEdge(grid, event)) return false;
        boolean towardEnd = isTowardEnd(grid, event);
        if (event.getAction() == KeyEvent.ACTION_DOWN && edgeListener != null) edgeListener.onHorizontalEdge(towardEnd);
        return true;
    }

    private boolean isHorizontalEdge(HorizontalGridView grid, KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        RecyclerView.Adapter<?> adapter = grid.getAdapter();
        int position = grid.getSelectedPosition();
        if (adapter == null || position == RecyclerView.NO_POSITION || adapter.getItemCount() == 0) return false;
        return isTowardEnd(grid, event) ? position == adapter.getItemCount() - 1 : position == 0;
    }

    private boolean isTowardEnd(HorizontalGridView grid, KeyEvent event) {
        boolean rtl = grid.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        return event.getKeyCode() == (rtl ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT);
    }
}
