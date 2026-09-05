package com.fongmi.android.tv.ad.feedback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 每站点最近成功播放的域名基线，供 {@link DomainReputationClassifier} 判断
 * 「非当前适配的域名」。见设计文档第 8.3 节。
 *
 * <p>只存 host 字符串，不存 URL 全文、query 或 token。单站点上限 8 个域名，
 * 全局上限 200 个站点，均按最久未用淘汰。
 *
 * <p>本类只负责内存中的 LRU 结构与序列化，持久化由调用方注入的
 * {@link Storage} 完成，因此可在纯 JVM 下单测。
 */
public final class SitePlaylistHostBaseline {

    /** 单站点保留的域名数上限。 */
    public static final int MAX_HOSTS_PER_SITE = 8;
    /** 全局保留的站点数上限。 */
    public static final int MAX_SITES = 200;

    /** 持久化后端，实现通常包装 SharedPreferences。 */
    public interface Storage {
        String read();

        void write(String value);
    }

    /** 内存实现，测试与无持久化场景使用。 */
    public static final class MemoryStorage implements Storage {
        private String value = "";

        @Override
        public String read() {
            return value;
        }

        @Override
        public void write(String value) {
            this.value = value == null ? "" : value;
        }
    }

    private final Storage storage;
    /** 上一次 record 命中的站点，用于判断访问顺序是否真的发生变化。 */
    private String lastAccessedSite;
    // accessOrder=true：get 也算一次使用，实现真正的 LRU
    private final LinkedHashMap<String, List<String>> sites =
            new LinkedHashMap<>(16, 0.75f, true);

    public SitePlaylistHostBaseline(Storage storage) {
        this.storage = storage == null ? new MemoryStorage() : storage;
        load();
    }

    /** 记录一次成功播放涉及的域名。 */
    public synchronized void record(String siteKey, String... hosts) {
        if (siteKey == null || siteKey.isBlank() || hosts == null) return;
        // accessOrder=true 的 LinkedHashMap 在读写时都会把该站点移到尾部。
        // 若本次 host 没变就直接返回，这个「站点最近被使用」的顺序只存在内存里，
        // 重启后恢复的淘汰顺序会与运行期不一致，因此需要单独跟踪。
        boolean siteOrderChanged = sites.containsKey(siteKey)
                && !siteKey.equals(lastAccessedSite);
        lastAccessedSite = siteKey;
        List<String> known = sites.computeIfAbsent(siteKey, key -> new ArrayList<>());
        boolean seenAny = false;
        boolean changed = false;
        for (String host : hosts) {
            if (host == null || host.isBlank()) continue;
            String normalized = host.toLowerCase(Locale.US).trim();
            seenAny = true;
            // 已在末尾说明状态没变，跳过写盘 —— STATE_READY 会因 seek、
            // 卡顿恢复反复触发，每次都 persist 是不必要的 I/O
            int last = known.size() - 1;
            if (last >= 0 && known.get(last).equals(normalized)) continue;
            // 已存在则移到末尾，表示最近使用
            known.remove(normalized);
            known.add(normalized);
            changed = true;
        }
        if (!seenAny) {
            // 全部输入为空白：不能留下空条目占用站点配额
            if (known.isEmpty()) sites.remove(siteKey);
            return;
        }
        if (!changed) {
            // host 没变但站点访问顺序变了，仍要落盘，否则重启后 LRU 顺序失真
            if (siteOrderChanged) persist();
            return;
        }
        while (known.size() > MAX_HOSTS_PER_SITE) known.remove(0);
        evictSites();
        persist();
    }

    /** 某站点已知的域名，最近使用的在后。 */
    public synchronized List<String> hosts(String siteKey) {
        if (siteKey == null) return List.of();
        List<String> known = sites.get(siteKey);
        return known == null ? List.of() : List.copyOf(known);
    }

    public synchronized int siteCount() {
        return sites.size();
    }

    public synchronized void clear() {
        sites.clear();
        persist();
    }

    private void evictSites() {
        // LinkedHashMap 的迭代顺序即访问顺序，最久未用在前
        while (sites.size() > MAX_SITES) {
            String oldest = sites.keySet().iterator().next();
            sites.remove(oldest);
        }
    }

    /**
     * 序列化为紧凑文本：{@code siteKey|host1,host2;siteKey2|host3}。
     * 不用 JSON 是为了避免依赖 Gson，本类需在纯 JVM 下可测。
     */
    private void persist() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : sites.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            if (builder.length() > 0) builder.append(';');
            builder.append(escape(entry.getKey())).append('|');
            builder.append(String.join(",", entry.getValue()));
        }
        storage.write(builder.toString());
    }

    private void load() {
        String raw = storage.read();
        if (raw == null || raw.isBlank()) return;
        for (String chunk : raw.split(";")) {
            int separator = chunk.indexOf('|');
            if (separator <= 0) continue;
            String siteKey = unescape(chunk.substring(0, separator));
            String hostList = chunk.substring(separator + 1);
            if (siteKey.isBlank() || hostList.isBlank()) continue;
            List<String> hosts = new ArrayList<>();
            for (String host : hostList.split(",")) {
                if (!host.isBlank()) hosts.add(host.trim());
            }
            if (hosts.isEmpty()) continue;
            while (hosts.size() > MAX_HOSTS_PER_SITE) hosts.remove(0);
            sites.put(siteKey, hosts);
        }
        evictSites();
    }

    /** siteKey 可能含分隔符，转义避免破坏格式。 */
    private static String escape(String value) {
        return value.replace("%", "%25").replace(";", "%3B")
                .replace("|", "%7C").replace(",", "%2C");
    }

    private static String unescape(String value) {
        return value.replace("%2C", ",").replace("%7C", "|")
                .replace("%3B", ";").replace("%25", "%");
    }
}
