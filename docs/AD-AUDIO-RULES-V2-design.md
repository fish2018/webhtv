# 音频去广告升级设计：频谱指纹社区闭环与语音广告复合规则

> 任务：`AD-AUDIO-RULES-V2`
>
> 状态：Phase 1 已提交且通过 34 项 JVM 测试。Phase 2A 执行/生命周期隔离已通过最终 50 项 JVM 回归与 Leanback arm64 Debug 构建。Phase 2B 的复合规则、识别区间和路由代码已接线，当前仅产生带明确近似标记的确认候选；词级时间对齐、真实 TV 性能/精度、Phase 3/4 仍未完成，不代表整项升级完成。当前交付记录见第 16 节。
>
> 编写日期：2026-09-08（Asia/Shanghai）
>
> 适用版本：WebHTV 当前 `dev2`，基线 `1806e4647bab28f2b257fe87f3b4567b4ff65b36`

## 1. 目标与结论

用户提供的资料实际上包含两条不同的去广告能力，不能合并成一个解析器：

1. **音频频谱指纹规则**：在线 `rules.json`，由采集器上传、云端校验合并，匹配已知广告的解码后 PCM。
2. **语音广告规则**：人可读的“广告之后*>马上回来，30”语法，通过 Sherpa-ONNX 识别文本，再按多句顺序和时间窗口命中。

本项目当前已经具备第一条能力的本地运行时和 Probe v1 兼容解析器，但第二条仍是“平面关键词包含匹配”。本次升级推荐：

- 保留当前 `spectral-sequence-v2` 运行时，不复制采集器 APK，不引入第二条 Exo 音频处理器；
- 继续兼容上游 `ad-audio-probe-rules` v1，使用社区 `rules.json` 作为**独立的指纹来源**；
- 新增独立的语音规则文档/解析/匹配层，把用户提供的 `*`、`>`、`,`、`[pre,post]` 语法转换为有界的语音广告候选；
- 把语音识别计算从播放实时性保护中单独治理：不得在音频回调中执行识别，限制 Sherpa 线程数和后台预算，队列过载时丢弃旧识别输入而不是拖慢 EXO；
- 默认继续关闭语音广告识别和自动跳过；指纹通道、语音通道、HLS/URL 规则互不改变策略和生命周期；
- 第一阶段只使用已验证的本地/内置语音规则，暂不假设在线 `rules.json` 能承载语音文本规则。

**推荐决策：实施“WebHTV 适配版 V2”，而不是盲目接入上游 SDK 或把两种规则强行拼接。**

## 2. 证据基线

### 2.1 本地 WebHTV 基线

| 证据 | 当前事实 | 设计影响 |
|---|---|---|
| `app/src/main/java/com/fongmi/android/tv/ad/audio/ProbeRuleCodec.java` | 严格解析 `ad-audio-probe-rules`、schema 1、`spectral-sequence-v1`，再映射到本地指纹 v2 | 远程指纹规则无需另造格式；必须保持整包拒绝和 revision 单调 |
| `ProbeRuleDownloader.java` | 后台 HTTPS 下载，4 MiB 限制，解析成功后交给 `ProbeRuleStore` 原子替换 | 继续 fail-open；网络更新不能阻塞播放 |
| `ProbeRuleStore.java` | 本地缓存、原子写入、revision 防回滚 | V2 不覆盖现有缓存；新来源必须复用同一安全边界 |
| `PlaybackMediaAudioPipeline.java` / `PlaybackMediaSignalHub.java` | 单一 Exo PCM 管线、多消费者、session/generation 生命周期 | 指纹和语音共享 PCM，不再安装第二个 AudioProcessor |
| `PcmAdAudioSignalProvider.java` | 指纹匹配运行在 Hub consumer 与后台 matcher 中 | 保持指纹通道隔离，规则规模增长前先测量再做索引 |
| `SpeechAdKeywordSet.java` | 逗号/分号分隔的平面关键词，文本包含即命中 | 不能表达顺序句、跨段 wildcard、前后跳转窗口，需要新规则模型 |
| `SpeechAdSignalProvider.java` | Sherpa 会话、PCM 邮箱、recognition callback、候选生成和代际校验已存在 | 新 matcher 应嵌入 Provider，不应绕开现有 Coordinator |
| `RealtimeSubtitleRecognizer.java` | 在线模型最多使用 `min(4, CPU/2)` 个线程，CPU provider；离线模型另有识别线程 | 必须增加 TV 性能预算，否则模型就绪后可能抢占 EXO 资源 |
| `AdAudioRuntimeController.java` | PCM、Probe、Speech 三个 Provider 组合，Coordinator 是唯一 seek authority | 语音候选只能通过现有 policy/coordinator 跳转 |

### 2.2 外部仓库与在线规则

以下版本通过 GitHub API 在 **2026-09-08** 读取，记录完整 revision，避免把网页瞬时内容当作版本基线：

| 仓库/地址 | 读取到的 revision | 观察到的内容 | 可信度与限制 |
|---|---|---|---|
| `https://github.com/0o755/m3u8-ad-audio-probe` | `61c7d2ec57792f8b439254b35cc0c3a9a31acf20` | Probe SDK；公开规则协议为 `ad-audio-probe-rules` schema 1；MIT | 官方仓库 README/公开文档；不直接作为 WebHTV 运行时依赖 |
| `https://github.com/0o755/m3u8-ad-audio-collector` | `070e4ff6f500386d0cfa779c981ba3cb39bf66e7` | 采集器、规则测试、提交 Worker；只调用 Probe 公共 API | 采集端与播放端职责不同；不复制 APK 内部实现 |
| `https://github.com/0o755/m3u8-ad-audio-rules` | `bb0041673883f949045d272b845394a2b518882d` | 云端规则仓库，提交后经 Worker/GitHub Actions 校验、去重、冲突过滤、合并 | 规则仓库无签名；当前只能依靠 HTTPS、严格 schema、大小限制和 revision |
| `https://raw.githubusercontent.com/0o755/m3u8-ad-audio-rules/main/rules.json` | 文件 SHA-256 `d5ab4d42196186221676727b81ddc7474363f76a3dd1360362cf16fc7bea6b6c` | `revision=3`、`schemaVersion=1`、`algorithm=spectral-sequence-v1`、5 条规则；每条有 4 个相位序列 | 通过 GitHub API 获取同一文件内容；不是固定发布包，后续内容会变 |
| `https://m3u8-ad-audio-rules-sync.ccfork.workers.dev/rules.json` | 2026-09-08 网页读取工具返回 `revision=3` | 实际响应头字段为 `ad-audio-probe-rules`、schema 1、`spectral-sequence-v1` | A：直接响应证据；早先本机 curl 的 TLS 失败仍保留为环境问题。网页工具可读取不代表本机或 Android 下载器已通过联网验收 |
| `https://github.com/0o755/m3u8-ad-audio-collector/releases` | 当前 API 未发现正式 latest release | 用户另提供了采集器 APK/Demo 地址 | 不把未验证 APK 当作生产依赖；优先依赖公开源码、合同和规则文件 |

用户提供的 `/run/user/1000/gvfs/smb-share:server=192.168.50.3,share=users/Maple/共享/星落6.0.1.apk` 已于 2026-09-08 做只读文件/ZIP 目录检查：81,613,887 字节、1,722 个 ZIP 条目、存在 `AndroidManifest.xml`，原生库目录仅见 `arm64-v8a`。文件名检索未出现 Sherpa/ONNX/Vosk/Whisper/fingerprint 等显式名称；这**不能证明没有这些能力**，也不能推断广告算法、识别精度或性能。未安装、执行、提取或反编译 APK；其交互行为对照仍未完成，不把它作为实现来源或生产依赖。

### 2.3 用户提供的语音语法

语音规则示例包含以下实际需求：

```text
广告之后*>马上回来，30
中场休息*>广告时间，[2,30]
投注*平台*>注册*彩金，30
```

其含义是：

- `*`：同一句中匹配任意文本，可为空，可跨识别片段；
- `>`：多个句段按顺序出现；
- `,30`：以最后一个句段结束为锚点，向后覆盖 30 秒；
- `[2,30]`：从第一个句段开始前 2 秒，到最后一个句段结束后 30 秒；
- 行尾不需要再写 `*`；空行、`//` 注释和中文标点应被容忍。

这不是当前社区 `rules.json` 的字段，不能塞进 `ProbeRuleCodec` 的 `fingerprints` 数组，也不应把全文规则复制到 `SpeechAdKeywordSet` 的逗号关键词中。

## 3. 用户可见能力

实施后，用户可以得到两条相互独立的能力：

1. **已知音频广告自动/确认跳过**：开启音频指纹后，WebHTV 在后台更新社区指纹规则；播放 EXO VOD 时从解码 PCM 匹配，命中后按现有“提示确认”或“自动跳过”策略处理。
2. **语音广告句式识别**：开启语音广告识别并准备好模型后，识别“广告之后……马上回来”“中场休息……广告时间”等顺序句，按规则指定的前后窗口给出候选；支持用户确认或自动跳过。

两种命中同时发生时，Coordinator 仍只允许一次有效 seek；不能因指纹和语音重复命中而连续跳转或延长跳转窗口。

## 4. 方案比较

### 4.1 方案 A：不改现有实现

保留现有指纹 v1/v2 兼容和语音平面关键词。

- 优点：风险最低、无需新增解析器。
- 缺点：用户提供的顺序语法无法使用；误报和漏报都只能靠增加孤立关键词；模型就绪后的 EXO 性能风险不解决。
- 结论：不能满足本次升级目标，拒绝作为最终方案。

### 4.2 方案 B：原样接入 Probe/采集器 SDK

在 WebHTV 中直接引入 `ad-audio-probe`，同时照搬采集器或 APK 的规则/匹配器。

- 优点：上游更新快，采集器与播放端名义上统一。
- 缺点：Probe 默认 Media3 适配器与 WebHTV 现有 Media3/FFmpeg/Exo 链路有版本和所有权边界；新增一套解码或 PCM 处理会增加包体、网络、生命周期和 CPU；上游指纹协议并不包含语音句法；当前规则仓库无签名；还会引入不可审计的二进制/反编译依赖。
- 结论：拒绝原样接入；只兼容其公开 rules-v1 数据合同和算法黄金样本。

### 4.3 方案 C：WebHTV 适配版（采用）

- 指纹：复用现有 `PlaybackMediaSignalHub`、`PcmAdAudioSignalProvider`、`ProbeRuleCodec/Store/Downloader`；仅增加来源回退、规则统计和规模化前的指标。
- 语音：新增独立 `SpeechAdRuleCodec`、不可变 `SpeechAdRuleSet`、有界 `SpeechAdMatcher`；`SpeechAdSignalProvider` 只负责 PCM/识别会话/代际，候选仍流向现有 multiplexer/policy/coordinator。
- 性能：语音计算使用低优先级、受限线程数和有界邮箱；模型未就绪、过载、识别错误均自动降级，不影响主播放。
- 安全：不把远程文本规则当作可信代码；限制行数、字符数、段数和窗口；禁止任意正则；规则源整包解析，失败保留旧缓存。
- 结论：满足能力目标，改动范围可逆，兼容当前行为和默认关闭契约。

## 5. 规则协议设计

### 5.1 语音规则文档

首期采用纯文本本地文档，后续可封装成 JSON，但不和 Probe JSON 混用：

```text
# speech-ad-rules v1
广告之后*>马上回来，30
中场休息*>广告时间，[2,30]
```

解析规则：

1. 去除 UTF-8 BOM、首尾空白、空行和 `//` 行尾注释；`#` 仅作为文档头或整行注释。
2. 只接受 ASCII/中文逗号 `,`/`，` 的一个动作后缀；没有动作后缀的行拒绝，不静默猜测。
3. 动作 `,post` 映射为 `preRoll=0`、`postRoll=post`；动作 `,[pre,post]` 映射为对应窗口，也接受省略外侧逗号的 `[pre,post]`。规范输出统一使用 ASCII 逗号；多重动作后缀整行拒绝。
4. 主体按 `>` 分成 1～8 个有序句段；每个句段长度限制 128 个 Unicode code point。
5. `*` 只表示通配文本，不表示正则；连续 `*` 合并；主体去除不可识别控制/格式字符，空白折叠。128 code point 上限适用于规范化后的整个句段，而不是每个 wildcard 分隔的字面量。
6. 句段必须包含至少一个字母或数字；全是 `*`、空句段和只有标点的规则拒绝。
7. `preRoll`/`postRoll` 限制在 0～120 秒；首期默认规则最多 30 秒，超过上限拒绝。
8. 单文档最多 256 条规则、总输入 64 KiB、每条最多 8 个句段；超限整份拒绝并保留旧快照。

