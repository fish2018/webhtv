# AI 观影报告设计文档

> **状态：✅ 已完成（2026-07-14 核实）**
> - `viewing/` 包完整：`ViewingReportGenerator` / `ViewingReportAiAnalyzer` / `ViewingReportCache` / `ViewingReportRange` / `ViewingReport`。
> - `ViewingReportActivity` + `ViewingReportRangeDialog` 已实现。
> - 入口已接入：mobile `HistoryActivity.onReport()`、leanback `HistoryActivity.onReport()`。
> - DB 迁移 `MIGRATION_36_37` 已为 `History` 增 typeName/area/actor/director/year 字段；这些字段同时作为 AI 推荐的长期偏好信号。
> - 字符串资源含 values / zh-rCN / zh-rTW。

## 1. 概述

### 1.1 功能定位
为用户生成个性化观影统计报告,类似网易云音乐年度听歌报告,包含观影时长、喜好分析、观影习惯等维度。支持灵活的时间范围选择,提升用户粘性和产品趣味性。

### 1.2 目标用户
- 核心用户:观影历史超过 10 部的活跃用户
- 场景:年末总结、查看观影习惯、社交分享

### 1.3 核心价值
- **用户价值**:可视化观影数据,了解自己的观影偏好
- **产品价值**:提升用户参与度,社交传播性强
- **技术价值**:复用现有 AI 配置能力,低成本高收益

---

## 2. 功能设计

### 2.1 入口设计

#### 2.1.1 手机端 (Mobile)
**位置**: 历史记录 Toolbar 右侧新增按钮

**UI 变更**:
- `app/src/mobile/res/menu/menu_history.xml` 新增 `report` item
- 顺序: [同步] [删除] [**报告**]
- 图标: `ic_action_report` (📊 图表图标)

```xml
<!-- 新增到 menu_history.xml -->
<item
    android:id="@+id/report"
    android:icon="@drawable/ic_action_report"
    android:title="@string/menu_viewing_report"
    app:showAsAction="ifRoom" />
```

**交互流程**:
1. 点击报告按钮 → 弹出时间范围选择对话框
2. 选择范围后 → 显示加载中
3. 生成完成 → 跳转到报告展示页

#### 2.1.2 TV 端 (Leanback)
**位置**: 历史记录列表顶部新增卡片入口

