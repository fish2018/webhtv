# 设置分类完整评估与调整记录

## 结论

当前设置按 用户任务和功能依赖重新划分，而不是单纯按技术名词堆叠：

- **TMDB**：所有以 TMDB 数据、TMDB 详情页模式或 TMDB 关联逻辑为前提的设置。
- **AI**：真正调用 AI 模型、AI 推荐或 AI 检测能力的设置。
- **增强**：通用增强能力、规则/网络/调试/同步，以及不依赖 AI 的广告处理。
- **个性化**：首页、搜索、播放历史与用户偏好。
- **字幕**：字幕来源、自动匹配与语言偏好；AI 字幕模型单独归入 AI。

TMDB 与 AI 仍然保持两个独立设置页。两者都是能力配置，但依赖对象、密钥、故障排查方式和用户心智模型不同，合并反而会让页面变长且难以定位。

## 逐项归类

### TMDB 设置页

| 设置 ID | 归类 | 评估 |
|---|---|---|
| tmdbSource | TMDB | TMDB 地址/API Key/配置状态，是所有 TMDB 能力的入口。 |
| detailInteractionMode | TMDB | 详情页模式虽然包含直连选项，但其余模式均为 TMDB 详情模式，且会直接控制 tmdb_enabled 与 TMDB Key 配置；属于 TMDB 的核心开关，不应留在增强页。 |
| detailThemeMode | TMDB | 详情页主体/样式只服务于 TMDB 详情页展示，和 TMDB 详情模式绑定；与通用主题不是同一层概念。 |
| tmdbMatchMode | TMDB | 控制媒体与 TMDB 条目的匹配方式，纯 TMDB 数据语义。 |
| tmdbEpisodeFileSize | TMDB | 按 TMDB 识别的剧集文件大小处理规则，只有 TMDB 识别链路才会使用。 |
| historyAggregation | TMDB | 该开关按 TMDB 条目聚合观看历史，并且只有 TMDB 配置 ready 时生效；移动到 TMDB 页最准确。 |

**关于详情页模式和主体**：是的，这两项都应放在 TMDB 页。详情页模式是 TMDB 能力的入口，详情页主体/样式是 TMDB 详情页的表现层配置；直连只是同一个模式选择器里的非 TMDB 分支，不足以改变整体归属。

**关于 TMDB 交互模型**：当前实现只有一个固定的原生模型，且没有可选项或交互行为。`tmdb_model` 配置键保留用于历史备份兼容和未来扩展，但不应作为用户可见设置展示。

### AI 设置页

| 设置 ID | 归类 | 评估 |
|---|---|---|
| aiRecommendation | AI | AI 推荐服务/模型配置，直接依赖 AI 能力。 |
| personalRecommendation | AI | 使用 AI 生成个性化推荐结果，属于 AI 功能开关。 |
| recommendationFeedback | AI | 推荐反馈用于改进/调整 AI 推荐，和推荐链路强相关。 |
| aiAdDetection | AI | AI 广告识别/检测，属于模型能力，不应与普通广告规则混在一起。 |
| subtitleRealtimeModel | AI | 实时字幕翻译/处理模型选择，直接调用 AI 模型。 |
| subtitleAiSettings | AI | AI 字幕并发数、分块数等模型调用参数，和实时字幕模型成套管理。 |
| subtitleAiMaxConcurrency | AI | AI 字幕请求并发参数。 |
| subtitleAiChunkCount | AI | AI 字幕分块/批处理参数。 |

### 增强设置页

