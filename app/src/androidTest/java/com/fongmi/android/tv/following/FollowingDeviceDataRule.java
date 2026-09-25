package com.fongmi.android.tv.following;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.util.ArrayList;
import java.util.List;

/** Preserves the real device following database around tests that use it as a fixture store. */
public final class FollowingDeviceDataRule extends TestWatcher {

    private Snapshot snapshot = Snapshot.empty();

    @Override
    protected void starting(Description description) {
        snapshot = Snapshot.capture();
    }

    @Override
    protected void finished(Description description) {
        snapshot.restore();
    }

    private static final class Snapshot {
        private final List<Following> following;
        private final List<FollowingSource> sources;

        private Snapshot(List<Following> following, List<FollowingSource> sources) {
            this.following = following;
            this.sources = sources;
        }

        static Snapshot empty() {
            return new Snapshot(List.of(), List.of());
        }

        static Snapshot capture() {
            List<Following> following = new ArrayList<>();
            for (Following item : FollowingStore.database().getFollowingDao().findAll()) {
                if (item != null) following.add(item.copy());
            }
            List<FollowingSource> sources = new ArrayList<>();
            for (FollowingSource source : FollowingStore.database().getFollowingSourceDao().findAll()) {
                if (source != null) sources.add(source.copy());
            }
            return new Snapshot(following, sources);
        }

        void restore() {
            FollowingStore.replaceAll(following, sources);
        }
    }
}
