package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

public class PlayerFocusContainer extends LinearLayout {

    private boolean mEnableCircularHorizontal;

    public PlayerFocusContainer(Context context) {
        super(context);
    }

    public PlayerFocusContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (attrs != null) {
            var ta = context.obtainStyledAttributes(attrs, com.fongmi.android.tv.R.styleable.PlayerFocusContainer);
            mEnableCircularHorizontal = ta.getBoolean(com.fongmi.android.tv.R.styleable.PlayerFocusContainer_enableCircularHorizontal, false);
            ta.recycle();
        }
    }

    private List<View> getFocusableDirectChildren() {
        List<View> list = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && child.isFocusable()) {
                list.add(child);
            }
        }
        return list;
    }

    private View getDirectFocusChild() {
        View focused = findFocus();
        if (focused == null) return null;
        View curr = focused;
        while (curr != null && curr != this) {
            View parent = (View) curr.getParent();
            if (parent == this) {
                return curr;
            }
            curr = (View) curr.getParent();
        }
        return null;
    }

    @Override
    public int getNextFocusLeftId() {
        View directFocus = getDirectFocusChild();
        List<View> children = getFocusableDirectChildren();
        if (directFocus == null || children.size() <= 1) {
            return super.getNextFocusLeftId();
        }
        int idx = children.indexOf(directFocus);
        if (idx <= 0) {
            if (mEnableCircularHorizontal) {
                return children.get(children.size() - 1).getId();
            } else {
                return super.getNextFocusLeftId();
            }
        } else {
            return children.get(idx - 1).getId();
        }
    }

    @Override
    public int getNextFocusRightId() {
        View directFocus = getDirectFocusChild();
        List<View> children = getFocusableDirectChildren();
        if (directFocus == null || children.size() <= 1) {
            return super.getNextFocusRightId();
        }
        int idx = children.indexOf(directFocus);
        if (idx >= children.size() - 1) {
            if (mEnableCircularHorizontal) {
                return children.get(0).getId();
            } else {
                return super.getNextFocusRightId();
            }
        } else {
            return children.get(idx + 1).getId();
        }
    }

    @Override
    public View focusSearch(View focused, int direction) {
        if (!mEnableCircularHorizontal) {
            return super.focusSearch(focused, direction);
        }
        View directFocus = getDirectFocusChild();
        List<View> children = getFocusableDirectChildren();
        if (directFocus == null || children.size() <= 1) {
            return super.focusSearch(focused, direction);
        }

        int idx = children.indexOf(directFocus);
        if (direction == FOCUS_LEFT) {
            //已经第一个，环形跳到末尾，直接返回，不让父ScrollView接管
            if (idx <= 0) {
                return children.get(children.size() - 1);
            } else {
                return children.get(idx - 1);
            }
        } else if (direction == FOCUS_RIGHT) {
            //已经最后一个，环形跳到第一个，直接返回，不让父ScrollView接管
            if (idx >= children.size() - 1) {
                return children.get(0);
            } else {
                return children.get(idx + 1);
            }
        }
        //上下交给系统
        return super.focusSearch(focused, direction);
    }

    // 关键！阻止HorizontalScrollView抢夺焦点搜索，把焦点搜索拦截在本容器
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        //只处理左右方向键
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            int dir;
            if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                dir = View.FOCUS_LEFT;
            } else if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                dir = View.FOCUS_RIGHT;
            } else {
                return super.dispatchKeyEvent(event);
            }
            View directFocus = getDirectFocusChild();
            if (directFocus != null && mEnableCircularHorizontal) {
                View next = focusSearch(directFocus, dir);
                if (next != null) {
                    next.requestFocus();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
