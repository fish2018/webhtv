<?php
/**
 * ============================================================================
 * Spider API 代理脚本(T4风格)
 * ============================================================================
 * 
 * 本脚本演示如何使用 Spider API 来调用 APP 内置的爬虫运行时
 * 支持 JAR/Python/QuickJS/PHP 等多种爬虫类型
 * 
 * @author    lg
 * @version   1.0.0
 * @license   MIT
 * 
 * ============================================================================
 * 使用前提
 * ============================================================================
 * 
 * 1. 在 APP 中启用 Spider API: 设置 → 个性设置 → Spider API → 开
 * 2. 确保本脚本能访问 APP 所在设备的 9978 端口
 * 3. 配置下方的 SPIDER_API_HOST 为正确的设备地址
 * 
 * ============================================================================
 */

// ============================================================================
// 配置区域
// ============================================================================

/**
 * Spider API 服务地址
 * 如需要局域网访问，可以修改为你手机的局域网 IP 地址
 */
define('SPIDER_API_HOST', 'http://127.0.0.1:9978');

/**
 * 默认站点 key
 * 设置后可以省略 URL 中的 key 参数
 * 留空则必须在每个请求中指定 key
 */
define('DEFAULT_SITE_KEY', '');

/**
 * 请求超时时间（秒）
 */
define('REQUEST_TIMEOUT', 30);

/**
 * 是否开启调试模式
 */
define('DEBUG_MODE', false);

// ============================================================================
// Spider API 客户端类
// ============================================================================

class SpiderApiClient
{
    /** @var string */
    private $host;
    /** @var int */
    private $timeout;
    /** @var bool */
    private $debug;
    /** @var string|null */
    private $lastError = null;

    public function __construct($host, $timeout = 30, $debug = false)
    {
        $this->host = rtrim($host, '/');
        $this->timeout = $timeout;
        $this->debug = $debug;
    }

    // ========================================================================
    // 配置管理方法
    // ========================================================================

    /**
     * 使用 APP 数据源（推荐）
     * 
     * 直接使用 APP 当前加载的 VodConfig 中的站点
     * 这是最简单的使用方式，无需额外加载配置
     * 
     * @return array API 响应
     */
    public function useAppDataSource()
    {
        return $this->configAction('use_app');
    }

    /**
     * 通过 URL 加载配置
     * 
     * @param string $url 配置文件 URL（支持 http/https/file 协议）
     * @return array API 响应
     * 
     * @example
     * // 从远程加载
     * $client->loadConfigByUrl('http://example.com/config.json');
     * 
     * // 从本地文件加载（APP assets 目录）
     * $client->loadConfigByUrl('file://VodPlus/config.json');
     */
    public function loadConfigByUrl($url)
    {
        return $this->configAction('load', ['url' => $url]);
    }

    /**
     * 通过 JSON 字符串加载配置
     * 
     * @param string $jsonContent 配置 JSON 字符串
     * @return array API 响应
     */
    public function loadConfigByContent($jsonContent)
    {
        return $this->configAction('load', ['content' => $jsonContent]);
    }

    /**
     * 通过配置对象加载
     * 
     * @param array $config 配置数组
     * @return array API 响应
     * 
     * @example
     * $client->loadConfigByObject([
     *     'spider' => 'http://example.com/spider.jar',
     *     'sites' => [
     *         [
     *             'key' => 'site1',
     *             'name' => '站点1',
     *             'api' => 'csp_SomeSpider',
     *             'type' => 3,
     *             'ext' => '扩展参数'
     *         ]
     *     ]
     * ]);
     */
    public function loadConfigByObject($config)
    {
        return $this->configAction('load', ['config' => $config]);
    }

    /**
     * 切换当前站点
     * 
     * @param string $key 站点 key
     * @return array API 响应
     */
    public function switchSite($key)
    {
        return $this->configAction('switch', ['key' => $key]);
    }

    /**
     * 获取配置状态
     * 
     * @return array API 响应（包含站点列表等信息）
     */
    public function getStatus()
    {
        return $this->configAction('status');
    }

    /**
     * 移除站点（仅独立配置模式下有效）
     * 
     * @param string $key 站点 key
     * @return array API 响应
     */
    public function removeSite($key)
    {
        return $this->configAction('remove', ['key' => $key]);
    }

