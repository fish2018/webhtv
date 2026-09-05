package com.fongmi.android.tv.subtitle;

import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.subtitle.model.SubtitleAsset;
import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchResult;
import com.fongmi.android.tv.subtitle.model.SubtitleRequest;
import com.fongmi.android.tv.subtitle.provider.SubtitleProviderRegistry;
import com.fongmi.android.tv.utils.Task;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

public final class SubtitleMatchService {

    private static final String TAG = "SubtitleMatch";

    public interface Listener {
        void onComplete(SubtitleRequest request, SubtitleMatchResult result);
    }

    private final SubtitleContextBuilder contextBuilder;
    private final SubtitleQueryPlanner queryPlanner;
    private final SubtitleProviderRegistry registry;
    private final SubtitleRanker ranker;
    private final Map<String, Future<?>> jobs;
    private final Map<String, Integer> generations;

    public SubtitleMatchService() {
        this(new SubtitleContextBuilder(), new SubtitleQueryPlanner(), new SubtitleProviderRegistry(), new SubtitleRanker());
    }

    SubtitleMatchService(SubtitleContextBuilder contextBuilder, SubtitleQueryPlanner queryPlanner, SubtitleProviderRegistry registry, SubtitleRanker ranker) {
        this.contextBuilder = contextBuilder;
        this.queryPlanner = queryPlanner;
        this.registry = registry;
        this.ranker = ranker;
        this.jobs = new ConcurrentHashMap<>();
        this.generations = new ConcurrentHashMap<>();
    }

    public void autoMatch(SubtitleRequest request, Listener listener) {
        if (request == null) return;
        if (request.isManualOnly()) {
            Log.i(TAG, "auto skipped reason=manual_only title=" + request.getVodName());
            dispatch(listener, request, SubtitleMatchResult.skipped("manual_only"));
            return;
        }
        if (registry.enabledProviders().isEmpty()) {
            Log.w(TAG, "auto skipped reason=provider_unavailable title=" + request.getVodName());
            dispatch(listener, request, SubtitleMatchResult.skipped("provider_unavailable"));
            return;
        }
        Log.i(TAG, "auto start title=" + request.getVodName() + " episode=" + request.getEpisodeName() + " trigger=" + request.getTrigger());
        String playbackKey = request.getPlaybackKey();
        cancel(playbackKey);
        int generation = generations.merge(playbackKey, 1, Integer::sum);
        Future<?> job = Task.searchExecutor().submit(() -> runAutoMatch(request, generation, listener));
        jobs.put(playbackKey, job);
    }

    public void manualSearch(SubtitleRequest request, Listener listener) {
        manualSearch(request, "", listener);
    }

    public void manualSearch(SubtitleRequest request, String keyword, Listener listener) {
        if (request == null) return;
        if (registry.enabledProviders().isEmpty()) {
            Log.w(TAG, "manual skipped reason=provider_unavailable title=" + request.getVodName());
            dispatch(listener, request, SubtitleMatchResult.skipped("provider_unavailable"));
            return;
        }
        String text = keyword == null ? "" : keyword.trim();
        Log.i(TAG, "manual start title=" + request.getVodName() + " episode=" + request.getEpisodeName() + " keyword=" + text);
        Task.searchExecutor().submit(() -> {
            SubtitleMatchResult result;
            try {
                SubtitleContext context = contextBuilder.build(request);
                List<com.fongmi.android.tv.subtitle.model.SubtitleQuery> queries = TextUtils.isEmpty(text) ? queryPlanner.build(context) : queryPlanner.buildManual(context, text);
                Log.i(TAG, "manual context title=" + context.getCanonicalTitle() + " original=" + context.getOriginalTitle() + " year=" + context.getYear() + " tmdb=" + context.hasTmdbIdentity() + " custom=" + !TextUtils.isEmpty(text) + " queries=" + queries.size());
                List<SubtitleCandidate> ranked = ranker.rank(registry.search(queries, context), context);
                Log.i(TAG, "manual ranked count=" + ranked.size() + " best=" + best(ranked));
                result = SubtitleMatchResult.noMatch(ranked, ranked.isEmpty() ? "empty_result" : "manual_candidates");
            } catch (Throwable e) {
                Log.w(TAG, "manual error " + e.getMessage(), e);
                result = SubtitleMatchResult.error(e.getMessage());
            }
            dispatch(listener, request, result);
        });
    }

