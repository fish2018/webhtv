package com.fongmi.android.tv.subtitle.provider;

import android.util.Log;

import com.fongmi.android.tv.subtitle.model.SubtitleAsset;
import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleQuery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class SubtitleProviderRegistry {

    private static final String TAG = "SubtitleMatch";
    private static final int MAX_SEARCH_THREADS = 8;

    private final Map<String, SubtitleProvider> providers;

    public SubtitleProviderRegistry() {
        this(new AssrtSubtitleProvider(), new XunleiSubtitleProvider(), new ShooterSubtitleProvider());
    }

    SubtitleProviderRegistry(SubtitleProvider... providers) {
        this.providers = new LinkedHashMap<>();
        if (providers != null) for (SubtitleProvider provider : providers) register(provider);
    }

    public void register(SubtitleProvider provider) {
        if (provider == null || isEmpty(provider.getName())) return;
        providers.put(provider.getName(), provider);
    }

    public List<SubtitleProvider> enabledProviders() {
        List<SubtitleProvider> items = new ArrayList<>();
        for (SubtitleProvider provider : providers.values()) if (provider.isEnabled()) items.add(provider);
        return items;
    }

    public List<SubtitleCandidate> search(List<SubtitleQuery> queries, SubtitleContext context) {
        List<SubtitleCandidate> items = new ArrayList<>();
        if (queries == null || queries.isEmpty()) return items;

        List<SearchTask> tasks = new ArrayList<>();
        List<SubtitleProvider> enabled = enabledProviders();
        Map<String, Integer> addedByProvider = new LinkedHashMap<>();
        for (SubtitleProvider provider : enabled) {
            addedByProvider.put(provider.getName(), 0);
            if (provider.isQueryIndependent()) {
                SubtitleQuery query = firstQuery(queries);
                if (query != null) tasks.add(new SearchTask(provider, query));
            } else {
                for (SubtitleQuery query : queries) if (query != null) tasks.add(new SearchTask(provider, query));
            }
        }
        if (tasks.isEmpty()) return items;

        int threads = Math.max(1, Math.min(MAX_SEARCH_THREADS, tasks.size()));
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<SearchResult>> futures = new ArrayList<>();
        for (SearchTask task : tasks) futures.add(executor.submit(() -> searchOne(task.provider, task.query, context)));
        executor.shutdown();

        Set<String> dedupe = new LinkedHashSet<>();
        for (Future<SearchResult> future : futures) {
            SearchResult result;
            try {
                result = future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
                break;
            } catch (ExecutionException e) {
                Throwable error = e.getCause() == null ? e : e.getCause();
                logW("provider search failed error=" + error.getMessage(), error);
                continue;
            }
            if (result.error != null) {
                logW("provider search failed provider=" + result.providerName + " query=" + result.queryText + " error=" + result.error.getMessage(), result.error);
                continue;
            }
            int added = addCandidates(items, dedupe, result.candidates);
            addedByProvider.put(result.providerName, addedByProvider.getOrDefault(result.providerName, 0) + added);
            logI("provider search done provider=" + result.providerName + " query=" + result.queryText + " added=" + added);
        }
        for (Map.Entry<String, Integer> entry : addedByProvider.entrySet()) logI("provider summary provider=" + entry.getKey() + " totalAdded=" + entry.getValue());
        return items;
    }

    public SubtitleAsset resolve(SubtitleCandidate candidate, SubtitleContext context) throws Exception {
        if (candidate == null) return null;
        SubtitleProvider provider = providers.get(candidate.getProvider());
        return provider == null ? null : provider.resolve(candidate, context);
    }

    private SearchResult searchOne(SubtitleProvider provider, SubtitleQuery query, SubtitleContext context) {
        try {
            logI("provider search start provider=" + provider.getName() + " query=" + query.getText() + " source=" + query.getSource());
            return SearchResult.success(provider.getName(), query.getText(), provider.search(query, context));
        } catch (Throwable error) {
            return SearchResult.failure(provider.getName(), query.getText(), error);
        }
    }

    private int addCandidates(List<SubtitleCandidate> items, Set<String> dedupe, List<SubtitleCandidate> candidates) {
        int before = items.size();
        if (candidates == null) return 0;
        for (SubtitleCandidate candidate : candidates) {
            if (candidate == null) continue;
            String key = candidate.getProvider() + "|" + candidate.getCandidateId();
            if (dedupe.add(key)) items.add(candidate);
        }
        return items.size() - before;
    }

    private SubtitleQuery firstQuery(List<SubtitleQuery> queries) {
        for (SubtitleQuery query : queries) if (query != null) return query;
        return null;
    }

    private static boolean isEmpty(CharSequence value) {
        return value == null || value.length() == 0;
    }

    private void logI(String message) {
        try {
            Log.i(TAG, message);
        } catch (Throwable ignored) {
        }
    }

    private void logW(String message, Throwable error) {
        try {
            Log.w(TAG, message, error);
        } catch (Throwable ignored) {
        }
    }

    private record SearchTask(SubtitleProvider provider, SubtitleQuery query) {
    }

    private static final class SearchResult {

        private final String providerName;
        private final String queryText;
        private final List<SubtitleCandidate> candidates;
        private final Throwable error;

        private SearchResult(String providerName, String queryText, List<SubtitleCandidate> candidates, Throwable error) {
            this.providerName = providerName;
            this.queryText = queryText;
            this.candidates = candidates;
            this.error = error;
        }

        private static SearchResult success(String providerName, String queryText, List<SubtitleCandidate> candidates) {
            return new SearchResult(providerName, queryText, candidates, null);
        }

        private static SearchResult failure(String providerName, String queryText, Throwable error) {
            return new SearchResult(providerName, queryText, null, error);
        }
    }
}