    /**
     * 清除配置（仅独立配置模式下有效）
     * 
     * @return array API 响应
     */
    public function clearConfig()
    {
        return $this->configAction('clear');
    }

    // ========================================================================
    // 爬虫执行方法
    // ========================================================================

    /**
     * 通过站点 key 执行爬虫方法
     * 
     * @param string $key    站点 key
     * @param string $method 方法名
     * @param array  $params 方法参数
     * @return array API 响应
     * 
     * @example
     * // 获取首页内容
     * $client->executeByKey('site1', 'homeContent', ['filter' => true]);
     * 
     * // 获取分类内容
     * $client->executeByKey('site1', 'categoryContent', [
     *     'tid' => 'movie',
     *     'pg' => 1,
     *     'filter' => true,
     *     'extend' => ['area' => '大陆', 'year' => '2024']
     * ]);
     */
    public function executeByKey($key, $method, $params = array())
    {
        $data = array_merge([
            'key' => $key,
            'method' => $method
        ], $params);

        return $this->post('/spider', $data);
    }

    /**
     * 通过完整站点配置执行爬虫方法
     * 
     * 无需预先加载配置，直接传入站点信息执行
     * 适用于临时调用或动态生成站点配置的场景
     * 
     * @param array  $site   站点配置
     * @param string $method 方法名
     * @param array  $params 方法参数
     * @return array API 响应
     * 
     * @example
     * // Type 3: JAR 爬虫
     * $client->executeBySite([
     *     'key' => 'test',
     *     'name' => '测试站',
     *     'api' => 'csp_SomeSpider',
     *     'type' => 3,
     *     'jar' => 'http://example.com/spider.jar',
     *     'ext' => '扩展参数'
     * ], 'homeContent', ['filter' => true]);
     * 
     * // Type 4: 苹果CMS V10 API
     * $client->executeBySite([
     *     'key' => 'cms_test',
     *     'name' => 'CMS站点',
     *     'api' => 'https://example.com/api.php/provide/vod',
     *     'type' => 4
     * ], 'homeContent', ['filter' => true]);
     */
    public function executeBySite($site, $method, $params = array())
    {
        $data = array_merge([
            'site' => $site,
            'method' => $method
        ], $params);

        return $this->post('/spider', $data);
    }

    // ========================================================================
    // 便捷方法：常用爬虫操作
    // ========================================================================

    /**
     * 获取首页内容
     * 
     * @param string $key    站点 key
     * @param bool   $filter 是否获取筛选条件
     * @return array API 响应
     */
    public function homeContent($key, $filter = true)
    {
        return $this->executeByKey($key, 'homeContent', ['filter' => $filter]);
    }

    /**
     * 获取首页视频列表
     * 
     * @param string $key 站点 key
     * @return array API 响应
     */
    public function homeVideoContent($key)
    {
        return $this->executeByKey($key, 'homeVideoContent');
    }

    /**
     * 获取分类内容
     * 
     * @param string $key     站点 key
     * @param string $tid     分类 ID
     * @param int    $page    页码
     * @param bool   $filter  是否获取筛选条件
     * @param array  $extend  筛选参数
     * @return array API 响应
     */
    public function categoryContent($key, $tid, $page = 1, $filter = true, $extend = array())
    {
        return $this->executeByKey($key, 'categoryContent', [
            'tid' => $tid,
            'pg' => $page,
            'filter' => $filter,
            'extend' => $extend
        ]);
    }

    /**
     * 获取视频详情
     * 
     * @param string       $key 站点 key
     * @param string|array $ids 视频 ID 或 ID 数组
     * @return array API 响应
     */
    public function detailContent($key, $ids)
    {
        return $this->executeByKey($key, 'detailContent', [
            'ids' => is_array($ids) ? $ids : [$ids]
        ]);
    }

    /**
     * 获取播放地址
     * 
     * @param string $key   站点 key
     * @param string $flag  播放源标识
     * @param string $id    播放 ID
     * @param array  $flags 所有播放源标识（可选）
     * @return array API 响应
     */
    public function playerContent($key, $flag, $id, $flags = array())
    {
        return $this->executeByKey($key, 'playerContent', [
            'flag' => $flag,
            'id' => $id,
            'flags' => $flags
        ]);
    }

