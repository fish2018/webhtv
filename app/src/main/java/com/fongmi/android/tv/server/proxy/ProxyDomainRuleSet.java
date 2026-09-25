package com.fongmi.android.tv.server.proxy;

import java.net.IDN;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class ProxyDomainRuleSet {

    public static final ProxyDomainRuleSet EMPTY = new ProxyDomainRuleSet(List.of());

    private final List<Rule> rules;

    private ProxyDomainRuleSet(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static ProxyDomainRuleSet parse(String text) {
        if (text == null || text.isBlank()) return EMPTY;
        List<Rule> rules = new ArrayList<>();
        Set<String> claimedDomains = new HashSet<>();
        String[] lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int separator = line.indexOf("=");
            if (separator <= 0 || separator != line.lastIndexOf("=")) {
                throw invalid(index, "expected domains=threads,shards");
            }
            String[] values = line.substring(separator + 1).split(",", -1);
            if (values.length != 2) throw invalid(index, "expected threads,shards");
            int concurrency = positive(values[0], index, "threads",
                    ProxyRuntimeConfigValidator.MAX_RANGE_CONCURRENCY);
            int shardCount = positive(values[1], index, "shards",
                    ProxyRuntimeConfigValidator.MAX_SHARD_COUNT);

            List<String> domains = new ArrayList<>();
            for (String value : line.substring(0, separator).split(",", -1)) {
                String domain = normalizeDomain(value, index);
                if (!claimedDomains.add(domain)) throw invalid(index, "duplicate domain: " + domain);
                domains.add(domain);
            }
            if (domains.isEmpty()) throw invalid(index, "at least one domain is required");
            rules.add(new Rule(domains, concurrency, shardCount));
        }
        return rules.isEmpty() ? EMPTY : new ProxyDomainRuleSet(rules);
    }

    public static String extractHost(String url) {
        String host = host(url);
        if (host == null) return null;
        try {
            return normalizeDomain(host, 0);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public ProxyRuntimeConfig resolve(String url, ProxyRuntimeConfig global) {
        Objects.requireNonNull(global, "global");
        if (!global.enabled() || rules.isEmpty()) return global;
        String host = host(url);
        if (host == null) return global;
        for (Rule rule : rules) {
            if (rule.matches(host)) return withParallelPlan(global, rule.rangeConcurrency(), rule.shardCount());
        }
        return global;
    }

    public int size() {
        return rules.size();
    }

    public int maxConcurrency() {
        int max = 0;
        for (Rule rule : rules) max = Math.max(max, rule.rangeConcurrency());
        return max;
    }

    public String serialize() {
        StringBuilder builder = new StringBuilder();
        for (Rule rule : rules) {
            if (builder.length() > 0) builder.append("\n");
            builder.append(String.join(",", rule.domains()));
            builder.append("=").append(rule.rangeConcurrency()).append(",").append(rule.shardCount());
        }
        return builder.toString();
    }

    public List<Rule> rules() {
        return rules;
    }

    private static ProxyRuntimeConfig withParallelPlan(
            ProxyRuntimeConfig config,
            int concurrency,
            int shardCount) {
        return new ProxyRuntimeConfig(
                config.schemaVersion(),
                config.enabled(),
                config.portMode(),
                config.configuredPort(),
                config.serverWorkers(),
                config.connectionQueueCapacity(),
                config.rangeWorkers(),
                concurrency,
                ProxyRuntimeConfig.ShardMode.COUNT,
                shardCount,
                config.chunkSizeBytes(),
                config.maxSessions(),
                config.reorderWindowBlocks(),
                config.bufferBudgetBytes(),
                config.retryCount(),
                config.connectTimeoutMillis(),
                config.readTimeoutMillis(),
                config.fileThresholdBytes());
    }

    private static int positive(String value, int line, String label, int maximum) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) throw invalid(line, label + " must be positive");
            if (parsed > maximum) throw invalid(line, label + " exceeds safety limit " + maximum);
            return parsed;
        } catch (NumberFormatException e) {
            throw invalid(line, label + " must be an integer");
        }
    }

    private static String normalizeDomain(String value, int line) {
        String domain = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if (domain.startsWith("*.")) domain = domain.substring(2);
        else if (domain.startsWith(".")) domain = domain.substring(1);
        while (domain.endsWith(".")) domain = domain.substring(0, domain.length() - 1);
        if (domain.isEmpty()) throw invalid(line, "domain is empty");
        if (domain.contains("/") || domain.contains(":") || domain.contains("@") || domain.contains("*")) {
            throw invalid(line, "domain must not contain a scheme, path, port, or wildcard outside the leading label");
        }
        try {
            domain = IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.US);
        } catch (IllegalArgumentException e) {
            throw invalid(line, "invalid domain");
        }
        if (domain.isEmpty() || domain.length() > 253 || domain.startsWith("-") || domain.endsWith("-")) {
            throw invalid(line, "invalid domain");
        }
        return domain;
    }

    private static String host(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) return null;
            return normalizeHost(host);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalizeHost(String host) {
        String normalized = host.trim().toLowerCase(Locale.US);
        while (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);
        try {
            return IDN.toASCII(normalized, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.US);
        } catch (IllegalArgumentException e) {
            return normalized;
        }
    }

    private static IllegalArgumentException invalid(int zeroBasedLine, String reason) {
        return new IllegalArgumentException("Invalid domain rule at line " + (zeroBasedLine + 1) + ": " + reason);
    }

    @Override
    public boolean equals(Object object) {
        return this == object
                || object instanceof ProxyDomainRuleSet other && rules.equals(other.rules);
    }

    @Override
    public int hashCode() {
        return rules.hashCode();
    }

    @Override
    public String toString() {
        return "ProxyDomainRuleSet{rules=" + rules.size() + "}";
    }

    public record Rule(List<String> domains, int rangeConcurrency, int shardCount) {

        public Rule {
            domains = List.copyOf(domains);
            if (domains.isEmpty()) throw new IllegalArgumentException("domains are required");
            if (rangeConcurrency <= 0 || shardCount <= 0) throw new IllegalArgumentException("parallel plan must be positive");
        }

        boolean matches(String host) {
            for (String domain : domains) {
                if (host.equals(domain) || host.endsWith("." + domain)) return true;
            }
            return false;
        }
    }
}