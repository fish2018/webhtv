package com.fongmi.android.tv.subtitle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}