编译结果使用字面量片段和通配状态机，不调用用户可控正则，避免灾难性回溯。中文匹配使用 NFKC 规范化；拉丁文本保留大小写无关匹配；不保存完整识别文本到日志。

### 5.2 语音匹配状态机

`SpeechAdMatcher` 由单一后台 worker 调用，维护当前 timeline 的有界识别窗口；session/generation 的旧 callback 必须由现有 Provider 先行拒绝，不能由一个 timeline token 替代全部代际校验：

1. 每个识别结果只保留规范化文本、起止时间、timeline token；每次输入最多 4,096 code point，共享窗口最多 128 个结果、8,192 UTF-16 code unit，超限按完整旧结果淘汰，不截断代理字符对；
2. 规则按 `>` 顺序匹配，首字面量即使跨结果也保留原结果的 `firstStartUs`，末字面量保留所属结果的 `lastEndUs`；
3. `*` 可跨增量识别片段，包括空串和换行；窗口从最早结果起点到最新结果终点最长 30 秒，未完成的首句也不得无限保留。Phase 1 只接受已定稿、非重叠、按时间递增的增量结果；重复/重叠结果拒绝，超长或无效输入中断文本连续性。累计 partial/final 去重与修订属于 Phase 2 适配职责，不能把两个累计全文直接拼接；
4. 同一规则在 30 秒冷却窗口内不重复发候选；
5. 命中后输出 `ruleId`、`preRollMs`、`postRollMs`、`firstStartUs`、`lastEndUs` 和 timeline token，不在 Match 中输出原文；
6. seek、切源、音频 flush、引擎重建和规则热更新提升 generation，清空所有状态；旧 callback 丢弃；
7. Provider 的候选保持原始 capture 时间；**只能由现有 `AdSkipCoordinator.targetFor` 使用 `PlaybackMediaClock` 转为媒体时间并做 duration 钳制**。Provider 不能预先加 media anchor，也不能拿媒体总时长钳制 capture 坐标，避免重复转换。无法取得新鲜时钟或目标已过期时不得 seek。

候选区间定义：

```text
captureStart = max(0, firstStartUs / 1000 - preRollMs)
captureEnd   = lastEndUs / 1000 + postRollMs
mediaTarget  = clamp(clock.mapCaptureToMediaMs(captureEnd), 0, mediaDurationMs)
```

若原始区间为空、映射失败、时间轴过期、媒体为直播/不可 seek，或最终 `mediaTarget <= currentPosition`，则丢弃候选并记录固定枚举诊断。Phase 1 的带 duration 辅助方法只在同坐标系下有意义，不能代替既有 Coordinator 的媒体坐标校验。

未知 duration 以负值表示，此时仅计算未钳制上界的候选，不构成自动 seek 授权。Phase 1 的时间来源是**整个识别片段边界**，不是词级对齐结果；不能声称精确定位“关键词后第 30 秒”。Phase 2 必须确认识别器能否提供可靠终点/对齐，不能把回调墙钟或过期播放位置冒充关键词时间。

`[2,30]` 描述候选广告区间，不代表可以撤销已经播放的前 2 秒。在没有预扫描/前瞻缓冲的实时识别中，只能在命中后向有效终点前跳，不得为了“补跳前段”回 seek；本设计不新增预解码或播放延迟。迟到结果、已越过终点或时间不可信的候选不自动执行。

### 5.3 与现有关键词设置的迁移

- 保留 `SpeechAdSetting` 的启用、跳过秒数、模式入口，避免备份和 UI 立即失效。
- 将现有逗号关键词自动转换为单句规则，例如 `赌场` 转为 `赌场，15`；旧用户无需重新配置。
- 新增“语音规则”文本编辑/导入入口；旧“关键词”作为快捷规则区或兼容输入，不再把复杂规则拆成逗号 token。
- 规则来源分为 `builtin`、`user`、`imported`，合并时按稳定 ID 去重；用户规则优先，不自动覆盖用户同 ID 内容。
- 首期不从音频指纹 `rules.json` 推导语音词，也不把识别文本自动写成 URL/HLS/音频指纹规则。

旧关键词的 ASCII 单词边界语义必须继续保留；新规则的字面量包含匹配不能直接替代旧 `SpeechAdKeywordSet`，否则 `ad` 可能误命中 `download`。Phase 1 没有迁移旧设置。

### 5.4 规则误伤、冲突与默认策略

| 用户示例类型 | 设计处置 |
|---|---|
| “广告之后 > 马上回来”等明确过渡句 | 可作为候选模板，但未经误跳样本与时间对齐验收仍默认确认，不直接自动跳 |
| “本片*冠名”“充值*优惠”“品牌*推荐”等单句 | 可能属于正常剧情/讨论，要求用户主动启用 |
| “激情”“私密”“少妇”“美女*主播”“福利*视频”等宽泛词 | 默认不启用；不得把词命中描述成色情内容判断或可靠儿童保护 |
| “下集预告”“精彩花絮” | 属于可选内容跳过，不等同广告，后续 UI 应与广告开关分离 |
| 同一主体配置不同窗口，如 `本片*冠名,25` 与 `本片*冠名,[4,30]` | Phase 1 保留为不同 ID，仅完全相同的规范规则去重；Phase 3 导入需显式展示冲突、由用户选择。不得默默取最长窗口，Provider/Coordinator 接线后仍最多一次有效 seek |

用户提供的完整示例不是经过精度验证的默认库；语法可解析与适合自动跳过是两项独立验收。

## 6. 播放实时性保护

重点防范并验证“模型未就绪时正常、就绪后开启识别导致 EXO 卡顿”的风险。本轮没有执行真实模型/设备对照，不能把线程数或某个调用点认定为已证实根因；“功能能识别”不是充分验收条件，播放实时性是硬约束。

### 6.1 当前风险点

- `RealtimeSubtitleRecognizer.threadCount()` 目前最多配置 4 个 CPU 推理线程；电视盒的可用核心数和调度能力差异很大。
- 语音 Provider 会持续接收 PCM；模型就绪后每帧都可能触发重采样、VAD/解码或识别队列工作。
- Hub consumer 的实时入口即使不做识别，也承担 PCM 引用/复制和投递；必须有明确耗时预算。

### 6.2 适配策略

实施阶段加入以下契约：

1. **音频入口零推理**：Hub/AudioProcessor 回调只做格式检查、有限复制或入有界邮箱；不得创建 Sherpa recognizer、执行 ONNX decode、规则匹配或日志 I/O。
2. **低优先级识别线程**：语音 Provider 使用独立的后台 `Executor`，创建线程时设置 Android background 优先级；不与 UI、Exo 控制线程共用执行器。
3. **线程数上限**：语音广告专用 profile 先使用 1 个推理线程，并完成 1/2 线程 A/B；现有 `threadCount()` 实际已限制为 1～4，问题不是“没有上限”，而是未按播放共存负载验证。实时字幕的原默认配置不随广告 profile 改变；提高广告上限需要 profile 证明无回归。
4. **有界退压**：PCM 和识别任务都有固定上限；过载时丢弃最旧帧并 reset 当前语音识别段，禁止无界积压和反向阻塞音频生产者。
5. **分阶段启用**：模型未验证、播放状态不适合、媒体非 VOD、时钟无效或 Provider backlog 超阈值时，保持 `DEGRADED/IDLE`；不为了“凑完整识别”牺牲播放。
6. **可观测指标**：记录识别耗时桶、队列峰值、丢帧数、recognizer reset 次数和 Provider 状态；禁止记录 PCM、完整文本、URL、Cookie、关键词正文。
7. **用户保护**：TV 设置说明语音识别可能消耗 CPU；如果连续检测到过载或播放 watchdog 风险，本次会话自动停用语音 Provider，但指纹/HLS 去广告继续工作。

### 6.3 性能验收门槛

在至少一台低端 TV 盒和一台高性能 Android TV 上，使用相同 VOD、相同音轨和相同模型完成对照：

- 关闭语音识别为基线，开启“模型未就绪”、开启“模型就绪 + 线程 1”、开启“模型就绪 + 线程 2”四组；
- 比较 `droppedFrames`、video/audio renderer backlog、`ad-audio`/Sherpa 线程 CPU、识别队列峰值和首帧时间；
- 语音开启组不得造成产品允许阈值之外的掉帧或音频 underrun；任一设备不满足时保留“语音默认关闭 + 自动降级”，不得宣称功能完成；
- 先完成性能实测，再决定是否开放语音自动跳过；指纹确认跳过不依赖语音性能门槛。

## 7. 指纹规则接入与社区闭环

### 7.1 保持现有兼容链

当前公开 `rules.json` 已被本地 `ProbeRuleCodec` 兼容：

```text
format       = ad-audio-probe-rules
schemaVersion= 1
revision     = 3
algorithm    = spectral-sequence-v1
rules        = 5
```

因此不实施格式转换、不接入采集器 APK、不安装 Probe 自己的 Media3 播放器。播放侧继续：

```text
PlaybackMediaAudioProcessor
        -> PlaybackMediaSignalHub
        -> PcmAdAudioSignalProvider
        -> AdAudioDetectionMultiplexer
        -> AdSkipPolicyController
        -> AdSkipCoordinator
```

### 7.2 下载与来源策略

短期沿用 Worker 默认地址，增加以下可逆改进：

- Worker 为主，GitHub Raw 仅作为固定官方仓库的显式回退；不对任意用户自定义 URL 自动跨域回退；
- 两个响应都必须通过完全相同的 `ProbeRuleCodec`、大小限制、revision 单调校验和原子 Store；
- 网络失败、TLS 失败、HTTP 非 2xx、schema/算法错误或 revision 回退时保留旧缓存并继续播放；
- 记录当前 source、revision、规则数和错误码，不记录规则正文和媒体请求信息；
- 在签名规则包发布前，不把“HTTPS + schema 合法”描述成真实性证明；自动跳过默认仍关闭。

中期可把远程规则迁移到已有 `SignedRulePackage*`/`SignedProbeRuleSidecar*` 验证链，但必须先完成外部发布者密钥、轮换、撤销和恢复合同；不能只在客户端加一个公钥就宣称闭环安全。

### 7.3 规模与性能

当前 matcher 仍近似全规则扫描。远端当前只有 5 条规则，暂不为了未来数千条规则提前重构。达到以下任一条件后再开独立阶段：

- 规则数超过 256；
- 低端设备每 hop matcher 时间或队列 reset 超过预算；
- 线上规则实测显示全扫描造成可复现的资源竞争。

届时按首帧/前两帧建立候选桶，仍在后台 matcher 线程完成，并保留旧扫描器作为回退实现。

## 8. 分阶段实施计划

### Phase 0：设计与基线（本文件）

- [x] 固定 WebHTV 基线、外部仓库完整 commit ID 和远程规则快照信息。
- [x] 明确指纹 JSON 与语音文本规则不是同一协议。
- [x] 记录“不改、原样接入、WebHTV 适配”三种方案和取舍。
- [x] 记录播放实时性、兼容、安全、回滚和验收门槛。

### Phase 1：语音规则纯 JVM 层

预计变更范围：

```text
app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdRule.java
app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdRuleCodec.java
app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdRuleSet.java
app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdMatcher.java
app/src/test/java/com/fongmi/android/tv/ad/audio/SpeechAdRuleCodecTest.java
app/src/test/java/com/fongmi/android/tv/ad/audio/SpeechAdMatcherTest.java
```

验收：覆盖注释、中文标点、通配、顺序句、前后窗口、跨识别片段、非法/超限输入、冷却和 timeline reset；不依赖 Android、JNI 或真实模型。

### Phase 2：Provider 接线与性能保护

预计变更：

- `SpeechAdSignalProvider`：使用新 matcher、候选时间窗口和有界退压；
- `SpeechAdConfig/Setting`：兼容旧关键词，加入规则来源/性能 profile；
- `RealtimeSubtitleRecognizer`：线程数配置化、TV 默认 1、低优先级和 reset 合同；
- `AdAudioRuntimeController`：独立语音 executor、状态隔离和过载自动降级；
- 现有端到端测试：验证语音候选、指纹候选不会双重 seek。

