package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.app.Dialog;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.recyclerview.widget.RecyclerView;

public final class FocusLoop {

    public enum Mode {
        HORIZONTAL,
        VERTICAL,
        BOTH
    }

    private FocusLoop() {
    }

    // ==================== 基于 id 二维数组（静态布局） ====================

    public static boolean handle(Activity activity, int[][] grid, Mode mode, KeyEvent event) {
        if (activity == null) return false;
        View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        return handle(activity.getCurrentFocus(), decor, grid, mode, event);
    }

    public static boolean handle(Dialog dialog, int[][] grid, Mode mode, KeyEvent event) {
        if (dialog == null) return false;
        Window window = dialog.getWindow();
        View decor = window == null ? null : window.getDecorView();
        View focus = dialog.getCurrentFocus();
        if (focus == null && decor != null) focus = decor.findFocus();
        return handle(focus, decor, grid, mode, event);
    }

    public static boolean handle(View currentFocus, View root, int[][] grid, Mode mode, KeyEvent event) {
        if (currentFocus == null || root == null || grid == null || grid.length == 0) return false;
        if (mode == null || !KeyUtil.isActionDown(event)) return false;
        int keyCode = event.getKeyCode();
        boolean horizontal = (mode == Mode.HORIZONTAL || mode == Mode.BOTH)
            && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT);
        boolean vertical = (mode == Mode.VERTICAL || mode == Mode.BOTH)
            && (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN);
        if (!horizontal && !vertical) return false;

        int id = currentFocus.getId();
        int row = -1, col = -1;
        for (int r = 0; r < grid.length; r++) {
            if (grid[r] == null) continue;
            for (int c = 0; c < grid[r].length; c++) {
                if (grid[r][c] == id) {
                    row = r;
                    col = c;
                    break;
                }
            }
            if (row >= 0) break;
        }
        if (row < 0 || grid[row] == null || col < 0 || col >= grid[row].length) return false;

        int nextRow = row;
        int nextCol = col;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            nextRow = (row - 1 + grid.length) % grid.length;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            nextRow = (row + 1) % grid.length;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            nextCol = (col - 1 + grid[row].length) % grid[row].length;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            nextCol = (col + 1) % grid[row].length;
        }
        if (nextRow < 0 || nextRow >= grid.length || grid[nextRow] == null) return false;
        if (nextCol >= grid[nextRow].length) nextCol = grid[nextRow].length - 1;
        if (nextCol < 0) return false;

        View target = root.findViewById(grid[nextRow][nextCol]);
        if (target != null && target.isFocusable() && target.getVisibility() == View.VISIBLE) {
            return target.requestFocus();
        }
        return false;
    }

    // ==================== 基于位置的网格（RecyclerView 等动态布局） ====================

    public static int nextGridPosition(int currentPosition, int itemCount, int spanCount, Mode mode, int keyCode) {
        if (currentPosition < 0 || currentPosition >= itemCount || itemCount <= 0 || spanCount <= 0) return -1;
        if (mode == null) return -1;
        boolean horizontal = (mode == Mode.HORIZONTAL || mode == Mode.BOTH)
            && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT);
        boolean vertical = (mode == Mode.VERTICAL || mode == Mode.BOTH)
            && (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN);
        if (!horizontal && !vertical) return -1;

        int totalRows = (itemCount + spanCount - 1) / spanCount;
        int row = currentPosition / spanCount;
        int col = currentPosition % spanCount;

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            int nextRow = keyCode == KeyEvent.KEYCODE_DPAD_UP
                ? (row - 1 + totalRows) % totalRows
                : (row + 1) % totalRows;
            int next = nextRow * spanCount + col;
            if (next >= itemCount) next = itemCount - 1;
            return next;
        } else {
            int rowStart = row * spanCount;
            int rowEnd = Math.min(rowStart + spanCount, itemCount) - 1;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                return currentPosition <= rowStart ? rowEnd : currentPosition - 1;
            } else {
                return currentPosition >= rowEnd ? rowStart : currentPosition + 1;
            }
        }
    }

    public static boolean handleRecyclerGrid(RecyclerView recycler, int itemCount, int spanCount, Mode mode, KeyEvent event) {
        if (recycler == null || !KeyUtil.isActionDown(event)) return false;
        View focus = recycler.findFocus();
        if (focus == null || focus == recycler) return false;
        // 向上查找 RecyclerView 的直接子 View（itemView），避免 ClassCastException
        View itemView = focus;
        while (itemView != null && itemView.getParent() != recycler) {
            if (!(itemView.getParent() instanceof View)) return false;
            itemView = (View) itemView.getParent();
        }
        if (itemView == null) return false;
        int position = recycler.getChildAdapterPosition(itemView);
        if (position == RecyclerView.NO_POSITION) return false;
        int next = nextGridPosition(position, itemCount, spanCount, mode, event.getKeyCode());
        if (next < 0) return false;
        RecyclerView.ViewHolder vh = recycler.findViewHolderForAdapterPosition(next);
        if (vh != null && vh.itemView.isFocusable() && vh.itemView.getVisibility() == View.VISIBLE) {
            return vh.itemView.requestFocus();
        }
        // 目标不在屏幕内，先滚动再请求焦点
        final int finalNext = next;
        recycler.scrollToPosition(finalNext);
        recycler.postDelayed(() -> {
            if (recycler == null) return;
            RecyclerView.ViewHolder vh2 = recycler.findViewHolderForAdapterPosition(finalNext);
            if (vh2 != null && vh2.itemView != null && vh2.itemView.isFocusable()) {
                vh2.itemView.requestFocus();
            }
        }, 100);
        return true;
    }

    // ==================== 普通 ViewGroup 动态子 View（addView 复用同一个 xml） ====================

    public static boolean handleChildGrid(ViewGroup container, int spanCount, Mode mode, KeyEvent event) {
        if (container == null || !KeyUtil.isActionDown(event)) return false;
        View focus = container.findFocus();
        if (focus == null) return false;
        // 找到 container 的直接子 View（焦点可能在子 View 的子 View 里）
        View directChild = focus;
        while (directChild != null && directChild.getParent() != container) {
            if (!(directChild.getParent() instanceof View)) return false;
            directChild = (View) directChild.getParent();
        }
        if (directChild == null) return false;
        // 只收集可见且可聚焦的子 View，跳过 GONE 项
        java.util.List<View> visible = new java.util.ArrayList<>();
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && child.isFocusable()) visible.add(child);
        }
        int position = visible.indexOf(directChild);
        if (position < 0) return false;
        int itemCount = visible.size();
        if (itemCount <= 1) return false;
        int next = nextGridPosition(position, itemCount, spanCount, mode, event.getKeyCode());
        if (next < 0 || next >= itemCount) return false;
        View target = visible.get(next);
        if (target != null) {
            return target.requestFocus();
        }
        return false;
    }
}