    /**
     * 搜索内容
     * 
     * @param string $key     站点 key
     * @param string $keyword 搜索关键词
     * @param bool   $quick   是否快速搜索
     * @param int    $page    页码
     * @return array API 响应
     */
    public function searchContent($key, $keyword, $quick = false, $page = 1)
    {
        return $this->executeByKey($key, 'searchContent', [
            'wd' => $keyword,
            'quick' => $quick,
            'pg' => $page
        ]);
    }

    // ========================================================================
    // 内部方法
    // ========================================================================

    /**
     * 执行配置操作
     */
    private function configAction($action, $extra = array())
    {
        $data = array_merge(['action' => $action], $extra);
        return $this->post('/spider/config', $data);
    }

    /**
     * 发送 POST 请求
     */
    private function post($endpoint, $data)
    {
        $url = $this->host . $endpoint;
        $jsonData = json_encode($data, JSON_UNESCAPED_UNICODE);

        $this->log("POST {$url}");
        $this->log("Request: {$jsonData}");

        $ch = curl_init();
        curl_setopt_array($ch, [
            CURLOPT_URL => $url,
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => $jsonData,
            CURLOPT_HTTPHEADER => [
                'Content-Type: application/json; charset=utf-8',
                'Accept: application/json'
            ],
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => $this->timeout,
            CURLOPT_CONNECTTIMEOUT => 10
        ]);

        $response = curl_exec($ch);
        $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $error = curl_error($ch);
        curl_close($ch);

        if ($response === false) {
            $this->lastError = "cURL Error: {$error}";
            $this->log("Error: {$this->lastError}");
            return ['code' => -1, 'msg' => $this->lastError];
        }

        $this->log("Response ({$httpCode}): {$response}");

        $result = json_decode($response, true);
        if (json_last_error() !== JSON_ERROR_NONE) {
            $this->lastError = "JSON Parse Error: " . json_last_error_msg();
            return ['code' => -1, 'msg' => $this->lastError, 'raw' => $response];
        }

        return $result;
    }

    /**
     * 获取最后的错误信息
     */
    public function getLastError()
    {
        return $this->lastError;
    }

    /**
     * 调试日志
     */
    private function log($message)
    {
        if ($this->debug) {
            error_log("[SpiderAPI] {$message}");
        }
    }
}

// ============================================================================
// HTTP 代理接口 - Type 4 (苹果CMS V10) 风格
// ============================================================================

/**
 * 处理 HTTP 请求并代理到 Spider API
 * 
 * 默认使用 Type 4 (苹果CMS V10) 接口风格:
 * - ?ac=list                          获取分类列表（首页）
 * - ?ac=detail&ids=1,2,3              获取视频详情
 * - ?ac=list&t=1&pg=1                 获取分类内容
 * - ?wd=关键词                         搜索
 * - ?ac=play&flag=xxx&id=xxx          获取播放地址
 * 
 * 同时支持 Spider API 原生风格:
 * - ?action=homeContent&key=xxx
 * - ?action=detailContent&key=xxx&ids=1,2,3
 */
class SpiderApiProxy
{
    /** @var SpiderApiClient */
    private $client;
    /** @var string */
    private $defaultSiteKey;

    public function __construct($client, $defaultSiteKey = '')
    {
        $this->client = $client;
        $this->defaultSiteKey = $defaultSiteKey;
    }

    /**
     * 设置默认站点 key
     */
    public function setDefaultSiteKey($key)
    {
        $this->defaultSiteKey = $key;
    }

    /**
     * 处理请求
     */
    public function handle()
    {
        // 设置响应头
        header('Content-Type: application/json; charset=utf-8');
        header('Access-Control-Allow-Origin: *');
        header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
        header('Access-Control-Allow-Headers: Content-Type');

        // 处理 CORS 预检请求
        if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
            http_response_code(204);
            exit;
        }

