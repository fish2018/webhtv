# LitePan 风格剧名识别清洗优化设计

> **状态**：✅ 基础实施单元已完成并验证；批量扫描上下文、独立规则缓存与 TMDB 联网验证待后续阶段（2026-09-07）
> **授权记录**：用户已确认“改编成设计文档后开始实施”。本次原子实施先落地不扩大调用方范围的规则/上下文基础设施、AI 上下文传递、本地字段约束、缓存隔离和无损回退；后续再实施真正的扫描批次分组与群体校验。
> **适用范围**：`app/src/main/java/com/fongmi/android/tv/title/` 及其调用方
> **参考实现**：LitePan `internal/aiorganize`、`internal/mediaorganize/planner/recognition.go` 等（commit `374affdb6584e3826cfab474fa8671418b23cf39`）
> **依赖文档**：`docs/ai-real-title-extraction-design.md`（现有 AI 真实剧名提取设计）

## 1. 背景与问题

现有 `MediaTitleResolver` 已实现规则解析 → AI 提取 → 缓存 → 置信度合并的流程，能够处理 `庆余年2 S02E05 4K 高码 国语中字 更新至18集` 等常见噪声。但在以下场景中仍有提升空间：

1. **上下文利用不足**：仅依赖单个文件名（`rawTitle`），未利用同目录其他文件（如同季其他集、片头/片尾特别篇、扫描文件夹）进行群体推断，导致孤立标题（如 `qyn 第二季 防和谐版`）误判率较高。
2. **AI 触发策略保守**：当前 AI 仅在规则解析失败或置信度低于阈值时介入，未区分“需要创造性归因”（如谐音 `青余年`→`庆余年`）与“仅需去噪”，导致 AI 调用过于频繁或时机不当。
3. **证据与降级不透明**：AI 返回结果后仅保留 `canonicalTitle`，未记录使用了哪些规则、AI 返回的原始字段、降级原因，不利于调优与人工干预。
4. **缓存粒度单一**：仅以 `MediaTitleRequest` 为键缓存完整解析结果，未区分规则层、AI 层、TMDB 验证层的中间产物，导致相同规则结果重复走 AI。
5. **缺少群体一致性校验**：未利用同一组文件（如同一季的多集）的标题、年份、季集号进行交叉校验，容易出现个别文件被误导致整组偏移（如第5集被识别为第4集导致后移）。

LitePan 在媒体整理场景中采用了更严格的分层流程：**规则预解析 → 上下文分组（目录/同名文件） → 低置信组交给 AI → AI 结果本地约束与 TMDB 验证 → 失败无损回退**，并在每一步保留可追溯的证据与降级原因。本设计借鉴其思想，在不改动现有对外接口的前提下，增强标题识别的准确性与可观测性。

## 2. 目标

1. 在保持 `MediaTitleResolver` 对外接口（`resolve(MediaTitleRequest)` → `MediaTitleResolution`）不变的前提下，内部增加上下文感知与分层验证。
2. 利用文件系统上下文（同目录文件、文件夹结构、扫描批次）提升谐音、拼音缩写、防和谐版等弱特征标题的识别率。
3. 使 AI 调用更具针对性：仅当规则+上下文仍无法给出高置信结果时才调用，并传入更丰富的上下文（如同组文件清单、文件夹名）。
4. 在 `MediaTitleResolution` 中新增证据链字段（规则来源、AI 原始返回、降级原因、置信度分解），便于调试与人工干预。
5. 引入中间产物缓存（规则解析缓存、AI 响应缓存、TMDB 验证缓存），减少重复计算。
6. 增加群体一致性校验（同组季集号连续性、年份互斥、媒体类型一致性），在出现矛盾时降级为人工确认或保留多候选。

## 3. 非目标

- 不改变 `MediaTitleRequest`、`MediaTitleResolution` 的现有公开字段（为保持向后兼容，仅在内部实现或通过追加字段的方式扩展）。
- 不强制所有播放源都走新流程；规则兜底、现有 TMDB 缓存、手动重匹配仍然有效。
- 不在第一版引入离线字典或大型模型微调；仅利用现有 AI 配置能力（OpenAI/Anthropic 兼容端点）。
- 不把完整文件路径、播放 URL、扫描任务元数据发送给 AI；仅传递文件名及必要的上下文摘要（如同目录文件名列表、文件夹名）。
- 不保证 100% 自动化；对于置信度极低或上下文矛盾的情况，仍会返回需人工确认的状态。

