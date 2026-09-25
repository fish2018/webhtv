# 观影记录同步稳定身份与主备地址匹配设计

> **文档状态：** 阶段性实现（规范、服务端身份解析与 Android 接线已完成；Go/Rust 仅完成源码接线，未在当前环境编译）。
> **目标：** 指导 WebHTV 客户端、内置远端服务端和兼容迁移的完整实现，并作为提交前、灰度期间和发布后的验收依据。
> **适用版本：** 当前 `5.6.0` 测试版及后续版本。
> **最后更新：** 2026-09-24（Asia/Shanghai）

## Recovery anchor

- **目标：** 在不废弃现有 `interfaceKey` 稳定身份规则的前提下，实现两台设备分别手动新增同一主备接口时自动匹配，并找回旧版 URL 哈希空间中的观影记录。
- **验收核心：** 地址匹配只负责发现身份；正式同步始终使用稳定 `interfaceKey`；精确地址命中可自动采用已有身份，只有裸域名命中不能静默合并；旧数据、删除墓碑、游标和冲突均不能丢失或串线。
- **当前状态：** 已完成协议 v1 规范化 fixture、三套 JS 服务端身份解析/别名路由、Cloudflare Durable Object registry、Go/Rust 源码接线、Android 47→48 迁移、canonical/legacy 读取和 Mobile/Leanback 配置入口接线；Go/Rust 因当前环境无工具链未编译，设备端与完整验收矩阵仍未完成。
- **当前相关源码：** `app/src/main/java/com/fongmi/android/tv/playback/PlaybackConfigIdentity.java`、`PlaybackRemoteSyncer.java`、`PlaybackRecord.java`、`RemoteSyncConfig.java`、`app/src/main/java/com/fongmi/android/tv/bean/Config.java`、`serverless/*/playback-sync*`。
- **下一动作：** 在安装 Go/Rust 工具链后分别运行 `go test ./...` 与 `cargo test`，补齐 Rust/Go 共享 fixture；随后编译 Leanback、执行 Android 单元测试和测试包覆盖安装验证。未通过本文第 14 节全部验收项前不得宣称兼容完成。

---

## 1. 决策摘要

### 1.1 最终采用的身份分层

系统同时保留三种不同语义的标识，禁止互相替代：

| 标识 | 语义 | 是否作为正式同步空间键 | 生命周期 |
| --- | --- | --- | --- |
| `interfaceKey` | 一个点播接口的长期正式身份 | **是** | 创建后长期稳定，不随地址、主备顺序、显示名称改变 |
| `addressMatchKey` / `endpointMatchKey` | 从当前及历史主备地址得到的匹配线索 | 否 | 可随地址增删产生新线索，旧线索保留为别名 |
| `legacyConfigKey` | 旧版 `SHA-256(URL)` 同步身份 | 否，仅用于迁移 | 兼容窗口内保留；不再生成新的正式身份 |

核心原则：

```text
地址/域名匹配：找到“可能是同一个接口”
interfaceKey：决定“最终进入哪个同步空间”
legacyConfigKey：找回旧版本已经写入的数据
```

### 1.2 两台设备手动新增的行为

两台设备分别新增配置时，客户端为本地配置先生成临时或正式 UUID；在第一次同步前向同一 Token 下的服务端注册地址匹配线索：

```text
设备 A：A 主地址、B 备用地址 -> interfaceKey = K_A
设备 B：B 主地址、C 备用地址 -> 临时 interfaceKey = K_B
```

如果 B 在两边的地址集合中都生成了相同的**精确地址匹配键**，且该键没有身份冲突，服务端返回：

```text
canonicalInterfaceKey = K_A
```

设备 B 放弃临时 `K_B`，采用 `K_A`；本地主备顺序仍按设备 B 的用户选择保存，缺少的地址追加到本地列表末尾。此后双方均使用 `K_A` 同步。

### 1.3 “基于域名匹配”的边界

域名参与匹配，但**不能把裸域名作为无条件唯一身份**：

- `host + port + path + query` 精确匹配（允许 HTTP/HTTPS 传输差异）时，可自动匹配；
- 仅 `host` 相同、路径不同，只能作为候选，必须用户确认；
- 同一个地址匹配键对应多个不同 `interfaceKey` 时，必须冲突，不得静默覆盖；
- 共享 CDN、公共文件域名、相同根域名不能直接合并。

这样既支持“主备地址中任意一个精确地址命中就匹配”，又不会因为 `api.example.com/a.json` 与 `api.example.com/b.json` 共用域名而串线。

### 1.4 不采用的方案

不把以下方案作为正式修复：

1. **恢复为 `SHA-256(当前 URL)`**：会重新导致换域名、切备用地址后同步空间变化，等于废弃稳定身份设计。
2. **把主备地址去重排序后的集合哈希直接当 `configKey`**：增删备用地址就会改变身份，不能满足长期同步。
3. **仅比较裸域名并自动合并**：误合并风险过高，不能区分同域名不同接口。
4. **要求用户重新填写 Token**：问题是身份空间变化，不是 Token 必然失效；重新填写可能掩盖并加重数据迁移问题。
5. **只在界面上隐藏“其他配置”**：不能修复同步、续播、删除、收藏和配置归属。

---

## 2. 当前实现与问题证据

### 2.1 当前客户端行为

当前 `PlaybackConfigIdentity` 已使用稳定身份：

```java
// app/src/main/java/com/fongmi/android/tv/playback/PlaybackConfigIdentity.java
public static String currentKey() {
    return keyForCid(VodConfig.getCid());
}
```

`keyForCid()` 优先读取 `Config.interfaceKey`，缺失时生成并持久化。`PlaybackRecord.from()` 将该值写入播放事件的 `configKey`，`PlaybackRemoteSyncer` 使用它发送：

```http
X-WebHTV-Config-Key: <interfaceKey>
```

相关文件：

- `app/src/main/java/com/fongmi/android/tv/playback/PlaybackConfigIdentity.java`
- `app/src/main/java/com/fongmi/android/tv/playback/PlaybackRecord.java`
- `app/src/main/java/com/fongmi/android/tv/playback/PlaybackRemoteSyncer.java`
- `app/src/main/java/com/fongmi/android/tv/playback/PlaybackWebhookSender.java`
- `app/src/main/java/com/fongmi/android/tv/playback/RemoteSyncConfig.java`

### 2.2 当前数据库行为

`Config` 已有：

- `interfaceKey`：稳定接口身份；
- `urlsJson`：主地址和备用地址集合，顺序表达连接优先级；
- `(interfaceKey, type)` 唯一索引。

`MIGRATION_45_46` 为旧配置生成随机身份，`MIGRATION_46_47` 增加历史 `sourceBindingKey`。当前数据库版本为 47。相关证据：

- `app/src/main/java/com/fongmi/android/tv/bean/Config.java`
- `app/src/main/java/com/fongmi/android/tv/db/Migrations.java`
- `app/src/main/java/com/fongmi/android/tv/db/AppDatabase.java`
- `app/src/main/java/com/fongmi/android/tv/bean/History.java`

### 2.3 当前服务端行为

Cloudflare、Deno、Vercel、Go、Rust 版本都按以下逻辑隔离同步空间：

```text
space = hash(token) + ":" + hash(configKey)
```

例如：

- `serverless/webhtv-remote-vercel/api/playback-sync.js`
- `serverless/webhtv-remote-deno/playback-sync.js`
- `serverless/webhtv-remote-go/playback.go`
- `serverless/webhtv-remote-rust/src/playback.rs`

服务端当前能接受新的 UUID 形式 `configKey`，但没有将旧 URL 哈希空间与新 `interfaceKey` 空间建立别名，也没有在两台设备手动新增时解析地址匹配关系。

### 2.4 用户可见的回归

旧版本：

```text
configKey = SHA-256(URL)
```

新版迁移后：

