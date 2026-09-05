package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.HlsAdRule;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户自建的 HLS 结构化规则。
 *
 * <p>内置规则来自 APK assets、接口规则来自点播/直播配置，两者都不可写；
 * 而区间反馈需要产出真正能删除广告切片的规则 —— {@code RuleConfig.getAds()}
 * 的唯一消费者是 {@code CustomWebView}，只拦 WebView 请求，不拦播放器直连的
 * 切片请求。因此新增这条用户可写的来源。
 */
public final class UserHlsRuleStore {

    private static final String PREF_KEY = "user_hls_rules";
    private static final Type LIST_TYPE = new TypeToken<List<HlsAdRule>>() {}.getType();
    /** 上限防止规则库无限膨胀拖慢每次 manifest 匹配。 */
    private static final int MAX_RULES = 200;

    private UserHlsRuleStore() {
    }

    public static synchronized List<HlsAdRule> load() {
        try {
            List<HlsAdRule> rules = App.gson().fromJson(Prefers.getString(PREF_KEY, "[]"), LIST_TYPE);
            return rules == null ? new ArrayList<>() : rules;
        } catch (Throwable e) {
            return new ArrayList<>();
        }
    }

    public static synchronized void save(List<HlsAdRule> rules) {
        List<HlsAdRule> value = rules == null ? new ArrayList<>() : new ArrayList<>(rules);
        while (value.size() > MAX_RULES) value.remove(0);
        Prefers.put(PREF_KEY, App.gson().toJson(value));
        HlsRuleConfig.invalidate();
    }

    /**
     * 追加一条规则。写入前先 compile 校验，非法规则直接拒绝 ——
     * 否则会在 HlsRuleConfig 里变成一条永久报错的条目。
     *
     * @return 是否写入成功
     */
    public static synchronized boolean add(HlsAdRule rule) {
        if (rule == null) return false;
        try {
            rule.compile();
        } catch (RuntimeException e) {
            return false;
        }
        List<HlsAdRule> rules = load();
        if (rules.stream().anyMatch(existing -> existing.getId().equals(rule.getId()))) return false;
        rules.add(rule);
        save(rules);
        return true;
    }

    public static synchronized boolean delete(String id) {
        if (id == null || id.isBlank()) return false;
        List<HlsAdRule> rules = load();
        boolean removed = rules.removeIf(rule -> id.equals(rule.getId()));
        if (removed) save(rules);
        return removed;
    }

    public static synchronized void clear() {
        Prefers.put(PREF_KEY, "[]");
        HlsRuleConfig.invalidate();
    }
}
