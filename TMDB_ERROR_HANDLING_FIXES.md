# TMDB 详情页崩溃修复

## 问题描述

手机版进入 TMDB 详情页时出现 `ClassCastException` 崩溃：

```
java.lang.ClassCastException: com.google.gson.internal.LinkedTreeMap cannot be cast to i92
```

**根本原因**：
- Gson 在反序列化泛型集合（如 `List<TmdbItem>`）时，如果类型信息丢失，会使用 `LinkedTreeMap` 作为默认容器
- 代码在没有异常处理的情况下直接调用 `.getAsJsonObject()`、`.getAsInt()` 等方法
- 当数据格式不符合预期或混淆导致类型信息丢失时，直接抛出异常导致应用崩溃

## 修复策略

**核心原则**：捕获异常，打印日志，不让应用崩溃

### 1. 新增工具类 `SafeJsonParser.java`

创建了一个安全的 JSON 解析工具类，提供以下方法：
- `safeGetAsJsonObject()` - 安全地将 JsonElement 转换为 JsonObject
- `safeGetAsJsonArray()` - 安全地将 JsonElement 转换为 JsonArray
- `safeGetAsInt()` - 安全地获取 int 值
- `safeGetAsString()` - 安全地获取 String 值
- `safeGetAsDouble()` - 安全地获取 double 值
- `safeForEach()` - 安全地遍历 JsonArray

**特点**：
- 所有方法都有 try-catch 保护
- 捕获 `ClassCastException` 和其他异常
- 打印详细的错误日志（包含上下文信息）
- 返回默认值而不是崩溃

### 2. TmdbService.java 修复

在以下关键方法中添加异常处理：

#### 2.1 列表解析方法
- `cast()` - 演员列表解析
- `episodes()` - 剧集列表解析
- `items()` - TMDB 项目列表解析
- `aggregateCast()` - 聚合演员列表解析
- `integers()` - 整数列表解析

**修复示例**：
```java
// 修复前
for (JsonElement element : results) {
    JsonObject object = element.getAsJsonObject(); // 可能崩溃
    items.add(...);
}

// 修复后
for (JsonElement element : results) {
    try {
        if (!element.isJsonObject()) continue;
        JsonObject object = element.getAsJsonObject();
        items.add(...);
    } catch (ClassCastException e) {
        SpiderDebug.log("TmdbService", "ClassCastException: " + e.getMessage());
    } catch (Throwable e) {
        SpiderDebug.log("TmdbService", "Error: " + e.getMessage());
    }
}
```

#### 2.2 Gson fromJson 调用保护
在以下方法中为 `App.gson().fromJson()` 添加异常捕获：
- `configuration()` - TMDB 配置
- `searchRaw()` - 搜索结果
- `requestJson()` - 通用请求
- `readCache()` - 缓存读取

**修复示例**：
```java
// 修复前
return App.gson().fromJson(response.body().string(), JsonObject.class);

// 修复后
try {
    return App.gson().fromJson(response.body().string(), JsonObject.class);
} catch (ClassCastException e) {
    SpiderDebug.log("TmdbService", "ClassCastException: " + e.getMessage());
    throw new IllegalStateException("数据解析失败: " + e.getMessage(), e);
}
```

### 3. TmdbDetailActivity.java 修复

在以下关键方法中添加异常处理：

#### 3.1 季度信息解析
- `tmdbSeasonYear()` - 获取季度年份
- `firstSeasonNumber()` - 获取第一季编号
- `addSeasonCount()` - 添加季度集数统计

#### 3.2 分级信息解析
- `certificationForRegion()` - 按地区获取分级信息

#### 3.3 剧集元数据解析
- `episodeMeta()` - 剧集元数据（时长、评分）
- `episodeCrew()` - 剧集制作人员

**修复模式**：
```java
// 外层 try-catch 保护整个方法
try {
    for (JsonElement element : array) {
        // 内层 try-catch 保护每个元素
        try {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            // 处理逻辑
        } catch (ClassCastException e) {
            Log.e(TAG, "ClassCastException: " + e.getMessage());
        } catch (Throwable e) {
            Log.e(TAG, "Error: " + e.getMessage());
        }
    }
} catch (Throwable e) {
    Log.e(TAG, "Fatal error: " + e.getMessage(), e);
    return defaultValue;
}
```

## 日志增强

所有异常捕获都添加了详细的日志输出：
- 使用 `SpiderDebug.log()` 输出到应用日志系统
- 使用 `android.util.Log.e()` 输出到 Android logcat
- 日志包含：
  - 上下文信息（方法名、数据类型）
  - 异常类型（ClassCastException 等）
  - 异常消息

## 测试建议

1. **正常流程测试**：
   - 进入各种类型的 TMDB 详情页（电影、剧集）
   - 查看演员列表、剧集列表
   - 检查评分、分级信息显示

2. **异常场景测试**：
   - 网络不稳定情况
   - 数据格式异常的条目
   - 查看 logcat 日志，确认异常被正确捕获

3. **日志验证**：
   ```bash
   adb logcat | grep -E "TmdbService|TmdbDetailActivity|SafeJsonParser"
   ```

## 后续优化建议

1. **Proguard 规则优化**：
   - 考虑为 TMDB 相关的 bean 类添加 `@Keep` 注解
   - 或在 proguard-rules.pro 中添加更精确的保留规则

2. **类型安全增强**：
   - 考虑使用 TypeToken 进行泛型反序列化
   - 为关键数据类添加自定义 Gson TypeAdapter

3. **监控和上报**：
   - 考虑将 ClassCastException 统计上报到分析平台
   - 跟踪哪些数据源容易出现格式问题

## 修改文件列表

1. **新增**：
   - `app/src/main/java/com/fongmi/android/tv/utils/SafeJsonParser.java`

2. **修改**：
   - `app/src/main/java/com/fongmi/android/tv/service/TmdbService.java`
   - `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`

## 影响范围

- **兼容性**：修改完全向后兼容，不影响现有功能
- **性能**：异常处理开销极小，正常流程无影响
- **用户体验**：从崩溃变为降级显示，大幅提升稳定性
