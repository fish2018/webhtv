# 外部接口去广规则学习与升级设计

## 1. 文档状态

- 状态：提议（Proposed）
- 版本：v0.1
- 日期：2026-07-11
- 适用范围：点播/直播配置接口中的 `ads`、`rules` 去广规则
- 不包含：蜘蛛代码自动升级、站点实现更新、远程脚本执行、云端规则服务

## 2. 背景

饭太硬、嗷呜等第三方影视接口不仅提供站点和解析配置，也可能携带维护者长期积累的去广规则。例如：

- `ads` 中的广告域名黑名单；
- `rules[].hosts` 中的目标域名或 URL 特征；
- `rules[].regex` 中针对 M3U8、TS 切片或嗅探地址的匹配规则；
- `rules[].exclude` 中用于避免误杀正片的保护规则。

当前应用加载接口后，会把接口中的 `ads` 和 `rules` 作为当前配置规则使用；用户自定义规则则由 `UserAdRuleStore` 持久化，并由 `RuleConfig` 合并。现有 AI 去广能力还能根据播放上下文生成候选规则。

目前缺少的是一条受控的沉淀链路：当用户接入或刷新外部接口时，识别其中可复用的去广规则，经过校验、去重和审核后保存为用户规则，使规则在接口更换或上游规则消失后仍可继续使用。

## 3. 目标与非目标

### 3.1 目标

1. 接入或刷新用户接口时识别新增、变更和删除的去广候选规则。
2. 区分去广规则、视频嗅探规则、播放脚本和代理配置，避免错误学习。
3. 对候选规则进行规范化、合法性校验、去重、风险评级和来源追踪。
4. 第一阶段由用户确认后保存到本地用户规则库。
5. 为后续基于实际命中、播放失败和用户反馈的置信度升级预留数据结构。
6. 不影响现有接口加载速度和正常播放；学习失败不能导致接口接入失败。

### 3.2 非目标

1. 不执行或学习外部接口中的 JavaScript `script`。
2. 不把 `proxy`、`hosts` DNS 映射、`parses` 或 `spider` 当作去广规则。
3. 不在第一阶段静默启用学习结果。
4. 不把未经验证的本地学习结果上传或共享给其他用户。
5. 不保证外部规则永久有效；系统只负责记录来源和效果，不替代规则维护者。

## 4. 核心决策

### 4.1 采用“候选导入”而不是“全自动学习”

外部接口属于不可信输入，其中的 `rules` 同时承载去广、嗅探、网页交互和播放适配等不同用途。仅凭字段结构无法可靠判断其语义。因此第一阶段只生成候选规则，并要求用户确认后才写入 `UserAdRuleStore`。

### 4.2 当前接口规则与持久化规则分层

- 当前接口规则：继续由 `VodConfig` / `LiveConfig` 加载，只在该配置生效期间使用。
- 学习候选：保存在独立候选存储中，默认不参与拦截。
- 已确认规则：转换为 `UserAdRule`，由 `RuleConfig` 合并并参与拦截。

该分层避免同一规则因为“接口本身生效”和“用户已导入”而出现不可解释的管理状态。

### 4.3 AI 只参与解释和补充，不作为信任来源

结构化规则的提取、校验和去重必须由确定性代码完成。AI 可以：

- 对难以分类的规则给出类别建议；
- 根据规则内容生成人类可读说明；
- 结合 M3U8 样本建议更精确的 `exclude`；
- 对候选规则给出附加风险提示。

AI 结果不能绕过正则校验、风险阈值或用户确认。

## 5. 现有能力复用

| 现有组件 | 复用方式 |
|---|---|
| `VodConfig` / `LiveConfig` | 在成功解析 `ads`、`rules` 后触发候选提取 |
| `RuleConfig` | 继续负责合并当前接口规则与用户已启用规则 |
| `UserAdRuleStore` | 保存用户确认导入的规则 |
| `UserAdRule` | 承载最终启用规则，后续扩展来源元数据 |
| `AdDetectionResult` | 复用 hosts/regex/exclude/confidence 表达候选分析结果 |
| `AdRuleManageDialog` | 增加“待审核导入”入口和来源信息 |
| `AdBlockStatsStore` | 后续用于累计命中次数和评估规则效果 |

## 6. 总体流程

