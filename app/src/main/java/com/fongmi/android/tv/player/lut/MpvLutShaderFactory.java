package com.fongmi.android.tv.player.lut;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.SpiderDebug;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public class MpvLutShaderFactory {

    public static final int PREVIEW_SLIDE_MS = 420;

    private static final int MAX_LUT_SIZE = 65;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public static MpvLutShader create(LutPreset preset, int strength, boolean preview) throws IOException {
        if (preset == null) throw new IOException("Missing LUT preset");
        int[][][] cube = loadCube(preset);
        int size = cube.length;
        int safeStrength = Math.min(Math.max(strength, 0), 100);
        File file = outputFile(preset, safeStrength, preview);
        long start = System.currentTimeMillis();
        writeShader(file, preset, cube, safeStrength, preview);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-mpv", "shader preset=%s format=%s strength=%d preview=%s size=%d file=%s cost=%dms", preset.getId(), preset.getFormat(), safeStrength, preview, size, file.getAbsolutePath(), System.currentTimeMillis() - start);
        return new MpvLutShader(file, preset.getName(), safeStrength, size, preview);
    }

    private static int[][][] loadCube(LutPreset preset) throws IOException {
        return switch (preset.getFormat()) {
            case CUBE -> {
                try (InputStream stream = open(preset)) {
                    yield LutCubeParser.parse(stream);
                }
            }
            case BITMAP -> loadBitmapCube(preset);
        };
    }

    private static int[][][] loadBitmapCube(LutPreset preset) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = open(preset)) {
            BitmapFactory.decodeStream(stream, null, bounds);
        }
        if (!isBitmapLut(bounds.outWidth, bounds.outHeight)) throw new IOException("Bitmap LUT must be N x N^2, N <= " + MAX_LUT_SIZE);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream stream = open(preset)) {
            bitmap = BitmapFactory.decodeStream(stream, null, options);
        }
        if (bitmap == null) throw new IOException("Unable to decode bitmap LUT");
        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            if (copy == null) throw new IOException("Unable to convert bitmap LUT");
            bitmap = copy;
        }
        if (!isBitmapLut(bitmap.getWidth(), bitmap.getHeight())) throw new IOException("Invalid bitmap LUT size");
        int size = bitmap.getWidth();
        int[][][] cube = new int[size][size][size];
        for (int y = 0; y < bitmap.getHeight(); y++) {
            int r = y / size;
            int g = y % size;
            for (int b = 0; b < size; b++) cube[r][g][b] = bitmap.getPixel(b, y);
        }
        return cube;
    }

    private static boolean isBitmapLut(int width, int height) {
        return width > 1 && width <= MAX_LUT_SIZE && height == width * width;
    }

    private static InputStream open(LutPreset preset) throws IOException {
        if (preset.isAsset()) return App.get().getAssets().open(preset.getPath());
        if (preset.getPath().startsWith("content://")) {
            InputStream stream = App.get().getContentResolver().openInputStream(Uri.parse(preset.getPath()));
            if (stream == null) throw new IOException("Unable to open LUT uri");
            return stream;
        }
        return new FileInputStream(preset.getPath());
    }

    private static File outputFile(LutPreset preset, int strength, boolean preview) throws IOException {
        File dir = new File(App.get().getCacheDir(), "mpv_lut_shaders");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Unable to create MPV LUT shader directory");
        String key = preset.getId() + "|" + preset.getPath() + "|" + preset.getFormat() + "|" + strength + "|" + preview + "|" + sourceStamp(preset);
        return new File(dir, "lut_" + sha1(key).substring(0, 16) + ".glsl");
    }

    private static String sourceStamp(LutPreset preset) {
        if (preset.isAsset() || TextUtils.isEmpty(preset.getPath()) || preset.getPath().startsWith("content://")) return "";
        File file = new File(preset.getPath());
        return file.length() + ":" + file.lastModified();
    }

    private static void writeShader(File file, LutPreset preset, int[][][] cube, int strength, boolean preview) throws IOException {
        int size = cube.length;
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8), 64 * 1024)) {
            writer.write("// Generated by WebHTV MPV LUT pipeline. Source: ");
            writer.write(safeComment(preset.getName()));
            writer.write('\n');
            if (preview) {
                writer.write("//!PARAM " + MpvLutShader.PREVIEW_PROGRESS_PARAM + "\n");
                writer.write("//!TYPE float\n");
                writer.write("//!MINIMUM 0.0\n");
                writer.write("//!MAXIMUM 1.0\n");
                writer.write("0.0\n\n");
            }
            writer.write("//!HOOK MAIN\n");
            writer.write("//!BIND HOOKED\n");
            writer.write("//!BIND WEBHTV_LUT\n");
            writer.write("//!DESC WebHTV LUT\n\n");
            writer.write("vec3 webhtv_lut_tetrahedral(sampler3D lut, vec3 color) {\n");
            writer.write(String.format(Locale.US, "    float lut_size = %.1f;\n", (float) size));
            writer.write("    vec3 coord = clamp(color, 0.0, 1.0) * (lut_size - 1.0);\n");
            writer.write("    vec3 base = floor(coord);\n");
            writer.write("    vec3 fracv = fract(coord);\n");
            writer.write("    float texel = 1.0 / lut_size;\n");
            writer.write("    vec3 c000 = (base + vec3(0.5)) * texel;\n");
            writer.write("    vec3 c100 = c000 + vec3(texel, 0.0, 0.0);\n");
            writer.write("    vec3 c010 = c000 + vec3(0.0, texel, 0.0);\n");
            writer.write("    vec3 c110 = c000 + vec3(texel, texel, 0.0);\n");
            writer.write("    vec3 c001 = c000 + vec3(0.0, 0.0, texel);\n");
            writer.write("    vec3 c101 = c000 + vec3(texel, 0.0, texel);\n");
            writer.write("    vec3 c011 = c000 + vec3(0.0, texel, texel);\n");
            writer.write("    vec3 c111 = c000 + vec3(texel, texel, texel);\n");
            writer.write("    vec3 v000 = texture(lut, c000).rgb;\n");
            writer.write("    vec3 v100 = texture(lut, c100).rgb;\n");
            writer.write("    vec3 v010 = texture(lut, c010).rgb;\n");
            writer.write("    vec3 v110 = texture(lut, c110).rgb;\n");
            writer.write("    vec3 v001 = texture(lut, c001).rgb;\n");
            writer.write("    vec3 v101 = texture(lut, c101).rgb;\n");
            writer.write("    vec3 v011 = texture(lut, c011).rgb;\n");
            writer.write("    vec3 v111 = texture(lut, c111).rgb;\n");
            writer.write("    if (fracv.x >= fracv.y && fracv.y >= fracv.z) return (1.0 - fracv.x) * v000 + (fracv.x - fracv.y) * v100 + (fracv.y - fracv.z) * v110 + fracv.z * v111;\n");
            writer.write("    if (fracv.x >= fracv.z && fracv.z >= fracv.y) return (1.0 - fracv.x) * v000 + (fracv.x - fracv.z) * v100 + (fracv.z - fracv.y) * v101 + fracv.y * v111;\n");
            writer.write("    if (fracv.y >= fracv.x && fracv.x >= fracv.z) return (1.0 - fracv.y) * v000 + (fracv.y - fracv.x) * v010 + (fracv.x - fracv.z) * v110 + fracv.z * v111;\n");
            writer.write("    if (fracv.y >= fracv.z && fracv.z >= fracv.x) return (1.0 - fracv.y) * v000 + (fracv.y - fracv.z) * v010 + (fracv.z - fracv.x) * v011 + fracv.x * v111;\n");
            writer.write("    if (fracv.z >= fracv.x && fracv.x >= fracv.y) return (1.0 - fracv.z) * v000 + (fracv.z - fracv.x) * v001 + (fracv.x - fracv.y) * v101 + fracv.y * v111;\n");
            writer.write("    return (1.0 - fracv.z) * v000 + (fracv.z - fracv.y) * v001 + (fracv.y - fracv.x) * v011 + fracv.x * v111;\n");
            writer.write("}\n\n");
            writer.write("vec4 hook() {\n");
            writer.write("    vec4 color = HOOKED_tex(HOOKED_pos);\n");
            writer.write("    vec3 mapped = webhtv_lut_tetrahedral(WEBHTV_LUT, color.rgb);\n");
            writer.write(String.format(Locale.US, "    vec3 graded = mix(color.rgb, mapped, %.6f);\n", strength / 100.0f));
            if (preview) {
                writer.write("    float progress = clamp(" + MpvLutShader.PREVIEW_PROGRESS_PARAM + ", 0.0, 1.0);\n");
                writer.write("    float edge = 0.5 * (1.0 - progress);\n");
                writer.write("    float line_width = max(1.2 / HOOKED_size.x, 0.0015);\n");
                writer.write("    float mask = smoothstep(edge - line_width, edge + line_width, HOOKED_pos.x);\n");
                writer.write("    float line = 1.0 - smoothstep(0.0, line_width, abs(HOOKED_pos.x - edge));\n");
                writer.write("    line *= step(0.001, edge) * step(edge, 0.999);\n");
                writer.write("    color.rgb = mix(color.rgb, graded, mask);\n");
                writer.write("    color.rgb = mix(color.rgb, vec3(1.0), line * 0.72);\n");
            } else {
                writer.write("    color.rgb = graded;\n");
            }
            writer.write("    return color;\n");
            writer.write("}\n\n");
            writer.write("//!TEXTURE WEBHTV_LUT\n");
            writer.write("//!SIZE " + size + " " + size + " " + size + "\n");
            writer.write("//!FORMAT rgba8\n");
            writer.write("//!FILTER NEAREST\n");
            writer.write("//!BORDER CLAMP\n");
            writeTextureData(writer, cube);
            writer.write('\n');
        }
    }

    private static void writeTextureData(Writer writer, int[][][] cube) throws IOException {
        int size = cube.length;
        for (int b = 0; b < size; b++) {
            for (int g = 0; g < size; g++) {
                for (int r = 0; r < size; r++) {
                    int color = cube[r][g][b];
                    writeByte(writer, Color.red(color));
                    writeByte(writer, Color.green(color));
                    writeByte(writer, Color.blue(color));
                    writeByte(writer, 255);
                }
            }
        }
    }

    private static void writeByte(Writer writer, int value) throws IOException {
        int safe = Math.min(255, Math.max(0, value));
        writer.write(HEX[(safe >> 4) & 0x0f]);
        writer.write(HEX[safe & 0x0f]);
    }

    private static String safeComment(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }

    private static String sha1(String value) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(HEX[(b >> 4) & 0x0f]);
                builder.append(HEX[b & 0x0f]);
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IOException("Unable to hash LUT shader cache key", e);
        }
    }
}
