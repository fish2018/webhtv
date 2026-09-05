package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 无法解析真实路径的 content URI 要复制到应用内部目录。这个目录不是 cache，系统「清理缓存」
 * 清不掉，所以复制策略必须自己保证不会无界增长——同时又不能删掉已被配置引用的文件。
 *
 * <p>这里测的是真实行为（真建文件、真比对内容），不是源码文本。
 */
public class FileChooserImportTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static InputStream stream(String body) {
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void importWritesContentAndReturnsPathInsideTargetDir() throws IOException {
        File dir = folder.newFolder("imports");
        String path = FileChooser.persistentImport(stream("module.exports={};"), "pkg.zip", dir);

        File file = new File(path);
        assertTrue("导入结果必须真的落盘", file.isFile());
        assertEquals("内容必须完整写入", "module.exports={};", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        assertEquals("必须落在指定的持久目录里", dir.getCanonicalFile(), file.getParentFile().getCanonicalFile());
    }

    /**
     * 反复导入同一个包是最常见的操作（改完本地包再导一次）。若每次都留新副本，
     * 内部存储会被几十 MB 的 zip 一路吃光，而用户在设置里看不到也删不掉。
     */
    @Test
    public void reimportingIdenticalContentReusesOneFile() throws IOException {
        File dir = folder.newFolder("imports");
        String first = FileChooser.persistentImport(stream("same-bytes"), "pkg.zip", dir);
        String second = FileChooser.persistentImport(stream("same-bytes"), "pkg.zip", dir);

        assertEquals("相同内容必须复用同一份文件", first, second);
        assertEquals("目录里只该有一个文件", 1, countFiles(dir));
    }

    /** 同名但内容不同的两个包必须各自独立，否则换包后仍加载旧内容。 */
    @Test
    public void sameNameWithDifferentContentDoesNotOverwrite() throws IOException {
        File dir = folder.newFolder("imports");
        String first = FileChooser.persistentImport(stream("bundle-a"), "pkg.zip", dir);
        String second = FileChooser.persistentImport(stream("bundle-b"), "pkg.zip", dir);

        assertNotEquals("同名不同内容不能相互覆盖", first, second);
        assertEquals("bundle-a", new String(Files.readAllBytes(new File(first).toPath()), StandardCharsets.UTF_8));
        assertEquals("bundle-b", new String(Files.readAllBytes(new File(second).toPath()), StandardCharsets.UTF_8));
    }

    /** 空内容说明流已失效（文档被删/权限被撤），落盘一个 0 字节文件只会让后续解析莫名失败。 */
    @Test
    public void emptyStreamIsRejectedAndLeavesNothingBehind() throws IOException {
        File dir = folder.newFolder("imports");
        assertThrows(IOException.class, () -> FileChooser.persistentImport(stream(""), "pkg.zip", dir));
        assertEquals("失败后不能留下任何残留文件", 0, countFiles(dir));
    }

    /** 中途失败不能留下 .import-*.tmp，否则每次失败都在内部存储里堆一份垃圾。 */
    @Test
    public void failureMidStreamCleansUpTempFile() throws IOException {
        File dir = folder.newFolder("imports");
        InputStream broken = new InputStream() {
            private int remaining = 16;

            @Override
            public int read() throws IOException {
                if (remaining-- <= 0) throw new IOException("stream broke");
                return 'x';
            }
        };

        assertThrows(IOException.class, () -> FileChooser.persistentImport(broken, "pkg.zip", dir));
        assertEquals("读取中断后不能留下临时文件", 0, countFiles(dir));
    }

    /** 文件名来自 content provider，可能含路径分隔符或奇怪字符，不能直接拼进路径。 */
    @Test
    public void hostileDisplayNamesStayInsideTargetDir() throws IOException {
        File dir = folder.newFolder("imports");
        for (String name : new String[]{"../../evil.zip", "a/b/c.zip", null, "  ", "包名.zip"}) {
            String path = FileChooser.persistentImport(stream("body-" + name), name, dir);
            File file = new File(path);
            assertEquals("文件名 " + name + " 不能逃出目标目录",
                    dir.getCanonicalFile(), file.getParentFile().getCanonicalFile());
            assertTrue(file.isFile());
        }
    }

    private static int countFiles(File dir) {
        File[] files = dir.listFiles();
        return files == null ? 0 : files.length;
    }
}
