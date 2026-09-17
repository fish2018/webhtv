# 修复 00.mp4 等无效文件名被错误匹配到 TMDB 集数的问题

## 问题描述

用户报告了两个相关问题：

1. **播放页选集卡片 TMDB 匹配错误**：详情页显示正确的 TMDB 海报，但播放页的选集卡片显示错误的海报（按位置顺序错配）
2. **无效文件名被强制匹配**：`00.mp4` 这种无集号的文件被错误匹配到第 18 集

根本原因：
- 问题 1：播放页适配器（EpisodeAdapter, EpisodeHoriHolder, EpisodeGridHolder）直接使用 `episode.getTmdbEpisode()`，没有验证 TMDB 数据的集号是否与 Episode 实际集号匹配
- 问题 2：`Util.getNumber()` 的集数提取逻辑不够严格，`00.mp4` 返回 `0` 而不是 `-1`（无效）

## 解决方案

### 1. 增强 `Util.getNumber()` 集数提取逻辑

**文件**: `app/src/main/java/com/fongmi/android/tv/utils/Util.java`

参考 CatVodSpider 的 `DanmakuScanner.extractEpisodeNum()` 实现，升级集数提取能力：

#### 新增功能
- ✅ **中文数字支持**：第一集、第二十三集 → 1, 23
- ✅ **更多格式**：S01E03, EP03, E03, 第X集
- ✅ **严格过滤**：排除文件大小（210.03G）、年份（2026）、版本号（v2）、画质（1080p）
- ✅ **扩展名移除**：避免从 mp4, mkv 等提取数字
- ✅ **无效集号拒绝**：`00.mp4`、`000`、`trailer` 等返回 `-1`

#### 提取优先级
1. 独立纯数字（如 "17"）
2. S01E03 格式（最高优先级）
3. 中文格式（第XX集/话/章，支持中文数字）
4. EP03, E03 格式
5. 其他数字格式（经过严格过滤）

#### 过滤规则
```java
// 无效值
00, 000, 0 → -1

// 年份
1900-2099 → -1

// 有效范围
1-999 → 接受
1000+ → -1
```

#### 中文数字解析
```java
第一集 → 1
第十五集 → 15
第二十三集 → 23
第九十九集 → 99
第一百零五集 → 105
第一百二十三集 → 123
```

### 2. 修复播放页 TMDB 匹配逻辑

在播放页的三个适配器中添加 TMDB 数据验证，使用与详情页相同的匹配逻辑：

#### Leanback 版本
**文件**: `app/src/leanback/java/com/fongmi/android/tv/ui/adapter/EpisodeAdapter.java`

```java
private void bindCardView(@NonNull ViewHolder holder, Episode item, int position) {
    // 使用集号匹配 TMDB 数据，而不是直接使用 item.getTmdbEpisode()
    int episodeNumber = item.getNumber() > 0 ? item.getNumber() : position + 1;
    TmdbEpisode tmdbEpisode = item.getTmdbEpisode();
    if (!TmdbEpisodeMatcher.shouldApply(item, tmdbEpisode, episodeNumber)) {
        tmdbEpisode = null;
    }
    // ... 继续绑定卡片
}
```

#### Mobile 版本 - 横向滚动卡片
**文件**: `app/src/mobile/java/com/fongmi/android/tv/ui/holder/EpisodeHoriHolder.java`

```java
@Override
public void initView(Episode item) {
    int position = getBindingAdapterPosition();
    int episodeNumber = item.getNumber() > 0 ? item.getNumber() : position + 1;
    TmdbEpisode tmdbEpisode = item.getTmdbEpisode();
    if (!TmdbEpisodeMatcher.shouldApply(item, tmdbEpisode, episodeNumber)) {
        tmdbEpisode = null;
    }
    // ... 继续处理
}
```

#### Mobile 版本 - 网格卡片
**文件**: `app/src/mobile/java/com/fongmi/android/tv/ui/holder/EpisodeGridHolder.java`

