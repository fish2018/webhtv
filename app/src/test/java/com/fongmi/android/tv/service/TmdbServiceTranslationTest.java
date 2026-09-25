package com.fongmi.android.tv.service;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbItem;
import com.google.gson.JsonParser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TmdbServiceTranslationTest {

    @Test
    public void preferredTitleSelectsChineseTranslationOverEnglishPrimary() {
        TmdbService service = new TmdbService();
        TmdbItem item = new TmdbItem(1, "tv", "English Title", "", "", "", "");
        String detail = """
                {"name":"English Title",
                 "translations":{"translations":[
                   {"iso_639_1":"en","data":{"name":"English Title"}},
                   {"iso_639_1":"zh","iso_3166_1":"CN","data":{"name":"中文标题"}}
                 ]}}
                """;

        assertEquals("中文标题", service.preferredTitle(item, JsonParser.parseString(detail).getAsJsonObject(), config("zh-CN")));
    }

    @Test
    public void translatedOverviewSelectsChineseTranslationOverEnglishPrimary() {
        TmdbService service = new TmdbService();
        String detail = """
                {"overview":"English overview",
                 "translations":{"translations":[
                   {"iso_639_1":"en","data":{"overview":"English overview"}},
                   {"iso_639_1":"zh","iso_3166_1":"CN","data":{"overview":"中文简介"}}
                 ]}}
                """;

        assertEquals("中文简介", service.translatedOverview(JsonParser.parseString(detail).getAsJsonObject(), config("zh-CN")));
    }

    @Test
    public void episodesUseChineseEpisodeTranslation() {
        TmdbService service = new TmdbService();
        String season = """
                {"episodes":[{
                  "episode_number":1,
                  "name":"Episode 1",
                  "overview":"English episode",
                  "translations":{"translations":[
                    {"iso_639_1":"en","data":{"name":"Episode 1","overview":"English episode"}},
                    {"iso_639_1":"zh","iso_3166_1":"CN","data":{"name":"第一集","overview":"中文单集"}}
                  ]}
                }]}
                """;

        assertEquals(1, service.episodes(JsonParser.parseString(season).getAsJsonObject(), config("zh-CN"), 1, 1).size());
        assertEquals("第一集", service.episodes(JsonParser.parseString(season).getAsJsonObject(), config("zh-CN"), 1, 1).get(0).getTitle());
        assertEquals("中文单集", service.episodes(JsonParser.parseString(season).getAsJsonObject(), config("zh-CN"), 1, 1).get(0).getOverview());
    }

    private static TmdbConfig config(String language) {
        try {
            TmdbConfig config = new TmdbConfig();
            var field = TmdbConfig.class.getDeclaredField("language");
            field.setAccessible(true);
            field.set(config, language);
            return config;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
