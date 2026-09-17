package com.fongmi.android.tv.web;

import android.text.TextUtils;

import com.fongmi.android.tv.player.PlayerHelper;
import com.github.catvod.utils.Json;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonElement;

import java.util.HashMap;
import java.util.Map;

import okhttp3.Headers;

public class HeaderPolicy {

    public static Map<String, String> parse(JsonElement element) {
        if (element == null || element.isJsonNull()) return new HashMap<>();
        Map<String, String> headers = Json.toMap(element);
        return headers == null ? new HashMap<>() : headers;
    }

    public static Map<String, String> parse(String json) {
        Map<String, String> headers = Json.toMap(json);
        return headers == null ? new HashMap<>() : headers;
    }

    public static Headers of(Map<String, String> headers) {
        Headers.Builder builder = new Headers.Builder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (!TextUtils.isEmpty(entry.getKey()) && entry.getValue() != null) builder.set(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    public static Map<String, String> withDefaultUa(Map<String, String> headers) {
        Map<String, String> result = new HashMap<>(headers);
        boolean hasUa = result.keySet().stream().anyMatch(HttpHeaders.USER_AGENT::equalsIgnoreCase);
        if (!hasUa) result.put(HttpHeaders.USER_AGENT, PlayerHelper.getUa());
        return result;
    }

    public static boolean hasCookie(Map<String, String> headers) {
        return headers.keySet().stream().anyMatch(HttpHeaders.COOKIE::equalsIgnoreCase);
    }
}
