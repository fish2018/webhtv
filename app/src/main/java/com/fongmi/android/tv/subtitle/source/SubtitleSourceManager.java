package com.fongmi.android.tv.subtitle.source;

import com.fongmi.android.tv.subtitle.provider.SubtitleProviderRegistry;
import com.fongmi.android.tv.subtitle.provider.T3SubtitleProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SubtitleSourceManager {

    private final SubtitleProviderRegistry registry;
    private final SubtitleScriptRuntimeFactory runtimeFactory;
    private final Set<String> reservedProviderNames;
    private final List<T3SubtitleProvider> activeProviders = new ArrayList<>();

    public SubtitleSourceManager(SubtitleProviderRegistry registry, SubtitleScriptRuntimeFactory runtimeFactory) {
        if (registry == null) throw new IllegalArgumentException("registry is null");
        if (runtimeFactory == null) throw new IllegalArgumentException("runtimeFactory is null");
        this.registry = registry;
        this.runtimeFactory = runtimeFactory;
        this.reservedProviderNames = new HashSet<>();
        reservedProviderNames.addAll(registry.providerNames());
    }

    public synchronized List<T3SubtitleProvider> install(String subscriptionId, String json, String baseUrl) {
        String namespace = subscriptionId == null || subscriptionId.isBlank() ? "subscription" : subscriptionId.trim();
        SubtitleSubscription subscription = SubtitleSubscription.parse(json, baseUrl);
        List<T3SubtitleProvider> staged = new ArrayList<>();
        Set<String> names = new HashSet<>(reservedProviderNames);
        try {
            for (SubtitleSourceConfig source : subscription.getSubtitles()) {
                if (!source.isEnabled() || source.getType() != 3 || !names.add(source.getKey())) continue;
                SubtitleScriptRuntime runtime = runtimeFactory.create(source.getApi());
                T3SubtitleProvider provider = new T3SubtitleProvider(source, runtime, namespace + ":" + source.getKey());
                provider.initialize(SubtitleSubscription.resolve(baseUrl, source.getExt()));
                staged.add(provider);
            }
        } catch (Exception error) {
            destroy(staged);
            throw new IllegalStateException("Unable to initialize subtitle subscription", error);
        }
        unregister(activeProviders);
        destroy(activeProviders);
        activeProviders.clear();
        activeProviders.addAll(staged);
        for (T3SubtitleProvider provider : activeProviders) registry.register(provider);
        return Collections.unmodifiableList(new ArrayList<>(activeProviders));
    }

    public synchronized List<T3SubtitleProvider> getActiveProviders() {
        return Collections.unmodifiableList(new ArrayList<>(activeProviders));
    }

    public synchronized void destroy() {
        unregister(activeProviders);
        destroy(activeProviders);
        activeProviders.clear();
    }

    private void unregister(List<T3SubtitleProvider> providers) {
        for (T3SubtitleProvider provider : providers) registry.unregister(provider.getName());
    }

    private static void destroy(List<T3SubtitleProvider> providers) {
        for (T3SubtitleProvider provider : providers) {
            try {
                provider.destroy();
            } catch (Throwable ignored) {
            }
        }
    }
}
