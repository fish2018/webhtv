package com.fongmi.android.tv.setting;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.StringRes;

import com.fongmi.android.tv.R;
import com.github.catvod.utils.Prefers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlayerButtonSetting {

    public static final String PLAYER = "player";
    public static final String DECODE = "decode";
    public static final String PLAY_PARAMS = "play_params";
    public static final String MULTI_THREAD_PROXY = "multi_thread_proxy";
    public static final String CODEC_CAPABILITY = "codec_capability";
    public static final String SPEED = "speed";
    public static final String SCALE = "scale";
    public static final String QUALITY = "quality";
    public static final String LUT = "lut";
    public static final String PAN_DIAGNOSTIC = "pan_diagnostic";
    public static final String KARAOKE = "karaoke";
    public static final String IMMERSIVE_AUDIO = "immersive_audio";
    public static final String RESET = "reset";
    public static final String SEARCH = "search";
    public static final String REPEAT = "repeat";
    public static final String PARSE = "parse";
    public static final String DISPLAY = "display";
    public static final String TEXT = "text";
    public static final String AUDIO = "audio";
    public static final String VIDEO = "video";
    public static final String OPENING = "opening";
    public static final String ENDING = "ending";
    public static final String DANMAKU = "danmaku";
    public static final String AD_FEEDBACK = "ad_feedback";
    public static final String TITLE = "title";
    public static final String PREV = "prev";
    public static final String NEXT = "next";
    public static final String EPISODES = "episodes";
    public static final String FULLSCREEN = "fullscreen";
    public static final String CHANGE = "change";
    public static final String CAST = "cast";
    public static final String TIMER = "timer";

    private static final String ORDER = "player_button_order";
    private static final String HIDDEN = "player_button_hidden";
    private static final List<Item> DEFAULT = List.of(
            new Item(PLAYER, R.string.play_exo),
            new Item(DECODE, R.string.play_decode),
            new Item(PLAY_PARAMS, R.string.play_params),
            new Item(MULTI_THREAD_PROXY, R.string.multi_thread_proxy_button),
            new Item(CODEC_CAPABILITY, R.string.codec_capability_short),
            new Item(SPEED, R.string.play_speed),
            new Item(SCALE, R.string.play_scale),
            new Item(QUALITY, R.string.detail_quality),
            new Item(LUT, R.string.play_lut),
            new Item(PAN_DIAGNOSTIC, R.string.pan_diagnostic_entry),
            new Item(KARAOKE, R.string.player_karaoke_mode),
            new Item(IMMERSIVE_AUDIO, R.string.player_immersive_audio_mode),
            new Item(RESET, R.string.play_reset),
            new Item(SEARCH, R.string.play_search),
            new Item(REPEAT, R.string.play_repeat),
            new Item(PARSE, R.string.parse),
            new Item(DISPLAY, R.string.control_display),
            new Item(TEXT, R.string.play_track_text),
            new Item(AUDIO, R.string.play_track_audio),
            new Item(VIDEO, R.string.play_track_video),
            new Item(OPENING, R.string.play_op),
            new Item(ENDING, R.string.play_ed),
            new Item(DANMAKU, R.string.danmaku),
            new Item(AD_FEEDBACK, R.string.play_ad_feedback),
            new Item(TITLE, R.string.play_title),
            new Item(PREV, R.string.play_prev),
            new Item(NEXT, R.string.play_next),
            new Item(EPISODES, R.string.play_episodes),
            new Item(FULLSCREEN, R.string.play_fullscreen),
            new Item(CHANGE, R.string.play_change),
            new Item(CAST, R.string.play_cast),
            new Item(TIMER, R.string.play_timer));

    public static List<Item> getItems() {
        List<Item> items = new ArrayList<>();
        List<String> order = getOrder();
        Set<String> hidden = getHidden();
        for (String id : order) {
            Item item = find(id);
            if (item != null) items.add(item.withVisible(!hidden.contains(id)));
        }
        return items;
    }

    public static int getVisibleCount() {
        int count = 0;
        for (Item item : getItems()) if (item.visible()) count++;
        return count;
    }

    public static int getTotalCount() {
        return DEFAULT.size();
    }

    public static boolean isVisible(String id) {
        return !getHidden().contains(id);
    }

    public static void putVisible(String id, boolean visible) {
        Set<String> hidden = getHidden();
        if (visible) hidden.remove(id);
        else hidden.add(id);
        Prefers.put(HIDDEN, join(hidden));
    }

    public static void move(String id, int offset) {
        List<String> order = getOrder();
        int from = order.indexOf(id);
        int to = from + offset;
        if (from < 0 || to < 0 || to >= order.size()) return;
        order.remove(from);
        order.add(to, id);
        Prefers.put(ORDER, join(order));
    }

    public static void putOrder(List<String> ids) {
        LinkedHashSet<String> order = new LinkedHashSet<>();
        for (String id : ids) if (contains(id)) order.add(id);
        for (Item item : DEFAULT) order.add(item.id());
        Prefers.put(ORDER, join(order));
    }

    public static void reset() {
        Prefers.remove(ORDER);
        Prefers.remove(HIDDEN);
    }

    public static void applyOrder(ViewGroup container, Map<String, View> views) {
        if (container == null) return;
        if (Prefers.getString(ORDER).isEmpty()) {
            applyVisibility(views);
            return;
        }
        List<View> ordered = new ArrayList<>();
        for (String id : getOrder()) {
            View view = views.get(id);
            if (view != null) ordered.add(view);
        }
        reorderViewsPreservingUnmanagedChildren(container, ordered);
        applyVisibility(views);
    }

    private static void reorderViewsPreservingUnmanagedChildren(ViewGroup container, List<View> ordered) {
        // Action rows also contain fixed spacers and feature buttons; replace only managed slots.
        List<View> managedOrder = new ArrayList<>();
        for (View view : ordered) if (view.getParent() == container) managedOrder.add(view);
        if (managedOrder.isEmpty()) return;

        Set<View> managed = new HashSet<>(managedOrder);
        List<View> children = new ArrayList<>();
        int managedIndex = 0;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (managed.contains(child)) children.add(managedOrder.get(managedIndex++));
            else children.add(child);
        }
        container.removeAllViews();
        for (View child : children) container.addView(child);
    }

    private static void updateFocusNavigation(Map<String, View> views) {
        Set<ViewGroup> containers = new HashSet<>();
        for (View view : views.values()) {
            if (view.getParent() instanceof ViewGroup container) containers.add(container);
        }
        for (ViewGroup container : containers) updateFocusNavigation(container);
    }

    private static void updateFocusNavigation(ViewGroup container) {
        List<View> focusable = new ArrayList<>();
        for (int i = 0; i < container.getChildCount(); i++) {
            View view = container.getChildAt(i);
            if (view.getVisibility() == View.VISIBLE && view.isFocusable()) focusable.add(view);
        }
        for (int i = 0; i < focusable.size(); i++) {
            View current = focusable.get(i);
            View prev = i > 0 ? focusable.get(i - 1) : focusable.get(focusable.size() - 1);
            View next = i < focusable.size() - 1 ? focusable.get(i + 1) : focusable.get(0);
            current.setNextFocusLeftId(prev.getId());
            current.setNextFocusRightId(next.getId());
        }
    }

    public static void applyVisibility(Map<String, View> views) {
        Set<String> hidden = getHidden();
        for (Map.Entry<String, View> entry : views.entrySet()) {
            if (hidden.contains(entry.getKey())) entry.getValue().setVisibility(View.GONE);
        }
        updateFocusNavigation(views);
    }

    private static List<String> getOrder() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String id : split(Prefers.getString(ORDER))) if (contains(id)) ids.add(id);
        for (Item item : DEFAULT) ids.add(item.id());
        return new ArrayList<>(ids);
    }

    private static Set<String> getHidden() {
        Set<String> hidden = new HashSet<>();
        for (String id : split(Prefers.getString(HIDDEN))) if (contains(id)) hidden.add(id);
        return hidden;
    }

    private static Item find(String id) {
        for (Item item : DEFAULT) if (item.id().equals(id)) return item;
        return null;
    }

    private static boolean contains(String id) {
        return find(id) != null;
    }

    private static List<String> split(String value) {
        if (value == null || value.isEmpty()) return List.of();
        return Arrays.asList(value.split(","));
    }

    private static String join(Iterable<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append(',');
            builder.append(value);
        }
        return builder.toString();
    }

    public record Item(String id, @StringRes int name, boolean visible) {

        public Item(String id, @StringRes int name) {
            this(id, name, true);
        }

        public Item withVisible(boolean visible) {
            return new Item(id, name, visible);
        }
    }
}
