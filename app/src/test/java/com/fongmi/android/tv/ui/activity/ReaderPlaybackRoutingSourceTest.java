package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class ReaderPlaybackRoutingSourceTest {

    @Test
    public void readerResultNeverFallsThroughToVideoPipeline() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java");

        int route = source.indexOf("NovelRouter.isReaderUrl(result)");
        int pipeline = source.indexOf("player().parse(", route);
        int unconditionalReturn = source.indexOf("return;", route);

        assertTrue("reader routing must exist in startPlayer", route >= 0);
        assertTrue("reader result must return before the video pipeline", unconditionalReturn > route
                && (pipeline < 0 || unconditionalReturn < pipeline));
    }

    @Test
    public void historyEntryUsesReaderRouteBeforeCreatingPlaybackPage() throws Exception {
        String coordinator = read("app/src/main/java/com/fongmi/android/tv/ui/activity/HistoryResumeCoordinator.java");
        String router = read("app/src/main/java/com/fongmi/android/tv/ui/novel/NovelRouter.java");

        int readerRoute = coordinator.indexOf("NovelRouter.openHistory(activity, history");
        int playbackRoute = coordinator.indexOf("VideoActivity.startFromHistory(activity, history");
        int historyMethod = router.indexOf("public static boolean openHistory(");
        int currentSourceGate = coordinator.indexOf("!Setting.isGlobalHistoryEnabled() || history.isCurrentSourceAvailable()");

        assertTrue("history entry must try the reader route first", readerRoute >= 0);
        assertTrue("reader history route must run before the playback fallback",
                playbackRoute < 0 || readerRoute < playbackRoute);
        assertTrue("cross-config history must resolve its source before trying the reader route",
                currentSourceGate >= 0 && currentSourceGate < readerRoute);
        assertTrue("history reader route must resolve the recorded book, not a blank playback page",
                historyMethod >= 0
                        && router.indexOf("history.getVodId()", historyMethod) > historyMethod
                        && router.indexOf("history.getEpisodeUrl()", historyMethod) > historyMethod);
    }

    @Test
    public void historyReaderRouteUsesTheCompleteResolvedPayload() throws Exception {
        String router = read("app/src/main/java/com/fongmi/android/tv/ui/novel/NovelRouter.java");

        int resolve = router.indexOf("private static ReaderData resolveHistory(");
        int payload = router.indexOf("String content = readerPayload(result);", resolve);

        assertTrue("history reader routing must pass the complete playUrl+url payload to WebReaderActivity",
                resolve >= 0 && payload > resolve);
        assertEquals("all Result reader entry points must use the same resolved payload",
                4, countOccurrences(router, "String payload = readerPayload(result);"));
    }

    @Test
    public void historyReaderRouteBindsTargetConfigAndSupportsCancellation() throws Exception {
        String router = read("app/src/main/java/com/fongmi/android/tv/ui/novel/NovelRouter.java");
        String coordinator = read("app/src/main/java/com/fongmi/android/tv/ui/activity/HistoryResumeCoordinator.java");

        int open = router.indexOf("public static boolean openHistory(Activity activity, History history, Vod target,");
        assertTrue("history route must capture an immutable target config", open >= 0
                && router.indexOf("int targetCid", open) > open
                && router.indexOf("targetCid != VodConfig.getCid()", open) > open);
        assertTrue("history route must invalidate superseded and cancelled requests",
                router.contains("AtomicBoolean canceled = new AtomicBoolean(false);")
                        && router.contains("request != HISTORY_REQUESTS.get()")
                        && router.contains("future.cancel(true);"));
        assertTrue("cross-source resume must pass its target config into the reader route",
                coordinator.contains("resolved.episode(), targetCid,"));
    }

    @Test
    public void readerHistoryIsNamespacedAwayFromPlaybackHistory() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/novel/ReaderHistory.java");

        assertTrue("reader progress must use an explicit reader key namespace",
                source.contains("private static final String KEY_NAMESPACE = AppDatabase.SYMBOL + MEDIA_TYPE;"));
        assertTrue("reader rows must be typed before they can be reused",
                source.contains("static boolean isReaderRecord(@Nullable History history)"));
        assertTrue("saving reader progress must preserve a captured config id",
                source.contains("private static void saveSync(int cid, Record record"));
    }

    @Test
    public void readerProgressRefreshesHistoryAfterEverySave() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/novel/ReaderHistory.java");

        boolean savedThenRefreshed = Pattern.compile(
                "saveRow\\(history\\);\\s*App\\.post\\(RefreshEvent::history\\);")
                .matcher(source).find();

        assertTrue("reader history must save and refresh the updated row together",
                savedThenRefreshed);
        assertTrue("reader history must refresh the history view after saving progress",
                savedThenRefreshed);
    }

    @Test
    public void readerAsyncDialogsCancelTheirBackgroundLaunches() throws Exception {
        String router = read("app/src/main/java/com/fongmi/android/tv/ui/novel/NovelRouter.java");

        assertEquals("route, openHistory and openSite must all wire cancellation",
                3, countOccurrences(router, "setOnCancelListener"));
        assertTrue(router.contains("future.cancel(true);"));
        assertTrue(router.contains("canceled.get()"));
    }

    @Test
    public void crossSourceReaderSeedMigratesKeyAndAlignsChapterProgress() throws Exception {
        String router = read("app/src/main/java/com/fongmi/android/tv/ui/novel/NovelRouter.java");

        assertTrue(router.contains("target.replace(expectedKey);"));
        assertTrue(router.contains("alignResolvedHistoryProgress(target, history, episode.getUrl());"));
        assertTrue(router.contains("target.setMediaType(ReaderHistory.MEDIA_TYPE);"));
    }

    @Test
    public void crossSourceReaderRouteDoesNotCopyVideoTimeIntoReaderAnchors() throws Exception {
        String router = read("app/src/main/java/com/fongmi/android/tv/ui/novel/NovelRouter.java");

        assertTrue("reader candidate gating must require a reader source or reader record",
                router.contains("ReaderHistory.isReaderRecord(history)")
                        && router.contains("isReaderUrl(targetEpisode == null ? null : targetEpisode.getUrl())"));
        assertTrue("progress alignment must check the source record type before copying anchors",
                router.contains("ReaderHistory.isReaderRecord(source)")
                        && router.contains("source.hasPlaybackTime()"));
        assertTrue("new target rows must align progress before saving",
                router.contains("alignResolvedHistoryProgress(seed, history, episode.getUrl());"));
    }

    @Test
    public void cancelledHistoryRequestCannotWriteResolvedReaderHistory() throws Exception {
        String router = read("app/src/main/java/com/fongmi/android/tv/ui/novel/NovelRouter.java");

        assertTrue("history resolution must carry its cancellation token",
                router.contains("resolveHistory(history, target, targetFlag, targetEpisode, targetCid, canceled)"));
        assertTrue("history seeding must recheck cancellation immediately before writing",
                router.contains("if (canceled.get()) return;")
                        && router.contains("seedCrossSourceHistory(history, siteKey, vodId"));
    }

    @Test
    public void readerDispatchKeepsHostPageInBackstack() throws Exception {
        for (String path : new String[] {
                "app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java",
                "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java"
        }) {
            String source = read(path);
            int dispatch = source.indexOf("ContentDispatcher.dispatchResult(this");
            int stop = source.indexOf("stopPlayback();", dispatch);
            int finish = source.indexOf("finish();", stop);
            int blockEnd = source.indexOf('}', stop);

            assertTrue(path + " must dispatch results through ContentDispatcher", dispatch >= 0);
            assertTrue(path + " must stop playback after reader dispatch", stop > dispatch);
            assertTrue(path + " must keep its page in the back stack after reader dispatch",
                    finish < 0 || finish > blockEnd);
        }
    }

    @Test
    public void reclaimedOrRepeatedPlayerResultsAreNotStartedTwice() throws Exception {
        for (String path : new String[] {
                "app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java",
                "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java"
        }) {
            String source = read(path);

            assertTrue(path + " must track the result already applied to the player",
                    source.contains("mAppliedPlayerResult"));
            assertTrue(path + " must ignore a duplicate player result while playback remains active",
                    source.contains("if (result == mAppliedPlayerResult && !player().isEmpty()) return;"));
        }
    }

    /**
     * 返回键要能真正退出阅读器。
     *
     * 生命周期顺序是 阅读器 onPause -> 宿主 onResume -> 阅读器 onStop -> 阅读器 onDestroy。
     * 宿主 onResume 会因 shouldReclaim() 重新派发上一次的 playerContent 结果，
     * 关闭时间戳若等到 onDestroy 才写，那一刻 NovelRouter 的两道防线同时失效，
     * 阅读器会被立刻重新拉起，返回键表现为完全无效。
     */
    @Test
    public void readerMarksClosedBeforeHostResumes() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/web/WebReaderActivity.java");

        int pause = source.indexOf("protected void onPause()");
        int markInPause = source.indexOf("markClosed();", pause);
        int superPause = source.indexOf("super.onPause();", pause);
        int destroy = source.indexOf("protected void onDestroy()");

        assertTrue("onPause must exist", pause >= 0);
        assertTrue("onPause must mark the reader closed before super.onPause()",
                markInPause > pause && markInPause < superPause);
        assertTrue("markClosed must run before onDestroy is reached", markInPause < destroy);
        assertTrue("markClosed must clear the reader registration",
                source.contains("NovelRouter.currentReader = null;"));
        assertTrue("markClosed must stamp the close time",
                source.contains("NovelRouter.markReaderClosed();"));
        assertTrue("onDestroy must still mark closed for non-finish teardown",
                source.indexOf("markClosed();", destroy) > destroy);
    }

    @Test
    public void readerMarksClosedBeforeClearingHostRequests() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/web/WebReaderActivity.java");

        int pause = source.indexOf("protected void onPause()");
        int markInPause = source.indexOf("markClosed();", pause);
        int clearRequests = source.indexOf("hostChapterRequests.getAndSet(0)", pause);

        assertTrue("onPause must mark the reader closed before clearing host requests",
                markInPause > pause && clearRequests > pause && markInPause < clearRequests);
    }

    @Test
    public void hostResultClosesRequestBeforeWebViewGuard() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/web/WebReaderActivity.java");

        int method = source.indexOf("private void onEpisodeResolved(int newKind, String payload, String title, boolean fromHost)");
        int webViewGuard = source.indexOf("if (webView == null) return;", method);
        int closeRequest = source.indexOf("if (fromHost) endHostChapterRequest();", method);

        assertTrue("a host result must close its request even when the WebView was already destroyed",
                method >= 0 && webViewGuard > method && closeRequest > method && closeRequest < webViewGuard);
    }

    @Test
    public void parseFallbackKeepsRestoreAnchorUntilHostResultArrives() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/web/WebReaderActivity.java");

        int method = source.indexOf("private void resolveChapterSelf(String chapterUrl)");
        int dispatch = source.indexOf("if (fh && h != null)", method);
        int clear = source.indexOf("restoreAnchor = 0;", dispatch);
        int hostFlag = source.indexOf("boolean hostDispatched = false;", method);
        int guard = source.indexOf("if (!hostDispatched)", dispatch);

        assertTrue("parse=1 已成功交给宿主时，必须保留历史恢复锚点直到结果注入",
                method >= 0 && hostFlag > method && dispatch > method && clear > dispatch
                        && guard > dispatch && guard < clear);
    }

    @Test
    public void staleResultCannotBeDeliveredToAReaderWithoutPendingHostRequest() throws Exception {
        String router = read("app/src/main/java/com/fongmi/android/tv/ui/novel/NovelRouter.java");
        String reader = read("app/src/main/java/com/fongmi/android/tv/ui/web/WebReaderActivity.java");

        assertTrue("当前阅读器必须能报告自己是否有宿主切章请求在途",
                reader.contains("public boolean hasPendingHostChapterRequest()"));
        assertEquals("三个阅读器结果入口都必须拦截旧请求，避免串页",
                3, countOccurrences(router,
                        "!reader.hasPendingHostChapterRequest() && NovelRouter.consumeStaleChapterResult()"));
    }

    /**
     * 首帧不能把进度虚报到批次末尾。
     *
     * 漫画首批只挂 5 张图，未解码时 bottom 全堆在同一 y 上，没有锚点跨过阈值，
     * 循环末尾的 `idx = i` 就把序号推到最后一个锚点 —— 95 页漫画记成第 5 页、
     * 33 段小说记成第 33 段，下次进来直接跳到那里。
     *
     * 注意不能用 rect.height 当「未就绪」信号：小说段落带 content-visibility:auto，
     * 真机实测 height 恒为 0 而 bottom 有效，用 height 判断会让小说进度永远停在第 1 段。
     */
    @Test
    public void readerAnchorIndexShortCircuitsAtTopOfChapter() throws Exception {
        String source = read("app/src/main/assets/reader.html");

        int fn = source.indexOf("function currentAnchorIndex()");
        int guard = source.indexOf("if(scrolled <= 0) return lastAnchorIndex = 0;", fn);
        int loop = source.indexOf("for(", fn);
        int degraded = source.indexOf("return lastAnchorIndex = idx;", loop);

        assertTrue("currentAnchorIndex must exist", fn >= 0);
        assertTrue("stopping at the top must short-circuit to anchor 0 before scanning",
                guard > fn && guard < loop);
        assertTrue("a degraded measurement must not advance the anchor unless the document end is reached",
                degraded > loop);
        // #reader 的 min-height:105vh 让「图全未解码」时轻扫 32px 就到文末，
        // 只判 atDocumentEnd 会把退化值当可信 —— 必须同时要求锚点已全部进 DOM
        assertTrue("reaching the document end alone must not validate a degraded measurement",
                source.contains("anchorsSettled() && atDocumentEnd()"));
        assertFalse("rect.height is always 0 for content-visibility paragraphs; it must not gate the scan",
                source.contains("if(rect.height <= 0) break;"));
        assertTrue("progress bar must read the current anchor, not the loaded count",
                source.contains("Math.min(total, effectiveAnchorIndex() + 1)"));
        // 落库与进度条必须同一个真值，否则会出现「显示 33/33、重进回到第 30 段」的分叉
        assertTrue("the saved anchor must come from the same source as the progress bar",
                source.contains("effectiveAnchorIndex(), anchorTotal());"));
        // 小说正文下方还有 140px 内边距和章节导航，光靠 currentAnchorIndex 够不到最后一段，
        // 历史列表就永远到不了 100%
        assertTrue("a chapter read to its end must be able to record the final anchor",
                source.contains("if(anchorsSettled() && atDocumentEnd()) return total - 1;"));
        // 退化态统一由 anchorsSettled() 把关：漫画末页图加载失败时高度恒为 0、不占空间；
        // PDF 的 canvas 先插入后绘制，未绘制时是固有 300x150（有高度但不是真实页高），
        // 只判「到底」会把用户没看到的内容记成读完，下次直接跳到章末
        assertTrue("a degraded layout must be recognised before trusting the document end",
                source.contains("function anchorsSettled()"));
        // 漫画看末尾两页：图各自异步解码，末页不保证最后完成，只看末页仍会虚报
        assertTrue("a comic must require the last two pages to be laid out",
                source.contains("for(var i = Math.max(0, total - 2); i < total; i++){"));
        assertTrue("a PDF must require every page to be painted, not merely appended",
                source.contains("return pdfDoc != null && pdfAppendedCount >= pdfDoc.numPages;"));
        // 渲染失败也要计数，否则那一章永远达不到 numPages，永远记不成读完
        assertTrue("a failed page render must still count towards the settled total",
                source.contains(".promise.then(pageSettled, pageSettled);"));
        assertTrue("a failed getPage must also count",
                source.contains("}, pageSettled);"));
        // 换章会清零计数，上一章在途的回调若继续 ++ 会把新章撑到 numPages
        assertTrue("stale render callbacks must not inflate the next chapter's count",
                source.contains("var gen = pdfGen;")
                        && source.contains("if(gen !== pdfGen) return;"));
        // #reader 是所有章节共用的容器：getDocument 的成功/失败回调也必须带代号，
        // 否则上一章的慢加载会把新章内容整段替换成 pdf-error，或把旧 doc 塞进新章
        assertEquals("both getDocument handlers must be generation-guarded",
                3, countOccurrences(source, "if(gen !== pdfGen) return;"));
        // 遮罩是全屏 z-index:10002，只有这里会隐藏它：放在代号判定之后，
        // 跨类型换章时旧章早退，遮罩就永久盖在新章上吞掉所有点击
        assertTrue("the loading overlay must be hidden before the generation check",
                source.contains("showLoading(false);") && source.contains("if(gen !== pdfGen) return;"));
        // renderPdf 只在新章也是 PDF 时才调，从 PDF 切到漫画要靠 renderContent 作废代号
        assertTrue("switching away from a PDF chapter must invalidate its callbacks",
                source.contains("pdfGen++;") && source.contains("pdfDoc = null; pdfAppendedCount = 0;"));
        // fulfillment 里同步抛错不会被同一个 then 的 rejection 处理器捕获
        assertTrue("a throw inside the fulfillment handler must still count",
                source.contains("} catch(e){") && source.contains("pageSettled();"));
        assertTrue("currentAnchorIndex must use the same settled check",
                source.contains("if(found || (anchorsSettled() && atDocumentEnd()))"));
        // memo 有副作用：文末分支提前 return 前必须先让 currentAnchorIndex() 跑过一次
        int effFn = source.indexOf("function effectiveAnchorIndex()");
        int memoUpdate = source.indexOf("Math.min(total - 1, currentAnchorIndex());", effFn);
        int endBranch = source.indexOf("if(anchorsSettled() && atDocumentEnd())", effFn);
        assertTrue("the memo must be updated before the document-end shortcut returns",
                memoUpdate > effFn && memoUpdate < endBranch);
    }

    /**
     * 迟到的切章结果不能重新拉起已关闭的阅读器。
     *
     * 1500ms 静默期只挡得住紧随返回的那一拨回调；用户点了下一章又马上返回时，
     * 爬虫可能几秒后才回，这条结果落在窗口外就会另起一个阅读器压在宿主上面，
     * 表现仍然是返回键无效。用关闭代号比对才能识别它属于已经关掉的那个阅读器。
     */
    @Test
    public void staleChapterResultDoesNotRelaunchClosedReader() throws Exception {
        String router = read("app/src/main/java/com/fongmi/android/tv/ui/novel/NovelRouter.java");
        String reader = read("app/src/main/java/com/fongmi/android/tv/ui/web/WebReaderActivity.java");

        // 只数在途请求、不认身份：结果送达那一刻拿不到「这是哪一章的」，
        // 按令牌 / 按最早 / 整表清空都会删错条目，反而放行别人的迟到结果。
        assertTrue("in-flight host requests must be counted",
                router.contains("inFlightChapters.incrementAndGet();")
                        && router.contains("public static void endChapterRequest()"));
        // 关闭那一刻在途的请求，其结果逐个拦下；没有在途请求时不留额度，
        // 否则会无条件吞掉关闭后的第一次合法打开
        assertTrue("closing must convert in-flight requests into suppression credits",
                router.contains("int pending = inFlightChapters.getAndSet(0);")
                        && router.contains("if (pending > 0) {"));
        assertTrue("credits must accumulate and expire so silent failures cannot swallow opens forever",
                router.contains("staleChapterResults.addAndGet(pending);")
                        && router.contains("staleUntil = Math.max(staleUntil, readerClosedAt + PENDING_CHAPTER_TTL);")
                        && router.contains("if (android.os.SystemClock.elapsedRealtime() > staleUntil) {"));
        assertEquals("every relaunch site must consult the suppression guard",
                3, countOccurrences(router, "if (shouldSuppressRelaunch())"));
        assertTrue("the guard must combine the silence window and the stale check",
                router.contains("return justClosed() || stale;"));
        // 两处宿主派发都要记账，漏一处整套判定就失效
        assertEquals("both host paths must register their request",
                2, countOccurrences(reader, "NovelRouter.noteChapterRequest();"));
        // 宿主找不到章节时会静默返回，必须立刻收尾而不是等 45s 过期
        assertEquals("a dispatch that never happened must close out immediately",
                2, countOccurrences(reader, "if (!h.labPlayEpisode(chapterUrl)) chapterFailedWithToast(true);"));
    }

    /**
     * 阅读进度落库不需要数据迁移，且读完即 100%。
     *
     * 历史列表按 position/duration 画进度条，若把「读到最后一个锚点」也存成序号，
     * 2 页的漫画短章读完只显示 50%；但若整体改成 1 基，升级前写入的存量记录会被
     * 统一平移一个锚点。折中：只把「读完」编码为 duration，其余沿用序号原值。
     */
    @Test
    public void readingProgressKeepsLegacyRowsAndStillReachesFullBar() throws Exception {
        String history = read("app/src/main/java/com/fongmi/android/tv/ui/novel/ReaderHistory.java");
        String reader = read("app/src/main/java/com/fongmi/android/tv/ui/web/WebReaderActivity.java");

        // 非读完的锚点原样存序号 —— 存量 0 基记录读回来不偏移
        assertTrue("a mid-chapter anchor must be stored as-is so legacy rows still resolve",
                history.contains("long value = Math.max(0, Math.min(duration - 1, anchor));"));
        assertTrue("only the finished state is encoded as duration",
                history.contains("return duration == SCALE ? duration - 1 : duration;"));
        assertTrue("the finished encoding must convert back to the last anchor",
                history.contains("if (position >= duration) return (int) (duration - 1);"));
        assertTrue("restore must go through the converter",
                reader.contains("ReaderHistory.toAnchor(h.getPosition(), restoreTotal)"));
        // 旧版小说记录存的是百分比×SCALE，不能走锚点换算
        assertTrue("legacy percent records must bypass the anchor conversion",
                reader.contains("restoreTotal == ReaderHistory.SCALE"));
        // 百分比分支必须解锁 restoringPage，否则整个会话都不再上报，旧记录也永远迁不到新语义
        String html = read("app/src/main/assets/reader.html");
        int percentFn = html.indexOf("function restoreScrollPercent(p)");
        assertTrue("restoreScrollPercent must exist", percentFn > 0);
        assertTrue("the legacy percent path must release the reporting lock",
                html.indexOf("restoringPage = false;", percentFn) > percentFn
                        && html.indexOf("restoringPage = false;", percentFn) < html.indexOf("function restoreAnchor(index)"));
        // max>0 挡不住：min-height:105vh 让它首帧就成立，上报会把 90% 的进度覆盖成章首
        // anchorsSettled() 对小说恒为真（段落一次性建完），首帧就放行等于没有护栏；
        // content-visibility 让屏外段落先按 30px 估算，此刻 scrollHeight 还会长大
        // 小说的 content-visibility 估算高度在滚动前不变，「高度稳定」对它是空护栏；
        // 判据必须是「滚过去之后换算回来的锚点也稳定」
        assertTrue("the percent restore must converge on the resolved anchor",
                html.contains("if(!settled || lastMax === null || lastMax !== idx) stable = 0;"));
        assertTrue("it must require three stable measurements like restoreAnchor",
                html.contains("var ready = stable >= 3;"));
        assertTrue("the percent restore must be invalidated when content is replaced",
                html.contains("var gen = ++restoreGen;") && html.contains("restoringPage = true;"));
        // 短章内容一屏放得下时高度一开始就稳定，滚到 min-height 撑出的 5vh 后锚点仍是 0，
        // 盲目上报会把用户存的 90% 覆盖成章首
        assertTrue("migrating a percent record must not move the position backwards",
                html.contains("if(effectiveAnchorIndex() >= expected) reportProgress();"));
        // 漫画一批批懒加载，光等不会让剩下的页进 DOM，anchorsSettled 永远为假 ——
        // 旧百分比记录既恢复不了也迁移不了，必须主动催加载
        assertTrue("the percent restore must drive lazy loading for comics and PDFs",
                html.contains("if(DATA.kind === 3) renderPdfMore();")
                        && html.contains("else if(DATA.kind !== 1) loadMoreComic();"));
        assertTrue("the percent restore budget must use the monotonic clock",
                html.contains("var deadline = nowMs() + 60000;"));
    }

    /**
     * 换章必须清掉锚点记忆。
     *
     * PDF 章在 pdfDoc 解析完成前 anchorTotal() 为 0，effectiveAnchorIndex() 会在
     * total<=0 处早退，不经过 currentAnchorIndex() 的归零短路 —— 上一章的高位值
     * 会残留，把新章第 1 页记成上一章的位置。
     */
    @Test
    public void switchingChapterResetsTheAnchorMemo() throws Exception {
        String html = read("app/src/main/assets/reader.html");

        int render = html.indexOf("function renderContent()");
        int reset = html.indexOf("lastAnchorIndex = 0;", render);
        int clear = html.indexOf("r.innerHTML = '';", render);

        assertTrue("renderContent must exist", render > 0);
        assertTrue("the memo must be cleared when the content is replaced",
                reset > render && reset < clear);
    }

    /**
     * 关闭静默期与在途标记的时限都必须用单调时钟。
     *
     * wall clock 会被 NTP 校正或用户改时间往回跳，一旦往回跳，「现在 - 关闭时刻」
     * 变成大负数而恒小于窗口，静默期就永不结束，之后所有阅读打开都被当成残留回调拦掉。
     */
    @Test
    public void relaunchGuardsUseMonotonicClock() throws Exception {
        String router = read("app/src/main/java/com/fongmi/android/tv/ui/novel/NovelRouter.java");

        assertFalse("the silence window must not depend on wall-clock time",
                router.contains("readerClosedAt = System.currentTimeMillis();"));
        assertFalse("the pending-tag TTL must not depend on wall-clock time",
                router.contains("pendingChapterAt = System.currentTimeMillis();"));
        assertTrue("every timestamp and comparison must use elapsedRealtime",
                countOccurrences(router, "android.os.SystemClock.elapsedRealtime()") >= 3);
    }

    /**
     * 只撤销自己发出的那次在途标记。
     *
     * chapterFailed() 还会被空 URL、注入异常等与宿主请求无关的路径调用，
     * 无条件撤销会把另一次仍在途的请求的标记抹掉，重新打开「返回键失效」的缺口。
     */
    @Test
    public void onlyTheOwnHostRequestAbandonsItsPendingTag() throws Exception {
        String reader = read("app/src/main/java/com/fongmi/android/tv/ui/web/WebReaderActivity.java");

        assertTrue("the reader must track how many of its requests are in flight",
                reader.contains("hostChapterRequests = new java.util.concurrent.atomic.AtomicInteger()"));
        // 只有自己确实发过请求才收尾：空 URL、注入异常与任何请求无关，
        // 替别人收尾会让那一笔的迟到结果不再被拦（返回键失效）
        assertTrue("closing out must be gated on owning a request",
                reader.contains("if (ownHostRequest) endHostChapterRequest();"));
        assertTrue("the empty-url path must not close out anyone else",
                reader.contains("chapterFailed(false); return;"));
        assertTrue("the reader must only decrement what it actually holds",
                reader.contains("if (hostChapterRequests.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {"));
    }

    /**
     * 切章成功送达前台后必须清掉在途标记。
     *
     * 「送达前台阅读器」这条路径提前 return，不经过 shouldSuppressRelaunch()，
     * 所以标记不会被 isStaleChapterResult() 顺手清掉。留着它的后果是：
     * 用户关掉阅读器、过一会儿主动打开另一本书时，那次合法打开会被误判成过期结果吞掉。
     */
    @Test
    public void deliveredChapterResultClearsThePendingTag() throws Exception {
        String router = read("app/src/main/java/com/fongmi/android/tv/ui/novel/NovelRouter.java");
        String reader = read("app/src/main/java/com/fongmi/android/tv/ui/web/WebReaderActivity.java");

        // 送达要收尾，但 router 侧不能自作主张删条目 —— 它不知道是哪一笔
        assertFalse("the router must not guess which request was delivered",
                router.contains("            clearChapterRequest();"));
        assertTrue("delivery must close out exactly one in-flight request",
                reader.contains("if (fromHost) endHostChapterRequest();"));
        // 自解析路径没发过请求，不能替宿主收尾
        assertTrue("the self-resolve path must not close out a host request",
                reader.contains("onEpisodeResolved(fk, fp, at >= 0 ? chapters.get(at).getName() : \"\", false);"));
    }

    /**
     * 阅读器回到前台必须重新登记。
     *
     * onPause 交还前台时会清掉注册，只在 onCreate 注册的话，两个阅读器叠栈时
     * 下层那个再次回到前台就永久失去注册，之后它自己的切章会另起一个实例压在自己上面。
     */
    @Test
    public void readerReRegistersOnResume() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/web/WebReaderActivity.java");

        int resume = source.indexOf("protected void onResume()");
        int register = source.indexOf("NovelRouter.currentReader = this;", resume);
        int pause = source.indexOf("protected void onPause()");

        assertTrue("onResume must exist", resume > 0);
        assertTrue("onResume must re-register the reader", register > resume && register < pause);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of(path.substring("app/".length())), StandardCharsets.UTF_8);
    }
}