    public void resolve(SubtitleRequest request, SubtitleCandidate candidate, Listener listener) {
        if (request == null || candidate == null) return;
        Log.i(TAG, "resolve start provider=" + candidate.getProvider() + " id=" + candidate.getCandidateId() + " name=" + candidate.getDisplayName());
        Task.searchExecutor().submit(() -> {
            SubtitleMatchResult result;
            try {
                SubtitleContext context = contextBuilder.build(request);
                SubtitleAsset asset = registry.resolve(candidate, context);
                Log.i(TAG, "resolve result matched=" + (asset != null) + " provider=" + candidate.getProvider() + " id=" + candidate.getCandidateId());
                result = asset == null ? SubtitleMatchResult.noMatch(List.of(candidate), "resolve_failed") : SubtitleMatchResult.matched(candidate, asset, List.of(candidate));
            } catch (Throwable e) {
                Log.w(TAG, "resolve error provider=" + candidate.getProvider() + " id=" + candidate.getCandidateId() + " error=" + e.getMessage(), e);
                result = SubtitleMatchResult.error(e.getMessage());
            }
            dispatch(listener, request, result);
        });
    }

    public void cancel(String playbackKey) {
        if (playbackKey == null) return;
        generations.merge(playbackKey, 1, Integer::sum);
        Future<?> job = jobs.remove(playbackKey);
        if (job != null) job.cancel(true);
    }

    private void runAutoMatch(SubtitleRequest request, int generation, Listener listener) {
        SubtitleMatchResult result;
        try {
            SubtitleContext context = contextBuilder.build(request);
            result = matchContext(context, "auto");
            if (result.getStatus() == com.fongmi.android.tv.subtitle.model.SubtitleMatchStatus.NO_MATCH) {
                SubtitleContext fallback = contextBuilder.buildWithAiFallback(request);
                Log.i(TAG, "auto ai fallback title=" + fallback.getCanonicalTitle() + " sourceTitle=" + context.getCanonicalTitle());
                result = matchContext(fallback, "auto ai");
            }
        } catch (Throwable e) {
            Log.w(TAG, "auto error " + e.getMessage(), e);
            result = SubtitleMatchResult.error(e.getMessage());
        }
        if (!isCurrent(request.getPlaybackKey(), generation)) return;
        jobs.remove(request.getPlaybackKey());
        dispatch(listener, request, result);
    }

    private SubtitleMatchResult matchContext(SubtitleContext context, String phase) throws Exception {
        List<com.fongmi.android.tv.subtitle.model.SubtitleQuery> queries = queryPlanner.build(context);
        Log.i(TAG, phase + " context title=" + context.getCanonicalTitle() + " original=" + context.getOriginalTitle() + " year=" + context.getYear() + " tmdb=" + context.hasTmdbIdentity() + " queries=" + queries.size());
        List<SubtitleCandidate> ranked = ranker.rank(registry.search(queries, context), context);
        SubtitleCandidate selected = ranker.pickBest(ranked, context);
        Log.i(TAG, phase + " ranked count=" + ranked.size() + " selected=" + best(selected) + " best=" + best(ranked));
        if (selected == null) return SubtitleMatchResult.noMatch(ranked, ranked.isEmpty() ? "empty_result" : "below_threshold");
        SubtitleAsset asset = registry.resolve(selected, context);
        Log.i(TAG, phase + " resolve matched=" + (asset != null) + " selected=" + best(selected));
        return asset == null ? SubtitleMatchResult.noMatch(ranked, "resolve_failed") : SubtitleMatchResult.matched(selected, asset, ranked);
    }

    private boolean isCurrent(String playbackKey, int generation) {
        return generation == generations.getOrDefault(playbackKey, 0);
    }

    private void dispatch(Listener listener, SubtitleRequest request, SubtitleMatchResult result) {
        if (listener == null) return;
        if (result != null) Log.i(TAG, "dispatch status=" + result.getStatus() + " reason=" + result.getReason() + " candidates=" + result.getCandidates().size());
        App.post(() -> listener.onComplete(request, result));
    }

    private String best(List<SubtitleCandidate> ranked) {
        return ranked == null || ranked.isEmpty() ? "" : best(ranked.get(0));
    }

    private String best(SubtitleCandidate candidate) {
        if (candidate == null) return "";
        return candidate.getProvider() + ":" + candidate.getCandidateId() + ":" + candidate.getScore() + ":" + candidate.getDisplayName();
    }
}