```text
用户新增或刷新接口
        │
        ▼
正常下载与解析配置 ────────────────┐
        │                           │ 解析失败
        ▼                           ▼
提取 ads / rules                保持原错误流程
        │
        ▼
分类与安全校验
        │
        ├── 非去广规则 → 忽略并记录分类原因
        │
        ▼
规范化与指纹去重
        │
        ▼
与当前接口规则、用户规则、历史候选比较
        │
        ▼
生成新增 / 更新 / 已存在候选
        │
        ▼
后台保存候选，不阻塞接口接入
        │
        ▼
提示“发现 N 条可导入去广规则”
        │
        ▼
用户预览、测试、编辑并确认
        │
        ▼
写入 UserAdRuleStore，RuleConfig.invalidate()
```

## 7. 输入字段分类

| 输入字段 | 是否学习 | 处理方式 |
|---|---:|---|
| `ads[]` | 是 | 作为域名型高可信候选，但仍校验格式和范围 |
| `rules[].hosts` | 条件学习 | 必须结合 `regex`、规则名称和 host 特征判断用途 |
| `rules[].regex` | 条件学习 | 校验正则安全性并识别去广/M3U8 特征 |
| `rules[].exclude` | 是 | 仅随同同一候选规则导入，不单独生成规则 |
| `rules[].script` | 否 | 外部可执行内容，禁止持久化和自动执行 |
| `proxy[]` | 否 | 网络代理匹配规则，不是广告规则 |
| 根级 `hosts[]` | 否 | 通常是 DNS/域名映射，语义不同 |
| `parses` / `spider` / `sites` | 否 | 与去广规则学习无关 |

### 7.1 初始分类规则

以下条件用于生成候选，不直接决定启用：

- `ads[]` 中合法且足够具体的域名：高可信。
- 包含 `#EXT-X-DISCONTINUITY`、`#EXTINF`、`.ts`、`m3u8` 等媒体清单特征：可能是 M3U8 去广规则。
- host/path 含 `ad`、`ads`、`advert`、`preroll`、`midroll`、`commercial` 等明确广告特征：中高可信。
- 只包含 `item_id=`、`is_play_url=`、视频 CDN 路径等嗅探特征：标记为嗅探规则，不学习。
- 包含点击按钮或 DOM 操作的 `script`：禁止学习。
- 规则过宽，如 `.*`、通用顶级域、仅匹配 `.m3u8`：高风险或拒绝。

分类器应输出 `AD_BLOCK`、`SNIFF`、`SCRIPT`、`NETWORK_PROXY`、`UNKNOWN` 五类，并保存分类原因。

## 8. 候选数据模型

建议新增独立模型，避免在第一阶段过度修改 `UserAdRule`：

```java
public class ImportedAdRuleCandidate {
    private String id;
    private String fingerprint;
    private String name;
    private List<String> hosts;
    private List<String> regex;
    private List<String> exclude;

    private String sourceConfigUrlHash;
    private String sourceConfigName;
    private String sourceRuleName;
    private String sourceType;       // ads / rules
    private String classification;   // AD_BLOCK / SNIFF / ...

    private float confidence;
    private String riskLevel;        // LOW / MEDIUM / HIGH / REJECTED
    private List<String> reasons;
    private String status;           // PENDING / IMPORTED / IGNORED / SUPERSEDED

    private long firstSeenAt;
    private long lastSeenAt;
    private int seenCount;
}
```

### 8.1 隐私要求

- 不明文保存带 token、签名或账号参数的接口 URL。
- `sourceConfigUrlHash` 使用规范化 URL 的哈希；UI 只展示用户配置名称或脱敏 host。
- 不保存完整媒体 URL 样本，只保存必要的 host、路径模板或脱敏摘要。

## 9. 规范化与去重

### 9.1 规范化

- host 转小写，去除协议、端口、路径、首尾空白和尾随点。
- 删除空字符串和完全重复项。
- regex 保留语义，不进行可能改变匹配行为的自动改写。
- 对数组排序后生成稳定指纹。
- 规则名称不参与指纹，避免不同接口对同一规则使用不同名称造成重复。

### 9.2 指纹

```text
fingerprint = SHA-256(
  sorted(normalizedHosts) + "\n" +
  sorted(regex) + "\n" +
  sorted(exclude)
)
```

相同指纹只保留一个候选，并累计来源与 `seenCount`。如果新版本仅增加 `exclude` 或收窄正则，则显示为“建议更新”，不直接覆盖用户已编辑版本。

## 10. 置信度与风险评级

