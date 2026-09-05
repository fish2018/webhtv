package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchPageStateTest {

    @Test
    public void emptyPageDoesNotCommitAndDoesNotDropPendingSite() {
        SearchPageState state = new SearchPageState();
        state.recordInitial("site-a", 4);

        assertTrue(state.begin("site-a", 2));
        assertFalse(state.begin("site-b", 2));

        SearchPageState.Completion completion = state.complete("site-a", false, 4);

        assertFalse(completion.accepted());
        assertEquals(1, state.getPage("site-a"));
        assertTrue(state.begin("site-a", 2));
    }

    @Test
    public void successfulPageCommitsOnlyItsOwnSite() {
        SearchPageState state = new SearchPageState();
        state.recordInitial("site-a", 3);
        state.recordInitial("site-b", 2);

        assertTrue(state.begin("site-a", 2));
        SearchPageState.Completion completion = state.complete("site-a", true, 3);

        assertTrue(completion.accepted());
        assertEquals("site-a", completion.siteKey());
        assertEquals(2, state.getPage("site-a"));
        assertEquals(1, state.getPage("site-b"));
    }

    @Test
    public void preciseEmptyPageCanContinueUntilKnownEnd() {
        SearchPageState state = new SearchPageState();
        state.recordInitial("site-a", 3);

        assertTrue(state.shouldContinue("site-a", true, true, false));
        assertTrue(state.begin("site-a", 2));
        state.complete("site-a", true, 3);
        assertTrue(state.shouldContinue("site-a", true, true, false));

        assertTrue(state.begin("site-a", 3));
        state.complete("site-a", true, 3);
        assertFalse(state.shouldContinue("site-a", true, true, false));
    }

    @Test
    public void pendingRequestBlocksFilterDrivenDuplicateRequest() {
        SearchPageState state = new SearchPageState();
        state.recordInitial("site-a", 0);

        assertTrue(state.begin("site-a", 2));
        assertFalse(state.shouldContinue("site-a", true, true, false));
        SearchPageState.Completion completion = state.complete("site-a", false, 0);

        assertFalse(completion.accepted());
        assertFalse(state.hasPending());
    }

    @Test
    public void resultFromAnotherSiteDoesNotCommitRequestedPage() {
        SearchPageState state = new SearchPageState();
        state.recordInitial("site-a", 3);

        assertTrue(state.begin("site-a", 2));
        SearchPageState.Completion completion = state.complete("site-b", true, 3);

        assertFalse(completion.accepted());
        assertEquals(1, state.getPage("site-a"));
        assertTrue(state.begin("site-a", 2));
    }

    @Test
    public void siteSwitchRestoresEachCommittedCursor() {
        SearchPageState state = new SearchPageState();
        state.recordInitial("site-a", 4);
        state.recordInitial("site-b", 5);

        assertTrue(state.begin("site-a", 2));
        state.complete("site-a", true, 4);
        assertTrue(state.begin("site-b", 2));
        state.complete("site-b", true, 5);
        assertTrue(state.begin("site-b", 3));
        state.complete("site-b", true, 5);

        assertEquals(2, state.getPage("site-a"));
        assertEquals(3, state.getPage("site-b"));
    }

    @Test
    public void repeatedPageStopsUnknownPaginationWithoutCommitting() {
        SearchPageState state = new SearchPageState();
        state.recordInitial("site-a", 0, "same-page");

        assertTrue(state.begin("site-a", 2));
        SearchPageState.Completion completion = state.complete("site-a", true, 0, "same-page");

        assertFalse(completion.accepted());
        assertEquals(1, state.getPage("site-a"));
        assertFalse(state.canLoadMore("site-a"));
        assertFalse(state.shouldContinue("site-a", true, true, false));
    }

    @Test
    public void uniquePageStillCommitsWhenPageCountIsUnknown() {
        SearchPageState state = new SearchPageState();
        state.recordInitial("site-a", 0, "page-one");

        assertTrue(state.begin("site-a", 2));
        SearchPageState.Completion completion = state.complete("site-a", true, 0, "page-two");

        assertTrue(completion.accepted());
        assertEquals(2, state.getPage("site-a"));
        assertTrue(state.canLoadMore("site-a"));
    }
}
