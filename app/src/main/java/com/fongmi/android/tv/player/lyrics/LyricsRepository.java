package com.fongmi.android.tv.player.lyrics;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.LyricsSetting;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class LyricsRepository {

    public interface Callback {
        void onResult(LyricsResult result);
    }

    public interface SearchCallback {
        void onResult(List<LyricsResult> results, boolean complete);
    }

    private static final String TAG = "lyrics";
    private static final long AUTO_LOAD_TIMEOUT_MS = 7000;
    private static final long AUTO_LOAD_SLOW_GRACE_MS = 1200;
    private final LrcLibClient client = new LrcLibClient();
    private final KuwoClient kuwo = new KuwoClient();
    private final KugouClient kugou = new KugouClient();
    private final MiguClient migu = new MiguClient();
    private final QqMusicClient qqMusic = new QqMusicClient();
    private final NeteaseClient netease = new NeteaseClient();
    private final TtmlClient ttml = new TtmlClient();
    private final LyricsMatcher matcher = new LyricsMatcher();

    public void load(LyricsRequest request, Callback callback) {
        load(request, false, callback);
    }

    public void loadPreferWord(LyricsRequest request, Callback callback) {
        load(request, true, callback);
    }

    public void loadPreferWord(LyricsRequest request, boolean forceRefresh, Callback callback) {
        load(request, true, forceRefresh, callback);
    }

    public void search(LyricsRequest request, SearchCallback callback) {
        Task.execute(() -> {
            int sourceMode = LyricsSetting.getSourceMode();
            List<LyricsResult> cached = readSearchCache(request, sourceMode);
            if (!cached.isEmpty()) postSearch(callback, cached, false);
            try {
                if (sourceMode == LyricsSetting.SOURCE_AUTO) searchAuto(request, sourceMode, cached, callback);
                else {
                    List<LyricsResult> results = searchSync(request);
                    if (results.isEmpty() && !cached.isEmpty()) results = cached;
                    writeSearchCache(request, sourceMode, results);
                    postSearch(callback, results, true);
                }
            } catch (Throwable e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "search failed title=%s error=%s", request.getTitle(), e.getMessage());
                postSearch(callback, List.of(), true);
            }
        });
    }

    public void remember(LyricsRequest request, LyricsResult result) {
        if (request == null || result == null || !result.isValid()) return;
        writeChoice(request, result);
        writeCache(request, LyricsSetting.getSourceMode(), result);
    }

    public boolean hasChoice(LyricsRequest request) {
        LyricsResult choice = readChoice(request);
        return choice != null && choice.isValid() && choice.isCacheCurrent();
    }

    private void load(LyricsRequest request, boolean preferWord, Callback callback) {
        load(request, preferWord, false, callback);
    }

    private void load(LyricsRequest request, boolean preferWord, boolean forceRefresh, Callback callback) {
        if (preferWord && !forceRefresh && LyricsSetting.getSourceMode() == LyricsSetting.SOURCE_AUTO) {
            loadProgressive(request, callback);
            return;
        }
        Task.execute(() -> {
            LyricsResult result = null;
            try {
                result = loadSync(request, preferWord, forceRefresh);
            } catch (Throwable e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "load failed title=%s error=%s", request.getTitle(), e.getMessage());
            }
            LyricsResult finalResult = result;
            App.post(() -> callback.onResult(finalResult));
        });
    }

    private void loadProgressive(LyricsRequest request, Callback callback) {
        Task.execute(() -> {
            int sourceMode = LyricsSetting.getSourceMode();
            LyricsResult early = null;
            LyricsResult result = null;
            try {
                early = loadQuickSync(request);
                if (early != null && early.isValid()) {
                    LyricsResult finalEarly = early;
                    App.post(() -> callback.onResult(finalEarly));
                }
            } catch (Throwable e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "quick load failed title=%s error=%s", request.getTitle(), e.getMessage());
            }
            try {
                result = sourceMode == LyricsSetting.SOURCE_AUTO ? loadAutoSourcesProgressive(request, sourceMode, early, callback) : loadSync(request, true, false);
            } catch (Throwable e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "progressive load failed title=%s error=%s", request.getTitle(), e.getMessage());
            }
            LyricsResult finalResult = result;
            if (sourceMode != LyricsSetting.SOURCE_AUTO && shouldNotifyProgress(request, early, finalResult)) App.post(() -> callback.onResult(finalResult));
            else if ((early == null || !early.isValid()) && (finalResult == null || !finalResult.isValid())) App.post(() -> callback.onResult(null));
        });
    }

    private LyricsResult loadQuickSync(LyricsRequest request) {
        int sourceMode = LyricsSetting.getSourceMode();
        LyricsResult choice = readChoice(request);
        if (isTrustedWord(choice)) return choice;
        LyricsResult local = readLocal(request);
        if (local != null && local.isValid() && local.hasWordTiming()) {
            writeCache(request, sourceMode, local);
            return local;
        }
        LyricsResult cached = readCache(request, sourceMode);
        if (cached != null && cached.isValid() && cached.isCacheCurrent() && isTrustedWord(cached)) return cached;
        if (choice != null && choice.isValid() && choice.isCacheCurrent()) return choice;
        if (local != null && local.isValid()) {
            writeCache(request, sourceMode, local);
            return local;
        }
        if (cached != null && cached.isValid() && cached.isCacheCurrent()) return cached;
        if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "quick miss title=%s artist=%s parse=%s", request.getTitle(), request.getArtist(), request.getParseInfo());
        return null;
    }

    private boolean shouldNotifyProgress(LyricsRequest request, LyricsResult early, LyricsResult result) {
        if (result == null || !result.isValid()) return false;
        if (early == null || !early.isValid()) return true;
        if (sameResult(early, result)) return false;
        if (result.hasWordTiming() && !early.hasWordTiming()) return true;
        return weightedScore(request, result) > weightedScore(request, early) + 10;
    }

    private LyricsResult loadSync(LyricsRequest request, boolean preferWord, boolean forceRefresh) {
        int sourceMode = LyricsSetting.getSourceMode();
        LyricsResult choice = forceRefresh ? null : readChoice(request);
        if (choice != null && choice.isValid() && choice.isCacheCurrent() && (!preferWord || isTrustedWord(choice))) return choice;
        LyricsResult cached = forceRefresh ? null : readCache(request, sourceMode);
        if (cached != null && cached.isValid() && cached.isCacheCurrent() && (!preferWord || isTrustedWord(cached))) return cached;
        LyricsResult remote = choice != null && choice.isValid() && choice.isCacheCurrent() ? choice : null;
        if (cached != null && cached.isValid() && cached.isCacheCurrent() && shouldUseRemote(request, remote, cached)) remote = cached;
        LyricsResult local = readLocal(request);
        if (sourceMode != LyricsSetting.SOURCE_AUTO) return loadSource(request, sourceMode, local);
        if (local != null && local.isValid()) {
            if (!preferWord || local.hasWordTiming()) {
                writeCache(request, sourceMode, local);
                return local;
            }
            if (shouldUseRemote(request, remote, local)) remote = local;
        }
        remote = loadAutoSources(request, sourceMode, remote);
        if (remote != null && remote.isValid()) writeCache(request, sourceMode, remote);
        if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "match title=%s artist=%s parse=%s result=%s score=%d word=%s", request.getTitle(), request.getArtist(), request.getParseInfo(), remote == null ? "none" : remote.getSource(), remote == null ? 0 : remote.getScore(), remote != null && remote.hasWordTiming());
        return remote;
    }

    private LyricsResult loadAutoSources(LyricsRequest request, int sourceMode, LyricsResult seed) {
        ArrayList<LyricsResult> results = new ArrayList<>();
        ArrayList<SearchFuture> futures = new ArrayList<>();
        add(results, seed);
        addSearch(futures, "AMLL TTML", () -> one(ttml.find(request)));
        addSearch(futures, "Kuwo", () -> one(kuwo.find(request)));
        addSearch(futures, "QQMusic", () -> one(qqMusic.find(request)));
        addSearch(futures, "Netease", () -> one(netease.find(request)));
        addSearch(futures, "Kugou", () -> one(kugou.find(request)));
        addSearch(futures, "Migu", () -> one(migu.find(request)));
        addSearch(futures, "LRCLIB", () -> one(matcher.best(request, client.findCandidates(request))));
        collectSearch(results, futures, 18000, "load");
        List<LyricsResult> sorted = sorted(request, results, 24);
        if (SpiderDebug.isEnabled()) logCandidates(request, "load", sorted);
        return sorted.isEmpty() ? null : sorted.get(0);
    }

    private LyricsResult loadAutoSourcesProgressive(LyricsRequest request, int sourceMode, LyricsResult seed, Callback callback) {
        ArrayList<LyricsResult> results = new ArrayList<>();
        ArrayList<SearchFuture> futures = new ArrayList<>();
        add(results, seed);
        addSearch(futures, "AMLL TTML", () -> one(ttml.find(request)));
        addSearch(futures, "Kuwo", () -> one(kuwo.find(request)));
        addSearch(futures, "QQMusic", () -> one(qqMusic.find(request)));
        addSearch(futures, "Netease", () -> one(netease.find(request)));
        addSearch(futures, "Kugou", () -> one(kugou.find(request)));
        addSearch(futures, "Migu", () -> one(migu.find(request)));
        addSearch(futures, "LRCLIB", () -> one(matcher.best(request, client.findCandidates(request))));
        LyricsResult notified = seed != null && seed.isValid() ? seed : null;
        LyricsResult best = notified;
        long deadline = System.currentTimeMillis() + AUTO_LOAD_TIMEOUT_MS;
        long slowDeadline = Long.MAX_VALUE;
        while (!futures.isEmpty() && System.currentTimeMillis() < deadline) {
            boolean changed = false;
            for (int i = futures.size() - 1; i >= 0; i--) {
                SearchFuture item = futures.get(i);
                if (!item.future.isDone()) continue;
                futures.remove(i);
                try {
                    List<LyricsResult> done = item.future.get(1, TimeUnit.MILLISECONDS);
                    addAll(results, done);
                    if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "load source=%s done count=%d cost=%dms", item.source, done == null ? 0 : done.size(), System.currentTimeMillis() - item.startMs);
                    changed = true;
                } catch (Throwable e) {
                    if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "load source=%s failed error=%s", item.source, e.getMessage());
                }
            }
            if (changed) {
                List<LyricsResult> sorted = sorted(request, results, 24);
                LyricsResult candidate = sorted.isEmpty() ? null : sorted.get(0);
                if (candidate != null && candidate.isValid()) {
                    best = candidate;
                    if (slowDeadline == Long.MAX_VALUE) slowDeadline = System.currentTimeMillis() + AUTO_LOAD_SLOW_GRACE_MS;
                }
                if (shouldNotifyProgress(request, notified, candidate)) {
                    notified = candidate;
                    writeCache(request, sourceMode, candidate);
                    LyricsResult finalCandidate = candidate;
                    App.post(() -> callback.onResult(finalCandidate));
                    if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "load early title=%s artist=%s parse=%s result=%s score=%d word=%s", request.getTitle(), request.getArtist(), request.getParseInfo(), candidate.getSource(), candidate.getScore(), candidate.hasWordTiming());
                }
            }
            if (best != null && best.isValid() && onlySlowFallbackPending(futures) && System.currentTimeMillis() >= slowDeadline) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "load stop slow fallback pending title=%s artist=%s pending=%s", request.getTitle(), request.getArtist(), pendingSources(futures));
                break;
            }
            if (!changed) sleepSearch();
        }
        for (SearchFuture item : futures) {
            item.future.cancel(true);
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "load source=%s timeout", item.source);
        }
        List<LyricsResult> sorted = sorted(request, results, 24);
        if (SpiderDebug.isEnabled()) logCandidates(request, "load", sorted);
        LyricsResult finalBest = sorted.isEmpty() ? best : sorted.get(0);
        if (finalBest != null && finalBest.isValid()) writeCache(request, sourceMode, finalBest);
        if (shouldNotifyProgress(request, notified, finalBest)) {
            LyricsResult finalResult = finalBest;
            App.post(() -> callback.onResult(finalResult));
        }
        if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "match title=%s artist=%s parse=%s result=%s score=%d word=%s", request.getTitle(), request.getArtist(), request.getParseInfo(), finalBest == null ? "none" : finalBest.getSource(), finalBest == null ? 0 : finalBest.getScore(), finalBest != null && finalBest.hasWordTiming());
        return finalBest;
    }

    private boolean onlySlowFallbackPending(List<SearchFuture> futures) {
        if (futures == null || futures.isEmpty()) return false;
        for (SearchFuture item : futures) if (!isSlowFallback(item.source)) return false;
        return true;
    }

    private boolean isSlowFallback(String source) {
        return "LRCLIB".equals(source) || "AMLL TTML".equals(source);
    }

    private String pendingSources(List<SearchFuture> futures) {
        StringBuilder builder = new StringBuilder();
        for (SearchFuture item : futures) {
            if (builder.length() > 0) builder.append(',');
            builder.append(item.source);
        }
        return builder.toString();
    }

    private List<LyricsResult> searchSync(LyricsRequest request) {
        int sourceMode = LyricsSetting.getSourceMode();
        ArrayList<LyricsResult> results = new ArrayList<>();
        ArrayList<SearchFuture> futures = new ArrayList<>();
        LyricsResult local = readLocal(request);
        switch (sourceMode) {
            case LyricsSetting.SOURCE_LOCAL -> add(results, local);
            case LyricsSetting.SOURCE_TTML -> add(results, ttml.find(request));
            case LyricsSetting.SOURCE_QQ -> addAll(results, qqMusic.findAll(request, 8));
            case LyricsSetting.SOURCE_NETEASE -> addAll(results, netease.findAll(request, 8));
            case LyricsSetting.SOURCE_KUWO -> addAll(results, kuwo.findAll(request, 8));
            case LyricsSetting.SOURCE_LRCLIB -> addAll(results, matcher.all(request, client.findCandidates(request), 8));
            case LyricsSetting.SOURCE_KUGOU -> addAll(results, kugou.findAll(request, 8));
            case LyricsSetting.SOURCE_MIGU -> addAll(results, migu.findAll(request, 8));
            default -> {
                add(results, local);
                addSearch(futures, "QQMusic", () -> qqMusic.findAll(request, 4));
                addSearch(futures, "AMLL TTML", () -> one(ttml.find(request)));
                addSearch(futures, "Netease", () -> netease.findAll(request, 4));
                addSearch(futures, "Kuwo", () -> kuwo.findAll(request, 4));
                addSearch(futures, "Kugou", () -> kugou.findAll(request, 4));
                addSearch(futures, "Migu", () -> migu.findAll(request, 4));
                addSearch(futures, "LRCLIB", () -> matcher.all(request, client.findCandidates(request), 6));
                collectSearch(results, futures);
            }
        }
        return sorted(request, results, 24);
    }

    private void searchAuto(LyricsRequest request, int sourceMode, List<LyricsResult> cached, SearchCallback callback) {
        ArrayList<LyricsResult> results = new ArrayList<>(cached == null ? List.of() : cached);
        ArrayList<SearchFuture> futures = new ArrayList<>();
        add(results, readLocal(request));
        if (!results.isEmpty() && (cached == null || cached.isEmpty())) postSearch(callback, sorted(request, results, 24), false);
        addSearch(futures, "QQMusic", () -> qqMusic.findAll(request, 4));
        addSearch(futures, "AMLL TTML", () -> one(ttml.find(request)));
        addSearch(futures, "Netease", () -> netease.findAll(request, 4));
        addSearch(futures, "Kuwo", () -> kuwo.findAll(request, 4));
        addSearch(futures, "Kugou", () -> kugou.findAll(request, 4));
        addSearch(futures, "Migu", () -> migu.findAll(request, 4));
        addSearch(futures, "LRCLIB", () -> matcher.all(request, client.findCandidates(request), 6));
        collectSearchProgressive(request, sourceMode, results, futures, callback);
    }

    private LyricsResult loadSource(LyricsRequest request, int sourceMode, LyricsResult local) {
        LyricsResult result = switch (sourceMode) {
            case LyricsSetting.SOURCE_LOCAL -> local;
            case LyricsSetting.SOURCE_TTML -> ttml.find(request);
            case LyricsSetting.SOURCE_QQ -> qqMusic.find(request);
            case LyricsSetting.SOURCE_NETEASE -> netease.find(request);
            case LyricsSetting.SOURCE_KUWO -> kuwo.find(request);
            case LyricsSetting.SOURCE_LRCLIB -> matcher.best(request, client.findCandidates(request));
            case LyricsSetting.SOURCE_KUGOU -> kugou.find(request);
            case LyricsSetting.SOURCE_MIGU -> migu.find(request);
            default -> null;
        };
        if (result != null && result.isValid()) writeCache(request, sourceMode, result);
        if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "match sourceMode=%d title=%s artist=%s parse=%s result=%s score=%d", sourceMode, request.getTitle(), request.getArtist(), request.getParseInfo(), result == null ? "none" : result.getSource(), result == null ? 0 : result.getScore());
        return result;
    }

    private boolean shouldUseRemote(LyricsRequest request, LyricsResult current, LyricsResult remote) {
        if (remote == null || !remote.isValid()) return false;
        if (current == null || !current.isValid()) return true;
        if (remote.hasWordTiming() && !current.hasWordTiming()) return true;
        if (remote.hasWordTiming() && current.hasWordTiming()) {
            int remotePriority = wordPriority(remote) + sourceAffinity(request, remote);
            int currentPriority = wordPriority(current) + sourceAffinity(request, current);
            if (remotePriority > currentPriority && remote.getScore() >= current.getScore() - 15) return true;
            if (remotePriority < currentPriority && remote.getScore() <= current.getScore() + 15) return false;
        }
        return weightedScore(request, remote) > weightedScore(request, current) + 10;
    }

    private boolean isTrustedWord(LyricsResult result) {
        return result != null && result.isValid() && result.hasWordTiming() && wordPriority(result) >= 30;
    }

    private int weightedScore(LyricsResult result) {
        return weightedScore(null, result);
    }

    private int weightedScore(LyricsRequest request, LyricsResult result) {
        if (result == null) return 0;
        int quality = result.hasWordTiming() ? 80 + wordPriority(result) : result.isSynced() ? 18 : 0;
        int match = request == null ? 0 : LyricsMatcher.matchScore(request, result.getTrackName(), result.getArtistName(), result.getDurationMs() / 1000.0);
        return result.getScore() + quality + sourceAffinity(request, result) + match;
    }

    private int wordPriority(LyricsResult result) {
        String source = result == null || result.getSource() == null ? "" : result.getSource();
        if (source.contains("Local TTML")) return 60;
        if (source.contains("AMLL TTML")) return 50;
        if (source.contains("Kuwo")) return 48;
        if (source.contains("QQMusic")) return 45;
        if (source.contains("Netease")) return 42;
        if (source.contains("Kugou KRC")) return 38;
        if (source.contains("Migu MRC")) return 36;
        if (source.contains("Kugou")) return 8;
        if (source.contains("Local")) return 30;
        return 0;
    }

    private int sourceAffinity(LyricsRequest request, LyricsResult result) {
        if (request == null || result == null || TextUtils.isEmpty(result.getSource())) return 0;
        String source = result.getSource().toLowerCase(Locale.ROOT);
        String text = (safe(request.getKey()) + " " + safe(request.getUrl())).toLowerCase(Locale.ROOT);
        if (source.contains("kuwo") && containsAny(text, "kuwo", "kuwo.cn", "酷我", "kw")) return 24;
        if (source.contains("qqmusic") && containsAny(text, "qqmusic", "qq.com", "y.qq.com", "qq音乐", "tencent")) return 22;
        if (source.contains("netease") && containsAny(text, "netease", "music.163", "163.com", "网易", "ncm")) return 20;
        if (source.contains("kugou") && containsAny(text, "kugou", "酷狗", "kg")) return 18;
        if (source.contains("migu") && containsAny(text, "migu", "咪咕")) return 16;
        if (source.contains("amll") && containsAny(text, "amll", "ttml")) return 14;
        return 0;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) if (!TextUtils.isEmpty(value) && text.contains(value)) return true;
        return false;
    }

    private void addSearch(List<SearchFuture> futures, String source, SearchAction action) {
        long start = System.currentTimeMillis();
        futures.add(new SearchFuture(source, Task.largeExecutor().submit(() -> {
            try {
                return action.run();
            } catch (Throwable e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "search source=%s failed error=%s", source, e.getMessage());
                return List.of();
            }
        }), start));
    }

    private void collectSearch(List<LyricsResult> results, List<SearchFuture> futures) {
        collectSearch(results, futures, 22000, "search");
    }

    private void collectSearch(List<LyricsResult> results, List<SearchFuture> futures, long timeoutMs, String operation) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!futures.isEmpty() && System.currentTimeMillis() < deadline) {
            boolean changed = false;
            for (int i = futures.size() - 1; i >= 0; i--) {
                SearchFuture item = futures.get(i);
                if (!item.future.isDone()) continue;
                futures.remove(i);
                try {
                    List<LyricsResult> done = item.future.get(1, TimeUnit.MILLISECONDS);
                    addAll(results, done);
                    if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "%s source=%s done count=%d cost=%dms", operation, item.source, done == null ? 0 : done.size(), System.currentTimeMillis() - item.startMs);
                    changed = true;
                } catch (Throwable e) {
                    if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "%s source=%s failed error=%s", operation, item.source, e.getMessage());
                }
            }
            if (!changed) sleepSearch();
        }
        for (SearchFuture item : futures) {
            item.future.cancel(true);
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "%s source=%s timeout", operation, item.source);
        }
    }

    private void collectSearchProgressive(LyricsRequest request, int sourceMode, List<LyricsResult> results, List<SearchFuture> futures, SearchCallback callback) {
        long deadline = System.currentTimeMillis() + 22000;
        String lastKey = resultListKey(sorted(request, results, 24));
        while (!futures.isEmpty() && System.currentTimeMillis() < deadline) {
            boolean changed = false;
            for (int i = futures.size() - 1; i >= 0; i--) {
                SearchFuture item = futures.get(i);
                if (!item.future.isDone()) continue;
                futures.remove(i);
                try {
                    List<LyricsResult> done = item.future.get();
                    addAll(results, done);
                    if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "search source=%s done count=%d cost=%dms", item.source, done == null ? 0 : done.size(), System.currentTimeMillis() - item.startMs);
                    changed = true;
                } catch (Throwable e) {
                    if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "search source=%s failed error=%s", item.source, e.getMessage());
                }
            }
            List<LyricsResult> sorted = sorted(request, results, 24);
            String key = resultListKey(sorted);
            if (changed && !TextUtils.equals(key, lastKey)) {
                lastKey = key;
                postSearch(callback, sorted, false);
            }
            if (!changed) sleepSearch();
        }
        for (SearchFuture item : futures) {
            item.future.cancel(true);
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "search source=%s timeout", item.source);
        }
        List<LyricsResult> sorted = sorted(request, results, 24);
        if (SpiderDebug.isEnabled()) logCandidates(request, "search", sorted);
        writeSearchCache(request, sourceMode, sorted);
        postSearch(callback, sorted, true);
    }

    private String resultListKey(List<LyricsResult> results) {
        StringBuilder builder = new StringBuilder();
        for (LyricsResult result : results) builder.append(resultKey(result)).append('#').append(result.getScore()).append('#').append(result.hasWordTiming()).append(';');
        return builder.toString();
    }

    private void sleepSearch() {
        try {
            Thread.sleep(120);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void postSearch(SearchCallback callback, List<LyricsResult> results, boolean complete) {
        List<LyricsResult> finalResults = results == null ? List.of() : new ArrayList<>(results);
        App.post(() -> callback.onResult(finalResults, complete));
    }

    private List<LyricsResult> one(LyricsResult result) {
        ArrayList<LyricsResult> results = new ArrayList<>();
        add(results, result);
        return results;
    }

    private void add(List<LyricsResult> results, LyricsResult result) {
        if (result != null && result.isValid()) results.add(result);
    }

    private void addAll(List<LyricsResult> results, List<LyricsResult> items) {
        if (items == null) return;
        for (LyricsResult item : items) add(results, item);
    }

    private List<LyricsResult> sorted(List<LyricsResult> items, int limit) {
        return sorted(null, items, limit);
    }

    private List<LyricsResult> sorted(LyricsRequest request, List<LyricsResult> items, int limit) {
        ArrayList<LyricsResult> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        ArrayList<LyricsResult> candidates = new ArrayList<>(items == null ? List.of() : items);
        candidates.sort(Comparator.comparingInt((LyricsResult result) -> weightedScore(request, result)).reversed());
        for (LyricsResult item : candidates) {
            if (request != null && !LyricsMatcher.isAcceptableMatch(request, item)) continue;
            if (!seen.add(resultKey(item))) continue;
            results.add(item);
            if (results.size() >= limit) break;
        }
        return results;
    }

    private void logCandidates(LyricsRequest request, String operation, List<LyricsResult> results) {
        if (!SpiderDebug.isEnabled()) return;
        StringBuilder builder = new StringBuilder();
        int count = Math.min(results == null ? 0 : results.size(), 8);
        for (int i = 0; i < count; i++) {
            LyricsResult item = results.get(i);
            if (i > 0) builder.append(", ");
            builder.append(item.getSource()).append(':').append(item.getScore()).append('/').append(weightedScore(request, item)).append(item.hasWordTiming() ? "/word" : item.isSynced() ? "/sync" : "/plain");
        }
        SpiderDebug.log(TAG, "%s ranked title=%s artist=%s parse=%s results=[%s]", operation, request.getTitle(), request.getArtist(), request.getParseInfo(), builder);
    }

    private String resultKey(LyricsResult result) {
        long duration = Math.round(result.getDurationMs() / 1000.0);
        return String.join("|",
                safe(result.getSource()),
                LyricsMatcher.normalize(result.getTrackName()),
                LyricsMatcher.normalize(result.getArtistName()),
                String.valueOf(duration));
    }

    private boolean sameResult(LyricsResult first, LyricsResult second) {
        if (first == null || second == null) return first == second;
        return TextUtils.equals(resultKey(first), resultKey(second)) && TextUtils.equals(first.getLyrics(), second.getLyrics());
    }

    private String safe(String text) {
        return text == null ? "" : text.trim();
    }

    private LyricsResult readCache(LyricsRequest request, int sourceMode) {
        for (File file : cacheFiles(request, sourceMode)) {
            LyricsResult result = readResultFile(file);
            if (result == null || !LyricsMatcher.isAcceptableMatch(request, result)) continue;
            if (!sameFile(file, cacheFile(request, sourceMode))) writeCache(request, sourceMode, result);
            return result;
        }
        return null;
    }

    private List<LyricsResult> readSearchCache(LyricsRequest request, int sourceMode) {
        for (File file : searchCacheFiles(request, sourceMode)) {
            if (!Path.exists(file)) continue;
            ArrayList<LyricsResult> results = new ArrayList<>();
            try {
                LyricsResult[] items = App.gson().fromJson(Path.read(file), LyricsResult[].class);
                if (items != null) for (LyricsResult item : items) if (item != null && item.isValid() && item.isCacheCurrent() && LyricsMatcher.isAcceptableMatch(request, item)) results.add(item);
            } catch (Exception e) {
                continue;
            }
            if (!sameFile(file, searchCacheFile(request, sourceMode))) writeSearchCache(request, sourceMode, results);
            return sorted(request, results, 24);
        }
        return List.of();
    }

    private void writeSearchCache(LyricsRequest request, int sourceMode, List<LyricsResult> results) {
        try {
            Path.write(searchCacheFile(request, sourceMode), App.gson().toJson(results == null ? List.of() : results).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private void writeCache(LyricsRequest request, int sourceMode, LyricsResult result) {
        try {
            Path.write(cacheFile(request, sourceMode), App.gson().toJson(result).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private LyricsResult readChoice(LyricsRequest request) {
        LyricsResult result = readResultFile(choiceFile(request));
        if (LyricsMatcher.isAcceptableMatch(request, result)) return result;
        result = readResultFile(legacyChoiceFile(request));
        if (LyricsMatcher.isAcceptableMatch(request, result)) {
            writeChoice(request, result);
            return result;
        }
        result = readMatchingChoice(request);
        if (result != null) writeChoice(request, result);
        return result;
    }

    private void writeChoice(LyricsRequest request, LyricsResult result) {
        try {
            Path.write(choiceFile(request), App.gson().toJson(result).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private LyricsResult readResultFile(File file) {
        if (!Path.exists(file)) return null;
        try {
            return App.gson().fromJson(Path.read(file), LyricsResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    private LyricsResult readMatchingChoice(LyricsRequest request) {
        File[] files = choiceDir().listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) return null;
        LyricsResult best = null;
        int bestScore = Integer.MIN_VALUE;
        for (File file : files) {
            LyricsResult result = readResultFile(file);
            if (result == null || !result.isValid() || !result.isCacheCurrent() || !choiceMatches(request, result)) continue;
            int score = weightedScore(request, result);
            if (best == null || score > bestScore) {
                best = result;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean choiceMatches(LyricsRequest request, LyricsResult result) {
        String requestTitle = LyricsMatcher.normalize(request.getTitle());
        String requestArtist = LyricsMatcher.normalize(request.getArtist());
        String resultTitle = LyricsMatcher.normalize(result.getTrackName());
        String resultArtist = LyricsMatcher.normalize(result.getArtistName());
        boolean directTitle = relatedText(requestTitle, resultTitle);
        boolean directArtist = TextUtils.isEmpty(requestArtist) || TextUtils.isEmpty(resultArtist) || relatedText(requestArtist, resultArtist);
        if (directTitle && directArtist) return true;
        return !TextUtils.isEmpty(requestArtist) && !TextUtils.isEmpty(resultArtist) && relatedText(requestTitle, resultArtist) && relatedText(requestArtist, resultTitle);
    }

    private boolean relatedText(String first, String second) {
        if (TextUtils.isEmpty(first) || TextUtils.isEmpty(second)) return false;
        return first.equals(second) || first.contains(second) || second.contains(first);
    }

    private LyricsResult readLocal(LyricsRequest request) {
        File source = sourceFile(request.getUrl());
        if (source == null) return null;
        File parent = source.getParentFile();
        if (parent == null) return null;
        List<String> bases = localBases(request, source);
        File ttml = findLocal(parent, bases, ".ttml");
        if (Path.exists(ttml)) {
            String text = TtmlClient.toEnhancedLrc(Path.read(ttml));
            if (!TextUtils.isEmpty(text) && LyricsParser.hasTimedLine(text)) return new LyricsResult("Local TTML", request.getTitle(), request.getArtist(), request.getAlbum(), text, request.getDurationMs(), true, 104);
        }
        File lrc = findLocal(parent, bases, ".lrc");
        if (!Path.exists(lrc)) return null;
        String text = Path.read(lrc);
        if (TextUtils.isEmpty(text)) return null;
        boolean synced = LyricsParser.hasTimedLine(text);
        return new LyricsResult("Local", request.getTitle(), request.getArtist(), request.getAlbum(), text, request.getDurationMs(), synced, 100);
    }

    private List<String> localBases(LyricsRequest request, File source) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        String sourceBase = fileBase(source.getName());
        String title = request.getTitle();
        String artist = request.getArtist();
        addLocalBase(values, sourceBase);
        addLocalBase(values, title);
        addLocalBase(values, request.displayKeyword());
        if (!TextUtils.isEmpty(artist) && !TextUtils.isEmpty(title)) {
            addLocalBase(values, artist + " - " + title);
            addLocalBase(values, title + " - " + artist);
            addLocalBase(values, artist + "_" + title);
            addLocalBase(values, title + "." + sourceBase);
            addLocalBase(values, sourceBase + "." + title);
        }
        return new ArrayList<>(values);
    }

    private void addLocalBase(Set<String> values, String value) {
        String text = safe(value).trim();
        if (TextUtils.isEmpty(text)) return;
        values.add(text);
        String safe = text.replaceAll("[\\\\/:*?\"<>|]+", " ").replaceAll("\\s+", " ").trim();
        if (!TextUtils.isEmpty(safe)) values.add(safe);
    }

    private File findLocal(File parent, List<String> bases, String suffix) {
        for (String base : bases) {
            File file = new File(parent, base + suffix);
            if (Path.exists(file)) return file;
            file = new File(parent, base + suffix.toUpperCase(Locale.ROOT));
            if (Path.exists(file)) return file;
        }
        File[] files = parent.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (!file.isFile()) continue;
            String name = file.getName();
            if (!name.toLowerCase(Locale.ROOT).endsWith(suffix)) continue;
            String base = fileBase(name);
            for (String value : bases) if (sameLocalBase(base, value)) return file;
        }
        return null;
    }

    private boolean sameLocalBase(String a, String b) {
        return normalizeLocalBase(a).equals(normalizeLocalBase(b));
    }

    private String normalizeLocalBase(String value) {
        return Normalizer.normalize(safe(value), Normalizer.Form.NFKC)
                .replace('　', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String fileBase(String name) {
        String value = safe(name);
        int dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private File sourceFile(String url) {
        try {
            if (TextUtils.isEmpty(url)) return null;
            url = url.replace("clan://", "file://tvbox/");
            if (url.startsWith("file://")) return new File(Uri.parse(url).getPath());
            if (url.startsWith("/")) return new File(url);
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private File cacheFile(LyricsRequest request, int sourceMode) {
        File dir = Path.cache("lyrics");
        if (!dir.exists()) dir.mkdirs();
        String suffix = LyricsSetting.cacheSuffix(sourceMode);
        return new File(dir, request.stableSignature() + (suffix.isEmpty() ? "" : "-" + suffix) + ".json");
    }

    private File legacyCacheFile(LyricsRequest request, int sourceMode) {
        File dir = Path.cache("lyrics");
        if (!dir.exists()) dir.mkdirs();
        String suffix = LyricsSetting.cacheSuffix(sourceMode);
        return new File(dir, request.signature() + (suffix.isEmpty() ? "" : "-" + suffix) + ".json");
    }

    private List<File> cacheFiles(LyricsRequest request, int sourceMode) {
        ArrayList<File> files = new ArrayList<>();
        addFile(files, cacheFile(request, sourceMode));
        addFile(files, legacyCacheFile(request, sourceMode));
        return files;
    }

    private File searchCacheFile(LyricsRequest request, int sourceMode) {
        File dir = Path.cache("lyrics");
        if (!dir.exists()) dir.mkdirs();
        String suffix = LyricsSetting.cacheSuffix(sourceMode);
        return new File(dir, request.searchSignature() + (suffix.isEmpty() ? "" : "-" + suffix) + "-search.json");
    }

    private File legacySearchCacheFile(LyricsRequest request, int sourceMode) {
        File dir = Path.cache("lyrics");
        if (!dir.exists()) dir.mkdirs();
        String suffix = LyricsSetting.cacheSuffix(sourceMode);
        return new File(dir, request.signature() + (suffix.isEmpty() ? "" : "-" + suffix) + "-search.json");
    }

    private List<File> searchCacheFiles(LyricsRequest request, int sourceMode) {
        ArrayList<File> files = new ArrayList<>();
        addFile(files, searchCacheFile(request, sourceMode));
        addFile(files, legacySearchCacheFile(request, sourceMode));
        return files;
    }

    private File choiceFile(LyricsRequest request) {
        return new File(choiceDir(), request.stableSignature() + ".json");
    }

    private File legacyChoiceFile(LyricsRequest request) {
        return new File(choiceDir(), request.signature() + ".json");
    }

    private static File choiceDir() {
        File dir = new File(cacheDir(), "choices");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void addFile(List<File> files, File file) {
        if (file == null) return;
        for (File item : files) if (sameFile(item, file)) return;
        files.add(file);
    }

    private boolean sameFile(File first, File second) {
        if (first == null || second == null) return first == second;
        return TextUtils.equals(first.getAbsolutePath(), second.getAbsolutePath());
    }

    public static int cacheCount() {
        File[] files = cacheDir().listFiles((dir, name) -> name.endsWith(".json"));
        return files == null ? 0 : files.length;
    }

    public static int clearCache() {
        File dir = cacheDir();
        int count = cacheCount();
        Path.clear(dir);
        if (!dir.exists()) dir.mkdirs();
        return count;
    }

    private static File cacheDir() {
        return Path.cache("lyrics");
    }

    private interface SearchAction {
        List<LyricsResult> run();
    }

    private static class SearchFuture {
        private final String source;
        private final Future<List<LyricsResult>> future;
        private final long startMs;

        private SearchFuture(String source, Future<List<LyricsResult>> future, long startMs) {
            this.source = source;
            this.future = future;
            this.startMs = startMs;
        }
    }
}
