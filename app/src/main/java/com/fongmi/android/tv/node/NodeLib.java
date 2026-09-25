package com.fongmi.android.tv.node;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import com.fongmi.android.tv.nodejs.NodeBridge;
import com.fongmi.android.tv.utils.GithubProxy;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Response;

/**
 * 管理 {@code libnode.so}（nodejs-mobile）的按需下载。
 *
 * <p>单架构解压后约 60MB，打进 APK 不合适，所以只在用户真的要用猫源时才拉，
 * 且只拉当前设备架构那一份。非系统依赖只有 {@code libc++_shared.so}，APK 已自带。
 */
public final class NodeLib {

    /** 官方预编译包（nodejs-mobile/nodejs-mobile），与 include/ 头文件同一 tag。 */
    private static final String VERSION = "v18.20.4";
    private static final String BASE = "https://github.com/nodejs-mobile/nodejs-mobile/releases/download/" + VERSION + "/";

    public interface Progress {
        void onProgress(long done, long total);
    }

    private NodeLib() {
    }

    public static File file(Context context) {
        return new File(dir(context), "libnode.so");
    }

    private static File dir(Context context) {
        File dir = new File(context.getFilesDir(), "node/" + abi());
        dir.mkdirs();
        return dir;
    }

    public static boolean installed(Context context) {
        File file = file(context);
        return file.exists() && file.length() > 0 && marker(context).exists();
    }

    private static File marker(Context context) {
        return new File(dir(context), "version");
    }

    /**
     * nodejs-mobile 的 zip 内目录名与 Android abi 名一致。
     *
     * <p>取的是桥接 so 自身的架构而非设备支持列表——发起 dlopen 的是它，两者必须一致。
     */
    private static String abi() {
        String abi = NodeBridge.abi();
        if ("arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi) || "x86_64".equals(abi)) return abi;
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis != null && abis.length > 0) return abis[0];
        return "arm64-v8a";
    }

    /**
     * 确保 libnode.so 就位并 dlopen 成功。阻塞，调用方放后台线程。
     *
     * @return null 表示可用，否则是失败原因
     */
    public static synchronized String ensure(Context context, Progress progress) {
        try {
            if (!installed(context)) {
                String error = fetch(context, progress);
                if (error != null) return error;
            }
            if (NodeBridge.isLoaded()) return null;
            return NodeBridge.load(file(context));
        } catch (Throwable e) {
            SpiderDebug.log("node", e);
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    /**
     * 官方只发布含三个架构的整包（57MB），而我们只要其中一个 libnode.so。
     * 先用 Range 只取需要的条目（省约三分之二流量）；服务端不支持时回退整包下载。
     */
    private static String fetch(Context context, Progress progress) {
        String entry = "bin/" + abi() + "/libnode.so";
        File out = file(context);
        // 走 GithubProxy：这是 GitHub release 资源，直连在部分网络下不可达。
        // 代理源与"关于"页的在线更新共用同一份用户配置（可选择、可自建）。
        String url = GithubProxy.apply(origin());
        String error = NodeRangeZip.extract(url, entry, out,
                progress == null ? null : progress::onProgress);
        if (error == null) {
            try {
                writeMarker(context);
                return null;
            } catch (IOException e) {
                return e.getMessage();
            }
        }
        SpiderDebug.log("node", "range fetch failed (%s), fall back to full zip", error);
        String full = fetchFull(context, url, entry, progress);
        // 代理本身失效时（挂了、限流）退回直连，好过整个功能不可用
        if (full != null && !url.equals(origin())) {
            SpiderDebug.log("node", "proxy failed (%s), retry direct", full);
            return fetchFull(context, origin(), entry, progress);
        }
        return full;
    }

    private static String origin() {
        return BASE + "nodejs-mobile-" + VERSION + "-android.zip";
    }

    private static String fetchFull(Context context, String url, String entry, Progress progress) {
        File zip = new File(dir(context), "nodejs-mobile.zip");
        try {
            String error = download(url, zip, progress);
            if (error != null) return error;
            if (!NodeZip.extract(zip, entry, file(context))) return "zip 内未找到 " + entry;
            writeMarker(context);
            return null;
        } catch (Exception e) {
            SpiderDebug.log("node", e);
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        } finally {
            zip.delete();
        }
    }

    private static void writeMarker(Context context) throws IOException {
        try (FileOutputStream out = new FileOutputStream(marker(context))) {
            out.write((VERSION + ":" + abi()).getBytes());
        }
    }

    private static String download(String url, File target, Progress progress) {
        try (Response res = OkHttp.newCall(url, "node-lib").execute()) {
            if (!res.isSuccessful() || res.body() == null) return "下载失败 HTTP " + res.code();
            long total = res.body().contentLength();
            long done = 0;
            try (InputStream in = res.body().byteStream(); FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[65536];
                int len;
                long lastPost = 0;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                    done += len;
                    if (progress != null && done - lastPost >= 1048576) {
                        lastPost = done;
                        progress.onProgress(done, total);
                    }
                }
            }
            if (progress != null) progress.onProgress(done, total);
            return null;
        } catch (Exception e) {
            SpiderDebug.log("node", e);
            String message = e.getMessage();
            return TextUtils.isEmpty(message) ? e.getClass().getSimpleName() : message;
        }
    }
}
