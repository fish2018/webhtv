package com.fongmi.android.tv.ui.web;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * 锁定「猫源设置中心在 App 内打开」的接线。
 *
 * <p>猫源通过 {@code /msg} 的 {@code openInternalWebview} 请求宿主开网页。原先落到
 * {@code ACTION_VIEW} 跳外部浏览器，同时详情页还会打开一个空白播放页。这两处都容易在重构里
 * 被改回去，所以用源码断言钉住。
 */
public class CatWebWiringTest {

    @Test
    public void catWebviewPrefersInAppActivity() throws IOException {
        String source = read("com/fongmi/android/tv/server/process/CatWebview.java");
        int open = source.indexOf("static void open(String url)");
        assertTrue("CatWebview 必须有 open", open >= 0);

        int inApp = source.indexOf("CatWebActivity.intent(", open);
        assertTrue("必须优先用内嵌 CatWebActivity 打开", inApp > open);

        int actionView = source.indexOf("Intent.ACTION_VIEW");
        assertTrue("ACTION_VIEW 只该出现在兜底路径里，不能是主路径",
                actionView > inApp || actionView < 0);
        assertTrue("兜底必须在独立的 external 方法里，且提示用户已离开 App",
                source.indexOf("private static void external(String url)") > 0
                        && source.indexOf("R.string.cat_web_external") > 0);
    }

    /**
     * 让位判定不得依赖时序信号。
     *
     * <p>详情结果会被缓存（命中时压根不调 spider，{@code /msg} 不会再发），且
     * {@code setDetail} 每次进入被投递两次（{@code singleTop} 加观察者重投）——
     * 「刚刚请求过内嵌页」这类标记在这两种情况下都会失配，表现为返回落到播放页、
     * 或再点一次直接进播放页。
     */
    @Test
    public void yieldDecisionDoesNotDependOnTiming() throws IOException {
        String action = read("com/fongmi/android/tv/api/CatAction.java");
        assertTrue("判定不得引入时间窗口", !action.contains("System.currentTimeMillis()"));
        assertTrue("判定不得消费一次性标记", !action.contains("consumeRecentRequest"));

        String webview = read("com/fongmi/android/tv/server/process/CatWebview.java");
        assertTrue("发起处也不该再打时序标记", !webview.contains("markRequested"));
    }

    /**
     * 开页不得排进主线程队列。
     *
     * <p>请求到达时详情页正在启动播放服务，主线程可能已排了好几秒（实测 27 秒），
     * {@code App.post} 会让设置页姗姗来迟，用户先盯着播放页。
     */
    @Test
    public void webviewLaunchesOffTheMainThreadQueue() throws IOException {
        String source = read("com/fongmi/android/tv/server/process/CatWebview.java");
        int open = source.indexOf("static void open(String url)");
        int start = source.indexOf("startActivity(", open);
        assertTrue("open 必须直接拉起", start > open);

        int post = source.indexOf("App.post(", open);
        assertTrue("拉起不得包在 App.post 里", post < 0 || post > start);
        assertTrue("用应用上下文就得带 NEW_TASK", source.indexOf("FLAG_ACTIVITY_NEW_TASK", open) > open);
    }

    /** 详情页要在开页请求到达时就退，不能等那份可能被堵住好几秒的 detail 结果。 */
    @Test
    public void detailYieldsOnRequestNotOnlyOnResult() throws IOException {
        String webview = read("com/fongmi/android/tv/server/process/CatWebview.java");
        assertTrue("开页后必须广播事件", webview.contains("CatWebEvent.post()"));
        // 退回系统浏览器时同样要让详情页退场，否则返回会撞上那个空白页
        int external = webview.indexOf("private static void external(String url)");
        assertTrue("兜底路径也要发事件",
                external >= 0 && webview.indexOf("CatWebEvent.post()", external) > external);

        String event = read("com/fongmi/android/tv/event/CatWebEvent.java");
        assertTrue("事件要带请求时刻，供详情页判断是不是自己触发的", event.contains("public boolean after(long"));

        for (String flavor : new String[]{"leanback", "mobile"}) {
            String source = readFlavor(flavor, "com/fongmi/android/tv/ui/activity/VideoActivity.java");
            int sub = source.indexOf("public void onCatWebEvent(");
            assertTrue(flavor + " 必须订阅开页事件", sub >= 0);
            assertTrue(flavor + " 必须用 detailStartTime 做归属判断，而不是固定时间窗",
                    source.indexOf("event.after(detailStartTime)", sub) > sub);
            assertTrue(flavor + " 命中要 finish", source.indexOf("finish();", sub) > sub);
        }
    }

