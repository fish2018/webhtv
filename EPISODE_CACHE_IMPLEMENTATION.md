# 集数播放进度缓存实现说明

## 问题描述

**原始问题：**
- 第5集播放到一半 → 切换到第6集 → 再切回第5集 → 从头开始播放
- 用户期望：切回第5集时，应该从上次播放的位置继续

**根本原因：**
在 `VideoActivity.updateHistory()` 方法中，切换集数时会将播放位置重置为 `C.TIME_UNSET`：
```java
mHistory.setPosition(sameEpisode ? mHistory.getPosition() : C.TIME_UNSET);
```

## 解决方案

采用**基于现有缓存系统的集数位置缓存**方案：

### 架构优势

1. **集成到现有缓存系统**
   - 复用 `Path.cache()` 路径
   - 跟随系统缓存清理一起清除
   - 用户可在设置中查看和手动清理

2. **内存 + 磁盘双层缓存**
   - 内存：当前会话快速访问
   - 磁盘：应用重启后仍然有效

3. **自动管理策略**
   - 每部剧最多缓存 50 集
   - 超过限制时自动移除最旧的记录
   - 30 天未访问自动过期

## 实现细节

### 1. 新增文件：`EpisodePositionCache.java`

**核心功能：**
```java
// 保存集数播放位置
EpisodePositionCache.get().put(siteKey, vodId, flag, episodeName, position, duration);

// 获取集数播放位置
EpisodePosition cached = EpisodePositionCache.get().get(siteKey, vodId, flag, episodeName);

// 持久化到磁盘
EpisodePositionCache.get().save();

// 清空缓存
EpisodePositionCache.get().clear();
```

**数据结构：**
```
{
  "站点|视频ID|线路": {
    "episodes": {
      "第1集": {"position": 12345, "duration": 60000, "timestamp": 1234567890},
      "第2集": {"position": 67890, "duration": 60000, "timestamp": 1234567891}
    },
    "lastAccessTime": 1234567890
  }
}
```

**缓存文件位置：**
`/data/data/com.silent.android.webhtv/cache/episode_positions.json`

### 2. 修改：`VideoActivity.updateHistory()`

**修改前：**
```java
// 切换集数时直接重置位置
mHistory.setPosition(sameEpisode ? mHistory.getPosition() : C.TIME_UNSET);
```

**修改后：**
```java
if (!sameEpisode || !sameFlag) {
    // 保存当前集的位置到缓存
    EpisodePositionCache.get().put(getKey(), getId(), getFlag().getFlag(), 
        mHistory.getEpisode(), player().getPosition(), player().getDuration());
}

if (!sameEpisode) {
    // 从缓存恢复新集的位置
    EpisodePosition cached = EpisodePositionCache.get().get(
        getKey(), getId(), getFlag().getFlag(), item.getName());
    
    if (cached != null) {
        mHistory.setPosition(cached.position);
        mHistory.setDuration(cached.duration);
    } else {
        mHistory.setPosition(C.TIME_UNSET);
    }
}
```

### 3. 修改：`VideoActivity.saveHistory()`

在保存历史记录时，同时持久化集数位置缓存：
```java
Task.execute(() -> {
    history.save();
    EpisodePositionCache.get().save();  // 持久化缓存
    if (exit) RefreshEvent.history();
});
```

### 4. 修改：`FileUtil.java`

**清理缓存时一并清除：**
```java
public static void clearCache(Callback callback) {
    Task.execute(() -> {
        Path.clear(Path.cache());
        EpisodePositionCache.get().clear();  // 清理集数缓存
        App.post(callback::success);
    });
}
```

**缓存大小统计：**
```java
public static void getCacheSize(Callback callback) {
    Task.execute(() -> {
        long totalSize = getDirectorySize(Path.cache());
        totalSize += EpisodePositionCache.get().getCacheSize();  // 包含集数缓存
        String usage = byteCountToDisplaySize(totalSize);
        App.post(() -> callback.success(usage));
    });
}
```

## 使用场景

### 场景 1：正常切换集数
1. 用户在第5集播放到 30 分钟
2. 切换到第6集 → 第5集位置自动保存到缓存
3. 切回第5集 → 从缓存恢复 30 分钟位置继续播放 ✅

