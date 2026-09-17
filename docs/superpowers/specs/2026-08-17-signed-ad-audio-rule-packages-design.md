# 签名音频指纹规则包设计

## 状态

基础设施已实施并通过本地验证，尚未接入生产密钥与远程更新。

## 日期

2026-08-17

## 交付状态

已完成：Tink Android RAW Ed25519 验签、严格签名信封 codec、可信密钥生命周期、
revision 高水位、current/previous 双槽原子缓存、崩溃恢复、签名规则运行时适配器和
本地优先的双源选择。现有 SDK v2 payload、本地规则导入和默认关闭行为保持不变。

仍待独立设计、评审和交付：生产公钥配置与轮换流程、固定 HTTPS 更新器、作者端规则
质量流水线，以及 H5/真实样本兼容 oracle。在这些条件具备前，不把签名源接入
`PlayerManager`，也不启用远程下载。

## 背景

当前音频指纹运行时已经完成本地 SDK v2 JSON 导入、严格解析、原子替换、热更新、播放代际隔离和确认后跳过。第三方采集 APK 使用兼容的 `spectral-sequence-v2` 与 `schemaVersion: 2`，但其 `RuleLoader` 和 `RuleStore` 没有签名、revision 防回滚、过期控制或可靠恢复，不能直接用于线上分发。

项目最低 Android API 为 24。Android 官方 `Signature` 算法表只从 API 33 保证 `Ed25519`，因此不能直接依赖系统 JCA。采用 `com.google.crypto.tink:tink-android:1.23.0` 提供 Ed25519 验签；Tink Android 官方完整支持 API 24。

参考：

- Android `Signature` 支持表：https://developer.android.com/reference/java/security/Signature
- Tink Android 安装与 API 范围：https://developers.google.com/tink/setup/java
- Tink 数字签名语义：https://developers.google.com/tink/digital-signature

## 目标

1. 保持现有 SDK v2 payload 完全兼容，不把签名元数据塞进 `schemaVersion: 2`。
2. 验证规则包的发布者身份、内容完整性、有效期和 revision 单调性。
3. 当前包损坏时，仅恢复到本机此前验证过且仍有效的上一版本。
4. 把规则来源契约从本地存储实现中解耦，使本地包、官方签名包和未来测试源使用同一运行时接口。
5. 所有网络、磁盘、解析和密码学操作都留在播放热路径之外。
6. 在没有正式生产公钥和固定官方端点时保持功能关闭，不生成或提交生产私钥。

## 非目标

- 本阶段不实现 HTTP 下载、ETag、定时任务、推送或用户可配置 URL。
- 不实现社区多源聚合、跨包规则合并或本地规则覆盖单条官方规则。
- 不开放自动跳过；签名只证明来源和完整性，不证明规则不会误命中。
- 不让采集 APK 或 H5 持有发布私钥。它们只能提交候选，签名发生在受控 CI 或服务端。
- 不导入第三方 APK 的二进制、反编译代码、`RuleLoader` 或 `SafeMatcherWorker`。

## 方案选择

### 采用：Tink Android + Ed25519

原因：

- 在项目 `minSdk 24` 上提供一致实现，不依赖不同厂商系统 Provider；
- 使用受维护的 `PublicKeyVerify` 原语，客户端只持有公钥；
- Ed25519 签名固定长度、确定性强，适合规则包发布；
- 后续可以在可信密钥注册表中增加算法和密钥，而不修改 payload 或 matcher。

代价是增加一个密码学依赖和少量包体。本项目只启用数字签名能力，不引入 Tink 的网络、密钥生成 UI 或私钥存储。

### 未采用：系统 ECDSA P-256

优点是 API 24 可直接使用且没有新依赖；缺点是偏离已确定的 Ed25519 协议，并把 Provider 差异、签名编码和延展性留给业务代码。

### 未采用：独立 EdDSA/Bouncy Castle 实现

它同样能兼容旧 Android，但引入第二套密码学 Provider 或较窄的维护依赖，供应链和长期升级成本高于 Tink。

## 信任边界与威胁模型

### 资产

