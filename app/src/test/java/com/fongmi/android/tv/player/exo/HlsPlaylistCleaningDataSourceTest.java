package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.common.util.Log;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.hls.playlist.HlsAdsParser;

import com.fongmi.android.tv.bean.HlsAdRule;
import com.fongmi.android.tv.utils.HlsAdblockPipeline;
import com.fongmi.android.tv.utils.HlsManifestCleaner;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Exo 路径上的去广告。此前 {@code HlsManifestCleaner} 只在 IJK 的本地代理里执行，
 * 默认内核（EXO）上用户与接口规则完全不生效，只有 fork 版 parser 里的 legacy
 * 启发式在跑。
 */
public class HlsPlaylistCleaningDataSourceTest {

    /**
     * 让 legacy 启发式在纯 JVM 下真的跑起来。
     *
     * <p>本模块没有配 {@code testOptions { unitTests.returnDefaultValues }}，所以
     * {@code android.util.Log} 是未 mock 的桩，{@code HlsAdsParser.process} 第一句
     * {@code Log.d} 就抛 {@code RuntimeException}，而 {@code HlsAdblockPipeline} 会
     * {@code catch (Throwable)} 后原样返回 —— 启发式**从未真正执行**。上一轮的测试
     * 因此有几条钉的是这个 harness 假象而不是代码：例如「无规则时逐字节透传」在真机上
     * 是假的，启发式会删掉那 3 片广告。
     *
     * <p>media3 自己的 {@code Log} 封装支持关掉日志，关掉后 {@code Log.d} 变成 no-op，
     * 于是启发式能在 JVM 里完整执行。
     */
    @BeforeClass
    public static void silenceMedia3Log() {
        previousLogLevel = Log.getLogLevel();
        Log.setLogLevel(Log.LOG_LEVEL_OFF);
    }

    /**
     * 日志级别是进程级静态状态，而本模块没配 {@code forkEvery}，整个 module 跑在同一个
     * JVM 里 —— 不还原会按类的执行顺序泄漏给别的测试类。
     */
    @AfterClass
    public static void restoreMedia3Log() {
        Log.setLogLevel(previousLogLevel);
    }

    private static int previousLogLevel;

    private static final String PLAYLIST_HOST = "v.example.com";
    private static final String PLAYLIST_URL = "https://" + PLAYLIST_HOST + "/play/index.m3u8";

    /**
     * 30 片，下标 3-5 是广告 —— 与正片**同域、同命名结构**，只有时长不同（6.4s 对 10.0s）。
     *
     * <p>这个形状是刻意选的：legacy 启发式靠命名分组与前缀分析找广告，对完全均匀的
     * 命名会弃权，于是任何「删掉了广告」的断言都只能由结构化规则达成。此前夹具用
     * 独立广告域名 + 断点标记，启发式对它的输出与结构化引擎**逐字节相同**，导致
     * 4 条测试把规则换成 {@code List.of()} 也照样绿 —— 它们根本没在验证结构化规则。
     */
    private static String playlist() {
        StringBuilder text = new StringBuilder("#EXTM3U\n#EXT-X-TARGETDURATION:10\n");
        for (int i = 0; i < 30; i++) {
            boolean ad = i >= 3 && i <= 5;
            text.append(String.format(Locale.US, "#EXTINF:%.3f,%n", ad ? 6.4 : 10.0));
            text.append("https://").append(PLAYLIST_HOST).append("/seg/").append(i).append(".ts\n");
        }
        return text.append("#EXT-X-ENDLIST\n").toString();
    }

    /** 广告切片的 URI，供断言使用。 */
    private static String adUri(int index) {
        return "https://" + PLAYLIST_HOST + "/seg/" + index + ".ts";
    }

