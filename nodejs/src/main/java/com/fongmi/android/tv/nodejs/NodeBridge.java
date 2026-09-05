package com.fongmi.android.tv.nodejs;

import java.io.File;

/**
 * {@code libnode.so}（nodejs-mobile）的最小桥接层。
 *
 * <p>libnode 只导出 C++ 修饰名 {@code node::Start(int, char**)}，所以中间必须垫一层
 * native 代码（{@code libcnode.so}，几十 KB，打进 APK）做 dlopen/dlsym 转调。
 * libnode 本体约 60MB，改为运行时按设备架构下载，不进 APK。
 */
public final class NodeBridge {

    private static volatile boolean loaded;

    private NodeBridge() {
    }

    static {
        System.loadLibrary("cnode");
    }

    private static native String nativeAbi();

    private static native String nativeLoad(String libPath);

    private static native int nativeStart(String[] argv);

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * 桥接层自身编译时的 abi——libnode 必须与它一致。
     *
     * <p>不能用 {@code Build.SUPPORTED_ABIS}：x86_64 模拟器跑 arm64 包时首项是 x86_64，
     * 但真正发起 dlopen 的是 arm64 的本 so。
     */
    public static String abi() {
        try {
            String abi = nativeAbi();
            return abi == null ? "" : abi;
        } catch (Throwable e) {
            return "";
        }
    }

    /**
     * 打开下载好的 libnode.so。
     *
     * @return null 表示成功，否则是 dlopen/dlsym 的错误信息
     */
    public static synchronized String load(File libnode) {
        if (loaded) return null;
        if (libnode == null || !libnode.exists()) return "libnode.so not found";
        String error = nativeLoad(libnode.getAbsolutePath());
        loaded = error == null;
        return error;
    }

    /**
     * 启动 Node 并执行入口脚本。会阻塞到事件循环结束，调用方必须放在独立线程。
     *
     * @param script 入口脚本绝对路径
     * @param extra  追加到 node 之后、脚本之前的参数（如 --max-old-space-size=...）
     */
    public static int start(File script, String... extra) {
        if (!loaded) return -1;
        String[] argv = new String[2 + (extra == null ? 0 : extra.length)];
        int i = 0;
        argv[i++] = "node";
        if (extra != null) for (String s : extra) argv[i++] = s;
        argv[i] = script.getAbsolutePath();
        return nativeStart(argv);
    }
}
