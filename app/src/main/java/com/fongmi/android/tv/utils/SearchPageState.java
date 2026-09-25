package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Vod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SearchPageState {

    private final Map<String, Integer> pages = new HashMap<>();
    private final Map<String, Integer> pageCounts = new HashMap<>();
    private final Map<String, Set<String>> pageTokens = new HashMap<>();
    private final Set<String> exhaustedSites = new HashSet<>();
    private Request pending;

    public void clear() {
        pages.clear();
        pageCounts.clear();
        pageTokens.clear();
        exhaustedSites.clear();
        pending = null;
    }

    public void cancelPending() {
        pending = null;
    }

    public void recordInitial(String siteKey, int pageCount) {
        recordInitial(siteKey, pageCount, "");
    }

    public void recordInitial(String siteKey, int pageCount, String pageToken) {
        if (!isSite(siteKey)) return;
        pages.putIfAbsent(siteKey, 1);
        recordPageCount(siteKey, pageCount);
        recordPageToken(siteKey, pageToken);
    }

    public int getPage(String siteKey) {
        return Math.max(1, pages.getOrDefault(siteKey, 1));
    }

    public int getPageCount(String siteKey) {
        return Math.max(0, pageCounts.getOrDefault(siteKey, 0));
    }

    public boolean begin(String siteKey, int requestedPage) {
        if (!isSite(siteKey) || hasPending() || !canLoadMore(siteKey)) return false;
        if (requestedPage != getPage(siteKey) + 1) return false;
        pending = new Request(siteKey, requestedPage);
        return true;
    }

    public Completion complete(String resultSiteKey, boolean hasItems, int pageCount) {
        return complete(resultSiteKey, hasItems, pageCount, "");
    }

    public Completion complete(String resultSiteKey, boolean hasItems, int pageCount, String pageToken) {
        if (pending == null) return Completion.none();
        Request request = pending;
        pending = null;
        boolean sameSite = !hasItems || request.siteKey.equals(resultSiteKey);
        if (sameSite) recordPageCount(request.siteKey, pageCount);
        boolean repeated = hasItems && sameSite && !recordPageToken(request.siteKey, pageToken);
        if (repeated) exhaustedSites.add(request.siteKey);
        boolean accepted = hasItems && sameSite && !repeated;
        if (accepted) pages.put(request.siteKey, request.page);
        return new Completion(true, accepted, request.siteKey, request.page);
    }

    public boolean hasPending() {
        return pending != null;
    }

    public boolean isPending(String siteKey) {
        return pending != null && pending.siteKey.equals(siteKey);
    }

    public boolean canLoadMore(String siteKey) {
        if (!isSite(siteKey) || exhaustedSites.contains(siteKey)) return false;
        int pageCount = getPageCount(siteKey);
        return pageCount == 0 || getPage(siteKey) < pageCount;
    }

    public boolean shouldContinue(String siteKey, boolean precise, boolean rawPageHasItems, boolean visiblePageHasItems) {
        return precise && rawPageHasItems && !visiblePageHasItems && !hasPending() && canLoadMore(siteKey);
    }

    private void recordPageCount(String siteKey, int pageCount) {
        if (pageCount > 0) pageCounts.merge(siteKey, pageCount, Math::max);
    }

    private boolean recordPageToken(String siteKey, String pageToken) {
        if (pageToken == null || pageToken.isEmpty()) return true;
        return pageTokens.computeIfAbsent(siteKey, key -> new HashSet<>()).add(pageToken);
    }

    public static String pageToken(List<Vod> items) {
        if (items == null || items.isEmpty()) return "";
        List<String> entries = new ArrayList<>(items.size());
        for (Vod item : items) entries.add(itemToken(item));
        Collections.sort(entries);
        StringBuilder token = new StringBuilder();
        for (String entry : entries) token.append(entry.length()).append(':').append(entry);
        return token.toString();
    }

    private static String itemToken(Vod item) {
        if (item == null) return "0:0:0:";
        StringBuilder token = new StringBuilder();
        appendToken(token, item.getSiteKey());
        appendToken(token, item.getId());
        appendToken(token, item.getName());
        return token.toString();
    }

    private static void appendToken(StringBuilder token, String value) {
        String text = Objects.toString(value, "");
        token.append(text.length()).append(':').append(text);
    }

    private boolean isSite(String siteKey) {
        return siteKey != null && !siteKey.isEmpty() && !"all".equals(siteKey);
    }

    private static final class Request {

        private final String siteKey;
        private final int page;

        private Request(String siteKey, int page) {
            this.siteKey = siteKey;
            this.page = page;
        }
    }

    public static final class Completion {

        private static final Completion NONE = new Completion(false, false, "", 0);

        private final boolean handled;
        private final boolean accepted;
        private final String siteKey;
        private final int page;

        private Completion(boolean handled, boolean accepted, String siteKey, int page) {
            this.handled = handled;
            this.accepted = accepted;
            this.siteKey = siteKey;
            this.page = page;
        }

        private static Completion none() {
            return NONE;
        }

        public boolean handled() {
            return handled;
        }

        public boolean accepted() {
            return accepted;
        }

        public String siteKey() {
            return siteKey;
        }

        public int page() {
            return page;
        }
    }
}
