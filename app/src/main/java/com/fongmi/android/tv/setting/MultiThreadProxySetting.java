package com.fongmi.android.tv.setting;

import android.content.SharedPreferences;

import com.fongmi.android.tv.server.proxy.ProxyDomainRuleSet;
import com.fongmi.android.tv.server.proxy.ProxyRuntimeConfig;
import com.fongmi.android.tv.server.proxy.ProxyRuntimeConfigCodec;
import com.fongmi.android.tv.server.proxy.ProxyRuntimeConfigValidator;
import com.github.catvod.utils.Prefers;

import java.util.Map;

public final class MultiThreadProxySetting {

    private static final String DOMAIN_RULES = "multi_thread_proxy_domain_rules";

    private MultiThreadProxySetting() {
    }

    public static ProxyRuntimeConfig get() {
        ProxyRuntimeConfig config = ProxyRuntimeConfigCodec.decode(Prefers.getPrefers().getAll());
        return ProxyRuntimeConfigValidator.validate(config).isValid()
                ? config
                : ProxyRuntimeConfig.defaults();
    }

    public static ProxyDomainRuleSet getDomainRules() {
        try {
            return ProxyDomainRuleSet.parse(getDomainRulesText());
        } catch (IllegalArgumentException e) {
            return ProxyDomainRuleSet.EMPTY;
        }
    }

    public static String getDomainRulesText() {
        return Prefers.getString(DOMAIN_RULES);
    }

    public static ProxyRuntimeConfigValidator.Result validateStored() {
        return ProxyRuntimeConfigValidator.validate(
                ProxyRuntimeConfigCodec.decode(Prefers.getPrefers().getAll()));
    }

    public static void put(ProxyRuntimeConfig config) {
        ProxyRuntimeConfigValidator.requireValid(config);
        SharedPreferences.Editor editor = Prefers.getPrefers().edit();
        for (Map.Entry<String, Object> entry : ProxyRuntimeConfigCodec.encode(config).entrySet()) {
            put(editor, entry.getKey(), entry.getValue());
        }
        editor.apply();
    }

    public static void putDomainRules(ProxyDomainRuleSet rules) {
        Prefers.put(DOMAIN_RULES, rules == null ? "" : rules.serialize());
    }

    public static void reset() {
        SharedPreferences.Editor editor = Prefers.getPrefers().edit();
        for (String key : ProxyRuntimeConfigCodec.keys()) editor.remove(key);
        editor.remove(DOMAIN_RULES);
        editor.apply();
    }

    private static void put(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean bool) {
            editor.putBoolean(key, bool);
        } else if (value instanceof Integer integer) {
            editor.putInt(key, integer);
        } else if (value instanceof Long longValue) {
            editor.putLong(key, longValue);
        } else if (value instanceof String text) {
            editor.putString(key, text);
        } else {
            throw new IllegalArgumentException("Unsupported preference value for " + key);
        }
    }
}