```text
configKey = interfaceKey(UUID)
```

同一个 Token 下，服务端看到的是两个不同空间。通常不会出现 HTTP 错误，而是返回空增量：

```text
HTTP 200
changes = []
```

因此用户会认为“同步不了了”，但旧记录多数仍在旧空间中，并非已经删除。

### 2.5 相关历史提交

| 完整 commit | 内容 | 对本文的影响 |
| --- | --- | --- |
| `eb76ab8a82841cfd4c58e865a4a3391363636c83` | 增加稳定接口身份和备用地址 | 当前 `interfaceKey` 基线，必须保留 |
| `5bdd9372e3509f379ebfbcb89a3673f9ce7fdb60` | 持久化历史接口绑定 | 本机历史仍以 `cid`/绑定字段工作 |
| `1f008c0da92f8360bd7f23484b97b39db86bb97e` | 远端配置更新保留接口身份 | 远端配置入口必须继续优先使用 `interfaceKey` |
| `82a069f74611d92e01044db0801eef5f695da8ef` | 增加持久化观影记录同步服务 | 服务端协议和多实现兼容基线 |
| `f5d63d16eda9c6b640a2a7a7a1dd5923f1f5420a` | 删除同步设计/实现 | 删除墓碑和游标不能被迁移破坏 |

> 注：若上述历史提交在实现前被重新整理，应以实际 Git 历史中的完整 ID 和内容复核；表中前两项和 `82a069f...` 是当前仓库已核验的身份/同步基线。实现阶段不得用短哈希替代任务文档中的完整提交记录。

---

## 3. 目标、非目标与验收合同

### 3.1 必须实现的目标

1. 两台设备分别手动新增同一接口时，只要至少一个精确主备地址命中，就能在同一 Token 和同一配置类型范围内自动采用同一个正式 `interfaceKey`。
2. 主地址、备用地址顺序不同不影响匹配；本地主备优先级不被悄悄覆盖。
3. 增加、删除、替换备用地址不改变正式 `interfaceKey`。
4. 旧版 URL 哈希空间中的记录能在兼容窗口内迁移/读取到正式空间。
5. 已同步的进度、完播事件、删除墓碑、速度、TMDB 季集进度等业务语义不因身份迁移而丢失或复活。
6. 两个不同 `interfaceKey` 即使使用相同域名，也不会静默串线。
7. 同一接口类型之外的配置（VOD、Live、Wall）不因地址相同而互相合并。
8. 服务端多个实现的协议、规范化和冲突语义一致。
9. 网络失败、并发注册、重复请求、服务端旧版本和回滚均有确定行为。
10. 用户能看到匹配、迁移、确认、冲突和失败原因，不把空同步误报为数据删除。

### 3.2 明确非目标

- 不从影片名称、站点列表内容、接口显示名称推断身份。
- 不对仅根域名相同的两个接口自动合并。
- 不在没有认证 Token 的情况下建立跨设备身份关联。
- 不在本任务中改变 `History.cid` 的基础数据模型，也不把本机 Room 自增 `cid` 当跨设备身份。
- 不强制所有设备使用相同的主地址顺序。
- 不在服务端保存未哈希的完整 URL，除非已有部署明确需要且完成隐私评审。

### 3.3 不可破坏的不变量

```text
I1: interfaceKey 一旦作为正式身份使用，地址变化不得自动重算。
I2: 一个 (token, configType, canonicalInterfaceKey) 只能对应一个逻辑身份。
I3: 一个强地址匹配键不能无提示地绑定到多个明确不同身份。
I4: 删除墓碑的时间优先级高于较旧的进度 upsert。
I5: 游标只有在该空间本次返回完全处理成功后才能推进。
I6: 任何迁移操作必须可重试、幂等、可审计、可回滚。
I7: 旧客户端继续按旧 configKey 请求时，兼容窗口内不能被新身份迁移破坏。
I8: 仅匹配 host 不能绕过人工确认。
```

---

## 4. 术语与数据模型

### 4.1 术语

| 术语 | 定义 |
| --- | --- |
| `configType` | 配置类型，当前至少为 `vod`；服务端分区和匹配都必须带上 |
| `interfaceKey` | 客户端 `Config.interfaceKey`，服务端正式逻辑身份 |
| `provisionalInterfaceKey` | 手动新增且尚未向服务端解析前的本地 UUID |
| `strictAddressKey` | 包含 scheme、host、port、path、query 的规范化地址哈希 |
| `endpointMatchKey` | 忽略 HTTP/HTTPS 传输差异、保留 host/port/path/query 的强匹配键 |
| `hostMatchKey` | 仅包含 host（和配置类型）的弱候选键，不可自动合并 |
| `legacyConfigKey` | 旧客户端使用的 `SHA-256(URL)` |
| `canonicalInterfaceKey` | 服务端选定的正式身份；客户端最终必须使用它 |
| `identityAlias` | 指向 `canonicalInterfaceKey` 的旧接口身份、地址键或 legacy key |
| `identityGroup` | 同一 Token、配置类型和正式身份下的记录集合 |
| `claim` | 客户端提交地址线索并请求采用/注册正式身份的动作 |
| `adopt` | 当前配置放弃本地临时身份并采用服务端正式身份 |
| `merge` | 将两个已有身份的远端记录和墓碑按规则合并 |

### 4.2 客户端 `Config` 建议新增字段

当前已有 `interfaceKey`、`urlsJson`。建议在数据库版本 48 中增加：

```java
@SerializedName("legacyConfigKeysJson")
private String legacyConfigKeysJson;

@SerializedName("addressMatchAliasesJson")
private String addressMatchAliasesJson;

@SerializedName("identityResolutionState")
private String identityResolutionState;
```

推荐语义：

```json
{
  "interfaceKey": "uuid-a",
  "url": "https://a.example/api.json",
  "urls": [
    "https://a.example/api.json",
    "https://b.example/api.json"
  ],
  "legacyConfigKeys": [
    "sha256(old-primary-url)",
    "sha256(old-backup-url)"
  ],
  "addressMatchAliases": [
    "endpoint-key-for-removed-address"
  ],
  "identityResolutionState": "resolved"
}
```

`identityResolutionState` 取值：

| 值 | 含义 |
| --- | --- |
| `unresolved` | 本地新建，尚未向服务端认领 |
| `resolving` | 已发起解析，等待结果 |
| `resolved` | 当前 `interfaceKey` 已通过服务端确认 |
| `offline` | 网络不可用，保留临时身份并等待重试 |
| `confirm_required` | 发现弱匹配或已有数据身份，需要用户确认 |
| `conflict` | 地址或身份映射冲突，禁止自动合并 |
| `migration_pending` | 已确认采用身份，但远端旧空间尚未完成迁移 |

字段可以以 JSON 字符串存入 `Config`，但必须有明确的 Java/JS/Go/Rust 共享结构和上限：

- `legacyConfigKeys` 最多 16 个；
- `addressMatchAliases` 最多 32 个；
- 每个 key 最多 128 个 ASCII 字符；
- 保存前去重、排序仅用于集合序列化，不改变 `urlsJson` 主备顺序；
- 所有新字段允许为空，旧数据库迁移后必须可正常打开。

### 4.3 服务端身份注册表

服务端现有播放空间之外增加逻辑身份注册表。实现可以使用 KV、SQLite、JSON 文件或 Durable Object，但语义必须一致：

```text
IdentityRecord {
  tokenHash: string,
  configType: string,
  canonicalInterfaceKey: string,
  strictKeys: set<string>,
  endpointKeys: set<string>,
  hostKeys: set<string>,
  legacyKeys: set<string>,
  aliases: set<string>,
  createdAt: integer,
  updatedAt: integer,
  lastSeenAt: integer,
  migrationEpoch: string,
  state: active | conflict | retired
}
```

索引必须至少支持：