- 客户端内置的可信公钥和密钥状态；
- 已验证规则包、上一版本和 revision 高水位；
- 现有本地用户规则，不应被远程包覆盖或删除；
- 播放运行时的稳定性，不应被超大包、深层 JSON 或密码学异常拖垮。

### 外部不可信输入

- 完整签名包 JSON；
- 包内 metadata、payload、摘要、keyId 和签名；
- 未来更新器返回的 HTTP 状态、header 和正文；
- 磁盘上的 current、previous 和 state 文件，可能因崩溃或损坏而不完整。

### STRIDE 控制

| 威胁 | 控制 |
|---|---|
| 冒充发布者 | keyId 只能解析到客户端内置的可信公钥，Tink Ed25519 验签 |
| 内容篡改 | 签名覆盖固定领域分隔、全部安全元数据和规范 payload 的 SHA-256 |
| revision 降级 | 每个 packageId 持久化最高已接受 revision，低版本永不重新安装 |
| 同 revision 换包 | 相同 revision 只允许相同 payload 摘要，摘要不同视为发布冲突 |
| 过期包重放 | createdAt、expiresAt、密钥有效期和设备时钟窗口共同校验 |
| 资源耗尽 | 原始包、payload、字段长度、JSON 深度、token 数和规则规模均有硬上限 |
| 错误信息泄漏 | 只输出固定错误码和计数，不记录包正文、签名、公钥、URL 或规则内容 |

本设计不防御已 root 且能任意修改应用私有目录或进程内存的本地攻击者。清除应用数据会同时清除 revision 高水位，这是 Android 应用数据模型的预期结果。

## 模块边界

### `AdAudioRuleSource`

从 `AdAudioRuntimeController` 中提取顶层接口：

```java
public interface AdAudioRuleSource {
    AdAudioRuleSnapshot load();
}
```

它只返回当前可用于运行时的不可变快照，不暴露网络、Tink、文件路径或缓存状态。

### `AdAudioRuleSnapshot`

从 `AdAudioRuleStore.Snapshot` 提取顶层不可变记录，保留现有五个字段：

```java
public record AdAudioRuleSnapshot(
        String sourceId,
        String version,
        AudioFingerprintRuleSet ruleSet,
        List<String> warnings,
        String lastError) {
}
```

运行时只依赖这五个稳定字段。签名 revision、过期时间和缓存槽位属于签名仓库内部状态，不扩散到 matcher。

### `AdAudioRuleStore`

继续管理用户本地 SDK v2 JSON，并实现 `AdAudioRuleSource`。文件名、2 MiB 上限和现有本地导入行为不变。

### `SignedRulePackageCodec`

负责外层信封的严格流式预检、字段解析、payload 规范化、摘要校验和确定性签名输入构造。它不访问磁盘，不选择公钥，也不决定 revision 是否可接受。

### `TrustedRuleKeyRegistry`

按 `keyId` 查找可信 `PublicKeyVerify` 及密钥状态。每个条目包含算法、启用时间、停用时间和 `ACTIVE`、`RETIRED`、`REVOKED` 状态：

- 安装新包只接受 `ACTIVE` 密钥；
- 加载本机缓存可接受创建时有效的 `ACTIVE` 或 `RETIRED` 密钥；
- `REVOKED` 密钥签发的包始终拒绝，包括缓存；
- 未知 keyId 直接失败，不尝试网络取钥。

生产注册表只包含公钥。正式公钥由发布流程提供；仓库不得包含对应私钥。测试使用进程内临时密钥对。

Tink Ed25519 keyset 必须使用 `RAW` output prefix。这样信封中的签名是可由其他标准 Ed25519 实现验证的 64 字节原始签名，不携带 Tink 专用的 5 字节 key prefix。

### `SignedRulePackageVerifier`

按固定顺序组合 codec、时钟、密钥注册表和 Tink：

1. 检查原始字节数和 UTF-8；
2. 严格解析外层信封并拒绝未知字段；
3. 使用现有 `AudioFingerprintRuleCodec` 严格解析 payload；
4. 将 payload 重新编码为现有规范 SDK v2 JSON；
5. 计算 SHA-256，并与 `payloadSha256` 常量时间比较；
6. 校验 packageId、revision、时间窗口和密钥状态；
7. 构造确定性签名输入；
8. 最后调用 Tink `PublicKeyVerify.verify`。

