package com.fongmi.android.tv.web;

import com.google.gson.JsonObject;

public final class WebThemeRoute {

    public static final WebThemeRoute EMPTY = new WebThemeRoute("", "", "", "", "");

    private final String vodId;
    private final String title;
    private final String pic;
    private final String remarks;
    private final String content;

    private WebThemeRoute(String vodId, String title, String pic, String remarks, String content) {
        this.vodId = limited(vodId, 2048);
        this.title = limited(title, 512);
        this.pic = limited(pic, 4096);
        this.remarks = limited(remarks, 1024);
        this.content = limited(content, 20_000);
    }

    public static WebThemeRoute detail(String vodId, String title, String pic, String remarks) {
        return detail(vodId, title, pic, remarks, "");
    }

    public static WebThemeRoute detail(String vodId, String title, String pic, String remarks, String content) {
        String id = limited(vodId, 2048);
        if (id.isEmpty()) throw new IllegalArgumentException("vodId is required");
        return new WebThemeRoute(id, title, pic, remarks, content);
    }

    public String getVodId() {
        return vodId;
    }

    public String getTitle() {
        return title;
    }

    public String getPic() {
        return pic;
    }

    public String getRemarks() {
        return remarks;
    }

    public String getContent() {
        return content;
    }

    JsonObject json() {
        return json(vodId);
    }

    JsonObject json(String publicVodId) {
        JsonObject object = new JsonObject();
        String id = limited(publicVodId, 2048);
        if (!id.isEmpty()) object.addProperty("vodId", id);
        return object;
    }

    private static String limited(String value, int maxLength) {
        String safe = value == null ? "" : value;
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }
}