验收：单测 + JVM 端到端 + 至少一次 Leanback/TV debug 构建；设备性能验证不以编译通过替代。

### Phase 3：规则来源与 UI

- 内置默认语音规则和用户导入/编辑；
- 规则校验错误显示明确原因，不覆盖旧有效规则；
- 音频指纹设置显示来源、revision、规则数量、最后刷新结果；
- 语音设置显示模型状态、规则数量和性能保护状态；
- Mobile/Leanback 入口保持语义一致，TV 焦点导航单独验证。

### Phase 4：受控发布

- 默认：指纹确认模式关闭自动跳过；语音识别关闭；
- 灰度：先允许指纹确认跳过，再根据设备 profile 开放指纹自动跳过；
- 语音：性能门槛通过后仍默认关闭，用户明确打开；自动跳过后置；
- 监测误跳、漏检、掉帧、队列溢出和模型启动失败；发现主播放退化立即关闭语音 Provider，不回滚指纹缓存。

## 9. 明确不做的事情

- 不把人类语音规则写进 `rules.json` 的 `fingerprints` 字段；
- 不把完整识别文本、关键词、媒体 URL、Cookie、Authorization 上传到社区服务；
- 不复制采集器 APK、反编译代码或 Probe 内部类；
- 不在音频回调、UI 线程或播放器控制线程执行 ONNX 推理；
- 不让语音 Provider 直接调用 `seekTo`；
- 不因规则下载失败清空已验证缓存或阻塞主播放；
- 不在没有真实设备 profile 的情况下声称“降低线程优先级即可解决 TV 卡顿”；
- 不将当前无签名远程规则描述成防篡改发布物。

## 10. 风险、回滚与接受标准

### 风险

| 风险 | 防护 |
|---|---|
| 语音模型抢占 CPU 导致 EXO 掉帧 | 线程 1、低优先级、独立 executor、有界退压、过载停用、设备对照测试 |
| 语音规则 wildcard 误报 | 复杂规则默认确认；至少 2 句的规则优先；冷却、时间窗、人工撤销 |
| 规则误跳正常内容 | 指纹与语音独立策略；无签名规则不默认自动跳；规则整包校验和可回退 |
| 远程 Worker/Raw 内容不一致 | revision + 内容摘要 + 相同 codec；只切换到完整验证通过的快照 |
| 规则数量增长导致 matcher 变慢 | 当前小库不提前索引；以 profile 触发单独候选桶阶段 |
| 旧用户设置丢失 | 旧关键词转换为单句规则；保留原 Prefs key 和备份字段语义 |

### 回滚路径

1. 语音规则层：关闭新 matcher feature flag，继续使用旧 `SpeechAdKeywordSet`；
2. Provider 层：停止创建新语音 executor，保留指纹 Provider 和旧语音配置；
3. 模型层：恢复原 Sherpa 线程数/工厂实现；
4. 指纹来源层：恢复到最后一个 `ProbeRuleStore` 有效 revision，不删除 current/previous；
5. 规则文件：新格式解析失败不写入主文件，删除临时文件即可恢复；
6. 若修改跨多个阶段，按 Phase 2 → Phase 1 逆序回退，每阶段独立 commit/tag。

### 完成标准

- 公开指纹 `rules.json` revision 3 的 5 条规则可被现有 codec 解析并进入缓存，网络失败不影响播放；
- 用户示例中的 `*`、`>`、`,30`、`[2,30]` 至少各有正例、负例和跨识别片段测试；
- 语音候选经过 session/generation/timeline、duration 和 seekability 校验；
- 指纹与语音同时命中时最多产生一次有效跳转；
- 模型未就绪、识别异常、队列溢出、规则超限均 fail-open；
- 在目标 TV 设备上，模型就绪开启语音识别不得引入超出门槛的掉帧或 AudioTrack underrun；
- 所有新增规则/设置均可通过单独 feature flag 关闭，旧用户设置和旧缓存可继续读取；
- 设计、实现、验证、提交和恢复 tag 均记录在本文件，不以“编译通过”代替运行时验收。

## 11. 用户决策与当前下一步

- **建议**：实施 Phase 1（语音规则纯 JVM 层）和 Phase 2（Provider 性能保护），保持指纹远程规则协议不变。
- **暂缓**：签名远程规则发布、数千条规则索引、语音规则云端同步，直到发布者合同和真实设备数据齐备。
- **忽略**：直接引入采集器 APK、Probe 默认 Media3 播放器或把语音规则伪装成指纹 JSON。

**唯一下一步**：确认 Phase 2 的 Provider 输入/时间对齐合同、变更范围及真实 TV 性能验收方案后，再启动新的 guard 会话实施；不同时改 UI、下载器和原生依赖。

## 12. Phase 1 实施与验证记录（2026-09-08）

### 范围与完成情况

- 分支 `dev2`；Phase 1 基线为设计提交 `045ae26ab2374264f72dd4c266eca2f1f2dfc5e9`，设计恢复标签为 `recovery/AD-AUDIO-RULES-V2/20260908130147-045ae26ab237`。
- 延续原 `AD-AUDIO-RULES-V2`、`standard` guard 会话；初始受保护脏路径为空，交接时四个未跟踪规则类属于该会话，不重新归属其他任务。
- 新增 `SpeechAdRule`、`SpeechAdRuleCodec`、`SpeechAdRuleSet`、`SpeechAdMatcher` 及两个对应测试类；仅这六个文件与本文档属于本阶段范围。
- 不执行用户正则；规范文本产生稳定 ID，集合不可变；程序构造同样约束动作秒粒度，序列化输出不得突破文档大小上限。
- 未修改 Provider、旧关键词设置、UI、指纹下载/缓存、依赖、JNI 或原生库；没有新增运行时默认开关或实际 seek。回滚不需要清理用户配置。

### 决定性验证

最终使用本机 `javac` 编译全部四个新类与两个测试类，随后运行缓存中的 JUnit 4.13.2：

```text
org.junit.runner.JUnitCore
  com.fongmi.android.tv.ad.audio.SpeechAdRuleCodecTest
  com.fongmi.android.tv.ad.audio.SpeechAdMatcherTest
结果：OK (34 tests)，0 failures
```

- 覆盖 BOM/注释、中英文逗号、两个动作格式、规范化与 round-trip、重复/连续 wildcard、空串/换行与顺序匹配、非法多重后缀、规则数/文档字节/整句长度上限、不可变集合、时间窗/冷却/重叠回调/timeline reset、跨首句时间来源与 duration 钳制。
- 新增边界回归用例先在旧实现复现 9 个失败，再于修正后全部通过；不是只更改断言使旧实现变绿。
- 早先 `:app:testDebugUnitTest` 因缺少 flavor 任务失败，随后 Mobile arm64 定向 Gradle 执行完成主/测试源码编译，但发现两处错误的 duration 断言；它们已修正，最终 JVM 测试覆盖了修订实现。**不把先前 Gradle 失败写成成功，不声称最终源码经过 APK/设备验收**。本阶段没有 Android API/依赖改动，直接 JVM 编译和相同 JUnit 用例是本阶段规则合同的最终门槛。
- 完整最终输出保存在本机 `/tmp/AD-AUDIO-RULES-V2-green.O8b3vf/test.log`；临时日志不是持久验收的唯一来源，测试命令、范围和结论在本文与提交的 Verification 字段中保留。

### 尚未完成的验证与设计门槛

- 真实 ASR 定稿/累计结果行为、词级或片段终点的可信度及媒体时钟换算；未解决前不得用新语音规则自动 seek。
- 目标 TV 的 CPU、视频丢帧、AudioTrack underrun、队列丢弃与模型启动对照；低优先级和单线程只是待测方案，不是性能修复证明。
- 指纹/语音同时命中后的唯一 seek、网络失败缓存保留和所有 UI/迁移交互，仍属于后续接线阶段。
- 现有源码/规则直接证据与本地 JVM 反例测试足以限定 Phase 1 的语法合同，但**不等于整项最佳实践评审已完成**。Phase 2 前还需补齐其官方识别器/平台调度资料、相关上游 issue/回退讨论、成熟项目实现，以及目标设备测量；论文或外部 benchmark 若不适用于本地设备，必须记录不适用原因而不照搬性能结论。
- 星落 APK 仅完成只读包目录对照，实际交互、算法和性能均未验证。

### 提交与回滚

本节与六个新源码/测试文件原子提交，guard 的提交 Verification 字段记录最终测试结果，并立即创建唯一的 `recovery/AD-AUDIO-RULES-V2/<timestamp>-<commit>` 注释标签；不以另一次文档提交追写自身 hash。可用 `git log -1 -- app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdMatcher.java` 定位本阶段实现提交，其恢复标签由该提交的本地 annotated tag 标识。

回滚本阶段实现提交即可移除未接线的规则层，保留设计基线与现有播放行为；后续若已经接线，必须先回滚接线阶段再回滚本阶段，不重写历史或移动已发布标签。

## 13. Phase 2 决策补充：执行隔离、时间合同与验收

### 13.1 本轮权限与冻结基线

- 本轮仅更新本文件，不修改运行时代码或依赖；guard 单元为 `AD-AUDIO-RULES-V2-P2-DESIGN`。接续时工作区干净，分支 `dev2`，HEAD 为 `fa8f8959b17dd923397776fa9492152da232a819`。
- 不重新实施 2026-08-18 的旧关键词 Provider 计划；该计划已存在实现。参考 `docs/superpowers/specs/2026-08-18-speech-ad-keyword-provider-design.md` 时，以当前代码和后续修复为准。
- 必须保留 `532be2d7ed5b1562c36d6d85b824d5072d51b849`（语音链路修复）、`9355ea530e467c27d6fb28413c2cf6cb471ce729`（timeline reset 保留会话）及 `4cf2f76e2dea95f0ba69baeb66258d5b1a04035f`（原 Provider）已形成的行为。它们是本地保护基线，不是本轮 cherry-pick 候选。
- Java 制品为 `app/libs/sherpa-onnx-v1.13.4.jar`，SHA-256 为 `c529915aa0c56213678065ad47f3d19c39564555c3a6d95bdbb79e8af82b88fe`；`javap` 确认 Result 有 text/tokens/timestamps/ysProbs，ModelConfig Builder 有 `setNumThreads`，没有通用 ORT session/spinning 配置入口。此检查不证明配套原生库的构建来源。
- Sherpa `v1.13.4` 官方源码固定为 `142807252687d81b40d6315f23470a1512a00de3`；Media3 保持目录中的 `1.11.0-alpha01-fongmi`。**不升级任何 JAR/AAR/SO，不改 JNI，不引入 Vosk**。

### 13.2 当前调用链的具体事实

以下定位均对应上述本地 HEAD；风险是代码推导，尚未在 TV 上复现根因。

