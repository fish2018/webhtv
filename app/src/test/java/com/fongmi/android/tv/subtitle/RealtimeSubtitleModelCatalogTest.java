package com.fongmi.android.tv.subtitle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.setting.Setting;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class RealtimeSubtitleModelCatalogTest {

    @Test
    public void catalogContainsAllReferenceLanguagesAndLegacyBilingualModel() {
        List<String> ids = Arrays.stream(RealtimeSubtitleModelCatalog.models())
                .map(RealtimeSubtitleModelCatalog.ModelSpec::id)
                .toList();

        assertEquals(List.of("zh", "yue", "en", "de", "fr", "es", "ja", "zh-en"), ids);
    }

    @Test
    public void cantoneseAndJapaneseUseOfflineVadEngines() {
        RealtimeSubtitleModelCatalog.ModelSpec cantonese = RealtimeSubtitleModelCatalog.find("yue");
        RealtimeSubtitleModelCatalog.ModelSpec japanese = RealtimeSubtitleModelCatalog.find("ja");

        assertEquals(RealtimeSubtitleModelCatalog.Engine.OFFLINE_WENET_CTC, cantonese.engine());
        assertEquals(RealtimeSubtitleModelCatalog.Engine.OFFLINE_MOONSHINE, japanese.engine());
        assertTrue(cantonese.needsVad());
        assertTrue(japanese.needsVad());
        assertFalse(RealtimeSubtitleModelCatalog.find("de").needsVad());
    }

    @Test
    public void everyDownloadIsPinnedBySizeAndSha256() {
        for (RealtimeSubtitleModelCatalog.ModelSpec model : RealtimeSubtitleModelCatalog.models()) {
            for (RealtimeSubtitleModelCatalog.ModelFile file : model.files()) {
                assertTrue(file.size() > 0L);
                assertEquals(64, file.sha256().length());
                assertTrue(file.url().startsWith("https://"));
            }
        }
        assertEquals(64, RealtimeSubtitleModelCatalog.vad().sha256().length());
    }

    @Test
    public void offlineDownloadsIncludeSharedVadBeforeModelFiles() {
        RealtimeSubtitleModelCatalog.ModelSpec cantonese = RealtimeSubtitleModelCatalog.find("yue");
        RealtimeSubtitleModelCatalog.ModelSpec japanese = RealtimeSubtitleModelCatalog.find("ja");

        assertSame(RealtimeSubtitleModelCatalog.vad(), RealtimeSubtitleModelCatalog.downloads(cantonese)[0]);
        assertSame(RealtimeSubtitleModelCatalog.vad(), RealtimeSubtitleModelCatalog.downloads(japanese)[0]);
        assertEquals(cantonese.files().length + 1, RealtimeSubtitleModelCatalog.downloads(cantonese).length);
        assertEquals(japanese.files().length + 1, RealtimeSubtitleModelCatalog.downloads(japanese).length);
        assertEquals(RealtimeSubtitleModelCatalog.find("de").files().length, RealtimeSubtitleModelCatalog.downloads(RealtimeSubtitleModelCatalog.find("de")).length);
    }

    @Test
    public void settingIdsMatchCatalogOrder() {
        assertEquals(List.of(
                        Setting.REALTIME_SUBTITLE_MODEL_ZH,
                        Setting.REALTIME_SUBTITLE_MODEL_YUE,
                        Setting.REALTIME_SUBTITLE_MODEL_EN,
                        Setting.REALTIME_SUBTITLE_MODEL_DE,
                        Setting.REALTIME_SUBTITLE_MODEL_FR,
                        Setting.REALTIME_SUBTITLE_MODEL_ES,
                        Setting.REALTIME_SUBTITLE_MODEL_JA,
                        Setting.REALTIME_SUBTITLE_MODEL_ZH_EN),
                Arrays.stream(RealtimeSubtitleModelCatalog.models())
                        .map(RealtimeSubtitleModelCatalog.ModelSpec::id)
                        .toList());
    }
}
