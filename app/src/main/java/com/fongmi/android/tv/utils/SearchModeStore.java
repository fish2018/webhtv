package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class SearchModeStore {

    public static final int MODE_ALL = 0;
    public static final int MODE_HOME = 1;
    public static final int MODE_TAG = 2;
    public static final int MODE_WHITE = 3;
    public static final int MODE_BLACK = 4;

    private static final String KEY_MODE = "search_mode";
    private static final String KEY_WHITE = "search_white_list";
    private static final String KEY_BLACK = "search_black_list";
    private static final String KEY_TAG = "search_tag_groups";

    private static final Type STRING_LIST = new TypeToken<List<String>>() {}.getType();

    public static int getMode() {
        return Prefers.getInt(KEY_MODE, MODE_ALL);
    }

    public static void putMode(int mode) {
        Prefers.put(KEY_MODE, mode);
    }

    public static List<String> getWhiteList() {
        return safeList(Prefers.getString(KEY_WHITE, "[]"));
    }

    public static void putWhiteList(List<String> keys) {
        Prefers.put(KEY_WHITE, App.gson().toJson(keys == null ? new ArrayList<>() : keys));
    }

    public static List<String> getBlackList() {
        return safeList(Prefers.getString(KEY_BLACK, "[]"));
    }

    public static void putBlackList(List<String> keys) {
        Prefers.put(KEY_BLACK, App.gson().toJson(keys == null ? new ArrayList<>() : keys));
    }

    public static List<String> getTagGroups() {
        return safeList(Prefers.getString(KEY_TAG, "[]"));
    }

    public static void putTagGroups(List<String> groups) {
        Prefers.put(KEY_TAG, App.gson().toJson(groups == null ? new ArrayList<>() : groups));
    }

    private static List<String> safeList(String json) {
        try {
            if (TextUtils.isEmpty(json)) return new ArrayList<>();
            List<String> list = App.gson().fromJson(json, STRING_LIST);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static List<Site> filterSites(List<Site> all, String forceSiteKey) {
        int mode = getMode();
        List<Site> result = new ArrayList<>();
        if (mode == MODE_HOME) {
            Site home = VodConfig.get().getHome();
            if (home != null && all.contains(home)) result.add(home);
            else if (home != null) result.add(home);
            return result;
        }
        if (!TextUtils.isEmpty(forceSiteKey)) {
            for (Site s : all) {
                if (s.getKey().equals(forceSiteKey)) { result.add(s); return result; }
            }
            return result;
        }
        switch (mode) {
            case MODE_ALL:
                for (Site s : all) if (s.isSearchable()) result.add(s);
                break;
            case MODE_TAG:
                List<String> groups = getTagGroups();
                if (groups.isEmpty()) {
                    for (Site s : all) if (s.isSearchable()) result.add(s);
                } else {
                    for (Site s : all) {
                        for (String g : groups) {
                            if (s.inGroup(g)) { result.add(s); break; }
                        }
                    }
                }
                break;
            case MODE_WHITE:
                List<String> white = getWhiteList();
                for (Site s : all) if (white.contains(s.getKey())) result.add(s);
                break;
            case MODE_BLACK:
                List<String> black = getBlackList();
                for (Site s : all) if (!black.contains(s.getKey()) && s.isSearchable()) result.add(s);
                break;
        }
        return result;
    }
}
