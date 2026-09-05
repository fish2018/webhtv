package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.util.Log;

import com.fongmi.android.tv.bean.HlsAdRule;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Locale;

/**
 * 隐式 IV 的加密 playlist 必须让**两套引擎都不动手**。
 *
 * <p>这道门原先放在 {@code HlsManifestCleaner} 里，那是错的：cleaner 唯一的拒绝方式是
 * {@code Result.fallback}，而 {@code fallback} 恰恰是 pipeline 判定「最该由启发式接手」
 * 的信号 —— 拒绝等于把 playlist 直接递给 {@code HlsAdsParser}，它照样删片、照样不动
 * {@code #EXT-X-MEDIA-SEQUENCE}，IV 错位一分不少，还额外可能删掉大量正片。
 */
public class HlsEncryptionGuardTest {

    private static final String URL = "https://v.example.com/play/index.m3u8";
    private static int previousLogLevel;

    /**
     * 让 legacy 启发式在纯 JVM 下真的跑起来 —— 本模块没配
     * {@code unitTests.returnDefaultValues}，未 mock 的 {@code android.util.Log} 会让
     * {@code HlsAdsParser.process} 第一句就抛，而 pipeline 会 catch 掉，启发式实际从未执行。
     */
    @BeforeClass
    public static void silenceMedia3Log() {
        previousLogLevel = Log.getLogLevel();
        Log.setLogLevel(Log.LOG_LEVEL_OFF);
    }

    /** 日志级别是进程级静态状态，同一个 test JVM 里会泄漏给其他测试类。 */
    @AfterClass
    public static void restoreMedia3Log() {
        Log.setLogLevel(previousLogLevel);
    }

    private static HlsManifestCleaner.Rule adRule() {
        return HlsAdRule.createUserRule("ad", "ad", List.of("v.example.com"),
                List.of("ad-cdn.other.com"), List.of(), null, null, false, true, 2).compile();
    }

    /** 30 片，下标 3-5 是跨域广告；{@code keyLine} 为 null 时不加密。 */
    private static String playlist(String keyLine) {
        StringBuilder t = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n");
        t.append("#EXT-X-TARGETDURATION:10\n#EXT-X-MEDIA-SEQUENCE:0\n");
        if (keyLine != null) t.append(keyLine).append('\n');
        for (int i = 0; i < 30; i++) {
            boolean ad = i >= 3 && i <= 5;
            t.append(String.format(Locale.US, "#EXTINF:%.3f,%n", ad ? 6.4 : 10.0));
            t.append(ad ? "https://ad-cdn.other.com/ads/" + i + ".ts"
                    : "https://v.example.com/seg/" + i + ".ts").append('\n');
        }
        return t.append("#EXT-X-ENDLIST\n").toString();
    }

