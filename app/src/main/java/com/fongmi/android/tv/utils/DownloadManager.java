package com.fongmi.android.tv.utils;

import android.os.Environment;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.DownloadGroup;
import com.fongmi.android.tv.bean.DownloadItem;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.service.DownloadService;
import com.github.catvod.utils.Path;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 下載任務的中央調度器，負責任務佇列、狀態持久化與進度回調分發。
 */
public class DownloadManager {

    private static final int MAX_RUNNING = 2;

    private final List<DownloadItem> items;
    private final List<Listener> listeners;
    private final Map<String, Object> tasks;

    private static class Loader {
        static volatile DownloadManager INSTANCE = new DownloadManager();
    }

    public static DownloadManager get() {
        return Loader.INSTANCE;
    }

    private DownloadManager() {
        this.items = new CopyOnWriteArrayList<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.tasks = new ConcurrentHashMap<>();
        load();
    }

    private static File record() {
        return new File(dir(), ".record");
    }

    private void load() {
        try {
            File file = record();
            if (!file.exists()) return;
            String json = Path.read(file);
            if (TextUtils.isEmpty(json)) return;
            Type type = new TypeToken<List<DownloadItem>>() {}.getType();
            List<DownloadItem> cache = new Gson().fromJson(json, type);
            if (cache == null) return;
            for (DownloadItem item : cache) {
                // 進程重啟後正在下載的任務一律轉為暫停，避免狀態殘留
                if (item.isRunning() || item.isWaiting()) item.setState(DownloadItem.STATE_PAUSED);
                items.add(item);
            }
        } catch (Exception ignored) {
        }
    }

