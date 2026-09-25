package com.fongmi.android.tv.following;

import java.util.ArrayList;
import java.util.List;

public final class FollowingBackupCodec {

    private FollowingBackupCodec() {
    }

    public static Payload capture() {
        return new Payload(FollowingStore.list(), FollowingStore.database().getFollowingSourceDao().findAll());
    }

    public static void restoreFull(List<Following> following, List<FollowingSource> sources) {
        FollowingStore.replaceAll(safe(following), safe(sources));
    }

    public static void merge(List<Following> following, List<FollowingSource> sources) {
        FollowingStore.mergeAll(safe(following), safe(sources));
    }

    private static <T> List<T> safe(List<T> items) {
        return items == null ? new ArrayList<>() : items;
    }

    public record Payload(List<Following> following, List<FollowingSource> sources) {
    }
}
