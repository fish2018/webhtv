package com.fongmi.android.tv.api;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.crawler.SpiderNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CspWarmup {

    private static final long DELAY_MS = 500;
    private static final Object LOCK = new Object();

    private static final Set<String> attemptedKeys = new HashSet<>();
    private static long generation;

    private CspWarmup() {
    }

    public static void reset() {
        long currentGeneration;
        synchronized (LOCK) {
            currentGeneration = ++generation;
            attemptedKeys.clear();
        }
        SpiderDebug.log("csp-warmup", "reset generation=%s", currentGeneration);
    }

    public static void schedule(String reason) {
        if (!Setting.isCspWarmup()) return;
        String key = configKey();
        long currentGeneration;
        synchronized (LOCK) {
            if (!attemptedKeys.add(key)) {
                SpiderDebug.log("csp-warmup", "skip duplicate reason=%s config=%s", reason, key);
                return;
            }
            currentGeneration = generation;
        }
        SpiderDebug.log("csp-warmup", "schedule reason=%s config=%s generation=%s delay=%sms", reason, key, currentGeneration, DELAY_MS);
        App.post(() -> Task.execute(() -> run(key, reason, currentGeneration)), DELAY_MS);
    }

    private static void run(String key, String reason, long scheduledGeneration) {
        long start = System.currentTimeMillis();
        try {
            synchronized (LOCK) {
                if (scheduledGeneration != generation) {
                    SpiderDebug.log("csp-warmup", "skip stale reason=%s config=%s scheduled=%s current=%s", reason, key, scheduledGeneration, generation);
                    return;
                }
            }
            if (!Setting.isCspWarmup()) {
                SpiderDebug.log("csp-warmup", "cancel disabled reason=%s", reason);
                return;
            }
            List<Site> sites = pickSites();
            if (sites.isEmpty()) {
                SpiderDebug.log("csp-warmup", "skip no native csp reason=%s cost=%sms", reason, System.currentTimeMillis() - start);
                return;
            }
            int success = 0;
            for (Site site : sites) if (initSite(site, reason)) success++;
            SpiderDebug.log("csp-warmup", "done reason=%s success=%s total=%s cost=%sms", reason, success, sites.size(), System.currentTimeMillis() - start);
        } catch (Throwable e) {
            SpiderDebug.log("csp-warmup", "error reason=%s err=%s msg=%s cost=%sms", reason, e.getClass().getSimpleName(), e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private static boolean initSite(Site site, String reason) {
        long start = System.currentTimeMillis();
        try {
            SpiderDebug.log("csp-warmup", "init start reason=%s site=%s api=%s jar=%s", reason, site.getKey(), site.getApi(), jarKey(site));
            Spider spider = site.recent().spider();
            if (spider == null || spider instanceof SpiderNull) {
                SpiderDebug.log("csp-warmup", "init unavailable reason=%s site=%s api=%s cost=%sms", reason, site.getKey(), site.getApi(), System.currentTimeMillis() - start);
                return false;
            }
            SpiderDebug.log("csp-warmup", "init done reason=%s site=%s api=%s cost=%sms", reason, site.getKey(), site.getApi(), System.currentTimeMillis() - start);
            return true;
        } catch (Throwable e) {
            SpiderDebug.log("csp-warmup", "init error reason=%s site=%s err=%s msg=%s cost=%sms", reason, site.getKey(), e.getClass().getSimpleName(), e.getMessage(), System.currentTimeMillis() - start);
            return false;
        }
    }

    private static List<Site> pickSites() {
        if (Setting.getCspWarmupMode() == Setting.CSP_WARMUP_CUSTOM) return pickCustomSites();
        Site site = pickSite();
        return site == null ? Collections.emptyList() : Collections.singletonList(site);
    }

    private static List<Site> pickCustomSites() {
        List<Site> result = new ArrayList<>();
        Set<String> jars = new HashSet<>();
        for (String key : Setting.getCspWarmupSites()) {
            Site site = VodConfig.get().getSite(key);
            if (!isWarmable(site)) continue;
            String jar = jarKey(site);
            if (!jars.add(jar)) continue;
            result.add(site);
        }
        return result;
    }

    private static Site pickSite() {
        Site fallback = null;
        for (Site site : VodConfig.get().getSites()) {
            if (!isWarmable(site)) continue;
            if (fallback == null) fallback = site;
            if (!site.hasHomePage()) return site;
        }
        return fallback;
    }

    public static boolean isWarmable(Site site) {
        if (site == null || site.isEmpty() || site.getType() != 3) return false;
        String api = site.getApi();
        return !TextUtils.isEmpty(api) && api.startsWith("csp_") && !"csp_Builtin".equalsIgnoreCase(api) && !TextUtils.isEmpty(jarKey(site));
    }

    public static String jarKey(Site site) {
        return site == null ? "" : site.getJar().trim();
    }

    private static String configKey() {
        int mode = Setting.getCspWarmupMode();
        return VodConfig.getCid() + ":" + VodConfig.getUrl() + ":" + mode + (mode == Setting.CSP_WARMUP_CUSTOM ? ":" + TextUtils.join(",", Setting.getCspWarmupSites()) : "");
    }
}
