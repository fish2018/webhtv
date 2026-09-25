# 点播接口稳定唯一标记设计评估

## 1. 结论

用户反馈的核心不是“备用地址切换后继续保存历史”这一单点问题，而是当前系统错误地把“接口地址 URL”同时当成了“接口身份”。

正确模型应当把以下概念拆开：

- **接口身份**：一个长期稳定、与 URL 无关的唯一标记，例如 `interfaceKey`。
- **接口地址**：当前可用的主地址以及多个备用地址，可以随时修改或自动切换。
- **本机数据库编号**：Room 自动生成的 `Config.id`，也就是现有 `cid`，仅用于本机表关联。

因此推荐方案是：给每个点播接口新增稳定的 `interfaceKey`，后续主地址变化、备用地址切换、域名更换时，都更新同一条 `Config` 记录，而不是按新 URL 新建一条配置。只要原 `Config.id` 保持不变，现有按 `cid` 隔离的历史、收藏、季度进度等数据就仍属于原接口，历史页也不会错误显示为“其他配置”。

## 2. 当前问题定位

### 2.1 当前接口身份实际绑定 URL

现有 `PlaybackConfigIdentity` 通过 `SHA-256(url)` 生成 `configKey`：

```text
configKey = SHA-256(config.url)
```

这意味着 URL 一变，逻辑身份就变。即使新旧 URL 实际上属于同一个接口，远端同步以及依赖 `configKey` 的配置级数据也会把它们视为两个接口。

### 2.2 当前配置查找和保存按 URL 定位

`Config.find(url, type)`、配置 DAO 的查询与删除、配置弹窗保存、远端 `config.upsert` 等入口，主要都以 `url + type` 识别配置。

因此把旧地址改成备用地址或换成新域名时，系统很容易发生以下行为：

```text
旧地址 Config(id=10, url=A)
    -> 切换或录入新地址 B
新地址 Config(id=27, url=B)
```

历史记录虽然仍保存在 `cid=10` 下，但当前激活接口变成 `cid=27`。历史页的 `History.getSiteName()` 又直接比较：

```text
history.cid != VodConfig.getCid()
```

于是旧历史显示“其他配置”。这与用户反馈完全一致。

### 2.3 `cid` 能保持本机数据归属，但不能作为跨端身份

现有 `History`、`Keep`、`TmdbSeasonProgress` 等数据已经大量使用 `cid` 隔离。这个本机数据模型本身可以继续保留，不需要把所有表立即改成字符串身份。

但 `cid` 是本机数据库自增编号，备份恢复或跨设备后可能变化，所以不能直接作为同步协议中的接口唯一标记。跨端需要稳定的 `interfaceKey`，本机再把它映射为具体 `cid`。

## 3. 推荐数据模型

建议在 `Config` 增加两个字段：

```text
interfaceKey: String
urlsJson: String
```

语义如下：

- `interfaceKey`：稳定唯一标记。创建后默认不随 URL 修改。
- `urlsJson`：接口地址集合，至少包含当前主地址，也可包含多个备用地址。
- `url`：为兼容现有代码，阶段一继续表示当前首选或最近成功地址。
- `id`：继续作为本机 `cid`，供历史、收藏和进度表关联。

地址集合建议使用有序结构：

```json
[
  "https://primary.example/config.json",
  "https://backup-a.example/config.json",
  "https://backup-b.example/config.json"
]
```

顺序表达用户优先级。加载成功后可以记录 `lastSuccessfulUrl`，但不应因此改变 `interfaceKey`。

## 4. 唯一标记生成和管理规则

### 4.1 新建接口

优先级建议如下：

1. 如果订阅内容或远端管理请求明确提供 `interfaceKey`，使用提供值。
2. 用户手工新建且未提供时，由 App 生成随机 UUID。
3. 从旧版本升级的存量配置，首次迁移时生成稳定 UUID 并写回数据库。

不建议继续用 URL 哈希作为正式身份。URL 哈希只能作为迁移期间识别旧同步数据的兼容别名。

### 4.2 修改接口地址

编辑已有接口时必须保留：

```text
Config.id
Config.interfaceKey
```

只修改：

```text
Config.url
Config.urlsJson
Config.name  // 用户确实修改名称时
```

也就是说，“修改地址”必须是原记录的更新操作，不能再走 `Config.find(newUrl, type)` 后另建记录的路径。

### 4.3 自动切换备用地址

备用地址切换只改变本次请求所用 URL，成功后可以更新当前首选地址或最近成功地址，但必须满足：

