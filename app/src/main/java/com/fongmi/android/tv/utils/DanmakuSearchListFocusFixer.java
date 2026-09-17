package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.fongmi.android.tv.App;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public final class DanmakuSearchListFocusFixer {

    private static final String SEARCH_TEXT = "\u5f39\u5e55\u641c\u7d22";
    private static final long INTERVAL_MS = 400;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final Set<ListView> TUNED_LISTS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<View> STYLED_ROWS = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean started;

    private DanmakuSearchListFocusFixer() {
    }

    public static void start() {
        if (started || !Util.isLeanback()) return;
        started = true;
        HANDLER.post(PULSE);
    }

    public static void stop() {
        if (!started) return;
        started = false;
        HANDLER.removeCallbacks(PULSE);
    }

    private static final Runnable PULSE = new Runnable() {
        @Override
        public void run() {
            if (!started) return;
            scan();
            if (started) HANDLER.postDelayed(this, INTERVAL_MS);
        }
    };

    private static void scan() {
        Activity activity = App.activity();
        if (activity == null) return;
        for (View root : getRootViews()) {
            if (root == null || root.getWindowToken() == null || !containsSearchButton(root)) continue;
            tuneListViews(root);
        }
    }

    private static List<View> getRootViews() {
        List<View> roots = getRootViewsByMethod();
        return roots.isEmpty() ? getRootViewsByField() : roots;
    }

    private static List<View> getRootViewsByMethod() {
        List<View> roots = new ArrayList<>();
        try {
            Object global = getWindowManagerGlobal();
            Method namesMethod = global.getClass().getMethod("getViewRootNames");
            Method rootMethod = global.getClass().getMethod("getRootView", String.class);
            String[] names = (String[]) namesMethod.invoke(global);
            if (names == null) return roots;
            for (String name : names) {
                Object root = rootMethod.invoke(global, name);
                if (root instanceof View) roots.add((View) root);
            }
        } catch (Throwable ignored) {
        }
        return roots;
    }

    private static List<View> getRootViewsByField() {
        try {
            Object global = getWindowManagerGlobal();
            Field viewsField = global.getClass().getDeclaredField("mViews");
            viewsField.setAccessible(true);
            Object views = viewsField.get(global);
            if (!(views instanceof List<?>)) return Collections.emptyList();
            List<View> roots = new ArrayList<>();
            for (Object view : (List<?>) views) if (view instanceof View) roots.add((View) view);
            return roots;
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    private static Object getWindowManagerGlobal() throws Exception {
        Class<?> clazz = Class.forName("android.view.WindowManagerGlobal");
        Method method = clazz.getMethod("getInstance");
        return method.invoke(null);
    }

    private static boolean containsSearchButton(View view) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (SEARCH_TEXT.contentEquals(text)) return true;
        }
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) if (containsSearchButton(group.getChildAt(i))) return true;
        return false;
    }

    private static void tuneListViews(View view) {
        if (view instanceof ListView && looksLikeResultList((ListView) view)) tune((ListView) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) tuneListViews(group.getChildAt(i));
    }

    private static boolean looksLikeResultList(ListView list) {
        for (int i = 0; i < list.getChildCount(); i++) if (findText1(list.getChildAt(i)) != null) return true;
        return false;
    }

    private static void tune(ListView list) {
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setDrawSelectorOnTop(false);
        list.setCacheColorHint(Color.TRANSPARENT);
        updateRows(list);
        if (!TUNED_LISTS.add(list)) return;
        list.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                updateRows(list);
                return true;
            }
        });
    }

    private static void updateRows(ListView list) {
        int first = list.getFirstVisiblePosition();
        int selected = list.getSelectedItemPosition();
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            TextView text = findText1(row);
            if (text == null) continue;
            boolean active = selected != AdapterView.INVALID_POSITION && selected == first + i;
            styleRow(row, text, active);
        }
    }

    private static TextView findText1(View view) {
        if (view instanceof TextView && view.getId() == android.R.id.text1) return (TextView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView text = findText1(group.getChildAt(i));
            if (text != null) return text;
        }
        return null;
    }

    private static void styleRow(View row, TextView text, boolean active) {
        if (STYLED_ROWS.add(row)) row.setBackground(rowBackground(row.getContext()));
        row.setSelected(active);
        row.setActivated(active);
        text.setSelected(active);
        text.setActivated(active);
        text.setTextColor(rowTextColor());
    }

    private static StateListDrawable rowBackground(Context context) {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_pressed}, row(context, Color.parseColor("#0B57D0")));
        drawable.addState(new int[]{android.R.attr.state_focused}, row(context, Color.parseColor("#0B57D0")));
        drawable.addState(new int[]{android.R.attr.state_activated}, row(context, Color.parseColor("#0B57D0")));
        drawable.addState(new int[]{android.R.attr.state_selected}, row(context, Color.parseColor("#0B57D0")));
        drawable.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return drawable;
    }

    private static ColorStateList rowTextColor() {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_pressed},
                new int[]{android.R.attr.state_focused},
                new int[]{android.R.attr.state_activated},
                new int[]{android.R.attr.state_selected},
                new int[]{}
        };
        int[] colors = new int[]{Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE, Color.parseColor("#202124")};
        return new ColorStateList(states, colors);
    }

    private static GradientDrawable row(Context context, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, 6));
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
