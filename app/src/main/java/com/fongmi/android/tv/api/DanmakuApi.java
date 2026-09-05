package com.fongmi.android.tv.api;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.player.danmaku.DanmakuUrlPolicy;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.title.MediaTitleRequest;
import com.fongmi.android.tv.title.MediaTitleResolver;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Response;

public class DanmakuApi {

    private static final String TAG = DanmakuApi.class.getSimpleName();

    public static boolean canSearch() {
        return DanmakuSetting.isLoad() && DanmakuSetting.isAuto() && DanmakuSetting.hasValidApiUrl();
    }

    public static boolean canAutoSearch(List<Danmaku> siteDanmakus) {
        return canSearch();
    }

    public static Call newCall(String name, String episode) {
        String url = DanmakuSetting.getValidApiUrl();
        if (TextUtils.isEmpty(url)) return null;
        OkHttp.cancel(TAG);
        name = Trans.t2s(false, name);
        episode = Trans.t2s(false, episode);
        try {
            if (url.contains("{name}") || url.contains("{episode}")) {
                return OkHttp.newCall(url.replace("{name}", Uri.encode(name)).replace("{episode}", Uri.encode(episode)), TAG);
            } else {
                url = getSearchUrl(url);
                if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "search name=%s episode=%s %s", name, episode, DanmakuUrlPolicy.logSummary(url));
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("name", name);
                params.put("episode", episode);
                return OkHttp.newCall(url, OkHttp.toBody(params), TAG);
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String getSearchUrl(String url) {
        Uri uri = Uri.parse(url);
        List<String> segments = uri.getPathSegments();
        if (!segments.isEmpty() && "danmaku".equalsIgnoreCase(segments.get(segments.size() - 1))) return url;
        if (segments.size() > 1) return url;
        return uri.buildUpon().appendPath("danmaku").build().toString();
    }

    public static List<Danmaku> arrayFrom(String body) {
        return normalize(Danmaku.arrayFrom(body));
    }

    private static List<Danmaku> normalize(List<Danmaku> items) {
        if (items.isEmpty()) return items;
        String api = getSearchUrl(DanmakuSetting.getValidApiUrl());
        for (Danmaku item : items) item.setUrl(normalizeUrl(api, item.getUrl()));
        return items;
    }

    private static String normalizeUrl(String api, String url) {
        String normalized = DanmakuUrlPolicy.normalize(api, url);
        if (SpiderDebug.isEnabled() && !TextUtils.equals(url, normalized)) SpiderDebug.log("danmaku", "normalize result %s", DanmakuUrlPolicy.logSummary(normalized));
        return normalized;
    }

    public static void search(String name, String episode, Consumer<Danmaku> found) {
        Call call = newCall(name, episode);
        if (call == null) return;
        call.enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    arrayFrom(response.body().string()).stream().findFirst().ifPresent(item -> App.post(() -> found.accept(item)));
                } catch (Exception ignored) {
                }
            }
        });
    }

    public static void search(MediaTitleRequest request, Consumer<Danmaku> found) {
        if (request == null || found == null) return;
        Danmaku cached = Setting.getDanmakuMatchCache().find(request.getSiteKey(), request.getVodId(), request.getEpisodeName());
        if (cached != null && !cached.isEmpty()) {
            App.post(() -> found.accept(cached));
            return;
        }
        MediaTitleResolver resolver = new MediaTitleResolver();
        List<String> titles = resolver.queryTitles(request, 3);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "resolved titles raw=%s episode=%s titles=%s", request.getRawTitle(), request.getEpisodeName(), titles);
        searchFirst(titles, request.getEpisodeName(), 0, found, () -> {
            List<String> cleanedTitles = resolver.queryCleanedTitles(request, 3);
            cleanedTitles.removeIf(title -> containsTitle(titles, title));
            if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "cleaned fallback titles raw=%s episode=%s titles=%s", request.getRawTitle(), request.getEpisodeName(), cleanedTitles);
            searchFirst(cleanedTitles, request.getEpisodeName(), 0, found, () -> {
                List<String> fallbackTitles = resolver.queryAiFallbackTitles(request, 3);
                fallbackTitles.removeIf(title -> containsTitle(titles, title) || containsTitle(cleanedTitles, title));
                if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "ai fallback titles raw=%s episode=%s titles=%s", request.getRawTitle(), request.getEpisodeName(), fallbackTitles);
                searchFirst(fallbackTitles, request.getEpisodeName(), 0, found, null);
            });
        });
    }

    private static boolean containsTitle(List<String> titles, String title) {
        if (titles == null || TextUtils.isEmpty(title)) return false;
        for (String item : titles) if (title.equalsIgnoreCase(item)) return true;
        return false;
    }

    private static void searchFirst(List<String> titles, String episode, int index, Consumer<Danmaku> found, Runnable exhausted) {
        if (titles == null || index >= titles.size()) {
            if (exhausted != null) exhausted.run();
            return;
        }
        Call call = newCall(titles.get(index), episode);
        if (call == null) {
            searchFirst(titles, episode, index + 1, found, exhausted);
            return;
        }
        call.enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    List<Danmaku> items = arrayFrom(response.body().string());
                    if (!items.isEmpty()) {
                        App.post(() -> found.accept(items.get(0)));
                    } else {
                        searchFirst(titles, episode, index + 1, found, exhausted);
                    }
                } catch (Exception e) {
                    searchFirst(titles, episode, index + 1, found, exhausted);
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                searchFirst(titles, episode, index + 1, found, exhausted);
            }
        });
    }

    public static void cancel() {
        OkHttp.cancel(TAG);
    }
}