```text
before.interfaceKey == after.interfaceKey
before.id == after.id
```

这样自动故障转移不会改变接口的本机历史空间。

### 4.4 唯一标记冲突

导入、同步或远端新增配置时，如果收到已存在的 `interfaceKey`：

- 将其识别为同一接口的更新。
- 合并地址集合并去重。
- 保留本机现有 `Config.id`。
- 禁止静默新建第二条相同 `interfaceKey` 的配置。

如果新请求使用相同 URL、却明确提供不同 `interfaceKey`，应视为两个逻辑接口。URL 可以相同，身份不能被 URL 强行合并。

## 5. 数据库与迁移方案

### 5.1 Config 表迁移

数据库升级时：

1. 给 `Config` 增加非空 `interfaceKey` 字段，旧行先填空字符串。
2. 给点播配置逐条生成 UUID。
3. 新增唯一索引：`type + interfaceKey`。
4. 可选增加 `urlsJson`，并把旧 `url` 作为地址集合首项。

不要改动现有 `Config.id`。这是保住旧历史、收藏和季度进度的关键。

### 5.2 无需批量搬迁历史

如果迁移只为现有 `Config` 补 `interfaceKey`，且保留其 `id`，那么以下表中的 `cid` 不需要改写：

- `History`
- `Keep`
- `TmdbSeasonProgress`
- 其他按 `cid` 隔离的本机状态

只有当前代码曾因改 URL 已经生成两条配置时，才需要提供显式“合并接口”操作。合并时把来源 `cid` 的数据迁移到目标 `cid`，按时间和业务主键解决冲突，然后删除来源配置。

### 5.3 删除语义

删除一个地址不能等价于删除接口。建议区分：

- 删除备用地址：只从 `urlsJson` 移除地址，不删历史。
- 删除整个接口：删除 `Config`，再按现有产品语义决定是否删除该 `cid` 下的历史与收藏。

当前 `Config.delete()` 会联动删除 `History` 和 `Keep`，因此地址编辑和备用地址管理绝不能复用完整接口删除流程。

## 6. 代码改造范围

### 6.1 身份解析

重构 `PlaybackConfigIdentity`：

```text
currentKey()       -> 当前 Config.interfaceKey
keyForCid(cid)     -> Config.find(cid).interfaceKey
cidForKey(key)     -> 按 interfaceKey 查询 Config.id
keyForUrl(url)     -> 仅作为旧协议兼容方法，不再作为正式身份
```

### 6.2 Config DAO

新增按稳定身份查询的方法，并为地址查询降低职责：

```text
findByInterfaceKey(interfaceKey, type)
findByAnyUrl(url, type)
updateUrls(id, primaryUrl, urlsJson)
```

配置保存入口应优先按 `interfaceKey` 查找，只有旧请求没有该字段时才回退按 URL 查找。

### 6.3 手机端和电视端配置弹窗

手机端与 Leanback 端都需要明确区分：

- 新增接口
- 编辑现有接口
- 管理同一接口的多个地址

编辑现有接口时，弹窗必须持有原 `Config.id/interfaceKey`，不能只把新 URL 交给静态 `find()`。

界面可展示一个只读或高级设置中的“接口标记”，允许复制，不建议默认允许随意修改。若提供修改，应进行冲突检查和二次确认。

### 6.4 远端配置协议

`config.list` 响应增加：

```json
{
  "interfaceKey": "stable-uuid",
  "url": "https://primary.example/config.json",
  "urls": [
    "https://primary.example/config.json",
    "https://backup.example/config.json"
  ]
}
```

`config.upsert` 的定位优先级：

```text
interfaceKey > 旧版本的 url + type
```

历史同步协议已经使用 `configKey` 的位置，应改为读取稳定 `interfaceKey`；迁移期可以同时接受旧 URL 哈希，并通过兼容别名映射到本机配置。

### 6.5 故障转移

当前接口故障转移候选可能来自多个独立 `Config`。新模型下，同一接口的备用 URL 应首先作为同一 `Config` 的地址候选处理。

跨接口故障转移与接口内地址故障转移必须分开：

- 接口内地址切换：身份和 `cid` 不变。
- 跨接口切换：身份和 `cid` 改变，历史页显示为其他配置是合理的。

这能消除“备用线路”和“另一个点播接口”在语义上的混淆。

## 7. 历史页修复结果

完成上述改造后，用户场景变为：