## 4. 总体方案

参考 LitePan `aiorganize.Service.EnhanceWithProgress` 的分层思想，建议在 `MediaTitleResolver` 内部引入以下阶段：

### 4.1 流程概览

```
原始上下文（文件名 + 文件夹名 + 同目录文件列表）
        ↓
【阶段1】规则预解析（MediaTitleParser） → 获得规则标题、年份、季集、媒体类型等中间产物
        ↓
【阶段2】上下文分组与初步合并
        - 将同一扫描批次中、目录相近或命名相似的文件组织为一个“标题组”
        - 在组内取年份/季集号的众数或范围，修正异常值
        - 若组内成员规则解析结果高度一致（例如标题相似度>0.9），则直接合并为组标题，进入缓存
        ↓
【阶段3】置信度评估与 AI 触发决策
        - 基于规则解析的完整度（是否有年份/季集）、组内一致性、历史学习样本匹配度计算综合置信度
        - 若置信度 ≥ 高阈值（如 0.85），直接进入规则缓存路径
        - 若置信度 在 [低阈值, 高阈值]（如 0.5~0.85）且组 size > 1，则将该组送入 AI，请求标题归一化
        - 若置信度 < 低阈值（如 0.5）或为孤一文件（组 size = 1），则直接送入 AI（因为规则几乎无信息）
        ↓
【阶段4】AI 调用（复用 AiTitleExtractionService，但增强 prompt 上下文）
        - Prompt 中不仅包括原始文件名，还包括：文件夹名、同目录其他文件名（去重后取前 N 条）、组内统计信息（如众数年份、季集范围）
        - AI 返回结构化字段：标题、年份、媒体类型、季集号、集号、集标题、别名列表、置信度（0~1）
        - 若 AI 调用失败或返回为空，则记录降级原因并继续后续规则兜底路径
        ↓
【阶段5】结果融合与本地约束
        - 将 AI 返回与规则解析结果按置信度加权融合（可调参数）
        - 对融合结果执行本地约束：年份必须在合理范围（1900~当前+2）、季集号非负、媒体类型与文件名后缀匹配等
        - 生成候选列表（规则候选、AI 候选、融合候选），按置信度排序
        ↓
【阶段6】TMDB 验证与回填（复用现有 TmdbMatcher.searchAndMatch 流程）
        - 按候选列表顺序向 TMDB 查询，首个返回可用结果即为最终标题
        - 若所有候选均未命中 TMDB，则保留最高置信度的规则/AI 结果作为 `canonicalTitle`，并标记 `needsVerification=true`
        ↓
【阶段7】输出 MediaTitleResolution
        - 填充现有字段（rawTitle、ruleTitle、canonicalTitle、year、seasonNumber、episodeNumber、mediaType、aliases、confidence、source 等）
        - 新增内部字段（仅用于调试/日志，不反序列化到外部）：
            - ruleSource：规则解析使用的具体子规则（如 SEASON_PATTERN、YEAR_PATTERN）
            - aiRawResult：AI 原始返回的 JSON 文本（脱敏后）
            - confidenceBreakdown：ruleConf、contextConf、aiConf、finalConf 的分解
            - degradationReason：若进入 AI 或 TMDB 失败的具体原因（如 "low rule confidence", "api timeout", "tmdb no match"）
            - groupInfo：所在标题组的尺寸、内部一致性得分
```

### 4.2 关键实现点

- **上下文分组**：可采用简单的启发式规则：同一文件夹且文件名基于相同正则（去除季集号、集号、分辨率、编码等噪声后）相似度 > 0.8；或文件夹名包含相同关键词（如同样出现“庆余年”）。
- **Prompt 示例（参照 LitePan aiorganize/client.go 中的 chat/completions 或 messages 格式）**：
  ```json
  {
    "model": "{{.Model}}",
    "messages": [
      {"role":"system","content":"你是一个媒体标题归一化助手。请从给定的文件名、文件夹名及同目录其他文件中提取标准的媒体标题、年份、媒体类型（movie/tv）、季号、集号、集标题。只返回 JSON，不要有额外说明。"},
      {"role":"user","content":"文件名：青余年 第18集\\n文件夹名：/媒体/国产剧/\\n同目录文件：[青余年 第1集, 青余年 第2集, ...]"}
    ],
    "max_tokens": 512,
    "temperature": 0.1
  }
  ```
