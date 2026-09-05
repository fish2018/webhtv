package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class ProxyDomainRuleSetTest {

    @Test
    public void multipleDomainsShareOneParallelPlanAndMatchSubdomainsSafely() {
        ProxyDomainRuleSet rules = ProxyDomainRuleSet.parse(
                "Example.COM, *.cdn.example.com = 6, 12\nvideo.example.net=3,7");
        ProxyRuntimeConfig global = config(true, 4, 8);

        assertPlan(rules.resolve("https://example.com/movie.mp4", global), 6, 12);
        assertPlan(rules.resolve("https://a.example.com/movie.mp4", global), 6, 12);
        assertPlan(rules.resolve("https://edge.cdn.example.com/movie.mp4", global), 6, 12);
        assertPlan(rules.resolve("https://video.example.net/movie.mp4", global), 3, 7);
        assertSame(global, rules.resolve("https://badexample.com/movie.mp4", global));
        assertEquals(6, rules.maxConcurrency());
        assertEquals(2, rules.size());
    }

    @Test
    public void noMatchFallsBackToGlobalAndDisabledConfigIgnoresOverrides() {
        ProxyDomainRuleSet rules = ProxyDomainRuleSet.parse("media.example.com=9,18");
        ProxyRuntimeConfig enabled = config(true, 4, 8);
        ProxyRuntimeConfig disabled = config(false, 4, 8);

        assertSame(enabled, rules.resolve("https://other.example.com/a.mp4", enabled));
        assertSame(disabled, rules.resolve("https://media.example.com/a.mp4", disabled));
    }

    @Test
    public void parserRejectsMalformedOrAmbiguousRules() {
        assertThrows(IllegalArgumentException.class, () -> ProxyDomainRuleSet.parse("example.com=0,8"));
        assertThrows(IllegalArgumentException.class, () -> ProxyDomainRuleSet.parse("example.com=4,0"));
        assertThrows(IllegalArgumentException.class, () -> ProxyDomainRuleSet.parse("https://example.com/path=4,8"));
        assertThrows(IllegalArgumentException.class, () -> ProxyDomainRuleSet.parse("example.com=129,8"));
        assertThrows(IllegalArgumentException.class, () -> ProxyDomainRuleSet.parse("example.com=4,65537"));
        assertThrows(IllegalArgumentException.class, () -> ProxyDomainRuleSet.parse("example.com=4,8\n*.example.com=6,12"));
    }

    @Test
    public void extractsNormalizedCurrentHostForOneClickRuleCreation() {
        assertEquals("media.example.com", ProxyDomainRuleSet.extractHost("https://Media.Example.com:8443/video.mp4?token=1"));
        assertEquals("xn--fsqu00a.xn--0zwm56d", ProxyDomainRuleSet.extractHost("https://xn--fsqu00a.xn--0zwm56d/video.mp4"));
        assertEquals(null, ProxyDomainRuleSet.extractHost("not-a-url"));
    }

    @Test
    public void normalizedTextKeepsOneRulePerLine() {
        ProxyDomainRuleSet rules = ProxyDomainRuleSet.parse(" Example.COM , *.CDN.Example.com = 6 , 12 ");

        assertEquals("example.com,cdn.example.com=6,12", rules.serialize());
    }

    private static void assertPlan(ProxyRuntimeConfig config, int threads, int shards) {
        assertEquals(threads, config.rangeConcurrency());
        assertEquals(ProxyRuntimeConfig.ShardMode.COUNT, config.shardMode());
        assertEquals(shards, config.shardCount());
    }

    private static ProxyRuntimeConfig config(boolean enabled, int threads, int shards) {
        ProxyRuntimeConfig defaults = ProxyRuntimeConfig.defaults();
        return new ProxyRuntimeConfig(
                defaults.schemaVersion(),
                enabled,
                defaults.portMode(),
                defaults.configuredPort(),
                defaults.serverWorkers(),
                defaults.connectionQueueCapacity(),
                Math.max(threads, defaults.rangeWorkers()),
                threads,
                ProxyRuntimeConfig.ShardMode.COUNT,
                shards,
                defaults.chunkSizeBytes(),
                defaults.maxSessions(),
                defaults.reorderWindowBlocks(),
                defaults.bufferBudgetBytes(),
                defaults.retryCount(),
                defaults.connectTimeoutMillis(),
                defaults.readTimeoutMillis(),
                defaults.fileThresholdBytes());
    }
}