| 路径 / 符号 | 已观察事实 | 决策影响 |
|---|---|---|
| `subtitle/RealtimeSubtitleRecognizer.java:147-174`，`acceptStreaming` / `emitStreamingResult` | 只在 endpoint 或 4.8 秒自适应边界发结果；随后 reset。不是每个 PCM 帧都发累计 partial。终点有末 token 时间加 0.32 秒的估算 | 不先写一个复杂的通用 partial 去重框架；透传完整区间，但不得将估算宣称词级精确度 |
| 同文件 `acceptOffline` / `recognizeLoop` / `recognize`（177-230） | VAD 片段按采样数计算起止，后台队列容量 3；队列积压时只保留最新片段，省略较旧识别段 | 被省略的时间不能与后续文本静默拼接成跨句命中；广告 profile 需要连续性标识/重置，不能直接改变字幕现有策略 |
| `subtitle/SpeechRecognitionFactory.java` 与 `RealtimeSubtitleSpeechRecognitionFactory.java:46-80` | 门面和适配器均透传 text/startUs/endUs/token；Session 本身没有并发安全保证 | 复用现有门面，不新建另一套识别 SDK；native 会话必须有单一串行所有者 |
| `ad/audio/SpeechAdSignalProvider.java:308-314`，`recognitionListener` | Provider 转发时丢弃 `endUs` | 2B 恢复终点传递，不能用旧 HostPosition 或回调墙钟补齐 |
| 同文件 `activateLocked`（285-289）、`drainMailbox`（429-445）、`closeRecognitionSessionLocked`（634 起） | 初始化、accept、reset/close 进入 Provider 锁域；Hub 以 `DIRECT_EXECUTOR` 调用 consumer | 慢推理可让音频入队等待 Provider 锁；禁止仅“换成低优先级”后宣布阻塞消失 |
| `player/audio/PlaybackMediaSignalHub.java:247-295` | mailbox 内调用 executor；DIRECT_EXECUTOR 会在调用线程执行 drain，consumer 本身虽不在 drain 的局部锁块内，但外侧 offer/schedule 调用尚未退出 | 不把这个注册方式误认为天然异步。关闭注册与 Provider 锁还可能形成反向获取关系，入口和清理需要解耦 |
| `RealtimeSubtitleRecognizer.release`（123-144）及 `recognize`（219-230） | release 等待 `recognitionFuture.get()`；离线结果回调又可进入 Provider 锁 | 持 Provider 锁等待 native worker 可能产生循环等待；不使用超时后直接释放 native 指针作为“修复” |
| `ad/audio/AdAudioRuntimeController.java:387,390-398,498-504,566-573` | PCM 指纹与 Speech 使用同一 worker，路由白名单只加入固定 `speech-keyword` ID | 2A 分离 worker；2B 同步更新规则 ID 白名单与配置版本，否则新 matcher 命中也会被丢弃 |
| `ad/audio/AdSkipCoordinator.targetFor`（288-307）与 `player/audio/PlaybackMediaClock.Snapshot.mapCaptureToMediaMs` | Coordinator 对 captureEnd 加 media anchor、验证 fresh/generation、钳制 duration，并拒绝已经过去的目标 | 唯一坐标转换与 seek authority 保持不动；修正本文件早先可能导致重复转换的描述 |
| `SpeechAdSignalProviderTest.candidateUsesCaptureTimeAndLeavesDurationClampingToTheCoordinator` | 现有测试明确要求 Provider 不用 media duration 钳制 capture 区间 | 这是保护合同，不把该用例当作旧预期删除 |

同时保留缓冲期间 park 而不销毁模型、seek/flush 后同会话 reset 并拒绝旧回调、模型未就绪 fail-open、旧 ASCII 单词边界和指纹独立策略。

### 13.3 最佳实践证据记录

访问日期统一为 **2026-09-08，Asia/Shanghai**。A 为当前接口/源码直接证据；B 为维护者对特定问题的解释；C 为未经本地复现的外部报告。GitHub 内容经 agent-reach 的 `gh api` 路由读取；本机 `agent-reach` 可执行程序不可用。官方网页由网页读取工具取得，没有修改代理配置。

| 证据类别 | 来源、revision 与已读位置 | 等级 / 支持的判断 / 局限与影响 |
|---|---|---|
| 精确上游源码 | `https://github.com/k2-fsa/sherpa-onnx/blob/142807252687d81b40d6315f23470a1512a00de3/sherpa-onnx/java-api/src/main/java/com/k2fsa/sherpa/onnx/OnlineRecognizerResult.java` 与 `OnlineModelConfig.java` | A：Java 公开 token 时间戳和线程数，不公开完整词起止或任意 ORT 配置；采用已有 API，不为本阶段新增 JNI 选项 |
| 精确上游实现 | 同 revision 的 `sherpa-onnx/csrc/online-recognizer-transducer-impl.h`，`Convert` / `GetResult` / `Reset`；`online-transducer-greedy-search-decoder.cc` | A：token 时间来自解码帧，另有 segment/start_time；Java Result 没有 start_time 字段；重置后时间对齐仍须多片段样本验证，不能仅按最新 PCM 末尾推断 |
| 官方运行时文档 | `https://onnxruntime.ai/docs/performance/tune-performance/threading.html`，thread management/intra-op/spinning | A：intra-op=1 不创建额外 intra-op worker；旋转等待、线程池和多 Session 有 CPU/延迟权衡。官方建议不能证明 WebHTV 总线程数为 1，也不能通过未暴露的 Java API“关闭全部 spinning”；只设置可用的线程预算并测量 |
| 官方平台文档 | `https://developer.android.com/topic/performance/threads`，thread priority | A：后台线程仍与渲染线程争 CPU；Android Process 优先级作用于调度，Java Thread 优先级不能作为整套 native 线程的资源隔离证明。仅广告执行入口设 background，音频/视频线程不降级 |
| 上游维护者讨论 | `https://github.com/k2-fsa/sherpa-onnx/issues/982#issuecomment-2160185844`，2024-06-11；关联 PR #989 | B：该维护者说明针对 CTC 的 token 时间性质有限。不能推广为所有模型都有完整词区间；只用其支持“模型相关、必须验收”的边界，不移植该历史 PR |
| 上游现场性能报告 | `https://github.com/k2-fsa/sherpa-onnx/issues/2151` 及 2025-12-16 维护者追问 | C：报告多实例/多线程下的性能下降，但维护者仍要求完整代码；没有可移植 TV 结论。拒绝据此做全局线程池/亲和性/native 重构 |
| 成熟 Android 应用源码 | Sherpa 同 revision 的 `android/SherpaOnnxVadAsr/app/src/main/java/com/k2fsa/sherpa/onnx/MainActivity.kt` | A：示例有后台录音/识别与流释放流程，可对照生命周期；它是麦克风应用，不证明 Exo PCM 回调可阻塞，也不授权增加麦克风权限 |
| 独立项目对照 | `https://github.com/alphacep/vosk-android-demo/blob/a5e58ec399135f78325bd3e289849fc3fb57ce96/app/src/main/java/org/vosk/demo/VoskActivity.java`，官方 Android demo | A（边界对照）：其活动/识别服务关系与 WebHTV 播放音轨旁路不同，只作 API/生命周期比较；不照搬 SDK、录音入口或性能结论。旧星落评估只对应 5.8.5，不冒充 6.0.1 证据 |
| 测试/测量与论文适用性 | 当前本地 `SpeechAdSignalProviderTest` / `SpeechAdRuntimeEndToEndTest`；外部现场报告如上 | 本轮阅读旧回归合同而不重复测试。本阶段不改变模型/识别算法，新的算法论文不决定锁所有权设计；没有同款 TV 上可迁移的公开 benchmark，所以实际性能仍是设备门槛，不用论文/博客替代。本项目定向并发测试与实际设备对照才决定是否放行 |

以上覆盖了源码、官方文档、issue/维护者、相关应用和现场报告；不声称已完成真实设备性能证明。临时源码/JSON 保存于 `/tmp/AD-AUDIO-RULES-V2-phase2-evidence/`，持久结论和固定 revision 以本节为准。当前证据已能决定 2A 设计，不再为同一问题扩大搜索。

### 13.4 方案比较与推荐

| 方案 | 正确性/兼容性 | 性能/生命周期 | 结论 |
|---|---|---|---|
| 不改 | 旧关键词可继续使用，但新规则仍无入口 | 共享 worker 与锁等待风险不变 | 保留为回滚基线，不代表满足升级目标 |
| 原样照搬采集器/其他 ASR demo | 引入另一音轨/麦克风、SDK 或模型所有权；不解决 WebHTV capture 坐标与旧修复 | 新增 CPU、内存、包体和 native 生命周期；平台演示不是 TV 播放证明 | 拒绝 |
| 只设 numThreads=1 或只调优先级 | 不解决丢终点/新 ID 路由，也不消除持锁等待 | 可能降低竞争，仍可阻塞或死锁；降线程也可能扩大积压 | 不作为完整修复 |
| WebHTV 适配：先 2A 执行隔离，再 2B 时间与规则接线 | 保留旧配置/字幕路径、capture 合同、单 Coordinator；逐阶段验证 | 单独 native owner、有界消息、不持控制锁推理；可独立回滚，不改二进制 | **推荐** |

#### Phase 2A：执行与生命周期隔离（本次实现单元）

1. 创建**语音广告专用串行 owner worker**，不复用 PCM 指纹 worker；所有模型 create、Session accept/reset/close 由该 owner 串行处理。字幕默认创建入口和现有线程策略不变，广告工厂显式传入受限 profile。
2. Host/Hub 回调只更新可见 token/状态或进入有界输入队列；无模型校验、初始化、推理、文件/日志 I/O、Future 等待。不能只把 `recognitionSession.accept` 移出 synchronized 后允许 close 并发释放指针。
3. 控制状态锁不跨 native 调用；识别回调只投递带 instance/session/generation/timeline 的有界结果事件，不等待主线程或 Provider 锁。reset/close 先逻辑失效 token，再由 owner 执行物理 reset/release；释放未完成时不启动第二个替代会话。
4. owner 的唤醒任务合并，PCM/结果均有固定容量；生命周期命令优先于旧 PCM。丢帧/丢识别段标记不连续并 reset 本段，不能跨缺口拼词。对于正在 native decode 的会话，超时只诊断/停用，不并行 free，不伪造“已经释放”。
5. 广告 profile 从 numThreads=1 开始；Android background priority 仅在新广告 worker 内设置。不启用 native affinity、全局 ORT 线程池或新的 JNI 开关。新增固定枚举/计数/耗时诊断，不记录文本、关键词、PCM 或媒体 URL。
6. 保留旧关键词规则、模式、时长与 Prefs；暂不启用新复合规则。新增慢 create/accept/close 和迟到回调的并发测试，先证明音频入口/指纹工作不必等待 ASR，再做设备共存测试。

拟批准路径（不是本轮实际修改授权）：

```text
app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdSignalProvider.java
app/src/main/java/com/fongmi/android/tv/ad/audio/AdAudioRuntimeController.java
app/src/main/java/com/fongmi/android/tv/ad/audio/AdAudioDiagnostics.java
app/src/main/java/com/fongmi/android/tv/subtitle/RealtimeSubtitleSpeechRecognitionFactory.java
app/src/main/java/com/fongmi/android/tv/subtitle/RealtimeSubtitleRecognizer.java
app/src/test/java/com/fongmi/android/tv/ad/audio/SpeechAdSignalProviderTest.java
app/src/test/java/com/fongmi/android/tv/ad/audio/AdAudioRuntimeControllerTest.java
app/src/test/java/com/fongmi/android/tv/ad/audio/SpeechAdRuntimeEndToEndTest.java
app/src/test/java/com/fongmi/android/tv/subtitle/RealtimeSubtitleRecognizerTest.java
docs/AD-AUDIO-RULES-V2-design.md
```

实现若确需另增 owner/配置类或公共接口，先列出最小路径与合同变动，不能悄悄扩大此集合。既有实时字幕回归不可删除或改成永远不验证；只为广告新增 profile，并维持原调用重载的语义。

#### Phase 2B：新规则、时间精度和唯一跳转

- 透传真实识别区间并接入 `SpeechAdMatcher`；保留旧关键词集合，不把 ASCII 单词边界降为任意子串。新规则快照与规则 ID 白名单、路由 version 同步更新，旧 callback 不能使用新快照。
- Phase 1 的整结果起止无法定位结果中间的关键字：后续需加入匹配字符范围和可选 token 对齐数据，保留旧调用重载。NFKC/BPE/字节回退或 tokens/timestamps 长度不匹配时不能逐项硬配；无可靠边界只作明确标识的近似候选，不自动执行。
- `+0.32 秒` 与 VAD 整段终点都不是词边界真值。自动路径必须在合法标注音频上量化“末关键词结束 + post”误差；建议先冻结 **500 ms 最大额外时间误差**作为待批准门槛，超过或无法校准不启用该模型的自动路径。不能把停用自动路径当作整项任务完成，必须完成至少一个目标模型/设备的自动场景验收。
- 只产生 capture 坐标候选；沿用 multiplexer → policy → coordinator 处理最终 seek。至少覆盖从非零 media anchor 开始、seek 后重建时间轴、片尾钳制、迟到结果、指纹/语音同时命中和冲突规则最多一次有效跳转。
- Phase 2B 需要补充批准 matcher/配置/路由的具体范围；UI、导入冲突展示和远程来源仍属于 Phase 3，不并入 2A。

### 13.5 验证、资源与发布门槛

