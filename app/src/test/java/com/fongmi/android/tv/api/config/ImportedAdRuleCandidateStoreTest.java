package com.fongmi.android.tv.api.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.bean.ImportedAdRuleCandidate;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ImportedAdRuleCandidateStoreTest {

    @Test
    public void mergeReopensImportedCandidateWhenItsUserRuleWasDeleted() {
        ImportedAdRuleCandidate storedCandidate = candidate("rule-1", ImportedAdRuleCandidate.STATUS_IMPORTED);
        List<ImportedAdRuleCandidate> stored = new ArrayList<>(List.of(storedCandidate));

        ImportedAdRuleCandidateStore.mergeInto(
                stored,
                List.of(candidate("rule-1", ImportedAdRuleCandidate.STATUS_PENDING)),
                fingerprint -> false,
                200L);

        assertEquals(ImportedAdRuleCandidate.STATUS_PENDING, storedCandidate.getStatus());
        assertEquals(200L, storedCandidate.getLastSeenAt());
        assertEquals(2, storedCandidate.getSeenCount());
    }

    @Test
    public void mergeKeepsImportedCandidateHiddenWhileMatchingUserRuleExists() {
        ImportedAdRuleCandidate storedCandidate = candidate("rule-1", ImportedAdRuleCandidate.STATUS_IMPORTED);
        List<ImportedAdRuleCandidate> stored = new ArrayList<>(List.of(storedCandidate));

        ImportedAdRuleCandidateStore.mergeInto(
                stored,
                List.of(candidate("rule-1", ImportedAdRuleCandidate.STATUS_PENDING)),
                fingerprint -> true,
                200L);

        assertEquals(ImportedAdRuleCandidate.STATUS_IMPORTED, storedCandidate.getStatus());
    }

    @Test
    public void deletingImportedUserRuleReopensCandidateImmediately() {
        ImportedAdRuleCandidate imported = candidate("rule-1", ImportedAdRuleCandidate.STATUS_IMPORTED);
        ImportedAdRuleCandidate ignored = candidate("rule-2", ImportedAdRuleCandidate.STATUS_IGNORED);
        List<ImportedAdRuleCandidate> stored = new ArrayList<>(List.of(imported, ignored));

        assertTrue(ImportedAdRuleCandidateStore.reopen(stored, "rule-1"));
        assertEquals(ImportedAdRuleCandidate.STATUS_PENDING, imported.getStatus());
        assertFalse(ImportedAdRuleCandidateStore.reopen(stored, "rule-2"));
        assertEquals(ImportedAdRuleCandidate.STATUS_IGNORED, ignored.getStatus());
    }

    private static ImportedAdRuleCandidate candidate(String id, String status) {
        ImportedAdRuleCandidate candidate = new ImportedAdRuleCandidate();
        candidate.setId(id);
        candidate.setFingerprint(id);
        candidate.setStatus(status);
        candidate.setConfidence(0.5f);
        candidate.setSeenCount(1);
        candidate.setLastSeenAt(100L);
        return candidate;
    }
}
