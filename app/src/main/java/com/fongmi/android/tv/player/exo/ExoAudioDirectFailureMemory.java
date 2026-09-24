package com.fongmi.android.tv.player.exo;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.Format;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Short-lived, process-only evidence that one exact direct path recovered with PCM. */
final class ExoAudioDirectFailureMemory {

    static final int MAX_ENTRIES = 32;
    static final long TTL_MS = 10 * 60_000;
    private static final ExoAudioDirectFailureMemory PROCESS = new ExoAudioDirectFailureMemory();

    // No player, Context, AudioTrack, URL, header, or codec initialization bytes are retained.
    record Route(int id, int type, int encodings, int sampleRates, int channelMasks) {}
    record Key(String media, String format, AudioAttributes attributes, Route route) {}

    private final LinkedHashMap<Key, Long> confirmed = new LinkedHashMap<>();
    private long generation;

    static ExoAudioDirectFailureMemory process() {
        return PROCESS;
    }

    synchronized long generation() {
        return generation;
    }

    synchronized boolean hasMedia(String media, long nowMs) {
        expire(nowMs);
        if (media == null) return false;
        for (Key key : confirmed.keySet()) {
            if (key.media.equals(media)) return true;
        }
        return false;
    }

    synchronized boolean contains(Key key, long nowMs) {
        expire(nowMs);
        return key != null && confirmed.containsKey(key);
    }

    synchronized boolean confirm(Key key, long expectedGeneration, long nowMs) {
        if (key == null || key.media == null || key.route == null
                || expectedGeneration != generation) return false;
        expire(nowMs);
        confirmed.remove(key);
        confirmed.put(key, nowMs);
        while (confirmed.size() > MAX_ENTRIES) {
            confirmed.remove(confirmed.keySet().iterator().next());
        }
        return true;
    }

    synchronized void invalidate() {
        confirmed.clear();
        generation++;
    }

    private void expire(long nowMs) {
        Iterator<Map.Entry<Key, Long>> entries = confirmed.entrySet().iterator();
        while (entries.hasNext()) {
            long confirmedAtMs = entries.next().getValue();
            if (nowMs < confirmedAtMs || nowMs - confirmedAtMs >= TTL_MS) entries.remove();
        }
    }

    static String mediaId(String url) {
        if (url == null || url.isEmpty()) return null;
        MessageDigest digest = newDigest();
        update(digest, url);
        return hex(digest.digest());
    }

    static String formatId(Format format) {
        if (format == null) return null;
        MessageDigest digest = newDigest();
        update(digest, format.id);
        update(digest, format.sampleMimeType);
        update(digest, format.codecs);
        update(digest, format.sampleRate);
        update(digest, format.channelCount);
        update(digest, format.initializationData.size());
        for (byte[] data : format.initializationData) {
            update(digest, data.length);
            digest.update(data);
        }
        return hex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void update(MessageDigest digest, String value) {
        if (value == null) {
            update(digest, -1);
        } else {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            update(digest, bytes.length);
            digest.update(bytes);
        }
    }

    private static void update(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static String hex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            chars[i * 2] = digits[(bytes[i] & 0xff) >>> 4];
            chars[i * 2 + 1] = digits[bytes[i] & 15];
        }
        return new String(chars);
    }
}