只有全部成功才返回 `VerifiedRulePackage`。不向调用方暴露半解析对象。

### `SignedRulePackageStore`

负责单个预配置 packageId 的安装、revision 高水位、current/previous 两槽缓存和崩溃恢复。它不下载数据，不参与播放生命周期。

### `SignedRulePackageSource`

实现 `AdAudioRuleSource`，只从 `SignedRulePackageStore` 读取已经验证的 current 或 previous，并转换为运行时快照：

- `sourceId` 为 `signed:<packageId>`；
- `version` 为 `<revision>:<payloadDigest 前 16 个十六进制字符>`；
- 恢复 previous 时增加固定 warning；
- 无可用包时返回空规则及固定错误码。

### `PrioritizedAdAudioRuleSource`

首版不合并两个规则集，使用明确优先级：

1. 本地用户规则存在且有效时使用本地规则；
2. 本地为空时使用官方签名规则；
3. 本地损坏但签名规则有效时使用签名规则，并附加本地源失败 warning；
4. 两者都不可用时返回空规则及最具体的错误码。

这样保留采集和测试工作流，也避免算法配置、重复 ruleId 和局部覆盖语义失控。

## 线格式

外层信封使用严格 JSON，payload 保持原始 SDK v2 对象：

```json
{
  "packageSchemaVersion": 1,
  "packageId": "official.ad-audio",
  "revision": 42,
  "createdAtEpochMs": 1786896000000,
  "expiresAtEpochMs": 1789488000000,
  "payloadSha256": "64-char-lowercase-hex",
  "payload": {
    "schemaVersion": 2,
    "algorithm": {
      "id": "spectral-sequence-v2",
      "sampleRate": 16000,
      "windowMs": 512,
      "hopMs": 256,
      "bandCount": 16
    },
    "rules": []
  },
  "signature": {
    "keyId": "release-1",
    "algorithm": "ED25519",
    "value": "base64url-without-padding"
  }
}
```

### 字段约束

- `packageSchemaVersion` 必须为整数 1；
- `packageId`、`keyId` 匹配 `[A-Za-z0-9._-]{1,64}`；
- `revision` 为大于 0 的有符号 64 位整数；
- 时间使用非负 epoch milliseconds，避免 ISO 解析和时区差异；
- `expiresAtEpochMs` 必须晚于 `createdAtEpochMs`，最长有效期 90 天；
- 允许设备时钟向未来偏差 5 分钟，超过则 `PACKAGE_NOT_YET_VALID`；
- `payloadSha256` 只允许 64 位小写十六进制；
- `signature.value` 必须是无 padding 的 base64url，解码后 Ed25519 签名固定 64 字节；
- 外层、signature 和 payload 均拒绝未知字段、重复字段、非整数数字和 trailing data；
- 原始信封上限为 `AdAudioRuleStore.MAX_IMPORT_BYTES + 64 KiB`，规范 payload 仍受 2 MiB 上限约束。

## 确定性签名输入

不对 JSON 文本本身签名，也不依赖对象字段顺序或空白。签名覆盖以下大端二进制序列：

```text
ASCII "webhtv.ad-audio.package/v1" + 0x00
u16(packageId UTF-8 byte length) + packageId bytes
i64(revision)
i64(createdAtEpochMs)
i64(expiresAtEpochMs)
u16(keyId UTF-8 byte length) + keyId bytes
u16(algorithm UTF-8 byte length) + algorithm bytes
32 raw bytes of SHA-256(canonical SDK v2 payload UTF-8)
```

所有字符串先通过 ASCII 字段约束，因此 Java、CI 和未来服务端可以无歧义复现。`payloadSha256` 文本字段必须等于最后 32 字节的小写十六进制表示。

仓库增加固定黄金向量：给定 metadata、规范 payload 和测试公钥，签名输入十六进制、摘要和 Ed25519 签名必须完全一致。该向量是未来作者端/服务端互操作契约。

