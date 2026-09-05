package com.fongmi.android.tv.ui.helper;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

public final class PlayerControlFocusHelper {

    private PlayerControlFocusHelper() {
    }

    public static boolean ensureFocus(View root, View preferred) {
        if (!isVisible(root)) return false;
        View current = root.findFocus();
        if (isFocusable(current) && isDescendant(root, current)) return true;
        View target = firstFocusable(preferred);
        if (target == null || !isDescendant(root, target)) target = firstFocusable(root);
        return target != null && target.requestFocus();
    }

    public static boolean handleKey(View root, View preferred, KeyEvent event) {
        if (!isVisible(root) || event == null || !isFocusKey(event)) return false;
        if (!containsFocus(root)) {
            ensureFocus(root, preferred);
            return true;
        }
        if (!isDirectionKey(event) || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        View focus = root.findFocus();
        View next = focus == null ? null : focus.focusSearch(direction(event));
        if (next == null || !isDescendant(root, next)) return true;
        return false;
    }

    public static boolean containsFocus(View root) {
        if (!isVisible(root)) return false;
        View focus = root.findFocus();
        return isFocusable(focus) && isDescendant(root, focus);
    }

    public static boolean isDescendant(View root, View child) {
        for (View current = child; current != null; ) {
            if (current == root) return true;
            Object parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static View firstFocusable(View view) {
        if (!isVisible(view) || !view.isEnabled()) return null;
        if (view.isFocusable()) return view;
        if (!(view instanceof ViewGroup group)) return null;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = firstFocusable(group.getChildAt(i));
            if (child != null) return child;
        }
        return null;
    }

    private static boolean isFocusKey(KeyEvent event) {
        return isDirectionKey(event) || isConfirmKey(event);
    }

    private static boolean isDirectionKey(KeyEvent event) {
        int keyCode = event.getKeyCode();
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private static boolean isConfirmKey(KeyEvent event) {
        int keyCode = event.getKeyCode();
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private static int direction(KeyEvent event) {
        return switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT;
            default -> View.FOCUS_FORWARD;
        };
    }

    private static boolean isFocusable(View view) {
        return isVisible(view) && view.isEnabled() && view.isFocusable();
    }

    private static boolean isVisible(View view) {
        return view != null && view.getVisibility() == View.VISIBLE && view.isShown();
    }
}
