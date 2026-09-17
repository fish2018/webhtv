package com.fongmi.android.tv.server.process;

import android.content.Intent;
import android.net.Uri;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.event.CatWebEvent;
import com.fongmi.android.tv.ui.web.CatWebActivity;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.crawler.SpiderDebug;

/**
 * 猫源 {@code openInternalWebview} 的落地：内嵌 {@link CatWebActivity} 渲染，起不来才退回系统浏览器。
 *
 * <p>两个调用方：bundle 通过 {@code /msg} 请求开页（{@link CatMessage}），
 * 以及点击时的站点级分流（{@code CatAction.openWebsite}）——后者不经详情页，
 * 所以播放页不会被创建。
 */
public final class CatWebview {

    private CatWebview() {
    }

    /**
     * 立即拉起，<b>不经主线程队列</b>。
     *
     * <p>这个请求到达时详情页正在启动播放服务、装配 UI，主线程队列可能已经排了好几秒
     * （实测过 27 秒）。{@code App.post} 会把开页排在那堆活后面，用户点完先盯着播放页发呆，
     * 设置页姗姗来迟——正是"进去也会到播放页"的由来。{@code startActivity} 本身是 IPC，
     * 不要求在主线程调，所以直接在 Nano 工作线程上发。
     *
     * <p>用应用上下文加 {@code NEW_TASK}：taskAffinity 默认相同，系统会把它落到已有任务栈顶，
     * 所以返回仍回到点击来的那个列表，不会另起一个任务。
     */
    public static void open(String url) {
        try {
            App.get().startActivity(CatWebActivity.intent(App.get(), url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            // 通知详情页立刻退场，别等那份可能被堵住好几秒的 detail 结果
            CatWebEvent.post();
        } catch (Throwable e) {
            // 内嵌页起不来（被裁剪、被策略拦）时仍要让用户看到页面，交回系统
            SpiderDebug.log("cat-msg", e);
            external(url);
        }
    }

    /**
     * 兜底：内嵌失败才走系统浏览器，并明确告知用户已经离开了 App。
     *
     * <p>同样要发事件让详情页退场——这次点击的本意是看网页，不管页面最终在哪儿渲染，
     * 那个空白详情页都不该留在栈里等用户返回时撞上。
     */
    private static void external(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            App.get().startActivity(intent);
            CatWebEvent.post();
            Notify.show(R.string.cat_web_external);
        } catch (Throwable e) {
            SpiderDebug.log("cat-msg", e);
        }
    }
}
