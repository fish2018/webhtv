package com.fongmi.android.tv.bean;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class AiConfig {

    private static final Gson GSON = new Gson();
    public static final String PROTOCOL_OPENAI_RESPONSES = "openai_responses";
    public static final String PROTOCOL_OPENAI_CHAT = "openai_chat";
    public static final String PROTOCOL_ANTHROPIC_MESSAGES = "anthropic_messages";
    public static final String PROTOCOL_GEMINI_NATIVE = "gemini_native";
    public static final String DEFAULT_PROTOCOL = PROTOCOL_OPENAI_RESPONSES;
    public static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/responses";
    public static final String DEFAULT_OPENAI_CHAT_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    public static final String DEFAULT_ANTHROPIC_ENDPOINT = "https://api.anthropic.com/v1/messages";
    public static final String DEFAULT_GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta";
    public static final String DEFAULT_MODEL = "gpt-4.1-mini";
    public static final int DEFAULT_RECOMMEND_PROMPT_VERSION = 3;
    public static final String LEGACY_RECOMMEND_PROMPT_V1 = "你是一位专业的影视剧推荐专家，熟悉全球影视内容，包括电视剧、电影、动漫、纪录片等。你的任务是根据用户提供的观影历史和搜索记录，分析用户的偏好，并输出个性化的影视推荐列表。只返回可解析 JSON，不要解释，建议格式为 {\"items\":[{\"title\":\"片名\",\"year\":2024,\"mediaType\":\"movie 或 tv\",\"reason\":\"一句推荐理由\"}]}。优先推荐不同于历史记录和当前影片的作品，推荐数量由提示词自行决定。";
    public static final String LEGACY_RECOMMEND_PROMPT_V2 = "你是一位专业的影视推荐专家，熟悉全球电影、电视剧、动漫、纪录片、综艺及短剧内容。"
            + "你的任务是根据用户的当前影片、播放历史、搜索记录、观看进度和内容元数据，分析用户偏好，并输出个性化影视推荐。"
            + "请重点分析用户偏好的题材、类型、国家/地区、语言、年代、叙事风格、节奏和受众倾向；"
            + "播放历史中的观看深度、已看集数、观看时长、完播率和最近观看时间权重更高；"
            + "搜索记录代表兴趣意向，但不等同于已观看；当前影片代表即时兴趣，应提高相似题材、相似气质、相似受众作品的权重。"
            + "优先推荐不同于当前影片和播放历史的作品，避免推荐用户已经看过或正在看的片名、别名和易混淆续作。"
            + "推荐结果应兼顾相似偏好和适度拓展，不要全部集中在同一题材；如果历史中包含乱码、异常标题、合集、非影视内容，请降低其权重。"
            + "推荐数量为 12-24 部，默认推荐 16 部；若用户历史较少则推荐 12 部，历史丰富则推荐 18-24 部。"
            + "只返回可解析 JSON，不要解释、Markdown 或多余文本，格式为 {\"items\":[{\"title\":\"片名\",\"year\":2024,\"mediaType\":\"movie 或 tv\",\"reason\":\"一句推荐理由\"}]}。"
            + "mediaType 只能使用 movie 或 tv，reason 控制在 20-45 个中文字符，并说明它为什么适合这个用户。";
    public static final String DEFAULT_RECOMMEND_PROMPT = LEGACY_RECOMMEND_PROMPT_V2
            + "用户明确标记为不感兴趣的作品属于强负反馈，必须排除其片名、别名、翻拍版和明显同名混淆项，不能因题材、演员、导演或搜索记录相似而重新推荐。";
    public static final int DEFAULT_TITLE_EXTRACTION_PROMPT_VERSION = 2;
    public static final String LEGACY_TITLE_EXTRACTION_PROMPT_V1 = "你是影视资源标题解析器。你的任务是从网盘/资源站标题中提取真实影视作品名。\n"
            + "要求：只返回严格 JSON，不要 Markdown，不要解释；不要保留清晰度、编码、字幕、语言版本、更新状态、集数、合集、资源组、平台名；"
            + "如果标题是拼音缩写、谐音、防和谐写法，请尽量还原为中文正式片名；学习样本来自用户本机手动修正，相关时优先，但不要强行套用；不要编造 TMDB ID。\n"
            + "返回格式：{\"canonicalTitle\":\"剧名\",\"originalTitle\":\"\",\"mediaType\":\"tv|movie|unknown\",\"year\":0,\"seasonNumber\":-1,\"episodeNumber\":-1,\"episodeTitle\":\"\",\"aliases\":[\"别名\"],\"confidence\":0.0,\"reasonCode\":\"clean|homophone|pinyin|uncertain\"}";
    public static final String DEFAULT_TITLE_EXTRACTION_PROMPT = "你是影视资源标题解析器。你的任务是从网盘、资源站、推送链接标题中提取真实影视作品名。\n"
            + "要求：只返回严格 JSON，不要 Markdown，不要解释；不要保留清晰度、编码、字幕、语言版本、更新状态、集数、合集、资源组、平台名、线路名或分类词。"
            + "标题中的 #、空格、点、短横线、竖线常用于分隔或防和谐；如果两侧中文片段能组成正式片名，请合并还原，例如“凡人#修仙传”应提取为“凡人修仙传”。"
            + "开头或结尾的单字母、A/B/C、动漫、电影、电视剧、剧集等通常是资源分组或分类，不属于作品名。"
            + "如果标题是拼音缩写、谐音、防和谐写法，请尽量还原为中文正式片名；学习样本来自用户本机手动修正，相关时优先，但不要强行套用；不要编造 TMDB ID。\n"
            + "返回格式：{\"canonicalTitle\":\"剧名\",\"originalTitle\":\"\",\"mediaType\":\"tv|movie|unknown\",\"year\":0,\"seasonNumber\":-1,\"episodeNumber\":-1,\"episodeTitle\":\"\",\"aliases\":[\"别名\"],\"confidence\":0.0,\"reasonCode\":\"clean|homophone|pinyin|separator|uncertain\"}";
    public static final int DEFAULT_VIEWING_REPORT_PROMPT_VERSION = 2;
    public static final String LEGACY_VIEWING_REPORT_PROMPT_V1 = "你是观影行为分析专家。根据用户的观影统计数据和片单，生成深度个性化的观影画像报告。\n"
            + "输入数据包含：总时长、作品数、集数、完播率、观影时段分布、周末占比、深夜次数、题材分布、演员分布、地区分布、片名列表等。\n"
            + "你的任务：\n"
            + "1. summary: 生成 60-120 字的个性化观影画像，结合具体数据，分析用户的观影风格、偏好特征、观影习惯，语气温暖有趣，像朋友聊天。\n"
            + "2. tags: 提取 4-6 个风格标签，每个 2-6 字，例如“悬疑爱好者”“深夜观影党”“追剧达人”“冷门发掘者”，基于数据推断而非编造。\n"
            + "3. badges: 判定成就徽章，格式为 [{\"id\":\"drama_king\",\"name\":\"剧集狂魔\",\"reason\":\"完播 12 部长剧\"}]，可选 id: drama_king(完播>=10部剧)、night_owl(深夜观影>=20次)、indie_explorer(小众作品>=5部)、marathon(单日>=5小时)、loyal_fan(同演员>=3部)、globe_trotter(>=5国作品)、early_bird(上午观影>=15次)，只返回满足条件的徽章。\n"
            + "4. genreInsights: 基于题材分布和片名，推断用户的题材偏好深层原因，例如“你偏爱节奏紧凑的悬疑推理，可能享受烧脑解谜的过程”，40-80字。\n"
            + "5. highlights: 列举 2-3 个观影数据中的有趣亮点，例如[\"深夜观影 23 次，是夜猫子体质\",\"完播率 68%，选片有品味但不强迫症\"]，每条 15-30字。\n"
            + "6. recommendationHint: 基于偏好给出推荐方向提示，例如“可以尝试日本推理剧或欧美悬疑电影”，20-40字。\n"
            + "要求：只返回严格 JSON，不要 Markdown、不要解释，格式为 {\"summary\":\"...\",\"tags\":[...],\"badges\":[...],\"genreInsights\":\"...\",\"highlights\":[...],\"recommendationHint\":\"...\"}。\n"
            + "不要编造数据里没有的内容，若某个维度数据不足(如题材为空)则该字段返回空字符串或空数组。";
    public static final String DEFAULT_VIEWING_REPORT_PROMPT = "你是资深观影行为分析师，擅长从数据里读出用户真实的观看偏好，而不是套话吹捧。你的分析要诚实、具体、有洞察，像一个懂数据又会说人话的朋友。\n"
            + "\n"
            + "【分析原则：基于统计数据做推理，禁止编造】\n"
            + "- 只能使用输入里出现的题材、演员、地区等统计。输入没给的维度一律不要提，更不能虚构“你喜欢悬疑”这类无依据结论。\n"
            + "- 若题材/演员/地区为空，就明说这部分数据还没积累起来。\n"
            + "- 发现数据里的有趣对比或倾向时要点出来（例如深夜占比高、某演员反复出现、时段高度集中、某题材远超其它）。\n"
            + "- 数据量很少时坦诚说明样本还不够，宁可少下结论，也不要为凑字数硬编。\n"
            + "\n"
            + "【输出字段】只返回严格 JSON，不要 Markdown、不要解释：\n"
            + "{\"summary\":\"...\",\"tags\":[...],\"badges\":[...],\"genreInsights\":\"...\",\"highlights\":[...],\"recommendationHint\":\"...\"}\n"
            + "1. summary: 60-120字个性化观影画像，扣住真实数据分析观影偏好与习惯，语气温暖有趣但不违心吹捧。\n"
            + "2. tags: 4-6个风格标签，每个2-6字，必须由数据支撑（如“悬疑爱好者”“深夜观影党”“追剧达人”）；数据太少时可只给2-3个诚实的标签。\n"
            + "3. badges: 满足条件才给，格式[{\"id\":\"night_owl\",\"name\":\"深夜观影者\",\"reason\":\"深夜观看11次\"}]。可选id: drama_king(完播>=10部剧)、night_owl(深夜>=20次)、indie_explorer(小众>=5部)、marathon(单日>=5小时)、loyal_fan(同演员>=3部)、globe_trotter(>=5国)、early_bird(上午>=15次)。不满足就返回空数组，不要为凑数硬发。\n"
            + "4. genreInsights: 只在有题材数据时给，40-80字，推断题材偏好的深层原因；没有就返回空字符串。\n"
            + "5. highlights: 2-3条真实亮点或有趣事实，每条15-30字，可包含数据里的倾向或对比。\n"
            + "6. recommendationHint: 20-40字方向建议，基于用户真实偏好给具体题材或类型方向。\n"
            + "\n"
            + "整体要求：宁可少说、说实话，也不要用空泛好话堆字数。所有结论都要能在输入数据里找到依据。";
    // v1 (legacy): URL 特征分析（未解析 m3u8）
    public static final String DEFAULT_AD_DETECTION_PROMPT_V1 = "你是视频广告特征识别专家。根据用户反馈的视频播放上下文，分析可能的广告 URL 特征，输出去广规则建议。\n"
            + "\n"
            + "常见广告特征：\n"
            + "- 主机名含 ad/ads/adv/adx/adservice/adsdk/advertisement 等广告相关词\n"
            + "- 路径含 /ad/ /ads/ /preroll/ /midroll/ /commercial/ 等广告相关段\n"
            + "- 与主内容域名不同的第三方域\n"
            + "\n"
            + "输出要求：只返回严格 JSON，不要 Markdown、不要解释。格式：\n"
            + "{\"confidence\":0.0,\"hostsBlacklist\":[\"域名片段\"],\"regexPatterns\":[\"广告URL正则\"],\"excludePatterns\":[\"正片保护正则\"],\"reasoning\":\"简短分析理由\"}\n"
            + "\n"
            + "规则设计原则：\n"
            + "- hostsBlacklist 越具体越好，避免误伤正常域名\n"
            + "- regexPatterns 命中即视为广告，需转义特殊字符，避免匹配过宽\n"
            + "- excludePatterns 命中即视为正片，用于保护已知正片 URL\n"
            + "- confidence 取 0.0-1.0，低于 0.5 时在 reasoning 中提示不确定，建议人工审核";

    // v2 (current): m3u8 切片证据分析
    public static final int DEFAULT_AD_DETECTION_PROMPT_VERSION = 2;
    public static final String DEFAULT_AD_DETECTION_PROMPT = "你是视频广告特征识别专家。根据用户反馈的 m3u8 切片列表，分析可能的广告片段，输出去广规则建议。\n"
            + "\n"
            + "广告特征（优先级递减）：\n"
            + "1. #EXT-X-DISCONTINUITY 标记 + 前后切片跨域，高置信度广告边界\n"
            + "2. 切片域名与主内容域不同（跨域）且含 ad/ads/adv/adservice/preroll/midroll 等\n"
            + "3. 连续跨域切片 + 时长累计 5-60 秒（典型广告时长）\n"
            + "4. 路径含 /ad/ /ads/ /preroll/ /midroll/ /commercial/ 等广告相关段\n"
            + "\n"
            + "输出要求：只返回严格 JSON，不要 Markdown、不要解释。格式：\n"
            + "{\"confidence\":0.0,\"hostsBlacklist\":[\"域名片段\"],\"regexPatterns\":[\"广告URL正则\"],\"excludePatterns\":[\"正片保护正则\"],\"reasoning\":\"简短分析理由\"}\n"
            + "\n"
            + "规则设计原则：\n"
            + "- hostsBlacklist 越具体越好，避免误伤正常域名\n"
            + "- regexPatterns 命中即视为广告，需转义特殊字符，避免匹配过宽\n"
            + "- excludePatterns 命中即视为正片，用于保护已知正片 URL\n"
            + "- confidence 取 0.0-1.0，低于 0.5 时在 reasoning 中说明证据不足，建议人工审核\n"
            + "- 若无明显广告特征（无 DISCONTINUITY、无跨域、无关键词），confidence 应低于 0.3";

    public static final int DEFAULT_GROUP_RULE_PROMPT_VERSION = 2;
    public static final String LEGACY_GROUP_RULE_PROMPT_V1 = "你是影视点播源名称分组规则生成器。请根据源名称生成 Java 正则表达式数组，每条规则的第 1 个捕获组作为分组名。只返回 JSON：{\"rules\":[{\"name\":\"规则名称\",\"regex\":\"Java正则\"}]}。";
    public static final String DEFAULT_GROUP_RULE_PROMPT = "你是影视点播源名称的分组规则生成器。\n"
            + "你的任务是从点播源显示名称中识别可重复出现的分类、线路、采集方、内容类型或质量标签，并生成尽可能少而稳定的 Java 正则规则。\n"
            + "严格要求：\n"
            + "1. 只返回严格 JSON，不要 Markdown、解释、注释或多余文字。\n"
            + "2. 输出格式：{\"rules\":[{\"name\":\"规则名称\",\"regex\":\"Java正则\",\"reason\":\"一句简短说明\"}]}。\n"
            + "3. 每条 regex 必须兼容 java.util.regex.Pattern，第 1 个捕获组必须是最终分组名。\n"
            + "4. 分组名必须来自多个源名称中重复出现的稳定片段，不要捕获整个源名称。\n"
            + "5. 优先识别方括号、圆括号、竖线、短横线、冒号、圆点等分隔符附近的标签。\n"
            + "6. 不要为只出现一次的文字生成规则；不要生成重复或等价规则。\n"
            + "7. 不要使用回溯引用、复杂嵌套量词或不受支持的语法。\n"
            + "8. 最多输出 6 条规则，JSON 中的反斜杠必须正确转义。";

    @SerializedName("enabled")
    private boolean enabled;
    @SerializedName(value = "protocol", alternate = {"apiFormat", "format"})
    private String protocol;
    @SerializedName("endpoint")
    private String endpoint;
    @SerializedName("apiKey")
    private String apiKey;
    @SerializedName("model")
    private String model;
    @SerializedName(value = "customUserAgent", alternate = {"userAgent", "ua"})
    private String customUserAgent;
    @SerializedName("recommendPrompt")
    private String recommendPrompt;
    @SerializedName("recommendPromptVersion")
    private int recommendPromptVersion;
    @SerializedName("recommendPromptCustom")
    private boolean recommendPromptCustom;
    @SerializedName("titleExtractionPrompt")
    private String titleExtractionPrompt;
    @SerializedName("titleExtractionPromptVersion")
    private int titleExtractionPromptVersion;
    @SerializedName("titleExtractionPromptCustom")
    private boolean titleExtractionPromptCustom;
    @SerializedName("viewingReportPrompt")
    private String viewingReportPrompt;
    @SerializedName("viewingReportPromptVersion")
    private int viewingReportPromptVersion;
    @SerializedName("viewingReportPromptCustom")
    private boolean viewingReportPromptCustom;
    @SerializedName("adDetectionPrompt")
    private String adDetectionPrompt;
    @SerializedName("adDetectionPromptVersion")
    private int adDetectionPromptVersion;
    @SerializedName("adDetectionPromptCustom")
    private boolean adDetectionPromptCustom;
    @SerializedName("groupRulePrompt")
    private String groupRulePrompt;
    @SerializedName("groupRulePromptVersion")
    private int groupRulePromptVersion;
    @SerializedName("groupRulePromptCustom")
    private boolean groupRulePromptCustom;

    public static AiConfig objectFrom(String json) {
        try {
            AiConfig config = GSON.fromJson(json, AiConfig.class);
            return config == null ? new AiConfig().sanitize() : config.sanitize();
        } catch (Exception e) {
            return new AiConfig().sanitize();
        }
    }

    public AiConfig sanitize() {
        protocol = isSupportedProtocol(protocol) ? protocol.trim() : DEFAULT_PROTOCOL;
        endpoint = trimOr(endpoint, defaultEndpoint(protocol));
        apiKey = trimOr(apiKey, "");
        model = trimOr(model, DEFAULT_MODEL);
        customUserAgent = trimOr(customUserAgent, "");
        sanitizeRecommendPrompt();
        sanitizeTitleExtractionPrompt();
        sanitizeViewingReportPrompt();
        sanitizeAdDetectionPrompt();
        sanitizeGroupRulePrompt();
        return this;
    }

    public boolean isReady() {
        sanitize();
        return enabled && !isEmpty(endpoint) && !isEmpty(apiKey) && !isEmpty(model);
    }

    public boolean isModelFetchReady() {
        sanitize();
        return !isEmpty(endpoint) && !isEmpty(apiKey);
    }

    public String toJson() {
        return GSON.toJson(sanitize());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getCustomUserAgent() {
        return customUserAgent;
    }

    public void setCustomUserAgent(String customUserAgent) {
        this.customUserAgent = customUserAgent;
    }

    public String getRecommendPrompt() {
        return recommendPrompt;
    }

    public void setRecommendPrompt(String recommendPrompt) {
        String value = recommendPrompt == null ? "" : recommendPrompt.trim();
        if (isEmpty(value) || isBuiltInRecommendPrompt(value)) {
            resetRecommendPrompt();
        } else {
            this.recommendPrompt = value;
            this.recommendPromptCustom = true;
            this.recommendPromptVersion = DEFAULT_RECOMMEND_PROMPT_VERSION;
        }
    }

    public int getRecommendPromptVersion() {
        return recommendPromptVersion;
    }

    public boolean isRecommendPromptCustom() {
        return recommendPromptCustom;
    }

    public void resetRecommendPrompt() {
        recommendPrompt = DEFAULT_RECOMMEND_PROMPT;
        recommendPromptCustom = false;
        recommendPromptVersion = DEFAULT_RECOMMEND_PROMPT_VERSION;
    }

    public String getTitleExtractionPrompt() {
        return titleExtractionPrompt;
    }

    public void setTitleExtractionPrompt(String titleExtractionPrompt) {
        String value = titleExtractionPrompt == null ? "" : titleExtractionPrompt.trim();
        if (isEmpty(value) || isBuiltInTitleExtractionPrompt(value)) {
            resetTitleExtractionPrompt();
        } else {
            this.titleExtractionPrompt = value;
            this.titleExtractionPromptCustom = true;
            this.titleExtractionPromptVersion = DEFAULT_TITLE_EXTRACTION_PROMPT_VERSION;
        }
    }

    public int getTitleExtractionPromptVersion() {
        return titleExtractionPromptVersion;
    }

    public boolean isTitleExtractionPromptCustom() {
        return titleExtractionPromptCustom;
    }

    public void resetTitleExtractionPrompt() {
        titleExtractionPrompt = DEFAULT_TITLE_EXTRACTION_PROMPT;
        titleExtractionPromptCustom = false;
        titleExtractionPromptVersion = DEFAULT_TITLE_EXTRACTION_PROMPT_VERSION;
    }

    public String getViewingReportPrompt() {
        return viewingReportPrompt;
    }

    public void setViewingReportPrompt(String viewingReportPrompt) {
        String value = viewingReportPrompt == null ? "" : viewingReportPrompt.trim();
        if (isEmpty(value) || isBuiltInViewingReportPrompt(value)) {
            resetViewingReportPrompt();
        } else {
            this.viewingReportPrompt = value;
            this.viewingReportPromptCustom = true;
            this.viewingReportPromptVersion = DEFAULT_VIEWING_REPORT_PROMPT_VERSION;
        }
    }

    public int getViewingReportPromptVersion() {
        return viewingReportPromptVersion;
    }

    public boolean isViewingReportPromptCustom() {
        return viewingReportPromptCustom;
    }

    public void resetViewingReportPrompt() {
        viewingReportPrompt = DEFAULT_VIEWING_REPORT_PROMPT;
        viewingReportPromptCustom = false;
        viewingReportPromptVersion = DEFAULT_VIEWING_REPORT_PROMPT_VERSION;
    }

    public String getAdDetectionPrompt() {
        return adDetectionPrompt;
    }

    public void setAdDetectionPrompt(String adDetectionPrompt) {
        String value = adDetectionPrompt == null ? "" : adDetectionPrompt.trim();
        if (isEmpty(value) || isBuiltInAdDetectionPrompt(value)) {
            resetAdDetectionPrompt();
        } else {
            this.adDetectionPrompt = value;
            this.adDetectionPromptCustom = true;
            this.adDetectionPromptVersion = DEFAULT_AD_DETECTION_PROMPT_VERSION;
        }
    }

    public int getAdDetectionPromptVersion() {
        return adDetectionPromptVersion;
    }

    public boolean isAdDetectionPromptCustom() {
        return adDetectionPromptCustom;
    }

    public void resetAdDetectionPrompt() {
        adDetectionPrompt = DEFAULT_AD_DETECTION_PROMPT;
        adDetectionPromptCustom = false;
        adDetectionPromptVersion = DEFAULT_AD_DETECTION_PROMPT_VERSION;
    }

    public String getGroupRulePrompt() {
        return groupRulePrompt;
    }

    public void setGroupRulePrompt(String groupRulePrompt) {
        String value = groupRulePrompt == null ? "" : groupRulePrompt.trim();
        if (isEmpty(value) || isBuiltInGroupRulePrompt(value)) {
            resetGroupRulePrompt();
        } else {
            this.groupRulePrompt = value;
            this.groupRulePromptCustom = true;
            this.groupRulePromptVersion = DEFAULT_GROUP_RULE_PROMPT_VERSION;
        }
    }

    public int getGroupRulePromptVersion() {
        return groupRulePromptVersion;
    }

    public boolean isGroupRulePromptCustom() {
        return groupRulePromptCustom;
    }

    public void resetGroupRulePrompt() {
        groupRulePrompt = DEFAULT_GROUP_RULE_PROMPT;
        groupRulePromptCustom = false;
        groupRulePromptVersion = DEFAULT_GROUP_RULE_PROMPT_VERSION;
    }

    public static String defaultEndpoint(String protocol) {
        if (PROTOCOL_OPENAI_CHAT.equals(protocol)) return DEFAULT_OPENAI_CHAT_ENDPOINT;
        if (PROTOCOL_ANTHROPIC_MESSAGES.equals(protocol)) return DEFAULT_ANTHROPIC_ENDPOINT;
        if (PROTOCOL_GEMINI_NATIVE.equals(protocol)) return DEFAULT_GEMINI_ENDPOINT;
        return DEFAULT_ENDPOINT;
    }

    public static boolean isSupportedProtocol(String protocol) {
        if (protocol == null) return false;
        String value = protocol.trim();
        return PROTOCOL_OPENAI_RESPONSES.equals(value)
                || PROTOCOL_OPENAI_CHAT.equals(value)
                || PROTOCOL_ANTHROPIC_MESSAGES.equals(value)
                || PROTOCOL_GEMINI_NATIVE.equals(value);
    }

    private static String trimOr(String value, String fallback) {
        return isEmpty(value) ? fallback : value.trim();
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void sanitizeRecommendPrompt() {
        String value = recommendPrompt == null ? "" : recommendPrompt.trim();
        if (isEmpty(value) || isBuiltInRecommendPrompt(value)) {
            resetRecommendPrompt();
            return;
        }
        recommendPrompt = value;
        recommendPromptCustom = true;
        if (recommendPromptVersion <= 0) recommendPromptVersion = DEFAULT_RECOMMEND_PROMPT_VERSION;
    }

    private void sanitizeTitleExtractionPrompt() {
        String value = titleExtractionPrompt == null ? "" : titleExtractionPrompt.trim();
        if (isEmpty(value) || isBuiltInTitleExtractionPrompt(value)) {
            resetTitleExtractionPrompt();
            return;
        }
        titleExtractionPrompt = value;
        titleExtractionPromptCustom = true;
        if (titleExtractionPromptVersion <= 0) titleExtractionPromptVersion = DEFAULT_TITLE_EXTRACTION_PROMPT_VERSION;
    }

    private void sanitizeViewingReportPrompt() {
        String value = viewingReportPrompt == null ? "" : viewingReportPrompt.trim();
        if (isEmpty(value) || isBuiltInViewingReportPrompt(value)) {
            resetViewingReportPrompt();
            return;
        }
        viewingReportPrompt = value;
        viewingReportPromptCustom = true;
        if (viewingReportPromptVersion <= 0) viewingReportPromptVersion = DEFAULT_VIEWING_REPORT_PROMPT_VERSION;
    }

    private void sanitizeAdDetectionPrompt() {
        String value = adDetectionPrompt == null ? "" : adDetectionPrompt.trim();
        if (isEmpty(value) || isBuiltInAdDetectionPrompt(value)) {
            resetAdDetectionPrompt();
            return;
        }
        adDetectionPrompt = value;
        adDetectionPromptCustom = true;
        if (adDetectionPromptVersion <= 0) adDetectionPromptVersion = DEFAULT_AD_DETECTION_PROMPT_VERSION;
    }

    private void sanitizeGroupRulePrompt() {
        String value = groupRulePrompt == null ? "" : groupRulePrompt.trim();
        if (isEmpty(value) || isBuiltInGroupRulePrompt(value)) {
            resetGroupRulePrompt();
            return;
        }
        groupRulePrompt = value;
        groupRulePromptCustom = true;
        if (groupRulePromptVersion <= 0) groupRulePromptVersion = DEFAULT_GROUP_RULE_PROMPT_VERSION;
    }

    public static boolean isBuiltInRecommendPrompt(String prompt) {
        if (prompt == null) return false;
        String value = prompt.trim();
        if (DEFAULT_RECOMMEND_PROMPT.equals(value)) return true;
        return LEGACY_RECOMMEND_PROMPT_V2.equals(value) || LEGACY_RECOMMEND_PROMPT_V1.equals(value);
    }

    public static boolean isBuiltInTitleExtractionPrompt(String prompt) {
        if (prompt == null) return false;
        String value = prompt.trim();
        if (DEFAULT_TITLE_EXTRACTION_PROMPT.equals(value)) return true;
        return LEGACY_TITLE_EXTRACTION_PROMPT_V1.equals(value);
    }

    public static boolean isBuiltInViewingReportPrompt(String prompt) {
        if (prompt == null) return false;
        String value = prompt.trim();
        if (DEFAULT_VIEWING_REPORT_PROMPT.equals(value)) return true;
        return LEGACY_VIEWING_REPORT_PROMPT_V1.equals(value);
    }

    public static boolean isBuiltInAdDetectionPrompt(String prompt) {
        if (prompt == null) return false;
        String value = prompt.trim();
        if (DEFAULT_AD_DETECTION_PROMPT.equals(value)) return true;
        return DEFAULT_AD_DETECTION_PROMPT_V1.equals(value);
    }

    public static boolean isBuiltInGroupRulePrompt(String prompt) {
        if (prompt == null) return false;
        String value = prompt.trim();
        if (DEFAULT_GROUP_RULE_PROMPT.equals(value)) return true;
        return LEGACY_GROUP_RULE_PROMPT_V1.equals(value);
    }

    public static String[] systemRecommendPromptsForCache() {
        return new String[]{DEFAULT_RECOMMEND_PROMPT, LEGACY_RECOMMEND_PROMPT_V2, LEGACY_RECOMMEND_PROMPT_V1};
    }

    public static String[] systemTitleExtractionPromptsForCache() {
        return new String[]{DEFAULT_TITLE_EXTRACTION_PROMPT, LEGACY_TITLE_EXTRACTION_PROMPT_V1};
    }

    public static String[] systemViewingReportPromptsForCache() {
        return new String[]{DEFAULT_VIEWING_REPORT_PROMPT, LEGACY_VIEWING_REPORT_PROMPT_V1};
    }

    public static String[] systemAdDetectionPromptsForCache() {
        return new String[]{DEFAULT_AD_DETECTION_PROMPT};
    }

    public static String[] systemGroupRulePromptsForCache() {
        return new String[]{DEFAULT_GROUP_RULE_PROMPT, LEGACY_GROUP_RULE_PROMPT_V1};
    }
}