**UI 变更**:
- `app/src/leanback/res/layout/activity_history.xml` 顶部增加横向按钮区域
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/HistoryActivity.java` 新增按钮响应

**布局方案**:
```
┌─────────────────────────────────────────┐
│ [🗑️ 清空历史]  [📊 观影报告]            │  ← 新增按钮区
└─────────────────────────────────────────┘
┌─────────────────────────────────────────┐
│                                         │
│        历史记录列表 RecyclerView         │
│                                         │
└─────────────────────────────────────────┘
```

**遥控器交互**:
- 进入历史页面默认焦点在列表第一项
- 按上键 → 焦点移至顶部按钮区
- 按确认键 → 弹出时间范围选择对话框

---

### 2.2 时间范围选择

#### 2.2.1 可选范围
参考业界标准(网易云音乐、QQ音乐、Spotify),提供以下预设:

| 选项 | 说明 | 数据范围 |
|-----|------|---------|
| **全部** | 所有历史记录 | `createTime > 0` |
| **本年** | 当前自然年 | `2026-01-01 00:00` 至今 |
| **上半年** | 当前年份 1-6 月 | `2026-01-01` ~ `2026-06-30` |
| **下半年** | 当前年份 7-12 月 | `2026-07-01` ~ `2026-12-31` |
| **本季度** | 当前季度 | Q3: `2026-07-01` ~ `2026-09-30` |
| **本月** | 当前自然月 | `2026-07-01` ~ `2026-07-31` |
| **本周** | 本周一至今 | `2026-07-07` (周一) 至今 |
| **最近 30 天** | 滚动 30 天 | `now - 30 days` |
| **最近 7 天** | 滚动 7 天 | `now - 7 days` |

**默认选项**: 
- 首次使用: **全部** (展示完整数据,效果最佳)
- 7 月后访问: 自动推荐 **本年**
- 12 月后访问: 高亮推荐 **本年** 并标注"年度报告"

#### 2.2.2 对话框设计

**Mobile 端**:
```
┌────────────────────────────┐
│    选择报告时间范围         │
├────────────────────────────┤
│ ⚪ 全部                    │
│ ⚪ 本年 (2026)             │
│ ⚪ 上半年                  │
│ ⚪ 下半年                  │
│ ⚪ 本季度 (Q3)             │
│ ⚪ 本月 (7月)              │
│ ⚪ 本周                    │
│ ⚪ 最近 30 天              │
│ ⚪ 最近 7 天               │
├────────────────────────────┤
│        [取消]  [生成报告]   │
└────────────────────────────┘
```

**TV 端**:
- 单选列表对话框,遥控器上下键选择
- 确认键直接生成,返回键取消

---

### 2.3 报告内容设计

#### 2.3.1 数据维度

**基础统计** (无需 AI):
- 观影总时长 (小时)
- 观看作品数量
- 观看集数 (剧集总集数)
- 最常观看时段 (凌晨/上午/下午/晚上/深夜)
- 完播率 (position/duration > 0.9 的比例)
- 平均观影时长

**AI 分析维度**:
- 最爱题材 Top 3 (悬疑、爱情、动作...)
- 最爱演员 Top 5
- 最爱导演 Top 3
- 最爱国家/地区
- 观影风格标签 (文艺青年、动作爱好者、剧集狂魔...)
- 个性化评语 (AI 生成一句话总结)

**高级洞察** (AI 生成):
- 观影情绪曲线 (轻松 → 紧张 → 治愈)
- 冷门片发掘者徽章 (观看了 X 部小众作品)
- 追剧达人徽章 (完播 X 部长剧)
- 跨文化探索者 (观看了 X 个国家的作品)

#### 2.3.2 报告结构

**报告分为 5 个卡片区块**:

1. **概览卡片** (Overview)
   - 时间范围标题
   - 观影总时长 (大字号高亮)
   - 观看作品数 / 观看集数
   - 超越 X% 用户提示 (本地模拟)

2. **时间分布卡片** (Time Pattern)
   - 最常观影时段
   - 周末 vs 工作日占比
   - 平均单次观看时长
   - 深夜观影次数

3. **内容偏好卡片** (Content Preference)
   - 最爱题材 Top 3 (饼图或横向条形图)
   - 电影 vs 剧集占比
   - 完播率数据
   - 国家/地区分布

4. **人物偏好卡片** (People)
   - 最爱演员 Top 5 (带头像)
   - 最爱导演 Top 3
   - 关注新人演员数量

5. **个性化总结卡片** (AI Summary)
   - AI 生成的个性化评语
   - 观影风格标签 (3-5 个 tag)
   - 成就徽章展示
   - 分享按钮

#### 2.3.3 报告示例

```
┌─────────────────────────────────────┐
│   🎬 你的 2026 年观影报告            │
│                                     │
│      你累计观影 186.5 小时           │
│     相当于 7.7 天不眠不休             │
│                                     │
│   📺 观看了 68 部作品,共 412 集      │
│   🏆 超越了 78% 的用户               │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│   ⏰ 观影时间分布                    │
│                                     │
│   最爱观影时段: 晚上 21:00-23:00     │
│   周末观影占比: 45%                  │
│   平均单次观看: 52 分钟              │
│   深夜观影次数: 23 次                │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│   🎭 你最爱的题材                    │
│                                     │
│   🔍 悬疑推理    ████████ 32%       │
│   💕 爱情        █████ 24%          │
│   🎬 动作冒险    ████ 18%           │
│                                     │
│   剧集 vs 电影: 72% vs 28%          │
│   完播率: 68%                        │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│   👤 你最爱的演员                    │
│                                     │
│   1️⃣ 张若昀 (庆余年系列)            │
│   2️⃣ 王鹤棣 (苍兰诀)                │
│   3️⃣ 赵丽颖 (风吹半夏、与凤行)       │
│   4️⃣ 肖战 (玉骨遥、梦中的那片海)     │
│   5️⃣ 杨紫 (长相思系列)              │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│   ✨ AI 为你生成的观影画像           │
│                                     │
│   "你是典型的悬疑爱好者,偏爱节奏     │
│    紧凑的剧情片,对国产古装剧情有     │
│    独特品味。深夜观影习惯说明你享    │
│    受独处时光的沉浸感。"            │
│                                     │
│   🏷️ #悬疑爱好者 #深夜观影党         │
│   🏷️ #古装剧迷 #追剧达人            │
│                                     │
│   🏆 获得徽章:                       │
│   📺 剧集狂魔 (完播10部长剧)         │
│   🌙 深夜观影者 (深夜观影23次)       │
│   🔍 冷门发掘者 (观看8部小众作品)    │
│                                     │
│        [分享报告] [关闭]             │
└─────────────────────────────────────┘
```

---

## 3. 技术实现

### 3.1 模块设计

#### 3.1.1 核心类结构

```
app/src/main/java/com/fongmi/android/tv/viewing/
├── ViewingReport.java              # 报告数据模型
├── ViewingReportRequest.java       # 报告请求参数
├── ViewingReportGenerator.java     # 报告生成器(聚合本地统计)
├── ViewingReportAiAnalyzer.java    # AI 分析服务
├── ViewingReportCache.java         # 报告缓存
└── ViewingReportActivity.java      # 报告展示页

app/src/main/java/com/fongmi/android/tv/ui/dialog/
└── ViewingReportRangeDialog.java   # 时间范围选择对话框