**决定性 JVM 回归**：使用可阻塞的 fake recognizer 和 latch/barrier，而不是 sleep 猜时序。慢 ASR 时 Hub 发布和独立指纹任务可完成；close/reset 立即使 token 失效且 native 不并发释放；积压丢弃后不跨空洞命中；配置替换只接受当前 ID；保留既有 capture/duration、park、同 Session reset、prompt/auto/undo 测试。真实模型不适合用 JVM stub 证明。

**最小构建**：一次 `bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest` 的定向相关测试加 Leanback arm64 debug 构建；除相关编辑/不确定失败外不重跑。不为本阶段重建 FFmpeg/Media3/JNI/所有 ABI。共享字幕 factory 的 Android 调用/生命周期由相关测试和这一构建覆盖，不据此声称所有设备均兼容。

**真实 TV 对照**（批准后先冻结样本/阈值，再看结果）：

- 同一设备、OS、APK hash、模型 hash、媒体与解码器、输出/倍速、网络和温度条件；每组先预热，再至少 3 次同长度观测，报告中位数、p95 与离散范围。包括关闭语音、模型未就绪、模型就绪线程 1/2，以及语音+实时字幕同时开启。
- 复用 `PlaybackAnalyticsListener.onDroppedVideoFrames` 的累计计数，以及已有 `onAudioUnderrun` 调试日志；后者当前受 `SpiderDebug` 开关约束，必须两组使用相同开关。不能用 UI FPS 代替视频解码丢帧；不另建一套播放器统计体系。
- 建议门槛：无新增 ANR/音频 underrun/死锁；音频投递 p99 ≤1 ms 且单次不超过 5 ms；5 分钟样本新增视频丢帧不超过 1 帧；额外启动/seek p95 ≤50 ms；目标设备内 ASR 实时系数 <1、队列不持续增长。以上是**待批准的产品门槛，不是行业标准或已测结果**；方差跨越门槛则继续取证，不只取最快一轮。
- 降级只能作为运行时安全保护。若正常目标负载下持续停用识别，说明功能/性能验收未通过，不能按“主播放不再卡”宣布完成。

**设备事实**：2026-09-08 13:40 左右 `adb devices -l` 有 `192.168.50.3:5555/5557/5559/5561` 四个在线端点，显示型号分别为 LIO_AN00、SM_N9700、V1923A、HD1910；没有指定哪个是目标 TV，也没有测试本轮模型。不能凭“ADB 在线”证明有合格 TV 环境。本轮未安装、改设置或播放这些设备；目标 TV 与合法标注音频需在设备验收前落实，不阻止 2A 的代码/并发验证准备。

**资源/包体/回滚**：只新增广告专用 Java worker/profile 与有界队列，不引入第二条 PCM 管线、麦克风、依赖或 native 制品；精确 APK 增量在构建后记录，不能宣称为零。2A、2B 分别原子提交并打恢复标签；回滚先 2B 后 2A，保留用户关键词、缓存、字幕默认行为。功能 flag 默认关闭不等于通过回归；任何 material regression 未解决时不得发布/宣称整项完成。

## 14. 设计补充与恢复校准（2026-09-08）

> 历史说明：14.1/14.3 保留较早恢复时的草稿状态，不是当前交付状态；当前 guard 已覆盖接口及 2A 实现路径，以第 15 节为准。14.2 的安全与兼容约束仍适用。

### 14.1 已提交设计与未提交草稿分开记录（历史）

本轮恢复检查时，分支为 `dev2`，HEAD 为 Phase 2 设计提交 `411171eadfe07906b912210f5a669711f4476c11`。当前请求仍是“先输出更新相关设计文档”，上一版恢复锚点也明确要求先取得实施确认；交接摘要中的“正在实施”只说明存在草稿，不能替代阶段批准。本轮不继续代码实施、不撤销草稿、不扩大 guard 范围、不构建或安装 APK。

当前已有 active guard `AD-AUDIO-RULES-V2-P2A`，基线与上述 HEAD 相同，其初始 dirty/protected 清单为空。本轮恢复时发现以下未提交修改，均按既有工作保留：

| 路径（相对 `app/src/main/java/com/fongmi/android/tv/`） | 草稿内容及当前限制 |
|---|---|
| `ad/audio/AdAudioRuntimeController.java` | 已增加独立 speech worker，但生产关闭路径仍使用 `shutdownNow`；尚未证明异步 Session 释放不会被丢弃 |
| `subtitle/RealtimeSubtitleRecognizer.java` | 已增加广告 profile/单线程预算；离线 worker 的单参数 `Process.setThreadPriority` 位于线程工厂调用体，而非新线程 Runnable 内，不能视为正确的新线程降优先级实现 |
| `subtitle/RealtimeSubtitleSpeechRecognitionFactory.java` | 已增加带 profile 的创建入口；依赖下行接口草稿，尚未编译或验证默认字幕路径兼容性 |
| `subtitle/SpeechRecognitionFactory.java` | 已增加 `ExecutionProfile` 与默认创建重载，但**不在当前 guard scope 内**；不得直接提交，也不得仅修改 guard 内部状态使其放行 |

`SpeechAdSignalProvider.java` 尚未修改：模型创建及 `accept/reset/close` 的锁内执行问题仍在。新增 worker/profile 不等于已经完成执行隔离。第 13.5 节要求的并发测试、Leanback 构建及 TV 性能验收均未对这组草稿执行。

### 14.2 补充到 Phase 2A 的实现与验收约束

1. **接口范围先闭合**：优先评估在已声明的具体工厂和 Recognizer 路径内适配广告 profile，保持字幕默认入口与现有 `SpeechRecognitionFactory` 合同不变。确需修改接口时，须先明确批准该路径及兼容策略，再安全隔离并纳入任务；不能把已有越界草稿反推为授权。
2. **释放任务必须执行，而不只是入队**：关闭先停止新输入并使 token 失效，再由同一串行 owner 完成 Session 释放。关闭线程池的顺序必须保证最终 close 命令仍可执行；不得在排入 close 后直接 `shutdownNow()` 丢弃它，也不得在主线程等待 native 推理结束。正常关闭优先采用能保留已提交清理任务的有序停止；超时只能记录未完成状态，不能并发 free。测试须验证慢 accept 返回后 close 恰好执行一次、旧回调无效、worker 最终终止；配置替换还须验证旧 close 在新 create 之前完成。
3. **只降低实际广告线程的优先级**：单参数 `Process.setThreadPriority` 应在新 worker 的 Runnable 开始执行时调用，不能写在线程工厂返回 Thread 前的调用体中。离线 ASR 的 Java 子线程须在自己的执行入口设置，不能假定继承父线程的 Android 调度优先级；保留字幕及音视频线程默认策略。静态/profile 单测之外，真实 Android 验收要观察执行线程，而不是仅断言 Java `Thread.getPriority()`。
4. **验证边界不变**：保留第 13.5 节的慢 create/accept/close、reset、迟到回调、PCM 缺口、指纹并行和 prompt/auto/undo 场景。规则接线、词时间对齐、UI、远程语音规则以及任何新 native 依赖不并入 2A。通过 JVM/构建只能说明相应代码门槛通过，不能替代 TV 播放性能或完整自动跳过验收。

### 14.3 本轮增量证据与交付边界（历史）

仅核对上述两个草稿风险对应的官方 API，不重复第 13.3 节已完成的上游调研：

| 来源 / revision / 访问日期 | 等级、支持事实与决策影响 |
|---|---|
| `https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html`；Java SE 17 API；2026-09-08 | A：`shutdown()` 允许已提交任务继续执行，`shutdownNow()` 阻止等待任务开始并尝试中断执行中任务。支持“有序保留释放任务”的设计，不保证 native 推理响应中断，也不证明当前草稿能够安全终止 |
| `https://developer.android.com/reference/android/os/Process.html`；`setThreadPriority(int)` / `setThreadPriority(int,int)`，API level 1；2026-09-08 | A：单参数版本作用于调用线程，Android 优先级不由 Java 子线程自动继承。支持把设置放入各广告 worker 执行入口；不证明 native 线程预算或 TV 性能已达标 |

agent-reach 的 Jina Reader 请求遇到 TLS EOF；本轮改由网页读取工具读取上述官方正文。该网络失败不记作源码/测试失败，不据此重跑构建。

本次只对本文作增量更新，源码草稿保留。交付检查限于本文 diff/结构及四个草稿的 SHA-256 保全；不重跑 Phase 1 测试。当前 guard 同时包含未验证代码和一个越界文件，不能形成安全的整体完成提交，因此不调用 `finish`、不创建新 commit/tag；不以绕过 guard 的单独文档提交掩盖该状态。

## 15. Phase 2A 实现与代码交付（2026-09-08）

### 15.1 实际范围与实现决策

沿用 `AD-AUDIO-RULES-V2-P2A` / `standard` guard，分支 `dev2`，基线 `411171eadfe07906b912210f5a669711f4476c11`。初始 dirty/staged/protected 均为空，11 个允许路径内的代码与本文作为同一逻辑单元收尾。

实际改动：`ad/audio/AdAudioDiagnostics.java`、`AdAudioRuntimeController.java`、`SpeechAdSignalProvider.java`；`subtitle/SpeechRecognitionFactory.java`、`RealtimeSubtitleSpeechRecognitionFactory.java`、`RealtimeSubtitleRecognizer.java`；三个对应测试文件与本文。`SpeechAdRuntimeEndToEndTest.java` 在范围内但仅执行、未修改。没有修改 `PlayerManager`、Hub、UI、规则配置、依赖锁或 native 制品。

| 合同 / 位置 | 最终实现与取舍 |
|---|---|
| `AdAudioRuntimeController.Workers/close` | 指纹 analysis 与 speech owner 使用独立 executor；生产 speech worker 异步串行、无 caller-runs fallback。先令 Provider 失效并交接清理任务，再 `shutdown()` 保留已接收任务，不在宿主等待 native。 |
| `SpeechAdSignalProvider` 控制锁 | monitor 只保护状态和有界邮箱；模型检查/创建、resample、识别/reset、匹配、Hub 注册/释放和 native close 在锁外。生产模型生命周期由 speech owner 执行，Hub 继续轻量同步校验/有界复制/投递，不增加第二条 PCM 管线。 |
| `dispatchOwner/runOwnerCommand` | 合并 wakeup 循环排空命令，PCM/结果间优先 lifecycle，不依靠关停后重新 submit。独立 `submissionLock` 覆盖 scheduled 标记到 executor 接收；重复 close 也必须完成交接后才能返回。生产该锁不覆盖 native 执行；所有 dispatch 调用须在 Provider monitor 外，不能在提交锁外加 scheduled 快路径。 |
| 输入/结果边界 | 默认 PCM 邮箱 16 帧，单帧最多 192000 samples，采样率 8000–192000 Hz，拒绝空、非法时间或超限输入。结果/错误队列最多 8 项，文本最多 8192 字符。溢出立即更新 timeline token、使在途旧结果失效；合并 reset 后才接收保留的最新有界尾部，不能跨缺口拼词。 |
| seek/park/close | 同媒体 seek 保留实例并 reset；无丢帧的缓冲只 park，实际丢弃 PCM 才断开片段。关闭先失效 token/摘下引用，再由 owner 锁外清理 Hub lease/registration 和 Session。过时创建结果在 owner 关闭；reset 失败停用实例，不继续拼接旧上下文。 |
| factory/profile | 增加兼容的 `create(listener, profile)` 默认重载，旧 factory 可继续实现旧入口。广告在线/离线模型线程预算为 1，字幕默认入口及 `max(1,min(4,CPU/2))` 预算不变。广告 owner 和离线 worker 在各自 Runnable 入口设 Android background priority；不等同于全进程只有一个线程。 |
| `RealtimeSubtitleRecognizer.release` | 停止新任务后等待识别 executor 真正退出，再释放 native；被中断仍等待，完成后恢复 interrupt 标记。广告路径由 owner 等待且不持 Provider monitor。native 永不返回时仍只能保留待关闭状态，未实现强杀/超时后并发 free。 |
| diagnostics/旧行为 | speech 热路径仅内存计数，不写日志、不记录文本/URL。增加丢帧、queue peak、reset、close pending/closed、拒绝和三个 accept 耗时桶；peak 是高水位不是次数。候选仍用旧关键词 ID、capture 起点和 skipSeconds，Coordinator 保持唯一映射/seek 权限及 duration 钳制，prompt/auto/undo 不变。 |