## revision 与时间语义

- revision 在单个 packageId 内严格单调增加；
- `revision < highestAcceptedRevision`：拒绝 `REVISION_ROLLBACK`；
- `revision == highestAcceptedRevision` 且摘要相同：幂等成功；current 已是同一完整包时不重写，current 缺失或损坏时允许用该候选恢复缓存；
- `revision == highestAcceptedRevision` 且摘要不同：拒绝 `REVISION_CONFLICT`；
- `revision > highestAcceptedRevision`：验证全部通过后才安装；
- current 或 previous 过期后不可激活，不提供隐式宽限期；
- previous 恢复不降低高水位，未来下载仍必须高于历史最高 revision；
- RETIRED 密钥不能安装新包，但其已缓存且创建时有效的包可以继续用到包自身 expiresAt；
- REVOKED 密钥没有缓存宽限期。

## 持久化与恢复

每个 packageId 使用独立目录，文件为：

```text
current.json
previous.json
state.json
*.tmp
```

`state.json` 只保存 schemaVersion、packageId、highestAcceptedRevision 和对应 payload digest，不保存规则正文。

### 安装顺序

1. 在内存中完成全部解析、摘要、时间、密钥、签名和 revision 校验；
2. 将候选原始信封写入 `current.tmp`，flush 并 `FileDescriptor.sync()`；
3. 若这是更高 revision 且 current 有效，将其复制到 `previous.tmp`，sync 后原子替换 previous；相同 revision 的恢复安装不轮换 previous；
4. 原子替换 current；
5. 写入并原子替换 state；
6. 删除遗留临时文件。

所有原子移动沿用现有 `ATOMIC_MOVE`，文件系统不支持时退化为同目录 `REPLACE_EXISTING`。原始已签名信封必须保留，不能只缓存解析结果，否则应用升级或密钥撤销后无法重新验证。

### 加载与故障恢复

1. 清理遗留 `.tmp`；
2. 独立读取并重新验证 current、previous 和 state；
3. 高水位取有效 state、current、previous 中的最大 revision；
4. current 有效则使用 current；
5. current 无效时，仅在 previous 签名、packageId、时间和密钥状态仍有效时恢复 previous；
6. 恢复 previous 时不覆盖 current，不降低高水位，并返回 `CACHE_RECOVERED` warning；
7. 两槽均无效则返回空规则和固定错误码。

普通“清除已下载规则”只删除 current 和 previous，保留 state 高水位。只有清除整个应用数据或开发者专用重置信任状态才能删除高水位。

## 运行时数据流

```text
受控 CI / 服务端
    -> 生成规范 SDK v2 payload
    -> 质量检查与人工审核
    -> Ed25519 私钥签名
    -> 签名信封

未来后台更新器（本阶段不实现）
    -> 固定 HTTPS 端点
    -> SignedRulePackageStore.install
    -> 验签、反回滚、原子缓存

PlayerManager / AdAudioRuntimeController
    -> PrioritizedAdAudioRuleSource.load
    -> 本地规则优先，否则读取已验证签名缓存
    -> 现有 matcher 热更新
```

`AdAudioRuntimeController.reloadRules()` 不做网络请求，不解析信封，也不直接调用 Tink。它继续只消费 `AdAudioRuleSnapshot`。

## 错误语义与诊断

对外只使用稳定错误码：