```text
(tokenHash, configType, canonicalInterfaceKey)
(tokenHash, configType, strictKey)   -> 0..1 identity
(tokenHash, configType, endpointKey) -> 0..1 identity
(tokenHash, configType, legacyKey)   -> 0..1 identity
```

索引值为多个身份时不能覆盖，必须进入冲突状态。

---

## 5. 地址规范化与匹配算法

### 5.1 设计要求

匹配算法必须在 Android Java、Cloudflare/Deno/Vercel JavaScript、Go、Rust 中产生一致结果。不能直接依赖某个运行时的 URL 字符串化差异。实现前先建立共享规范和测试向量，所有服务端版本使用同一组向量。

### 5.2 输入限制

只接受：

- 绝对 `http` 或 `https` URL；
- 非空 host；
- 无 userinfo（禁止 `user:password@host`）；
- 总长度不超过 4096 字节；
- path、query 经过长度限制后仍可被安全解析。

以下输入不参与自动匹配，但仍可作为普通地址保存并在播放时由现有 URL 校验处理：

- `file:`、`content:`、自定义 scheme；
- 带用户密码的 URL；
- 无法在所有实现中一致解析的异常 URL。

### 5.3 规范化步骤

对每个 URL `u` 执行：

1. 去除首尾 ASCII 空白；内部空白视为非法。
2. 解析 scheme、host、port、path、query、fragment。
3. scheme 转小写。
4. host 使用 IDNA 转 ASCII 后转小写；IPv6 保留规范方括号形式。
5. 删除默认端口：`http:80`、`https:443`；非默认端口必须保留。
6. 删除 fragment，因为 fragment 不发送给 HTTP 服务端。
7. 空 path 规范为 `/`。
8. path 执行 RFC 3986 dot-segment 处理；保留尾部 `/` 语义。
9. 只对百分号编码的 unreserved 字符进行解码/统一；不得解码 `/`、`?`、`#` 等 reserved 字符。
10. query 默认保留参数顺序、重复参数和参数值，不得擅自排序或删除参数；仅统一百分号编码大小写和 unreserved 字符。
11. 生成 `CanonicalUrlV1`。规范化失败则不生成任何匹配键。

规范化必须写成独立纯函数，禁止在 Android 和各服务端分别“凭感觉”实现。

### 5.4 三层匹配键

对 `configType` 和规范化地址生成三类键，均使用小写十六进制 SHA-256：

```text
strictAddressKey = SHA256(
  "webhtv.address.strict.v1\n"
  + configType + "\n"
  + scheme + "\n"
  + host + "\n"
  + port + "\n"
  + path + "\n"
  + query
)

endpointMatchKey = SHA256(
  "webhtv.address.endpoint.v1\n"
  + configType + "\n"
  + host + "\n"
  + port + "\n"
  + path + "\n"
  + query
)

hostMatchKey = SHA256(
  "webhtv.address.host.v1\n"
  + configType + "\n"
  + host + "\n"
  + port
)
```

规则：

- `strictAddressKey` 相同：完全相同地址，自动匹配。
- `endpointMatchKey` 相同：仅 scheme/默认传输差异，且结果唯一时自动匹配。
- 只有 `hostMatchKey` 相同：只返回候选，默认不自动采用身份。
- 主备列表是集合参与匹配；不能把列表排序哈希作为正式身份。

### 5.5 规范化测试向量

实现必须至少覆盖以下逻辑（具体 hash 由共享测试工具生成并固定）：

| 输入 A | 输入 B | 期望 |
| --- | --- | --- |
| `HTTPS://API.Example.com:443/config.json#x` | `https://api.example.com/config.json` | strict 相同，endpoint 相同 |
| `http://api.example.com:80/config.json` | `https://api.example.com/config.json` | strict 不同，endpoint 相同 |
| `https://api.example.com/a.json` | `https://api.example.com/b.json` | host 相同，endpoint 不同；不得自动合并 |
| `https://api.example.com/a.json?x=1&y=2` | `https://api.example.com/a.json?y=2&x=1` | 默认 query 不同；不得擅自认为相同 |
| `https://api.example.com/a.json` | `https://api.example.com/a.json#fragment` | strict 相同 |
| `https://api.example.com/a/../b.json` | `https://api.example.com/b.json` | 规范化后 endpoint 相同 |
| `https://a.example.com/api.json` | `https://b.example.com/api.json` | 不匹配，仅不能依靠根域名 |
| `https://api.example.com` | `https://api.example.com/` | endpoint 相同 |

### 5.6 主备列表去重与合并

地址列表的**匹配计算**和**本地保存顺序**分离：

- 计算键时，对规范化地址去重；
- 本地 `urlsJson` 保留用户当前主地址在第一个位置；
- 现有地址顺序不被服务端响应重排；
- 匹配成功后，新设备缺少的地址追加到本地列表末尾；
- 远端身份注册表保存键集合，不承担某台设备的主备优先级；
- 删除备用地址前，先把它的匹配键写入 `addressMatchAliases`，避免旧设备立即失联；
- 地址重新加入时复用旧键，不重复创建别名。

### 5.7 匹配决策算法

服务端对同一 `(tokenHash, configType)` 执行：

```text
1. 验证请求 Token、configType、key 数量和格式。
2. 如果 submittedInterfaceKey 已绑定唯一 identity：
   2.1 若提交的 strict/endpoint/legacy key 均未指向其他 identity，更新线索并返回 keep。
   2.2 若任一 key 指向其他 identity，返回 conflict。
3. 查询 submitted strictAddressKeys 的身份集合。
4. 若结果恰好一个，返回 adopt 或 merge。
5. 若无 strict 命中，再查询 endpointMatchKeys。
6. 若结果恰好一个，返回 adopt 或 merge，并标记 matchedBy=endpoint。
7. 若结果多个，返回 conflict。
8. 若只有 hostMatchKeys 命中，返回 confirm_required，不修改正式身份。
9. 无任何命中：注册 submittedInterfaceKey 为新 canonical identity，返回 create。
10. 所有注册、索引更新和迁移决定必须在一个原子事务/CAS 中完成。
```

### 5.8 何时允许自动 `adopt`

为了防止已有两套数据被误合并，自动采用只在以下任一条件成立时允许：

- 本地 `provisionalInterfaceKey` 尚未产生远端记录，或服务端确认源空间为空；
- 目标 identity 与源 identity 实际是同一个已注册身份；
- 用户已经在 UI 明确确认“合并当前接口记录”。

如果源、目标两个 identity 都已有观影记录，精确地址命中也返回 `confirm_required`，由用户确认后执行 `merge`。这是“自动匹配新建配置”和“合并两个已有接口”之间的安全边界。

---

## 6. 协议设计

### 6.1 版本和能力

新增协议标识：

```text
schema: webhtv.playback.identity.v1
X-WebHTV-Identity-Version: 2
X-WebHTV-Address-Match-Version: 1
```

`GET /api/playback/sync/status` 和新增解析接口均返回能力：

```json
{
  "capabilities": {
    "playbackSync": true,
    "identityResolve": true,
    "identityAliases": true,
    "legacyUrlHashMigration": true,
    "identityMerge": true
  },
  "identityProtocol": "webhtv.playback.identity.v1",
  "addressMatchVersion": 1
}
```

未支持能力的旧服务端不得被客户端当作支持处理。

### 6.2 身份解析接口

新增接口（两个路径均兼容）：

```text
POST /api/playback/identity/resolve
POST /playback/identity/resolve
```

请求头：

```http
Content-Type: application/json
X-WebHTV-Token: <token>
X-WebHTV-Identity-Version: 2
X-WebHTV-Address-Match-Version: 1
X-WebHTV-Request-Id: <idempotent-request-id>
```

请求体：