app/src/main/res/layout/
├── activity_viewing_report.xml     # 报告展示页布局
├── dialog_viewing_report_range.xml # 时间选择对话框
├── card_report_overview.xml        # 概览卡片
├── card_report_time.xml            # 时间分布卡片
├── card_report_preference.xml      # 内容偏好卡片
├── card_report_people.xml          # 人物偏好卡片
└── card_report_summary.xml         # AI 总结卡片
```

#### 3.1.2 数据模型

```java
public class ViewingReport {
    
    // 时间范围
    private ViewingReportRange range;
    private long startTimestamp;
    private long endTimestamp;
    
    // 基础统计
    private long totalWatchMinutes;      // 总观影时长(分钟)
    private int totalVodCount;           // 观看作品数
    private int totalEpisodeCount;       // 观看集数
    private double averageWatchMinutes;  // 平均观影时长
    private double completionRate;       // 完播率
    
    // 时间分布
    private Map<TimeSlot, Integer> timeSlotDistribution;  // 时段分布
    private double weekendRatio;         // 周末占比
    private int lateNightCount;          // 深夜观影次数
    
    // 内容偏好
    private List<GenreStat> topGenres;   // 题材 Top 3
    private double tvRatio;              // 剧集占比
    private double movieRatio;           // 电影占比
    private List<CountryStat> topCountries;  // 国家/地区分布
    
    // 人物偏好
    private List<ActorStat> topActors;   // 演员 Top 5
    private List<DirectorStat> topDirectors;  // 导演 Top 3
    
    // AI 分析结果
    private String aiSummary;            // AI 个性化评语
    private List<String> styleTags;      // 观影风格标签
    private List<Badge> badges;          // 成就徽章
    
    // 元数据
    private long generatedAt;
    private String cacheKey;
    private boolean aiAnalyzed;          // 是否包含 AI 分析
}

public enum ViewingReportRange {
    ALL("全部"),
    THIS_YEAR("本年"),
    FIRST_HALF("上半年"),
    SECOND_HALF("下半年"),
    THIS_QUARTER("本季度"),
    THIS_MONTH("本月"),
    THIS_WEEK("本周"),
    LAST_30_DAYS("最近30天"),
    LAST_7_DAYS("最近7天");
    
    private final String label;
}

public enum TimeSlot {
    DAWN(0, 6, "凌晨"),       // 00:00-06:00
    MORNING(6, 12, "上午"),   // 06:00-12:00
    AFTERNOON(12, 18, "下午"), // 12:00-18:00
    EVENING(18, 23, "晚上"),  // 18:00-23:00
    LATE_NIGHT(23, 24, "深夜"); // 23:00-24:00
}

public class Badge {
    private String id;          // drama_king, night_owl, indie_explorer
    private String name;        // 剧集狂魔、深夜观影者、冷门发掘者
    private String description; // 完播 10 部长剧
    private String icon;        // 📺 🌙 🔍
}
```

#### 3.1.3 数据流

```
用户选择时间范围
  ↓
ViewingReportGenerator.generate(range)
  ↓
从 History 读取指定范围记录 (History.get() 后按 createTime 过滤)
  ↓
本地统计层(必选,离线可用):
  - 计算总时长/作品数/完播率
  - 统计观影时段分布
  - 分析题材/演员偏好(依赖扩展后的 History 字段)
  ↓
AI 文案层(可选,可关闭):
  - 构建统计摘要 JSON
  - 调用 AiCompletionClient 生成个性化文案
  - 超时/失败/未配置 → 降级为本地默认文案
  ↓
写入缓存(24 小时有效,history 指纹变化即失效)
  ↓
ViewingReportActivity 展示
```

核心原则:**本地统计永远先跑,AI 只补一层文案**。未配置 AI Key 时报告依然完整,只是少了个性化评语和标签。


---

### 3.2 数据库扩展(完整版方案)

#### 3.2.1 History 表字段扩展

**现状分析**:
- `History` 表只有 `vodName/vodPic/vodFlag/vodRemarks/position/duration/createTime`
- 缺失题材/演员/导演/地区/年份信息,无法统计偏好

**扩展方案**:
```java
@Entity
public class History {
    // ... 现有字段 ...
    
    // 新增字段(观影报告专用)
    @SerializedName("typeName")
    private String typeName;      // 类型/题材(电影、电视剧、动漫、悬疑...)
    
    @SerializedName("area")
    private String area;          // 地区(中国大陆、美国、日本...)
    
    @SerializedName("actor")
    private String actor;         // 演员列表(逗号分隔)
    
    @SerializedName("director")
    private String director;      // 导演列表(逗号分隔)
    
    @SerializedName("year")
    private String year;          // 年份
    
