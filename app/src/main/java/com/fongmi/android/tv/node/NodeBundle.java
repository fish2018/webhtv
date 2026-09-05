package com.fongmi.android.tv.node;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

import okhttp3.Response;

/**
 * 猫源 bundle（CatPawOpen 的 {@code index.js}）的下载、校验与本地缓存。
 *
 * <p>用户填的是 {@code .../index.js.md5}——那个地址返回 32 位校验值，真正的 bundle 在去掉
 * {@code .md5} 后缀的地址上。每次启动只拉几十字节的 md5 比对，命中就用本地缓存，
 * 避免重复下载 1.2MB 的 bundle。
 *
 * <p>本地包（用户自己解压出来的 {@code index.js} + {@code index.config.js} 目录，或还没解压
 * 的 zip）走同一套缓存判定，只是把「下载」换成「复制/解压」、把远端 md5 换成文件的实际 md5。
 *
 * <p>不把运行目录直接指到用户选的位置：Node 要在 bundle 同级写 {@code data/}、{@code port}、
 * {@code boot.js}，外部存储未必允许这些写入，且用户可能随时移走包。
 */
public final class NodeBundle {

    private static final String SUFFIX = ".md5";
    private static final String MARKER = "index.js.md5";
    private static final String SOURCE_STAMP = "source.key";
    private static final long MAX_ENTRY_BYTES = 32L * 1024L * 1024L;
    private static final int MAX_METADATA_BYTES = 4096;
    private static final int MAX_SOURCE_KEY_BYTES = 16 * 1024;
    /** 复用判定要抢在用户感知之前给结论，慢比错更难接受，所以比下载超时短得多。 */
    private static final long METADATA_TIMEOUT_MS = 3_000L;

    private static final java.util.Set<String> MEMBERS = new java.util.HashSet<>(java.util.Arrays.asList(
            "index.js", MARKER, "index.config.js", "index.config.js.md5"));

    private NodeBundle() {
    }

