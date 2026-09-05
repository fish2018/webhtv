package com.fongmi.android.tv.viewing;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Path;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 观影报告缓存。24 小时有效,history 变化后自动失效。
 */
public class ViewingReportCache {

    private static final long CACHE_EXPIRY_MS = TimeUnit.HOURS.toMillis(24);
    private static final String CACHE_DIR = "viewing_report";

    public static ViewingReport read(ViewingReportRange range) {
        String fingerprint = historyFingerprint();
        File file = cacheFile(range, fingerprint);
        if (file == null || !file.exists()) return null;
        if (System.currentTimeMillis() - file.lastModified() > CACHE_EXPIRY_MS) {
            file.delete();
            return null;
        }
        try {
            String json = Path.read(file);
            if (TextUtils.isEmpty(json)) return null;
            return App.gson().fromJson(json, ViewingReport.class);
        } catch (Throwable e) {
            file.delete();
            return null;
        }
    }

    public static void write(ViewingReport report) {
        if (report == null || report.getRange() == null) return;
        String fingerprint = historyFingerprint();
        File file = cacheFile(report.getRange(), fingerprint);
        if (file == null) return;
        try {
            String json = App.gson().toJson(report);
            Path.write(file, json.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    /**
     * 清除指定范围的缓存文件。用于手动刷新报告。
     */
    public static void clear(ViewingReportRange range) {
        if (range == null) return;
        String fingerprint = historyFingerprint();
        File file = cacheFile(range, fingerprint);
        if (file != null && file.exists()) file.delete();
    }

    private static File cacheFile(ViewingReportRange range, String fingerprint) {
        try {
            File dir = Path.cache(CACHE_DIR);
            if (!dir.exists()) dir.mkdirs();
            String name = range.name().toLowerCase(Locale.ROOT) + "_" + fingerprint + ".json";
            return new File(dir, name);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 基于历史记录数量和最新时间戳计算指纹。history 变化后,指纹变化,缓存失效。
     */
    private static String historyFingerprint() {
        try {
            int count = com.fongmi.android.tv.bean.History.get().size();
            long latest = com.fongmi.android.tv.bean.History.get().stream()
                    .mapToLong(com.fongmi.android.tv.bean.History::getCreateTime)
                    .max()
                    .orElse(0L);
            String raw = count + "|" + latest;
            return md5(raw);
        } catch (Throwable e) {
            return "fallback";
        }
    }

    private static String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format(Locale.US, "%02x", b));
            return hex.toString().substring(0, 12);
        } catch (Throwable e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
