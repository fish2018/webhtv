package com.fongmi.android.tv.server.proxy;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public final class ProxyPortController implements AutoCloseable {

    private final ProxyCapabilityToken capabilityToken;
    private ProxyRuntimeConfig activeConfig;
    private ProxyDomainRuleSet activeRules;
    private MultiThreadProxyServer server;
    private long configRevision;

    public ProxyPortController() {
        this(ProxyCapabilityToken.generate());
    }

    ProxyPortController(ProxyCapabilityToken capabilityToken) {
        this.capabilityToken = Objects.requireNonNull(capabilityToken, "capabilityToken");
        this.activeConfig = ProxyRuntimeConfig.defaults();
        this.activeRules = ProxyDomainRuleSet.EMPTY;
    }

    public synchronized Snapshot apply(ProxyRuntimeConfig config) throws IOException {
        return apply(config, ProxyDomainRuleSet.EMPTY);
    }

    public synchronized Snapshot apply(
            ProxyRuntimeConfig config,
            ProxyDomainRuleSet rules) throws IOException {
        ProxyRuntimeConfigValidator.requireValid(config);
        rules = rules == null ? ProxyDomainRuleSet.EMPTY : rules;
        if (!config.enabled()) return disable(config, rules);
        if (config.equals(activeConfig)
                && rules.equals(activeRules)
                && server != null
                && server.isReady()) {
            return snapshot();
        }
        if (requiresSamePortRestart(config)) return restartSamePort(config, rules);
        return startBeforeStop(config, rules);
    }

    public synchronized Snapshot snapshot() {
        boolean ready = server != null && server.isReady();
        int actualPort = ready ? server.actualPort() : MultiThreadProxyServer.UNBOUND_PORT;
        return new Snapshot(configRevision, activeConfig, actualPort, ready);
    }

    public synchronized String capabilityToken() {
        return capabilityToken.value();
    }

    public synchronized ProxyStreamRegistration register(
            String url,
            Map<String, String> headers) throws IOException {
        if (server == null || !server.isReady()) throw new IOException("multi-thread proxy is not ready");
        return server.registerTarget(url, headers);
    }

    @Override
    public synchronized void close() {
        MultiThreadProxyServer previous = server;
        server = null;
        if (previous != null) previous.close();
    }

    private Snapshot startBeforeStop(
            ProxyRuntimeConfig config,
            ProxyDomainRuleSet rules) throws IOException {
        MultiThreadProxyServer candidate = startCandidate(config, rules, revisionFor(config, rules));
        MultiThreadProxyServer previous = server;
        server = candidate;
        updateActive(config, rules);
        Snapshot published = snapshot();
        if (previous != null) previous.close();
        return published;
    }

    private Snapshot restartSamePort(
            ProxyRuntimeConfig config,
            ProxyDomainRuleSet rules) throws IOException {
        ProxyRuntimeConfig previousConfig = activeConfig;
        ProxyDomainRuleSet previousRules = activeRules;
        MultiThreadProxyServer previous = server;
        server = null;
        previous.close();
        try {
            MultiThreadProxyServer candidate = startCandidate(config, rules, revisionFor(config, rules));
            server = candidate;
            updateActive(config, rules);
            return snapshot();
        } catch (IOException | RuntimeException failure) {
            restore(previousConfig, previousRules, failure);
            throw failure;
        }
    }

    private MultiThreadProxyServer startCandidate(
            ProxyRuntimeConfig config,
            ProxyDomainRuleSet rules,
            long revision) throws IOException {
        MultiThreadProxyServer candidate = new MultiThreadProxyServer(config, capabilityToken, revision, rules);
        try {
            candidate.startServer();
            if (!candidate.isReady()) throw new IOException("Multi-thread proxy candidate is not ready");
            return candidate;
        } catch (IOException | RuntimeException e) {
            candidate.close();
            throw e;
        }
    }

    private void restore(
            ProxyRuntimeConfig config,
            ProxyDomainRuleSet rules,
            Throwable failure) {
        try {
            server = startCandidate(config, rules, configRevision);
        } catch (IOException | RuntimeException restoreFailure) {
            server = null;
            failure.addSuppressed(restoreFailure);
        }
    }

    private boolean requiresSamePortRestart(ProxyRuntimeConfig config) {
        return server != null
                && server.isReady()
                && config.portMode() == ProxyRuntimeConfig.PortMode.FIXED
                && config.configuredPort() == server.actualPort();
    }

    private Snapshot disable(ProxyRuntimeConfig config, ProxyDomainRuleSet rules) {
        MultiThreadProxyServer previous = server;
        server = null;
        updateActive(config, rules);
        Snapshot published = snapshot();
        if (previous != null) previous.close();
        return published;
    }

    private long revisionFor(ProxyRuntimeConfig config, ProxyDomainRuleSet rules) {
        return config.equals(activeConfig) && rules.equals(activeRules)
                ? configRevision
                : configRevision + 1;
    }

    private void updateActive(ProxyRuntimeConfig config, ProxyDomainRuleSet rules) {
        if (!config.equals(activeConfig) || !rules.equals(activeRules)) configRevision++;
        activeConfig = config;
        activeRules = rules;
    }

    public record Snapshot(
            long configRevision,
            ProxyRuntimeConfig config,
            int actualPort,
            boolean ready) {
    }
}