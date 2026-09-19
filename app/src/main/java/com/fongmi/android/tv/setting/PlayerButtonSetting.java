package com.fongmi.android.tv.setting;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.StringRes;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.Util;
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
    public static final String CODEC_CAPABILITY = "codec_capability";
    public static final String SPEED = "speed";
    public static final String SCALE = "scale";
    public static final String LUT = "lut";
    public static final String KARAOKE = "karaoke";
    public static final String RESET = "reset";
    public static final String REPEAT = "repeat";
    public static final String TEXT = "text";
    public static final String AUDIO = "audio";
    public static final String VIDEO = "video";
    public static final String OPENING = "opening";
    public static final String ENDING = "ending";
    public static final String DANMAKU = "danmaku";
    public static final String TITLE = "title";
    public static final String PREV = "prev";
    public static final String NEXT = "next";
    public static final String EPISODES = "episodes";
    public static final String FULLSCREEN = "fullscreen";
    public static final String CHANGE = "change";
    public static final String SETTING = "setting";
    public static final String PUSH = "push";
    public static final String TIMER = "timer";
    public static final String PDS = "diagnostic_entry";

    private static final String ORDER = "player_button_order";
    private static final String HIDDEN = "player_button_hidden";
    private static final List<Item> DEFAULT = List.of(
            new Item(NEXT, R.string.play_next),
            new Item(EPISODES, R.string.play_episodes),
            new Item(PREV, R.string.play_prev),
            new Item(PLAYER, R.string.play_exo),
            new Item(DECODE, R.string.play_decode),
            new Item(SPEED, R.string.play_speed),
            new Item(SCALE, R.string.play_scale),
            new Item(OPENING, R.string.play_op),
            new Item(ENDING, R.string.play_ed),
            new Item(VIDEO, R.string.play_track_video),
            new Item(AUDIO, R.string.play_track_audio),
            new Item(RESET, R.string.play_reset),
            new Item(REPEAT, R.string.play_repeat),
            new Item(TIMER, R.string.play_timer),
            new Item(PUSH, R.string.push),
            new Item(SETTING, R.string.play_setting),
            new Item(CHANGE, R.string.play_change),
            new Item(PLAY_PARAMS, R.string.play_params),
            new Item(CODEC_CAPABILITY, R.string.codec_capability_short),
            new Item(LUT, R.string.play_lut),
            new Item(TEXT, R.string.play_track_text),
            new Item(DANMAKU, R.string.danmaku),
            new Item(TITLE, R.string.play_title),
            new Item(FULLSCREEN, R.string.play_fullscreen),
            new Item(PDS, R.string.pan_diagnostic_entry)
    );

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
        List<View> ordered = new ArrayList<>();
        for (String id : getOrder()) {
            View view = views.get(id);
            if (view != null) ordered.add(view);
        }
        for (View view : ordered) if (view.getParent() == container) container.removeView(view);
        for (View view : ordered) container.addView(view);
        applyVisibility(views);
    }

    public static void applyVisibility(Map<String, View> views) {
        Set<String> hidden = getHidden();
        for (Map.Entry<String, View> entry : views.entrySet()) {
            entry.getValue().setVisibility(hidden.contains(entry.getKey()) ? View.GONE : View.VISIBLE);
        }
    }

    private static List<String> getOrder() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String id : split(Prefers.getString(ORDER))) if (contains(id)) ids.add(id);
        for (Item item : DEFAULT) ids.add(item.id());
        return new ArrayList<>(ids);
    }

    private static Set<String> getHidden() {
        Set<String> hidden = new HashSet<>();
        String custom = Prefers.getString(HIDDEN);
        if (custom.isEmpty()) {
            hidden.addAll(getDefaultHiddenIds());
        } else {
            for (String id : split(custom)) if (contains(id)) hidden.add(id);
        }
        return hidden;
    }

    private static List<String> getDefaultHiddenIds() {
        List<String> hidden = new ArrayList<>(List.of(
                PLAY_PARAMS, CODEC_CAPABILITY, LUT, TEXT, DANMAKU, TITLE, FULLSCREEN, PDS, CHANGE
        ));
        if (Util.isLeanback()) hidden.add(SETTING);
        else hidden.add(PUSH);
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
