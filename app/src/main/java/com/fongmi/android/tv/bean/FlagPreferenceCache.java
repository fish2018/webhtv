package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.github.catvod.utils.Path;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 线路选择偏好缓存
 *
 * 线路选择原本只作为播放的副产物写进 History.vodFlag，而写库要过
 * saveInlineHistory 的播放态门槛和 History.canSave() 的 position>0 门槛。
 * 详情页里「切了线路但没起播」或「从播放器返回详情页后再切线路」都不满足，
 * 进程被杀后重进就退回 flags.get(0)。
 *
 * 这里把线路选择独立存成用户偏好：切一下就落盘，不依赖播放进度，
 * 也不进 History 表（避免 position=0 的空记录污染「最近观看」列表）。
 *
 * 缓存策略与 EpisodePositionCache 一致：内存优先 + JSON 落盘 + 过期清理。
 */
public class FlagPreferenceCache {

    private static final String CACHE_FILE_NAME = "flag_preferences.json";
    private static final int MAX_ENTRIES = 500;
    private static final long EXPIRE_TIME = 90L * 24 * 60 * 60 * 1000; // 90天过期
    private static final long RENEW_INTERVAL = 24L * 60 * 60 * 1000; // 同一选择每天最多续期一次

    private final Map<String, FlagPreference> cache;
    private final Gson gson;
    private final File cacheFile;
    // UI 线程写、Task.execute 的后台线程读，必须 volatile 才能保证写盘不被漏掉。
    private volatile boolean dirty = false;

    private static class Loader {
        static volatile FlagPreferenceCache INSTANCE = new FlagPreferenceCache();
    }

    public static FlagPreferenceCache get() {
        return Loader.INSTANCE;
    }

    private FlagPreferenceCache() {
        this(Path.cache(CACHE_FILE_NAME));
    }

    FlagPreferenceCache(File cacheFile) {
        this.cache = new ConcurrentHashMap<>();
        this.gson = new Gson();
        this.cacheFile = cacheFile;
        load();
    }

    /**
     * 一次线路选择。stableKey 是 Flag.stableKey 生成的「线路名#索引」，
     * 用于区分同名线路；flagName 是退化匹配用的线路名。
     */
    public static class FlagPreference {
        public String stableKey;
        public String flagName;
        public long timestamp;

        public FlagPreference() {
        }

        public FlagPreference(String stableKey, String flagName) {
            this.stableKey = stableKey;
            this.flagName = flagName;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isExpired() {
            // gson 反序列化不走构造函数，缺字段/损坏数据的 timestamp 会是 0。
            // 直接判过期会让这条偏好永久失效，按「刚写入」处理，下次 put 自会续期。
            if (timestamp <= 0) return false;
            return System.currentTimeMillis() - timestamp > EXPIRE_TIME;
        }

        boolean isUsable() {
            return !getStableKey().isEmpty() || !getFlagName().isEmpty();
        }

        public String getStableKey() {
            return stableKey == null ? "" : stableKey;
        }

        public String getFlagName() {
            return flagName == null ? "" : flagName;
        }
    }

    /**
     * 构建缓存 key，格式: siteKey|vodId
     *
     * 不含线路名——每部剧在每个站源下只记一条「当前选中线路」。
     */
    private String buildKey(String siteKey, String vodId) {
        return siteKey + "|" + vodId;
    }

