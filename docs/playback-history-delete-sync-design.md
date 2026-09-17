# 观影记录删除同步设计与前后端边界

## 文档状态

- 状态：待实现，作为后续开发与服务端联调依据
- 分析基线：`dev@d9cc4c7d88`
- 记录日期：2026-07-30
- 参考实现：`upstream/beta` 分支提交 `f5d63d16ed`（`fix: sync playback history deletions`）

## 结论

当前项目存在“播放进度可以同步，但用户删除历史记录无法同步”的缺口。

需要区分以下两种能力：

1. `POST /api/playback/progress/delete` 是入站接口，用于外部程序删除某一台设备的本地历史；当前项目已经支持。
2. 多端删除同步是出站事件与远端变更拉取能力：设备 A 删除后，服务端和设备 B 都必须知道这次删除；当前 `dev` 尚未实现。

要可靠实现多端删除同步，客户端必须记录和应用删除墓碑，历史记录存储服务端也必须接收、持久化并返回删除墓碑。只有在现有服务端已经具备这套协议时，才可以只修改当前 Android 项目。

删除同步不是单纯增加一个设置开关。若没有删除时间和墓碑，服务端残留的旧进度会在下一次同步时把本地已删除记录重新创建。

## 当前实现与缺口

### 历史页删除只修改本地数据库

历史页单条删除最终经过：

- `History.deleteDisplayItem()`
- `History.deleteRelated(...)`

清空历史经过：

- `History.deleteForDisplay()`

这些方法当前直接调用 `HistoryDao.delete(...)`，没有生成删除事件、持久化删除时间或通知 Webhook。

### 远端同步只处理新增和更新

`PlaybackRemoteSyncer` 当前使用：

```java
PlaybackProgressInput.listFromJson(body)
PlaybackProgressWriter.applyFromRemoteSync(inputs, config)
```

远端响应只能按普通进度记录解析，不能识别以下删除形式：

- `deleted`、`deletions`、`tombstones`
- `action: "delete"`
- `deleted: true`
- `event: "playback.deleted"`

### Webhook 不会上报历史删除

`WebhookConfig.acceptsEvent(...)` 当前只接受：

- `playback.progress`
- `playback.ended`

`PlaybackEventCollector` 也没有历史删除入口，因此用户在历史页删除记录时，远端存储服务不会收到通知。

### 本机删除 API 不等于删除同步

当前接口：

```http
POST /api/playback/progress/delete
```

支持按单条、站点或全部范围清理本机数据库，但它是接收外部请求的本机写入能力，不会自动向远端传播，也不会阻止旧远端进度复活。

### 当前仓库不包含观影记录存储服务

仓库内的 `serverless/` 实现用于远程托管、命令和备份同步，没有观影记录 Webhook 存储及查询实现。因此服务端是否已经支持删除墓碑，不能仅从当前仓库判断，需要检查实际使用的历史存储服务。

## 可复现的数据错误

典型“删除后复活”流程：

1. 设备 A、本地历史服务和设备 B 都有同一条播放记录。
2. 用户在设备 A 删除该记录。
3. A 只删除本地 `History` 行，服务端仍保留旧记录。
4. A 或 B 再次执行远端同步。
5. 因为客户端没有删除墓碑，旧记录被作为正常 upsert 再次写回。

不能通过“远端结果中没有某条记录”推断删除，因为缺失还可能由分页、站点过滤、接口故障或保留条数限制造成。

## 设计决策

### 使用删除墓碑，而不是只做硬删除

删除操作必须形成持久化变更：

```text
记录身份 + 删除范围 + deletedAt
```

建议默认保留 90 天。保留时间必须覆盖设备可能离线的最长周期；超过保留期仍未同步的设备不保证收到旧删除事件。

### 删除范围

| `scope` | 定位字段 | 语义 |
| --- | --- | --- |
| `item` | `configKey + siteKey + vodId`，必要时补充 `historyKey` | 删除单条影片历史 |
| `site` | `configKey + siteKey` | 删除指定站点历史 |
| `all` | `configKey` | 删除指定点播配置下的全部历史 |

