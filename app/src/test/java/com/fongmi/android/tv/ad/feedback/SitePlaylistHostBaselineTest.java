package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class SitePlaylistHostBaselineTest {

    @Test
    public void recordsAndReadsBackHosts() {
        SitePlaylistHostBaseline baseline =
                new SitePlaylistHostBaseline(new SitePlaylistHostBaseline.MemoryStorage());

        baseline.record("siteA", "v.example.com", "cdn.example.com");

        assertEquals(List.of("v.example.com", "cdn.example.com"), baseline.hosts("siteA"));
    }

    @Test
    public void normalisesHostCaseAndWhitespace() {
        SitePlaylistHostBaseline baseline =
                new SitePlaylistHostBaseline(new SitePlaylistHostBaseline.MemoryStorage());

        baseline.record("siteA", "  V.Example.COM  ");

        assertEquals(List.of("v.example.com"), baseline.hosts("siteA"));
    }

    @Test
    public void repeatedHostMovesToMostRecentWithoutDuplicating() {
        SitePlaylistHostBaseline baseline =
                new SitePlaylistHostBaseline(new SitePlaylistHostBaseline.MemoryStorage());

        baseline.record("siteA", "a.com", "b.com");
        baseline.record("siteA", "a.com");

        assertEquals(List.of("b.com", "a.com"), baseline.hosts("siteA"));
    }

    @Test
    public void evictsOldestHostBeyondPerSiteLimit() {
        SitePlaylistHostBaseline baseline =
                new SitePlaylistHostBaseline(new SitePlaylistHostBaseline.MemoryStorage());

        for (int i = 0; i < SitePlaylistHostBaseline.MAX_HOSTS_PER_SITE + 3; i++) {
            baseline.record("siteA", "host" + i + ".com");
        }

        List<String> hosts = baseline.hosts("siteA");
        assertEquals(SitePlaylistHostBaseline.MAX_HOSTS_PER_SITE, hosts.size());
        // 最早的三个被淘汰
        assertTrue(hosts.contains("host3.com"));
        assertTrue(hosts.stream().noneMatch(host -> host.equals("host0.com")));
    }

    @Test
    public void evictsLeastRecentlyUsedSiteBeyondGlobalLimit() {
        SitePlaylistHostBaseline baseline =
                new SitePlaylistHostBaseline(new SitePlaylistHostBaseline.MemoryStorage());

        for (int i = 0; i < SitePlaylistHostBaseline.MAX_SITES; i++) {
            baseline.record("site" + i, "host.com");
        }
        // 触碰 site0，使其变为最近使用
        baseline.hosts("site0");
        baseline.record("overflow", "host.com");

        assertEquals(SitePlaylistHostBaseline.MAX_SITES, baseline.siteCount());
        // site0 因刚被访问而存活，site1 成为最久未用被淘汰
        assertTrue(baseline.hosts("site0").contains("host.com"));
        assertTrue(baseline.hosts("site1").isEmpty());
    }

    @Test
    public void survivesRoundTripThroughStorage() {
        SitePlaylistHostBaseline.MemoryStorage storage = new SitePlaylistHostBaseline.MemoryStorage();
        SitePlaylistHostBaseline first = new SitePlaylistHostBaseline(storage);
        first.record("siteA", "a.com", "b.com");
        first.record("siteB", "c.com");

        SitePlaylistHostBaseline restored = new SitePlaylistHostBaseline(storage);

        assertEquals(List.of("a.com", "b.com"), restored.hosts("siteA"));
        assertEquals(List.of("c.com"), restored.hosts("siteB"));
    }

    @Test
    public void escapesSiteKeyContainingSeparators() {
        SitePlaylistHostBaseline.MemoryStorage storage = new SitePlaylistHostBaseline.MemoryStorage();
        SitePlaylistHostBaseline first = new SitePlaylistHostBaseline(storage);
        String awkward = "site;with|odd,chars%here";
        first.record(awkward, "a.com");

        SitePlaylistHostBaseline restored = new SitePlaylistHostBaseline(storage);

        assertEquals(List.of("a.com"), restored.hosts(awkward));
        assertEquals(1, restored.siteCount());
    }

    @Test
    public void ignoresBlankAndNullInput() {
        SitePlaylistHostBaseline baseline =
                new SitePlaylistHostBaseline(new SitePlaylistHostBaseline.MemoryStorage());

        baseline.record(null, "a.com");
        baseline.record("  ", "a.com");
        baseline.record("siteA", (String[]) null);
        baseline.record("siteA", "", "   ");

        assertEquals(0, baseline.siteCount());
        assertTrue(baseline.hosts("siteA").isEmpty());
        assertTrue(baseline.hosts(null).isEmpty());
    }

    @Test
    public void toleratesCorruptStoredValue() {
        SitePlaylistHostBaseline.MemoryStorage storage = new SitePlaylistHostBaseline.MemoryStorage();
        storage.write("garbage;;|;siteA|a.com;noseparator");

        SitePlaylistHostBaseline baseline = new SitePlaylistHostBaseline(storage);

        assertEquals(List.of("a.com"), baseline.hosts("siteA"));
        assertEquals(1, baseline.siteCount());
    }

    @Test
    public void clearRemovesEverythingAndPersists() {
        SitePlaylistHostBaseline.MemoryStorage storage = new SitePlaylistHostBaseline.MemoryStorage();
        SitePlaylistHostBaseline baseline = new SitePlaylistHostBaseline(storage);
        baseline.record("siteA", "a.com");

        baseline.clear();

        assertEquals(0, baseline.siteCount());
        assertEquals(0, new SitePlaylistHostBaseline(storage).siteCount());
    }
}
