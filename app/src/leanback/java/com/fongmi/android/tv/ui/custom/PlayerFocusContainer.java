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
            curr = parent;
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
}
