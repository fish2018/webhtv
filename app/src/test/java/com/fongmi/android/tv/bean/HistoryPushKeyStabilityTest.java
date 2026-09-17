package com.fongmi.android.tv.bean;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 网盘（push）历史记录的 key 必须稳定。
 *
 * <p>push 的 key 第二段就是那条播放地址本身（SiteApi#pushDetail 把 id 原样塞进 Vod），
 * 而从历史进入播放正是靠 {@link History#getVodId()} 反解这一段。普通站点的 vodId 是稳定的
 * 条目 id，迁移无害；push 迁移会把记录改写成详情阶段解析出的另一条地址，那条地址往往带
 * 时效，下次从历史打开必然失败，用户只能回网盘重新找。
 */
public class HistoryPushKeyStabilityTest {

    @Test
    public void pushHistoryIsExcludedFromKeyMigration() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/bean/History.java");

        int replace = source.indexOf("public void replace(String key)");
        int guard = source.indexOf("if (isPushHistory()) return;", replace);
        int firstWrite = source.indexOf("String previous = getKey();", replace);

        assertTrue("replace must exist", replace > 0);
        assertTrue("push records must bail out of key migration", guard > replace);
        assertTrue("the guard must run before any migration work", guard < firstWrite);
    }

    @Test
    public void pushExemptionsStayConsistentAcrossMergeAndMigration() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/bean/History.java");

        // 按名合并早已对 push 豁免（多个网盘链接常同名）；key 迁移是同一类问题的另一面，
        // 两处必须共用同一个判据，避免其中一处被改动时另一处静默失配
        assertTrue("the push predicate must be shared", source.contains("private boolean isPushHistory()"));
        assertTrue("name merging must stay exempt", source.contains("return !isPushHistory();"));
        assertTrue("the predicate must key off the push site prefix",
                source.contains("key.startsWith(SiteApi.PUSH + AppDatabase.SYMBOL)"));
    }

    @Test
    public void pushKeysSurviveTheVodIdRoundTrip() {
        // 迁移被挡住的前提是 key 保持原样，反解才能拿回可用地址
        History history = new History();
        history.setKey("push_agent@@@https://pan.quark.cn/s/eab19bde98be@@@48");

        assertTrue("a push key must still resolve to its playable url",
                history.getVodId().startsWith("https://pan.quark.cn/"));
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of(path.substring("app/".length())), StandardCharsets.UTF_8);
    }
}