```json
{
  "schema": "webhtv.playback.identity.v1",
  "operation": "resolve",
  "configType": "vod",
  "interfaceKey": "local-provisional-uuid",
  "strictAddressKeys": ["..."],
  "endpointMatchKeys": ["..."],
  "hostMatchKeys": ["..."],
  "legacyConfigKeys": ["old-url-sha256"],
  "sourceDataState": "empty",
  "client": "android",
  "appVersion": "5.6.0"
}
```

禁止上传完整 URL；若部署确有审计需要，必须另行完成隐私评审并加密保存。

响应示例：

```json
{
  "ok": true,
  "action": "adopt",
  "canonicalInterfaceKey": "existing-uuid",
  "matchedBy": "endpointMatchKey",
  "matchedKeys": ["..."],
  "sourceInterfaceKey": "local-provisional-uuid",
  "migrationRequired": false,
  "identityEpoch": "20260924-000001",
  "capabilities": {
    "identityResolve": true,
    "identityAliases": true,
    "legacyUrlHashMigration": true,
    "identityMerge": true
  }
}
```

### 6.3 `action` 语义

| action | HTTP | 客户端行为 |
| --- | ---: | --- |
| `create` | 200 | 使用提交的 `interfaceKey`，保存已注册状态 |
| `keep` | 200 | 正式身份未变，追加/刷新匹配线索 |
| `adopt` | 200 | 使用 `canonicalInterfaceKey` 替换本地临时身份 |
| `migration_pending` | 200 | 采用正式身份但必须先完成空间迁移，暂不推进游标 |
| `confirm_required` | 200 | 弹窗展示候选/原因，用户确认前不改变正式身份 |
| `conflict` | 409 | 不改任何身份；提示用户选择或拆分地址 |
| `unsupported` | 404/405/501 | 进入旧服务端兼容模式，不将请求当成失败数据 |
| `invalid` | 400 | 修复请求，不重试相同非法数据 |
| `unauthorized` | 401/403 | 检查 Token，不生成新空间掩盖权限问题 |
| `unavailable` | 503 | 保留本地身份，延迟重试 |

### 6.4 同步接口增加别名

现有播放同步 `GET`/`POST` 保持兼容，新增：

```http
X-WebHTV-Config-Key: <canonicalInterfaceKey>
X-WebHTV-Config-Aliases: <legacy-or-old-key>,<address-alias>
X-WebHTV-Identity-Version: 2
```

由于 Header 长度有限，正式实现可以使用 JSON：

```json
{
  "configKey": "canonical-interface-key",
  "configAliases": ["old-url-sha256", "old-interface-key"]
}
```

约束：

- alias 最多 16 个/请求；
- 单个 alias 只允许 `[a-z0-9._:-]`，长度不超过 128；
- header 与 body 同时存在时必须一致，否则 400；
- 服务端只允许 Token 所属身份使用 alias；
- alias 命中多个正式身份时 409；
- alias 迁移成功后，旧 key 请求在兼容窗口内路由到 canonical 空间。

### 6.5 旧端点兼容

客户端探测能力的顺序：

1. 请求 `/status` 获取 `identityResolve` / `identityAliases`；
2. 若支持，调用 resolve 并使用 canonical 身份；
3. 若返回 404/405/501，保留本地 `interfaceKey`，对已保存的 `legacyConfigKeys` 执行**只读双空间拉取**；
4. 只有服务端明确声明支持幂等兼容写入，或用户开启“旧服务兼容写入”后，才向 legacy 空间双写；
5. 普通自定义 Webhook 默认不双写，避免用户收到重复事件。

旧服务端不支持身份解析时，客户端仍可通过旧 URL hash 拉取旧数据，但无法安全地让两台全新设备自动发现同一 UUID；这时 UI 必须明确显示“服务端不支持自动身份匹配”，而不是静默生成多个空间。

### 6.6 配置管理接口

现有 `config.list`、`config.upsert`、备份恢复必须继续传递：

```json
{
  "type": 0,
  "interfaceKey": "canonical-uuid",
  "url": "https://a.example/api.json",
  "urls": [
    "https://a.example/api.json",
    "https://b.example/api.json"
  ],
  "addressMatchAliases": ["..."],
  "legacyConfigKeys": ["..."]
}
```

定位优先级固定为：

```text
interfaceKey > 精确地址匹配 > 旧 url + type > 新建
```

同一 `interfaceKey` 的更新必须保留本机 `Config.id/cid`；仅地址不同不能删除旧配置再创建新配置。

---

## 7. 服务端实现设计

### 7.1 逻辑空间与物理空间

现有物理空间可以继续使用：

```text
physicalSpace = hash(token) + ":" + hash(canonicalInterfaceKey)
```

新增身份路由层：

```text
(tokenHash, configType, submittedKey)
       -> canonicalInterfaceKey
       -> physicalSpace
```

所有新请求先经过身份路由，再读写 canonical 物理空间。不能只在 `status` 接口返回匹配结果，而不改变实际 `GET`/`POST` 的空间路由。

### 7.2 注册表原子操作

`resolve` 必须是幂等原子操作：

```text
lock/CAS key = tokenHash + configType + sorted(submittedStrongKeys)
```

事务内完成：

1. 校验请求；
2. 查询当前 `interfaceKey`、strict key、endpoint key、legacy key；
3. 检测 0/1/多身份结果；
4. 创建、采用或标记冲突；
5. 写入全部新别名；
6. 生成 `identityEpoch`；
7. 记录审计结果；
8. 返回响应。

两个设备同时第一次提交同一地址时，最多只能创建一个 canonical identity，另一个必须收到 `adopt`。

### 7.3 迁移旧 URL 哈希空间

旧版本空间迁移步骤：

1. 新客户端数据库升级时为当前及已保存备用 URL 计算 `legacyConfigKeys`。
2. Resolve 请求同时提交 `legacyConfigKeys`。
3. 服务端若发现 legacy key 已有物理空间，则将它绑定为 canonical identity 的 alias。
4. 将旧空间的 items、tombstones、event IDs 合并到 canonical 空间。
5. 合并产生新的 canonical sequence，不能直接复用旧空间 sequence。
6. 返回 `migrationRequired=false` 表示服务端已完成；或返回 `migration_pending` 让客户端等待异步任务。
7. 旧物理空间保留为只读/路由别名至少 90 天，兼容旧客户端继续请求。

### 7.4 记录合并规则

#### 普通进度 upsert

逻辑记录键必须在 canonical 空间内重新计算，不能把旧 `configKey` 作为独立记录身份。候选记录按以下字段比较：

```text
updatedAt（缺失时 timestamp）
event timestamp
completed
positionMs
```

优先级：

1. `updatedAt`/事件时间较新的记录胜出；
2. 时间相同时 `completed=true` 胜过 `false`；
3. 时间、完播状态都相同时 `positionMs` 较大者胜出；
4. 再相同按稳定 `eventId` 字典序选取，确保多实现结果一致。

#### 删除墓碑

- 删除墓碑按 `(scope, historyKey/siteKey/vodId/mediaType/tmdbId/seasonNumber)` 归一化；
- 同一墓碑取 `deletedAt` 最大者；
- `deletedAt >= upsert.updatedAt` 时禁止旧进度复活；
- 更晚的新 upsert 可以合法恢复，且必须产生新的 canonical change；
- `all/site/season/item` 作用域优先级和现有 `PlaybackDeleteTombstoneStore` 语义保持一致；
- 迁移期间删除墓碑必须与普通进度一起搬迁，不能只迁移可见记录。

#### 幂等事件

- 合并 `eventId` 去重表；
- 同时保留 `dedupeKey` 去重；
- 旧空间和 canonical 空间相同事件不得产生两次有效变更；
- 双写失败重试不能放大记录数量。

### 7.5 游标处理

迁移后 canonical sequence 可能重建，服务端应返回：

```json
{
  "resetSince": true,
  "nextSince": "0",
  "identityEpoch": "..."
}
```

客户端收到 `resetSince=true`：