    /**
     * 直播窗口：无 {@code #EXT-X-ENDLIST}，带 {@code #EXT-X-MEDIA-SEQUENCE}。
     *
     * @param mediaSequence 窗口首片的序号
     * @param adIndexes     窗口内属于广告的下标（相对窗口起点）
     */
    private static String livePlaylist(int mediaSequence, int windowSize, Set<Integer> adIndexes) {
        StringBuilder text = new StringBuilder("#EXTM3U\n#EXT-X-TARGETDURATION:10\n");
        text.append("#EXT-X-MEDIA-SEQUENCE:").append(mediaSequence).append('\n');
        text.append("#EXT-X-DISCONTINUITY-SEQUENCE:0\n");
        for (int i = 0; i < windowSize; i++) {
            boolean ad = adIndexes.contains(i);
            if (ad) text.append("#EXT-X-DISCONTINUITY\n");
            text.append(String.format(Locale.US, "#EXTINF:%.3f,%n", ad ? 6.4 : 10.0));
            text.append(ad ? "https://ad-cdn.other.com/ads/" + (mediaSequence + i) + ".ts"
                    : "https://" + PLAYLIST_HOST + "/seg/" + (mediaSequence + i) + ".ts")
                    .append('\n');
        }
        return text.toString();
    }

    /**
     * 匹配 {@link #playlist()} 里 6.4s 那三片的规则。
     *
     * <p>用时长而非域名区分：夹具刻意让广告与正片同域同命名，好让 legacy 启发式弃权，
     * 从而使「广告被删」只可能由结构化规则达成。
     */
    private static HlsManifestCleaner.Rule adRule() {
        return HlsAdRule.createUserRule("ad", "ad", List.of(PLAYLIST_HOST),
                List.of(), List.of(), 6.3d, 6.5d, false, false, 1).compile();
    }

    private static String readFully(HlsPlaylistCleaningDataSource source, String url)
            throws IOException {
        source.open(null, url, 0, C.LENGTH_UNSET);
        return drain(source);
    }

