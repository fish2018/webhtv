# WebHTV 开发者接入文档

本目录只记录当前仓库代码实际支持的接入能力。字段、默认值、调用链、返回协议和错误处理以源码为准。

## 索引

- [总配置接入](configuration.md)：顶层配置、网络规则、Doh/Proxy/Headers/Rules。
- [点播源接入](vod-site.md)：Site 字段、站点类型、WebHome chrome、验证清单。
- [Spider 接入协议](spider.md)：Java/JAR、JS、Python、CatSpider 与生命周期。
- [Result 与 Vod 返回协议](result-vod.md)：列表、分类、筛选、详情和线路结构。
- [直播源接入](live.md)：TXT/M3U/JSON/Spider、Group、Channel、Catchup、EPG、DRM。
- [解析器接入](parser.md)：解析类型、`ext.flag/header`、`playUrl` 前缀。
- [字幕接入](subtitle.md)：Spider `subs`、格式、鉴权和局域网注入。
- [弹幕接入](danmaku.md)：Spider `danmaku`、来源字段和注入。
- [播放结果与播放器接入](player.md)：播放字段、多 URL、DRM、错误处理。
- [本地代理与服务端接口](proxy-server.md)：本地服务、Spider proxy、Action 和端点。
- [扩展包接入](extensions.md)：JAR/JS/Python/CatSpider 加载与验证。

## 推荐阅读顺序

1. [总配置接入](configuration.md)
2. 根据场景读 [点播源接入](vod-site.md) 或 [直播源接入](live.md)
3. [Spider 接入协议](spider.md) 与 [Result 与 Vod 返回协议](result-vod.md)
4. [播放结果与播放器接入](player.md)，需要时读解析、字幕、弹幕和代理
5. 使用非 Java 运行时读 [扩展包接入](extensions.md)

## 源码总览

- 配置：`app/src/main/java/com/fongmi/android/tv/api/config/`
- Bean/协议模型：`app/src/main/java/com/fongmi/android/tv/bean/`
- Spider 接口：`catvod/src/main/java/com/github/catvod/crawler/Spider.java`
- 运行时：`app/src/main/java/com/fongmi/android/tv/api/loader/`、`quickjs/`、`chaquo/`
- 本地服务：`app/src/main/java/com/fongmi/android/tv/server/`

更完整的 WebHome、管理页面、观影记录同步和构建说明见仓库根目录 `README.md` 和 `webhome-devkit/docs/应用完整开发文档.md`。
