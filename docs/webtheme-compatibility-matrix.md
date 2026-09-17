# WebTheme Host API 兼容矩阵

<!-- 由 WebThemeCompatibilityMatrix 从运行时注册表生成；请勿手工修改表格。 -->

> 运行时事实源：`WebThemeCapabilityRegistry`；页面契约事实源：`WebThemePage`。

- Manifest Schema：`2`
- Host API：`3`

## 页面契约

| 页面 | Manifest Key | 基础契约 | 基础权限 |
| --- | --- | --- | --- |
| `HOME` | `home` | `vod.home@1` | `vod.home` |
| `DETAIL` | `detail` | `vod.detail@1` | `vod.detail` |

## Bridge 能力

| Method | Capability ID | Permission | Pages | Contract | V1 Legacy | Manifest Required |
| --- | --- | --- | --- | ---: | --- | --- |
| `theme.info` | `theme.info@1` | — | `HOME`, `DETAIL` | `1` | 否 | 否 |
| `ui.getViewport` | `ui.getViewport@1` | — | `HOME`, `DETAIL` | `1` | 是 | 否 |
| `navigation.back` | `navigation.back@1` | — | `HOME`, `DETAIL` | `1` | 是 | 否 |
| `navigation.reload` | `navigation.reload@1` | — | `HOME`, `DETAIL` | `1` | 是 | 否 |
| `navigation.openNativeDetail` | `navigation.openNativeDetail@1` | — | `DETAIL` | `1` | 否 | 否 |
| `vod.home` | `vod.home@1` | `vod.home` | `HOME` | `1` | 是 | 是 |
| `vod.category` | `vod.category@1` | `vod.category` | `HOME` | `1` | 是 | 是 |
| `navigation.openDetail` | `navigation.openDetail@1` | `navigation.openDetail` | `HOME` | `1` | 否 | 是 |
| `vod.detail` | `vod.detail@1` | `vod.detail` | `DETAIL` | `1` | 否 | 是 |
| `favorite.status` | `favorite.read@1` | `favorite.read` | `DETAIL` | `1` | 否 | 是 |
| `favorite.set` | `favorite.write@1` | `favorite.write` | `DETAIL` | `1` | 否 | 是 |
| `history.item` | `history.read@1` | `history.read` | `DETAIL` | `1` | 否 | 是 |
| `player.playVod` | `player.playVod@1` | `player.playVod` | `DETAIL` | `1` | 是 | 是 |
| `app.search` | `app.search@1` | `app.search` | `HOME`, `DETAIL` | `1` | 是 | 是 |
| `app.openVod` | `app.openVod@1` | `app.openVod` | `HOME` | `1` | 是 | 是 |
| `app.openSite` | `app.openSite@1` | `app.openSite` | `HOME` | `1` | 是 | 是 |
| `app.openSetting` | `app.openSetting@1` | `app.openSetting` | `HOME` | `1` | 是 | 是 |
| `person.open` | `person.open@1` | `person.open` | `DETAIL` | `1` | 否 | 是 |
| `image.preview` | `image.preview@1` | `image.preview` | `DETAIL` | `1` | 否 | 是 |
| `image.save` | `image.save@1` | `image.save` | `DETAIL` | `1` | 否 | 是 |
| `recommendation.open` | `recommendation.open@1` | `recommendation.open` | `DETAIL` | `1` | 否 | 是 |
| `recommendation.info` | `recommendation.info@1` | `recommendation.info` | `DETAIL` | `1` | 否 | 是 |
| `recommendation.feedback` | `recommendation.feedback@1` | `recommendation.feedback` | `DETAIL` | `1` | 否 | 是 |
| `external.open` | `external.open@1` | `external.open` | `DETAIL` | `1` | 否 | 是 |
| `episode.info` | `episode.info@1` | `episode.info` | `DETAIL` | `1` | 否 | 是 |