- 清除该 `canonicalInterfaceKey` 的本地游标；
- 从 0 或服务端声明的 `minSince` 全量拉取一次；
- 所有记录成功应用、没有 malformed/failed 后才保存新游标；
- 分页/limit 截断时不推进到未处理之后。

### 7.6 多实现一致性

以下实现必须共享同一协议 fixture 和验收结果：

- `serverless/webhtv-remote-cloudflare/src/playback-sync.js`
- `serverless/webhtv-remote-deno/playback-sync.js`
- `serverless/webhtv-remote-vercel/api/playback-sync.js`
- `serverless/webhtv-remote-go/playback.go`
- `serverless/webhtv-remote-rust/src/playback.rs`

每个实现都必须覆盖：创建、精确匹配、HTTP/HTTPS endpoint 匹配、裸域名候选、冲突、legacy 迁移、并发 CAS、删除墓碑、游标重置和旧客户端请求。

---

## 8. 客户端实现设计

### 8.1 `Config` 与数据库迁移

修改：

- `app/src/main/java/com/fongmi/android/tv/bean/Config.java`
- `app/src/main/java/com/fongmi/android/tv/db/Migrations.java`
- `app/src/main/java/com/fongmi/android/tv/db/AppDatabase.java`
- `app/schemas/com.fongmi.android.tv.db.AppDatabase/48.json`

要求：

1. `MIGRATION_47_48` 为已有配置从 `url` 和 `urlsJson` 计算当前 legacy hash 与 address keys。
2. 迁移失败的单条地址不能阻塞整个数据库升级，记录空别名并在后续 resolve 重试。
3. 保留 `interfaceKey`、`Config.id`、`History.cid`。
4. `Config.urls()`、`replaceUrl()`、`mergeUrls()` 在修改前追加旧地址别名。
5. 空配置、Live/Wall 配置和旧 JSON 均保持向后兼容。

### 8.2 `PlaybackConfigIdentity`

建议新增纯函数：

```java
String keyForCid(int cid);                       // 现有正式身份
List<String> legacyKeysForCid(int cid);
List<String> strictAddressKeysForCid(int cid);
List<String> endpointMatchKeysForCid(int cid);
List<String> hostMatchKeysForCid(int cid);
IdentitySnapshot snapshotForCid(int cid);
```

必须保留：

- `keyForUrl()` 作为旧协议兼容函数；
- `cidForKey()` 对 canonical `interfaceKey` 和旧 URL hash 的映射；
- `normalizeKey()` 的大小写、空白和长度约束。

不能在 `keyForCid()` 中根据地址重新生成 `interfaceKey`。

### 8.3 配置编辑和手动新增

涉及：

- `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java`
- `app/src/main/java/com/fongmi/android/tv/remote/RemoteConfigOps.java`
- `app/src/main/java/com/fongmi/android/tv/server/process/Manage.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Backup.java`

流程：

1. 新建配置：生成 provisional UUID，保存本地主备顺序，标记 `unresolved`。
2. 异步调用 resolve；不得在 UI 线程阻塞网络。
3. `adopt`：仅更新当前 `Config.interfaceKey`，保留 `Config.id` 和本地主备顺序。
4. `create/keep`：标记 `resolved`。
5. `confirm_required`：展示命中的 host/path、候选名称（如服务端允许）和数据数量，用户确认后再执行 merge/adopt。
6. `conflict`：明确显示两个身份冲突，不自动覆盖任何配置。
7. 离线：配置仍可保存，但标记 `offline`；下次启动/同步重试。
8. 同一设备重复保存同一地址时，先按已有 `interfaceKey` 更新，不创建第二条配置。

### 8.4 主备故障转移

`VodConfig` 现有地址切换逻辑必须继续满足：

```text
Config.id 不变
interfaceKey 不变
History.cid 不变
```

自动切换成功后可以更新当前主地址，但必须把旧主地址放入地址别名/备用集合；不能因为成功地址变化而重新 resolve 成另一个接口。

### 8.5 远端同步器

涉及：

- `PlaybackRemoteSyncer.java`
- `RemoteSyncConfig.java`
- `PlaybackRemoteSyncStore.java`
- `PlaybackRemoteSyncPayload.java`
- `PlaybackProgressWriter.java`
- `PlaybackProgressInput.java`
- `PlaybackProgressDeleteInput.java`

要求：

1. 同步前按当前 VOD 配置获取 identity snapshot。
2. 未解析身份时先 resolve；网络失败不得丢弃本地记录。
3. `adopt` 后将本地 `interfaceKey` 更新为 canonical，并按服务端响应处理 `resetSince`。
4. 按 canonical key 保存 cursor；legacy alias cursor 单独保存，不能覆盖 canonical cursor。
5. 读取 canonical 空间以及兼容窗口内声明的 legacy 空间；同一记录按服务端 merge 规则应用。
6. 别名空间中任何一页 malformed 或失败时，不推进该别名的 cursor。
7. `PlaybackProgressWriter` 应把来自 legacy 空间的记录写入当前 `cid`，不能因旧 key 未被 `cidForKey()` 识别而静默丢弃。
8. 完成迁移后，后续新写入只写 canonical；旧服务端 fallback 只有在能力协商明确允许时双写。

### 8.6 Webhook

`PlaybackWebhookSender` 的普通上报端点和内置可迁移同步端点必须区分：

- 内置 `playback/sync` 且能力声明支持 alias 时，使用 canonical `configKey` 和 aliases；
- 普通 Webhook 只发送一次，不能因为 legacy 兼容自动复制两份；
- Header 编码继续使用 `PlaybackHttpHeaders`；
- server 端 409/identity conflict 需要记录可见错误，但不重试相同请求造成噪声。

### 8.7 本地与远端配置导入

备份、远程 `config.list/upsert` 和一键同步必须传递 `interfaceKey`、主备地址、legacy keys 和地址别名。恢复策略：

```text
有 interfaceKey 且本机命中 -> 更新原 Config
有 interfaceKey 但本机无 -> 创建并保留该 key
无 interfaceKey 但 URL 命中 -> 复用本机 key
完全无匹配 -> 新建 UUID
```

任何导入都不能因为新 URL 与旧 URL 不同而删除原配置。

### 8.8 用户界面

“增强功能 → 观影记录同步”至少展示：

- 当前正式 `interfaceKey` 的短指纹（只显示前 8 位，完整值不展示给普通用户）；
- 身份状态：已确认、解析中、离线待重试、需要确认、冲突；
- 最近匹配方式：正式 key、精确地址、HTTP/HTTPS endpoint、旧 URL hash；
- 迁移记录数、待迁移数量和最后错误；
- “查看候选”“采用此接口”“保持独立”操作；
- 明确说明 Token 不需要因地址变化重新填写。

确认对话框必须显示：

```text
匹配原因：命中备用地址 b.example/api.json
目标接口：接口名称/身份短指纹
目标已有记录：N 条
本地已有记录：M 条
合并后不可自动拆分，是否继续？
```

---

## 9. 迁移、冲突和失败状态机

### 9.1 新配置状态机

```text
NEW
  -> UNRESOLVED
  -> RESOLVING
       -> CREATED/RESOLVED
       -> ADOPTED/RESOLVED
       -> CONFIRM_REQUIRED
       -> CONFLICT
       -> OFFLINE (retry)
```

### 9.2 已有数据的身份合并

```text
source identity + target identity
  -> server dry-run
  -> show counts/conflicts
  -> user confirm
  -> atomic merge
  -> canonical identity
  -> reset canonical cursor
  -> full pull/apply
```

dry-run 必须返回：

- 两边记录数量；
- 将被覆盖的进度数量；
- 删除墓碑冲突数量；
- 受影响的 TMDB 季集/集数数量；
- 是否存在无法自动合并字段。

### 9.3 冲突分类

