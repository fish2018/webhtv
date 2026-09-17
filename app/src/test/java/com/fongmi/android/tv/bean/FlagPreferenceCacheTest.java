package com.fongmi.android.tv.bean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FlagPreferenceCacheTest {

    private File directory;
    private File cacheFile;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("flag-preference-cache").toFile();
        cacheFile = new File(directory, "flag_preferences.json");
    }

    @After
    public void tearDown() {
        File temp = new File(cacheFile.getPath() + ".tmp");
        if (temp.exists()) temp.delete();
        if (cacheFile.exists()) cacheFile.delete();
        directory.delete();
    }

    @Test
    public void preferenceSurvivesProcessRestart() {
        FlagPreferenceCache cache = new FlagPreferenceCache(cacheFile);
        cache.put("site", "vod", "夸克原画#0101#1", "夸克原画#0101");
        cache.save();

        FlagPreferenceCache restored = new FlagPreferenceCache(cacheFile);
        FlagPreferenceCache.FlagPreference preference = restored.get("site", "vod");

        assertNotNull(preference);
        assertEquals("夸克原画#0101#1", preference.getStableKey());
        assertEquals("夸克原画#0101", preference.getFlagName());
    }

    @Test
    public void latestSelectionReplacesEarlierOne() {
        FlagPreferenceCache cache = new FlagPreferenceCache(cacheFile);
        cache.put("site", "vod", "BD5播放#0", "BD5播放");
        cache.put("site", "vod", "夸克无限#0101#2", "夸克无限#0101");
        cache.save();

        FlagPreferenceCache restored = new FlagPreferenceCache(cacheFile);

        assertEquals("夸克无限#0101#2", restored.get("site", "vod").getStableKey());
        assertEquals(1, restored.size());
    }

    @Test
    public void preferencesAreScopedPerSiteAndVod() {
        FlagPreferenceCache cache = new FlagPreferenceCache(cacheFile);
        cache.put("site-a", "vod-1", "线路一#0", "线路一");
        cache.put("site-b", "vod-1", "线路二#1", "线路二");
        cache.put("site-a", "vod-2", "线路三#2", "线路三");

        assertEquals("线路一#0", cache.get("site-a", "vod-1").getStableKey());
        assertEquals("线路二#1", cache.get("site-b", "vod-1").getStableKey());
        assertEquals("线路三#2", cache.get("site-a", "vod-2").getStableKey());
        assertNull(cache.get("site-b", "vod-2"));
    }

    @Test
    public void blankIdentityIsRejected() {
        FlagPreferenceCache cache = new FlagPreferenceCache(cacheFile);
        cache.put("", "vod", "线路一#0", "线路一");
        cache.put("site", "", "线路一#0", "线路一");
        cache.put("site", "vod", "", "");

        assertEquals(0, cache.size());
        assertNull(cache.get("site", "vod"));
    }

    @Test
    public void removeClearsStoredPreference() {
        FlagPreferenceCache cache = new FlagPreferenceCache(cacheFile);
        cache.put("site", "vod", "线路一#0", "线路一");

        cache.remove("site", "vod");

        assertNull(cache.get("site", "vod"));
    }

    @Test
    public void missingCacheFileYieldsNoPreference() {
        FlagPreferenceCache cache = new FlagPreferenceCache(cacheFile);

        assertNull(cache.get("site", "vod"));
        assertEquals(0, cache.size());
    }

    /**
     * 起播路径每次换集都会重申当前线路。若「选择未变」时只刷内存不落盘，
     * 磁盘 timestamp 会一直停在首次选择的时刻，长期只用同一条线路的用户
     * 反而会在过期窗口后丢失偏好。
     */
    @Test
    public void reaffirmingTheSameSelectionRenewsItOnDisk() throws Exception {
        FlagPreferenceCache cache = new FlagPreferenceCache(cacheFile);
        cache.put("site", "vod", "线路一#0", "线路一");
        cache.save();

        long firstWrite = readTimestamp();
        // 把磁盘上的 timestamp 退回 60 天前，模拟长期使用同一条线路。
        writeTimestamp(firstWrite - 60L * 24 * 60 * 60 * 1000);

        FlagPreferenceCache reopened = new FlagPreferenceCache(cacheFile);
        reopened.put("site", "vod", "线路一#0", "线路一");
        reopened.save();

        // 只能倒退到 EXPIRE_TIME 以内：一旦磁盘 timestamp 越过过期窗口，
        // load() 会先把它丢掉，任何 put 都来不及续期。
        assertTrue("重申同一选择必须续期落盘，否则偏好会在过期窗口后失效",
                readTimestamp() >= firstWrite);
    }

    /**
     * 与上一条互补：一天内重申同一选择不该反复写盘。
     */
    @Test
    public void reaffirmingWithinTheRenewIntervalDoesNotRewrite() throws Exception {
        FlagPreferenceCache cache = new FlagPreferenceCache(cacheFile);
        cache.put("site", "vod", "线路一#0", "线路一");
        cache.save();

        long firstWrite = readTimestamp();
        cache.put("site", "vod", "线路一#0", "线路一");
        cache.save();

        assertEquals("一天内重申同一选择不该重复写盘", firstWrite, readTimestamp());
    }

    /**
     * 已经超容的旧文件必须收敛。淘汰若不置 dirty，内存减下去了但不落盘，
     * 下次打开又是超容状态。
     */
    @Test
    public void overCapacityFileConvergesAndPersists() throws Exception {
        StringBuilder json = new StringBuilder("{");
        long now = System.currentTimeMillis();
        for (int i = 0; i < 520; i++) {
            if (i > 0) json.append(',');
            json.append("\"site|vod-").append(i).append("\":{\"stableKey\":\"线路#0\",")
                    .append("\"flagName\":\"线路\",\"timestamp\":").append(now - i).append('}');
        }
        Files.write(cacheFile.toPath(), json.append('}').toString().getBytes(StandardCharsets.UTF_8));

        FlagPreferenceCache cache = new FlagPreferenceCache(cacheFile);
        assertTrue("载入时就该收口到容量上限内", cache.size() <= 500);

        // 故意用「已存在且一天内」的选择：这条路径 changed 和 stale 都是 false，
        // 不会自己置 dirty，只有淘汰本身置了 dirty 才会落盘。
        FlagPreferenceCache.FlagPreference survivor = cache.get("site", "vod-0");
        assertNotNull("最新的条目应当留在容量内", survivor);
        cache.put("site", "vod-0", survivor.getStableKey(), survivor.getFlagName());
        cache.save();

        // 必须查磁盘上的真实条目数：load() 每次都会收口，
        // 断言重新载入后的 size() 恒为 500，看不出到底有没有落盘。
        assertEquals("收口结果必须落盘，否则下次打开又要重新淘汰", 500, countPersistedEntries());
    }

    /**
     * 索引未知时 savePreferredFlag 会故意只存线路名，这条路径必须放行。
     */
    @Test
    public void flagNameOnlyPreferenceIsAccepted() {
        FlagPreferenceCache cache = new FlagPreferenceCache(cacheFile);
        cache.put("site", "vod", "", "夸克原画#0101");
        cache.save();

        FlagPreferenceCache.FlagPreference preference =
                new FlagPreferenceCache(cacheFile).get("site", "vod");

        assertNotNull("只有线路名的偏好也要能存能取", preference);
        assertEquals("", preference.getStableKey());
        assertEquals("夸克原画#0101", preference.getFlagName());
    }

    @Test
    public void removeIsPersistedNotOnlyInMemory() {
        FlagPreferenceCache cache = new FlagPreferenceCache(cacheFile);
        cache.put("site", "vod", "线路一#0", "线路一");
        cache.save();

        cache.remove("site", "vod");
        cache.save();

        assertNull("删除必须落盘", new FlagPreferenceCache(cacheFile).get("site", "vod"));
    }

    @Test
    public void clearRemovesBothTheCacheFileAndAnyTemporaryFile() throws Exception {
        FlagPreferenceCache cache = new FlagPreferenceCache(cacheFile);
        cache.put("site", "vod", "线路一#0", "线路一");
        cache.save();
        File temp = new File(cacheFile.getPath() + ".tmp");
        Files.write(temp.toPath(), "{}".getBytes(StandardCharsets.UTF_8));

        cache.clear();

        assertTrue(!cacheFile.exists());
        assertTrue("残留的临时文件也要清掉", !temp.exists());
        assertNull(cache.get("site", "vod"));
    }

    /**
     * gson 反序列化不走构造函数，缺字段的旧数据 timestamp 会是 0。
     * 直接判过期会让这条偏好永久失效。
     */
    @Test
    public void entryWithoutTimestampIsKeptRatherThanDiscarded() throws Exception {
        Files.write(cacheFile.toPath(),
                "{\"site|vod\":{\"stableKey\":\"线路一#0\",\"flagName\":\"线路一\"}}".getBytes(StandardCharsets.UTF_8));

        FlagPreferenceCache cache = new FlagPreferenceCache(cacheFile);
        FlagPreferenceCache.FlagPreference preference = cache.get("site", "vod");

        assertNotNull("缺 timestamp 的条目不该被当成过期丢掉", preference);
        assertEquals("线路一#0", preference.getStableKey());
    }

    @Test
    public void unusablePersistedEntryIsDropped() throws Exception {
        Files.write(cacheFile.toPath(),
                "{\"site|vod\":{\"timestamp\":0}}".getBytes(StandardCharsets.UTF_8));

        FlagPreferenceCache cache = new FlagPreferenceCache(cacheFile);

        assertNull("两个字段都空的条目匹配不到线路，必须丢弃", cache.get("site", "vod"));
        assertEquals(0, cache.size());
    }

    @Test
    public void saveLeavesNoTemporaryFileBehind() {
        FlagPreferenceCache cache = new FlagPreferenceCache(cacheFile);
        cache.put("site", "vod", "线路一#0", "线路一");
        cache.save();

        assertTrue(cacheFile.exists());
        assertTrue("临时文件必须在替换后消失，否则会随使用不断堆积",
                !new File(cacheFile.getPath() + ".tmp").exists());
    }

    @Test
    public void repeatedSavesKeepThePreferenceReadable() {
        FlagPreferenceCache cache = new FlagPreferenceCache(cacheFile);
        for (int i = 0; i < 5; i++) {
            cache.put("site", "vod", "线路" + i + "#" + i, "线路" + i);
            cache.save();
        }

        assertEquals("线路4#4", new FlagPreferenceCache(cacheFile).get("site", "vod").getStableKey());
    }

    @Test
    public void corruptedCacheFileDegradesToNoPreference() throws Exception {
        Files.write(cacheFile.toPath(), "{ not json".getBytes(StandardCharsets.UTF_8));

        FlagPreferenceCache cache = new FlagPreferenceCache(cacheFile);

        assertNull(cache.get("site", "vod"));
        cache.put("site", "vod", "线路一#0", "线路一");
        cache.save();
        assertEquals("线路一#0", new FlagPreferenceCache(cacheFile).get("site", "vod").getStableKey());
    }

    @Test
    public void expiredEntryIsNotRestored() throws Exception {
        FlagPreferenceCache cache = new FlagPreferenceCache(cacheFile);
        cache.put("site", "vod", "线路一#0", "线路一");
        cache.save();

        writeTimestamp(System.currentTimeMillis() - 200L * 24 * 60 * 60 * 1000);

        assertNull(new FlagPreferenceCache(cacheFile).get("site", "vod"));
    }

    private int countPersistedEntries() throws Exception {
        String content = new String(Files.readAllBytes(cacheFile.toPath()), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\"site\\|vod-\\d+\"").matcher(content);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private long readTimestamp() throws Exception {
        String content = new String(Files.readAllBytes(cacheFile.toPath()), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\"timestamp\":(\\d+)").matcher(content);
        assertTrue("落盘内容里必须有 timestamp", matcher.find());
        return Long.parseLong(matcher.group(1));
    }

    private void writeTimestamp(long timestamp) throws Exception {
        String content = new String(Files.readAllBytes(cacheFile.toPath()), StandardCharsets.UTF_8);
        String rewritten = content.replaceAll("\"timestamp\":\\d+", "\"timestamp\":" + timestamp);
        Files.write(cacheFile.toPath(), rewritten.getBytes(StandardCharsets.UTF_8));
    }
}
