package com.fongmi.android.tv.subtitle.provider;

import com.fongmi.android.tv.subtitle.model.SubtitleAsset;
import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchType;
import com.fongmi.android.tv.subtitle.model.SubtitleQuery;
import com.fongmi.android.tv.subtitle.model.SubtitleQuerySource;
import com.fongmi.android.tv.subtitle.model.SubtitleStrictness;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class SubtitleProviderRegistryTest {

    @Test
    public void search_runsProviderQueriesConcurrentlyAndDedupesResults() {
        CountDownLatch started = new CountDownLatch(4);
        SubtitleProviderRegistry registry = new SubtitleProviderRegistry(
                new BlockingProvider("alpha", started, true),
                new BlockingProvider("beta", started, false));

        List<SubtitleCandidate> candidates = registry.search(
                List.of(query("流浪地球"), query("The Wandering Earth")),
                SubtitleContext.builder().canonicalTitle("流浪地球").build());

        assertEquals(3, candidates.size());
        assertEquals("alpha", candidates.get(0).getProvider());
        assertEquals("shared", candidates.get(0).getCandidateId());
        assertEquals("beta", candidates.get(1).getProvider());
        assertEquals("流浪地球", candidates.get(1).getCandidateId());
        assertEquals("The Wandering Earth", candidates.get(2).getCandidateId());
    }

    @Test
    public void search_runsQueryIndependentProviderOnce() {
        CountingProvider provider = new CountingProvider();
        SubtitleProviderRegistry registry = new SubtitleProviderRegistry(provider);

        List<SubtitleCandidate> candidates = registry.search(
                List.of(query("流浪地球"), query("The Wandering Earth")),
                SubtitleContext.builder().canonicalTitle("流浪地球").build());

        assertEquals(1, provider.calls);
        assertEquals(1, candidates.size());
        assertEquals("流浪地球", candidates.get(0).getCandidateId());
    }

    private SubtitleQuery query(String text) {
        return new SubtitleQuery(text, text, "zh", SubtitleQuerySource.SOURCE_TITLE, SubtitleStrictness.NORMAL, 0, -1, -1);
    }

    private static final class BlockingProvider implements SubtitleProvider {

        private final String name;
        private final CountDownLatch started;
        private final boolean duplicatePerQuery;

        private BlockingProvider(String name, CountDownLatch started, boolean duplicatePerQuery) {
            this.name = name;
            this.started = started;
            this.duplicatePerQuery = duplicatePerQuery;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public List<SubtitleCandidate> search(SubtitleQuery query, SubtitleContext context) throws Exception {
            started.countDown();
            if (!started.await(1, TimeUnit.SECONDS)) throw new AssertionError("searches did not overlap");
            String id = duplicatePerQuery ? "shared" : query.getText();
            return List.of(new SubtitleCandidate(name, id, query.getText(), "zh", "srt", "", 0, 0, -1, -1, SubtitleMatchType.METADATA_FUZZY, query.getKey(), true, "{}"));
        }

        @Override
        public SubtitleAsset resolve(SubtitleCandidate candidate, SubtitleContext context) {
            return null;
        }
    }

    private static final class CountingProvider implements SubtitleProvider {

        private int calls;

        @Override
        public String getName() {
            return "counter";
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean isQueryIndependent() {
            return true;
        }

        @Override
        public List<SubtitleCandidate> search(SubtitleQuery query, SubtitleContext context) {
            calls++;
            return List.of(new SubtitleCandidate("counter", query.getText(), query.getText(), "zh", "srt", "", 0, 0, -1, -1, SubtitleMatchType.METADATA_FUZZY, query.getKey(), true, "{}"));
        }

        @Override
        public SubtitleAsset resolve(SubtitleCandidate candidate, SubtitleContext context) {
            return null;
        }
    }
}
