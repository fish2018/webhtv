package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.ImportedAdRuleCandidate;
import com.fongmi.android.tv.bean.Rule;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class InterfaceAdRuleAnalyzer {

    private static final int MAX_ITEMS = 200;
    private static final int MAX_VALUES = 32;
    private static final int MAX_REGEX_LENGTH = 2048;
    private static final Pattern HOST = Pattern.compile("^(?:[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?\\.)+[a-z0-9-]{2,63}$");

    private InterfaceAdRuleAnalyzer() {
    }

    public static List<ImportedAdRuleCandidate> analyze(String sourceName, String sourceUrl,
            List<String> ads, List<Rule> rules) {
        Map<String, ImportedAdRuleCandidate> result = new LinkedHashMap<>();
        String sourceHash = hash(normalizeSourceUrl(sourceUrl));
        int adCount = Math.min(ads == null ? 0 : ads.size(), MAX_ITEMS);
        for (int i = 0; i < adCount; i++) {
            String host = normalizeHost(ads.get(i));
            if (!isSafeHost(host)) continue;
            add(result, candidate(sourceName, sourceHash, host, "ads", List.of(host), List.of(), List.of(), 0.8f,
                    ImportedAdRuleCandidate.RISK_LOW, List.of("来自接口 ads 域名黑名单")));
        }
        int ruleCount = Math.min(rules == null ? 0 : rules.size(), MAX_ITEMS);
        for (int i = 0; i < ruleCount; i++) analyzeRule(result, sourceName, sourceHash, rules.get(i));
        return new ArrayList<>(result.values());
    }

    private static void analyzeRule(Map<String, ImportedAdRuleCandidate> result, String sourceName,
            String sourceHash, Rule rule) {
        if (rule == null || !rule.getScript().isEmpty()) return;
        List<String> hosts = normalizeHosts(rule.getHosts());
        // External Rule semantics are opposite to UserAdRule:
        // Rule.exclude rejects ad URLs, Rule.regex accepts/protects valid media URLs.
        List<String> regex = safeRegex(rule.getExclude());
        List<String> exclude = safeRegex(rule.getRegex());
        if (regex.isEmpty()) return;
        String joined = String.join(" ", regex).toLowerCase(Locale.ROOT);
        String name = rule.getName().toLowerCase(Locale.ROOT);
        if (isSniffRule(name, joined)) return;
        boolean m3u8 = joined.contains("#ext-x-discontinuity") || joined.contains("#extinf") || joined.contains("\\.ts");
        boolean adSignal = containsAdSignal(name + " " + joined + " " + String.join(" ", hosts));
        if (!m3u8 && !adSignal) return;
        float confidence = m3u8 ? 0.65f : 0.6f;
        if (adSignal) confidence += 0.15f;
        String risk = m3u8 ? ImportedAdRuleCandidate.RISK_MEDIUM : ImportedAdRuleCandidate.RISK_LOW;
        List<String> reasons = new ArrayList<>();
        if (m3u8) reasons.add("包含 M3U8 广告边界或切片特征");
        if (adSignal) reasons.add("包含明确广告域名、路径或名称特征");
        add(result, candidate(sourceName, sourceHash, rule.getName(), "rules", hosts, regex, exclude,
                Math.min(confidence, 1f), risk, reasons));
    }

    private static ImportedAdRuleCandidate candidate(String sourceName, String sourceHash, String ruleName,
            String sourceType, List<String> hosts, List<String> regex, List<String> exclude,
            float confidence, String risk, List<String> reasons) {
        ImportedAdRuleCandidate item = new ImportedAdRuleCandidate();
        item.setHosts(hosts);
        item.setRegex(regex);
        item.setExclude(exclude);
        item.setFingerprint(fingerprint(hosts, regex, exclude));
        item.setId(item.getFingerprint());
        String fallback = hosts.isEmpty() ? "去广规则" : hosts.get(0);
        item.setName((sourceName == null || sourceName.isBlank() ? "接口" : sourceName) + "导入：" +
                (ruleName == null || ruleName.isBlank() ? fallback : ruleName));
        item.setSourceConfigName(sourceName);
        item.setSourceConfigUrlHash(sourceHash);
        item.setSourceRuleName(ruleName);
        item.setSourceType(sourceType);
        item.setClassification(ImportedAdRuleCandidate.CLASS_AD_BLOCK);
        item.setConfidence(confidence);
        item.setRiskLevel(risk);
        item.setReasons(reasons);
        item.setStatus(ImportedAdRuleCandidate.STATUS_PENDING);
        long now = System.currentTimeMillis();
        item.setFirstSeenAt(now);
        item.setLastSeenAt(now);
        item.setSeenCount(1);
        return item;
    }

    private static void add(Map<String, ImportedAdRuleCandidate> result, ImportedAdRuleCandidate item) {
        result.putIfAbsent(item.getFingerprint(), item);
    }

    private static List<String> normalizeHosts(List<String> input) {
        return input == null ? List.of() : input.stream().limit(MAX_VALUES).map(InterfaceAdRuleAnalyzer::normalizeHost)
                .filter(InterfaceAdRuleAnalyzer::isSafeHost).distinct().sorted().toList();
    }

    private static List<String> safeRegex(List<String> input) {
        if (input == null) return List.of();
        List<String> result = new ArrayList<>();
        for (String value : input.stream().limit(MAX_VALUES).toList()) {
            if (value == null) continue;
            String regex = value.trim();
            if (regex.isEmpty() || regex.length() > MAX_REGEX_LENGTH || isDangerousRegex(regex)) continue;
            try {
                Pattern.compile(regex);
                result.add(regex);
            } catch (RuntimeException ignored) {
            }
        }
        return result.stream().distinct().sorted().toList();
    }

    private static boolean isDangerousRegex(String regex) {
        String compact = regex.replaceAll("\\s+", "");
        if (".*".equals(compact) || ".+".equals(compact)) return true;
        // Reject nested/repeated quantifiers and backreferences. Java Pattern has no execution timeout,
        // so external expressions must stay within a conservative linear-ish subset.
        if (compact.matches(".*\\([^)]*[+*][^)]*\\)[+*{].*")) return true;
        if (compact.matches(".*(?:\\.\\*|\\.\\+)[+*{].*")) return true;
        if (compact.matches(".*\\\\[1-9].*")) return true;
        return compact.contains("(?R") || compact.contains("(?0");
    }

    private static String normalizeHost(String value) {
        if (value == null) return "";
        String host = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (host.contains("://")) host = URI.create(host).getHost();
        } catch (RuntimeException ignored) {
            return "";
        }
        if (host == null) return "";
        int port = host.indexOf(':');
        if (port >= 0) host = host.substring(0, port);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host;
    }

    private static boolean isSafeHost(String host) {
        return host.length() <= 253 && HOST.matcher(host).matches() && !host.startsWith("*.");
    }

    private static boolean containsAdSignal(String value) {
        String text = value.toLowerCase(Locale.ROOT);
        return text.contains("广告") || text.contains("adserver") || text.contains("adservice")
                || text.contains("/ads/") || text.contains("/ad/") || text.contains("preroll")
                || text.contains("midroll") || text.contains("commercial") || text.matches(".*(?:^|[._-])ads?(?:[._-]|$).*?");
    }

    private static boolean isSniffRule(String name, String regex) {
        return name.contains("嗅探") || regex.contains("is_play_url=") || regex.contains("item_id=")
                || regex.contains("video/tos/") || regex.contains("m3u8?pt=m3u8");
    }

    private static String normalizeSourceUrl(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value);
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null).toString();
        } catch (Exception e) {
            return "";
        }
    }

    static String fingerprint(List<String> hosts, List<String> regex, List<String> exclude) {
        return hash("hosts\n" + sorted(hosts) + "\nregex\n" + sorted(regex) + "\nexclude\n" + sorted(exclude));
    }

    private static String sorted(List<String> values) {
        return values == null ? "" : values.stream().sorted(Comparator.naturalOrder()).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
