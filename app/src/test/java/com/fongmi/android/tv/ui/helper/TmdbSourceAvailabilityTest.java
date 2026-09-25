package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbSourcePayload;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.setting.TmdbSourceState;
import com.google.gson.Gson;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbSourceAvailabilityTest {

    private static final Gson GSON = new Gson();

    @Test
    public void nullOrUnparsableDataIsAbsent() {
        assertEquals(TmdbSourceState.ABSENT_OR_INVALID, TmdbSourceAvailability.classify(null, null, null));
        assertEquals(TmdbSourceState.ABSENT_OR_INVALID, TmdbSourceAvailability.classify(new Vod(), new TmdbSourcePayload(), null));
    }

    @Test
    public void identityOnlyDoesNotCountDeclaredEmptyCapabilities() {
        TmdbSourcePayload payload = payload("""
                {"schema":1,"id":550,"media_type":"movie","complete":["core","credits","images"],
                 "detail":{"id":550}}
                """);

        assertEquals(TmdbSourceState.IDENTITY_ONLY,
                TmdbSourceAvailability.classify(new Vod(), payload, TmdbSourceAdapter.toBundle(payload, new Vod(), new TmdbConfig())));
    }

    @Test
    public void anyUserVisibleFieldMakesPayloadRenderable() {
        TmdbSourcePayload payload = payload("""
                {"schema":1,"id":550,"media_type":"movie",
                 "detail":{"id":550,"title":"Fight Club"}}
                """);

        TmdbBundle bundle = TmdbSourceAdapter.toBundle(payload, new Vod(), new TmdbConfig());

        assertEquals(TmdbSourceState.RENDERABLE, TmdbSourceAvailability.classify(new Vod(), payload, bundle));
        assertTrue(TmdbSourceAvailability.isRenderable(bundle));
    }

    @Test
    public void nestedCollectionsAreRenderableWithoutCoreCopy() {
        TmdbSourcePayload payload = payload("""
                {"schema":1,"id":1399,"media_type":"tv","season_number":1,
                 "detail":{"id":1399,"seasons":[{"season_number":1,"episode_count":10}]}}
                """);

        TmdbBundle bundle = TmdbSourceAdapter.toBundle(payload, new Vod(), new TmdbConfig());

        assertEquals(TmdbSourceState.RENDERABLE, TmdbSourceAvailability.classify(new Vod(), payload, bundle));
    }

    @Test
    public void mismatchedBundleIdentityIsRejected() {
        TmdbSourcePayload payload = payload("""
                {"schema":1,"id":550,"media_type":"movie","detail":{"id":550,"title":"Fight Club"}}
                """);
        TmdbSourcePayload other = payload("""
                {"schema":1,"id":551,"media_type":"movie","detail":{"id":551,"title":"Other"}}
                """);
        TmdbBundle otherBundle = TmdbSourceAdapter.toBundle(other, new Vod(), new TmdbConfig());

        assertEquals(TmdbSourceState.ABSENT_OR_INVALID, TmdbSourceAvailability.classify(null, payload, otherBundle));
    }

    @Test
    public void paymentSourceIdentityMustMatchVodPayload() {
        TmdbSourcePayload payload = payload("""
                {"schema":1,"id":550,"media_type":"movie","detail":{"id":550,"title":"Fight Club"}}
                """);
        TmdbSourcePayload other = payload("""
                {"schema":1,"id":551,"media_type":"movie","detail":{"id":551,"title":"Other"}}
                """);
        Vod vod = new Vod();
        vod.setTmdb(other);

        assertEquals(TmdbSourceState.ABSENT_OR_INVALID,
                TmdbSourceAvailability.classify(vod, payload, TmdbSourceAdapter.toBundle(payload, vod, new TmdbConfig())));
    }

    @Test
    public void nullBundleIsNeverRenderable() {
        assertFalse(TmdbSourceAvailability.isRenderable(null));
    }

    private static TmdbSourcePayload payload(String json) {
        return GSON.fromJson(json, TmdbSourcePayload.class);
    }
}
