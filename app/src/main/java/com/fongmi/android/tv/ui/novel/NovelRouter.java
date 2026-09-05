package com.fongmi.android.tv.ui.novel;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.ComicSourceConfig;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.NovelSourceConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.ui.web.WebReaderActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Sniffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 集数点击 / 播放入口路由器：决定是「小说/漫画阅读」还是「普通播放」。
 *
 * 两种拦截点：
 * 1) route()        —— 详情页 selectInlineEpisode：内容驱动判定。漫画 url 以 pics:// / manga:// 开头
 *                      直接进漫画；其余异步取一次 playerContent，按返回内容（url/playUrl/msg/header）
 *                      判定（novel:// 或非视频文本 → 小说，否则回退播放）。
 * 2) guardInlinePlay() —— 详情页 startInlinePlayer（内联播放器汇聚点）：按 play_url 协议前缀
 *                      硬编码路由到 WebReaderActivity(reader.html)；其他 → 原 player 流程。
 */
public final class NovelRouter {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final AtomicInteger HISTORY_REQUESTS = new AtomicInteger();
    private static final AtomicInteger CONTENT_REQUESTS = new AtomicInteger();

    private static Handler mainHandler() {
        return MainHandler.INSTANCE;
    }

    private static class MainHandler {
        private static final Handler INSTANCE = new Handler(Looper.getMainLooper());
    }

    public interface Fallback { void run(); }

    /** @return true 表示本方法已接管（含异步判定），调用方应 return。 */
    public static boolean route(Context ctx, String siteKey, Episode episode, Vod vod, Flag flag, Fallback fallback) {
        String url = episode == null ? null : episode.getUrl();
        if (isComic(url)) {
            openPics(ctx, siteKey, flag, vod, episode, url);
            return true;
        }
        if (isNovel(url)) {
            openReader(ctx, siteKey, flag, vod, episode, url);
            return true;
        }
        // 站点未命中小说/漫画规则时不做异步探测，避免给普通视频站增加一次多余请求
        String pure = pureSiteKey(siteKey);
        boolean comicSite = ComicSourceConfig.isEnabledByKey(pure);
        boolean novelSite = NovelSourceConfig.isEnabledByKey(pure);
        if (!comicSite && !novelSite) return false;
        ProgressDialog pd = new ProgressDialog(ctx);
        pd.setMessage("正在识别内容…");
        pd.setCancelable(true);
        pd.show();
        int request = CONTENT_REQUESTS.incrementAndGet();
        AtomicBoolean canceled = new AtomicBoolean(false);
        Future<?> future = executor.submit(() -> {
            Runnable launch = null;
            try {
                Result r = SiteApi.playerContent(pure, flag == null ? "" : flag.getFlag(), episode.getUrl());
                String u = readerPayload(r);
                if (isComic(u)) {
                    final String p = u;
                    launch = () -> openPics(ctx, siteKey, flag, vod, episode, p);
                } else if (isNovel(u)) {
                    final String p = u;
                    launch = () -> openReader(ctx, siteKey, flag, vod, episode, p);
                } else if (u != null && !u.isEmpty() && !r.needParse()
                        && !u.startsWith("http://") && !u.startsWith("https://")
                        && !Sniffer.isVideoFormat(u)) {
                    // 非协议、非 http、无需二次解析 → 视为内联文本内容（小说正文）
                    final String p = u;
                    launch = comicSite
                            ? () -> openPics(ctx, siteKey, flag, vod, episode, p)
                            : () -> openReader(ctx, siteKey, flag, vod, episode, p);
                }
            } catch (Throwable ignore) {
                launch = null;
            }
            // startActivity 统一回主线程执行，与 dismiss 保持时序
            final Runnable finalLaunch = launch;
            mainHandler().post(() -> {
                try { pd.dismiss(); } catch (Throwable ignore) {}
                if (canceled.get() || request != CONTENT_REQUESTS.get() || isDead(ctx)) return;
                if (finalLaunch != null) finalLaunch.run();
                else if (fallback != null) fallback.run();
            });
        });
        pd.setOnCancelListener(ignored -> {
            canceled.set(true);
            CONTENT_REQUESTS.compareAndSet(request, request + 1);
            future.cancel(true);
        });
        return true;
    }

    /** 宿主 Activity 是否已不可用（回调到达时页面可能已销毁）。 */
    private static boolean isDead(Context ctx) {
        if (!(ctx instanceof Activity)) return false;
        Activity a = (Activity) ctx;
        return a.isFinishing() || a.isDestroyed();
    }

    /* ---------------- 播放入口拦截：按 play_url 协议前缀路由到阅读器 ---------------- */

