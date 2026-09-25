package com.fongmi.android.tv.server.proxy;

import com.fongmi.android.tv.setting.MultiThreadProxySetting;

import java.io.IOException;
import java.util.Map;

public final class MultiThreadProxy {

    private static final ProxyPortController CONTROLLER = new ProxyPortController();

    private MultiThreadProxy() {
    }

    public static ProxyPortController.Snapshot applyStored() throws IOException {
        return apply(MultiThreadProxySetting.get(), MultiThreadProxySetting.getDomainRules());
    }

    public static ProxyPortController.Snapshot apply(ProxyRuntimeConfig config) throws IOException {
        return apply(config, ProxyDomainRuleSet.EMPTY);
    }

    public static ProxyPortController.Snapshot apply(
            ProxyRuntimeConfig config,
            ProxyDomainRuleSet rules) throws IOException {
        return CONTROLLER.apply(config, rules);
    }

    public static ProxyPortController.Snapshot snapshot() {
        return CONTROLLER.snapshot();
    }

    public static String capabilityToken() {
        return CONTROLLER.capabilityToken();
    }

    public static ProxyStreamRegistration register(
            String url,
            Map<String, String> headers) throws IOException {
        return CONTROLLER.register(url, headers);
    }

    public static void stop() {
        CONTROLLER.close();
    }
}