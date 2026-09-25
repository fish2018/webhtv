package com.fongmi.android.tv.setting;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Config;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public final class InterfaceOrderStore {

    public static final String KEY_VOD = "interface_order_vod";
    private static final Type STRING_LIST = new TypeToken<List<String>>() {}.getType();

    private InterfaceOrderStore() {
    }

    @NonNull
    public static List<String> getVodOrder() {
        List<String> values = read();
        return dedupe(values);
    }

    public static void putVodOrder(@Nullable List<String> urls) {
        List<String> normalized = urls == null ? new ArrayList<>() : dedupe(urls);
        if (normalized.isEmpty()) Prefers.remove(KEY_VOD);
        else Prefers.put(KEY_VOD, App.gson().toJson(normalized));
    }

    public static void saveVodConfigs(@NonNull List<Config> configs) {
        List<String> order = new ArrayList<>();
        for (Config config : configs) if (config != null) order.add(config.getUrl());
        for (String url : getVodOrder()) if (!order.contains(url)) order.add(url);
        putVodOrder(order);
    }

    @NonNull
    public static List<Config> sortVodConfigs(@NonNull List<Config> configs) {
        List<String> available = new ArrayList<>();
        for (Config config : configs) if (config != null) available.add(config.getUrl());
        List<String> order = sortUrls(available, getVodOrder());
        List<Config> result = new ArrayList<>();
        for (String url : order) {
            Config match = find(configs, url);
            if (match != null && !contains(result, match)) result.add(match);
        }
        for (Config item : configs) if (!contains(result, item)) result.add(item);
        return result;
    }

    @Nullable
    public static Config findVodConfig(@NonNull List<Config> configs, @Nullable String url) {
        return find(configs, url);
    }

    @Nullable
    private static Config find(@NonNull List<Config> configs, @Nullable String url) {
        if (TextUtils.isEmpty(url)) return null;
        for (Config item : configs) if (TextUtils.equals(item.getUrl(), url)) return item;
        return null;
    }

    private static boolean contains(@NonNull List<Config> items, @NonNull Config target) {
        for (Config item : items) if (TextUtils.equals(item.getUrl(), target.getUrl())) return true;
        return false;
    }

    @NonNull
    private static List<String> read() {
        String json = Prefers.getString(KEY_VOD, "[]");
        if (TextUtils.isEmpty(json)) return new ArrayList<>();
        try {
            List<String> values = App.gson().fromJson(json, STRING_LIST);
            return values == null ? new ArrayList<>() : values;
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }

    @NonNull
    private static List<String> dedupe(@NonNull List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) if (!TextUtils.isEmpty(value)) result.add(value);
        return new ArrayList<>(result);
    }

    @NonNull
    static List<String> normalize(@NonNull List<String> values) {
        return Collections.unmodifiableList(dedupe(values));
    }

    @NonNull
    static List<String> sortUrls(@NonNull List<String> available, @NonNull List<String> savedOrder) {
        List<String> result = new ArrayList<>();
        List<String> known = dedupe(available);
        for (String url : dedupe(savedOrder)) {
            if (known.contains(url) && !result.contains(url)) result.add(url);
        }
        for (String url : known) if (!result.contains(url)) result.add(url);
        return result;
    }
}