    public static boolean isLocal(String url) {
        try {
            File root = Path.root();
            return localDir(url, root) != null || localZip(url, root) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean isMd5(String value) {
        if (value == null || value.length() != 32) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    /**
     * 本地包（目录或 zip）的来源身份：路径 + 两个文件的内容指纹。
     *
     * <p>远端来源不走这里——它的身份要带上服务端公布的校验值，见 {@link #remoteSourceKey}。
     * 早先这里对远端会自己发两次 md5 请求，那让「算身份」这件事变成有网络副作用的操作。
     */
    static String sourceKey(String url) {
        return sourceKey(url, root());
    }

    /**
     * {@code root} 必须与 {@link #ensure} 用的是同一个，否则相对路径可能解析到不同文件——
     * 那会让指纹算的是 A 包、装的是 B 包。单测传 {@code null} 以避开 Environment。
     */
    static String sourceKey(String url, File root) {
        if (TextUtils.isEmpty(url)) return "";
        String value = url.trim();
        try {
            File local = localDir(value, root);
            if (local != null) {
                String bundle = contentSignature(new File(local, "index.js"));
                String config = contentSignature(new File(local, "index.config.js"));
                if (TextUtils.isEmpty(bundle) || TextUtils.isEmpty(config)) return "";
                return localSourceKey(local, bundle, config);
            }
            File zip = localZip(value, root);
            if (zip != null) return zipSourceKey(zip);
        } catch (Throwable ignored) {
        }
        return "";
    }

    /**
     * 远端身份必须带上服务端公布的校验值，否则「同一个 URL」就等于「同一份内容」——
     * 服务端原地更新 bundle（URL 不变、md5 变了）时会继续复用旧的运行缓存，跑的还是旧 JS。
     *
     * <p>拿不到校验值时返回空串：宁可当作身份未知重新走一遍 ensure，也不能凭 URL 相等就复用。
     */
    static String remoteSourceKey(String url, String bundleMd5, String configMd5) {
        if (!isMd5(bundleMd5) || !isMd5(configMd5)) return "";
        return "remote:" + bundleUrl(url) + ":" + lower(bundleMd5) + ":" + lower(configMd5);
    }

    /**
     * 已安装的来源键是否就是这个远端地址的（不比对内容，只看归属）。
     *
     * <p>也接受旧版本落盘的 {@code remote:<url>}（无内容指纹）：否则老用户升级后在断网
     * 状态下会被判成「没有该地址的缓存」，尽管两个文件都完好躺在磁盘上。
     */
    static boolean installedIsRemoteOf(String installed, String url) {
        if (TextUtils.isEmpty(installed)) return false;
        String prefix = "remote:" + bundleUrl(url);
        return installed.startsWith(prefix + ":") || installed.equals(prefix);
    }

    /** 从已安装的来源键里取出安装时已校验过的两个 md5，用于离线交叉校验。旧格式返回空。 */
    static String[] installedDigests(String installed) {
        if (TextUtils.isEmpty(installed)) return new String[]{"", ""};
        int config = installed.lastIndexOf(':');
        if (config <= 0) return new String[]{"", ""};
        int bundle = installed.lastIndexOf(':', config - 1);
        if (bundle <= 0) return new String[]{"", ""};
        String bundleMd5 = installed.substring(bundle + 1, config);
        String configMd5 = installed.substring(config + 1);
        if (!isMd5(bundleMd5) || !isMd5(configMd5)) return new String[]{"", ""};
        return new String[]{bundleMd5, configMd5};
    }

    /**
     * 运行中的猫源是否仍与来源一致，决定能否跳过重启直接复用。
     *
     * <p>判据是完整的来源身份（路径/地址 + 两个文件的内容指纹），而不是地址相等：
     * 本地包被改写、或服务端原地更新了 bundle，都必须重启才能跑上新内容。
     *
     * <p>远端拿不到校验值时（断网、404）退回「这份缓存确实属于该地址」——此时
     * {@link #ensureRemote} 也只会启用已校验过的缓存，两处口径一致，不会因为一次
     * 网络抖动就把正在正常服务的 Node 杀掉重启。
     */
    static boolean servesCurrentSource(String url, String servingKey) {
        if (TextUtils.isEmpty(servingKey)) return false;
        if (!isRemote(url)) {
            String key = sourceKey(url);
            return !TextUtils.isEmpty(key) && key.equals(servingKey);
        }
        String bundleMd5 = remoteMd5(url);
        String configMd5 = remoteMd5(configUrl(url));
        String key = remoteSourceKey(url, bundleMd5, configMd5);
        if (!TextUtils.isEmpty(key)) return key.equals(servingKey);
        return installedIsRemoteOf(servingKey, url);
    }

    private static File root() {
        try {
            return Path.root();
        } catch (Throwable ignored) {
            return null;
        }
    }

    static File localDir(String url, File root) {
        File target = target(url, root);
        if (target == null) return null;
        if (target.isDirectory()) return new File(target, MARKER).isFile() ? target : null;
        if (!MEMBERS.contains(lower(target.getName()))) return null;
        File parent = target.getParentFile();
        return parent != null && new File(parent, MARKER).isFile() ? parent : null;
    }

    static File localZip(String url, File root) {
        File target = target(url, root);
        if (target == null || !target.isFile() || !lower(target.getName()).endsWith(".zip")) return null;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(target)) {
            java.util.zip.ZipEntry marker = zip.getEntry(MARKER);
            return marker != null && !marker.isDirectory() ? target : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    static boolean isRemote(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String value = lower(url.trim());
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static File target(String url, File root) {
        if (TextUtils.isEmpty(url) || isRemote(url)) return null;
        String path = url.trim();
        String value = lower(path);
        if (value.startsWith("file://")) path = path.substring(7);
        else if (value.startsWith("file:/")) path = path.substring(6);
        return resolve(path, root);
    }

    private static File resolve(String path, File root) {
        if (TextUtils.isEmpty(path)) return null;
        if (root != null) {
            File relative = new File(root, path);
            if (relative.exists()) return relative;
        }
        File absolute = new File(path);
        if (absolute.exists()) return absolute;
        File rooted = new File("/" + path);
        return rooted.exists() ? rooted : null;
    }

    public static String bundleUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String trimmed = url.trim();
        return lower(trimmed).endsWith(SUFFIX) ? trimmed.substring(0, trimmed.length() - SUFFIX.length()) : trimmed;
    }

    public static String md5Url(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String trimmed = url.trim();
        return lower(trimmed).endsWith(SUFFIX) ? trimmed : trimmed + SUFFIX;
    }

    public static File dir(Context context) {
        File dir = new File(context.getFilesDir(), "node/bundle");
        dir.mkdirs();
        return dir;
    }

    public static File file(Context context) {
        return file(dir(context));
    }

    public static File config(Context context) {
        return config(dir(context));
    }

    static String installedSourceKey(Context context) {
        return read(sourceStamp(dir(context)));
    }

    // 以下都按运行目录取文件，而不是按 Context——单测无法构造 Context（抽象方法太多），
    // 但只要能传目录，ensure/install/isCached/sweep 全都可以做真实行为测试。

    private static File file(File dir) {
        return new File(dir, "index.js");
    }

    private static File config(File dir) {
        return new File(dir, "index.config.js");
    }

    private static File stamp(File dir) {
        return new File(dir, "index.js.md5");
    }

    private static File configStamp(File dir) {
        return new File(dir, "index.config.js.md5");
    }

    private static File sourceStamp(File dir) {
        return new File(dir, SOURCE_STAMP);
    }

    private static String configUrl(String url) {
        String bundle = bundleUrl(url);
        int slash = bundle.lastIndexOf('/');
        return slash < 0 ? bundle : bundle.substring(0, slash + 1) + "index.config.js";
    }

    public static synchronized String ensure(Context context, String url) {
        return ensure(dir(context), url);
    }

    static synchronized String ensure(File dir, String url) {
        return ensure(dir, url, root());
    }

    /** {@code root} 与 {@link #sourceKey(String, File)} 必须一致，否则指纹和安装会指向不同文件。 */
    static synchronized String ensure(File dir, String url, File root) {
        dir.mkdirs();
        sweep(dir);
        File source = localDir(url, root);
        if (source != null) return ensureLocal(dir, source);
        File zip = localZip(url, root);
        if (zip != null) return ensureZip(dir, zip);
        if (!isRemote(url)) return "猫源地址无法访问，本地包可能已被移动：" + url;
        return ensureRemote(dir, url);
    }

    /**
     * 清掉上次被杀进程留下的 staging 目录和 backup 临时文件。
     *
     * <p>换源时主进程 {@code stopService} 会触发 {@code NodeService.onDestroy} 里的
     * {@code killProcess}，正在 install 的 finally 不会执行，残留最多可达几十 MB
     * 且落在内部存储里，用户既看不到也删不掉。
     *
     * <p>backup 残留一律丢弃、不尝试当恢复点：那份内容是否完整无从判断，而丢掉最多
     * 多下一次，认错了却会把损坏的 bundle 当有效缓存跑起来。
     */
    static void sweep(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            // 该目录里没有需要保留的 .tmp：backup、writeAtomic 的临时文件都是过程产物。
            if (name.startsWith(".stage-")) cleanup(file);
            else if (name.endsWith(".tmp")) deleteQuietly(file);
        }
    }

    private static String ensureLocal(File dir, File source) {
        File bundle = new File(source, "index.js");
        File config = new File(source, "index.config.js");
        if (!bundle.isFile() || bundle.length() == 0) return "本地包缺少 index.js，请选择整个包（zip 或解压后的文件夹）";
        if (!config.isFile() || config.length() == 0) return "本地包缺少 index.config.js";
        if (same(bundle, file(dir)) || same(config, config(dir))) return "本地包不能指向 Node 运行目录";

        File staging = null;
        try {
            staging = stagingDir(dir);
            PreparedFile preparedBundle = prepareFile(bundle, new File(staging, "index.js"));
            PreparedFile preparedConfig = prepareFile(config, new File(staging, "index.config.js"));
            // 目录形态只在标记与内容不一致时告警，不阻断：解压出来的目录是用户可写的，
            // 改 index.config.js 换站点、魔改 index.js 都是本地包的正常用法，marker 不会跟着更新。
            // 来源身份用的是实际内容指纹（见下面 localSourceKey），所以缓存判定仍然自洽。
            warnMarkerMismatch(source, MARKER, preparedBundle.md5());
            warnMarkerMismatch(source, "index.config.js.md5", preparedConfig.md5());
            String key = localSourceKey(source, preparedBundle.md5(), preparedConfig.md5());
            return install(dir, preparedBundle, preparedConfig, key);
        } catch (Exception e) {
            SpiderDebug.log("node", e);
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        } finally {
            cleanup(staging);
        }
    }

    /**
     * 发布标记与实际内容不符时只记日志。
     *
     * <p>zip 形态保持硬校验（压缩包内容用户改不了，不符就是包损坏），但解压后的目录里
     * 改配置是主要用法，硬校验会把一批当前可用的包判死，且用户没有自救路径。
     */
    private static void warnMarkerMismatch(File source, String name, String actual) {
        String expected = readMarker(new File(source, name));
        if (!isMd5(expected) || expected.equalsIgnoreCase(actual)) return;
        SpiderDebug.log("node", "local bundle %s says %s but content is %s, using actual content", name, expected, actual);
    }

    private static String ensureZip(File dir, File zip) {
        File staging = null;
        try (java.util.zip.ZipFile file = new java.util.zip.ZipFile(zip)) {
            String bundleExpected = readEntry(file, MARKER);
            if (!isMd5(bundleExpected)) throw new IOException("本地包 index.js.md5 无效");
            String configExpected = readEntry(file, "index.config.js.md5");
            if (!TextUtils.isEmpty(configExpected) && !isMd5(configExpected)) throw new IOException("本地包 index.config.js.md5 无效");
            staging = stagingDir(dir);
            PreparedFile bundle = prepareZipEntry(file, "index.js", new File(staging, "index.js"), bundleExpected);
            PreparedFile config = prepareZipEntry(file, "index.config.js", new File(staging, "index.config.js"), configExpected);
            return install(dir, bundle, config, localSourceKey(zip, bundle.md5(), config.md5()));
        } catch (Exception e) {
            SpiderDebug.log("node", e);
            return "本地包解压失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            cleanup(staging);
        }
    }

    private static String ensureRemote(File dir, String url) {
        // 一次取齐校验值：来源键、缓存判定、下载校验全都用这两个值，避免重复请求。
        String expectedBundle = remoteMd5(url);
        String expectedConfig = remoteMd5(configUrl(url));
        String key = remoteSourceKey(url, expectedBundle, expectedConfig);
        // 两个校验值缺任何一个都算不出完整身份，此时只能靠已装好的缓存服务，不能下载未校验内容。
        if (TextUtils.isEmpty(key)) return ensureRemoteOffline(dir, url);

        boolean[] cached = remoteCacheState(dir, url, expectedBundle, expectedConfig);
        boolean bundleCached = cached[0];
        boolean configCached = cached[1];
        if (bundleCached && configCached) {
            refreshSourceKey(dir, key);
            return null;
        }

        File staging = null;
        try {
            staging = stagingDir(dir);
            PreparedFile bundle = bundleCached
                    ? prepareFile(file(dir), new File(staging, "index.js"))
                    : download(bundleUrl(url), new File(staging, "index.js"), expectedBundle);
            PreparedFile config = configCached
                    ? prepareFile(config(dir), new File(staging, "index.config.js"))
                    : download(configUrl(url), new File(staging, "index.config.js"), expectedConfig);
            return install(dir, bundle, config, key);
        } catch (Exception e) {
            SpiderDebug.log("node", e);
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        } finally {
            cleanup(staging);
        }
    }

    /**
     * 逐个文件判定远端缓存是否可用，返回 {@code [bundle, config]}。
     *
     * <p>归属和内容要分开判：**不能**把本次算出的复合来源键（含两个文件的指纹）当成单个
     * 文件的缓存键。那样「服务端只改了 index.config.js」会连 index.js 一起判失效，
     * 每次配置更新都重下整包。归属用落盘的键比（旧格式也认），内容各自比自己的校验值。
     *
     * @return 长度 2 的数组，下标 0 是 index.js、1 是 index.config.js
     */
    static boolean[] remoteCacheState(File dir, String url, String expectedBundle, String expectedConfig) {
        String installed = read(sourceStamp(dir));
        if (!installedIsRemoteOf(installed, url)) return new boolean[]{false, false};
        return new boolean[]{
                isCached(file(dir), stamp(dir), sourceStamp(dir), installed, expectedBundle),
                isCached(config(dir), configStamp(dir), sourceStamp(dir), installed, expectedConfig)};
    }

    /**
     * 两个文件都命中缓存、但落盘的来源键不是当前身份时，就地补齐。
     *
     * <p>典型来源是旧版本落盘的 {@code remote:<url>}（没有内容指纹）。不补的话
     * {@link #servesCurrentSource} 每次都判定「身份不符」，于是每次加载配置都白白重启
     * 一遍 {@code :node} 子进程。
     *
     * <p>调用方必须已确认两个文件的实际内容都等于服务端公布的校验值。这里再按两个
     * stamp 复核一遍：只读 32 字节、不重算哈希，代价可忽略，但能挡住「将来有人从别处
     * 调它」——那会静默写入一个与内容不符的身份，让下次复用判定认错。
     *
     * @return 是否发生了改写
     */
    static boolean refreshSourceKey(File dir, String key) {
        if (TextUtils.isEmpty(key) || key.equals(read(sourceStamp(dir)))) return false;
        String[] digests = installedDigests(key);
        if (!digests[0].equalsIgnoreCase(read(stamp(dir)))) return false;
        if (!digests[1].equalsIgnoreCase(read(configStamp(dir)))) return false;
        try {
            writeAtomic(sourceStamp(dir), key);
            // 只记摘要：来源键里含完整 URL，而猫源地址支持 user:pass@host 形式的凭据。
            SpiderDebug.log("node", "refreshed source key to digests %s/%s", digests[0], digests[1]);
            return true;
        } catch (Exception e) {
            // 没写上只影响下次的复用判定（会多重启一次），不影响本次服务。
            SpiderDebug.log("node", e);
            return false;
        }
    }

    /**
     * 校验值不全（断网、404、返回 HTML）时的降级：继续用已经装好、且确实属于这个地址的
     * 缓存，绝不下载未校验的内容。没有可用缓存才报错。
     *
     * <p>交叉校验的期望值**只能**取 {@code source.key} 里安装时已校验过的那两个 md5。
     * 用服务端这次拿到的新值去比旧缓存必然不等——那会把「暂时无法更新」误判成
     * 「缓存不可用」，让一次 config 校验值抖动就整个源起不来。
     */
    private static String ensureRemoteOffline(File dir, String url) {
        String installed = read(sourceStamp(dir));
        if (!installedIsRemoteOf(installed, url)) return "猫源校验值不可用，且没有该地址的本地缓存：" + url;
        String[] digests = installedDigests(installed);
        boolean bundleCached = isCached(file(dir), stamp(dir), sourceStamp(dir), installed, digests[0]);
        boolean configCached = isCached(config(dir), configStamp(dir), sourceStamp(dir), installed, digests[1]);
        if (bundleCached && configCached) {
            SpiderDebug.log("node", "remote md5 unavailable, keeping verified cache for %s", url);
            return null;
        }
        return "猫源校验值不可用，本地缓存也不完整，无法安全启动：" + url;
    }

    private static String remoteMd5(String url) {
        // 校验值只有 32 字节，但走默认 30s 超时会把复用判定拖成半分钟的黑屏。
        try (Response response = OkHttp.newCall(OkHttp.client(METADATA_TIMEOUT_MS), md5Url(url), "node-bundle").execute()) {
            if (!response.isSuccessful() || response.body() == null) return "";
            if (response.body().contentLength() > MAX_METADATA_BYTES) return "";
            try (InputStream in = response.body().byteStream()) {
                String value = readLimited(in, MAX_METADATA_BYTES).trim();
                return isMd5(value) ? value : "";
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private static PreparedFile prepareFile(File source, File target) throws IOException {
        try (InputStream in = new FileInputStream(source)) {
            return new PreparedFile(target, copyAndDigest(in, target));
        }
    }

    private static boolean same(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    private static PreparedFile prepareZipEntry(java.util.zip.ZipFile zip, String name, File target, String expected) throws IOException {
        java.util.zip.ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.isDirectory()) throw new IOException("本地包缺少 " + name);
        if (entry.getSize() > MAX_ENTRY_BYTES) throw new IOException("本地包 " + name + " 超过大小限制");
        try (InputStream in = zip.getInputStream(entry)) {
            String actual = copyAndDigest(in, target);
            if (isMd5(expected) && !expected.equalsIgnoreCase(actual)) throw new IOException("本地包 " + name + " 校验失败");
            return new PreparedFile(target, actual);
        }
    }

    private static PreparedFile download(String url, File target, String expected) throws IOException {
        if (!isMd5(expected)) throw new IOException("bundle 校验值不可用");
        try (Response response = OkHttp.newCall(url, "node-bundle").execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("bundle 下载失败 HTTP " + response.code());
            try (InputStream in = response.body().byteStream()) {
                String actual = copyAndDigest(in, target);
                if (isMd5(expected) && !expected.equalsIgnoreCase(actual)) throw new IOException("bundle 校验失败");
                return new PreparedFile(target, actual);
            }
        }
    }

    /** 正式文件只在准备完成后替换，失败时恢复旧缓存。 */
    static String install(File dir, PreparedFile bundle, PreparedFile config, String sourceKey) {
        File bundleTarget = file(dir);
        File configTarget = config(dir);
        File bundleBackup = null;
        File configBackup = null;
        boolean bundlePublished = false;
        boolean configPublished = false;
        File stamp = stamp(dir);
        File configStamp = configStamp(dir);
        File sourceStamp = sourceStamp(dir);
        boolean oldStamp = stamp.isFile();
        boolean oldConfigStamp = configStamp.isFile();
        boolean oldSourceStamp = sourceStamp.isFile();
        String oldStampText = read(stamp);
        String oldConfigStampText = read(configStamp);
        String oldSourceStampText = read(sourceStamp);
        try {
            if (bundleTarget.isFile()) bundleBackup = backup(bundleTarget);
            if (configTarget.isFile()) configBackup = backup(configTarget);
            move(bundle.file(), bundleTarget);
            bundlePublished = true;
            move(config.file(), configTarget);
            configPublished = true;
            writeAtomic(stamp, bundle.md5());
            writeAtomic(configStamp, config.md5());
            writeAtomic(sourceStamp, sourceKey);
            deleteQuietly(bundleBackup);
            deleteQuietly(configBackup);
            return null;
        } catch (Exception e) {
            boolean restored = true;
            if (bundleBackup != null) restored = restore(bundleBackup, bundleTarget);
            else if (bundlePublished) deleteQuietly(bundleTarget);
            if (configBackup != null) restored &= restore(configBackup, configTarget);
            else if (configPublished) deleteQuietly(configTarget);
            restoreText(stamp, oldStamp, oldStampText);
            restoreText(configStamp, oldConfigStamp, oldConfigStampText);
            restoreText(sourceStamp, oldSourceStamp, oldSourceStampText);
            // 回滚不完整时磁盘可能是「新 index.js + 旧 config」的混合态，而 stamp 已被恢复成旧值。
            // 删掉来源键把缓存标记为不可信，强制下次重新准备，而不是让 isCached 把混合态当有效缓存。
            if (!restored) {
                SpiderDebug.log("node", "rollback incomplete, invalidating cache source key");
                deleteQuietly(sourceStamp);
            }
            SpiderDebug.log("node", e);
            return "本地包写入失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    static boolean isCached(File target, File stamp, File sourceStamp, String sourceKey, String expected) {
        if (TextUtils.isEmpty(sourceKey) || !target.isFile() || target.length() == 0 || target.length() > MAX_ENTRY_BYTES) return false;
        if (!sourceKey.equals(read(sourceStamp))) return false;
        String local = read(stamp);
        if (!isMd5(local)) return false;
        String actual = contentSignature(target);
        return isMd5(actual) && actual.equalsIgnoreCase(local) && (!isMd5(expected) || expected.equalsIgnoreCase(actual));
    }

    private static String readEntry(java.util.zip.ZipFile zip, String name) throws IOException {
        java.util.zip.ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.isDirectory() || entry.getSize() > MAX_METADATA_BYTES) return "";
        try (InputStream in = zip.getInputStream(entry)) {
            return marker(readLimited(in, MAX_METADATA_BYTES));
        }
    }

    /** 目录形态的发布标记，限长读取，缺失或过大都当作没有。 */
    private static String readMarker(File file) {
        if (!file.isFile() || file.length() > MAX_METADATA_BYTES) return "";
        try (InputStream in = new FileInputStream(file)) {
            return marker(readLimited(in, MAX_METADATA_BYTES));
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 取校验值本体。{@code md5sum > index.js.md5} 生成的是「{@code <hash>  index.js}」，
     * 只 trim 会让整行过不了 {@link #isMd5}——那会把本来能用的包判成「校验值无效」。
     */
    private static String marker(String text) {
        String value = text == null ? "" : text.trim();
        int space = value.indexOf(' ');
        if (space > 0) value = value.substring(0, space);
        int tab = value.indexOf('\t');
        if (tab > 0) value = value.substring(0, tab);
        return value;
    }

    private static String zipSourceKey(File zip) {
        try (java.util.zip.ZipFile file = new java.util.zip.ZipFile(zip)) {
            if (!isMd5(readEntry(file, MARKER))) return "";
            String bundle = entryDigest(file, "index.js");
            String config = entryDigest(file, "index.config.js");
            String configMarker = readEntry(file, "index.config.js.md5");
            if (!isMd5(bundle) || !isMd5(config) || (!TextUtils.isEmpty(configMarker) && !isMd5(configMarker))) return "";
            return localSourceKey(zip, bundle, config);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String entryDigest(java.util.zip.ZipFile zip, String name) throws IOException {
        java.util.zip.ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.isDirectory() || entry.getSize() > MAX_ENTRY_BYTES) return "";
        try (InputStream in = zip.getInputStream(entry)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[65536];
            long total = 0;
            int length;
            while ((length = in.read(buffer)) != -1) {
                total += length;
                if (total > MAX_ENTRY_BYTES) return "";
                digest.update(buffer, 0, length);
            }
            return total == 0 ? "" : hex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("MD5 不可用", e);
        }
    }

    private static String copyAndDigest(InputStream in, File target) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            throw new IOException("MD5 不可用", e);
        }
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        long total = 0;
        try (OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[65536];
            int length;
            while ((length = in.read(buffer)) != -1) {
                total += length;
                if (total > MAX_ENTRY_BYTES) throw new IOException("文件超过大小限制");
                digest.update(buffer, 0, length);
                out.write(buffer, 0, length);
            }
        }
        if (total == 0) throw new IOException("文件为空");
        return hex(digest.digest());
    }

    private static File stagingDir(File dir) throws IOException {
        File staging = new File(dir, ".stage-" + UUID.randomUUID());
        if (!staging.mkdirs()) throw new IOException("无法创建本地包临时目录");
        return staging;
    }

    /**
     * 用移动而不是复制：目标文件反正要被替换掉，同目录的 rename 是常数时间，
     * 而复制要多读写一遍最多 32 MiB。复制还会顺带算一个用不上的 MD5，且对 0 字节
     * 目标文件抛「文件为空」——那会让残缺缓存永久无法自愈。
     */
    private static File backup(File target) throws IOException {
        File backup = File.createTempFile(target.getName() + ".backup-", ".tmp", target.getParentFile());
        try {
            move(target, backup);
            return backup;
        } catch (Exception e) {
            deleteQuietly(backup);
            throw e;
        }
    }

    /** @return 是否成功恢复；失败意味着磁盘停在混合态，调用方要把缓存标记为不可信。 */
    private static boolean restore(File backup, File target) {
        if (backup == null || !backup.exists()) return true;
        try {
            move(backup, target);
            return true;
        } catch (Exception e) {
            SpiderDebug.log("node", e);
            return false;
        }
    }

    private static void restoreText(File target, boolean existed, String text) {
        try {
            if (existed) writeAtomic(target, text);
            else deleteQuietly(target);
        } catch (Exception e) {
            SpiderDebug.log("node", e);
        }
    }

    private static void writeAtomic(File target, String text) throws IOException {
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        File temporary = File.createTempFile(target.getName() + ".", ".tmp", parent);
        try {
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            }
            move(temporary, target);
        } finally {
            deleteQuietly(temporary);
        }
    }

    private static void move(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String localSourceKey(File source, String bundleDigest, String configDigest) {
        return "local:" + canonical(source) + ":" + bundleDigest + ":" + configDigest;
    }

    private static String contentSignature(File file) {
        return file.isFile() ? digestFile(file) : "";
    }

    private static String digestFile(File file) {
        if (file.length() <= 0 || file.length() > MAX_ENTRY_BYTES) return "";
        try (InputStream in = new FileInputStream(file)) {
            return digestStream(in, MAX_ENTRY_BYTES);
        } catch (IOException e) {
            return "";
        }
    }

    private static String digestStream(InputStream in, long maxBytes) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            throw new IOException("MD5 不可用", e);
        }
        byte[] buffer = new byte[65536];
        long total = 0;
        int length;
        while ((length = in.read(buffer)) != -1) {
            total += length;
            if (total > maxBytes) return "";
            digest.update(buffer, 0, length);
        }
        return total == 0 ? "" : hex(digest.digest());
    }

    private static String canonical(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        return value.toString();
    }

    private static String read(File file) {
        if (file == null || !file.isFile()) return "";
        try (InputStream in = new FileInputStream(file)) {
            return readLimited(in, MAX_SOURCE_KEY_BYTES).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static String readLimited(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 256));
        byte[] buffer = new byte[256];
        int total = 0;
        int length;
        while ((length = in.read(buffer)) != -1) {
            total += length;
            if (total > maxBytes) throw new IOException("文本超过大小限制");
            output.write(buffer, 0, length);
        }
        return total == 0 ? "" : output.toString(StandardCharsets.UTF_8.name());
    }

    private static void cleanup(File directory) {
        if (directory == null) return;
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) deleteQuietly(file);
        deleteQuietly(directory);
    }

    private static void deleteQuietly(File file) {
        // 不用 deleteOnExit 兜底：Android 上进程被 kill，shutdown hook 不跑，
        // 只会把路径永久留在 DeleteOnExitHook 里。残留交给 sweep() 下次启动清。
        if (file != null && file.exists() && !file.delete()) SpiderDebug.log("node", "failed to delete %s", file.getAbsolutePath());
    }

    /** 准备好、已算过摘要、等待发布到运行目录的文件。package-private 以便直测 install 的回滚。 */
    record PreparedFile(File file, String md5) {
    }
}