```text
接口 X
interfaceKey = webhtv-demo
cid = 10
addresses = [A, B, C]
```

从 A 切到 B 后仍是：

```text
interfaceKey = webhtv-demo
cid = 10
currentUrl = B
```

因此：

- `History.cid` 仍为 10。
- `VodConfig.getCid()` 仍为 10。
- 历史页不会显示“其他配置”。
- 收藏和季度进度仍在原接口空间。
- 远端同步使用稳定 `interfaceKey`，不会因域名变化创建新接口空间。

不建议只修改 `History.getSiteName()`，让它在 URL 接近或名称相同时隐藏“其他配置”。那只能修正显示，不能修复续播、删除、同步、收藏和配置归属，反而可能把两个真实不同的接口误判为同一个。

## 8. 兼容策略

### 8.1 旧版本升级

旧配置迁移后自动获得 UUID。历史数据无需移动，因为 `cid` 保持不变。

### 8.2 旧备份恢复

旧备份没有 `interfaceKey` 时：

- 如果能按旧 `url + type` 命中本机配置，复用本机 `interfaceKey` 和 `id`。
- 否则创建新配置并生成 UUID。
- 恢复历史时继续使用备份恢复已有的 `cid` 映射流程。

### 8.3 旧同步客户端

迁移期保留 URL 哈希别名：

```text
legacyConfigKey = SHA-256(oldUrl)
```

本机可以维护旧哈希到新 `interfaceKey` 的映射。待协议版本覆盖完成后，再逐步停止依赖 URL 哈希。

## 9. 建议实施顺序

### 阶段一：稳定本机身份

1. 给 `Config` 增加 `interfaceKey` 和数据库迁移。
2. 改造 `PlaybackConfigIdentity`。
3. 修改手机端、电视端编辑路径，保证改 URL 不改 `id/interfaceKey`。
4. 增加单元测试，验证地址修改后 `cid` 不变。

此阶段即可解决历史显示“其他配置”的主要问题。

### 阶段二：同接口多地址

1. 增加地址集合。
2. 把接口内故障转移改为地址候选切换。
3. 增加地址管理 UI。
4. 验证自动切换后历史、收藏和进度不变。

### 阶段三：同步与备份

1. 更新远端配置协议。
2. 更新历史同步的 `configKey` 语义。
3. 增加旧 URL 哈希兼容映射。
4. 覆盖备份恢复、跨设备映射和冲突合并测试。

## 10. 最小验收标准

必须至少验证以下场景：

1. 新建接口 A，播放并产生历史，手工把地址改为 B，历史仍显示为当前接口且可续播。
2. 同一接口从主地址自动切到备用地址，`Config.id` 与 `interfaceKey` 均不变。
3. App 重启后仍从新地址加载，旧历史、收藏和季度进度都存在。
4. 两个不同 `interfaceKey` 使用相同 URL 时仍保持独立。
5. 同一 `interfaceKey` 从远端收到新 URL 时更新原记录，不新建配置。
6. 旧数据库升级后所有原配置都有非空唯一标记，原历史无需迁移即可读取。
7. 旧备份恢复后，配置与历史的 `cid` 映射正确。
8. 删除一个备用地址不会删除接口历史；删除整个接口才触发现有清理语义。
9. 手机端和电视端行为一致。
10. 历史同步在 URL 变化前后仍映射到同一接口空间。

## 11. 风险与约束

- 最大风险不是数据库加字段，而是遗漏仍按 URL 创建配置的入口，包括本地弹窗、远端管理、订阅仓库、备份恢复和故障转移。
- 不能用接口名称作为身份，因为名称可重复且可修改。
- 不能自动按站点列表内容相似度合并接口，误合并会让历史和收藏串线。
- 不能在普通地址修改时删除旧 `Config`，否则当前 `Config.delete()` 会删除关联历史与收藏。
- `interfaceKey` 一旦投入同步协议，应视为持久协议字段，不应随应用重装、地址变化或显示名变化自动重算。

## 12. 最终建议

应把需求正式定义为“点播接口稳定身份与多地址模型”，而不是“修复历史页其他配置文案”。

最小正确改法是：

```text
稳定 interfaceKey 负责跨地址、跨端身份
稳定 Config.id/cid 负责本机数据归属
可变 URL 列表负责连接与故障转移
```

只要修改地址时保留原 `Config.id` 和 `interfaceKey`，现有大部分历史数据结构都可以继续使用，改造范围可控，同时能够从根本上解决备用地址切换后被识别为“其他配置”的问题。
