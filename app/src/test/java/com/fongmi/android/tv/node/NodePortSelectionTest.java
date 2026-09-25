package com.fongmi.android.tv.node;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 锁定猫源端口选择：魔改 bundle（如把弹幕服务器合并进去的）会在同一进程里起多个 HTTP 服务，
 * 句柄顺序不保证猫源那个在前。只落第一个端口会让 App 连到附带服务，取配置吃 401 信封，
 * sites 解析为空——表现为「订阅无效」且无任何报错。
 */
public class NodePortSelectionTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void bootPublishesEveryServerPortWithPreferredFirst() throws IOException {
        String source = read("com/fongmi/android/tv/node/NodeBoot.java");
        int publish = source.indexOf("const publish = () =>");
        assertTrue("NodeBoot 必须生成 publish()", publish >= 0);

        int collect = source.indexOf("ports.push(a.port)", publish);
        assertTrue("必须收集全部 Server 句柄的端口，而不是只取第一个", collect > publish);
        assertTrue("不得在拿到第一个端口后就直接落盘返回",
                source.indexOf("writeFileSync('\" + portEscaped + \"', String(a.port))") < 0);

        int unshift = source.indexOf("ports.unshift(want)", collect);
        assertTrue("我们通过 DEV_HTTP_PORT 指定的端口必须排在候选最前", unshift > collect);

