package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.fongmi.android.tv.setting.Setting;
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
import java.util.function.BooleanSupplier;

/**
 * 集数播放位置缓存
 * 用于记住每一集的播放进度，支持快速切换集数时恢复播放位置
 *
 * 缓存策略：
 * - 内存优先：当前会话中的所有集数位置保存在内存
 * - 持久化：定期保存到磁盘，应用重启后仍然有效
 * - 清理：跟随系统缓存清理一起清除
 */
public class EpisodePositionCache {

    private static final String CACHE_FILE_NAME = "episode_positions.json";
    private static final int MAX_EPISODES_PER_VOD = 50; // 每部剧最多缓存50集
    private static final long EXPIRE_TIME = 30L * 24 * 60 * 60 * 1000; // 30天过期

    private final Map<String, VodPositions> cache;
    private final Gson gson;
    private final BooleanSupplier enabled;
    private final File cacheFile;
    private boolean dirty = false;

    private static class Loader {
        static volatile EpisodePositionCache INSTANCE = new EpisodePositionCache();
    }

    public static EpisodePositionCache get() {
        return Loader.INSTANCE;
    }

    private EpisodePositionCache() {
        this(Setting::isEpisodeHistory, Path.cache(CACHE_FILE_NAME));
    }

    EpisodePositionCache(BooleanSupplier enabled, File cacheFile) {
        this.cache = new ConcurrentHashMap<>();
        this.gson = new Gson();
        this.enabled = enabled;
        this.cacheFile = cacheFile;
        load();
    }

    /**
     * 单集播放位置
     */
    public static class EpisodePosition {
        public long position;
        public long duration;
        public long timestamp;

        public EpisodePosition() {
        }

        public EpisodePosition(long position, long duration) {
            this.position = position;
            this.duration = duration;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > EXPIRE_TIME;
        }
    }

    /**
     * 一部剧的所有集数位置
     */
    public static class VodPositions {
        public Map<String, EpisodePosition> episodes;
        public long lastAccessTime;

        public VodPositions() {
            this.episodes = new HashMap<>();
            this.lastAccessTime = System.currentTimeMillis();
        }

        public void put(String episodeName, long position, long duration) {
            this.lastAccessTime = System.currentTimeMillis();
            this.episodes.put(episodeName, new EpisodePosition(position, duration));

            // 限制最大缓存集数，移除最早的
            if (this.episodes.size() > MAX_EPISODES_PER_VOD) {
                removeOldest();
            }
        }

        public EpisodePosition get(String episodeName) {
            EpisodePosition pos = this.episodes.get(episodeName);
            if (pos != null && pos.isExpired()) {
                this.episodes.remove(episodeName);
                return null;
            }
            return pos;
        }

        private void removeOldest() {
            String oldest = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<String, EpisodePosition> entry : episodes.entrySet()) {
                if (entry.getValue().timestamp < oldestTime) {
                    oldestTime = entry.getValue().timestamp;
                    oldest = entry.getKey();
                }
            }
            if (oldest != null) {
                episodes.remove(oldest);
            }
        }

        public void removeExpired() {
            episodes.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }
    }

    /**
     * 构建剧集的缓存 key
     * 格式: siteKey|vodId|flag
     */
    private String buildVodKey(String siteKey, String vodId, String flag) {
        return siteKey + "|" + vodId + "|" + flag;
    }

    /**
     * 保存集数播放位置
     *
     * @param siteKey 站点key
     * @param vodId 视频id
     * @param flag 线路名称
     * @param episodeName 集数名称
     * @param position 播放位置(毫秒)
     * @param duration 总时长(毫秒)
     */
    public void put(String siteKey, String vodId, String flag, String episodeName, long position, long duration) {
        if (!enabled.getAsBoolean()) return;
        if (TextUtils.isEmpty(episodeName) || position < 0) return;

        String vodKey = buildVodKey(siteKey, vodId, flag);
        VodPositions vodPositions = cache.get(vodKey);

        if (vodPositions == null) {
            vodPositions = new VodPositions();
            cache.put(vodKey, vodPositions);
        }

        vodPositions.put(episodeName, position, duration);
        dirty = true;
    }

    /**
     * 获取集数播放位置
     *
     * @param siteKey 站点key
     * @param vodId 视频id
     * @param flag 线路名称
     * @param episodeName 集数名称
     * @return 播放位置，如果没有缓存返回 null
     */
    public EpisodePosition get(String siteKey, String vodId, String flag, String episodeName) {
        if (!enabled.getAsBoolean()) return null;
        if (TextUtils.isEmpty(episodeName)) return null;

        String vodKey = buildVodKey(siteKey, vodId, flag);
        VodPositions vodPositions = cache.get(vodKey);

        if (vodPositions == null) return null;

        return vodPositions.get(episodeName);
    }

    /**
     * 移除某部剧的所有缓存
     */
    public void removeVod(String siteKey, String vodId, String flag) {
        String vodKey = buildVodKey(siteKey, vodId, flag);
        if (cache.remove(vodKey) != null) {
            dirty = true;
        }
    }

    /**
     * 保存到磁盘
     */
    public synchronized void save() {
        if (!dirty) return;

        try {
            // 清理过期数据
            cache.values().forEach(VodPositions::removeExpired);
            cache.entrySet().removeIf(entry -> entry.getValue().episodes.isEmpty());

            File cacheFile = getCacheFile();
            cacheFile.getParentFile().mkdirs();

            try (FileWriter writer = new FileWriter(cacheFile)) {
                gson.toJson(cache, writer);
                dirty = false;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 从磁盘加载
     */
    private synchronized void load() {
        try {
            File cacheFile = getCacheFile();
            if (!cacheFile.exists()) return;

            try (FileReader reader = new FileReader(cacheFile)) {
                Map<String, VodPositions> loaded = gson.fromJson(reader,
                    new TypeToken<Map<String, VodPositions>>(){}.getType());

                if (loaded != null) {
                    cache.clear();
                    cache.putAll(loaded);

                    // 加载时清理过期数据
                    cache.values().forEach(VodPositions::removeExpired);
                    cache.entrySet().removeIf(entry -> entry.getValue().episodes.isEmpty());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 清空所有缓存
     */
    public synchronized void clear() {
        cache.clear();
        dirty = false;

        File cacheFile = getCacheFile();
        if (cacheFile.exists()) {
            cacheFile.delete();
        }
    }

    /**
     * 获取缓存文件大小
     */
    public long getCacheSize() {
        File cacheFile = getCacheFile();
        return cacheFile.exists() ? cacheFile.length() : 0;
    }

    /**
     * 获取缓存的剧集数量
     */
    public int getVodCount() {
        return cache.size();
    }

    /**
     * 获取缓存的总集数
     */
    public int getEpisodeCount() {
        return cache.values().stream()
            .mapToInt(vod -> vod.episodes.size())
            .sum();
    }

    private File getCacheFile() {
        return cacheFile;
    }
}
