package com.fongmi.android.tv.ui.player;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ShortDramaQueueCoordinatorTest {

    @Test
    public void forwardQueueCommitsNaturalTransitionOnce() {
        ShortDramaQueueCoordinator coordinator = new ShortDramaQueueCoordinator();
        coordinator.begin(session(false, 0));

        ShortDramaQueueCoordinator.ResolveRequest request = coordinator.nextRequest();
        assertNotNull(request);
        assertEquals("e2", request.episode().id());

        ShortDramaQueueCoordinator.QueueItem queued = coordinator.acceptResolved(
                request.generation(), request.episode(), item("https://cdn.test/e2.m3u8"));
        assertNotNull(queued);
        assertTrue(coordinator.shouldSuppressLegacyAutoAdvance());
        assertNull(coordinator.acceptResolved(
                request.generation(), request.episode(), item("https://cdn.test/e2b.m3u8")));

        ShortDramaQueueCoordinator.Transition transition = coordinator.onMediaItemTransition(
                request.generation(), queued.mediaId(), ShortDramaQueueCoordinator.TransitionReason.AUTO);
        assertNotNull(transition);
        assertEquals("e1", transition.previous().id());
        assertEquals("e2", transition.current().id());
        assertEquals("e3", transition.nextRequest().episode().id());
        assertFalse(coordinator.shouldSuppressLegacyAutoAdvance());
        assertNull(coordinator.onMediaItemTransition(
                request.generation(), queued.mediaId(), ShortDramaQueueCoordinator.TransitionReason.AUTO));
    }

    @Test
    public void staleGenerationAndWrongEpisodeCannotRepopulateQueue() {
        ShortDramaQueueCoordinator coordinator = new ShortDramaQueueCoordinator();
        coordinator.begin(session(false, 0));
        ShortDramaQueueCoordinator.ResolveRequest old = coordinator.nextRequest();
        assertNotNull(old);

        ShortDramaQueueCoordinator.Snapshot newer = coordinator.begin(session(false, 1));
        assertTrue(newer.generation() > old.generation());
        assertNull(coordinator.acceptResolved(
                old.generation(), old.episode(), item("https://cdn.test/old.mp4")));

        ShortDramaQueueCoordinator.ResolveRequest expected = coordinator.nextRequest();
        assertNotNull(expected);
        assertNull(coordinator.acceptResolved(
                expected.generation(), new ShortDramaQueueCoordinator.Episode("e1", "1", 0),
                item("https://cdn.test/wrong.mp4")));
        assertNull(coordinator.snapshot().queued());
    }

    @Test
    public void reverseQueueStopsAtFirstEpisode() {
        ShortDramaQueueCoordinator coordinator = new ShortDramaQueueCoordinator();
        coordinator.begin(session(true, 2));

        ShortDramaQueueCoordinator.ResolveRequest second = coordinator.nextRequest();
        assertEquals("e2", second.episode().id());
        ShortDramaQueueCoordinator.QueueItem secondItem = coordinator.acceptResolved(
                second.generation(), second.episode(), item("http://cdn.test/e2.mp4"));
        ShortDramaQueueCoordinator.Transition secondTransition = coordinator.onMediaItemTransition(
                second.generation(), secondItem.mediaId(), ShortDramaQueueCoordinator.TransitionReason.AUTO);
        assertEquals("e1", secondTransition.nextRequest().episode().id());

        ShortDramaQueueCoordinator.ResolveRequest first = coordinator.nextRequest();
        ShortDramaQueueCoordinator.QueueItem firstItem = coordinator.acceptResolved(
                first.generation(), first.episode(), item("http://cdn.test/e1.mp4"));
        ShortDramaQueueCoordinator.Transition firstTransition = coordinator.onMediaItemTransition(
                first.generation(), firstItem.mediaId(), ShortDramaQueueCoordinator.TransitionReason.AUTO);
        assertNull(firstTransition.nextRequest());
        assertNull(coordinator.nextRequest());
    }

    @Test
    public void onlyDirectHttpVodItemsAreEligible() {
        assertTrue(item("https://cdn.test/e.m3u8").isEligible());
        assertTrue(item("http://cdn.test/e.mp4").isEligible());
        assertFalse(new ShortDramaQueueCoordinator.ResolvedItem(
                "rtsp://cdn.test/e", Collections.emptyMap(), "", ShortDramaQueueCoordinator.MediaKind.PROGRESSIVE_MP4).isEligible());
        assertFalse(new ShortDramaQueueCoordinator.ResolvedItem(
                "https://cdn.test/e.m3u8", Collections.emptyMap(), "", ShortDramaQueueCoordinator.MediaKind.HLS,
                true, false, false, false, false, false).isEligible());
        assertFalse(new ShortDramaQueueCoordinator.ResolvedItem(
                "https://cdn.test/e.m3u8***https://cdn.test/f.m3u8|||10", Collections.emptyMap(), "", ShortDramaQueueCoordinator.MediaKind.HLS).isEligible());
        assertFalse(new ShortDramaQueueCoordinator.ResolvedItem(
                "https://cdn.test/e.m3u8", Collections.emptyMap(), "", ShortDramaQueueCoordinator.MediaKind.HLS,
                false, true, false, false, false, false).isEligible());
    }

    @Test
    public void nonAutoOrWrongTimelineEventDoesNotMutateState() {
        ShortDramaQueueCoordinator coordinator = new ShortDramaQueueCoordinator();
        coordinator.begin(session(false, 0));
        ShortDramaQueueCoordinator.ResolveRequest request = coordinator.nextRequest();
        ShortDramaQueueCoordinator.QueueItem item = coordinator.acceptResolved(
                request.generation(), request.episode(), item("https://cdn.test/e2.mp4"));

        assertNull(coordinator.onMediaItemTransition(
                request.generation(), item.mediaId(), ShortDramaQueueCoordinator.TransitionReason.MANUAL));
        assertNull(coordinator.onMediaItemTransition(
                request.generation(), "wrong", ShortDramaQueueCoordinator.TransitionReason.AUTO));
        assertEquals("e1", coordinator.snapshot().current().id());
        assertEquals("e2", coordinator.snapshot().queued().episode().id());
    }

    private static ShortDramaQueueCoordinator.Session session(boolean reverse, int currentIndex) {
        return new ShortDramaQueueCoordinator.Session(
                "work", "site", "line", "flag",
                Arrays.asList(
                        new ShortDramaQueueCoordinator.Episode("e1", "1", 0),
                        new ShortDramaQueueCoordinator.Episode("e2", "2", 1),
                        new ShortDramaQueueCoordinator.Episode("e3", "3", 2)),
                currentIndex,
                reverse);
    }

    private static ShortDramaQueueCoordinator.ResolvedItem item(String uri) {
        return new ShortDramaQueueCoordinator.ResolvedItem(
                uri, Map.of("Referer", "https://site.test/"), "", uri.contains("m3u8")
                        ? ShortDramaQueueCoordinator.MediaKind.HLS
                        : ShortDramaQueueCoordinator.MediaKind.PROGRESSIVE_MP4);
    }
}
