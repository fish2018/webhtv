package com.fongmi.android.tv.api;

import android.text.TextUtils;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.loader.CatSpider;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.event.CatWebEvent;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.server.process.CatWebview;
import com.github.catvod.crawler.SpiderDebug;

/**
 * 猫源"动作项"的识别。
 *
 * <p>猫源的配置站点（CatPawOpen 的 {@code baseset}）把设置入口伪装成点播条目：
 * {@code vod_id} 是动作名而非片源 id（如 {@code openInternalWebsite}），{@code vod_pic} 是二维码。
 * 点它时 bundle 通过 {@code /msg} 请求宿主打开网页，然后把 {@code detail} 返回成
 * <b>含一个全空条目的列表</b>——列表非空，于是详情页照常打开，用户会看到设置页背后压着空白播放页。
 *
 * <p>判定只看两件事：结果里那个条目<b>什么都没有</b>，且站点是猫源。这两条合起来就是动作项的签名——
 * 真片源至少有名字，真失败会返回空列表（那条路归 {@code setEmpty}）。
 *
 * <p>刻意<b>不</b>依赖"刚刚请求过内嵌网页"这类时序信号：详情结果会被缓存（命中时压根不调 spider，
 * 消息不会再发），且 {@code setDetail} 每次进入会被投递两次（{@code singleTop} 加观察者重投），
 * 时序信号在这两种情况下都会失配。也不做动作名白名单——那些名字属于 bundle，随版本变。
 */
public final class CatAction {

    private CatAction() {
    }

    /**
     * 这次 detail 结果是否只是"打开网页"的副产物，详情页该让位。
     *
     * @param detailStartTime 本次取详情的起始时刻，用来确认开页请求确实由这次导航触发。
     *                        少了这一条，坏掉的 spider 对真片源返回空对象时页面会静默关闭，
     *                        用户只看到闪一下、得不到任何解释。
     */
    public static boolean shouldYieldDetail(String key, long detailStartTime, Result result) {
        if (result == null || result.getList().isEmpty()) return false;
        if (!blank(result.getVod())) return false;
        if (!CatWebEvent.requestedAfter(detailStartTime)) return false;
        return isCatSource(key);
    }

    /**
     * 这个条目有没有任何可显示的东西。
     *
     * <p>宽到足以覆盖 bundle 以后多写几个空字段的情形，窄到不会误判真片源——真片源总有名字，
     * 就算没名字也会有线路、简介或封面。
     */
    public static boolean blank(Vod vod) {
        if (vod == null) return true;
        return vod.getName().isEmpty()
                && vod.getFlags().isEmpty()
                && TextUtils.isEmpty(vod.getContent())
                && TextUtils.isEmpty(vod.getPic());
    }

    /** 站点的 api 是否指向本机 bundle 的爬虫路由。 */
    public static boolean isCatSource(String key) {
        if (TextUtils.isEmpty(key)) return false;
        Site site = VodConfig.get().getSite(key);
        return site != null && CatSpider.matches(site.getApi());
    }

    /**
     * 配置站点的爬虫路由片段。{@code baseset} 是 bundle 里 {@code meta.key} 的字面量，
     * 两代 bundle 都没变；只有这个站点会注册 {@code /proxy/:img}，也只有它没有真片源。
     */
    private static final String SETTING_SPIDER = "/spider/baseset";

    /** 二维码代理路由：{@code .../proxy/<base64(目标地址)>}。 */
    private static final String PROXY = "/proxy/";

    /**
     * 这次点击是不是配置站点里的「动作项」——点它的本意是开网页，不是看片。
     *
     * <p>刻意<b>不</b>按动作名白名单判定。动作名是 bundle 的实现细节且在增长：老 bundle 只有
     * {@code openInternalWebsite}（点击配置），新的又加了 {@code builtinDanmuApiQr}（弹幕服务），
     * 以后还会有——写死名字就是每加一个都得再改一次代码，漏掉的那个继续闪播放页。
     *
     * <p>改按「能不能拿到要打开的地址」判定，这一条对所有动作项都成立：bundle 在 category 里
     * 就把地址 base64 编进了 {@code vod_pic} 的 {@code /proxy/} 段，
     * 而 detail 里 dispatch 的正是同一个地址（{@code /website} 对 {@code /website}，
     * 弹幕那项对 {@code iR(base)}）。所以 pic 里解出来的地址就是权威值。
     *
     * <p>范围收在配置站点内：那里没有真片源，所以不会把正常条目误判成动作项。
     * 老 bundle 的「扫码配置」是唯一的例外——它 pic 同样是 proxy 地址却不该开页，
     * 靠 {@code vod_id} 的形状排除（它是 {@code String(Math.random())}，纯小数；
     * 动作项的 id 都是具名标识符）。
     */
    public static boolean isWebsiteAction(String key, String id, String pic) {
        return !TextUtils.isEmpty(websiteUrl(key, id, pic));
    }

    /**
     * 点击那一刻就直接开页，不经详情页。
     *
     * <p>地址取自 {@code vod_pic}，<b>不</b>调 {@code detail} 让 bundle 去 dispatch：详情结果会被
     * 缓存，命中时压根不调 spider，那条消息永远不会再发——用户就会点了没反应。
     *
     * @return true 表示已接管，调用方应立即 return，不要再启动详情页
     */
    public static boolean openWebsite(String key, String id, String pic) {
        String url = websiteUrl(key, id, pic);
        if (TextUtils.isEmpty(url)) return false;
        SpiderDebug.log("cat-action", "open settings key=%s id=%s url=%s", key, id, url);
        CatWebview.open(url);
        return true;
    }

