package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.server.Server;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class SubtitleUriFactory {

    public String fromDirectUrl(String url) {
        return url == null ? "" : url;
    }

    public String fromLocalFile(File file) {
        return file == null ? "" : file.getAbsolutePath();
    }

    public String fromWebResource(String url, Map<String, String> headers, boolean includeCredentials) {
        StringBuilder builder = new StringBuilder(Server.get().getAddress("/webResource?url=")).append(encode(url));
        if (headers != null && !headers.isEmpty()) builder.append("&headers=").append(encode(App.gson().toJson(headers)));
        if (includeCredentials) builder.append("&credentials=include");
        return builder.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
