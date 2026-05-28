package com.fongmi.android.tv.setting;

import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.bean.Proxy;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class ProxySetting {

    private static final String NAME = "app";

    public static void apply() {
        OkHttp.selector().remove(NAME);
        String url = Setting.getShellProxyUrl().trim();
        if (TextUtils.isEmpty(url) || !isValid(url)) return;
        OkHttp.selector().addAll(Proxy.arrayFrom(create(url)));
        SpiderDebug.log("proxy", "app proxy enabled hosts=%s url=%s", Setting.getShellProxyHosts(), url);
    }

    private static JsonArray create(String url) {
        JsonObject object = new JsonObject();
        object.addProperty("name", NAME);
        object.add("hosts", hosts());
        object.add("urls", urls(url));
        JsonArray array = new JsonArray();
        array.add(object);
        return array;
    }

    private static JsonArray hosts() {
        JsonArray array = new JsonArray();
        for (String item : Setting.getShellProxyHosts().split(",")) {
            String host = item.trim();
            if (!TextUtils.isEmpty(host)) array.add(host);
        }
        if (array.isEmpty()) array.add("*");
        return array;
    }

    private static JsonArray urls(String url) {
        JsonArray array = new JsonArray();
        array.add(url);
        return array;
    }

    public static boolean isValid(String url) {
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        return scheme != null && (scheme.startsWith("http") || scheme.startsWith("socks")) && uri.getHost() != null && uri.getPort() > 0;
    }
}