        try {
            $result = $this->dispatch();
            echo json_encode($result, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
        } catch (Exception $e) {
            http_response_code(500);
            echo json_encode([
                'code' => -1,
                'msg' => 'Proxy Error: ' . $e->getMessage()
            ], JSON_UNESCAPED_UNICODE);
        }
    }

    /**
     * 路由分发
     */
    private function dispatch()
    {
        $input = $this->getInput();
        
        // 优先检查是否使用 Spider API 原生 action 参数
        if (isset($input['action'])) {
            return $this->dispatchByAction($input);
        }

        // 否则使用 Type 4 (苹果CMS V10) 风格参数
        return $this->dispatchByType4Style($input);
    }

    /**
     * Type 4 (苹果CMS V10) 风格路由
     * 
     * 参数说明:
     * - ac: 操作类型 (list/detail/play)
     * - t:  分类 ID
     * - pg: 页码
     * - ids: 视频 ID（逗号分隔）
     * - wd: 搜索关键词
     * - flag: 播放源标识
     * - id: 播放 ID
     * - key: 站点 key（可选，默认使用 defaultSiteKey）
     */
    private function dispatchByType4Style($input)
    {
        $ac = isset($input['ac']) ? $input['ac'] : '';
        $key = isset($input['key']) ? $input['key'] : $this->defaultSiteKey;

        // 验证站点 key
        if (empty($key)) {
            return array(
                'code' => -1, 
                'msg' => '缺少站点 key 参数。请在 URL 中添加 ?key=站点key 或配置 DEFAULT_SITE_KEY'
            );
        }

        // ================================================================
        // 搜索 (优先级最高，因为可能同时有 ac 参数)
        // ================================================================
        if (!empty($input['wd'])) {
            $pg = (int)(isset($input['pg']) ? $input['pg'] : 1);
            return $this->client->searchContent($key, $input['wd'], false, $pg);
        }

        // ================================================================
        // 根据 ac 参数分发
        // ================================================================
        switch ($ac) {
            case 'detail':
                // 获取视频详情: ?ac=detail&ids=1,2,3
                $ids = isset($input['ids']) ? $input['ids'] : '';
                if (empty($ids)) {
                    return array('code' => -1, 'msg' => '缺少 ids 参数');
                }
                $idArray = is_array($ids) ? $ids : explode(',', $ids);
                return $this->client->detailContent($key, $idArray);

            case 'play':
                // 获取播放地址: ?ac=play&flag=xxx&id=xxx
                $flag = isset($input['flag']) ? $input['flag'] : '';
                $id = isset($input['id']) ? $input['id'] : '';
                if (empty($flag) || empty($id)) {
                    return array('code' => -1, 'msg' => '缺少 flag 或 id 参数');
                }
                $flags = isset($input['flags']) ? explode(',', $input['flags']) : array();
                return $this->client->playerContent($key, $flag, $id, $flags);

            case 'list':
            default:
                // 获取分类/首页内容
                $t = isset($input['t']) ? $input['t'] : '';
                $pg = (int)(isset($input['pg']) ? $input['pg'] : 1);
                $filter = (isset($input['f']) ? $input['f'] : '1') === '1';

                if (empty($t)) {
                    // 无分类 ID = 获取首页内容
                    return $this->client->homeContent($key, $filter);
                } else {
                    // 有分类 ID = 获取分类内容
                    $extend = array();
                    // 支持筛选参数
                    foreach (array('area', 'year', 'type', 'class', 'lang') as $field) {
                        if (!empty($input[$field])) {
                            $extend[$field] = $input[$field];
                        }
                    }
                    return $this->client->categoryContent($key, $t, $pg, $filter, $extend);
                }
        }
    }

