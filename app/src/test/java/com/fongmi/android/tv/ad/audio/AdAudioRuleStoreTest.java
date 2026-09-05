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

public class AdAudioRuleStoreTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void importedSnapshotSurvivesRestart() throws Exception {
        Path directory = temporaryFolder.newFolder().toPath();

        AdAudioRuleSnapshot imported = new AdAudioRuleStore(directory).importJson(validRuleJson("rule-a"));
        AdAudioRuleSnapshot reloaded = new AdAudioRuleStore(directory).load();

        assertEquals("rule-a", imported.ruleSet().rules().get(0).id());
        assertEquals("rule-a", reloaded.ruleSet().rules().get(0).id());
        assertEquals(imported.version(), reloaded.version());
        assertFalse(reloaded.hasError());
    }

    /** 社区 Probe Rules v1 可以直接导入，落盘时被规范化成本项目的 v2 格式。 */
    @Test
    public void probeV1RulesImportAndReloadAsCanonicalV2() throws Exception {
        Path directory = temporaryFolder.newFolder().toPath();
        String probeJson;
        try (InputStream stream = getClass().getResourceAsStream("/probe-rules-v1-community.json")) {
            assertNotNull(stream);
            probeJson = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        AdAudioRuleSnapshot imported = new AdAudioRuleStore(directory).importJson(probeJson);
        AdAudioRuleSnapshot reloaded = new AdAudioRuleStore(directory).load();
        String stored = Files.readString(directory.resolve("ad-audio-rules.json"), StandardCharsets.UTF_8);

        assertEquals(4, imported.ruleSet().rules().size());
        assertEquals(4, reloaded.ruleSet().rules().size());
        assertEquals(imported.version(), reloaded.version());
        assertFalse(reloaded.hasError());
        assertTrue(stored.contains(AudioFingerprintRuleSet.ALGORITHM_ID));
        assertFalse(stored.contains(ProbeRuleCodec.FORMAT));
    }

    /**
     * v2 的规则 id 是自由字符串，所以 id 里出现 Probe 的 format 值时不能被误判成 v1。
     * 用子串匹配分流会让这个合法的 v2 文件整包被拒。
     */
    @Test
    public void v2RuleIdContainingTheProbeFormatValueStillImportsAsV2() throws Exception {
        Path directory = temporaryFolder.newFolder().toPath();

        AdAudioRuleSnapshot imported =
                new AdAudioRuleStore(directory).importJson(validRuleJson(ProbeRuleCodec.FORMAT));

        assertEquals(ProbeRuleCodec.FORMAT, imported.ruleSet().rules().get(0).id());
    }

    @Test
    public void invalidImportDoesNotReplaceLastGoodSnapshot() throws Exception {
        Path directory = temporaryFolder.newFolder().toPath();
        AdAudioRuleStore store = new AdAudioRuleStore(directory);
        store.importJson(validRuleJson("rule-a"));

        assertThrows(IllegalArgumentException.class, () -> store.importJson("{\"rules\":["));

        AdAudioRuleSnapshot reloaded = new AdAudioRuleStore(directory).load();
        assertEquals("rule-a", reloaded.ruleSet().rules().get(0).id());
        assertFalse(Files.exists(directory.resolve("ad-audio-rules.json.tmp")));
    }

    @Test
    public void oversizedImportDoesNotReplaceLastGoodSnapshot() throws Exception {
        Path directory = temporaryFolder.newFolder().toPath();
        AdAudioRuleStore store = new AdAudioRuleStore(directory);
        store.importJson(validRuleJson("rule-a"));

        String oversized = " ".repeat(AdAudioRuleStore.MAX_IMPORT_BYTES + 1);
        assertThrows(IllegalArgumentException.class, () -> store.importJson(oversized));

        assertEquals("rule-a", new AdAudioRuleStore(directory).load().ruleSet().rules().get(0).id());
    }

    @Test
    public void corruptPersistedFileProducesErrorSnapshot() throws Exception {
        Path directory = temporaryFolder.newFolder().toPath();
        Files.writeString(directory.resolve("ad-audio-rules.json"), "not-json");

        AdAudioRuleSnapshot snapshot = new AdAudioRuleStore(directory).load();

        assertTrue(snapshot.ruleSet().rules().isEmpty());
        assertTrue(snapshot.hasError());
    }

    @Test
    public void clearRemovesRulesAndTemporaryFile() throws Exception {
        Path directory = temporaryFolder.newFolder().toPath();
        AdAudioRuleStore store = new AdAudioRuleStore(directory);
        store.importJson(validRuleJson("rule-a"));
        Files.writeString(directory.resolve("ad-audio-rules.json.tmp"), "partial");

        AdAudioRuleSnapshot snapshot = store.clear();

        assertTrue(snapshot.ruleSet().rules().isEmpty());
        assertFalse(Files.exists(directory.resolve("ad-audio-rules.json")));
        assertFalse(Files.exists(directory.resolve("ad-audio-rules.json.tmp")));
    }

    private static String validRuleJson(String id) {
        return "{"
                + "\"schemaVersion\":2,"
                + "\"algorithm\":{\"id\":\"spectral-sequence-v2\",\"sampleRate\":16000,"
                + "\"windowMs\":512,\"hopMs\":256,\"bandCount\":16},"
                + "\"rules\":[{\"id\":\"" + id + "\",\"durationMs\":15000,\"anchorOffsetMs\":0,"
                + "\"anchorDurationMs\":3000,\"fingerprint\":["
                + "\"32f0007c\",\"35c100e0\",\"3b8b01c0\",\"d30a0380\"]}]}";
    }
}