    // Getter/Setter 省略...
}
```

**数据来源**:
- 播放时从 `Vod` 对象拷贝:`item.getTypeName()` / `item.getActor()` / `item.getDirector()`
- TMDB 增强开启时,这些字段已自动从 TMDB 回填到 `Vod`

**复用范围（2026-07-18 更新）**:
- 上述字段不再只服务观影报告，也会进入 `AiRecommendationService` 的 `playHistory` 结构化上下文。
- AI 推荐按最近去重历史综合题材、地区、演员、导演、年份、完成率和新近度判断长期偏好。
- 富集元数据会进入 AI 推荐缓存指纹；仅播放进度变化不会导致缓存频繁失效。
- 详细方案见 `docs/ai-recommendation-history-metadata-design.md`。

#### 3.2.2 数据库迁移

**版本升级**: `36 → 37`

**迁移脚本** (`app/src/main/java/com/fongmi/android/tv/db/Migrations.java`):
```java
public static final Migration MIGRATION_36_37 = new Migration(36, 37) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase database) {
        database.execSQL("ALTER TABLE History ADD COLUMN typeName TEXT DEFAULT NULL");
        database.execSQL("ALTER TABLE History ADD COLUMN area TEXT DEFAULT NULL");
        database.execSQL("ALTER TABLE History ADD COLUMN actor TEXT DEFAULT NULL");
        database.execSQL("ALTER TABLE History ADD COLUMN director TEXT DEFAULT NULL");
        database.execSQL("ALTER TABLE History ADD COLUMN year TEXT DEFAULT NULL");
    }
};
```

**注册迁移** (`app/src/main/java/com/fongmi/android/tv/db/AppDatabase.java`):
```java
@Database(entities = {...}, version = 37)  // 版本号改为 37
public abstract class AppDatabase extends RoomDatabase {
    public static final int VERSION = 37;  // 常量改为 37
    
    private static synchronized AppDatabase create(Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, "tv")
                .allowMainThreadQueries()
                .addMigrations(Migrations.MIGRATION_30_31)
                .addMigrations(Migrations.MIGRATION_31_32)
                .addMigrations(Migrations.MIGRATION_32_33)
                .addMigrations(Migrations.MIGRATION_33_34)
                .addMigrations(Migrations.MIGRATION_34_35)
                .addMigrations(Migrations.MIGRATION_35_36)
                .addMigrations(Migrations.MIGRATION_36_37)  // ← 新增此行
                .fallbackToDestructiveMigration(true)
                .build();
    }
}
```

**兼容性说明**:
- 新字段默认 `NULL`,不影响老版本数据查询
- 老历史记录在下次播放同资源时会自动补全
- `fallbackToDestructiveMigration(true)` 兜底,极端情况重建数据库

#### 3.2.3 数据写入时机

**VideoActivity 修改点** (mobile + leanback 两个版本):

**位置**: `createHistory(Vod item)` 方法

```java
// app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java (约 2752 行)
// app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java (约 2820 行)

private History createHistory(Vod item) {
    History history = new History();
    history.setKey(History.key(mSite, mVod));
    history.setVodPic(item.getVodPic());
    history.setVodName(item.getName());
    history.setCid(VodConfig.getCid());
    
    // ↓ 新增:为观影报告补充元数据
    history.setTypeName(item.getTypeName());
    history.setArea(item.getArea());
    history.setActor(item.getActor());
    history.setDirector(item.getDirector());
    history.setYear(item.getYear());
    
    return history;
}
```

**效果**:
- 首次播放某资源 → 写入完整元数据
- 切换集数时不重新创建 History,不影响已有字段
- TMDB 增强关闭时,`item.getActor()` 可能为空,但不会报错

---

### 3.3 本地统计实现

#### 3.3.1 ViewingReportGenerator 核心逻辑

```java
public class ViewingReportGenerator {

    // 深夜时段:23:00-06:00
    private static final int LATE_NIGHT_START = 23;
    private static final int LATE_NIGHT_END = 6;
    // 完播阈值:观看进度 >= 90%
    private static final double COMPLETION_THRESHOLD = 0.9;
    // 长剧阈值:观看时长 >= 30 分钟视为有效单次
    private static final long MIN_VALID_MINUTES = 5;

    public ViewingReport generate(ViewingReportRange range) {
        long start = range.startTimestamp();
        long end = range.endTimestamp();
        List<History> records = filterByRange(History.get(), start, end);

        ViewingReport report = new ViewingReport();
        report.setRange(range);
        report.setStartTimestamp(start);
        report.setEndTimestamp(end);

        computeBasicStats(report, records);
        computeTimePattern(report, records);
        computeContentPreference(report, records);
        computePeoplePreference(report, records);
        computeBadges(report, records);

        return report;
    }

