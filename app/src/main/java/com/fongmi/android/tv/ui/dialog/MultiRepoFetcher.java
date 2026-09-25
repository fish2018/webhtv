package com.fongmi.android.tv.ui.dialog;
 
import android.text.TextUtils;
 
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
 
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.MultiRepo;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
 
import java.util.ArrayList;
import java.util.List;
 
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
 
/**
 * 多仓仓库网络请求工具：支持带缓存标识的增量更新，解析 json / text 格式。
 */
final class MultiRepoFetcher {
 
    private MultiRepoFetcher() {
    }
 
    static void fetchRepo(@NonNull MultiRepo repo, boolean force, @NonNull RepoCallback callback) {
        Task.submit(() -> {
            String url = repo.getUrl();
            if (TextUtils.isEmpty(url)) {
                postError(callback, "仓库链接为空");
                return;
            }
            if (!url.startsWith("http")) url = UrlUtil.convert(url);
            try {
                Request.Builder rb = new Request.Builder().url(url);
                if (!force) {
                    if (!TextUtils.isEmpty(repo.getEtag())) rb.header("If-None-Match", repo.getEtag());
                    if (!TextUtils.isEmpty(repo.getLastModified())) rb.header("If-Modified-Since", repo.getLastModified());
                }
                try (Response response = OkHttp.client().newCall(rb.build()).execute()) {
                    if (response.code() == 304) {
                        String cached = repo.getCache();
                        if (TextUtils.isEmpty(cached)) {
                            postError(callback, "服务器返回 304 但本地无缓存");
                        } else {
                            postSuccess(callback, repo, cached, repo.getEtag(), repo.getLastModified(), true);
                        }
                        return;
                    }
                    if (!response.isSuccessful()) {
                        String cached = repo.getCache();
                        if (!TextUtils.isEmpty(cached)) {
                            postSuccess(callback, repo, cached, repo.getEtag(), repo.getLastModified(), true);
                        } else {
                            postError(callback, "仓库请求失败: HTTP " + response.code());
                        }
                        return;
                    }
                    ResponseBody body = response.body();
                    if (body == null) {
                        String cached = repo.getCache();
                        if (!TextUtils.isEmpty(cached)) {
                            postSuccess(callback, repo, cached, repo.getEtag(), repo.getLastModified(), true);
                        } else {
                            postError(callback, "仓库响应体为空");
                        }
                        return;
                    }
                    String content = body.string();
                    String newEtag = firstNonEmpty(response.header("ETag"), response.header("etag"));
                    String newLastModified = firstNonEmpty(response.header("Last-Modified"), response.header("last-modified"));
                    postSuccess(callback, repo, content, newEtag, newLastModified, false);
                }
            } catch (Throwable e) {
                String cached = repo.getCache();
                if (!TextUtils.isEmpty(cached)) {
                    postSuccess(callback, repo, cached, repo.getEtag(), repo.getLastModified(), true);
                } else {
                    postError(callback, "仓库请求异常: " + e.getMessage());
                }
            }
        });
    }
 
    private static String firstNonEmpty(String a, String b) {
        if (!TextUtils.isEmpty(a)) return a;
        if (!TextUtils.isEmpty(b)) return b;
        return "";
    }
 
    private static void postSuccess(RepoCallback callback, MultiRepo repo, String content, String etag, String lastModified, boolean fromCache) {
        App.post(() -> callback.onSuccess(new RepoResult(repo, content, etag, lastModified, fromCache)));
    }
 
    private static void postError(RepoCallback callback, String msg) {
        App.post(() -> callback.onError(msg));
    }
 
    /**
     * 解析仓库内容为接口 Depot 列表。支持 json 和 text 两种格式。
     */
    @NonNull
    static List<Depot> parseRepoContent(@Nullable String content) {
        List<Depot> result = new ArrayList<>();
        if (TextUtils.isEmpty(content)) return result;
        try {
            String trimmed = content.trim();
            if (trimmed.startsWith("{")) {
                com.google.gson.JsonObject obj = Json.parse(trimmed).getAsJsonObject();
                if (obj.has("urls")) {
                    result.addAll(Depot.arrayFrom(obj.getAsJsonArray("urls").toString()));
                } else if (obj.has("name") && obj.has("url")) {
                    Depot single = App.gson().fromJson(obj.toString(), Depot.class);
                    if (single != null && !TextUtils.isEmpty(single.getUrl())) result.add(single);
                }
            } else if (trimmed.startsWith("[")) {
                result.addAll(Depot.arrayFrom(trimmed));
            } else {
                // 文本格式 name,url
                JsonArray arr = new JsonArray();
                String[] lines = trimmed.split("\\r?\\n");
                for (String line : lines) {
                    String t = line.trim();
                    if (TextUtils.isEmpty(t) || t.startsWith("#")) continue;
                    int firstComma = t.indexOf(',');
                    if (firstComma <= 0) continue;
                    String name = t.substring(0, firstComma).trim();
                    String url = t.substring(firstComma + 1).trim();
                    if (TextUtils.isEmpty(name) || TextUtils.isEmpty(url)) continue;
                    JsonObject o = new JsonObject();
                    o.addProperty("name", name);
                    o.addProperty("url", url);
                    arr.add(o);
                }
                result.addAll(Depot.arrayFrom(arr.toString()));
            }
        } catch (Throwable ignored) {
        }
        return result;
    }
 
    public static class RepoResult {
        public final MultiRepo repo;
        public final String content;
        public final String etag;
        public final String lastModified;
        public final boolean fromCache;
 
        RepoResult(MultiRepo repo, String content, String etag, String lastModified, boolean fromCache) {
            this.repo = repo;
            this.content = content;
            this.etag = etag;
            this.lastModified = lastModified;
            this.fromCache = fromCache;
        }
    }
 
    public interface RepoCallback {
        void onSuccess(RepoResult result);
        void onError(String msg);
    }
}
