package com.fongmi.android.tv.ad.audio;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 按上游 m3u8-ad-audio-probe 的规则更新约束抓取社区 rules.json：只允许 HTTPS，
 * 重定向后仍必须是 HTTPS，整次调用 45 秒上限，读取上限 4 MiB，
 * 解析成功后才交给 {@link ProbeRuleStore} 原子替换。规则源不签名，因此只做严格解析、
 * revision 单调递增和大小限制，不做真实性校验。
 */
public final class ProbeRuleDownloader {

    private static final long CONNECT_TIMEOUT_MS = 15_000L;
    private static final long CALL_TIMEOUT_MS = 45_000L;
    private static final int HTTP_NOT_MODIFIED = 304;
    private static final int MAX_REDIRECTS = 5;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private static OkHttpClient client;

    private ProbeRuleDownloader() {
    }

    /**
     * 指纹功能开启、配置了 HTTPS 规则源且已过刷新间隔时，在后台拉一次规则。
     * 并发调用会被合并，异常只记录不上抛。
     */
    public static void refreshIfDue() {
        if (!AdAudioSetting.isEnabled()) return;
        String url = AdAudioSetting.getProbeRuleUrl();
        if (!isHttps(url)) return;
        long now = System.currentTimeMillis();
        if (!AdAudioSetting.isProbeRefreshDue(now)) return;
        if (!RUNNING.compareAndSet(false, true)) return;
        Task.execute(() -> {
            try {
                refresh(url, ProbeRuleStore.get());
                AdAudioSetting.markProbeRefreshed(System.currentTimeMillis());
            } catch (IOException | RuntimeException e) {
                // fail-open：拉取或校验失败时已有缓存继续生效，不影响播放。
                SpiderDebug.log("ad-audio-probe-rules", e);
            } finally {
                RUNNING.set(false);
            }
        });
    }

    /**
     * 设置页「立即刷新」：忽略刷新间隔，结果回到主线程。
     *
     * <p>返回 {@code false} 表示已有刷新在跑、本次没有启动，此时不会有任何回调，
     * 调用方需要自己复位「正在刷新」文本。返回 {@code true} 时保证恰好回调一次。
     */
    public static boolean refreshNow(Callback callback) {
        return refreshNow(callback, AdAudioSetting.getProbeRuleUrl(), null);
    }

    static boolean refreshNow(Callback callback, String url, ProbeRuleStore store) {
        if (url == null || url.isEmpty()) {
            // 空地址是「关闭社区规则源」，不是配置错误，让调用方给出自己的文案。
            callback.onDisabled();
            return true;
        }
        if (!isHttps(url)) {
            callback.onFailure(new IllegalArgumentException("probe rule url must be https"));
            return true;
        }
        if (!RUNNING.compareAndSet(false, true)) return false;
        Task.execute(() -> {
            try {
                AdAudioRuleSnapshot snapshot = refresh(url,
                        store == null ? ProbeRuleStore.get() : store);
                AdAudioSetting.markProbeRefreshed(System.currentTimeMillis());
                App.post(() -> callback.onSuccess(snapshot));
            } catch (IOException | RuntimeException e) {
                SpiderDebug.log("ad-audio-probe-rules", e);
                App.post(() -> callback.onFailure(e));
            } finally {
                RUNNING.set(false);
            }
        });
        return true;
    }

    public interface Callback {

        void onSuccess(AdAudioRuleSnapshot snapshot);

        void onFailure(Throwable error);

        /** 规则源为空，即用户已关闭社区规则源；不是错误，调用方应给出对应提示。 */
        void onDisabled();
    }

    /**
     * 同步抓取并安装。解析失败、版本回滚或同版本内容冲突会抛
     * {@link IllegalArgumentException}，已有缓存保持生效。
     */
    public static AdAudioRuleSnapshot refresh(String url, ProbeRuleStore store) throws IOException {
        if (!isHttps(url)) throw new IllegalArgumentException("probe rule url must be https");
        return refresh(url, store, client());
    }

    /**
     * 手动跟随重定向，每一跳在**发出请求之前**校验必须是 https。
     *
     * <p>不能依赖 OkHttp 的自动重定向：实测 {@code followSslRedirects(false)} 仍会跟随
     * https→http 的 302，而只检查最终 URL 已经太晚——明文的那一跳请求已经发出去了，
     * 中间人拿到了请求也能返回任意内容。
     */
    static AdAudioRuleSnapshot refresh(String url, ProbeRuleStore store, OkHttpClient client)
            throws IOException {
        if (store == null) throw new IllegalArgumentException("store is required");
        boolean requireHttps = isHttps(url);
        String target = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (requireHttps && !isHttps(target)) {
                throw new IOException("probe rule url was redirected off https");
            }
            Request request = new Request.Builder().url(target)
                    .header("Accept", "application/json")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.code() == HTTP_NOT_MODIFIED) return store.load();
                if (isRedirect(response.code())) {
                    target = nextRedirect(response.request().url(), response.header("Location"),
                            requireHttps);
                    continue;
                }
                if (!response.isSuccessful()) {
                    throw new IOException("probe rule download failed: " + response.code());
                }
                return store.install(readBounded(response.body()));
            }
        }
        throw new IOException("probe rule url has too many redirects");
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    /**
     * 解析一跳重定向的目标并校验协议，在发出下一个请求之前调用。
     *
     * @throws IOException Location 缺失、无法解析，或从 https 降级到了明文
     */
    static String nextRedirect(HttpUrl current, String location, boolean requireHttps)
            throws IOException {
        if (location == null || location.isEmpty()) {
            throw new IOException("probe rule redirect has no location");
        }
        HttpUrl resolved = current.resolve(location);
        if (resolved == null) throw new IOException("probe rule redirect is invalid");
        String next = resolved.toString();
        if (requireHttps && !isHttps(next)) {
            throw new IOException("probe rule url was redirected off https");
        }
        return next;
    }

    private static byte[] readBounded(ResponseBody body) throws IOException {
        if (body == null) throw new IOException("probe rule response has no body");
        long declared = body.contentLength();
        if (declared > ProbeRuleStore.MAX_DOWNLOAD_BYTES) {
            throw new IOException("probe rules are too large: " + declared);
        }
        try (InputStream input = body.byteStream()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(16_384);
            byte[] buffer = new byte[8_192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count == 0) continue;
                total += count;
                if (total > ProbeRuleStore.MAX_DOWNLOAD_BYTES) {
                    throw new IOException("probe rules are too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    /**
     * 故意不复用 {@link com.github.catvod.net.OkHttp#client()}：那个客户端为了兼容资源站的破证书
     * 装了 trust-all 的 {@code sslSocketFactory} 和恒真 {@code hostnameVerifier}。规则源没有签名，
     * 传输层是唯一的真实性保障，用信任一切的客户端拉规则等于任何中间人都能塞入任意指纹，
     * 而指纹会直接驱动播放器 seek。这里用平台默认 TLS 校验。
     *
     * <p>{@code followRedirects(false)}：重定向由 {@link #refresh} 手动跟随，以便在发出每一跳
     * 请求之前校验 https，OkHttp 自己的开关做不到这一点。
     */
    static synchronized OkHttpClient client() {
        if (client != null) return client;
        return client = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    private static boolean isHttps(String url) {
        return url != null && url.toLowerCase(Locale.ROOT).startsWith("https://");
    }
}
