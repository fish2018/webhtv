package com.fongmi.android.tv.history;

import com.fongmi.android.tv.bean.TmdbSeasonScope;
import com.fongmi.android.tv.bean.TmdbSeasonSegment;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbSeasonScopeTest {

    @Test
    public void unknownDoesNotAcceptAnySeason() {
        TmdbSeasonScope scope = TmdbSeasonScope.unknown();

        assertFalse(scope.accepts(1));
        assertFalse(scope.accepts(0));
    }

    @Test
    public void multiAcceptsOnlyCoveredSeasons() {
        TmdbSeasonScope scope = TmdbSeasonScope.multi(List.of(1, 2));

        assertTrue(scope.accepts(1));
        assertTrue(scope.accepts(2));
        assertFalse(scope.accepts(3));
    }

    @Test
    public void specialSeasonZeroIsKnownOnlyWhenExplicitlyCreated() {
        assertEquals(TmdbSeasonScope.Kind.UNKNOWN, TmdbSeasonScope.unknown().getKind());
        assertEquals(Integer.valueOf(0), TmdbSeasonScope.known(0).getSeasonNumber());
    }

    @Test
    public void invalidKnownAndSingleSeasonMultiBecomeUnknown() {
        assertEquals(TmdbSeasonScope.Kind.UNKNOWN, TmdbSeasonScope.known(-1).getKind());
        assertEquals(TmdbSeasonScope.Kind.UNKNOWN, TmdbSeasonScope.multi(List.of(1, 1, -1)).getKind());
    }

    @Test
    public void multiSegmentsKeepSourceAndTmdbBoundaries() {
        TmdbSeasonSegment first = new TmdbSeasonSegment(1, 0, 9, 1);
        TmdbSeasonSegment second = new TmdbSeasonSegment(2, 10, 21, 1);
        TmdbSeasonScope scope = TmdbSeasonScope.multiSegments(List.of(first, second));

        assertEquals(List.of(first, second), scope.getSegments());
        assertEquals(10, scope.segmentFor(2).getSourceEpisodeStartIndex());
        assertEquals(1, scope.segmentFor(2).getTmdbEpisodeStartNumber());
    }
}