    // 按 createTime 过滤时间范围
    private List<History> filterByRange(List<History> all, long start, long end) {
        List<History> result = new ArrayList<>();
        for (History h : all) {
            long t = h.getCreateTime();
            if (t <= 0) continue;
            if (start > 0 && t < start) continue;
            if (end > 0 && t > end) continue;
            result.add(h);
        }
        return result;
    }
}
```

#### 3.3.2 基础统计

```java
private void computeBasicStats(ViewingReport report, List<History> records) {
    long totalMs = 0;
    int completed = 0;
    int validCount = 0;
    Set<String> uniqueVods = new HashSet<>();

    for (History h : records) {
        long pos = h.getPosition();
        long dur = h.getDuration();
        if (pos > 0) totalMs += pos;
        if (dur > 0 && pos > 0) {
            validCount++;
            if ((double) pos / dur >= COMPLETION_THRESHOLD) completed++;
        }
        if (h.getVodName() != null) uniqueVods.add(normalize(h.getVodName()));
    }

    report.setTotalWatchMinutes(TimeUnit.MILLISECONDS.toMinutes(totalMs));
    report.setTotalVodCount(uniqueVods.size());
    report.setTotalEpisodeCount(records.size());
    report.setCompletionRate(validCount == 0 ? 0 : (double) completed / validCount);
    report.setAverageWatchMinutes(records.isEmpty() ? 0
            : TimeUnit.MILLISECONDS.toMinutes(totalMs) / (double) records.size());
}
```

#### 3.3.3 时间分布统计

```java
private void computeTimePattern(ViewingReport report, List<History> records) {
    Map<TimeSlot, Integer> distribution = new EnumMap<>(TimeSlot.class);
    int weekendCount = 0;
    int lateNightCount = 0;
    Calendar cal = Calendar.getInstance();

    for (History h : records) {
        if (h.getCreateTime() <= 0) continue;
        cal.setTimeInMillis(h.getCreateTime());
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

        TimeSlot slot = TimeSlot.of(hour);
        distribution.merge(slot, 1, Integer::sum);

        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) weekendCount++;
        if (hour >= LATE_NIGHT_START || hour < LATE_NIGHT_END) lateNightCount++;
    }

    report.setTimeSlotDistribution(distribution);
    report.setWeekendRatio(records.isEmpty() ? 0 : (double) weekendCount / records.size());
    report.setLateNightCount(lateNightCount);
}
```

#### 3.3.4 题材/演员偏好统计

```java
private void computeContentPreference(ViewingReport report, List<History> records) {
    Map<String, Integer> genreCount = new HashMap<>();
    Map<String, Integer> countryCount = new HashMap<>();
    int tvCount = 0, movieCount = 0;
    Set<String> countedVods = new HashSet<>();

    for (History h : records) {
        // 同一作品只统计一次偏好,避免长剧刷高权重
        String vodKey = normalize(h.getVodName());
        if (!countedVods.add(vodKey)) continue;

        // 题材(拆分多标签)
        splitAndCount(genreCount, h.getTypeName());
        // 地区
        splitAndCount(countryCount, h.getArea());
        // 电影/剧集判定
        if (isTvType(h.getTypeName())) tvCount++;
        else movieCount++;
    }

    report.setTopGenres(topN(genreCount, 3, GenreStat::new));
    report.setTopCountries(topN(countryCount, 3, CountryStat::new));
    int total = tvCount + movieCount;
    report.setTvRatio(total == 0 ? 0 : (double) tvCount / total);
    report.setMovieRatio(total == 0 ? 0 : (double) movieCount / total);
}

private void computePeoplePreference(ViewingReport report, List<History> records) {
    Map<String, Integer> actorCount = new HashMap<>();
    Map<String, Integer> directorCount = new HashMap<>();
    Set<String> countedVods = new HashSet<>();

    for (History h : records) {
        if (!countedVods.add(normalize(h.getVodName()))) continue;
        splitAndCount(actorCount, h.getActor());
        splitAndCount(directorCount, h.getDirector());
    }

    report.setTopActors(topN(actorCount, 5, ActorStat::new));
    report.setTopDirectors(topN(directorCount, 3, DirectorStat::new));
}

// 按分隔符拆分并计数(演员/题材常以逗号、斜杠、空格分隔)
private void splitAndCount(Map<String, Integer> counter, String value) {
    if (value == null || value.trim().isEmpty()) return;
    for (String part : value.split("[,，/、|｜;；\s]+")) {
        String item = part.trim();
        if (!item.isEmpty()) counter.merge(item, 1, Integer::sum);
    }
}
```

#### 3.3.5 成就徽章规则

| 徽章 ID | 名称 | 图标 | 触发条件 |
|--------|------|-----|---------|
| `drama_king` | 剧集狂魔 | 📺 | 完播剧集 >= 10 部 |
| `night_owl` | 深夜观影者 | 🌙 | 深夜观影次数 >= 20 次 |
| `indie_explorer` | 冷门发掘者 | 🔍 | 观看小众作品 >= 5 部(无 TMDB 匹配或评分人数少) |
| `marathon` | 马拉松选手 | 🏃 | 单日观影 >= 5 小时 |
| `loyal_fan` | 忠实粉丝 | 💖 | 同一演员作品 >= 3 部 |
| `globe_trotter` | 环球观影者 | 🌍 | 观看 >= 5 个不同国家/地区作品 |
| `early_bird` | 早起观影者 | 🌅 | 上午观影次数 >= 15 次 |

---

### 3.4 AI 文案层实现

#### 3.4.1 定位说明

本项目已明确采用 **本地统计为主 + AI 润色文案** 的方案:

- 所有数字、Top N、徽章均由本地 `ViewingReportGenerator` 计算,准确可信,不依赖网络。
- AI 只负责把统计结果**转化为有温度的个性化文案**:一句话画像 + 3-5 个风格标签。
- AI 不参与数字计算,避免幻觉污染统计数据。
- 未配置 AI Key / AI 关闭 / 请求超时 → 自动降级为本地模板文案,报告依然完整。

#### 3.4.2 ViewingReportAiAnalyzer

```java
public class ViewingReportAiAnalyzer {

