# FFmpeg 模式切换功能

## 功能说明

用户现在可以在设置中切换**四种**播放器渲染器实现：
- **NextLib**（手机 + TV 默认）：使用第三方 `nextlib-media3ext` 库的自定义 FFmpeg 音频/视频渲染器
- **Simple**：使用默认视频渲染路径，不使用 NextLib 视频渲染器；保留 FFmpeg 音频兜底
- **Official**：仅使用 Media3 官方渲染器（保留自定义缓冲配置）
- **自动**：仅 EXO 内核生效。播放失败时在**当前解码**下自动遍历 NextLib → Simple → Official，三种都失败后再交给现有的解码/内核降级链（硬解→软解→换内核）。遍历不写盘，仅进程内临时覆盖；退出重播或换片后重置。

## 使用方法

### Leanback（电视端）
设置 → 播放器设置 → FFmpeg模式 → 点击循环切换

### Mobile（手机端）
设置 → 播放器 → FFmpeg模式 → 点击循环切换

显示：`NextLib` → `Official` → `Simple` → `自动` → `NextLib` ...
**注意**：切换后需退出视频重新播放才生效

**默认模式**：手机端与 TV 端均为 NextLib

---

## 🎯 推荐使用顺序

**如果遇到卡顿掉帧，请按此顺序尝试：**

### 1. **Simple 模式**
✅ 默认视频渲染路径接近 TV 项目
✅ 无自定义 FFmpeg 视频渲染器
✅ 保留 FFmpeg 音频兜底，避免系统解码器不支持的 iOS/HLS 音轨无声
✅ 无自定义缓冲配置（使用 ExoPlayer 默认值）
✅ 最简单、最稳定

### 2. **NextLib 模式**（手机 + TV 默认）
⚠️ 使用第三方 FFmpeg 渲染器
⚠️ 自定义缓冲配置
⚠️ 最复杂，可能存在兼容性问题
⚠️ 开始流畅后可能卡顿（过度缓冲）

### 3. **Official 模式**
❌ 移除 NextLib 渲染器
❌ 保留自定义缓冲配置
❌ 可能从头卡到尾（某些格式不支持）

---

## 技术实现

### 核心差异对比

| 特性 | NextLib | Official | Simple |
|------|---------|----------|--------|
| **FFmpeg 渲染器** | NextLib 音频 + 视频 | 无 | 仅音频兜底 |
| **LoadControl** | 自定义缓冲 | 自定义缓冲 | ExoPlayer 默认 |
| **ExoEnhanced** | 支持 | 支持 | 不支持 |
| **复杂度** | 高 | 中 | 低 |
| **与 TV 项目对比** | 完全不同 | 部分相似 | 视频路径相近，音频兜底增强 |

### 1. NextLib 模式（mode = 0）

```java
// 使用 FfmpegRenderersFactory 自定义渲染器
private static class FfmpegRenderersFactory extends DefaultRenderersFactory {
    @Override
    protected void buildAudioRenderers(...) {
        super.buildAudioRenderers(...);
        out.add(..., new CompatFfmpegAudioRenderer(...));
    }
    @Override
    protected void buildVideoRenderers(...) {
        super.buildVideoRenderers(...);
        out.add(..., new FfmpegVideoRenderer(...));
    }
}

// 自定义 LoadControl
builder.setLoadControl(buildLoadControl());
```

**依赖**: `io.github.anilbeesetti:nextlib-media3ext:1.10.0-0.12.1`

### 2. Official 模式（mode = 1）

```java
// 仅使用标准 DefaultRenderersFactory
DefaultRenderersFactory factory = new DefaultRenderersFactory(App.get());
return factory
    .setEnableDecoderFallback(true)
    .setExtensionRendererMode(Math.max(audioRenderMode, videoRenderMode));

// 自定义 LoadControl
builder.setLoadControl(buildLoadControl());
```

### 3. Simple 模式（mode = 2）

```java
// 使用默认视频路径，只追加 FFmpeg 音频兜底
DefaultRenderersFactory factory = new FfmpegAudioFallbackRenderersFactory(App.get(), audioRenderMode, audioPrefer);
return factory
    .setEnableDecoderFallback(true)
    .setExtensionRendererMode(Math.max(audioRenderMode, videoRenderMode));

// 不设置 LoadControl，使用 ExoPlayer 默认值
// builder.setLoadControl(...);  // 注释掉
```

**关键**：Simple 模式在 `buildPlayer` 中跳过 `setLoadControl`，但仍保留 FFmpeg 音频兜底。

---

## 修改文件列表

### 核心逻辑
- `app/src/main/java/com/fongmi/android/tv/setting/PlayerSetting.java`
  - 新增 `getFFmpegMode()` / `putFFmpegMode(int)` 方法
  - 支持 0=NextLib, 1=Official, 2=Simple
  - **默认值：手机端与 TV 端均为 `0`（NextLib）**

- `app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java`
  - `buildPlayer` - Simple 模式跳过 `setLoadControl`
  - `buildRenderersFactory` - 根据模式分发到三种实现
  - `buildNextLibRenderersFactory` - NextLib 模式
  - `buildOfficialRenderersFactory` - Official 模式
  - `buildSimpleRenderersFactory` - Simple 模式

