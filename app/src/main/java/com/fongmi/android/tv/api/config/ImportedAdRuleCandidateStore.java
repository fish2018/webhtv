package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.ImportedAdRuleCandidate;
import com.fongmi.android.tv.bean.UserAdRule;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class ImportedAdRuleCandidateStore {

    private static final String PREF_KEY = "imported_ad_rule_candidates";
    private static final int MAX_STORED = 500;
    private static final Type LIST_TYPE = new TypeToken<List<ImportedAdRuleCandidate>>() {}.getType();

    private ImportedAdRuleCandidateStore() {
    }

    public static synchronized List<ImportedAdRuleCandidate> load() {
        try {
            List<ImportedAdRuleCandidate> items = App.gson().fromJson(Prefers.getString(PREF_KEY, "[]"), LIST_TYPE);
            return items == null ? new ArrayList<>() : items;
        } catch (Throwable e) {
            return new ArrayList<>();
        }
    }

    public static synchronized void merge(List<ImportedAdRuleCandidate> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        List<ImportedAdRuleCandidate> stored = load();
        mergeInto(stored, incoming, ImportedAdRuleCandidateStore::alreadyImported, System.currentTimeMillis());
        trim(stored);
        save(stored);
    }

    static void mergeInto(List<ImportedAdRuleCandidate> stored, List<ImportedAdRuleCandidate> incoming,
            Predicate<String> isImported, long now) {
        for (ImportedAdRuleCandidate candidate : incoming) {
            ImportedAdRuleCandidate existing = find(stored, candidate.getFingerprint());
            if (existing == null) {
                stored.add(candidate);
            } else {
                existing.setLastSeenAt(now);
                existing.setSeenCount(existing.getSeenCount() + 1);
                if (candidate.getConfidence() > existing.getConfidence()) existing.setConfidence(candidate.getConfidence());
                if (ImportedAdRuleCandidate.STATUS_IMPORTED.equals(existing.getStatus())
                        && !isImported.test(existing.getFingerprint())) {
                    existing.setStatus(ImportedAdRuleCandidate.STATUS_PENDING);
                }
            }
        }
    }

    private static void trim(List<ImportedAdRuleCandidate> stored) {
        if (stored.size() > MAX_STORED) {
            stored.sort((a, b) -> {
                boolean aPending = ImportedAdRuleCandidate.STATUS_PENDING.equals(a.getStatus());
                boolean bPending = ImportedAdRuleCandidate.STATUS_PENDING.equals(b.getStatus());
                if (aPending != bPending) return aPending ? -1 : 1;
                return Long.compare(b.getLastSeenAt(), a.getLastSeenAt());
            });
            stored.subList(MAX_STORED, stored.size()).clear();
        }
    }

    public static synchronized List<ImportedAdRuleCandidate> pending() {
        return load().stream().filter(item -> ImportedAdRuleCandidate.STATUS_PENDING.equals(item.getStatus())).toList();
    }

    public static synchronized boolean importCandidate(String id) {
        List<ImportedAdRuleCandidate> stored = load();
        ImportedAdRuleCandidate candidate = findById(stored, id);
        if (candidate == null || !ImportedAdRuleCandidate.STATUS_PENDING.equals(candidate.getStatus())) return false;
        if (alreadyImported(candidate.getFingerprint())) {
            candidate.setStatus(ImportedAdRuleCandidate.STATUS_IMPORTED);
            save(stored);
            return true;
        }
        UserAdRuleStore.add(UserAdRule.fromImportedCandidate(candidate));
        candidate.setStatus(ImportedAdRuleCandidate.STATUS_IMPORTED);
        save(stored);
        return true;
    }

    public static synchronized void ignore(String id) {
        List<ImportedAdRuleCandidate> stored = load();
        ImportedAdRuleCandidate candidate = findById(stored, id);
        if (candidate == null) return;
        candidate.setStatus(ImportedAdRuleCandidate.STATUS_IGNORED);
        save(stored);
    }

    public static synchronized void reopen(String id) {
        if (id == null || id.isBlank()) return;
        List<ImportedAdRuleCandidate> stored = load();
        if (reopen(stored, id)) save(stored);
    }

    static boolean reopen(List<ImportedAdRuleCandidate> stored, String id) {
        ImportedAdRuleCandidate candidate = findById(stored, id);
        if (candidate == null || !ImportedAdRuleCandidate.STATUS_IMPORTED.equals(candidate.getStatus())) return false;
        candidate.setStatus(ImportedAdRuleCandidate.STATUS_PENDING);
        return true;
    }

    private static boolean alreadyImported(String fingerprint) {
        for (UserAdRule rule : UserAdRuleStore.load()) {
            String existing = InterfaceAdRuleAnalyzer.fingerprint(rule.getHosts(), rule.getRegex(), rule.getExclude());
            if (fingerprint.equals(existing)) return true;
        }
        return false;
    }

    private static ImportedAdRuleCandidate find(List<ImportedAdRuleCandidate> items, String fingerprint) {
        for (ImportedAdRuleCandidate item : items) if (fingerprint.equals(item.getFingerprint())) return item;
        return null;
    }

    private static ImportedAdRuleCandidate findById(List<ImportedAdRuleCandidate> items, String id) {
        for (ImportedAdRuleCandidate item : items) if (id.equals(item.getId())) return item;
        return null;
    }

    private static void save(List<ImportedAdRuleCandidate> items) {
        Prefers.put(PREF_KEY, App.gson().toJson(items));
    }
}
