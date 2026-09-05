# TMDB 详情页崩溃修复 - 快速参考

## 问题
手机版进入 TMDB 详情页崩溃：
```
ClassCastException: com.google.gson.internal.LinkedTreeMap cannot be cast to i92
```

## 解决方案
✅ **已添加全面的异常捕获和日志记录，防止崩溃**

### 修改的文件

1. **新增文件**：
   - `app/src/main/java/com/fongmi/android/tv/utils/SafeJsonParser.java`
     - 提供安全的 JSON 解析工具方法
     - 所有方法都有异常保护和日志记录

2. **修改文件**：
   - `app/src/main/java/com/fongmi/android/tv/service/TmdbService.java`
     - 保护了所有列表解析方法（cast, episodes, items, aggregateCast, integers）
     - 保护了所有 Gson.fromJson 调用
     - 添加了详细的错误日志
   
   - `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`
     - 保护了季度信息解析（tmdbSeasonYear, firstSeasonNumber, addSeasonCount）
     - 保护了分级信息解析（certificationForRegion）
     - 保护了剧集元数据解析（episodeMeta, episodeCrew）

### 修复策略

**双层防护**：
```java
// 外层：保护整个方法
try {
    for (JsonElement element : array) {
        // 内层：保护每个元素
        try {
            JsonObject object = element.getAsJsonObject();
            // 处理逻辑
        } catch (ClassCastException e) {
            // 记录日志，继续处理下一个
            Log.e(TAG, "类型转换错误: " + e.getMessage());
        }
    }
} catch (Throwable e) {
    // 兜底保护，返回安全默认值
    Log.e(TAG, "方法执行失败: " + e.getMessage(), e);
    return safeDefaultValue;
}
```

### 日志增强

所有异常都会输出详细日志：
- **SpiderDebug.log** - 应用内日志系统
- **android.util.Log** - Android logcat
- 包含上下文信息（方法名、元素类型、错误原因）

### 查看日志

```bash
# 查看所有相关日志
adb logcat | grep -E "TmdbService|TmdbDetailActivity|SafeJsonParser"

# 只看错误
adb logcat *:E | grep -E "TmdbService|TmdbDetailActivity"
```

### 测试重点

1. ✅ 进入各类 TMDB 详情页（电影/剧集）
2. ✅ 查看演员列表、剧集列表
3. ✅ 检查评分、分级信息
4. ✅ 验证应用不再崩溃
5. ✅ 查看日志确认异常被正确捕获

### 效果

- **修复前**：数据异常 → 应用崩溃 → 用户体验极差
- **修复后**：数据异常 → 记录日志 → 降级显示 → 应用稳定运行

### 原则

> **永远不要因为数据解析问题让应用崩溃**
> - 捕获异常
> - 记录日志
> - 返回默认值或跳过
> - 让用户可以继续使用应用

## 编译和部署

```bash
# 清理构建
./gradlew clean

# 构建 Debug 版本
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

---

**详细文档**：查看 `TMDB_ERROR_HANDLING_FIXES.md`
