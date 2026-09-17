package io.github.anilbeesetti.nextlib.media3ext.ffdecoder;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FfmpegVc1SupportTest {

    private static final String AAR_PATH = "third_party/maven/io/github/anilbeesetti/nextlib-media3ext/"
            + "1.10.0-0.12.1-fongmi-softload-av3a-ffmpeg901-r3/"
            + "nextlib-media3ext-1.10.0-0.12.1-fongmi-softload-av3a-ffmpeg901-r3.aar";

    @Test
    public void bundledFfmpeg_hasVc1DecoderForEveryAbi() throws Exception {
        File root = new File(System.getProperty("user.dir"));
        while (root != null && !new File(root, AAR_PATH).isFile()) root = root.getParentFile();
        assertNotNull("Unable to locate project root from " + System.getProperty("user.dir"), root);

        try (ZipFile aar = new ZipFile(new File(root, AAR_PATH))) {
            for (String abi : List.of("armeabi-v7a", "arm64-v8a")) {
                String path = "jni/" + abi + "/libavcodec.so";
                ZipEntry entry = aar.getEntry(path);
                assertNotNull(path + " is missing", entry);
                try (InputStream input = aar.getInputStream(entry)) {
                    assertTrue(path + " does not contain the VC-1 decoder",
                            contains(input, "libavcodec/vc1dec.c", "SMPTE VC-1"));
                }
            }
        }
    }

    private static boolean contains(InputStream input, String... values) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int read; (read = input.read(buffer)) != -1; ) output.write(buffer, 0, read);
        String content = new String(output.toByteArray(), StandardCharsets.ISO_8859_1);
        for (String value : values) if (content.contains(value)) return true;
        return false;
    }
}
