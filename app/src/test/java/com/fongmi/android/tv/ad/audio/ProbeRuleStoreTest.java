package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 上游 m3u8-ad-audio-probe 的规则更新语义：只升不降、同版本冲突拒绝、失败保留旧缓存。 */
public class ProbeRuleStoreTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void installedRulesSurviveRestart() throws Exception {
        Path directory = temporaryFolder.newFolder().toPath();

        AdAudioRuleSnapshot installed = new ProbeRuleStore(directory).install(community());
        ProbeRuleStore reopened = new ProbeRuleStore(directory);
        AdAudioRuleSnapshot loaded = reopened.load();

        assertEquals(4, installed.ruleSet().rules().size());
        assertEquals(4, loaded.ruleSet().rules().size());
        assertEquals(installed.version(), loaded.version());
        assertTrue(loaded.version().startsWith("2:"));
        assertEquals(2L, reopened.revision());
        assertFalse(loaded.hasError());
    }

    @Test
    public void higherRevisionReplacesTheCache() throws Exception {
        ProbeRuleStore store = new ProbeRuleStore(temporaryFolder.newFolder().toPath());
        store.install(community());

        AdAudioRuleSnapshot updated = store.install(withRevision(3));

        assertTrue(updated.version().startsWith("3:"));
        assertEquals(3L, store.revision());
    }

    @Test
    public void lowerRevisionIsRejectedAndCacheKeeps() throws Exception {
        ProbeRuleStore store = new ProbeRuleStore(temporaryFolder.newFolder().toPath());
        store.install(community());

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> store.install(withRevision(1)));

        assertEquals(ProbeRuleStore.ERROR_ROLLBACK, error.getMessage());
        assertEquals(2L, store.revision());
    }

    @Test
    public void sameRevisionWithDifferentContentIsAPublishConflict() throws Exception {
        ProbeRuleStore store = new ProbeRuleStore(temporaryFolder.newFolder().toPath());
        store.install(community());
        byte[] tampered = text().replace("ad-1786989757783-1", "ad-1786989757783-9")
                .getBytes(StandardCharsets.UTF_8);

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> store.install(tampered));

        assertEquals(ProbeRuleStore.ERROR_CONFLICT, error.getMessage());
        assertEquals("ad-1786989757783-1", store.load().ruleSet().rules().get(0).id());
    }

    @Test
    public void reinstallingIdenticalBytesIsAccepted() throws Exception {
        ProbeRuleStore store = new ProbeRuleStore(temporaryFolder.newFolder().toPath());
        AdAudioRuleSnapshot first = store.install(community());

        AdAudioRuleSnapshot second = store.install(community());

        assertEquals(first.version(), second.version());
    }

    @Test
    public void corruptCacheReportsAnErrorAndCanBeReplaced() throws Exception {
        Path directory = temporaryFolder.newFolder().toPath();
        ProbeRuleStore store = new ProbeRuleStore(directory);
        store.install(community());
        Files.writeString(directory.resolve("ad-audio-probe-rules.json"), "{\"rules\":[");

        AdAudioRuleSnapshot broken = store.load();
        AdAudioRuleSnapshot repaired = store.install(community());

        assertEquals(ProbeRuleStore.ERROR_LOAD_FAILED, broken.lastError());
        assertFalse(broken.hasRules());
        assertEquals(4, repaired.ruleSet().rules().size());
    }

    @Test
    public void malformedDownloadLeavesNoTemporaryFile() throws Exception {
        Path directory = temporaryFolder.newFolder().toPath();
        ProbeRuleStore store = new ProbeRuleStore(directory);

        assertThrows(IllegalArgumentException.class,
                () -> store.install("{\"format\":\"nope\"}".getBytes(StandardCharsets.UTF_8)));

        assertFalse(Files.exists(directory.resolve("ad-audio-probe-rules.json")));
        assertFalse(Files.exists(directory.resolve("ad-audio-probe-rules.json.tmp")));
        assertEquals(0L, store.revision());
    }

    @Test
    public void emptyStoreLoadsWithoutError() throws Exception {
        AdAudioRuleSnapshot snapshot =
                new ProbeRuleStore(temporaryFolder.newFolder().toPath()).load();

        assertFalse(snapshot.hasRules());
        assertFalse(snapshot.hasError());
        assertEquals(ProbeRuleStore.SOURCE_ID, snapshot.sourceId());
    }

    /** 手动导入的规则优先于社区源，社区源只在本地为空时补位。 */
    @Test
    public void manualImportWinsOverTheCommunityFeed() throws Exception {
        ProbeRuleStore probe = new ProbeRuleStore(temporaryFolder.newFolder().toPath());
        AdAudioRuleStore local = new AdAudioRuleStore(temporaryFolder.newFolder().toPath());
        probe.install(community());
        local.importJson(text().replace("ad-1786989757783-1", "mine"));

        AdAudioRuleSnapshot resolved = new PrioritizedAdAudioRuleSource(local, probe).load();

        assertEquals("mine", resolved.ruleSet().rules().get(0).id());
    }

    private byte[] withRevision(int revision) throws Exception {
        return text().replace("\"revision\": 2", "\"revision\": " + revision)
                .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] community() throws Exception {
        return text().getBytes(StandardCharsets.UTF_8);
    }

    private String text() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/probe-rules-v1-community.json")) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