    /**
     * 详情页 startInlinePlayer 前的拦截：按 play_url 协议前缀或站点规则路由到 WebReaderActivity(reader.html)。
     * 命中 → 启动 WebReaderActivity 并返回 true（调用方必须停止内联播放器并 return）。
     * 不命中 → 返回 false（继续走原 player 流程）。
     */
    public static boolean guardInlinePlay(Context ctx, Result result,
                                          String siteKey, String flag,
                                          String vodId, String vodName, String vodPic,
                                          Episode currentEpisode, List<Episode> chapters) {
        if (ctx == null || result == null) return false;
        int kind = readerUrlKind(result);
        if (kind == 0) kind = kindBySiteRule(siteKey, result);
        if (kind == 0) return false;

        // 阅读器已在前台 → 回传解析结果，不重复启动（切换章节场景）
        WebReaderActivity reader = currentReader;
        if (reader != null && !reader.isFinishing() && !reader.isDestroyed()) {
            if (!reader.hasPendingHostChapterRequest() && NovelRouter.consumeStaleChapterResult()) return false;
            // 不在这里清表：结清哪一条只有阅读器知道（它持有本次请求的令牌），
            // 在这里猜或整表清空都会抹掉另一次仍在途的请求，返回键会重新失效。
            String payload = readerPayload(result);
            if (TextUtils.isEmpty(payload)) return false;
            reader.onEpisodeResolved(kind, payload, extractTitle(payload));
            return true;
        }
        // 刚关闭 / 属于已关阅读器的在途切章：都是返回后残留的回调，不再拉起
        if (shouldSuppressRelaunch()) return false;

        if (ctx instanceof NovelReaderHost) setHost((NovelReaderHost) ctx);

        String payload = readerPayload(result);
        if (TextUtils.isEmpty(payload)) return false;
        ArrayList<Episode> ch = new ArrayList<>();
        if (chapters != null) ch.addAll(chapters);
        int index = 0;
        if (currentEpisode != null) {
            int i = ch.indexOf(currentEpisode);
            if (i >= 0) index = i;
        }

        Intent it = new Intent(ctx, WebReaderActivity.class);
        it.putExtra(WebReaderActivity.EXTRA_KIND, kind);
        it.putExtra(WebReaderActivity.EXTRA_CACHE_KEY, WebReaderActivity.cacheLargeData(payload, ch));
        it.putExtra(WebReaderActivity.EXTRA_SITE_KEY, pureSiteKey(siteKey));
        it.putExtra(WebReaderActivity.EXTRA_FLAG, flag == null ? "" : flag);
        it.putExtra(WebReaderActivity.EXTRA_VOD_ID, vodId == null ? "" : vodId);
        it.putExtra(WebReaderActivity.EXTRA_VOD_NAME, vodName == null ? "" : vodName);
        it.putExtra(WebReaderActivity.EXTRA_VOD_PIC, vodPic == null ? "" : vodPic);
        it.putExtra(WebReaderActivity.EXTRA_INDEX, index);
        ctx.startActivity(it);
        return true;
    }

    /* ---------------- 内容判定 ---------------- */

    private static boolean isComic(String url) {
        if (url == null) return false;
        String u = url.trim();
        return u.startsWith("pics://") || u.startsWith("manga://");
    }

    private static boolean isNovel(String url) {
        if (url == null) return false;
        return url.trim().startsWith("novel://");
    }

    /** 从 Result 多字段中提取首个有效 play_url。优先返回带 novel:///pics:///manga:// 协议前缀的字段，避免内容藏在 msg/header 时漏判。 */
    public static String readerPayload(Result r) {
        if (r == null) return null;
        String playUrl = r.getPlayUrl();
        String urlV = null;
        try { if (r.getUrl() != null) urlV = r.getUrl().v(); } catch (Throwable ignore) {}
        String a = playUrl != null ? playUrl.trim() : "";
        String b = urlV != null ? urlV.trim() : "";
        if (isReaderPrefix(a)) return a;
        if (isReaderPrefix(b)) return b;
        if (!a.isEmpty()) return a;
        if (!b.isEmpty()) return b;
        if (notEmpty(r.getMsg())) return r.getMsg();
        Map<String, String> header = r.getHeader();
        if (header != null) {
            for (String v : header.values()) if (notEmpty(v)) return v;
        }
        return null;
    }

    private static boolean isReaderPrefix(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.startsWith("novel://") || s.startsWith("pics://") || s.startsWith("manga://");
    }

    /** 判定 play_url 协议类型：0=非阅读/普通视频；1=novel:// 小说；2=pics:///manga:// 漫画。 */
    public static int readerUrlKind(Result r) {
        String u = readerPayload(r);
        if (u == null) return 0;
        u = u.trim();
        if (u.startsWith("novel://")) return 1;
        if (u.startsWith("pics://") || u.startsWith("manga://")) return 2;
        return 0;
    }

    /** 是否应路由到阅读器（按 play_url 协议前缀）。供 PlaybackActivity.startPlayer 等汇聚点调用。 */
    public static boolean isReaderUrl(Result result) {
        return readerUrlKind(result) != 0;
    }

    /** 是否为阅读内容协议 URL（供 ContentDispatcher.dispatchUrl 判定）。 */
    public static boolean isReaderUrl(String url) {
        return isReaderPrefix(url == null ? null : url.trim());
    }

