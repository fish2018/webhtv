package com.fongmi.android.tv.api;

import com.github.catvod.utils.Json;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 猫源配置的识别与错误暴露。
 *
 * <p>背景：魔改 bundle（把弹幕服务器合并进去的那类）在同一进程起多个 HTTP 服务，App 可能连到
 * 附带服务。那些服务对 {@code /config} 返回 401 信封或欢迎页——都是合法非空 JSON，
 * 旧逻辑当成就绪后解析出空 sites，用户只看到「订阅无效」而没有任何原因。
 */
public class CatSourceConfigTest {

    @Test
    public void rejectsUnauthorizedEnvelopeAsConfig() {
        assertFalse("401 信封不是猫源配置",
                CatSource.isConfig("{\"errorCode\":401,\"success\":false,\"errorMessage\":\"Unauthorized\"}"));
    }

    @Test
    public void rejectsWelcomeBannerAsConfig() {
        assertFalse("附带服务的欢迎页不是猫源配置",
                CatSource.isConfig("{\"message\":\"Welcome to the LogVar Danmu API server\",\"version\":\"1.20.8\"}"));
    }

    @Test
    public void rejectsBlankAndNonJsonAsConfig() {
        assertFalse(CatSource.isConfig(null));
        assertFalse(CatSource.isConfig(""));
        assertFalse(CatSource.isConfig("   "));
        assertFalse(CatSource.isConfig("Not found"));
        assertFalse(CatSource.isConfig("<html><body>502</body></html>"));
    }

    @Test
    public void acceptsGroupedBundleConfig() {
        assertTrue("本机 bundle 的分组形态要认得",
                CatSource.isConfig("{\"video\":{\"sites\":[{\"key\":\"nodejs_douban\",\"api\":\"/spider/douban/3\"}]}}"));
    }

    @Test
    public void acceptsFlatSitesConfig() {
        assertTrue("远端猫源的扁平数组要认得",
                CatSource.isConfig("[{\"key\":\"nodejs_muou\",\"api\":\"/spider/muou/3\"}]"));
        assertTrue("标准 TVBox 配置也要认得",
                CatSource.isConfig("{\"sites\":[{\"key\":\"csp_Demo\"}]}"));
    }

    @Test
    public void bundleSuffixDetectionIsLocaleIndependent() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertTrue("bundle 地址判定不能受默认 Locale 影响",
                    CatSource.isBundle("HTTPS://HOST/INDEX.JS"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void rejectsEmptyFlatArray() {
        assertFalse("空数组说明这个端口没在服务站点，应继续探下一个", CatSource.isConfig("[]"));
    }

    @Test
    public void normalizeSurfacesErrorEnvelopeInsteadOfEmptySites() {
        try {
            CatSource.normalize("http://127.0.0.1:9321/config",
                    Json.parse("{\"errorCode\":401,\"success\":false,\"errorMessage\":\"Unauthorized\"}"));
            fail("错误信封必须报错，不能静默解析成空 sites");
        } catch (IllegalArgumentException e) {
            assertTrue("报错要带上服务端原话：" + e.getMessage(),
                    e.getMessage() != null && e.getMessage().contains("Unauthorized"));
            assertTrue("报错要带上状态码：" + e.getMessage(), e.getMessage().contains("401"));
        }
    }

    @Test
    public void normalizeKeepsDepotConfigWithoutSites() {
        assertTrue("仓库配置没有 sites 是正常的，不能被错误信封判定误伤",
                CatSource.normalize("http://host/cfg",
                        Json.parse("{\"urls\":[{\"url\":\"http://a/b.json\",\"name\":\"A\"}]}")).has("urls"));
    }

    @Test
    public void normalizeKeepsConfigThatMerelyCarriesErrorMessageField() {
        assertTrue("带 errorMessage 字段但确有站点的配置要放行",
                CatSource.normalize("http://host/cfg",
                        Json.parse("{\"errorMessage\":\"\",\"sites\":[{\"key\":\"csp_A\"}]}")).has("sites"));
    }

    @Test
    public void normalizeLiftsVideoSitesAndRebasesRelativeApi() {
        assertEquals("分组配置要把 video.sites 提上来并补基址",
                "http://127.0.0.1:9988/spider/douban/3",
                CatSource.normalize("http://127.0.0.1:9988/config",
                                Json.parse("{\"video\":{\"sites\":[{\"key\":\"nodejs_douban\",\"api\":\"/spider/douban/3\"}]}}"))
                        .getAsJsonArray("sites").get(0).getAsJsonObject().get("api").getAsString());
    }

    @Test
    public void normalizeKeepsUserinfoInRebasedApi() {
        assertEquals("基址要保留 userinfo，否则每个站点请求都先吃一个 401",
                "https://user:pass@catpaw.example.me/spider/muou/3",
                CatSource.normalize("https://user:pass@catpaw.example.me/index.js.md5",
                                Json.parse("[{\"key\":\"nodejs_muou\",\"api\":\"/spider/muou/3\"}]"))
                        .getAsJsonArray("sites").get(0).getAsJsonObject().get("api").getAsString());
    }

    @Test
    public void normalizeLeavesAbsoluteApiAlone() {
        assertEquals("绝对地址不该被改写",
                "https://other.host/api.php/provide/vod",
                CatSource.normalize("https://catpaw.example.me/index.js.md5",
                                Json.parse("[{\"key\":\"csp_X\",\"api\":\"https://other.host/api.php/provide/vod\"}]"))
                        .getAsJsonArray("sites").get(0).getAsJsonObject().get("api").getAsString());
    }
}