executor 拒绝时降级并使 token 失效，保留未清理引用，不在调用线程 fallback native free，不声称资源已关闭。生产 Runtime 的有序关停不应触发此拒绝，回归覆盖该提交顺序。

### 15.2 审查与修复

- **有效并已修复**：早期存在 Hub 清理锁反转、锁内日志 I/O、溢出未立即失效在途结果、递归 submit 在关停后丢清理、中断等待后提前 free 等风险，分别以锁外 owner 清理、内存 diagnostics、timeline token、单 wakeup drain 和真实终止等待修正，并补并发用例。
- **独立审查发现并修复**：`ownerTaskScheduled=true` 到实际 `execute()` 之间的提交窗口，可能使新 create 越过旧 close，或让 shutdown 后清理被拒绝。追加提交交接锁和重复 close 屏障；`closeFinishesPendingSubmissionBeforeTheRuntimeCanShutdownTheOwner` 固定该窗口。按生产异步 executor 前提复核未发现新的确定反例。未调用跨模型/外部代理 CLI。
- **保留的取舍**：仅开 speech 时，模型检查前无 capture，首次 `needsPipelineRebuild()` 为 false；创建命令在 native create 前取得 capture，后续宿主 refresh 可发现重建需求。只读核对 `PlayerManager.refreshAdAudioRuntime`、telemetry pump 及约 5 秒调度路径；不是立即唤醒或严格 5 秒保证，不改宿主策略。
- **覆盖边界**：新增 Runtime/Hub 测试重放首次 refresh、阻塞检查/创建、capture、管线 attach/代际 reset 和 PCM 到达；另覆盖慢 close 先于替换 create、Runtime shutdown 保留最终 cleanup。这是 fake recognizer/Hub 租约测试，不是实际 Android PlayerManager/音频设备端到端验收。

早期 35/37/49 项通过均属不同草稿，不代表最终补丁。首次 49 项通过后因提交竞态作了相关修复，才重跑相同门槛，不是对未改变版本重复验证。最后一次增量读取 ExecutorService 页面时 Jina 返回 HTTP 401、网页读取未返回可用正文，不记作新增证据；沿用第 13.3/14.3 节已记录来源，不扩大网络研究。

### 15.3 最终验证与产物

```bash
bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests 'com.fongmi.android.tv.ad.audio.SpeechAdSignalProviderTest' \
  --tests 'com.fongmi.android.tv.ad.audio.AdAudioRuntimeControllerTest' \
  --tests 'com.fongmi.android.tv.ad.audio.SpeechAdRuntimeEndToEndTest' \
  --tests 'com.fongmi.android.tv.subtitle.RealtimeSubtitleRecognizerTest' \
  --tests 'com.fongmi.android.tv.ad.audio.AdAudioDiagnosticsTest' \
  --console=plain
```

| 测试类 | 用例数 | failures / errors |
|---|---:|---:|
| `SpeechAdSignalProviderTest` | 21 | 0 / 0 |
| `AdAudioRuntimeControllerTest` | 20 | 0 / 0 |
| `SpeechAdRuntimeEndToEndTest` | 1 | 0 / 0 |
| `RealtimeSubtitleRecognizerTest` | 5 | 0 / 0 |
| `AdAudioDiagnosticsTest` | 3 | 0 / 0 |
| **总计** | **50** | **0 / 0** |

最终 JVM：`BUILD SUCCESSFUL in 13s`；日志 `/tmp/ad-audio-p2a-handoff-final-tests.log`；XML 位于 `app/build/test-results/testLeanbackArm64_v8aDebugUnitTest/`。慢 create/accept/close、提交交接、reset storm、PCM 缺口、迟到回调、执行拒绝和 release 中断使用受控 fake/latch/barrier；没有用 JVM stub 代替真实模型证明。

随后一次 `bash ./gradlew :app:assembleLeanbackArm64_v8aDebug --console=plain`：`BUILD SUCCESSFUL in 10s`；日志 `/tmp/ad-audio-p2a-handoff-final-assemble.log`。仅执行既有 arm64 构建依赖链，无单独 FFmpeg/Media3 上游重建或全 ABI 矩阵。既有 CXX5202、废弃 API/Gradle 警告未阻止本目标，不在本次修复范围。

- APK：`app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`。
- 字节数：`188769225`。
- SHA-256：`434ef9e96edfa9fdec9907e36c2dfd3eb4100f4308dc5a9700035ad450281cfc`。
- **没有可靠的同配置、实现前 APK 基线，完整 2A 包体增量仍未测量**；只记录绝对大小，不宣称零增量。未新增模型、JAR、AAR 或 `.so` 依赖。

日志/XML/APK 仅为本地构建产物，不提交、上传或发布。最终代码/文档 whitespace、文档结构和 scope/commit/tag 检查在收尾执行，证据记录于 guard 归档。

### 15.4 交付、回滚与未完成门槛

**仅完成 2A 的代码隔离、确定性并发回归和 arm64 构建。** 未安装 APK、改设备设置、push、发布或启动 2B；旧关键词、默认启用开关和自动跳过策略不变。

真实 TV/模型的调度、与播放/实时字幕共存的 CPU、视频丢帧、underrun、启动/seek、实时系数和精度仍按第 13.5 节冻结样本后验收；无合格 TV/标注音频结果，不能宣布整项升级或性能验收完成。词时间对齐、复合规则接入、UI/导入和远程语音规则仍属于 2B/3/4。

实现与本文由当前 guard 一次原子提交，并创建 `recovery/AD-AUDIO-RULES-V2-P2A/<timestamp>` annotated 本地恢复标签；具体 commit/tag 由 `task_guard.sh finish` 输出和归档记录，不为自引用 ID 再开文档提交。回滚时先隔离其他工作，再 `git revert <2A-commit>` 回到 Phase 2 设计基线的运行时，保留 Phase 1 纯规则组件、用户配置与缓存；不使用 `reset --hard` 覆盖其他工作。

### Recovery anchor

- **目标/验收**：2A 独立 speech owner、控制锁外 native/Hub 操作、有界输入/结果、逻辑失效和物理串行清理已实现；最终 50 项 JVM、Leanback arm64 Debug 构建通过。完整升级及 TV 验收尚未完成。
- **工作区/定位**：`dev2`；基线 `411171eadfe07906b912210f5a669711f4476c11`；guard `AD-AUDIO-RULES-V2-P2A`，11 个允许路径、初始 protected 为空。本文与 6 个实现文件、3 个测试文件同一任务提交；以 `git log -1 --format=%H -- docs/AD-AUDIO-RULES-V2-design.md` 和该 guard 恢复标签定位，实际 Git/guard 状态优先。
- **完成证据**：第 15.2 节独立审查和提交竞态修复；第 15.3 节最终 50/0/0 XML、13 秒测试、10 秒构建及 APK hash。旧 35/37/49 项是历史，不重跑 Phase 1。
- **当前文件/符号**：Provider 的 `dispatchOwner/runOwnerCommand/close`、Runtime 的 `Workers/close`、factory profile 与 Recognizer 的 `awaitRecognitionTermination`。收尾前仅这些已验证代码及本文待提交；收尾后的实际残留以 guard/diff 为准，不丢弃已有修改。
- **未解决边界**：真实 TV/模型性能精度、实际设备端到端、同配置实现前 APK 增量基线；native 永不返回只能保留待关闭。2B/3/4 未实施，无安装/push/发布。
- **回滚锚点**：2A annotated recovery tag 保留已验证实现；运行时回退基线 `411171eadfe07906b912210f5a669711f4476c11`，采用可逆 revert，不覆盖用户脏文件。
- **唯一下一步**：取得 Phase 2B（词时间对齐与复合规则接线）的明确阶段批准；未批准前不修改 matcher/配置/路由，不把代码门槛冒充设备发布门槛。

## 16. Phase 2B 复合规则接线（2026-09-08）

### 16.1 实际实施范围

本阶段严格沿用第 13.4 节的 WebHTV 适配路线，只完成 matcher/config/routing 与候选策略接线；不修改 UI、`SpeechAdSetting` 持久化入口、远程语音规则来源、Media3/原生依赖或播放器坐标转换。

- `SpeechAdConfig` 增加可选不可变 `SpeechAdRuleSet`，保留旧四参数构造和 `create(enabled, keywords, ...)`，因此旧关键词配置、默认关闭和备份 key 语义不变。
- `SpeechAdSignalProvider` 在单一 speech owner 上使用 `SpeechAdMatcher`；识别回调完整透传 `startUs/endUs`，复合规则跨结果命中时输出规则 ID、原始 capture 区间和前后窗口。Provider 不读取媒体 duration、不加 media anchor、不调用 `seekTo`。
- 复合候选的边界仍是整个识别片段边界，`fullMatch=false` 表示其时间边界是近似的；现阶段不把 Sherpa 的 segment 终点冒充词级终点。
- `AdAudioRuntimeController` 将复合规则 ID 纳入当前路由白名单，并将规则摘要并入有界 routing version；超长既有 source version 会先做固定 SHA-256 前缀收敛，避免破坏 mux/policy 的 128 字符合同。
- `AdSkipPolicyController` 增加显式 prompt-only rule ID 集合。复合规则即使用户选择语音 AUTO，也只能进入确认路径；确认后仍由既有 `AdSkipCoordinator` 完成唯一的 capture→media 映射、duration/seekability/fresh-clock 校验和 seek。
- 结果队列溢出或超长识别结果会丢弃当前文本连续性并重置 matcher；timeline reset、seek/source 生命周期和旧 callback 代际校验继续由 Provider/Hub 负责。

### 16.2 验收边界与未完成项

已覆盖的代码合同：

- `*`、`>`、ASCII/中文动作窗口、跨识别片段、冷却和 matcher reset 由 Phase 1 测试继续覆盖；本阶段新增 Provider 端点传递、复合候选 capture 区间、timeline 隔离、规则配置摘要和 Runtime 白名单/确认模式测试。
- 旧平面关键词仍使用 `SpeechAdKeywordSet` 的原有 ASCII 单词边界和 provider 冷却；复合规则使用独立 ID、独立 matcher 状态，不把两种协议合并。
- 语音与指纹候选仍共享现有 multiplexer/policy/coordinator 状态机；不会绕过 Coordinator 直接 seek。Coordinator 在 prompt/seek/undo 状态中拒绝后续重复操作，因此同一时刻最多一次有效跳转。
- 模型未就绪、识别异常、输入超限、队列溢出和无效时间继续 fail-open；不记录识别正文、规则正文、PCM、媒体 URL 或请求头。

明确未宣称完成：

- Sherpa 当前公开 Java API 未提供可泛化、已校准的字级起止范围；本阶段没有新增 token/timestamp 硬配，也没有把整个 segment 的近似终点开放给 AUTO。目标模型/设备的合法标注音频误差（建议最大额外误差 500 ms）仍待实测和批准。
- 未实现 Phase 3 的内置/用户导入 UI、冲突展示、规则持久化和语音/指纹状态页面；未实现 Phase 4 的 TV 灰度、自动模式放量和设备指标验收。
- 未安装 APK、未连接或修改设备、未 push、未发布；旧指纹远程规则协议和缓存链路未改。

### 16.3 最终验证计划

收尾验证已按计划各执行一次以下定向 JVM 回归和一次既有 Leanback arm64 Debug 构建；这证明代码/构建门槛，不替代真实 TV/模型验收：

```bash
bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests 'com.fongmi.android.tv.ad.audio.SpeechAdRuleCodecTest' \
  --tests 'com.fongmi.android.tv.ad.audio.SpeechAdMatcherTest' \
  --tests 'com.fongmi.android.tv.ad.audio.SpeechAdConfigTest' \
  --tests 'com.fongmi.android.tv.ad.audio.SpeechAdSignalProviderTest' \
  --tests 'com.fongmi.android.tv.ad.audio.AdAudioRuntimeControllerTest' \
  --tests 'com.fongmi.android.tv.ad.audio.AdSkipPolicyControllerTest' \
  --tests 'com.fongmi.android.tv.ad.audio.SpeechAdRuntimeEndToEndTest' \
  --tests 'com.fongmi.android.tv.subtitle.RealtimeSubtitleRecognizerTest' \
  --tests 'com.fongmi.android.tv.ad.audio.AdAudioDiagnosticsTest' \
  --console=plain
bash ./gradlew :app:assembleLeanbackArm64_v8aDebug --console=plain
```

