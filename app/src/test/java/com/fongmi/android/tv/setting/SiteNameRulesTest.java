package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SiteNameRulesTest {

    @Test
    public void customNameOverridesRawNameAndGroups() {
        assertEquals("[主力][短剧]我的一号站", SiteNameRules.effectiveName("[荐][采集]影视天堂", "[主力][短剧]我的一号站"));
        assertEquals(List.of("主力", "短剧"), SiteNameRules.groups("[荐][采集]影视天堂", "[主力][短剧]我的一号站"));
    }

    @Test
    public void blankCustomNameFallsBackToRawNameAndGroups() {
        assertEquals("[原始]站源A", SiteNameRules.effectiveName("[原始]站源A", "  "));
        assertEquals(List.of("原始"), SiteNameRules.groups("[原始]站源A", ""));
    }

    @Test
    public void customNameWithoutTagsRemovesRawGroups() {
        assertEquals(List.of(), SiteNameRules.groups("[原始]站源A", "我的站"));
    }

    @Test
    public void groupsAreOrderedAndDeduplicated() {
        assertEquals(List.of("主力", "备用"), SiteNameRules.groups("", "[主力][备用][主力]我的站"));
    }

    @Test
    public void searchMatchesCustomNameRawNameAndKey() {
        assertTrue(SiteNameRules.matchesSearch("XYQ线路一", "[主力]爸妈用", "csp_xxx", "爸妈"));
        assertTrue(SiteNameRules.matchesSearch("XYQ线路一", "[主力]爸妈用", "csp_xxx", "xyq"));
        assertTrue(SiteNameRules.matchesSearch("XYQ线路一", "[主力]爸妈用", "csp_xxx", "CSP_XXX"));
        assertTrue(SiteNameRules.matchesSearch("XYQ线路一", "[主力]爸妈用", "csp_xxx", "主力"));
        assertFalse(SiteNameRules.matchesSearch("XYQ线路一", "[主力]爸妈用", "csp_xxx", "音乐"));
    }

    @Test
    public void unchangedOriginalNameIsNotStoredAsCustomName() {
        assertEquals("", SiteNameRules.customNameForStorage("📁｜文件｜浏览", "📁｜文件｜浏览"));
        assertEquals("", SiteNameRules.customNameForStorage("📁｜文件｜浏览", "  📁｜文件｜浏览  "));
    }

    @Test
    public void editedNameOrAddedTagsAreStored() {
        assertEquals("[本地]📁｜文件｜浏览", SiteNameRules.customNameForStorage("📁｜文件｜浏览", "  [本地]📁｜文件｜浏览  "));
        assertEquals("我的文件", SiteNameRules.customNameForStorage("📁｜文件｜浏览", "我的文件"));
    }
}
