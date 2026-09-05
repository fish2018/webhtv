# 手机版详情页按钮修改 - 最终版

## 修改概述

根据不同的详情页模式，实现不同的按钮行为：

### 影视原生模式
- **保持"搜索"按钮**（不变）
- 短按：显示快速搜索（站内搜索同名视频）

### 原生增强模式
- **"换源"按钮**（新增显示）
- 短按：自动换源
- 长按：全局搜索

### 沉浸融合/炫彩详情/详情直放模式
- **"换源"按钮**（文本从"搜索"改为"换源"）
- 短按：自动换源
- 长按：全局搜索

## 详情页模式说明

手机版使用两套详情页系统：

### 1. VideoActivity
承载两种模式：
- **影视原生模式**（非 TMDB）：使用 `activity_video.xml` 的原生布局
- **原生增强模式**（TMDB 内嵌）：使用 `TmdbHeaderView` 内嵌视图

### 2. TmdbDetailActivity  
承载三种模式（TV版和手机版共用）：
- **沉浸融合模式**：内嵌播放器 + 融合按钮组
- **炫彩详情模式**：详情区按钮行
- **详情直放模式**：播放器控制栏按钮

## 修改文件清单

### 1. 布局文件（保持影视原生不变）

#### `app/src/mobile/res/layout/activity_video.xml`（手机）
**第252-256行** - **保持"搜索"**：
```xml
<com.google.android.material.textview.MaterialTextView
    android:id="@+id/search"
    style="@style/VideoActionButton"
    android:layout_marginEnd="8dp"
    android:text="@string/play_search"
    app:drawableTopCompat="@drawable/ic_action_search_shadow" />
```

#### `app/src/mobile/res/layout-sw600dp/activity_video.xml`（平板）
**第245-249行** - **保持"搜索"**（同上）

### 2. TmdbDetailActivity 布局（改为"换源"）

#### `app/src/main/res/layout/activity_tmdb_detail.xml`

**第539-542行** - `playerChangeSource`（详情直放模式）：
```xml
<TextView
    android:id="@+id/playerChangeSource"
    android:text="@string/play_change" />
```

**第717-729行** - `changeSource`（沉浸融合模式）：
```xml
<com.google.android.material.button.MaterialButton
    android:id="@+id/changeSource"
    android:text="@string/play_change" />
```

**第909-927行** - `changeSourceDetail`（炫彩详情模式）：
```xml
<com.google.android.material.button.MaterialButton
    android:id="@+id/changeSourceDetail"
    android:text="@string/play_change" />
```

### 3. TmdbHeaderView 布局（已经是"换源"）

#### `app/src/main/res/layout/view_tmdb_header.xml`
**第297-309行** - `tmdbChangeSource`（原生增强模式）：
```xml
<com.google.android.material.button.MaterialButton
    android:id="@+id/tmdbChangeSource"
    android:text="@string/play_change" />
```
✅ 布局文本已经是"换源"，无需修改

### 4. Java 代码修改

#### `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`

**第784行** - **保持原有搜索行为**：
```java
mBinding.search.setOnClickListener(view -> onSearch());
```

**第3420-3440行** - TmdbHeaderView 换源按钮回调（原生增强模式）：
```java
mTmdbHeaderView.setActionListener(new com.fongmi.android.tv.ui.custom.TmdbHeaderView.ActionListener() {
    @Override
    public void onChangeSource() {
        onChange();  // 短按：换源
    }

    @Override
    public void onChangeSourceLongClick() {
        onSearchGlobal();  // 长按：全局搜索
    }

    @Override
    public void onRematch() {
        showManualTmdbMatchDialog();
    }

    @Override
    public void onKeep() {
        VideoActivity.this.onKeep();
    }
});
```

**第1914-1918行** - 新增全局搜索方法：
```java
private boolean onSearchGlobal() {
    SearchActivity.start(this, mBinding.name.getText().toString());
    return true;
}
```

#### `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`

**第539-542、791-792行** - 修改按钮点击事件：
```java
// 沉浸融合/炫彩详情模式
binding.changeSource.setOnClickListener(view -> changeSource());
binding.changeSourceDetail.setOnClickListener(view -> changeSource());
binding.changeSource.setOnLongClickListener(view -> openGlobalSourceSearch());
binding.changeSourceDetail.setOnLongClickListener(view -> openGlobalSourceSearch());

// 详情直放模式（播放器控制栏）
binding.playerChangeSource.setOnClickListener(view -> changeSource());
binding.playerChangeSource.setOnLongClickListener(view -> openGlobalSourceSearch());
```

