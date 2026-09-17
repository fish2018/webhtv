package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.utils.KeyUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class CustomVerticalGridView extends VerticalGridView {

    public interface HeaderVisibilityListener {

        void onHeaderVisibilityChanged(boolean visible);
    }

    private HeaderVisibilityListener headerVisibilityListener;
    private List<View> views;
    private boolean pressDown;
    private boolean pressUp;
    private boolean moveTop;

    public CustomVerticalGridView(@NonNull Context context) {
        this(context, null);
    }

    public CustomVerticalGridView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomVerticalGridView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setMoveTop(true);
    }

    @Override
    protected void initAttributes(@NonNull Context context, @Nullable AttributeSet attrs) {
        super.initAttributes(context, attrs);
        setOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable ViewHolder child, int position, int subposition) {
                if (pressDown && position == 1) hideHeader();
                if (pressUp && position == 0) showHeader();
            }
        });
    }

    public void setHeader(FragmentActivity activity, int... layoutIds) {
        if (activity != null) views = Arrays.stream(layoutIds).mapToObj(id -> (View) activity.findViewById(id)).filter(Objects::nonNull).toList();
    }

    public void setMoveTop(boolean moveTop) {
        this.moveTop = moveTop;
    }

    public void setHeaderVisibilityListener(@Nullable HeaderVisibilityListener listener) {
        this.headerVisibilityListener = listener;
    }

    public void hideHeader() {
        setHeaderVisibility(View.GONE);
    }

    public void showHeader() {
        setHeaderVisibility(View.VISIBLE);
    }

    private void setHeaderVisibility(int visibility) {
        if (views == null || views.isEmpty()) return;
        for (View view : views) view.setVisibility(visibility);
        boolean visible = visibility == View.VISIBLE;
        if (headerVisibilityListener != null) headerVisibilityListener.onHeaderVisibilityChanged(visible);
    }

    public boolean isHeaderVisible() {
        return views != null && !views.isEmpty() && views.get(0).getVisibility() == View.VISIBLE;
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (!KeyUtil.isActionDown(event)) return super.dispatchKeyEvent(event);
        if (KeyUtil.isBackKey(event)) return moveTop && moveToTop();
        if (KeyUtil.isUpKey(event) && focusHeader()) return true;
        pressUp = KeyUtil.isUpKey(event);
        pressDown = KeyUtil.isDownKey(event);
        return super.dispatchKeyEvent(event);
    }

    private boolean focusHeader() {
        if (views == null || getSelectedPosition() != 0) return false;
        showHeader();
        for (View view : views) if (view.requestFocus()) return true;
        return false;
    }

    public boolean moveToTop() {
        if (views == null || getSelectedPosition() == 0 || getAdapter() == null || getAdapter().getItemCount() == 0) return false;
        showHeader();
        for (View view : views) if (view.requestFocus()) break;
        scrollToPosition(0);
        return true;
    }
}
