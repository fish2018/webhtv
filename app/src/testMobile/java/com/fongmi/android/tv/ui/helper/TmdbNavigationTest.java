package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Vod;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class TmdbNavigationTest {

    @Test
    public void bestVod_prefersExactTitleMatch() {
        Vod partial = vod("黑客帝国：矩阵重启");
        Vod exact = vod("黑客帝国");

        assertSame(exact, TmdbNavigation.bestVod(Arrays.asList(partial, exact), "黑客帝国"));
    }

    @Test
    public void bestVod_returnsNullWhenNothingMatches() {
        assertNull(TmdbNavigation.bestVod(Collections.singletonList(vod("无关作品")), "黑客帝国"));
    }

    private Vod vod(String name) {
        return new FakeVod(name);
    }

    private static final class FakeVod extends Vod {

        private final String name;

        private FakeVod(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getRemarks() {
            return "";
        }
    }
}
