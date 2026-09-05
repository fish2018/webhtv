# HLS 内置规则来源与维护

内置 HLS 规则必须默认关闭，并同时具备正确命中和防误杀测试。规则 ID 是永久逻辑身份，更新匹配条件时只增加 `version`，不修改 ID。

## 当前公开来源

- `guxiangbin/tvbox2` 的公开 TVBox 配置：暴风、量子、非凡规则的 host 特征和典型时长。
  - https://github.com/guxiangbin/tvbox2/blob/main/%E7%A5%9E%E7%A7%98%E5%B0%8F%E7%B1%B3.txt
- `1771245847/TvBox` 的公开配置：量子、非凡、暴风等规则的补充 host 特征和时长。
  - https://github.com/1771245847/TvBox/blob/master/MoonYs.json

这些来源是社区配置，不是资源站官方协议。域名和广告时长可能变化，因此本项目只把它们作为默认关闭的实验规则，不能据此保证长期有效。

## 当前内置规则

| ID | 作用域特征 | 删除信号 | 默认状态 |
|---|---|---|---|
| `builtin.hls.baofeng.preroll` | `bfzy` / `s5.bfzycdn` | discontinuity + 约 3 秒 | 关闭 |
| `builtin.hls.liangzi.preroll` | `vip.lz` / `hd.lz` / `cdnlz` | discontinuity + 约 6.433333 秒 | 关闭 |
| `builtin.hls.feifan.preroll` | `vip.ffzy` / `hd.ffzy` / `super.ffzy` | discontinuity + 约 6.666667 秒 | 关闭 |

## 更新要求

1. 提供脱敏后的实际 manifest 样本或公开配置证据。
2. 至少一个应删除 fixture。
3. 至少一个相同时长但没有广告边界的反 fixture。
4. 至少一个错误 playlist host 的反 fixture。
5. 保持默认关闭，除非有持续命中率和误杀率数据支持默认开启。
6. 不复制私有订阅 token、Cookie、鉴权 URL 或无法确认授权的配置内容。
