package com.fongmi.android.tv.api.loader;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 猫源爬虫：把 {@link Spider} 的调用转成对本机 bundle 的 HTTP 请求。
 *
 * <p>CatPawOpen 的 {@code /config} 给出的站点 {@code type} 是 3（在本项目里意味着"本地
 * JS 爬虫"），但它的 {@code api} 指向 bundle 上的 HTTP 路由，方法名接在后面，
 * 统一用 POST + JSON：{@code POST <api>/home}、{@code /category}、{@code /detail}、
 * {@code /play}、{@code /search}。字段沿用它的约定：{@code id}/{@code page}/{@code wd}/
 * {@code filters}/{@code flag}。
 */
public class CatSpider extends Spider {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final String api;

    public CatSpider(String api) {
        this.api = api.endsWith("/") ? api.substring(0, api.length() - 1) : api;
    }

    /** api 是绝对地址且落在 bundle 的爬虫路由上。 */
    public static boolean matches(String api) {
        if (TextUtils.isEmpty(api)) return false;
        return api.startsWith("http") && api.contains("/spider/");
    }

    @Override
    public void init(Context context, String extend) {
        post("/init", new JsonObject());
    }

    @Override
    public String homeContent(boolean filter) {
        return post("/home", new JsonObject());
    }

    @Override
    public String homeVideoContent() {
        // home 的响应里已经带 list，再单独取一次没有意义
        return "";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        JsonObject body = new JsonObject();
        body.addProperty("id", tid);
        body.addProperty("page", page(pg));
        if (extend != null && !extend.isEmpty()) {
            JsonObject filters = new JsonObject();
            for (Map.Entry<String, String> entry : extend.entrySet()) filters.addProperty(entry.getKey(), entry.getValue());
            body.add("filters", filters);
        }
        return post("/category", body);
    }

    @Override
    public String detailContent(List<String> ids) {
        JsonObject body = new JsonObject();
        body.addProperty("id", ids == null || ids.isEmpty() ? "" : ids.get(0));
        return post("/detail", body);
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        JsonObject body = new JsonObject();
        body.addProperty("wd", key);
        body.addProperty("page", page(pg));
        return post("/search", body);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        JsonObject body = new JsonObject();
        body.addProperty("flag", flag);
        body.addProperty("id", id);
        return post("/play", body);
    }

    private int page(String pg) {
        try {
            return TextUtils.isEmpty(pg) ? 1 : Math.max(1, Integer.parseInt(pg.trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private String post(String path, JsonObject body) {
        String url = api + path;
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();
            try (Response response = OkHttp.client().newCall(request).execute()) {
                if (response.body() == null) return "";
                String text = response.body().string();
                if (response.code() != 200) {
                    SpiderDebug.log("cat-spider", "%s -> HTTP %s", path, response.code());
                    return "";
                }
                return unwrap(text);
            }
        } catch (Exception e) {
            SpiderDebug.log("cat-spider", e);
            return "";
        }
    }

    /** 部分路由把结果包在 {@code {code, data}} 里，取出 data 才是标准结果体。 */
    private String unwrap(String text) {
        if (TextUtils.isEmpty(text)) return "";
        try {
            JsonObject object = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
            if (object.has("data") && object.get("data").isJsonObject()) return object.getAsJsonObject("data").toString();
            if (object.has("data") && object.get("data").isJsonArray()) {
                JsonArray array = object.getAsJsonArray("data");
                JsonObject wrapper = new JsonObject();
                wrapper.add("list", array);
                return wrapper.toString();
            }
            return text;
        } catch (Exception ignored) {
            return text;
        }
    }
}