- **缓存策略**：
  - `ruleCache`：以 `rawTitle` + `folderName` + `siblingFileHash` 为键，缓存规则解析中间产物。
  - `aiCache`：以上述键 + Prompt 版本号为键，缓存 AI 返回的结构化结果（TTL 24h）。
  - `tmdbCache`：现有 `TmdbMatchCache` 继续使用，按标题查询缓存 TMDB 结果（已实现）。
- **置信度分解**（可参考）：
  - `ruleConf`：规则解析的完整度（如 是否成功提取年份、季集号）。
  - `contextConf`：上下文一致性得分（如 组内标题相似度的平均值）。
  - `aiConf`：AI 返回自身携带的置信度（若无则默认 0.7）。
  - `finalConf`：加权平均（如 0.4*ruleConf + 0.3*contextConf + 0.3*aiConf），随后经 TMDB 验证后可能提升或保持不变。

## 5. 数据模型扩展（仅内部）

为保持向后兼容，建议在 `MediaTitleResolution` 中新增以下 **包私有** 或 **仅通过 getter/setter 内部使用** 的字段（不影响 JSON 序列化或跨模块传递）：

```java
// 内部调试/日志字段（对外保持现有 MediaTitleResolution 不变）
private String ruleSource;          // 如 "SEASON_PATTERN+YEAR_PATTERN"
private String aiRawResult;         // AI 原始返回（脱敏后），用于排查
private String confidenceBreakdown; // 例如 "rule:0.6 context:0.8 ai:0.7 final:0.71"
private String degradationReason;   // 空表示无降级，否则如 "api_timeout"、"tmdb_no_match"
private GroupInfo groupInfo;        // 标题组尺寸、内部一致性
```

对应的 `GroupInfo` 可为简单数据类：

```java
private static class GroupInfo {
    int size;          // 组内文件数量
    float innerConsistency; // 0~1，组内标题相似度均值
    String folderName; // 所属文件夹名（脱敏后仅保留基名）
}
```

这些字段由 `MediaTitleParser`、`MediaTitleResolver` 和 `AiTitleExtractionService` 填充，并通过现有 getter/setter 在内部链路传递；由于 AI 结果缓存使用 Gson，它们会随缓存条目一并保存，便于诊断，但不改变现有解析调用方的必需输入和输出语义。日志仅输出脱敏后的文件名及证据摘要，不输出完整路径、播放 URL 或密钥。

## 6. 实现步骤（分阶段）

| 阶段 | 目标 | 主要工作 |
|------|------|----------|
| **第一阶段** | 上下文分组与规则中间产物缓存 | - 在 `MediaTitleResolver.resolve()` 前增加扫描批次上下文（可通过构造函数注入 `ScanContext` 包含同目录文件列表）<br>- 实现简单的文件名相似度去噪算法（移除季集号、分辨率、编码等噪声后比较基名）<br>- 新增 `ruleCache`（基于 LRU 或简单 Map）缓存规则解析中间产物<br>- 单元测试：验证相同组内文件命中规则缓存 |
| **第二阶段** | 置信度评估与 AI 触发决策 | - 定义置信度计算函数（规则完整度 + 上下文一致性 + 历史样本匹配度）<br>- 根据阈值分派至 AI 或直接走规则路径<br>- 新增 `aiCache` 用于缓存 AI 结构化结果<br>- 集成测试：mock AI 响应，验证低置信组触发 AI，高置信组跳过 AI |
| **第三阶段** | AI 调用与结果融合 | - 扩充 `AiTitleExtractionService` 的 prompt，增加文件夹名、同目录文件列表等上下文（保持后向兼容：旧字段仍可用）<br>- 解析 AI 返回的 JSON，映射到临时结构（标题、年份、媒体类型、季集号、集号、别名）<br>- 实现规则结果与 AI 結果的加权融合函数<br>- 单元测试：各种 AI 返回情况（完整、部分缺失、 JSON 非法）的容错 |
| **第四阶段** | TMDB 验证与回填、输出增强 | - 复用现有 `TmdbMatcher.searchAndMatch` 流程，按融合结果排序后逐个候选查询<br>- 若 TMDB 未命中但置信度仍高，则保留规则/AI 结果并标记 `needsVerification=true`<br>- 在 `MediaTitleResolution` 中填充现有字段，同时在内部记证据链字段（仅日志）<br>- 集成测试：构造扫描批次，验证最终输出与预期一致，日志包含预期证据 |
| **第五阶段** | 人工验收与性能基线 | - 选取典型资源标题（包含谐音、拼音缩写、防和谐版、混合语言）进行人工打分<br>- 比较优化前后的 TMDB 命中率、弹幕自动匹配成功率、字幕匹配率<br>- 性能基线：确保单次解析延迟不增加超过 30ms（AI 调用由缓存和分组降低频率抵消）<br>- 编写验收报告并提交评审 |