| 设置 ID | 归类 | 评估 |
|---|---|---|
| adRuleManage | 增强 | **不是强依赖 AI**。规则管理同时包含用户手工规则、接口默认规则、内置 HLS 规则、导入规则，以及可选的 AI 产生规则；管理对象是广告规则，而不是 AI 模型。 |
| adAudioFingerprint | 增强 | 音频指纹规则/处理能力，不等于 AI 检测，不依赖 AI 配置。 |
| driveCheck | 增强 | 网盘/驱动检测类通用能力。 |
| siteName | 增强 | 站点名称显示与识别增强。 |
| audioSource | 增强 | 音频源选择。 |
| shortDramaSource | 增强 | 短剧源选择。 |
| debugLog | 增强 | 调试与诊断开关。 |
| siteHealthSort | 增强 | 站点健康度排序。 |
| managePage | 增强 | 管理页/管理能力入口。 |
| remoteTrust | 增强 | 远程内容信任策略。 |
| gitCloud | 增强 | Git 云端配置/同步。 |
| shellProxy / shellProxyConfig | 增强 | 代理能力及其配置。 |
| customCsp | 增强 | 自定义 CSP。 |
| webHomeExtension / webHomeTheme / webHomeFullscreen | 增强 | Web 首页扩展、主题与全屏行为。 |
| cspWarmup | 增强 | CSP 预热/初始化。 |
| playbackArtworkWall | 增强 | 播放时艺术图/背景墙。 |
| playbackWebhook | 增强 | 播放事件 Webhook。 |
| loginState | 增强 | 登录状态处理。 |
| oneKeySync | 增强 | 通用配置一键同步。 |
| githubRepo / cnbRepo | 增强 | TV 端的代码仓库/云端来源配置。 |

### 个性化设置页

| 设置 ID | 归类 | 评估 |
|---|---|---|
| homeVodAutoLoad / homeSiteLock | 个性化 | 首页加载和站点锁定偏好。 |
| autoBackup | 个性化 | 用户数据自动备份偏好。 |
| homeButtons / fullscreenMenuKey / homeMenuKey | 个性化 | 首页按钮、全屏菜单键和首页菜单键布局/交互偏好。 |
| playBackToDetail | 个性化 | 播放结束或返回时是否回到详情页的通用体验偏好。 |
| episodeHistory | 个性化 | 单站点/剧集历史行为。 |
| globalHistory | 个性化 | 跨来源的全局续播/历史行为；虽然 TMDB 会参与部分识别，但它不是 TMDB 专属功能，保留在个性化页更合理。 |
| playSpeed | 个性化 | 播放速度偏好。 |
| groupRule | 个性化 | 剧集/分组展示规则偏好。 |
| homeHistory | 个性化 | 首页历史展示偏好。 |
| searchThread | 个性化 | 搜索线程数/搜索性能偏好。 |
| searchUi / searchColumn / siteColumn / searchResultSort | 个性化 | 搜索界面、列数、站点列和排序偏好。 |
| resetApp | 个性化 | 用户级应用重置入口。 |

### 字幕设置页

| 设置 ID | 归类 | 评估 |
|---|---|---|
| subtitleAutoMatch | 字幕 | 字幕自动匹配策略，不等同于 AI 字幕模型。 |
| subtitleLanguage | 字幕 | 字幕语言偏好。 |
| subtitleAssrtToken | 字幕 | Assrt 字幕源凭证，属于字幕服务配置。 |

## 关键边界

1. **规则管理 ≠ AI 设置**：AI 广告检测只负责识别，规则管理负责查看、启用、禁用、添加、导入和删除规则。两者可以互相产生数据，但不是强依赖关系。
2. **详情页模式/主体 = TMDB 设置**：四种模式依赖 TMDB，直连只是兼容分支；详情主题也只在 TMDB 详情页模式下展示。
3. **TMDB 历史聚合 = TMDB 设置**：它改变的是 TMDB 条目维度的历史聚合，不是普通的全局历史开关。
4. **全局历史 ≠ TMDB 设置**：全局历史服务于所有来源的续播和历史体验，即使内部可能使用 TMDB 辅助识别，也不应让用户误以为必须配置 TMDB。
5. **AI 字幕参数 = AI 设置，普通字幕匹配 = 字幕设置**：按是否调用模型切分，而不是按字幕这个业务名词全部放在一起。

## 最终页面结构

- 设置首页：TMDB、AI、增强、个性化、字幕，以及播放器/弹幕等现有独立入口。
- TMDB：TMDB 配置 + 详情页模式/主体 + 匹配 + TMDB 剧集文件大小 + TMDB 历史聚合。
- AI：推荐、反馈、AI 广告检测、实时字幕模型及 AI 字幕参数。
- 增强：通用增强能力 + 广告规则管理 + 音频指纹规则。
- 个性化：首页、搜索、播放历史和用户体验偏好。
- 字幕：字幕源、自动匹配和语言设置。