    /**
     * ContentDispatcher 分流入口：判定当前解析结果是否应交给阅读器。
     *
     * 命中条件（任一）：
     * 1) 内容本身是阅读协议（novel:// / pics:// / manga://）；
     * 2) 站点命中小说源 / 漫画源配置规则，且返回内容不是可播放的视频地址。
     *
     * @return true 表示已启动阅读器，调用方应停止播放并结束当前页
     */
    public static boolean handleResult(Activity activity, String historyKey, String siteKey, String flag, String vodName, String vodPic, List<Episode> episodes, int position, Result result) {
        if (activity == null || result == null) return false;

        int kind = readerUrlKind(result);
        if (kind == 0) kind = kindBySiteRule(siteKey, result);
        if (kind == 0) return false;

        Episode current = episodes == null || position < 0 || position >= episodes.size() ? null : episodes.get(position);
        String payload = readerPayload(result);
        String title = extractTitle(payload);
        if (title == null || title.isEmpty()) title = current == null ? vodName : current.getName();

        if (activity instanceof NovelReaderHost) setHost((NovelReaderHost) activity);

        // 阅读器已在前台 → 回传解析结果，不重复启动（切换章节场景）
        WebReaderActivity reader = currentReader;
        if (reader != null && !reader.isFinishing() && !reader.isDestroyed()) {
            if (!reader.hasPendingHostChapterRequest() && NovelRouter.consumeStaleChapterResult()) return false;
            // 不在这里清表：结清哪一条只有阅读器知道（它持有本次请求的令牌），
            // 在这里猜或整表清空都会抹掉另一次仍在途的请求，返回键会重新失效。
            reader.onEpisodeResolved(kind, payload, title);
            return true;
        }

        // 刚关闭 / 属于已关阅读器的在途切章：都是返回后残留的 playerContent 回调，不再拉起
        if (shouldSuppressRelaunch()) return false;

        ArrayList<Episode> ch = new ArrayList<>();
        if (episodes != null) ch.addAll(episodes);

        Intent it = new Intent(activity, WebReaderActivity.class);
        it.putExtra(WebReaderActivity.EXTRA_KIND, kind);
        it.putExtra(WebReaderActivity.EXTRA_CACHE_KEY, WebReaderActivity.cacheLargeData(payload, ch));
        it.putExtra(WebReaderActivity.EXTRA_SITE_KEY, pureSiteKey(siteKey));
        it.putExtra(WebReaderActivity.EXTRA_FLAG, flag == null ? "" : flag);
        // vodId 从 historyKey（siteKey@@@vodId@@@cid）解出：没有它 ReaderHistory 无法标识一本书，
        // 这条分流路径上的阅读进度会既不记录也不恢复。
        it.putExtra(WebReaderActivity.EXTRA_VOD_ID, vodIdOf(historyKey));
        it.putExtra(WebReaderActivity.EXTRA_VOD_NAME, vodName == null ? "" : vodName);
        it.putExtra(WebReaderActivity.EXTRA_VOD_PIC, vodPic == null ? "" : vodPic);
        it.putExtra(WebReaderActivity.EXTRA_INDEX, Math.max(0, position));
        activity.startActivity(it);
        return true;
    }

    /** 从 historyKey（siteKey@@@vodId@@@cid）中取出 vodId。 */
    private static String vodIdOf(String historyKey) {
        if (historyKey == null) return "";
        String[] parts = historyKey.split("@@@");
        return parts.length > 1 ? parts[1] : "";
    }

    /** 站点是否命中小说 / 漫画源规则（供 ContentDispatcher.dispatchSite 判定）。 */
    public static boolean isReaderSite(String key) {
        String pure = pureSiteKey(key);
        if (pure.isEmpty()) return false;
        return ComicSourceConfig.isEnabledByKey(pure) || NovelSourceConfig.isEnabledByKey(pure);
    }

    /** Opens a readable history item directly, falling back when the source is not readable. */
    public static boolean openHistory(Activity activity, History history, int targetCid, Fallback fallback) {
        return openHistory(activity, history, null, null, null, targetCid, fallback);
    }

    /** Opens a resolved cross-source history item directly in the reader. */
    public static boolean openHistory(Activity activity, History history, Vod target,
                                      Flag targetFlag, Episode targetEpisode, int targetCid,
                                      Fallback fallback) {
        if (activity == null || history == null) return false;
        String siteKey = pureSiteKey(target == null ? history.getSiteKey() : target.getSiteKey());
        String vodId = target == null ? history.getVodId() : target.getId();
        if (TextUtils.isEmpty(vodId) || targetCid != VodConfig.getCid()) return false;
        if (!isReadableHistoryCandidate(isReaderSite(siteKey), history, targetEpisode)) return false;

        ProgressDialog dialog = new ProgressDialog(activity);
        dialog.setMessage("正在打开阅读器...");
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        int request = HISTORY_REQUESTS.incrementAndGet();
        AtomicBoolean canceled = new AtomicBoolean(false);
        Future<ReaderData> future = executor.submit(() -> {
            ReaderData data = resolveHistory(history, target, targetFlag, targetEpisode, targetCid, canceled);
            mainHandler().post(() -> {
                try {
                    dialog.dismiss();
                } catch (Throwable ignore) {
                }
                if (canceled.get() || request != HISTORY_REQUESTS.get()
                        || targetCid != VodConfig.getCid() || isDead(activity)) return;
                if (data != null) openReaderData(activity, data);
                else if (fallback != null) fallback.run();
            });
            return data;
        });
        dialog.setOnCancelListener(ignored -> {
            synchronized (canceled) {
                canceled.set(true);
            }
            future.cancel(true);
        });
        return true;
    }

    static boolean isReadableHistoryCandidate(boolean readerSite, History history, Episode targetEpisode) {
        if (history == null) return false;
        return isReaderUrl(history.getEpisodeUrl())
                || isReaderUrl(targetEpisode == null ? null : targetEpisode.getUrl())
                || readerSite
                || ReaderHistory.isReaderRecord(history);
    }