置信度表示“它像不像去广规则”，风险表示“启用后是否可能误杀”，两者必须分开。

### 10.1 建议评分

| 信号 | 置信度变化 |
|---|---:|
| 来自根级 `ads[]` | +0.45 |
| 明确广告域名/路径关键词 | +0.25 |
| M3U8 广告边界组合特征 | +0.20 |
| 被两个不同接口重复发现 | +0.15 |
| 本地实际命中过广告反馈样本 | +0.20 |
| 只有单一固定切片时长 | -0.15 |
| 更像视频嗅探规则 | -0.50 |
| 规则语义无法判断 | 最高限制为 0.40 |

最终值限制在 0.0～1.0。第一阶段该分数仅用于排序和提示。

### 10.2 风险规则

- LOW：具体广告子域、具体路径，且有排除保护。
- MEDIUM：依赖媒体标签组合或多个切片时长。
- HIGH：跨域通配、宽泛正则、可能命中主内容 CDN。
- REJECTED：无效正则、空规则、匹配所有内容、长度/数量超限、包含脚本。

## 11. 安全约束

1. 单接口最多分析 200 条 `ads` 和 200 条 `rules`。
2. 单条 host 最大 253 字符，单条 regex 建议最大 2,048 字符。
3. 单候选最多 32 个 hosts、32 个 regex、32 个 exclude。
4. 正则必须可编译，并通过危险模式检查；高风险正则不得自动启用。
5. 分类和分析在后台线程运行，设置超时；失败时丢弃学习结果但保留正常配置。
6. 不执行任何来源于接口的脚本。
7. 导入前展示具体规则内容、来源、风险和可能影响。
8. 已导入规则必须可以单独禁用、编辑和删除。

## 12. 用户交互

### 12.1 接口接入完成提示

```text
接口接入成功
发现 3 条新的去广候选规则，其中 1 条高风险。
[稍后处理] [查看规则]
```

提示不能阻塞用户进入首页；同一批候选只提示一次。

### 12.2 候选列表

每条显示：

- 规则名称和来源接口；
- 类型：域名去广 / M3U8 片段 / 未确定；
- 置信度和风险；
- 新增、更新或已存在状态；
- hosts/regex/exclude 摘要；
- 导入、忽略、编辑后导入、测试操作。

默认勾选仅限 LOW 风险且置信度不低于 0.75 的候选，但仍需要用户点击确认。

### 12.3 导入结果

导入后创建 `UserAdRule`：

- `source = "interface_import"`；
- 名称默认带来源，例如“饭太硬导入：cdn.ryplay”；
- 默认启用状态由用户确认页选择；
- 保留候选 ID，便于后续识别上游更新。

## 13. 组件设计

建议新增：

| 组件 | 职责 |
|---|---|
| `InterfaceAdRuleExtractor` | 从 `ads`、`rules` 提取原始候选 |
| `AdRuleClassifier` | 确定规则类别并给出理由 |
| `AdRuleValidator` | host、regex、数量和复杂度校验 |
| `AdRuleNormalizer` | 规范化和生成稳定指纹 |
| `ImportedAdRuleCandidateStore` | 保存候选、来源和审核状态 |
| `InterfaceAdRuleLearningService` | 编排提取、分类、评分、去重流程 |
| `AdRuleImportDialog` | 展示、编辑、测试和确认导入 |

### 13.1 接入点

学习任务应在配置已经成功应用后异步触发：

```java
applyConfig(config);
InterfaceAdRuleLearningService.analyzeAsync(configMeta, config);
```

不得让学习服务反向修改本次 `VodConfig` / `LiveConfig` 的解析结果。

## 14. 与现有 AI 去广的关系

两种来源最终进入同一个审核与规则管理体系：

| 来源 | 证据 | 默认策略 |
|---|---|---|
| 接口导入 | 上游结构化 `ads/rules` | 确定性校验后人工导入 |
| 播放反馈 AI | 当前播放上下文和 M3U8 样本 | AI 建议后人工确认 |
| 手动创建 | 用户输入 | 校验后由用户决定启用 |

后续可以把接口候选和 AI 检测结果互相佐证。例如 AI 检测到的广告 host 与两个接口的 `ads` 相同，可提高置信度；但仍不能跳过风险校验。

## 15. 分阶段实施

### Phase 1：安全导入闭环