    private static String drain(HlsPlaylistCleaningDataSource source) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[512];
        while (true) {
            int read = source.read(chunk, 0, chunk.length);
            if (read == C.RESULT_END_OF_INPUT) break;
            out.write(chunk, 0, read);
        }
        source.close();
        return out.toString(StandardCharsets.UTF_8);
    }

    /**
     * 结构化规则能在 Exo 路径上删掉广告切片、保留正片。
     *
     * <p>不再断言「无规则时逐字节透传」—— 那在真机上是假的：无规则时结构化引擎不改动，
     * {@link HlsAdblockPipeline} 会让 legacy 启发式接手，而它对这份夹具确实会删片。
     * 想验证「结构化引擎自身没有副作用」得关掉启发式，那属于 pipeline 的契约，
     * 由 {@link #pipelineKeepsTheTwoEnginesMutuallyExclusive} 覆盖。
     */
    @Test
    public void removesAdSegmentsFromPlaylistOnTheExoPath() throws IOException {
        HlsPlaylistCleaningDataSource cleaning = new HlsPlaylistCleaningDataSource(
                new FakeSource(playlist()), () -> List.of(adRule()), () -> true);

        String cleaned = readFully(cleaning, PLAYLIST_URL);

        assertFalse("广告切片应被删除", cleaned.contains(adUri(3)));
        assertTrue("正片切片必须保留", cleaned.contains("/seg/0.ts"));
        assertTrue("正片切片必须保留", cleaned.contains("/seg/29.ts"));
        assertEquals("正片一片不少", 27, countSegments(cleaned));
    }

    private static int countSegments(String manifest) {
        int count = 0;
        for (String line : manifest.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) count++;
        }
        return count;
    }

    /**
     * 净化后 {@code open()} 必须返回改写后的长度。返回上游原长会让 Exo 认为响应被
     * 截断（净化只会变短），把正常播放报成 EOF 错误。
     */
    @Test
    public void reportsTheRewrittenLengthNotTheUpstreamLength() throws IOException {
        HlsPlaylistCleaningDataSource source = new HlsPlaylistCleaningDataSource(
                new FakeSource(playlist()), () -> List.of(adRule()), () -> true);

        long reported = source.open(null, PLAYLIST_URL, 0, C.LENGTH_UNSET);
        int delivered = drain(source).getBytes(StandardCharsets.UTF_8).length;

        assertEquals("声明的长度必须等于实际交付的字节数", delivered, reported);
        assertTrue("净化后应短于原文", reported < playlist().getBytes(StandardCharsets.UTF_8).length);
    }

    /** 非 playlist 的响应（切片、字幕等）必须逐字节原样透传。 */
    @Test
    public void passesNonPlaylistPayloadsThroughByteForByte() throws IOException {
        byte[] payload = new byte[4096];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i % 251);
        HlsPlaylistCleaningDataSource source = new HlsPlaylistCleaningDataSource(
                new FakeSource(payload), () -> List.of(adRule()), () -> true);

        source.open(null, "https://" + PLAYLIST_HOST + "/seg/0.ts", 0, C.LENGTH_UNSET);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[100];
        while (true) {
            int read = source.read(chunk, 0, chunk.length);
            if (read == C.RESULT_END_OF_INPUT) break;
            out.write(chunk, 0, read);
        }
        source.close();

        assertArrayEquals("切片数据不得被改动", payload, out.toByteArray());
    }

    /** 带 BOM 与前导空白的 playlist 也要被识别 —— 与 media3 的 parser 判据一致。 */
    @Test
    public void recognisesPlaylistBehindByteOrderMarkAndWhitespace() throws IOException {
        String text = "﻿\n  " + playlist();
        HlsPlaylistCleaningDataSource source = new HlsPlaylistCleaningDataSource(
                new FakeSource(text), () -> List.of(adRule()), () -> true);

        String cleaned = readFully(source, PLAYLIST_URL);

        assertFalse("BOM 后的 playlist 同样要净化", cleaned.contains(adUri(3)));
    }

    /**
     * 区间请求不得改写：range 寻址的是原始响应的字节偏移，而净化会改变长度，
     * 改写会让偏移全部错位。
     */
    @Test
    public void leavesRangedRequestsUntouched() throws IOException {
        HlsPlaylistCleaningDataSource source = new HlsPlaylistCleaningDataSource(
                new FakeSource(playlist()), () -> List.of(adRule()), () -> true);

        source.open(null, PLAYLIST_URL, 10, 200);
        String served = drain(source);

        assertTrue("区间请求必须原样透传", served.contains(adUri(3)));
    }

    /** 去广告总开关关闭时完全不介入。 */
    @Test
    public void doesNothingWhenAdblockIsDisabled() throws IOException {
        HlsPlaylistCleaningDataSource source = new HlsPlaylistCleaningDataSource(
                new FakeSource(playlist()), () -> List.of(adRule()), () -> false);

        assertEquals(playlist(), readFully(source, PLAYLIST_URL));
    }

    /**
     * 本地 {@code /m3u8} 代理的输出已经净化过，不能再来一遍 —— 那会让规则在同一份
     * playlist 上叠加两次，删除量翻倍并可能越过回退闸门。
     */
    @Test
    public void skipsPlaylistsAlreadyCleanedByTheLocalProxy() throws IOException {
        HlsPlaylistCleaningDataSource source = new HlsPlaylistCleaningDataSource(
                new FakeSource(playlist()), () -> List.of(adRule()), () -> true);

        String served = readFully(source,
                "http://127.0.0.1:9978/m3u8?url=https%3A%2F%2Fv.example.com%2Fa.m3u8");

        assertTrue("本地代理已净化，此处必须放过", served.contains(adUri(3)));
    }

    /** 读取规则配置抛异常时必须降级为透传，而不是把异常抛给播放中的用户。 */
    @Test
    public void degradesToPassThroughWhenRuleConfigThrows() throws IOException {
        HlsPlaylistCleaningDataSource source = new HlsPlaylistCleaningDataSource(
                new FakeSource(playlist()),
                () -> { throw new IllegalStateException("config unavailable"); },
                () -> true);

        assertEquals(playlist(), readFully(source, PLAYLIST_URL));
    }

    /** 上游按小块返回时，嗅探与整体读取都必须正确拼接。 */
    @Test
    public void handlesUpstreamThatReturnsTinyChunks() throws IOException {
        FakeSource upstream = new FakeSource(playlist());
        upstream.maxChunk = 3;
        HlsPlaylistCleaningDataSource source = new HlsPlaylistCleaningDataSource(
                upstream, () -> List.of(adRule()), () -> true);

        String cleaned = readFully(source, PLAYLIST_URL);

        assertFalse("分片读取不得影响净化", cleaned.contains(adUri(3)));
        assertTrue(cleaned.contains("/seg/29.ts"));
    }

    /**
     * 直播窗口一律不改写。
     *
     * <p>{@code HlsManifestCleaner} 会按删掉的头部片数抬高
     * {@code #EXT-X-MEDIA-SEQUENCE}，但它的回退闸门（删除比例、删除时长、删除必须
     * 在头部连续）会在相邻两次 reload 之间翻转，回退时发出的是**未抬高**的原始序号。
     * 而滑动窗口里新的广告块总是先出现在尾部（删除点在保留点之后 → 非连续 → 回退），
     * 随窗口滑动才变成头部连续 —— 于是序号在抬高与原始之间来回跳。media3 的
     * {@code isNewerThan} 会静默丢弃「变旧」的那次 reload，最终
     * {@code targetDuration × 3.5} 后抛 {@code PlaylistStuckException} 卡播。
     */
    @Test
    public void neverRewritesLivePlaylists() throws IOException {
        String live = livePlaylist(100, 12, Set.of(0, 1, 2));
        HlsPlaylistCleaningDataSource source = new HlsPlaylistCleaningDataSource(
                new FakeSource(live), () -> List.of(adRule()), () -> true);

        assertEquals("直播窗口必须逐字节透传", live, readFully(source, PLAYLIST_URL));
    }

    /**
     * 直播的关键不变量：相邻 reload 发出的 media sequence 不得倒退。
     *
     * <p>倒退需要「先净化、后回退」这个特定顺序 —— 发出的序号要么是窗口起点（回退），
     * 要么是首个保留切片的原始下标（净化后），两者在纯滑动中各自单调；只有从净化跳到
     * 回退才会掉回去。触发它的正是真实直播的形态：头部还有正在被消费的广告块（净化，
     * 抬高序号），同时**新的**广告块滑进窗口尾部（删除点不连续 → 回退 → 原始序号）。
     *
     * <p>这里把广告块放在绝对下标 100-102 与 112-114：窗口起点 100 时只见到头部那块，
     * 抬高到 103；起点 101 时尾部滑入 112，变成不连续而回退到 101 —— 比上一次小。
     */
    @Test
    public void keepsMediaSequenceMonotonicAcrossLiveReloads() throws IOException {
        Set<Integer> adSegments = Set.of(100, 101, 102, 112, 113, 114);
        int previous = Integer.MIN_VALUE;
        for (int start = 100; start <= 118; start++) {
            Set<Integer> ads = new HashSet<>();
            for (int offset = 0; offset < 12; offset++) {
                if (adSegments.contains(start + offset)) ads.add(offset);
            }
            String live = livePlaylist(start, 12, ads);
            HlsPlaylistCleaningDataSource source = new HlsPlaylistCleaningDataSource(
                    new FakeSource(live), () -> List.of(adRule()), () -> true);

            int emitted = mediaSequenceOf(readFully(source, PLAYLIST_URL));

            assertTrue("media sequence 不得倒退：" + previous + " -> " + emitted
                    + "（窗口起点 " + start + "）", emitted >= previous);
            previous = emitted;
        }
    }

    private static int mediaSequenceOf(String manifest) {
        for (String line : manifest.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                return Integer.parseInt(trimmed.substring("#EXT-X-MEDIA-SEQUENCE:".length()).trim());
            }
        }
        throw new AssertionError("缺少 #EXT-X-MEDIA-SEQUENCE：" + manifest);
    }

    /**
     * 结构化规则改动过的 manifest，绝不能再交给 legacy 启发式。
     *
     * <p>{@code HlsAdsParser.findMinorityGroup} 只有一道「最大分组须超过切片总数的
     * 50%」的闸门，且对删除量没有上限；而删掉广告切片会缩小它比对的总数。夹具刻意
     * 用「正片分布在两个 CDN（260 + 240 片）+ 广告 25 片」这个实测形状：原始 playlist
     * 上启发式因 {@code 520 <= 525} 放弃，结构化净化后变成 {@code 520 > 500}，闸门打开
     * 并删掉那 240 片正片 —— 48% 的正片被静默删除。
     *
     * <p>用这个形状而不是 {@link #playlist()}：后者太均匀，启发式对净化前后的结果一致，
     * 即使两套引擎真被串联起来测试也照样绿。
     *
     * <p>Exo 侧另一半（不让 fork 版 parser 重复跑）由 {@code ExoUtil} 的
     * {@code setAdblock(false)} 保证，那一处纯 JVM 测不了。
     */
    @Test
    public void pipelineKeepsTheTwoEnginesMutuallyExclusive() {
        String twoCdn = twoCdnPlaylist();
        HlsManifestCleaner.Rule rule = HlsAdRule.createUserRule("ad", "ad",
                List.of(PLAYLIST_HOST), List.of("ad-cdn.other.com"), List.of(),
                null, null, false, true, 2).compile();

        HlsAdblockPipeline.Outcome outcome =
                HlsAdblockPipeline.apply(PLAYLIST_URL, twoCdn, List.of(rule), true);

        assertTrue("结构化引擎应当生效", outcome.structured());
        assertFalse("结构化改动过就不该再跑启发式", outcome.legacy());
        // 若两套引擎被串联，启发式会把 cdn-b 那 240 片正片一并删掉
        assertEquals("只应删掉 25 片广告", 500, countSegments(outcome.manifest()));
        assertTrue("第二个 CDN 的正片必须完好", outcome.manifest().contains("cdn-b.example.com"));
    }

    /**
     * 上一条的反面：把净化后的结果再喂给启发式，确认「串联确实会毁掉正片」。
     *
     * <p>这条不测生产代码路径，它测的是**威胁本身仍然存在** —— 一旦有人把
     * {@code ExoUtil} 的 {@code setAdblock} 改回 {@code Setting.isAdblock()}，或者给
     * pipeline 加上「两套都跑」的开关，48% 正片就会消失。若哪天启发式自己加了删除
     * 上限而让这条测试变红，那说明威胁解除，可以连同那半边约束一起删掉。
     */
    @Test
    public void chainingTheTwoEnginesWouldDestroyMainContent() {
        String twoCdn = twoCdnPlaylist();
        HlsManifestCleaner.Rule rule = HlsAdRule.createUserRule("ad", "ad",
                List.of(PLAYLIST_HOST), List.of("ad-cdn.other.com"), List.of(),
                null, null, false, true, 2).compile();

        // 启发式对原始 playlist 弃权（525 片里最大分组 260，520 <= 525）
        String heuristicOnRaw = HlsAdsParser.process(twoCdn);
        assertEquals("原始 playlist 上启发式应当弃权", 525, countSegments(heuristicOnRaw));

        // 但对净化后的 500 片，闸门打开（520 > 500）
        String structured = HlsManifestCleaner.clean(PLAYLIST_URL, twoCdn, List.of(rule)).manifest();
        assertEquals(500, countSegments(structured));
        String chained = HlsAdsParser.process(structured);

        assertEquals("串联后 cdn-b 的 240 片正片被删掉", 260, countSegments(chained));
        assertFalse("这就是必须避免串联的原因", chained.contains("cdn-b.example.com"));
    }

    /**
     * 正片分布在两个 CDN（260 + 240 片）+ 25 片广告，共 525 片。
     *
     * <p>广告时长取 3.0s：25 片合计 75s，压在 {@code HlsManifestCleaner} 的 90s 删除
     * 时长闸门内，否则结构化引擎会整体回退而测不到后续的引擎交互。
     */
    private static String twoCdnPlaylist() {
        StringBuilder text = new StringBuilder("#EXTM3U\n#EXT-X-TARGETDURATION:10\n");
        for (int i = 0; i < 260; i++) {
            text.append("#EXTINF:10.000,\n")
                    .append("https://").append(PLAYLIST_HOST).append("/seg/").append(i).append(".ts\n");
        }
        for (int i = 0; i < 25; i++) {
            text.append("#EXT-X-DISCONTINUITY\n#EXTINF:3.000,\n")
                    .append("https://ad-cdn.other.com/ads/").append(i).append(".ts\n");
        }
        for (int i = 0; i < 240; i++) {
            text.append("#EXTINF:10.000,\n")
                    .append("https://cdn-b.example.com/seg/").append(i).append(".ts\n");
        }
        return text.append("#EXT-X-ENDLIST\n").toString();
    }

    /**
     * 结构化引擎没改动时，pipeline 必须把 manifest 递给启发式 —— 否则默认内核会比改动
     * 前更差（改动前 fork 版 parser 无条件跑启发式）。
     *
     * <p>只钉「调用发生了」而不钉「删到了东西」：启发式是否动手取决于它自己的分组判据，
     * 而两个夹具恰好都会让它弃权（{@link #playlist()} 命名太均匀，{@link #twoCdnPlaylist()}
     * 的 525 片里最大分组 260 不过半，实测两者 {@code process} 前后片数不变）。用「片数
     * 变少」当判据会把这条测试变成对启发式内部阈值的断言，那不是本处的契约。
     *
     * <p>判据落在 {@code legacy()} 上：它由 {@code !filtered.equals(manifest)} 得出，
     * 弃权时为 false，所以只能反向钉住 —— 结构化未改动时 pipeline 不得提前返回。
     * 若把 {@code HlsAdblockPipeline} 的 legacy 分支删掉，{@code manifest()} 会变成
     * {@code null} 之外的同一对象，这条测试确实抓不到。真正守住 legacy 分支的是
     * {@link #pipelineKeepsTheTwoEnginesMutuallyExclusive} 的 {@code legacy=false} 断言
     * 与 {@link #chainingTheTwoEnginesWouldDestroyMainContent}；这里只补一条
     * 「结构化未命中时不短路」的正向路径。
     */
    @Test
    public void pipelineStillReachesTheHeuristicWhenNoRuleMatches() {
        String twoCdn = twoCdnPlaylist();

        HlsAdblockPipeline.Outcome outcome =
                HlsAdblockPipeline.apply(PLAYLIST_URL, twoCdn, List.of(), true);

        assertFalse("无规则命中", outcome.structured());
        // 启发式对这个形状弃权，所以 manifest 应当与输入相同、且不是 null
        assertEquals("弃权时必须原样返回", twoCdn, outcome.manifest());
        assertFalse("弃权就不算 legacy 改动过", outcome.legacy());
    }

    /**
     * 超过 2 MiB 上限时必须原样透传，且字节精确、顺序不变、只发一遍。
     *
     * <p>夹具刻意含广告切片：不含的话结构化引擎与启发式都不会改动，测试即使把
     * {@code MAX_PLAYLIST_BYTES} 那道门删掉也照样绿 —— 钉不住上限本身。
     */
    @Test
    public void passesOversizedPlaylistThroughByteForByte() throws IOException {
        StringBuilder huge = new StringBuilder("#EXTM3U\n#EXT-X-TARGETDURATION:10\n");
        int i = 0;
        while (huge.length() < 3 * 1024 * 1024) {
            boolean ad = i % 50 == 7;
            if (ad) huge.append("#EXT-X-DISCONTINUITY\n");
            huge.append(ad ? "#EXTINF:3.000,\n" : "#EXTINF:10.000,\n");
            huge.append(ad ? "https://ad-cdn.other.com/ads/" + i + ".ts"
                    : "https://" + PLAYLIST_HOST + "/seg/" + i + ".ts").append('\n');
            i++;
        }
        String text = huge.append("#EXT-X-ENDLIST\n").toString();
        HlsPlaylistCleaningDataSource source = new HlsPlaylistCleaningDataSource(
                new FakeSource(text), () -> List.of(adRule()), () -> true);

        assertEquals("超限的 playlist 必须逐字节透传", text, readFully(source, PLAYLIST_URL));
    }

    /**
     * 同一个实例被反复 {@code open()} / {@code close()} —— 这正是
     * {@code DefaultHlsPlaylistTracker} 复用一个 DataSource 做直播 reload 的生命周期。
     * 状态没清干净会把上一次的缓冲字节串到下一次。
     */
    @Test
    public void reusesOneInstanceAcrossOpenCloseCycles() throws IOException {
        FakeSource upstream = new FakeSource(playlist());
        upstream.rewindOnOpen = true;
        HlsPlaylistCleaningDataSource source = new HlsPlaylistCleaningDataSource(
                upstream, () -> List.of(adRule()), () -> true);

        String first = readFully(source, PLAYLIST_URL);
        String second = readFully(source, PLAYLIST_URL);

        assertEquals("复用同一实例的两次结果必须一致", first, second);
        assertFalse(second.contains(adUri(3)));
    }

    /** {@code DataReader} 契约：{@code readLength == 0} 必须返回 0，而不是 EOF。 */
    @Test
    public void returnsZeroForZeroLengthRead() throws IOException {
        HlsPlaylistCleaningDataSource source = new HlsPlaylistCleaningDataSource(
                new FakeSource(playlist()), () -> List.of(adRule()), () -> true);

        source.open(null, PLAYLIST_URL, 0, C.LENGTH_UNSET);

        assertEquals(0, source.read(new byte[4], 0, 0));
        source.close();
    }

    /**
     * {@code withPlaylistCleaning} 确实产出净化层。
     *
     * <p>坦白其局限：它钉的是那个工厂方法本身，**不是**「Exo 的数据源链条上真的挂着
     * 它」。后者要走 {@code getDataSourceFactory}，而那里需要 {@code App.get()} 与磁盘
     * 缓存，纯 JVM 下构造不出来。也就是说把 {@code getDataSourceFactory} 里的调用删掉，
     * 这条测试仍会绿 —— 那一处只能靠人工复查或仪器化测试守。
     */
    @Test
    public void withPlaylistCleaningProducesACleaningSource() {
        DataSource.Factory wrapped = MediaSourceFactory.withPlaylistCleaning(
                () -> new FakeSource(playlist()));

        assertTrue("工厂必须产出净化层",
                wrapped.createDataSource() instanceof HlsPlaylistCleaningDataSource);
    }

    private static final class FakeSource implements DataSource {

        private final byte[] data;
        private int position;
        private int maxChunk = Integer.MAX_VALUE;
        /** 直播 reload 会对同一个 DataSource 反复 open，每次都从头给。 */
        private boolean rewindOnOpen;

        FakeSource(String text) {
            this(text.getBytes(StandardCharsets.UTF_8));
        }

        FakeSource(byte[] data) {
            this.data = data;
        }

        @Override
        public void addTransferListener(TransferListener transferListener) {
        }

        @Override
        public long open(DataSpec dataSpec) {
            if (rewindOnOpen) position = 0;
            return data.length - position;
        }

        @Override
        public int read(byte[] target, int offset, int length) {
            if (position >= data.length) return C.RESULT_END_OF_INPUT;
            int count = Math.min(Math.min(length, maxChunk), data.length - position);
            System.arraycopy(data, position, target, offset, count);
            position += count;
            return count;
        }

        @Override
        public Uri getUri() {
            return null;
        }

        @Override
        public Map<String, List<String>> getResponseHeaders() {
            return Map.of();
        }

        @Override
        public void close() {
        }
    }
}