#### `app/src/main/java/com/fongmi/android/tv/ui/custom/TmdbHeaderView.java`

**第96-103行** - 添加长按接口：
```java
public interface ActionListener {
    void onChangeSource();
    void onChangeSourceLongClick();  // 新增
    void onRematch();
    void onKeep();
}
```

**第520-527行** - 添加长按事件：
```java
View tmdbChangeSource = headerRoot.findViewById(R.id.tmdbChangeSource);
tmdbChangeSource.setOnClickListener(view -> {
    if (actionListener != null) actionListener.onChangeSource();
});
tmdbChangeSource.setOnLongClickListener(view -> {
    if (actionListener != null) actionListener.onChangeSourceLongClick();
    return true;
});
```

**第531-536行** - 解除原生增强模式的隐藏限制：
```java
private void updateOriginalEnhancedActionVisibility() {
    if (headerRoot == null) return;
    View changeSource = headerRoot.findViewById(R.id.tmdbChangeSource);
    if (changeSource == null) return;
    // 原来：原生增强模式隐藏换源按钮
    // 现在：所有模式都显示换源按钮
    changeSource.setVisibility(View.VISIBLE);
}
```

## 功能对比表

| 详情页模式 | Activity | 按钮文本 | 短按 | 长按 | 修改内容 |
|-----------|----------|---------|------|------|---------|
| 影视原生 | VideoActivity | 搜索 | 快速搜索 | - | 保持不变 ✓ |
| 原生增强 | VideoActivity + TmdbHeaderView | 换源 | 自动换源 | 全局搜索 | 显示按钮 + 添加长按 ✓ |
| 沉浸融合 | TmdbDetailActivity | 换源 | 自动换源 | 全局搜索 | 改文本 + 添加长按 ✓ |
| 炫彩详情 | TmdbDetailActivity | 换源 | 自动换源 | 全局搜索 | 改文本 + 添加长按 ✓ |
| 详情直放 | TmdbDetailActivity | 换源 | 自动换源 | 全局搜索 | 改文本 + 添加长按 ✓ |

## 修复的问题

1. ✅ **原生增强模式没有换源按钮**
   - 原因：`TmdbHeaderView` 在原生增强模式下隐藏了换源按钮
   - 修复：移除隐藏逻辑，让按钮在所有模式下可见

2. ✅ **沉浸融合/炫彩详情/详情直放模式显示"搜索"**
   - 原因：`TmdbDetailActivity` 三个按钮的文本都是 `@string/play_search`
   - 修复：全部改为 `@string/play_change`

3. ✅ **添加长按全局搜索功能**
   - 所有 TMDB 模式的换源按钮都支持长按进入全局搜索

4. ✅ **保持影视原生模式不变**
   - 影视原生模式继续使用"搜索"按钮，保持原有行为

## 编译结果

```bash
./gradlew app:assembleMobileArm64_v8aDebug
```

✅ 编译成功

## 用户体验

### 影视原生模式
- 保持原有"搜索"功能，用户习惯不变
- 快速搜索同名视频，站内换源

### TMDB 模式（原生增强/沉浸融合/炫彩详情/详情直放）
- **短按"换源"**：视频卡顿时快速切换到其他站源
- **长按"换源"**：搜索其他视频，功能不丢失
- **操作统一**：所有 TMDB 模式按钮行为一致

## 设计理由

1. **影视原生保持搜索**：
   - 影视原生模式不显示 TMDB 信息，主要用于快速浏览
   - "搜索"按钮触发快速搜索（站内同名搜索），符合该模式定位
   - 不修改可避免影响现有用户习惯

2. **TMDB 模式统一为换源**：
   - TMDB 模式通常用于深度观看，换源是更高频的需求
   - 所有 TMDB 模式（原生增强/沉浸融合/炫彩详情/详情直放）操作统一
   - 长按保留全局搜索，功能不丢失

3. **标题长按保持换源**：
   - 所有模式下，长按标题都可触发换源
   - 为习惯该操作的用户保留快捷入口
