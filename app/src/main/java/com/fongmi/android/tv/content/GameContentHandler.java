package com.fongmi.android.tv.content;

import android.app.Activity;
import android.text.TextUtils;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.ui.web.GameWebActivity;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 实验室：小游戏内容处理器（对齐 ReaderContentHandler）。
 *
 * 消费「动作卡片」协议（与影视+ VodPlus 的 action 卡片兼容）。
 * 按协议的 type 字段判定：仅 type == "browser" 且含非空 url 的卡片才接管，
 * 用内置 WebView（GameWebActivity）直接打开，无需进详情页、无需 playerContent 解析。
 *
 * 支持的 vod_id JSON 变体：
 *   {"actionId":"browser","type":"browser","url":"https://...","header":{...}}            ← 小游戏.js
 *   {"actionId":"OPEN_URL","type":"browser","title":"...","url":"https://..."}             ← 资源管理.py 游戏大厅/书签/网页浏览器
 *   {"action":{"actionId":"OPEN_URL","type":"browser","title":"...","url":"https://..."}}  ← 资源管理.py _open_url_action 嵌套变体
 *
 * 不接管的卡片（type 为 input / toast 等，或 vod_id 非 JSON 的普通详情卡）一律返回 false，
 * 回退原详情页流程——即「没有 type:browser 的不调用 web」。
 */
public class GameContentHandler implements ContentHandler {

    private static final String GAME_URL_PREFIX = "game://";
    private static final String TYPE_BROWSER = "browser";

    @Override
    public boolean canHandleSite(String key, String name) {
        // action 卡片的判定依赖 vod_id 内容而非站点名（dispatchSite 以此为门），
        // 必须放行才能进入 handleSite；本 handler 注册在 Reader/Audio 之后，不影响白名单接管。
        return true;
    }

    @Override
    public boolean canHandleUrl(String url) {
        if (url == null) return false;
        String value = url.trim();
        return value.startsWith(GAME_URL_PREFIX) && isWebUrl(value.substring(GAME_URL_PREFIX.length()));
    }

    @Override
    public boolean handleSite(Activity activity, String key, String id, String name, String pic, String mark) {
        JsonObject action = browserAction(id);
        if (action == null) return false; // 非 type:browser 动作卡（input/toast 等）或普通详情卡 → 走原详情页
        GameWebActivity.start(activity, urlOf(action), titleOf(action, name), null, headersOf(action));
        return true;
    }

    @Override
    public boolean handleUrl(Activity activity, String url, String title) {
        if (!canHandleUrl(url)) return false;
        String target = url.trim().substring(GAME_URL_PREFIX.length()).trim();
        if (!isWebUrl(target)) return false;
        GameWebActivity.start(activity, target, title, null, null);
        return true;
    }

    @Override
    public boolean handleResult(Activity activity, String historyKey, String siteKey, String flag,
                                String vodName, String vodPic, List<Episode> episodes, int position,
                                Result result, long timeout) {
        return false; // 小游戏不接管播放结果
    }

    /* ---------------- 动作卡片解析 ---------------- */

    /**
     * 解析 vod_id 并判定是否为 browser 动作卡片（调 web 的网页/游戏卡）。
     * 兼容顶层直接是 action config，与 config 嵌套在 "action" 字段里的两种变体。
     * 判定：type == "browser"，或 type 缺失时按 actionId 兜底（OPEN_URL / browser）；
     * input / toast 等其他动作（actionId 为中文动作名）不接管。
     *
     * @return 带 browser 动作且含非空 url 的 action 对象；否则 null
     */
    static JsonObject browserAction(String id) {
        JsonObject obj = parseJson(id);
        if (obj == null) return null;
        // 嵌套变体：{"action":{...真正的 action config...}}
        JsonElement nested = obj.get("action");
        if (nested != null && nested.isJsonObject()) obj = nested.getAsJsonObject();
        if (!isBrowserAction(obj)) return null;
        String url = urlOf(obj);
        return isWebUrl(url) ? obj : null;
    }

    static boolean isWebUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String value = url.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static boolean isBrowserAction(JsonObject obj) {
        if (TYPE_BROWSER.equals(stringOf(obj, "type"))) return true;
        String actionId = stringOf(obj, "actionId");
        return "OPEN_URL".equals(actionId) || TYPE_BROWSER.equals(actionId);
    }

    static String urlOf(JsonObject obj) {
        return stringOf(obj, "url");
    }

    static String titleOf(JsonObject obj, String fallback) {
        String title = stringOf(obj, "title");
        return TextUtils.isEmpty(title) ? (fallback == null ? "" : fallback) : title;
    }

    /** 从动作卡片 JSON 中提取 header（含 User-Agent）；无返回 null。 */
    static java.util.HashMap<String, String> headersOf(JsonObject obj) {
        if (obj == null) return null;
        JsonElement header = obj.get("header");
        if (header == null || !header.isJsonObject()) return null;
        java.util.HashMap<String, String> map = new java.util.HashMap<>();
        for (Map.Entry<String, JsonElement> entry : header.getAsJsonObject().entrySet()) {
            if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                map.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return map.isEmpty() ? null : map;
    }

    private static String stringOf(JsonObject obj, String key) {
        if (obj == null) return null;
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) return null;
        String value = el.getAsString();
        return value == null ? null : value.trim();
    }

    private static JsonObject parseJson(String id) {
        if (TextUtils.isEmpty(id)) return null;
        String s = id.trim();
        if (!s.startsWith("{") || !s.endsWith("}")) return null;
        try {
            JsonElement el = JsonParser.parseString(s);
            if (el != null && el.isJsonObject()) return el.getAsJsonObject();
        } catch (Throwable ignore) {
        }
        return null;
    }
}
