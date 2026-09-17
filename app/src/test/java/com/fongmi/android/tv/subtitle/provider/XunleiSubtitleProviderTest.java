package com.fongmi.android.tv.subtitle.provider;

import com.fongmi.android.tv.subtitle.SubtitleTitleParser;
import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleQuery;
import com.fongmi.android.tv.subtitle.model.SubtitleQuerySource;
import com.fongmi.android.tv.subtitle.model.SubtitleStrictness;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class XunleiSubtitleProviderTest {

    @Test
    public void parseCandidates_mapsOracleResponseToSubtitleCandidates() {
        String body = "{"
                + "\"code\":0,"
                + "\"result\":\"ok\","
                + "\"data\":["
                + "{"
                + "\"gcid\":\"78D16F0B7FDCA8CFAAEC1651CACA0C66B0156A29\","
                + "\"cid\":\"089EA14B5D2D092D3885925C7EB9336B8F3287A7\","
                + "\"url\":\"https://subtitle.v.geilijiasu.com/08/9E/089EA14B5D2D092D3885925C7EB9336B8F3287A7.srt\","
                + "\"ext\":\"srt\","
                + "\"name\":\"流浪地球.srt\","
                + "\"duration\":7213866,"
                + "\"languages\":[\"默认\"],"
                + "\"extra_name\":\"（网友上传）\""
                + "},"
                + "{"
                + "\"cid\":\"71DBF3FB09033A577B8EB2310CB6E73687F040EF\","
                + "\"url\":\"https://subtitle.v.geilijiasu.com/71/DB/71DBF3FB09033A577B8EB2310CB6E73687F040EF.ass\","
                + "\"ext\":\"ass\","
                + "\"name\":\"流浪地球+The.Wandering.Earth.2019.CHINESE.1080p.BluRay.x264.TrueHD.7.1.Atmos-FGT.chs.ass\","
                + "\"duration\":7502780,"
                + "\"languages\":[\"简体中文\"]"
                + "}"
                + "]"
                + "}";
        SubtitleQuery query = new SubtitleQuery("title", "流浪地球", "zh", SubtitleQuerySource.SOURCE_TITLE, SubtitleStrictness.NORMAL, 2019, -1, -1);

        List<SubtitleCandidate> candidates = XunleiSubtitleProvider.parseCandidates(body, query, new SubtitleTitleParser());

        assertEquals(2, candidates.size());
        SubtitleCandidate first = candidates.get(0);
        assertEquals("xunlei", first.getProvider());
        assertEquals("089EA14B5D2D092D3885925C7EB9336B8F3287A7", first.getCandidateId());
        assertEquals("流浪地球.srt", first.getDisplayName());
        assertEquals("zh", first.getLanguage());
        assertEquals("srt", first.getFormat());
        assertTrue(first.isRequiresResolve());
        assertTrue(first.getProviderPayload().contains("089EA14B5D2D092D3885925C7EB9336B8F3287A7.srt"));

        SubtitleCandidate second = candidates.get(1);
        assertEquals("ass", second.getFormat());
        assertEquals(2019, second.getYear());
        assertEquals("简体中文", second.getLanguage());
    }

    @Test
    public void parseCandidates_boostsCidMatch() {
        String body = "{"
                + "\"code\":0,"
                + "\"result\":\"ok\","
                + "\"data\":["
                + "{\"cid\":\"match-cid\",\"url\":\"https://example.com/match.srt\",\"ext\":\"srt\",\"name\":\"match.srt\",\"languages\":[\"默认\"]},"
                + "{\"cid\":\"other-cid\",\"url\":\"https://example.com/other.srt\",\"ext\":\"srt\",\"name\":\"other.srt\",\"languages\":[\"默认\"]}"
                + "]"
                + "}";
        SubtitleQuery query = new SubtitleQuery("title", "流浪地球", "zh", SubtitleQuerySource.SOURCE_TITLE, SubtitleStrictness.NORMAL, 2019, -1, -1);

        List<SubtitleCandidate> candidates = XunleiSubtitleProvider.parseCandidates(body, query, new SubtitleTitleParser(), "match-cid");

        assertTrue(candidates.get(0).getScore() > candidates.get(1).getScore());
    }
}