    /**
     * 每个会进详情的入口都要让位。
     *
     * <p>首页点击在 TMDB 详情模式下走的是 TmdbDetailActivity，不是 VideoActivity——
     * 只改后者会让「返回落到详情页」在这条路上原样复现。这里按「谁调 detailContent 谁就得让位」
     * 来兜住所有入口。
     */
    @Test
    public void everyDetailEntryYields() throws IOException {
        String[] entries = {
                "main:com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java",
                "leanback:com/fongmi/android/tv/ui/activity/VideoActivity.java",
                "mobile:com/fongmi/android/tv/ui/activity/VideoActivity.java",
        };
        for (String entry : entries) {
            String[] parts = entry.split(":", 2);
            String source = readFlavor(parts[0], parts[1]);
            assertTrue(parts[1] + "（" + parts[0] + "）必须订阅开页事件，否则从内嵌页返回会落回它",
                    source.contains("public void onCatWebEvent("));
            assertTrue(parts[1] + "（" + parts[0] + "）还要有结果兜底判定",
                    source.contains("CatAction.shouldYieldDetail("));
            // 兜底判定必须带上本次取详情的起始时刻，否则会误伤「真片源但详情为空」
            assertTrue(parts[1] + "（" + parts[0] + "）兜底判定必须传入 detail 起始时刻",
                    source.contains("shouldYieldDetail(getKey(), detailStartTime, result)")
                            || source.contains("shouldYieldDetail(key, loadStart, result)"));
        }
    }

    /** 缓存住「什么都没有」的详情会跳过 spider，副作用（请求开网页）就再也不发生。 */
    @Test
    public void blankDetailIsNotCached() throws IOException {
        String source = read("com/fongmi/android/tv/api/SiteApi.java");
        int store = source.indexOf("VodDetailCache.putContent(sourceKey, id, content)");
        assertTrue("SiteApi 必须有详情缓存写入", store >= 0);

        int guard = source.lastIndexOf("CatAction.blank(result.getVod())", store);
        assertTrue("写缓存前必须排除「什么都没有」的详情", guard > 0 && guard < store);
    }

    /**
     * 只有占位线路、没有元数据的详情不得进缓存。
     *
     * <p>设置类站点（网盘配置一类）把每个设置项做成条目，真正的工作在 {@code detailContent}
     * 里发生——弹输入框、写配置；返回值只是个占位假线路，实测形如
     * {@code {"list":[{"vod_play_from":"Config","vod_play_url":"Config$Config"}]}}。
     * 缓存住它，第二次点击 spider 不被调用，弹窗不出，宿主拿着假地址直奔播放页。
     *
     * <p>{@code blank} 挡不住：占位线路会生成 Flag，{@code getFlags()} 非空。所以判定要看
     * 元数据（名字/封面/简介）而<b>不看线路</b>——线路是占位结果也能凑出来的东西。
     */
    @Test
    public void detailWithoutMetadataIsNotCached() throws IOException {
        String source = read("com/fongmi/android/tv/api/SiteApi.java");
        int store = source.indexOf("VodDetailCache.putContent(sourceKey, id, content)");
        assertTrue("SiteApi 必须有详情缓存写入", store > 0);

        int guard = source.lastIndexOf("!hasMetadata(result.getVod())", store);
        assertTrue("写缓存前必须排除「只有占位线路、没有元数据」的详情", guard > 0 && guard < store);

        int helper = source.indexOf("private static boolean hasMetadata(Vod vod)");
        assertTrue("必须有 hasMetadata 判定", helper > 0);
        String body = source.substring(helper, source.indexOf('}', source.indexOf("return", helper)));
        assertTrue("判定必须看名字", body.contains("getName()"));
        assertTrue("判定必须看封面", body.contains("getPic()"));
        assertTrue("判定必须看简介", body.contains("getContent()"));
        assertTrue("判定绝不能看线路——占位结果也有线路，看了就挡不住设置类站点",
                !body.contains("getFlags()"));
    }

