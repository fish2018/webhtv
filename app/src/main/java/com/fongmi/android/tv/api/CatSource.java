package com.fongmi.android.tv.api;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.node.NodeBundle;
import com.fongmi.android.tv.node.NodeRuntime;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 把「猫源」（CatPawOpen 一类的 CatVod T4 服务端）返回的配置整形成标准 TVBox 配置。
 *
 * <p>两处差异：配置顶层是裸站点数组而不是带 {@code sites} 字段的对象；站点 {@code api}
 * 是服务端上的相对路径（如 {@code /video/douban}）而不是绝对地址。凭据不用管——
 * {@code AuthInterceptor} 已经会把 URL 里的 userinfo 转成认证头并按 host 记住。
 */
public class CatSource {

    /**
     * 猫源地址指向 Node bundle 本身（如 {@code .../index.js.md5}），不是可直接解析的配置。
     *
     * <p>本地包（用户自己解压的目录）也算：这时地址是路径而不是 URL，靠目录里有
     * {@code index.js.md5} 认定，与 CatPawOpen 的发布约定一致。
     */
    public static boolean isBundle(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String value = url.trim().toLowerCase(Locale.ROOT);
        if (value.endsWith(".js.md5") || value.endsWith("/index.js")) return true;
        return NodeBundle.isLocal(url);
    }

    /**
     * 把 bundle 跑起来，返回本机可读的配置地址。阻塞到服务就绪——调用方本身在后台线程。
     */
    public static String serve(String url) throws Exception {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<String> error = new java.util.concurrent.atomic.AtomicReference<>();
        NodeRuntime.start(App.get(), url, new NodeRuntime.Callback() {
            @Override
            public void onProgress(String message) {
                SpiderDebug.log("cat-source", "%s", message);
            }

            @Override
            public void onReady(String baseUrl) {
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                error.set(message);
                latch.countDown();
            }
        });
        if (!latch.await(60, TimeUnit.SECONDS)) {
            throw new Exception("猫源启动超时");
        }
        if (error.get() != null) throw new Exception("猫源启动失败: " + error.get());
        return NodeRuntime.configUrl();
    }

    /** 本机跑的 bundle 按媒体类型分组，点播站点在这个键下面。 */
    private static final String VIDEO = "video";

    /**
     * 这段响应是不是猫源配置。
     *
     * <p>用来在多个本机端口里认出真正的猫源服务：魔改 bundle 会额外起自己的 HTTP 服务
     * （如内置弹幕服务器），那些服务对 {@code /config} 会返回 401 信封或欢迎页——都是
     * 非空响应，只判空会把它们当成就绪。所以这里按配置形状判定。
     */
    public static boolean isConfig(String text) {
        if (TextUtils.isEmpty(text)) return false;
        try {
            JsonElement root = Json.parse(text);
            if (root == null || root.isJsonNull()) return false;
            // 扁平站点数组：空数组说明不是在服务站点，当作不匹配继续探下一个端口
            if (root.isJsonArray()) return !root.getAsJsonArray().isEmpty();
            if (!root.isJsonObject()) return false;
            JsonObject object = root.getAsJsonObject();
            if (object.has("sites")) return true;
            JsonElement video = object.get(VIDEO);
            return video != null && video.isJsonObject() && video.getAsJsonObject().has("sites");
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * @throws IllegalArgumentException 配置既不是站点数组也不是对象时——服务端返回空响应、
     *                                  HTML 错误页或纯文本都会走到这里，明确报错比抛 NPE 好定位。
     */
    public static JsonObject normalize(String url, JsonElement root) {
        if (root == null || root.isJsonNull()) throw new IllegalArgumentException("配置为空");
        if (!root.isJsonArray() && !root.isJsonObject()) throw new IllegalArgumentException("配置格式不是 JSON 对象或数组");
        if (root.isJsonObject()) reject(root.getAsJsonObject());
        JsonObject object = root.isJsonArray() ? wrap(root.getAsJsonArray()) : lift(root.getAsJsonObject());
        rebase(object, base(url));
        return object;
    }

    /**
     * 服务端的错误信封（如 {@code {errorCode:401, errorMessage:"Unauthorized"}}）照常是合法
     * JSON 对象，往下走会解析出空 sites，用户只看到「订阅无效」而没有任何原因。这里如实报错。
     *
     * <p>判定刻意收窄到「有 errorMessage 且没有任何配置内容」——{@code normalize} 对所有点播
     * 配置都跑，不能把恰好带这个字段的正常配置和仓库配置（{@code urls}）误判掉。
     */
    private static void reject(JsonObject object) {
        if (object.has("sites") || object.has(VIDEO) || object.has("urls")) return;
        String message = string(object, "errorMessage");
        if (TextUtils.isEmpty(message)) return;
        JsonElement code = object.get("errorCode");
        throw new IllegalArgumentException(code == null ? message : message + "（" + code + "）");
    }

    private static JsonObject wrap(JsonArray sites) {
        JsonObject object = new JsonObject();
        object.add("sites", sites);
        return object;
    }

    /**
     * 猫源有两种 config 形态：远端服务给的是扁平站点数组；本机跑 bundle 时是
     * {@code {video:{sites:[...]}, read:{...}, comic:{...}, ...}}。后者把 video.sites 提上来，
     * 其余分组（小说/漫画/音乐/网盘）当前不接入点播列表。
     */
    private static JsonObject lift(JsonObject object) {
        if (object.has("sites") || !object.has(VIDEO)) return object;
        JsonElement video = object.get(VIDEO);
        if (!video.isJsonObject()) return object;
        JsonElement sites = video.getAsJsonObject().get("sites");
        if (sites == null || !sites.isJsonArray()) return object;
        JsonObject out = new JsonObject();
        out.add("sites", sites);
        return out;
    }

    /** 相对 api 单独存在没有意义，所以对任何配置都补基址，不只猫源。 */
    private static void rebase(JsonObject object, String base) {
        if (TextUtils.isEmpty(base) || !object.has("sites")) return;
        JsonElement sites = object.get("sites");
        if (!sites.isJsonArray()) return;
        for (JsonElement element : sites.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject site = element.getAsJsonObject();
            String api = string(site, "api");
            if (api.startsWith("/")) site.addProperty("api", base + api);
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) return "";
        return element.getAsString();
    }

    /**
     * {@code scheme://userinfo@host:port}——保留 userinfo，免得每次请求都先吃一个 401。
     *
     * <p>刻意不用 {@code Uri.parse}：这一段只需要截到 authority 结束，纯字符串处理就够，
     * 且能让 {@code normalize} 在普通单元测试里跑（{@code android.net.Uri} 的桩会波及全局）。
     */
    private static String base(String url) {
        if (TextUtils.isEmpty(url)) return "";
        int mark = url.indexOf("://");
        if (mark <= 0) return "";
        int start = mark + 3;
        int end = url.length();
        for (int i = start; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        return end == start ? "" : url.substring(0, end);
    }
}
