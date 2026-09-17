package com.fongmi.android.tv.subtitle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class RealtimeSubtitleModelVerifierTest {

    @Test
    public void verifiedMarkerRejectsSameSizeMutation() throws Exception {
        File file = Files.createTempFile("realtime-subtitle-model", ".bin").toFile();
        try {
            Files.write(file.toPath(), "abc".getBytes(StandardCharsets.UTF_8));
            assertTrue(RealtimeSubtitleModelVerifier.verify(file, 3L, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
            assertTrue(RealtimeSubtitleModelVerifier.isVerified(file, 3L, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));

            Files.write(file.toPath(), "abd".getBytes(StandardCharsets.UTF_8));

            assertFalse(RealtimeSubtitleModelVerifier.isVerified(file, 3L, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
            assertFalse(RealtimeSubtitleModelVerifier.verify(file, 3L, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
        } finally {
            RealtimeSubtitleModelVerifier.deleteMarker(file);
            file.delete();
        }
    }
}