    /**
     * 记录用户选中的线路。
     *
     * @param siteKey  站点 key
     * @param vodId    视频 id
     * @param stableKey Flag.stableKey 生成的稳定键（线路名#索引）
     * @param flagName  线路名，稳定键失效时的退化匹配依据
     */
    public void put(String siteKey, String vodId, String stableKey, String flagName) {
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId)) return;
        if (TextUtils.isEmpty(stableKey) && TextUtils.isEmpty(flagName)) return;
        String key = buildKey(siteKey, vodId);
        FlagPreference existing = cache.get(key);
        boolean changed = existing == null
                || !TextUtils.equals(existing.getStableKey(), stableKey == null ? "" : stableKey)
                || !TextUtils.equals(existing.getFlagName(), flagName == null ? "" : flagName);
        // 选择没变也要续期：起播路径每次换集都会重申当前线路，只刷内存不落盘的话，
        // 磁盘上的 timestamp 会一直停在首次选择的时刻，长期只用同一条线路的用户
        // 反而会在 90 天后被判过期。RENEW_INTERVAL 之内不重复写盘，避免频繁 IO。
        FlagPreference updated = new FlagPreference(stableKey, flagName);
        boolean stale = existing == null || updated.timestamp - existing.timestamp >= RENEW_INTERVAL;
        cache.put(key, updated);
        if (changed || stale) dirty = true;
        trimToCapacity();
    }

    /**
     * 读取上次选中的线路，没有或已过期返回 null。
     */
    public FlagPreference get(String siteKey, String vodId) {
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId)) return null;
        String key = buildKey(siteKey, vodId);
        FlagPreference preference = cache.get(key);
        if (preference == null) return null;
        if (preference.isExpired() || !preference.isUsable()) {
            cache.remove(key);
            dirty = true;
            return null;
        }
        return preference;
    }

    public void remove(String siteKey, String vodId) {
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId)) return;
        if (cache.remove(buildKey(siteKey, vodId)) != null) dirty = true;
    }

    /**
     * 淘汰到容量上限以内。淘汰要置 dirty：否则遇到已经超容的旧文件时，
     * 内存里减下去了但不落盘，下次打开又是超容状态，永远收敛不了。
     */
    private void trimToCapacity() {
        while (cache.size() > MAX_ENTRIES) {
            String oldest = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<String, FlagPreference> entry : cache.entrySet()) {
                if (entry.getValue().timestamp < oldestTime) {
                    oldestTime = entry.getValue().timestamp;
                    oldest = entry.getKey();
                }
            }
            if (oldest == null || cache.remove(oldest) == null) return;
            dirty = true;
        }
    }

    public synchronized void save() {
        if (!dirty) return;
        // 先清 dirty 再快照：期间若有新的 put，它会重新置上 dirty，
        // 下一次 save 仍会落盘。反过来（写完再清）会把并发 put 的置位擦掉，丢写。
        dirty = false;
        File temp = null;
        try {
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            Map<String, FlagPreference> snapshot = new HashMap<>(cache);
            if (cacheFile.getParentFile() != null) cacheFile.getParentFile().mkdirs();
            // 先写临时文件再替换：直写目标文件时若中途失败（磁盘满、进程被杀），
            // 会在磁盘上留下被截断的半个 JSON，下次 load 整份偏好都解析不出来。
            temp = new File(cacheFile.getPath() + ".tmp");
            try (FileWriter writer = new FileWriter(temp)) {
                gson.toJson(snapshot, writer);
            }
            // Android 上 renameTo 是原子替换，一次就成。Windows 等平台目标已存在时会失败，
            // 需要先移走旧文件——但要留着它直到新文件就位，否则重试再失败就两份都没了。
            if (!temp.renameTo(cacheFile)) {
                File backup = new File(cacheFile.getPath() + ".bak");
                if (backup.exists()) backup.delete();
                boolean moved = !cacheFile.exists() || cacheFile.renameTo(backup);
                if (!moved || !temp.renameTo(cacheFile)) {
                    if (backup.exists() && !cacheFile.exists()) backup.renameTo(cacheFile);
                    throw new IOException("rename failed");
                }
                backup.delete();
            }
        } catch (IOException | RuntimeException e) {
            // 写盘失败要把 dirty 还回去，否则这次选择再也不会被重试落盘。
            dirty = true;
            if (temp != null && temp.exists()) temp.delete();
            e.printStackTrace();
        }
    }

    private synchronized void load() {
        try {
            if (!cacheFile.exists()) return;
            try (FileReader reader = new FileReader(cacheFile)) {
                Map<String, FlagPreference> loaded = gson.fromJson(reader,
                        new TypeToken<Map<String, FlagPreference>>() {}.getType());
                if (loaded == null) return;
                cache.clear();
                // 两个字段都空的条目匹配不到任何线路，留着只会挡住后续写入的有效偏好。
                loaded.forEach((key, value) -> {
                    if (key == null || key.isEmpty() || value == null) return;
                    if (!value.isUsable() || value.isExpired()) return;
                    cache.put(key, value);
                });
                // 收口已经超容的旧文件。启动不主动写盘，dirty 交给下一次 put 带出去。
                trimToCapacity();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void clear() {
        cache.clear();
        dirty = false;
        if (cacheFile.exists()) cacheFile.delete();
        File temp = new File(cacheFile.getPath() + ".tmp");
        if (temp.exists()) temp.delete();
    }

    int size() {
        return cache.size();
    }
}
