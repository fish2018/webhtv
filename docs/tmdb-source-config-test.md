# TMDB 数据源配置测试

配置弹窗使用当前未保存的 TMDB API 凭据、API 域名、图片域名和 OMDb API Key 执行只读测试。输入会先经过与保存配置相同的域名、路径和凭据类型规范化；TMDB API 检查请求 `3/configuration`，只有成功返回且包含 TMDB `images` 配置对象时通过；图片检查请求实际配置尺寸下的固定 TMDB 图片，只有 HTTP 状态、内容类型、非空响应和图片文件签名均有效时通过；OMDb 检查使用固定 IMDb ID 请求 `www.omdbapi.com`，只有返回有效 `imdbID` 时通过。测试在后台线程执行，不保存或改变配置，并分别显示 TMDB API、图片域名和 OMDb API 的结果及请求延迟。
