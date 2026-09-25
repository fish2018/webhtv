package com.fongmi.android.tv.setting;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure state for one VOD interface failover round. */
public final class InterfaceFailoverState {

    private final int mode;
    private final List<String> candidates;
    private final Set<String> attempted = new LinkedHashSet<>();
    private int nextIndex;
    private boolean cancelled;
    private boolean selected;

    public InterfaceFailoverState(int mode, @Nullable String originUrl, @NonNull List<String> candidateUrls) {
        this.mode = InterfaceFailoverPolicy.clampMode(mode);
        this.candidates = candidates(candidateUrls);
        if (originUrl != null && !originUrl.isEmpty()) attempted.add(originUrl);
    }

    @Nullable
    public String nextAutomatic() {
        if (cancelled || selected || !InterfaceFailoverPolicy.isAuto(mode)) return null;
        while (nextIndex < candidates.size()) {
            String url = candidates.get(nextIndex++);
            if (attempted.add(url)) return url;
        }
        return null;
    }

    @Nullable
    public String select(int index) {
        if (cancelled || selected || !InterfaceFailoverPolicy.isConfirm(mode)
                || index < 0 || index >= candidates.size()) return null;
        selected = true;
        nextIndex = candidates.size();
        String url = candidates.get(index);
        return attempted.add(url) ? url : null;
    }

    public boolean shouldContinueAfterFailure() {
        return !cancelled && !selected && InterfaceFailoverPolicy.isAuto(mode)
                && nextIndex < candidates.size();
    }

    public void cancel() {
        cancelled = true;
    }

    @NonNull
    public List<String> candidates() {
        return candidates;
    }

    @NonNull
    private static List<String> candidates(@NonNull List<String> values) {
        int limit = InterfaceFailoverPolicy.fallbackLimit(values.size() + 1);
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isEmpty() && unique.size() < limit) unique.add(value);
        }
        return Collections.unmodifiableList(new ArrayList<>(unique));
    }
}