```java
@Override
public void initView(Episode item) {
    updateLayout();
    int position = getBindingAdapterPosition();
    int episodeNumber = item.getNumber() > 0 ? item.getNumber() : position + 1;
    TmdbEpisode episode = item.getTmdbEpisode();
    if (!TmdbEpisodeMatcher.shouldApply(item, episode, episodeNumber)) {
        episode = null;
    }
    // ... 继续处理
}
```

### 3. 更新测试用例

#### 更新 TmdbEpisodeMatcherTest
**文件**: `app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbEpisodeMatcherTest.java`

将 `allowsWhenSourceEpisodeHasNoNumberAndTmdbEpisodeExists` 改为 `rejectsWhenSourceEpisodeHasNoNumber`：

```java
@Test
public void rejectsWhenSourceEpisodeHasNoNumber() {
    // 无法提取集号的文件名（如 "正片"、"00.mp4"、"trailer"）不应该匹配 TMDB 数据
    // 避免按位置错配（如 00.mp4 被匹配到 E18）
    Episode episode = Episode.create("正片", "http://example.test/1");
    TmdbEpisode tmdbEpisode = new TmdbEpisode(1, "青丘脚下", "", "", "", 0, 0);

    assertEquals(-1, episode.getNumber());
    assertFalse(TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode, 1));
}
```

#### 新增 UtilTest
**文件**: `app/src/test/java/com/fongmi/android/tv/utils/UtilTest.java`

新增 8 个测试用例覆盖所有场景：

1. `getNumber_extractsStandardFormats` - 标准格式（S01E03, EP05, E12）
2. `getNumber_extractsChineseNumbers` - 中文数字（第一集、第二十三集、第一百零五集）
3. `getNumber_filtersOutInvalidNumbers` - 无效集号（00.mp4, 2026, v2）
4. `getNumber_filtersOutFileSize` - 文件大小过滤（[1.87GB], [210.03G]）
5. `getNumber_filtersOutVideoQuality` - 画质信息过滤（1080p, 4K, x265）
6. `getNumber_handlesComplexRealWorldCases` - 真实复杂文件名
7. `getNumber_handlesEdgeCases` - 边界情况（空字符串, null, 超大集数）
8. `getNumber_prioritizesExplicitFormats` - 格式优先级

## 测试结果

所有测试通过：

```
UtilTest: 8 tests, 0 failures ✅
TmdbEpisodeMatcherTest: 10 tests, 0 failures ✅
```

测试用例覆盖：
- ✅ `00.mp4` → -1（不再返回 0）
- ✅ `第17集` → 17
- ✅ `S01E17` → 17
- ✅ `[1.87GB]第17集` → 17（文件大小被正确移除）
- ✅ `2026` → -1（年份被拒绝）
- ✅ `正片` → -1（无集号被拒绝）

## 修改的文件

1. `app/src/main/java/com/fongmi/android/tv/utils/Util.java` - 增强集数提取逻辑
2. `app/src/leanback/java/com/fongmi/android/tv/ui/adapter/EpisodeAdapter.java` - 添加 TMDB 验证
3. `app/src/mobile/java/com/fongmi/android/tv/ui/holder/EpisodeHoriHolder.java` - 添加 TMDB 验证
4. `app/src/mobile/java/com/fongmi/android/tv/ui/holder/EpisodeGridHolder.java` - 添加 TMDB 验证
5. `app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbEpisodeMatcherTest.java` - 更新测试用例
6. `app/src/test/java/com/fongmi/android/tv/utils/UtilTest.java` - 新增测试文件

## 效果

### 修复前
- `00.mp4` 被提取为集号 `0` 或其他错误值，匹配到错误的 TMDB 数据
- 播放页选集卡片按位置顺序显示错误的 TMDB 海报
- 详情页第 17 集，播放页变成第 13 集的海报

### 修复后
- `00.mp4` 返回 `-1`（无效集号），不会匹配任何 TMDB 数据
- 播放页选集卡片正确验证 TMDB 集号，只显示匹配的海报
- 详情页和播放页显示一致的正确 TMDB 信息

## 相关参考

- CatVodSpider 的 `DanmakuScanner.extractEpisodeNum()` 实现
- TmdbDetailActivity 的详情页 TMDB 匹配逻辑
- TmdbEpisodeMatcher 的验证规则