- 提取 `ads` 和明确的去广 `rules`。
- 完成分类、校验、规范化和指纹去重。
- 保存待审核候选。
- 提供候选列表和人工导入。
- 导入到 `UserAdRuleStore`，不做自动启用。

### Phase 2：来源更新与效果验证

- 识别上游规则新增、收窄、扩展和删除。
- 关联现有命中统计。
- 提供规则测试和更新差异预览。
- 对长期零命中或导致播放失败的规则给出停用建议。

### Phase 3：可选灰度自动升级

仅在用户显式开启后：

- LOW 风险；
- 置信度不低于 0.90；
- 至少两个独立来源或有本地正向验证；
- 不包含脚本和高复杂度正则；
- 自动启用后可快速回滚。

即使进入 Phase 3，也不建议提供无条件全自动模式。

## 16. 测试策略

### 16.1 单元测试

- 饭太硬样例中的 `cdn.ryplay` 规则被识别为 M3U8 去广候选。
- 嗷呜样例中的 `mozai.4gtv.tv` 被识别为域名去广候选。
- 抖音、火山等嗅探规则不会被导入为广告规则。
- `script`、`proxy`、根级 DNS `hosts` 不进入候选库。
- 相同规则在不同接口中生成相同指纹。
- 无效或危险正则被标记为 `REJECTED`。
- 接口 URL 中的 token、query 不会被明文持久化。

### 16.2 集成测试

- 配置正常应用后才启动学习任务。
- 学习异常不影响接口接入和播放。
- 用户确认导入后，`RuleConfig` 能立即获取新规则。
- 忽略的候选在同一版本接口刷新时不重复提示。
- 上游规则变更时展示差异，不覆盖用户编辑内容。

### 16.3 性能目标

- 不增加接口首屏等待时间。
- 典型 100 条规则的后台分析目标小于 300 ms，不含可选 AI 调用。
- 候选存储和规则合并不在每个媒体请求上重复解析 JSON 或编译正则。

## 17. 验收标准

Phase 1 完成需同时满足：

1. 接入饭太硬、嗷呜样例后能够生成正确候选。
2. `ads`、去广 `rules`、嗅探 `rules`、`script`、`proxy` 分类符合预期。
3. 未经用户确认的候选不参与请求拦截。
4. 用户可查看来源、风险和完整规则后再导入。
5. 相同规则不会被重复保存。
6. 危险正则和过宽 host 不允许导入或默认启用。
7. 学习流程失败不影响配置加载、首页展示和视频播放。
8. 导入规则可在现有规则管理界面中启停、编辑和删除。

## 18. 待确认问题

1. 候选规则是否随应用备份/恢复；建议 Phase 1 只备份已导入规则，不备份待审核候选。
2. 用户切换接口后，已导入规则是否继续全局生效；建议继续生效，但在规则名称中清晰展示来源。
3. 是否允许按站点或接口限定规则作用域；建议 Phase 2 引入，Phase 1 保持现有全局规则语义。
4. 是否提供“低风险规则默认勾选”；建议提供，但仍要求用户最终确认。

## 19. 边界约束

### 始终执行

- 校验不可信输入。
- 保留来源、指纹、风险和审核状态。
- 学习任务与配置接入解耦。
- 用户可回滚所有已导入规则。

### 实施前需要确认

- 修改 `UserAdRule` 持久化格式。
- 引入新的正则安全检测依赖。
- 增加云端规则同步或社区规则库。
- 启用任何形式的自动发布。

### 永不执行

- 执行外部接口提供的学习脚本。
- 将接口中的所有 `rules` 无差别转为广告规则。
- 将带凭证的完整接口 URL 上传给 AI 或写入日志。
- 未经校验和授权静默修改用户全局规则。

## 20. 相关文档与代码

- `docs/AI_AD_DETECTION_DESIGN.md`：现有 AI 去广反馈与规则管理设计。
- `docs/ai-ad-detection-design.md`：现有广告检测和统计设计。
- `app/src/main/java/com/fongmi/android/tv/api/config/VodConfig.java`
- `app/src/main/java/com/fongmi/android/tv/api/config/LiveConfig.java`
- `app/src/main/java/com/fongmi/android/tv/api/config/RuleConfig.java`
- `app/src/main/java/com/fongmi/android/tv/api/config/UserAdRuleStore.java`
- `app/src/main/java/com/fongmi/android/tv/bean/UserAdRule.java`
- `app/src/main/java/com/fongmi/android/tv/bean/AdDetectionResult.java`

