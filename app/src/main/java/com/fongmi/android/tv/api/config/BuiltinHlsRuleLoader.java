package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.HlsAdRule;
import com.fongmi.android.tv.bean.HlsRulePackage;
import com.github.catvod.utils.Asset;

import java.util.List;

public final class BuiltinHlsRuleLoader {

    private static final String ASSET = "rules/hls_rules.json";
    private static volatile HlsRulePackage cached;

    private BuiltinHlsRuleLoader() {}

    public static HlsRulePackage getPackage() {
        HlsRulePackage value = cached;
        if (value != null) return value;
        synchronized (BuiltinHlsRuleLoader.class) {
            if (cached == null) cached = load();
            return cached;
        }
    }

    public static List<HlsAdRule> getRules() {
        return getPackage().getRules();
    }

    private static HlsRulePackage load() {
        return HlsRulePackage.parse(Asset.read(ASSET));
    }
}