## 7. 验证计划

### 7.1 单元测试

- `MediaTitleParserTest`：新增上下文感知的清洗方法（如 `cleanSearchTitlesWithContext`）的边界情况。
- `MediaTitleResolverTest`：
  - 注入 `ScanContext`（同目录文件列表）验证组内一致性提升置信度。
  - 验证低置信组触发 AI 调用（mock `AiTitleExtractionService`）。
  - 验证 AI 返回后的融合与本地约束（如年份超限被修正）。
  - 验证 TMDB 验证失败时保留规则/AI 结果并设置 `needsVerification=true`。
- `AiTitleExtractionServiceTest`：验证新增上下文字段是否正确拼接到 prompt 中（不改变原有行为）。

### 7.2 集成测试

- 构造一个包含 10 个文件的模拟扫描批次（同一季不同清晰度、不同语言版本）。
- mock TMDB 返回（部分候选命中、部分未命中）。
- 断言最终 `MediaTitleResolution`：
  - `canonicalTitle` 为正确标题。
  - `year`、`seasonNumber`、`episodeNumber` 与众数或推断值一致。
  - `aliases` 包含规则提取的别名及 AI 返回的别名。
  - `confidence` 在合理区间（0.6~0.95）。
  - 日志中包含 `ruleSource`、`confidenceBreakdown`、`groupInfo` 等信息。

### 7.3 人工验收

- 从真实用户资源库中抽取 100 条困难标题（包含谐音如 `青余年`、拼音缩写如 `qyn`、防和谐版如 `防和谐`、混合信息如 `长相思2.2024.2160p.WEB-DL`）。
- 在开发机上跑现有实现与优化后实现，记录：
  - TMDB 自动匹配成功率（调用 `TmdbUIAdapter.autoMatch` 后是否得到正确 `Vod.name`）。
  - 弹幕自动搜索命中率（使用 `DanmakuApi.search` 是否返回弹幕）。
  - 字幕自动匹配率（使用 `SubtitleTmdbResolver` 是否得到正确语言字幕）。
  - 人工肉眼判断的标题正确率。
- 要求优化后实现相比基线：TMDB 自动匹配率提升 ≥10pp，弹幕/字幕命中率提升 ≥5pp，且不引入新的误匹配（人工判断错误率不升高）。

### 7.4 性能与安全回归

- 确认单次 `resolve()` 在 AI 未命中缓存时延迟不超过基线的 1.2倍（主要开销在网络，受缓存命数影响）。
- 确认没有新增的外联请求（AI 请求仍受现有 `Setting.isAiTitleExtraction()` 开关控制；未开启时不应产生任何 AI 流量）。
- 确认没有敏感信息泄漏：日志中不出现完整文件路径、播放 URL、API 密钥（通过脱敏处理）。

## 8. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 上下文分组误判（将不同剧的文件划入同一组） | 导致错误的众数年份/季集，进而误导标题识别 | 分组阈值保守（相似度 > 0.85）+ 再次验证：组内如果出现媒体类型冲突（如一部是电影一部是剧集）立即解组 |
| AI 调用频率增加导致配额耗尽或成本上升 | 服务不可用或用户感知延迟 | 先走规则缓存 + 上下文一致性过滤，仅当组内置信度在中间区间且组 size > 1 时才送 AI；单文件低置信虽然也送 AI，但总量受缓存限制（相同文件名复用） |
| AI 返回格式不一致或出现幻觉 | 标题错误，后续 TMDB 错误匹配 | 解析时进行字段合法性校验（年份范围、媒体类型枚举、季集号非负），不合格则降级为仅使用规则结果，并在日志记录 `aiRawResult` 以便排查 |
| 新增缓存导致内存占用增长 | 在低端设备上可能造成 OOM | 采用 LRU 策略（默认最大 256 条），并提供开关在低内存设备上可调小或关闭规则/ai 缓存（仅保留 TMDB 缓存） |
| 本地约束过严导致合法标题被错误修正 | 比如某些合法的年份或季集号被误删 | 本地约束仅做基本范围检查（年份 1900~当前+2，季集号 ≥0，集号 ≥0），不做剧情逻辑校验；如需更严格可后期加入学习样本 |