    /**
     * 这次 spider 调用顺带开了网页，这条详情就绝不能进缓存。
     *
     * <p>开页是 spider 调用的副作用（bundle 反向调 {@code /msg}），缓存命中时不调 spider，
     * 副作用也就不再发生——第二次点击网页不开、还会停在空详情页上，即「只有第一次生效」。
     *
     * <p>与 {@link #detailWithoutMetadataIsNotCached} 互补：那条看结果形状，这条看副作用。
     * 形状千变万化，副作用是确凿的，两类都要。
     */
    @Test
    public void webOpeningDetailIsNotCached() throws IOException {
        String source = read("com/fongmi/android/tv/api/SiteApi.java");
        int sample = source.indexOf("long beforeSpider = System.currentTimeMillis()");
        assertTrue("必须在调 spider 之前先取一次时刻", sample > 0);

        int spider = source.indexOf("site.recent().spider().detailContent(", sample);
        assertTrue("取样必须在 spider 调用之前，否则跨不过副作用发生的那一刻", spider > sample);

        int store = source.indexOf("VodDetailCache.putContent(sourceKey, id, content)");
        int compare = source.lastIndexOf("CatWebEvent.requestedAfter(beforeSpider)", store);
        assertTrue("写缓存前必须判定这次调用有没有开过页", compare > sample && compare < store);
    }

    @Test
    public void activityRegistersNoJavascriptBridge() throws IOException {
        String source = read("com/fongmi/android/tv/ui/web/CatWebActivity.java");
        // 查调用形式而非裸词：类注释里正解释着「为什么不挂 JS 桥」
        assertTrue("这个页面渲染远程页面，绝不能挂 JS 桥——addJavascriptInterface 是 WebView 级别的",
                !source.contains(".addJavascriptInterface("));
        assertTrue("JS 必须开：设置页是 React 应用", source.contains("setJavaScriptEnabled(true)"));
        assertTrue("远程页面没有理由读本地文件", source.contains("setAllowFileAccess(false)"));
    }

    @Test
    public void activityKeepsNavigationInside() throws IOException {
        String source = read("com/fongmi/android/tv/ui/web/CatWebActivity.java");
        int override = source.indexOf("shouldOverrideUrlLoading");
        assertTrue("必须接管页面内导航", override >= 0);
        assertTrue("http(s) 一律留在本页", source.indexOf("return false", override) > override);
        assertTrue("非 http(s) 的 scheme 要丢掉，不去唤起外部应用",
                source.indexOf("blockScheme", override) > override);
    }

    @Test
    public void activityBackGoesBackBeforeClosing() throws IOException {
        String source = read("com/fongmi/android/tv/ui/web/CatWebActivity.java");
        int back = source.indexOf("handleOnBackPressed");
        assertTrue("必须处理返回", back >= 0);
        assertTrue("先让 WebView 回退，退到底才关页面",
                source.indexOf("canGoBack()", back) > back && source.indexOf("goBack()", back) > back);
        // 拦 onKeyDown 会让「按住返回键」的重复事件连续触发回退
        assertTrue("不得在 onKeyDown 里再调一次 dispatcher",
                !source.contains("getOnBackPressedDispatcher().onBackPressed()"));
    }

    @Test
    public void bothFlavorsYieldDetailToWebview() throws IOException {
        for (String flavor : new String[]{"leanback", "mobile"}) {
            String source = readFlavor(flavor, "com/fongmi/android/tv/ui/activity/VideoActivity.java");
            int yield = source.indexOf("CatAction.shouldYieldDetail(getKey(), detailStartTime, result)");
            assertTrue(flavor + " 的 setDetail 必须先判断是否该让位给内嵌网页", yield >= 0);

            int setEmpty = source.indexOf("if (result.getList().isEmpty()) setEmpty(", yield);
            assertTrue(flavor + " 的让位判断必须在 setEmpty/setDetail 分派之前", setEmpty > yield);
            assertTrue(flavor + " 命中时要直接 finish 并 return，不能继续往下走",
                    source.indexOf("finish();", yield) > yield
                            && source.indexOf("finish();", yield) < setEmpty);

            // 等播放服务会让判定依赖的时间关联窗口过期，重投时反而露出空白页
            int pending = source.indexOf("mPendingDetail = result", yield - 600 < 0 ? 0 : yield - 600);
            if (pending >= 0) {
                assertTrue(flavor + " 的让位判断必须排在「等播放服务」的早退之前", yield < pending);
            }
        }
    }