        int write = source.indexOf("ports.join(',')", unshift);
        assertTrue("候选端口必须逗号分隔一起落盘", write > unshift);
    }

    @Test
    public void bootWritesPortFileAtomically() throws IOException {
        String source = read("com/fongmi/android/tv/node/NodeBoot.java");
        int tmp = source.indexOf("portEscaped + \".tmp'");
        int rename = source.indexOf("renameSync", tmp < 0 ? 0 : tmp);
        assertTrue("端口文件要先写临时文件，避免 Java 侧并发读到半截端口号", tmp >= 0);
        assertTrue("临时文件必须 rename 到位", rename > tmp);
    }

    /** 目标端口始终不出现时（bundle 忽略 DEV_HTTP_PORT），轮询会跑满 30 秒——期间不该反复重写同样的内容。 */
    @Test
    public void bootSkipsRewriteWhenCandidatesUnchanged() throws IOException {
        String source = read("com/fongmi/android/tv/node/NodeBoot.java");
        int guard = source.indexOf("if (text === last) return");
        int write = source.indexOf("writeFileSync", guard < 0 ? 0 : guard);
        assertTrue("候选集没变要提前返回，不重写端口文件", guard >= 0);
        assertTrue("提前返回必须排在写文件之前", write > guard);
    }

    @Test
    public void bootKeepsPublishingUntilPreferredPortAppears() throws IOException {
        String source = read("com/fongmi/android/tv/node/NodeBoot.java");
        int ret = source.indexOf("return ports.includes(want)");
        assertTrue("附带服务可能比猫源晚绑定，publish 要到目标端口出现才算完成", ret >= 0);
        assertTrue("轮询仍需有次数上限兜底", source.indexOf("++tries > 150", ret) > ret);
    }

    @Test
    public void waitReadyProbesCandidatesAndValidatesConfigShape() throws IOException {
        String source = read("com/fongmi/android/tv/node/NodeService.java");
        int method = source.indexOf("private int waitReady(");
        assertTrue("NodeService 必须有 waitReady", method >= 0);

        int read = source.indexOf("readPorts(portFile)", method);
        assertTrue("waitReady 必须读候选端口集合", read > method);

        int loop = source.indexOf("for (int candidate : candidates)", read);
        assertTrue("必须逐个探候选端口", loop > read);

        int validate = source.indexOf("CatSource.isConfig(cfg)", loop);
        assertTrue("必须按配置形状认准端口——401 信封和欢迎页都是非空响应，只判空会认错", validate > loop);
        assertTrue("不得再用「响应非空」当就绪判据",
                source.indexOf("if (!TextUtils.isEmpty(text)) return true;") < 0);

        int assign = source.indexOf("return candidate;", validate);
        assertTrue("只有校验通过的端口才可采纳", assign > validate);
    }

    @Test
    public void waitReadyRereadsPortFileEachRound() throws IOException {
        String source = read("com/fongmi/android/tv/node/NodeService.java");
        int method = source.indexOf("private int waitReady(");
        assertTrue("NodeService 必须有 waitReady", method >= 0);
        int read = source.indexOf("readPorts(portFile)", method);
        assertTrue("waitReady 必须在循环内每轮重读端口文件", read > method);
    }

    @Test
    public void runtimeStartHasBoundedFailureHandling() throws IOException {
        String runtime = read("com/fongmi/android/tv/node/NodeRuntime.java");
        String source = read("com/fongmi/android/tv/api/CatSource.java");

        assertTrue("NodeRuntime 启动必须有超时", runtime.contains("START_TIMEOUT_MS"));
        assertTrue("启动超时必须停止 NodeService", runtime.contains("NodeService.stop(context)"));
        assertTrue("启动超时必须复位 STARTING", runtime.contains("STARTING.compareAndSet(true, false)"));
        assertTrue("CatSource 等待必须有时间上限", source.contains("latch.await(60, TimeUnit.SECONDS)"));
    }

    @Test
    public void timeoutInvalidatesLateRepliesAndKeepsTheStoppingServiceVisible() throws IOException {
        String runtime = read("com/fongmi/android/tv/node/NodeRuntime.java");
        int timeout = runtime.indexOf("private static synchronized void timeout(");
        int stop = runtime.indexOf("NodeService.stop(context);", timeout);
        int invalidate = runtime.indexOf("START_GENERATION.incrementAndGet();", timeout);
        int clearUrl = runtime.indexOf("servingUrl = \"\";", timeout);
        int nextMethod = runtime.indexOf("private static boolean same(", timeout);

        assertTrue("timeout must invalidate the old generation before stopping its service",
                timeout >= 0 && invalidate > timeout && invalidate < stop);
        assertTrue("timeout must keep the stopping service visible until the next start waits for process death",
                clearUrl < timeout || clearUrl > nextMethod);
        assertTrue("start and timeout must serialize the state transition so a retry cannot race the generation invalidation",
                runtime.contains("public static synchronized void start(")
                        && runtime.contains("private static synchronized void timeout("));
    }

    @Test
    public void nodeServiceStopsAfterReportingStartupError() throws IOException {
        String service = read("com/fongmi/android/tv/node/NodeService.java");
        int method = service.indexOf("private void sendError(");
        int send = service.indexOf("reply.send(msg)", method);
        int stop = service.indexOf("stopSelf();", method);

        assertTrue("报告启动错误后必须停止前台 Service，不能让失败的 :node 常驻",
                method >= 0 && send > method && stop > send);
    }

    @Test
    public void staleSuppressionCreditsAccumulateAcrossReaderClosures() throws IOException {
        String router = read("com/fongmi/android/tv/ui/novel/NovelRouter.java");
        assertTrue("多次关闭时不能覆盖尚未消费的迟到结果额度",
                router.contains("staleChapterResults.addAndGet(pending);")
                        && router.contains("Math.max(staleUntil, readerClosedAt + PENDING_CHAPTER_TTL)"));
    }

    /**
     * 复用运行中的 Node 必须过 {@code servesCurrentSource}（比完整来源身份），
     * 而不是自己拼一个只看地址的条件——后者在服务端原地更新 bundle 后会继续跑旧 JS。
     * {@code servingSourceKey} 只能在 READY 时从磁盘读实际安装值，不能提前写请求值。
     */
    @Test
    public void runtimeReuseGoesThroughFullSourceIdentityCheck() throws IOException {
        String runtime = read("com/fongmi/android/tv/node/NodeRuntime.java");
        int reuse = runtime.indexOf("NodeBundle.servesCurrentSource(url, servingSourceKey)");
        int start = runtime.indexOf("if (!STARTING.compareAndSet(false, true))");

        assertTrue("复用判定必须调用 servesCurrentSource", reuse >= 0);
        assertTrue("复用判定必须在真正启动之前", start > reuse);

        // 枚举所有赋值，而不是排除某一种拼写：右值只允许「清空」或「从磁盘读实际安装值」。
        // 否则任何新写法（比如把请求侧算出的 key 直接塞进来）都能绕过检查。
        java.util.List<String> assigned = new java.util.ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("servingSourceKey\\s*=\\s*([^;]+);").matcher(runtime);
        while (matcher.find()) assigned.add(matcher.group(1).trim());

        assertTrue("必须有 servingSourceKey 的赋值", !assigned.isEmpty());
        assertTrue("已安装来源键只能在 READY 后从磁盘读取",
                assigned.contains("NodeBundle.installedSourceKey(App.get())"));
        for (String value : assigned) {
            assertTrue("servingSourceKey 不能被赋成 " + value + "（只允许 \"\" 或 installedSourceKey）",
                    value.equals("\"\"") || value.equals("NodeBundle.installedSourceKey(App.get())"));
        }
    }

    @Test
    public void bundleMetadataAndFirstDownloadsUseBoundedFailClosedValidation() throws IOException {
        String bundle = read("com/fongmi/android/tv/node/NodeBundle.java");
        int metadata = bundle.indexOf("private static String remoteMd5(String url)");
        int download = bundle.indexOf("private static PreparedFile download(");

        assertTrue("远端 md5 响应必须限长读取，避免错误响应造成无界内存占用",
                metadata >= 0
                        && bundle.indexOf("MAX_METADATA_BYTES", metadata) > metadata
                        && bundle.indexOf("readLimited", metadata) > metadata
                        && !bundle.contains("OkHttp.string(md5Url(url))"));
        assertTrue("首次下载必须要求有效 md5，不能在元数据失败时接受任意响应",
                download >= 0
                        && bundle.indexOf("if (!isMd5(expected))", download) > download
                        && bundle.indexOf("if (isMd5(expected) && !expected.equalsIgnoreCase(actual))", download) > download);
    }

    /**
     * {@code ByteArrayOutputStream.toString(Charset)} 是 API 33 才有的重载，而 minSdk 是 24，
     * {@code desugar_jdk_libs_nio} 也不覆盖 {@code java.io.ByteArrayOutputStream}。用了它，
     * Android 13 以下一进 readLimited 就 NoSuchMethodError——而 source.key/stamp 的读取全走这里，
     * 等于整个猫源功能在旧系统上必崩。单元测试跑在 JVM 上，测不出来，只能锁源码。
     */
    @Test
    public void textDecodingStaysWithinMinSdkApiSurface() throws IOException {
        String bundle = read("com/fongmi/android/tv/node/NodeBundle.java");
        assertTrue("readLimited 必须用 API 24 就有的 toString(String)，不能用 API 33 的 toString(Charset)",
                bundle.contains("output.toString(StandardCharsets.UTF_8.name())")
                        && !bundle.contains("output.toString(StandardCharsets.UTF_8)"));
    }

    @Test
    public void readPortsParsesListAndTolueratesLegacySingleValue() throws Exception {
        assertEquals("多端口按逗号解析，顺序保持落盘顺序（猫源在前）",
                Arrays.asList(9988, 9321), readPorts("9988,9321"));
        assertEquals("单端口旧格式要照样能解析", Collections.singletonList(9988), readPorts("9988"));
        assertEquals("带换行/空格也要能解析", Arrays.asList(9988, 9321), readPorts(" 9988 , 9321 \n"));
        assertEquals("坏分片跳过而不是让整体失败", Collections.singletonList(9988), readPorts("9988,abc,0,-1"));
        assertEquals("重复端口去重", Collections.singletonList(9988), readPorts("9988,9988"));
        assertEquals("空内容返回空列表", Collections.emptyList(), readPorts(""));
        assertEquals("纯垃圾内容返回空列表", Collections.emptyList(), readPorts("not-a-port"));
    }

    @Test
    public void readPortsReturnsEmptyWhenFileMissing() throws Exception {
        File missing = new File(folder.getRoot(), "absent");
        assertEquals("端口文件还没写出来时不能抛异常", Collections.emptyList(), invokeReadPorts(missing));
    }

    @SuppressWarnings("unchecked")
    private List<Integer> readPorts(String content) throws Exception {
        File file = folder.newFile();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return invokeReadPorts(file);
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> invokeReadPorts(File file) throws Exception {
        Method method = NodeRuntime.class.getDeclaredMethod("readPorts", File.class);
        method.setAccessible(true);
        return (List<Integer>) method.invoke(null, file);
    }

    private static String read(String relative) throws IOException {
        Path path = mainJava().resolve(Paths.get(relative.replace('/', java.io.File.separatorChar)));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path mainJava() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve(Paths.get("app", "src", "main", "java"));
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("app/src/main/java not found from " + Paths.get("").toAbsolutePath());
    }
}