    private static ReaderData resolveHistory(History history, Vod target,
                                             Flag targetFlag, Episode targetEpisode, int targetCid,
                                             AtomicBoolean canceled) {
        try {
            if (canceled.get()) return null;
            String siteKey = pureSiteKey(target == null ? history.getSiteKey() : target.getSiteKey());
            String vodId = target == null ? history.getVodId() : target.getId();
            Vod vod = target;
            if (vod == null || vod.getFlags().isEmpty()) {
                Result detail = SiteApi.detailContent(siteKey, vodId);
                if (canceled.get()) return null;
                vod = detail == null ? null : detail.getVod();
                if (vod == null || vod.getFlags().isEmpty()) return null;
                vod.checkName(history.getVodName());
                vod.checkPic(history.getVodPic());
            }
            if (TextUtils.isEmpty(vodId)) vodId = vod.getId();
            if (TextUtils.isEmpty(vodId) || vod.getFlags().isEmpty()) return null;
            if (targetCid != VodConfig.getCid()) return null;

            Flag flag = targetFlag;
            Episode episode = targetEpisode;
            if (flag == null || episode == null) {
                flag = com.fongmi.android.tv.ui.helper.TmdbUIAdapter.selectPlaybackFlag(
                        vod.getFlags(), history.getSourceBindingKey(),
                        history.getEpisodeUrl(), history.getVodFlag());
                if (flag != null) episode = findHistoryEpisode(flag, history);
            }
            if (flag == null || episode == null) {
                for (Flag candidate : vod.getFlags()) {
                    episode = findHistoryEpisode(candidate, history);
                    if (episode != null) {
                        flag = candidate;
                        break;
                    }
                }
            }
            if (flag == null || episode == null) {
                if (!canDefaultToFirstChapter(vod, history)) return null;
                flag = vod.getFlags().get(0);
                episode = flag.getEpisodes().get(0);
            }
            if (TextUtils.isEmpty(episode.getUrl())) return null;

            String payload = episode.getUrl();
            int kind = isComic(payload) ? 2 : isNovel(payload) ? 1 : 0;
            if (kind == 0) {
                Result result = SiteApi.playerContent(siteKey, flag.getFlag(), payload);
                if (canceled.get()) return null;
                String content = readerPayload(result);
                if (TextUtils.isEmpty(content)) return null;
                kind = readerUrlKind(result);
                if (kind == 0) kind = kindBySiteRule(siteKey, result);
                if (kind == 0) return null;
                payload = content;
            }

            if (targetCid != VodConfig.getCid()) return null;

            String name = TextUtils.isEmpty(vod.getName()) ? history.getVodName() : vod.getName();
            String pic = TextUtils.isEmpty(vod.getPic()) ? history.getVodPic() : vod.getPic();
            seedCrossSourceHistory(history, siteKey, vodId, flag.getFlag(), episode, name, pic, targetCid, canceled);
            ArrayList<Episode> chapters = chaptersOf(flag, episode);
            return new ReaderData(kind, payload, siteKey, flag.getFlag(), vodId, name, pic,
                    chapters, indexOf(chapters, episode));
        } catch (Throwable ignore) {
            return null;
        }
    }

    static Episode findHistoryEpisode(Flag flag, History history) {
        if (flag == null || history == null) return null;
        String url = history.getEpisodeUrl();
        if (!TextUtils.isEmpty(url)) {
            for (Episode episode : flag.getEpisodes()) {
                if (episode != null && TextUtils.equals(url, episode.getUrl())) return episode;
            }
        }
        String name = history.getVodRemarks();
        return TextUtils.isEmpty(name) ? null : flag.find(name, true);
    }

    private static void seedCrossSourceHistory(History history, String siteKey, String vodId,
                                               String flag, Episode episode, String name, String pic,
                                               int targetCid) {
        if (history == null || TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId)) return;
        History target = ReaderHistory.find(targetCid, siteKey, vodId);
        if (target != null) {
            String expectedKey = ReaderHistory.buildKey(siteKey, vodId, targetCid);
            target.replace(expectedKey);
            alignResolvedHistoryProgress(target, history, episode.getUrl());
            target.setCid(targetCid);
            target.setVodName(name);
            target.setVodPic(pic);
            target.setVodFlag(flag);
            target.setVodRemarks(episode.getName());
            target.setEpisodeUrl(episode.getUrl());
            target.setMediaType(ReaderHistory.MEDIA_TYPE);
            target.setCreateTime(System.currentTimeMillis());
            ReaderHistory.saveRow(target);
            return;
        }

