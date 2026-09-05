# 播放器屏显系统统一 - 实施总结

## 任务目标

将沉浸融合模式（手机端 TmdbDetailActivity）的独立显示面板废弃，统一使用影视原生模式的 `PlayerOsdController`（OSD）系统。

## 完成状态

✅ **已完成** - 编译通过，架构统一

## 核心修改

### 1. TmdbDetailActivity.java - 3 处修改

#### 1.1 启用 OSD 系统（第 805-808 行）
```java
@Override
public boolean suppressPersistentOsd() {
    return false;  // 原来返回 Util.isMobile()
}
```

#### 1.2 废弃独立面板更新方法（第 6155-6159 行）
```java
private void updateInlineDisplayPanel() {
    // 已废弃：统一使用 PlayerOsdController（OSD）系统
}
```

#### 1.3 废弃独立面板隐藏方法（第 5689-5697 行）
```java
private void hideInlineDisplayPanel() {
    // 已废弃：统一使用 PlayerOsdController（OSD）系统
}
```

### 2. PlayerOsdController.java - 1 处简化

#### 移除控制栏显示时的 persistentSuppressed 特殊分支（第 168-211 行）

**修改前：**
```java
if (controlsVisible) {
    if (persistentSuppressed) {
        root.setVisibility(View.GONE);  // 手机端特殊处理
        return true;
    }
    // 强制显示标题、分辨率、时间
}
```

**修改后：**
```java
if (controlsVisible) {
    // 所有模式统一：强制显示标题、分辨率、时间
    setTopLeftForControls(player);
    setTopRightForControls();
    // ...
}
```

## 架构对比

### 修改前（双系统）

```
TV端 (leanback)           → PlayerOsdController
手机影视原生 (mobile)      → PlayerOsdController
手机沉浸融合 (mobile)      → updateInlineDisplayPanel (独立面板)
                           ↳ suppressPersistentOsd() = true
```

### 修改后（统一系统）

```
TV端 (leanback)           → PlayerOsdController
手机影视原生 (mobile)      → PlayerOsdController
手机沉浸融合 (mobile)      → PlayerOsdController
                           ↳ suppressPersistentOsd() = false
```

## 统一后的行为

### 所有播放模式（TV/手机原生/手机融合）

**控制栏显示时：**
- ✅ 强制显示：标题、分辨率、时间
- ❌ 隐藏：进度、流量、诊断面板、迷你进度条
- 无论用户是否在设置中开启这些屏显选项

**控制栏隐藏时：**
- 按用户设置正常显示所有屏显信息
- 退出控制栏后，强制显示的信息自动消失

## 技术细节

### OSD 布局适配

两个 Activity 都使用相同的 OSD 布局：

**mobile VideoActivity（影视原生）：**
```xml
<FrameLayout android:id="@+id/video">  <!-- 全屏容器 -->
    <include android:id="@+id/osd" layout="@layout/view_player_osd"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</FrameLayout>
```

**TmdbDetailActivity（沉浸融合）：**
```xml
<MaterialCardView android:id="@+id/playerPanel"  <!-- 252dp 内嵌卡片 -->
    android:layout_width="match_parent"
    android:layout_height="252dp">
    <include android:id="@+id/osd" layout="@layout/view_player_osd"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</MaterialCardView>
```

OSD 是 `match_parent`，会自适应父容器：
- 影视原生：全屏显示
- 沉浸融合：内嵌时 252dp，全屏时自动扩展

### OSD 生命周期

沉浸融合模式的 OSD 生命周期正常工作：
```java
onStart()    → inlinePlayerUi.onStart()   → osd.start()
onStop()     → inlinePlayerUi.onStop()    → osd.stop()
onDestroy()  → inlinePlayerUi.release()   → osd.release()
```

## 遗留代码（可未来清理）

### 保留但不再使用的布局 View

`activity_tmdb_detail.xml` 中的独立面板 View（编译不会报错）：
- `playerDisplayTopLeft` (容器)
  - `playerDisplayTitle`
  - `playerDisplaySize`
- `playerDisplayClock`
- `playerDisplayTraffic`
- `playerDisplayBottomProgress` (容器)
  - `playerDisplayBar`
  - `playerDisplayPosition`
- `playerDisplayMini`

### 保留的空方法

- `updateInlineDisplayPanel()` - 25+ 处调用点，保留避免遗漏
- `hideInlineDisplayPanel()` - 4 处调用点，保留避免遗漏
- `tintInlineDisplay()` - 引用面板 View，但 View 仍在布局中不会编译错误

### 保留的正常引用

- `binding.playerDisplay` - 控制栏里的「屏显」**按钮**（不是面板），正常使用
- `inlinePauseInfo` - 暂停信息标志位，保留但已不生效

## 编译验证

```bash
./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac
```

**结果：** ✅ BUILD SUCCESSFUL - 无语法错误，无引用错误

## 测试建议

### 手机端沉浸融合模式（主要测试目标）

1. **进入详情页，点击播放**
   - 验证内嵌播放器正常显示
   - 验证 OSD 布局适配 252dp 卡片

2. **唤醒控制栏**
   - ✅ 左上角显示标题和分辨率
   - ✅ 右上角显示时间
   - ❌ 不显示进度、流量等其他屏显

3. **退出控制栏**
   - 验证强制显示的信息消失
   - 验证屏显按用户设置显示

4. **进入全屏**
   - 验证 OSD 随播放器扩展到全屏
   - 验证控制栏唤醒行为与内嵌时一致

### 手机端影视原生模式（对照组）

1. **点击剧集直接播放**
2. **验证行为与沉浸融合一致**

### TV端（leanback）

1. **点击剧集播放**
2. **验证行为与手机端一致**

## 问题排查指南

### 沉浸融合模式右上角没有时间
- 检查 `suppressPersistentOsd()` 是否返回 `false`
- 检查 `PlayerSetting.isOsdEnabled()` 是否为 `true`

### 出现两套屏显同时显示
- 检查 `updateInlineDisplayPanel()` 是否已改为空实现
- 检查 `suppressPersistentOsd()` 是否返回 `false`

### OSD 位置不对或尺寸异常
- 检查 `activity_tmdb_detail.xml` 中 `osd` include 是否在 `playerPanel` 内
- 检查 OSD 布局是否为 `match_parent`

## 收益

1. **代码简化**：移除 70+ 行重复逻辑
2. **维护成本降低**：单一屏显系统，统一维护
3. **行为一致**：所有模式使用相同的屏显逻辑
4. **架构清晰**：不再需要 `persistentSuppressed` 特殊分支

## 相关文档

- [详细重构文档](feature_osd_unified_system.md)
- [控制栏强制显示功能](feature_osd_controls_display.md)