    private static int segmentCount(String manifest) {
        int count = 0;
        for (String line : manifest.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) count++;
        }
        return count;
    }

    /** 未加密的对照：两套引擎照常工作，证明夹具本身是可净化的。 */
    @Test
    public void plainPlaylistIsStillCleaned() {
        HlsAdblockPipeline.Outcome outcome =
                HlsAdblockPipeline.apply(URL, playlist(null), List.of(adRule()), true);

        assertTrue(outcome.structured());
        assertEquals(27, segmentCount(outcome.manifest()));
    }

    /**
     * 核心不变量：隐式 IV 时 manifest 必须逐字节原样返回，且两套引擎都没跑。
     *
     * <p>只断言 {@code structured==false} 是不够的 —— 那正是旧实现的状态，而启发式在
     * 它之后照样把片删了。必须同时钉住 {@code legacy==false} 与字节相等。
     */
    @Test
    public void impliedIvPlaylistIsUntouchedByBothEngines() {
        String manifest = playlist("#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"");

        HlsAdblockPipeline.Outcome outcome =
                HlsAdblockPipeline.apply(URL, manifest, List.of(adRule()), true);

        assertFalse("结构化引擎不得改动", outcome.structured());
        assertFalse("启发式也不得接手", outcome.legacy());
        assertEquals("必须逐字节原样返回", manifest, outcome.manifest());
        assertEquals(30, segmentCount(outcome.manifest()));
    }

    /** 显式 {@code IV=} 固定了 IV，与序号无关，应当照常净化。 */
    @Test
    public void explicitIvPlaylistIsStillCleaned() {
        String manifest = playlist(
                "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\",IV=0x0123456789abcdef0123456789abcdef");

        HlsAdblockPipeline.Outcome outcome =
                HlsAdblockPipeline.apply(URL, manifest, List.of(adRule()), true);

        assertTrue("显式 IV 不受影响", outcome.structured());
        assertEquals(27, segmentCount(outcome.manifest()));
    }

    /** {@code METHOD=NONE} 只是取消加密，不涉及 IV 推导。 */
    @Test
    public void methodNoneIsNotTreatedAsEncrypted() {
        HlsAdblockPipeline.Outcome outcome = HlsAdblockPipeline.apply(
                URL, playlist("#EXT-X-KEY:METHOD=NONE"), List.of(adRule()), true);

        assertTrue(outcome.structured());
        assertEquals(27, segmentCount(outcome.manifest()));
    }

    /**
     * 四个曾经骗过这道门的形状，全部必须被识别为隐式 IV。
     *
     * <p>前三条源于「把属性列表整体 uppercase 后做子串查找」这个朴素实现：查
     * {@code METHOD=NONE} 会被 URI 里的 {@code ?method=none} 命中；查 {@code IV=} 会被
     * {@code ?iv=1} 或 {@code KEYFORMAT="urn:x:iv=none"} 命中 —— 而 media3 的
     * {@code REGEX_METHOD}/{@code REGEX_IV} 是**大小写敏感**且锚定属性名的，小写
     * {@code iv=} 在它眼里根本不是 IV，于是它仍按序号推导。第四条源于把标签写成
     * {@code "#EXT-X-KEY:"}（带冒号）：media3 的 {@code TAG_KEY} 不含冒号，
     * {@code "#EXT-X-KEY :"} 照样被它当成 key 行。
     */
    @Test
    public void recognisesShapesThatDefeatNaiveSubstringMatching() {
        String[] keyLines = {
                // URI 的 query 里出现 method=none
                "#EXT-X-KEY:METHOD=AES-128,URI=\"https://k/x?method=none\"",
                // URI 的 query 里出现小写 iv=
                "#EXT-X-KEY:METHOD=AES-128,URI=\"https://k/k?iv=1&t=2\"",
                // KEYFORMAT 里出现小写 iv=
                "#EXT-X-KEY:METHOD=AES-128,URI=\"k.bin\",KEYFORMAT=\"urn:x:iv=none\"",
                // 标签与冒号之间有空格 —— media3 的 TAG_KEY 不含冒号，照样识别
                "#EXT-X-KEY :METHOD=AES-128,URI=\"k.bin\"",
        };

        for (String keyLine : keyLines) {
            String manifest = playlist(keyLine);
            HlsAdblockPipeline.Outcome outcome =
                    HlsAdblockPipeline.apply(URL, manifest, List.of(adRule()), true);

            assertEquals("必须原样返回：" + keyLine, manifest, outcome.manifest());
            assertFalse("结构化引擎不得改动：" + keyLine, outcome.structured());
            assertFalse("启发式不得接手：" + keyLine, outcome.legacy());
        }
    }

    /**
     * 纯 {@code \r} 换行的 playlist 同样要识别。
     *
     * <p>按 {@code "\n"} 切行会把整份 playlist 塌成一行，key 行就此消失；而
     * {@code BufferedReader.readLine} 认单独的 {@code \r} 为行结束，media3 能看到 key。
     */
    @Test
    public void recognisesKeyLineInCarriageReturnOnlyPlaylist() {
        String manifest = playlist("#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"")
                .replace("\n", "\r");

        HlsAdblockPipeline.Outcome outcome =
                HlsAdblockPipeline.apply(URL, manifest, List.of(adRule()), true);

        assertEquals("CR-only 也必须原样返回", manifest, outcome.manifest());
        assertFalse(outcome.structured());
        assertFalse(outcome.legacy());
    }

    /**
     * {@code IV = 0x…}（等号旁有空格）在 media3 眼里也不算固定 IV，必须拒绝。
     *
     * <p>这个方向本来就安全（朴素实现也会拒），留作回归：将来若有人把正则放宽成容忍
     * 空格，就会与 media3 产生分歧。
     */
    @Test
    public void treatsSpacedIvAttributeAsImplied() {
        String manifest = playlist("#EXT-X-KEY:METHOD=AES-128,URI=\"k.bin\",IV = 0x00");

        HlsAdblockPipeline.Outcome outcome =
                HlsAdblockPipeline.apply(URL, manifest, List.of(adRule()), true);

        assertEquals(manifest, outcome.manifest());
        assertFalse(outcome.structured());
    }

    /**
     * {@code #EXT-X-SESSION-KEY} 只作用于 multivariant playlist，不参与切片 IV 推导，
     * 不该被这道门挡住。
     *
     * <p>media3 的判据是 {@code line.startsWith("#EXT-X-KEY")}，而
     * {@code "#EXT-X-SESSION-KEY"} 不以它开头。
     */
    @Test
    public void sessionKeyDoesNotBlockCleaning() {
        HlsAdblockPipeline.Outcome outcome = HlsAdblockPipeline.apply(URL,
                playlist("#EXT-X-SESSION-KEY:METHOD=AES-128,URI=\"k.bin\""),
                List.of(adRule()), true);

        assertTrue("SESSION-KEY 不影响切片 IV", outcome.structured());
        assertEquals(27, segmentCount(outcome.manifest()));
    }

    /**
     * 小写 {@code #ext-x-key:} 在 media3 眼里不是 key 行（{@code startsWith} 大小写敏感），
     * 所以它看不到加密，净化是安全的。
     */
    @Test
    public void lowercaseKeyTagIsInvisibleToBothSides() {
        HlsAdblockPipeline.Outcome outcome = HlsAdblockPipeline.apply(URL,
                playlist("#ext-x-key:METHOD=AES-128,URI=\"k.bin\""),
                List.of(adRule()), true);

        assertTrue("media3 也看不到这行，判据必须一致", outcome.structured());
    }
}