    private static final int CALL_TIMEOUT_SECONDS = 30;
    private final AiConfig config;

    public ViewingReportAiAnalyzer() {
        this.config = AiConfig.objectFrom(Setting.getAiConfig());
    }

    // 只有 AI 就绪且开关开启才调用,否则返回本地降级文案
    public AiPortrait analyze(ViewingReport report) {
        if (!config.isReady() || !Setting.isAiViewingReport()) {
            return AiPortrait.fallback(report);
        }
        try {
            String prompt = buildPrompt(report);
            AiCompletionClient.RequestSpec spec = AiCompletionClient.requestSpec(config, prompt);
            Request request = AiCompletionClient.buildRequest(spec);
            try (Response response = client().newCall(request).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) return AiPortrait.fallback(report);
                AiPortrait portrait = parsePortrait(
                        AiCompletionClient.extractCompletionText(body, config));
                return portrait == null ? AiPortrait.fallback(report) : portrait;
            }
        } catch (Throwable e) {
            SpiderDebug.log("ai-report", "analyze failed: %s", e.getMessage());
            return AiPortrait.fallback(report);
        }
    }
}
```

#### 3.4.3 Prompt 设计

只发送**已聚合的统计摘要**,不发送原始播放记录、URL、Cookie 等敏感信息。

```
你是影视观影行为分析师。根据用户的观影统计数据,生成一段个性化观影画像。

要求:
- 只返回严格 JSON,不要 Markdown,不要解释。
- summary 为一段 40-80 字的个性化评语,语气温暖有趣,结合具体数据。
- tags 为 3-5 个风格标签,每个 2-6 字,前面不带 # 号。
- 不要编造数据里没有的内容,只基于提供的统计。

返回格式:
{"summary":"个性化评语","tags":["标签1","标签2","标签3"]}

用户统计数据 JSON:
{
  "totalHours": 186.5,
  "vodCount": 68,
  "episodeCount": 412,
  "topTimeSlot": "晚上",
  "lateNightCount": 23,
  "weekendRatio": 0.45,
  "completionRate": 0.68,
  "topGenres": ["悬疑","爱情","动作"],
  "tvRatio": 0.72,
  "topActors": ["张若昀","赵丽颖"],
  "topCountries": ["中国大陆","美国"]
}
```

#### 3.4.4 输出校验

- `summary` 为空或超长(> 200 字)→ 丢弃,用降级文案。
- `tags` 去重、去空、去 `#`,超过 5 个只取前 5 个。
- 解析失败 → 降级。

#### 3.4.5 本地降级文案

无 AI 时,根据本地统计的关键特征拼接模板:

```java
static AiPortrait fallback(ViewingReport report) {
    List<String> tags = new ArrayList<>();
    StringBuilder summary = new StringBuilder();

    // 时段标签
    if (report.getLateNightCount() >= 20) tags.add("深夜观影党");
    if (report.getWeekendRatio() > 0.6) tags.add("周末充电型");

    // 题材标签(取 Top 1)
    if (!report.getTopGenres().isEmpty()) {
        tags.add(report.getTopGenres().get(0).getName() + "爱好者");
    }

    // 剧集/电影倾向
    if (report.getTvRatio() > 0.7) tags.add("追剧达人");
    else if (report.getMovieRatio() > 0.7) tags.add("电影控");

    summary.append("这段时间你累计观影 ")
           .append(report.getTotalHours())
           .append(" 小时,看了 ")
           .append(report.getTotalVodCount())
           .append(" 部作品。");
    if (!report.getTopGenres().isEmpty()) {
        summary.append("你偏爱").append(report.getTopGenres().get(0).getName()).append("题材,");
    }
    summary.append("继续享受你的观影时光吧。");

    return new AiPortrait(summary.toString(), tags);
}
```

---

### 3.5 UI 实现

#### 3.5.1 手机端入口(改动最小)