| 冲突 | 示例 | 处理 |
| --- | --- | --- |
| `identity_conflict` | 同一强地址键属于 UUID-A 和 UUID-B | 409，人工确认或保持独立 |
| `host_ambiguous` | 仅同 host，不同 path | 候选，不改身份 |
| `type_conflict` | vod 与 live 共享地址 | 按类型隔离，不合并 |
| `legacy_alias_conflict` | 同一旧 URL hash 已绑定多个正式身份 | 禁止迁移，要求选择 |
| `data_merge_conflict` | 两个已有空间都有不同最新进度 | 显示 dry-run，用户确认 |
| `protocol_unsupported` | 服务端无 identity 接口 | 只读 legacy fallback，提示能力不足 |
| `storage_unavailable` | KV/Redis/文件存储失败 | 保留本地状态，指数退避 |

### 9.4 重试和幂等

- 每次 resolve 携带 `X-WebHTV-Request-Id`；服务端保存至少 24 小时结果。
- 网络错误使用 5 秒、30 秒、5 分钟、30 分钟、2 小时退避，上限由现有后台服务周期控制。
- 400/409 不对同一请求无限重试；401/403 需要用户处理；503 可重试。
- `adopt`、`merge`、legacy 迁移重复执行必须返回同一 `identityEpoch` 和结果。
- 客户端在收到成功响应但本地写入失败时，重新读取服务端 canonical 状态，不重新生成 UUID。

---

## 10. 安全、隐私与数据隔离

1. 身份注册和匹配必须在 Token 范围内；不同 Token 即使地址完全相同也不能合并。
2. `configType` 必须参与所有索引，防止 VOD、Live、Wall 串线。
3. 服务端只保存哈希后的地址键和必要的审计元数据，日志不得打印 Token、完整 URL 或完整 `interfaceKey`。
4. alias 和地址键限制长度、字符集和数量，防止 Header、KV key 和 JSON 炸弹。
5. 解析接口必须限速；同一 Token 每分钟 resolve 次数需有上限。
6. 不能用用户可控的 `configName` 作为身份判断或索引。
7. 主备地址匹配不等于服务器所有权证明；同一 Token 下才允许自动认领，跨 Token 永远不关联。
8. 同一强地址键发生多个身份冲突时不得“最后写入覆盖”，否则攻击者可以劫持既有身份。
9. 迁移审计记录只保留不可逆 key、操作结果、时间、版本和计数，不保存播放标题等敏感内容，除非已有同步存储策略允许。
10. 删除墓碑在兼容窗口内不能因旧空间重放而被清除或复活。

---

## 11. 可观测性与诊断

客户端日志（默认脱敏）：

```text
playback-identity-resolve
  action=adopt|keep|create|confirm|conflict
  matchedBy=interface|strict|endpoint|host|legacy|none
  configType=vod
  sourceKeyPrefix=...
  targetKeyPrefix=...
  aliases=<count>
  migration=<none|pending|done>
```

服务端指标：

- `identity_resolve_total{action,matchedBy,type}`；
- `identity_conflict_total{reason,type}`；
- `identity_migration_total{source,success}`；
- `identity_migration_records_total`；
- `identity_alias_lookup_total{kind}`；
- `playback_sync_empty_after_identity_change_total`；
- `identity_cas_retry_total`；
- `identity_storage_error_total`。

诊断接口 `/status` 可返回：

- canonical key 短指纹；
- alias 数量和类型；
- 当前 migration epoch；
- item/tombstone 数量；
- 最新 sequence/cursor；
- 最近一次 resolve action 和时间。

不得返回 Token、完整 URL、完整播放记录或可逆的地址明文。

---

## 12. 逐文件实现清单

### 12.1 Android 客户端

| 路径 | 实现内容 |
| --- | --- |
| `app/src/main/java/com/fongmi/android/tv/bean/Config.java` | 新增 alias/legacy 列表、解析状态、去重合并与序列化 |
| `app/src/main/java/com/fongmi/android/tv/db/Migrations.java` | 47→48 迁移、旧 URL hash 和地址键初始化 |
| `app/src/main/java/com/fongmi/android/tv/db/AppDatabase.java` | 版本 48、注册迁移 |
| `app/schemas/com.fongmi.android.tv.db.AppDatabase/48.json` | Room schema |
| `app/src/main/java/com/fongmi/android/tv/playback/PlaybackConfigIdentity.java` | 规范化、strict/endpoint/host/legacy key 生成 |
| `app/src/main/java/com/fongmi/android/tv/playback/PlaybackIdentityResolver.java` | resolve、能力探测、状态机、冲突结果 |
| `app/src/main/java/com/fongmi/android/tv/playback/RemoteSyncConfig.java` | canonical/alias cursor、identity epoch、能力缓存 |
| `app/src/main/java/com/fongmi/android/tv/playback/PlaybackRemoteSyncer.java` | resolve 前置、canonical/legacy 拉取、游标重置 |
| `app/src/main/java/com/fongmi/android/tv/playback/PlaybackRemoteSyncStore.java` | 保存解析结果和迁移状态 |
| `app/src/main/java/com/fongmi/android/tv/playback/PlaybackProgressInput.java` | 接受 canonical/legacy 输入并保留来源 |
| `app/src/main/java/com/fongmi/android/tv/playback/PlaybackProgressDeleteInput.java` | 删除墓碑迁移和来源 key 处理 |
| `app/src/main/java/com/fongmi/android/tv/playback/PlaybackProgressWriter.java` | legacy 记录映射当前 cid、冲突与墓碑保护 |
| `app/src/main/java/com/fongmi/android/tv/playback/PlaybackWebhookSender.java` | 内置同步端点能力判断，普通 Webhook 禁止重复双写 |
| `app/src/mobile/.../ConfigDialog.java` | 手机端新增/修改后的身份解析和确认 UI |
| `app/src/leanback/.../ConfigDialog.java` | TV 遥控器可操作的同等流程 |
| `app/src/main/java/com/fongmi/android/tv/remote/RemoteConfigOps.java` | interfaceKey/地址键优先定位与响应 |
| `app/src/main/java/com/fongmi/android/tv/server/process/Manage.java` | 管理服务配置字段透传 |
| `app/src/main/java/com/fongmi/android/tv/bean/Backup.java` | 备份/恢复保留正式身份和 aliases |
| `app/src/main/java/com/fongmi/android/tv/ui/dialog/ViewingRecordSyncDialog.java` | 状态、候选、迁移和冲突展示 |

### 12.2 服务端

每个版本都要实现同一套逻辑：

| 路径 | 实现内容 |
| --- | --- |
| `serverless/webhtv-remote-cloudflare/src/playback-sync.js` | resolve、身份 Durable Object/KV 路由、迁移和能力 |
| `serverless/webhtv-remote-deno/playback-sync.js` | resolve、Deno KV 原子注册和迁移 |
| `serverless/webhtv-remote-vercel/api/playback-sync.js` | resolve、Vercel KV/Upstash CAS 和迁移 |
| `serverless/webhtv-remote-go/playback.go` | resolve、JSON 文件原子替换、并发锁和迁移 |
| `serverless/webhtv-remote-rust/src/playback.rs` | resolve、文件/锁、迁移和能力 |
| 各自 `README.md` | 新端点、兼容窗口、环境变量、回滚说明 |
| 各自 `test/playback-sync.test.js` / `playback_test.go` | 共享身份/迁移/冲突测试 |

### 12.3 共享测试 fixture

建议新增：

```text
serverless/playback-identity-fixtures/
  canonical-addresses.json
  resolve-cases.json
  merge-cases.json
  legacy-migration-cases.json
```

若暂不抽共享目录，则每个实现必须逐字复制同一组 JSON fixture，并在 CI 中比较结果。

---

## 13. 测试策略

### 13.1 Android 单元测试

建议新增：