结果：`BUILD SUCCESSFUL in 10s`；9 个测试类共 **106 tests，0 failures，0 errors，0 skipped**。分类计数为 `AdAudioDiagnosticsTest=3`、`AdAudioRuntimeControllerTest=21`、`AdSkipPolicyControllerTest=9`、`SpeechAdConfigTest=10`、`SpeechAdMatcherTest=16`、`SpeechAdRuleCodecTest=18`、`SpeechAdRuntimeEndToEndTest=1`、`SpeechAdSignalProviderTest=23`、`RealtimeSubtitleRecognizerTest=5`。完整日志：`/tmp/ad-audio-p2b-final-tests.log`；XML：`app/build/test-results/testLeanbackArm64_v8aDebugUnitTest/`。

随后一次构建结果：`BUILD SUCCESSFUL in 47s`；日志：`/tmp/ad-audio-p2b-final-assemble.log`。APK 为 `app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`，字节数 `188769225`，SHA-256 `0fc9c0ca0e315e57fef6c418f6b3e3d8f8a64cb4f4bc9b7890e31f2d9eea6bc4`。`git diff --check` 通过。上述 JVM/构建结果不替代真实 TV/模型验收；未安装 APK、未 push 或发布。

### Recovery anchor

- **目标/验收**：Phase 2B 复合语音规则接线完成；复合候选使用识别 `startUs/endUs`、保持 capture 坐标、进入规则白名单，并由显式 prompt-only 策略阻止未经词级校准的 AUTO。最终定向 JVM/构建尚未执行。
- **工作区/定位**：分支 `dev2`；基线 `1e5642857f326e907f5164001d31d5ea84d3dacf`；guard `AD-AUDIO-RULES-V2-P2B`；当前任务改动以 guard scope/diff 为准，初始 protected 为空。
- **当前文件/符号**：`SpeechAdConfig`、`SpeechAdSignalProvider.processRecognitionResult/ResetMatcherCommand`、`AdAudioRuntimeController.routingSnapshotLocked/installModeResolver`、`AdSkipPolicyController.setPromptOnlyRuleIds`，以及对应配置/Provider/Runtime 测试。
- **已完成证据**：最终定向回归 106/0/0/0（tests/failures/errors/skipped），9 个测试 XML；Leanback arm64 Debug 构建 47 秒通过，APK 188769225 bytes，SHA-256 `0fc9c0ca0e315e57fef6c418f6b3e3d8f8a64cb4f4bc9b7890e31f2d9eea6bc4`；日志路径见第 16.3 节。
- **未解决边界**：词级时间对齐、500 ms 误差门槛、真实 TV/模型性能、Phase 3/4；无安装/push/发布。
- **回滚锚点**：Phase 2A annotated tag `recovery/AD-AUDIO-RULES-V2-P2A/20260908204153-1e5642857f32`；本阶段采用单 commit + `recovery/AD-AUDIO-RULES-V2-P2B/<timestamp>`，回滚用可逆 revert，不覆盖其他工作。
- **唯一下一步**：用 `task_guard.sh finish` 原子提交本阶段代码、测试与设计记录，并创建 `recovery/AD-AUDIO-RULES-V2-P2B/<timestamp>` annotated 本地恢复标签；不把 JVM/构建通过写成 TV/模型验收通过。

## 17. Phase 3 规则来源、设置与备份设计（2026-09-08）

### 17.1 本轮决策问题与本地事实

本轮只决定 Phase 3 的**规则持久化、导入/编辑、来源优先级、备份兼容和 Mobile/Leanback 入口语义**，不实施运行时代码。Phase 3 不包含远程语音规则同步、音频指纹协议、Media3/FFmpeg/JNI/native 变更，也不替代 Phase 4 的真实设备性能和识别精度验收。

当前代码事实如下：

- `app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdSetting.java` 只有
  `speech_ad_enabled`、`speech_ad_keywords`、`speech_ad_skip_seconds`、
  `speech_ad_skip_mode` 四个旧 key；`SpeechAdConfig` 已有 `SpeechAdRuleSet`，但
  `snapshot()` 尚未读取持久化的 v2 规则文档。
- `SpeechAdRuleCodec` 已把输入限制为 UTF-8 64 KiB、最多 256 条规则，并在写入前可以给出行号错误；因此规则文档是有界配置，不应被当作无限增长的日志或数据库。
- `SpeechAdRuleCodec` 的规则 ID 由规范文本摘要生成；同一规范规则可以稳定去重，但不同窗口不能默默合并为“最长窗口”。旧 `SpeechAdKeywordSet` 的 ASCII 单词边界语义仍必须保留，不能把旧关键词静默改写成字面量规则。
- `SettingAdActivity` 与 `SettingAdFragment` 已有同语义但分开的设置入口，音频指纹导入已经使用 `ActivityResultContracts.OpenDocument`、`ContentResolver`、后台解析和失败保留旧规则的模式；这应作为语音规则 UI 的最小复用模板。
- `Backup.create()` 会收集完整 `SharedPreferences`；按设置类别同步时，`Backup.APP_PREFS`/`Backup.include()` 才决定哪些 key 随设置备份。当前 `speech_ad_*` 和 `ad_audio_*` 尚未进入该白名单。
- 产品 manifest 当前明确 `android:allowBackup="false"`。产品自己的 `Backup`/`AppBackup` 才是跨设备配置迁移合同；`AppBackup` 对 `filesDir` 只打包显式允许的子树，不能假定新文件会自动进入产品备份。
- `PlayerManager.configureAdAudioRuntime()` 在刷新运行时时调用 `SpeechAdSetting.snapshot()`；新的设置写入成功后仍须沿用 `reloadAdAudioSettings()`，由现有 Runtime/Coordinator 重新装载，不能由 UI 直接 seek 或改动播放器坐标。

### 17.2 最佳实践证据与决策影响

本轮使用 IPv4/HTTP/1.1 直读官方页面；`agent-reach` 命令本机未安装，Jina 读取因 TLS EOF 不稳定，故不把路由器的失败当作证据。以下来源均在 **2026-09-08（Asia/Shanghai）** 访问，网页的 `Last-Modified` 作为页面 revision 记录；本地源码/测试以当前 HEAD `db8e98432ecd26029def37ff6e2bf01dc68cd793` 为准。

| 来源 / revision | 等级与事实 | WebHTV 适用性及决策影响 |
|---|---|---|
| Android Developers，`https://developer.android.com/training/data-storage/shared-preferences`；页面 `Last-Modified: 2026-03-05` | A：`SharedPreferences` 面向相对较小的 key-value 集合；`apply()` 立即更新内存并异步落盘，`commit()` 同步落盘且不应在主线程调用；页面同时提示新项目优先评估 DataStore | 保留现有 `Prefers` 作为四个标量设置和一个受 64 KiB 限制的规则文本槽位，写入前先完整解析/规范化；不在 UI 线程调用同步 commit，不为本阶段引入 DataStore 迁移。若规则未来超出当前硬上限，另开存储迁移阶段。 |
| Android Developers，`https://developer.android.com/training/data-storage/shared/documents-files`；页面 `Last-Modified: 2026-09-01` | A：Storage Access Framework 的 `ACTION_OPEN_DOCUMENT` 让用户从文档提供者选择文件；应用通过返回的 URI/`ContentResolver` 读取，不需要自行申请共享存储读权限 | 语音规则使用现有 `OpenDocument` launcher，接受 `text/plain`/文本扩展名并严格限制 UTF-8 字节数；读取成功后立即复制为应用自己的规范文本，不保存外部 URI 权限，避免源文件被删或权限变化影响播放。 |
| AndroidX API reference，`https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenDocument`；`Added in androidx.activity 1.2.0` | A：`OpenDocument` 是 Activity Result contract，可按 MIME 类型创建文档选择 Intent；当前项目已有相同 API 调用，不需要新增依赖或回退到旧的 request-code API | Mobile/Leanback 各保留一个 launcher，但把解析/保存委托给同一设置语义；两端不能各自实现不同的规则格式。 |
| Android Developers，`https://developer.android.com/identity/data/autobackup`；页面 `Last-Modified: 2026-02-26` | A：系统备份通过 include/exclude 控制 `sharedpref`/`file` 等 domain，并有传输类型和配额约束；系统备份配置不等于产品自定义导入导出 | 因为 WebHTV 关闭 `allowBackup` 且已有自定义 Backup JSON/ZIP，Phase 3 显式把新偏好 key 加入 `Backup.APP_PREFS`；不依赖系统 Auto Backup 迁移规则文档，也不在本阶段修改 manifest。 |
| Android Developers，`https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/navigation`；页面 `Last-Modified: 2026-05-28` | A：交互控件应可获得焦点；方向键/D-pad 默认按布局猜测，错误时可用 `nextFocusUp/Down/Left/Right` 明确指定；必须逐控件测试方向导航 | Leanback 新增的每一行继续使用现有可聚焦 row 样式；若新增管理对话框改变焦点路径，补显式焦点属性和源码测试，并把真实 D-pad 测试列为设备验收，不以 XML 静态检查冒充设备通过。 |
| 本地 `SpeechAdRuleCodec`、`AdAudioRuleStore`、`SettingAdActivity`、`SettingAdFragment`、`Backup` 及其测试 | A（项目源码）：已有有界解析、规范化、临时文件/原子替换、后台导入、旧快照保留、双入口和自定义备份框架 | 采用已有 WebHTV 约定，避免引入新依赖、新 JSON 协议或第二套播放器设置架构；新增行为必须有规则 codec、设置、备份和两端入口的定向测试。 |

### 17.3 方案比较与推荐

| 方案 | 正确性、兼容性与维护 | 性能、备份与可回滚性 | 决策 |
|---|---|---|---|
| 不变更：继续只有旧关键词四个 key | 风险最低，但用户提供的 `*`/`>` 规则没有可用入口；无法展示规则来源、数量和错误 | 不增加解析或 UI 成本，但 Phase 3 目标不成立 | 作为回滚基线，不满足目标 |
| 原样采用平台“现代”路线：DataStore + 新 Preference 页面 + 系统 Auto Backup | 与当前 Java/`Prefers`/自定义 Backup 架构不一致；引入异步状态迁移、依赖和两套恢复合同；系统备份还受 manifest 当前关闭状态影响 | 可能更适合未来大量结构化设置，但本阶段范围大、回滚面大，不能解决既有两端设置复用 | 拒绝本阶段原样采用，保留为未来独立迁移候选 |
| 直接把规则文本塞进现有 `speech_ad_keywords` | 会破坏旧 ASCII 单词边界、旧备份值和 UI 的关键词语义；非法复杂规则也难以区分 | 失败时容易覆盖旧设置，恢复和诊断不清晰 | 拒绝 |
| **WebHTV 适配：标量沿用 Prefers，规则文档使用独立 bounded key，SAF 导入后规范化，产品 Backup 显式收录** | 保留旧四 key 和旧关键词 matcher；新增 `speech_ad_rules_v1` 不改变旧用户语义；用户编辑/导入共享同一 codec；全部失败都保留旧有效文档 | 64 KiB/256 条硬上限控制内存与解析成本；`apply()` 不阻塞 UI；自定义 Backup 能随 key 清除/恢复；默认 feature flag 关闭，可单 commit/revert | **推荐实施** |

这里的“独立 bounded key”不是把复杂规则伪装成关键词：它是新 key `speech_ad_rules_v1`，只存 `SpeechAdRuleCodec.serialize()` 的规范纯文本；旧 `speech_ad_keywords` 永不迁移、覆盖或改变边界语义。选择 key 而不是新增内部文件，是因为产品 `Backup.create()`/`restorePrefers()` 已经对偏好有完整恢复合同，而 `AppBackup` 当前会跳过任意未列入允许子树的 `filesDir` 文件；不为一份最多 64 KiB 的设置文本新增文件归档协议。

### 17.4 推荐设计的具体合同

#### 持久化与来源

新增的稳定 key 及默认值：

```text
speech_ad_rules_v1          = ""       # 规范纯文本；空表示无用户文档
speech_ad_rules_source      = ""       # "user" 或 "imported"，仅记录当前文档来源
speech_ad_builtin_enabled   = false    # 内置规则包默认不参与运行时匹配
```

