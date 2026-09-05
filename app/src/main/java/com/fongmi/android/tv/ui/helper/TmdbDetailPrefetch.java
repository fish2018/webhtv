package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

final class TmdbDetailPrefetch {

    private final ListeningExecutorService executor;
    private Identity identity;
    private ListenableFuture<Result> future;

    TmdbDetailPrefetch(ListeningExecutorService executor) {
        this.executor = executor;
    }

    synchronized StartResult start(TmdbItem item, Callable<Result> task) {
        Identity next = Identity.from(item);
        if (next == null) return null;
        if (next.equals(identity) && future != null && !future.isCancelled()) {
            return new StartResult(StartState.REUSED, future);
        }
        StartState state = future == null ? StartState.STARTED : StartState.REPLACED;
        cancelLocked();
        identity = next;
        future = executor.submit(task);
        return new StartResult(state, future);
    }

    synchronized ListenableFuture<Result> take(TmdbItem item) {
        Identity expected = Identity.from(item);
        if (expected == null || !expected.equals(identity)) return null;
        ListenableFuture<Result> result = future;
        identity = null;
        future = null;
        return result;
    }

    synchronized void cancel() {
        cancelLocked();
    }

    private void cancelLocked() {
        if (future != null) future.cancel(true);
        identity = null;
        future = null;
    }

    enum StartState {
        STARTED,
        REUSED,
        REPLACED
    }

    static final class StartResult {

        private final StartState state;
        private final ListenableFuture<Result> future;

        StartResult(StartState state, ListenableFuture<Result> future) {
            this.state = state;
            this.future = future;
        }

        StartState getState() {
            return state;
        }

        ListenableFuture<Result> getFuture() {
            return future;
        }
    }

    static final class Result {

        private final TmdbItem item;
        private final JsonObject detail;
        private final List<TmdbPerson> cast;

        Result(TmdbItem item, JsonObject detail, List<TmdbPerson> cast) {
            this.item = item;
            this.detail = detail;
            this.cast = cast == null ? new ArrayList<>() : new ArrayList<>(cast);
        }

        TmdbItem getItem() {
            return item;
        }

        JsonObject getDetail() {
            return detail;
        }

        List<TmdbPerson> getCast() {
            return new ArrayList<>(cast);
        }
    }

    private static final class Identity {

        private final int id;
        private final String mediaType;

        private Identity(int id, String mediaType) {
            this.id = id;
            this.mediaType = normalize(mediaType);
        }

        static Identity from(TmdbItem item) {
            return item == null || item.getTmdbId() <= 0 ? null : new Identity(item.getTmdbId(), item.getMediaType());
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Identity)) return false;
            Identity other = (Identity) obj;
            return id == other.id && mediaType.equals(other.mediaType);
        }

        @Override
        public int hashCode() {
            return 31 * id + mediaType.hashCode();
        }
    }
}