**menu_history.xml** 在 sync/delete 之间插入报告项:
```xml
<item
    android:id="@+id/report"
    android:icon="@drawable/ic_action_report"
    android:title="@string/menu_viewing_report"
    app:showAsAction="always" />
```

**HistoryActivity.java**(mobile)`onOptionsItemSelected` 增加分支:
```java
@Override
public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == android.R.id.home) onBackInvoked();
    else if (item.getItemId() == R.id.delete) onDelete();
    else if (item.getItemId() == R.id.sync) onSync();
    else if (item.getItemId() == R.id.report) onReport();  // ← 新增
    return super.onOptionsItemSelected(item);
}

private void onReport() {
    ViewingReportRangeDialog.show(this, range ->
        ViewingReportActivity.start(this, range));
}
```

#### 3.5.2 TV 端入口(顶部焦点栏方案)

> 决策:采用顶部焦点栏方案。当前 leanback `activity_history.xml` 只有一个铺满屏幕的 RecyclerView,无任何工具栏。

**activity_history.xml**(leanback)改造 —— 外层套一个纵向 LinearLayout,顶部加焦点栏:
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.appcompat.widget.LinearLayoutCompat
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <!-- 新增:顶部焦点栏 -->
    <androidx.appcompat.widget.LinearLayoutCompat
        android:id="@+id/topBar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:paddingHorizontal="24dp"
        android:paddingTop="16dp"
        android:gravity="end">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/report"
            style="@style/Widget.Material3.Button.TonalButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/menu_viewing_report"
            app:icon="@drawable/ic_action_report"
            android:nextFocusDown="@id/recycler" />

    </androidx.appcompat.widget.LinearLayoutCompat>

    <com.fongmi.android.tv.ui.custom.ProgressLayout
        android:id="@+id/progressLayout"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/recycler"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clipChildren="false"
            android:clipToPadding="false"
            android:overScrollMode="never"
            android:nextFocusUp="@id/report"
            android:padding="24dp" />

    </com.fongmi.android.tv.ui.custom.ProgressLayout>
</androidx.appcompat.widget.LinearLayoutCompat>
```

**HistoryActivity.java**(leanback)`initView` 绑定按钮:
```java
@Override
protected void initView(Bundle savedInstanceState) {
    setRecyclerView();
    setReportButton();  // ← 新增
    getHistory();
}

