package com.fongmi.android.tv.subtitle;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

final class RealtimeSubtitleModelVerifier {

    private static final int MARKER_MAGIC = 0x52535631;
    private static final String MARKER_SUFFIX = ".verified";

    private RealtimeSubtitleModelVerifier() {
    }

    static boolean isVerified(File file, long expectedSize, String expectedSha256) {
        String digest = normalizeDigest(expectedSha256);
        if (!matches(file, expectedSize) || digest == null) {
            deleteMarker(file);
            return false;
        }
        try (DataInputStream input = new DataInputStream(new FileInputStream(marker(file)))) {
            return input.readInt() == MARKER_MAGIC
                    && input.readLong() == expectedSize
                    && input.readLong() == file.lastModified()
                    && digest.equals(input.readUTF());
        } catch (IOException e) {
            return false;
        }
    }

    static boolean verify(File file, long expectedSize, String expectedSha256) throws IOException {
        String expected = normalizeDigest(expectedSha256);
        if (!matches(file, expectedSize) || expected == null) {
            deleteMarker(file);
            return false;
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file), 64 * 1024)) {
            byte[] buffer = new byte[64 * 1024];
            int length;
            while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
        }
        if (!expected.equals(toHex(digest.digest()))) {
            deleteMarker(file);
            return false;
        }
        markVerified(file, expectedSize, expected);
        return true;
    }

    static void markVerified(File file, long expectedSize, String expectedSha256) throws IOException {
        String digest = normalizeDigest(expectedSha256);
        if (!matches(file, expectedSize) || digest == null) throw new IOException("Invalid verified model file");
        File marker = marker(file);
        File temporary = new File(marker.getPath() + ".tmp");
        try (FileOutputStream stream = new FileOutputStream(temporary);
             DataOutputStream output = new DataOutputStream(stream)) {
            output.writeInt(MARKER_MAGIC);
            output.writeLong(expectedSize);
            output.writeLong(file.lastModified());
            output.writeUTF(digest);
            output.flush();
            stream.getFD().sync();
        }
        if (marker.exists() && !marker.delete() || !temporary.renameTo(marker)) {
            temporary.delete();
            throw new IOException("Cannot write model verification marker");
        }
    }

    static void deleteMarker(File file) {
        File marker = marker(file);
        if (marker.exists()) marker.delete();
        File temporary = new File(marker.getPath() + ".tmp");
        if (temporary.exists()) temporary.delete();
    }

    private static boolean matches(File file, long expectedSize) {
        return file != null && file.isFile() && expectedSize >= 0L && file.length() == expectedSize;
    }

    private static File marker(File file) {
        return new File(file.getPath() + MARKER_SUFFIX);
    }

    private static String normalizeDigest(String digest) {
        if (digest == null || digest.length() != 64) return null;
        String normalized = digest.toLowerCase(Locale.US);
        for (int i = 0; i < normalized.length(); i++) {
            char value = normalized.charAt(i);
            if (value < '0' || value > '9' && value < 'a' || value > 'f') return null;
        }
        return normalized;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) builder.append(String.format(Locale.US, "%02x", value & 0xff));
        return builder.toString();
    }
}