跨设备时应优先使用 `siteKey + vodId`。`historyKey` 可能包含本机 `cid` 后缀，而 `cid` 在不同设备之间不稳定。

### 删除事件

客户端向 Webhook 服务发送：

```json
{
  "schema": "webhtv.playback.v1",
  "event": "playback.deleted",
  "eventId": "uuid",
  "timestamp": 1781170005000,
  "deletedAt": 1781170005000,
  "scope": "item",
  "configKey": "sha256(config-url)",
  "historyKey": "site_key@@@vod_id@@@1",
  "siteKey": "site_key",
  "vodId": "vod_id"
}
```

服务端使用 `eventId` 或 `Idempotency-Key` 保证重复投递不会产生重复副作用，并按 `token + configKey` 隔离用户空间和点播配置。

### 远端同步响应

推荐响应模型：

```json
{
  "items": [
    {
      "configKey": "sha256(config-url)",
      "siteKey": "site_key",
      "vodId": "vod_id",
      "vodName": "影片名",
      "episodeName": "第1集",
      "positionMs": 123456,
      "durationMs": 456789,
      "updatedAt": 1781170000000
    }
  ],
  "deleted": [
    {
      "configKey": "sha256(config-url)",
      "siteKey": "site_key",
      "vodId": "deleted_vod_id",
      "scope": "item",
      "deletedAt": 1781170005000
    }
  ],
  "nextSince": "1781170005000"
}
```

客户端请求可携带：

```http
X-WebHTV-Since: <上次成功处理的 nextSince>
X-WebHTV-Limit: <单次最大变更数>
```

为了兼容旧客户端，服务端应继续把普通进度放在 `items` 中，并把删除放在独立的 `deleted` 数组中。新客户端可以额外兼容 `deletions`、`tombstones`、`changes` 等形式。

### 冲突规则

同一记录同时存在 upsert 和墓碑时，按事件时间处理：

- `updatedAt <= deletedAt`：跳过旧进度，记录保持删除。
- `updatedAt > deletedAt`：允许重新创建，表示用户删除后又重新播放。
- 同一批响应中先应用删除，再应用 upsert；较新的 upsert 仍可恢复记录。
- 没有有效时间戳的远端删除不能覆盖明确更新的本地记录。

服务端不能只删除当前主记录而丢弃墓碑，否则离线客户端上线后仍无法知道曾发生删除。

## 当前分支特有约束

### 全局历史和 TMDB 聚合

当前 `dev` 支持全局历史及按 TMDB 身份聚合。一个界面卡片可能代表多个底层 `History` 行，并可能跨越多个 `cid/configKey`。

因此单条界面删除必须：

1. 在物理删除前取得所有实际受影响的 `History`；
2. 为每个底层记录计算稳定 `configKey`；
3. 按实际 `siteKey + vodId` 分别生成墓碑和删除事件；
4. 删除对应的 `Track` 后再刷新界面。

只给界面上的代表记录生成一个墓碑，会导致同一 TMDB 条目下其他来源的旧记录再次同步回来。

### 全局清空

全局历史模式下，清空操作可能跨多个点播配置。应按每个实际 `configKey` 生成 `scope=all` 墓碑，不能只使用当前 `VodConfig.getCid()`。

### 删除入口需要集中

Activity、Adapter 和 `History` 当前存在多层清空调用。实现时应由一个领域层删除协调器完成“查询受影响记录、写墓碑、删除数据库、发送事件”，避免在多个 UI 回调中重复发送 Webhook。服务端仍需执行幂等去重。

## Android 项目改造范围

### 本地数据层

- 新增 `PlaybackDeleteTombstone` Room 实体；
- 新增 DAO 和持久化 Store；
- 当前数据库版本为 40，新增迁移必须使用 `40 -> 41`；
- 墓碑定期清理，但不能跟随普通历史清空而立即删除。

### 删除协调层