| 错误码 | 含义 |
|---|---|
| `PACKAGE_TOO_LARGE` | 信封或 payload 超限 |
| `PACKAGE_MALFORMED` | UTF-8、JSON、字段类型或 base64url 非法 |
| `UNSUPPORTED_PACKAGE_SCHEMA` | 外层版本不支持 |
| `PACKAGE_ID_MISMATCH` | 包来源与预配置 packageId 不一致 |
| `UNSUPPORTED_SIGNATURE_ALGORITHM` | 当前只接受 `ED25519` |
| `UNKNOWN_KEY` | keyId 不在内置注册表 |
| `KEY_NOT_VALID` | 密钥未生效、已停用或已撤销 |
| `PAYLOAD_DIGEST_MISMATCH` | payload 与声明摘要不一致 |
| `PAYLOAD_INVALID` | SDK v2 payload 未通过现有严格 codec |
| `SIGNATURE_INVALID` | Tink 验签失败 |
| `PACKAGE_NOT_YET_VALID` | createdAt 超过允许时钟偏差 |
| `PACKAGE_EXPIRED` | 包已过期 |
| `REVISION_ROLLBACK` | revision 低于高水位 |
| `REVISION_CONFLICT` | 同 revision 出现不同摘要 |
| `CACHE_IO_FAILED` | 缓存读写失败 |
| `NO_VALID_SIGNED_PACKAGE` | current 和 previous 均不可用 |

诊断只记录错误码计数、是否恢复 previous 和当前 sourceId，不记录信封正文、摘要全文、签名、公钥、规则 ID 或未来下载 URL。

## 依赖与密钥发布

- 在 version catalog 固定 `tink = "1.23.0"`；
- app 只依赖 `libs.tink.android`；
- R8/ProGuard 采用 Tink 官方默认配置，不添加宽泛 keep；
- 测试密钥只存在于测试源码或运行时生成，不复用于生产；
- 生产公钥通过独立发布流程交付并接受代码审查；
- 生产私钥保存在 CI/HSM/受控签名服务中，永不进入 APK、Git、日志或 H5；
- 没有正式公钥时，生产 `TrustedRuleKeyRegistry` 为空，签名源返回 `UNKNOWN_KEY`，现有本地规则仍可用。

## 测试策略

### 契约测试

- 外层 schema、字段长度、未知/重复字段、整数边界和 trailing data；
- 固定签名输入、payload 摘要和 Ed25519 黄金向量；
- SDK v2 payload 规范化前后摘要一致；
- Java 端导出的签名输入可由独立脚本复现。

### 安全测试

- 分别篡改 packageId、revision、时间、keyId、algorithm、payload、摘要和签名，全部拒绝；
- 未知、RETIRED、REVOKED 和时间范围外密钥；
- 超大输入、深层 JSON、过多 token、非法 UTF-8 和 base64url；
- 低 revision、同 revision 同摘要幂等、同 revision 不同摘要冲突；
- 过期 current 不能通过 previous 绕过有效期。

### 持久化测试

- 首次安装、升级、幂等重装和普通清除；
- current 损坏后恢复 previous；
- current/previous/state 分别截断或损坏；
- 每个原子写阶段注入 IOException，重启后只能看到完整旧状态或完整新状态；
- 恢复 previous 后高水位保持历史最高值；
- state 损坏时从有效槽位重建高水位。

### 集成回归

- `AdAudioRuleStore` 本地导入行为和文件名不变；
- 本地规则优先级、签名回退和双源都失败的错误语义；
- `AdAudioRuntimeController` 仍只在已有规则、UI 已绑定、Exo VOD 可 seek 时激活；
- 热更新、generation 隔离、matcher、撤销和字幕 consumer 测试全部继续通过；
- leanback 单元测试和 mobile Java 编译通过。

## 验收标准

1. API 24 JVM/Android 测试路径不依赖系统 Ed25519 Provider。
2. 未通过签名、摘要、时间、密钥状态和 revision 任一校验的包不会到达 `AudioFingerprintMatcher`。
3. 任意单文件写入中断后，重启只能加载完整验证过的 current 或 previous。
4. previous 恢复不降低 revision 高水位，过期或撤销包不恢复。
5. 没有生产公钥、网络端点或签名缓存时，现有本地规则功能和默认关闭行为完全不变。
6. 仓库和 APK 中不存在生产私钥。

## 后续阶段

本设计完成后，远程更新单独设计 `SignedRulePackageUpdater`：固定 HTTPS allowlist、禁止重定向、无 Cookie/认证继承、正文流式上限、ETag、退避和后台调度。更新器只能把字节交给本设计的 `install`，不能绕过 verifier 或直接写缓存。

作者端质量管线和 H5/真实样本 oracle 仍是独立工作，不与客户端签名仓库耦合。