### UI 界面（Leanback + Mobile）
- 布局文件 - 添加"FFmpeg模式"设置项
- Activity/Fragment - 添加循环切换逻辑（0→1→2→0）
- 显示文本：`NextLib` / `Official` / `Simple`

### 字符串资源
- `strings.xml` (EN/CN/TW) - `player_ffmpeg_mode`

---

## 问题诊断

### 用户反馈分析

**症状**：
- Official 模式：从头卡到尾（完全无法播放）
- NextLib 模式：开始流畅，后面卡顿掉帧

**原因分析**：
1. **Official 从头卡到尾** → 系统 MediaCodec 不支持该视频格式，需要 FFmpeg 软解
2. **NextLib 开始流畅后卡顿** → FFmpeg 能解码，但可能是：
   - 自定义缓冲配置过度缓冲，导致内存压力
   - 渲染器插入逻辑导致 AV 不同步
   - ExoEnhanced 参数过于激进

**解决方案**：两端统一默认 NextLib，卡顿时手动降级
- ✅ 手机端与 TV 端均默认 NextLib，优先保证 iOS/HLS 等格式的音视频兼容
- ✅ 遇到卡顿掉帧可手动切到 Simple，移除复杂的 NextLib 视频渲染器插入逻辑
- ✅ Simple 模式仍保留 FFmpeg 音频兜底，避免系统音频解码器不支持时无声
- ✅ Simple 模式使用 ExoPlayer 默认缓冲策略

---

## 技术细节

### LoadControl 差异

**TV 项目（Simple 模式）：**
```java
ExoPlayer player = new ExoPlayer.Builder(App.get())
    .setTrackSelector(...)
    .setRenderersFactory(...)
    .setMediaSourceFactory(...)
    .build();  // 不设置 LoadControl
```

**当前项目（NextLib/Official 模式）：**
```java
builder.setLoadControl(new DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        DEFAULT_MIN_BUFFER_MS * PlayerSetting.getBuffer(),  // 用户设置放大
        DEFAULT_MAX_BUFFER_MS * PlayerSetting.getBuffer(),
        ...
    )
    .build());
```

**问题**：用户设置的缓冲倍数可能导致过度缓冲，触发 GC 或内存压力。

### 渲染器链对比

**NextLib 模式：**
```
1. MediaCodec 硬解渲染器（系统）
2. CompatFfmpegAudioRenderer（NextLib）
3. FfmpegVideoRenderer（NextLib）
```

**Official 模式：**
```
1. MediaCodec 硬解渲染器（系统）
2. 无额外渲染器
```

**Simple 模式：**
```
1. MediaCodec 硬解渲染器（系统）
2. CompatFfmpegAudioRenderer（仅音频兜底）
```

通过 `setExtensionRendererMode` 控制是否优先使用扩展渲染器。

---

## 存储键

```java
Prefers.getInt("ffmpeg_mode", getDefaultFFmpegMode())
// 0 = NextLib
// 1 = Official
// 2 = Simple
// 3 = 自动（AUTO）：持久值；实际渲染时由 getEffectiveFFmpegMode() 解析成具体模式
// getDefaultFFmpegMode(): mobile 与 leanback/TV 均为 0（NextLib）
```

## 自动（AUTO）模式

选择「自动」后，播放失败时会在**当前解码档位**下按 `FFMPEG_AUTO_ORDER`（NextLib → Simple → Official）依次重建播放器重试，用尽三种模式仍失败才交回既有的解码/内核降级链（硬解→软解→换内核）。整体次序为：硬解下遍历三模式 → 软解下遍历三模式 → 换下一个内核。

- 仅在 EXO 内核 + FFmpeg 模式为 AUTO 时生效（`fallbackFfmpegMode`）。
- 只对**播放失败**（`PlaybackException`）触发；掉帧不触发（掉帧没有干净的布尔信号，且换渲染器需重建播放器，误判代价大）。
- 遍历状态用 `ffmpegModeFallbackTried[]` 防重复；切软解与 `reset()` 时清空，运行时用进程内 `ffmpegModeOverride` 临时覆盖具体模式，不写盘。

---

## 未来优化建议

1. ~~**默认值调整**：手机端与 TV 端统一默认 NextLib~~ ✅ 已完成
2. ~~**失败自动遍历**：新增「自动」模式，播放失败时遍历三种 FFmpeg 模式~~ ✅ 已完成
3. **自动检测**：根据视频格式主动选择合适的模式（当前仅在失败后被动遍历）
4. **性能监控**：记录掉帧率，提示用户切换模式

---

**修改日期**: 2026-07-05
**相关 Issue**: 用户反馈 TV 项目能正常播放的剧，当前项目卡顿掉帧
**测试结论**: Official 从头卡到尾，NextLib 开始流畅后卡顿；Simple 保留音频兜底并移除 NextLib 视频渲染器
**默认模式**: 手机端与 TV 端均为 NextLib