    /**
     * 动作项要打开的地址，不是动作项就返回空。判定与取值合在一处，避免两边条件走岔。
     *
     * <p>最后一关是<b>同源校验</b>：{@code pic} 是服务端下发的，解出来的地址可以是任意 host。
     * 动作项的地址本该指向猫源自己的服务（{@code /website}、弹幕服务都在同一台上），
     * 所以只放行与站点 api 同 host 的地址——否则第三方源就能让我们把任意外部页面
     * 当成「设置页」加载进 WebView。
     */
    private static String websiteUrl(String key, String id, String pic) {
        if (!isSettingSite(key) || !isActionId(id)) return "";
        String url = target(pic);
        if (TextUtils.isEmpty(url)) return "";
        Site site = VodConfig.get().getSite(key);
        String api = site == null ? "" : site.getApi();
        if (sameHost(api, url)) return url;
        SpiderDebug.log("cat-action", "reject cross-host target api=%s url=%s", api, url);
        return "";
    }

    /** 站点是猫源的配置站点（{@code baseset}）。 */
    private static boolean isSettingSite(String key) {
        if (!isCatSource(key)) return false;
        Site site = VodConfig.get().getSite(key);
        return site != null && site.getApi().contains(SETTING_SPIDER);
    }

    /**
     * 目标地址是否可信——即确实指向这个猫源自己的服务。
     *
     * <p>不比端口：弹幕服务跑在另一个端口（实测 9321，猫源本体 9988），它是同一台机器上的
     * 附带服务，属于合法目标。
     *
     * <p>本机 bundle 要特殊对待：宿主始终用 {@code 127.0.0.1:<port>} 访问它，而 bundle 在
     * {@code vod_pic} 里自报的是<b>局域网 IP</b>（它按 Host 头推自己的地址）。两者是同一个
     * 进程的两种写法，纯字符串比对会把它判成跨 host——这正是「点击配置仍先进详情页」的原因。
     * 所以 api 是回环地址时，放行回环与私有网段；此时 bundle 就跑在本机，它自报的地址可信。
     *
     * <p>api 指向远端时仍要求同 host：那种情况下 pic 是远端下发的数据，不能让它把任意外部
     * 页面当成「设置页」加载进 WebView。
     */
    private static boolean sameHost(String api, String url) {
        String left = host(api);
        String right = host(url);
        if (left.isEmpty() || right.isEmpty()) return false;
        if (left.equalsIgnoreCase(right)) return true;
        return isLocal(left) && isPrivate(right);
    }

    /** 回环地址：宿主访问本机 bundle 用的就是这个。 */
    private static boolean isLocal(String host) {
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)
                || "::1".equals(host) || "0.0.0.0".equals(host) || host.startsWith("127.");
    }

    /**
     * 私有网段或回环——本机 bundle 自报地址的合法取值范围。
     *
     * <p>收在私有网段内：万一 pic 被做手脚指向公网域名，也不会被当成本机服务放行。
     */
    private static boolean isPrivate(String host) {
        if (isLocal(host)) return true;
        if (host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("169.254.")) return true;
        if (!host.startsWith("172.")) return false;
        // 172.16.0.0/12 → 第二段 16..31
        int dot = host.indexOf('.', 4);
        try {
            int second = Integer.parseInt(dot < 0 ? host.substring(4) : host.substring(4, dot));
            return second >= 16 && second <= 31;
        } catch (Exception e) {
            return false;
        }
    }

    private static String host(String url) {
        if (TextUtils.isEmpty(url)) return "";
        int mark = url.indexOf("://");
        if (mark < 0) return "";
        String rest = url.substring(mark + 3);
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String authority = rest.substring(0, end);
        // userinfo 里可能藏 @host 混淆，取最后一个 @ 之后的部分才是真 host
        int at = authority.lastIndexOf('@');
        if (at >= 0) authority = authority.substring(at + 1);
        int colon = authority.indexOf(':');
        if (colon >= 0) authority = authority.substring(0, colon);
        return authority;
    }

    /**
     * 动作项的 id 是具名标识符（{@code openInternalWebsite}、{@code builtinDanmuApiQr}）。
     *
     * <p>老 bundle 的「扫码配置」用 {@code String(Math.random())} 当 id，是纯小数——
     * 它要的是在详情页看二维码，不该被当成开页动作。
     */
    private static boolean isActionId(String id) {
        if (TextUtils.isEmpty(id)) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = c == '_' || Character.isLetter(c) || (i > 0 && Character.isDigit(c));
            if (!ok) return false;
        }
        return Character.isLetter(id.charAt(0)) || id.charAt(0) == '_';
    }

    /**
     * 从 {@code vod_pic} 的 {@code /proxy/<base64>} 段解出要打开的地址。
     *
     * <p>解不出 http(s) 地址就返回空——宁可退回原来的详情页路径，也不拿一个可疑的地址去开页。
     */
    private static String target(String pic) {
        if (TextUtils.isEmpty(pic)) return "";
        int mark = pic.lastIndexOf(PROXY);
        if (mark < 0) return "";
        String encoded = pic.substring(mark + PROXY.length());
        if (TextUtils.isEmpty(encoded)) return "";
        int query = TextUtils.indexOf(encoded, '?');
        if (query >= 0) encoded = encoded.substring(0, query);
        int fragment = TextUtils.indexOf(encoded, '#');
        if (fragment >= 0) encoded = encoded.substring(0, fragment);
        try {
            String decoded = new String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), "UTF-8").trim();
            return decoded.startsWith("http://") || decoded.startsWith("https://") ? decoded : "";
        } catch (Throwable e) {
            return "";
        }
    }
}
