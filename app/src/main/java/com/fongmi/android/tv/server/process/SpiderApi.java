package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.server.impl.Process;
import com.github.catvod.Proxy;
import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.Util;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

public class SpiderApi implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/spider");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        try {
            JsonObject input = new JsonObject();
            for (Map.Entry<String, String> entry : session.getParms().entrySet()) {
                input.addProperty(entry.getKey(), entry.getValue());
            }
            String post = files.get("postData");
            if (post != null && !post.isEmpty()) {
                try {
                    JsonObject body = JsonParser.parseString(post).getAsJsonObject();
                    for (String key : body.keySet()) input.add(key, body.get(key));
                } catch (Exception ignored) {
                }
            }
            JsonObject result = url.startsWith("/spider/config") ? config(input) : execute(input);
            return json(result);
        } catch (Exception e) {
            return json(error(e.getMessage()));
        }
    }

    private JsonObject config(JsonObject input) {
        String action = input.has("action") ? input.get("action").getAsString() : "";
        JsonObject data = new JsonObject();
        if ("status".equals(action) || "siteList".equals(action)) {
            JsonArray sites = new JsonArray();
            for (Site site : VodConfig.get().getSites()) {
                JsonObject item = new JsonObject();
                item.addProperty("key", site.getKey());
                item.addProperty("name", site.getName());
                item.addProperty("searchable", 1);
                item.addProperty("quickSearch", 1);
                item.addProperty("filterable", 1);
                sites.add(item);
            }
            data.add("sites", sites);
        }
        JsonObject result = new JsonObject();
        result.addProperty("code", 0);
        result.add("data", data);
        return result;
    }

    private JsonObject execute(JsonObject input) {
        String key = get(input, "key", "");
        String method = get(input, "method", "");
        if (method.isEmpty()) {
            if (input.has("wd")) method = "searchContent";
            else if (input.has("ids")) method = "detailContent";
            else if (input.has("flag") && input.has("id")) method = "playerContent";
            else if (input.has("tid") || input.has("t")) method = "categoryContent";
            else method = "homeContent";
        }
        if (key.isEmpty()) return error("缺少 key 参数");
        Site site = VodConfig.get().getSite(key);
        if (site == null) return error("site not found: " + key);
        Spider spider = site.recent().spider();
        String raw;
        try {
            switch (method) {
                case "homeContent": {
                    boolean filter = !input.has("filter") || input.get("filter").getAsBoolean();
                    raw = spider.homeContent(filter);
                    break;
                }
                case "categoryContent": {
                    String tid = get(input, "tid", "");
                    if (tid.isEmpty()) tid = get(input, "t", "");
                    String pg = get(input, "pg", "1");
                    boolean filter = !input.has("filter") || !"0".equals(input.get("filter").getAsString());
                    HashMap<String, String> extend = new HashMap<>();
                    if (input.has("extend") && input.get("extend").isJsonObject()) {
                        JsonObject ex = input.getAsJsonObject("extend");
                        for (String k : ex.keySet()) extend.put(k, ex.get(k).getAsString());
                    }
                    raw = spider.categoryContent(tid, pg, filter, extend);
                    break;
                }
                case "detailContent": {
                    List<String> ids = new ArrayList<>();
                    if (input.has("ids")) {
                        if (input.get("ids").isJsonArray()) {
                            for (JsonElement element : input.getAsJsonArray("ids")) ids.add(element.getAsString());
                        } else {
                            ids.add(input.get("ids").getAsString());
                        }
                    }
                    raw = spider.detailContent(ids);
                    break;
                }
                case "searchContent": {
                    String wd = get(input, "wd", "");
                    boolean quick = input.has("quick") && !"0".equals(input.get("quick").getAsString());
                    raw = spider.searchContent(wd, quick);
                    break;
                }
                case "playerContent": {
                    String flag = get(input, "flag", "");
                    String id = get(input, "id", "");
                    List<String> flags = new ArrayList<>();
                    if (input.has("flags") && input.get("flags").isJsonArray()) {
                        for (JsonElement element : input.getAsJsonArray("flags")) flags.add(element.getAsString());
                    }
                    raw = spider.playerContent(flag, id, flags);
                    raw = rewritePlayUrl(raw);
                    break;
                }
                default:
                    return error("unknown method: " + method);
            }
        } catch (Exception e) {
            return error(e.getMessage());
        }
        JsonObject result = new JsonObject();
        result.addProperty("code", 0);
        try {
            result.add("data", JsonParser.parseString(raw));
        } catch (Exception e) {
            result.addProperty("data", raw);
        }
        return result;
    }

    private String rewritePlayUrl(String raw) {
        if (raw == null) return "";
        String ip = Util.getIp();
        int port = Proxy.getPort();
        if (ip == null || ip.isEmpty()) return raw;
        return raw.replace("127.0.0.1:" + port, ip + ":" + port).replace("localhost:" + port, ip + ":" + port);
    }

    private String get(JsonObject input, String key, String def) {
        return input.has(key) && !input.get(key).isJsonNull() ? input.get(key).getAsString() : def;
    }

    private JsonObject error(String msg) {
        JsonObject result = new JsonObject();
        result.addProperty("code", -1);
        result.addProperty("msg", msg);
        return result;
    }

    private Response json(JsonObject object) {
        Response response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", object.toString());
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type");
        return response;
    }
}
