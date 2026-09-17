package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 安全的 JSON 解析工具类，防止因类型转换错误导致应用崩溃
 */
public class SafeJsonParser {

    private static final String TAG = "SafeJsonParser";

    /**
     * 安全地将 JsonElement 转换为 JsonObject
     */
    public static JsonObject safeGetAsJsonObject(JsonElement element, String context) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            if (element.isJsonObject()) {
                return element.getAsJsonObject();
            } else {
                logWarning(context, "Expected JsonObject but got: " + element.getClass().getSimpleName());
                return null;
            }
        } catch (ClassCastException e) {
            logError(context, "ClassCastException when converting to JsonObject", e);
            return null;
        } catch (Throwable e) {
            logError(context, "Unexpected error when converting to JsonObject", e);
            return null;
        }
    }

    /**
     * 安全地将 JsonElement 转换为 JsonArray
     */
    public static JsonArray safeGetAsJsonArray(JsonElement element, String context) {
        if (element == null || element.isJsonNull()) {
            return new JsonArray();
        }
        try {
            if (element.isJsonArray()) {
                return element.getAsJsonArray();
            } else {
                logWarning(context, "Expected JsonArray but got: " + element.getClass().getSimpleName());
                return new JsonArray();
            }
        } catch (ClassCastException e) {
            logError(context, "ClassCastException when converting to JsonArray", e);
            return new JsonArray();
        } catch (Throwable e) {
            logError(context, "Unexpected error when converting to JsonArray", e);
            return new JsonArray();
        }
    }

    /**
     * 安全地从 JsonObject 获取 int 值
     */
    public static int safeGetAsInt(JsonObject object, String key, int defaultValue, String context) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return object.get(key).getAsInt();
        } catch (ClassCastException e) {
            logError(context, "ClassCastException when getting int for key: " + key, e);
            return defaultValue;
        } catch (Throwable e) {
            logError(context, "Unexpected error when getting int for key: " + key, e);
            return defaultValue;
        }
    }

    /**
     * 安全地从 JsonObject 获取 String 值
     */
    public static String safeGetAsString(JsonObject object, String key, String defaultValue, String context) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            String value = object.get(key).getAsString();
            return TextUtils.isEmpty(value) ? defaultValue : value;
        } catch (ClassCastException e) {
            logError(context, "ClassCastException when getting string for key: " + key, e);
            return defaultValue;
        } catch (Throwable e) {
            logError(context, "Unexpected error when getting string for key: " + key, e);
            return defaultValue;
        }
    }

    /**
     * 安全地从 JsonObject 获取 double 值
     */
    public static double safeGetAsDouble(JsonObject object, String key, double defaultValue, String context) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return object.get(key).getAsDouble();
        } catch (ClassCastException e) {
            logError(context, "ClassCastException when getting double for key: " + key, e);
            return defaultValue;
        } catch (Throwable e) {
            logError(context, "Unexpected error when getting double for key: " + key, e);
            return defaultValue;
        }
    }

    /**
     * 安全地遍历 JsonArray
     */
    public static void safeForEach(JsonArray array, String context, JsonElementConsumer consumer) {
        if (array == null || array.size() == 0) return;
        for (int i = 0; i < array.size(); i++) {
            try {
                JsonElement element = array.get(i);
                if (element != null && !element.isJsonNull()) {
                    consumer.accept(element, i);
                }
            } catch (ClassCastException e) {
                logError(context, "ClassCastException at index " + i, e);
            } catch (Throwable e) {
                logError(context, "Unexpected error at index " + i, e);
            }
        }
    }

    @FunctionalInterface
    public interface JsonElementConsumer {
        void accept(JsonElement element, int index) throws Exception;
    }

    private static void logWarning(String context, String message) {
        String fullMessage = "[" + context + "] " + message;
        SpiderDebug.log(TAG, fullMessage);
        android.util.Log.w(TAG, fullMessage);
    }

    private static void logError(String context, String message, Throwable throwable) {
        String fullMessage = "[" + context + "] " + message;
        SpiderDebug.log(TAG, fullMessage + " - " + throwable.getMessage());
        android.util.Log.e(TAG, fullMessage, throwable);
    }
}