- 为用户单条删除、站点删除和清空提供统一入口；
- 区分用户主动删除、远端删除和内部 key 迁移，内部迁移不能误发删除事件；
- 用户主动删除写墓碑并发送 `playback.deleted`；
- 远端删除写墓碑但不重复向同一 Webhook 回传，避免事件环路。

### Webhook

- `WebhookConfig` 接受 `deleted` 事件；
- `PlaybackRecord` 增加 `scope`、`deletedAt` 等删除字段；
- 删除事件不发送无意义的进度字段；
- 沿用现有重试和 `Idempotency-Key` 机制。

### 远端拉取

- 引入同时包含 upsert 和 deletion 的响应解析器；
- 先处理删除，再处理普通记录；
- 保存每个远端源、每个 `configKey` 的增量游标；
- 分页截断或批次存在失败时不推进游标；
- 统计结果增加 `deleted` 数量。

### 向后兼容

- 旧服务端只返回数组或 `items` 时，继续按原逻辑同步进度；
- 服务端不返回墓碑时，客户端不能假定远端缺失即删除；
- 新增字段不能影响现有本机写入 API；
- 删除事件应受观影记录同步总开关控制，不一定需要新增独立 UI 开关。

## 历史存储服务端改造范围

若现有服务端尚不支持，必须补充：

1. 接收 `playback.deleted` Webhook；
2. 按 `token + configKey` 校验和分区；
3. 使用 `eventId` 幂等去重；
4. 持久化 `scope`、定位字段和 `deletedAt`；
5. 查询接口同时返回 upsert 和未过期墓碑；
6. 支持或兼容 `X-WebHTV-Since`、`X-WebHTV-Limit`、`nextSince`；
7. 保证同一游标之后的变更顺序稳定；
8. 墓碑到期前不能因主记录被硬删除而丢失删除信息。

如果服务端已经是通用追加事件存储，并能原样返回上述删除事件，则可能无需修改服务端代码，但仍需要联调验证全部契约。

## 参考实现与移植注意事项

`upstream/beta` 的 `f5d63d16ed` 已实现删除墓碑、删除 Webhook、删除感知的远端解析和增量游标，可作为客户端实现蓝本。

不能直接无审查地 cherry-pick：

- 该提交所在分支的数据库版本是 37，当前 `dev` 已是 40；
- 当前分支在其后增加了全局历史、TMDB 聚合和跨配置续播；
- `History`、`HistoryDao`、`AppDatabase` 和历史页面删除路径已经发生较大变化；
- 原提交没有覆盖当前“一个展示项对应多个底层记录”的删除语义。

建议按功能拆分移植，而不是整提交直接合入。

## 推荐实施顺序

1. 先冻结删除事件和远端响应协议；
2. 服务端先兼容接收并返回墓碑，保持旧 `items` 响应不变；
3. Android 客户端增加本地墓碑和删除事件；
4. Android 客户端增加删除感知的远端拉取；
5. 开启端到端联调和监控；
6. 验证墓碑保留期后再制定清理策略。

## 验证清单

- 设备 A 删除单条记录，设备 B 下一次同步删除；
- 设备 B 离线多个同步周期后上线，仍能收到墓碑；
- 墓碑之后收到更旧的 upsert，不会复活；
- 墓碑之后发生新播放，新 upsert 可以恢复；
- `scope=site` 只影响指定站点；
- `scope=all` 只影响指定 `configKey`；
- 全局历史清空覆盖所有实际配置；
- TMDB 聚合展示项删除覆盖全部底层来源；
- 重复删除事件不会重复产生副作用；
- 分页、`maxItems` 和失败重试不会跳过墓碑；
- 不同 token 或 `configKey` 之间不串数据；
- 旧服务端和旧响应格式仍可同步普通进度；
- 内部历史 key 迁移不会被误判为用户删除。

## 非目标

- 不通过“远端列表中不存在”推断本地删除；
- 不把本机 `cid` 当作跨设备稳定身份；
- 不在没有持久化墓碑的情况下承诺离线设备最终一致；
- 本文只记录设计与边界，不表示当前版本已经完成删除同步。
