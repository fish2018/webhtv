package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PushParserTest {

    @Test
    public void fromTextExtractsTitleAfterPipe() {
        PushParser.Parsed parsed = PushParser.fromText("https://cdn.test/movie.mp4|莫离");

        assertEquals("https://cdn.test/movie.mp4", parsed.getUrl());
        assertEquals("莫离", parsed.getTitle());
        assertEquals("莫离", parsed.getName());
        assertEquals("https://cdn.test/movie.mp4|莫离", parsed.getId());
    }

    @Test
    public void fromTextExtractsTitleFromQuarkShare() {
        PushParser.Parsed parsed = PushParser.fromText("https://pan.quark.cn/s/da1777a548ea|丢");

        assertEquals("https://pan.quark.cn/s/da1777a548ea", parsed.getUrl());
        assertEquals("丢", parsed.getTitle());
        assertEquals("丢", parsed.getName());
    }

    @Test
    public void fromTextExtractsTitleFromBaiduShareWithPassword() {
        PushParser.Parsed parsed = PushParser.fromText("https://pan.baidu.com/s/1yfjq53RDyugPJIGjJh3_HA?pwd=v659|F 凡人#修仙传 动漫");

        assertEquals("https://pan.baidu.com/s/1yfjq53RDyugPJIGjJh3_HA?pwd=v659", parsed.getUrl());
        assertEquals("F 凡人#修仙传 动漫", parsed.getTitle());
        assertEquals("F 凡人#修仙传 动漫", parsed.getName());
    }

    @Test
    public void fromTextSniffsUrlBeforePipeTitle() {
        PushParser.Parsed parsed = PushParser.fromText("推送 https://cdn.test/movie.mp4?token=1 | 莫离 (2026)");

        assertEquals("https://cdn.test/movie.mp4?token=1", parsed.getUrl());
        assertEquals("莫离 (2026)", parsed.getTitle());
    }

    @Test
    public void fromTextKeepsHeaderSuffixInUrl() {
        PushParser.Parsed parsed = PushParser.fromText("https://cdn.test/movie.mp4|User-Agent=WebHTV");

        assertEquals("https://cdn.test/movie.mp4|User-Agent=WebHTV", parsed.getUrl());
        assertEquals("", parsed.getTitle());
    }

    @Test
    public void fromIdSplitsEncodedPushTitle() {
        PushParser.Parsed parsed = PushParser.fromId("https://cdn.test/movie.m3u8|莫离");

        assertEquals("https://cdn.test/movie.m3u8", parsed.getUrl());
        assertEquals("莫离", parsed.getTitle());
    }

    @Test
    public void fromTextKeepsEd2kPipesWithoutTitle() {
        PushParser.Parsed parsed = PushParser.fromText("ed2k://|file|movie.mkv|123|abcdef|/");

        assertEquals("ed2k://|file|movie.mkv|123|abcdef|/", parsed.getUrl());
        assertEquals("", parsed.getTitle());
    }

    @Test
    public void fromTextStripsPushScheme() {
        PushParser.Parsed parsed = PushParser.fromText("push://https://cdn.test/movie.mp4|莫离");

        assertEquals("https://cdn.test/movie.mp4", parsed.getUrl());
        assertEquals("莫离", parsed.getTitle());
    }
}
