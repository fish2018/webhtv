package com.fongmi.android.tv.bean;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TMDB 站点规则：默认排除项与括号写法归一。
 *
 * <p>默认规则原本只写半角 {@code [音]} 一类，而猫源站点名一律用全角角括号
 * （{@code 「设」配置}、{@code 「盘」木偶}），于是五条默认规则对猫源 57 个站点一条也匹配不上，
 * 连配置站点都会去拉 TMDB。这里锁定归一化行为，以及配置站点被默认排除。
 */
public class TmdbConfigSiteRuleTest {

    private static TmdbConfig fresh() {
        return TmdbConfig.objectFrom("{\"apiKey\":\"k\"}");
    }

    @Test
    public void defaultsCoverNonVideoCategoriesAndSettings() {
        assertEquals("默认排除项：音/听/书/漫/短/画/小说/漫画/配置 加配置站点",
                11, TmdbConfig.getDefaultDisabledRules().size());
        assertTrue("配置站点必须默认排除", TmdbConfig.getDefaultDisabledRules().contains("[设]"));
        assertTrue("配置纯文本规则", TmdbConfig.getDefaultDisabledRules().contains("配置"));
        assertTrue("小说规则", TmdbConfig.getDefaultDisabledRules().contains("[小说]"));
        assertTrue("漫画规则", TmdbConfig.getDefaultDisabledRules().contains("[漫画]"));
        assertTrue("画规则", TmdbConfig.getDefaultDisabledRules().contains("[画]"));
        assertTrue("配规则", TmdbConfig.getDefaultDisabledRules().contains("[配]"));
    }

    @Test
    public void defaultsAreInjectedWhenUserNeverConfigured() {
        assertEquals("用户没配过排除规则时注入默认",
                TmdbConfig.getDefaultDisabledRules(), fresh().getDisabledSites());
    }

    @Test
    public void catSourceSettingSiteIsExcluded() {
        assertFalse("「设」配置 是配置站点，不该跑 TMDB",
                fresh().isSiteEnabled("nodejs_baseset", "「设」配置"));
    }

    @Test
    public void catSourceVideoSitesStayEnabled() {
        TmdbConfig config = fresh();
        assertTrue("「盘」木偶 是片源", config.isSiteEnabled("nodejs_muou", "「盘」木偶"));
        assertTrue("「直」瓜子 是片源", config.isSiteEnabled("nodejs_guazi", "「直」瓜子"));
        assertTrue("「荐」豆瓣 是片源", config.isSiteEnabled("nodejs_douban", "「荐」豆瓣"));
        assertTrue("「采」电影天堂 是片源", config.isSiteEnabled("nodejs_dytt", "「采」电影天堂"));
    }

    @Test
    public void halfWidthRuleMatchesFullWidthSiteName() {
        // 默认规则写的是 [音]，要能命中猫源风格的 「音」
        assertFalse("[音] 应命中 「音」xxx", fresh().isSiteEnabled("nodejs_audio", "「音」听书"));
        assertFalse("[漫] 应命中 【漫】xxx", fresh().isSiteEnabled("nodejs_comic", "【漫】漫画站"));
        assertFalse("[书] 应命中 ［书］xxx", fresh().isSiteEnabled("nodejs_novel", "［书］小说站"));
    }

    @Test
    public void halfWidthSiteNameStillMatches() {
        assertFalse("原本的半角写法不能因归一化而失效",
                fresh().isSiteEnabled("csp_Audio", "[音]喜马拉雅"));
    }

    @Test
    public void userWrittenFullWidthRuleAlsoWorks() {
        TmdbConfig config = TmdbConfig.objectFrom("{\"apiKey\":\"k\",\"exclude\":[\"「盘」\"]}");
        assertFalse("用户手填全角规则要能命中半角站点名", config.isSiteEnabled("x", "[盘]夸克"));
        assertFalse("也要能命中同为全角的站点名", config.isSiteEnabled("y", "「盘」夸克"));
    }

    @Test
    public void unrelatedSiteIsUnaffected() {
        assertTrue("不含任何排除标记的站点照常启用",
                fresh().isSiteEnabled("csp_Bilibili", "哔哩哔哩"));
    }

    @Test
    public void newDefaultRulesExcludeCorrespondingSites() {
        TmdbConfig config = fresh();
        assertFalse("[小说] 应命中 「小说」xxx", config.isSiteEnabled("nodejs_novel_full", "「小说」阅读站"));
        assertFalse("[漫画] 应命中 【漫画】xxx", config.isSiteEnabled("nodejs_comic_full", "【漫画】动漫站"));
        assertFalse("[画] 应命中 ［画］xxx", config.isSiteEnabled("nodejs_paint", "［画］图库"));
        assertFalse("配置 应命中 含配置的站点名", config.isSiteEnabled("nodejs_cfg", "「设」配置中心"));
        assertFalse("[配] 应命中 [配]xxx", config.isSiteEnabled("nodejs_cfg2", "[配]设置站"));
    }

    @Test
    public void bookRuleDoesNotMatchNovel() {
        // [书] 是 [书]xxx 的精确边界，不应被 [小说]xxx 命中（子串不含 [书]）
        TmdbConfig config = TmdbConfig.objectFrom("{\"apiKey\":\"k\",\"exclude\":[\"[书]\"]}");
        assertTrue("[书] 不该命中 [小说]xxx", config.isSiteEnabled("nodejs_novel_full", "[小说]阅读站"));
    }
}