- `PlaybackAddressCanonicalizerTest`
- `PlaybackIdentityMatchKeyTest`
- `PlaybackIdentityResolverTest`
- `PlaybackIdentityMigrationTest`
- `RemoteSyncIdentityCursorTest`
- `PlaybackLegacyAliasTest`
- `PlaybackIdentityAcceptanceTest`

必须覆盖：

1. 规范化向量和跨端 hash 结果；
2. 主备顺序不同的地址集合；
3. 一项精确地址命中即可采用 canonical key；
4. 只有 host 命中时返回确认；
5. 地址增删不改变 `interfaceKey`；
6. 旧数据库升级生成 legacy keys；
7. 两套已有数据合并需要用户确认；
8. 同类型隔离和相同 URL 不同身份冲突；
9. legacy 游标和 canonical 游标独立；
10. 删除墓碑不能被旧进度复活；
11. resolve 超时、409、503、重复 request ID；
12. 手机和 TV 配置入口都保留 `Config.id/interfaceKey`。

### 13.2 服务端单元/集成测试

每个内置服务端都至少覆盖：

| 测试 | 期望 |
| --- | --- |
| 空注册 | create 一个 canonical identity |
| 相同 strict key | 第二请求 adopt 第一个 key |
| 相同 endpoint key | HTTP/HTTPS 变化可 adopt |
| 仅 host 相同 | confirm_required，不改映射 |
| 两个 identity 抢同一 strong key | 409 conflict |
| 不同 Token 相同 key | 两个独立 identity |
| 不同 configType 相同 key | 两个独立 identity |
| 并发 resolve | 一个 create，其余 adopt，不能双建 |
| legacy 空间存在 | 搬迁并保留旧 alias |
| legacy 空间不存在 | alias 注册但不制造空数据错误 |
| 旧客户端按 legacy key GET/POST | 兼容窗口路由到 canonical |
| 删除墓碑迁移 | 旧进度不能复活 |
| 重复迁移 | 幂等，不重复 sequence |
| 存储 CAS 冲突 | 可重试，最终一致 |
| 超长/非法 key | 400，不改变数据 |

### 13.3 端到端设备场景

使用测试包，不卸载现有包，覆盖安装验证：

1. 设备 A 配置 `A + B`，设备 B 手动配置 `B + C`，两边使用同 Token；B 自动采用 A 的 `interfaceKey`。
2. A 先播放后，B 再完成解析，B 能拉到记录。
3. B 播放后，A 能拉到记录。
4. A 将 B 切为当前主地址，记录仍在同一空间。
5. A 删除 C，B 仍能使用 B，身份不变。
6. 两边只输入同 host 不同 path，必须提示确认，不能自动合并。
7. 旧测试版上传记录，最新测试版覆盖安装后能找回。
8. 最新版上传后，旧版在兼容窗口内仍能读取/写入；能力不支持时 UI 明确提示。
9. 两个已有不同接口都包含相同地址，产生冲突提示，选择保持独立后互不串线。
10. 清除网络、恢复网络、杀进程、重启 App 后状态和游标不丢。

设备验证规则：

- 使用 `/home/maple/Workspace/webhtv/dev1/webhtv` 对应的 `192.168.50.3:5555`；
- 不卸载现有包，使用覆盖安装；
- 不直接占用其他工作区分配的模拟器；
- 构建使用测试包，优先 `scripts/build_arm64_debug_install.sh`；
- 每次打包/安装/测试后及时清理 Gradle、临时日志和模拟器资源；
- 若需第二设备对比，先向用户申请，不能强制使用其他分配地址。

---

## 14. 完整验收矩阵

以下项目全部通过才可将实现标记为完成：

| ID | 场景 | 证据 | 通过标准 |
| --- | --- | --- | --- |
| ID-01 | 新设备无匹配 | Android + 服务端测试 | create，生成唯一 canonical key |
| ID-02 | 两边完全相同地址、顺序不同 | resolver test | 自动 adopt，同一 key |
| ID-03 | 仅一个备用地址命中 | resolver/设备测试 | 自动 adopt，地址并集合并，主备顺序本地保留 |
| ID-04 | HTTP/HTTPS 同 endpoint | canonicalizer test | endpoint 命中；无多身份冲突时 adopt |
| ID-05 | 仅 host 相同、path 不同 | resolver test | confirm_required，不自动合并 |
| ID-06 | 同地址多身份 | conflict test | 409，任何身份不被覆盖 |
| ID-07 | 不同 Token | isolation test | 空间完全隔离 |
| ID-08 | 不同 configType | isolation test | VOD/Live/Wall 不合并 |
| ID-09 | 修改主地址 | Android test/device | `Config.id/interfaceKey/cid` 不变 |
| ID-10 | 增删备用地址 | Android test | 正式 key 不变，旧 match alias 保留 |
| ID-11 | 自动故障转移 | device/log | 地址切换不产生新同步空间 |
| ID-12 | 旧 URL hash 记录 | migration E2E | 最新版可拉取并迁移旧记录 |
| ID-13 | 旧删除墓碑 | migration E2E | 旧进度不能复活已删除记录 |
| ID-14 | 新旧客户端并存 | compatibility E2E | 兼容窗口内按协议互读互写 |
| ID-15 | 服务端不支持 resolve | fallback test | 只读 legacy 可用，UI 明确能力不足 |
| ID-16 | 两边均已有数据 | merge dry-run/E2E | 先显示差异，未确认不得合并 |
| ID-17 | 并发首次注册 | server race test | 一个 create，其余 adopt，不双建 |
| ID-18 | 重试/重复请求 | idempotency test | 结果和 sequence 不重复 |
| ID-19 | 分页/失败 | cursor test | 未处理完不得推进 cursor |
| ID-20 | 备份恢复 | Android E2E | 保留 interfaceKey、cid、主备和 aliases |
| ID-21 | 重装/跨设备导入 | E2E | 导入同一配置后进入同一空间 |
| ID-22 | 隐私/安全 | static/log audit | 日志和服务端不泄露 Token/完整 URL |
| ID-23 | 五种服务端实现 | shared fixture + tests | 结果一致，无实现漂移 |
| ID-24 | 回滚 | staging rollback | 回滚客户端/服务端不删除旧空间，旧客户端仍可工作 |

### 14.1 最低命令验证

实现阶段至少执行一次并保存完整输出：

```bash
bash ./gradlew --console=plain \
  :app:compileMobileArm64_v8aDebugJavaWithJavac \
  :app:compileLeanbackArm64_v8aDebugJavaWithJavac \
  :app:testMobileArm64_v8aDebugUnitTest \
  :app:testLeanbackArm64_v8aDebugUnitTest
```

服务端按实现执行：

```bash
cd serverless/webhtv-remote-vercel && npm test
cd serverless/webhtv-remote-deno && deno test
cd serverless/webhtv-remote-cloudflare && npm test
cd serverless/webhtv-remote-go && go test ./...
cd serverless/webhtv-remote-rust && cargo test
```

实际命令以各目录 `package.json`/README 和环境可用工具为准；一个实现失败不能用另一个实现的通过代替。

---

## 15. 发布、灰度和回滚

### 15.1 分阶段实施

#### 阶段 A：规范和 fixture

- 固定 canonical URL、三类 match key 算法；
- 固定 resolve 请求/响应 JSON；
- 五个服务端实现共享向量；
- 只读代码和测试，不改变线上空间。

#### 阶段 B：服务端能力

- 增加 identity registry 和 resolve；
- 支持 alias 路由；
- 支持 legacy 空间迁移；
- status 返回能力；
- 旧接口仍保持原有 `configKey` 行为。

#### 阶段 C：客户端写入 aliases、读取兼容空间

- 47→48 迁移；
- 保存 legacy/address aliases；
- resolve 失败时不改变当前正式身份；
- 先做只读 legacy 拉取；
- 添加 UI 状态和日志。

#### 阶段 D：开启自动匹配

