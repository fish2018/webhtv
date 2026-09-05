package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public final class HlsRuleStateStore {

    private static final String PREF_KEY = "builtin_hls_rule_overrides";
    private static final Type MAP_TYPE = new TypeToken<Map<String, Boolean>>() {}.getType();

    private HlsRuleStateStore() {}

    public static synchronized Map<String, Boolean> load() {
        try {
            Map<String, Boolean> value = App.gson().fromJson(Prefers.getString(PREF_KEY, "{}"), MAP_TYPE);
            return value == null ? new HashMap<>() : new HashMap<>(value);
        } catch (Throwable e) {
            return new HashMap<>();
        }
    }

    public static synchronized void setEnabled(String stateKey, boolean enabled) {
        if (stateKey == null || stateKey.isBlank()) return;
        Map<String, Boolean> states = load();
        states.put(stateKey, enabled);
        Prefers.put(PREF_KEY, App.gson().toJson(states));
        HlsRuleConfig.invalidate();
    }

    public static synchronized void clearOverride(String stateKey) {
        Map<String, Boolean> states = load();
        if (states.remove(stateKey) != null) {
            Prefers.put(PREF_KEY, App.gson().toJson(states));
            HlsRuleConfig.invalidate();
        }
    }
}
