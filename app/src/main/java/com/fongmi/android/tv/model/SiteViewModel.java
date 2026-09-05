package com.fongmi.android.tv.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.player.karaoke.KaraokeResult;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteBlockSetting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.utils.Task;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class SiteViewModel extends ViewModel {

    private final MutableLiveData<Result> result;
    private final MutableLiveData<Result> player;
    private final MutableLiveData<Result> search;
    private final MutableLiveData<SearchProgress> searchProgress;
    private final MutableLiveData<Result> action;

    private final Map<TaskType, ListenableFuture<?>> futures;
    private final Map<TaskType, AtomicInteger> taskIds;
    private final List<Future<?>> searchFuture;
    private final ListeningExecutorService playerExecutor;
    private final AtomicInteger searchEpoch;
    private final Object searchLock;
    private ListeningExecutorService searchExecutor;
    private KaraokeResult karaokeResult;
    private int karaokeResultAction;

    public SiteViewModel() {
        result = new MutableLiveData<>();
        player = new MutableLiveData<>();
        search = new MutableLiveData<>();
        searchProgress = new MutableLiveData<>();
        action = new MutableLiveData<>();
        searchEpoch = new AtomicInteger(0);
        searchFuture = new CopyOnWriteArrayList<>();
        searchLock = new Object();
        // Player spiders can share a loopback proxy and may ignore interruption.
        // Keep resolutions serial so a canceled source fully exits before the next starts.
        playerExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        futures = new EnumMap<>(TaskType.class);
        taskIds = new EnumMap<>(TaskType.class);
        for (TaskType type : TaskType.values()) taskIds.put(type, new AtomicInteger(0));
    }

    public LiveData<Result> getResult() {
        return result;
    }

    public LiveData<Result> getPlayer() {
        return player;
    }

    public LiveData<Result> getSearch() {
        return search;
    }

    public LiveData<SearchProgress> getSearchProgress() {
        return searchProgress;
    }

    public LiveData<Result> getAction() {
        return action;
    }

    public KaraokeResult getKaraokeResult() {
        return karaokeResult;
    }

    public int getKaraokeResultAction() {
        return karaokeResultAction;
    }

    public void setKaraokeResult(KaraokeResult result, int action) {
        karaokeResult = result;
        karaokeResultAction = action;
    }

    public void clearKaraokeResult() {
        karaokeResult = null;
        karaokeResultAction = 0;
    }

    public SiteViewModel init() {
        search.setValue(null);
        searchProgress.setValue(null);
        result.setValue(null);
        player.setValue(null);
        action.setValue(null);
        return this;
    }

    public void homeContent() {
        execute(TaskType.RESULT, result, () -> SiteApi.homeContent(VodConfig.get().getHome()));
    }

    public void categoryContent(String key, String tid, String page, boolean filter, HashMap<String, String> extend) {
        execute(TaskType.RESULT, result, () -> SiteApi.categoryContent(key, tid, page, filter, extend));
    }

    public void action(String key, String act) {
        execute(TaskType.ACTION, action, () -> SiteApi.action(key, act));
    }

    public void detailContent(String key, String id) {
        detailContent(key, id, false);
    }

    public void detailContent(String key, String id, boolean refresh) {
        execute(TaskType.RESULT, result, () -> SiteApi.detailContent(key, id, refresh));
    }

    public void playerContent(String key, String flag, String id) {
        execute(TaskType.PLAYER, player, () -> SiteApi.playerContent(key, flag, id));
    }

    /** 按指定内核取播放地址：取址要按内核区分线路，所以内核必须显式传入而不是读全局默认。 */
    public void playerContent(String key, String flag, String id, int playerType) {
        execute(TaskType.PLAYER, player, () -> SiteApi.playerContent(key, flag, id, playerType));
    }

    /**
     * 换条目时使旧取流请求失效；LiveData 消费方只接受下一次请求或空值。
     */
    public void cancelPlayerContent() {
        taskIds.get(TaskType.PLAYER).incrementAndGet();
        ListenableFuture<?> future = futures.get(TaskType.PLAYER);
        if (future != null) future.cancel(true);
        player.setValue(null);
        futures.remove(TaskType.PLAYER);
    }

    public void searchContent(Site site, String keyword, boolean quick, String page) {
        long start = System.currentTimeMillis();
        execute(TaskType.RESULT, result, SearchTask.create(site, keyword, quick, page),
                result -> SiteHealthStore.recordSearch(site, true, result.getList().size(), System.currentTimeMillis() - start, ""),
                error -> SiteHealthStore.recordSearch(site, false, 0, System.currentTimeMillis() - start, error.getMessage()));
    }

    public void searchContent(List<Site> sites, String keyword, boolean quick) {
        synchronized (searchLock) {
            int epoch = stopSearchLocked();
            List<Site> tasks = new ArrayList<>();
            for (Site site : sites) {
                if (quick && !site.isQuickSearch()) continue;
                tasks.add(site);
            }
            int total = tasks.size();
            AtomicInteger completed = new AtomicInteger();
            searchProgress.postValue(SearchProgress.start(total));
            // A site spider may ignore interruption after its timeout. Isolate every
            // generation so an uncooperative old worker cannot starve the next search;
            // shutdownNow() remains best-effort because the JVM cannot forcibly stop it.
            ListeningExecutorService executor = searchExecutor = Task.newSearchExecutor(Setting.getSearchThread());
            for (Site site : tasks) {
                long start = System.currentTimeMillis();
                FluentFuture<Result> future = FluentFuture.from(executor.submit(SearchTask.create(site, keyword, quick))).withTimeout(Constant.TIMEOUT_SEARCH, TimeUnit.MILLISECONDS, Task.scheduler());
                searchFuture.add(future);
                future.addCallback(Task.callback(
                        result -> {
                            if (searchEpoch.get() != epoch) return;
                            SiteHealthStore.recordSearch(site, true, result.getList().size(), System.currentTimeMillis() - start, "");
                            postSearchResult(epoch, result);
                            postSearchProgress(epoch, completed, total);
                        },
                        error -> {
                            if (searchEpoch.get() != epoch) return;
                            if (error instanceof CancellationException) return;
                            SiteHealthStore.recordSearch(site, false, 0, System.currentTimeMillis() - start, error.getMessage());
                            postSearchProgress(epoch, completed, total);
                            error.printStackTrace();
                        }
                ), MoreExecutors.directExecutor());
            }
        }
    }

    private void postSearchResult(int epoch, Result result) {
        App.post(() -> {
            if (searchEpoch.get() == epoch) search.setValue(result);
        });
    }

    private void postSearchProgress(int epoch, AtomicInteger completed, int total) {
        if (searchEpoch.get() != epoch) return;
        searchProgress.postValue(SearchProgress.of(completed.incrementAndGet(), total));
    }

    private void execute(TaskType type, MutableLiveData<Result> liveData, Callable<Result> callable) {
        execute(type, liveData, callable, null, null);
    }

    private void execute(TaskType type, MutableLiveData<Result> liveData, Callable<Result> callable, Consumer<Result> onSuccess, Consumer<Throwable> onError) {
        AtomicInteger taskId = Objects.requireNonNull(taskIds.get(type));
        int currentId = taskId.incrementAndGet();
        ListenableFuture<?> old = futures.get(type);
        if (old != null) old.cancel(true);
        ListeningExecutorService executor = type == TaskType.PLAYER ? playerExecutor : Task.executor();
        FluentFuture<Result> future = FluentFuture.from(executor.submit(callable)).withTimeout(Constant.TIMEOUT_VOD, TimeUnit.MILLISECONDS, Task.scheduler());
        futures.put(type, future);
        future.addCallback(Task.callback(
                result -> {
                    if (taskId.get() != currentId) return;
                    if (type == TaskType.PLAYER) App.post(() -> {
                        if (taskId.get() != currentId) return;
                        if (onSuccess != null) onSuccess.accept(result);
                        liveData.setValue(result);
                    });
                    else {
                        if (onSuccess != null) onSuccess.accept(result);
                        liveData.postValue(result);
                    }
                },
                error -> {
                    if (taskId.get() != currentId) return;
                    if (error instanceof CancellationException) return;
                    Result failure = error instanceof ExtractException ? Result.error(error.getMessage()) : Result.empty();
                    if (type == TaskType.PLAYER) App.post(() -> {
                        if (taskId.get() != currentId) return;
                        if (onError != null) onError.accept(error);
                        liveData.setValue(failure);
                    });
                    else {
                        if (onError != null) onError.accept(error);
                        liveData.postValue(failure);
                    }
                    error.printStackTrace();
                }
        ), MoreExecutors.directExecutor());
    }

    public int stopSearch() {
        synchronized (searchLock) {
            return stopSearchLocked();
        }
    }

    private int stopSearchLocked() {
        int epoch = searchEpoch.incrementAndGet();
        searchFuture.forEach(future -> future.cancel(true));
        searchFuture.clear();
        if (searchExecutor != null) {
            searchExecutor.shutdownNow();
            searchExecutor = null;
        }
        return epoch;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopSearch();
        futures.values().forEach(future -> future.cancel(true));
        playerExecutor.shutdownNow();
    }

    private enum TaskType {RESULT, PLAYER, ACTION}
}
