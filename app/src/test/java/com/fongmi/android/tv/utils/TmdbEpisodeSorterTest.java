package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Vod;

import org.junit.Test;

import java.util.List;

public class TmdbEpisodeSorterTest {

    @Test
    public void mixedSeasonMetadata_usesEpisodeNumberForEntireFlag() {
        Flag flag = flag("S01E100", "S02E01", "第50集");

        TmdbEpisodeSorter.sort(vod(flag));

        assertEquals(List.of("S02E01", "第50集", "S01E100"), names(flag));
    }

    @Test
    public void completeSeasonMetadata_sortsBySeasonBeforeEpisodeNumber() {
        Flag flag = flag("S02E01", "S01E100", "S01E02");

        TmdbEpisodeSorter.sort(vod(flag));

        assertEquals(List.of("S01E02", "S01E100", "S02E01"), names(flag));
    }

    @Test
    public void noSeasonMetadata_sortsByEpisodeNumber() {
        Flag flag = flag("第30集", "第1集", "第20集");

        TmdbEpisodeSorter.sort(vod(flag));

        assertEquals(List.of("第1集", "第20集", "第30集"), names(flag));
    }

    private static Flag flag(String... names) {
        Flag flag = new Flag("source");
        for (int i = 0; i < names.length; i++) {
            flag.getEpisodes().add(Episode.create(names[i], "https://example.test/" + i));
        }
        return flag;
    }

    private static Vod vod(Flag flag) {
        Vod vod = new Vod();
        vod.setFlags(List.of(flag));
        return vod;
    }

    private static List<String> names(Flag flag) {
        return flag.getEpisodes().stream().map(Episode::getName).toList();
    }
}
