package com.fongmi.android.tv.ui.helper;

import android.app.Dialog;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class TouchOptimizationHelper {

    private static final String BASE_GRID_VIEW = "androidx.leanback.widget.BaseGridView";
    private static final int FOCUS_SCROLL_ALIGNED = 0;
    private static final int FOCUS_SCROLL_ITEM = 1;
    private static final Map<View, Boolean> ORIGINAL_TOUCH_MODES = new WeakHashMap<>();
    private static final Map<View, GridState> GRID_STATES = new WeakHashMap<>();
    private static final Set<RecyclerView> RECYCLERS_WITH_LISTENER = Collections.newSetFromMap(new WeakHashMap<>());

    private TouchOptimizationHelper() {
    }

    public static void sync(View root) {
        if (!Util.isLeanback()) return;
        if (root == null) return;
        if (Setting.isTouchOptimized()) optimize(root);
        else restore(root);
    }

    public static void sync(Dialog dialog) {
        if (!Util.isLeanback()) return;
        if (dialog == null || dialog.getWindow() == null) return;
        View decor = dialog.getWindow().getDecorView();
        decor.post(() -> sync(decor));
    }

    public static void optimize(View root) {
        if (root == null || !Setting.isTouchOptimized()) return;
        traverse(root, true);
    }

    public static void restore(View root) {
        if (root == null) return;
        traverse(root, false);
    }

    /** Returns whether a leanback grid is receiving a touch gesture or settling from one. */
    public static boolean isTouchActive(View view) {
        if (view == null) return false;
        synchronized (GRID_STATES) {
            for (Map.Entry<View, GridState> entry : GRID_STATES.entrySet()) {
                View grid = entry.getKey();
                GridState state = entry.getValue();
                if (state == null || (!state.touchActive && !state.touchSettling) || grid == null) continue;
                if (view == grid || isDescendantOf(view, grid)) return true;
            }
        }
        return false;
    }

    private static void traverse(View view, boolean optimize) {
        if (isInputView(view)) return;
        if (view instanceof RecyclerView recycler) {
            if (optimize) attachListener(recycler);
            else restoreGrid(recycler);
        }
        if (isBaseGridView(view)) {
            if (optimize) attachGrid(view);
            else restoreGrid(view);
        }
        if (optimize) {
            if (view.isFocusableInTouchMode()) {
                if (!ORIGINAL_TOUCH_MODES.containsKey(view)) ORIGINAL_TOUCH_MODES.put(view, true);
                view.setFocusableInTouchMode(false);
            }
        } else {
            Boolean original = ORIGINAL_TOUCH_MODES.remove(view);
            if (Boolean.TRUE.equals(original)) view.setFocusableInTouchMode(true);
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) traverse(group.getChildAt(i), optimize);
        }
    }

    private static void attachListener(RecyclerView recycler) {
        if (!RECYCLERS_WITH_LISTENER.add(recycler)) return;
        recycler.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                sync(view);
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
            }
        });
    }

    private static void attachGrid(View grid) {
        synchronized (GRID_STATES) {
            if (GRID_STATES.containsKey(grid)) return;
            GridState state = new GridState();
            state.grid = new WeakReference<>(grid);
            state.focusStrategy = invokeInt(grid, "getFocusScrollStrategy", FOCUS_SCROLL_ALIGNED);
            state.focusSearchDisabled = invokeBoolean(grid, "isFocusSearchDisabled", false);
            GRID_STATES.put(grid, state);
            try {
                Class<?> touchListener = Class.forName(BASE_GRID_VIEW + "$OnTouchInterceptListener");
                Method setter = grid.getClass().getMethod("setOnTouchInterceptListener", touchListener);
                setter.invoke(grid, Proxy.newProxyInstance(touchListener.getClassLoader(), new Class[]{touchListener}, handler(state)));
                ((RecyclerView) grid).addOnScrollListener(new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                        if (newState == RecyclerView.SCROLL_STATE_IDLE && !state.touchActive) state.touchSettling = false;
                    }
                });
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                GRID_STATES.remove(grid);
            }
        }
    }

    private static InvocationHandler handler(GridState state) {
        return (proxy, method, args) -> {
            if ("onInterceptTouchEvent".equals(method.getName()) && args != null && args.length > 0) {
                return onTouch(state, (MotionEvent) args[0]);
            }
            if (method.getReturnType() == boolean.class) return false;
            return null;
        };
    }

    private static boolean onTouch(GridState state, MotionEvent event) {
        if (event == null || !Setting.isTouchOptimized()) {
            state.touchGeneration++;
            restoreGrid(state.grid.get());
            return false;
        }
        View grid = state.grid.get();
        if (grid == null) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            state.touchGeneration++;
            state.touchSettling = false;
            state.touchActive = true;
            invoke(grid, "setFocusScrollStrategy", int.class, FOCUS_SCROLL_ITEM);
            invoke(grid, "setFocusSearchDisabled", boolean.class, true);
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            int generation = ++state.touchGeneration;
            state.touchActive = false;
            restoreGrid(grid);
            state.touchSettling = true;
            grid.post(() -> {
                if (state.touchGeneration == generation && !state.touchActive
                        && (!(grid instanceof RecyclerView recycler) || recycler.getScrollState() == RecyclerView.SCROLL_STATE_IDLE)) {
                    state.touchSettling = false;
                }
            });
        } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            state.touchGeneration++;
            restoreGrid(grid);
        }
        return false;
    }

    private static void restoreGrid(RecyclerView recycler) {
        if (!isBaseGridView(recycler)) return;
        synchronized (GRID_STATES) {
            GridState state = GRID_STATES.get(recycler);
            if (state == null) return;
            View grid = state.grid.get();
            if (grid != null) restoreGrid(grid);
        }
    }

    private static void restoreGrid(View grid) {
        GridState state = GRID_STATES.get(grid);
        if (state == null) return;
        invoke(grid, "setFocusScrollStrategy", int.class, state.focusStrategy);
        invoke(grid, "setFocusSearchDisabled", boolean.class, state.focusSearchDisabled);
        state.touchActive = false;
        state.touchSettling = false;
    }

    private static boolean isBaseGridView(View view) {
        try {
            return Class.forName(BASE_GRID_VIEW).isInstance(view);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean isDescendantOf(View view, View parent) {
        View current = view;
        while (current != null) {
            if (current == parent) return true;
            if (!(current.getParent() instanceof View next)) return false;
            current = next;
        }
        return false;
    }

    private static int invokeInt(View target, String name, int fallback) {
        try {
            Object value = target.getClass().getMethod(name).invoke(target);
            return value instanceof Integer ? (Integer) value : fallback;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }

    private static boolean invokeBoolean(View target, String name, boolean fallback) {
        try {
            Object value = target.getClass().getMethod(name).invoke(target);
            return value instanceof Boolean ? (Boolean) value : fallback;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }

    private static void invoke(View target, String name, Class<?> parameter, Object value) {
        try {
            target.getClass().getMethod(name, parameter).invoke(target, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static boolean isInputView(View view) {
        return view instanceof WebView || view instanceof EditText || view.onCheckIsTextEditor();
    }

    private static final class GridState {
        private WeakReference<View> grid;
        private int focusStrategy;
        private boolean focusSearchDisabled;
        private boolean touchActive;
        private boolean touchSettling;
        private int touchGeneration;
    }
}
