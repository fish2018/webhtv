package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

public class PlayerFocusContainer extends LinearLayout {

    public PlayerFocusContainer(Context context) {
        super(context);
    }

    public PlayerFocusContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private List<View> getFocusableChildren() {
        List<View> list = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && child.isFocusable()) {
                list.add(child);
            }
        }
        return list;
    }

    @Override
    public int getNextFocusLeftId() {
        View focused = findFocus();
        List<View> children = getFocusableChildren();
        if (focused == null || children.size() <= 1) return super.getNextFocusLeftId();

        int idx = children.indexOf(focused);
        if (idx <= 0) {
            // 到最左侧，环形跳转；想要焦点停留在当前按钮就 return focused.getId();
            return children.get(children.size()-1).getId();
        } else {
            return children.get(idx - 1).getId();
        }
    }

    @Override
    public int getNextFocusRightId() {
        View focused = findFocus();
        List<View> children = getFocusableChildren();
        if (focused == null || children.size() <= 1) return super.getNextFocusLeftId();

        int idx = children.indexOf(focused);
        if (idx >= children.size() - 1) {
            // 到最右侧，环形跳转；想要焦点停留在当前按钮就 return focused.getId();
            return children.get(0).getId();
        } else {
            return children.get(idx + 1).getId();
        }
    }
}
