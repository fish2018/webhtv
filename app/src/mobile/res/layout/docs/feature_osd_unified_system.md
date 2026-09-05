# 播放器屏显系统统一重构

## 概述

将沉浸融合模式的独立显示面板（`updateInlineDisplayPanel` / `playerDisplay*`）废弃，统一使用 `PlayerOsdController`（OSD）系统。

## 重构前的问题

### 双显示系统架构（已废弃）

**系统1：通用 OSD（`PlayerOsdController`）**
- TV端（leanback）和手机端影视原生模式使用
- 布局：`view_player_osd.xml`
- View：`osdTopLeft`, `osdTopRight`, `osdBottomLeft`, `osdBottomRight` 等

**系统2：手机端独立面板（`updateInlineDisplayPanel`）**
- 仅手机端沉浸融合模式使用
- 布局：`activity_tmdb_detail.xml` 中的 `playerDisplay*`
- View：`playerDisplayTitle`, `playerDisplaySize`, `playerDisplayClock` 等

### 问题
1. **重复维护**：两套系统实现相同功能，逻辑分散在两处
2. **不一致**：行为差异需要单独处理
3. **复杂性**：`suppressPersistentOsd()` 返回 `Util.isMobile()` 导致逻辑混乱

## 重构后的架构

### 统一 OSD 系统

所有模式（TV端、手机端影视原生、手机端沉浸融合）都使用 `PlayerOsdController`。

## 关键修改

### 1. `TmdbDetailActivity.java`

#### 1.1 修改 `suppressPersistentOsd()` 返回值（第 805-808 行）

**修改前：**
```java
@Override
public boolean suppressPersistentOsd() {
    return Util.isMobile();
}
```

**修改后：**
```java
@Override
public boolean suppressPersistentOsd() {
    // 统一使用 OSD 系统，不再使用独立的 playerDisplay* 面板
    return false;
}
```

#### 1.2 废弃 `updateInlineDisplayPanel()`（第 6155-6159 行）

**修改前：** 70+ 行复杂逻辑，处理控制栏显示/隐藏时的面板更新

**修改后：**
```java
private void updateInlineDisplayPanel() {
    // 已废弃：统一使用 PlayerOsdController（OSD）系统
    // 原逻辑已迁移到 PlayerOsdController.render()
    // 保留此方法避免删除所有调用点时遗漏
}
```

#### 1.3 废弃 `hideInlineDisplayPanel()`（第 5689-5697 行）

**修改前：** 隐藏所有 `playerDisplay*` View

**修改后：**
```java
private void hideInlineDisplayPanel() {
    // 已废弃：统一使用 PlayerOsdController（OSD）系统
}
```

### 2. `PlayerOsdController.java`

#### 2.1 简化 `render()` 方法（第 168-211 行）

**移除了 `persistentSuppressed` 在控制栏显示时的特殊处理**

**修改前：**
```java
if (controlsVisible) {
    if (persistentSuppressed) {
        root.setVisibility(View.GONE);
        return true;
    }
    // ... 强制显示逻辑
}
```

**修改后：**
```java
if (controlsVisible) {
    setTopLeftForControls(player);
    setTopRightForControls();
    // ... 强制显示逻辑
    // persistentSuppressed 检查仅在控制栏隐藏时保留
}
```

现在 `persistentSuppressed` 只影响**控制栏隐藏时**的行为（是否显示持久化屏显），而不影响控制栏显示时的强制显示逻辑。

## 统一后的行为

### 所有模式（TV端、手机端原生、手机端融合）

**控制栏显示时：**
- ✅ 强制显示：标题、分辨率、时间（无论用户设置）
- ❌ 隐藏：进度、流量、诊断面板、迷你进度条

**控制栏隐藏时：**
- 按用户设置正常显示所有屏显信息
- `persistentSuppressed = true` 时只显示诊断面板

## 布局位置验证

### mobile VideoActivity（影视原生）
```xml
<FrameLayout android:id="@+id/video">
    <include android:id="@+id/osd" layout="@layout/view_player_osd"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</FrameLayout>
```

### TmdbDetailActivity（沉浸融合）
```xml
<MaterialCardView android:id="@+id/playerPanel"
    android:layout_width="match_parent"
    android:layout_height="252dp">
    <include android:id="@+id/osd" layout="@layout/view_player_osd"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</MaterialCardView>
```

两者都是 `match_parent`，OSD 会自适应父容器大小：
- 影视原生：全屏
- 沉浸融合：252dp 内嵌卡片（全屏时自动扩展）

## 生命周期

沉浸融合模式的 OSD 生命周期正常工作：
- `onStart()` → `inlinePlayerUi.onStart()` → `osd.start()`
- `onStop()` → `inlinePlayerUi.onStop()` → `osd.stop()`
- `onDestroy()` → `inlinePlayerUi.release()` → `osd.release()`

## 遗留代码

### 保留但不再使用的 View（可未来清理）

`activity_tmdb_detail.xml` 中的独立面板 View：
- `playerDisplayTopLeft` (容器)
  - `playerDisplayTitle`
  - `playerDisplaySize`
- `playerDisplayClock`
- `playerDisplayTraffic`
- `playerDisplayBottomProgress` (容器)
  - `playerDisplayBar`
  - `playerDisplayPosition`
- `playerDisplayMini`

### 保留但空实现的方法

- `updateInlineDisplayPanel()` - 25+ 处调用点，空实现避免遗漏
- `hideInlineDisplayPanel()` - 4 处调用点，空实现避免遗漏
- `tintInlineDisplay()` - 引用面板 View，但因 View 仍在布局中不会编译错误

### 保留的非面板引用

- `binding.playerDisplay` - 控制栏里的「屏显」**按钮**（不是面板），保留正常使用
- `inlinePauseInfo` - 暂停时是否显示信息的标志位，保留但已不生效

## 后续清理建议（可选）

1. 从 `activity_tmdb_detail.xml` 删除 `playerDisplay*` View
2. 删除 `updateInlineDisplayPanel()` 和 `hideInlineDisplayPanel()` 的所有调用
3. 删除 `tintInlineDisplay()` 方法
4. 清理 `inlinePauseInfo` 标志位

## 测试建议

### 沉浸融合模式（手机端）
1. 进入详情页，点击播放进入内嵌播放器
2. 唤醒控制栏，验证：
   - ✅ 左上角显示标题和分辨率
   - ✅ 右上角显示时间
   - ❌ 不显示进度、流量等其他屏显
3. 退出控制栏，验证：
   - 屏显按用户设置显示（如果用户关闭了某项屏显，应该不显示）

### 影视原生模式（手机端）
1. 点击剧集直接播放（全屏）
2. 唤醒控制栏，验证行为与沉浸融合一致

### TV端（leanback）
1. 点击剧集播放
2. 唤醒控制栏，验证行为与手机端一致

## 问题排查

### 如果沉浸融合模式右上角没有时间
- 检查 `suppressPersistentOsd()` 是否返回 `false`
- 检查 `PlayerSetting.isOsdEnabled()` 是否为 `true`
- 检查 OSD 布局是否正确 include 到 `playerPanel` 中

### 如果出现两套屏显同时显示
- 检查 `updateInlineDisplayPanel()` 是否已改为空实现
- 检查 `suppressPersistentOsd()` 是否返回 `false`

### 如果编译错误
- 检查是否误删了 `binding.playerDisplay` **按钮**的引用（不应该删）
- `playerDisplay*` **面板 View** 仍在布局中，不会导致编译错误
