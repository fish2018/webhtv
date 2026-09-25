package com.fongmi.android.tv.node;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 猫源本地包的识别。
 *
 * <p>本地包是用户自己解压出来的 {@code index.js} + {@code index.config.js} 目录，判定文件是
 * {@code index.js.md5}（CatPawOpen 的发布约定）。没有这个标记就无法与「随便一个目录」区分，
 * 所以不能只看目录里有没有 index.js。
 */
public class NodeLocalBundleTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void recognizesDirectoryWithMarker() throws IOException {
        File dir = pack("pkg");
        assertEquals("目录里有 index.js.md5 就是本地包", dir, NodeBundle.localDir(dir.getAbsolutePath(), null));
    }

    @Test
    public void md5MarkersMustContainOnlyHexDigits() {
        assertTrue(NodeBundle.isMd5("b24cea4ad00908b04d0fbe8d0a01999e"));
        assertFalse("校验值不能只看长度", NodeBundle.isMd5("b24cea4ad00908b04d0fbe8d0a01999g"));
        assertFalse(NodeBundle.isMd5("b24cea4ad00908b04d0fbe8d0a01999"));
    }

    /**
     * {@code md5sum > index.js.md5} 生成的是「{@code <hash>  index.js}」两段格式。
     * 只按整行判定会把这种包判成「校验值无效」，而它在改动前是能正常安装的。
     */
    @Test
    public void markerAcceptsMd5sumOutputFormat() throws Exception {
        String hash = "b24cea4ad00908b04d0fbe8d0a01999e";
        java.lang.reflect.Method marker = NodeBundle.class.getDeclaredMethod("marker", String.class);
        marker.setAccessible(true);

        assertEquals("裸校验值要原样返回", hash, marker.invoke(null, hash));
        assertEquals("md5sum 双空格格式要取第一段", hash, marker.invoke(null, hash + "  index.js"));
        assertEquals("单空格格式也要支持", hash, marker.invoke(null, hash + " index.js"));
        assertEquals("制表符分隔同样处理", hash, marker.invoke(null, hash + "\tindex.js"));
        assertEquals("前后换行/空白要去掉", hash, marker.invoke(null, "  " + hash + "\n"));
        assertEquals("", marker.invoke(null, ""));
        assertEquals("", marker.invoke(null, (Object) null));
    }

    @Test
    public void localSourceKeyChangesWhenBundleContentChanges() throws IOException {
        File dir = pack("fingerprint");
        String before = NodeBundle.sourceKey(dir.getAbsolutePath(), null);
        Files.write(new File(dir, "index.js").toPath(), "module.exports={changed:true};".getBytes(StandardCharsets.UTF_8));
        String after = NodeBundle.sourceKey(dir.getAbsolutePath(), null);

        assertTrue("本地包内容变化后不能继续使用旧来源指纹", before.startsWith("local:") && !before.equals(after));
    }

    /** config 变了、index.js 没变，也必须换身份——否则改完配置仍跑旧 config。 */
    @Test
    public void localSourceKeyChangesWhenConfigContentChanges() throws IOException {
        File dir = pack("config-fingerprint");
        String before = NodeBundle.sourceKey(dir.getAbsolutePath(), null);
        Files.write(new File(dir, "index.config.js").toPath(), "module.exports={site:2};".getBytes(StandardCharsets.UTF_8));

        assertNotEquals("config 内容变化也必须换来源身份", before, NodeBundle.sourceKey(dir.getAbsolutePath(), null));
    }

    /** 本地来源永远不能与远端来源撞身份，否则换源时会复用彼此的运行缓存。 */
    @Test
    public void localAndRemoteKeysNeverCollide() throws IOException {
        File dir = pack("namespaced");
        String local = NodeBundle.sourceKey(dir.getAbsolutePath(), null);
        String md5 = "b24cea4ad00908b04d0fbe8d0a01999e";
        String remote = NodeBundle.remoteSourceKey("https://host/cat/index.js.md5", md5, md5);

        assertTrue("本地身份必须有 local: 前缀", local.startsWith("local:"));
        assertTrue("远端身份必须有 remote: 前缀", remote.startsWith("remote:"));
        assertNotEquals("本地与远端身份不能相等", local, remote);
        assertFalse("本地身份不能被误判成某个远端地址的缓存",
                NodeBundle.installedIsRemoteOf(local, "https://host/cat/index.js.md5"));
        assertEquals("非法地址不能产出身份", "", NodeBundle.sourceKey("", null));
        assertEquals("", NodeBundle.sourceKey(null, null));
    }

    @Test
    public void recognizesFileInsidePackageAndReturnsItsDirectory() throws IOException {
        File dir = pack("pkg");
        assertEquals("选中包里任意文件都该定位到包目录",
                dir, NodeBundle.localDir(new File(dir, "index.js.md5").getAbsolutePath(), null));
        assertEquals(dir, NodeBundle.localDir(new File(dir, "index.js").getAbsolutePath(), null));
        assertEquals(dir, NodeBundle.localDir(new File(dir, "index.config.js").getAbsolutePath(), null));
    }

    /**
     * 用户常把普通订阅 json 和本地包丢在同一个文件夹（比如都在 Download 里）。若只看「父目录有没有
     * index.js.md5」，那个 json 会被判成本地包，配置内容被整个忽略，表现为选了 A 源却加载出 B 源。
     */
    @Test
    public void rejectsUnrelatedFileSittingNextToPackage() throws IOException {
        File dir = pack("mixed");
        File other = new File(dir, "my-subscription.json");
        Files.write(other.toPath(), "{\"sites\":[]}".getBytes(StandardCharsets.UTF_8));
        assertNull("包外文件不能顺推到包目录", NodeBundle.localDir(other.getAbsolutePath(), null));
    }

    @Test
    public void resolvesPathRelativeToExternalRoot() throws IOException {
        File root = folder.newFolder("sdcard");
        File dir = new File(root, "Download/pkg");
        write(dir);
        // 文件选择器生成的形态：file:/ 加上相对外部存储根的路径
        assertEquals("选择器给的相对路径要能还原",
                dir, NodeBundle.localDir("file://Download/pkg/index.js.md5", root));
    }

    @Test
    public void rejectsDirectoryWithoutMarker() throws IOException {
        File dir = folder.newFolder("plain");
        Files.write(new File(dir, "index.js").toPath(), "x".getBytes(StandardCharsets.UTF_8));
        assertNull("没有 index.js.md5 的目录不能当本地包，否则任何目录都会被误认", NodeBundle.localDir(dir.getAbsolutePath(), null));
    }

    /**
     * 本地包在主进程判定、在 {@code :node} 子进程加载。中间用户挪走了包，ensure 会落到远端下载分支，
     * 而那套逻辑拿不到 md5 时一律复用已有缓存——会把上一个源的 bundle 当就绪跑起来。所以要能识别
     * 出「这地址根本下载不了」并如实报错。
     */
    @Test
    public void distinguishesRemoteFromLocalAddresses() {
        assertTrue(NodeBundle.isRemote("https://host/index.js.md5"));
        assertTrue(NodeBundle.isRemote("HTTP://host/index.js.md5"));
        assertTrue("前后空白不该影响判定", NodeBundle.isRemote("  https://host/index.js.md5 "));
        assertFalse("本地路径不是可下载地址", NodeBundle.isRemote("file://catpkg"));
        assertFalse(NodeBundle.isRemote("/sdcard/catpkg"));
        assertFalse(NodeBundle.isRemote(null));
        assertFalse(NodeBundle.isRemote(""));
    }

    /**
     * 远端身份必须带上服务端公布的校验值。只用 URL 的话，服务端原地更新 bundle
     * （地址不变、md5 变了）后仍会命中「同一来源」，继续跑旧 JS 直到进程被杀。
     */
    @Test
    public void remoteSourceKeyTracksServerPublishedChecksums() {
        String url = "https://host/cat/index.js.md5";
        String a = "b24cea4ad00908b04d0fbe8d0a01999e";
        String b = "ffffffffffffffffffffffffffffffff";

        String first = NodeBundle.remoteSourceKey(url, a, a);
        assertTrue("远端身份要有 remote: 前缀", first.startsWith("remote:"));
        assertNotEquals("bundle 校验值变化必须换身份", first, NodeBundle.remoteSourceKey(url, b, a));
        assertNotEquals("config 校验值变化必须换身份", first, NodeBundle.remoteSourceKey(url, a, b));
        assertEquals("同样的校验值必须得到同样的身份", first, NodeBundle.remoteSourceKey(url, a, a));
        assertEquals("带不带 .md5 后缀是同一个来源",
                first, NodeBundle.remoteSourceKey("https://host/cat/index.js", a, a));
        assertEquals("校验值大小写不该影响身份", first, NodeBundle.remoteSourceKey(url, a.toUpperCase(Locale.ROOT), a));
    }

    /** 拿不到校验值时必须返回空身份，让上层走「重新准备」而不是凭地址复用。 */
    @Test
    public void remoteSourceKeyIsEmptyWithoutValidChecksums() {
        String url = "https://host/cat/index.js.md5";
        String ok = "b24cea4ad00908b04d0fbe8d0a01999e";
        assertEquals("", NodeBundle.remoteSourceKey(url, "", ok));
        assertEquals("", NodeBundle.remoteSourceKey(url, ok, ""));
        assertEquals("非法 md5 不能产出身份", "", NodeBundle.remoteSourceKey(url, "TODO", ok));
        assertEquals("", NodeBundle.remoteSourceKey(url, null, ok));
    }

    /**
     * 断网降级只允许复用「确实属于该地址」的缓存。前缀比较必须带分隔符，
     * 否则 {@code https://host/cat} 的缓存会被 {@code https://host/cat2} 命中。
     */
    @Test
    public void offlineFallbackOnlyAcceptsCacheBelongingToTheSameUrl() {
        String md5 = "b24cea4ad00908b04d0fbe8d0a01999e";
        String installed = NodeBundle.remoteSourceKey("https://host/cat/index.js.md5", md5, md5);

        assertTrue(NodeBundle.installedIsRemoteOf(installed, "https://host/cat/index.js.md5"));
        assertTrue("带不带 .md5 后缀是同一个来源", NodeBundle.installedIsRemoteOf(installed, "https://host/cat/index.js"));
        assertFalse("别的地址不能复用这份缓存", NodeBundle.installedIsRemoteOf(installed, "https://host/other/index.js.md5"));
        assertFalse("前缀相同但不是同一地址也不行", NodeBundle.installedIsRemoteOf(installed, "https://host/cat/index.js2"));
        assertFalse("本地来源不能被当成远端缓存", NodeBundle.installedIsRemoteOf("local:/sdcard/pkg:a:b", "https://host/cat/index.js.md5"));
        assertFalse(NodeBundle.installedIsRemoteOf("", "https://host/cat/index.js.md5"));
    }

    /**
     * 旧版本落盘的来源键没有内容指纹（{@code remote:<url>}）。不认它的话，老用户升级后在
     * 断网状态下会被判成「没有该地址的缓存」，尽管两个文件都完好躺在磁盘上。
     */
    @Test
    public void offlineFallbackStillAcceptsLegacySourceKeys() {
        String url = "https://host/cat/index.js.md5";
        assertTrue("旧格式（无内容指纹）必须仍被认作该地址的缓存",
                NodeBundle.installedIsRemoteOf("remote:https://host/cat/index.js", url));
        assertFalse("旧格式同样不能跨地址复用",
                NodeBundle.installedIsRemoteOf("remote:https://host/other/index.js", url));
        assertFalse("前缀是子串但不是同一地址也不行",
                NodeBundle.installedIsRemoteOf("remote:https://host/cat/index.js2", url));
    }

    @Test
    public void rejectsRemoteAndMissingPaths() throws IOException {
        assertNull(NodeBundle.localDir(null, null));
        assertNull(NodeBundle.localDir("", null));
        assertNull("远端地址必须继续走下载分支", NodeBundle.localDir("https://host/index.js.md5", null));
        assertNull(NodeBundle.localDir("HTTP://host/index.js.md5", null));
        assertNull("不存在的路径不是本地包", NodeBundle.localDir(new File(folder.getRoot(), "absent").getAbsolutePath(), null));
    }

    @Test
    public void recognizesZipCarryingMarker() throws IOException {
        File zip = zip("pkg.zip", true);
        assertEquals("发布形态就是 zip，选中它要能识别", zip, NodeBundle.localZip(zip.getAbsolutePath(), null));
        assertNull("zip 不是解压目录，localDir 不该认它", NodeBundle.localDir(zip.getAbsolutePath(), null));
    }

    /**
     * 指纹和安装必须用同一个 root 解析路径。相对地址在 {@code <root>/path} 和 {@code /path}
     * 两处都存在时，若一边传 null 一边传 root，就会「算 A 包的指纹、装 B 包的内容」。
     */
    @Test
    public void sourceKeyResolvesPathsWithTheSameRootAsInstall() throws IOException {
        File root = folder.newFolder("sdcard");
        // 带随机后缀，避免依赖开发机上恰好没有 /Download/pkg（resolve 的最后一级回退是 "/" + path）
        String relative = "Download/pkg-" + java.util.UUID.randomUUID();
        File dir = new File(root, relative);
        write(dir);

        String viaRoot = NodeBundle.sourceKey(relative, root);
        assertTrue("相对路径要能按 root 解析出指纹", viaRoot.startsWith("local:"));
        assertTrue("指纹必须指向 root 下解析到的那个包", viaRoot.contains(dir.getCanonicalPath()));
        assertEquals("同一个 root 解析同一个地址必须稳定", viaRoot, NodeBundle.sourceKey(relative, root));
        assertEquals("换掉 root 后该相对路径不再存在，不能仍然返回旧指纹",
                "", NodeBundle.sourceKey(relative, folder.newFolder("other")));
    }

    /** 目录和 zip 是同一份包的两种形态，来源身份必须都带内容指纹。 */
    @Test
    public void zipSourceKeyCoversContentNotJustPath() throws IOException {
        File zip = zip("keyed.zip", true);
        String key = NodeBundle.sourceKey(zip.getAbsolutePath(), null);
        assertTrue("zip 也要有本地来源指纹", key.startsWith("local:"));
        assertTrue("指纹必须包含 zip 自身路径", key.contains(zip.getCanonicalPath()));

        // 同一个路径重新打一份内容不同的 zip：身份必须变，否则改完包仍跑旧 JS。
        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zip))) {
            entry(out, "index.js.md5", "b24cea4ad00908b04d0fbe8d0a01999e");
            entry(out, "index.js", "module.exports={changed:true};");
            entry(out, "index.config.js", "module.exports={};");
        }
        assertNotEquals("zip 内容变化必须换来源身份", key, NodeBundle.sourceKey(zip.getAbsolutePath(), null));
    }

    /** marker 内容不是合法 md5 的 zip 不能产出来源身份，否则伪造 marker 就能命中缓存。 */
    @Test
    public void zipWithBadMarkerHasNoSourceKey() throws IOException {
        File file = new File(folder.getRoot(), "bad.zip");
        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(file))) {
            entry(out, "index.js.md5", "TODO");
            entry(out, "index.js", "module.exports={};");
            entry(out, "index.config.js", "module.exports={};");
        }
        assertEquals("marker 非法时不能算出来源身份", "", NodeBundle.sourceKey(file.getAbsolutePath(), null));
    }

    /** 缺 index.config.js 的目录不是完整包，不能算出来源身份。 */
    @Test
    public void directoryMissingConfigHasNoSourceKey() throws IOException {
        File dir = folder.newFolder("partial");
        Files.write(new File(dir, "index.js.md5").toPath(), "b24cea4ad00908b04d0fbe8d0a01999e".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "index.js").toPath(), "module.exports={};".getBytes(StandardCharsets.UTF_8));
        assertEquals("缺 config 的包不能算出来源身份", "", NodeBundle.sourceKey(dir.getAbsolutePath(), null));
    }

    @Test
    public void rejectsUnrelatedZipAndNonZipFile() throws IOException {
        assertNull("不含 index.js.md5 的 zip 只是普通压缩包", NodeBundle.localZip(zip("other.zip", false).getAbsolutePath(), null));
        File plain = folder.newFile("notzip.bin");
        Files.write(plain.toPath(), "PK-not-really".getBytes(StandardCharsets.UTF_8));
        assertNull(NodeBundle.localZip(plain.getAbsolutePath(), null));
        assertNull(NodeBundle.localZip("https://host/pkg.zip", null));
    }

    private File zip(String name, boolean marker) throws IOException {
        File file = new File(folder.getRoot(), name);
        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(file))) {
            if (marker) entry(out, "index.js.md5", "b24cea4ad00908b04d0fbe8d0a01999e");
            entry(out, "index.js", "module.exports={};");
            entry(out, "index.config.js", "module.exports={};");
        }
        return file;
    }

    private static void entry(java.util.zip.ZipOutputStream out, String name, String body) throws IOException {
        out.putNextEntry(new java.util.zip.ZipEntry(name));
        out.write(body.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private File pack(String name) throws IOException {
        File dir = folder.newFolder(name);
        write(dir);
        return dir;
    }

    private static void write(File dir) throws IOException {
        dir.mkdirs();
        Files.write(new File(dir, "index.js.md5").toPath(), "b24cea4ad00908b04d0fbe8d0a01999e".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "index.js").toPath(), "module.exports={};".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "index.config.js").toPath(), "module.exports={};".getBytes(StandardCharsets.UTF_8));
    }
}