private void setReportButton() {
    mBinding.report.setOnClickListener(v ->
        ViewingReportRangeDialog.show(this, range ->
            ViewingReportActivity.start(this, range)));
}
```

**焦点链路要点**:
- 默认焦点仍在 RecyclerView 第一项(进入即可上下浏览历史)。
- 首行卡片按「上」→ 焦点跳到报告按钮(`nextFocusUp="@id/report"`)。
- 报告按钮按「下」→ 回到列表(`nextFocusDown="@id/recycler"`)。
- 需真机遥控器验证:长列表滚动到顶部时上键不误触发返回。

#### 3.5.3 时间范围选择对话框

单选列表,手机与 TV 复用同一个 Dialog 类,`setSingleChoiceItems` 遥控器友好:
```java
public static void show(FragmentActivity activity, OnRangeSelected listener) {
    ViewingReportRange[] ranges = ViewingReportRange.values();
    String[] labels = new String[ranges.length];
    for (int i = 0; i < ranges.length; i++) labels[i] = ranges[i].displayLabel();

    new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
        .setTitle(R.string.dialog_viewing_report_range)
        .setItems(labels, (d, which) -> listener.onSelected(ranges[which]))
        .setNegativeButton(R.string.dialog_negative, null)
        .show();
}
```

#### 3.5.4 报告展示页

- `ViewingReportActivity`:mobile / leanback 各一份,共用 `main` 层的 Generator/Analyzer。
- 布局:`ScrollView`(TV 用 `NestedScrollView` + 焦点滚动) 纵向排列 5 张 MaterialCardView。
- 生成流程:进入页先显示 loading → 后台线程 `generate()` + `analyze()` → 主线程填充卡片。
- 分享:`View` 转 Bitmap 保存到相册 / 调系统分享(二期)。

---

### 3.6 缓存策略

```
Path.cache()/viewing_report/{range}_{historyFingerprint}.json
```

- **historyFingerprint** = md5(记录数 + 最新 createTime),历史变化即失效。
- **有效期** 24 小时,过期删除重算。
- 同一范围重复打开秒开;新增播放记录后自动失效重算。
- AI 文案随报告一起缓存,不重复请求。

---

### 3.7 设置项

复用现有 AI 配置对话框(`AiConfigDialog`),新增一个独立开关:

| 设置项 | 默认 | 说明 |
|-------|------|------|
| `AI 观影画像` | 关闭 | 是否用 AI 生成个性化文案;关闭时用本地模板 |

- 底层复用 `AiConfig` 的端点/协议/模型/Key,用户无需重复配置。
- `Setting.isAiViewingReport()` / `Setting.putAiViewingReport(boolean)` 新增读写方法。
- 首次开启弹隐私说明:"仅发送片名与统计摘要,不含播放地址、账号信息"。

---

## 4. 隐私与安全

发送给 AI 的字段**仅限聚合统计**:
- 总时长、作品数、集数、完播率
- 时段分布、周末占比、深夜次数
- Top 题材/演员/地区名称(纯文本)

**不发送**:
- API Key、播放 URL、网盘链接
- Cookie、Header、站点配置
- 完整播放历史明细、观看进度时间点
- 用户设备标识

日志只记录耗时、来源、是否降级,不打印完整 AI 响应。

---

## 5. 失败策略

| 场景 | 行为 |
|-----|------|
| 无历史记录 | 显示空状态引导页,提示"先看几部再来" |
| AI 未配置 / 关闭 | 纯本地报告 + 模板文案 |
| AI 超时(30s) | 本地报告 + 模板文案,不阻塞 |
| AI 返回非法 JSON | 丢弃 AI 结果,降级 |
| 扩展字段大量为 NULL(老数据) | 题材/演员卡片显示"暂无足够数据",其余正常 |
| 历史记录 > 1000 条 | 只取最近 1000 条统计,底部标注 |

---

## 6. 测试计划

### 6.1 单元测试

```
总时长: [50min, 80min] -> 130 分钟
完播率: 进度[97%, 50%, 91%] -> 2/3 = 0.67
时段归类: createTime=23:30 -> LATE_NIGHT
时段归类: createTime=08:00 -> MORNING
题材去重: 同一剧集 20 集只计 1 次题材权重
范围过滤: THIS_WEEK 只保留本周一之后的记录
徽章: 完播 10 部长剧 -> drama_king 触发
空数据: 空 History -> ViewingReport.empty,不崩溃
```

### 6.2 集成测试

- 100 条历史 → 生成耗时 < 2s。
- AI mock 返回合法 JSON → summary/tags 正确填充。
- AI mock 超时 → 降级模板文案。
- 缓存命中 → 不发起 AI 请求。
- 数据库 36→37 迁移 → 老数据可读,新字段为 NULL。

### 6.3 人工验收

- 手机端:工具栏报告按钮可点,范围切换生效,卡片滚动流畅。
- TV 端:遥控器上键从首行卡片聚焦到报告按钮,下键回列表,无误返回。
- 老用户升级:历史不丢,题材卡片渐进补全。
- 断网:纯本地报告正常显示。

---

## 7. 实施步骤

1. `History` 增字段 + `MIGRATION_36_37` + 版本号 37。
2. mobile/leanback `VideoActivity.createHistory()` 补写元数据。
3. 新增 `ViewingReportRange` / `TimeSlot` / `ViewingReport` 等数据模型。
4. 实现 `ViewingReportGenerator`(本地统计)+ 单元测试。
5. 实现 `ViewingReportCache`。
6. 实现 `ViewingReportAiAnalyzer` + 本地降级文案。
7. 新增 `ViewingReportRangeDialog`。
8. 新增 `ViewingReportActivity` + 5 张卡片布局(mobile/leanback)。
9. 手机端 menu 入口 + leanback 顶部焦点栏入口。
10. 设置项 `AI 观影画像` 开关 + 隐私提示。
11. 字符串资源(含 zh-rCN / zh-rTW / values)。
12. 灰度发布,先默认关闭 AI 文案,验证后再默认开启。

---

## 8. 里程碑

**Phase 1 - 本地报告 MVP**:
- History 字段扩展 + 迁移
- 本地统计 + 手机端入口 + 基础卡片
- 纯本地模板文案

**Phase 2 - AI 文案 + TV 端**:
- AI 个性化画像 + 标签
- TV 端顶部焦点栏入口
- 成就徽章系统

**Phase 3 - 增强**:
- 分享成图 / 导出
- 自定义时间范围
- 观影日历热力图

---

## 9. 附:业界方案参考

| 产品 | 时间范围 | 核心维度 | 可借鉴点 |
|-----|---------|---------|---------|
| Spotify Wrapped | 年度 | 时长/歌手/曲风/分钟数 | 强动画、社交分享卡片 |
| 网易云年度报告 | 年度 | 时长/最爱歌手/关键词/夜间 | "凌晨听歌"情感化文案 |
| 豆瓣观影报告 | 年度 | 观影量/评分分布/类型 | 数据可视化图表 |
| Letterboxd Year in Review | 年度 | 观影量/导演/时长/国家 | 多维度榜单 |
| YouTube Recap | 月/年 | 观看时长/频道/主题 | 灵活时间范围 |

**本方案特点**:
- 灵活时间范围(全部/年/季/月/周/滚动),不局限年度。
- 本地优先,离线可用,AI 仅润色。
- 复用现有 AI 配置,零额外配置成本。

---

**文档版本**: v1.1
**创建日期**: 2026-07-07
**最近更新**: 2026-07-18
