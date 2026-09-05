# E-SP6：软硬解显示反映实际解码器

- 任务 ID：`E-SP6`
- 类别：Exo 性能专项
- 唯一文档：`docs/E-SP6-decode-label-reflects-actual.md`
- 状态：已实施；待实机确认文案。
- 下一动作：用户在电视上确认「配置」行与控制栏解码按钮是否出现 `硬解→软解`。

## 用户观察到的失败

诊断面板自相矛盾：`配置` 行显示 `EXO / 硬解`，而同一面板的 `视频` 行显示 `decoder ffmpegLavc63.3.100-hevc`（FFmpeg 软解）。用户据此无法判断卡顿是否源于落到软解，排查被迫绕远。

## 根因

`PlayerManager.getDecodeText()` → `engine.getDecodeText()` → `ResUtil.getStringArray(R.array.select_decode)[decode]`，即**只反映用户配置的解码档**，与实际运行的解码器无关。

而两者本就可以合法地不一致：硬解档下 `getVideoRenderMode()` 返回 `EXTENSION_RENDERER_MODE_OFF`，`getFfmpegVideoRenderMode()` 又把它转成 `ON`，FFmpeg 视频渲染器仍作为兜底装入以接 MediaCodec 拒绝的编码（见 [`E-SP5`](E-SP5-exo-ffmpeg-fallback-load-shedding.md)）。设备缺该编码硬解时，解码就落到软解，而标签仍写「硬解」。

实际解码器名字本就在同一面板可得（`PlayerOsdController` 的 `视频` 行已用 `snapshot.videoDecoderName()`），缺的只是判定与对照。

## 实现（最终版：复用既有事实管线）

**第一版是错的设计，已重做。** 我新建了 `ExoDecoderKindPolicy` 自己按解码器名前缀分类，但仓库里**早已有等价且更强的实现** —— `PlaybackMediaFactsMapper.decodeModeFact()`：

```java
if (kind == PlayerEngine.DecoderKind.HARDWARE) return ... HARDWARE ...;
if (kind == PlayerEngine.DecoderKind.SOFTWARE) return ... SOFTWARE ...;
String lower = normalize(decoderName);
if (lower.startsWith("omx.google.") || lower.startsWith("c2.android.")
        || lower.contains("ffmpeg") || lower.contains("libgav1")
        || lower.contains("dav1d") || lower.contains("avcodec")) { ... SOFTWARE ... }
```

它先采信**引擎自己上报**的 `PlayerEngine.DecoderKind`（IJK 经 `FFP_PROPV_DECODER_MEDIACODEC`／`AVCODEC`，MPV 经 `hwdec`），只在引擎报 `UNKNOWN` 时才退回名字判定，且 token 比我的多 `dav1d`、`avcodec`。我那句 `if (!isExo()) return configured;` 短路之所以必须存在，恰恰是因为我没用引擎上报 —— 用了就不需要短路。

契约要求「选设计前先识别既有等价或部分实现」，这一条我漏了，属实施流程上的失误。

最终实现：

- 新增 `DecodeLabelPolicy`，**只负责标签决策**，不做分类。判据：`hardwareProfile && actual == DecodeMode.SOFTWARE` 才返回 `硬解→软解`；`UNKNOWN` 与 `null` 一律不下判断（漏报而非误报）。
- `PlayerManager.getActualDecodeMode()` 读 `playbackAutoContextStore.snapshot().media().decoder().videoDecodeMode()`，即已算好的事实。
- 删除 `ExoDecoderKindPolicy` 及其 11 个用例。
- 去掉 Exo 专属短路，**三个内核一并覆盖**。

各内核的取值来源经核对：Exo 在 `ExoPlayerEngine:662` 传 `DecoderKind.UNKNOWN` 但在 `:660` 传了 `analytics.videoDecoderName()`，故走名字分支；IJK 与 MPV 走引擎权威上报。

已知的一处覆盖缩小：既有 token 清单不含 `libvpx`，而被删掉的类里有。作为独立单元补入 `PlaybackMediaFactsMapper`，不在本单元扩范围。

## 接线（不变）

接入点选在 `PlayerManager.getDecodeText()` 这一处漏斗：全部六个控制栏调用点（leanback 的 `CastActivity:175`／`LiveActivity:256`／`VideoActivity:1624`，mobile 的 `LiveActivity:389`／`VideoActivity:1822`，以及 `TmdbDetailActivity:7399`）与 OSD `配置` 行都经由它，改一处即全部一致，无需逐个改动。