### 场景 2：应用重启后
1. 用户观看了第1-10集，每集都有播放进度
2. 关闭应用 → 缓存已持久化到磁盘
3. 重新打开应用，切换集数 → 仍能恢复各集的播放位置 ✅

### 场景 3：缓存管理
1. 用户进入设置 → 存储管理
2. 查看缓存大小 → 包含集数位置缓存
3. 点击"清理缓存" → 一键清除所有缓存（包括集数位置） ✅

### 场景 4：自动清理
1. 某部剧集已经 30 天未观看
2. 下次访问时自动清理过期的集数位置
3. 节省存储空间 ✅

## 兼容性说明

### ✅ 向后兼容
- 不修改 `History` 表结构
- 不修改 `historyKey` 格式
- 现有功能不受影响（收藏、Keep 等）

### ✅ 无数据迁移需求
- 新缓存机制独立运行
- 不依赖旧数据
- 旧用户无需任何操作

### ✅ 渐进式生效
- 只有切换集数时才开始缓存
- 没有缓存时回退到原始行为（从头播放）
- 用户体验平滑过渡

## 性能影响

### 内存占用
- 每集约 40 字节（position + duration + timestamp）
- 假设缓存 10 部剧，每部 50 集：10 × 50 × 40 = 20KB
- **影响：可忽略**

### 磁盘占用
- JSON 格式存储，压缩后约 5-10KB
- **影响：极小**

### 性能开销
- 内存读写：O(1)，微秒级
- 磁盘持久化：异步执行，不阻塞主线程
- **影响：用户无感知**

## 测试建议

### 功能测试
1. ✅ 切换集数后返回，播放位置正确恢复
2. ✅ 不同线路的集数位置独立记录
3. ✅ 应用重启后缓存仍然有效
4. ✅ 清理缓存后集数位置被清除
5. ✅ 超过 50 集后自动移除最旧记录
6. ✅ 30 天未观看的记录自动过期

### 兼容性测试
1. ✅ 旧版本升级后无异常
2. ✅ 收藏功能正常
3. ✅ 历史记录同步正常
4. ✅ 多线路切换正常

### 边界测试
1. ✅ 缓存为空时不崩溃
2. ✅ 磁盘写入失败时不影响播放
3. ✅ JSON 解析失败时降级处理
4. ✅ 并发访问时线程安全

## 代码修改清单

| 文件 | 类型 | 平台 | 说明 |
|------|------|------|------|
| `EpisodePositionCache.java` | 新增 | **共用** | 集数位置缓存核心类 |
| `VideoActivity.java` (mobile) | 修改 | **手机版** | `updateHistory()` 和 `saveHistory()` |
| `VideoActivity.java` (leanback) | 修改 | **TV版** | `updateHistory()` 和 `saveHistory()` |
| `TmdbDetailActivity.java` | 修改 | **共用** | `updateInlineHistory()` 和 `saveInlineHistory()` - 沉浸融合模式内联播放器 |
| `FileUtil.java` | 修改 | **共用** | `clearCache()` 和 `getCacheSize()` |

**总行数：** 约 +350 行（主要是新增的 `EpisodePositionCache.java`）

**平台支持：** ✅ 手机版 + ✅ TV版 + ✅ 沉浸融合模式 全部覆盖

## 未来优化方向

1. **智能预测**
   - 记录用户观看习惯
   - 预加载可能观看的集数

2. **云同步**
   - 支持多设备同步观看进度
   - 需要后端接口支持

3. **统计分析**
   - 查看各集的观看率
   - 推荐相似剧集

## 结论

✅ **推荐采用此方案，理由：**

1. **低风险**：不修改核心数据结构，向后兼容
2. **用户友好**：集成到现有设置，可手动清理
3. **性能优秀**：内存优先，异步持久化
4. **架构清晰**：职责分离，易于维护
5. **自动管理**：过期清理，不会无限增长

相比于方案1（修改 historyKey 结构），此方案避免了数据库膨胀和兼容性风险。
相比于纯内存缓存，此方案增加了持久化能力，用户体验更好。