    /**
     * 设置页要在点击那一刻就开，不能靠详情页事后退场。
     *
     * <p>事后退场（{@code onCatWebEvent} / {@code shouldYieldDetail}）只能让播放页尽快消失，
     * 消不掉「它已经被创建并闪了一下」——用户看到的就是「先进播放页，再跳到配置页」。
     * 真正的修法是在 {@code startActivity} 之前分流，跟音频源/小说源同一个机制。
     * 那两套判定<b>保留</b>：它们兜住本 handler 覆盖不到的入口（历史、搜索、推送）。
     */
    @Test
    public void websiteActionDispatchesBeforeAnyDetailPage() throws IOException {
        String app = read("com/fongmi/android/tv/App.java");
        assertTrue("必须注册猫源动作项的 handler", app.contains("new com.fongmi.android.tv.content.CatActionContentHandler()"));

        String handler = read("com/fongmi/android/tv/content/CatActionContentHandler.java");
        assertTrue("站点级分流要落在 handleSite 上", handler.contains("CatAction.openWebsite(key, id, pic)"));

        String action = read("com/fongmi/android/tv/api/CatAction.java");
        int open = action.indexOf("public static boolean openWebsite(");
        assertTrue("CatAction 必须提供点击即开页的入口", open >= 0);
        assertTrue("命中要直接开网页", action.indexOf("CatWebview.open(", open) > open);
        // 走 detail 让 bundle dispatch 会被详情缓存吃掉：命中缓存时压根不调 spider，消息永不再发
        assertTrue("不得靠调 detail 让 bundle 去 dispatch，那条路会被详情缓存吃掉",
                !action.contains("detailContent("));
    }

    /**
     * 判定不得写死动作名。
     *
     * <p>动作名是 bundle 的实现细节且在增长：老 bundle 只有 {@code openInternalWebsite}
     * （点击/通用配置），新的又加了 {@code builtinDanmuApiQr}（弹幕服务）。写死名字等于
     * 每加一项都要再改一次代码，漏掉的那项继续闪播放页——这正是弹幕服务复现的原因。
     *
     * <p>改按「能不能从 vod_pic 的 proxy 段解出要打开的地址」判定：bundle 在 category 里就把
     * 地址 base64 编进了 pic，detail 里 dispatch 的是同一个地址。
     */
    @Test
    public void dispatchDoesNotHardcodeActionNames() throws IOException {
        String action = read("com/fongmi/android/tv/api/CatAction.java");
        int is = action.indexOf("public static boolean isWebsiteAction(");
        assertTrue("要有独立的动作项判定", is >= 0);

        // 动作名只该出现在注释里（说明来历），不能进判定逻辑
        assertTrue("判定不得比对具体动作名，否则每加一项都要改代码",
                !action.contains("\"openInternalWebsite\"") && !action.contains("\"builtinDanmuApiQr\""));
        assertTrue("地址要从 pic 的 proxy 段解出来", action.contains("PROXY") && action.contains("Base64.decode"));
        // 范围必须收在配置站点内，否则真片源会被当成动作项
        assertTrue("判定必须限定在猫源的配置站点内", action.indexOf("isSettingSite(key)", is) > is);
    }

    /**
     * 老 bundle 的「扫码配置」仍要能进详情页看二维码。
     *
     * <p>它的 pic 同样是 proxy 地址，所以按地址判定会把它一起吞掉；靠 {@code vod_id} 的形状
     * 排除——它是 {@code String(Math.random())}（纯小数），动作项的 id 都是具名标识符。
     */
    @Test
    public void qrItemStillOpensDetail() throws IOException {
        String action = read("com/fongmi/android/tv/api/CatAction.java");
        int id = action.indexOf("private static boolean isActionId(");
        assertTrue("必须按 id 形状排除扫码项", id >= 0);
        assertTrue("纯小数 id（Math.random）不能算动作项",
                action.indexOf("isDigit", id) > id || action.indexOf("isLetter", id) > id);
    }

