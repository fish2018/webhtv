package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchResult;
import com.fongmi.android.tv.subtitle.model.SubtitleRequest;
import com.fongmi.android.tv.utils.Task;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class SubtitleAutoController {

    public interface Callback {
        void onSubtitleResult(SubtitleRequest request, SubtitleMatchResult result);
    }

    private final SubtitleMatchService matchService;
    private final Callback callback;
    private final Map<String, ScheduledFuture<?>> scheduled;
    private final Map<String, Integer> generations;

    public SubtitleAutoController(Callback callback) {
        this(new SubtitleMatchService(), callback);
    }

    SubtitleAutoController(SubtitleMatchService matchService, Callback callback) {
        this.matchService = matchService;
        this.callback = callback;
        this.scheduled = new ConcurrentHashMap<>();
        this.generations = new ConcurrentHashMap<>();
    }

    public void onPlaybackStarted(SubtitleRequest request) {
        schedule(request, 0L);
    }

    public void onEpisodeChanged(SubtitleRequest request) {
        schedule(request, 250L);
    }

    public void onSourceChanged(SubtitleRequest request) {
        schedule(request, 250L);
    }

    public void onStop(String playbackKey) {
        if (playbackKey == null) return;
        generations.merge(playbackKey, 1, Integer::sum);
        ScheduledFuture<?> future = scheduled.remove(playbackKey);
        if (future != null) future.cancel(false);
        matchService.cancel(playbackKey);
    }

    private void schedule(SubtitleRequest request, long delayMs) {
        if (request == null || request.getPlaybackKey().isEmpty()) return;
        if (!Setting.isSubtitleAutoMatchEnabled()) return;
        String playbackKey = request.getPlaybackKey();
        ScheduledFuture<?> old = scheduled.remove(playbackKey);
        if (old != null) old.cancel(false);
        int generation = generations.merge(playbackKey, 1, Integer::sum);
        ScheduledFuture<?> future = Task.scheduler().schedule(() -> {
            if (generation != generations.getOrDefault(playbackKey, 0)) return;
            matchService.autoMatch(request, (currentRequest, result) -> {
                if (generation != generations.getOrDefault(playbackKey, 0)) return;
                callback.onSubtitleResult(currentRequest, result);
            });
        }, delayMs, TimeUnit.MILLISECONDS);
        scheduled.put(playbackKey, future);
    }
}
