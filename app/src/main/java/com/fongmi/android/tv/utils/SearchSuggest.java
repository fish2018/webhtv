package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Word;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class SearchSuggest {

    private static final int LIMIT = 20;

    public static String iqiyiUrl(String keyword) {
        return "https://suggest.video.iqiyi.com/?if=mobile&key=" + URLEncoder.encode(keyword);
    }

    public static String tencentUrl(String keyword) {
        return "https://tv.aiseet.atianqi.com/i-tvbin/qtv_video/search/get_search_smart_box?format=json&page_num=0&page_size=10&key=" + URLEncoder.encode(keyword);
    }

    public static List<Word.Data> parseIqiyi(String result) {
        return Word.objectFrom(result).getData();
    }

    public static List<Word.Data> parseTencent(String result) {
        List<Word.Data> items = new ArrayList<>();
        try {
            JsonObject root = Json.parse(result).getAsJsonObject();
            JsonObject data = root.getAsJsonObject("data");
            JsonObject search = data.getAsJsonObject("search_data");
            JsonArray groups = search.getAsJsonArray("vecGroupData");
            for (JsonElement groupElement : groups) {
                JsonArray groupData = groupElement.getAsJsonObject().getAsJsonArray("group_data");
                for (JsonElement itemElement : groupData) {
                    String title = getTencentTitle(itemElement.getAsJsonObject());
                    if (!TextUtils.isEmpty(title)) items.add(Word.Data.create(title));
                }
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    public static List<Word.Data> merge(List<Word.Data> first, List<Word.Data> second) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        addAll(set, first);
        addAll(set, second);
        return set.stream().limit(LIMIT).map(Word.Data::create).toList();
    }

    private static void addAll(LinkedHashSet<String> set, List<Word.Data> items) {
        if (items == null) return;
        for (Word.Data item : items) {
            String title = clean(item.getTitle());
            if (!TextUtils.isEmpty(title)) set.add(title);
        }
    }

    private static String getTencentTitle(JsonObject item) {
        String title = "";
        try {
            JsonObject args = item.getAsJsonObject("action").getAsJsonObject("actionArgs");
            title = args.getAsJsonObject("search_keyword").getAsJsonPrimitive("strVal").getAsString();
        } catch (Exception ignored) {
        }
        if (!TextUtils.isEmpty(title)) return clean(title);
        try {
            title = item.getAsJsonObject("cell_info").getAsJsonPrimitive("title").getAsString();
        } catch (Exception ignored) {
        }
        return clean(title);
    }

    private static String clean(String title) {
        if (TextUtils.isEmpty(title)) return "";
        return title.replaceAll("(?i)</?hl>", "").replaceAll("\\s+", " ").trim();
    }
}
