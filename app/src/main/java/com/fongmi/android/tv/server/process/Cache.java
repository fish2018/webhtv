package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.fongmi.android.tv.utils.AppCache;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

public class Cache implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/cache");
    }

    private String getKey(String rule, String key) {
        return AppCache.key(rule, key);
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        Map<String, String> params = session.getParms();
        String action = params.get("do");
        String rule = params.get("rule");
        String key = params.get("key");
        if ("get".equals(action)) return Nano.ok(AppCache.get(getKey(rule, key)));
        if ("set".equals(action)) AppCache.put(getKey(rule, key), params.get("value"));
        if ("del".equals(action)) AppCache.remove(getKey(rule, key));
        return Nano.ok();
    }
}