    /**
     * Spider API 原生 action 风格路由
     */
    private function dispatchByAction($input)
    {
        $action = $input['action'];

        switch ($action) {
            // ================================================================
            // 配置管理
            // ================================================================
            
            case 'use_app':
                return $this->client->useAppDataSource();

            case 'load':
                if (!empty($input['url'])) {
                    return $this->client->loadConfigByUrl($input['url']);
                } elseif (!empty($input['content'])) {
                    return $this->client->loadConfigByContent($input['content']);
                } elseif (!empty($input['config'])) {
                    return $this->client->loadConfigByObject($input['config']);
                }
                return array('code' => -1, 'msg' => 'Missing url, content or config');

            case 'switch':
                return $this->client->switchSite(isset($input['key']) ? $input['key'] : '');

            case 'status':
                return $this->client->getStatus();

            // ================================================================
            // 爬虫执行 (Spider API 原生风格)
            // ================================================================

            case 'homeContent':
                return $this->executeSpiderMethod($input, 'homeContent');

            case 'homeVideoContent':
                return $this->executeSpiderMethod($input, 'homeVideoContent');

            case 'categoryContent':
                return $this->executeSpiderMethod($input, 'categoryContent');

            case 'detailContent':
                return $this->executeSpiderMethod($input, 'detailContent');

            case 'playerContent':
                return $this->executeSpiderMethod($input, 'playerContent');

            case 'searchContent':
                return $this->executeSpiderMethod($input, 'searchContent');

            default:
                return array('code' => -1, 'msg' => "Unknown action: {$action}");
        }
    }

    /**
     * 执行爬虫方法 (Spider API 原生风格)
     */
    private function executeSpiderMethod($input, $method)
    {
        // 方式一：通过 site 对象执行（优先）
        if (!empty($input['site'])) {
            return $this->client->executeBySite($input['site'], $method, $input);
        }

        // 方式二：通过 key 执行
        $key = isset($input['key']) ? $input['key'] : $this->defaultSiteKey;
        if (empty($key)) {
            return array('code' => -1, 'msg' => 'Missing key or site');
        }

        return $this->client->executeByKey($key, $method, $input);
    }

    /**
     * 获取请求输入
     */
    private function getInput()
    {
        // 合并 GET 和 POST 参数
        $input = array_merge($_GET, $_POST);

        // 尝试解析 JSON body
        $rawBody = file_get_contents('php://input');
        if (!empty($rawBody)) {
            $jsonBody = json_decode($rawBody, true);
            if (is_array($jsonBody)) {
                $input = array_merge($input, $jsonBody);
            }
        }

        return $input;
    }
}

// ============================================================================
// 使用示例
// ============================================================================

/*
 * ============================================================================
 * Type 4 (苹果CMS V10) 风格接口 - 默认模式
 * ============================================================================
 * 
 * 本代理默认使用 Type 4 风格，兼容苹果CMS V10 API 格式
 * 
 * 基础 URL: http://your-server.com/spider_api_proxy.php
 * 
 * --------------------------------------------------
 * 获取首页/分类列表
 * --------------------------------------------------
 * 
 * # 获取首页内容（分类列表 + 筛选条件）
 * ?key=站点key
 * ?key=站点key&ac=list
 * 
 * # 获取分类内容
 * ?key=站点key&ac=list&t=1              # 分类ID=1
 * ?key=站点key&ac=list&t=1&pg=2         # 第2页
 * 
 * # 带筛选条件
 * ?key=站点key&ac=list&t=1&area=大陆&year=2024
 * 
 * --------------------------------------------------
 * 获取视频详情
 * --------------------------------------------------
 * 
 * ?key=站点key&ac=detail&ids=123
 * ?key=站点key&ac=detail&ids=123,456,789    # 批量获取
 * 
 * --------------------------------------------------
 * 搜索
 * --------------------------------------------------
 * 
 * ?key=站点key&wd=搜索关键词
 * ?key=站点key&wd=搜索关键词&pg=2           # 第2页
 * 
 * --------------------------------------------------
 * 获取播放地址
 * --------------------------------------------------
 * 
 * ?key=站点key&ac=play&flag=播放源&id=播放ID
 * 
 * 
 * ============================================================================
 * Spider API 原生风格接口 - 使用 action 参数
 * ============================================================================
 * 
 * 当 URL 中包含 action 参数时，自动切换到 Spider API 原生风格
 * 
 * --------------------------------------------------
 * 配置管理
 * --------------------------------------------------
 * 
 * # 使用 APP 数据源（推荐）
 * ?action=use_app
 * 
 * # 查看配置状态
 * ?action=status
 * 
 * # 加载独立配置
 * ?action=load&url=http://example.com/config.json
 * 
 * # 切换站点
 * ?action=switch&key=站点key
 * 
 * --------------------------------------------------
 * 爬虫执行
 * --------------------------------------------------
 * 
 * ?action=homeContent&key=站点key&filter=true
 * ?action=categoryContent&key=站点key&tid=1&pg=1
 * ?action=detailContent&key=站点key&ids=123
 * ?action=searchContent&key=站点key&wd=关键词
 * ?action=playerContent&key=站点key&flag=播放源&id=播放ID
 * 
 * 
 * ============================================================================
 * PHP SDK 使用示例
 * ============================================================================
 */