    /** 解不出 http(s) 地址时要退回原来的详情页路径，不能拿可疑地址去开页。 */
    @Test
    public void undecodableTargetFallsBack() throws IOException {
        String action = read("com/fongmi/android/tv/api/CatAction.java");
        int target = action.indexOf("private static String target(");
        int bodyEnd = action.indexOf("\n    }", target);
        assertTrue("要有地址解码", target >= 0);
        assertTrue("只接受 http(s)", action.indexOf("startsWith(\"http://\")", target) > target);
        assertTrue("proxy 后的 Base64 必须整段解码，不能按 slash 截断",
                action.indexOf("encoded.indexOf('/')", target) < 0
                        || action.indexOf("encoded.indexOf('/')", target) > bodyEnd);
    }

    /**
     * 解出来的地址必须与站点同 host。
     *
     * <p>{@code vod_pic} 是服务端下发的，不做这一关，第三方源就能让我们把任意外部页面
     * 当成「设置页」加载进 WebView。动作项的地址本该指向猫源自己那台服务。
     */
    @Test
    public void targetMustBeSameHostAsSite() throws IOException {
        String action = read("com/fongmi/android/tv/api/CatAction.java");
        int url = action.indexOf("private static String websiteUrl(");
        assertTrue("判定与取值要合在一处，避免两边条件走岔", url >= 0);
        assertTrue("必须做同源校验", action.indexOf("sameHost(", url) > url);
        // userinfo 里可以藏 @host 混淆（http://good@evil.com/），必须取最后一个 @ 之后
        assertTrue("host 提取要防 userinfo 混淆", action.contains("lastIndexOf('@')"));
    }

    /**
     * 本机 bundle 自报局域网 IP 时不能被同源校验拦掉。
     *
     * <p>宿主始终用 {@code 127.0.0.1:<port>} 访问本机 bundle，而 bundle 在 {@code vod_pic} 里
     * 自报的是局域网 IP（按 Host 头推自己的地址）。纯字符串比对会把这两种写法判成跨 host，
     * 表现就是「点击配置仍先进详情页」——实测踩过。
     *
     * <p>放行范围收在私有网段内：本机源指向公网域名/公网 IP 仍要拦截。
     */
    @Test
    public void loopbackSiteTrustsPrivateTarget() throws IOException {
        String action = read("com/fongmi/android/tv/api/CatAction.java");
        int same = action.indexOf("private static boolean sameHost(");
        assertTrue("要有同源判定", same >= 0);
        assertTrue("api 是回环时要放行私有网段，否则本机 bundle 自报的地址会被误拦",
                action.indexOf("isLocal(left) && isPrivate(right)", same) > same);
        assertTrue("放行范围必须收在私有网段，不能放行公网",
                action.contains("192.168.") && action.contains("169.254."));
    }

    /**
     * 网页主题首页也要分流。
     *
     * <p>首页用 Web 主题渲染时，点击走的是 {@code navigation.openDetail} 桥而不是原生列表，
     * 那条路不经 {@code ContentDispatcher}——只改原生入口会让动作项在这条路上原样闪详情页，
     * 实测踩过（START 先是 TmdbDetailActivity，1 秒后才是 CatWebActivity）。
     */
    @Test
    public void webThemeBridgeAlsoDispatches() throws IOException {
        String bridge = read("com/fongmi/android/tv/web/WebHomeThemeBridge.java");
        int open = bridge.indexOf("private String openDetail(");
        assertTrue("桥必须有 openDetail", open >= 0);

        int dispatch = bridge.indexOf("CatAction.openWebsite(", open);
        int detail = bridge.indexOf("TmdbDetailActivity.start(", open);
        assertTrue("openDetail 必须先分流猫源动作项", dispatch > open);
        assertTrue("分流要排在打开详情页之前", dispatch < detail);
    }

    private static String read(String relative) throws IOException {
        return text(mainJava().resolve(path(relative)));
    }

    private static String readFlavor(String flavor, String relative) throws IOException {
        return text(srcRoot().resolve(Paths.get(flavor, "java")).resolve(path(relative)));
    }

    private static Path path(String relative) {
        return Paths.get(relative.replace('/', java.io.File.separatorChar));
    }

    private static String text(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path mainJava() {
        return srcRoot().resolve(Paths.get("main", "java"));
    }

    private static Path srcRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve(Paths.get("app", "src"));
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("app/src not found from " + Paths.get("").toAbsolutePath());
    }
}
