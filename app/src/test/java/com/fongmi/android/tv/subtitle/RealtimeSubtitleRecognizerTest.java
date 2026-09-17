package com.fongmi.android.tv.subtitle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RealtimeSubtitleRecognizerTest {

    @Test
    public void routesStreamingAndOfflineEngines() {
        assertTrue(RealtimeSubtitleRecognizer.isStreaming(RealtimeSubtitleModelCatalog.find("zh")));
        assertTrue(RealtimeSubtitleRecognizer.isStreaming(RealtimeSubtitleModelCatalog.find("de")));
        assertFalse(RealtimeSubtitleRecognizer.isStreaming(RealtimeSubtitleModelCatalog.find("yue")));
        assertFalse(RealtimeSubtitleRecognizer.isStreaming(RealtimeSubtitleModelCatalog.find("ja")));
    }

    @Test
    public void selectsModelTypeForLegacyAndKrokoTransducers() {
        assertEquals("zipformer", RealtimeSubtitleRecognizer.onlineModelType(RealtimeSubtitleModelCatalog.find("zh")));
        assertEquals("zipformer", RealtimeSubtitleRecognizer.onlineModelType(RealtimeSubtitleModelCatalog.find("en")));
        assertEquals("zipformer", RealtimeSubtitleRecognizer.onlineModelType(RealtimeSubtitleModelCatalog.find("zh-en")));
        assertEquals("zipformer2", RealtimeSubtitleRecognizer.onlineModelType(RealtimeSubtitleModelCatalog.find("de")));
        assertEquals("zipformer2", RealtimeSubtitleRecognizer.onlineModelType(RealtimeSubtitleModelCatalog.find("fr")));
        assertEquals("zipformer2", RealtimeSubtitleRecognizer.onlineModelType(RealtimeSubtitleModelCatalog.find("es")));
    }

    @Test
    public void offlineVadFlushesAfterTwoPointTwoSeconds() {
        assertEquals(35_200, RealtimeSubtitleRecognizer.offlineFlushSamples(RealtimeSubtitleModelCatalog.find("yue")));
        assertEquals(35_200, RealtimeSubtitleRecognizer.offlineFlushSamples(RealtimeSubtitleModelCatalog.find("ja")));
    }
    @Test
    public void adProfileHasOneThreadAndSubtitleKeepsItsPreviousBudget() {
        assertEquals(1, RealtimeSubtitleRecognizer.threadCount(SpeechRecognitionFactory.ExecutionProfile.AD_AUDIO));
        assertEquals(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)),
                RealtimeSubtitleRecognizer.threadCount(SpeechRecognitionFactory.ExecutionProfile.SUBTITLE));
    }

    @Test(timeout = 10_000)
    public void interruptedReleaseWaitsForTheActualDecodeWorkerToExit() throws Exception {
        ExecutorService decode = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch allowDecode = new CountDownLatch(1);
        CountDownLatch waitEntered = new CountDownLatch(1);
        AtomicBoolean released = new AtomicBoolean();
        AtomicBoolean interruptPreserved = new AtomicBoolean();
        decode.execute(() -> {
            entered.countDown();
            try {
                allowDecode.await();
            } catch (InterruptedException error) {
                throw new AssertionError("decode must not be interrupted to fake completion", error);
            }
        });
        Thread owner = new Thread(() -> {
            Thread.currentThread().interrupt();
            waitEntered.countDown();
            RealtimeSubtitleRecognizer.awaitRecognitionTermination(decode);
            released.set(true);
            interruptPreserved.set(Thread.currentThread().isInterrupted());
        });
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            owner.start();
            assertTrue(waitEntered.await(2, TimeUnit.SECONDS));
            assertFalse(released.get()); // JNI is still blocked, even though owner was interrupted.
            allowDecode.countDown();
            owner.join(2_000L);
            assertFalse(owner.isAlive());
            assertTrue(decode.isTerminated());
            assertTrue(released.get());
            assertTrue(interruptPreserved.get());
        } finally {
            allowDecode.countDown();
            decode.shutdown();
            owner.join(2_000L);
        }
    }

}