// 创建客户端
// $client = new SpiderApiClient(SPIDER_API_HOST, REQUEST_TIMEOUT, DEBUG_MODE);

// ============================================================================
// 示例 1: 使用 APP 数据源（推荐方式）
// ============================================================================

// 切换到 APP 数据源模式（APP 启动后默认就是此模式，通常无需调用）
// $result = $client->useAppDataSource();

// 查看可用站点
// $status = $client->getStatus();
// var_dump($status);

// 通过 key 调用爬虫（key 来自 APP 配置中的站点）
// $result = $client->homeContent('your_site_key', true);

// ============================================================================
// 示例 2: 加载独立配置
// ============================================================================

// 方式 A: 通过 URL 加载
// $client->loadConfigByUrl('http://your-server.com/config.json');

// 方式 B: 通过本地文件加载（APP assets 目录）
// $client->loadConfigByUrl('file://VodPlus/config.json');

// 方式 C: 通过 JSON 字符串加载
// $client->loadConfigByContent('{"spider":"...","sites":[...]}');

// 方式 D: 通过配置对象加载
// $client->loadConfigByObject([
//     'spider' => 'http://example.com/spider.jar',
//     'sites' => [
//         [
//             'key' => 'my_site',
//             'name' => '我的站点',
//             'api' => 'csp_MySpider',
//             'type' => 3,
//             'ext' => 'custom_param=value'
//         ]
//     ]
// ]);

// ============================================================================
// 示例 3: 通过完整 site 对象执行（无需预加载配置）
// ============================================================================

// Type 3: JAR 爬虫
// $result = $client->executeBySite([
//     'key' => 'jar_spider',
//     'name' => 'JAR爬虫',
//     'api' => 'csp_SomeSpider',
//     'type' => 3,
//     'jar' => 'http://example.com/spider.jar',
//     'ext' => 'token=xxx'
// ], 'homeContent', ['filter' => true]);

// Type 4: 苹果CMS V10 API
// $result = $client->executeBySite([
//     'key' => 'cms_site',
//     'name' => 'CMS站点',
//     'api' => 'https://example.com/api.php/provide/vod',
//     'type' => 4
// ], 'homeContent', ['filter' => true]);

// Type 1: 苹果CMS JSON
// $result = $client->executeBySite([
//     'key' => 'json_site',
//     'name' => 'JSON站点',
//     'api' => 'https://example.com/api.php/provide/vod/?ac=list',
//     'type' => 1
// ], 'homeContent');

// ============================================================================
// 示例 4: 常用操作
// ============================================================================

// 获取首页内容和筛选条件
// $home = $client->homeContent('site_key', true);

// 获取首页推荐视频
// $videos = $client->homeVideoContent('site_key');

// 获取分类内容（带筛选）
// $category = $client->categoryContent('site_key', 'movie', 1, true, [
//     'area' => '大陆',
//     'year' => '2024',
//     'type' => '动作'
// ]);

// 获取视频详情
// $detail = $client->detailContent('site_key', 'video_id_123');

// 获取播放地址
// $player = $client->playerContent('site_key', '播放源', 'play_id_456');

// 搜索视频
// $search = $client->searchContent('site_key', '搜索关键词', false, 1);

// ============================================================================
// 主入口：作为 HTTP 代理运行
// ============================================================================

// 当直接访问此文件时，作为 HTTP 代理运行
if (php_sapi_name() !== 'cli') {
    $client = new SpiderApiClient(SPIDER_API_HOST, REQUEST_TIMEOUT, DEBUG_MODE);
    $proxy = new SpiderApiProxy($client, DEFAULT_SITE_KEY);
    $proxy->handle();
}
