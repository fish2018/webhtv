package com.fongmi.android.tv.ui.novel;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Task;

import java.util.Objects;

/**
 * 小说 / 漫画阅读进度记录（对齐 {@link com.fongmi.android.tv.ui.audio.AudioHistory}）。
 *
 * 复用 History 表，因此阅读记录会和影视记录一起出现在历史列表里：
 * - episodeUrl / vodRemarks：上次读到的章节 URL 与章节名，用于重进时定位章节
 * - position / duration：章节内锚点序号与锚点总数（锚点即小说的段落、漫画的页）。
 *   position/duration 恰好等于「已读比例」，历史列表的进度条无需特殊处理；
 *   读到最后一个锚点时 position 记为 duration，让「读完」显示 100%。
 *   恢复时用 {@link #toAnchor(long, long)} 换回锚点序号。
 *
 * 记锚点序号而不记滚动百分比，是因为文档高度并不稳定：漫画图片分批懒加载且异步解码，
 * 小说段落用了 content-visibility:auto（屏外段落先按估算高度占位），
 * 同一像素位置在不同时刻对应的内容不同。锚点序号还能在改字号 / 行高后仍回到同一段文字。
 *
 * 兼容：早期版本的小说记录存的是「百分比 × {@link #SCALE}」且 duration = SCALE，
 * 读取方无法区分，因此仍按 position/duration 得到比例交给 HTML，由 HTML 兜底处理。
 */
public final class ReaderHistory {

    /** 旧版小说进度的放大倍数（现仅用于兼容历史记录）。 */
    public static final long SCALE = 10000L;
    /** 历史表是阅读与影视共用表，阅读记录必须显式标注类型。 */
    public static final String MEDIA_TYPE = "reader";

    private static final String KEY_NAMESPACE = AppDatabase.SYMBOL + MEDIA_TYPE;

    private ReaderHistory() {
    }

    /** 阅读记录 key，与音频一致带上 cid，避免换配置后串记录。 */
    public static String buildKey(String siteKey, String vodId) {
        return buildKey(siteKey, vodId, VodConfig.getCid());
    }

    public static String buildKey(String siteKey, String vodId, int cid) {
        return siteKey + AppDatabase.SYMBOL + vodId + AppDatabase.SYMBOL + cid + KEY_NAMESPACE;
    }

    private static String buildLegacyKey(String siteKey, String vodId) {
        return siteKey + AppDatabase.SYMBOL + vodId;
    }

    public static boolean canUse(String siteKey, String vodId) {
        return !TextUtils.isEmpty(siteKey) && !TextUtils.isEmpty(vodId);
    }

    /** 查询已保存的阅读记录；null 表示没读过。 */
    @Nullable
    public static History find(String siteKey, String vodId) {
        return find(VodConfig.getCid(), siteKey, vodId);
    }

    @Nullable
    public static History find(int cid, String siteKey, String vodId) {
        if (!canUse(siteKey, vodId)) return null;
        History history = History.find(cid, buildKey(siteKey, vodId, cid));
        if (isReaderRecord(history)) return history;
        // 老版本阅读记录没有类型标记，且旧 key 可能与普通视频完全相同；无法可靠区分时
        // 宁可让旧进度失效，也不能把同键的影视进度当作阅读进度或被其覆盖。
        History legacy = History.find(cid, buildLegacyKey(siteKey, vodId));
        return isReaderRecord(legacy) ? legacy : null;
    }

    static boolean isReaderRecord(@Nullable History history) {
        if (history == null) return false;
        if (MEDIA_TYPE.equals(history.getMediaType())) return true;
        return history.getKey().endsWith(KEY_NAMESPACE);
    }

    /**
     * 保存阅读进度（异步）。
     *
     * @param anchor 章节内锚点序号（0 基）：小说=段落，漫画/PDF=页
     * @param total  锚点总数；<= 0 时视为无效位置，不保存
     */
    public static void save(Record record, String chapterName, String chapterUrl, int anchor, int total) {
        if (Setting.isIncognito() || record == null || !record.canUse()) return;
        if (total <= 0) return;
        long duration = total;
        long position = toPosition(anchor, duration);
        int cid = VodConfig.getCid();
        Task.execute(() -> saveSync(cid, record, chapterName, chapterUrl, position, duration));
    }

    /**
     * 锚点序号 → 落库进度值。
     *
     * 历史列表按 position/duration 画进度条。读到最后一个锚点时直接存 duration，
     * 这样「读完」就是 100%（否则 2 页的漫画短章读完只显示 50%）；其余情况沿用
     * 0 基序号，与升级前写入的存量记录同语义，不需要数据迁移。
     *
     * 存量记录不会被误判成「读完」：旧代码的上限是 min(duration, anchor)，而 anchor
     * 最大只有 total-1，所以旧记录的 position 必然小于 duration。
     */
    static long toPosition(int anchor, long duration) {
        long value = Math.max(0, Math.min(duration - 1, anchor));
        if (value < duration - 1) return value;
        // 「读完」编码为 duration。但 duration 恰好等于 SCALE 时不能这么写：
        // 那会让整条记录长得和旧版百分比记录（duration == SCALE）一模一样，
        // 下次恢复走百分比分支，把锚点当成 0~1 的比例用。这种章少记一个锚点即可。
        return duration == SCALE ? duration - 1 : duration;
    }

    /** 落库进度值 → 0 基锚点序号，供阅读器恢复定位。 */
    public static int toAnchor(long position, long duration) {
        // total 无意义时不能把 position 原样当序号返回：那会让恢复逻辑去找一个不存在的锚点
        if (duration <= 0) return 0;
        // position == duration 是「读完」的编码，换回最后一个锚点的序号
        if (position >= duration) return (int) (duration - 1);
        return (int) Math.max(0, position);
    }

    private static void saveSync(int cid, Record record, String chapterName, String chapterUrl,
                                 long position, long duration) {
        String key = buildKey(record.siteKey, record.vodId, cid);
        History history = find(cid, record.siteKey, record.vodId);
        if (history == null) {
            history = new History();
            history.setKey(key);
        } else if (!key.equals(history.getKey())) {
            history.replace(key);
        }
        history.setCid(cid);
        history.setVodName(record.vodName);
        history.setVodPic(record.vodPic);
        history.setVodFlag(record.vodFlag);
        history.setVodRemarks(chapterName);
        history.setEpisodeUrl(chapterUrl);
        history.setMediaType(MEDIA_TYPE);
        history.setPosition(position);
        history.setDuration(duration);
        history.setCreateTime(System.currentTimeMillis());
        saveRow(history);
        App.post(RefreshEvent::history);
    }

    static void saveRow(History history) {
        AppDatabase.get().getHistoryDao().insertOrUpdate(history);
    }

    /** 阅读身份（一本书 / 一部漫画）。 */
    public static final class Record {

        private final String siteKey;
        private final String vodId;
        private final String vodFlag;
        private final String vodName;
        private final String vodPic;

        public Record(String siteKey, String vodId, String vodFlag, String vodName, String vodPic) {
            this.siteKey = Objects.toString(siteKey, "");
            this.vodId = Objects.toString(vodId, "");
            this.vodFlag = Objects.toString(vodFlag, "");
            this.vodName = Objects.toString(vodName, "");
            this.vodPic = Objects.toString(vodPic, "");
        }

        public boolean canUse() {
            return ReaderHistory.canUse(siteKey, vodId);
        }
    }
}