        History seed = new History();
        seed.setKey(ReaderHistory.buildKey(siteKey, vodId, targetCid));
        seed.setCid(targetCid);
        seed.setVodName(name);
        seed.setVodPic(pic);
        seed.setVodFlag(flag);
        seed.setVodRemarks(episode.getName());
        alignResolvedHistoryProgress(seed, history, episode.getUrl());
        seed.setEpisodeUrl(episode.getUrl());
        seed.setTmdbId(0);
        seed.setMediaType(ReaderHistory.MEDIA_TYPE);
        seed.setTmdbSeasonNumber(0);
        seed.setTmdbEpisodeNumber(0);
        seed.setCreateTime(System.currentTimeMillis());
        ReaderHistory.saveRow(seed);
    }

    private static void seedCrossSourceHistory(History history, String siteKey, String vodId,
                                               String flag, Episode episode, String name, String pic,
                                               int targetCid, AtomicBoolean canceled) {
        synchronized (canceled) {
            if (canceled.get()) return;
            seedCrossSourceHistory(history, siteKey, vodId, flag, episode, name, pic, targetCid);
        }
    }

    /**
     * A resolved chapter must not inherit the target row's progress when its URL changed.
     * Keep progress only when the target row already represents the resolved chapter; otherwise
     * use the source row's progress, or clear it when the source has no valid chapter position.
     */
    static void alignResolvedHistoryProgress(History target, History source, String resolvedEpisodeUrl) {
        if (target == null || source == null || TextUtils.isEmpty(resolvedEpisodeUrl)) return;
        if (TextUtils.equals(target.getEpisodeUrl(), resolvedEpisodeUrl)) return;
        if (ReaderHistory.isReaderRecord(source) && source.hasPlaybackTime()) {
            target.setPosition(source.getPosition());
            target.setDuration(source.getDuration());
        } else {
            target.resetPlaybackPosition();
        }
    }

    private static boolean canDefaultToFirstChapter(Vod vod, History history) {
        if (vod == null || vod.getFlags() == null || vod.getFlags().size() != 1) return false;
        Flag flag = vod.getFlags().get(0);
        if (flag == null || flag.getEpisodes() == null || flag.getEpisodes().size() != 1) return false;
        return TextUtils.isEmpty(history.getEpisodeUrl())
                && TextUtils.isEmpty(history.getVodRemarks());
    }

    private static void openReaderData(Activity activity, ReaderData data) {
        Intent intent = new Intent(activity, WebReaderActivity.class);
        intent.putExtra(WebReaderActivity.EXTRA_KIND, data.kind);
        intent.putExtra(WebReaderActivity.EXTRA_CACHE_KEY,
                WebReaderActivity.cacheLargeData(data.payload, data.chapters));
        intent.putExtra(WebReaderActivity.EXTRA_SITE_KEY, data.siteKey);
        intent.putExtra(WebReaderActivity.EXTRA_FLAG, data.flag);
        intent.putExtra(WebReaderActivity.EXTRA_VOD_ID, data.vodId);
        intent.putExtra(WebReaderActivity.EXTRA_VOD_NAME, data.name);
        intent.putExtra(WebReaderActivity.EXTRA_VOD_PIC, data.pic);
        intent.putExtra(WebReaderActivity.EXTRA_INDEX, data.index);
        activity.startActivity(intent);
    }

    private record ReaderData(int kind, String payload, String siteKey, String flag,
                              String vodId, String name, String pic,
                              ArrayList<Episode> chapters, int index) {
    }

    /**
     * 站点级分流：命中小说 / 漫画源规则时，后台取详情 + 解析首章，直接进阅读器，
     * 不再打开播放页（解决「先进播放页再报错」）。
     *
     * @return true 表示已接管（异步进行中），调用方应立即 return
     */
    public static boolean openSite(Activity activity, String key, String id, String name, String pic, String mark) {
        if (activity == null || !isReaderSite(key)) return false;
        String pure = pureSiteKey(key);
        boolean comicSite = ComicSourceConfig.isEnabledByKey(pure);

        ProgressDialog pd = new ProgressDialog(activity);
        pd.setMessage("正在加载…");
        pd.setCancelable(true);
        pd.show();
        int request = CONTENT_REQUESTS.incrementAndGet();
        AtomicBoolean canceled = new AtomicBoolean(false);

        Future<?> future = executor.submit(() -> {
            String error = null;
            Runnable launch = null;
            try {
                Result detail = SiteApi.detailContent(pure, id);
                Vod vod = detail.getVod();
                vod.checkName(name);
                vod.checkPic(pic);
                Flag flag = vod.getFlags().isEmpty() ? null : vod.getFlags().get(0);
                if (flag == null || flag.getEpisodes().isEmpty()) throw new IllegalStateException("没有可阅读的章节");
                Episode ep = flag.getEpisodes().get(0);

                String payload = ep.getUrl();
                int kind = isComic(payload) ? 2 : isNovel(payload) ? 1 : 0;
                if (kind == 0) {
                    // 章节 url 不是阅读协议 → 解析首章内容
                    Result r = SiteApi.playerContent(pure, flag.getFlag(), ep.getUrl());
                    String u = readerPayload(r);
                    if (u == null || u.isEmpty()) throw new IllegalStateException("章节内容为空");
                    kind = isComic(u) ? 2 : isNovel(u) ? 1 : 0;
                    if (kind == 0) {
                        // 仍不是阅读协议：要求二次解析或是普通视频 → 交回原流程
                        if (r.needParse() || Sniffer.isVideoFormat(u.trim())
                                || u.trim().startsWith("http://") || u.trim().startsWith("https://")) {
                            throw new FallbackException();
                        }
                        kind = comicSite ? 2 : 1;
                    }
                    payload = u;
                }

                final int finalKind = kind;
                final String finalPayload = payload;
                final Flag finalFlag = flag;
                final Vod finalVod = vod;
                final Episode finalEp = ep;
                launch = () -> {
                    if (finalKind == 2) openPics(activity, pure, finalFlag, finalVod, finalEp, finalPayload);
                    else openReader(activity, pure, finalFlag, finalVod, finalEp, finalPayload);
                };
            } catch (FallbackException fe) {
                error = null; // 静默回退，由主线程走原播放流程
            } catch (Throwable e) {
                error = e.getMessage() == null || e.getMessage().isEmpty() ? "加载失败" : e.getMessage();
            }

            final String finalError = error;
            final Runnable finalLaunch = launch;
            final boolean fallback = launch == null && error == null;
            mainHandler().post(() -> {
                try { pd.dismiss(); } catch (Throwable ignore) {}
                if (canceled.get() || request != CONTENT_REQUESTS.get() || isDead(activity)) return;
                if (finalLaunch != null) finalLaunch.run();
                else if (fallback) openPlayerFallback(activity, key, id, name, pic, mark);
                else Notify.show(finalError);
            });
        });
        pd.setOnCancelListener(ignored -> {
            canceled.set(true);
            CONTENT_REQUESTS.compareAndSet(request, request + 1);
            future.cancel(true);
        });
        return true;
    }

    /** 站点命中规则但内容不是阅读数据时，回退到普通播放流程。 */
    private static void openPlayerFallback(Activity activity, String key, String id, String name, String pic, String mark) {
        fallbackLauncher.launch(activity, key, id, name, pic, mark);
    }

    /** 由各 flavor 的 VideoActivity 注入（避免 main 源集直接依赖 flavor 类）。 */
    public interface FallbackLauncher {
        void launch(Activity activity, String key, String id, String name, String pic, String mark);
    }

    public static volatile FallbackLauncher fallbackLauncher = (a, k, i, n, p, m) -> Notify.show("内容无法阅读");

    /** 内部信号：内容应交回普通播放流程。 */
    private static final class FallbackException extends RuntimeException {
        FallbackException() { super(null, null, false, false); }
    }

    /** 单 URL 入口（ContentDispatcher.dispatchUrl / 推送场景）。 */
    public static boolean openReaderUrl(Activity activity, String url, String title) {
        if (activity == null || url == null) return false;
        String u = url.trim();
        int kind = u.startsWith("novel://") ? 1 : (u.startsWith("pics://") || u.startsWith("manga://")) ? 2 : 0;
        if (kind == 0) return false;
        Intent it = new Intent(activity, WebReaderActivity.class);
        it.putExtra(WebReaderActivity.EXTRA_KIND, kind);
        it.putExtra(WebReaderActivity.EXTRA_CACHE_KEY, WebReaderActivity.cacheLargeData(u, new ArrayList<>()));
        it.putExtra(WebReaderActivity.EXTRA_VOD_NAME, title == null ? "" : title);
        it.putExtra(WebReaderActivity.EXTRA_INDEX, 0);
        activity.startActivity(it);
        return true;
    }

    /**
     * 站点规则判定：站点命中漫画源 / 小说源配置，且返回内容确实是阅读内容时，按阅读处理。
     *
     * 注意：只接受「已经是阅读内容」的返回。爬虫返回 parse=1 / jx=1（要求二次解析）
     * 或普通 http 视频地址时一律放行给原播放链路 —— 例如漫画爬虫解析失败后会兜底返回
     * {parse:1, url:原始API地址}，此时抢进阅读器只会显示破图，交回播放器才能暴露真实错误。
     *
     * @return 0=不处理 1=小说 2=漫画
     */
    private static int kindBySiteRule(String siteKey, Result result) {
        String key = pureSiteKey(siteKey);
        if (key.isEmpty()) return 0;

        boolean comic = ComicSourceConfig.isEnabledByKey(key);
        boolean novel = NovelSourceConfig.isEnabledByKey(key);
        if (!comic && !novel) return 0;

        // 要求二次解析 → 内容还没解析出来，不是阅读数据
        if (result.needParse()) return 0;

        String u = readerPayload(result);
        if (u == null || u.isEmpty()) return 0;
        u = u.trim();

        // 正常视频地址（漫画站混放视频预告等）→ 交回播放器
        if (Sniffer.isVideoFormat(u)) return 0;
        // 裸 http(s) 地址：既不是阅读协议也没解析完，交回播放器（避免把 API 地址当图片列表）
        if (u.startsWith("http://") || u.startsWith("https://")) return 0;

        return comic ? 2 : 1;
    }

    private static String pureSiteKey(String siteKey) {
        String sk = siteKey == null ? "" : siteKey;
        int at = sk.indexOf("@@@");
        return at > 0 ? sk.substring(0, at) : sk;
    }

    /** 当前前台的阅读器实例（用于切换章节后回传解析结果，避免重复启动）。 */
    public static volatile WebReaderActivity currentReader;

    /**
     * 播放器宿主（VideoActivity 实现 NovelReaderHost，负责执行解析任务）。
     *
     * 用弱引用持有：宿主是 Activity，静态强引用会把整个播放页（含 player、adapter、bitmap）
     * 留到进程结束。宿主已销毁时取到 null，调用方会退回「阅读器自行解析」。
     */
    private static volatile java.lang.ref.WeakReference<NovelReaderHost> hostRef;

    public static void setHost(NovelReaderHost h) {
        hostRef = h == null ? null : new java.lang.ref.WeakReference<>(h);
    }

    public static NovelReaderHost getHost() {
        java.lang.ref.WeakReference<NovelReaderHost> ref = hostRef;
        return ref == null ? null : ref.get();
    }
    /** 阅读器关闭时刻（单调时钟），用于拦截「返回后残留 playerContent 回调又重新拉起阅读器」。 */
    public static volatile long readerClosedAt = 0L;

    /**
     * 阅读器关闭代号：每次交还前台自增一次。
     *
     * 单靠 readerClosedAt 的 1500ms 窗口不够 —— 用户点了下一章又立刻返回时，爬虫可能几秒后才回，
     * 这条迟到的结果落在窗口外就会重新拉起阅读器（就是「返回不了、只能强杀」的表现）。
     * 阅读器发起切章时记下代号，结果回来时比对：代号变过说明这期间阅读器被关过，结果作废。
     */
    private static volatile long readerCloseGen = 0L;

    /**
     * 交给宿主解析、尚未收尾的切章请求数。
     *
     * 只需要「数量」而不需要「身份」：抑制规则本身是「用户按返回那一刻若有请求在途，
     * 它们的结果一律不许再拉起阅读器」。按身份追踪反而做不对 —— 结果送达那一刻
     * 拿不到「这是哪一章的」，之前按令牌 / 按最早 / 整表清空的写法都会删错条目。
     */
    private static final java.util.concurrent.atomic.AtomicInteger inFlightChapters = new java.util.concurrent.atomic.AtomicInteger();

    /** 关闭时在途的请求数：它们的结果还在路上，必须逐个拦下。 */
    private static final java.util.concurrent.atomic.AtomicInteger staleChapterResults = new java.util.concurrent.atomic.AtomicInteger();

    /** 上述待拦额度的失效时刻（单调时钟）；过期即清，避免永久吞掉合法打开。 */
    private static volatile long staleUntil = 0L;

    /**
     * 待拦结果的有效期。
     *
     * 宿主解析有多条静默失败路径不回到本类（playerContent 报错走 onError、
     * 二次解析出来是普通视频地址），那些结果永远不会到达。用时限兜底，
     * 否则额度下不去，用户之后主动打开别的书会被一直误吞。
     * 取 45s：HTML 侧切章看门狗 30s 就会放弃并回退章节。
     */
    private static final long PENDING_CHAPTER_TTL = 45_000L;

    /** 交还前台时调用：作废所有在途的切章结果。 */
    public static void markReaderClosed() {
        // 单调时钟：wall clock 被 NTP 校正 / 用户改时间往回跳时，
        // 「现在 - 关闭时刻」会变成大负数而恒小于窗口，静默期就永不结束，
        // 之后所有阅读打开都被当成残留回调拦掉。
        readerClosedAt = android.os.SystemClock.elapsedRealtime();
        readerCloseGen++;
        // 关闭这一刻仍在途的请求，其结果都属于「上一轮」，逐个拦下。
        // 没有在途请求时不留额度 —— 否则会无条件吞掉关闭后的第一次合法打开。
        int pending = inFlightChapters.getAndSet(0);
        if (pending > 0) {
            staleChapterResults.addAndGet(pending);
            staleUntil = Math.max(staleUntil, readerClosedAt + PENDING_CHAPTER_TTL);
        }
    }

    /** 阅读器把切章交给宿主解析前调用。 */
    public static void noteChapterRequest() {
        inFlightChapters.incrementAndGet();
    }

    /** 一次在途请求已收尾（结果送达或判失败）。 */
    public static void endChapterRequest() {
        inFlightChapters.updateAndGet(n -> n > 0 ? n - 1 : 0);
    }

    /**
     * 这条结果是否属于「已经被关掉的那个阅读器」发起的切章。
     * 是则必须丢弃：用户已经返回，重新拉起阅读器就是返回键失效的根因。
     */
    private static boolean isStaleChapterResult() {
        if (staleChapterResults.get() <= 0) return false;
        if (android.os.SystemClock.elapsedRealtime() > staleUntil) {
            staleChapterResults.set(0);
            return false;
        }
        staleChapterResults.updateAndGet(n -> n > 0 ? n - 1 : 0);
        return true;
    }

    /** 公开一次性消费判定：true 表示这是已关闭阅读器的迟到结果。 */
    public static boolean consumeStaleChapterResult() {
        return isStaleChapterResult();
    }



    /** 关闭后的静默期：刚返回时残留的回调一律不再拉起阅读器。 */
    private static boolean justClosed() {
        return readerClosedAt > 0 && android.os.SystemClock.elapsedRealtime() - readerClosedAt < 1500;
    }

    /** 前台没有阅读器时，判断这条结果该不该拉起新阅读器。 */
    private static boolean shouldSuppressRelaunch() {
        // 两个判定都要执行，不能用 || 短路：isStaleChapterResult() 是一次性的读后清，
        // 被短路掉标记就留了下来，等静默期过后用户主动打开另一本书时会被它误吞。
        boolean stale = isStaleChapterResult();
        return justClosed() || stale;
    }

    /**
     * 播放入口汇聚点（PlaybackActivity.startPlayer）调用：
     * playerContent 已返回 novel:// / pics:// / manga:// 这类「阅读内容协议」时，
     * 把 JSON 内容注入到本地阅读器 Web 模板（WebReaderActivity）渲染，全屏阅读。
     *
     * 切换章节时播放器会再次走到这里，此时阅读器已在前台 → 直接回传结果，不再启动新实例。
     *
     * @param activity 当前 Activity（VideoActivity / TmdbDetailActivity 等）
     * @param result   playerContent 返回的 Result，getRealUrl() 即阅读内容协议
     * @param key      PlaybackActivity.startPlayer 传入的 key（可能是 getHistoryKey 含 @@@，需提取纯 siteKey）
     * @param vod      当前 Vod（含整本书章节列表）；为 null 时阅读器仍显示当前章内容（无章节导航）
     */
    public static boolean routeReaderEngine(Activity activity, Result result, String key, Vod vod) {
        if (activity == null || result == null) return false;
        int kind = readerUrlKind(result);
        if (kind == 0) return false;

        String payload = readerPayload(result);
        String flag = result.getFlag() == null ? "" : result.getFlag();

        // 关键修复：startPlayer 传入的 key 是 getHistoryKey()（siteKey@@@vodId@@@1），
        // 而 SiteApi.playerContent 需要纯 siteKey。这里提取纯 siteKey 供阅读器切章时使用。
        String siteKey = key == null ? "" : key;
        int atIdx = siteKey.indexOf("@@@");
        if (atIdx > 0) siteKey = siteKey.substring(0, atIdx);

        if (activity instanceof NovelReaderHost) setHost((NovelReaderHost) activity);

        // 整本书章节列表（跨所有线路合并）
        ArrayList<Episode> ch = new ArrayList<>();
        if (vod != null && vod.getFlags() != null) {
            for (Flag f : vod.getFlags()) {
                if (f != null && f.getEpisodes() != null) ch.addAll(f.getEpisodes());
            }
        }

        // 当前章节：用 payload 中的 title 与章节名匹配
        int index = 0;
        String payloadTitle = extractTitle(payload);
        if (payloadTitle != null && !ch.isEmpty()) {
            for (int i = 0; i < ch.size(); i++) {
                if (payloadTitle.equals(ch.get(i).getName())) { index = i; break; }
            }
        }

        // 阅读器已在前台 → 回传解析结果，不重复启动（解决「切换章节回不到播放器」）
        WebReaderActivity reader = currentReader;
        if (reader != null && !reader.isFinishing() && !reader.isDestroyed()) {
            if (!reader.hasPendingHostChapterRequest() && NovelRouter.consumeStaleChapterResult()) return false;
            // 不在这里清表：结清哪一条只有阅读器知道（它持有本次请求的令牌），
            // 在这里猜或整表清空都会抹掉另一次仍在途的请求，返回键会重新失效。
            reader.onEpisodeResolved(kind, payload, extractTitle(payload));
            return true;
        }

        // 用户刚关闭阅读器（1.5 秒内），说明这是返回后残留的 playerContent 回调，
        // 不再拉起阅读器，让播放器页面正常展示。
        if (shouldSuppressRelaunch()) {
            return false;
        }

        Intent it = new Intent(activity, WebReaderActivity.class);
        it.putExtra(WebReaderActivity.EXTRA_KIND, kind); // 1=小说 2=漫画
        it.putExtra(WebReaderActivity.EXTRA_CACHE_KEY, WebReaderActivity.cacheLargeData(payload, ch));
        it.putExtra(WebReaderActivity.EXTRA_SITE_KEY, siteKey);
        it.putExtra(WebReaderActivity.EXTRA_FLAG, flag);
        it.putExtra(WebReaderActivity.EXTRA_VOD_ID, vod == null ? "" : vod.getId());
        it.putExtra(WebReaderActivity.EXTRA_VOD_NAME, vod == null ? "" : vod.getName());
        it.putExtra(WebReaderActivity.EXTRA_VOD_PIC, vod == null ? "" : vod.getPic());
        it.putExtra(WebReaderActivity.EXTRA_INDEX, index);
        activity.startActivity(it);
        return true;
    }

    /** 从 novel:// / pics:// payload 中提取 title（用于匹配当前章节）。 */
    private static String extractTitle(String payload) {
        if (payload == null) return null;
        String s = payload.trim();
        if (s.startsWith("novel://")) s = s.substring("novel://".length()).trim();
        else if (s.startsWith("pics://") || s.startsWith("manga://")) s = s.substring(s.indexOf("://") + 3);
        try {
            com.google.gson.JsonElement el = new com.google.gson.Gson().fromJson(s, com.google.gson.JsonElement.class);
            if (el != null && el.isJsonObject()) {
                com.google.gson.JsonElement t = el.getAsJsonObject().get("title");
                if (t != null && t.isJsonPrimitive()) return t.getAsString();
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    private static ArrayList<Episode> chaptersOf(Flag flag, Episode current) {
        ArrayList<Episode> list = new ArrayList<>();
        if (flag != null && flag.getEpisodes() != null) list.addAll(flag.getEpisodes());
        if (list.isEmpty() && current != null) list.add(current);
        return list;
    }

    private static int indexOf(ArrayList<Episode> list, Episode ep) {
        int i = list.indexOf(ep);
        return i < 0 ? 0 : i;
    }

    public static void openReader(Context ctx, String siteKey, Flag flag, Vod vod, Episode ep, String payload) {
        ArrayList<Episode> ch = chaptersOf(flag, ep);
        Intent it = new Intent(ctx, WebReaderActivity.class);
        it.putExtra(WebReaderActivity.EXTRA_KIND, 1);
        it.putExtra(WebReaderActivity.EXTRA_CACHE_KEY, WebReaderActivity.cacheLargeData(payload, ch));
        it.putExtra(WebReaderActivity.EXTRA_SITE_KEY, pureSiteKey(siteKey));
        it.putExtra(WebReaderActivity.EXTRA_FLAG, flag == null ? "" : flag.getFlag());
        it.putExtra(WebReaderActivity.EXTRA_VOD_ID, vod == null ? "" : vod.getId());
        it.putExtra(WebReaderActivity.EXTRA_VOD_NAME, vod == null ? "" : vod.getName());
        it.putExtra(WebReaderActivity.EXTRA_VOD_PIC, vod == null ? "" : vod.getPic());
        it.putExtra(WebReaderActivity.EXTRA_INDEX, indexOf(ch, ep));
        ctx.startActivity(it);
    }

    public static void openPics(Context ctx, String siteKey, Flag flag, Vod vod, Episode ep, String payload) {
        ArrayList<Episode> ch = chaptersOf(flag, ep);
        Intent it = new Intent(ctx, WebReaderActivity.class);
        it.putExtra(WebReaderActivity.EXTRA_KIND, 2);
        it.putExtra(WebReaderActivity.EXTRA_CACHE_KEY, WebReaderActivity.cacheLargeData(payload, ch));
        it.putExtra(WebReaderActivity.EXTRA_SITE_KEY, pureSiteKey(siteKey));
        it.putExtra(WebReaderActivity.EXTRA_FLAG, flag == null ? "" : flag.getFlag());
        it.putExtra(WebReaderActivity.EXTRA_VOD_ID, vod == null ? "" : vod.getId());
        it.putExtra(WebReaderActivity.EXTRA_VOD_NAME, vod == null ? "" : vod.getName());
        it.putExtra(WebReaderActivity.EXTRA_VOD_PIC, vod == null ? "" : vod.getPic());
        it.putExtra(WebReaderActivity.EXTRA_INDEX, indexOf(ch, ep));
        ctx.startActivity(it);
    }
}