- 内置规则随 APK 放在 `app/src/main/res/raw/speech_ad_rules_v1.txt`，只读、可版本化，不写入用户 key。首期只放保守的多片段过渡句模板；宽泛单词、色情/儿童保护含义和“下集预告”不进入默认启用库。
- `speech_ad_builtin_enabled=false`、`speech_ad_enabled=false` 共同保持当前默认关闭行为。用户打开语音能力后，旧关键词仍按旧 matcher 工作；只有用户显式打开内置规则后，内置规则才加入 `SpeechAdConfig.rules()`。
- 用户编辑或导入都替换当前自定义文档，分别把 `speech_ad_rules_source` 记为 `user` 或 `imported`；两者不是两个并行规则池，避免用户不知道哪一份生效。内置规则与自定义文档合并时按 `custom > builtin` 的稳定 ID 优先；规范文本相同只保留一条，ID 相同但规范文本不同则整次合并失败并保留旧有效快照，不取最长窗口。
- `SpeechAdSetting.snapshot()` 只返回已验证的 `SpeechAdConfig`。写入入口先执行完整 parse、canonicalize 和上限检查，再一次性写 key；解析失败不得清空或部分写入旧文档。外部旧备份若带有非法值，运行时 fail-open、显示配置错误，并继续保留旧关键词路径。
- 旧关键词**不自动转换**为 v2 规则。这是对第 5.3 节早期“自动转换”草案的明确修正：两种匹配边界不同，静默转换会把 `ad` 等旧边界语义改变为字面量命中，造成兼容回归。

#### UI 与交互

- 保留现有四个语音设置行；新增一个“语音规则”管理行和一个“内置规则”开关/状态行。摘要至少显示：当前来源（无、内置、用户编辑、用户导入）、有效规则数、内置开关、最近一次校验错误。规则正文不写日志。
- 管理入口提供“编辑当前自定义规则、导入文本规则、清空自定义规则、恢复/查看内置规则”四个动作。编辑器使用多行 `EditText`；确定时先在后台或确认回调边界完成 codec 校验，错误必须带行号且保留旧快照。取消不产生写入。
- 导入使用独立的 `ActivityResultContracts.OpenDocument` launcher，优先 MIME `text/plain`，同时按文件名接受 `.txt`；通过 `ContentResolver` 限制读取字节、严格 UTF-8 解码和解析，成功后只保存规范文本，不保留 URI。JSON 指纹导入入口和语音规则入口保持分离。
- Mobile 与 Leanback 只共享设置语义/`SpeechAdSetting`，不强行共享现有 Activity/Fragment 基类；两端资源、按钮结果、错误文案和来源摘要保持一致。Leanback 每个新 row 必须 `focusable`，必要时添加 `nextFocus*`；源码测试覆盖入口、资源 ID、launcher 和 reload 调用，目标 TV 再执行 Up/Down/Left/Right 走查。
- 每次成功编辑、导入、清空或切换内置规则后调用现有 `reloadAdAudioSettings()`。UI 不创建 Provider、不持有 native Session、不做 `seekTo`。

#### 备份、恢复与迁移

- 将 `speech_ad_rules_v1`、`speech_ad_rules_source`、`speech_ad_builtin_enabled` 加入 `Backup.APP_PREFS`，并用 `BackupPreferenceFilterTest` 验证：完整备份和 settings-only 同步包含三者，config/spider-only 不包含；不修改旧 key 的归属。
- `Backup.create()` 已收集完整偏好，因此新 key 会进入完整产品备份；`Backup.restore()` 的 `restorePrefers(clear=true)` 会对新备份按 key 恢复，缺少这些新 key 的旧备份按默认值回退，不需要解压额外文件。系统 Auto Backup 仍不作为产品恢复合同。
- 任何写入前都以规范文本为唯一持久化形式；不备份外部 URI、原始非法文本、解析错误、识别正文、媒体 URL 或请求头。恢复后沿用现有 `reload()`/播放器刷新路径，第一次运行仍保持语音默认关闭。
- 旧用户升级不迁移关键词、不丢失关键词、不自动打开内置规则。回滚本阶段后，新 key 可被忽略，旧四 key 和旧 matcher 仍可读取；若已产生新 key，下一次设置恢复不会覆盖旧 key 的合法值。

### 17.5 Phase 3 最小实施单元、验收与回滚

获得明确的“开始实施”后，按以下一个可逆阶段执行；本设计阶段不修改这些路径：

```text
app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdSetting.java
app/src/main/java/com/fongmi/android/tv/bean/Backup.java
app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingAdActivity.java
app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingAdFragment.java
app/src/leanback/res/layout/activity_setting_ad.xml
app/src/mobile/res/layout/fragment_setting_ad.xml
app/src/main/res/raw/speech_ad_rules_v1.txt
app/src/main/res/values/strings.xml
app/src/main/res/values-zh-rCN/strings.xml
app/src/main/res/values-zh-rTW/strings.xml
app/src/test/java/com/fongmi/android/tv/ad/audio/SpeechAdConfigTest.java
app/src/test/java/com/fongmi/android/tv/bean/BackupPreferenceFilterTest.java
app/src/test/java/com/fongmi/android/tv/ui/activity/SpeechAdSettingSourceTest.java
```

验收至少包括：

1. codec 正例/负例、64 KiB/256 条上限、重复 ID/不同窗口冲突、BOM/UTF-8、导入失败保留旧值；旧关键词 ASCII 边界回归不变。
2. `SpeechAdSetting` 的默认关闭、旧 key 兼容、内置开关、自定义来源、规范 round-trip、坏恢复值 fail-open 和 `reloadAdAudioSettings()` 接线。
3. 完整 Backup、settings-only、旧备份缺 key、三种新 key 的 include/filter 回归；不重复运行与本阶段无关的全量矩阵。
4. Mobile/Leanback 的入口、资源、焦点 row、导入 launcher 和错误摘要源码测试；一次受影响的 Leanback arm64 JVM 定向测试及一次 Debug 构建。若有目标设备，再单独记录 D-pad 方向走查，不能用构建结果替代。

回滚采用本阶段单 commit 的可逆 `git revert`：先关闭/移除内置规则参与和新 UI，再由旧四 key/旧关键词路径继续运行；不删除用户偏好或改动指纹缓存。若某个规则文档恢复失败，保留上一份规范文档并让语音 Provider fail-open，绝不清空指纹规则或阻塞主播放。

### 17.6 决策状态与恢复锚点

- **建议**：按第 17.3/17.4 节实施 Phase 3；这是当前最小、兼容现有 Java/Prefers/Backup、无新依赖且可独立回滚的路线。
- **仍需用户批准**：本记录是设计研究和实施计划，不授权修改第 17.5 节代码/资源路径；收到明确“开始实施”前不得启动 Phase 3 实现 guard。
- **当前未完成**：内置规则内容的最终文案审查、真实 TV D-pad 体验、模型/标注音频精度、Phase 4 灰度和设备指标；本节不声称这些门槛已通过。
- **当前工作区/定位**：分支 `dev2`；HEAD `db8e98432ecd26029def37ff6e2bf01dc68cd793`；当前 guard `AD-AUDIO-RULES-V2-P3-DESIGN`，仅允许 `docs/AD-AUDIO-RULES-V2-design.md`，初始 protected dirty 路径为空。
- **已完成证据**：本节完成本地代码事实审查和官方 Android 存储/SAF/备份/TV 导航证据记录；未运行 Phase 3 代码测试或构建，也未修改 APK/设备。
- **回滚锚点**：P2B recovery tag `recovery/AD-AUDIO-RULES-V2-P2B/20260908220006-db8e98432ecd`；本设计提交可单独 revert，不影响 P2B 已验证运行时。
- **唯一下一步**：等待用户批准“按第 17 节开始实施 Phase 3”；若批准，先重新核对工作区并启动 `AD-AUDIO-RULES-V2-P3` 实现 guard，不重复 P2B 的 106 项测试或 47 秒构建。

## 18. Phase 3 实施记录（2026-09-09）

用户继续执行“按计划实施”的目标后，沿用 `dev2`、设计提交
`da8d4d245f0866dd0ec879d4e076b8858e3027df` 启动 `AD-AUDIO-RULES-V2-P3`。
初始工作区干净；本单元只修改第 17.5 节的 13 个实现/测试/资源路径及本文。
此前第 17.6 节的“等待批准”是设计阶段的历史状态，不再是本阶段下一步。

### 18.1 实现时的兼容与安全约束

- 使用同一个 `SharedPreferences.Editor.apply()` 发布规范文本和来源，不分别写两个 key。
  官方 `SharedPreferences.Editor` 的 `apply()` 合同（2026-09-09 复核）只保证内存更新和异步写盘，不提供写盘失败回执；不把 UI 的“已保存设置”描述成已通过断电持久性验收。来源：
  `https://developer.android.com/reference/android/content/SharedPreferences.Editor`。
- 同一主体的不同窗口生成不同 ID，所以只检查 ID 不足以满足第 5.4 节。
  编辑、导入、打开内置库和恢复均在发布前检查主体/窗口冲突；明确指出冲突位置，
  用户通过编辑保留一个窗口，或关闭内置库后使用自定义窗口。不得自动选最长窗口或同时启用两者。
- 合并后的总量同样受 256 条/64 KiB 限制；超限整次拒绝，原设置不变。
  原文为空的编辑是显式清空；空文件或只有注释的导入不是清空请求，应拒绝。
- 缓存同配置的不可变规则快照，防止设置摘要反复解析/散列，或因新对象导致 Runtime 将相同配置当成替换配置。
  内置资源只读一次；不改变 Provider、模型线程、唯一 seek authority 或复合规则 prompt-only 策略。
- 从产品备份恢复的三项新配置按一个验证单元处理；错误类型、语法、超限和冲突不覆盖旧有效配置。
  没有新 key 的旧完整备份仍按第 17.4 节恢复为新功能默认值。只清理这三项新配置，不修改无关恢复行为。
- 外部提供者异常只转成固定错误，不把 URI、路径、正文或任意异常消息写到摘要/日志。
  导入完成后成功才刷新 Runtime；页面退出不应阻止已经成功写入的配置通知现有播放器。

### Recovery anchor（验证完成，待提交）

- **目标**：第 17 节的本地语音规则设置、双端 UI、备份、定向回归和构建；全局 V2 目标仍包含未完成的真实模型/TV 验收，不缩减为本阶段。
- **文件/状态**：`SpeechAdSetting`、`Backup`、双端 `SettingAd*` 与 layout、raw 规则、三组字符串和三份 P3 测试已完成；保留旧四个关键词 key，不自动迁移；新规则采用 64 KiB/256 条上限、严格 UTF-8、规范化存储、来源记录、内置开关、冲突拒绝和坏恢复值保护。导入失败不刷新 Runtime，成功导入即使页面退出也通知现有播放器；管理菜单包含编辑、导入、清空和查看内置规则。
- **证据**：定向 `:app:testLeanbackArm64_v8aDebugUnitTest` 选择 `SpeechAdConfigTest`（13）、`SpeechAdRuleCodecTest`（18）、`SpeechAdKeywordSetTest`（9）、`BackupPreferenceFilterTest`（11）、`SpeechAdSettingSourceTest`（2），合计 **53 tests，0 failures，0 errors，0 skipped**；日志 `/tmp/ad-audio-p3-focused-tests-final.log`，XML 位于 `app/build/test-results/testLeanbackArm64_v8aDebugUnitTest/`。`bash ./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon --console=plain` 成功（19s，47 tasks up-to-date）；日志 `/tmp/ad-audio-p3-compile-final.log`。P2B 历史 106 项结果不作为 P3 证明。
- **设备**：只读 `adb devices -l` 可见五个连接，尚未确认各自测试用途/TV 代表性；未安装、启动或改变设备应用。
- **回滚**：基线 recovery tag `recovery/AD-AUDIO-RULES-V2-P3-DESIGN/20260908233846-da8d4d245f08`，本阶段最终采用原子 commit/tag，不 push。
- **边界**：本阶段没有生成或安装 APK，没有执行真实 TV/D-pad 走查、模型/标注音频精度、启动/seek 性能或包大小/hash 验收；这些仍属于后续设备/Phase 4 工作。未修改 native、Media3、FFmpeg、JNI、远程规则协议或播放器核心。
- **唯一下一步**：运行 `bash .codex/scripts/task_guard.sh finish --verified ... --commit-message ...` 原子提交本阶段并立即创建唯一 annotated recovery tag；不 push。