`isHardDecode()` **保持只反映配置档**，因为它被用于行为判定（LUT 可用性、回退链等），不能被标签逻辑污染；新增 `isHardProfileRunningSoftware()` 供 UI 使用。

顺带修正两处显示：

- `getSoftDecodeTuneText()` 原先在 `isHardDecode()` 为真时直接返回空，导致硬解回退到软解时看不到降负载状态 —— 而 `E-SP5` 刚让该兜底渲染器启用降负载，恰恰此时最需要确认。改为「配置为硬解且未运行软解」才隐藏。
- 该文案原写「EXO跳帧/滤波/低分辨」，但 `E-SP5` 已把 `skipFrame` 改为 `AVDISCARD_DEFAULT`（不再丢帧），故删去「跳帧」。

## 边界

只改显示与判定，不改任何解码器选路、渲染器构造或回退行为。`isHardDecode()` 的语义未变。

## 验证

- `DecodeLabelPolicyTest`：6 个用例，覆盖仅在硬解档遇软解才报不一致、`UNKNOWN`／`null` 绝不下判断、标签两侧显示、软解文案由调用方注入以保证语言一致、软解文案缺失时退回原标签、无判断时原样透传（保证内核无关）。

### 去短路的副作用：IJK 分支虚假声明（评审发现并已修）

去掉 Exo 专属短路后 `isHardProfileRunningSoftware()` 对 IJK 也可为真，于是 `getSoftDecodeTuneText()` 的 IJK 分支变为可达，输出「软解降负载 IJK跳帧/滤波」。但 IJK 在**硬解档下降负载是明确关闭**的：手动档走 `decode == PlayerEngine.SOFT ? configuredSoftTuneMode() : TuneMode.OFF`（`IjkSimplePlayer:1028-1032`，`IjkDecodePressurePolicy:40-43` 同理），自动档初值也是 `OFF`，须 runtime 见到 `actualDecode == SOFTWARE` 才升档。故在「IJK 硬解档 + 回退 avcodec + 手动降负载档」下，面板会声称生效而实际为 OFF。

修法是**读引擎已应用的档位而非从设置反推**（反推正是产生错误声明的原因）：新增 `PlayerManager.getAppliedIjkTuneMode()` 转发 `IjkPlayerEngine.getAppliedDecodeControlConfig().tuneMode()`，`OFF` 或非 IJK 时显示「软解降负载 关」，否则附上实际档位标签。

MPV 分支经评审确认本就正确：`applySoftDecodeOptions` 刻意不按解码档设限（源码注释说明 MPV 可能在引擎仍表示硬解请求时静默回退），默认 MILD，故硬解回退时降负载确实生效。

### 本地化缺陷（自查发现并已修）

标签取自**本地化**数组 `select_decode`：默认（英文）为 `Soft`／`Hard`，`zh-rCN` 为 `软解`／`硬解`，`zh-rTW` 为 `軟解`／`硬解`。而首版在策略类里硬编码了中文 `"→软解"`，于是英文环境会显示 `Hard→软解`（中英混杂）、繁中会显示 `硬解→软解`（简繁混杂）。

改为由调用方注入软解文案：`decodeLabel(configuredLabel, softLabel, ...)`，`PlayerManager` 从**同一个**本地化数组取 `[PlayerEngine.SOFT]`。策略类因此仍保持纯逻辑、可单测，且两侧文案必然同语言。`softLabel` 为空时退回原标签，不产出残缺的 `硬解→`。
- `FallbackDecodeLabelSyncTest` 仍通过 —— 它以源码字符串锁定各宿主的解码标签必须取引擎实时值，本改动只改实现未移动调用点，且使该值更「实时」。
- `player` 全包 1242 用例，仅 2 个预存 MPV 失败（基线 `3d5165d3c0` 已复现）。
- `compileLeanbackArm64_v8aDebugJavaWithJavac` 与 `compileMobileArm64_v8aDebugJavaWithJavac` 均 `BUILD SUCCESSFUL`。
- 空值安全：`PlaybackAnalyticsListener.snapshot` 为 `volatile` 且初始 `Snapshot.empty()`，永不为 null；内部名字为空时分类器返回 `UNKNOWN`。

验证边界：单测与编译证明判定与接线正确，**不等同于**已在设备上确认文案渲染与截断表现。控制栏按钮宽度有限，`硬解→软解` 是否被截断需实机确认。

## 回滚

恢复本任务的原子提交即可。不涉及依赖锁、AAR、native 二进制或 patch。