    private void save() {
        try {
            Path.write(record(), new Gson().toJson(items).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public List<DownloadItem> getItems() {
        return new ArrayList<>(items);
    }

    public DownloadItem find(String id) {
        for (DownloadItem item : items) if (item.getId().equals(id)) return item;
        return null;
    }

    public List<DownloadItem> getItems(String groupId) {
        List<DownloadItem> result = new ArrayList<>();
        for (DownloadItem item : items) if (item.getGroupId().equals(groupId)) result.add(item);
        return result;
    }

    /**
     * 依影片維度聚合，供下載列表首頁使用。
     */
    public List<DownloadGroup> getGroups() {
        Map<String, DownloadGroup> map = new LinkedHashMap<>();
        for (DownloadItem item : items) {
            DownloadGroup group = map.get(item.getGroupId());
            if (group == null) map.put(item.getGroupId(), group = new DownloadGroup(item));
            group.add(item);
        }
        return new ArrayList<>(map.values());
    }

    public boolean contains(DownloadItem item) {
        return find(item.getId()) != null;
    }

    public int getRunningCount() {
        int count = 0;
        for (DownloadItem item : items) if (item.isRunning()) ++count;
        return count;
    }

    public boolean hasActive() {
        for (DownloadItem item : items) if (item.isActive()) return true;
        return false;
    }

    public void add(DownloadItem item) {
        add(Collections.singletonList(item));
    }

    public void add(List<DownloadItem> adds) {
        for (DownloadItem item : adds) {
            if (contains(item)) continue;
            items.add(item);
        }
        save();
        notifyChange();
        schedule();
    }

    public void pause(DownloadItem item) {
        stopTask(item);
        item.setState(DownloadItem.STATE_PAUSED);
        item.setSpeed(0);
        save();
        notifyItem(item);
        schedule();
    }

    public void resume(DownloadItem item) {
        if (item.isDone()) return;
        item.setState(DownloadItem.STATE_WAITING);
        save();
        notifyItem(item);
        schedule();
    }

    public void toggle(DownloadItem item) {
        if (item.isDone()) return;
        if (item.isActive()) pause(item);
        else resume(item);
    }

    public void remove(DownloadItem item) {
        stopTask(item);
        items.remove(item);
        Path.clear(item.getFile());
        if (!TextUtils.isEmpty(item.getPath())) {
            Path.clear(new File(item.getPath() + ".part"));
            Path.clear(new File(item.getPath() + ".idx"));
        }
        save();
        notifyChange();
        schedule();
    }

    public void remove(List<DownloadItem> removes) {
        for (DownloadItem item : new ArrayList<>(removes)) {
            stopTask(item);
            items.remove(item);
            Path.clear(item.getFile());
        }
        save();
        notifyChange();
        schedule();
    }

    public void removeGroup(DownloadGroup group) {
        remove(group.getItems());
    }

    public void pauseAll() {
        for (DownloadItem item : items) {
            if (!item.isActive()) continue;
            stopTask(item);
            item.setState(DownloadItem.STATE_PAUSED);
            item.setSpeed(0);
        }
        save();
        notifyChange();
    }

    public void resumeAll() {
        for (DownloadItem item : items) {
            if (item.isDone() || item.isActive()) continue;
            item.setState(DownloadItem.STATE_WAITING);
        }
        save();
        notifyChange();
        schedule();
    }

    public void clear() {
        pauseAll();
        for (DownloadItem item : items) Path.clear(item.getFile());
        items.clear();
        save();
        notifyChange();
    }

    /**
     * 依併發上限啟動處於等待狀態的任務。
     */
    private synchronized void schedule() {
        int running = getRunningCount();
        for (DownloadItem item : items) {
            if (running >= MAX_RUNNING) break;
            if (!item.isWaiting() || tasks.containsKey(item.getId())) continue;
            start(item);
            ++running;
        }
        notifyState();
    }

    private void start(DownloadItem item) {
        item.setState(DownloadItem.STATE_RUNNING);
        App.post(() -> DownloadService.start(App.get()));
        notifyItem(item);
        tasks.put(item.getId(), new Object());
        // 下載為長時間任務，使用大執行緒池避免佔滿共用的預設執行緒池
        Task.submitLarge(() -> {
            try {
                execute(item);
            } catch (Exception e) {
                onError(item, e.getMessage());
            }
        });
    }

    /**
     * 解析真實播放位址後，依副檔名選擇 m3u8 分片下載或一般檔案下載。
     */
    private void execute(DownloadItem item) throws Exception {
        String url = item.getUrl();
        Map<String, String> headers = item.getHeaders();
        if (TextUtils.isEmpty(url)) {
            Result result = SiteApi.playerContent(item.getKey(), item.getFlag(), item.getEpisodeUrl());
            url = result.getRealUrl();
            if (TextUtils.isEmpty(url)) throw new Exception("Parse url failed");
            headers = result.getHeader();
            item.setUrl(url);
            item.setHeaders(headers);
            save();
        }
        if (!tasks.containsKey(item.getId())) return;
        boolean m3u8 = url.contains(".m3u8") || url.contains("m3u8?");
        File file = file(item, m3u8);
        item.setM3u8(m3u8);
        item.setPath(file.getAbsolutePath());
        if (m3u8) executeM3u8(item, url, headers, file);
        else executeFile(item, url, headers, file);
    }

    private void executeM3u8(DownloadItem item, String url, Map<String, String> headers, File file) throws Exception {
        item.setM3u8(true);
        M3u8Downloader downloader = new M3u8Downloader(url, headers, file);
        tasks.put(item.getId(), downloader);
        // m3u8 以分片為單位回調，需在這裡自行計算下載速度（累計位元組 / 耗時）
        final long[] last = {0, System.currentTimeMillis()};
        downloader.start((done, total, bytes) -> {
            item.setSegmentDone(done);
            item.setSegmentTotal(total);
            item.setCurrent(bytes);
            long now = System.currentTimeMillis();
            long dt = now - last[1];
            if (dt >= 500) {
                item.setSpeed((bytes - last[0]) * 1000 / dt);
                last[0] = bytes;
                last[1] = now;
            }
            notifyItem(item);
        });
        if (downloader.isPaused() || !tasks.containsKey(item.getId())) return;
        if (file.exists() && file.length() > 0) onSuccess(item);
        else onError(item, "Download failed");
    }

    private void executeFile(DownloadItem item, String url, Map<String, String> headers, File file) {
        Download download = Download.create(url, headers, file).resume(true).tag(item.getId());
        tasks.put(item.getId(), download);
        download.start(new Download.Callback() {
            @Override
            public void progress(int progress) {
            }

            @Override
            public void progress(int progress, long bytes, long total, long speed, long elapsed) {
                item.setCurrent(bytes);
                item.setTotal(total);
                item.setSpeed(speed);
                notifyItem(item);
            }

            @Override
            public void error(String msg) {
                onError(item, msg);
            }

            @Override
            public void success(File result) {
                onSuccess(item);
            }
        });
    }

    private File file(DownloadItem item, boolean m3u8) {
        String name = filter(item.getVodName()) + "_" + filter(item.getEpisodeName());
        return new File(dir(), name + "_" + item.getId().substring(0, 8) + (m3u8 ? ".ts" : ".mp4"));
    }

    /**
     * 移除檔名中的非法字元，避免建立檔案失敗。
     */
    private static String filter(String name) {
        if (TextUtils.isEmpty(name)) return "video";
        String result = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        if (result.length() > 40) result = result.substring(0, 40);
        return TextUtils.isEmpty(result) ? "video" : result;
    }

    /**
     * 使用應用私有的外部儲存目錄，免申請儲存權限，卸載時自動清理。
     */
    public static File dir() {
        File base = App.get().getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (base == null) base = App.get().getFilesDir();
        File dir = new File(base, "WebHTV");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void onSuccess(DownloadItem item) {
        tasks.remove(item.getId());
        item.setState(DownloadItem.STATE_DONE);
        item.setSpeed(0);
        save();
        notifyItem(item);
        schedule();
    }

    private void onError(DownloadItem item, String msg) {
        tasks.remove(item.getId());
        if (item.isPaused()) return;
        item.setState(DownloadItem.STATE_ERROR);
        item.setSpeed(0);
        save();
        notifyItem(item);
        schedule();
    }

    private void stopTask(DownloadItem item) {
        Object task = tasks.remove(item.getId());
        if (task instanceof Download) ((Download) task).pause();
        if (task instanceof M3u8Downloader) ((M3u8Downloader) task).pause();
    }

    private void notifyItem(DownloadItem item) {
        App.post(() -> {
            for (Listener listener : listeners) listener.onDownloadUpdate(item);
        });
        notifyState();
    }

    private void notifyChange() {
        App.post(() -> {
            for (Listener listener : listeners) listener.onDownloadChange();
        });
    }

    private void notifyState() {
        App.post(() -> {
            for (Listener listener : listeners) listener.onDownloadState(hasActive());
        });
    }

    public interface Listener {

        default void onDownloadUpdate(@NonNull DownloadItem item) {
        }

        default void onDownloadChange() {
        }

        default void onDownloadState(boolean active) {
        }
    }
}