## 9. 结论与下一步

本设计在不改变现有对外接口的前提下，引入了上下文感知、分层验证、证据链可追溯以及中间产物缓存等 LitePan 风格的优化手段，预期显著提升谐音、拼音缩写、防和谐版等弱特征标题的识别率，同时保持系统的稳健性与可观测性。

**当前实施入口**：已获得用户实施授权。第一阶段先落地不改变现有调用方接口的规则/上下文基础设施、严格字段校验、证据链和针对性单测；后续阶段在该基础上扩展批量上下文与 AI prompt，并保留规则结果作为无损回退。

> **注**：本文档记录的是设计与实施边界，代码变更按阶段提交。实施时需结合现有代码实际情况调整细节，但核心思想（规则先行 → 上下文分组 → 低置信交给 AI → 本地约束 → TMDB 验证 → 无损回落）应保持不变。

## 10. 本次基础实施记录（2026-09-07）

### 10.1 已实施内容

本次选择“最小可逆闭环”，不新增 `ScanContext` 或改动现有 UI/业务调用方：

1. **受控上下文输入**：`MediaTitleRequest` 增加可选文件夹名和同组标题；文件夹只保留基名，同组标题去 URL/绝对路径、忽略空值、大小限制为 16 条并做大小写不敏感去重。
2. **规则层共识**：`MediaTitleParser` 对同组标题执行现有清洗后的一致性投票；只有上下文置信度达到阈值时才提升剧名，不把 `rawRemarks` 或 `episodeName` 自动伪装成同组标题；纯“更新至 N 集”备注不再成为搜索候选。
3. **证据链**：`MediaTitleResolution` 增加规则来源、上下文置信度/组大小、AI 原因码、置信度分解和降级原因；解析、AI、TMDB 缓存合并路径均保留这些信息。
4. **AI 上下文与本地约束**：prompt 追加脱敏后的文件夹名、同组标题及规则证据；AI 返回的标题拒绝 URL、路径、清晰度/集数噪声；年份、季号、集号越界时保留规则回退值，别名字段按“遇到首个合法值”处理。
5. **缓存隔离**：AI 标题缓存键升级为 `v2`，纳入文件夹名和同组标题，避免不同上下文复用旧 AI 结果；解析调用方不允许 AI 时仍直接规则回退。
6. **回归覆盖**：新增上下文共识、冲突上下文、敏感路径边界、备注污染、缓存键变化、prompt 证据、AI 越界字段和多别名容错测试。

### 10.2 本次变更文件

- `app/src/main/java/com/fongmi/android/tv/title/MediaTitleRequest.java`
- `app/src/main/java/com/fongmi/android/tv/title/MediaTitleCandidate.java`
- `app/src/main/java/com/fongmi/android/tv/title/MediaTitleResolution.java`
- `app/src/main/java/com/fongmi/android/tv/title/MediaTitleParser.java`
- `app/src/main/java/com/fongmi/android/tv/title/MediaTitleCache.java`
- `app/src/main/java/com/fongmi/android/tv/title/MediaTitleResolver.java`
- `app/src/main/java/com/fongmi/android/tv/service/AiTitleExtractionService.java`
- 对应 `app/src/test/java/com/fongmi/android/tv/title/` 和 `app/src/test/java/com/fongmi/android/tv/service/AiTitleExtractionServiceTest.java`

### 10.3 验证结果

执行：

```bash
./gradlew :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests 'com.fongmi.android.tv.title.*' \
  --tests 'com.fongmi.android.tv.service.AiTitleExtractionServiceTest' \
  --console=plain
```

结果：`BUILD SUCCESSFUL`；6 个测试类共 29 个测试，`skipped=0`、`failures=0`、`errors=0`。构建输出中的原生库位数提示和既有 deprecated API 提示不属于本次标题清洗改动。

### 10.4 尚未实施与下一步

- **尚未实施**：真正的扫描批次/目录 `ScanContext` 注入、独立规则中间产物缓存、季集号连续性/年份互斥群体校验、TMDB 联网候选验证和 100 条真实资源人工基线。
- **下一步**：在确认现有调用方能够稳定提供同目录文件列表后，新增一个独立的批次上下文适配单元；该单元只负责产出受控 `contextTitles`，失败时传空列表并保持当前规则路径。
- **回滚**：删除本次原子提交即可恢复原有单标题解析、AI prompt 和缓存键；缓存键使用 `v2`，不会读取新旧上下文混淆的旧条目。
