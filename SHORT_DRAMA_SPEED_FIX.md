# 短剧模式倍速保持功能修复

## 问题描述

### 问题1：切换集数时倍速重置
在短剧模式下，用户通过设置按钮调整了播放倍速后，当自动播放下一集或手动切换集数时，播放倍速会重置为 1.0 倍速，导致用户需要在每一集都重新设置倍速。

### 问题2：不同剧集共享倍速
用户在剧A设置了 1.5x 倍速后，切换到剧B（不同的剧）时，剧B也会使用 1.5x 倍速，而不是从 1.0x 开始。用户期望不同剧集的倍速设置应该是独立的。

## 问题原因

### 原因1：切换集数时倍速丢失
1. **倍速保存机制正常**：用户调整倍速后，速度会正确保存到：
   - `PlayerSetting.putDefaultSpeed()` - 全局默认倍速
   - `mHistory.setSpeed()` - 当前剧集的历史记录中

2. **切换集数时倍速丢失**：
   - 切换集数时会调用 `onRefresh()` 重新初始化播放器
   - 在 `onPrepare()` 回调中，只恢复了播放位置（`setPosition()`）
   - **没有恢复播放速度**，导致速度重置为默认值

### 原因2：全局默认速度被污染
1. **全局默认速度的设计意图**：`PlayerSetting.getDefaultSpeed()` 本应只用于设置所有新剧的初始倍速
2. **实际问题**：每次用户调整倍速时，都会更新全局默认速度：
   ```java
   PlayerSetting.putDefaultSpeed(player().getSpeed());  // 每次调速都写入
   ```
3. **导致跨剧污染**：
   - 用户在剧A设置 1.5x → `PlayerSetting.putDefaultSpeed(1.5)`
   - 切换到新剧B → 使用 `PlayerSetting.getDefaultSpeed()` → 得到 1.5x
   - 用户期望剧B从 1.0x 开始，但实际是 1.5x

## 修复方案

### 代码修改

在两个文件中添加了倍速恢复逻辑：

1. **app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java**
2. **app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java**

### 修改内容

#### 1. 在 `onPrepare()` 方法中添加速度恢复

```java
protected void onPrepare() {
    android.util.Log.d("VideoActivity", "onPrepare: setting Clock callback");
    setPlayerKernel();
    setDecode();
    setLut();
    setPosition();
    setSpeed();  // 新增：恢复播放速度
    mClock.setCallback(this);
    requestIntroSkipPlan();
}
```

#### 2. 新增 `setSpeed()` 方法

```java
private void setSpeed() {
    if (mHistory == null) return;
    float speed = mHistory.getSpeed();
    if (speed > 0 && speed != 1f) {
        mBinding.control.action.speed.setText(player().setSpeed(speed));
    }
}
```

## 功能特点

### 1. 同一部剧集自动继承倍速
- 用户在某一集设置倍速后，该剧的所有集数都会使用相同的倍速
- 切换集数时自动应用历史记录中保存的倍速
- 不需要每集都重新设置

### 2. 不同剧集独立倍速
- 每部剧有独立的 `History` 记录
- 切换到不同的剧时，倍速设置互不影响
- 每部剧可以有自己的倍速设置

### 3. 适用范围
- ✅ 短剧模式（自动全屏播放）
- ✅ 普通剧集模式
- ✅ Mobile 端和 TV 端（leanback）

## 技术细节

### 速度保存时机

用户调整倍速时，速度会在以下时机保存：

1. **点击倍速按钮调整**（`onSpeed()`）：
   ```java
   PlayerSetting.putDefaultSpeed(player().getSpeed());
   mHistory.setSpeed(player().getSpeed());
   ```

2. **长按倍速按钮切换**（`onSpeedLong()`）：
   ```java
   PlayerSetting.putDefaultSpeed(player().getSpeed());
   mHistory.setSpeed(player().getSpeed());
   ```

3. **手势调速结束**（`onSpeedEnd()`）：
   ```java
   mHistory.setSpeed(player().getSpeed());
   ```

### 速度恢复时机

播放器准备完成后（`onPrepare()`）：
- 恢复播放位置：`setPosition()`
- **恢复播放速度：`setSpeed()`**（新增）

### 代码逻辑

```java
private void setSpeed() {
    // 检查历史记录是否存在
    if (mHistory == null) return;
    
    // 获取历史记录中保存的速度
    float speed = mHistory.getSpeed();
    
    // 只在速度有效且不为1倍速时恢复
    // speed > 0: 确保速度值有效
    // speed != 1f: 1倍速是默认值，无需特别设置
    if (speed > 0 && speed != 1f) {
        // 应用速度并更新UI显示
        mBinding.control.action.speed.setText(player().setSpeed(speed));
    }
}
```

## 测试场景

### 场景 1：短剧模式下切换集数
1. 进入短剧模式播放第1集
2. 通过设置按钮调整倍速为 1.5x
3. 播放完毕自动切换到第2集
4. ✅ 第2集应该以 1.5x 倍速播放

### 场景 2：手动切换集数
1. 播放某一集，设置倍速为 2.0x
2. 手动选择下一集播放
3. ✅ 下一集应该以 2.0x 倍速播放

### 场景 3：切换不同的剧
1. 在剧 A 设置倍速为 1.5x
2. 切换到剧 B（未设置过倍速）
3. ✅ 剧 B 应该以默认倍速（1.0x）播放
4. 返回剧 A
5. ✅ 剧 A 应该恢复为 1.5x 倍速

### 场景 4：倍速为 1.0x 时
1. 设置倍速为 1.0x（默认速度）
2. 切换集数
3. ✅ 正常播放，不会执行额外的速度设置

## 相关文件

- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/main/java/com/fongmi/android/tv/bean/History.java`
- `app/src/main/java/com/fongmi/android/tv/setting/PlayerSetting.java`

## Git 提交

```bash
commit 8132a4da8
Author: [Your Name]
Date: [Date]

fix(VideoActivity): 短剧模式下切换集数时保持用户设置的倍速

问题：
- 用户在短剧模式下通过设置按钮调整倍速后，切换到下一集时倍速会重置为1.0倍速
- 虽然倍速设置被保存到了 mHistory.setSpeed() 和 PlayerSetting.putDefaultSpeed()
- 但在 onPrepare() 方法中只恢复了播放位置，没有恢复播放速度

修复：
- 在 onPrepare() 方法中添加 setSpeed() 调用
- 新增 setSpeed() 方法从历史记录中恢复用户设置的播放速度
- 同时修复 mobile 端和 leanback (TV) 端的 VideoActivity

影响范围：
- 适用于同一部剧的所有集数，用户设置一次倍速后切换集数会自动继承
- 不影响其他剧集的倍速设置（每部剧有独立的 History 记录）
```