- 精确 strict/endpoint key 可自动 adopt；
- host-only 只能确认；
- 已有数据必须 dry-run + 用户确认；
- 覆盖安装测试包验证。

#### 阶段 E：兼容窗口收口

- canonical 新写入稳定后，旧 alias 只读/路由；
- 至少保留 90 天删除墓碑和旧空间别名；
- 再评估是否停止 legacy 双写，不得直接删除旧空间。

### 15.2 回滚策略

服务端：

- 身份注册表和 alias 是增量数据，回滚代码不得删除它们；
- 关闭自动 adopt 只需把 resolve action 改为 confirm_required，不撤销已确认的 canonical key；
- 保留旧物理空间和迁移快照至少 90 天；
- 迁移错误时从快照恢复 canonical 空间，保留 source alias 供重试。

客户端：

- 可以通过 feature flag 关闭自动匹配，但不能把已确认的 `interfaceKey` 重算成 URL hash；
- 回滚到旧客户端时，服务端 alias 路由仍接收旧 URL hash；
- 升级/回滚都不能清空 `legacyConfigKeys`、address aliases、cursor 或 tombstone。

### 15.3 发布门槛

发布前必须同时满足：

1. 第 14 节 ID-01 至 ID-24 全部有证据；
2. 五个服务端实现通过共享 fixture；
3. Mobile 和 Leanback Java 编译、单元测试通过；
4. 真实测试包覆盖安装验证过至少一个跨设备精确匹配场景；
5. 旧版数据迁移和删除墓碑有实测结果；
6. 无未解释的 `identity_conflict`、空同步或 cursor 回退日志；
7. 有可执行回滚步骤和保留期限；
8. 未产生正式包，只有测试包。

---

## 实施进度记录（2026-09-24）

已实现：

- `serverless/playback-identity-fixtures/identity.js`：CanonicalUrlV1、strict/endpoint/host/legacy SHA-256、Token+configType registry、CAS resolve、adopt/confirm/conflict、legacy space merge。
- Vercel/Deno/Cloudflare：`/api/playback/identity/resolve` 与 `/playback/identity/resolve`，canonical alias 读写路由，status 能力声明；三套 JS 回归测试和共享 identity fixture 通过。
- Go/Rust：身份 registry、resolve 路由、类型隔离、alias space routing、持久化字段已接线；当前机器无 `go/gofmt/cargo/rustc`，只能保留未编译风险。
- Android：`Config` 新增 legacy/address/state 字段；Room 47→48 迁移；规范化 key；`PlaybackIdentityResolver`；同步器 canonical/legacy pull、cursor reset；远程配置与备份入口接线；Mobile Arm64 Java 编译通过。
- `app/schemas/.../48.json` 由 Room processor 生成，identity hash 为 `bbde8a33386ce26c0e0897467faa465d`。

已验证：

- `node --check`：共享 identity、Vercel、Deno、Cloudflare 通过。
- `node --test serverless/playback-identity-fixtures/identity.test.js`：2/2 通过。
- Vercel/Deno：4/4 通过；Cloudflare：6/6 通过。
- `bash ./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac --no-daemon`：通过；`compileLeanbackArm64_v8aDebugJavaWithJavac`：通过；带 `--rerun-tasks` 生成 48 schema 也通过。
- `:app:testMobileArm64_v8aDebugUnitTest --tests StableInterfaceIdentityAcceptanceTest`：通过；`:app:testLeanbackArm64_v8aDebugUnitTest`：通过。Mobile 全量曾有一个无关的 `TmdbDetailActivityLayoutTest` 失败，未修改其范围。
- `scripts/build_arm64_debug_install.sh --flavor mobile --abi arm64-v8a --serial 192.168.50.3:5555`：测试包构建、覆盖安装、启动进程/前台 Activity 健康检查通过；安装后已 force-stop，未卸载。

未完成/限制：

- 已执行 Leanback 编译、Mobile/Leanback 定向单元测试、测试包覆盖安装和单设备启动健康检查；尚未执行需要第二设备的跨设备场景，本任务没有卸载或使用第二模拟器。
- Go/Rust 没有本地工具链，不能把源码审阅当作编译/测试证据；五种服务端一致性、Go/Rust 编译和完整 ID-01 至 ID-24 仍未闭合。
- UI 目前保存后异步 resolve，冲突/confirm 状态记录到同步配置和日志，完整候选/合并确认对话框与 dry-run 计数仍待后续阶段。

Recovery anchor：完成上述阶段性实现后，下一步只做工具链可用性恢复及 Go/Rust 编译验证；若工具链不可用，应保留当前代码、记录阻塞，不得宣称第 14 节 ID-01 至 ID-24 全部通过。

## 16. 实施顺序与完成定义

### 16.1 推荐实现顺序

1. 先实现 URL 规范化和跨端 fixture，不接入运行时。
2. 实现服务端 identity registry、resolve、冲突和 CAS 测试。
3. 实现服务端 legacy alias 路由和数据迁移测试。
4. 实现 Android 47→48 迁移、alias 持久化和纯函数测试。
5. 实现配置新增/修改/备份/远端管理入口的身份解析。
6. 实现 `PlaybackRemoteSyncer` canonical/legacy 读取、cursor reset 和 writer 映射。
7. 实现 Webhook 能力隔离和普通端点不重复投递。
8. 实现 Mobile/Leanback UI 的状态、确认和冲突处理。
9. 运行命令验证、服务端测试和一台模拟器覆盖安装。
10. 申请第二设备后执行跨设备端到端场景。
11. 记录迁移、回滚和验收证据，最后才允许发布测试包。

### 16.2 完成定义

本设计对应的实现只有在以下条件全部满足时才算完成：

```text
stable interfaceKey 未被废弃
AND 任意一个精确主备地址命中可安全发现 canonical identity
AND 裸域名命中不会静默合并
AND 旧 URL hash 数据可兼容读取/迁移
AND 删除墓碑、游标、幂等和并发规则通过验证
AND 配置、备份、故障转移、Webhook、Mobile、Leanback 全部接线
AND 五种服务端行为一致
AND 第 14 节验收矩阵全部有权威证据
AND 回滚不丢数据
```

任何一项缺失都只能标记为“部分实现”或“兼容性未完成”，不能以“新版新数据能同步”替代完整目标。

---

## 17. 相关文档与源码索引

### 设计依据

- `docs/interface-stable-identity-design.md`：稳定接口身份、地址集合和旧 URL hash 兼容的既有设计。
- `docs/playback-history-delete-sync-design.md`：删除墓碑、历史删除上报和远端合并边界。
- `serverless/webhtv-remote-vercel/README.md`：当前远端同步协议、Token、`configKey` 和存储限制。
- `serverless/webhtv-remote-cloudflare/README.md`：Cloudflare 版本的同步接口和能力说明。
- `serverless/webhtv-remote-rust/README.md`：Rust 版本的持久同步服务边界。

### 客户端关键入口

- `app/src/main/java/com/fongmi/android/tv/playback/PlaybackConfigIdentity.java`
- `app/src/main/java/com/fongmi/android/tv/playback/PlaybackRecord.java`
- `app/src/main/java/com/fongmi/android/tv/playback/PlaybackRemoteSyncer.java`
- `app/src/main/java/com/fongmi/android/tv/playback/PlaybackProgressWriter.java`
- `app/src/main/java/com/fongmi/android/tv/playback/RemoteSyncConfig.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Config.java`
- `app/src/main/java/com/fongmi/android/tv/db/Migrations.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java`

### 服务端关键入口

- `serverless/webhtv-remote-cloudflare/src/playback-sync.js`
- `serverless/webhtv-remote-deno/playback-sync.js`
- `serverless/webhtv-remote-vercel/api/playback-sync.js`
- `serverless/webhtv-remote-go/playback.go`
- `serverless/webhtv-remote-rust/src/playback.rs`
