package com.fongmi.android.tv;

import java.util.concurrent.TimeUnit;

public class Constant {

    public static final long INTERVAL_SEEK = TimeUnit.SECONDS.toMillis(10);
    public static final long INTERVAL_HIDE = TimeUnit.SECONDS.toMillis(5);
    public static final long TIMEOUT_VOD = TimeUnit.SECONDS.toMillis(30);
    public static final long TIMEOUT_LIVE = TimeUnit.SECONDS.toMillis(30);
    public static final long TIMEOUT_EPG = TimeUnit.SECONDS.toMillis(5);
    public static final long TIMEOUT_XML = TimeUnit.SECONDS.toMillis(15);
    public static final long TIMEOUT_PLAY = TimeUnit.SECONDS.toMillis(15);
    public static final long TIMEOUT_SYNC = TimeUnit.SECONDS.toMillis(2);
    public static final long TIMEOUT_SYNC_TRANSFER = TimeUnit.MINUTES.toMillis(10);
    public static final long TIMEOUT_SEARCH = TimeUnit.SECONDS.toMillis(30);
    public static final long TIMEOUT_PARSE_DEF = TimeUnit.SECONDS.toMillis(15);
    /** 超级解析需要按顺序给多个 Web 解析器单独机会。 */
    public static final long TIMEOUT_PARSE_SUPER = TimeUnit.SECONDS.toMillis(90);
    /** Web 解析页通常还要加载 iframe/WASM/播放器接口，不能与 JSON 请求共用 15 秒。 */
    public static final long TIMEOUT_PARSE_WEB = TimeUnit.SECONDS.toMillis(30);
    public static final long TIMEOUT_PARSE_LIVE = TimeUnit.SECONDS.toMillis(10);
    public static long getOpEdLimit(long duration) {
        if (duration < TimeUnit.MINUTES.toMillis(15)) return TimeUnit.MINUTES.toMillis(3);
        if (duration < TimeUnit.MINUTES.toMillis(30)) return TimeUnit.MINUTES.toMillis(6);
        return TimeUnit.MINUTES.toMillis(10);
    }
}
