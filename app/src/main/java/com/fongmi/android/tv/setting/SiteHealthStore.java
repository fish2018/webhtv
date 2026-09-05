package com.fongmi.android.tv.setting;

import android.graphics.Color;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.AdBlockStatsStore;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SiteHealthStore {

    private static final String KEY = "site_health";
    private static final long SAVE_DELAY = 2000;
    private static final long RETAIN_MS = TimeUnit.DAYS.toMillis(90);
    private static final int COLOR_GOOD = Color.parseColor("#0B8043");
    private static final int COLOR_WARN = Color.parseColor("#FFD54F");
    private static final int COLOR_BAD = Color.parseColor("#FF5252");
    private static final int COLOR_UNKNOWN = Color.parseColor("#80FFFFFF");
    private static final String REASON_EMPTY_RESULT = "EMPTY_RESULT";
    private static final Type TYPE = new TypeToken<Map<String, Health>>() {}.getType();
    private static final Runnable SAVE = () -> Task.execute(SiteHealthStore::saveNow);

    private static final Map<String, Health> items = new LinkedHashMap<>();
    private static boolean loaded;
    private static boolean dirty;

    public static void recordSearch(Site site, boolean success, int count, long cost, String error) {
        if (skip(site)) return;
        synchronized (SiteHealthStore.class) {
            Health health = get(site.getKey());
            health.updatedAt = System.currentTimeMillis();
            health.lastSearchCost = Math.max(0, cost);
            health.lastSearchCount = Math.max(0, count);
            if (success) {
                health.searchSuccess++;
                health.lastSearchSuccessAt = health.updatedAt;
                if (count <= 0) {
                    health.searchEmpty++;
                    health.addSearchReason(REASON_EMPTY_RESULT);
                    health.lastSearchError = REASON_EMPTY_RESULT;
                }
            } else {
                health.searchFail++;
                health.lastSearchFailAt = health.updatedAt;
                health.lastSearchError = trim(error);
                health.addSearchReason(reason("SEARCH", error));
            }
            markDirty();
        }
    }

    public static void recordDetail(String key, boolean success, long cost, String error) {
        if (skip(key)) return;
        synchronized (SiteHealthStore.class) {
            Health health = get(key);
            health.updatedAt = System.currentTimeMillis();
            health.lastDetailCost = Math.max(0, cost);
            if (success) {
                health.detailSuccess++;
                health.lastDetailSuccessAt = health.updatedAt;
            } else {
                health.detailFail++;
                health.lastDetailFailAt = health.updatedAt;
                health.lastDetailError = trim(error);
                health.addDetailReason(reason("DETAIL", error));
            }
            markDirty();
        }
    }

    public static void recordParse(String key, boolean success, long cost, String error) {
        if (skip(key)) return;
        synchronized (SiteHealthStore.class) {
            Health health = get(key);
            health.updatedAt = System.currentTimeMillis();
            health.lastParseCost = Math.max(0, cost);
            if (success) {
                health.parseSuccess++;
                health.lastParseSuccessAt = health.updatedAt;
            } else {
                health.parseFail++;
                health.lastParseFailAt = health.updatedAt;
                health.lastParseError = trim(error);
                health.addParseReason(reason("PARSE", error));
            }
            markDirty();
        }
    }

    public static void recordPlay(String key, boolean success, String error) {
        if (skip(key)) return;
        synchronized (SiteHealthStore.class) {
            Health health = get(key);
            health.updatedAt = System.currentTimeMillis();
            if (success) {
                health.playSuccess++;
                health.lastPlaySuccessAt = health.updatedAt;
            } else {
                health.playFail++;
                health.lastPlayFailAt = health.updatedAt;
                health.lastPlayError = trim(error);
                health.addPlayReason(reason("PLAY", error));
            }
            markDirty();
        }
    }

    public static void sortSites(List<Site> sites) {
        if (!Setting.isSiteHealthSort()) return;
        if (sites == null || sites.size() < 2) return;
        sites.sort((a, b) -> Double.compare(score(b.getKey()), score(a.getKey())));
    }

    public static void sortVods(List<Vod> vods) {
        if (!Setting.isSiteHealthSort()) return;
        if (vods == null || vods.size() < 2) return;
        vods.sort(SiteHealthStore::compareVods);
    }

    public static int compareVods(Vod a, Vod b) {
        if (!Setting.isSiteHealthSort()) return 0;
        return Double.compare(score(b.getSiteKey()), score(a.getSiteKey()));
    }

    public static void clear() {
        synchronized (SiteHealthStore.class) {
            ensureLoaded();
            items.clear();
            dirty = false;
        }
        App.removeCallbacks(SAVE);
        Prefers.remove(KEY);
    }

    public static void clear(String siteKey) {
        if (skip(siteKey)) return;
        boolean changed;
        synchronized (SiteHealthStore.class) {
            ensureLoaded();
            changed = items.remove(key(siteKey)) != null;
            if (changed) dirty = true;
        }
        if (changed) flush();
    }

    public static Summary summary() {
        return report().summary;
    }

    public static Report report() {
        synchronized (SiteHealthStore.class) {
            ensureLoaded();
            String cid = String.valueOf(VodConfig.getCid());
            String prefix = cid + ":";
            Map<String, String> names = siteNames();
            List<Row> rows = new ArrayList<>();
            for (Map.Entry<String, Health> entry : items.entrySet()) {
                if (!entry.getKey().startsWith(prefix)) continue;
                Health health = entry.getValue();
                if (health == null || health.total() == 0) continue;
                String siteKey = entry.getKey().substring(prefix.length());
                rows.add(Row.from(siteKey, displayName(siteKey, names), health));
            }
            rows.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
            return new Report(cid, rows);
        }
    }

    public static int getColor(Site site) {
        Status status = getStatus(site);
        if (status == Status.GOOD) return COLOR_GOOD;
        if (status == Status.WARN) return COLOR_WARN;
        if (status == Status.BAD) return COLOR_BAD;
        return COLOR_UNKNOWN;
    }

    public static void flush() {
        App.removeCallbacks(SAVE);
        Task.execute(SiteHealthStore::saveNow);
    }

    private static Status getStatus(Site site) {
        if (skip(site)) return Status.UNKNOWN;
        synchronized (SiteHealthStore.class) {
            Health health = find(site.getKey());
            return health == null ? Status.UNKNOWN : health.status(calculateAdPenalty(site.getKey()));
        }
    }

    private static double score(String key) {
        if (skip(key)) return 0;
        synchronized (SiteHealthStore.class) {
            Health health = find(key);
            if (health == null) return 0;
            double baseScore = health.score();
            return baseScore - calculateAdPenalty(key);
        }
    }

    private static double calculateAdPenalty(String siteKey) {
        long adBlocked = AdBlockStatsStore.getSiteBlockedCount(siteKey);
        if (adBlocked > 5) {
            // 广告多：扣 8-15 分（对数衰减）
            return 8 + Math.min(7, Math.log1p(adBlocked - 5) * 2);
        } else if (adBlocked > 0) {
            // 广告少：扣 2-8 分（线性）
            return 2 + adBlocked * 1.2;
        }
        return 0;
    }

    private static Health get(String siteKey) {
        ensureLoaded();
        String key = key(siteKey);
        Health health = items.get(key);
        if (health == null) items.put(key, health = new Health());
        return health;
    }

    private static Health find(String siteKey) {
        ensureLoaded();
        return items.get(key(siteKey));
    }

    private static String key(String siteKey) {
        return VodConfig.getCid() + ":" + siteKey;
    }

    private static Map<String, String> siteNames() {
        Map<String, String> names = new LinkedHashMap<>();
        for (Site site : VodConfig.get().getSites()) {
            if (site == null || TextUtils.isEmpty(site.getKey())) continue;
            names.put(site.getKey(), site.getDisplayName());
        }
        return names;
    }

    private static String displayName(String siteKey, Map<String, String> names) {
        String name = names.get(siteKey);
        return TextUtils.isEmpty(name) ? siteKey : name;
    }

    private static boolean skip(Site site) {
        return site == null || skip(site.getKey());
    }

    private static boolean skip(String key) {
        return TextUtils.isEmpty(key) || SiteApi.PUSH.equals(key);
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Map<String, Health> restored = App.gson().fromJson(Prefers.getString(KEY), TYPE);
            if (restored != null) items.putAll(restored);
        } catch (Throwable ignored) {
        }
    }

    private static void markDirty() {
        dirty = true;
        App.post(SAVE, SAVE_DELAY);
    }

    private static void saveNow() {
        Map<String, Health> copy;
        synchronized (SiteHealthStore.class) {
            ensureLoaded();
            if (!dirty) return;
            prune();
            copy = new LinkedHashMap<>(items);
            dirty = false;
        }
        Prefers.put(KEY, App.gson().toJson(copy));
    }

    private static void prune() {
        long expire = System.currentTimeMillis() - RETAIN_MS;
        items.entrySet().removeIf(entry -> entry.getValue().updatedAt > 0 && entry.getValue().updatedAt < expire);
    }

    private static String trim(String text) {
        if (TextUtils.isEmpty(text)) return "";
        return text.length() <= 120 ? text : text.substring(0, 120);
    }

    private static String reason(String stage, String error) {
        if (TextUtils.isEmpty(error)) return stage + "_FAILED";
        String text = error.toLowerCase(Locale.ROOT);
        if (text.contains("empty") || text.contains("no result") || text.contains("not found") || text.contains("空")) return REASON_EMPTY_RESULT;
        if (text.contains("timeout") || text.contains("timed out") || text.contains("超时")) return "TIMEOUT";
        if (text.contains("403")) return "HTTP_403";
        if (text.contains("404")) return "HTTP_404";
        if (text.contains("429")) return "HTTP_429";
        if (text.contains("500") || text.contains("502") || text.contains("503") || text.contains("504")) return "HTTP_5XX";
        if (text.contains("connect") || text.contains("network") || text.contains("socket") || text.contains("refused") || text.contains("reset")) return "NETWORK";
        if (text.contains("parse") || text.contains("解析")) return "PARSE_FAILED";
        if (text.contains("vip") || text.contains("会员")) return "VIP_REQUIRED";
        if (text.contains("geo") || text.contains("region") || text.contains("地区")) return "GEO_BLOCKED";
        if (text.contains("drm") || text.contains("encrypted") || text.contains("加密")) return "DRM_OR_ENCRYPTED";
        if (text.contains("m3u8") || text.contains("manifest")) return "MANIFEST_FAILED";
        if (text.contains("codec") || text.contains("decoder") || text.contains("解码")) return "CODEC_UNSUPPORTED";
        if (text.contains("cors")) return "CORS_BLOCKED";
        return stage + "_FAILED";
    }

    public enum Status {GOOD, WARN, BAD, UNKNOWN}

    public static class Report {

        public final Summary summary;
        public final List<Row> rows;
        public final String cid;

        private Report(String cid, List<Row> rows) {
            this.cid = cid;
            this.rows = Collections.unmodifiableList(rows);
            this.summary = Summary.from(rows);
        }

        public boolean isEmpty() {
            return rows.isEmpty();
        }
    }

    public static class Summary {

        public final int siteCount;
        public final int sampleCount;
        public final int failureCount;
        public final int degradedCount;
        public final String topFailureReason;
        public final int topFailureCount;

        private Summary(int siteCount, int sampleCount, int failureCount, int degradedCount, String topFailureReason, int topFailureCount) {
            this.siteCount = siteCount;
            this.sampleCount = sampleCount;
            this.failureCount = failureCount;
            this.degradedCount = degradedCount;
            this.topFailureReason = topFailureReason;
            this.topFailureCount = topFailureCount;
        }

        private static Summary from(List<Row> rows) {
            int samples = 0;
            int failures = 0;
            int degraded = 0;
            Map<String, Integer> reasons = new LinkedHashMap<>();
            for (Row row : rows) {
                samples += row.sampleCount();
                failures += row.failureCount();
                if (row.status == Status.WARN || row.status == Status.BAD) degraded++;
                row.addReasonsTo(reasons);
            }
            String reason = topReason(reasons);
            return new Summary(rows.size(), samples, failures, degraded, reason, reasonCount(reasons, reason));
        }
    }

    public static class Row {

        public final String siteKey;
        public final String siteName;
        public final Status status;
        public final Stage search;
        public final Stage detail;
        public final Stage parse;
        public final Stage play;
        public final long updatedAt;

        private Row(String siteKey, String siteName, Status status, Stage search, Stage detail, Stage parse, Stage play, long updatedAt) {
            this.siteKey = siteKey;
            this.siteName = siteName;
            this.status = status;
            this.search = search;
            this.detail = detail;
            this.parse = parse;
            this.play = play;
            this.updatedAt = updatedAt;
        }

        private static Row from(String siteKey, String siteName, Health health) {
            double adPenalty = calculateAdPenalty(siteKey);
            return new Row(siteKey, siteName, health.status(adPenalty),
                    new Stage("SEARCH", Math.max(0, health.searchSuccess - health.searchEmpty), health.searchFail + health.searchEmpty, health.searchEmpty, health.lastSearchCost, health.lastSearchFailAt, health.lastSearchError, health.searchReasons),
                    new Stage("DETAIL", health.detailSuccess, health.detailFail, 0, health.lastDetailCost, health.lastDetailFailAt, health.lastDetailError, health.detailReasons),
                    new Stage("PARSE", health.parseSuccess, health.parseFail, 0, health.lastParseCost, health.lastParseFailAt, health.lastParseError, health.parseReasons),
                    new Stage("PLAY", health.playSuccess, health.playFail, 0, 0, health.lastPlayFailAt, health.lastPlayError, health.playReasons),
                    health.updatedAt);
        }

        public int sampleCount() {
            return search.sampleCount() + detail.sampleCount() + parse.sampleCount() + play.sampleCount();
        }

        public int failureCount() {
            return search.failureCount() + detail.failureCount() + parse.failureCount() + play.failureCount();
        }

        public String topFailureReason() {
            Map<String, Integer> reasons = new LinkedHashMap<>();
            addReasonsTo(reasons);
            return topReason(reasons);
        }

        public int topFailureCount() {
            Map<String, Integer> reasons = new LinkedHashMap<>();
            addReasonsTo(reasons);
            return reasonCount(reasons, topReason(reasons));
        }

        private void addReasonsTo(Map<String, Integer> reasons) {
            search.addReasonsTo(reasons);
            detail.addReasonsTo(reasons);
            parse.addReasonsTo(reasons);
            play.addReasonsTo(reasons);
        }
    }

    public static class Stage {

        public final String name;
        public final int success;
        public final int fail;
        public final int empty;
        public final long lastCost;
        public final long lastFailAt;
        public final String lastError;
        public final Map<String, Integer> reasons;

        private Stage(String name, int success, int fail, int empty, long lastCost, long lastFailAt, String lastError, Map<String, Integer> reasons) {
            this.name = name;
            this.success = success;
            this.fail = fail;
            this.empty = empty;
            this.lastCost = lastCost;
            this.lastFailAt = lastFailAt;
            this.lastError = TextUtils.isEmpty(lastError) ? "" : lastError;
            this.reasons = copyReasons(reasons);
        }

        public int sampleCount() {
            return success + fail;
        }

        public int failureCount() {
            return fail;
        }

        public int successRate() {
            int total = sampleCount();
            return total == 0 ? -1 : Math.round(success * 100f / total);
        }

        public String topReason() {
            return SiteHealthStore.topReason(reasons);
        }

        public int topReasonCount() {
            return reasonCount(reasons, topReason());
        }

        private void addReasonsTo(Map<String, Integer> target) {
            for (Map.Entry<String, Integer> entry : reasons.entrySet()) {
                target.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
    }

    private static Map<String, Integer> copyReasons(Map<String, Integer> reasons) {
        if (reasons == null || reasons.isEmpty()) return Collections.emptyMap();
        return Collections.unmodifiableMap(new LinkedHashMap<>(reasons));
    }

    private static String topReason(Map<String, Integer> reasons) {
        String top = "";
        int count = 0;
        if (reasons == null) return top;
        for (Map.Entry<String, Integer> entry : reasons.entrySet()) {
            int value = entry.getValue() == null ? 0 : entry.getValue();
            if (value <= count) continue;
            top = entry.getKey();
            count = value;
        }
        return top;
    }

    private static int reasonCount(Map<String, Integer> reasons, String reason) {
        if (TextUtils.isEmpty(reason) || reasons == null) return 0;
        Integer count = reasons.get(reason);
        return count == null ? 0 : count;
    }

    public static class Health {

        private int searchSuccess;
        private int searchFail;
        private int searchEmpty;
        private int detailSuccess;
        private int detailFail;
        private int parseSuccess;
        private int parseFail;
        private int playSuccess;
        private int playFail;
        private int lastSearchCount;
        private long lastSearchCost;
        private long lastDetailCost;
        private long lastParseCost;
        private long lastSearchSuccessAt;
        private long lastSearchFailAt;
        private long lastDetailSuccessAt;
        private long lastDetailFailAt;
        private long lastParseSuccessAt;
        private long lastParseFailAt;
        private long lastPlaySuccessAt;
        private long lastPlayFailAt;
        private long updatedAt;
        private String lastSearchError;
        private String lastDetailError;
        private String lastParseError;
        private String lastPlayError;
        private Map<String, Integer> searchReasons;
        private Map<String, Integer> detailReasons;
        private Map<String, Integer> parseReasons;
        private Map<String, Integer> playReasons;

        private int total() {
            return searchSuccess + searchFail + detailSuccess + detailFail + parseSuccess + parseFail + playSuccess + playFail;
        }

        private Status status() {
            return status(0);
        }

        private Status status(double adPenalty) {
            if (total() == 0) return Status.UNKNOWN;
            double score = score() - adPenalty;
            if (lastPlayFailAt > lastPlaySuccessAt && playFail >= 3 && score < 0) return Status.BAD;
            if (score >= 20 || lastPlaySuccessAt >= lastPlayFailAt && playSuccess > 0) return Status.GOOD;
            if (score <= -20) return Status.BAD;
            return Status.WARN;
        }

        private double score() {
            int success = searchSuccess + detailSuccess * 2 + playSuccess * 5;
            int fail = searchFail + detailFail * 2 + playFail * 5;
            if (success + fail == 0) return 0;
            double score = 60.0 * (success - fail) / (success + fail + 4.0);
            score += Math.min(lastSearchCount, 20) * 1.2;
            score -= Math.min(lastSearchCost, TimeUnit.SECONDS.toMillis(8)) / 1000.0;
            score -= Math.min(lastDetailCost, TimeUnit.SECONDS.toMillis(8)) / 1500.0;
            if (lastPlaySuccessAt > lastPlayFailAt) score += 18;
            if (lastPlayFailAt > lastPlaySuccessAt) score -= 18;
            return score;
        }

        private void addSearchReason(String reason) {
            searchReasons = addReason(searchReasons, reason);
        }

        private void addDetailReason(String reason) {
            detailReasons = addReason(detailReasons, reason);
        }

        private void addParseReason(String reason) {
            parseReasons = addReason(parseReasons, reason);
        }

        private void addPlayReason(String reason) {
            playReasons = addReason(playReasons, reason);
        }

        private Map<String, Integer> addReason(Map<String, Integer> reasons, String reason) {
            if (TextUtils.isEmpty(reason)) return reasons;
            if (reasons == null) reasons = new LinkedHashMap<>();
            reasons.merge(reason, 1, Integer::sum);
            return reasons;
        }
    }
}
