package com.fongmi.android.tv.server.process;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import okhttp3.Protocol;

public class ApkUrlPushTest {

    @Test
    public void boundedCopyWritesExpectedContent() throws Exception {
        byte[] value = new byte[]{1, 2, 3, 4};
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long written = ApkUrlPush.copy(new ByteArrayInputStream(value), output, value.length, 16, 8, 4);
        assertEquals(value.length, written);
        assertArrayEquals(value, output.toByteArray());
    }

    @Test
    public void boundedCopyRejectsTruncationOverflowAndStorageShortage() {
        assertThrows(IOException.class, () -> ApkUrlPush.copy(new ByteArrayInputStream(new byte[]{1, 2}), new ByteArrayOutputStream(), 3, 16, 8, 4));
        assertThrows(IOException.class, () -> ApkUrlPush.copy(new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5}), new ByteArrayOutputStream(), -1, 16, 4, 4));
        assertThrows(IOException.class, () -> ApkUrlPush.copy(new ByteArrayInputStream(new byte[]{1, 2, 3}), new ByteArrayOutputStream(), -1, 6, 8, 4));
    }

    @Test
    public void boundedCopyRejectsEmptyContent() {
        assertThrows(IOException.class, () -> ApkUrlPush.copy(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), 0, 16, 8, 4));
    }

    @Test
    public void apkDownloadClientUsesHttp11Only() {
        assertEquals(List.of(Protocol.HTTP_1_1), ApkUrlPush.createClient().protocols());
    }

    @Test
    public void boundedCopyReportsKnownLengthProgress() throws Exception {
        byte[] value = new byte[]{1, 2, 3, 4};
        List<long[]> events = new ArrayList<>();
        ApkUrlPush.copy(new ByteArrayInputStream(value), new ByteArrayOutputStream(), value.length, 16, 8, 4, () -> false,
                (progress, bytes, total, speed, elapsed) -> events.add(new long[]{progress, bytes, total, speed, elapsed}));

        assertFalse(events.isEmpty());
        long[] last = events.get(events.size() - 1);
        assertEquals(100, last[0]);
        assertEquals(value.length, last[1]);
        assertEquals(value.length, last[2]);
    }

    @Test
    public void boundedCopyStopsWhenCanceled() {
        AtomicBoolean canceled = new AtomicBoolean();
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}) {
            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                return super.read(buffer, offset, Math.min(2, length));
            }
        };

        assertThrows(InterruptedIOException.class, () -> ApkUrlPush.copy(input, new ByteArrayOutputStream(), 8, 16, 12, 4, canceled::get,
                (progress, bytes, total, speed, elapsed) -> {
                    if (bytes >= 2) canceled.set(true);
                }));
        assertTrue(canceled.get());
    }
}
