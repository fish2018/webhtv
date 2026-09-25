package com.fongmi.android.tv.node;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 猫源本地包的安装、缓存判定和失败回滚——这些是有状态的路径，出错的后果是「装了 A 跑 B」
 * 或者「缓存永久坏掉只能清数据」，所以要真建目录、真装、真读磁盘来验证。
 *
 * <p>{@code NodeBundle} 的这些入口都接一个运行目录（而不是 Context），正是为了能这样测。
 */
public class NodeBundleInstallTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File runtime;

    private File runtime() throws IOException {
        if (runtime == null) runtime = folder.newFolder("node-bundle");
        return runtime;
    }

    @Test
    public void installsLocalDirectoryAndMarksItCached() throws IOException {
        File dir = runtime();
        File source = pack("pkg", "module.exports={a:1};", "module.exports={site:1};");

        assertNull("完整的本地目录包必须能装上", NodeBundle.ensure(dir, source.getAbsolutePath(), null));
        assertEquals("module.exports={a:1};", read(new File(dir, "index.js")));
        assertEquals("module.exports={site:1};", read(new File(dir, "index.config.js")));
        assertTrue("来源键必须落盘，否则下次判不出缓存归属", read(new File(dir, "source.key")).startsWith("local:"));
        assertEquals("stamp 必须是实际内容的 md5", md5("module.exports={a:1};"), read(new File(dir, "index.js.md5")));
    }

    /** 内容变了必须真的换掉运行目录里的文件，否则改完配置仍跑旧 config。 */
    @Test
    public void reinstallingChangedSourceReplacesRuntimeFiles() throws IOException {
        File dir = runtime();
        File source = pack("pkg", "module.exports={a:1};", "module.exports={site:1};");
        assertNull(NodeBundle.ensure(dir, source.getAbsolutePath(), null));
        String firstKey = read(new File(dir, "source.key"));

        Files.write(new File(source, "index.config.js").toPath(), "module.exports={site:2};".getBytes(StandardCharsets.UTF_8));
        assertNull(NodeBundle.ensure(dir, source.getAbsolutePath(), null));

        assertEquals("新 config 必须生效", "module.exports={site:2};", read(new File(dir, "index.config.js")));
        assertFalse("来源键必须随内容变化", firstKey.equals(read(new File(dir, "source.key"))));
    }

    /**
     * 解压出来的目录是用户可写的，改 index.config.js 换站点是本地包的主要用法，marker
     * 不会跟着更新。硬校验会把这批当前可用的包判死，且用户没有自救路径。
     */
    @Test
    public void markerMismatchInDirectoryDoesNotBlockInstall() throws IOException {
        File dir = runtime();
        File source = pack("pkg", "module.exports={a:1};", "module.exports={site:1};");
        Files.write(new File(source, "index.js.md5").toPath(), "ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8));

        assertNull("marker 与内容不符只该告警，不该阻断", NodeBundle.ensure(dir, source.getAbsolutePath(), null));
        assertEquals("装进去的必须是实际内容", "module.exports={a:1};", read(new File(dir, "index.js")));
        assertEquals("stamp 记的必须是实际内容的 md5，而不是那个过期 marker",
                md5("module.exports={a:1};"), read(new File(dir, "index.js.md5")));
    }

    /**
     * md5sum 生成的 marker 是「hash  文件名」两段格式。zip 形态是硬校验，认不出这个格式
     * 就会把整包判成「校验值无效」——而这类包在改动前是能正常安装的。
     */
    @Test
    public void md5sumStyleMarkerIsAcceptedInZip() throws IOException {
        File dir = runtime();
        String body = "module.exports={a:1};";
        File zip = zip("md5sum.zip", md5(body) + "  index.js", body, "module.exports={site:1};");

        assertNull("md5sum 格式的 marker 必须能用", NodeBundle.ensure(dir, zip.getAbsolutePath(), null));
        assertEquals(body, read(new File(dir, "index.js")));
    }

    /** zip 里的内容用户改不了，marker 不符就是包损坏，必须拒绝而不是装进去。 */
    @Test
    public void corruptZipIsRejected() throws IOException {
        File dir = runtime();
        File zip = zip("bad.zip", "ffffffffffffffffffffffffffffffff", "module.exports={a:1};", "module.exports={site:1};");

        String error = NodeBundle.ensure(dir, zip.getAbsolutePath(), null);
        assertNotNull("marker 与内容不符的 zip 必须拒绝", error);
        assertFalse("拒绝后不能留下运行文件", new File(dir, "index.js").isFile());
    }

    @Test
    public void installsZipPackage() throws IOException {
        File dir = runtime();
        String body = "module.exports={a:2};";
        File zip = zip("ok.zip", md5(body), body, "module.exports={site:9};");

        assertNull(NodeBundle.ensure(dir, zip.getAbsolutePath(), null));
        assertEquals(body, read(new File(dir, "index.js")));
        assertTrue(read(new File(dir, "source.key")).startsWith("local:"));
    }

    @Test
    public void missingConfigIsRejectedWithActionableMessage() throws IOException {
        File dir = runtime();
        File source = folder.newFolder("partial");
        Files.write(new File(source, "index.js.md5").toPath(), md5("x").getBytes(StandardCharsets.UTF_8));
        Files.write(new File(source, "index.js").toPath(), "x".getBytes(StandardCharsets.UTF_8));

        String error = NodeBundle.ensure(dir, source.getAbsolutePath(), null);
        assertNotNull("缺 config 的包必须报错", error);
        assertTrue("错误要指明缺什么：" + error, error.contains("index.config.js"));
        assertFalse("失败时不能留下半截运行文件", new File(dir, "index.js").isFile());
    }

    /** 装到一半被杀会留下 staging 目录和临时文件，不清会一直吃内部存储。 */
    @Test
    public void ensureSweepsLeftoversFromKilledProcess() throws IOException {
        File dir = runtime();
        File stage = new File(dir, ".stage-abandoned");
        stage.mkdirs();
        Files.write(new File(stage, "index.js").toPath(), "leftover".getBytes(StandardCharsets.UTF_8));
        File backup = new File(dir, "index.js.backup-123.tmp");
        Files.write(backup.toPath(), "old".getBytes(StandardCharsets.UTF_8));

        File source = pack("pkg", "module.exports={a:1};", "module.exports={site:1};");
        assertNull(NodeBundle.ensure(dir, source.getAbsolutePath(), null));

        assertFalse("staging 残留必须被清掉", stage.exists());
        assertFalse("backup 残留必须被清掉", backup.exists());
    }

    @Test
    public void sweepKeepsInstalledFilesAndRemovesOnlyLeftovers() throws IOException {
        File dir = runtime();
        File source = pack("pkg", "module.exports={a:1};", "module.exports={site:1};");
        assertNull(NodeBundle.ensure(dir, source.getAbsolutePath(), null));

        File stage = new File(dir, ".stage-x");
        stage.mkdirs();
        File temp = new File(dir, "index.js.999.tmp");
        Files.write(temp.toPath(), "t".getBytes(StandardCharsets.UTF_8));

        NodeBundle.sweep(dir);

        assertFalse(stage.exists());
        assertFalse("writeAtomic 的临时文件也要清", temp.exists());
        assertTrue("已装好的 bundle 不能被清掉", new File(dir, "index.js").isFile());
        assertTrue(new File(dir, "index.config.js").isFile());
        assertTrue(new File(dir, "source.key").isFile());
    }

    // ---- isCached：缓存判定的每条拒绝理由都要真的生效，否则会把不对的内容当就绪跑起来 ----

    @Test
    public void isCachedAcceptsOnlySelfConsistentVerifiedFiles() throws IOException {
        File dir = runtime();
        String body = "module.exports={a:1};";
        File target = new File(dir, "index.js");
        File stamp = new File(dir, "index.js.md5");
        File source = new File(dir, "source.key");
        Files.write(target.toPath(), body.getBytes(StandardCharsets.UTF_8));
        Files.write(stamp.toPath(), md5(body).getBytes(StandardCharsets.UTF_8));
        Files.write(source.toPath(), "remote:https://h/x:k".getBytes(StandardCharsets.UTF_8));

        assertTrue("内容、stamp、来源键、服务端校验值全部一致才算命中",
                NodeBundle.isCached(target, stamp, source, "remote:https://h/x:k", md5(body)));
        assertTrue("服务端校验值拿不到时退回自洽判定",
                NodeBundle.isCached(target, stamp, source, "remote:https://h/x:k", ""));

        assertFalse("来源键为空不能命中", NodeBundle.isCached(target, stamp, source, "", md5(body)));
        assertFalse("来源键不属于这份缓存不能命中",
                NodeBundle.isCached(target, stamp, source, "remote:https://h/other:k", md5(body)));
        assertFalse("服务端校验值与实际内容不符不能命中",
                NodeBundle.isCached(target, stamp, source, "remote:https://h/x:k", "ffffffffffffffffffffffffffffffff"));
        assertFalse("目标文件不存在不能命中",
                NodeBundle.isCached(new File(dir, "absent.js"), stamp, source, "remote:https://h/x:k", md5(body)));

        Files.write(stamp.toPath(), "ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8));
        assertFalse("stamp 与实际内容不符不能命中（回滚失败留下的混合态）",
                NodeBundle.isCached(target, stamp, source, "remote:https://h/x:k", ""));

        Files.write(stamp.toPath(), "TODO".getBytes(StandardCharsets.UTF_8));
        assertFalse("stamp 不是合法 md5 不能命中",
                NodeBundle.isCached(target, stamp, source, "remote:https://h/x:k", ""));
    }

    @Test
    public void isCachedRejectsEmptyTarget() throws IOException {
        File dir = runtime();
        File target = new File(dir, "index.js");
        File stamp = new File(dir, "index.js.md5");
        File source = new File(dir, "source.key");
        Files.write(target.toPath(), new byte[0]);
        Files.write(stamp.toPath(), md5("").getBytes(StandardCharsets.UTF_8));
        Files.write(source.toPath(), "k".getBytes(StandardCharsets.UTF_8));

        assertFalse("0 字节的目标文件不是有效缓存", NodeBundle.isCached(target, stamp, source, "k", ""));
    }

    // ---- 来源键补齐：不补会让每次加载配置都白白重启一遍 :node 子进程 ----

    /**
     * 旧版本落盘的来源键没有内容指纹。两个文件都命中缓存时要就地补成当前身份，
     * 否则 servesCurrentSource 每次都判「身份不符」→ 每次都重启子进程。
     */
    @Test
    public void legacySourceKeyIsRefreshedOnceContentIsVerified() throws IOException {
        File dir = runtime();
        String bundle = "module.exports={a:1};";
        String config = "module.exports={site:1};";
        String url = "https://host/cat/index.js.md5";
        seedInstalled(dir, bundle, config, "remote:https://host/cat/index.js");
        String key = NodeBundle.remoteSourceKey(url, md5(bundle), md5(config));

        // 旧格式的键仍然「属于」这个地址，所以缓存判定本身能命中——这是补齐的前提。
        assertTrue("旧格式的键必须仍被认作该地址的缓存",
                NodeBundle.installedIsRemoteOf(read(new File(dir, "source.key")), url));
        assertTrue("内容与服务端一致时缓存必须命中，不该重下",
                NodeBundle.isCached(new File(dir, "index.js"), new File(dir, "index.js.md5"),
                        new File(dir, "source.key"), read(new File(dir, "source.key")), md5(bundle)));

        assertTrue("来源键必须被补成当前身份", NodeBundle.refreshSourceKey(dir, key));
        assertEquals(key, read(new File(dir, "source.key")));
        assertFalse("已经是当前身份时不该重复改写", NodeBundle.refreshSourceKey(dir, key));
        assertFalse("空身份不能覆盖掉已有的键", NodeBundle.refreshSourceKey(dir, ""));
        assertEquals("拒绝改写时键必须原样保留", key, read(new File(dir, "source.key")));
    }

    /**
     * 守卫：身份里的摘要必须与两个 stamp 一致才允许写入。只靠调用点的前置条件的话，
     * 将来有人从别处调它就会静默写入一个与内容不符的身份，让下次复用判定认错。
     */
    @Test
    public void refreshRejectsKeyThatDoesNotMatchInstalledStamps() throws IOException {
        File dir = runtime();
        String bundle = "module.exports={a:1};";
        String config = "module.exports={site:1};";
        String url = "https://host/cat/index.js.md5";
        seedInstalled(dir, bundle, config, "remote:https://host/cat/index.js");
        String stale = "ffffffffffffffffffffffffffffffff";

        assertFalse("bundle 摘要与 stamp 不符时不能写入",
                NodeBundle.refreshSourceKey(dir, NodeBundle.remoteSourceKey(url, stale, md5(config))));
        assertFalse("config 摘要不符同样不能写入",
                NodeBundle.refreshSourceKey(dir, NodeBundle.remoteSourceKey(url, md5(bundle), stale)));
        assertFalse("拆不出摘要的旧格式身份不能写入",
                NodeBundle.refreshSourceKey(dir, "remote:https://host/cat/index.js"));
        assertEquals("被拒绝时键必须原样保留",
                "remote:https://host/cat/index.js", read(new File(dir, "source.key")));

        assertTrue("摘要与两个 stamp 都一致时才允许写入",
                NodeBundle.refreshSourceKey(dir, NodeBundle.remoteSourceKey(url, md5(bundle), md5(config))));
    }

    /**
     * 服务端只改 index.config.js（最常规的更新形态）时，index.js 一字未变，必须仍判命中。
     * 把两个文件的指纹揉进一个键、再拿它当单文件的缓存键，就会每次配置更新都重下整包。
     */
    @Test
    public void updatingOnlyConfigKeepsBundleCached() throws IOException {
        File dir = runtime();
        String bundle = "module.exports={a:1};";
        String oldConfig = "module.exports={site:1};";
        String newConfig = "module.exports={site:2};";
        String url = "https://host/cat/index.js.md5";
        seedInstalled(dir, bundle, oldConfig, NodeBundle.remoteSourceKey(url, md5(bundle), md5(oldConfig)));

        // 服务端公布的新校验值：bundle 没变、config 变了
        boolean[] cached = NodeBundle.remoteCacheState(dir, url, md5(bundle), md5(newConfig));
        assertTrue("index.js 没变就必须命中，不能因为 config 更新了而重下整包", cached[0]);
        assertFalse("index.config.js 变了必须判未命中", cached[1]);
    }

    /** 两个都没变时必须全命中，否则每次加载都白跑一遍下载。 */
    @Test
    public void unchangedRemoteBundleIsFullyCached() throws IOException {
        File dir = runtime();
        String bundle = "module.exports={a:1};";
        String config = "module.exports={site:1};";
        String url = "https://host/cat/index.js.md5";
        seedInstalled(dir, bundle, config, NodeBundle.remoteSourceKey(url, md5(bundle), md5(config)));

        boolean[] cached = NodeBundle.remoteCacheState(dir, url, md5(bundle), md5(config));
        assertTrue(cached[0]);
        assertTrue(cached[1]);
    }

    /** 旧格式来源键也要能命中，否则升级后白重下几十 MB。 */
    @Test
    public void legacyKeyStillYieldsCacheHits() throws IOException {
        File dir = runtime();
        String bundle = "module.exports={a:1};";
        String config = "module.exports={site:1};";
        String url = "https://host/cat/index.js.md5";
        seedInstalled(dir, bundle, config, "remote:https://host/cat/index.js");

        boolean[] cached = NodeBundle.remoteCacheState(dir, url, md5(bundle), md5(config));
        assertTrue("旧格式键的缓存必须仍可命中", cached[0]);
        assertTrue(cached[1]);
    }

    /** 缓存属于别的地址时一律不能命中，否则会拿 A 源的 bundle 当 B 源用。 */
    @Test
    public void cacheBelongingToAnotherUrlNeverHits() throws IOException {
        File dir = runtime();
        String bundle = "module.exports={a:1};";
        String config = "module.exports={site:1};";
        seedInstalled(dir, bundle, config,
                NodeBundle.remoteSourceKey("https://host/cat/index.js.md5", md5(bundle), md5(config)));

        boolean[] cached = NodeBundle.remoteCacheState(dir, "https://host/other/index.js.md5", md5(bundle), md5(config));
        assertFalse(cached[0]);
        assertFalse(cached[1]);
    }

    // ---- installedDigests：离线交叉校验的期望值来源，拆错会让校验静默失效 ----

    @Test
    public void installedDigestsExtractsVerifiedChecksums() {
        String a = "b24cea4ad00908b04d0fbe8d0a01999e";
        String b = "ffffffffffffffffffffffffffffffff";

        String[] parsed = NodeBundle.installedDigests("remote:https://host/cat/index.js:" + a + ":" + b);
        assertEquals(a, parsed[0]);
        assertEquals(b, parsed[1]);

        assertEquals("带端口的地址不能影响拆分", a,
                NodeBundle.installedDigests("remote:http://host:8080/x/index.js:" + a + ":" + b)[0]);

        for (String bad : new String[]{"remote:https://host/cat/index.js", "remote:", ":", "", null,
                "remote:https://host/a:b:c"}) {
            String[] empty = NodeBundle.installedDigests(bad);
            assertEquals("旧格式/畸形输入必须拆不出摘要：" + bad, "", empty[0]);
            assertEquals("", empty[1]);
        }
    }

    // ---- install 的回滚：失败后必须回到旧缓存，或把缓存标记为不可信，绝不留下混合态 ----

    @Test
    public void installReplacesBothFilesAndStamps() throws IOException {
        File dir = runtime();
        // 先铺一份旧的，否则 install 里 backup 分支根本不会执行，下面的 backup 断言就是空的。
        seedInstalled(dir, "old-bundle", "old-config", "local:/old:a:b");
        NodeBundle.PreparedFile bundle = staged(dir, "index.js", "new-bundle");
        NodeBundle.PreparedFile config = staged(dir, "index.config.js", "new-config");

        assertNull(NodeBundle.install(dir, bundle, config, "local:/pkg:a:b"));
        assertEquals("new-bundle", read(new File(dir, "index.js")));
        assertEquals("new-config", read(new File(dir, "index.config.js")));
        assertEquals(md5("new-bundle"), read(new File(dir, "index.js.md5")));
        assertEquals(md5("new-config"), read(new File(dir, "index.config.js.md5")));
        assertEquals("local:/pkg:a:b", read(new File(dir, "source.key")));
        // staging 目录由 ensure 的 finally 清理，install 只负责不留自己的 backup 临时文件。
        assertFalse("发布成功后必须删掉 backup", hasTempLeftover(dir));
    }

    /**
     * config 的 staging 文件缺失（进程被杀、磁盘满）时 install 必然失败。此时 bundle 已经
     * 发布出去了，必须把旧的搬回来——否则下次启动跑的是新 JS + 旧 config 的混合态。
     */
    @Test
    public void installRollsBackWhenSecondFileCannotBePublished() throws IOException {
        File dir = runtime();
        seedInstalled(dir, "old-bundle", "old-config", "local:/old:a:b");

        NodeBundle.PreparedFile bundle = staged(dir, "index.js", "new-bundle");
        NodeBundle.PreparedFile missing = new NodeBundle.PreparedFile(new File(dir, ".stage-gone/index.config.js"), md5("x"));

        assertNotNull("staging 文件缺失必须报错", NodeBundle.install(dir, bundle, missing, "local:/new:c:d"));
        assertEquals("bundle 必须回滚成旧内容", "old-bundle", read(new File(dir, "index.js")));
        assertEquals("config 不能被动过", "old-config", read(new File(dir, "index.config.js")));
        assertEquals("stamp 必须回滚", md5("old-bundle"), read(new File(dir, "index.js.md5")));
        assertEquals("来源键必须回滚", "local:/old:a:b", read(new File(dir, "source.key")));
        assertFalse("回滚后不能留下 backup 残留", hasTempLeftover(dir));
    }

    /** 首次安装（无旧缓存）失败时不能留下半截文件，否则 isCached 可能把它当有效缓存。 */
    @Test
    public void installLeavesNothingBehindWhenFirstInstallFails() throws IOException {
        File dir = runtime();
        NodeBundle.PreparedFile bundle = staged(dir, "index.js", "new-bundle");
        NodeBundle.PreparedFile missing = new NodeBundle.PreparedFile(new File(dir, ".stage-gone/index.config.js"), md5("x"));

        assertNotNull(NodeBundle.install(dir, bundle, missing, "local:/new:c:d"));
        assertFalse("已发布的 bundle 必须被撤销", new File(dir, "index.js").isFile());
        assertFalse("不能留下 stamp", new File(dir, "index.js.md5").isFile());
        assertFalse("不能留下来源键", new File(dir, "source.key").isFile());
    }

    private NodeBundle.PreparedFile staged(File dir, String name, String body) throws IOException {
        File staging = new File(dir, ".stage-" + name);
        staging.mkdirs();
        File file = new File(staging, name);
        Files.write(file.toPath(), body.getBytes(StandardCharsets.UTF_8));
        return new NodeBundle.PreparedFile(file, md5(body));
    }

    private void seedInstalled(File dir, String bundle, String config, String key) throws IOException {
        Files.write(new File(dir, "index.js").toPath(), bundle.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "index.config.js").toPath(), config.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "index.js.md5").toPath(), md5(bundle).getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "index.config.js.md5").toPath(), md5(config).getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "source.key").toPath(), key.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean hasTempLeftover(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File file : files) if (file.getName().endsWith(".tmp")) return true;
        return false;
    }

    private File pack(String name, String bundle, String config) throws IOException {
        File dir = folder.newFolder(name);
        Files.write(new File(dir, "index.js").toPath(), bundle.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "index.config.js").toPath(), config.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "index.js.md5").toPath(), md5(bundle).getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private File zip(String name, String marker, String bundle, String config) throws IOException {
        File file = new File(folder.getRoot(), name);
        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(file))) {
            entry(out, "index.js.md5", marker);
            entry(out, "index.js", bundle);
            entry(out, "index.config.js", config);
        }
        return file;
    }

    private static void entry(java.util.zip.ZipOutputStream out, String name, String body) throws IOException {
        out.putNextEntry(new java.util.zip.ZipEntry(name));
        out.write(body.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
    }

    private static String md5(String text) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder();
            for (byte item : digest) value.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
            return value.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
