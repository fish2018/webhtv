# 修复播放页选集卡片TMDB海报匹配错误

## 问题描述

用户反馈播放页选集卡片显示的TMDB数据与详情页不一致：
- **详情页**：`00.mp4` 显示"暂无 TMDB 数据"（正确）
- **播放页**：`00.mp4` 显示为"18"，并错误匹配了第18集的TMDB数据

根本原因：
1. **集号提取问题**：`Util.getNumber()` 将 `00.mp4` 提取为集号 `0`，但 `0` 是无效集号
2. **匹配逻辑不一致**：`TmdbEpisodeMatcher.shouldApply()` 在详情页拒绝匹配，但播放页 holder 未使用该验证
3. **标题生成逻辑漏洞**：`getCardTitle(item)` 直接从 `item.getTmdbEpisode()` 重新获取数据，绕过了 `initView` 里的匹配验证，导致显示已被拒绝的TMDB数据

## 解决方案

### 1. 增强集号提取逻辑（已在 `fix-00mp4-episode-number-extraction.md` 中完成）

- 将 `Util.getNumber()` 重命名为 `Util.getEpisodeNumber()`
- 对于无效文件名（如 `00.mp4`），返回 `-1` 而非 `0`
- 增强提取支持：S01E03、中文数字、EP/E 格式
- 过滤文件大小、年份、画质、版本号等干扰信息

### 2. 修复匹配验证逻辑

**TmdbEpisodeMatcher.shouldApply()**
```java
// 旧逻辑：集号不匹配时仍返回 true（允许按位置匹配）
if (extractedNumber != tmdbNumber) return true;

// 新逻辑：无有效集号时返回 false（拒绝匹配）
if (extractedNumber <= 0) return false;
if (extractedNumber != tmdbNumber) return true;
```

**TmdbUIAdapter**
- 在设置TMDB数据前增加 `shouldApply()` 验证
- 拒绝为无效集号的剧集关联TMDB数据

### 3. 修复标题显示逻辑（本次新增）

**问题**：`getCardTitle(item)` 重新从 `item.getTmdbEpisode()` 获取数据，绕过了匹配验证

**解决**：
1. 为 `getCardTitle` 添加重载版本，接受已验证的 `TmdbEpisode` 参数：
   ```java
   public static String getCardTitle(Episode item, TmdbEpisode tmdbEpisode)
   ```

2. 更新所有调用点传递验证后的 `tmdbEpisode`：
   - `EpisodeGridHolder.bindCard(item, episode)` → `getCardTitle(item, episode)`
   - `EpisodeHoriHolder.bindCard(item, tmdbEpisode)` → `getCardTitle(item, tmdbEpisode)`
   - `EpisodeAdapter (leanback).bindCard()` → `getCardTitle(item, tmdbEpisode)`

3. 保持向后兼容：无参版本 `getCardTitle(item)` 调用新重载

## 修改文件

**核心逻辑** (已在前次修复中完成)
- `app/src/main/java/com/fongmi/android/tv/utils/Util.java`
- `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbEpisodeMatcher.java`
- `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Episode.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Flag.java`

**标题显示修复** (本次新增)
- `app/src/mobile/java/com/fongmi/android/tv/ui/adapter/EpisodeAdapter.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/holder/EpisodeGridHolder.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/holder/EpisodeHoriHolder.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/adapter/EpisodeAdapter.java`

**测试**
- `app/src/test/java/com/fongmi/android/tv/utils/UtilTest.java` (新增)
- `app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbEpisodeMatcherTest.java`
- `app/src/test/java/com/fongmi/android/tv/ui/adapter/EpisodeAdapterTest.java`

## 测试验证

```bash
./gradlew testLeanbackArm64_v8aDebugUnitTest --tests "UtilTest" --tests "TmdbEpisodeMatcherTest" --tests "EpisodeAdapterTest"
```

全部通过 ✅

## 预期效果

- `00.mp4` 在详情页和播放页**均显示原文件名**，不匹配TMDB
- 正常剧集（如 `19.mp4`）正确匹配对应的TMDB数据
- 验证逻辑与显示逻辑保持一致，杜绝绕过验证的漏洞
